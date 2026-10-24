package io.campuscore.restfulapi.auth.model;

import java.time.Instant;

public record RacedRotation(AuthUserRecord user, String sessionId, Instant rotatedAt) {
}
