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
 * JPA entity mapping to assistant.knowledge_runtime_document.
 */
@Entity
@IdClass(KnowledgeRuntimeDocumentId.class)
@Table(name = "knowledge_runtime_document", schema = "assistant")
public class KnowledgeRuntimeDocument {

    @Id
    @Column(name = "release_id", nullable = false)
    private UUID releaseId;

    @Id
    @Column(name = "source_id", nullable = false, length = 180)
    private String sourceId;

    @Column(name = "revision_id")
    private UUID revisionId;

    @Column(nullable = false, insertable = false, updatable = false)
    private int version;

    @Column(nullable = false, length = 48)
    private String domain;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false, insertable = false, updatable = false)
    private short priority;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean active;

    @Column(nullable = false, length = 32, insertable = false, updatable = false)
    private String visibility;

    @Column(name = "published_at", nullable = false, insertable = false, updatable = false)
    private Instant publishedAt;

    protected KnowledgeRuntimeDocument() {
    }

    public UUID getReleaseId() {
        return releaseId;
    }

    public String getSourceId() {
        return sourceId;
    }

    public UUID getRevisionId() {
        return revisionId;
    }

    public int getVersion() {
        return version;
    }

    public String getDomain() {
        return domain;
    }

    public String getSlug() {
        return slug;
    }

    public String getLocale() {
        return locale;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public short getPriority() {
        return priority;
    }

    public boolean isActive() {
        return active;
    }

    public String getVisibility() {
        return visibility;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeRuntimeDocument other)) {
            return false;
        }
        return Objects.equals(releaseId, other.releaseId) && Objects.equals(sourceId, other.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseId, sourceId);
    }
}
