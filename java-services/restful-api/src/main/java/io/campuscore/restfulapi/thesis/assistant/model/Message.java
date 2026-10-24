package io.campuscore.restfulapi.thesis.assistant.model;

import io.campuscore.restfulapi.thesis.assistant.dto.ThesisAssistantDtos;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Message(UUID id, String role, String content, String model, boolean degraded, String reasonCode, Instant createdAt, List<ThesisAssistantDtos.Citation> citations, String feedback) {
    public Message(UUID id, String role, String content, String model, boolean degraded, String reasonCode, Instant createdAt, List<ThesisAssistantDtos.Citation> citations) {
        this(id, role, content, model, degraded, reasonCode, createdAt, citations, null);
    }
}
