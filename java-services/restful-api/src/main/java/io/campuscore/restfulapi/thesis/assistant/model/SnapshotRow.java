package io.campuscore.restfulapi.thesis.assistant.model;

import java.time.Instant;

public record SnapshotRow(KnowledgeDocument document, int priority, Instant publishedAt) { }
