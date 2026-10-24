package io.campuscore.restfulapi.thesis.dto;

import java.util.UUID;

public record CouncilAssignment(
        UUID councilId,
        String name,
        String memberRole,
        UUID roundId,
        String roundName,
        String roundStatus,
        java.time.Instant reportDate,
        java.time.Instant gvpbDeadline,
        int topicCount) { }
