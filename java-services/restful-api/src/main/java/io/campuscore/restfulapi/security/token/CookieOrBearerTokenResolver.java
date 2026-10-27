package io.campuscore.restfulapi.security.token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

/** Resolves the shared web cookie contract and the mobile bearer contract. */
@Component
public class CookieOrBearerTokenResolver implements BearerTokenResolver {

    /**
     * Endpoints reachable without a session (permitAll in SecurityConfig).
     * Resolving a stale or corrupted token here would let BearerTokenAuthenticationFilter
     * fail the request with 401 before it ever reaches the anonymous controller,
     * locking a signed-out user out of signing in or recovering a session.
     */
    private static final Set<String> ANONYMOUS_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/auth/two-factor/verify",
            "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password");

    private final DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
    private final String accessTokenCookie;

    public CookieOrBearerTokenResolver(
            @Value("${security.access-token-cookie:cc_access_token}") String accessTokenCookie) {
        this.accessTokenCookie = accessTokenCookie;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (ANONYMOUS_AUTH_PATHS.contains(request.getRequestURI())) {
            return null;
        }
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
