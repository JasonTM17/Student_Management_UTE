package io.campuscore.restfulapi.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Authentication and account DTOs for web and mobile clients. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 200) String password,
            @NotBlank @Size(max = 120) String firstName,
            @NotBlank @Size(max = 120) String lastName,
            @Size(max = 80) String phone,
            @Size(max = 40) String gender,
            String dateOfBirth,
            @Size(max = 500) String address) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record UpdateProfileRequest(
            String firstName,
            String lastName,
            String phone,
            String dateOfBirth,
            String address) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 8) String newPassword) {
    }

    public record ChallengeTokenRequest(
            @NotBlank @Size(max = 240) String token) {
    }

    public record EmailRequest(
            @NotBlank @Email String email) {
    }

    public record PasswordResetRequest(
            @NotBlank @Email String email) {
    }

    public record PasswordResetConfirmRequest(
            @NotBlank @Size(max = 240) String token,
            @NotBlank @Size(min = 8, max = 200) String newPassword) {
    }

    public record RegistrationPendingResponse(
            String email,
            boolean verificationRequired,
            long expiresInSeconds,
            long resendAfterSeconds) {
    }

    public record MessageResponse(String message) {
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
            boolean emailVerified,
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
