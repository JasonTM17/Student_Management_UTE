package io.campuscore.restfulapi.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsrfCookieFilterTest {

    private final CsrfCookieFilter filter = new CsrfCookieFilter(
            "cc_access_token",
            "cc_csrf",
            "X-CSRF-Token");

    @Test
    void cookieMutationRequiresMatchingDoubleSubmitToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/contract/ping");
        request.setCookies(
                new jakarta.servlet.http.Cookie("cc_access_token", "access"),
                new jakarta.servlet.http.Cookie("cc_csrf", "expected"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void bearerMutationDoesNotRequireCookieCsrf() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/contract/ping");
        request.addHeader("Authorization", "Bearer token");
        request.setCookies(new jakarta.servlet.http.Cookie("cc_access_token", "access"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
    }
}
