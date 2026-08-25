package io.campuscore.restfulapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import io.campuscore.restfulapi.web.ApiErrorWriter;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the existing double-submit CSRF contract for cookie-authenticated web requests. */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    private final String accessTokenCookie;
    private final String refreshTokenCookie;
    private final String csrfCookie;
    private final String csrfHeader;
    private final ApiErrorWriter errorWriter;

    public CsrfCookieFilter(
            @Value("${security.access-token-cookie:cc_access_token}") String accessTokenCookie,
            @Value("${security.refresh-token-cookie:cc_refresh_token}") String refreshTokenCookie,
            @Value("${security.csrf-cookie:cc_csrf}") String csrfCookie,
            @Value("${security.csrf-header:X-CSRF-Token}") String csrfHeader,
            ApiErrorWriter errorWriter) {
        this.accessTokenCookie = accessTokenCookie;
        this.refreshTokenCookie = refreshTokenCookie;
        this.csrfCookie = csrfCookie;
        this.csrfHeader = csrfHeader;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request.getMethod())
                || hasBearerHeader(request)
                || isPublicLifecycleRequest(request)
                || !hasSessionCookie(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String csrfCookieValue = cookieValue(request, csrfCookie);
        String csrfHeaderValue = request.getHeader(csrfHeader);
        if (csrfCookieValue == null
                || csrfHeaderValue == null
                || !sameSecret(csrfCookieValue, csrfHeaderValue)) {
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.FORBIDDEN,
                    "CSRF_INVALID",
                    "Invalid CSRF token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSafeMethod(String method) {
        return HttpMethod.GET.matches(method)
                || HttpMethod.HEAD.matches(method)
                || HttpMethod.OPTIONS.matches(method);
    }

    private boolean hasBearerHeader(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ");
    }

    private boolean hasSessionCookie(HttpServletRequest request) {
        return cookieValue(request, accessTokenCookie) != null
                || cookieValue(request, refreshTokenCookie) != null;
    }

    private boolean isPublicLifecycleRequest(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/register")
                || path.startsWith("/api/v1/auth/email-verifications/")
                || path.equals("/api/v1/auth/password-reset-requests")
                || path.equals("/api/v1/auth/password-reset/confirm")
                || path.equals("/api/v1/auth/verify-email")
                || path.equals("/api/v1/auth/resend-verification")
                || path.equals("/api/v1/auth/forgot-password")
                || path.equals("/api/v1/auth/reset-password");
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean sameSecret(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
