package io.campuscore.restfulapi.thesis.assistant.model;

import java.util.UUID;

/** Locked latest-revision row read by the knowledge governance workflow. */
public record KnowledgeRevisionRow(UUID id, int version, String createdBy) { }
