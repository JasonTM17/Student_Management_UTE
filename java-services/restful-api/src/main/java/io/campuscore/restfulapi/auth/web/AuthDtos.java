package io.campuscore.restfulapi.auth.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Authentication and account DTOs for web and mobile clients. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String email,
            @NotBlank String password) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    /**
     * Avatar accepts a base64 data URL produced by the client-side resize
     * (≤ ~150 KB). phone/address bounds mirror the VARCHAR(80)/VARCHAR(500)
     * columns of campuscore_auth."User" (V2) so overflow is a 400 validation
     * error instead of a data-too-long 500.
     */
    public record UpdateProfileRequest(
            String firstName,
            String lastName,
            @Size(max = 80, message = "phone must be at most 80 characters") String phone,
            String dateOfBirth,
            @Size(max = 500, message = "address must be at most 500 characters") String address,
            @Size(max = 200_000, message = "avatar must be a data URL of at most 200k characters") String avatar) {
    }

    public record ChangePasswordRequest(
            @NotBlank String oldPassword,
            @NotBlank @Size(min = 8) String newPassword) {
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
            boolean mustChangePassword,
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

    /**
     * The two-factor fields are additive and omitted (NON_NULL) whenever a
     * login completes in one step, keeping the historical single-step JSON
     * byte-compatible. A second-step challenge returns null user/tokens with
     * twoFactorRequired=true plus the challengeId and masked email.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(
            AuthUserResponse user,
            String accessToken,
            String refreshToken,
            Boolean twoFactorRequired,
            String challengeId,
            String maskedEmail) {

        public LoginResponse(AuthUserResponse user, String accessToken, String refreshToken) {
            this(user, accessToken, refreshToken, null, null, null);
        }
    }
}
