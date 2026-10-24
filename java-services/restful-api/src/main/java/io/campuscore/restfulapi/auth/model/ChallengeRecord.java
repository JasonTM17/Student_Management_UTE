package io.campuscore.restfulapi.auth.model;

import java.time.Instant;

public record ChallengeRecord(
        String id,
        String userId,
        String purpose,
        String codeHash,
        Instant expiresAt,
        Instant consumedAt,
        int attempts,
        Instant createdAt) {
}
