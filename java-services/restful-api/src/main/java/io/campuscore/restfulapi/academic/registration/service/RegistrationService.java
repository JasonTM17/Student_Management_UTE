package io.campuscore.restfulapi.academic.registration.service;

import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.CatalogSectionResponse;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.CurriculumRelevance;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.EligibilityResponse;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.RoundResponse;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.SectionScheduleView;
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos.SummaryResponse;
import io.campuscore.restfulapi.academic.registration.model.SlipPayload;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.model.SemesterStatuses;
import io.campuscore.restfulapi.academic.dto.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.exception.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import io.campuscore.restfulapi.academic.registration.dto.RegistrationDtos;
import io.campuscore.restfulapi.academic.registration.renderer.RegistrationPdfRenderer;

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
    private static final String CLASSROOM = "academic.\"Classroom\"";
    private static final String LECTURER = "academic.\"Lecturer\"";
    private static final String SEMESTER = "academic.\"Semester\"";
    /** Display names live on the auth profile; same join convention as AcademicEnrollmentReadRepository. */
    private static final String AUTH_USER = "campuscore_auth.\"User\"";
    private static final String CURRICULUM_COURSE = "academic.\"CurriculumCourse\"";
    private static final int STANDARD_CREDIT_LIMIT = CreditLimitApplicationService.STANDARD_LIMIT;
    private static final int MINIMUM_CREDITS = 14;

    private final NamedParameterJdbcTemplate jdbc;
    private final AcademicEnrollmentReadService reads;
    private final RegistrationPdfRenderer pdfRenderer;
    private final CreditLimitApplicationService creditLimitApplications;
    private final TransactionTemplate transactions;
    private final boolean postgres;

    public RegistrationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicEnrollmentReadService reads,
            RegistrationPdfRenderer pdfRenderer,
            CreditLimitApplicationService creditLimitApplications,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.reads = reads;
        this.pdfRenderer = pdfRenderer;
        this.creditLimitApplications = creditLimitApplications;
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
                Math.min(STANDARD_CREDIT_LIMIT, rs.getInt("creditLimit"))));
    }

    @Transactional
    public EligibilityResponse eligibility(String studentId, String semesterId, String roundId) {
        Map<String, Object> student = requireStudent(studentId);
        Map<String, Object> round = roundId == null || roundId.isBlank()
                ? openReadRound(semesterId)
                : round(roundId);
        assertEligible(student, round, Instant.now());
        int used = creditsUsed(studentId, String.valueOf(round.get("semester_id")));
        int limit = creditLimitApplications.effectiveLimit(studentId, round);
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

    /**
     * Query budget contract: catalog() runs a FIXED number of queries —
     * student (1), round (1), cohort gate (1), active enrollments (1), and ONE
     * fan-out fetch for catalog rows + schedules + curriculum relevance that
     * is grouped in memory. The budget does not grow with the section count
     * (no per-row schedule lookups); any new catalog field must stay batched —
     * a join or a single IN (...) query — never a per-row round trip.
     *
     * <p>Upper bound is 5 queries on the happy REGISTRATION path and 6 when the
     * ADD_DROP fallback re-opens the round (openReadRound retries once after
     * the REGISTRATION round is closed). Never let either path grow with the
     * catalog size.
     */
    @Transactional
    public List<CatalogSectionResponse> catalog(String studentId, String semesterId, String roundId) {
        Map<String, Object> student = requireStudent(studentId);
        Map<String, Object> round = roundId == null || roundId.isBlank()
                ? openReadRound(semesterId)
                : round(roundId);
        assertEligible(student, round, Instant.now());
        String effectiveSemester = String.valueOf(round.get("semester_id"));
        List<Map<String, Object>> active = activeEnrollments(studentId, effectiveSemester);
        Set<String> enrolledSections = active.stream()
                .map(row -> String.valueOf(row.get("section_id")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // One fan-out query: every section contributes at least one row
        // (sections without schedules yield a single row with null schedule
        // columns); sections with N schedule rows yield N rows. Grouped into
        // one response per section below, in result order.
        List<CatalogRow> rows = jdbc.query(
                "SELECT section.\"id\", section.\"sectionNumber\", section.\"courseId\", course.\"code\", course.\"name\","
                        + " course.\"credits\", section.\"capacity\", section.\"enrolledCount\", section.\"status\","
                        + " schedule.\"dayOfWeek\" AS schedule_day, schedule.\"startTime\" AS schedule_start,"
                        + " schedule.\"endTime\" AS schedule_end,"
                        + " classroom.\"building\" AS room_building, classroom.\"roomNumber\" AS room_number,"
                        + " lecturer_user.\"firstName\" AS lecturer_first_name, lecturer_user.\"lastName\" AS lecturer_last_name,"
                        + " (SELECT cc.\"isMandatory\" FROM " + CURRICULUM_COURSE + " cc"
                        + "  WHERE cc.\"curriculumId\" = :curriculumId AND cc.\"courseId\" = section.\"courseId\" LIMIT 1)"
                        + "   AS curriculum_mandatory"
                        + " FROM " + SECTION + " section"
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " LEFT JOIN " + SCHEDULE + " schedule ON schedule.\"sectionId\" = section.\"id\""
                        + " LEFT JOIN " + CLASSROOM + " classroom ON classroom.\"id\" = schedule.\"classroomId\""
                        + " LEFT JOIN " + LECTURER + " lecturer ON lecturer.\"id\" = section.\"lecturerId\""
                        + " LEFT JOIN " + AUTH_USER + " lecturer_user ON lecturer_user.\"id\" = lecturer.\"userId\""
                        + " WHERE section.\"semesterId\" = :semesterId"
                        + " AND course.\"isActive\" = TRUE"
                        + " ORDER BY course.\"code\", section.\"id\", schedule.\"dayOfWeek\", schedule.\"startTime\", schedule.\"id\"",
                new MapSqlParameterSource()
                        .addValue("curriculumId", student.get("curriculum_id"))
                        .addValue("semesterId", effectiveSemester),
                (rs, rowNum) -> {
                    TimeRange schedule = rs.getObject("schedule_day") == null ? null : new TimeRange(
                            rs.getInt("schedule_day"),
                            rs.getString("schedule_start"),
                            rs.getString("schedule_end"));
                    String building = rs.getString("room_building");
                    String roomNumber = rs.getString("room_number");
                    String room = building == null || roomNumber == null
                            ? null
                            : (building.trim() + " " + roomNumber.trim()).trim();
                    String lecturer = displayName(rs.getString("lecturer_first_name"), rs.getString("lecturer_last_name"));
                    boolean mandatory = rs.getBoolean("curriculum_mandatory");
                    CurriculumRelevance relevance = rs.wasNull()
                            ? CurriculumRelevance.OUTSIDE
                            : (mandatory ? CurriculumRelevance.MANDATORY : CurriculumRelevance.ELECTIVE);
                    return new CatalogRow(
                            rs.getString("id"),
                            rs.getString("sectionNumber"),
                            rs.getString("courseId"),
                            rs.getString("code"),
                            rs.getString("name"),
                            rs.getInt("credits"),
                            rs.getInt("capacity"),
                            rs.getInt("enrolledCount"),
                            rs.getString("status"),
                            schedule,
                            room,
                            lecturer,
                            relevance);
                });

        // Group the fan-out back to one response per section and compute the
        // schedule-conflict flag entirely in memory from the fetched ranges.
        Map<String, CatalogSectionResponse> bySection = new LinkedHashMap<>();
        for (CatalogRow row : rows) {
            CatalogSectionResponse assembled = bySection.get(row.id());
            List<TimeRange> scheduleRanges = row.schedule() == null
                    ? List.of()
                    : List.of(row.schedule());
            if (assembled == null) {
                bySection.put(row.id(), new CatalogSectionResponse(
                        row.id(),
                        row.sectionNumber(),
                        row.courseId(),
                        row.courseCode(),
                        row.courseName(),
                        row.credits(),
                        row.capacity(),
                        row.enrolledCount(),
                        Math.max(0, row.capacity() - row.enrolledCount()),
                        row.status(),
                        false,
                        enrolledSections.contains(row.id()),
                        row.schedule() == null
                                ? List.of()
                                : List.of(new SectionScheduleView(
                                        row.schedule().day(),
                                        row.schedule().start(),
                                        row.schedule().end(),
                                        row.room(),
                                        row.lecturer())),
                        row.relevance()));
                continue;
            }
            if (row.schedule() == null) {
                continue;
            }
            List<SectionScheduleView> schedules = new ArrayList<>(assembled.schedules());
            schedules.add(new SectionScheduleView(
                    row.schedule().day(),
                    row.schedule().start(),
                    row.schedule().end(),
                    row.room(),
                    row.lecturer()));
            bySection.put(row.id(), new CatalogSectionResponse(
                    assembled.id(),
                    assembled.sectionNumber(),
                    assembled.courseId(),
                    assembled.courseCode(),
                    assembled.courseName(),
                    assembled.credits(),
                    assembled.capacity(),
                    assembled.enrolledCount(),
                    assembled.remainingSeats(),
                    assembled.status(),
                    false,
                    assembled.alreadyEnrolled(),
                    List.copyOf(schedules),
                    assembled.curriculumRelevance()));
        }

        List<TimeRange> busy = new ArrayList<>();
        for (String enrolledSection : enrolledSections) {
            CatalogSectionResponse enrolled = bySection.get(enrolledSection);
            if (enrolled != null) {
                enrolled.schedules().forEach(view -> busy.add(
                        new TimeRange(view.dayOfWeek(), view.startTime(), view.endTime())));
            }
        }
        List<CatalogSectionResponse> catalog = new ArrayList<>(bySection.values());
        for (int index = 0; index < catalog.size(); index++) {
            CatalogSectionResponse current = catalog.get(index);
            boolean conflict = !enrolledSections.contains(current.id())
                    && overlaps(busy, current.schedules().stream()
                            .map(view -> new TimeRange(view.dayOfWeek(), view.startTime(), view.endTime()))
                            .toList());
            if (conflict) {
                catalog.set(index, new CatalogSectionResponse(
                        current.id(),
                        current.sectionNumber(),
                        current.courseId(),
                        current.courseCode(),
                        current.courseName(),
                        current.credits(),
                        current.capacity(),
                        current.enrolledCount(),
                        current.remainingSeats(),
                        current.status(),
                        true,
                        current.alreadyEnrolled(),
                        current.schedules(),
                        current.curriculumRelevance()));
            }
        }
        return catalog;
    }

    @Transactional
    public SummaryResponse summary(String studentId, String semesterId) {
        requireStudent(studentId);
        Map<String, Object> round = openReadRound(semesterId);
        int used = creditsUsed(studentId, String.valueOf(round.get("semester_id")));
        int limit = creditLimitApplications.effectiveLimit(studentId, round);
        List<String> ids = activeEnrollments(studentId, String.valueOf(round.get("semester_id"))).stream()
                .map(row -> String.valueOf(row.get("id")))
                .toList();
        int minCredits = "SUMMER".equalsIgnoreCase(String.valueOf(round.get("semester_type")))
                ? 0
                : MINIMUM_CREDITS;
        return new SummaryResponse(
                String.valueOf(round.get("id")),
                limit,
                used,
                Math.max(0, limit - used),
                ids,
                minCredits,
                minCredits > 0 && used < minCredits);
    }

    public EnrollmentResponse enroll(
            String studentId,
            String sectionId,
            List<String> roles,
            String idempotencyKey) {
        requireKey(idempotencyKey);
        if (studentId == null || studentId.isBlank()) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        String hash = sha256("ENROLL|" + sectionId);
        EnrollmentResponse response = transactions.execute(status -> {
            EnrollmentResponse enrolled =
                    enrollLocked(studentId, sectionId, roles, idempotencyKey, hash);
            if (enrolled == null) {
                throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "ENROLLMENT_FAILED",
                        "Enrollment transaction returned no result");
            }
            persistSlip(studentId, enrolled);
            return enrolled;
        });
        if (response == null) {
            throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "ENROLLMENT_FAILED", "Enrollment transaction returned no result");
        }
        return response;
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey) {
        drop(enrollmentId, studentId, roles, idempotencyKey, null);
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey,
            String actorId) {
        requireKey(idempotencyKey);
        boolean admin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
        if (!admin && (studentId == null || studentId.isBlank())) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        String hash = sha256("DROP|" + enrollmentId);
        // Self-service drops record the student profile id (same convention as
        // the ENROLL event); privileged drops record the caller's user id (same
        // convention as ADMIN_DELETE) so an admin who happens to hold a student
        // profile is not misattributed as the enrollment owner.
        String resolvedActor = admin ? actorId : studentId;
        transactions.executeWithoutResult(status -> dropLocked(enrollmentId, studentId, roles, idempotencyKey, hash,
                resolvedActor));
    }

    @Transactional
    public SlipPayload slip(String studentId, String semesterId) {
        // Lock order Student -> Section -> Round: a slip asserts the
        // student's committed active set, and every path that changes it
        // (enroll, drop, cancel/reopen purge) serializes on this row first.
        // Share-lock it before reading anything — renders stay parallel but
        // a reopen can neither commit past the render's active-set read nor
        // leave a persisted slip missing the reopened course.
        shareLockStudent(studentId);
        MapSqlParameterSource parameters = new MapSqlParameterSource("studentId", studentId);
        String sql = "SELECT \"sha256\", \"payload\" FROM " + SLIP + " WHERE \"studentId\" = :studentId";
        if (semesterId != null && !semesterId.isBlank()) {
            sql += " AND \"semesterId\" = :semesterId";
            parameters.addValue("semesterId", semesterId);
        }
        sql += " ORDER BY \"createdAt\" DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            byte[] payload = payloadBytes(row.get("payload"));
            if (isValidPdf(payload)) {
                return new SlipPayload(String.valueOf(row.get("sha256")).trim(), payload);
            }
        }

        // When no valid pre-persisted slip exists, dynamically generate and store a slip for active enrollments
        String targetSemesterId = semesterId;
        if (targetSemesterId == null || targetSemesterId.isBlank()) {
            // Resolve the semester of the student's latest active enrollment.
            // Ordering raw semester ids lexically can pick a historical semester
            // whose rows are all COMPLETED/DROPPED and produce a false 404.
            List<String> semesters = jdbc.queryForList(
                    "SELECT \"semesterId\" FROM " + ENROLLMENT + " WHERE \"studentId\" = :studentId"
                            + " AND \"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                            + " ORDER BY \"enrolledAt\" DESC LIMIT 1",
                    new MapSqlParameterSource("studentId", studentId), String.class);
            if (!semesters.isEmpty()) {
                targetSemesterId = semesters.get(0);
            }
        }
        if (targetSemesterId != null && !targetSemesterId.isBlank()) {
            List<Map<String, Object>> active = activeEnrollments(studentId, targetSemesterId);
            if (!active.isEmpty()) {
                // A section cancellation deletes persisted slips; a slip
                // rendered from pre-cancel state could commit afterwards and
                // keep listing the cancelled course. Share-lock the section
                // rows so the render serializes with the cancel's UPDATE but
                // not with other renders, then re-read the active set under
                // the lock before persisting.
                for (String sectionId : active.stream()
                        .map(row -> String.valueOf(row.get("section_id"))).distinct().sorted().toList()) {
                    jdbc.queryForList(
                            "SELECT \"id\" FROM " + SECTION + " WHERE \"id\" = :id"
                                    + (postgres ? " FOR SHARE" : " FOR UPDATE"),
                            new MapSqlParameterSource("id", sectionId));
                }
                active = activeEnrollments(studentId, targetSemesterId);
            }
            if (!active.isEmpty()) {
                Map<String, Object> first = active.get(0);
                String enrollmentId = String.valueOf(first.get("id"));
                String sectionId = String.valueOf(first.get("section_id"));

                byte[] pdf = renderSlipPdf(studentId, targetSemesterId, enrollmentId, sectionId, active);
                String digest = sha256(pdf);

                String roundId = first.get("round_id") != null ? String.valueOf(first.get("round_id")) : null;
                if (roundId == null || roundId.isBlank()) {
                    List<String> roundIds = jdbc.queryForList(
                            "SELECT \"id\" FROM " + ROUND + " WHERE \"semesterId\" = :semesterId ORDER BY \"windowStart\" DESC",
                            new MapSqlParameterSource("semesterId", targetSemesterId), String.class);
                    if (!roundIds.isEmpty()) {
                        roundId = roundIds.get(0);
                    } else {
                        List<String> fallbackRounds = jdbc.queryForList(
                                "SELECT \"id\" FROM " + ROUND + " ORDER BY \"windowStart\" DESC",
                                new MapSqlParameterSource(), String.class);
                        if (!fallbackRounds.isEmpty()) {
                            roundId = fallbackRounds.get(0);
                        }
                    }
                }

                if (roundId != null && !roundId.isBlank()) {
                    try {
                        jdbc.update(
                                "INSERT INTO " + SLIP
                                        + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"sha256\", \"byteSize\", \"payload\")"
                                        + " SELECT :id, :studentId, :semesterId, :roundId, :sha, :size, :payload"
                                        + " WHERE NOT EXISTS (SELECT 1 FROM " + SLIP
                                        + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId AND TRIM(\"sha256\") = :sha)",
                                new MapSqlParameterSource()
                                        .addValue("id", UUID.randomUUID().toString())
                                        .addValue("studentId", studentId)
                                        .addValue("semesterId", targetSemesterId)
                                        .addValue("roundId", roundId)
                                        .addValue("sha", digest)
                                        .addValue("size", pdf.length)
                                        .addValue("payload", pdf));
                    } catch (Exception ignored) {
                    }
                }
                return new SlipPayload(digest, pdf);
            }
        }

        throw problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration slip not found");
    }

    private static boolean isValidPdf(byte[] data) {
        if (data == null || data.length < 50) return false;
        String s = new String(data, StandardCharsets.US_ASCII);
        return s.startsWith("%PDF-") && s.contains("startxref") && s.contains("%%EOF");
    }

    /**
     * Renders the slip from live registration data: every course row carries
     * its code, name, section number and credits, plus the student code/name
     * and semester name. Both the enroll-time persist and the on-demand
     * fallback go through here so a stored slip never shows the placeholder
     * row of internal ids the legacy {@code render} overload produced.
     */
    private byte[] renderSlipPdf(String studentId, String semesterId, String enrollmentId, String sectionId,
            List<Map<String, Object>> active) {
        String studentCode = studentId;
        String studentName = null;
        try {
            Map<String, Object> stuInfo = jdbc.queryForMap(
                    "SELECT s.\"studentId\" AS student_code, u.\"firstName\" AS first_name, u.\"lastName\" AS last_name"
                            + " FROM " + STUDENT + " s"
                            + " LEFT JOIN \"campuscore_auth\".\"User\" u ON u.\"id\" = s.\"userId\""
                            + " WHERE s.\"id\" = :studentId",
                    new MapSqlParameterSource("studentId", studentId));
            if (stuInfo.get("student_code") != null) {
                studentCode = String.valueOf(stuInfo.get("student_code"));
            }
            studentName = displayName(
                    (String) stuInfo.get("first_name"),
                    (String) stuInfo.get("last_name"));
        } catch (Exception ignored) {
        }

        String semesterName = semesterId;
        try {
            Map<String, Object> semInfo = jdbc.queryForMap(
                    "SELECT \"name\" FROM " + SEMESTER + " WHERE \"id\" = :semesterId",
                    new MapSqlParameterSource("semesterId", semesterId));
            if (semInfo.get("name") != null) {
                semesterName = String.valueOf(semInfo.get("name"));
            }
        } catch (Exception ignored) {
        }

        List<RegistrationPdfRenderer.CourseItem> courseItems = active.stream().map(row -> new RegistrationPdfRenderer.CourseItem(
                String.valueOf(row.get("course_code")),
                String.valueOf(row.get("course_name")),
                String.valueOf(row.get("section_number")),
                ((Number) row.get("credits")).intValue(),
                String.valueOf(row.get("id")),
                String.valueOf(row.get("section_id"))
        )).toList();

        return pdfRenderer.renderEnriched(
                studentId,
                studentCode,
                studentName,
                semesterId,
                semesterName,
                enrollmentId,
                sectionId,
                courseItems);
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
        Map<String, Object> student = lockStudent(studentId);
        Map<String, Object> section = lockSection(sectionId);
        String semesterId = String.valueOf(section.get("semester_id"));
        String courseId = String.valueOf(section.get("course_id"));
        int credits = ((Number) section.get("credits")).intValue();
        if (!Boolean.TRUE.equals(section.get("course_active"))) {
            throw problem(HttpStatus.CONFLICT, "COURSE_INACTIVE", "Course is not active for registration");
        }
        if (!"OPEN".equals(String.valueOf(section.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "SECTION_CLOSED", "Section is not open for registration");
        }
        // The semester lifecycle gates registration independently of the round
        // rows: a CLOSED semester must refuse enroll even if a round is still
        // flagged OPEN, and reopening the semester restores it unchanged.
        requireOpenSemester(semesterId);
        Map<String, Object> round = openRoundReadOnly(semesterId, "REGISTRATION");
        assertEligible(student, round, Instant.now());
        List<Map<String, Object>> active = lockActiveEnrollments(studentId, semesterId);
        if (active.stream().anyMatch(row -> sectionId.equals(String.valueOf(row.get("section_id"))))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_DUPLICATE", "Student is already enrolled in this section");
        }
        if (active.stream().anyMatch(row -> courseId.equals(String.valueOf(row.get("course_id"))))) {
            throw problem(HttpStatus.CONFLICT, "DUPLICATE_COURSE", "Student already has this course this term");
        }
        // Cancelling a section preserves its enrollment rows as history, and
        // the per-course uniqueness still counts them while their status stays
        // active — the duplicate check above skips cancelled sections, so a
        // same-course replacement would collide on the unique index at insert.
        // Retire the preserved row as an automatic drop under this lock order;
        // a later reopen does not resurrect it.
        retireCancelledSectionEnrollment(studentId, semesterId, courseId, hash);
        int used = active.stream().mapToInt(row -> ((Number) row.get("credits")).intValue()).sum();
        int limit = creditLimitApplications.effectiveLimit(studentId, round);
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
            String hash,
            String actorId) {
        Map<String, Object> replay = claimIdempotency(studentId == null ? enrollmentId : studentId, idempotencyKey, hash);
        if (replay != null) {
            return;
        }
        // Resolve the immutable owner without locking Enrollment. All mutations
        // of one student's registration serialize on Student before taking any
        // Enrollment lock, including administrative drops.
        String targetStudentId;
        try {
            targetStudentId = jdbc.queryForObject(
                    "SELECT \"studentId\" FROM " + ENROLLMENT + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", enrollmentId), String.class);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
        lockStudent(targetStudentId);
        Map<String, Object> enrollment;
        try {
            enrollment = jdbc.queryForMap(
                    "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id, \"semesterId\" AS semester_id,"
                            + " \"status\" FROM " + ENROLLMENT + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", enrollmentId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
        if (!targetStudentId.equals(String.valueOf(enrollment.get("student_id")))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment changed during the request");
        }
        boolean admin = roles != null && (roles.contains("ADMIN") || roles.contains("SUPER_ADMIN"));
        if (!admin && !java.util.Objects.equals(studentId, String.valueOf(enrollment.get("student_id")))) {
            throw problem(HttpStatus.FORBIDDEN, "ENROLLMENT_FORBIDDEN", "Enrollment does not belong to the current student");
        }
        if (!List.of("ENROLLED", "PENDING").contains(String.valueOf(enrollment.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_ACTIVE", "Enrollment is no longer active");
        }
        // Student serializes same-student operations. Across students, Section
        // must precede Round on both enroll and drop to avoid a second lock cycle.
        Map<String, Object> section = lockSection(String.valueOf(enrollment.get("section_id")));
        String droppedCourseId = String.valueOf(section.get("course_id"));
        String semesterId = String.valueOf(enrollment.get("semester_id"));
        // A cancelled section is already out of every active calculation: its
        // enrollment cannot satisfy a corequisite, so keeping it would protect
        // nothing while trapping the student. Its drop therefore bypasses the
        // corequisite guard and the add/drop window entirely.
        if (!"CANCELLED".equals(String.valueOf(section.get("status")))) {
            assertNoActiveCorequisiteDependents(targetStudentId, droppedCourseId, semesterId, enrollmentId);
            openRoundReadOnly(semesterId, "ADD_DROP");
        }
        jdbc.update(
                "UPDATE " + ENROLLMENT + " SET \"status\" = 'DROPPED', \"droppedAt\" = CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
        jdbc.update(
                "UPDATE " + SECTION + " SET \"enrolledCount\" = CASE WHEN \"enrolledCount\" > 0 THEN \"enrolledCount\" - 1 ELSE 0 END,"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :sectionId",
                new MapSqlParameterSource("sectionId", enrollment.get("section_id")));
        // Persisted slips are snapshots of the active registration set. A drop
        // must invalidate them in the same transaction, otherwise the next slip
        // download keeps claiming the dropped course as a valid enrollment.
        jdbc.update(
                "DELETE FROM " + SLIP + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId",
                new MapSqlParameterSource()
                        .addValue("studentId", targetStudentId)
                        .addValue("semesterId", String.valueOf(enrollment.get("semester_id"))));
        jdbc.update(
                "INSERT INTO " + EVENT
                        + " (\"id\", \"enrollmentId\", \"studentId\", \"sectionId\", \"action\", \"actorId\", \"requestHash\")"
                        + " VALUES (:id, :enrollmentId, :studentId, :sectionId, 'DROP', :actorId, :hash)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("studentId", targetStudentId)
                        .addValue("sectionId", enrollment.get("section_id"))
                        .addValue("actorId", actorId == null || actorId.isBlank() ? "system" : actorId)
                        .addValue("hash", hash));
        completeIdempotency(studentId == null ? enrollmentId : studentId, idempotencyKey, enrollmentId, hash);
    }

    /**
     * Cancelling a section preserves its enrollment rows as history, and the
     * partial unique index on (student, course, semester) still counts them
     * while their status stays active — the in-Java duplicate check skips
     * cancelled sections, so inserting a replacement enroll for the same
     * course would collide at the database boundary. Retire the preserved row
     * as an automatic drop under the same student-first lock order the manual
     * drop uses; a later section reopen does not resurrect it.
     */
    private void retireCancelledSectionEnrollment(
            String studentId, String semesterId, String courseId, String hash) {
        List<Map<String, Object>> preserved = jdbc.queryForList(
                "SELECT enrollment.\"id\", enrollment.\"sectionId\" AS section_id FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " WHERE enrollment.\"studentId\" = :studentId"
                        + " AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"courseId\" = :courseId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" = 'CANCELLED'"
                        // Same join-shaping as lockActiveEnrollments: only the
                        // Enrollment row is meant to lock.
                        + (postgres ? " FOR UPDATE OF enrollment" : " FOR UPDATE"),
                new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("semesterId", semesterId)
                        .addValue("courseId", courseId));
        for (Map<String, Object> row : preserved) {
            String enrollmentId = String.valueOf(row.get("id"));
            String oldSectionId = String.valueOf(row.get("section_id"));
            jdbc.update(
                    "UPDATE " + ENROLLMENT + " SET \"status\" = 'DROPPED', \"droppedAt\" = CURRENT_TIMESTAMP,"
                            + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", enrollmentId));
            jdbc.update(
                    "UPDATE " + SECTION + " SET \"enrolledCount\" = CASE WHEN \"enrolledCount\" > 0 THEN \"enrolledCount\" - 1 ELSE 0 END,"
                            + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :sectionId",
                    new MapSqlParameterSource("sectionId", oldSectionId));
            // The action name carries the explanation: the seat was dropped
            // because its section was cancelled and replaced by a new section.
            jdbc.update(
                    "INSERT INTO " + EVENT
                            + " (\"id\", \"enrollmentId\", \"studentId\", \"sectionId\", \"action\", \"actorId\", \"requestHash\")"
                            + " VALUES (:id, :enrollmentId, :studentId, :sectionId, 'REPLACED', :actorId, :hash)",
                    new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID().toString())
                            .addValue("enrollmentId", enrollmentId)
                            .addValue("studentId", studentId)
                            .addValue("sectionId", oldSectionId)
                            .addValue("actorId", studentId)
                            .addValue("hash", hash));
            // A persisted slip asserting the old seat is stale the moment the
            // preserved row drops; the enroll-time persist re-issues it.
            jdbc.update(
                    "DELETE FROM " + SLIP + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId",
                    new MapSqlParameterSource()
                            .addValue("studentId", studentId)
                            .addValue("semesterId", semesterId));
        }
    }

    private void persistSlip(String studentId, EnrollmentResponse response) {
        if (response == null) {
            throw problem(HttpStatus.INTERNAL_SERVER_ERROR, "SLIP_PERSIST_FAILED", "Enrollment response missing after enroll");
        }
        byte[] pdf = renderSlipPdf(
                studentId,
                response.semesterId(),
                response.id(),
                response.sectionId(),
                activeEnrollments(studentId, response.semesterId()));
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

    private Map<String, Object> lockStudent(String studentId) {
        try {
            Map<String, Object> student = jdbc.queryForMap(
                    "SELECT \"id\", \"curriculumId\" AS curriculum_id, \"year\", \"status\" FROM " + STUDENT
                            + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", studentId));
            if (!"ACTIVE".equals(String.valueOf(student.get("status")))) {
                throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is not active");
            }
            return student;
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
    }

    /**
     * The read-side half of the student row lock: shares with other renders
     * but conflicts with the FOR UPDATE every mutating path takes first, so
     * a slip never serves or persists against a set a committed write has
     * already changed.
     */
    private void shareLockStudent(String studentId) {
        jdbc.queryForList(
                "SELECT \"id\" FROM " + STUDENT + " WHERE \"id\" = :id"
                        + (postgres ? " FOR SHARE" : " FOR UPDATE"),
                new MapSqlParameterSource("id", studentId));
    }

    private Map<String, Object> lockSection(String sectionId) {
        try {
            return jdbc.queryForMap(
                    "SELECT section.\"id\", section.\"status\", section.\"capacity\", section.\"enrolledCount\" AS enrolled_count,"
                            + " section.\"semesterId\" AS semester_id, section.\"courseId\" AS course_id, course.\"credits\" AS credits,"
                            + " course.\"isActive\" AS course_active"
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

    private void requireOpenSemester(String semesterId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SEMESTER + " WHERE \"id\" = :id AND \"status\" IN (:openStatuses)",
                new MapSqlParameterSource().addValue("id", semesterId).addValue("openStatuses", SemesterStatuses.OPEN),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.CONFLICT, "SEMESTER_CLOSED", "Semester is closed for registration");
        }
    }

    /** Read paths must not hold exclusive round locks; only enroll/drop serialize on them. */
    private Map<String, Object> openRoundReadOnly(String semesterId, String preferredKind) {
        return openRound(semesterId, preferredKind, false);
    }

    /**
     * Reads the active round for catalog, eligibility, and summary screens.
     * Catalog and summary remain available during an open add/drop window after
     * the initial registration window has closed; mutation paths keep their
     * stricter phase-specific locks below.
     */
    private Map<String, Object> openReadRound(String semesterId) {
        try {
            return openRoundReadOnly(semesterId, "REGISTRATION");
        } catch (DomainException exception) {
            if (!"WINDOW_CLOSED".equals(exception.code())) {
                throw exception;
            }
            return openRoundReadOnly(semesterId, "ADD_DROP");
        }
    }

    private Map<String, Object> openRound(String semesterId, String preferredKind, boolean forUpdate) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String sql = "SELECT registration_round.\"id\", registration_round.\"semesterId\" AS semester_id,"
                + " registration_round.\"kind\", registration_round.\"status\","
                + " registration_round.\"windowStart\" AS window_start,"
                + " registration_round.\"windowEnd\" AS window_end,"
                + " registration_round.\"creditLimit\" AS credit_limit,"
                + " (SELECT semester.\"type\" FROM " + SEMESTER
                + " semester WHERE semester.\"id\" = registration_round.\"semesterId\") AS semester_type"
                + " FROM " + ROUND + " registration_round"
                + " WHERE registration_round.\"status\" = 'OPEN'";
        if (semesterId != null && !semesterId.isBlank()) {
            sql += " AND registration_round.\"semesterId\" = :semesterId";
            parameters.addValue("semesterId", semesterId);
        }
        sql += " ORDER BY registration_round.\"windowStart\"" + (forUpdate ? " FOR UPDATE" : "");
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
        // The two round kinds are interchangeable for reads, and each write
        // path must keep working while the other window is the only one open:
        // drops fall back to a live REGISTRATION round, and enrollments fall
        // back to a live ADD_DROP round (its seeded name is literally
        // "supplementary registration + drop" — adds are its purpose, so
        // blocking them between the registration deadline and the add/drop
        // deadline left students unable to add courses for days).
        String fallbackKind = "ADD_DROP".equals(preferredKind) ? "REGISTRATION" : "ADD_DROP";
        Map<String, Object> fallback = rounds.stream()
                .filter(row -> fallbackKind.equals(String.valueOf(row.get("kind"))))
                .filter(row -> inWindow(row, now))
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            return fallback;
        }
        throw problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "No open registration window");
    }

    private Map<String, Object> round(String roundId) {
        try {
            return jdbc.queryForMap(
                    "SELECT registration_round.\"id\", registration_round.\"semesterId\" AS semester_id,"
                            + " registration_round.\"kind\", registration_round.\"status\","
                            + " registration_round.\"windowStart\" AS window_start,"
                            + " registration_round.\"windowEnd\" AS window_end,"
                            + " registration_round.\"creditLimit\" AS credit_limit,"
                            + " (SELECT semester.\"type\" FROM " + SEMESTER
                            + " semester WHERE semester.\"id\" = registration_round.\"semesterId\") AS semester_type"
                            + " FROM " + ROUND + " registration_round"
                            + " WHERE registration_round.\"id\" = :id",
                    new MapSqlParameterSource("id", roundId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ROUND_NOT_FOUND", "Registration round not found");
        }
    }

    private void assertEligible(Map<String, Object> student, Map<String, Object> round, Instant now) {
        if (!"OPEN".equals(String.valueOf(round.get("status"))) || !inWindow(round, now)) {
            throw problem(HttpStatus.CONFLICT, "WINDOW_CLOSED", "Registration window is closed");
        }
        requireOpenSemester(String.valueOf(round.get("semester_id")));
        // One query answers both "does this round restrict cohorts" and "does
        // the student match one": counting the two populations in a single
        // round trip keeps the catalog query budget contract at a constant.
        Map<String, Object> counts = jdbc.queryForMap(
                "SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN (\"curriculumId\" IS NULL OR \"curriculumId\" = :curriculumId)"
                        + " AND (\"year\" IS NULL OR \"year\" = :year) THEN 1 ELSE 0 END) AS matched"
                        + " FROM " + COHORT + " WHERE \"roundId\" = :roundId",
                new MapSqlParameterSource()
                        .addValue("roundId", round.get("id"))
                        .addValue("curriculumId", student.get("curriculum_id"))
                        .addValue("year", student.get("year")));
        long total = counts.get("total") == null ? 0 : ((Number) counts.get("total")).longValue();
        if (total == 0) {
            return;
        }
        long matched = counts.get("matched") == null ? 0 : ((Number) counts.get("matched")).longValue();
        if (matched == 0) {
            throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "COHORT_INELIGIBLE", "Student is outside this registration cohort");
        }
    }

    private boolean inWindow(Map<String, Object> round, Instant now) {
        Instant start = timestamp((Timestamp) round.get("window_start"));
        Instant end = timestamp((Timestamp) round.get("window_end"));
        return start != null && end != null && !now.isBefore(start) && !now.isAfter(end);
    }

    private List<Map<String, Object>> activeEnrollments(String studentId, String semesterId) {
        // Credits come from the CURRENT section -> course definition, not the
        // enrollment snapshot: a snapshot raised after registration would
        // under-count the 30-credit cap on both the read and write paths.
        return jdbc.queryForList(
                "SELECT enrollment.\"id\", enrollment.\"sectionId\" AS section_id, enrollment.\"courseId\" AS course_id,"
                        + " enrollment.\"roundId\" AS round_id, course.\"credits\" AS credits,"
                        + " course.\"code\" AS course_code, course.\"name\" AS course_name, section.\"sectionNumber\" AS section_number"
                        + " FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" <> 'CANCELLED'"
                        + " ORDER BY course.\"code\", section.\"sectionNumber\"",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId));
    }

    private List<Map<String, Object>> lockActiveEnrollments(String studentId, String semesterId) {
        return jdbc.queryForList(
                "SELECT enrollment.\"id\", enrollment.\"sectionId\" AS section_id, enrollment.\"courseId\" AS course_id,"
                        + " course.\"credits\" AS credits FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" <> 'CANCELLED'"
                        // PostgreSQL otherwise locks the joined Section/Course rows too,
                        // inverting Section -> Round for a different student's drop.
                        + (postgres ? " FOR UPDATE OF enrollment" : " FOR UPDATE"),
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId));
    }

    /**
     * Quota counts the CURRENT credits of each enrolled section (section →
     * course), not the snapshot taken at enroll time: when a course's credits
     * are raised after a student registered, the stale snapshot under-counts
     * the 30-credit cap and lets the student register past the limit.
     */
    private int creditsUsed(String studentId, String semesterId) {
        Integer used = jdbc.queryForObject(
                "SELECT COALESCE(SUM(course.\"credits\"), 0) FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" <> 'CANCELLED'",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("semesterId", semesterId),
                Integer.class);
        return used == null ? 0 : used;
    }

    private void assertPrereqAndCoreq(String studentId, String courseId, String semesterId, List<String> requestSectionIds) {
        List<Map<String, Object>> requirements = jdbc.queryForList(
                "SELECT \"requiredCourseId\" AS required_course_id, \"kind\", \"minLetterGrade\" AS min_letter_grade"
                        + " FROM " + REQUIREMENT + " WHERE \"courseId\" = :courseId",
                new MapSqlParameterSource("courseId", courseId));
        for (Map<String, Object> requirement : requirements) {
            String required = String.valueOf(requirement.get("required_course_id"));
            String kind = String.valueOf(requirement.get("kind"));
            String minLetter = requirement.get("min_letter_grade") == null
                    ? null : String.valueOf(requirement.get("min_letter_grade"));
            if ("PREREQ".equals(kind) && !hasCompleted(studentId, required, minLetter)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "PREREQUISITE_UNMET", "A required prerequisite is missing");
            }
            if ("COREQ".equals(kind) && !hasActiveCourse(studentId, semesterId, required) && !requestHasCourse(requestSectionIds, required)) {
                throw problem(HttpStatus.UNPROCESSABLE_ENTITY, "COREQUISITE_UNMET", "A required corequisite is missing");
            }
        }
    }

    private void assertNoActiveCorequisiteDependents(
            String studentId,
            String droppedCourseId,
            String semesterId,
            String droppedEnrollmentId) {
        List<String> otherActiveCourseIds = activeEnrollments(studentId, semesterId).stream()
                .filter(row -> !droppedEnrollmentId.equals(String.valueOf(row.get("id"))))
                .map(row -> String.valueOf(row.get("course_id")))
                .distinct()
                .toList();
        if (otherActiveCourseIds.isEmpty()) {
            return;
        }
        List<String> dependentCourses = jdbc.queryForList(
                "SELECT \"courseId\" FROM " + REQUIREMENT
                        + " WHERE \"courseId\" IN (:courseIds) AND \"requiredCourseId\" = :requiredId AND \"kind\" = 'COREQ'",
                new MapSqlParameterSource()
                        .addValue("courseIds", otherActiveCourseIds)
                        .addValue("requiredId", droppedCourseId),
                String.class);
        if (!dependentCourses.isEmpty()) {
            throw problem(HttpStatus.CONFLICT, "COREQUISITE_UNMET",
                    "Cannot drop course because it is a corequisite for another active course this semester");
        }
    }

    /** Letter ordering used for prerequisite minimum-grade enforcement. */
    private static final java.util.Map<String, Integer> LETTER_RANK = java.util.Map.of(
            "F", 0, "D", 1, "D+", 2, "C", 3, "C+", 4, "B", 5, "B+", 6, "A", 7, "A+", 8);

    private boolean hasCompleted(String studentId, String courseId, String minLetter) {
        // Same predicate as the transcript read: a COMPLETED enrollment whose
        // grade is published (or under appeal) is completed history, and stays
        // completed even if its section was later cancelled.
        List<String> letters = jdbc.queryForList(
                "SELECT enrollment.\"letterGrade\" FROM " + ENROLLMENT + " enrollment"
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"courseId\" = :courseId"
                        + " AND enrollment.\"status\" = 'COMPLETED'"
                        + " AND enrollment.\"gradeStatus\" IN ('PUBLISHED', 'APPEALED')"
                        // A passing letter is required: a graded row without a
                        // letter (a producer bug) must never silently unlock a
                        // prerequisite, matching the knowledge-base copy.
                        + " AND enrollment.\"letterGrade\" IS NOT NULL"
                        + " AND enrollment.\"letterGrade\" NOT IN ('F', 'W')",
                new MapSqlParameterSource().addValue("studentId", studentId).addValue("courseId", courseId),
                String.class);
        if (letters.isEmpty()) {
            return false;
        }
        // minLetterGrade was a dead column: the gate accepted any non-F/W pass.
        // When the requirement declares a floor (e.g. 'B'), the student's best
        // achieved letter must rank at or above it; an unknown floor value is
        // ignored rather than silently tightening or loosening the rule.
        int minRank = minLetter == null ? Integer.MIN_VALUE
                : LETTER_RANK.getOrDefault(minLetter.trim().toUpperCase(java.util.Locale.ROOT), Integer.MIN_VALUE);
        return letters.stream()
                .map(letter -> LETTER_RANK.getOrDefault(letter, Integer.MIN_VALUE))
                .anyMatch(rank -> rank >= minRank);
    }

    private boolean hasActiveCourse(String studentId, String semesterId, String courseId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ENROLLMENT + " enrollment"
                        + " JOIN " + SECTION + " section ON section.\"id\" = enrollment.\"sectionId\""
                        + " WHERE enrollment.\"studentId\" = :studentId AND enrollment.\"semesterId\" = :semesterId"
                        + " AND enrollment.\"courseId\" = :courseId"
                        + " AND enrollment.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND section.\"status\" <> 'CANCELLED'",
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

    /** One fan-out row of the catalog query: section columns + optional schedule + relevance. */
    private record CatalogRow(
            String id,
            String sectionNumber,
            String courseId,
            String courseCode,
            String courseName,
            int credits,
            int capacity,
            int enrolledCount,
            String status,
            TimeRange schedule,
            String room,
            String lecturer,
            CurriculumRelevance relevance) {
    }

    private static String displayName(String firstName, String lastName) {
        // Vietnamese order: lastName carries the surname + title, firstName the
        // given name, so "TS. Phạm Thị" + "Ngọc Lan" renders "TS. Phạm Thị Ngọc Lan".
        String name = ((lastName == null ? "" : lastName.trim()) + " " + (firstName == null ? "" : firstName.trim())).trim();
        return name.isEmpty() ? null : name;
    }
}
