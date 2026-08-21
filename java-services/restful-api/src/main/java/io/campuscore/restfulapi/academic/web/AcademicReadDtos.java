package io.campuscore.restfulapi.academic.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the read-only academic catalog candidate. */
public final class AcademicReadDtos {

    private AcademicReadDtos() {
    }

    public record AcademicYearSummary(
            String id,
            int year,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant endDate,
            boolean isCurrent,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt) {
    }

    public record DepartmentSummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String facultyId,
            boolean isActive) {
    }

    public record FacultySummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String dean,
            String phone,
            String email,
            String building,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            boolean isActive) {
    }

    public record FacultyDepartmentSummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String chair,
            String phone,
            String email,
            String building,
            String facultyId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            boolean isActive) {
    }

    public record FacultyResponse(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String dean,
            String phone,
            String email,
            String building,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            boolean isActive,
            List<FacultyDepartmentSummary> departments) {
    }

    public record DepartmentLecturerSummary(
            String id,
            String userId,
            String departmentId,
            String employeeId,
            String title,
            String specialization,
            String office,
            String phone,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            boolean isActive) {
    }

    public record DepartmentResponse(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String code,
            String description,
            String descriptionEn,
            String descriptionVi,
            String chair,
            String phone,
            String email,
            String building,
            String facultyId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            boolean isActive,
            FacultySummary faculty,
            List<DepartmentLecturerSummary> lecturers) {
    }

    public record SemesterResponse(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String type,
            String academicYearId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant endDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant registrationStart,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant registrationEnd,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant addDropStart,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant addDropEnd,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            AcademicYearSummary academicYear) {
    }

    public record SemesterCatalogSummary(
            String id,
            String name,
            String nameEn,
            String nameVi,
            String type,
            String academicYearId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant endDate,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt) {
    }

    public record AcademicYearResponse(
            String id,
            int year,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant startDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant endDate,
            boolean isCurrent,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            List<SemesterCatalogSummary> semesters) {
    }

    public record CourseResponse(
            String id,
            String code,
            String name,
            String nameEn,
            String nameVi,
            String description,
            String descriptionEn,
            String descriptionVi,
            int credits,
            String departmentId,
            String semesterId,
            boolean isActive,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            DepartmentSummary department) {
    }

    public record ClassroomSectionSummary(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            String classroomId,
            int capacity,
            int enrolledCount,
            String status) {
    }

    public record ClassroomResponse(
            String id,
            String building,
            String roomNumber,
            int capacity,
            String type,
            boolean isActive,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            List<ClassroomSectionSummary> sections) {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record FacultyListResponse(List<FacultyResponse> data, PageMeta meta) {
    }

    public record DepartmentListResponse(List<DepartmentResponse> data, PageMeta meta) {
    }

    public record AcademicYearListResponse(List<AcademicYearResponse> data, PageMeta meta) {
    }

    public record SemesterListResponse(List<SemesterResponse> data, PageMeta meta) {
    }

    public record CourseListResponse(List<CourseResponse> data, PageMeta meta) {
    }

    public record ClassroomListResponse(List<ClassroomResponse> data, PageMeta meta) {
    }
}
