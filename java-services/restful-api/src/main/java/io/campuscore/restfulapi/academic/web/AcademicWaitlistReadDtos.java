package io.campuscore.restfulapi.academic.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** Legacy-compatible DTOs for the read-only academic waitlist candidate. */
public final class AcademicWaitlistReadDtos {
    private AcademicWaitlistReadDtos() {
    }

    public record PageMeta(long total, int page, int limit, int totalPages) {
    }

    public record UserSummary(String id, String email, String firstName, String lastName) {
    }

    public record StudentSummary(String id, String studentId, UserSummary user) {
    }

    public record DepartmentSummary(String id, String code, String name, String nameEn, String nameVi) {
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

    public record ClassroomSummary(String id, String building, String roomNumber) {
    }

    public record SectionScheduleResponse(
            String id,
            int dayOfWeek,
            String startTime,
            String endTime,
            ClassroomSummary classroom) {
    }

    public record SectionSummary(
            String id,
            String sectionNumber,
            CourseSummary course,
            SemesterSummary semester,
            int capacity,
            int enrolledCount,
            String status,
            List<SectionScheduleResponse> schedules) {
    }

    public record WaitlistResponse(
            String id,
            String studentId,
            String sectionId,
            int position,
            String status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant addedAt,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX", timezone = "UTC")
            Instant convertedAt,
            StudentSummary student,
            SectionSummary section) {
    }

    public record WaitlistListResponse(List<WaitlistResponse> data, PageMeta meta) {
    }
}
