package io.campuscore.restfulapi.thesis.assistant.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@link KnowledgeRuntimeDocument}.
 */
public class KnowledgeRuntimeDocumentId implements Serializable {

    private UUID releaseId;
    private String sourceId;

    public KnowledgeRuntimeDocumentId() {
    }

    public KnowledgeRuntimeDocumentId(UUID releaseId, String sourceId) {
        this.releaseId = releaseId;
        this.sourceId = sourceId;
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public String getSourceId() {
        return sourceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeRuntimeDocumentId other)) {
            return false;
        }
        return Objects.equals(releaseId, other.releaseId) && Objects.equals(sourceId, other.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseId, sourceId);
    }
}
