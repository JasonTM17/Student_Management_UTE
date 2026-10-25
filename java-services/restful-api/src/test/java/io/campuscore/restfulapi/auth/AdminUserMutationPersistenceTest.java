package io.campuscore.restfulapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.academic.service.LecturerMutationService;
import io.campuscore.restfulapi.auth.service.AdminUserMutationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admin_user_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AdminUserMutationPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LecturerMutationService lecturerMutations;

    @Autowired
    private AdminUserMutationService userMutations;

    @MockitoSpyBean
    private NamedParameterJdbcTemplate namedJdbc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_audit");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void roleDemotionRevokesObsoleteSystemAuthorizationButPreservesCustomRole() throws Exception {
        // Only a super administrator may manage administrator accounts, so the
        // demotion of an ADMIN target runs under a super-admin actor.
        mvc.perform(put("/api/v1/users/target-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role": "STUDENT", "studentId": "SV2026001", "curriculumId": "curriculum-demo", "year": 2}
                                """))
                .andExpect(status().isOk());

        assertThat(roleNames("target-user"))
                .containsExactly("AUDITOR", "STUDENT")
                .doesNotContain("ADMIN");
        assertThat(authorities("target-user"))
                .containsExactly("academic:read", "audit:read")
                .doesNotContain("admin:write");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = ?",
                Integer.class,
                "target-user"))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = ?",
                Integer.class,
                "target-user"))
                .isZero();
    }

    @Test
    void ordinaryAdminCannotGrantOrEditSuperAdminAccounts() throws Exception {
        mvc.perform(put("/api/v1/users/target-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));

        insertUserRole("user-role-super-target", "target-user", "role-super-admin");
        mvc.perform(put("/api/v1/users/target-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Blocked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
    }

    @Test
    void ordinaryAdminCannotDeleteSuperAdminOrAssignCustomRole() throws Exception {
        insertUserRole("user-role-super-target", "target-user", "role-super-admin");
        mvc.perform(delete("/api/v1/users/target-user").with(adminJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));

        mvc.perform(put("/api/v1/users/second-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"AUDITOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
    }

    @Test
    void adminCannotDeleteOwnAccount() throws Exception {
        mvc.perform(delete("/api/v1/users/admin-user").with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DELETION_NOT_ALLOWED"));
    }

    @Test
    void adminCannotDemoteOwnAccount() throws Exception {
        mvc.perform(put("/api/v1/users/admin-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE_NOT_ALLOWED"));
    }

    @Test
    void adminCannotDeactivateOwnAccount() throws Exception {
        mvc.perform(put("/api/v1/users/admin-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"LOCKED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SELF_DEACTIVATION_NOT_ALLOWED"));
    }

    @Test
    void lastActiveSuperAdminCannotBeDemoted() {
        makeOnlyActiveSuperAdmin("target-user");

        assertThatThrownBy(() -> userMutations.update(
                        "target-user", Map.of("role", "ADMIN"), true, "actor-user"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));

        assertThat(roleNames("target-user")).contains("SUPER_ADMIN");
    }

    @Test
    void lastActiveSuperAdminCannotBeDeactivated() {
        makeOnlyActiveSuperAdmin("target-user");

        assertThatThrownBy(() -> userMutations.update(
                        "target-user", Map.of("status", "DISABLED"), true, "actor-user"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));

        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'target-user'",
                String.class)).isEqualTo("ACTIVE");
    }

    @Test
    void lastActiveSuperAdminCannotBeDeleted() {
        makeOnlyActiveSuperAdmin("target-user");

        assertThatThrownBy(() -> userMutations.delete("target-user", true, "actor-user"))
                .isInstanceOfSatisfying(DomainException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("LAST_SUPER_ADMIN_REQUIRED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'target-user'",
                Integer.class)).isEqualTo(1);
    }

    private void makeOnlyActiveSuperAdmin(String userId) {
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\" WHERE \"roleId\" = 'role-super-admin'");
        insertUserRole("last-super-role", userId, "role-super-admin");
    }

    @Test
    void userListIncludesCeilingTotalPagesMetadata() throws Exception {
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void userRoleFilterNarrowsPageAndTotalAndRejectsUnknownRole() throws Exception {
        insertUserRole("user-role-second-student", "second-user", "role-student");
        insertUserRole("user-role-third-student", "third-user", "role-student");

        // The role tabs used to narrow only the fetched page of 20, so the
        // count beside "All" and the pagination below the table described an
        // unfiltered set. Server-side the filter must join the assignment
        // tables, and meta.total must count exactly the matching accounts:
        // limit 1 proves the total comes from the COUNT query, not the page.
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .queryParam("role", "STUDENT")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].roles", org.hamcrest.Matchers.hasItem("STUDENT")));

        // The ADMIN tab sees the target and the real administrator actor;
        // the second user's STUDENT assignment must not leak through.
        mvc.perform(get("/api/v1/users")
                        .queryParam("role", "ADMIN")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].email", org.hamcrest.Matchers.containsInAnyOrder(
                        "target@campuscore.edu", "actor.admin@fixture.invalid")));

        // A name that is not a system role is refused with the same code the
        // mutation guards use; an empty page for a typo would masquerade as a
        // genuinely empty role.
        mvc.perform(get("/api/v1/users")
                        .queryParam("role", "DIRECTOR")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));

        // Omitting the filter leaves the pre-existing contract untouched.
        mvc.perform(get("/api/v1/users").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.data.length()").value(5));

        // The route now carries the catalog query allow-list, so a parameter
        // that looks like a filter but is silently ignored fails loudly.
        mvc.perform(get("/api/v1/users")
                        .queryParam("sort", "email")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void userSearchMatchesUnaccentedTypingAgainstDiacriticStoredNames() throws Exception {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('fold-user', 'nguyen.van@campuscore.edu', 'x', 'Văn', 'Nguyễn', 'ACTIVE',"
                        + " FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // Vietnamese operators routinely type without tone marks. The pre-fix
        // accent-sensitive LIKE returned an empty page for "nguyen" even though
        // the account's stored name is "Nguyễn".
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "nguyen")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].lastName").value("Nguyễn"));

        // Diacritic input still matches the stored spelling, and so does
        // uppercase unaccented input.
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "Nguyễn")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "NGUYEN")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void adminListSerializesRolesAsAnArrayLikeAuthUserResponse() throws Exception {
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "target@campuscore")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].roles").isArray())
                .andExpect(jsonPath("$.data[0].roles", org.hamcrest.Matchers.hasItem("ADMIN")))
                .andExpect(jsonPath("$.data[0].roles", org.hamcrest.Matchers.hasItem("AUDITOR")));
    }

    @Test
    void userSearchEscapesLikeWildcardsInsteadOfTreatingThemAsPatterns() throws Exception {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('wild-user', 'sale_2026@campuscore.edu', 'x', 'Wild', 'Card', 'ACTIVE',"
                        + " FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // Pre-fix "%" interpolated into the LIKE pattern and matched everyone.
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "%")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));

        // "_" is likewise literal: only the row whose email really contains it matches.
        mvc.perform(get("/api/v1/users")
                        .queryParam("page", "1")
                        .queryParam("limit", "10")
                        .queryParam("search", "sale_2026")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.data[0].email").value("sale_2026@campuscore.edu"));
    }

    @Test
    void officeIssuanceReturnsOneTimeSecretForcesRotationAndRejectsDuplicates() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"issued.student@campuscore.edu",
                                  "firstName":"Issued",
                                  "lastName":"Student",
                                  "role":"STUDENT",
                                  "studentId":"SV-ISSUED-001",
                                  "curriculumId":"curriculum-demo",
                                  "year":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn();

        String userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        assertThat(jdbc.queryForObject(
                "SELECT \"mustChangePassword\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = ?",
                Boolean.class,
                userId))
                .isTrue();

        // Duplicate identity conflicts surface as distinct field-mappable codes.
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"issued.student@campuscore.edu",
                                  "firstName":"Dup",
                                  "lastName":"Student",
                                  "role":"STUDENT",
                                  "studentId":"SV-ISSUED-002",
                                  "curriculumId":"curriculum-demo",
                                  "year":1
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    void ordinaryAdminCannotCreateAdministratorButSuperAdminCan() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new.admin@campuscore.edu","firstName":"New","lastName":"Admin","role":"ADMIN"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));

        mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new.admin@campuscore.edu","firstName":"New","lastName":"Admin","role":"ADMIN"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void passwordResetForcesRotationAndRevokesLiveSessions() throws Exception {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                        + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                        + " VALUES ('session-second-user', 'second-user', 'hashed-token',"
                        + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        mvc.perform(post("/api/v1/users/second-user/password-reset")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "SELECT \"mustChangePassword\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                Boolean.class))
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'second-user'",
                Integer.class))
                .isZero();
    }

    @Test
    void administrativeResetRevokesRealAccessTokensAndTheNewCredentialCanAuthenticate() throws Exception {
        String initial = "initial-" + java.util.UUID.randomUUID();
        jdbc.update("UPDATE campuscore_auth.\"User\" SET \"password\"=? WHERE \"id\"='second-user'",
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(initial));
        insertUserRole("second-student-role", "second-user", "role-student");
        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"second@campuscore.edu\",\"password\":\"" + initial + "\"}"))
                .andExpect(status().isOk()).andReturn();
        String oldAccess = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.pendingLoginChallenge(jdbc, "admin-reset-pending-login", "second-user");
        MvcResult reset = mvc.perform(post("/api/v1/users/second-user/password-reset").with(adminJwt()))
                .andExpect(status().isOk()).andReturn();
        String issued = objectMapper.readTree(reset.getResponse().getContentAsString()).get("temporaryPassword").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        mvc.perform(get("/api/v1/auth/me").cookie(login.getResponse().getCookie("cc_access_token")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_INVALID"));
        assertThat(jdbc.queryForObject("SELECT \"credentialVersion\" FROM campuscore_auth.\"User\" WHERE \"id\"='second-user'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT \"consumedAt\" FROM campuscore_auth.\"TwoFactorChallenge\" WHERE \"id\"='admin-reset-pending-login'",
                Object.class)).isNotNull();
        MvcResult fresh = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"second@campuscore.edu\",\"password\":\"" + issued + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.mustChangePassword").value(true)).andReturn();
        String currentAccess = objectMapper.readTree(fresh.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + currentAccess)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/auth/profile").header("Authorization", "Bearer " + currentAccess)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"0900000000\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void passwordResetClearsTheLoginLockoutSoTheIssuedCredentialWorks() throws Exception {
        // An admin-issued credential must be usable even while the account is
        // locked out from failed logins — otherwise the reset hands back a
        // password that still says "Invalid credentials" until the lock ages.
        jdbc.update("UPDATE \"campuscore_auth\".\"User\""
                + " SET \"lockedUntil\" = CURRENT_TIMESTAMP + INTERVAL '1' HOUR,"
                + " \"failedLoginAttempts\" = 5 WHERE \"id\" = 'second-user'");

        mvc.perform(post("/api/v1/users/second-user/password-reset")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty());

        assertThat(jdbc.queryForObject(
                "SELECT \"lockedUntil\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                Object.class))
                .isNull();
        assertThat(jdbc.queryForObject(
                "SELECT \"failedLoginAttempts\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT \"mustChangePassword\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                Boolean.class))
                .isTrue();
    }

    @Test
    void createFacultyHeadAssignsTheLecturerDeanPairAndOfficialProfile() throws Exception {
        // V89 contract: TRUONG_KHOA is a job title on a lecturer account, so the
        // issuance must land the LECTURER+TRUONG_KHOA pair and the official
        // lecturer profile in one account — not a dean without a profile.
        MvcResult result = mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"issued.dean@campuscore.edu",
                                  "firstName":"Issued",
                                  "lastName":"Dean",
                                  "role":"TRUONG_KHOA",
                                  "employeeId":"GV-DEAN-001",
                                  "departmentId":"department-demo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("LECTURER", "TRUONG_KHOA")))
                .andExpect(jsonPath("$.hasLecturerProfile").value(true))
                .andExpect(jsonPath("$.hasStudentProfile").value(false))
                .andReturn();

        String userId = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        assertThat(roleNames(userId)).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
        assertThat(jdbc.queryForObject(
                "SELECT \"employeeId\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = ?",
                String.class, userId))
                .isEqualTo("GV-DEAN-001");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = ?",
                Integer.class, userId))
                .isZero();
    }

    @Test
    void createFacultyHeadRequiresTheSameOfficialFieldsAsALecturer() throws Exception {
        // No invented or defaulted official identifiers: the dean profile is the
        // lecturer profile, so the same employeeId/departmentId requirements hold.
        mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dean.nofields@campuscore.edu","firstName":"No","lastName":"Fields","role":"TRUONG_KHOA"}
                                """))
                .andExpect(status().isBadRequest());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"email\" = 'dean.nofields@campuscore.edu'",
                Integer.class))
                .isZero();
    }

    @Test
    void createFacultyHeadRejectsDuplicateEmployeeIdAndInactiveDepartment() throws Exception {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('taken-lecturer', 'third-user', 'department-demo', 'GV-TAKEN', TRUE)");

        mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dean.dup@campuscore.edu","firstName":"Dup","lastName":"Dean","role":"TRUONG_KHOA","employeeId":"GV-TAKEN","departmentId":"department-demo"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_ID_EXISTS"));

        mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"dean.inactive@campuscore.edu","firstName":"Inact","lastName":"Dean","role":"TRUONG_KHOA","employeeId":"GV-DEAN-9","departmentId":"department-inactive"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void promotingLecturerToFacultyHeadKeepsTheSameOfficialProfile() throws Exception {
        insertUser("lect-user", "lect@campuscore.edu", "Lect", "User");
        insertUserRole("lect-user-role", "lect-user", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('lect-keep-profile', 'lect-user', 'department-demo', 'GV-KEEP', TRUE)");

        mvc.perform(put("/api/v1/users/lect-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("LECTURER", "TRUONG_KHOA")));

        assertThat(roleNames("lect-user")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
        // The promotion must not mint a second profile or rewrite the official
        // identifiers: same row id, same employeeId and department.
        assertThat(jdbc.queryForList(
                "SELECT \"id\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = 'lect-user'",
                String.class))
                .containsExactly("lect-keep-profile");
        assertThat(jdbc.queryForObject(
                "SELECT \"employeeId\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = 'lect-user'",
                String.class))
                .isEqualTo("GV-KEEP");
    }

    @Test
    void onlyASuperAdministratorAppointsAFacultyOrDepartmentHead() throws Exception {
        insertRole("role-truong-bo-mon", "TRUONG_BO_MON", true);
        insertLecturer("appoint-user", "appoint@campuscore.edu", "GV-APPOINT", "department-demo", false);

        for (String head : List.of("TRUONG_KHOA", "TRUONG_BO_MON")) {
            mvc.perform(put("/api/v1/users/appoint-user")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"role\":\"" + head + "\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
        }
        assertThat(roleNames("appoint-user")).containsExactly("LECTURER");

        mvc.perform(put("/api/v1/users/appoint-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.containsInAnyOrder("LECTURER", "TRUONG_KHOA")));

        assertThat(roleNames("appoint-user")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
    }

    @Test
    void secondFacultyHeadInTheSameFacultyIsRejected() throws Exception {
        // The incumbent sits in a sibling department: the rule is per faculty, not
        // per department.
        insertLecturer("incumbent-dean", "incumbent@campuscore.edu", "GV-INCUMBENT", "department-sibling", true);
        insertLecturer("candidate-user", "candidate@campuscore.edu", "GV-CANDIDATE", "department-demo", false);

        mvc.perform(put("/api/v1/users/candidate-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAN_ALREADY_ASSIGNED"));

        // The refusal lands before any write: the candidate keeps exactly the
        // roles it had and the incumbent keeps the title.
        assertThat(roleNames("candidate-user")).containsExactly("LECTURER");
        assertThat(roleNames("incumbent-dean")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
    }

    @Test
    void facultyHeadOfAnotherFacultyOrAnInactiveIncumbentDoesNotBlockTheAppointment() throws Exception {
        insertLecturer("other-faculty-dean", "otherdean@campuscore.edu", "GV-OTHERDEAN", "department-other", true);
        insertLecturer("suspended-dean", "suspendeddean@campuscore.edu", "GV-SUSPDEAN", "department-sibling", true);
        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"status\" = 'SUSPENDED' WHERE \"id\" = 'suspended-dean'");
        insertLecturer("free-candidate", "freecandidate@campuscore.edu", "GV-FREE", "department-demo", false);

        mvc.perform(put("/api/v1/users/free-candidate")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\"}"))
                .andExpect(status().isOk());

        assertThat(roleNames("free-candidate")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
    }

    @Test
    void creatingASecondFacultyHeadInTheSameFacultyIsRejectedBeforeAnyAccountExists() throws Exception {
        insertLecturer("create-incumbent", "createincumbent@campuscore.edu", "GV-CINC", "department-demo", true);

        mvc.perform(post("/api/v1/users")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"second.dean@campuscore.edu","firstName":"Second","lastName":"Dean","role":"TRUONG_KHOA","employeeId":"GV-SECOND","departmentId":"department-sibling"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEAN_ALREADY_ASSIGNED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"email\" = 'second.dean@campuscore.edu'",
                Integer.class))
                .isZero();
    }

    @Test
    void demotingDeanBackToLecturerRevokesSessionsAndKeepsTheProfile() throws Exception {
        insertUser("dean-user", "dean@campuscore.edu", "Dean", "User");
        insertUserRole("dean-user-tk", "dean-user", "role-truong-khoa");
        insertUserRole("dean-user-lect", "dean-user", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('dean-keep-profile', 'dean-user', 'department-demo', 'GV-DEAN', TRUE)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('dean-session', 'dean-user', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        // The account already holds LECTURER, but the dean pair is genuinely
        // changing, so refresh sessions must still be cut.
        mvc.perform(put("/api/v1/users/dean-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"LECTURER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.contains("LECTURER")));

        assertThat(roleNames("dean-user")).containsExactly("LECTURER");
        assertThat(jdbc.queryForList(
                "SELECT \"id\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = 'dean-user'",
                String.class))
                .containsExactly("dean-keep-profile");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'dean-user'",
                Integer.class))
                .isZero();
    }

    @Test
    void ordinaryAdminEchoingTheUnchangedDeanRoleLeavesTheProfileUntouched() throws Exception {
        insertUser("dean-echo", "dean.echo@campuscore.edu", "Dean", "Echo");
        insertUserRole("dean-echo-tk", "dean-echo", "role-truong-khoa");
        insertUserRole("dean-echo-lect", "dean-echo", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('dean-echo-profile', 'dean-echo', 'department-demo', 'GV-ECHO', TRUE)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('dean-echo-session', 'dean-echo', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        // Per AdminRoleCeilingTest the unchanged-role echo is allowed so other
        // fields stay savable; it must not destroy the lecturer profile or cut
        // sessions the way a real role change does.
        mvc.perform(put("/api/v1/users/dean-echo")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\",\"firstName\":\"Renamed\"}"))
                .andExpect(status().isOk());

        assertThat(roleNames("dean-echo")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
        assertThat(jdbc.queryForList(
                "SELECT \"id\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = 'dean-echo'",
                String.class))
                .containsExactly("dean-echo-profile");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'dean-echo'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT \"firstName\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'dean-echo'",
                String.class))
                .isEqualTo("Renamed");
    }

    @Test
    void typedUpdateCreatesTheTargetProfileOnRoleConversion() throws Exception {
        insertUser("convert-student", "convert.student@campuscore.edu", "Convert", "Student");
        insertUserRole("convert-student-role", "convert-student", "role-admin");
        mvc.perform(put("/api/v1/users/convert-student")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"STUDENT","studentId":"SV-CONV-1","curriculumId":"curriculum-demo","year":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasStudentProfile").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT \"studentId\" FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = 'convert-student'",
                String.class))
                .isEqualTo("SV-CONV-1");
        assertThat(roleNames("convert-student")).containsExactly("STUDENT");

        insertUser("convert-lecturer", "convert.lecturer@campuscore.edu", "Convert", "Lecturer");
        insertUserRole("convert-lecturer-role", "convert-lecturer", "role-admin");
        mvc.perform(put("/api/v1/users/convert-lecturer")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LECTURER","employeeId":"GV-CONV-1","departmentId":"department-demo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasLecturerProfile").value(true));
        assertThat(jdbc.queryForObject(
                "SELECT \"employeeId\" FROM \"campuscore_auth\".\"Lecturer\" WHERE \"userId\" = 'convert-lecturer'",
                String.class))
                .isEqualTo("GV-CONV-1");
        assertThat(roleNames("convert-lecturer")).containsExactly("LECTURER");
    }

    @Test
    void roleConversionIsBlockedWhenTheObsoleteProfileCarriesHistory() throws Exception {
        insertUser("history-student", "history.student@campuscore.edu", "History", "Student");
        insertUserRole("history-student-role", "history-student", "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('history-student-profile', 'history-student', 'SV-HIST-1', 'curriculum-demo',"
                + " 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO academic.conduct_semester_score (id, student_id)"
                + " VALUES ('conduct-row-1', 'history-student-profile')");

        // The student profile owns a conduct record. Deleting it would erase
        // that history, so the conversion must refuse and leave everything —
        // role, profile, and conduct row — exactly as it was.
        mvc.perform(put("/api/v1/users/history-student")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LECTURER","employeeId":"GV-HIST-1","departmentId":"department-demo"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_HAS_HISTORY"));
        assertThat(roleNames("history-student")).containsExactly("STUDENT");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"id\" = 'history-student-profile'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.conduct_semester_score WHERE student_id = 'history-student-profile'",
                Integer.class))
                .isEqualTo(1);

        // A profile with no history converts normally.
        mvc.perform(put("/api/v1/users/history-student")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_HAS_HISTORY"));

        insertUser("clean-student", "clean.student@campuscore.edu", "Clean", "Student");
        insertUserRole("clean-student-role", "clean-student", "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('clean-student-profile', 'clean-student', 'SV-CLEAN-1', 'curriculum-demo',"
                + " 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        mvc.perform(put("/api/v1/users/clean-student")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"LECTURER","employeeId":"GV-CLEAN-1","departmentId":"department-demo"}
                                """))
                .andExpect(status().isOk());
        assertThat(roleNames("clean-student")).containsExactly("LECTURER");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"id\" = 'clean-student-profile'",
                Integer.class))
                .isEqualTo(0);
    }

    @Test
    void missingOrDuplicateProfileFieldsAbortTheWholeConversion() throws Exception {
        insertUser("abort-user", "abort@campuscore.edu", "Abort", "User");
        insertUserRole("abort-user-role", "abort-user", "role-admin");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('abort-session', 'abort-user', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        // Missing profile fields: nothing may be written — not the name, not
        // the role, and the sessions stay.
        mvc.perform(put("/api/v1/users/abort-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"firstName\":\"ShouldNotLand\"}"))
                .andExpect(status().isBadRequest());
        assertThat(roleNames("abort-user")).containsExactly("ADMIN");
        assertThat(jdbc.queryForObject(
                "SELECT \"firstName\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'abort-user'",
                String.class))
                .isEqualTo("Abort");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'abort-user'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = 'abort-user'",
                Integer.class))
                .isZero();

        // A duplicate official id conflicts before any write the same way.
        mvc.perform(put("/api/v1/users/abort-user")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"STUDENT","studentId":"SV-TARGET","curriculumId":"curriculum-demo","year":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ID_EXISTS"));
        assertThat(roleNames("abort-user")).containsExactly("ADMIN");
    }

    @Test
    void resendingTheSameRoleDoesNotRewriteTheExistingProfile() throws Exception {
        insertUser("same-user", "same@campuscore.edu", "Same", "User");
        insertUserRole("same-user-role", "same-user", "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('same-student-profile', 'same-user', 'SV-SAME', 'curriculum-demo',"
                + " 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // Profile fields on an ordinary same-role edit must not reassign the
        // official profile: the row id and the recorded identifiers stay.
        mvc.perform(put("/api/v1/users/same-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"STUDENT","firstName":"Renamed","studentId":"SV-OTHER","curriculumId":"curriculum-inactive","year":4}
                                """))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForList(
                "SELECT \"id\" FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = 'same-user'",
                String.class))
                .containsExactly("same-student-profile");
        assertThat(jdbc.queryForObject(
                "SELECT \"studentId\" FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = 'same-user'",
                String.class))
                .isEqualTo("SV-SAME");
        assertThat(jdbc.queryForObject(
                "SELECT \"year\" FROM \"campuscore_auth\".\"Student\" WHERE \"userId\" = 'same-user'",
                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void studentYearMustBeAPositiveInteger() throws Exception {
        for (String badYear : List.of("0", "-1", "abc", "1.5")) {
            mvc.perform(post("/api/v1/users")
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"bad.year@campuscore.edu","firstName":"Bad","lastName":"Year","role":"STUDENT","studentId":"SV-BAD-YEAR","curriculumId":"curriculum-demo","year":%s}
                                    """.formatted("abc".equals(badYear) || "1.5".equals(badYear) ? "\"" + badYear + "\"" : badYear)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"email\" = 'bad.year@campuscore.edu'",
                Integer.class))
                .isZero();
    }

    @Test
    void newProfilesRejectInactiveOrMissingAcademicReferences() throws Exception {
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inactive.cur@campuscore.edu","firstName":"Inact","lastName":"Cur","role":"STUDENT","studentId":"SV-INACT-C","curriculumId":"curriculum-inactive","year":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing.cur@campuscore.edu","firstName":"Miss","lastName":"Cur","role":"STUDENT","studentId":"SV-MISS-C","curriculumId":"curriculum-missing","year":1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/users")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing.dept@campuscore.edu","firstName":"Miss","lastName":"Dept","role":"LECTURER","employeeId":"GV-MISS-D","departmentId":"department-missing"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"email\" LIKE '%.cur@campuscore.edu' OR \"email\" LIKE '%.dept@campuscore.edu'",
                Integer.class))
                .isZero();
    }

    @Test
    void statusEditsNormalizeCaseBlankKeepsCurrentAndCutsSessions() throws Exception {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('second-session', 'second-user', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        mvc.perform(put("/api/v1/users/second-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"suspended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                String.class))
                .isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'second-user'",
                Integer.class))
                .isZero();

        mvc.perform(put("/api/v1/users/second-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                String.class))
                .isEqualTo("ACTIVE");

        // A blank status means "not provided", never a stored blank.
        mvc.perform(put("/api/v1/users/second-user")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"  \",\"firstName\":\"Renamed\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'second-user'",
                String.class))
                .isEqualTo("ACTIVE");
    }

    @Test
    void listAndDetailExposeStudentAndLecturerProfilePresence() throws Exception {
        insertUserRole("second-user-student", "second-user", "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('second-student-profile', 'second-user', 'SV-SECOND', 'curriculum-demo',"
                + " 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // target-user carries both fixture profiles; second-user carries a
        // student profile only; third-user carries none.
        mvc.perform(get("/api/v1/users")
                        .queryParam("limit", "20")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 'target-user')].hasStudentProfile").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id == 'target-user')].hasLecturerProfile").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id == 'second-user')].hasStudentProfile").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id == 'second-user')].hasLecturerProfile").value(org.hamcrest.Matchers.contains(false)))
                .andExpect(jsonPath("$.data[?(@.id == 'third-user')].hasStudentProfile").value(org.hamcrest.Matchers.contains(false)));

        // Detail answers the same flags; the protected target needs the
        // super-admin actor, so read an unprotected account there.
        mvc.perform(get("/api/v1/users/second-user").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasStudentProfile").value(true))
                .andExpect(jsonPath("$.hasLecturerProfile").value(false));

        // Multi-word full name search across lastName + firstName and official
        // student/employee identifiers resolves the matching user account.
        insertUser("khoa-lecturer", "lecturer@campuscore.edu", "Khoa", "ThS. Hoàng Thái Xuân");
        insertUserRole("khoa-lecturer-role", "khoa-lecturer", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('khoa-lecturer-profile', 'khoa-lecturer', 'department-demo', 'LEC-DEMO-001', TRUE)");

        mvc.perform(get("/api/v1/users")
                        .queryParam("role", "LECTURER")
                        .queryParam("search", "Hoàng Thái Xuân Khoa")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("khoa-lecturer"))
                .andExpect(jsonPath("$.meta.total").value(1));

        mvc.perform(get("/api/v1/users")
                        .queryParam("role", "LECTURER")
                        .queryParam("search", "hoang thai xuan khoa")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("khoa-lecturer"));

        mvc.perform(get("/api/v1/users")
                        .queryParam("role", "LECTURER")
                        .queryParam("search", "LEC-DEMO-001")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("khoa-lecturer"));
    }

    @ParameterizedTest(name = "delete keeps a student whose only history is {0}")
    @MethodSource("studentOnlyHistoryInserts")
    void deleteKeepsStudentAccountsWhoseProfileCarriesAnyHistory(String label, String historyInsert)
            throws Exception {
        insertUser("del-" + label, "del." + label + "@campuscore.edu", "Del", label);
        insertUserRole("del-" + label + "-role", "del-" + label, "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('del-" + label + "-profile', 'del-" + label + "', 'SV-DEL-" + label + "',"
                + " 'curriculum-demo', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('del-" + label + "-session', 'del-" + label + "', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");
        jdbc.update(historyInsert.formatted("del-" + label + "-profile"));

        mvc.perform(delete("/api/v1/users/del-" + label).with(superJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_HAS_HISTORY"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'del-" + label + "'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"id\" = 'del-" + label + "-profile'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'del-" + label + "'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = 'del-" + label + "'",
                Integer.class)).isZero();
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> studentOnlyHistoryInserts() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("attendance",
                        "INSERT INTO academic.\"Attendance\" (\"id\", \"studentId\") VALUES ('del-attendance-row', '%s')"),
                org.junit.jupiter.params.provider.Arguments.of("conduct",
                        "INSERT INTO academic.conduct_semester_score (id, student_id) VALUES ('del-conduct-row', '%s')"),
                org.junit.jupiter.params.provider.Arguments.of("credit",
                        "INSERT INTO academic.\"CreditLimitApplication\" (\"id\", \"studentId\") VALUES ('del-credit-row', '%s')"));
    }

    @Test
    void deleteRemovesACleanProfiledAccount() throws Exception {
        insertUser("del-clean", "del.clean@campuscore.edu", "Del", "Clean");
        insertUserRole("del-clean-role", "del-clean", "role-student");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                + " VALUES ('del-clean-profile', 'del-clean', 'SV-DEL-CLEAN', 'curriculum-demo',"
                + " 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('del-clean-session', 'del-clean', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");

        mvc.perform(delete("/api/v1/users/del-clean").with(superJwt()))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'del-clean'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Student\" WHERE \"id\" = 'del-clean-profile'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'del-clean'",
                Integer.class)).isZero();
    }

    @ParameterizedTest(name = "conversion keeps a {0} profile referenced by {1}")
    @MethodSource("newlyGuardedHistoryReferences")
    void roleConversionIsBlockedByEveryNewlyGuardedReference(
            String profileKind, String label, String historyInsert, String conversionPayload) throws Exception {
        insertUser("ref-" + label, "ref." + label + "@campuscore.edu", "Ref", label);
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Session\""
                + " (\"id\", \"userId\", \"refreshToken\", \"expiresAt\", \"createdAt\")"
                + " VALUES ('ref-" + label + "-session', 'ref-" + label + "', 'hashed',"
                + " CURRENT_TIMESTAMP + INTERVAL '1' HOUR, CURRENT_TIMESTAMP)");
        String profileTable;
        if ("student".equals(profileKind)) {
            insertUserRole("ref-" + label + "-role", "ref-" + label, "role-student");
            jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                    + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                    + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                    + " VALUES ('ref-" + label + "-profile', 'ref-" + label + "', 'SV-REF-" + label + "',"
                    + " 'curriculum-demo', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            profileTable = "\"campuscore_auth\".\"Student\"";
        } else {
            insertUserRole("ref-" + label + "-role", "ref-" + label, "role-lecturer");
            jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                    + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                    + " VALUES ('ref-" + label + "-profile', 'ref-" + label + "', 'department-demo',"
                    + " 'GV-REF-" + label + "', TRUE)");
            profileTable = "\"campuscore_auth\".\"Lecturer\"";
        }
        jdbc.update(historyInsert.formatted("ref-" + label + "-profile"));

        mvc.perform(put("/api/v1/users/ref-" + label)
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversionPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROFILE_HAS_HISTORY"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + profileTable + " WHERE \"id\" = 'ref-" + label + "-profile'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"campuscore_auth\".\"Session\" WHERE \"userId\" = 'ref-" + label + "'",
                Integer.class)).isEqualTo(1);
        assertThat(roleNames("ref-" + label))
                .containsExactly("student".equals(profileKind) ? "STUDENT" : "LECTURER");
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> newlyGuardedHistoryReferences() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("student", "enevt",
                        "INSERT INTO academic.\"EnrollmentEvent\" (\"id\", \"studentId\") VALUES ('ref-enevt-row', '%s')",
                        "{\"role\":\"LECTURER\",\"employeeId\":\"GV-CONV-ENEVT\",\"departmentId\":\"department-demo\"}"),
                org.junit.jupiter.params.provider.Arguments.of("student", "report",
                        "INSERT INTO thesis.thesis_group_report (id, submitted_by) VALUES ('ref-report-row', '%s')",
                        "{\"role\":\"LECTURER\",\"employeeId\":\"GV-CONV-REPORT\",\"departmentId\":\"department-demo\"}"),
                org.junit.jupiter.params.provider.Arguments.of("lecturer", "score",
                        "INSERT INTO thesis.thesis_topic_score (id, lecturer_id) VALUES ('ref-score-row', '%s')",
                        "{\"role\":\"STUDENT\",\"studentId\":\"SV-CONV-SCORE\",\"curriculumId\":\"curriculum-demo\",\"year\":1}"),
                org.junit.jupiter.params.provider.Arguments.of("lecturer", "gvpb",
                        "INSERT INTO thesis.thesis_topic (id, gvpb_lecturer_id) VALUES ('ref-gvpb-row', '%s')",
                        "{\"role\":\"STUDENT\",\"studentId\":\"SV-CONV-GVPB\",\"curriculumId\":\"curriculum-demo\",\"year\":1}"),
                org.junit.jupiter.params.provider.Arguments.of("lecturer", "announce",
                        "INSERT INTO engagement.\"Announcement\" (\"id\", \"lecturerId\") VALUES ('ref-announce-row', '%s')",
                        "{\"role\":\"STUDENT\",\"studentId\":\"SV-CONV-ANN\",\"curriculumId\":\"curriculum-demo\",\"year\":1}"));
    }

    @Test
    void ordinaryAdminCannotChangeAProtectedAccountsStatusButSuperAdminCan() throws Exception {
        insertUser("dean-suspended", "dean.suspended@campuscore.edu", "Dean", "Suspended");
        insertUserRole("dean-suspended-tk", "dean-suspended", "role-truong-khoa");
        insertUserRole("dean-suspended-lect", "dean-suspended", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('dean-suspended-profile', 'dean-suspended', 'department-demo', 'GV-SUSP', TRUE)");
        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"status\" = 'SUSPENDED' WHERE \"id\" = 'dean-suspended'");

        // The role echo is the unchanged dean role, but reactivating the
        // account puts the reserved role back into service — a plain
        // administrator may not do that, and nothing may be written.
        mvc.perform(put("/api/v1/users/dean-suspended")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TRUONG_KHOA\",\"status\":\"ACTIVE\",\"firstName\":\"ShouldNotLand\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
        assertThat(jdbc.queryForObject(
                "SELECT \"status\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'dean-suspended'",
                String.class)).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject(
                "SELECT \"firstName\" FROM \"campuscore_auth\".\"User\" WHERE \"id\" = 'dean-suspended'",
                String.class)).isEqualTo("Dean");

        mvc.perform(put("/api/v1/users/dean-suspended")
                        .with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void selfEchoingOnlyTheLecturerHalfOfTheDeanPairIsRejected() throws Exception {
        insertUser("dean-self", "dean.self@campuscore.edu", "Dean", "Self");
        insertUserRole("dean-self-tk", "dean-self", "role-truong-khoa");
        insertUserRole("dean-self-lect", "dean-self", "role-lecturer");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES ('dean-self-profile', 'dean-self', 'department-demo', 'GV-SELF', TRUE)");

        // The account already holds LECTURER, so the old hasRole check let this
        // through and quietly dropped TRUONG_KHOA. The desired-set comparison
        // refuses it; only the whole unchanged pair may be echoed back.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                userMutations.update("dean-self", Map.of("role", "LECTURER"), true, "dean-self")))
                .isInstanceOf(io.campuscore.restfulapi.exception.DomainException.class)
                .extracting("code").isEqualTo("SELF_ROLE_CHANGE_NOT_ALLOWED");
        assertThat(roleNames("dean-self")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");

        userMutations.update("dean-self", Map.of("role", "TRUONG_KHOA"), true, "dean-self");
        assertThat(roleNames("dean-self")).containsExactlyInAnyOrder("LECTURER", "TRUONG_KHOA");
    }

    static java.util.stream.Stream<List<String>> unchangedProfileRoles() {
        return java.util.stream.Stream.of(List.of("STUDENT"), List.of("LECTURER"),
                List.of("LECTURER", "TRUONG_BO_MON"), List.of("LECTURER", "TRUONG_KHOA"), List.of("TRUONG_BO_MON"));
    }

    @ParameterizedTest
    @MethodSource("unchangedProfileRoles")
    void omittedRoleCompletesMissingProfileWithoutReplacingRolesOrSessions(List<String> roles) throws Exception {
        insertProfileGap(roles);
        boolean student = roles.contains("STUDENT");
        if (student) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"employeeId\",\"departmentId\",\"isActive\")"
                    + " VALUES ('gap-counterpart','profile-gap','GV-COUNTERPART','department-demo',TRUE)");
        } else {
            jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"status\",\"admissionDate\",\"createdAt\",\"updatedAt\")"
                    + " VALUES ('gap-counterpart','profile-gap','SV-COUNTERPART','curriculum-demo',2,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        }
        String counterpartTable = student ? "Lecturer" : "Student";
        Map<String, Object> counterpartBefore = jdbc.queryForMap("SELECT * FROM campuscore_auth.\"" + counterpartTable
                + "\" WHERE \"id\"='gap-counterpart'");
        Map<String, Object> fields = student
                ? Map.of("firstName", "Completed", "studentId", "SV-GAP", "curriculumId", "curriculum-demo", "year", 3)
                : Map.of("firstName", "Completed", "employeeId", "GV-GAP", "departmentId", "department-demo");
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(fields)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(student ? "$.hasStudentProfile" : "$.hasLecturerProfile").value(true));
        assertThat(roleNames("profile-gap")).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(roles.stream(), java.util.stream.Stream.of("AUDITOR")).toList());
        String table = student ? "Student" : "Lecturer";
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_auth.\"" + table
                + "\" WHERE \"userId\"='profile-gap'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"" + (student ? "studentId" : "employeeId") + "\" FROM campuscore_auth.\"" + table
                + "\" WHERE \"userId\"='profile-gap'", String.class)).isEqualTo(student ? "SV-GAP" : "GV-GAP");
        assertThat(jdbc.queryForObject("SELECT \"" + (student ? "curriculumId" : "departmentId") + "\" FROM campuscore_auth.\"" + table
                + "\" WHERE \"userId\"='profile-gap'", String.class)).isEqualTo(student ? "curriculum-demo" : "department-demo");
        if (student) assertThat(jdbc.queryForObject("SELECT \"year\" FROM campuscore_auth.\"Student\" WHERE \"userId\"='profile-gap'", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForMap("SELECT * FROM campuscore_auth.\"" + counterpartTable
                + "\" WHERE \"id\"='gap-counterpart'")).isEqualTo(counterpartBefore);
        String createdId = jdbc.queryForObject("SELECT \"id\" FROM campuscore_auth.\"" + table + "\" WHERE \"userId\"='profile-gap'", String.class);
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(fields)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT \"id\" FROM campuscore_auth.\"" + table + "\" WHERE \"userId\"='profile-gap'", String.class)).containsExactly(createdId);
        assertProfileGapSessionAndName("Completed");
    }

    static java.util.stream.Stream<String> invalidUnchangedProfileInputs() {
        return java.util.stream.Stream.of("student-partial", "lecturer-partial", "student-duplicate",
                "lecturer-duplicate", "student-inactive", "lecturer-inactive", "student-year");
    }

    @ParameterizedTest
    @MethodSource("invalidUnchangedProfileInputs")
    void omittedRoleRejectsInvalidProfileAndRollsBackTheName(String scenario) throws Exception {
        boolean student = scenario.startsWith("student");
        List<String> roles = student ? List.of("STUDENT") : List.of("LECTURER", "TRUONG_BO_MON");
        insertProfileGap(roles);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        fields.put("firstName", "Must roll back");
        fields.put(student ? "studentId" : "employeeId", scenario.endsWith("duplicate")
                ? student ? "SV-TARGET" : "GV-TARGET" : student ? "SV-GAP" : "GV-GAP");
        if (!scenario.endsWith("partial")) {
            fields.put(student ? "curriculumId" : "departmentId", scenario.endsWith("inactive")
                    ? student ? "curriculum-inactive" : "department-inactive"
                    : student ? "curriculum-demo" : "department-demo");
        }
        if (student) fields.put("year", scenario.endsWith("year") ? 0 : 2);
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(fields)))
                .andExpect(status().is(scenario.endsWith("duplicate") ? 409 : 400));
        assertThat(roleNames("profile-gap")).containsExactlyInAnyOrderElementsOf(
                java.util.stream.Stream.concat(roles.stream(), java.util.stream.Stream.of("AUDITOR")).toList());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"userId\"='profile-gap'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"userId\"='profile-gap'", Integer.class)).isZero();
        assertProfileGapSessionAndName("Before");
    }

    @Test
    void omittedRoleNameOnlyDoesNotRequireOrInventMissingProfiles() throws Exception {
        insertProfileGap(List.of("LECTURER", "TRUONG_BO_MON"));
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Renamed\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasLecturerProfile").value(false));
        assertThat(roleNames("profile-gap")).containsExactlyInAnyOrder("AUDITOR", "LECTURER", "TRUONG_BO_MON");
        assertProfileGapSessionAndName("Renamed");
    }

    @Test
    void omittedRoleProfileFieldsCannotGrantAnUnheldAcademicRole() throws Exception {
        insertProfileGap(List.of());
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"studentId":"SV-GAP","curriculumId":"curriculum-demo","year":2,
                                 "employeeId":"GV-GAP","departmentId":"department-demo"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasLecturerProfile").value(false))
                .andExpect(jsonPath("$.hasStudentProfile").value(false));
        assertThat(roleNames("profile-gap")).containsExactly("AUDITOR");
        assertProfileGapSessionAndName("Before");
    }

    @Test
    void omittedRoleKeepsExistingProfileIdentifiersAndProtectedTargetCeiling() throws Exception {
        insertProfileGap(List.of("LECTURER", "TRUONG_KHOA"));
        String fields = "{\"firstName\":\"Must roll back\",\"employeeId\":\"GV-GAP\",\"departmentId\":\"department-demo\"}";
        mvc.perform(put("/api/v1/users/profile-gap").with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON).content(fields))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
        assertProfileGapSessionAndName("Before");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"employeeId\",\"departmentId\",\"isActive\")"
                + " VALUES ('gap-existing','profile-gap','GV-KEEP-GAP','department-demo',TRUE)");
        mvc.perform(put("/api/v1/users/profile-gap").with(superJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":\"GV-TARGET\",\"departmentId\":\"department-inactive\"}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForMap("SELECT \"id\",\"employeeId\",\"departmentId\" FROM campuscore_auth.\"Lecturer\" WHERE \"userId\"='profile-gap'"))
                .containsEntry("id", "gap-existing").containsEntry("employeeId", "GV-KEEP-GAP").containsEntry("departmentId", "department-demo");
        assertThat(roleNames("profile-gap")).containsExactlyInAnyOrder("AUDITOR", "LECTURER", "TRUONG_KHOA");
        assertProfileGapSessionAndName("Before");
    }

    private void insertProfileGap(List<String> roles) {
        // This legacy fixture resets accounts but retains Session rows between
        // cases; remove only this synthetic session before reusing its id.
        jdbc.update("DELETE FROM campuscore_auth.\"Session\" WHERE \"id\"='gap-session'");
        insertUser("profile-gap", "profile.gap@fixture.invalid", "Before", "Synthetic");
        insertRole("role-gap-head", "TRUONG_BO_MON", true);
        Map<String, String> ids = Map.of("STUDENT", "role-student", "LECTURER", "role-lecturer",
                "TRUONG_BO_MON", "role-gap-head", "TRUONG_KHOA", "role-truong-khoa");
        for (String role : roles) insertUserRole("gap-" + role, "profile-gap", ids.get(role));
        insertUserRole("gap-custom", "profile-gap", "role-auditor");
        jdbc.update("INSERT INTO campuscore_auth.\"Session\" (\"id\",\"userId\",\"refreshToken\",\"expiresAt\",\"createdAt\")"
                + " VALUES ('gap-session','profile-gap','hashed',CURRENT_TIMESTAMP + INTERVAL '1' HOUR,CURRENT_TIMESTAMP)");
    }

    private void assertProfileGapSessionAndName(String name) {
        assertThat(jdbc.queryForObject("SELECT \"firstName\" FROM campuscore_auth.\"User\" WHERE \"id\"='profile-gap'", String.class)).isEqualTo(name);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_auth.\"Session\" WHERE \"id\"='gap-session'", Integer.class)).isEqualTo(1);
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) UNIQUE NOT NULL,
                    "password" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "phone" VARCHAR(80),
                    "gender" VARCHAR(40),
                    "dateOfBirth" TIMESTAMP,
                    "address" VARCHAR(500),
                    "avatar" TEXT,
                    "status" VARCHAR(40) NOT NULL,
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "refreshToken" VARCHAR(200),
                    "emailVerified" BOOLEAN NOT NULL,
                    "isSuperAdmin" BOOLEAN NOT NULL,
                    "failedLoginAttempts" INTEGER NOT NULL,
                    "lockedUntil" TIMESTAMP,
                    "lastLoginAt" TIMESTAMP,
                    "passwordChangedAt" TIMESTAMP,
                    "twoFactorEnabled" BOOLEAN NOT NULL DEFAULT FALSE,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureCredentialReplacementSchema(jdbc);
        jdbc.update("DELETE FROM campuscore_auth.\"TwoFactorChallenge\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Role" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(80) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "isSystem" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Permission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(160) UNIQUE NOT NULL,
                    "description" VARCHAR(500),
                    "module" VARCHAR(80) NOT NULL,
                    "action" VARCHAR(80) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."UserRole" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL REFERENCES "campuscore_auth"."User" ("id") ON DELETE CASCADE,
                    "roleId" VARCHAR(120) NOT NULL REFERENCES "campuscore_auth"."Role" ("id") ON DELETE CASCADE,
                    CONSTRAINT admin_user_role_unique UNIQUE ("userId", "roleId")
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."RolePermission" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roleId" VARCHAR(120) NOT NULL REFERENCES "campuscore_auth"."Role" ("id") ON DELETE CASCADE,
                    "permissionId" VARCHAR(120) NOT NULL REFERENCES "campuscore_auth"."Permission" ("id") ON DELETE CASCADE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Session" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "refreshToken" VARCHAR(200) NOT NULL,
                    "previousRefreshToken" VARCHAR(200),
                    "rotatedAt" TIMESTAMP,
                    "userAgent" VARCHAR(500),
                    "ipAddress" VARCHAR(80),
                    "expiresAt" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES "campuscore_auth"."User" ("id") ON DELETE CASCADE,
                    "studentId" VARCHAR(120) UNIQUE NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) UNIQUE NOT NULL REFERENCES "campuscore_auth"."User" ("id") ON DELETE CASCADE,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) UNIQUE NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Faculty" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "facultyId" VARCHAR(120) REFERENCES academic."Faculty" ("id"),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL REFERENCES academic."Department" ("id"),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120),
                    "employeeId" VARCHAR(120),
                    "title" VARCHAR(120),
                    "specialization" VARCHAR(240),
                    "office" VARCHAR(240),
                    "phone" VARCHAR(60),
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        // History-bearing tables the profile-removal guard consults. In
        // production these hold enrollments, attendance, conduct and thesis
        // rows; here they only need the referenced column.
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Attendance\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"RegistrationSlip\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"CreditLimitApplication\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.conduct_semester_score (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.conduct_activity (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Section\" (\"id\" VARCHAR(120) PRIMARY KEY, \"lecturerId\" VARCHAR(120))");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS thesis");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group (id VARCHAR(120) PRIMARY KEY, leader_student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group_member (id VARCHAR(120) PRIMARY KEY, student_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_council_member (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic_supervisor (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"EnrollmentEvent\" (\"id\" VARCHAR(120) PRIMARY KEY, \"studentId\" VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_group_report (id VARCHAR(120) PRIMARY KEY, submitted_by VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic_score (id VARCHAR(120) PRIMARY KEY, lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS thesis.thesis_topic (id VARCHAR(120) PRIMARY KEY, gvpb_lecturer_id VARCHAR(120))");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS engagement");
        jdbc.execute("CREATE TABLE IF NOT EXISTS engagement.\"Announcement\" (\"id\" VARCHAR(120) PRIMARY KEY, \"lecturerId\" VARCHAR(120))");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "actorId" VARCHAR(120),
                    "actorLabel" VARCHAR(240),
                    "action" VARCHAR(48) NOT NULL,
                    "entityType" VARCHAR(48) NOT NULL,
                    "entityId" VARCHAR(120),
                    "summary" VARCHAR(500),
                    "beforeState" TEXT,
                    "afterState" TEXT,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"RolePermission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"UserRole\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Permission\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"Role\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
        jdbc.update("DELETE FROM academic.\"Lecturer\"");
        jdbc.update("DELETE FROM academic.\"Curriculum\"");
        jdbc.update("DELETE FROM academic.\"Department\"");
        jdbc.update("DELETE FROM academic.\"Faculty\"");
        jdbc.update("DELETE FROM academic.\"Enrollment\"");
        jdbc.update("DELETE FROM academic.\"Attendance\"");
        jdbc.update("DELETE FROM academic.\"RegistrationSlip\"");
        jdbc.update("DELETE FROM academic.\"CreditLimitApplication\"");
        jdbc.update("DELETE FROM academic.conduct_semester_score");
        jdbc.update("DELETE FROM academic.conduct_activity");
        jdbc.update("DELETE FROM academic.\"Section\"");
        // thesis_group_report references its group with ON DELETE RESTRICT:
        // the report row must go before the group rows it protects.
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM academic.\"EnrollmentEvent\"");
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM engagement.\"Announcement\"");
    }

    private void insertFixture() {
        insertRole("role-admin", "ADMIN", true);
        insertRole("role-super-admin", "SUPER_ADMIN", true);
        insertRole("role-student", "STUDENT", true);
        insertRole("role-lecturer", "LECTURER", true);
        insertRole("role-truong-khoa", "TRUONG_KHOA", true);
        insertRole("role-auditor", "AUDITOR", false);
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\")"
                + " VALUES ('faculty-demo', 'Demo Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\")"
                + " VALUES ('faculty-other', 'Other Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\", \"isActive\")"
                + " VALUES ('department-demo', 'Demo Department', 'faculty-demo', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\", \"isActive\")"
                + " VALUES ('department-sibling', 'Sibling Department', 'faculty-demo', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\", \"isActive\")"
                + " VALUES ('department-other', 'Other Department', 'faculty-other', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\", \"isActive\")"
                + " VALUES ('department-inactive', 'Retired Department', 'faculty-demo', FALSE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\", \"isActive\")"
                + " VALUES ('curriculum-demo', 'Demo Curriculum', 'department-demo', TRUE)");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\", \"isActive\")"
                + " VALUES ('curriculum-inactive', 'Retired Curriculum', 'department-demo', FALSE)");
        insertPermission("permission-admin", "admin", "write");
        insertPermission("permission-academic", "academic", "read");
        insertPermission("permission-audit", "audit", "read");
        insertRolePermission("role-permission-admin", "role-admin", "permission-admin");
        insertRolePermission("role-permission-student", "role-student", "permission-academic");
        insertRolePermission("role-permission-audit", "role-auditor", "permission-audit");
        insertUser("admin-user", "actor.admin@fixture.invalid", "Actor", "Administrator");
        insertUser("super-user", "actor.super@fixture.invalid", "Actor", "Superuser");
        insertUserRole("actor-admin-role", "admin-user", "role-admin");
        insertUserRole("actor-super-role", "super-user", "role-super-admin");
        insertUser("target-user", "target@campuscore.edu", "Target", "User");
        insertUser("second-user", "second@campuscore.edu", "Second", "User");
        insertUser("third-user", "third@campuscore.edu", "Third", "User");
        insertUserRole("user-role-admin", "target-user", "role-admin");
        insertUserRole("user-role-auditor", "target-user", "role-auditor");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('target-student-profile', 'target-user', 'SV-TARGET', 'curriculum-demo',"
                        + " 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES ('target-lecturer-profile', 'target-user', 'department-demo', 'GV-TARGET', TRUE)");
    }

    private void insertLecturer(String userId, String email, String employeeId, String departmentId, boolean dean) {
        insertUser(userId, email, "Lect", userId);
        insertUserRole(userId + "-lect", userId, "role-lecturer");
        if (dean) {
            insertUserRole(userId + "-tk", userId, "role-truong-khoa");
        }
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Lecturer\""
                + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                + " VALUES (?, ?, ?, ?, TRUE)",
                userId + "-profile", userId, departmentId, employeeId);
    }

    private void insertRole(String id, String name, boolean system) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Role\""
                        + " (\"id\", \"name\", \"isSystem\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, name, system);
    }

    private void insertPermission(String id, String module, String action) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"Permission\""
                        + " (\"id\", \"name\", \"module\", \"action\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                id, module + ":" + action, module, action);
    }

    private void insertRolePermission(String id, String roleId, String permissionId) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"RolePermission\" (\"id\", \"roleId\", \"permissionId\") VALUES (?, ?, ?)",
                id, roleId, permissionId);
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"User\""
                        + " (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\","
                        + " \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, 'encoded', ?, ?, 'ACTIVE', TRUE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id, email, firstName, lastName);
    }

    private void insertUserRole(String id, String userId, String roleId) {
        jdbc.update("INSERT INTO \"campuscore_auth\".\"UserRole\" (\"id\", \"userId\", \"roleId\") VALUES (?, ?, ?)",
                id, userId, roleId);
    }

    @Test
    void protectedAccountCheckSerializesWithConcurrentRolePromotion() throws Exception {
        insertUser("race-target", "race@campuscore.edu", "Race", "Target");
        insertUserRole("race-target-role", "race-target", "role-student");

        CountDownLatch gatePassed = new CountDownLatch(1);
        CountDownLatch releaseInsert = new CountDownLatch(1);
        String workerName = "race-lecturer-create";
        Mockito.doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().equals(workerName)
                    && sql.startsWith("INSERT INTO \"academic\".\"Lecturer\"")) {
                gatePassed.countDown();
                if (!releaseInsert.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release barrier timeout");
                }
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).update(Mockito.anyString(), Mockito.any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> creator = pool.submit(() -> {
                Thread.currentThread().setName(workerName);
                try {
                    lecturerMutations.create(Map.of(
                            "userId", "race-target",
                            "departmentId", "department-demo",
                            "employeeId", "GV-RACE",
                            "title", "ThS."), false);
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            assertThat(gatePassed.await(15, TimeUnit.SECONDS))
                    .as("the lecturer create must reach the insert barrier").isTrue();

            Future<Throwable> promoter = pool.submit(() -> {
                try {
                    userMutations.update("race-target", Map.of("role", "ADMIN"), true, null);
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            // The promotion must wait on the same user row lock the protected
            // check holds: if it can finish while the creator is paused between
            // its role check and its lecturer write, the check ran against
            // roles the promotion was already replacing. Give it a generous
            // window — an unlocked implementation finishes well under this.
            Thread.sleep(1_000);
            assertThat(promoter.isDone())
                    .as("the role promotion must block until the protected check commits")
                    .isFalse();

            releaseInsert.countDown();
            assertThat(creator.get(15, TimeUnit.SECONDS)).isNull();
            assertThat(promoter.get(15, TimeUnit.SECONDS)).isNull();
            assertThat(roleNames("race-target")).contains("ADMIN");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"Lecturer\" WHERE \"userId\" = 'race-target'",
                    Integer.class)).isEqualTo(1);
        } finally {
            releaseInsert.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<String> roleNames(String userId) {
        return jdbc.queryForList(
                "SELECT role.\"name\" FROM \"campuscore_auth\".\"UserRole\" user_role"
                        + " JOIN \"campuscore_auth\".\"Role\" role ON role.\"id\" = user_role.\"roleId\""
                        + " WHERE user_role.\"userId\" = ? ORDER BY role.\"name\"",
                String.class,
                userId);
    }

    private List<String> authorities(String userId) {
        return jdbc.queryForList(
                "SELECT DISTINCT CONCAT(permission.\"module\", ':', permission.\"action\")"
                        + " FROM \"campuscore_auth\".\"UserRole\" user_role"
                        + " JOIN \"campuscore_auth\".\"RolePermission\" role_permission ON role_permission.\"roleId\" = user_role.\"roleId\""
                        + " JOIN \"campuscore_auth\".\"Permission\" permission ON permission.\"id\" = role_permission.\"permissionId\""
                        + " WHERE user_role.\"userId\" = ? ORDER BY 1",
                String.class,
                userId);
    }

    private RequestPostProcessor adminJwt() {
        return io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "admin-user");
    }

    private RequestPostProcessor superJwt() {
        return io.campuscore.restfulapi.testsupport.CurrentAccountJwt.currentJwt(jdbc, "super-user");
    }
}
