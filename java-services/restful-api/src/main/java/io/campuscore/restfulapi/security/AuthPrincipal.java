package io.campuscore.restfulapi.security;

import java.util.List;

/**
 * Authenticated user shape shared by the future Java auth owner and current
 * resource-server claim contract.
 */
public record AuthPrincipal(
        String id,
        String email,
        String firstName,
        String lastName,
        String status,
        boolean emailVerified,
        List<String> roles,
        List<String> permissions,
        String studentId,
        Integer studentYear,
        String lecturerId) {
}
