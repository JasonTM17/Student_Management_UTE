package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;

public record StudentGradeRow(String enrollmentId, String studentId, String studentName, String studentNumber,
        String sectionId, String sectionNumber, String courseCode, String courseName, String courseNameEn,
        String courseNameVi, String semester, String semesterNameEn, String semesterNameVi, BigDecimal finalGrade,
        String letterGrade, String gradeStatus, String gradeId, String gradeItemId, String gradeItemName,
        String gradeItemType, BigDecimal score, BigDecimal maxScore, BigDecimal weight) {
}
