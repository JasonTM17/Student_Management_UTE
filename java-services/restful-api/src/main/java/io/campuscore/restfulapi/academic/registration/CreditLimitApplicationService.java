package io.campuscore.restfulapi.academic.registration;

import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationDtos.Response;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the student-specific credit-limit exception workflow.
 *
 * <p>The registration round remains the source of the standard limit (28).
 * An approved row in this table can raise one student and one round to 30;
 * it can never raise the round-wide limit or accept a student-provided status.
 */
@Service
@Profile("persistence")
public class CreditLimitApplicationService {

    public static final int STANDARD_LIMIT = 28;
    public static final int APPROVED_LIMIT = 30;

    private static final String APPLICATION = "academic.\"CreditLimitApplication\"";
    private static final String ROUND = "academic.\"RegistrationRound\"";
    private static final String STUDENT = "academic.\"Student\"";
    private static final String SEMESTER = "academic.\"Semester\"";
    private static final String USER = "campuscore_auth.\"User\"";

    private final NamedParameterJdbcTemplate jdbc;

    public CreditLimitApplicationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Response findForStudent(String studentId, String roundId) {
        if (blank(studentId) || blank(roundId)) {
            return null;
        }
        try {
            return map(jdbc.queryForMap(applicationSelect() + " WHERE application.\"studentId\" = :studentId"
                    + " AND application.\"roundId\" = :roundId ORDER BY application.\"createdAt\" DESC LIMIT 1",
                    new MapSqlParameterSource().addValue("studentId", studentId).addValue("roundId", roundId)));
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    @Transactional
    public Response submit(String studentId, String roundId, String reason) {
        String cleanStudentId = required(studentId, "STUDENT_NOT_FOUND", "Student profile is required");
        String cleanRoundId = required(roundId, "ROUND_NOT_FOUND", "Registration round is required");
        String cleanReason = cleanReason(reason);
        Map<String, Object> round = openRound(cleanRoundId);
        ensureStudent(cleanStudentId);

        Long active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + APPLICATION
                        + " WHERE \"studentId\" = :studentId AND \"roundId\" = :roundId"
                        + " AND \"status\" IN ('PENDING', 'APPROVED')",
                new MapSqlParameterSource().addValue("studentId", cleanStudentId).addValue("roundId", cleanRoundId),
                Long.class);
        if (active != null && active > 0) {
            throw problem(HttpStatus.CONFLICT, "CREDIT_LIMIT_APPLICATION_EXISTS",
                    "An active credit-limit application already exists for this registration round");
        }

        String id = UUID.randomUUID().toString();
        try {
            jdbc.update(
                    "INSERT INTO " + APPLICATION
                            + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"requestedLimit\", \"reason\")"
                            + " VALUES (:id, :studentId, :semesterId, :roundId, :requestedLimit, :reason)",
                    new MapSqlParameterSource()
                            .addValue("id", id)
                            .addValue("studentId", cleanStudentId)
                            .addValue("semesterId", round.get("semester_id"))
                            .addValue("roundId", cleanRoundId)
                            .addValue("requestedLimit", APPROVED_LIMIT)
                            .addValue("reason", cleanReason));
        } catch (DataIntegrityViolationException exception) {
            // The partial unique index is the final race-safe guard when two
            // browser tabs submit at the same time.
            throw problem(HttpStatus.CONFLICT, "CREDIT_LIMIT_APPLICATION_EXISTS",
                    "An active credit-limit application already exists for this registration round");
        }
        return findById(id);
    }

    @Transactional(readOnly = true)
    public List<Response> list(String status) {
        String filter = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        if (!filter.equals("ALL") && !List.of("PENDING", "APPROVED", "REJECTED").contains(filter)) {
            throw problem(HttpStatus.BAD_REQUEST, "CREDIT_LIMIT_APPLICATION_STATUS_INVALID",
                    "Credit-limit application status is invalid");
        }
        String sql = applicationSelect();
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (!filter.equals("ALL")) {
            sql += " WHERE application.\"status\" = :status";
            parameters.addValue("status", filter);
        }
        sql += " ORDER BY application.\"createdAt\" DESC";
        return jdbc.queryForList(sql, parameters).stream().map(this::map).toList();
    }

    @Transactional
    public Response review(String applicationId, String decision, String reviewerId, String note) {
        String id = required(applicationId, "CREDIT_LIMIT_APPLICATION_NOT_FOUND", "Credit-limit application is required");
        String cleanDecision = decision == null ? "" : decision.trim().toUpperCase(Locale.ROOT);
        if (!List.of("APPROVED", "REJECTED").contains(cleanDecision)) {
            throw problem(HttpStatus.BAD_REQUEST, "CREDIT_LIMIT_APPLICATION_DECISION_INVALID",
                    "Decision must be APPROVED or REJECTED");
        }
        String cleanReviewer = required(reviewerId, "REVIEWER_REQUIRED", "A reviewer is required");
        String cleanNote = note == null ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > 1000) {
            throw problem(HttpStatus.BAD_REQUEST, "CREDIT_LIMIT_APPLICATION_NOTE_INVALID",
                    "Reviewer note must be at most 1000 characters");
        }

        Map<String, Object> current = lockById(id);
        if (!"PENDING".equals(String.valueOf(current.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "CREDIT_LIMIT_APPLICATION_NOT_PENDING",
                    "Only a pending credit-limit application can be reviewed");
        }
        openRound(String.valueOf(current.get("round_id")));
        int changed = jdbc.update(
                "UPDATE " + APPLICATION
                        + " SET \"status\" = :status, \"reviewedBy\" = :reviewedBy,"
                        + " \"reviewedAt\" = CURRENT_TIMESTAMP, \"reviewerNote\" = :reviewerNote,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP, \"version\" = \"version\" + 1"
                        + " WHERE \"id\" = :id AND \"status\" = 'PENDING'",
                new MapSqlParameterSource()
                        .addValue("status", cleanDecision)
                        .addValue("reviewedBy", cleanReviewer)
                        .addValue("reviewerNote", cleanNote)
                        .addValue("id", id));
        if (changed != 1) {
            throw problem(HttpStatus.CONFLICT, "CREDIT_LIMIT_APPLICATION_NOT_PENDING",
                    "Only a pending credit-limit application can be reviewed");
        }
        return findById(id);
    }

    /** Returns the standard round limit, raised only by a matching approved row. */
    @Transactional(readOnly = true)
    public int effectiveLimit(String studentId, Map<String, Object> round) {
        int standard = standardLimit(round);
        Integer requested;
        try {
            requested = jdbc.queryForObject(
                    "SELECT \"requestedLimit\" FROM " + APPLICATION
                            + " WHERE \"studentId\" = :studentId AND \"roundId\" = :roundId"
                            + " AND \"status\" = 'APPROVED' ORDER BY \"reviewedAt\" DESC LIMIT 1",
                    new MapSqlParameterSource()
                            .addValue("studentId", studentId)
                            .addValue("roundId", round.get("id")),
                    Integer.class);
        } catch (EmptyResultDataAccessException exception) {
            requested = null;
        }
        return requested == null ? standard : Math.min(APPROVED_LIMIT, Math.max(standard, requested));
    }

    public int standardLimit(Map<String, Object> round) {
        Object value = round.get("credit_limit");
        int configured = value instanceof Number number ? number.intValue() : STANDARD_LIMIT;
        return Math.max(1, Math.min(STANDARD_LIMIT, configured));
    }

    private Response findById(String id) {
        try {
            return map(jdbc.queryForMap(applicationSelect() + " WHERE application.\"id\" = :id",
                    new MapSqlParameterSource("id", id)));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "CREDIT_LIMIT_APPLICATION_NOT_FOUND",
                    "Credit-limit application was not found");
        }
    }

