package io.campuscore.thesis.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public record AccessContext(
        UUID userId,
        UUID studentId,
        UUID lecturerId,
        Set<String> roles,
        Set<String> permissions) {

    public static AccessContext from(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new IllegalArgumentException("A JWT authentication is required");
        }

        return new AccessContext(
                uuidClaim(jwt, "sub"),
                uuidClaim(jwt, "studentId"),
                uuidClaim(jwt, "lecturerId"),
                stringClaims(jwt, "roles"),
                stringClaims(jwt, "permissions"));
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission)
                || permissions.contains("thesis:*")
                || permissions.contains("*:*")
                || roles.contains("ADMIN")
                || roles.contains("SUPER_ADMIN");
    }

    private static UUID uuidClaim(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (value == null || value.toString().isBlank() || "null".equalsIgnoreCase(value.toString())) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static Set<String> stringClaims(Jwt jwt, String name) {
        Object value = jwt.getClaims().get(name);
        if (!(value instanceof Collection<?> values)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            if (item != null && !item.toString().isBlank()) {
                result.add(item.toString());
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
