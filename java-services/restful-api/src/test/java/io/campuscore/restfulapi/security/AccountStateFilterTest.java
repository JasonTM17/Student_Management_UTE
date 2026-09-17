package io.campuscore.restfulapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.web.ApiErrorWriter;
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

    @Test
    void activeUserPassesThrough() throws Exception {
        authenticate("user-1");
        when(repository.findAccountState("user-1"))
                .thenReturn(Optional.of(new AuthUserRepository.AccountState("ACTIVE", false)));

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
        when(repository.findAccountState("user-2"))
                .thenReturn(Optional.of(new AuthUserRepository.AccountState("INACTIVE", false)));

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
        when(repository.findAccountState("user-3"))
                .thenReturn(Optional.of(new AuthUserRepository.AccountState("ACTIVE", true)));

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
        // /api/v1/auth/me is on the rotation allowlist so filter skips the check entirely
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void databaseOutageOnDegradableEndpointPassesThrough() throws Exception {
        authenticate("user-4");
        when(repository.findAccountState("user-4"))
                .thenThrow(new DataAccessResourceFailureException("Connection refused"));

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/assistant/chat");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        // Chain should be called so the downstream service can degrade gracefully
        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }

    @Test
    void databaseOutageOnNonDegradableEndpointReturns503() throws Exception {
        authenticate("user-5");
        when(repository.findAccountState("user-5"))
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

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
        // Repository should not even be called when breaker is open
        verify(repository, never()).findAccountState("user-6");
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
        verify(repository, never()).findAccountState("user-7");
    }
}
