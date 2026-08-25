package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable assistant conversation message projection. */
@Entity
@Table(name = "chat_message", schema = "assistant")
public class AssistantMessageEntity {
    @Id private UUID id;
    @Column(name = "conversation_id", nullable = false) private UUID conversationId;
    @Column(name = "role", nullable = false, length = 16) private String role;
    @Column(name = "content", nullable = false) private String content;
    @Column(name = "model", nullable = false, length = 80) private String model;
    @Column(name = "degraded", nullable = false) private boolean degraded;
    @Column(name = "reason_code", nullable = false, length = 48) private String reasonCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "turn_id") private UUID turnId;
    @Column(name = "ordinal") private Integer ordinal;

    protected AssistantMessageEntity() { }

    public static AssistantMessageEntity of(UUID id, UUID conversationId, String role, String content,
            String model, boolean degraded, String reasonCode, UUID turnId, int ordinal, Instant now) {
        AssistantMessageEntity entity = new AssistantMessageEntity();
        entity.id = require(id, "id");
        entity.conversationId = require(conversationId, "conversationId");
        entity.role = requireText(role, "role");
        entity.content = content == null ? "" : content;
        entity.model = requireText(model, "model");
        entity.degraded = degraded;
        entity.reasonCode = requireText(reasonCode, "reasonCode");
        entity.turnId = turnId;
        entity.ordinal = ordinal;
        entity.createdAt = now;
        return entity;
    }

    public UUID getId() { return id; }
    public UUID getConversationId() { return conversationId; }
    public String getRole() { return role; }
    public String getContent() { return content; }
    public String getModel() { return model; }
    public boolean isDegraded() { return degraded; }
    public String getReasonCode() { return reasonCode; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getTurnId() { return turnId; }
    public Integer getOrdinal() { return ordinal; }

    private static UUID require(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
