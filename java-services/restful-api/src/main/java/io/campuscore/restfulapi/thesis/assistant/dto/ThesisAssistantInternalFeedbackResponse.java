package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.UUID;

public record ThesisAssistantInternalFeedbackResponse(UUID messageId, String rating, String reason, boolean removed) { }
