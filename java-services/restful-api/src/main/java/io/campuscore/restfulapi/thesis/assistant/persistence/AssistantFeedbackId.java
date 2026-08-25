package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AssistantFeedbackId implements Serializable {
    @Column(name = "message_id", nullable = false)
    private UUID messageId;
    @Column(name = "owner_id", nullable = false, length = 120)
    private String ownerId;

    protected AssistantFeedbackId() { }

    public AssistantFeedbackId(UUID messageId, String ownerId) {
        this.messageId = Objects.requireNonNull(messageId, "messageId");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
    }

    public UUID getMessageId() { return messageId; }
    public String getOwnerId() { return ownerId; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof AssistantFeedbackId that)) return false;
        return Objects.equals(messageId, that.messageId) && Objects.equals(ownerId, that.ownerId);
    }

    @Override
    public int hashCode() { return Objects.hash(messageId, ownerId); }
}
