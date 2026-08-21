package io.campuscore.restfulapi.academic.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the read-only academic attendance candidate. */
public final class AcademicAttendanceReadDtos {
    private AcademicAttendanceReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record UserSummary(String id, String email, String firstName, String lastName) {
    }

    public record StudentSummary(String id, String studentId, UserSummary user) {
    }

    public record CourseSummary(String id, String code, String name, String nameEn, String nameVi) {
    }

    public record SectionSummary(
            String id,
            String sectionNumber,
            String semesterId,
            CourseSummary course) {
    }

    public record AttendanceResponse(
            String id,
            String studentId,
            String sectionId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant date,
            String status,
            String notes,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant createdAt,
            StudentSummary student,
            SectionSummary section) {
    }

    public record AttendanceListResponse(List<AttendanceResponse> data, PageMeta meta) {
    }

    public record StudentAttendanceSummaryResponse(
            String sectionId,
            String courseCode,
            String courseName,
            String courseNameEn,
            String courseNameVi,
            long total,
            long present,
            long absent,
            long late,
            long excused,
            int attendanceRate) {
    }

    public record SectionAttendanceSummaryResponse(
            String sectionId,
            long totalSessions,
            long totalRecords,
            long present,
            long absent,
            long late,
            long excused,
            int attendanceRate) {
    }
}
