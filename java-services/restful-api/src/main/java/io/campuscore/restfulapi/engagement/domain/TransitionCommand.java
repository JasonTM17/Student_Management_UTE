package io.campuscore.restfulapi.engagement.domain;

import java.time.Instant;

public record TransitionCommand(
        String id,
        int expectedVersion,
        String archivedBy,
        Instant archivedAt,
        Instant updatedAt) {
}
