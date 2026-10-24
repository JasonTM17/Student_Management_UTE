package io.campuscore.restfulapi.academic.model;

import java.time.Instant;

public record AttendanceInsertRow(
        String id,
        String studentId,
        String sectionId,
        Instant date,
        String status,
        String notes,
        Instant createdAt,
        Instant updatedAt) {
}
