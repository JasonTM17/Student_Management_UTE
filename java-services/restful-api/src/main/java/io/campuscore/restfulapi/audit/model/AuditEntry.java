package io.campuscore.restfulapi.audit.model;

import java.time.Instant;

public record AuditEntry(
        String id,
        String actorId,
        String actorLabel,
        String action,
        String entityType,
        String entityId,
        String summary,
        String beforeState,
        String afterState,
        Instant createdAt) {
}
