package io.campuscore.restfulapi.security;

import io.campuscore.restfulapi.auth.repository.AuthUserRepository;
import io.campuscore.restfulapi.web.ApiErrorWriter;
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
        if (uri.startsWith("/api/v1/") && !isRotationAllowlist(uri)) {
            AuthUserRepository repository = users.getIfAvailable();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (repository != null && authentication instanceof JwtAuthenticationToken jwtToken) {
                if (DatabaseAvailabilityTracker.isRecentlyUnavailable()) {
                    if (isDegradableEndpoint(uri)) {
                        chain.doFilter(request, response);
                        return;
                    }
                    errorWriter.write(request, response, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "DATABASE_UNAVAILABLE", "Database service is temporarily unavailable");
                    return;
                }

                Optional<AuthUserRepository.AccountState> state;
                try {
                    state = repository.findAccountState(jwtToken.getToken().getSubject());
                    DatabaseAvailabilityTracker.recordSuccess();
                } catch (org.springframework.dao.DataAccessException exception) {
                    DatabaseAvailabilityTracker.recordFailure();
                    if (isDegradableEndpoint(uri)) {
                        chain.doFilter(request, response);
                        return;
                    }
                    errorWriter.write(request, response, org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                            "DATABASE_UNAVAILABLE", "Database service is temporarily unavailable");
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
                    if (state.get().mustChangePassword()) {
                        errorWriter.write(request, response, org.springframework.http.HttpStatus.FORBIDDEN,
                                "PASSWORD_CHANGE_REQUIRED",
                                "Rotate the issued temporary password before using the portal");
                        return;
                    }
                }
                // No row means the account was hard-deleted after the token was
                // minted. The token stays valid for at most the access-token TTL
                // (15 minutes); revocation on deactivation is still enforced
                // above because the row survives with a non-ACTIVE status.
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isDegradableEndpoint(String uri) {
        return uri.startsWith("/api/v1/assistant/")
                || uri.startsWith("/api/v1/thesis/assistant/");
    }

    /** The minimal endpoints an issued account needs to rotate its password. */
    private static boolean isRotationAllowlist(String uri) {
        return "/api/v1/auth/me".equals(uri)
                || "/api/v1/auth/change-password".equals(uri)
                || "/api/v1/auth/logout".equals(uri)
                || "/api/v1/auth/refresh".equals(uri);
    }
}
