package io.campuscore.restfulapi.academic.model;

public record CourseProgressRow(
        String courseId,
        int progressLevel,
        java.math.BigDecimal finalGrade,
        String letterGrade) {
}
