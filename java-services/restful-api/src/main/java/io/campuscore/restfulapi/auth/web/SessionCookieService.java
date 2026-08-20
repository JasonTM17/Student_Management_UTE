package io.campuscore.restfulapi.auth.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Emits the shared browser cookie contract used by the legacy Nest services. */
@Component
public class SessionCookieService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final String accessTokenCookie;
    private final String refreshTokenCookie;
    private final String csrfCookie;
    private final boolean cookieSecure;

    public SessionCookieService(
            @Value("${security.access-token-cookie:cc_access_token}") String accessTokenCookie,
            @Value("${security.refresh-token-cookie:cc_refresh_token}") String refreshTokenCookie,
            @Value("${security.csrf-cookie:cc_csrf}") String csrfCookie,
            @Value("${security.cookie-secure:false}") boolean cookieSecure) {
        this.accessTokenCookie = accessTokenCookie;
        this.refreshTokenCookie = refreshTokenCookie;
        this.csrfCookie = csrfCookie;
        this.cookieSecure = cookieSecure;
    }

    public void issue(
            HttpServletRequest request,
            HttpServletResponse response,
            String accessToken,
            String refreshToken,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt) {
        Instant now = Instant.now();
        addCookie(response, accessTokenCookie, accessToken, true, maxAge(now, accessTokenExpiresAt));
        addCookie(response, refreshTokenCookie, refreshToken, true, maxAge(now, refreshTokenExpiresAt));
        addCookie(response, csrfCookie, csrfToken(), false, maxAge(now, refreshTokenExpiresAt));
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            boolean httpOnly,
            Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private Duration maxAge(Instant now, Instant expiresAt) {
        Duration maxAge = Duration.between(now, expiresAt);
        return maxAge.isNegative() ? Duration.ZERO : maxAge;
    }

    private String csrfToken() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
