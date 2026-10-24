package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;

public record GradeSummaryRow(String id, String courseCode, String courseName, String courseNameEn, String courseNameVi,
        int credits, String sectionCode, String lecturerName, String semester, String semesterNameEn, String semesterNameVi,
        String semesterId, BigDecimal processScore, BigDecimal finalExamScore, BigDecimal finalGrade,
        String letterGrade, String gradeStatus, String enrollmentStatus) {
}
