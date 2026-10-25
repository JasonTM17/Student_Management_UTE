package io.campuscore.restfulapi.thesis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to thesis.thesis_topic_supervisor.
 */
@Entity
@Table(name = "thesis_topic_supervisor", schema = "thesis")
public class ThesisTopicSupervisor {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private UUID topicId;

    @Column(name = "lecturer_id", nullable = false, length = 120)
    private String lecturerId;

    @Column(name = "supervisor_order", nullable = false)
    private int supervisorOrder;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ThesisTopicSupervisor() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public String getLecturerId() {
        return lecturerId;
    }

    public int getSupervisorOrder() {
        return supervisorOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ThesisTopicSupervisor other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
