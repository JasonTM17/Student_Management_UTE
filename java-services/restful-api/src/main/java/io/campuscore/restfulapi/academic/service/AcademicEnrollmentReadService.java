package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository;
import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository.EnrollmentRow;
import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository.GradeItemRow;
import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository.GradeSummaryRow;
import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository.ScheduleRow;
import io.campuscore.restfulapi.academic.repository.AcademicEnrollmentReadRepository.StudentGradeRow;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentGradeDetail;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentListResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeItemResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.LecturerSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.PageMeta;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradeLine;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradeSectionRow;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentGradesByEnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.StudentSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptSemester;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.UserSummary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

/** Read-only service for academic enrollment, grade and transcript views. */
@Service
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.academic-enrollment-read", name = "enabled", havingValue = "true")
public class AcademicEnrollmentReadService {
    public static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, BigDecimal> GRADE_POINTS = Map.ofEntries(
            Map.entry("A+", BigDecimal.valueOf(4.0)),
            Map.entry("A", BigDecimal.valueOf(4.0)),
            Map.entry("A-", BigDecimal.valueOf(3.7)),
            Map.entry("B+", BigDecimal.valueOf(3.3)),
            Map.entry("B", BigDecimal.valueOf(3.0)),
            Map.entry("B-", BigDecimal.valueOf(2.7)),
            Map.entry("C+", BigDecimal.valueOf(2.3)),
            Map.entry("C", BigDecimal.valueOf(2.0)),
            Map.entry("C-", BigDecimal.valueOf(1.7)),
            Map.entry("D+", BigDecimal.valueOf(1.3)),
            Map.entry("D", BigDecimal.valueOf(1.0)),
            Map.entry("D-", BigDecimal.valueOf(0.7)),
            Map.entry("F", BigDecimal.ZERO));

    private final AcademicEnrollmentReadRepository academic;

