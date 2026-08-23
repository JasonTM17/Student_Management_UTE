package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicAttendanceReadRepository;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceReadRepository.AttendanceRow;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceReadRepository.SectionSummaryRow;
import io.campuscore.restfulapi.academic.repository.AcademicAttendanceReadRepository.StudentSummaryRow;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceListResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.SectionAttendanceSummaryResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.SectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.StudentAttendanceSummaryResponse;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.StudentSummary;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.UserSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Academic attendance query service. */
@Service
@Profile("persistence")
public class AcademicAttendanceReadService {
    public static final int MAX_PAGE_SIZE = 100;

    private final AcademicAttendanceReadRepository attendance;

    public AcademicAttendanceReadService(AcademicAttendanceReadRepository attendance) {
        this.attendance = attendance;
    }

    @Transactional(readOnly = true)
    public AttendanceListResponse findAll(
            int page,
            int limit,
            String sectionId,
            String studentId,
            String date) {
        requirePage(page, limit);
        String normalizedSectionId = normalizeOptional("sectionId", sectionId);
        String normalizedStudentId = normalizeOptional("studentId", studentId);
        Instant normalizedDate = normalizeDate(date);
        long total = attendance.countAll(normalizedSectionId, normalizedStudentId, normalizedDate);
        return new AttendanceListResponse(
                attendanceResponses(attendance.findAll(
                        offset(page, limit),
                        limit,
                        normalizedSectionId,
                        normalizedStudentId,
                        normalizedDate)),
                meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> findStudentAttendance(String studentId, String sectionId, String semesterId) {
        return attendanceResponses(attendance.findStudentAttendance(
                requireProfileId("studentId", studentId),
                normalizeOptional("sectionId", sectionId),
                normalizeOptional("semesterId", semesterId)));
    }

    @Transactional(readOnly = true)
    public List<StudentAttendanceSummaryResponse> findStudentAttendanceSummary(String studentId, String semesterId) {
        return attendance.studentSummary(
                        requireProfileId("studentId", studentId),
                        normalizeOptional("semesterId", semesterId))
                .stream()
                .map(AcademicAttendanceReadService::studentSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> findLecturerAttendance(String lecturerId, String sectionId, String date) {
        return attendanceResponses(attendance.findLecturerAttendance(
                requireProfileId("lecturerId", lecturerId),
                normalizeOptional("sectionId", sectionId),
                normalizeDate(date)));
    }

    @Transactional(readOnly = true)
    public List<AttendanceResponse> findSectionAttendance(String sectionId, String date) {
        return attendanceResponses(attendance.findSectionAttendance(
                normalizeRequired("sectionId", sectionId),
                normalizeDate(date)));
    }

    @Transactional(readOnly = true)
    public SectionAttendanceSummaryResponse findSectionAttendanceSummary(String sectionId) {
        String normalizedSectionId = normalizeRequired("sectionId", sectionId);
        SectionSummaryRow row = attendance.sectionSummary(normalizedSectionId);
        return new SectionAttendanceSummaryResponse(
                normalizedSectionId,
                row.totalSessions(),
                row.totalRecords(),
                row.present(),
                row.absent(),
                row.late(),
                row.excused(),
                rate(row.present() + row.late(), row.totalRecords()));
    }

    @Transactional(readOnly = true)
    public AttendanceResponse findOne(String id) {
        return attendance.findById(normalizeRequired("id", id))
                .map(AcademicAttendanceReadService::attendanceResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attendance record not found"));
    }

    private static List<AttendanceResponse> attendanceResponses(List<AttendanceRow> rows) {
        return rows.stream().map(AcademicAttendanceReadService::attendanceResponse).toList();
    }

    private static AttendanceResponse attendanceResponse(AttendanceRow row) {
        return new AttendanceResponse(
                row.id(),
                row.studentId(),
                row.sectionId(),
                row.date(),
                row.status(),
                row.notes(),
                row.createdAt(),
                new StudentSummary(
                        row.studentId(),
                        row.studentNumber(),
                        new UserSummary(
                                row.studentUserId(),
                                row.studentEmail(),
                                row.studentFirstName(),
                                row.studentLastName())),
                new SectionSummary(
                        row.sectionId(),
                        row.sectionNumber(),
                        row.semesterId(),
                        new CourseSummary(
                                row.courseId(),
                                row.courseCode(),
                                row.courseName(),
                                coalesce(row.courseNameEn(), row.courseName()),
                                coalesce(row.courseNameVi(), row.courseName()))));
    }

    private static StudentAttendanceSummaryResponse studentSummary(StudentSummaryRow row) {
        return new StudentAttendanceSummaryResponse(
                row.sectionId(),
                row.courseCode(),
                row.courseName(),
                coalesce(row.courseNameEn(), row.courseName()),
                coalesce(row.courseNameVi(), row.courseName()),
                row.total(),
                row.present(),
                row.absent(),
                row.late(),
                row.excused(),
                rate(row.present(), row.total()));
    }

    private static String requireProfileId(String name, String value) {
        String normalized = normalizeOptional(name, value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, name + " profile claim is required");
        }
        return normalized;
    }

    private static String normalizeRequired(String name, String value) {
        String normalized = normalizeOptional(name, value);
        if (normalized == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return normalized;
    }

    private static String normalizeOptional(String name, String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException(name + " is too long");
        }
        return trimmed;
    }

    private static Instant normalizeDate(String value) {
        String normalized = normalizeOptional("date", value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(normalized).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException dateOnlyFailure) {
                throw new IllegalArgumentException("date must be an ISO-8601 instant or yyyy-MM-dd");
            }
        }
    }

    private static void requirePage(int page, int limit) {
        if (page < 1) {
            throw new IllegalArgumentException("page must be at least 1");
        }
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private static PageMeta meta(long total, int page, int limit) {
        long pages = total == 0 ? 0 : ((total - 1) / limit) + 1;
        if (pages > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Academic attendance result is too large");
        }
        return new PageMeta(total, page, limit, (int) pages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static int rate(long numerator, long denominator) {
        return denominator > 0 ? Math.round((numerator * 100.0f) / denominator) : 0;
    }

    private static String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
