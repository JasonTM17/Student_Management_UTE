package io.campuscore.restfulapi.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class RateLimitFilterTest {

    private RateLimiterService limiterService;
    private RateLimitProperties properties;
    private ApiErrorWriter errorWriter;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        limiterService = new RateLimiterService();
        properties = new RateLimitProperties(true, 5, 3, 40, 60);
        errorWriter = new ApiErrorWriter(new ObjectMapper());
        filter = new RateLimitFilter(limiterService, properties, errorWriter);
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRequestsAreNotRateLimited() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/courses");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
        assertEquals(0, limiterService.getActiveKeyCount());
    }

    @Test
    void healthChecksAreWhitelisted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/health/liveness");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(0, limiterService.getActiveKeyCount());
    }

    @Test
    void loginPostIsEnforcedUpToLimitThenReturns429() throws Exception {
        String ip = "127.0.0.1";

        // First 5 requests must pass
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr(ip);
            MockHttpServletResponse res = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(req, res, chain);

            verify(chain).doFilter(req, res);
            assertEquals("5", res.getHeader("X-RateLimit-Limit"));
            assertEquals(String.valueOf(5 - i), res.getHeader("X-RateLimit-Remaining"));
            assertNotNull(res.getHeader("X-RateLimit-Reset"));
        }

        // 6th request must receive HTTP 429
        MockHttpServletRequest reqBlocked = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        reqBlocked.setRemoteAddr(ip);
        MockHttpServletResponse resBlocked = new MockHttpServletResponse();
        FilterChain chainBlocked = mock(FilterChain.class);

        filter.doFilter(reqBlocked, resBlocked, chainBlocked);

        verify(chainBlocked, never()).doFilter(reqBlocked, resBlocked);
        assertEquals(429, resBlocked.getStatus());
        assertEquals("5", resBlocked.getHeader("X-RateLimit-Limit"));
        assertEquals("0", resBlocked.getHeader("X-RateLimit-Remaining"));
        assertNotNull(resBlocked.getHeader("Retry-After"));
        assertTrue(resBlocked.getContentAsString().contains("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    void authenticatedUserEnrollmentRateLimitUsesUserPrincipal() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student-123", "n/a", List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/academic/me/enrollments");
        req.setRemoteAddr("10.0.0.99");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals("15", res.getHeader("X-RateLimit-Limit"));
        assertEquals("14", res.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void xForwardedForHeaderExtractsFirstClientIp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.addHeader("X-Forwarded-For", "203.0.113.195, 70.41.3.18, 150.172.238.178");
        req.setRemoteAddr("172.18.0.5"); // proxy/docker internal address
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals("5", res.getHeader("X-RateLimit-Limit"));
        assertEquals("4", res.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void policiesMapCorrectLimitsForThesisAndAdminMutations() throws Exception {
        // Thesis limit is 20
        MockHttpServletRequest thesisReq = new MockHttpServletRequest("POST", "/api/v1/thesis/topics");
        thesisReq.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse thesisRes = new MockHttpServletResponse();
        filter.doFilter(thesisReq, thesisRes, mock(FilterChain.class));
        assertEquals("20", thesisRes.getHeader("X-RateLimit-Limit"));

        // Admin limit is 30
        MockHttpServletRequest adminReq = new MockHttpServletRequest("POST", "/api/v1/admin/departments");
        adminReq.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse adminRes = new MockHttpServletResponse();
        filter.doFilter(adminReq, adminRes, mock(FilterChain.class));
        assertEquals("30", adminRes.getHeader("X-RateLimit-Limit"));
    }
}
