package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.academic.dto.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.dto.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.common.util.LikePattern;
import io.campuscore.restfulapi.engagement.service.GradePublishNotifier;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional course mutations owned by the single Java API. */
@Service
@Profile("persistence")
public class AcademicMutationService {

    private static final String STUDENT = "\"academic\".\"Student\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String USER = "\"campuscore_auth\".\"User\"";
    private static final String GRADE_ITEM = "\"academic\".\"GradeItem\"";
    private static final String STUDENT_GRADE = "\"academic\".\"StudentGrade\"";
    private static final String ENROLLMENT_EVENT = "\"academic\".\"EnrollmentEvent\"";
    /**
     * Statuses that may still receive or publish grades. Without the guard a
     * crafted API call could grade — and resurrect as COMPLETED — an
     * enrollment the student already dropped; the UI never shows such rows,
     * so the server has to be the boundary.
     */
    private static final List<String> GRADEABLE_STATUSES = List.of("ENROLLED", "CONFIRMED", "COMPLETED");

    /** Hard bound on the admin enrollment CSV export (rows). */
    private static final int EXPORT_LIMIT = 50_000;

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AcademicMutationService.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final AcademicEnrollmentReadService reads;
    private final RegistrationService registration;
    private final AdminAuditRecorder audit;
    private final GradePublishNotifier gradeNotifier;
    private final boolean postgres;

