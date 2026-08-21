package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicSectionReadRepository;
import io.campuscore.restfulapi.academic.repository.AcademicSectionReadRepository.GradingSectionRow;
import io.campuscore.restfulapi.academic.repository.AcademicSectionReadRepository.SectionGradeEnrollmentRow;
import io.campuscore.restfulapi.academic.repository.AcademicSectionReadRepository.SectionRow;
import io.campuscore.restfulapi.academic.repository.AcademicSectionReadRepository.SectionScheduleRow;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.DepartmentSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerGradingSectionResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionGradeEnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionGradesResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionListResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.UserSummary;
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

/** Read-only application service for the academic section strangler slice. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-section-read", name = "enabled", havingValue = "true")
public class AcademicSectionReadService {
    public static final int MAX_PAGE_SIZE = 100;

    private final AcademicSectionReadRepository sections;

    public AcademicSectionReadService(AcademicSectionReadRepository sections) {
        this.sections = sections;
    }

    @Transactional(readOnly = true)
    public SectionListResponse findSections(int page, int limit, String semesterId, String departmentId, String courseId) {
        requirePage(page, limit);
        String normalizedSemesterId = normalizeOptional("semesterId", semesterId);
        String normalizedDepartmentId = normalizeOptional("departmentId", departmentId);
        String normalizedCourseId = normalizeOptional("courseId", courseId);
        long total = sections.countSections(normalizedSemesterId, normalizedDepartmentId, normalizedCourseId);
        List<SectionRow> rows = sections.findSections(
                offset(page, limit),
                limit,
                normalizedSemesterId,
                normalizedDepartmentId,
                normalizedCourseId);
        return new SectionListResponse(sectionResponses(rows), meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public List<LecturerScheduleResponse> findLecturerSchedule(String lecturerId, String semesterId) {
        List<SectionRow> rows = sections.findLecturerSections(
                requireProfileId("lecturerId", lecturerId),
                normalizeOptional("semesterId", semesterId));
        Map<String, List<SectionScheduleRow>> schedules = schedules(rows);
        return rows.stream()
                .map(row -> lecturerSchedule(row, schedules.getOrDefault(row.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LecturerGradingSectionResponse> findLecturerGradingSections(String lecturerId, String semesterId) {
        return sections.findLecturerGradingSections(
                        requireProfileId("lecturerId", lecturerId),
                        normalizeOptional("semesterId", semesterId))
                .stream()
                .map(AcademicSectionReadService::lecturerGradingSection)
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionResponse findSection(String id) {
        SectionRow row = sections.findSectionById(normalizeRequired("id", id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
        Map<String, List<SectionScheduleRow>> schedules = schedules(List.of(row));
        return sectionResponse(row, schedules.getOrDefault(row.id(), List.of()));
    }

    @Transactional(readOnly = true)
    public SectionGradesResponse findSectionGrades(String sectionId) {
        SectionRow section = sections.findSectionById(normalizeRequired("sectionId", sectionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section not found"));
        return new SectionGradesResponse(
                section.id(),
                section.sectionNumber(),
                section.courseCode(),
                section.courseName(),
                coalesce(section.courseNameEn(), section.courseName()),
                coalesce(section.courseNameVi(), section.courseName()),
                section.credits(),
                section.departmentName(),
                coalesce(section.departmentNameEn(), section.departmentName()),
                coalesce(section.departmentNameVi(), section.departmentName()),
                section.semesterName(),
                coalesce(section.semesterNameEn(), section.semesterName()),
                section.semesterNameVi(),
                lecturerName(section),
                section.sectionStatus(),
                sections.findSectionGradeEnrollments(section.id()).stream()
                        .map(AcademicSectionReadService::gradeEnrollment)
                        .toList());
    }

    private List<SectionResponse> sectionResponses(List<SectionRow> rows) {
        Map<String, List<SectionScheduleRow>> schedules = schedules(rows);
        return rows.stream()
                .map(row -> sectionResponse(row, schedules.getOrDefault(row.id(), List.of())))
                .toList();
    }

    private Map<String, List<SectionScheduleRow>> schedules(List<SectionRow> rows) {
        List<String> sectionIds = rows.stream().map(SectionRow::id).distinct().toList();
        return sections.findSchedulesForSections(sectionIds).stream()
                .collect(Collectors.groupingBy(SectionScheduleRow::sectionId, LinkedHashMap::new, Collectors.toList()));
    }

    private static SectionResponse sectionResponse(SectionRow row, List<SectionScheduleRow> schedules) {
        return new SectionResponse(
                row.id(),
                row.id(),
                row.sectionNumber(),
                row.courseId(),
                row.semesterId(),
                row.lecturerId(),
                row.classroomId(),
                row.capacity(),
                row.enrolledCount(),
                row.sectionStatus(),
                null,
                course(row),
                semester(row),
                lecturer(row),
                classroom(row),
                schedules.stream().map(AcademicSectionReadService::schedule).toList());
    }

    private static LecturerScheduleResponse lecturerSchedule(SectionRow row, List<SectionScheduleRow> schedules) {
        return new LecturerScheduleResponse(
                row.id(),
                row.id(),
                row.sectionNumber(),
                row.courseCode(),
                row.courseName(),
                coalesce(row.courseNameEn(), row.courseName()),
                coalesce(row.courseNameVi(), row.courseName()),
                row.credits(),
                row.capacity(),
                Math.toIntExact(row.activeEnrollmentCount()),
                row.departmentName(),
                coalesce(row.departmentNameEn(), row.departmentName()),
                coalesce(row.departmentNameVi(), row.departmentName()),
                row.sectionStatus(),
                schedules.stream().map(AcademicSectionReadService::schedule).toList());
    }

    private static LecturerGradingSectionResponse lecturerGradingSection(GradingSectionRow row) {
        String gradeStatus = row.gradedCount() == 0
                ? "NONE"
                : row.gradedCount() >= row.enrolledCount() && row.enrolledCount() > 0
                        ? "ALL_GRADED"
                        : "PARTIAL";
        return new LecturerGradingSectionResponse(
                row.id(),
                row.id(),
                row.sectionNumber(),
                row.courseCode(),
                row.courseName(),
                coalesce(row.courseNameEn(), row.courseName()),
                coalesce(row.courseNameVi(), row.courseName()),
                row.credits(),
                row.departmentName(),
                coalesce(row.departmentNameEn(), row.departmentName()),
                coalesce(row.departmentNameVi(), row.departmentName()),
                row.semesterName(),
                row.semesterName(),
                coalesce(row.semesterNameEn(), row.semesterName()),
                row.semesterNameVi(),
                row.enrolledCount(),
                row.gradedCount(),
                row.publishedCount(),
                gradeStatus,
                row.enrolledCount() > 0
                        && row.gradedCount() >= row.enrolledCount()
                        && row.publishedCount() < row.enrolledCount());
    }

    private static SectionGradeEnrollmentResponse gradeEnrollment(SectionGradeEnrollmentRow row) {
        return new SectionGradeEnrollmentResponse(
                row.id(),
                row.studentId(),
                row.studentName(),
                row.studentCode(),
                row.email(),
                row.finalGrade(),
                row.letterGrade(),
                row.gradeStatus(),
                row.enrollmentStatus());
    }

    private static CourseSummary course(SectionRow row) {
        return new CourseSummary(
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
                        coalesce(row.departmentNameVi(), row.departmentName())));
    }

    private static SemesterSummary semester(SectionRow row) {
        return new SemesterSummary(
                row.semesterId(),
                row.semesterName(),
                coalesce(row.semesterNameEn(), row.semesterName()),
                row.semesterNameVi(),
                row.semesterStartDate());
    }

    private static LecturerSummary lecturer(SectionRow row) {
        if (row.lecturerId() == null) {
            return null;
        }
        return new LecturerSummary(
                row.lecturerId(),
                row.employeeId(),
                new UserSummary(row.lecturerUserId(), row.lecturerEmail(), row.lecturerFirstName(), row.lecturerLastName()));
    }

    private static ClassroomSummary classroom(SectionRow row) {
        if (row.classroomId() == null) {
            return null;
        }
        return new ClassroomSummary(row.classroomId(), row.building(), row.roomNumber());
    }

    private static SectionScheduleResponse schedule(SectionScheduleRow row) {
        return new SectionScheduleResponse(
                row.id(),
                row.dayOfWeek(),
                row.startTime(),
                row.endTime(),
                coalesce(row.building(), "TBA"),
                coalesce(row.roomNumber(), "TBA"),
                new ClassroomSummary(row.classroomId(), row.building(), row.roomNumber()));
    }

    private static String lecturerName(SectionRow row) {
        if (row.lecturerFirstName() == null && row.lecturerLastName() == null) {
            return null;
        }
        return ((row.lecturerFirstName() == null ? "" : row.lecturerFirstName().trim())
                + " "
                + (row.lecturerLastName() == null ? "" : row.lecturerLastName().trim())).trim();
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
            throw new IllegalArgumentException("Academic section result is too large");
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
