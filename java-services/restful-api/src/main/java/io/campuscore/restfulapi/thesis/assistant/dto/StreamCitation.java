package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.campuscore.restfulapi.thesis.assistant.dto.ThesisAssistantDtos.Citation;

public record StreamCitation(Citation citation) implements StreamEvent {
    @JsonProperty("type") public String type() { return "citation"; }
}
