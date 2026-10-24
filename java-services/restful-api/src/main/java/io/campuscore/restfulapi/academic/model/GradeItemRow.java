package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;
import java.time.Instant;

public record GradeItemRow(String id, String sectionId, String name, String type, BigDecimal maxScore, BigDecimal weight,
        Instant gradedAt, Instant createdAt, String sectionNumber, int capacity, int enrolledCount, String sectionStatus,
        String courseId, String courseCode, String courseName, String courseNameEn, String courseNameVi, int credits,
        String semesterId, String semesterName, String semesterNameEn, String semesterNameVi, Instant semesterStartDate) {
}
