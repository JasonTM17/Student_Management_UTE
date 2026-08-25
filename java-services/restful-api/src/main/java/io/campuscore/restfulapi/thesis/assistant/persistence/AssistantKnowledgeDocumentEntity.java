package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA aggregate root for the public assistant knowledge document. */
@Entity
@Table(name = "knowledge_document", schema = "assistant")
public class AssistantKnowledgeDocumentEntity {

    @Id
    private UUID id;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "archived_by", length = 120)
    private String archivedBy;

    protected AssistantKnowledgeDocumentEntity() {
    }

    public static AssistantKnowledgeDocumentEntity draft(UUID id, String slug, String locale,
            String title, String content, String source, int priority, Instant now) {
        AssistantKnowledgeDocumentEntity entity = new AssistantKnowledgeDocumentEntity();
        entity.id = require(id, "id");
        entity.replace(slug, locale, title, content, source, priority, now);
        entity.active = true;
        entity.visibility = "PUBLIC";
        entity.createdAt = now;
        return entity;
    }

    public void replace(String slug, String locale, String title, String content,
            String source, int priority, Instant now) {
        this.slug = requireText(slug, "slug");
        this.locale = requireText(locale, "locale");
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.source = requireText(source, "source");
        this.priority = checkedPriority(priority);
        this.updatedAt = now;
    }

    public void publish(Instant now) {
        this.active = true;
        this.visibility = "PUBLIC";
        this.updatedAt = now;
    }

    public void archive(String actor, Instant now) {
        this.active = false;
        this.archivedAt = now;
        this.archivedBy = requireText(actor, "archivedBy");
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getLocale() { return locale; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public short getPriority() { return priority; }
    public boolean isActive() { return active; }
    public String getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public String getArchivedBy() { return archivedBy; }

    private static UUID require(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }

    private static short checkedPriority(int value) {
        if (value < 1 || value > 1000) throw new IllegalArgumentException("priority must be between 1 and 1000");
        return (short) value;
    }
}
