package io.campuscore.restfulapi.thesis.assistant.model;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, String title, String locale, Instant createdAt, Instant updatedAt, @com.fasterxml.jackson.annotation.JsonIgnore String ownerId, String scope) {
    public Conversation(UUID id, String title, String locale, Instant createdAt, Instant updatedAt) { this(id, title, locale, createdAt, updatedAt, null, "academic"); }
    public Conversation(UUID id, String title, String locale, Instant createdAt, Instant updatedAt, String ownerId) { this(id, title, locale, createdAt, updatedAt, ownerId, "academic"); }
}
