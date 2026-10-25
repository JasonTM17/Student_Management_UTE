package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_council_topic.
 */
@Entity
@Table(name = "thesis_council_topic", schema = "thesis")
public class ThesisCouncilTopic {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "council_id", nullable = false)
    private UUID councilId;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "assigned_by", nullable = false, length = 120)
    private String assignedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ThesisCouncilTopic() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getCouncilId() {
        return councilId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisCouncilTopic other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
