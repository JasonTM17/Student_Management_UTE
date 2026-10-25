package io.campuscore.restfulapi.academic.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.List;

/** DTOs for academic attendance reads. */
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

    /**
     * Per-section totals plus the advisory exam-eligibility signal. The signal
     * never blocks anything: it is informational only. {@code examEligibility}
     * is {@code OK}, {@code AT_RISK} (countable sessions reached
     * {@code minSessions} and the rate is under {@code minRate}), or
     * {@code INSUFFICIENT_DATA} when fewer than {@code minSessions} countable
     * records exist.
     */
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
            int attendanceRate,
            long countableSessions,
            double minRate,
            int minSessions,
            boolean atRisk,
            String examEligibility) {
    }

    /**
     * One roster student whose advisory exam-eligibility signal is AT_RISK,
     * listed with the rate that put them there.
     */
    public record AttendanceRiskEntry(
            String studentId,
            String studentCode,
            String firstName,
            String lastName,
            long countableSessions,
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
            int attendanceRate,
            double minRate,
            int minSessions,
            List<AttendanceRiskEntry> atRiskStudents) {
    }

    public record AttendanceRosterEntry(
            String studentId,
            String studentCode,
            String studentEmail,
            String firstName,
            String lastName,
            String enrollmentStatus) {
    }
}
