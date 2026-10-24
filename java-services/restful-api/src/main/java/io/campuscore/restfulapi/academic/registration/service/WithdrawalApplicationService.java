package io.campuscore.restfulapi.academic.registration.service;

import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.Response;
import io.campuscore.restfulapi.academic.registration.dto.WithdrawalApplicationDtos.WindowResponse;
import io.campuscore.restfulapi.academic.registration.model.WithdrawalStatuses;
import io.campuscore.restfulapi.academic.registration.repository.WithdrawalApplicationRepository;
import io.campuscore.restfulapi.academic.service.SemesterStatuses;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.notification.model.CreateNotificationCommand;
import io.campuscore.restfulapi.notification.repository.NotificationWriteRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Approved course withdrawal (grade W). Unlike a self-service drop inside the
 * add/drop window — which leaves no transcript trace — an approved withdrawal
 * records the enrollment as WITHDRAWN with letter W: visible on the transcript
 * but excluded from attempted/earned credits, GPA and best-attempt selection.
 *
 * <p>Window rule (pinned): a regular semester ({@code FIRST}/{@code SECOND},
 * with {@code FALL}/{@code SPRING} accepted as legacy type aliases)
 * accepts applications from its start date for
 * {@code registration.withdrawal.window.days} days (default 14, env
 * {@code REGISTRATION_WITHDRAWAL_WINDOW_DAYS}); the day-14 boundary is
 * inclusive and all day math runs in {@code Asia/Bangkok}.
 */
@Service
@Profile("persistence")
public class WithdrawalApplicationService {

    static final int MAX_LIST_RESULTS = 500;
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    private final WithdrawalApplicationRepository repository;
    private final NotificationWriteRepository notifications;
    private final AdminAuditRecorder audit;
    private final int windowDays;

    public WithdrawalApplicationService(
            WithdrawalApplicationRepository repository,
            NotificationWriteRepository notifications,
            AdminAuditRecorder audit,
            @Value("${registration.withdrawal.window.days:14}") int windowDays) {
        this.repository = repository;
        this.notifications = notifications;
        this.audit = audit;
        this.windowDays = Math.max(1, windowDays);
    }

