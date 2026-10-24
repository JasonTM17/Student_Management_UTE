package io.campuscore.restfulapi.engagement.model;

import java.time.Instant;
import java.util.List;

public record UserVisibility(
        List<String> roles,
        String studentId,
        Integer studentYear,
        String lecturerId,
        Instant now) {
}
