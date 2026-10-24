package io.campuscore.restfulapi.thesis.assistant.dto;

import java.util.UUID;

public record CancelResponse(UUID clientRequestId, String status) { }
