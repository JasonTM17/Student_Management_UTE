package io.campuscore.restfulapi.security.ratelimit.filter;

import io.campuscore.restfulapi.exception.response.ApiErrorWriter;
import io.campuscore.restfulapi.security.ratelimit.model.RateLimitCategory;
import io.campuscore.restfulapi.security.ratelimit.model.RateLimitResult;
import io.campuscore.restfulapi.security.ratelimit.properties.RateLimitProperties;
import io.campuscore.restfulapi.security.ratelimit.service.ClientIpResolver;
import io.campuscore.restfulapi.security.ratelimit.service.RateLimiterService;
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
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(
            RateLimiterService rateLimiterService,
            RateLimitProperties properties,
            ApiErrorWriter errorWriter,
            ClientIpResolver clientIpResolver) {
        this.rateLimiterService = rateLimiterService;
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.clientIpResolver = clientIpResolver;
    }

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Audit S6: mail sends are capped at 5 per hour per identity. */
    static final int MAIL_LIMIT_PER_HOUR = 5;

    /** Audit S6: password-reset issuance is capped at 3 per hour per identity. */
    static final int PASSWORD_RESET_LIMIT_PER_HOUR = 3;

    /** Two-factor OTP verification: 10 tries per 15 minutes per IP. */
    static final int TWO_FACTOR_VERIFY_LIMIT_PER_WINDOW = 10;

    /** Window (seconds) for the two-factor OTP verification bucket. */
    static final int TWO_FACTOR_VERIFY_WINDOW_SECONDS = 900;

    /** Self-service reset requests: 5 emails per hour per IP (anonymous). */
    static final int FORGOT_PASSWORD_LIMIT_PER_WINDOW = 5;

    /** Window (seconds) for the forgot-password bucket. */
    static final int FORGOT_PASSWORD_WINDOW_SECONDS = 3600;

    /** Self-service resets: 10 consumes per 15 minutes per IP (anonymous). */
    static final int RESET_PASSWORD_LIMIT_PER_WINDOW = 10;

    /** Window (seconds) for the self-service reset bucket. */
    static final int RESET_PASSWORD_WINDOW_SECONDS = 900;

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
        String clientIp = clientIpResolver.resolve(request);

        // Authentication endpoints (login) are always strictly bound to IP to prevent brute-force
        if (category == RateLimitCategory.AUTH_LOGIN) {
            return "ip:" + clientIp + ":" + category.name();
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            String identifier = auth.getName();
            return "user:" + identifier + ":" + category.name();
        }

        return "ip:" + clientIp + ":" + category.name();
    }

    private RateLimitPolicy resolvePolicy(String uri) {
        if (uri.startsWith("/api/v1/auth/login")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_LOGIN, properties.getLoginLimit(), 60);
        }
        // Two-factor verify is anonymous and code-guessable, so — like login —
        // it is bound strictly to the client IP with its own tight bucket,
        // layered on top of the per-challenge 5-attempt lock.
        if (uri.startsWith("/api/v1/auth/two-factor/verify")) {
            return new RateLimitPolicy(
                    RateLimitCategory.TWO_FACTOR_VERIFY,
                    TWO_FACTOR_VERIFY_LIMIT_PER_WINDOW,
                    TWO_FACTOR_VERIFY_WINDOW_SECONDS);
        }
        // Self-service password reset is anonymous and mails a credential, so —
        // like login — each side gets its own IP-keyed bucket, before the
        // generic auth rules and far below the default POST limit.
        if (uri.startsWith("/api/v1/auth/forgot-password")) {
            return new RateLimitPolicy(
                    RateLimitCategory.FORGOT_PASSWORD,
                    FORGOT_PASSWORD_LIMIT_PER_WINDOW,
                    FORGOT_PASSWORD_WINDOW_SECONDS);
        }
        if (uri.startsWith("/api/v1/auth/reset-password")) {
            return new RateLimitPolicy(
                    RateLimitCategory.RESET_PASSWORD_SELF,
                    RESET_PASSWORD_LIMIT_PER_WINDOW,
                    RESET_PASSWORD_WINDOW_SECONDS);
        }
        if (uri.startsWith("/api/v1/auth/change-password")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_CHANGE_PASSWORD, 5, 60);
        }
        if (uri.startsWith("/api/v1/auth/refresh")) {
            return new RateLimitPolicy(RateLimitCategory.AUTH_REFRESH, 30, 60);
        }
        // Dedicated tight policies (audit S6): outbound mail and password-reset
        // issuance are expensive and abuse-prone, so they get their own
        // per-identity buckets far below the default POST limit.
        if (uri.startsWith("/api/v1/mail/")) {
            return new RateLimitPolicy(RateLimitCategory.MAIL_MUTATION, MAIL_LIMIT_PER_HOUR, 3600);
        }
        if (uri.startsWith("/api/v1/users/") && uri.endsWith("/password-reset")) {
            return new RateLimitPolicy(RateLimitCategory.PASSWORD_RESET, PASSWORD_RESET_LIMIT_PER_HOUR, 3600);
        }
        if (uri.startsWith("/api/v1/academic/me/enrollments")
                || uri.startsWith("/api/v1/academic/enrollments")
                || uri.startsWith("/api/v1/me/enrollments")
                || uri.startsWith("/api/v1/enrollments")) {
            return new RateLimitPolicy(RateLimitCategory.ENROLLMENT_MUTATION, 15, 60);
        }
        if (uri.startsWith("/api/v1/assistant/") || uri.startsWith("/api/v1/thesis/assistant")) {
            return new RateLimitPolicy(RateLimitCategory.ASSISTANT_CHAT, 20, 60);
        }
        if (uri.startsWith("/api/v1/thesis/")) {
            return new RateLimitPolicy(RateLimitCategory.THESIS_MUTATION, 20, 60);
        }
        if ((uri.startsWith("/api/v1/academic/sections/") || uri.startsWith("/api/v1/sections/"))
                && uri.contains("grades")) {
            return new RateLimitPolicy(RateLimitCategory.GRADING_MUTATION, 20, 60);
        }
        if (uri.startsWith("/api/v1/announcements")) {
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
