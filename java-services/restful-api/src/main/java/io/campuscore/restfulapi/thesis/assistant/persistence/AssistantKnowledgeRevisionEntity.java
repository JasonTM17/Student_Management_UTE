package io.campuscore.restfulapi.thesis.assistant.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Immutable-versioned governance revision for a knowledge document. */
@Entity
@Table(name = "knowledge_document_revision", schema = "assistant")
public class AssistantKnowledgeRevisionEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    /** Business revision number; state transitions are guarded by repository locks. */
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "state", nullable = false, length = 24)
    private String state;

    @Column(name = "locale", nullable = false, length = 8)
    private String locale;

    @Column(name = "slug", nullable = false, length = 180)
    private String slug;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "source", nullable = false)
    private String source;

    @Column(name = "priority", nullable = false)
    private short priority;

    @Column(name = "created_by", nullable = false, length = 120)
    private String createdBy;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected AssistantKnowledgeRevisionEntity() {
    }

    public static AssistantKnowledgeRevisionEntity draft(UUID id, UUID documentId, int version,
            String locale, String slug, String title, String content, String source,
            int priority, String actor, Instant now) {
        AssistantKnowledgeRevisionEntity entity = new AssistantKnowledgeRevisionEntity();
        entity.id = require(id, "id");
        entity.documentId = require(documentId, "documentId");
        entity.version = version;
        entity.state = "DRAFT";
        entity.replace(locale, slug, title, content, source, priority);
        entity.createdBy = requireText(actor, "createdBy");
        entity.createdAt = now;
        return entity;
    }

    public void replace(String locale, String slug, String title, String content,
            String source, int priority) {
        if (!"DRAFT".equals(state)) throw new IllegalStateException("KNOWLEDGE_STATE_CONFLICT");
        this.locale = requireText(locale, "locale");
        this.slug = requireText(slug, "slug");
        this.title = requireText(title, "title");
        this.content = requireText(content, "content");
        this.source = requireText(source, "source");
        if (priority < 1 || priority > 1000) throw new IllegalArgumentException("priority must be between 1 and 1000");
        this.priority = (short) priority;
    }

    public void submit() {
        transition("DRAFT", "PENDING_REVIEW");
    }

    public void publish(String reviewer, Instant now) {
        if (!"PENDING_REVIEW".equals(state)) throw new IllegalStateException("KNOWLEDGE_STATE_CONFLICT");
        String cleanReviewer = requireText(reviewer, "reviewedBy");
        if (cleanReviewer.equals(createdBy)) {
            throw new IllegalStateException("KNOWLEDGE_SECOND_REVIEW_REQUIRED");
        }
        this.state = "PUBLISHED";
        this.reviewedBy = cleanReviewer;
        this.publishedAt = now;
    }

    public void archive() {
        if ("PUBLISHED".equals(state) || "PENDING_REVIEW".equals(state)) state = "ARCHIVED";
    }

    private void transition(String expected, String next) {
        if (!expected.equals(state)) throw new IllegalStateException("KNOWLEDGE_STATE_CONFLICT");
        state = next;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public int getVersion() { return version; }
    public String getState() { return state; }
    public String getLocale() { return locale; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getSource() { return source; }
    public short getPriority() { return priority; }
    public String getCreatedBy() { return createdBy; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }

    private static UUID require(UUID value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value.trim();
    }
}
