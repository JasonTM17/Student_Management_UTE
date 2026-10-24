package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.UUID;

public record FeedbackResponse(UUID messageId, String rating, String reason, boolean removed) { }
