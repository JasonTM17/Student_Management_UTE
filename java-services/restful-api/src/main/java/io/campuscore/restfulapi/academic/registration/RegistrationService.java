package io.campuscore.restfulapi.academic.registration;

import io.campuscore.restfulapi.academic.registration.RegistrationDtos.CatalogSectionResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.EligibilityResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.RoundResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("persistence")
public class RegistrationService {

    private static final String ROUND = "academic.\"RegistrationRound\"";
    private static final String COHORT = "academic.\"RegistrationRoundCohort\"";
    private static final String REQUIREMENT = "academic.\"CourseRequirement\"";
    private static final String IDEMPOTENCY = "academic.\"RegistrationIdempotency\"";
    private static final String EVENT = "academic.\"EnrollmentEvent\"";
    private static final String SLIP = "academic.\"RegistrationSlip\"";
    private static final String SECTION = "academic.\"Section\"";
    private static final String ENROLLMENT = "academic.\"Enrollment\"";
    private static final String COURSE = "academic.\"Course\"";
    private static final String STUDENT = "academic.\"Student\"";
    private static final String SCHEDULE = "academic.\"SectionSchedule\"";

    private final NamedParameterJdbcTemplate jdbc;
    private final AcademicEnrollmentReadService reads;
    private final RegistrationPdfRenderer pdfRenderer;
    private final TransactionTemplate transactions;
    private final boolean postgres;

