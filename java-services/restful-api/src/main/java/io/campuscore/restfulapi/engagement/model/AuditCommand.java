package io.campuscore.restfulapi.engagement.model;

import java.time.Instant;

public record AuditCommand(
        String id,
        String announcementId,
        String action,
        String actorId,
        String actorLabel,
        String reason,
        int version,
        String beforeState,
        String afterState,
        Instant createdAt) {
}
