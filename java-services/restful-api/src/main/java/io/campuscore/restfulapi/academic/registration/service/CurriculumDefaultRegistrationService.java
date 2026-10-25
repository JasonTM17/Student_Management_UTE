package io.campuscore.restfulapi.academic.registration.service;

import static io.campuscore.restfulapi.academic.registration.dto.CurriculumDefaultRegistrationDtos.*;
import static io.campuscore.restfulapi.academic.registration.model.CurriculumDefaultRegistrationRows.*;

import io.campuscore.restfulapi.academic.registration.repository.CurriculumDefaultRegistrationRepository;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.EligibilityResponse;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("persistence")
public class CurriculumDefaultRegistrationService {
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final int MAX_BATCH_SIZE = 200;
    private static final Set<String> ACTIVE_STATUSES = Set.of("ENROLLED", "PENDING", "CONFIRMED");
    private static final String DEFAULT_SOURCE = "CURRICULUM_DEFAULT";

    private final CurriculumDefaultRegistrationRepository repository;
    private final RegistrationService registration;
    private final AdminAuditRecorder audit;
    private final TransactionTemplate studentTransaction;
    private final TransactionTemplate auditTransaction;

    public CurriculumDefaultRegistrationService(
            CurriculumDefaultRegistrationRepository repository,
            RegistrationService registration,
            AdminAuditRecorder audit,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.registration = registration;
        this.audit = audit;
        this.studentTransaction = new TransactionTemplate(transactionManager);
        this.studentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.auditTransaction = new TransactionTemplate(transactionManager);
        this.auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PlanResponse plan(String studentId, String semesterId, Integer curriculumYear, Integer curriculumSemester) {
        StudentRow student = requireStudent(studentId);
        SemesterRow semester = resolveSemester(semesterId);
        return buildPlan(student, semester, curriculumYear, curriculumSemester, null);
    }

    public StudentRegistrationResponse registerStudent(
            String studentId, String semesterId, Integer curriculumYear, Integer curriculumSemester) {
        PlanResponse plan = plan(studentId, semesterId, curriculumYear, curriculumSemester);
        List<CourseResult> results = new ArrayList<>();
        for (CoursePlan course : plan.courses()) {
            if (course.state() != CourseState.REGISTERABLE) {
                results.add(new CourseResult(course.courseId(), course.courseCode(), course.state(),
                        course.reasonCode(), null));
                continue;
            }
            try {
                var enrollment = registration.enrollCurriculumDefault(studentId,
                        course.proposedSection().id(), plan.registrationRoundId(), idempotencyKey(studentId,
                                plan.registrationRoundId(), course.courseId()));
                results.add(new CourseResult(course.courseId(), course.courseCode(), CourseState.ALREADY_REGISTERED,
                        null, enrollment.id()));
            } catch (DomainException exception) {
                results.add(new CourseResult(course.courseId(), course.courseCode(), stateForReason(exception.code()),
                        exception.code(), null));
            } catch (DataIntegrityViolationException exception) {
                results.add(new CourseResult(course.courseId(), course.courseCode(), CourseState.CONFLICT,
                        "REGISTRATION_CONFLICT", null));
            }
        }
        int registered = (int) results.stream().filter(row -> row.enrollmentId() != null).count();
        return new StudentRegistrationResponse(plan.semesterId(), registered, results.size() - registered,
                List.copyOf(results));
    }

    public AdminRunResponse dryRun(AdminRunRequest request) {
        Target target = resolveTarget(request);
        Page page = studentPage(target);
        RunCounts counts = new RunCounts();
        for (StudentCursorRow student : page.students()) {
            counts.students++;
            try {
                addPlanCounts(counts, buildPlan(studentRow(student), target.semester(), null, null, target.round()));
            } catch (DomainException exception) {
                counts.failedStudents++;
                counts.addSkipped(exception.code(), 1);
            }
        }
        return response(null, target, counts, true, page.hasMore(), page.nextCursor());
    }

    public AdminRunResponse apply(AdminRunRequest request, String actorId, String actorLabel) {
        Target target = resolveTarget(request);
        Page page = studentPage(target);
        RunCounts counts = new RunCounts();
        for (StudentCursorRow student : page.students()) {
            counts.students++;
            PlanResponse[] planned = new PlanResponse[1];
            try {
                StudentApplyResult result = studentTransaction.execute(status -> {
                    PlanResponse plan = buildPlan(studentRow(student), target.semester(), null, null, target.round());
                    planned[0] = plan;
                    if (!"RESOLVED".equals(plan.term().status())) {
                        throw problem(HttpStatus.UNPROCESSABLE_ENTITY,
                                blank(plan.term().reasonCode()) ? "CURRICULUM_TERM_UNKNOWN" : plan.term().reasonCode(),
                                "The student's current curriculum term could not be resolved");
                    }
                    int courseCount = plan.courses().size();
                    int skipped = 0;
                    int wouldRegister = 0;
                    Map<String, Integer> skippedReasons = new TreeMap<>();
                    for (CoursePlan course : plan.courses()) {
                        if (course.state() != CourseState.REGISTERABLE) {
                            skipped++;
                            skippedReasons.merge(reason(course), 1, Integer::sum);
                            continue;
                        }
                        wouldRegister++;
                        registration.enrollCurriculumDefaultInCurrentTransaction(student.id(),
                                course.proposedSection().id(), target.round().id(), idempotencyKey(student.id(),
                                        target.round().id(), course.courseId()));
                    }
                    return new StudentApplyResult(courseCount, wouldRegister, courseCount - skipped, skipped, skippedReasons);
                });
                if (result != null) {
                    counts.courses += result.courses();
                    counts.wouldRegister += result.wouldRegister();
                    counts.registered += result.registered();
                    counts.skipped += result.skipped();
                    result.reasons().forEach(counts::addSkipped);
                }
            } catch (DomainException exception) {
                recordStudentFailure(counts, planned[0], exception.code());
            } catch (DataAccessException exception) {
                recordStudentFailure(counts, planned[0], "REGISTRATION_CONFLICT");
            } catch (RuntimeException exception) {
                recordStudentFailure(counts, planned[0], "STUDENT_REGISTRATION_FAILED");
            }
        }
        String runId = UUID.randomUUID().toString();
        Map<String, Object> auditState = new LinkedHashMap<>();
        auditState.put("roundId", target.round().id());
        auditState.put("semesterId", target.semester().id());
        auditState.put("students", counts.students);
        auditState.put("courses", counts.courses);
        auditState.put("registered", counts.registered);
        auditState.put("skipped", counts.skipped);
        auditState.put("failedStudents", counts.failedStudents);
        auditState.put("skippedByReason", counts.reasons);
        auditTransaction.executeWithoutResult(status -> audit.record(actorId, actorLabel,
                "CURRICULUM_DEFAULT_REGISTRATION_APPLY", "CURRICULUM_DEFAULT_REGISTRATION_RUN", runId,
                "Curriculum default registration apply completed", null, auditState));
        return response(runId, target, counts, false, page.hasMore(), page.nextCursor());
    }

    public AdminRunList recentRuns() {
        return new AdminRunList(repository.findRecentRuns(20).stream()
                .map(row -> new AdminRunSummary(row.id(), row.actorLabel(), row.summary(), row.afterState(), row.createdAt()))
                .toList());
    }

    private PlanResponse buildPlan(
            StudentRow student, SemesterRow semester, Integer manualYear, Integer manualSemester, RoundRow roundOverride) {
        if (manualYear != null || manualSemester != null) {
            if (manualYear == null || manualSemester == null || manualYear < 1 || manualYear > 20
                    || manualSemester < 1 || manualSemester > 3) {
                throw problem(HttpStatus.BAD_REQUEST, "CURRICULUM_TERM_SELECTION_INVALID",
                        "Select a curriculum year and semester between 1 and 3");
            }
        }
        TermResolution term = manualYear == null
                ? resolveTerm(student, semester)
                : new TermResolution("MANUAL", manualYear, manualSemester, null);
        RoundRow round = roundOverride;
        if (round == null) {
            round = repository.findOpenRound(semester.id(), Instant.now()).orElse(null);
        }
        boolean eligible = false;
        String registrationReason = "WINDOW_CLOSED";
        int creditsRemaining = Integer.MAX_VALUE;
        if (round != null) {
            RegistrationAvailability availability = registration.curriculumAvailability(student.id(), round.id());
            eligible = availability.eligible();
            registrationReason = availability.reasonCode();
            creditsRemaining = availability.creditsRemaining();
        }
        if (!"RESOLVED".equals(term.status()) && !"MANUAL".equals(term.status())) {
            return new PlanResponse(semester.id(), semester.name(), semester.nameEn(), semester.nameVi(),
                    round == null ? null : round.id(), eligible, term, List.of());
        }

        Set<String> passed = new LinkedHashSet<>(repository.findPassedCourseIds(student.id()));
        Map<String, EnrollmentState> enrollmentStates = new LinkedHashMap<>();
        for (EnrollmentState state : repository.findEnrollmentStates(student.id(), semester.id())) {
            EnrollmentState previous = enrollmentStates.get(state.courseId());
            if (previous == null || ACTIVE_STATUSES.contains(state.status())
                    || (DEFAULT_SOURCE.equals(state.registrationSource()) && "DROPPED".equals(state.status()))) {
                enrollmentStates.put(state.courseId(), state);
            }
        }
        List<String> selectedSections = new ArrayList<>(repository.findActiveSectionIds(student.id(), semester.id()));
        List<CoursePlan> courses = new ArrayList<>();
        List<CourseRow> mandatory = repository.findMandatoryCourses(student.curriculumId(),
                term.curriculumYear(), term.curriculumSemester());
        for (CourseRow course : mandatory) {
            if (passed.contains(course.id())) {
                continue;
            }
            EnrollmentState enrollment = enrollmentStates.get(course.id());
            if (enrollment != null && ACTIVE_STATUSES.contains(enrollment.status())) {
                courses.add(coursePlan(course, CourseState.ALREADY_REGISTERED, null,
                        repository.findSections(course.id(), semester.id()), null, selectedSections, semester.id()));
                continue;
            }
            if (enrollment != null && DEFAULT_SOURCE.equals(enrollment.registrationSource())
                    && "DROPPED".equals(enrollment.status())) {
                courses.add(coursePlan(course, CourseState.DISMISSED, "CURRICULUM_DEFAULT_DISMISSED",
                        repository.findSections(course.id(), semester.id()), null, selectedSections, semester.id()));
                continue;
            }
            List<SectionRow> sections = repository.findSections(course.id(), semester.id());
            CoursePlan candidate = chooseCourse(course, sections, student.id(), semester.id(), selectedSections,
                    creditsRemaining, eligible, registrationReason);
            courses.add(candidate);
            if (candidate.proposedSection() != null
                    && (candidate.state() == CourseState.REGISTERABLE || candidate.reasonCode() == null
                            || !"CREDIT_CAP_EXCEEDED".equals(candidate.reasonCode()))) {
                selectedSections.add(candidate.proposedSection().id());
                if (creditsRemaining != Integer.MAX_VALUE) {
                    creditsRemaining = Math.max(0, creditsRemaining - course.credits());
                }
            }
        }
        return new PlanResponse(semester.id(), semester.name(), semester.nameEn(), semester.nameVi(),
                round == null ? null : round.id(), eligible, term, List.copyOf(courses));
    }

    private CoursePlan chooseCourse(CourseRow course, List<SectionRow> sections, String studentId,
            String semesterId, List<String> selectedSections, int creditsRemaining, boolean eligible,
            String registrationReason) {
        boolean hasOpen = sections.stream().anyMatch(section -> "OPEN".equals(section.status()));
        boolean hasFree = sections.stream().anyMatch(section -> "OPEN".equals(section.status())
                && section.capacity() > section.enrolledCount());
        List<SectionOption> options = sections.stream().map(section -> toOption(section,
                registration.hasCurriculumScheduleConflict(selectedSections, section.id()))).toList();
        if (!hasOpen) {
            return coursePlan(course, CourseState.NO_OPEN_SECTION, "NO_OPEN_SECTION", sections, null,
                    selectedSections, semesterId, options);
        }
        if (!hasFree) {
            return coursePlan(course, CourseState.FULL, "SECTION_FULL", sections, null,
                    selectedSections, semesterId, options);
        }
        SectionRow chosen = null;
        String prerequisiteReason = null;
        boolean freeSectionConflicts = false;
        for (SectionRow section : sections) {
            if (!"OPEN".equals(section.status()) || section.capacity() <= section.enrolledCount()) {
                continue;
            }
            if (registration.hasCurriculumScheduleConflict(selectedSections, section.id())) {
                freeSectionConflicts = true;
                continue;
            }
            try {
                registration.assertCurriculumRequirements(studentId, course.id(), semesterId, section.id(), selectedSections);
                chosen = section;
                break;
            } catch (DomainException exception) {
                if ("PREREQUISITE_UNMET".equals(exception.code()) || "COREQUISITE_UNMET".equals(exception.code())) {
                    prerequisiteReason = exception.code();
                } else {
                    throw exception;
                }
            }
        }
        if (chosen == null) {
            if (prerequisiteReason != null) {
                return coursePlan(course, CourseState.BLOCKED_PREREQUISITE, prerequisiteReason, sections, null,
                        selectedSections, semesterId, options);
            }
            return coursePlan(course, CourseState.CONFLICT, freeSectionConflicts ? "SCHEDULE_CONFLICT" : "NO_OPEN_SECTION",
                    sections, null, selectedSections, semesterId, options);
        }
        SectionOption proposed = toOption(chosen, false);
        if (course.credits() > creditsRemaining) {
            return coursePlan(course, CourseState.CONFLICT, "CREDIT_CAP_EXCEEDED", sections, proposed,
                    selectedSections, semesterId, options);
        }
        if (!eligible) {
            return coursePlan(course, CourseState.CONFLICT,
                    registrationReason == null ? "WINDOW_CLOSED" : registrationReason,
                    sections, proposed, selectedSections, semesterId, options);
        }
        return new CoursePlan(course.id(), course.code(), course.name(), course.nameEn(), course.nameVi(),
                course.credits(), CourseState.REGISTERABLE, null, options, proposed);
    }

    private CoursePlan coursePlan(CourseRow course, CourseState state, String reason, List<SectionRow> sections,
            SectionRow proposed, List<String> selectedSections, String semesterId) {
        return coursePlan(course, state, reason, sections,
                proposed == null ? null : toOption(proposed, registration.hasCurriculumScheduleConflict(selectedSections, proposed.id())),
                selectedSections, semesterId, sections.stream().map(section -> toOption(section,
                        registration.hasCurriculumScheduleConflict(selectedSections, section.id()))).toList());
    }

    private CoursePlan coursePlan(CourseRow course, CourseState state, String reason, List<SectionRow> sections,
            SectionOption proposed, List<String> selectedSections, String semesterId, List<SectionOption> options) {
        return new CoursePlan(course.id(), course.code(), course.name(), course.nameEn(), course.nameVi(),
                course.credits(), state, reason, options, proposed);
    }

    private SectionOption toOption(SectionRow section, boolean conflict) {
        return new SectionOption(section.id(), section.sectionNumber(), section.capacity(), section.enrolledCount(),
                Math.max(0, section.capacity() - section.enrolledCount()), section.status(), conflict,
                section.schedules().stream().map(schedule -> new ScheduleOption(schedule.dayOfWeek(),
                        schedule.startTime(), schedule.endTime())).toList());
    }

    private TermResolution resolveTerm(StudentRow student, SemesterRow semester) {
        if (student.admissionDate() == null || student.curriculumId() == null || student.curriculumId().isBlank()) {
            return new TermResolution("UNKNOWN", null, null, "CURRICULUM_TERM_UNKNOWN");
        }
        List<AcademicYearRow> admissionYears = repository.findAcademicYearsContaining(student.admissionDate());
        if (admissionYears.size() != 1) {
            return new TermResolution("UNKNOWN", null, null, "ADMISSION_YEAR_UNRESOLVED");
        }
        Integer termNumber = curriculumSemester(semester.type());
        if (termNumber == null) {
            return new TermResolution("UNKNOWN", null, null, "SEMESTER_TYPE_UNKNOWN");
        }
        int curriculumYear = semester.academicYear() - admissionYears.getFirst().year() + 1;
        if (curriculumYear < 1 || curriculumYear > 20) {
            return new TermResolution("UNKNOWN", null, null, "CURRICULUM_TERM_UNKNOWN");
        }
        return new TermResolution("RESOLVED", curriculumYear, termNumber, null);
    }

    static Integer curriculumSemester(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "FIRST", "FALL", "SEMESTER_1", "1" -> 1;
            case "SECOND", "SPRING", "SEMESTER_2", "2" -> 2;
            case "SUMMER", "THIRD", "SEMESTER_3", "3" -> 3;
            default -> null;
        };
    }

