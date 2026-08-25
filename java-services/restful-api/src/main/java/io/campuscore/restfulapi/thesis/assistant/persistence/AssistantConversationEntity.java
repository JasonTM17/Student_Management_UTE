package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Owner-scoped assistant conversation aggregate. */
@Entity
@Table(name = "chat_conversation", schema = "assistant")
public class AssistantConversationEntity {

    @Id
    private UUID id;
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;
    @Column(name = "title", length = 160)
    private String title;
    @Column(name = "locale", nullable = false, length = 2)
    private String locale;
    @Column(name = "state", nullable = false, length = 16)
    private String state;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    /** Existing timestamp provides optimistic protection without changing V11/V12. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "archived_at")
    private Instant archivedAt;
    @Column(name = "archived_by", length = 120)
    private String archivedBy;

    protected AssistantConversationEntity() { }

    public static AssistantConversationEntity pending(UUID id, String ownerId, String locale,
            Instant now, Instant expiresAt) {
        AssistantConversationEntity entity = new AssistantConversationEntity();
        entity.id = require(id, "id");
        entity.ownerId = requireText(ownerId, "ownerId");
        entity.locale = "en".equalsIgnoreCase(locale) ? "en" : "vi";
        entity.state = "PENDING";
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.expiresAt = require(expiresAt, "expiresAt");
        return entity;
    }

    public void activate(String title, Instant now) {
        if (!"PENDING".equals(state)) throw new IllegalStateException("ASSISTANT_CONVERSATION_STATE_CONFLICT");
        state = "ACTIVE";
        this.title = title == null || title.isBlank() ? null : title.trim();
        updatedAt = now;
    }

    public void archive(String actor, Instant now) {
        if (!ownerId.equals(actor)) throw new IllegalStateException("ASSISTANT_OWNER_MISMATCH");
        state = "PURGED";
        archivedAt = now;
        archivedBy = actor;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public String getTitle() { return title; }
    public String getLocale() { return locale; }
    public String getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getArchivedBy() { return archivedBy; }

    private static UUID require(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static Instant require(Instant value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
