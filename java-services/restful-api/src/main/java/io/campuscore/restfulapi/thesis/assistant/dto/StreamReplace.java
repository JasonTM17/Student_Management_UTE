package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StreamReplace(String text, List<String> sourceIds, String reasonCode) implements StreamEvent {
    @JsonProperty("type") public String type() { return "replace"; }
}
