package io.campuscore.thesis.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    private final String accessTokenCookie;
    private final String csrfCookie;
    private final String csrfHeader;

    public CsrfCookieFilter(
            @Value("${security.access-token-cookie:cc_access_token}") String accessTokenCookie,
            @Value("${security.csrf-cookie:cc_csrf}") String csrfCookie,
            @Value("${security.csrf-header:X-CSRF-Token}") String csrfHeader) {
        this.accessTokenCookie = accessTokenCookie;
        this.csrfCookie = csrfCookie;
        this.csrfHeader = csrfHeader;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request.getMethod()) || hasBearerHeader(request) || !hasCookie(request, accessTokenCookie)) {
            filterChain.doFilter(request, response);
            return;
        }

        String csrfCookieValue = cookieValue(request, csrfCookie);
        String csrfHeaderValue = request.getHeader(csrfHeader);
        if (csrfCookieValue == null || csrfHeaderValue == null || !sameSecret(csrfCookieValue, csrfHeaderValue)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
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
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    }

    private boolean hasCookie(HttpServletRequest request, String name) {
        return cookieValue(request, name) != null;
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
