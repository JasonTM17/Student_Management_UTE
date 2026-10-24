package io.campuscore.restfulapi.academic.model;

import java.time.Instant;

public record AttendanceRow(
        String id,
        String studentId,
        String sectionId,
        Instant date,
        String status,
        String notes,
        Instant createdAt,
        String studentNumber,
        String studentUserId,
        String studentEmail,
        String studentFirstName,
        String studentLastName,
        String sectionNumber,
        String semesterId,
        String courseId,
        String courseCode,
        String courseName,
        String courseNameEn,
        String courseNameVi) {
}
