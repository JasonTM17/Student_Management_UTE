package io.campuscore.restfulapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieOrBearerTokenResolverTest {

    private final CookieOrBearerTokenResolver resolver =
            new CookieOrBearerTokenResolver("cc_access_token");

    @Test
    void ignoresStaleCookieOnPublicLifecycleButKeepsBearerMobileContract() {
        MockHttpServletRequest publicRequest =
                new MockHttpServletRequest("POST", "/api/v1/auth/email-verifications/confirm");
        publicRequest.setCookies(new Cookie("cc_access_token", "stale"));

        assertThat(resolver.resolve(publicRequest)).isNull();

        publicRequest.addHeader("Authorization", "Bearer mobile-token");
        assertThat(resolver.resolve(publicRequest)).isEqualTo("mobile-token");
    }

    @Test
    void keepsCookieAuthenticationForProtectedRequests() {
        MockHttpServletRequest protectedRequest =
                new MockHttpServletRequest("GET", "/api/v1/auth/me");
        protectedRequest.setCookies(new Cookie("cc_access_token", "valid-token"));

        assertThat(resolver.resolve(protectedRequest)).isEqualTo("valid-token");
    }
}
