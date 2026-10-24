package io.campuscore.restfulapi.thesis.dto;

import java.time.Instant;
import java.util.UUID;

public record ReportResponse(
        UUID groupId,
        String title,
        String url,
        String note,
        String submittedBy,
        Instant submittedAt,
        Instant updatedAt,
        String fileName,
        String fileType,
        Long fileSize) { }