    @Transactional(readOnly = true)
    public WindowResponse window(String semesterId) {
        if (blank(semesterId)) {
            throw problem(HttpStatus.BAD_REQUEST, "SEMESTER_NOT_FOUND", "Semester is required");
        }
        Map<String, Object> semester;
        try {
            semester = repository.findSemester(semesterId.trim());
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "SEMESTER_NOT_FOUND", "Semester not found");
        }
        Instant start = instant(semester.get("semester_start"));
        Instant end = start == null ? null : windowEnd(start);
        boolean regular = isRegularSemester(String.valueOf(semester.get("type")));
        boolean open = SemesterStatuses.OPEN.contains(
                SemesterStatuses.normalize(String.valueOf(semester.get("status"))));
        boolean within = start != null && inWindow(start, Instant.now());
        return new WindowResponse(
                String.valueOf(semester.get("id")),
                String.valueOf(semester.get("name")),
                regular,
                open,
                start,
                end,
                regular && open && within);
    }

    @Transactional(readOnly = true)
    public List<Response> listForStudent(String studentId) {
        requireStudentId(studentId);
        return repository.listForStudent(studentId).stream().map(this::map).toList();
    }

    @Transactional(readOnly = true)
    public List<Response> listForReview(String status) {
        String filter = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        if (!"ALL".equals(filter)
                && !List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED").contains(filter)) {
            throw problem(HttpStatus.BAD_REQUEST, "WITHDRAWAL_APPLICATION_STATUS_INVALID",
                    "Withdrawal application status is invalid");
        }
        return repository.listForReview("ALL".equals(filter) ? null : filter, MAX_LIST_RESULTS)
                .stream().map(this::map).toList();
    }

    @Transactional
    public Response submit(String studentId, String actorId, String enrollmentId, String reason) {
        String cleanStudentId = requireStudentId(studentId);
        String cleanEnrollmentId = required(enrollmentId, "ENROLLMENT_NOT_FOUND", "Enrollment is required");
        String cleanReason = cleanReason(reason);
        // Student first: serializes with every registration mutation on this
        // student, so a submit cannot interleave with a drop/seat mutation.
        repository.lockStudent(cleanStudentId);

        Map<String, Object> enrollment;
        try {
            enrollment = repository.findEnrollmentWithSemester(cleanEnrollmentId);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
        if (!cleanStudentId.equals(String.valueOf(enrollment.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_FORBIDDEN",
                    "Enrollment does not belong to the current student");
        }
        if (!WithdrawalStatuses.WITHDRAWABLE_ENROLLMENTS.contains(String.valueOf(enrollment.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment is no longer active");
        }
        assertWithdrawableSemester(enrollment);

        if (repository.countPendingForEnrollment(cleanEnrollmentId) > 0) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_APPLICATION_EXISTS",
                    "A pending withdrawal application already exists for this enrollment");
        }
        String id = UUID.randomUUID().toString();
        try {
            repository.insert(id, cleanStudentId, cleanEnrollmentId,
                    String.valueOf(enrollment.get("semester_id")), cleanReason);
        } catch (DataIntegrityViolationException exception) {
            // The partial unique index is the final race-safe guard when two
            // browser tabs submit at the same time.
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_APPLICATION_EXISTS",
                    "A pending withdrawal application already exists for this enrollment");
        }
        audit.record(actorOr(actorId, cleanStudentId), null, "WITHDRAWAL_SUBMITTED",
                "WithdrawalApplication", id,
                "Withdrawal application " + id + " submitted for enrollment " + cleanEnrollmentId,
                null, applicationState("PENDING", enrollment.get("semester_id")));
        return map(repository.findById(id));
    }

    @Transactional
    public Response cancel(String applicationId, String studentId, String actorId) {
        String cleanStudentId = requireStudentId(studentId);
        String id = required(applicationId, "WITHDRAWAL_APPLICATION_NOT_FOUND",
                "Withdrawal application is required");
        repository.lockStudent(cleanStudentId);
        Map<String, Object> current = lockApplication(id);
        if (!cleanStudentId.equals(String.valueOf(current.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "WITHDRAWAL_APPLICATION_FORBIDDEN",
                    "Withdrawal application does not belong to the current student");
        }
        if (repository.transition(id, "CANCELLED", null, null) != 1) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_APPLICATION_NOT_PENDING",
                    "Only a pending withdrawal application can be cancelled");
        }
        audit.record(actorOr(actorId, cleanStudentId), null, "WITHDRAWAL_CANCELLED",
                "WithdrawalApplication", id,
                "Withdrawal application " + id + " cancelled by student",
                applicationState(current.get("status"), current.get("semester_id")),
                applicationState("CANCELLED", current.get("semester_id")));
        return map(repository.findById(id));
    }

    @Transactional
    public Response review(String applicationId, String decision, String reviewerId, String note) {
        String id = required(applicationId, "WITHDRAWAL_APPLICATION_NOT_FOUND",
                "Withdrawal application is required");
        String cleanDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "REJECTED").contains(cleanDecision)) {
            throw problem(HttpStatus.BAD_REQUEST, "WITHDRAWAL_DECISION_INVALID",
                    "Decision must be APPROVED or REJECTED");
        }
        String cleanReviewer = required(reviewerId, "REVIEWER_REQUIRED", "A reviewer is required");
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw problem(HttpStatus.BAD_REQUEST, "WITHDRAWAL_APPLICATION_NOTE_INVALID",
                    "Reviewer note must be at most 1000 characters");
        }

        // Lock order is Student -> Application -> Enrollment -> Section, the
        // same convention as submit/cancel/drop: cancel and drop take the
        // Student row first, so an approve that held the application row while
        // waiting for Student would close a deadlock cycle (Postgres kills one
        // side with 40P01). studentId is immutable after insert, so the
        // unlocked read below only picks which Student row to lock — the
        // PENDING check is re-done under the application lock.
        Map<String, Object> head;
        try {
            head = repository.findById(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "WITHDRAWAL_APPLICATION_NOT_FOUND",
                    "Withdrawal application was not found");
        }
        String studentId = String.valueOf(head.get("student_id"));
        repository.lockStudent(studentId);
        Map<String, Object> application = lockApplication(id);
        if (!"PENDING".equals(String.valueOf(application.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_APPLICATION_NOT_PENDING",
                    "Only a pending withdrawal application can be reviewed");
        }
        String enrollmentId = String.valueOf(application.get("enrollment_id"));
        String courseLabel;

        if ("APPROVED".equals(cleanDecision)) {
            Map<String, Object> enrollment = repository.lockEnrollment(enrollmentId);
            if (!WithdrawalStatuses.WITHDRAWABLE_ENROLLMENTS.contains(String.valueOf(enrollment.get("status")))) {
                throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE",
                        "Enrollment is no longer active");
            }
            repository.lockSection(String.valueOf(enrollment.get("section_id")));
            if (repository.markEnrollmentWithdrawn(enrollmentId,
                    WithdrawalStatuses.WITHDRAWABLE_ENROLLMENTS) != 1) {
                throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE",
                        "Enrollment is no longer active");
            }
            repository.releaseSeat(String.valueOf(enrollment.get("section_id")));
            repository.deleteSlips(studentId, String.valueOf(enrollment.get("semester_id")));
            repository.insertEvent(UUID.randomUUID().toString(), enrollmentId, studentId,
                    String.valueOf(enrollment.get("section_id")), "WITHDRAW",
                    cleanReviewer, "WITHDRAWAL|" + id);
            courseLabel = enrollmentLabel(enrollmentId);
        } else {
            courseLabel = enrollmentLabel(enrollmentId);
        }

        if (repository.transition(id, cleanDecision, cleanReviewer, cleanNote) != 1) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_APPLICATION_NOT_PENDING",
                    "Only a pending withdrawal application can be reviewed");
        }
        audit.record(cleanReviewer, null, "WITHDRAWAL_" + cleanDecision,
                "WithdrawalApplication", id,
                "Withdrawal application " + id + " " + cleanDecision + " by " + cleanReviewer,
                applicationState(application.get("status"), application.get("semester_id")),
                applicationState(cleanDecision, application.get("semester_id")));
        notifyStudent(studentId, cleanDecision, courseLabel);
        return map(repository.findById(id));
    }

    /**
     * Semester gate for submit: open lifecycle, regular type, in the
     * [startDate, startDate + windowDays] window (day-14 inclusive,
     * Asia/Bangkok). Review deliberately does not re-check the window —
     * pending applications must stay dispositionable after it closes.
     */
    private void assertWithdrawableSemester(Map<String, Object> enrollment) {
        String status = SemesterStatuses.normalize(String.valueOf(enrollment.get("semester_status")));
        if (!SemesterStatuses.OPEN.contains(status)) {
            throw problem(HttpStatus.CONFLICT, "SEMESTER_CLOSED", "Semester is closed for withdrawal requests");
        }
        if (!isRegularSemester(String.valueOf(enrollment.get("semester_type")))) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_SEMESTER_INELIGIBLE",
                    "Withdrawal is only available in a regular semester");
        }
        Instant start = instant(enrollment.get("semester_start"));
        if (start == null || !inWindow(start, Instant.now())) {
            throw problem(HttpStatus.CONFLICT, "WITHDRAWAL_WINDOW_CLOSED",
                    "The withdrawal window for this semester is closed");
        }
    }

    private boolean isRegularSemester(String type) {
        return WithdrawalStatuses.REGULAR_SEMESTER_TYPES.contains(
                type == null ? "" : type.trim().toUpperCase(Locale.ROOT));
    }

    /** startDate is a timestamp; the 2-week clock runs on Bangkok civil time. */
    private Instant windowEnd(Instant start) {
        return start.atZone(BANGKOK).toLocalDate()
                .plusDays(windowDays)
                .atTime(23, 59, 59)
                .atZone(BANGKOK)
                .toInstant();
    }

    private boolean inWindow(Instant start, Instant now) {
        return !now.isBefore(start) && !now.isAfter(windowEnd(start));
    }

    private Map<String, Object> lockApplication(String id) {
        try {
            return repository.lockById(id);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "WITHDRAWAL_APPLICATION_NOT_FOUND",
                    "Withdrawal application was not found");
        }
    }

    private String enrollmentLabel(String enrollmentId) {
        try {
            Map<String, Object> row = repository.findEnrollmentWithSemester(enrollmentId);
            String semester = String.valueOf(row.get("semester_name"));
            return "enrollment " + enrollmentId + " (" + semester + ")";
        } catch (EmptyResultDataAccessException exception) {
            return "enrollment " + enrollmentId;
        }
    }

    private void notifyStudent(String studentId, String decision, String courseLabel) {
        String userId = repository.studentUserId(studentId);
        if (blank(userId)) {
            return;
        }
        boolean approved = "APPROVED".equals(decision);
        notifications.create(new CreateNotificationCommand(
                UUID.randomUUID().toString(),
                userId,
                approved ? "Course withdrawal approved" : "Course withdrawal rejected",
                approved
                        ? "Your withdrawal request for " + courseLabel
                                + " was approved. The course now shows grade W on your transcript."
                        : "Your withdrawal request for " + courseLabel
                                + " was rejected. The enrollment remains active.",
                approved ? "SUCCESS" : "WARNING",
                "/dashboard/transcript",
                Instant.now()));
    }

    private Map<String, Object> applicationState(Object status, Object semesterId) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", status == null ? null : String.valueOf(status));
        state.put("semesterId", semesterId == null ? null : String.valueOf(semesterId));
        return state;
    }

    private String requireStudentId(String studentId) {
        return required(studentId, "STUDENT_NOT_FOUND", "Student profile is required");
    }

    private String cleanReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.length() < 20 || value.length() > 1000) {
            throw problem(HttpStatus.BAD_REQUEST, "WITHDRAWAL_APPLICATION_REASON_INVALID",
                    "Withdrawal reason must be between 20 and 1000 characters");
        }
        return value;
    }

    private static String required(String value, String code, String message) {
        if (blank(value)) {
            throw problem(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    private static String actorOr(String actorId, String fallback) {
        return actorId == null || actorId.isBlank() ? fallback : actorId;
    }

    private Response map(Map<String, Object> row) {
        return new Response(
                text(row.get("id")),
                text(row.get("student_id")),
                text(row.get("student_code")),
                text(row.get("student_name")),
                text(row.get("student_email")),
                text(row.get("enrollment_id")),
                text(row.get("semester_id")),
                text(row.get("semester_name")),
                text(row.get("course_code")),
                text(row.get("course_name")),
                text(row.get("section_number")),
                text(row.get("reason")),
                text(row.get("status")),
                text(row.get("reviewed_by")),
                instant(row.get("reviewed_at")),
                text(row.get("reviewer_note")),
                instant(row.get("created_at")),
                instant(row.get("updated_at")));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        return null;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
