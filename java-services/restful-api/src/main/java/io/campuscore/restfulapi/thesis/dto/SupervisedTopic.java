package io.campuscore.restfulapi.thesis.dto;

import java.util.UUID;

public record SupervisedTopic(
        UUID topicId,
        String title,
        String topicStatus,
        UUID roundId,
        String roundName,
        String roundStatus,
        java.time.Instant reportDate,
        int groupCount,
        int pendingGroupCount) { }