    public AcademicEnrollmentReadService(AcademicEnrollmentReadRepository academic) {
        this.academic = academic;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> findStudentEnrollments(String studentId, String semesterId) {
        return enrollments(academic.findStudentEnrollments(
                requireProfileId("studentId", studentId),
                normalizeOptional("semesterId", semesterId)));
    }

    @Transactional(readOnly = true)
    public EnrollmentListResponse findEnrollments(
            int page,
            int limit,
            String status,
            String semesterId,
            String studentId,
            String courseId,
            String sectionId) {
        requirePage(page, limit);
        String normalizedStatus = normalizeOptional("status", status);
        String normalizedSemesterId = normalizeOptional("semesterId", semesterId);
        String normalizedStudentId = normalizeOptional("studentId", studentId);
        String normalizedCourseId = normalizeOptional("courseId", courseId);
        String normalizedSectionId = normalizeOptional("sectionId", sectionId);
        long total = academic.countEnrollments(
                normalizedStatus,
                normalizedSemesterId,
                normalizedStudentId,
                normalizedCourseId,
                normalizedSectionId);
        return new EnrollmentListResponse(
                enrollments(academic.findEnrollments(
                        offset(page, limit),
                        limit,
                        normalizedStatus,
                        normalizedSemesterId,
                        normalizedStudentId,
                        normalizedCourseId,
                        normalizedSectionId)),
                meta(total, page, limit));
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse findEnrollment(String id) {
        EnrollmentRow row = academic.findEnrollmentById(normalizeRequired("id", id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found"));
        return enrollment(row, schedules(List.of(row)));
    }

    @Transactional(readOnly = true)
    public EnrollmentResponse findEnrollment(String id, List<String> roles, String studentId) {
        EnrollmentResponse enrollment = findEnrollment(id);
        if (roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"))) {
            return enrollment;
        }
        if (enrollment.studentId().equals(normalizeOptional("studentId", studentId))) {
            return enrollment;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
    }

    @Transactional(readOnly = true)
    public List<GradeSummary> findStudentGrades(String studentId, String semesterId) {
        return academic.findStudentGradeSummaries(
                        requireProfileId("studentId", studentId),
                        normalizeOptional("semesterId", semesterId))
                .stream()
                .map(AcademicEnrollmentReadService::gradeSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TranscriptResponse findStudentTranscript(String studentId) {
        List<GradeSummary> grades = findStudentGrades(studentId, null);
        Map<String, TranscriptAccumulator> bySemester = new LinkedHashMap<>();
        int totalAttempted = 0;
        int totalEarned = 0;
        BigDecimal totalPoints = BigDecimal.ZERO;

        for (GradeSummary grade : grades) {
            TranscriptAccumulator semester = bySemester.computeIfAbsent(
                    grade.semesterId(),
                    ignored -> new TranscriptAccumulator(
                            grade.semesterId(),
                            grade.semester(),
                            coalesce(grade.semesterNameEn(), grade.semester()),
                            grade.semesterNameVi()));
            semester.records.add(grade);
            BigDecimal point = GRADE_POINTS.get(grade.letterGrade());
            if (point != null) {
                BigDecimal weighted = point.multiply(BigDecimal.valueOf(grade.credits()));
                semester.attempted += grade.credits();
                semester.points = semester.points.add(weighted);
                totalAttempted += grade.credits();
                totalPoints = totalPoints.add(weighted);
                if (!"F".equals(grade.letterGrade())) {
                    semester.earned += grade.credits();
                    totalEarned += grade.credits();
                }
            } else if ("COMPLETED".equals(grade.enrollmentStatus())) {
                semester.earned += grade.credits();
                totalEarned += grade.credits();
            }
        }

        return new TranscriptResponse(
                new TranscriptSummary(gpa(totalPoints, totalAttempted), totalEarned, totalAttempted),
                bySemester.values().stream().map(TranscriptAccumulator::response).toList());
    }

    @Transactional(readOnly = true)
    public List<GradeItemResponse> findGradeItemsBySection(String sectionId) {
        return academic.findGradeItemsBySection(normalizeRequired("sectionId", sectionId)).stream()
                .map(AcademicEnrollmentReadService::gradeItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GradeItemResponse> findGradeItemsByLecturer(String lecturerId) {
        return academic.findGradeItemsByLecturer(requireProfileId("lecturerId", lecturerId)).stream()
                .map(AcademicEnrollmentReadService::gradeItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StudentGradeSectionRow> findStudentGradesBySection(String sectionId) {
        return studentGradeRows(academic.findStudentGradesBySection(normalizeRequired("sectionId", sectionId)));
    }

    @Transactional(readOnly = true)
    public List<StudentGradeSectionRow> findStudentGradesByLecturer(String lecturerId, String sectionId) {
        return studentGradeRows(academic.findStudentGradesByLecturer(
                requireProfileId("lecturerId", lecturerId),
                normalizeOptional("sectionId", sectionId)));
    }

    @Transactional(readOnly = true)
    public StudentGradesByEnrollmentResponse findStudentGradesByEnrollment(String enrollmentId) {
        List<StudentGradeSectionRow> rows = studentGradeRows(
                academic.findStudentGradesByEnrollment(normalizeRequired("enrollmentId", enrollmentId)));
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
        }
        StudentGradeSectionRow row = rows.get(0);
        return new StudentGradesByEnrollmentResponse(
                new EnrollmentGradeDetail(
                        row.enrollmentId(),
                        row.studentId(),
                        row.studentName(),
                        row.courseCode(),
                        row.courseName(),
                        row.courseNameEn(),
                        row.courseNameVi(),
                        row.semester(),
                        row.semesterNameEn(),
                        row.semesterNameVi()),
                row.grades(),
                row.calculatedTotal(),
                row.totalWeight());
    }

    private List<EnrollmentResponse> enrollments(List<EnrollmentRow> rows) {
        Map<String, List<ScheduleRow>> schedules = schedules(rows);
        return rows.stream().map(row -> enrollment(row, schedules)).toList();
    }

    private Map<String, List<ScheduleRow>> schedules(List<EnrollmentRow> rows) {
        List<String> sectionIds = rows.stream().map(EnrollmentRow::sectionId).distinct().toList();
        return academic.findSchedulesForSections(sectionIds).stream()
                .collect(Collectors.groupingBy(ScheduleRow::sectionId, LinkedHashMap::new, Collectors.toList()));
    }

    private static EnrollmentResponse enrollment(EnrollmentRow row, Map<String, List<ScheduleRow>> schedules) {
        return new EnrollmentResponse(
                row.id(),
                row.studentId(),
                row.sectionId(),
                row.semesterId(),
                row.status(),
                row.enrolledAt(),
                row.droppedAt(),
                row.gradeStatus(),
                row.finalGrade(),
                row.letterGrade(),
                row.createdAt(),
                row.updatedAt(),
                new StudentSummary(row.studentId(), row.studentNumber(),
                        new UserSummary(row.studentUserId(), row.studentEmail(), row.studentFirstName(), row.studentLastName())),
                section(row, schedules.getOrDefault(row.sectionId(), List.of())),
                semester(row.semesterIdValue(), row.semesterName(), row.semesterNameEn(), row.semesterNameVi(), row.semesterStartDate()));
    }

    private static SectionSummary section(EnrollmentRow row, List<ScheduleRow> schedules) {
        LecturerSummary lecturer = row.lecturerId() == null
                ? null
                : new LecturerSummary(row.lecturerId(), row.employeeId(),
                        new UserSummary(row.lecturerUserId(), row.lecturerEmail(), row.lecturerFirstName(), row.lecturerLastName()));
        return new SectionSummary(
                row.sectionId(),
                row.sectionNumber(),
                course(row.courseId(), row.courseCode(), row.courseName(), row.courseNameEn(), row.courseNameVi(), row.credits()),
                semester(row.semesterIdValue(), row.semesterName(), row.semesterNameEn(), row.semesterNameVi(), row.semesterStartDate()),
                lecturer,
                row.capacity(),
                row.enrolledCount(),
                row.sectionStatus(),
                schedules.stream().map(AcademicEnrollmentReadService::schedule).toList());
    }

    private static SectionSummary section(GradeItemRow row) {
        return new SectionSummary(
                row.sectionId(),
                row.sectionNumber(),
                course(row.courseId(), row.courseCode(), row.courseName(), row.courseNameEn(), row.courseNameVi(), row.credits()),
                semester(row.semesterId(), row.semesterName(), row.semesterNameEn(), row.semesterNameVi(), row.semesterStartDate()),
                null,
                row.capacity(),
                row.enrolledCount(),
                row.sectionStatus(),
                List.of());
    }

    private static SectionScheduleResponse schedule(ScheduleRow row) {
        return new SectionScheduleResponse(
                row.id(),
                row.dayOfWeek(),
                row.startTime(),
                row.endTime(),
                new ClassroomSummary(row.classroomId(), row.building(), row.roomNumber()));
    }

    private static CourseSummary course(String id, String code, String name, String nameEn, String nameVi, int credits) {
        return new CourseSummary(id, code, name, coalesce(nameEn, name), coalesce(nameVi, name), credits);
    }

    private static SemesterSummary semester(String id, String name, String nameEn, String nameVi, java.time.Instant startDate) {
        return new SemesterSummary(id, name, coalesce(nameEn, name), nameVi, startDate);
    }

    private static GradeSummary gradeSummary(GradeSummaryRow row) {
        return new GradeSummary(
                row.id(),
                row.courseCode(),
                row.courseName(),
                coalesce(row.courseNameEn(), row.courseName()),
                coalesce(row.courseNameVi(), row.courseName()),
                row.credits(),
                row.sectionCode(),
                row.lecturerName().isBlank() ? null : row.lecturerName(),
                row.semester(),
                coalesce(row.semesterNameEn(), row.semester()),
                row.semesterNameVi(),
                row.semesterId(),
                row.finalGrade(),
                row.letterGrade(),
                row.gradeStatus(),
                row.enrollmentStatus());
    }

    private static GradeItemResponse gradeItem(GradeItemRow row) {
        return new GradeItemResponse(row.id(), row.sectionId(), row.name(), row.type(), row.maxScore(), row.weight(),
                row.gradedAt(), row.createdAt(), section(row));
    }

    private static List<StudentGradeSectionRow> studentGradeRows(List<StudentGradeRow> rows) {
        Map<String, StudentGradeAccumulator> grouped = new LinkedHashMap<>();
        for (StudentGradeRow row : rows) {
            StudentGradeAccumulator accumulator = grouped.computeIfAbsent(row.enrollmentId(), ignored -> new StudentGradeAccumulator(row));
            if (row.gradeId() != null) {
                accumulator.grades.add(new StudentGradeLine(
                        row.gradeId(),
                        row.gradeItemId(),
                        row.gradeItemName(),
                        row.gradeItemType(),
                        row.score(),
                        row.maxScore(),
                        row.weight()));
            }
        }
        return grouped.values().stream().map(StudentGradeAccumulator::response).toList();
    }

    private static BigDecimal calculatedTotal(List<StudentGradeLine> grades) {
        if (grades.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (StudentGradeLine grade : grades) {
            if (grade.score() != null && grade.maxScore() != null && grade.weight() != null
                    && grade.maxScore().compareTo(BigDecimal.ZERO) > 0) {
                total = total.add(grade.score().divide(grade.maxScore(), 8, RoundingMode.HALF_UP).multiply(grade.weight()));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal totalWeight(List<StudentGradeLine> grades) {
        return grades.stream()
                .map(StudentGradeLine::weight)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
            throw new IllegalArgumentException("Academic enrollment result is too large");
        }
        return new PageMeta(total, page, limit, (int) pages);
    }

    private static long offset(int page, int limit) {
        return (long) (page - 1) * limit;
    }

    private static BigDecimal gpa(BigDecimal points, int credits) {
        return credits == 0 ? BigDecimal.ZERO : points.divide(BigDecimal.valueOf(credits), 2, RoundingMode.HALF_UP);
    }

    private static String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class TranscriptAccumulator {
        private final String semesterId;
        private final String semesterName;
        private final String semesterNameEn;
        private final String semesterNameVi;
        private final List<GradeSummary> records = new ArrayList<>();
        private int earned;
        private int attempted;
        private BigDecimal points = BigDecimal.ZERO;

        private TranscriptAccumulator(String semesterId, String semesterName, String semesterNameEn, String semesterNameVi) {
            this.semesterId = semesterId;
            this.semesterName = semesterName;
            this.semesterNameEn = semesterNameEn;
            this.semesterNameVi = semesterNameVi;
        }

        private TranscriptSemester response() {
            return new TranscriptSemester(semesterId, semesterName, semesterNameEn, semesterNameVi, records, gpa(points, attempted), earned, attempted);
        }
    }

    private static final class StudentGradeAccumulator {
        private final StudentGradeRow row;
        private final List<StudentGradeLine> grades = new ArrayList<>();

        private StudentGradeAccumulator(StudentGradeRow row) {
            this.row = row;
        }

        private StudentGradeSectionRow response() {
            return new StudentGradeSectionRow(
                    row.enrollmentId(),
                    row.studentId(),
                    row.studentName(),
                    row.studentNumber(),
                    row.sectionId(),
                    row.sectionNumber(),
                    row.courseCode(),
                    row.courseName(),
                    coalesce(row.courseNameEn(), row.courseName()),
                    coalesce(row.courseNameVi(), row.courseName()),
                    row.semester(),
                    coalesce(row.semesterNameEn(), row.semester()),
                    row.semesterNameVi(),
                    row.finalGrade(),
                    row.letterGrade(),
                    row.gradeStatus(),
                    List.copyOf(grades),
                    calculatedTotal(grades),
                    totalWeight(grades));
        }
    }
}
