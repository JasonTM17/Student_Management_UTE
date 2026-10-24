package io.campuscore.restfulapi.auth.model;

import java.time.Instant;

public record TokenRecord(
        String id,
        String userId,
        String tokenHash,
        Instant expiresAt,
        Instant usedAt,
        Instant createdAt,
        String requestIp) {
}
