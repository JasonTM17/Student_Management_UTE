package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record StreamMeta(UUID requestId, UUID clientRequestId, UUID turnId, UUID conversationId, String model, String locale) implements StreamEvent {
    @JsonProperty("type") public String type() { return "meta"; }
}
