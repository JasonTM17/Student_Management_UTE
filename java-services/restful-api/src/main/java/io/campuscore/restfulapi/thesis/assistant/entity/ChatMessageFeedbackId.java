package io.campuscore.restfulapi.thesis.assistant.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link ChatMessageFeedback}.
 */
public class ChatMessageFeedbackId implements Serializable {

    private UUID messageId;
    private String ownerId;

    public ChatMessageFeedbackId() {
    }

    public ChatMessageFeedbackId(UUID messageId, String ownerId) {
        this.messageId = messageId;
        this.ownerId = ownerId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ChatMessageFeedbackId other)) {
            return false;
        }
        return Objects.equals(messageId, other.messageId) && Objects.equals(ownerId, other.ownerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, ownerId);
    }
}
