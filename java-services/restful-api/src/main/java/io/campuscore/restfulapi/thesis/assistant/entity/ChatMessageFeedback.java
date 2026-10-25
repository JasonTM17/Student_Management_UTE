package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.chat_message_feedback.
 */
@Entity
@IdClass(ChatMessageFeedbackId.class)
@Table(name = "chat_message_feedback", schema = "assistant")
public class ChatMessageFeedback {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Id
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    @Column(nullable = false, length = 4)
    private String rating;

    @Column(length = 16)
    private String reason;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected ChatMessageFeedback() {
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getRating() {
        return rating;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessageFeedback other)) {
            return false;
        }
        return Objects.equals(messageId, other.messageId) && Objects.equals(ownerId, other.ownerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, ownerId);
    }
}
