package io.campuscore.restfulapi.engagement.model;

import java.time.Instant;

public record TransitionCommand(
        String id,
        int expectedVersion,
        String archivedBy,
        Instant archivedAt,
        Instant updatedAt) {
}