    /** Compatibility constructor for tests that only exercise limit/publish math. */
    public AcademicMutationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicEnrollmentReadService reads,
            RegistrationService registration,
            AdminAuditRecorder audit) {
        this(jdbc, reads, registration, audit, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AcademicMutationService(
            NamedParameterJdbcTemplate jdbc,
            AcademicEnrollmentReadService reads,
            RegistrationService registration,
            AdminAuditRecorder audit,
            GradePublishNotifier gradeNotifier) {
        this.jdbc = jdbc;
        this.reads = reads;
        this.registration = registration;
        this.audit = audit;
        this.gradeNotifier = gradeNotifier;
        this.postgres = databaseIsPostgres(jdbc);
    }

    private static boolean databaseIsPostgres(NamedParameterJdbcTemplate jdbc) {
        try {
            org.springframework.jdbc.core.JdbcTemplate template = jdbc.getJdbcTemplate();
            if (template == null) return false;
            String name = template.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                    connection -> connection.getMetaData().getDatabaseProductName());
            return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (org.springframework.dao.DataAccessException ignored) {
            return false;
        }
    }

    public EnrollmentResponse enroll(String studentId, String sectionId, List<String> roles, String idempotencyKey) {
        return registration.enroll(studentId, sectionId, roles, idempotencyKey);
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey) {
        drop(enrollmentId, studentId, roles, idempotencyKey, null);
    }

    public void drop(String enrollmentId, String studentId, List<String> roles, String idempotencyKey,
            String actorId) {
        registration.drop(enrollmentId, studentId, roles, idempotencyKey, actorId);
    }

    /** Compatibility entry point; hard deletes without a known actor record no audit trail. */
    @Transactional
    public void deleteEnrollment(String enrollmentId) {
        deleteEnrollment(enrollmentId, null);
    }

    /**
     * Admin hard delete (audit S4): a hard delete also erases the student's
     * grade history, so it now leaves both an EnrollmentEvent row (action
     * ADMIN_DELETE, in the same table the enroll/drop lifecycle uses) and an
     * AdminAudit row with the before-state, joining the caller's transaction
     * so a rolled-back delete cannot claim to have happened.
     */
    @Transactional
    public void deleteEnrollment(String enrollmentId, String actorId) {
        Map<String, Object> enrollment = enrollment(enrollmentId);
        String status = String.valueOf(enrollment.get("status"));
        jdbc.update(
                "INSERT INTO " + ENROLLMENT_EVENT
                        + " (\"id\", \"enrollmentId\", \"studentId\", \"sectionId\", \"action\", \"actorId\")"
                        + " VALUES (:id, :enrollmentId, :studentId, :sectionId, 'ADMIN_DELETE', :actorId)",
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("enrollmentId", enrollmentId)
                        .addValue("studentId", enrollment.get("student_id"))
                        .addValue("sectionId", enrollment.get("section_id"))
                        .addValue("actorId", actorId == null || actorId.isBlank() ? "system" : actorId));
        jdbc.update(
                "DELETE FROM " + ENROLLMENT + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", enrollmentId));
        // A persisted slip asserts the registration is still valid; a hard
        // delete must retire it exactly like a student drop does.
        jdbc.update(
                "DELETE FROM \"academic\".\"RegistrationSlip\""
                        + " WHERE \"studentId\" = :studentId AND \"semesterId\" = :semesterId",
                new MapSqlParameterSource()
                        .addValue("studentId", enrollment.get("student_id"))
                        .addValue("semesterId", enrollment.get("semester_id")));
        if (List.of("ENROLLED", "PENDING", "CONFIRMED").contains(status)) {
            jdbc.update(
                    "UPDATE " + SECTION + " SET \"enrolledCount\" = GREATEST(0, \"enrolledCount\" - 1),"
                            + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :sectionId",
                    new MapSqlParameterSource("sectionId", enrollment.get("section_id")));
        }
        if (actorId != null && !actorId.isBlank()) {
            audit.recordDeletion(
                    actorId,
                    null,
                    "ENROLLMENT",
                    enrollmentId,
                    Map.of(
                            "studentId", String.valueOf(enrollment.get("student_id")),
                            "sectionId", String.valueOf(enrollment.get("section_id")),
                            "status", status,
                            "gradeStatus", String.valueOf(enrollment.get("grade_status"))));
        }
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId) {
        return exportEnrollments(status, semesterId, studentId, courseId, null);
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId,
            String sectionId) {
        return exportEnrollments(status, semesterId, studentId, courseId, sectionId, null);
    }

    @Transactional(readOnly = true)
    public String exportEnrollments(
            String status,
            String semesterId,
            String studentId,
            String courseId,
            String sectionId,
            String search) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        List<String> filters = new ArrayList<>();
        addFilter(filters, parameters, "status", status, "e.\"status\" = :status");
        addFilter(filters, parameters, "semesterId", semesterId, "e.\"semesterId\" = :semesterId");
        addFilter(filters, parameters, "studentId", studentId, "e.\"studentId\" = :studentId");
        addFilter(filters, parameters, "courseId", courseId, "section.\"courseId\" = :courseId");
        addFilter(filters, parameters, "sectionId", sectionId, "e.\"sectionId\" = :sectionId");
        List<String> tokens = LikePattern.tokens(search);
        if (!tokens.isEmpty()) {
            parameters.addValue("foldFrom", LikePattern.FOLD_FROM);
            parameters.addValue("foldTo", LikePattern.FOLD_TO);
            for (int i = 0; i < tokens.size(); i++) {
                String paramName = "searchToken" + i;
                parameters.addValue(paramName, tokens.get(i));
                filters.add("(TRANSLATE(LOWER(student.\"studentId\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(student_user.\"firstName\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(student_user.\"lastName\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(student_user.\"email\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(section.\"sectionNumber\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(course.\"code\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\'"
                        + " OR TRANSLATE(LOWER(course.\"name\"), :foldFrom, :foldTo) LIKE :" + paramName + " ESCAPE '\\')");
            }
        }
        String where = filters.isEmpty() ? "" : " WHERE " + String.join(" AND ", filters);
        // Unfiltered exports used to load the whole enrollment table into
        // memory. A CSV past this bound would be unusable anyway; the caller
        // narrows filters instead of streaming the entire history.
        parameters.addValue("exportLimit", EXPORT_LIMIT);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT e.\"id\", student.\"studentId\" AS student_number,"
                        + " student_user.\"email\", student_user.\"firstName\", student_user.\"lastName\","
                        + " course.\"code\" AS course_code, course.\"name\" AS course_name,"
                        + " section.\"sectionNumber\", semester.\"name\" AS semester_name,"
                        + " e.\"status\", e.\"enrolledAt\", e.\"finalGrade\", e.\"letterGrade\""
                        + " FROM " + ENROLLMENT + " e"
                        + " JOIN " + STUDENT + " student ON student.\"id\" = e.\"studentId\""
                        + " JOIN " + USER + " student_user ON student_user.\"id\" = student.\"userId\""
                        + " JOIN " + SECTION + " section ON section.\"id\" = e.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                        + " JOIN " + SEMESTER + " semester ON semester.\"id\" = e.\"semesterId\""
                        + where + " ORDER BY e.\"enrolledAt\" DESC, e.\"id\" DESC LIMIT :exportLimit",
                parameters);
        StringBuilder csv = new StringBuilder(
                "id,studentNumber,email,studentName,courseCode,courseName,section,semester,status,enrolledAt,finalGrade,letterGrade\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(csv(row.get("id"))).append(',')
                    .append(csv(row.get("student_number"))).append(',')
                    .append(csv(row.get("email"))).append(',')
                    .append(csv((String.valueOf(row.get("lastName")) + " "
                            + String.valueOf(row.get("firstName"))).trim())).append(',')
                    .append(csv(row.get("course_code"))).append(',')
                    .append(csv(row.get("course_name"))).append(',')
                    .append(csv(row.get("sectionNumber"))).append(',')
                    .append(csv(row.get("semester_name"))).append(',')
                    .append(csv(row.get("status"))).append(',')
                    .append(csv(row.get("enrolledAt"))).append(',')
                    .append(csv(row.get("finalGrade"))).append(',')
                    .append(csv(row.get("letterGrade"))).append("\r\n");
        }
        return csv.toString();
    }

    /** Compatibility entry point; grade saves without a known actor record no audit trail. */
    @Transactional
    public void updateGrades(String sectionId, String lecturerId, boolean admin, List<GradeUpdate> grades) {
        updateGrades(sectionId, lecturerId, admin, null, grades);
    }

    /**
     * Saves draft grades. When an ADMIN (not the owning lecturer) performs the
     * save, an AdminAudit row records the actor and section (audit S3-quick);
     * the permission model itself is unchanged.
     */
    @Transactional
    public void updateGrades(
            String sectionId, String lecturerId, boolean admin, String actorId, List<GradeUpdate> grades) {
        requireSection(sectionId);
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        if (grades == null || grades.isEmpty()) {
            return;
        }
        // Official HCMUTE terminology for the two 50% components; the
        // canonical ids are stable so existing grade rows keep their item.
        String processItemId = canonicalGradeItem(sectionId, "PROCESS", "Điểm quá trình (ĐQT - 50%)");
        String finalItemId = canonicalGradeItem(sectionId, "FINAL", "Điểm kết thúc học phần (ĐKTHP - 50%)");

        // Pre-fetch and lock all targeted enrollments in a single batch query.
        // A replayed or double-submitted row must merge last-write-wins like the
        // per-row save did, never trip the (enrollmentId, gradeItemId) unique index.
        Map<String, GradeUpdate> mergedGrades = new LinkedHashMap<>();
        for (GradeUpdate grade : grades) {
            mergedGrades.put(grade.enrollmentId(), grade);
        }
        List<GradeUpdate> effectiveGrades = List.copyOf(mergedGrades.values());
        List<String> enrollmentIds = List.copyOf(mergedGrades.keySet());
        List<Map<String, Object>> enrollmentRows = jdbc.queryForList(
                "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id, \"status\","
                        + " \"gradeStatus\" AS grade_status"
                        + " FROM " + ENROLLMENT + " WHERE \"id\" IN (:ids) FOR UPDATE",
                new MapSqlParameterSource("ids", enrollmentIds));

        Map<String, Map<String, Object>> enrollmentMap = new HashMap<>();
        for (Map<String, Object> row : enrollmentRows) {
            enrollmentMap.put(String.valueOf(row.get("id")), row);
        }

        List<MapSqlParameterSource> componentBatch = new ArrayList<>(effectiveGrades.size() * 2);
        List<MapSqlParameterSource> enrollmentBatch = new ArrayList<>(effectiveGrades.size());

        for (GradeUpdate grade : effectiveGrades) {
            Map<String, Object> enrollment = enrollmentMap.get(grade.enrollmentId());
            if (enrollment == null) {
                throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
            }
            if (!sectionId.equals(enrollment.get("section_id"))) {
                throw problem(HttpStatus.BAD_REQUEST, "GRADE_SECTION_MISMATCH", "Grade enrollment is outside this section");
            }
            if (!GRADEABLE_STATUSES.contains(String.valueOf(enrollment.get("status")))) {
                throw problem(HttpStatus.CONFLICT, "ENROLLMENT_NOT_GRADEABLE",
                        "Enrollment status '" + enrollment.get("status") + "' cannot receive grades");
            }
            if ("PUBLISHED".equals(enrollment.get("grade_status"))
                    || "APPEALED".equals(enrollment.get("grade_status"))) {
                // Published and appealed grades are the student-visible official
                // record: an API replay must not silently flip them back to draft.
                throw problem(HttpStatus.CONFLICT, "GRADES_PUBLISHED_LOCKED",
                        "Published grades can no longer be edited");
            }
            // Both-null clears a mistakenly saved draft: the batch DELETE below
            // removes the component rows and the enrollment update restores the
            // untouched NOT_GRADED state. A processScore can be saved alone as a
            // mid-term draft, but a finalExamScore without processScore cannot average.
            boolean cleared = grade.processScore() == null && grade.finalExamScore() == null;
            if (grade.processScore() == null && grade.finalExamScore() != null) {
                throw problem(HttpStatus.BAD_REQUEST, "GRADE_PARTIAL_SCORE",
                        "processScore is required when finalExamScore is provided");
            }
            if (!cleared) {
                requireScoreRange(grade.processScore(), "processScore");
                if (grade.finalExamScore() != null) {
                    requireScoreRange(grade.finalExamScore(), "finalExamScore");
                }
            }
            boolean midTermOnly = grade.processScore() != null && grade.finalExamScore() == null;
            BigDecimal total = (cleared || midTermOnly) ? null : calculateFinalGrade(grade.processScore(), grade.finalExamScore());

            if (!cleared) {
                componentBatch.add(new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID().toString())
                        .addValue("enrollmentId", grade.enrollmentId())
                        .addValue("gradeItemId", processItemId)
                        .addValue("score", grade.processScore()));

                if (!midTermOnly) {
                    componentBatch.add(new MapSqlParameterSource()
                            .addValue("id", UUID.randomUUID().toString())
                            .addValue("enrollmentId", grade.enrollmentId())
                            .addValue("gradeItemId", finalItemId)
                            .addValue("score", grade.finalExamScore()));
                }
            }

            enrollmentBatch.add(new MapSqlParameterSource()
                    .addValue("finalGrade", total)
                    .addValue("letterGrade", total == null ? null : letterGrade(total))
                    .addValue("gradeStatus", cleared ? "NOT_GRADED" : "DRAFT")
                    .addValue("id", grade.enrollmentId()));
        }

        // Batch delete existing grade components for targeted enrollments and canonical items
        jdbc.update(
                "DELETE FROM " + STUDENT_GRADE + " WHERE \"enrollmentId\" IN (:enrollmentIds) AND \"gradeItemId\" IN (:itemIds)",
                new MapSqlParameterSource()
                        .addValue("enrollmentIds", enrollmentIds)
                        .addValue("itemIds", List.of(processItemId, finalItemId)));

        // Batch insert new grade component rows
        jdbc.batchUpdate(
                "INSERT INTO " + STUDENT_GRADE + " (\"id\", \"enrollmentId\", \"gradeItemId\", \"score\")"
                        + " VALUES (:id, :enrollmentId, :gradeItemId, :score)",
                componentBatch.toArray(new MapSqlParameterSource[0]));

        // Batch update enrollment final grades and status — cleared rows carry
        // NOT_GRADED in the batch so the statement must read the parameter.
        jdbc.batchUpdate(
                "UPDATE " + ENROLLMENT + " SET \"finalGrade\" = :finalGrade, \"letterGrade\" = :letterGrade,"
                        + " \"gradeStatus\" = :gradeStatus, \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                enrollmentBatch.toArray(new MapSqlParameterSource[0]));

        // Component scores are delete+inserted per save with no per-row
        // history, so the audit row is the only trace that a grade write
        // happened. Record it for both caller kinds — the routine lecturer
        // save used to leave no trace at all (admin-only audit).
        if (actorId != null && !actorId.isBlank()) {
            audit.record(actorId, null, admin ? "GRADE_UPDATE_BY_ADMIN" : "GRADE_UPDATE",
                    "SECTION", sectionId,
                    (admin ? "Admin " : "Lecturer ") + actorId + " saved draft grades for "
                            + effectiveGrades.size() + " enrollment(s) in section " + sectionId);
        }
    }

    /** Compatibility entry point; publishes without a known actor record no audit trail. */
    @Transactional
    public void publishGrades(String sectionId, String lecturerId, boolean admin) {
        publishGrades(sectionId, lecturerId, admin, null);
    }

    /**
     * Publishes grades officially. When an ADMIN (not the owning lecturer)
     * publishes, an AdminAudit row records the actor and section (audit
     * S3-quick); the permission model itself is unchanged.
     */
    @Transactional
    public void publishGrades(String sectionId, String lecturerId, boolean admin, String actorId) {
        requireSection(sectionId);
        if (!admin && !ownsSection(sectionId, lecturerId)) {
            throw problem(HttpStatus.FORBIDDEN, "SECTION_FORBIDDEN", "Section is not assigned to the current lecturer");
        }
        // Completeness uses the same gradeable-population predicate as the
        // grading read model (GRADEABLE_STATUSES). Counting PENDING
        // rows here used to deadlock publication: no visible student row to
        // grade, yet publish answered 409 forever.
        Long incomplete = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + ENROLLMENT + " e WHERE e.\"sectionId\" = :sectionId"
                        + " AND e.\"status\" IN (:gradeableStatuses)"
                        + " AND (SELECT COUNT(DISTINCT gi.\"type\")"
                        + " FROM " + STUDENT_GRADE + " sg JOIN " + GRADE_ITEM + " gi ON gi.\"id\" = sg.\"gradeItemId\""
                        + " WHERE sg.\"enrollmentId\" = e.\"id\" AND sg.\"score\" IS NOT NULL"
                        + " AND gi.\"type\" IN ('PROCESS', 'FINAL')) < 2",
                new MapSqlParameterSource("sectionId", sectionId)
                        .addValue("gradeableStatuses", GRADEABLE_STATUSES), Long.class);
        if (incomplete != null && incomplete > 0) {
            Long blockers = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + ENROLLMENT + " e WHERE e.\"sectionId\" = :sectionId"
                            + " AND e.\"status\" NOT IN ('ENROLLED', 'CONFIRMED', 'COMPLETED', 'DROPPED', 'CANCELLED', 'WITHDRAWN')",
                    new MapSqlParameterSource("sectionId", sectionId), Long.class);
            String detail = blockers != null && blockers > 0
                    ? "All gradeable students require process and final exam scores before publishing"
                            + " (" + blockers + " enrollment(s) in a non-gradeable state need attention)"
                    : "All students require process and final exam scores before publishing";
            throw problem(HttpStatus.CONFLICT, "GRADE_COMPONENTS_INCOMPLETE", detail);
        }
        // Capture only the rows this publication transitions, while the section
        // lock serializes competing saves/publications. Send after commit so a
        // rolled-back publication cannot leave a false official-grade message.
        List<String> audience = publishAudience(sectionId);
        String sectionLabel = sectionLabel(sectionId);
        int updated = jdbc.update(
                "UPDATE " + ENROLLMENT + " SET \"gradeStatus\" = 'PUBLISHED', \"status\" = 'COMPLETED',"
                        + " \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"sectionId\" = :sectionId"
                        + " AND \"status\" IN (:gradeableStatuses)"
                        // A repeat publish must not re-touch or re-notify rows
                        // already published or appealed; IS DISTINCT FROM keeps
                        // NULL-gradeStatus pre-publish rows eligible.
                        + " AND \"gradeStatus\" IS DISTINCT FROM 'PUBLISHED'"
                        + " AND \"gradeStatus\" IS DISTINCT FROM 'APPEALED'"
                        + " AND \"finalGrade\" IS NOT NULL AND \"letterGrade\" IS NOT NULL",
                new MapSqlParameterSource("sectionId", sectionId)
                        .addValue("gradeableStatuses", GRADEABLE_STATUSES));
        if (updated == 0) {
            // Zero matching rows must never read as success: either nothing is
            // graded yet, or the only graded rows sit in a non-gradeable
            // (dropped/cancelled) enrollment that publish must not resurrect.
            throw problem(HttpStatus.CONFLICT, "GRADES_EMPTY", "No complete grades are ready to publish");
        }
        if (actorId != null && !actorId.isBlank()) {
            audit.record(actorId, null, admin ? "GRADE_PUBLISH_BY_ADMIN" : "GRADE_PUBLISH",
                    "SECTION", sectionId,
                    (admin ? "Admin " : "Lecturer ") + actorId + " published official grades for section "
                            + sectionId + " (" + updated + " enrollment(s))");
        }
        if (gradeNotifier != null && !audience.isEmpty()) {
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                sendPublishedGradeNotification(sectionId, sectionLabel, audience);
                            }
                        });
            } else {
                sendPublishedGradeNotification(sectionId, sectionLabel, audience);
            }
        }
    }

    private void sendPublishedGradeNotification(String sectionId, String sectionLabel, List<String> audience) {
        try {
            gradeNotifier.fanOutToSection(sectionLabel, audience);
        } catch (RuntimeException exception) {
            // Publishing already committed; keep inbox delivery best-effort and
            // avoid exposing raw database/recipient details in application logs.
            log.warn("Grade publication committed but notification fan-out failed for section {}", sectionId);
        }
    }

    /** Auth user ids of enrollments matching the exact publish predicate. */
    private List<String> publishAudience(String sectionId) {
        try {
            return jdbc.queryForList(
                "SELECT DISTINCT student.\"userId\" FROM " + ENROLLMENT + " e"
                        + " JOIN " + STUDENT + " student ON student.\"id\" = e.\"studentId\""
                        + " WHERE e.\"sectionId\" = :sectionId"
                        + " AND e.\"status\" IN (:gradeableStatuses)"
                        + " AND e.\"gradeStatus\" IS DISTINCT FROM 'PUBLISHED'"
                        + " AND e.\"gradeStatus\" IS DISTINCT FROM 'APPEALED'"
                        + " AND e.\"finalGrade\" IS NOT NULL AND e.\"letterGrade\" IS NOT NULL"
                        + " AND student.\"userId\" IS NOT NULL",
                new MapSqlParameterSource("sectionId", sectionId)
                        .addValue("gradeableStatuses", GRADEABLE_STATUSES),
                String.class);
        } catch (DataAccessException exception) {
            // Fixtures or deployments without the Student/notification join
            // tables must still publish grades; the fan-out just has no
            // audience to reach.
            return List.of();
        }
    }

    private String sectionLabel(String sectionId) {
        try {
            return jdbc.queryForObject(
                    "SELECT course.\"code\" || ' · Section ' || section.\"sectionNumber\""
                            + " FROM " + SECTION + " section"
                            + " JOIN " + COURSE + " course ON course.\"id\" = section.\"courseId\""
                            + " WHERE section.\"id\" = :sectionId",
                    new MapSqlParameterSource("sectionId", sectionId),
                    String.class);
        } catch (DataAccessException exception) {
            return sectionId;
        }
    }

    private String canonicalGradeItem(String sectionId, String type, String name) {
        String id = sectionId + "-" + type.toLowerCase() + "-50";
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("sectionId", sectionId)
                .addValue("name", name)
                .addValue("type", type);
        if (postgres) {
            jdbc.update("INSERT INTO " + GRADE_ITEM
                            + " (\"id\", \"sectionId\", \"name\", \"type\", \"maxScore\", \"weight\", \"gradedAt\")"
                            + " VALUES (:id, :sectionId, :name, :type, 10, 50, CURRENT_TIMESTAMP)"
                            + " ON CONFLICT (\"id\") DO UPDATE SET \"name\" = EXCLUDED.\"name\", \"type\" = EXCLUDED.\"type\","
                            + " \"maxScore\" = 10, \"weight\" = 50, \"gradedAt\" = CURRENT_TIMESTAMP",
                    params);
            return id;
        }
        // H2 does not implement PostgreSQL's ON CONFLICT syntax. An
        // update-then-insert inside the surrounding transaction keeps the same
        // canonical-row semantics without aborting on a duplicate key.
        int updated = jdbc.update(
                "UPDATE " + GRADE_ITEM
                        + " SET \"name\" = :name, \"type\" = :type, \"maxScore\" = 10, \"weight\" = 50,"
                        + " \"gradedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id",
                params);
        if (updated == 0) {
            jdbc.update("INSERT INTO " + GRADE_ITEM
                            + " (\"id\", \"sectionId\", \"name\", \"type\", \"maxScore\", \"weight\", \"gradedAt\")"
                            + " VALUES (:id, :sectionId, :name, :type, 10, 50, CURRENT_TIMESTAMP)",
                    params);
        }
        return id;
    }

    private void saveComponent(String enrollmentId, String gradeItemId, BigDecimal score) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("enrollmentId", enrollmentId)
                .addValue("gradeItemId", gradeItemId).addValue("score", score);
        jdbc.update("DELETE FROM " + STUDENT_GRADE + " WHERE \"enrollmentId\" = :enrollmentId AND \"gradeItemId\" = :gradeItemId", params);
        params.addValue("id", UUID.randomUUID().toString());
        jdbc.update("INSERT INTO " + STUDENT_GRADE + " (\"id\", \"enrollmentId\", \"gradeItemId\", \"score\")"
                + " VALUES (:id, :enrollmentId, :gradeItemId, :score)", params);
    }

    static BigDecimal calculateFinalGrade(BigDecimal processScore, BigDecimal finalExamScore) {
        return processScore.add(finalExamScore).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    static String letterGrade(BigDecimal score) {
        if (score.compareTo(new BigDecimal("9.0")) >= 0) return "A+";
        if (score.compareTo(new BigDecimal("8.5")) >= 0) return "A";
        if (score.compareTo(new BigDecimal("8.0")) >= 0) return "B+";
        if (score.compareTo(new BigDecimal("7.0")) >= 0) return "B";
        if (score.compareTo(new BigDecimal("6.5")) >= 0) return "C+";
        if (score.compareTo(new BigDecimal("5.5")) >= 0) return "C";
        if (score.compareTo(new BigDecimal("5.0")) >= 0) return "D+";
        if (score.compareTo(new BigDecimal("4.0")) >= 0) return "D";
        return "F";
    }

    private void requireStudent(String studentId) {
        if (studentId == null || studentId.isBlank()) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + STUDENT + " WHERE \"id\" = :id AND \"status\" = 'ACTIVE'",
                new MapSqlParameterSource("id", studentId),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.FORBIDDEN, "STUDENT_PROFILE_REQUIRED", "Student profile is required");
        }
    }

    private Map<String, Object> section(String sectionId) {
        try {
            return jdbc.queryForMap(
                    "SELECT \"id\", \"semesterId\" AS semester_id, \"capacity\", \"enrolledCount\" AS enrolled_count, \"status\""
                            + " FROM " + SECTION + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", sectionId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "SECTION_NOT_FOUND", "Section not found");
        }
    }

    private void requireSection(String sectionId) {
        Map<String, Object> section = section(sectionId);
        // Grades are the official record of a class that actually met; writing
        // or publishing them for a cancelled section fabricates results for
        // sessions that never ran. Reads stay untouched: published history
        // from before the cancellation remains on the transcript.
        if ("CANCELLED".equals(String.valueOf(section.get("status")))) {
            throw problem(HttpStatus.CONFLICT, "SECTION_CANCELLED", "Section is cancelled");
        }
    }

    private boolean semesterOpen(String semesterId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SEMESTER + " WHERE \"id\" = :id AND \"status\" IN (:openStatuses)",
                new MapSqlParameterSource().addValue("id", semesterId).addValue("openStatuses", SemesterStatuses.OPEN),
                Long.class);
        return count != null && count > 0;
    }

    private boolean ownsSection(String sectionId, String lecturerId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SECTION + " WHERE \"id\" = :sectionId AND \"lecturerId\" = :lecturerId",
                new MapSqlParameterSource().addValue("sectionId", sectionId).addValue("lecturerId", lecturerId),
                Long.class);
        return count != null && count > 0;
    }

    private Map<String, Object> enrollment(String enrollmentId) {
        try {
            return jdbc.queryForMap(
                    "SELECT \"id\", \"studentId\" AS student_id, \"sectionId\" AS section_id, \"status\","
                            + " \"semesterId\" AS semester_id, \"gradeStatus\" AS grade_status"
                            + " FROM " + ENROLLMENT + " WHERE \"id\" = :id FOR UPDATE",
                    new MapSqlParameterSource("id", enrollmentId));
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "Enrollment not found");
        }
    }

    private static void addFilter(
            List<String> filters,
            MapSqlParameterSource parameters,
            String name,
            String value,
            String expression) {
        if (value != null && !value.isBlank()) {
            filters.add(expression);
            parameters.addValue(name, value.trim());
        }
    }

    static String csv(Object value) {
        String text = value == null ? "" : value.toString();
        if (startsFormulaCharacter(text)) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static boolean startsFormulaCharacter(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }

    /** Defense-in-depth score bound; the web DTO also enforces 0..10 via @DecimalMin/@DecimalMax. */
    private static void requireScoreRange(BigDecimal score, String field) {
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.TEN) > 0) {
            throw problem(HttpStatus.BAD_REQUEST, "GRADE_SCORE_OUT_OF_RANGE", field + " must be between 0 and 10");
        }
    }
}
