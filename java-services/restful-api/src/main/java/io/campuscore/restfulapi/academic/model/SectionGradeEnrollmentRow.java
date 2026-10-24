package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;

public record SectionGradeEnrollmentRow(
        String id,
        String studentId,
        String studentName,
        String studentCode,
        String email,
        BigDecimal processScore,
        BigDecimal finalExamScore,
        BigDecimal finalGrade,
        String letterGrade,
        String gradeStatus,
        String enrollmentStatus) {
}