    public RegistrationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicEnrollmentReadService reads,
            RegistrationPdfRenderer pdfRenderer,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.reads = reads;
        this.pdfRenderer = pdfRenderer;
        this.transactions = new TransactionTemplate(transactionManager);
        this.postgres = jdbc.getJdbcOperations().execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
    }

    public List<RoundResponse> listRounds(String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sql = "SELECT \"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\", \"creditLimit\""
                + " FROM " + ROUND;
        if (semesterId != null && !semesterId.isBlank()) {
            sql += " WHERE \"semesterId\" = :semesterId";
            parameters.addValue("semesterId", semesterId);
        }
        sql += " ORDER BY \"windowStart\"";
        return jdbc.query(sql, parameters, (rs, rowNum) -> new RoundResponse(
                rs.getString("id"),
                rs.getString("semesterId"),
                rs.getString("name"),
                rs.getString("kind"),
                rs.getString("status"),
                timestamp(rs.getTimestamp("windowStart")),
                timestamp(rs.getTimestamp("windowEnd")),
                rs.getInt("creditLimit")));
    }

    @Transactional
    public EligibilityResponse eligibility(String studentId, String semesterId, String roundId) {
        Map<String, Object> student = requireStudent(studentId);
        Map<String, Object> round = roundId == null || roundId.isBlank()
                ? openRoundReadOnly(semesterId, "REGISTRATION")
                : round(roundId);
        assertEligible(student, round, Instant.now());
        int used = creditsUsed(studentId, String.valueOf(round.get("semester_id")));
        int limit = ((Number) round.get("credit_limit")).intValue();
        return new EligibilityResponse(
                String.valueOf(round.get("id")),
                String.valueOf(round.get("semester_id")),
                String.valueOf(round.get("kind")),
                true,
                limit,
                used,
                Math.max(0, limit - used),
                timestamp((Timestamp) round.get("window_start")),
                timestamp((Timestamp) round.get("window_end")));
    }

    @Transactional
    public List<CatalogSectionResponse> catalog(String studentId, String semesterId, String roundId) {
        Map<String, Object> student = requireStudent(studentId);
        Map<String, Object> round = roundId == null || roundId.isBlank()
                ? openRoundReadOnly(semesterId, "REGISTRATION")
                : round(roundId);
        assertEligible(student, round, Instant.now());
        String effectiveSemester = String.valueOf(round.get("semester_id"));
        List<Map<String, Object>> active = activeEnrollments(studentId, effectiveSemester);
        List<TimeRange> busy = schedulesForSections(active.stream().map(row -> String.valueOf(row.get("section_id"))).toList());
        List<String> enrolledSections = active.stream().map(row -> String.valueOf(row.get("section_id"))).toList();
        return jdbc.query(
                "SELECT section.\"id\", section.\"sectionNumber\", section.\"courseId\", course.\"code\", course.\"name\","
                        + " course.\"credits\", section.\"capacity\", section.\"enrolledCount\", section.\"status\""
                        + " FROM " + SECTION + " section JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " WHERE section.\"semesterId\" = :semesterId ORDER BY course.\"code\"",
                new MapSqlParameterSource("semesterId", effectiveSemester),
                (rs, rowNum) -> {
                    String sectionId = rs.getString("id");
                    boolean conflict = overlaps(busy, schedulesForSection(sectionId));
                    int remaining = Math.max(0, rs.getInt("capacity") - rs.getInt("enrolledCount"));
                    return new CatalogSectionResponse(
                            sectionId,
                            rs.getString("sectionNumber"),
                            rs.getString("courseId"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getInt("credits"),
                            rs.getInt("capacity"),
                            rs.getInt("enrolledCount"),
                            remaining,
                            rs.getString("status"),
                            conflict && !enrolledSections.contains(sectionId),
                            enrolledSections.contains(sectionId));
                });
    }

    @Transactional
    public SummaryResponse summary(String studentId, String semesterId) {
        requireStudent(studentId);
        Map<String, Object> round = openRoundReadOnly(semesterId, "REGISTRATION");
        int used = creditsUsed(studentId, String.valueOf(round.get("semester_id")));
        int limit = ((Number) round.get("credit_limit")).intValue();
        List<String> ids = activeEnrollments(studentId, String.valueOf(round.get("semester_id"))).stream()
                .map(row -> String.valueOf(row.get("id")))
                .toList();
        return new SummaryResponse(String.valueOf(round.get("id")), limit, used, Math.max(0, limit - used), ids);
    }

    public EnrollmentResponse enroll(
            String studentId,
            String sectionId,
            List<String> roles,
            String idempotencyKey) {
        requireKey(idempotencyKey);
        String hash = sha256("ENROLL|" + sectionId);
        EnrollmentResponse response = transactions.execute(status -> {
            EnrollmentResponse enrollment = enrollLocked(studentId, sectionId, roles, idempotencyKey, hash);
            persistSlip(studentId, enrollment);
            return enrollment;
        });
        if (response == null) {
            throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "ENROLLMENT_FAILED", "Enrollment transaction returned no result");
        }
        return response;
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey) {
        requireKey(idempotencyKey);
        String hash = sha256("DROP|" + enrollmentId);
        transactions.executeWithoutResult(status -> dropLocked(enrollmentId, studentId, roles, idempotencyKey, hash));
    }

    public SlipPayload slip(String studentId, String semesterId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("studentId", studentId);
        String sql = "SELECT \"sha256\", \"payload\" FROM " + SLIP + " WHERE \"studentId\" = :studentId";
        if (semesterId != null && !semesterId.isBlank()) {
            sql += " AND \"semesterId\" = :semesterId";
            parameters.addValue("semesterId", semesterId);
        }
        sql += " ORDER BY \"createdAt\" DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration slip not found");
        }
        Map<String, Object> row = rows.get(0);
        return new SlipPayload(String.valueOf(row.get("sha256")).trim(), payloadBytes(row.get("payload")));
    }

    public record SlipPayload(String sha256, byte[] payload) {
    }

    private EnrollmentResponse enrollLocked(
            String studentId,
            String sectionId,
            List<String> roles,
            String idempotencyKey,
            String hash) {
        Map<String, Object> replay = claimIdempotency(studentId, idempotencyKey, hash);
        if (replay != null) {
            return reads.findEnrollment(String.valueOf(replay.get("enrollment_id")), roles, studentId);
        }
        Map<String, Object> student = requireStudent(studentId);
        Map<String, Object> section = lockSection(sectionId);
        String semesterId = String.valueOf(section.get("semester_id"));
        String courseId = String.valueOf(section.get("course_id"));
        int credits = ((Number) section.get("credits")).intValue();
        if (!"OPEN".equals(String.valueOf(section.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "SECTION_CLOSED", "Section is not open for registration");
        }
        Map<String, Object> round = openRound(semesterId, "REGISTRATION");
        assertEligible(student, round, Instant.now());
        List<Map<String, Object>> active = lockActiveEnrollments(studentId, semesterId);
        if (active.stream().anyMatch(row -> sectionId.equals(String.valueOf(row.get("section_id"))))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_DUPLICATE", "Student is already enrolled in this section");
        }
        if (active.stream().anyMatch(row -> courseId.equals(String.valueOf(row.get("course_id"))))) {
            throw problem(HttpStatus.CONFLICT, "DUPLICATE_COURSE", "Student already has this course this term");
        }
        int used = active.stream().mapToInt(row -> ((Number) row.get("credits")).intValue()).sum();
        int limit = ((Number) round.get("credit_limit")).intValue();
        if (used + credits > limit) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "CREDIT_CAP_EXCEEDED", "Credit cap exceeded");
        }
        assertPrereqAndCoreq(studentId, courseId, semesterId, List.of(sectionId));
        if (overlaps(
                schedulesForSections(active.stream().map(row -> String.valueOf(row.get("section_id"))).toList()),
                schedulesForSection(sectionId))) {
            throw problem(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT", "Section overlaps an enrolled meeting time");
        }
        int updated = jdbc.update(
                "UPDATE " + SECTION
                        + " SET \"enrolledCount\" = \"enrolledCount\" + 1, \"version\" = \"version\" + 1,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id AND \"enrolledCount\" < \"capacity\""
                        + " AND \"status\" = 'OPEN'",
                new MapSqlParameterSource("id", sectionId));
        if (updated == 0) {
            throw problem(HttpStatus.CONFLICT, "SECTION_FULL", "Section is full");
        }
        String enrollmentId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO " + ENROLLMENT
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\","
                        + " \"gradeStatus\", \"courseId\", \"roundId\", \"creditsSnapshot\", \"version\")"
                        + " VALUES (:id, :studentId, :sectionId, :semesterId, 'ENROLLED', :now, 'NOT_GRADED',"
                        + " :courseId, :roundId, :credits, 0)",
                new MapSqlParameterSource()
                        .addValue("id", enrollmentId)
                        .addValue("studentId", studentId)
                        .addValue("sectionId", sectionId)
                        .addValue("semesterId", semesterId)
                        .addValue("now", Timestamp.from(now))
                        .addValue("courseId", courseId)
                        .addValue("roundId", round.get("id"))
                        .addValue("credits", credits));
        jdbc.update(
                "INSERT INTO " + EVENT
                        + " (\"id\", \"enrollmentId\", \"studentId\", \"sectionId\", \"action\", \"actorId\", \"requestHash\")"
                        + " VALUES (:id, :enrollmentId, :studentId, :sectionId, 'ENROLL', :actorId, :hash)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("studentId", studentId)
                        .addValue("sectionId", sectionId)
                        .addValue("actorId", studentId)
                        .addValue("hash", hash));
        completeIdempotency(studentId, idempotencyKey, enrollmentId, hash);
        return reads.findEnrollment(enrollmentId, roles, studentId);
    }

    private void dropLocked(
            String enrollmentId,
            String studentId,
            List<String> roles,
            String idempotencyKey,
            String hash) {
        Map<String, Object> replay = claimIdempotency(studentId == null ? enrollmentId : studentId, idempotencyKey, hash);
        if (replay != null) {
            return;
        }
        Map<String, Object> enrollment;
        try {
            enrollment = jdbc.queryForMap(
                    "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id, \"semesterId\" AS semester_id,"
                            + " \"status\" FROM " + ENROLLMENT + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", enrollmentId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
        boolean admin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
        if (!admin && !studentId.equals(String.valueOf(enrollment.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_FORBIDDEN", "Enrollment does not belong to the current student");
        }
        if (!List.of("ENROLLED", "PENDING").contains(String.valueOf(enrollment.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment is no longer active");
        }
        openRound(String.valueOf(enrollment.get("semester_id")), "ADD_DROP");
        jdbc.update(
                "UPDATE " + ENROLLMENT + " SET \"status\" = 'DROPPED', \"droppedAt\" = CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
        jdbc.update(
                "UPDATE " + SECTION + " SET \"enrolledCount\" = CASE WHEN \"enrolledCount\" > 0 THEN \"enrolledCount\" - 1 ELSE 0 END,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :sectionId",
                new MapSqlParameterSource("sectionId", enrollment.get("section_id")));
        completeIdempotency(studentId == null ? enrollmentId : studentId, idempotencyKey, enrollmentId, hash);
    }

    private void persistSlip(String studentId, EnrollmentResponse response) {
        if (response == null) {
            throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "SLIP_PERSIST_FAILED", "Enrollment response missing after enroll");
        }
        byte[] pdf = pdfRenderer.render(studentId, response.semesterId(), response.id(), response.sectionId());
        String digest = sha256(pdf);
        int inserted = jdbc.update(
                "INSERT INTO " + SLIP
                        + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"sha256\", \"byteSize\", \"payload\")"
                        + " SELECT :id, :studentId, :semesterId, enrollment.\"roundId\", :sha, :size, :payload"
                        + " FROM " + ENROLLMENT + " enrollment WHERE enrollment.\"id\" = :enrollmentId"
                        + " AND NOT EXISTS (SELECT 1 FROM " + SLIP + " existing WHERE TRIM(existing.\"sha256\") = :sha"
                        + " AND existing.\"studentId\" = :studentId AND existing.\"semesterId\" = :semesterId)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("studentId", studentId)
                        .addValue("semesterId", response.semesterId())
                        .addValue("sha", digest)
                        .addValue("size", pdf.length)
                        .addValue("payload", pdf)
                        .addValue("enrollmentId", response.id()));
        if (inserted == 0) {
            Long existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + SLIP
                            + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId AND TRIM(\"sha256\") = :sha",
                    new MapSqlParameterSource()
                            .addValue("studentId", studentId)
                            .addValue("semesterId", response.semesterId())
                            .addValue("sha", digest),
                    Long.class);
            if (existing == null || existing == 0) {
                throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "SLIP_PERSIST_FAILED", "Registration slip was not stored");
            }
        }
        jdbc.update(
                "UPDATE " + IDEMPOTENCY + " SET \"slipSha256\" = :sha WHERE \"ownerId\" = :ownerId AND \"enrollmentId\" = :enrollmentId",
                new MapSqlParameterSource()
                        .addValue("sha", digest)
                        .addValue("ownerId", studentId)
                        .addValue("enrollmentId", response.id()));
    }

    private Map<String, Object> claimIdempotency(String ownerId, String key, String hash) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("ownerId", ownerId)
                .addValue("key", key)
                .addValue("hash", hash);
        if (postgres) {
            int inserted = jdbc.update(
                    "INSERT INTO " + IDEMPOTENCY
                            + " (\"id\", \"ownerId\", \"idempotencyKey\", \"requestHash\", \"state\")"
                            + " VALUES (:id, :ownerId, :key, :hash, 'IN_PROGRESS')"
                            + " ON CONFLICT (\"ownerId\", \"idempotencyKey\") DO NOTHING",
                    parameters);
            if (inserted == 1) {
                return null;
            }
        } else {
            try {
                jdbc.update(
                        "INSERT INTO " + IDEMPOTENCY
                                + " (\"id\", \"ownerId\", \"idempotencyKey\", \"requestHash\", \"state\")"
                                + " VALUES (:id, :ownerId, :key, :hash, 'IN_PROGRESS')",
                        parameters);
                return null;
            } catch (DataIntegrityViolationException duplicate) {
                // H2 does not implement PostgreSQL's ON CONFLICT syntax. This
                // fallback is test-only; production PostgreSQL never aborts the
                // surrounding transaction on the duplicate-key path.
            }
        }
        Map<String, Object> existing = jdbc.queryForMap(
                "SELECT \"requestHash\" AS request_hash, \"state\", \"enrollmentId\" AS enrollment_id"
                        + " FROM " + IDEMPOTENCY + " WHERE \"ownerId\" = :ownerId AND \"idempotencyKey\" = :key",
                new MapSqlParameterSource().addValue("ownerId", ownerId).addValue("key", key));
        if (!hash.equals(String.valueOf(existing.get("request_hash")).trim())) {
            throw problem(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "Idempotency key was reused with a different payload");
        }
        if ("IN_PROGRESS".equals(String.valueOf(existing.get("state")))) {
            throw problem(HttpStatus.CONFLICT, "IDEMPOTENCY_IN_PROGRESS", "The same request is already in progress");
        }
        return existing;
    }

    private void completeIdempotency(String ownerId, String key, String enrollmentId, String hash) {
        jdbc.update(
                "UPDATE " + IDEMPOTENCY
                        + " SET \"state\" = 'COMPLETED', \"enrollmentId\" = :enrollmentId, \"requestHash\" = :hash,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"ownerId\" = :ownerId AND \"idempotencyKey\" = :key",
                new MapSqlParameterSource()
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("hash", hash)
                        .addValue("ownerId", ownerId)
                        .addValue("key", key));
    }

    private void requireKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw problem(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required");
        }
    }

    private Map<String, Object> requireStudent(String studentId) {
        try {
            Map<String, Object> student = jdbc.queryForMap(
                    "SELECT \"id\", \"curriculumId\" AS curriculum_id, \"year\", \"status\" FROM " + STUDENT
                            + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", studentId));
            if (!"ACTIVE".equals(String.valueOf(student.get("status")))) {
                throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is not active");
            }
            return student;
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
    }

    private Map<String, Object> lockSection(String sectionId) {
        try {
            return jdbc.queryForMap(
                    "SELECT section.\"id\", section.\"status\", section.\"capacity\", section.\"enrolledCount\" AS enrolled_count,"
                            + " section.\"semesterId\" AS semester_id, section.\"courseId\" AS course_id, course.\"credits\" AS credits"
                            + " FROM " + SECTION + " section JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                            + " WHERE section.\"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", sectionId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found");
        }
    }

    private Map<String, Object> openRound(String semesterId, String preferredKind) {
        return openRound(semesterId, preferredKind, true);
    }

    /** Read paths must not hold exclusive round locks; only enroll/drop serialize on them. */
    private Map<String, Object> openRoundReadOnly(String semesterId, String preferredKind) {
        return openRound(semesterId, preferredKind, false);
    }

    private Map<String, Object> openRound(String semesterId, String preferredKind, boolean forUpdate) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sql = "SELECT \"id\", \"semesterId\" AS semester_id, \"kind\", \"status\", \"windowStart\" AS window_start,"
                + " \"windowEnd\" AS window_end, \"creditLimit\" AS credit_limit FROM " + ROUND
                + " WHERE \"status\" = 'OPEN'";
        if (semesterId != null && !semesterId.isBlank()) {
            sql += " AND \"semesterId\" = :semesterId";
            parameters.addValue("semesterId", semesterId);
        }
        sql += " ORDER BY \"windowStart\"" + (forUpdate ? " FOR UPDATE" : "");
        List<Map<String, Object>> rounds = jdbc.queryForList(sql, parameters);
        Instant now = Instant.now();
        Map<String, Object> preferred = rounds.stream()
                .filter(row -> preferredKind.equals(String.valueOf(row.get("kind"))))
                .filter(row -> inWindow(row, now))
                .findFirst()
                .orElse(null);
        if (preferred != null) {
            return preferred;
        }
        if ("ADD_DROP".equals(preferredKind)) {
            Map<String, Object> registration = rounds.stream()
                    .filter(row -> "REGISTRATION".equals(String.valueOf(row.get("kind"))))
                    .filter(row -> inWindow(row, now))
                    .findFirst()
                    .orElse(null);
            if (registration != null) {
                return registration;
            }
        }
        throw problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "No open registration window");
    }

    private Map<String, Object> round(String roundId) {
        try {
            return jdbc.queryForMap(
                    "SELECT \"id\", \"semesterId\" AS semester_id, \"kind\", \"status\", \"windowStart\" AS window_start,"
                            + " \"windowEnd\" AS window_end, \"creditLimit\" AS credit_limit FROM " + ROUND
                            + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", roundId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration round not found");
        }
    }

    private void assertEligible(Map<String, Object> student, Map<String, Object> round, Instant now) {
        if (!"OPEN".equals(String.valueOf(round.get("status"))) || !inWindow(round, now)) {
            throw problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "Registration window is closed");
        }
        Long cohorts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + COHORT + " WHERE \"roundId\" = :roundId",
                new MapSqlParameterSource("roundId", round.get("id")),
                Long.class);
        if (cohorts == null || cohorts == 0) {
            return;
        }
        Long matched = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + COHORT
                        + " WHERE \"roundId\" = :roundId AND (\"curriculumId\" IS NULL OR \"curriculumId\" = :curriculumId)"
                        + " AND (\"year\" IS NULL OR \"year\" = :year)",
                new MapSqlParameterSource()
                        .addValue("roundId", round.get("id"))
                        .addValue("curriculumId", student.get("curriculum_id"))
                        .addValue("year", student.get("year")),
                Long.class);
        if (matched == null || matched == 0) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "COHORT_INELIGIBLE", "Student is outside this registration cohort");
        }
    }

    private boolean inWindow(Map<String, Object> round, Instant now) {
        Instant start = timestamp((Timestamp) round.get("window_start"));
        Instant end = timestamp((Timestamp) round.get("window_end"));
        return start != null && end != null && !now.isBefore(start) && !now.isAfter(end);
    }

    private List<Map<String, Object>> activeEnrollments(String studentId, String semesterId) {
        return jdbc.queryForList(
                "SELECT enrollment.\"id\", enrollment.\"sectionId\" AS section_id, enrollment.\"courseId\" AS course_id,"
                        + " enrollment.\"creditsSnapshot\" AS credits FROM " + ENROLLMENT + " enrollment"
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId));
    }

    private List<Map<String, Object>> lockActiveEnrollments(String studentId, String semesterId) {
        return jdbc.queryForList(
                "SELECT enrollment.\"id\", enrollment.\"sectionId\" AS section_id, enrollment.\"courseId\" AS course_id,"
                        + " enrollment.\"creditsSnapshot\" AS credits FROM " + ENROLLMENT + " enrollment"
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED') FOR UPDATE",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId));
    }

    private int creditsUsed(String studentId, String semesterId) {
        Integer used = jdbc.queryForObject(
                "SELECT COALESCE(SUM(\"creditsSnapshot\"), 0) FROM " + ENROLLMENT
                        + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId"
                        + " AND \"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId),
                Integer.class);
        return used == null ? 0 : used;
    }

    private void assertPrereqAndCoreq(String studentId, String courseId, String semesterId, List<String> requestSectionIds) {
        List<Map<String, Object>> requirements = jdbc.queryForList(
                "SELECT \"requiredCourseId\" AS required_course_id, \"kind\" FROM " + REQUIREMENT + " WHERE \"courseId\" = :courseId",
                new MapSqlParameterSource("courseId", courseId));
        for (Map<String, Object> requirement : requirements) {
            String required = String.valueOf(requirement.get("required_course_id"));
            String kind = String.valueOf(requirement.get("kind"));
            if ("PREREQ".equals(kind) && !hasCompleted(studentId, required)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "PREREQUISITE_UNMET", "A required prerequisite is missing");
            }
            if ("COREQ".equals(kind) && !hasActiveCourse(studentId, semesterId, required) && !requestHasCourse(requestSectionIds, required)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "COREQUISITE_UNMET", "A required corequisite is missing");
            }
        }
    }

    private boolean hasCompleted(String studentId, String courseId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ENROLLMENT
                        + " WHERE \"studentId\" = :studentId AND \"courseId\" = :courseId"
                        + " AND \"gradeStatus\" IN ('COMPLETED', 'PUBLISHED')",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("courseId", courseId),
                Long.class);
        return count != null && count > 0;
    }

    private boolean hasActiveCourse(String studentId, String semesterId, String courseId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ENROLLMENT
                        + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId AND \"courseId\" = :courseId"
                        + " AND \"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')",
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("semesterId", semesterId)
                        .addValue("courseId", courseId),
                Long.class);
        return count != null && count > 0;
    }

    private boolean requestHasCourse(List<String> sectionIds, String courseId) {
        if (sectionIds.isEmpty()) {
            return false;
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" IN (:ids) AND \"courseId\" = :courseId",
                new MapSqlParameterSource().addValue("ids", sectionIds).addValue("courseId", courseId),
                Long.class);
        return count != null && count > 0;
    }

    private List<TimeRange> schedulesForSection(String sectionId) {
        return schedulesForSections(List.of(sectionId));
    }

    private List<TimeRange> schedulesForSections(List<String> sectionIds) {
        if (sectionIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                "SELECT \"dayOfWeek\" AS day_of_week, \"startTime\" AS start_time, \"endTime\" AS end_time FROM "
                        + SCHEDULE + " WHERE \"sectionId\" IN (:ids)",
                new MapSqlParameterSource("ids", sectionIds),
                (rs, rowNum) -> new TimeRange(rs.getInt("day_of_week"), rs.getString("start_time"), rs.getString("end_time")));
    }

    private boolean overlaps(List<TimeRange> left, List<TimeRange> right) {
        for (TimeRange first : left) {
            for (TimeRange second : right) {
                if (first.day == second.day
                        && first.start.compareTo(second.end) < 0
                        && second.start.compareTo(first.end) < 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Instant timestamp(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] payloadBytes(Object payload) {
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        if (payload instanceof java.sql.Blob blob) {
            try {
                return blob.getBytes(1, (int) blob.length());
            } catch (java.sql.SQLException exception) {
                throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "SLIP_PERSIST_FAILED", "Registration slip could not be read");
            }
        }
        throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "SLIP_PERSIST_FAILED", "Registration slip payload is missing");
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }

    private record TimeRange(int day, String start, String end) {
    }
}
