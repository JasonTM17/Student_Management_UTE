package io.campuscore.restfulapi.thesis.assistant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping to assistant.knowledge_document.
 */
@Entity
@Table(name = "knowledge_document", schema = "assistant")
public class KnowledgeDocument {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String locale;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, columnDefinition = "text")
    private String source;

    @Column(nullable = false, insertable = false, updatable = false)
    private short priority;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(nullable = false, insertable = false, updatable = false)
    private boolean active;

    @Column(nullable = false, length = 32, insertable = false, updatable = false)
    private String visibility;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by", length = 120)
    private String archivedBy;

    @Column(nullable = false, length = 48, insertable = false, updatable = false)
    private String domain;

    protected KnowledgeDocument() {
    }

    public UUID getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isActive() {
        return active;
    }

    public String getVisibility() {
        return visibility;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public String getArchivedBy() {
        return archivedBy;
    }

    public String getDomain() {
        return domain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeDocument other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
