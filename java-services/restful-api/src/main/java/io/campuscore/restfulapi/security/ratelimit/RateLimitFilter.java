package io.campuscore.restfulapi.security.ratelimit;

import io.campuscore.restfulapi.web.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that enforces sliding window rate limits on mutating HTTP POST APIs.
 * Distinguishes between unauthenticated IP callers and authenticated user accounts.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties properties;
    private final ApiErrorWriter errorWriter;

    public RateLimitFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties,
            ApiErrorWriter errorWriter) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Enforce on mutating requests (POST, PUT, PATCH, DELETE)
        String method = request.getMethod();
        if (method == null || !MUTATING_METHODS.contains(method.toUpperCase(java.util.Locale.ROOT))) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Whitelist internal, health, actuator, and documentation endpoints
        if (isWhitelisted(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine client identity & rate limit policy
        RateLimitPolicy policy = resolvePolicy(uri);
        String clientKey = resolveClientKey(request, policy.category());

        RateLimitResult result = rateLimiterService.tryAcquire(
                clientKey,
                policy.limit(),
                policy.windowSeconds());

        // Attach standard rate limit headers
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));

        if (!result.allowed()) {
            response.setHeader("Retry-After", String.valueOf(result.retryAfterSeconds()));
            errorWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED",
                    "Bạn đã gửi quá nhiều yêu cầu. Vui lòng thử lại sau " + result.retryAfterSeconds() + " giây.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String uri) {
        return uri.startsWith("/api/v1/health")
                || uri.startsWith("/actuator")
                || uri.startsWith("/api/docs")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/internal/rag");
    }

    private String resolveClientKey(HttpServletRequest request, RateLimitCategory category) {
        String clientIp = resolveClientIp(request);

        // Authentication endpoints (login/register) are always strictly bound to IP to prevent brute-force
        if (category == RateLimitCategory.AUTH_LOGIN || category == RateLimitCategory.AUTH_REGISTER) {
            return "ip:" + clientIp + ":" + category.name();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String identifier = auth.getName();
            return "user:" + identifier + ":" + category.name();
        }

        return "ip:" + clientIp + ":" + category.name();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String[] parts = xForwardedFor.split(",");
            if (parts.length > 0) {
                String candidate = parts[0].trim();
                if (!candidate.isBlank()) {
                    return candidate;
                }
            }
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
    }

    private RateLimitPolicy resolvePolicy(String uri) {
        if (uri.startsWith("/api/v1/auth/login")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_LOGIN, properties.getLoginLimit(), 60);
        }
        if (uri.startsWith("/api/v1/auth/register")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_REGISTER, properties.getRegisterLimit(), 60);
        }
        if (uri.startsWith("/api/v1/auth/change-password")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_CHANGE_PASSWORD, 5, 60);
        }
        if (uri.startsWith("/api/v1/auth/refresh")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_REFRESH, 30, 60);
        }
        if (uri.startsWith("/api/v1/academic/me/enrollments") || uri.startsWith("/api/v1/academic/enrollments")) {
            return new RateLimitPolicy(RateLimitCategory.ENROLLMENT_MUTATION, 15, 60);
        }
        if (uri.startsWith("/api/v1/thesis/")) {
            return new RateLimitPolicy(RateLimitCategory.THESIS_MUTATION, 20, 60);
        }
        if (uri.startsWith("/api/v1/academic/sections/") && uri.contains("grades")) {
            return new RateLimitPolicy(RateLimitCategory.GRADING_MUTATION, 20, 60);
        }
        if (uri.startsWith("/api/v1/announcements")) {
            return new RateLimitPolicy(RateLimitCategory.ANNOUNCEMENT_MUTATION, 20, 60);
        }
        if (uri.startsWith("/api/v1/assistant/")) {
            return new RateLimitPolicy(RateLimitCategory.ANNOUNCEMENT_MUTATION, 20, 60);
        }
        if (uri.startsWith("/api/v1/notifications")) {
            return new RateLimitPolicy(RateLimitCategory.NOTIFICATION_MUTATION, 30, 60);
        }
        if (uri.startsWith("/api/v1/admin/")) {
            return new RateLimitPolicy(RateLimitCategory.ADMIN_MUTATION, 30, 60);
        }

        return new RateLimitPolicy(
                RateLimitCategory.DEFAULT_POST,
                properties.getDefaultLimit(),
                properties.getDefaultWindowSeconds());
    }

    private record RateLimitPolicy(RateLimitCategory category, int limit, int windowSeconds) {}
}
