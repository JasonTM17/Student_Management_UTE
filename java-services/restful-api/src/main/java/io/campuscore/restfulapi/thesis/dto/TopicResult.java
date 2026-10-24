package io.campuscore.restfulapi.thesis.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TopicResult(UUID topicId, BigDecimal finalScore, String finalizedBy, Instant finalizedAt, String status) { }
