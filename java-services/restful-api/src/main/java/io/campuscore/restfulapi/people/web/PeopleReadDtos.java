package io.campuscore.restfulapi.people.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the read-only people profile candidate. */
public final class PeopleReadDtos {

    private PeopleReadDtos() {
    }

    public record UserSummary(
            String id,
            String email,
            String firstName,
            String lastName) {
    }

    public record DepartmentSummary(
            String id,
            String code,
            String name) {
    }

    public record CurriculumSummary(
            String id,
            String code,
            String name,
            DepartmentSummary department) {
    }

    public record StudentResponse(
            String id,
            String userId,
            String studentId,
            String curriculumId,
            int year,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant admissionDate,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            UserSummary user,
            CurriculumSummary curriculum) {
    }

    public record LecturerResponse(
            String id,
            String userId,
            String departmentId,
            String employeeId,
            String title,
            String specialization,
            String office,
            String phone,
            boolean isActive,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant updatedAt,
            UserSummary user,
            DepartmentSummary department) {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record StudentListResponse(List<StudentResponse> data, PageMeta meta) {
    }

    public record LecturerListResponse(List<LecturerResponse> data, PageMeta meta) {
    }
}
