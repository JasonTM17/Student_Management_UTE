package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record StreamDelta(int sequence, String text, List<String> sourceIds) implements StreamEvent {
    @JsonProperty("type") public String type() { return "delta"; }
}
