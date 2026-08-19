package io.campuscore.restfulapi.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/** Resolves the shared web cookie contract and the mobile bearer contract. */
@Component
public class CookieOrBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
    private final String accessTokenCookie;

    public CookieOrBearerTokenResolver(
            @Value("${security.access-token-cookie:cc_access_token}") String accessTokenCookie) {
        this.accessTokenCookie = accessTokenCookie;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String bearerToken = bearerTokenResolver.resolve(request);
        if (bearerToken != null) {
            return bearerToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (accessTokenCookie.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
