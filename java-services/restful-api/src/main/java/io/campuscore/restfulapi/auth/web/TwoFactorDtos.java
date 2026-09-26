package io.campuscore.restfulapi.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Two-factor (email OTP) request and response payloads. */
public final class TwoFactorDtos {

    private TwoFactorDtos() {
    }

    public record TwoFactorStatusResponse(boolean enabled) {
    }

    public record EnableTwoFactorRequest(@NotBlank String password) {
    }

    public record DisableTwoFactorRequest(@NotBlank String password) {
    }

    /** Six-digit codes only; the size bound keeps overflow a clean 400. */
    public record ConfirmTwoFactorRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(max = 8, message = "code must be at most 8 characters") String code) {
    }

    public record VerifyTwoFactorLoginRequest(
            @NotBlank String challengeId,
            @NotBlank @Size(max = 8, message = "code must be at most 8 characters") String code) {
    }

    public record ChallengeResponse(String challengeId) {
    }
}
