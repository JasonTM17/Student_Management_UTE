package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_topic_score.
 */
@Entity
@Table(name = "thesis_topic_score", schema = "thesis")
public class ThesisTopicScore {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "council_id")
    private UUID councilId;

    @Column(name = "lecturer_id", nullable = false, length = 120)
    private String lecturerId;

    @Column(nullable = false, length = 60, insertable = false, updatable = false)
    private String component;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal score;

    @Column(name = "graded_at", nullable = false, insertable = false, updatable = false)
    private Instant gradedAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 1000)
    private String comment;

    protected ThesisTopicScore() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getCouncilId() {
        return councilId;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public String getComponent() {
        return component;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Instant getGradedAt() {
        return gradedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisTopicScore other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
