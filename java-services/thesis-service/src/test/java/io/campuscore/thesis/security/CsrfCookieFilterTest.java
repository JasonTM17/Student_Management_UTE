package io.campuscore.thesis.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfCookieFilterTest {

    private final CsrfCookieFilter filter = new CsrfCookieFilter(
            "cc_access_token",
            "cc_csrf",
            "X-CSRF-Token");

    @Test
    void rejectsCookieAuthenticatedMutationWithoutMatchingCsrfHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/thesis/groups");
        request.setCookies(
                new jakarta.servlet.http.Cookie("cc_access_token", "access"),
                new jakarta.servlet.http.Cookie("cc_csrf", "expected"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> continued.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(continued).isFalse();
    }

    @Test
    void allowsBearerAuthenticatedMutationWithoutCookieCsrf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/thesis/groups");
        request.addHeader("Authorization", "Bearer access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) -> continued.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(continued).isTrue();
    }
}
