package io.campuscore.restfulapi.thesis.assistant.dto;

public record ThesisAssistantStreamError(String code, boolean retryable) {
    @com.fasterxml.jackson.annotation.JsonProperty("type")
    public String type() { return "error"; }
}
