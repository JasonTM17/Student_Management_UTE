package io.campuscore.restfulapi.academic.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTOs for sections, rosters and grading views. */
public final class AcademicSectionReadDtos {
    private AcademicSectionReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record SectionListResponse(List<SectionResponse> data, PageMeta meta) {
    }

    public record DepartmentSummary(
            String id,
            String code,
            String name,
            String nameEn,
            String nameVi) {
    }

    public record CourseSummary(
            String id,
            String code,
            String name,
            String nameEn,
            String nameVi,
            int credits,
            DepartmentSummary department) {
    }

    public record SemesterSummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate) {
    }

    public record UserSummary(String id, String email, String firstName, String lastName) {
    }

    public record LecturerSummary(String id, String employeeId, UserSummary user) {
    }

    public record ClassroomSummary(String id, String building, String roomNumber) {
    }

    public record SectionScheduleResponse(
            String id,
            int dayOfWeek,
            String startTime,
            String endTime,
            String building,
            String roomNumber,
            ClassroomSummary classroom) {
    }

    public record SectionResponse(
            String id,
            String sectionId,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            String classroomId,
            int capacity,
            int enrolledCount,
            String status,
            Integer maxCredits,
            CourseSummary course,
            SemesterSummary semester,
            LecturerSummary lecturer,
            ClassroomSummary classroom,
            List<SectionScheduleResponse> schedules) {
    }

    public record LecturerScheduleResponse(
            String id,
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            int capacity,
            int enrolledCount,
            String departmentName,
            String departmentNameEn,
            String departmentNameVi,
            String status,
            List<SectionScheduleResponse> schedules) {
    }

    public record LecturerGradingSectionResponse(
            String id,
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            String departmentName,
            String departmentNameEn,
            String departmentNameVi,
            String semester,
            String semesterName,
            String semesterNameEn,
            String semesterNameVi,
            long enrolledCount,
            long gradedCount,
            long publishedCount,
            String gradeStatus,
            boolean canPublish) {
    }

    public record SectionGradesResponse(
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            int credits,
            String departmentName,
            String departmentNameEn,
            String departmentNameVi,
            String semester,
            String semesterNameEn,
            String semesterNameVi,
            String lecturerName,
            String status,
            List<SectionGradeEnrollmentResponse> enrollments) {
    }

    public record SectionGradeEnrollmentResponse(
            String id,
            String studentId,
            String studentName,
            String studentCode,
            String email,
            BigDecimal finalGrade,
            String letterGrade,
            String gradeStatus,
            String enrollmentStatus) {
    }
}
