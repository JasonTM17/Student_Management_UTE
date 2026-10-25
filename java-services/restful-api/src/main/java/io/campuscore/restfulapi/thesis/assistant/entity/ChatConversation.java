package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.chat_conversation.
 */
@Entity
@Table(name = "chat_conversation", schema = "assistant")
public class ChatConversation {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(length = 160)
    private String title;

    @Column(nullable = false, length = 2, insertable = false, updatable = false)
    private String locale;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false, insertable = false, updatable = false)
    private Instant expiresAt;

    @Column(nullable = false, length = 16, insertable = false, updatable = false)
    private String state;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by", length = 120)
    private String archivedBy;

    @Column(nullable = false, length = 16, insertable = false, updatable = false)
    private String scope;

    protected ChatConversation() {
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getLocale() {
        return locale;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getState() {
        return state;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public String getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatConversation other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
