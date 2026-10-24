package io.campuscore.restfulapi.auth.model;

import java.time.Instant;

public record IssuedAccessToken(String accessToken, Instant expiresAt) {
}
