package io.campuscore.restfulapi.academic.model;

import java.time.Instant;

public record SectionRow(
        String id,
        String sectionNumber,
        String courseId,
        String semesterId,
        String lecturerId,
        String classroomId,
        int capacity,
        int enrolledCount,
        String sectionStatus,
        String courseCode,
        String courseName,
        String courseNameEn,
        String courseNameVi,
        int credits,
        String departmentId,
        String departmentCode,
        String departmentName,
        String departmentNameEn,
        String departmentNameVi,
        String semesterName,
        String semesterNameEn,
        String semesterNameVi,
        Instant semesterStartDate,
        String employeeId,
        String lecturerUserId,
        String lecturerEmail,
        String lecturerFirstName,
        String lecturerLastName,
        String building,
        String roomNumber,
        long activeEnrollmentCount) {
}
