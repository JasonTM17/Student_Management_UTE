package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.CreateRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.Eligibility;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.Response;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.ReviewRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.UnpublishRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.UnpublishResponse;
import io.campuscore.restfulapi.academic.model.GradeAppealStatus;
import io.campuscore.restfulapi.academic.repository.GradeAppealRepository;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.notification.model.CreateNotificationCommand;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("persistence")
public class GradeAppealService {

    private static final int MAX_LIST_RESULTS = 500;
    private final GradeAppealRepository repository;
    private final NotificationWriteRepository notifications;
    private final AdminAuditRecorder audit;
    private final int windowDays;
    private final Clock clock;

    public GradeAppealService(
            GradeAppealRepository repository,
            NotificationWriteRepository notifications,
            AdminAuditRecorder audit,
            @Value("${grade.appeal-window-days:7}") int windowDays,
            Clock gradeAppealClock) {
        this.repository = repository;
        this.notifications = notifications;
        this.audit = audit;
        this.windowDays = Math.max(1, windowDays);
        this.clock = gradeAppealClock;
    }

    @Transactional(readOnly = true)
    public List<Response> listForStudent(String studentId) {
        String id = required(studentId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "Student profile is required");
        return repository.findByStudent(id).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<Eligibility> eligibilityForStudent(String studentId) {
        String id = required(studentId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "Student profile is required");
        Instant now = clock.instant();
        return repository.appealEligibility(id).stream().map(row -> {
            Instant publishedAt = instant(row.get("grade_published_at"));
            Instant deadline = publishedAt == null ? null
                    : publishedAt.plusSeconds(windowDays * 24L * 60L * 60L);
            boolean eligible = "PUBLISHED".equals(text(row.get("grade_status")))
                    && !"DROPPED".equals(text(row.get("enrollment_status")))
                    && !"WITHDRAWN".equals(text(row.get("enrollment_status")))
                    && !"W".equalsIgnoreCase(text(row.get("letter_grade")))
                    && text(row.get("letter_grade")) != null
                    && decimal(row.get("final_grade")) != null
                    && publishedAt != null && !now.isAfter(deadline)
                    && !Boolean.TRUE.equals(row.get("appeal_submitted"));
            return new Eligibility(text(row.get("enrollment_id")), text(row.get("grade_status")),
                    publishedAt, deadline, eligible);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listForLecturer(String lecturerId, String status) {
        String id = required(lecturerId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "Lecturer profile is required");
        return repository.findForReview(normalizeFilter(status), id, MAX_LIST_RESULTS).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listForAdmin(String status) {
        return repository.findForReview(normalizeFilter(status), null, MAX_LIST_RESULTS).stream()
                .map(this::map).toList();
    }

    @Transactional
    public Response submit(String studentId, String actorId, CreateRequest request) {
        String ownerId = required(studentId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "Student profile is required");
        String enrollmentId = request == null ? "" : request.enrollmentId();
        enrollmentId = required(enrollmentId, HttpStatus.CONFLICT, "GRADE_APPEAL_ENROLLMENT_INELIGIBLE",
                "A grade enrollment is required");
        String reason = cleanReason(request == null ? null : request.reason(),
                "GRADE_APPEAL_REASON_INVALID", "Appeal reason must be between 20 and 1000 characters");
        repository.lockStudent(ownerId);

        Map<String, Object> head = findEnrollment(enrollmentId);
        if (!ownerId.equals(text(head.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "The grade enrollment does not belong to the current student");
        }
        String sectionId = text(head.get("section_id"));
        lockSection(sectionId);
        Map<String, Object> enrollment = lockEnrollment(enrollmentId);
        if (!ownerId.equals(text(enrollment.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "The grade enrollment does not belong to the current student");
        }
        assertAppealable(enrollment);
        Instant publishedAt = instant(enrollment.get("grade_published_at"));
        if (publishedAt == null) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_TIMESTAMP_UNAVAILABLE",
                    "The grade publication time is unavailable");
        }
        Instant appealUntil = publishedAt.plusSeconds(windowDays * 24L * 60L * 60L);
        if (clock.instant().isAfter(appealUntil)) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_WINDOW_CLOSED",
                    "The grade appeal window has closed");
        }
        if (repository.countAppealsForEnrollment(enrollmentId) > 0) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_ALREADY_SUBMITTED",
                    "Only one appeal may be submitted for this enrollment");
        }

        String appealId = UUID.randomUUID().toString();
        repository.insert(appealId, enrollmentId, reason);
        if (repository.markAppealed(enrollmentId) != 1) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_ENROLLMENT_INELIGIBLE",
                    "The grade is no longer published");
        }
        audit.record(actorOr(actorId, ownerId), null, "GRADE_APPEAL_SUBMITTED", "GradeAppeal", appealId,
                "Student submitted grade appeal " + appealId,
                Map.of("gradeStatus", "PUBLISHED"), Map.of("gradeStatus", "APPEALED", "status", "PENDING"));
        notifyReviewers(sectionId);
        return map(repository.findById(appealId));
    }

    @Transactional
    public Response cancel(String appealId, String studentId, String actorId) {
        Map<String, Object> head = findAppealHead(appealId);
        String ownerId = required(studentId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "Student profile is required");
        if (!ownerId.equals(text(head.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "The appeal does not belong to the current student");
        }
        repository.lockStudent(ownerId);
        Map<String, Object> appeal = lockAppeal(appealId);
        Map<String, Object> enrollment = lockEnrollment(text(appeal.get("enrollment_id")));
        lockSection(text(enrollment.get("section_id")));
        assertPending(appeal);
        if (!ownerId.equals(text(enrollment.get("student_id")))
                || !"APPEALED".equals(text(enrollment.get("grade_status")))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "Only a pending appeal can be cancelled");
        }
        if (repository.transitionToPublished(text(enrollment.get("id"))) != 1
                || repository.cancelAppeal(appealId) != 1) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "Only a pending appeal can be cancelled");
        }
        audit.record(actorOr(actorId, ownerId), null, "GRADE_APPEAL_CANCELLED", "GradeAppeal", appealId,
                "Student cancelled grade appeal " + appealId,
                Map.of("status", GradeAppealStatus.PENDING, "gradeStatus", "APPEALED"),
                Map.of("status", GradeAppealStatus.CANCELLED, "gradeStatus", "PUBLISHED"));
        return map(repository.findById(appealId));
    }

    @Transactional
    public Response review(
            String appealId,
            ReviewRequest request,
            String reviewerId,
            String lecturerId,
            boolean admin) {
        String reviewer = required(reviewerId, HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                "A reviewer is required");
        String decision = request == null || request.decision() == null
                ? "" : request.decision().trim().toUpperCase(Locale.ROOT);
        if (!Set.of(GradeAppealStatus.ACCEPTED, GradeAppealStatus.REJECTED).contains(decision)) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_DECISION_INVALID",
                    "Decision must be ACCEPTED or REJECTED");
        }
        String reason = cleanReason(request.reason(), "GRADE_APPEAL_REVIEW_REASON_INVALID",
                "Review reason must be between 20 and 1000 characters");
        BigDecimal correctedScore = request.correctedFinalGrade();
        if (GradeAppealStatus.ACCEPTED.equals(decision)) {
            validateCorrectedScore(correctedScore);
        } else if (correctedScore != null) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_CORRECTED_SCORE_INVALID",
                    "A corrected score is only provided when accepting an appeal");
        }

        String reviewerLecturerId = admin ? null : required(lecturerId, HttpStatus.FORBIDDEN,
                "GRADE_APPEAL_FORBIDDEN", "Lecturer profile is required");
        Map<String, Object> head = findAppealHead(appealId);
        String studentId = text(head.get("student_id"));
        if (!admin && !java.util.Objects.equals(reviewerLecturerId, text(head.get("lecturer_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "Only the lecturer assigned to this section may review this appeal");
        }
        if (reviewer.equals(text(head.get("student_user_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "A student cannot review their own appeal");
        }
        repository.lockStudent(studentId);
        Map<String, Object> appeal = lockAppeal(appealId);
        if (reviewer.equals(text(head.get("student_user_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "A student cannot review their own appeal");
        }
        Map<String, Object> enrollment = lockEnrollment(text(appeal.get("enrollment_id")));
        Map<String, Object> section = lockSection(text(enrollment.get("section_id")));
        if (!admin && !java.util.Objects.equals(text(section.get("lecturer_id")), reviewerLecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "GRADE_APPEAL_FORBIDDEN",
                    "Only the lecturer assigned to this section may review this appeal");
        }
        assertPending(appeal);
        if (!"APPEALED".equals(text(enrollment.get("grade_status")))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "The enrollment no longer has a pending appeal");
        }

        BigDecimal beforeScore = decimal(enrollment.get("final_grade"));
        String beforeLetter = text(enrollment.get("letter_grade"));
        if (beforeScore == null || beforeLetter == null) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "The published grade is incomplete");
        }
        BigDecimal afterScore = correctedScore;
        String afterLetter = null;
        int enrollmentUpdated;
        if (GradeAppealStatus.ACCEPTED.equals(decision)) {
            afterScore = correctedScore.setScale(2, java.math.RoundingMode.UNNECESSARY);
            afterLetter = AcademicMutationService.letterGrade(afterScore);
            enrollmentUpdated = repository.correctPublishedGrade(text(enrollment.get("id")), afterScore, afterLetter);
        } else {
            enrollmentUpdated = repository.transitionToPublished(text(enrollment.get("id")));
        }
        if (enrollmentUpdated != 1 || repository.resolveAppeal(appealId, decision, reviewer, reason,
                beforeScore, beforeLetter, afterScore, afterLetter) != 1) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "The appeal is no longer pending");
        }
        Map<String, Object> before = gradeState(beforeScore, beforeLetter, "APPEALED", GradeAppealStatus.PENDING);
        Map<String, Object> after = gradeState(afterScore == null ? beforeScore : afterScore,
                afterLetter == null ? beforeLetter : afterLetter, "PUBLISHED", decision);
        after.put("resolutionReason", reason);
        audit.record(reviewer, null, "GRADE_APPEAL_" + decision, "GradeAppeal", appealId,
                "Grade appeal " + appealId + " resolved as " + decision, before, after);
        notifyStudent(text(head.get("student_user_id")), decision);
        return map(repository.findById(appealId));
    }

    @Transactional
    public UnpublishResponse unpublish(
            String sectionId,
            UnpublishRequest request,
            String actorId) {
        String reason = cleanReason(request == null ? null : request.reason(),
                "GRADE_UNPUBLISH_REASON_INVALID", "Unpublish reason must be between 20 and 1000 characters");
        String section = required(sectionId, HttpStatus.NOT_FOUND, "GRADE_UNPUBLISH_CONFLICT",
                "Section is required");
        List<String> requested = normalizeEnrollmentIds(request == null ? null : request.enrollmentIds());
        boolean all = requested.isEmpty();
        List<Map<String, Object>> candidates = repository.sectionCandidates(section, all ? null : requested);
        if (!all && candidates.size() != requested.size()) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_UNPUBLISH_ENROLLMENTS_INVALID",
                    "Every selected enrollment must belong to this section");
        }

        // Lock the section's students before the Section row. Grade publication
        // already locks Section before updating enrollments, so taking the
        // enrollment locks first can leave a stale target set or invert the
        // publication lock order. Re-read official grades under Section below.
        List<String> lockedStudentIds = candidates.stream()
                .map(row -> text(row.get("student_id"))).distinct().sorted().toList();
        repository.lockStudents(lockedStudentIds);
        lockSection(section);
        candidates = repository.sectionCandidates(section, all ? null : requested);
        if (!all && candidates.size() != requested.size()) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "The selected grades changed before they could be unpublished");
        }
        List<Map<String, Object>> targets = candidates.stream()
                .filter(row -> all && ("PUBLISHED".equals(text(row.get("grade_status")))
                        || "APPEALED".equals(text(row.get("grade_status"))))
                        && isGradeableEnrollmentStatus(text(row.get("enrollment_status"))))
                .toList();
        if (!all) targets = candidates;
        if (!all && targets.stream()
                .anyMatch(row -> !isGradeableEnrollmentStatus(text(row.get("enrollment_status"))))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "Every selected enrollment must be eligible for grade publication");
        }
        if (!all && targets.stream().anyMatch(row -> !"PUBLISHED".equals(text(row.get("grade_status")))
                && !"APPEALED".equals(text(row.get("grade_status"))))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "Every selected enrollment must have a published grade");
        }

        List<String> enrollmentIds = targets.stream().map(row -> text(row.get("enrollment_id"))).toList();
        List<String> targetStudentIds = targets.stream()
                .map(row -> text(row.get("student_id"))).distinct().sorted().toList();
        if (!lockedStudentIds.containsAll(targetStudentIds)) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "The section changed while its grades were being unpublished");
        }
        List<Map<String, Object>> pending = repository.lockPendingAppeals(enrollmentIds);
        List<Map<String, Object>> lockedEnrollments = repository.lockSectionEnrollments(enrollmentIds);
        if (!pending.isEmpty()) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_APPEAL_PENDING",
                    "Grades with a pending appeal cannot be unpublished");
        }
        List<Map<String, Object>> accepted = repository.lockAcceptedAppeals(enrollmentIds);
        if (!accepted.isEmpty()) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_CORRECTION_LOCKED",
                    "Grades corrected by an accepted appeal cannot be unpublished");
        }
        if (lockedEnrollments.size() != enrollmentIds.size()
                || lockedEnrollments.stream().anyMatch(row -> !section.equals(text(row.get("section_id")))
                        || !"PUBLISHED".equals(text(row.get("grade_status")))
                        || !isGradeableEnrollmentStatus(text(row.get("enrollment_status"))))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "The selected grades changed before they could be unpublished");
        }
        if (enrollmentIds.isEmpty()) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "This section has no published grades to unpublish");
        }

        int updated = repository.unpublish(enrollmentIds);
        if (updated != enrollmentIds.size()) {
            throw problem(HttpStatus.CONFLICT, "GRADE_UNPUBLISH_CONFLICT",
                    "The selected grades changed before they could be unpublished");
        }
        List<CreateNotificationCommand> batch = new ArrayList<>(targets.size());
        for (Map<String, Object> target : targets) {
            String enrollmentId = text(target.get("enrollment_id"));
            audit.record(actorId, null, "GRADE_UNPUBLISHED", "Enrollment", enrollmentId,
                    "Admin unpublished a grade for enrollment " + enrollmentId,
                    gradeState(decimal(target.get("final_grade")), text(target.get("letter_grade")),
                            "PUBLISHED", null),
                    Map.of("gradeStatus", "DRAFT", "gradePublishedAt", "cleared", "reason", reason));
            batch.add(new CreateNotificationCommand(UUID.randomUUID().toString(), text(target.get("user_id")),
                    "Grade publication withdrawn", "A published grade was returned to draft for review.",
                    "GRADE_UNPUBLISH", "/dashboard/grades", clock.instant()));
        }
        notifications.createBatch(batch);
        return new UnpublishResponse(section, updated);
    }

    private void assertAppealable(Map<String, Object> enrollment) {
        String enrollmentStatus = text(enrollment.get("enrollment_status"));
        if (!"PUBLISHED".equals(text(enrollment.get("grade_status")))
                || "DROPPED".equals(enrollmentStatus) || "WITHDRAWN".equals(enrollmentStatus)
                || "W".equalsIgnoreCase(text(enrollment.get("letter_grade")))
                || text(enrollment.get("letter_grade")) == null
                || decimal(enrollment.get("final_grade")) == null) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_ENROLLMENT_INELIGIBLE",
                    "Only a published final grade for an eligible enrollment can be appealed");
        }
    }

    private void validateCorrectedScore(BigDecimal score) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0
                || score.compareTo(BigDecimal.TEN) > 0 || score.scale() > 2) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_CORRECTED_SCORE_INVALID",
                    "Corrected final grade must be from 0 to 10 with at most two decimal places");
        }
    }

    private static void assertPending(Map<String, Object> appeal) {
        if (!GradeAppealStatus.PENDING.equals(text(appeal.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_NOT_PENDING",
                    "Only a pending appeal can be changed");
        }
    }

    private String cleanReason(String value, String code, String message) {
        String reason = value == null ? "" : value.trim();
        if (reason.length() < 20 || reason.length() > 1000) {
            throw problem(HttpStatus.BAD_REQUEST, code, message);
        }
        return reason;
    }

    private String normalizeFilter(String value) {
        String filter = value == null || value.isBlank() ? GradeAppealStatus.PENDING
                : value.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(filter) && !GradeAppealStatus.FILTERS.contains(filter)) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_APPEAL_STATUS_INVALID",
                    "Appeal status filter is invalid");
        }
        return "ALL".equals(filter) ? null : filter;
    }

    private List<String> normalizeEnrollmentIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        if (ids.size() > MAX_LIST_RESULTS || ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_UNPUBLISH_ENROLLMENTS_INVALID",
                    "Enrollment selection is invalid");
        }
        List<String> normalized = ids.stream().map(String::trim).distinct().sorted().toList();
        if (normalized.size() != ids.size()) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_UNPUBLISH_ENROLLMENTS_INVALID",
                    "Enrollment selection contains duplicates");
        }
        return normalized;
    }

    private Map<String, Object> findEnrollment(String id) {
        try {
            return repository.findEnrollment(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.CONFLICT, "GRADE_APPEAL_ENROLLMENT_INELIGIBLE",
                    "The enrollment is unavailable for appeal");
        }
    }

    private Map<String, Object> lockEnrollment(String id) {
        try {
            return repository.lockEnrollment(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "GRADE_APPEAL_NOT_FOUND", "The grade appeal was not found");
        }
    }

    private Map<String, Object> findAppealHead(String id) {
        try {
            return repository.findAppealHead(required(id, HttpStatus.NOT_FOUND, "GRADE_APPEAL_NOT_FOUND",
                    "The grade appeal was not found"));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "GRADE_APPEAL_NOT_FOUND", "The grade appeal was not found");
        }
    }

    private Map<String, Object> lockAppeal(String id) {
        try {
            return repository.lockAppeal(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "GRADE_APPEAL_NOT_FOUND", "The grade appeal was not found");
        }
    }

    private Map<String, Object> lockSection(String id) {
        try {
            return repository.lockSection(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "GRADE_UNPUBLISH_CONFLICT", "The section was not found");
        }
    }

    private void notifyReviewers(String sectionId) {
        List<Map<String, Object>> reviewers = repository.findLecturerForSection(sectionId);
        final String message;
        final String link;
        if (reviewers.isEmpty()) {
            // An unstaffed section still needs a reachable review path. Admins
            // can resolve these appeals under the approved absence fallback.
            reviewers = repository.findAdminReviewers();
            message = "A student submitted a grade appeal for a section without an assigned lecturer.";
            link = "/admin/grade-appeals";
        } else {
            message = "A student submitted a grade appeal for one of your sections.";
            link = "/dashboard/lecturer/grade-appeals";
        }
        List<CreateNotificationCommand> batch = reviewers.stream()
                .map(reviewer -> new CreateNotificationCommand(UUID.randomUUID().toString(),
                        text(reviewer.get("user_id")), "Grade appeal received", message, "GRADE_APPEAL",
                        link, clock.instant()))
                .toList();
        if (!batch.isEmpty()) notifications.createBatch(batch);
    }

    private void notifyStudent(String userId, String decision) {
        boolean accepted = GradeAppealStatus.ACCEPTED.equals(decision);
        notifications.create(new CreateNotificationCommand(UUID.randomUUID().toString(), userId,
                accepted ? "Grade appeal accepted" : "Grade appeal rejected",
                accepted ? "Your grade appeal was accepted and your official grade was updated."
                        : "Your grade appeal was reviewed and rejected.",
                accepted ? "SUCCESS" : "WARNING", "/dashboard/grades", clock.instant()));
    }

    private Response map(Map<String, Object> row) {
        Instant publishedAt = instant(row.get("grade_published_at"));
        return new Response(text(row.get("id")), text(row.get("enrollment_id")), text(row.get("student_id")),
                text(row.get("student_code")), text(row.get("student_name")),
                text(row.get("course_code")), text(row.get("course_name")), text(row.get("section_id")),
                text(row.get("section_number")), text(row.get("semester_name")), decimal(row.get("final_grade")),
                text(row.get("letter_grade")), text(row.get("grade_status")), publishedAt,
                publishedAt == null ? null : publishedAt.plusSeconds(windowDays * 24L * 60L * 60L),
                text(row.get("reason")), text(row.get("status")), text(row.get("resolution_reason")),
                text(row.get("reviewed_by")), instant(row.get("reviewed_at")), decimal(row.get("before_final_grade")),
                text(row.get("before_letter_grade")), decimal(row.get("after_final_grade")),
                text(row.get("after_letter_grade")), instant(row.get("created_at")), instant(row.get("updated_at")));
    }

    private static Map<String, Object> gradeState(
            BigDecimal score, String letter, String gradeStatus, String appealStatus) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("finalGrade", score);
        state.put("letterGrade", letter);
        state.put("gradeStatus", gradeStatus);
        state.put("appealStatus", appealStatus);
        return state;
    }

    private static String required(String value, HttpStatus status, String code, String message) {
        if (value == null || value.isBlank()) throw problem(status, code, message);
        return value.trim();
    }

    private static String actorOr(String actor, String fallback) {
        return actor == null || actor.isBlank() ? fallback : actor;
    }

    private static boolean isGradeableEnrollmentStatus(String status) {
        return status != null && Set.of("ENROLLED", "CONFIRMED", "COMPLETED").contains(status);
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal decimal ? decimal : value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue()) : null;
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        if (value instanceof java.time.ZonedDateTime zonedDateTime) return zonedDateTime.toInstant();
        return null;
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
