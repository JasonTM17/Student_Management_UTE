package io.campuscore.restfulapi.auth.model;

import io.campuscore.restfulapi.auth.dto.AuthDtos.LoginResponse;
import java.time.Instant;

/**
 * A full session (response + expiry instants, challenge fields null) or a
 * second-factor challenge (response null, challenge fields set). The
 * controller branches on {@code response == null}.
 */
public record LoginResult(
        LoginResponse response,
        Instant accessTokenExpiresAt,
        Instant refreshTokenExpiresAt,
        String challengeId,
        String email) {

    public static LoginResult session(
            LoginResponse response,
            Instant accessTokenExpiresAt,
            Instant refreshTokenExpiresAt) {
        return new LoginResult(response, accessTokenExpiresAt, refreshTokenExpiresAt, null, null);
    }

    public static LoginResult challenge(String challengeId, String email) {
        return new LoginResult(null, null, null, challengeId, email);
    }
}
