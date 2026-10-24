package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record StreamError(String code, boolean retryable) implements StreamEvent {
    @JsonProperty("type") public String type() { return "error"; }
}
