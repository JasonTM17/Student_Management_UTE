package io.campuscore.restfulapi.thesis.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ScoreResponse(UUID topicId, String lecturerId, String component, BigDecimal score, String comment) { }
