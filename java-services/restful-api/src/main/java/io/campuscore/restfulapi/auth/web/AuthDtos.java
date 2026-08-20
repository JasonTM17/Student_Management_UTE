package io.campuscore.restfulapi.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible auth DTOs for the Java login candidate. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record AuthUserResponse(
            String id,
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
            String studentId,
            String lecturerId,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            StudentContext student) {
    }

    public record StudentContext(Integer year) {
    }

    public record LoginResponse(
            AuthUserResponse user,
            String accessToken,
            String refreshToken) {
    }
}
