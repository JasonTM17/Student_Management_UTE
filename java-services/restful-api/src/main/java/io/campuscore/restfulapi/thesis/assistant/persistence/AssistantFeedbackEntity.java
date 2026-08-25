package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Owner-scoped feedback row for a committed assistant message. */
@Entity
@Table(name = "chat_message_feedback", schema = "assistant")
public class AssistantFeedbackEntity {
    @EmbeddedId
    private AssistantFeedbackId id;
    @Column(name = "rating", nullable = false, length = 4) private String rating;
    @Column(name = "reason", length = 16) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected AssistantFeedbackEntity() { }

    public AssistantFeedbackEntity(AssistantFeedbackId id, String rating, String reason, Instant now) {
        this.id = id;
        this.rating = rating;
        this.reason = reason;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public AssistantFeedbackId getId() { return id; }
    public String getRating() { return rating; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