    private SemesterRow resolveSemester(String semesterId) {
        if (semesterId != null && !semesterId.isBlank()) {
            return repository.findSemester(semesterId)
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found"));
        }
        Instant now = Instant.now();
        return repository.findSemesterForOpenRound(now).or(() -> repository.findCurrentSemester(now))
                .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "CURRENT_SEMESTER_NOT_FOUND",
                        "No current semester is available"));
    }

    private StudentRow requireStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        return repository.findStudent(studentId)
                .orElseThrow(() -> problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required"));
    }

    private Target resolveTarget(AdminRunRequest request) {
        if (request == null || (blank(request.roundId()) && blank(request.semesterId()))) {
            throw problem(HttpStatus.BAD_REQUEST, "REGISTRATION_TARGET_REQUIRED", "A round or semester is required");
        }
        RoundRow round;
        SemesterRow semester;
        if (!blank(request.roundId())) {
            round = repository.findRound(request.roundId())
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration round not found"));
            semester = repository.findSemester(round.semesterId())
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found"));
            if (!blank(request.semesterId()) && !request.semesterId().equals(semester.id())) {
                throw problem(HttpStatus.BAD_REQUEST, "ROUND_SEMESTER_MISMATCH", "Round and semester do not match");
            }
        } else {
            semester = repository.findSemester(request.semesterId())
                    .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found"));
            round = repository.findOpenRound(semester.id(), Instant.now())
                    .orElseGet(() -> repository.findRoundForSemester(semester.id()).orElseThrow(() ->
                            problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "No registration round exists for semester")));
        }
        int batchSize = request.batchSize() == null ? DEFAULT_BATCH_SIZE : request.batchSize();
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw problem(HttpStatus.BAD_REQUEST, "REGISTRATION_BATCH_SIZE_INVALID",
                    "Batch size must be between 1 and " + MAX_BATCH_SIZE);
        }
        if (request.cursor() != null && request.cursor().length() > 120) {
            throw problem(HttpStatus.BAD_REQUEST, "REGISTRATION_CURSOR_INVALID", "Registration cursor is invalid");
        }
        return new Target(round, semester, batchSize, request.cursor());
    }

    private Page studentPage(Target target) {
        List<StudentCursorRow> students = repository.findStudentsForRound(target.round().id(), target.cursor(), target.batchSize());
        if (students.isEmpty()) {
            return new Page(students, false, null);
        }
        String next = students.getLast().id();
        boolean more = repository.hasStudentsAfter(target.round().id(), next);
        return new Page(students, more, more ? next : null);
    }

    private void recordStudentFailure(RunCounts counts, PlanResponse plan, String reason) {
        counts.failedStudents++;
        if (plan == null) {
            counts.addSkipped(reason, 1);
            return;
        }
        if (plan.courses().isEmpty()) {
            counts.addSkipped(reason, 1);
            return;
        }
        counts.courses += plan.courses().size();
        for (CoursePlan course : plan.courses()) {
            if (course.state() == CourseState.REGISTERABLE) {
                counts.wouldRegister++;
                counts.skipped++;
                counts.addSkipped(reason, 1);
            } else {
                counts.skipped++;
                counts.addSkipped(reason(course), 1);
            }
        }
    }

    private void addPlanCounts(RunCounts counts, PlanResponse plan) {
        if (!"RESOLVED".equals(plan.term().status())) {
            counts.failedStudents++;
            counts.addSkipped(blank(plan.term().reasonCode()) ? "CURRICULUM_TERM_UNKNOWN" : plan.term().reasonCode(), 1);
            return;
        }
        counts.courses += plan.courses().size();
        for (CoursePlan course : plan.courses()) {
            if (course.state() == CourseState.REGISTERABLE) {
                counts.wouldRegister++;
            } else {
                counts.skipped++;
                counts.addSkipped(reason(course), 1);
            }
        }
    }

    private AdminRunResponse response(String runId, Target target, RunCounts counts, boolean dryRun,
            boolean hasMore, String nextCursor) {
        List<ReasonCount> reasons = counts.reasons.entrySet().stream()
                .map(entry -> new ReasonCount(entry.getKey(), entry.getValue())).toList();
        return new AdminRunResponse(runId, target.round().id(), target.semester().id(), dryRun,
                counts.students, counts.courses, counts.wouldRegister, counts.registered, counts.skipped,
                counts.failedStudents, reasons, hasMore, nextCursor);
    }

    private StudentRow studentRow(StudentCursorRow row) {
        return new StudentRow(row.id(), row.curriculumId(), row.year(), row.admissionDate(), "ACTIVE");
    }

    private String idempotencyKey(String studentId, String roundId, String courseId) {
        String seed = studentId + "|" + roundId + "|" + courseId;
        return "curriculum-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    private CourseState stateForReason(String code) {
        return switch (code) {
            case "PREREQUISITE_UNMET", "COREQUISITE_UNMET" -> CourseState.BLOCKED_PREREQUISITE;
            case "SECTION_FULL" -> CourseState.FULL;
            case "SECTION_CLOSED", "SECTION_NOT_FOUND" -> CourseState.NO_OPEN_SECTION;
            case "DUPLICATE_COURSE", "ENROLLMENT_DUPLICATE" -> CourseState.ALREADY_REGISTERED;
            default -> CourseState.CONFLICT;
        };
    }

    private String reason(CoursePlan course) {
        return blank(course.reasonCode()) ? course.state().name() : course.reasonCode();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }

    private record Target(RoundRow round, SemesterRow semester, int batchSize, String cursor) { }

    private record Page(List<StudentCursorRow> students, boolean hasMore, String nextCursor) { }

    private record StudentApplyResult(int courses, int wouldRegister, int registered, int skipped,
            Map<String, Integer> reasons) { }

    private static final class RunCounts {
        private int students;
        private int courses;
        private int wouldRegister;
        private int registered;
        private int skipped;
        private int failedStudents;
        private final Map<String, Integer> reasons = new TreeMap<>();

        private void addSkipped(String reason, int count) {
            if (count <= 0) {
                return;
            }
            reasons.merge(reason, count, Integer::sum);
        }
    }
}
