package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.knowledge_runtime_state.
 */
@Entity
@Table(name = "knowledge_runtime_state", schema = "assistant")
public class KnowledgeRuntimeState {

    @Id
    @Column(nullable = false)
    private boolean singleton;

    @Column(name = "active_release_id")
    private UUID activeReleaseId;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected KnowledgeRuntimeState() {
    }

    public boolean isSingleton() {
        return singleton;
    }

    public UUID getActiveReleaseId() {
        return activeReleaseId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeRuntimeState other)) {
            return false;
        }
        return Objects.equals(singleton, other.singleton);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(singleton);
    }
}
