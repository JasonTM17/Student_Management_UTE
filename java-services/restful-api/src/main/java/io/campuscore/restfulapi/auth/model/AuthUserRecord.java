package io.campuscore.restfulapi.auth.model;

import io.campuscore.restfulapi.auth.dto.AuthDtos.AuthUserResponse;
import io.campuscore.restfulapi.auth.dto.AuthDtos.StudentContext;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AuthUserRecord(
        String id,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String phone,
        String gender,
        Instant dateOfBirth,
        String address,
        String avatar,
        String status,
        boolean mustChangePassword,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant createdAt,
        String studentId,
        Integer studentYear,
        String lecturerId,
        List<String> roles,
        List<String> permissions,
        long credentialVersion) {

    public AuthUserRecord withAuthorities(List<String> roles, List<String> permissions) {
        return new AuthUserRecord(
                id,
                email,
                passwordHash,
                firstName,
                lastName,
                phone,
                gender,
                dateOfBirth,
                address,
                avatar,
                status,
                mustChangePassword,
                failedLoginAttempts,
                lockedUntil,
                createdAt,
                studentId,
                studentYear,
                lecturerId,
                List.copyOf(Objects.requireNonNullElse(roles, List.of())),
                List.copyOf(Objects.requireNonNullElse(permissions, List.of())),
                credentialVersion);
    }

    public AuthUserResponse toResponse() {
        StudentContext student = studentYear == null ? null : new StudentContext(studentYear);
        return new AuthUserResponse(
                id,
                email,
                firstName,
                lastName,
                phone,
                gender,
                dateOfBirth,
                address,
                avatar,
                status,
                mustChangePassword,
                createdAt,
                roles,
                permissions,
                studentId,
                lecturerId,
                student);
    }
}
