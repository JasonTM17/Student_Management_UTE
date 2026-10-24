package io.campuscore.restfulapi.thesis.assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeDocumentView(UUID documentId, UUID revisionId, int version, String state, String domain, String locale, String slug, String title, String content, String source, int priority, String createdBy, String reviewedBy, Instant createdAt, Instant publishedAt) { }
