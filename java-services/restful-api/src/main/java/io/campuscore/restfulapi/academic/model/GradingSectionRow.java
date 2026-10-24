package io.campuscore.restfulapi.academic.model;

public record GradingSectionRow(
        String id,
        String sectionNumber,
        String courseCode,
        String courseName,
        String courseNameEn,
        String courseNameVi,
        int credits,
        String departmentName,
        String departmentNameEn,
        String departmentNameVi,
        String semesterName,
        String semesterNameEn,
        String semesterNameVi,
        long enrolledCount,
        long gradedCount,
        long publishedCount) {
}
