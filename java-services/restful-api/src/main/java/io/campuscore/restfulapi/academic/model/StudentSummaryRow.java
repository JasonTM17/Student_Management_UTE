package io.campuscore.restfulapi.academic.model;

public record StudentSummaryRow(
        String sectionId,
        String courseCode,
        String courseName,
        String courseNameEn,
        String courseNameVi,
        long total,
        long present,
        long absent,
        long late,
        long excused) {
}
