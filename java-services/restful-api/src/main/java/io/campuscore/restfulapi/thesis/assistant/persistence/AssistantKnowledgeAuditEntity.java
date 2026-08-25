package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Append-only audit row for assistant knowledge governance. */
@Entity
@Table(name = "knowledge_document_audit", schema = "assistant")
public class AssistantKnowledgeAuditEntity {

    @Id
    private UUID id;

    @Column(name = "revision_id", nullable = false)
    private UUID revisionId;

    @Column(name = "action", nullable = false, length = 32)
    private String action;

    @Column(name = "actor_id", nullable = false, length = 120)
    private String actorId;

    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssistantKnowledgeAuditEntity() {
    }

    public static AssistantKnowledgeAuditEntity record(UUID id, UUID revisionId, String action,
            String actorId, String note, Instant now) {
        AssistantKnowledgeAuditEntity entity = new AssistantKnowledgeAuditEntity();
        entity.id = require(id, "id");
        entity.revisionId = require(revisionId, "revisionId");
        entity.action = requireText(action, "action");
        entity.actorId = requireText(actorId, "actorId");
        entity.note = note == null || note.isBlank() ? null : note.trim();
        entity.createdAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getRevisionId() { return revisionId; }
    public String getAction() { return action; }
    public String getActorId() { return actorId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }

    private static UUID require(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
