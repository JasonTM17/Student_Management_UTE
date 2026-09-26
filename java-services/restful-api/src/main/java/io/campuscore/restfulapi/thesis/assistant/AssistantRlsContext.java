package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.AssistantRlsBoundary.Access;
import io.campuscore.restfulapi.web.DomainException;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Trusted, request-local identity and system scope used to populate transaction-local PostgreSQL settings. */
final class AssistantRlsContext {
    private static final ThreadLocal<Identity> CURRENT = new ThreadLocal<>();

    private AssistantRlsContext() { }

    enum Scope {
        USER,
        INTERNAL_OWNER,
        ADMIN_GOVERNANCE,
        RETENTION,
        KNOWLEDGE_PROJECTION
    }

    record Identity(String ownerId, Scope scope, boolean admin) {
        Identity {
            if ((scope == Scope.USER || scope == Scope.INTERNAL_OWNER || scope == Scope.ADMIN_GOVERNANCE)
                    && (ownerId == null || ownerId.isBlank())) {
                throw new IllegalArgumentException("An owner identity is required for this Assistant scope");
            }
            if ((scope == Scope.RETENTION || scope == Scope.KNOWLEDGE_PROJECTION) && ownerId != null) {
                throw new IllegalArgumentException("System Assistant scopes cannot carry a user owner");
            }
            if (scope != Scope.ADMIN_GOVERNANCE && scope != Scope.USER && admin) {
                throw new IllegalArgumentException("Admin authority is valid only for an authenticated user scope");
            }
        }
    }

    static Identity current() {
        return CURRENT.get();
    }

    static Identity forAccess(Access access) {
        return switch (access) {
            case AUTO -> authenticatedUser(false);
            case ADMIN_GOVERNANCE -> authenticatedUser(true);
            case RETENTION -> new Identity(null, Scope.RETENTION, false);
            case KNOWLEDGE_PROJECTION -> new Identity(null, Scope.KNOWLEDGE_PROJECTION, false);
        };
    }

    static Identity internalOwner(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            throw unauthenticated();
        }
        return new Identity(ownerId, Scope.INTERNAL_OWNER, false);
    }

    static <T> T withInternalOwner(String ownerId, Supplier<T> action) {
        return withIdentity(internalOwner(ownerId), action);
    }

    static <T> T withIdentity(Identity identity, Supplier<T> action) {
        Identity prior = CURRENT.get();
        if (prior != null && !prior.equals(identity)) {
            throw new IllegalStateException("Assistant RLS scope cannot change inside an active trusted scope");
        }
        if (prior == null) {
            CURRENT.set(identity);
        }
        try {
            return action.get();
        } finally {
            if (prior == null) {
                CURRENT.remove();
            }
        }
    }

    private static Identity authenticatedUser(boolean governanceRequired) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw unauthenticated();
        }

        String subject = jwtAuthentication.getToken().getSubject();
        if (subject == null || subject.isBlank()) {
            throw unauthenticated();
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean admin = authorities.contains("ROLE_ADMIN") || authorities.contains("ROLE_SUPER_ADMIN");
        if (governanceRequired && !admin) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ASSISTANT_ADMIN_REQUIRED",
                    "Administrator authority is required for knowledge governance");
        }
        return new Identity(subject, governanceRequired ? Scope.ADMIN_GOVERNANCE : Scope.USER, admin);
    }

    private static DomainException unauthenticated() {
        return new DomainException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                "Authenticated Assistant identity is required");
    }
}