    private Map<String, Object> lockById(String id) {
        try {
            return jdbc.queryForMap("SELECT \"id\", \"roundId\" AS round_id, \"status\" FROM " + APPLICATION
                    + " WHERE \"id\" = :id FOR UPDATE", new MapSqlParameterSource("id", id));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "CREDIT_LIMIT_APPLICATION_NOT_FOUND",
                    "Credit-limit application was not found");
        }
    }

    private Map<String, Object> openRound(String roundId) {
        Map<String, Object> round;
        try {
            round = jdbc.queryForMap(
                    "SELECT \"id\", \"semesterId\" AS semester_id, \"status\","
                            + " \"windowStart\" AS window_start, \"windowEnd\" AS window_end,"
                            + " \"creditLimit\" AS credit_limit FROM " + ROUND + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", roundId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration round not found");
        }
        if (!"OPEN".equals(String.valueOf(round.get("status"))) || !inWindow(round, Instant.now())) {
            throw problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "Registration window is closed");
        }
        return round;
    }

    private void ensureStudent(String studentId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT + " WHERE \"id\" = :studentId",
                new MapSqlParameterSource("studentId", studentId), Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Student profile not found");
        }
    }

    private String cleanReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.length() < 20 || value.length() > 1000) {
            throw problem(HttpStatus.BAD_REQUEST, "CREDIT_LIMIT_APPLICATION_REASON_INVALID",
                    "Application reason must be between 20 and 1000 characters");
        }
        return value;
    }

    private String required(String value, String code, String message) {
        if (blank(value)) {
            throw problem(HttpStatus.BAD_REQUEST, code, message);
        }
        return value.trim();
    }

    private boolean inWindow(Map<String, Object> round, Instant now) {
        Instant start = instant(round.get("window_start"));
        Instant end = instant(round.get("window_end"));
        return start != null && end != null && !now.isBefore(start) && !now.isAfter(end);
    }

    private String applicationSelect() {
        return "SELECT application.\"id\", application.\"studentId\", student.\"studentId\" AS student_code,"
                + " TRIM(COALESCE(account.\"lastName\", '') || ' ' || COALESCE(account.\"firstName\", '')) AS student_name,"
                + " account.\"email\" AS student_email, application.\"semesterId\", semester.\"name\" AS semester_name,"
                + " application.\"roundId\", round.\"name\" AS round_name,"
                + " LEAST(round.\"creditLimit\", " + STANDARD_LIMIT + ") AS standard_limit,"
                + " application.\"requestedLimit\", application.\"reason\", application.\"status\","
                + " application.\"reviewedBy\", application.\"reviewedAt\", application.\"reviewerNote\","
                + " application.\"createdAt\", application.\"updatedAt\""
                + " FROM " + APPLICATION + " application"
                + " JOIN " + STUDENT + " student ON student.\"id\" = application.\"studentId\""
                + " JOIN " + USER + " account ON account.\"id\" = student.\"userId\""
                + " JOIN " + SEMESTER + " semester ON semester.\"id\" = application.\"semesterId\""
                + " JOIN " + ROUND + " round ON round.\"id\" = application.\"roundId\"";
    }

    private Response map(Map<String, Object> row) {
        return new Response(
                text(row.get("id")),
                text(row.get("studentId")),
                text(row.get("student_code")),
                text(row.get("student_name")),
                text(row.get("student_email")),
                text(row.get("semesterId")),
                text(row.get("semester_name")),
                text(row.get("roundId")),
                text(row.get("round_name")),
                number(row.get("standard_limit"), STANDARD_LIMIT),
                number(row.get("requestedLimit"), APPROVED_LIMIT),
                text(row.get("reason")),
                text(row.get("status")),
                text(row.get("reviewedBy")),
                instant(row.get("reviewedAt")),
                text(row.get("reviewerNote")),
                instant(row.get("createdAt")),
                instant(row.get("updatedAt")));
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
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
