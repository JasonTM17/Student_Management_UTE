package io.campuscore.restfulapi.thesis.assistant.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentItem(
        UUID messageId,
        String rating,
        String reason,
        Instant updatedAt,
        String questionPreview,
        String answerPreview,
        String model,
        boolean degraded,
        String reasonCode) { }
