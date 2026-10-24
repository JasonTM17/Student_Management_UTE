package io.campuscore.restfulapi.thesis.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus, java.time.Instant resetAt) implements StreamEvent {
    /** Pre-resetAt arity retained for existing emitters; {@code resetAt} stays null. */
    public StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus) {
        this(messageId, reasonCode, degraded, terminalStatus, null);
    }

    @JsonProperty("type") public String type() { return "done"; }
}
