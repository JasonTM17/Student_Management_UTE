package io.campuscore.restfulapi.auth.dto;

import java.util.List;

public record IdentityResponse(
        String subject,
        List<String> roles,
        List<String> permissions,
        String studentId,
        String lecturerId) {
}
