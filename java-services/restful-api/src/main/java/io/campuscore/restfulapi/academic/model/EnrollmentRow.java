package io.campuscore.restfulapi.academic.model;

import java.math.BigDecimal;
import java.time.Instant;

public record EnrollmentRow(String id, String studentId, String sectionId, String semesterId, String status,
        Instant enrolledAt, Instant droppedAt, String gradeStatus, BigDecimal finalGrade, String letterGrade,
        Instant createdAt, Instant updatedAt, String studentNumber, String studentUserId, String studentEmail,
        String studentFirstName, String studentLastName, String sectionNumber, int capacity, int enrolledCount,
        String sectionStatus, String courseId, String courseCode, String courseName, String courseNameEn,
        String courseNameVi, int credits, String semesterIdValue, String semesterName, String semesterNameEn,
        String semesterNameVi, Instant semesterStartDate, String lecturerId, String employeeId, String lecturerUserId,
        String lecturerEmail, String lecturerFirstName, String lecturerLastName, String registrationSource) {
}
