package io.campuscore.restfulapi.auth.model;

import java.time.Instant;

public record IssuedRefreshToken(String refreshToken, Instant expiresAt) {
}
