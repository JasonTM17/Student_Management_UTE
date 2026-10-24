package io.campuscore.restfulapi.thesis.dto;

import java.util.UUID;

public record GradingTask(
        UUID topicId,
        String title,
        UUID councilId,
        String councilName,
        String roundName,
        String roundStatus,
        int myScoreRows) { }
