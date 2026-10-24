package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.UUID;

public record ThesisAssistantInternalCancelResponse(UUID clientRequestId, String status) { }
