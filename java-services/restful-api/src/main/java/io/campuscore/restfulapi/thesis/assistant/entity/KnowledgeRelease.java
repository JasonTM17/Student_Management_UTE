package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity mapping to assistant.knowledge_release.
 */
@Entity
@Table(name = "knowledge_release", schema = "assistant")
public class KnowledgeRelease {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "corpus_version", nullable = false, length = 120)
    private String corpusVersion;

    @Column(name = "corpus_hash", nullable = false, length = 64)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String corpusHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(nullable = false, length = 24)
    private String source;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(nullable = false, insertable = false, updatable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    private String manifest;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "previous_release_id")
    private UUID previousReleaseId;

    protected KnowledgeRelease() {
    }

    public UUID getId() {
        return id;
    }

    public String getCorpusVersion() {
        return corpusVersion;
    }

    public String getCorpusHash() {
        return corpusHash;
    }

    public int getRowCount() {
        return rowCount;
    }

    public String getSource() {
        return source;
    }

    public String getStatus() {
        return status;
    }

    public String getManifest() {
        return manifest;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public UUID getPreviousReleaseId() {
        return previousReleaseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeRelease other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
