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
        // trustProxyHeaders=false keeps the default socket-address client key,
        // matching direct (non-proxied) request handling in these tests.
        properties = new RateLimitProperties(true, false, false, 5, 40, 60);
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
    void proxiedRequestsKeepTheSocketAddressWhenHeaderTrustIsOff() throws Exception {
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

    /**
     * Audit S1: behind a trusted proxy the RIGHTMOST X-Forwarded-For element is
     * the one the proxy appended; earlier entries are client-supplied and
     * spoofable (Render appends the real client IP last).
     */
    @Test
    void trustedProxyUsesTheRightmostForwardedForEntry() {
        RateLimitProperties proxyTrusted = new RateLimitProperties(true, true, false, 5, 40, 60);
        RateLimitFilter proxyFilter = new RateLimitFilter(limiterService, proxyTrusted, errorWriter);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("203.0.113.9", proxyFilter.resolveClientIp(request));
    }

    /** Audit S1: a spoofed X-Real-IP is ignored unless explicitly opted in. */
    @Test
    void realIpIsIgnoredUnlessExplicitlyTrusted() {
        RateLimitProperties proxyTrusted = new RateLimitProperties(true, true, false, 5, 40, 60);
        RateLimitFilter proxyFilter = new RateLimitFilter(limiterService, proxyTrusted, errorWriter);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Real-IP", "6.6.6.6");
        request.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertEquals("203.0.113.9", proxyFilter.resolveClientIp(request));

        RateLimitProperties realIpTrusted = new RateLimitProperties(true, true, true, 5, 40, 60);
        assertEquals("6.6.6.6",
                new RateLimitFilter(limiterService, realIpTrusted, errorWriter).resolveClientIp(request));
    }

    /** Audit S1: spoofed first entries must not open extra IP buckets. */
    @Test
    void spoofedForwardedPrefixesShareOneBucketWhileDistinctRealIpsDoNot() throws Exception {
        RateLimitProperties proxyTrusted = new RateLimitProperties(true, true, false, 5, 40, 60);
        RateLimitFilter proxyFilter = new RateLimitFilter(limiterService, proxyTrusted, errorWriter);

        for (int i = 1; i <= 3; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.addHeader("X-Forwarded-For", "spoofed-" + i + ", 203.0.113.9");
            request.setRemoteAddr("10.0.0.1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            proxyFilter.doFilter(request, response, mock(FilterChain.class));
            assertEquals(String.valueOf(5 - i), response.getHeader("X-RateLimit-Remaining"));
        }
        // Three spoofed variants of one real client = exactly one bucket.
        assertEquals(1, limiterService.getActiveKeyCount());

        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        other.addHeader("X-Forwarded-For", "spoofed-1, 198.51.100.7");
        other.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        proxyFilter.doFilter(other, otherResponse, mock(FilterChain.class));
        // A different real (rightmost) client gets a fresh bucket of 5.
        assertEquals("4", otherResponse.getHeader("X-RateLimit-Remaining"));
        assertEquals(2, limiterService.getActiveKeyCount());
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

        // Enrollment limit is 15 on /api/v1/me/enrollments and /api/v1/enrollments/enroll
        MockHttpServletRequest enrollReq = new MockHttpServletRequest("POST", "/api/v1/me/enrollments");
        enrollReq.setRemoteAddr("10.0.0.2");
        MockHttpServletResponse enrollRes = new MockHttpServletResponse();
        filter.doFilter(enrollReq, enrollRes, mock(FilterChain.class));
        assertEquals("15", enrollRes.getHeader("X-RateLimit-Limit"));

        // Grading limit is 20 on /api/v1/sections/{id}/grades
        MockHttpServletRequest gradeReq = new MockHttpServletRequest("PUT", "/api/v1/sections/sec-01/grades");
        gradeReq.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse gradeRes = new MockHttpServletResponse();
        filter.doFilter(gradeReq, gradeRes, mock(FilterChain.class));
        assertEquals("20", gradeRes.getHeader("X-RateLimit-Limit"));
    }

    @Test
    void assistantChatUsesItsOwnRateLimitBucket() throws Exception {
        // Assistant chat is a dedicated category, not a reuse of the
        // announcement bucket, so a chatty assistant cannot exhaust the
        // announcement quota and vice versa.
        MockHttpServletRequest assistantReq = new MockHttpServletRequest("POST", "/api/v1/assistant/chat/stream");
        assistantReq.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse assistantRes = new MockHttpServletResponse();
        filter.doFilter(assistantReq, assistantRes, mock(FilterChain.class));
        assertEquals("20", assistantRes.getHeader("X-RateLimit-Limit"));
        assertEquals("19", assistantRes.getHeader("X-RateLimit-Remaining"));

        MockHttpServletRequest announcementReq = new MockHttpServletRequest("POST", "/api/v1/announcements");
        announcementReq.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse announcementRes = new MockHttpServletResponse();
        filter.doFilter(announcementReq, announcementRes, mock(FilterChain.class));
        assertEquals("20", announcementRes.getHeader("X-RateLimit-Limit"));
        assertEquals("19", announcementRes.getHeader("X-RateLimit-Remaining"));

        // Two distinct buckets for the same caller identity.
        assertEquals(2, limiterService.getActiveKeyCount());
    }

    /** Audit S6: outbound mail gets its own 5/hour bucket. */
    @Test
    void mailMutationsAreCappedAtFivePerHour() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/mail/notice");
        req.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, mock(FilterChain.class));
        assertEquals("5", res.getHeader("X-RateLimit-Limit"));
        assertEquals("4", res.getHeader("X-RateLimit-Remaining"));
    }

    /** Audit S6: admin password-reset issuance gets its own 3/hour bucket. */
    @Test
    void passwordResetIssuanceIsCappedAtThreePerHour() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/users/user-42/password-reset");
        req.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, mock(FilterChain.class));
        assertEquals("3", res.getHeader("X-RateLimit-Limit"));
        assertEquals("2", res.getHeader("X-RateLimit-Remaining"));
    }
}
