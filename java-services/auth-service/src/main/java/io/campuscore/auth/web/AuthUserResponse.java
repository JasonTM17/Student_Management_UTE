package io.campuscore.auth.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthUserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String gender,
        Instant dateOfBirth,
        String address,
        String avatar,
        String status,
        Instant createdAt,
        List<String> roles,
        List<String> permissions,
        UUID studentId,
        UUID lecturerId) {
}
