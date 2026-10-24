package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.exception.response.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AccountStateFilterTest {

    private AuthUserRepository repository;
    private ObjectProvider<AuthUserRepository> objectProvider;
    private ApiErrorWriter errorWriter;
    private AccountStateFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        DatabaseAvailabilityTracker.recordSuccess();
        repository = mock(AuthUserRepository.class);
        objectProvider = mock(ObjectProvider.class);
        when(objectProvider.getIfAvailable()).thenReturn(repository);
        errorWriter = new ApiErrorWriter(new ObjectMapper());
        filter = new AccountStateFilter(objectProvider, errorWriter);
    }

    @AfterEach
    void tearDown() {
        DatabaseAvailabilityTracker.recordSuccess();
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, Collections.emptyList()));
    }

    private static AuthUserRepository.AccountAuthorization state(String status, boolean mustChangePassword) {
        return state(status, mustChangePassword, 0L);
    }

    private static AuthUserRepository.AccountAuthorization state(String status, boolean mustChangePassword, long version) {
        return new AuthUserRepository.AccountAuthorization(status, mustChangePassword, null, null, null,
                java.util.Set.of(), java.util.Set.of(), version);
    }

    @Test
    void credentialVersionMustMatchCurrentEpochAndMalformedClaimsFailClosed() throws Exception {
        Object[][] cases = {
                {null, 0L, 200}, {0, 0L, 200}, {1L, 1L, 200}, {null, 1L, 401},
                {0L, 1L, 401}, {2L, 1L, 401}, {-1L, 0L, 401},
                {"0", 0L, 401}, {0.5D, 0L, 401}, {true, 0L, 401}, {java.util.List.of(0), 0L, 401}
        };
        for (Object[] testCase : cases) {
            var token = Jwt.withTokenValue("epoch-fixture").header("alg", "HS256").subject("epoch-user")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900));
            if (testCase[0] != null) token.claim("credentialVersion", testCase[0]);
            SecurityContextHolder.getContext().setAuthentication(
                    new JwtAuthenticationToken(token.build(), Collections.emptyList()));
            when(repository.findAccountAuthorization("epoch-user"))
                    .thenReturn(Optional.of(state("ACTIVE", false, (Long) testCase[1])));
            var request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
            var response = new MockHttpServletResponse();
            var chain = mock(FilterChain.class);
            filter.doFilter(request, response, chain);
            assertEquals(testCase[2], response.getStatus(), "claim type/value must respect the persisted epoch");
            if ((Integer) testCase[2] == 401) {
                assertTrue(response.getContentAsString().contains("SESSION_INVALID"));
                verify(chain, never()).doFilter(request, response);
            } else verify(chain).doFilter(request, response);
        }
    }

    @Test
    void staleCredentialCookiesDoNotBlockLogoutCleanup() throws Exception {
        authenticate("epoch-user");
        when(repository.findAccountAuthorization("epoch-user"))
                .thenReturn(Optional.of(state("ACTIVE", false, 1L)));
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void activeUserPassesThrough() throws Exception {
        authenticate("user-1");
        when(repository.findAccountAuthorization("user-1"))
                .thenReturn(Optional.of(state("ACTIVE", false)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void inactiveUserGetsForbidden() throws Exception {
        authenticate("user-2");
        when(repository.findAccountAuthorization("user-2"))
                .thenReturn(Optional.of(state("INACTIVE", false)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCOUNT_INACTIVE"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void mustChangePasswordBlocksNormalEndpoint() throws Exception {
        authenticate("user-3");
        when(repository.findAccountAuthorization("user-3"))
                .thenReturn(Optional.of(state("ACTIVE", true)));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/grades");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("PASSWORD_CHANGE_REQUIRED"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void mustChangePasswordAllowsRotationEndpoints() throws Exception {
        authenticate("user-3");
        when(repository.findAccountAuthorization("user-3"))
                .thenReturn(Optional.of(state("ACTIVE", true)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void staleTokenOnSessionEstablishmentEndpointsPassesThrough() throws Exception {
        // A revoked session leaves the access cookie behind; login, 2FA
        // verification and account recovery must still run so the user can
        // establish a fresh session instead of being stranded on 401.
        when(repository.findAccountAuthorization("user-stale")).thenReturn(Optional.empty());
        for (String uri : new String[] {
                "/api/v1/auth/login",
                "/api/v1/auth/two-factor/verify",
                "/api/v1/auth/forgot-password",
                "/api/v1/auth/reset-password" }) {
            authenticate("user-stale");

            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            assertEquals(200, response.getStatus(), uri);
            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void databaseOutageOnProtectedAssistantReturns503() throws Exception {
        authenticate("user-4");
        when(repository.findAccountAuthorization("user-4"))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void databaseOutageOnNonDegradableEndpointReturns503() throws Exception {
        authenticate("user-5");
        when(repository.findAccountAuthorization("user-5"))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/enrollments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("DATABASE_UNAVAILABLE"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void trackerOutageShortCircuitsDegradableEndpointImmediately() throws Exception {
        authenticate("user-6");
        DatabaseAvailabilityTracker.recordFailure();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verify(chain, never()).doFilter(request, response);
        // Repository should not even be called when breaker is open
        verify(repository, never()).findAccountAuthorization("user-6");
    }

    @Test
    void trackerOutageShortCircuitsNonDegradableEndpointTo503() throws Exception {
        authenticate("user-7");
        DatabaseAvailabilityTracker.recordFailure();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/student/enrollments");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("DATABASE_UNAVAILABLE"));
        verify(chain, never()).doFilter(request, response);
        verify(repository, never()).findAccountAuthorization("user-7");
    }
}
