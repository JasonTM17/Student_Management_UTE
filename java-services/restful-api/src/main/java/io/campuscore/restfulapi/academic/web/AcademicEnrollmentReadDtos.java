package io.campuscore.restfulapi.academic.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the read-only academic enrollment/grade candidate. */
public final class AcademicEnrollmentReadDtos {
    private AcademicEnrollmentReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record UserSummary(String id, String email, String firstName, String lastName) {
    }

    public record StudentSummary(String id, String studentId, UserSummary user) {
    }

    public record LecturerSummary(String id, String employeeId, UserSummary user) {
    }

    public record ClassroomSummary(String id, String building, String roomNumber) {
    }

    public record CourseSummary(String id, String code, String name, String nameEn, String nameVi, int credits) {
    }

    public record SemesterSummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate) {
    }

    public record SectionScheduleResponse(String id, int dayOfWeek, String startTime, String endTime, ClassroomSummary classroom) {
    }

    public record SectionSummary(
            String id,
            String sectionNumber,
            CourseSummary course,
            SemesterSummary semester,
            LecturerSummary lecturer,
            int capacity,
            int enrolledCount,
            String status,
            List<SectionScheduleResponse> schedules) {
    }

    public record EnrollmentResponse(
            String id,
            String studentId,
            String sectionId,
            String semesterId,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant enrolledAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant droppedAt,
            String gradeStatus,
            BigDecimal finalGrade,
            String letterGrade,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            StudentSummary student,
            SectionSummary section,
            SemesterSummary semester) {
    }

    public record EnrollmentListResponse(List<EnrollmentResponse> data, PageMeta meta) {
    }

    public record GradeSummary(
            String id,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            String sectionCode,
            String lecturerName,
            String semester,
            String semesterNameEn,
            String semesterNameVi,
            String semesterId,
            BigDecimal finalGrade,
            String letterGrade,
            String gradeStatus,
            String enrollmentStatus) {
    }

    public record TranscriptSummary(BigDecimal cumulativeGpa, int totalCreditsEarned, int totalCreditsAttempted) {
    }

    public record TranscriptSemester(
            String semesterId,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            List<GradeSummary> records,
            BigDecimal gpa,
            int creditsEarned,
            int creditsAttempted) {
    }

    public record TranscriptResponse(TranscriptSummary summary, List<TranscriptSemester> semesters) {
    }

    public record GradeItemResponse(
            String id,
            String sectionId,
            String name,
            String type,
            BigDecimal maxScore,
            BigDecimal weight,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant gradedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            SectionSummary section) {
    }

    public record StudentGradeLine(
            String id,
            String gradeItemId,
            String gradeItemName,
            String gradeItemType,
            BigDecimal score,
            BigDecimal maxScore,
            BigDecimal weight) {
    }

    public record StudentGradeSectionRow(
            String enrollmentId,
            String studentId,
            String studentName,
            String studentNumber,
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            String semester,
            String semesterNameEn,
            String semesterNameVi,
            BigDecimal finalGrade,
            String letterGrade,
            String gradeStatus,
            List<StudentGradeLine> grades,
            BigDecimal calculatedTotal,
            BigDecimal totalWeight) {
    }

    public record EnrollmentGradeDetail(
            String id,
            String studentId,
            String studentName,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            String semester,
            String semesterNameEn,
            String semesterNameVi) {
    }

    public record StudentGradesByEnrollmentResponse(
            EnrollmentGradeDetail enrollment,
            List<StudentGradeLine> grades,
            BigDecimal calculatedTotal,
            BigDecimal totalWeight) {
    }
}
