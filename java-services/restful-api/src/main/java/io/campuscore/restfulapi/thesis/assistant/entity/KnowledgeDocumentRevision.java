package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.knowledge_document_revision.
 */
@Entity
@Table(name = "knowledge_document_revision", schema = "assistant")
public class KnowledgeDocumentRevision {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, length = 24)
    private String state;

    @Column(nullable = false, length = 8)
    private String locale;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false, insertable = false, updatable = false)
    private short priority;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false, length = 48, insertable = false, updatable = false)
    private String domain;

    protected KnowledgeDocumentRevision() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getVersion() {
        return version;
    }

    public String getState() {
        return state;
    }

    public String getLocale() {
        return locale;
    }

    public String getSlug() {
        return slug;
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

    public String getCreatedBy() {
        return createdBy;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeDocumentRevision other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
