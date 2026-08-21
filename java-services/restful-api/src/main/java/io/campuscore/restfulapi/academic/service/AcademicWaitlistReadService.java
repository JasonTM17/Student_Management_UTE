package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicWaitlistReadRepository;
import io.campuscore.restfulapi.academic.repository.AcademicWaitlistReadRepository.ScheduleRow;
import io.campuscore.restfulapi.academic.repository.AcademicWaitlistReadRepository.WaitlistRow;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.SectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.StudentSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.UserSummary;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.WaitlistListResponse;
import io.campuscore.restfulapi.academic.web.AcademicWaitlistReadDtos.WaitlistResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only service for the academic waitlist strangler slice. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-waitlist-read", name = "enabled", havingValue = "true")
public class AcademicWaitlistReadService {
    public static final int MAX_PAGE_SIZE = 100;

    private final AcademicWaitlistReadRepository waitlist;

    public AcademicWaitlistReadService(AcademicWaitlistReadRepository waitlist) {
        this.waitlist = waitlist;
    }

    @Transactional(readOnly = true)
    public WaitlistListResponse findWaitlist(int page, int limit, String sectionId) {
        requirePage(page, limit);
        String normalizedSectionId = normalizeOptional("sectionId", sectionId);
        long total = waitlist.countWaitlist(normalizedSectionId);
        return new WaitlistListResponse(
                waitlistResponses(waitlist.findWaitlist(offset(page, limit), limit, normalizedSectionId)),
                meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public WaitlistResponse findWaitlistEntry(String id) {
        WaitlistRow row = waitlist.findWaitlistById(normalizeRequired("id", id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Waitlist entry not found"));
        return waitlistResponse(row, schedules(List.of(row)).getOrDefault(row.sectionId(), List.of()));
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> findSectionWaitlist(String sectionId) {
        return waitlistResponses(waitlist.findActiveBySection(normalizeRequired("sectionId", sectionId)));
    }

    @Transactional(readOnly = true)
    public List<WaitlistResponse> findStudentWaitlist(String studentId) {
        return waitlistResponses(waitlist.findActiveByStudent(requireProfileId("studentId", studentId)));
    }

    private List<WaitlistResponse> waitlistResponses(List<WaitlistRow> rows) {
        Map<String, List<ScheduleRow>> schedules = schedules(rows);
        return rows.stream()
                .map(row -> waitlistResponse(row, schedules.getOrDefault(row.sectionId(), List.of())))
                .toList();
    }

    private Map<String, List<ScheduleRow>> schedules(List<WaitlistRow> rows) {
        List<String> sectionIds = rows.stream().map(WaitlistRow::sectionId).distinct().toList();
        return waitlist.findSchedulesForSections(sectionIds).stream()
                .collect(Collectors.groupingBy(ScheduleRow::sectionId, LinkedHashMap::new, Collectors.toList()));
    }

    private static WaitlistResponse waitlistResponse(WaitlistRow row, List<ScheduleRow> schedules) {
        return new WaitlistResponse(
                row.id(),
                row.studentId(),
                row.sectionId(),
                row.position(),
                row.status(),
                row.addedAt(),
                row.convertedAt(),
                new StudentSummary(
                        row.studentId(),
                        row.studentNumber(),
                        new UserSummary(
                                row.studentUserId(),
                                row.studentEmail(),
                                row.studentFirstName(),
                                row.studentLastName())),
                section(row, schedules));
    }

    private static SectionSummary section(WaitlistRow row, List<ScheduleRow> schedules) {
        return new SectionSummary(
                row.sectionId(),
                row.sectionNumber(),
                new CourseSummary(
                        row.courseId(),
                        row.courseCode(),
                        row.courseName(),
                        coalesce(row.courseNameEn(), row.courseName()),
                        coalesce(row.courseNameVi(), row.courseName()),
                        row.credits(),
                        new DepartmentSummary(
                                row.departmentId(),
                                row.departmentCode(),
                                row.departmentName(),
                                coalesce(row.departmentNameEn(), row.departmentName()),
                                coalesce(row.departmentNameVi(), row.departmentName()))),
                new SemesterSummary(
                        row.semesterId(),
                        row.semesterName(),
                        coalesce(row.semesterNameEn(), row.semesterName()),
                        row.semesterNameVi(),
                        row.semesterStartDate()),
                row.capacity(),
                row.enrolledCount(),
                row.sectionStatus(),
                schedules.stream().map(AcademicWaitlistReadService::schedule).toList());
    }

    private static SectionScheduleResponse schedule(ScheduleRow row) {
        return new SectionScheduleResponse(
                row.id(),
                row.dayOfWeek(),
                row.startTime(),
                row.endTime(),
                new ClassroomSummary(row.classroomId(), row.building(), row.roomNumber()));
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
            throw new IllegalArgumentException("Academic waitlist result is too large");
        }
        return new PageMeta(total, page, limit, (int) pages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
