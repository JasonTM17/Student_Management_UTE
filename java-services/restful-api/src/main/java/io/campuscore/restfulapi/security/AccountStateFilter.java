package io.campuscore.restfulapi.security;

import io.campuscore.restfulapi.auth.model.AccountAuthorization;
import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.exception.response.ApiErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces office-issued account lifecycle on every authenticated business
 * request. Enforcement reads account state from the database (not token
 * claims) so a password reset or deactivation takes effect immediately, even
 * for access tokens issued earlier.
 */
@Component
public class AccountStateFilter extends OncePerRequestFilter {

    private final ObjectProvider<AuthUserRepository> users;
    private final ApiErrorWriter errorWriter;

    public AccountStateFilter(ObjectProvider<AuthUserRepository> users, ApiErrorWriter errorWriter) {
        this.users = users;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/v1/") && !isSessionCleanupEndpoint(uri)) {
            AuthUserRepository repository = users.getIfAvailable();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtToken) {
                if (repository == null || DatabaseAvailabilityTracker.isRecentlyUnavailable()) {
                    errorWriter.write(request, response, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "DATABASE_UNAVAILABLE", "Database service is temporarily unavailable");
                    return;
                }

                Optional<AccountAuthorization> state;
                try {
                    state = repository.findAccountAuthorization(jwtToken.getToken().getSubject());
                    DatabaseAvailabilityTracker.recordSuccess();
                } catch (org.springframework.dao.DataAccessException exception) {
                    DatabaseAvailabilityTracker.recordFailure();
                    errorWriter.write(request, response, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "DATABASE_UNAVAILABLE", "Database service is temporarily unavailable");
                    return;
                }
                if (state.isEmpty()) {
                    errorWriter.write(request, response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                            "SESSION_INVALID", "Sign in again to continue");
                    return;
                }
                if (state.isPresent()) {
                    // A present row is authoritative: deactivation and the
                    // office-issued credential flag take effect immediately,
                    // even for access tokens issued earlier.
                    if (!"ACTIVE".equals(state.get().status())) {
                        errorWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                                "ACCOUNT_INACTIVE", "This account is not active");
                        return;
                    }
                    if (!matchesCurrentAuthorization(jwtToken.getToken(), state.get())) {
                        errorWriter.write(request, response, org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "SESSION_INVALID", "Sign in again to continue");
                        return;
                    }
                    if (state.get().mustChangePassword() && !isRotationAllowlist(uri)) {
                        errorWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                                "PASSWORD_CHANGE_REQUIRED",
                                "Rotate the issued temporary password before using the portal");
                        return;
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean matchesCurrentAuthorization(org.springframework.security.oauth2.jwt.Jwt token,
            AccountAuthorization state) {
        if (!matchesCredentialVersion(token, state.credentialVersion())
                || !matchesSet(token.getClaims().get("roles"), state.roles())
                || !matchesSet(token.getClaims().get("permissions"), state.permissions())
                || !java.util.Objects.equals(token.getClaims().get("studentId"), state.studentId())
                || !java.util.Objects.equals(token.getClaims().get("lecturerId"), state.lecturerId())) {
            return false;
        }
        Object student = token.getClaims().get("student");
        if (student != null && !(student instanceof java.util.Map<?, ?>)) return false;
        Object year = student instanceof java.util.Map<?, ?> profile ? profile.get("year") : null;
        return year == null ? state.studentYear() == null
                : year instanceof Number number && state.studentYear() != null
                        && number.doubleValue() == state.studentYear().doubleValue();
    }

    private static boolean matchesCredentialVersion(org.springframework.security.oauth2.jwt.Jwt token, long current) {
        // Only untouched revision-zero accounts accept a pre-upgrade token.
        // Signed JWT numeric integers decode as Integer/Long; reject decimal,
        // string, null and negative claims rather than coercing them.
        if (!token.getClaims().containsKey("credentialVersion")) {
            return current == 0;
        }
        Object claim = token.getClaims().get("credentialVersion");
        return (claim instanceof Long || claim instanceof Integer)
                && ((Number) claim).longValue() >= 0
                && ((Number) claim).longValue() == current;
    }

    private static boolean matchesSet(Object claim, java.util.Set<String> current) {
        if (claim == null) return current.isEmpty();
        if (!(claim instanceof java.util.Collection<?> values)
                || values.stream().anyMatch(value -> !(value instanceof String))) return false;
        return new java.util.HashSet<>(values).equals(current);
    }

    private static boolean isSessionCleanupEndpoint(String uri) {
        // These endpoints establish, replace or discard session state rather
        // than consume it; a stale access token must not strand login, 2FA
        // verification or self-service recovery behind a revoked session.
        return "/api/v1/auth/login".equals(uri)
                || "/api/v1/auth/two-factor/verify".equals(uri)
                || "/api/v1/auth/forgot-password".equals(uri)
                || "/api/v1/auth/reset-password".equals(uri)
                || "/api/v1/auth/logout".equals(uri)
                || "/api/v1/auth/refresh".equals(uri);
    }

    /** The minimal endpoints an issued account needs to rotate its password. */
    private static boolean isRotationAllowlist(String uri) {
        return "/api/v1/auth/me".equals(uri)
                || "/api/v1/auth/change-password".equals(uri);
    }
}
