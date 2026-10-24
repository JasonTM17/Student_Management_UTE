package io.campuscore.restfulapi.auth.model;

public record AccountAuthorization(String status, boolean mustChangePassword, String studentId,
        Integer studentYear, String lecturerId, java.util.Set<String> roles, java.util.Set<String> permissions,
        long credentialVersion) {
}
