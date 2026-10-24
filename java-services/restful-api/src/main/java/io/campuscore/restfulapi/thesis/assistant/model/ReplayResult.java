package io.campuscore.restfulapi.thesis.assistant.model;

import io.campuscore.restfulapi.thesis.assistant.dto.ThesisAssistantDtos.Citation;
import java.util.List;
import java.util.UUID;

public record ReplayResult(UUID conversationId, UUID messageId, String answer, String model, boolean degraded, String reasonCode, List<Citation> citations, boolean replayed, String terminalStatus) { }
