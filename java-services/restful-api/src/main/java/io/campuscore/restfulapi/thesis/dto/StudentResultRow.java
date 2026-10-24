package io.campuscore.restfulapi.thesis.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record StudentResultRow(
        UUID groupId,
        String topicTitle,
        BigDecimal finalScore,
        String councilName,
        String leaderStudentId,
        String resultStatus,
        String resultReason,
        List<ScoreComment> comments) { }
