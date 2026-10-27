package io.campuscore.restfulapi.security.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieOrBearerTokenResolverTest {

    private final CookieOrBearerTokenResolver resolver =
            new CookieOrBearerTokenResolver("cc_access_token");

    @Test
    void staleAccessCookieDoesNotBlockAnonymousLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setCookies(new Cookie("cc_access_token", "stale"));

        assertNull(resolver.resolve(request));
    }

    @Test
    void staleAccessCookieDoesNotBlockSessionRefresh() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.setCookies(new Cookie("cc_access_token", "stale"));

        assertNull(resolver.resolve(request));
    }

    @Test
    void accessCookieStillResolvesOnAuthenticatedPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/me");
        request.setCookies(new Cookie("cc_access_token", "valid"));

        assertEquals("valid", resolver.resolve(request));
    }
}
