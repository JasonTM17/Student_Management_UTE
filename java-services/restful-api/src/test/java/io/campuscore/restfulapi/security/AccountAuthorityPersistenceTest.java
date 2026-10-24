package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.exception.response.ApiErrorWriter;
import io.campuscore.restfulapi.security.availability.DatabaseAvailabilityTracker;
import io.campuscore.restfulapi.security.filter.AccountStateFilter;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Actual JDBC + filter proof; fixtures own an isolated store, never a test-profile bypass. */
class AccountAuthorityPersistenceTest {
    private JdbcTemplate jdbc;
    private AccountStateFilter filter;

    @BeforeEach
    void prepare() {
        DatabaseAvailabilityTracker.recordSuccess();
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:authority_guard;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE SCHEMA campuscore_auth");
        jdbc.execute("CREATE TABLE campuscore_auth.\"User\" (\"id\" VARCHAR PRIMARY KEY, \"status\" VARCHAR, \"mustChangePassword\" BOOLEAN)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"Student\" (\"id\" VARCHAR, \"userId\" VARCHAR, \"year\" INTEGER)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"Lecturer\" (\"id\" VARCHAR, \"userId\" VARCHAR, \"isActive\" BOOLEAN)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"Role\" (\"id\" VARCHAR, \"name\" VARCHAR)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"UserRole\" (\"userId\" VARCHAR, \"roleId\" VARCHAR)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"Permission\" (\"id\" VARCHAR, \"module\" VARCHAR, \"action\" VARCHAR)");
        jdbc.execute("CREATE TABLE campuscore_auth.\"RolePermission\" (\"roleId\" VARCHAR, \"permissionId\" VARCHAR)");
        jdbc.update("INSERT INTO campuscore_auth.\"User\" VALUES ('user','ACTIVE',FALSE)");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureCredentialVersion(jdbc);
        jdbc.update("INSERT INTO campuscore_auth.\"Role\" VALUES ('role','ADMIN')");
        jdbc.update("INSERT INTO campuscore_auth.\"UserRole\" VALUES ('user','role')");
        var repository = new AuthUserRepository(new NamedParameterJdbcTemplate(jdbc));
        var beans = new StaticListableBeanFactory(Map.of("users", repository));
        filter = new AccountStateFilter(beans.getBeanProvider(AuthUserRepository.class), new ApiErrorWriter(new ObjectMapper()));
        authenticate(List.of("ADMIN"), List.of(), null);
    }

    @AfterEach
    void clear() {
        DatabaseAvailabilityTracker.recordSuccess();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(List<String> roles, List<String> permissions, String lecturerId) {
        var builder = Jwt.withTokenValue("signed-claim-fixture").header("alg", "HS256").subject("user")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900))
                .claim("roles", roles).claim("permissions", permissions);
        if (lecturerId != null) builder.claim("lecturerId", lecturerId);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(),
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }

    private void expect(String path, int status) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(status, response.getStatus());
        if (status != 200) verify(chain, never()).doFilter(request, response);
        else verify(chain).doFilter(request, response);
    }

    @Test
    void unchangedAccountPasses() throws Exception {
        expect("/api/v1/admin/users", 200);
    }

    @Test
    void demotedAdminTokenCannotKeepAdminAuthority() throws Exception {
        jdbc.update("UPDATE campuscore_auth.\"Role\" SET \"name\"='STUDENT'");
        expect("/api/v1/admin/users", 401);
    }

    @Test
    void deletedAccountCannotReadWithAnUnexpiredToken() throws Exception {
        jdbc.update("DELETE FROM campuscore_auth.\"User\"");
        expect("/api/v1/admin/users", 401);
        expect("/api/v1/auth/me", 401);
    }

    @Test
    void removedPermissionAndReassignedProfileInvalidateTheClaims() throws Exception {
        authenticate(List.of("ADMIN"), List.of("academic:write"), null);
        expect("/api/v1/admin/users", 401);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" VALUES ('new-profile','user',TRUE)");
        authenticate(List.of("ADMIN"), List.of(), "old-profile");
        expect("/api/v1/thesis/councils", 401);
    }

    /**
     * A deactivated lecturer profile must not keep matching a token's
     * lecturerId claim — the authorization join now requires isActive=TRUE,
     * so the stale token is rejected like any other profile reassignment.
     */
    @Test
    void deactivatedLecturerProfileRevokesTheBoundToken() throws Exception {
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" VALUES ('lecturer-profile','user',TRUE)");
        authenticate(List.of("ADMIN"), List.of(), "lecturer-profile");
        expect("/api/v1/thesis/councils", 200);
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\"=FALSE WHERE \"id\"='lecturer-profile'");
        expect("/api/v1/thesis/councils", 401);
    }

    @Test
    void rotationExemptsOnlyThePasswordFlag() throws Exception {
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"mustChangePassword\"=TRUE");
        expect("/api/v1/auth/me", 200);
        expect("/api/v1/auth/change-password", 200);
        expect("/api/v1/admin/users", 403);
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"status\"='INACTIVE'");
        expect("/api/v1/auth/me", 403);
    }

    @Test
    void databaseOutageNeverForwardsStaleAuthorityToEitherAssistant() throws Exception {
        jdbc.execute("DROP TABLE campuscore_auth.\"User\"");
        expect("/api/v1/assistant/chat", 503);
        expect("/api/v1/thesis/assistant/chat", 503);
    }
}
