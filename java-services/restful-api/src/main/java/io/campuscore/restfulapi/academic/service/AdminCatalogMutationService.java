package io.campuscore.restfulapi.academic.service;

import io.campuscore.restfulapi.academic.registration.service.CreditLimitApplicationService;
import io.campuscore.restfulapi.audit.service.AdminAuditRecorder;
import io.campuscore.restfulapi.exception.DomainException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Small, explicit admin catalog write boundary for the course project. */
@Service
@Profile("persistence")
public class AdminCatalogMutationService {

    private static final String FACULTY = "\"academic\".\"Faculty\"";
    private static final String DEPARTMENT = "\"academic\".\"Department\"";
    private static final String ACADEMIC_YEAR = "\"academic\".\"AcademicYear\"";
    private static final String SEMESTER = "\"academic\".\"Semester\"";
    private static final String COURSE = "\"academic\".\"Course\"";
    private static final String CLASSROOM = "\"academic\".\"Classroom\"";
    private static final String SECTION = "\"academic\".\"Section\"";
    private static final String SECTION_SCHEDULE = "\"academic\".\"SectionSchedule\"";
    private static final String ENROLLMENT = "\"academic\".\"Enrollment\"";
    private static final String SLIP = "\"academic\".\"RegistrationSlip\"";
    private static final String STUDENT = "\"academic\".\"Student\"";
    /** Section.lecturerId joins the academic profile table (the auth schema exposes it as a view). */
    private static final String LECTURER = "\"academic\".\"Lecturer\"";
    /** Statuses the academic services and the admin console understand. */
    private static final Set<String> SEMESTER_STATUSES =
            Set.of("DRAFT", "OPEN", "REGISTRATION_OPEN", "ADD_DROP_OPEN", "ACTIVE", "IN_PROGRESS", "CLOSED");
    private static final Set<String> SECTION_STATUSES = Set.of("OPEN", "CLOSED", "CANCELLED");

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAuditRecorder audit;
    private final CreditLimitApplicationService creditLimitApplications;

    public AdminCatalogMutationService(NamedParameterJdbcTemplate jdbc,
            CreditLimitApplicationService creditLimitApplications) {
        this.jdbc = jdbc;
        this.audit = new AdminAuditRecorder(jdbc);
        this.creditLimitApplications = creditLimitApplications;
    }

    @Transactional
    public Map<String, Object> createDepartment(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        String facultyId = text(input, "facultyId", null);
        if (facultyId == null || facultyId.isBlank()) {
            List<String> ids = jdbc.getJdbcTemplate().query(
                    "SELECT \"id\" FROM " + FACULTY + " LIMIT 1",
                    (rs, rowNum) -> rs.getString("id"));
            facultyId = ids.isEmpty() ? "faculty-demo" : ids.get(0);
        }
        jdbc.update(
                "INSERT INTO " + DEPARTMENT
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\", \"descriptionEn\", \"descriptionVi\", \"facultyId\")"
                        + " VALUES (:id, :name, :nameEn, :nameVi, :code, :description, :descriptionEn, :descriptionVi, :facultyId)",
                params(input, id).addValue("facultyId", facultyId));
        return created(DEPARTMENT, id);
    }

    @Transactional
    public Map<String, Object> createAcademicYear(Map<String, Object> input) {
        String id = id(input);
        String startDate = required(input, "startDate");
        String endDate = required(input, "endDate");
        requireOrderedRange(startDate, endDate);
        jdbc.update(
                "INSERT INTO " + ACADEMIC_YEAR
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\")"
                        + " VALUES (:id, :year, CAST(:startDate AS TIMESTAMP WITH TIME ZONE),"
                        + " CAST(:endDate AS TIMESTAMP WITH TIME ZONE), :isCurrent)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("year", number(input, "year", java.time.Year.now().getValue()))
                        .addValue("startDate", startDate)
                        .addValue("endDate", endDate)
                        .addValue("isCurrent", Boolean.parseBoolean(text(input, "isCurrent", "false"))));
        return created(ACADEMIC_YEAR, id);
    }

    @Transactional
    public Map<String, Object> createCourse(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        int credits = number(input, "credits", 3);
        requireCreditsInRange(credits);
        String departmentId = text(input, "departmentId", null);
        if (departmentId == null || departmentId.isBlank()) {
            List<String> ids = jdbc.getJdbcTemplate().query(
                    "SELECT \"id\" FROM " + DEPARTMENT + " LIMIT 1",
                    (rs, rowNum) -> rs.getString("id"));
            departmentId = ids.isEmpty() ? "department-demo" : ids.get(0);
        }
        jdbc.update(
                "INSERT INTO " + COURSE
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\", \"descriptionVi\", \"credits\", \"departmentId\")"
                        + " VALUES (:id, :code, :name, :nameEn, :nameVi, :description, :descriptionEn, :descriptionVi, :credits, :departmentId)",
                params(input, id)
                        .addValue("credits", credits)
                        .addValue("departmentId", departmentId));
        return created(COURSE, id);
    }

    @Transactional
    public Map<String, Object> createClassroom(Map<String, Object> input) {
        String id = id(input);
        String building = required(input, "building");
        String roomNumber = required(input, "roomNumber");
        int capacity = number(input, "capacity", 30);
        if (capacity < 1) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY", "Classroom capacity must be at least 1");
        }
        String type = text(input, "type", "LECTURE");
        // isActive is part of the classroom contract: an explicit false must
        // persist instead of falling back to the column default of TRUE.
        Boolean isActive = input.containsKey("isActive") && input.get("isActive") != null
                ? Boolean.parseBoolean(input.get("isActive").toString())
                : Boolean.TRUE;
        jdbc.update(
                "INSERT INTO " + CLASSROOM
                        + " (\"id\", \"building\", \"roomNumber\", \"capacity\", \"type\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (:id, :building, :roomNumber, :capacity, :type, :isActive, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                params(input, id)
                        .addValue("building", building)
                        .addValue("roomNumber", roomNumber)
                        .addValue("capacity", capacity)
                        .addValue("type", type)
                        .addValue("isActive", isActive));
        return created(CLASSROOM, id);
    }

    @Transactional
    public Map<String, Object> createSemester(Map<String, Object> input) {
        String id = id(input);
        required(input, "name");
        String academicYearId = text(input, "academicYearId", null);
        if (academicYearId == null || academicYearId.isBlank()) {
            List<String> ids = jdbc.getJdbcTemplate().query(
                    "SELECT \"id\" FROM " + ACADEMIC_YEAR + " ORDER BY \"isCurrent\" DESC, \"year\" DESC LIMIT 1",
                    (rs, rowNum) -> rs.getString("id"));
            academicYearId = ids.isEmpty() ? "academic-year-demo" : ids.get(0);
        }
        validateSemesterDates(null, academicYearId,
                input.get("startDate"), input.get("endDate"),
                input.get("registrationStart"), input.get("registrationEnd"));
        String status = text(input, "status", "DRAFT");
        requireSemesterStatus(status);
        // Missing dates keep the historic server defaults (now / now + 5
        // months); they are computed here because Postgres accepts
        // INTERVAL '5 months' while H2 does not parse that literal.
        java.sql.Timestamp startFallback = input.get("startDate") == null
                ? java.sql.Timestamp.from(Instant.now()) : null;
        java.sql.Timestamp endFallback = input.get("endDate") == null
                ? java.sql.Timestamp.from(Instant.now().plusSeconds(15_778_800)) : null;
        jdbc.update(
                "INSERT INTO " + SEMESTER
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\","
                        + " \"startDate\", \"endDate\", \"registrationStart\", \"registrationEnd\", \"status\")"
                        + " VALUES (:id, :name, :nameEn, :nameVi, :type, :academicYearId,"
                        + " COALESCE(CAST(:startDate AS TIMESTAMP WITH TIME ZONE), :startFallback),"
                        + " COALESCE(CAST(:endDate AS TIMESTAMP WITH TIME ZONE), :endFallback),"
                        // The registration window needs the same explicit cast as
                        // the main range: an untyped VARCHAR parameter reaches
                        // Postgres as character varying and the INSERT dies with
                        // "column ... is of type timestamp with time zone but
                        // expression is of type character varying" (a live 500).
                        + " CAST(:registrationStart AS TIMESTAMP WITH TIME ZONE),"
                        + " CAST(:registrationEnd AS TIMESTAMP WITH TIME ZONE), :status)",
                params(input, id)
                        .addValue("type", text(input, "type", "FIRST"))
                        .addValue("academicYearId", academicYearId)
                        .addValue("startDate", input.get("startDate"))
                        .addValue("startFallback", startFallback)
                        .addValue("endDate", input.get("endDate"))
                        .addValue("endFallback", endFallback)
                        .addValue("registrationStart", input.get("registrationStart"))
                        .addValue("registrationEnd", input.get("registrationEnd"))
                        .addValue("status", status));
        return created(SEMESTER, id);
    }

    @Transactional
    public Map<String, Object> createSection(Map<String, Object> input) {
        String id = id(input);
        int capacity = number(input, "capacity", 30);
        requireSectionCapacity(capacity);
        String status = text(input, "status", "OPEN");
        requireSectionStatus(status);
        String courseId = required(input, "courseId");
        String semesterId = required(input, "semesterId");
        requireCatalogReference(COURSE, "courseId", courseId);
        requireCatalogReference(SEMESTER, "semesterId", semesterId);
        String lecturerId = text(input, "lecturerId", null);
        if (lecturerId != null) {
            requireCatalogReference(LECTURER, "lecturerId", lecturerId);
        }
        String classroomId = text(input, "classroomId", null);
        if (classroomId != null) {
            requireCatalogReference(CLASSROOM, "classroomId", classroomId);
            requireActiveClassroom(classroomId);
            requireRoomFitsCapacity(classroomId, capacity);
        }
        jdbc.update(
                "INSERT INTO " + SECTION
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\", \"capacity\", \"status\")"
                        + " VALUES (:id, :sectionNumber, :courseId, :semesterId, :lecturerId, :classroomId, :capacity, :status)",
                params(input, id)
                        .addValue("sectionNumber", text(input, "sectionNumber", text(input, "code", "SECTION-01")))
                        .addValue("courseId", courseId)
                        .addValue("semesterId", semesterId)
                        .addValue("lecturerId", input.get("lecturerId"))
                        .addValue("classroomId", input.get("classroomId"))
                        .addValue("capacity", capacity)
                        .addValue("status", status));
        if (input.containsKey("schedules")) {
            replaceSectionSchedules(id, input.get("schedules"));
        }
        return created(SECTION, id);
    }

    @Transactional
    public Map<String, Object> update(String table, String id, Map<String, Object> input) {
        Map<String, Object> before = rowOrNull(table, id);
        Map<String, String> columns = allowedColumns(table);
        // Update runs the same invariants as create: without them a course
        // could gain 999 credits or a section a five-digit capacity through a
        // plain edit. One validator per invariant serves both paths.
        if (COURSE.equals(table) && input.containsKey("credits")) {
            requireCreditsInRange(number(input, "credits", 3));
        }
        if (SEMESTER.equals(table) && input.containsKey("status")) {
            requireSemesterStatus(text(input, "status", null));
        }
        if (SEMESTER.equals(table)) {
            validateSemesterUpdate(id, input);
        }
        if (ACADEMIC_YEAR.equals(table)
                && (input.containsKey("startDate") || input.containsKey("endDate"))) {
            Map<String, Object> currentYear = requireRow(table, id);
            requireOrderedRange(
                    mergedUpdateValue(input, currentYear, "startDate"),
                    mergedUpdateValue(input, currentYear, "endDate"));
        }
        if (SECTION.equals(table)) {
            validateSectionUpdate(id, input);
        }
        if (CLASSROOM.equals(table) && input.containsKey("capacity")) {
            int capacity = number(input, "capacity", 30);
            if (capacity < 1) {
                throw problem(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY", "Classroom capacity must be at least 1");
            }
        }
        // Reopening a cancelled section must serialize with concurrent
        // enrolls: without these locks an enroll could commit between the
        // conflict scan and this transaction's commit, leaving the student
        // over the cap with no acknowledgement on the audit trail. Take the
        // enroll path's Student FOR UPDATE first, in id order and BEFORE the
        // section write — the global Student -> Section -> Round order.
        // Taking them after the update would invert against
        // retire-on-re-enroll, which holds the student and then updates this
        // section's rows.
        boolean reopening = SECTION.equals(table) && before != null
                && "CANCELLED".equals(before.get("status"))
                && input.containsKey("status") && !"CANCELLED".equals(input.get("status"));
        if (reopening) {
            // A reopen re-adds enrollment capacity, so it must not run inside
            // a semester that already ended; every earlier lifecycle state
            // allows it subject to the conflict checks. Cancelling stays
            // allowed in any semester state because it only removes seats.
            requireNonTerminalSemesterForReopen(before.get("semesterId"));
            lockReopenAffectedStudents(id);
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        StringBuilder sql = new StringBuilder("UPDATE ").append(table).append(" SET ");
        boolean replacesSchedules = SECTION.equals(table) && input.containsKey("schedules");
        boolean first = true;
        for (Map.Entry<String, String> entry : columns.entrySet()) {
            if (!input.containsKey(entry.getKey())) {
                continue;
            }
            if (!first) {
                sql.append(", ");
            }
            first = false;
            sql.append(entry.getValue()).append(" = ");
            if (isTimestampField(table, entry.getKey())) {
                sql.append("CAST(:").append(entry.getKey()).append(" AS TIMESTAMP WITH TIME ZONE)");
            } else {
                sql.append(':').append(entry.getKey());
            }
            parameters.addValue(entry.getKey(), input.get(entry.getKey()));
        }
        if (first) {
            if (!replacesSchedules) {
                throw new IllegalArgumentException("At least one editable field is required");
            }
            requireExists(table, id);
        } else {
            // The transition decision above read `before` without a lock, so
            // a status write must verify that observed state: a transition
            // committed in between (a cancel behind a queued edit, another
            // admin's reopen) makes the guarded write match no row and the
            // edit refuses instead of stamping stale status over the commit.
            // IS NOT DISTINCT FROM keeps a null status comparable on both
            // engines; the row itself is never locked before the student
            // locks the reopen path takes first.
            boolean statusGuarded = SECTION.equals(table) && before != null
                    && input.containsKey("status");
            sql.append(", \"updatedAt\" = CURRENT_TIMESTAMP WHERE \"id\" = :id");
            if (statusGuarded) {
                sql.append(" AND \"status\" IS NOT DISTINCT FROM :beforeStatus");
                parameters.addValue("beforeStatus", before.get("status"));
            }
            int updated = jdbc.update(sql.toString(), parameters);
            if (updated == 0) {
                if (!statusGuarded) {
                    throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
                }
                requireExists(table, id);
                throw problem(HttpStatus.CONFLICT, "SECTION_STATUS_CHANGED",
                        "The section changed while the edit was open; reload and try again");
            }
        }
        if (replacesSchedules) {
            replaceSectionSchedules(id, input.get("schedules"));
        } else if (SECTION.equals(table)) {
            // Omitted schedules keep their rows, but new capacity or assignment
            // must still be valid against that retained timetable.
            if (input.containsKey("capacity")) {
                requireScheduledRoomsFitCapacity(id);
            }
            if (input.containsKey("lecturerId") || input.containsKey("semesterId")) {
                assertNoSectionConflicts(id);
            }
        }
        if (SECTION.equals(table) && input.containsKey("status") && before != null) {
            boolean cancelled = "CANCELLED".equals(before.get("status"));
            boolean cancelling = !cancelled && "CANCELLED".equals(input.get("status"));
            if (reopening) {
                // The cancelled section released its room/lecturer slots, so a
                // reopen must not silently restore a slot another section now
                // holds — nor one whose room was deactivated in the meantime.
                requireScheduledRoomsActive(id);
                assertNoSectionConflicts(id);
                // Students kept enrolled through the cancellation may have
                // re-registered since: the reopened section must not push them
                // over their credit cap or onto a timetable they already
                // occupy. A conflict refuses the reopen unless the operator
                // acknowledges it with a recorded reason. The per-student
                // detail rides on the error envelope's admin-only `details`.
                List<Map<String, Object>> conflicts = reopenConflicts(id);
                if (!conflicts.isEmpty()) {
                    boolean acknowledged = "true".equalsIgnoreCase(
                            String.valueOf(input.getOrDefault("acknowledgeConflicts", "")));
                    String reason = text(input, "reason", "");
                    if (!acknowledged || reason.isBlank()) {
                        throw new DomainException(HttpStatus.CONFLICT, "REOPEN_CONFLICTS",
                                "Reopening creates " + conflicts.size() + " student conflict(s); "
                                        + "pass acknowledgeConflicts with a reason to reopen anyway",
                                Map.of("conflictCount", conflicts.size(), "conflicts", conflicts));
                    }
                    audit.record(actor(), null, "SECTION_REOPEN_CONFLICTS_ACKNOWLEDGED", "Section", id,
                            "Reopen acknowledged " + conflicts.size() + " student conflict(s); reason: "
                                    + reason + "; " + conflicts,
                            Map.of(), Map.of("conflicts", conflicts));
                }
                // A persisted slip asserts registrations that no longer match
                // the student's real set after the cancel window — seats may
                // have been replaced or dropped — so every successful reopen
                // re-issues them on the next download.
                jdbc.update(
                        "DELETE FROM " + SLIP + " WHERE (\"studentId\", \"semesterId\") IN"
                                + " (SELECT e.\"studentId\", e.\"semesterId\" FROM " + ENROLLMENT + " e"
                                + " WHERE e.\"sectionId\" = :sectionId"
                                + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED'))",
                        new MapSqlParameterSource("sectionId", id));
            }
            if (cancelling) {
                // Persisted slips assert active registrations; leaving them in
                // place would keep offering the cancelled course on the
                // downloaded slip.
                jdbc.update(
                        "DELETE FROM " + SLIP + " WHERE (\"studentId\", \"semesterId\") IN"
                                + " (SELECT e.\"studentId\", e.\"semesterId\" FROM " + ENROLLMENT + " e"
                                + " WHERE e.\"sectionId\" = :sectionId"
                                + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED'))",
                        new MapSqlParameterSource("sectionId", id));
            }
        }
        Map<String, Object> after = get(table, id);
        audit.record(actor(), null, "UPDATED", entityType(table), id,
                entityType(table) + " " + id + " updated by " + actorLabel(),
                before, after);
        return after;
    }

    @Transactional
    public void delete(String table, String id) {
        Map<String, Object> before = rowOrNull(table, id);
        try {
            int deleted = jdbc.update("DELETE FROM " + table + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
            if (deleted == 0) {
                throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
            }
        } catch (DataIntegrityViolationException exception) {
            throw problem(HttpStatus.CONFLICT, "RESOURCE_IN_USE", "Resource is still referenced by another record");
        }
        audit.recordDeletion(actor(), null, entityType(table), id, before);
    }

    /** Returns the persisted row and records the create on the shared audit trail. */
    private Map<String, Object> created(String table, String id) {
        Map<String, Object> row = get(table, id);
        audit.record(actor(), null, "CREATED", entityType(table), id,
                entityType(table) + " " + id + " created by " + actorLabel(),
                null, row);
        return row;
    }

    private Map<String, Object> rowOrNull(String table, String id) {
        try {
            return get(table, id);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    /** Maps a qualified table literal like "academic"."Department" to its simple entity name. */
    private static String entityType(String table) {
        String stripped = table.replace("\"", "");
        return stripped.substring(stripped.lastIndexOf('.') + 1);
    }

    private static String actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof Jwt jwt
                ? jwt.getSubject()
                : null;
    }

    private static String actorLabel() {
        String actor = actor();
        return actor == null || actor.isBlank() ? "an automated actor" : actor;
    }

    private Map<String, Object> get(String table, String id) {
        return jdbc.queryForMap("SELECT * FROM " + table + " WHERE \"id\" = :id", new MapSqlParameterSource("id", id));
    }

    private static Map<String, String> allowedColumns(String table) {
        Map<String, String> columns = new LinkedHashMap<>();
        if (ACADEMIC_YEAR.equals(table)) {
            columns.put("year", "\"year\""); columns.put("startDate", "\"startDate\"");
            columns.put("endDate", "\"endDate\""); columns.put("isCurrent", "\"isCurrent\"");
        } else if (DEPARTMENT.equals(table)) {
            columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\""); columns.put("nameVi", "\"nameVi\"");
            columns.put("code", "\"code\""); columns.put("description", "\"description\"");
            columns.put("descriptionEn", "\"descriptionEn\""); columns.put("descriptionVi", "\"descriptionVi\"");
            columns.put("facultyId", "\"facultyId\"");
        } else if (COURSE.equals(table)) {
            columns.put("code", "\"code\""); columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\"");
            columns.put("nameVi", "\"nameVi\""); columns.put("description", "\"description\"");
            columns.put("descriptionEn", "\"descriptionEn\""); columns.put("descriptionVi", "\"descriptionVi\"");
            columns.put("credits", "\"credits\"");
            columns.put("departmentId", "\"departmentId\""); columns.put("isActive", "\"isActive\"");
        } else if (CLASSROOM.equals(table)) {
            columns.put("building", "\"building\""); columns.put("roomNumber", "\"roomNumber\""); columns.put("capacity", "\"capacity\"");
            columns.put("type", "\"type\""); columns.put("isActive", "\"isActive\"");
        } else if (SEMESTER.equals(table)) {
            columns.put("name", "\"name\""); columns.put("nameEn", "\"nameEn\""); columns.put("nameVi", "\"nameVi\"");
            columns.put("type", "\"type\""); columns.put("academicYearId", "\"academicYearId\"");
            columns.put("startDate", "\"startDate\""); columns.put("endDate", "\"endDate\"");
            columns.put("status", "\"status\""); columns.put("registrationStart", "\"registrationStart\"");
            columns.put("registrationEnd", "\"registrationEnd\""); columns.put("addDropStart", "\"addDropStart\"");
            columns.put("addDropEnd", "\"addDropEnd\"");
        } else if (SECTION.equals(table)) {
            columns.put("sectionNumber", "\"sectionNumber\""); columns.put("courseId", "\"courseId\"");
            columns.put("semesterId", "\"semesterId\""); columns.put("lecturerId", "\"lecturerId\"");
            columns.put("classroomId", "\"classroomId\""); columns.put("capacity", "\"capacity\""); columns.put("status", "\"status\"");
        } else {
            throw new IllegalArgumentException("Unsupported catalog resource");
        }
        return columns;
    }

    private static boolean isTimestampField(String table, String field) {
        if (ACADEMIC_YEAR.equals(table)) {
            return "startDate".equals(field) || "endDate".equals(field);
        }
        return SEMESTER.equals(table) && ("startDate".equals(field)
                || "endDate".equals(field)
                || "registrationStart".equals(field)
                || "registrationEnd".equals(field)
                || "addDropStart".equals(field)
                || "addDropEnd".equals(field));
    }

    private void replaceSectionSchedules(String sectionId, Object value) {
        if (!(value instanceof List<?> schedules)) {
            throw new IllegalArgumentException("schedules must be an array");
        }
        for (Object item : schedules) {
            if (!(item instanceof Map<?, ?> schedule)) {
                throw new IllegalArgumentException("Each schedule must be an object");
            }
            int dayOfWeek = requiredScheduleNumber(schedule, "dayOfWeek");
            if (dayOfWeek < 1 || dayOfWeek > 7) {
                throw problem(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE",
                        "dayOfWeek must be between 1 and 7");
            }
            String startTime = requiredScheduleText(schedule, "startTime");
            String endTime = requiredScheduleText(schedule, "endTime");
            // startTime/endTime are VARCHAR(10) columns compared
            // lexicographically everywhere downstream (the conflict SQL below
            // and RegistrationService.overlaps), which only matches
            // chronological order for the zero-padded HH:mm form. Enforce the
            // format here — an unpadded "7:00" would silently produce missed
            // and phantom schedule conflicts.
            java.time.LocalTime start = parseScheduleTime(startTime, "startTime");
            java.time.LocalTime end = parseScheduleTime(endTime, "endTime");
            if (!start.isBefore(end)) {
                throw problem(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE",
                        "startTime must be before endTime");
            }
            requireActiveClassroom(requiredScheduleText(schedule, "classroomId"));
        }
        jdbc.update(
                "DELETE FROM " + SECTION_SCHEDULE + " WHERE \"sectionId\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId));
        for (Object item : schedules) {
            Map<?, ?> schedule = (Map<?, ?>) item;
            jdbc.update(
                    "INSERT INTO " + SECTION_SCHEDULE
                            + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                            + " VALUES (:id, :sectionId, :classroomId, :dayOfWeek, :startTime, :endTime)",
                    new MapSqlParameterSource()
                            .addValue("id", optionalScheduleText(schedule, "id", UUID.randomUUID().toString()))
                            .addValue("sectionId", sectionId)
                            .addValue("classroomId", requiredScheduleText(schedule, "classroomId"))
                            .addValue("dayOfWeek", requiredScheduleNumber(schedule, "dayOfWeek"))
                            .addValue("startTime", requiredScheduleText(schedule, "startTime"))
                            .addValue("endTime", requiredScheduleText(schedule, "endTime")));
        }
        // Validate the effective replacement inside this transaction. This also
        // allows clearing or moving old small rooms while increasing capacity.
        requireScheduledRoomsFitCapacity(sectionId);
        assertNoSectionConflicts(sectionId);
    }

    /**
     * Per-student conflicts a reopen would create: the preserved enrollments
     * stayed active through the cancellation, but the students' other live
     * registrations moved on — the reopened section's credits may now break
     * their effective credit cap, and its timetable may overlap a section
     * they took in the meantime. Returns one structured entry per conflicted
     * student — student id, the reasons, and the peer section ids — for the
     * admin-only conflict detail.
     */
    private List<Map<String, Object>> reopenConflicts(String sectionId) {
        List<Map<String, Object>> affected = jdbc.queryForList(
                "SELECT e.\"studentId\" AS student_id, e.\"semesterId\" AS semester_id,"
                        + " e.\"roundId\" AS round_id, course.\"credits\" AS credits"
                        + " FROM " + ENROLLMENT + " e"
                        + " JOIN " + SECTION + " s ON s.\"id\" = e.\"sectionId\""
                        + " JOIN " + COURSE + " course ON course.\"id\" = s.\"courseId\""
                        + " WHERE e.\"sectionId\" = :sectionId"
                        + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')",
                new MapSqlParameterSource("sectionId", sectionId));
        // The cap an enroll attempt would face NOW: the currently open,
        // in-window round for the section's semester. Evaluating against the
        // round each preserved enrollment came from misses credit-limit
        // approvals granted on the later round — and applies a stale closed
        // window's standard limit besides.
        Map<String, Object> currentRound = affected.isEmpty() ? null
                : currentRoundOrNull(String.valueOf(affected.get(0).get("semester_id")));
        List<Map<String, Object>> conflicts = new java.util.ArrayList<>();
        for (Map<String, Object> row : affected) {
            String studentId = String.valueOf(row.get("student_id"));
            String semesterId = String.valueOf(row.get("semester_id"));
            int courseCredits = ((Number) row.get("credits")).intValue();
            List<String> details = new java.util.ArrayList<>();
            List<String> sectionIds = new java.util.ArrayList<>();
            Integer otherCredits = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(course.\"credits\"), 0) FROM " + ENROLLMENT + " e"
                            + " JOIN " + SECTION + " s ON s.\"id\" = e.\"sectionId\""
                            + " JOIN " + COURSE + " course ON course.\"id\" = s.\"courseId\""
                            + " WHERE e.\"studentId\" = :studentId AND e.\"semesterId\" = :semesterId"
                            + " AND e.\"sectionId\" <> :sectionId"
                            + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                            + " AND s.\"status\" <> 'CANCELLED'",
                    new MapSqlParameterSource()
                            .addValue("studentId", studentId)
                            .addValue("semesterId", semesterId)
                            .addValue("sectionId", sectionId),
                    Integer.class);
            int limit = currentRound == null
                    ? effectiveLimit(studentId, row.get("round_id"))
                    : creditLimitApplications.effectiveLimit(studentId, currentRound);
            int other = otherCredits == null ? 0 : otherCredits;
            if (other + courseCredits > limit) {
                details.add("CREDIT_CAP");
            }
            List<String> overlappingSections = jdbc.queryForList(
                    "SELECT DISTINCT e.\"sectionId\" FROM " + ENROLLMENT + " e"
                            + " JOIN " + SECTION + " s ON s.\"id\" = e.\"sectionId\" AND s.\"status\" <> 'CANCELLED'"
                            + " JOIN " + SECTION_SCHEDULE + " theirs ON theirs.\"sectionId\" = s.\"id\""
                            + " JOIN " + SECTION_SCHEDULE + " mine ON mine.\"sectionId\" = :sectionId"
                            + " AND theirs.\"dayOfWeek\" = mine.\"dayOfWeek\""
                            + " AND theirs.\"startTime\" < mine.\"endTime\" AND theirs.\"endTime\" > mine.\"startTime\""
                            + " WHERE e.\"studentId\" = :studentId AND e.\"semesterId\" = :semesterId"
                            + " AND e.\"sectionId\" <> :sectionId"
                            + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')",
                    new MapSqlParameterSource()
                            .addValue("sectionId", sectionId)
                            .addValue("studentId", studentId)
                            .addValue("semesterId", semesterId),
                    String.class);
            if (!overlappingSections.isEmpty()) {
                details.add("SCHEDULE_OVERLAP");
                sectionIds.addAll(overlappingSections);
            }
            if (!details.isEmpty()) {
                Map<String, Object> conflict = new java.util.LinkedHashMap<>();
                conflict.put("studentId", studentId);
                conflict.put("reasons", details);
                conflict.put("sections", sectionIds);
                conflicts.add(conflict);
            }
        }
        return conflicts;
    }

    /**
     * Locks every student still registered on the cancelled section under the
     * same FOR UPDATE the enroll path takes first, in ascending id order, so
     * a concurrent enroll serializes fully before the conflict scan or fully
     * after this transaction commits. The sorted order also keeps two
     * reopens, or a reopen and a retire-on-re-enroll, from inverting on each
     * other.
     */
    private void lockReopenAffectedStudents(String sectionId) {
        List<String> studentIds = jdbc.queryForList(
                "SELECT DISTINCT e.\"studentId\" FROM " + ENROLLMENT + " e"
                        + " WHERE e.\"sectionId\" = :sectionId"
                        + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " ORDER BY e.\"studentId\"",
                new MapSqlParameterSource("sectionId", sectionId), String.class);
        if (studentIds.isEmpty()) {
            return;
        }
        jdbc.queryForList(
                "SELECT \"id\" FROM " + STUDENT + " WHERE \"id\" IN (:studentIds)"
                        + " ORDER BY \"id\" FOR UPDATE",
                new MapSqlParameterSource("studentIds", studentIds), String.class);
    }

    /**
     * The round an enroll would be judged under right now — OPEN status and
     * inside its window, REGISTRATION preferred with the same ADD_DROP
     * fallback the enroll path uses — or null when no window is open.
     */
    private Map<String, Object> currentRoundOrNull(String semesterId) {
        List<Map<String, Object>> rounds = jdbc.queryForList(
                "SELECT \"id\", \"kind\", \"creditLimit\" AS credit_limit"
                        + " FROM \"academic\".\"RegistrationRound\""
                        + " WHERE \"semesterId\" = :semesterId AND \"status\" = 'OPEN'"
                        + " AND \"windowStart\" <= CURRENT_TIMESTAMP AND \"windowEnd\" >= CURRENT_TIMESTAMP"
                        + " ORDER BY \"windowStart\"",
                new MapSqlParameterSource("semesterId", semesterId));
        for (String kind : List.of("REGISTRATION", "ADD_DROP")) {
            for (Map<String, Object> round : rounds) {
                if (kind.equals(String.valueOf(round.get("kind")))) {
                    return round;
                }
            }
        }
        return null;
    }

    /** Cap from the enrollment's original round — fallback when no round window is open at reopen. */
    private int effectiveLimit(String studentId, Object roundId) {
        Integer configured = null;
        if (roundId != null) {
            try {
                configured = jdbc.queryForObject(
                        "SELECT \"creditLimit\" FROM academic.\"RegistrationRound\" WHERE \"id\" = :id",
                        new MapSqlParameterSource("id", String.valueOf(roundId)), Integer.class);
            } catch (EmptyResultDataAccessException exception) {
                configured = null;
            }
        }
        Map<String, Object> round = new LinkedHashMap<>();
        round.put("id", roundId == null ? null : String.valueOf(roundId));
        round.put("credit_limit", configured);
        return creditLimitApplications.effectiveLimit(studentId, round);
    }

    private void requireScheduledRoomsFitCapacity(String sectionId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("sectionId", sectionId);
        int capacity = jdbc.queryForObject(
                "SELECT \"capacity\" FROM " + SECTION + " WHERE \"id\" = :sectionId",
                parameters, Integer.class);
        List<String> classroomIds = jdbc.queryForList(
                "SELECT DISTINCT \"classroomId\" FROM " + SECTION_SCHEDULE + " WHERE \"sectionId\" = :sectionId",
                parameters, String.class);
        for (String classroomId : classroomIds) {
            requireRoomFitsCapacity(classroomId, capacity);
        }
    }

    /**
     * A section's schedule must not double-book its rooms or its lecturer
     * within the same semester. Silent overlaps used to produce timetable
     * collisions the registrar then had to resolve by hand.
     */
    private void assertNoSectionConflicts(String sectionId) {
        // Compare individual slots: MIN(start)/MAX(end) would occupy the free
        // gap between two sessions and incorrectly reject another class there.
        // Cancelled sections hold no rooms or lecturer time: neither the
        // checked section nor its peers participate while CANCELLED, which is
        // also what lets a slot be reclaimed and the cancellation be reversed.
        String overlappingSlots = " FROM " + SECTION_SCHEDULE + " ss"
                + " JOIN " + SECTION + " me ON me.\"id\" = ss.\"sectionId\""
                + " JOIN " + SECTION_SCHEDULE + " ss2 ON ss2.\"dayOfWeek\" = ss.\"dayOfWeek\""
                + " AND ss2.\"startTime\" < ss.\"endTime\" AND ss2.\"endTime\" > ss.\"startTime\""
                + " JOIN " + SECTION + " s2 ON s2.\"id\" = ss2.\"sectionId\""
                + " WHERE ss.\"sectionId\" = :sectionId AND ss2.\"sectionId\" <> ss.\"sectionId\""
                + " AND me.\"status\" <> 'CANCELLED' AND s2.\"status\" <> 'CANCELLED'"
                + " AND s2.\"semesterId\" = me.\"semesterId\"";
        Long roomClash = jdbc.queryForObject(
                "SELECT COUNT(*)" + overlappingSlots
                        + " AND ss2.\"classroomId\" = ss.\"classroomId\"",
                new MapSqlParameterSource("sectionId", sectionId),
                Long.class);
        if (roomClash != null && roomClash > 0) {
            throw problem(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                    "The room is already booked for an overlapping slot in this semester");
        }
        Long lecturerClash = jdbc.queryForObject(
                "SELECT COUNT(*)" + overlappingSlots
                        + " AND me.\"lecturerId\" IS NOT NULL"
                        + " AND s2.\"lecturerId\" = me.\"lecturerId\"",
                new MapSqlParameterSource("sectionId", sectionId),
                Long.class);
        if (lecturerClash != null && lecturerClash > 0) {
            throw problem(HttpStatus.CONFLICT, "SCHEDULE_CONFLICT",
                    "The lecturer already teaches an overlapping slot in this semester");
        }
    }

    /**
     * Provided start/end pairs must be ordered; missing values keep server
     * defaults. Parsing is lenient — an {@code <input type=date>} submits
     * bare calendar dates ("2026-01-01") while the API also accepts full ISO
     * instants — but a value that parses as neither is rejected instead of
     * silently skipping the ordering check (the old swallow made this
     * validation dead code).
     */
    private void requireOrderedRange(Object start, Object end) {
        Instant startAt = start == null ? null : parseTimestamp(start, "startDate");
        Instant endAt = end == null ? null : parseTimestamp(end, "endDate");
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "endDate must be after startDate");
        }
    }

    /**
     * Semester calendar invariants shared by create and update: ordered main
     * range, ordered registration window, and no overlap with a sibling
     * semester of the same academic year. Null values skip their check (create
     * falls back to server defaults; update keeps the stored value).
     */
    private void validateSemesterDates(
            String selfId,
            String academicYearId,
            Object start,
            Object end,
            Object registrationStart,
            Object registrationEnd) {
        Instant startAt = start == null ? null : parseTimestamp(start, "startDate");
        Instant endAt = end == null ? null : parseTimestamp(end, "endDate");
        Instant regStartAt = registrationStart == null ? null : parseTimestamp(registrationStart, "registrationStart");
        Instant regEndAt = registrationEnd == null ? null : parseTimestamp(registrationEnd, "registrationEnd");
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "endDate must be after startDate");
        }
        if (regStartAt != null && regEndAt != null && !regEndAt.isAfter(regStartAt)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE",
                    "registrationEnd must be after registrationStart");
        }
        if (academicYearId == null || academicYearId.isBlank() || startAt == null || endAt == null) {
            return;
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("academicYearId", academicYearId)
                .addValue("startDate", java.time.OffsetDateTime.ofInstant(startAt, java.time.ZoneOffset.UTC))
                .addValue("endDate", java.time.OffsetDateTime.ofInstant(endAt, java.time.ZoneOffset.UTC));
        String selfFilter = "";
        if (selfId != null) {
            selfFilter = " AND \"id\" <> :selfId";
            parameters.addValue("selfId", selfId);
        }
        Long overlapping = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + SEMESTER + " WHERE \"academicYearId\" = :academicYearId"
                        + " AND \"startDate\" < :endDate AND \"endDate\" > :startDate" + selfFilter,
                parameters, Long.class);
        if (overlapping != null && overlapping > 0) {
            throw problem(HttpStatus.CONFLICT, "SEMESTER_DATES_OVERLAP",
                    "Another semester of the same academic year already covers part of this date range");
        }
    }

    /** Update variant: merges the submitted date fields with the stored row before validating. */
    private void validateSemesterUpdate(String id, Map<String, Object> input) {
        boolean touchesDates = input.containsKey("startDate") || input.containsKey("endDate")
                || input.containsKey("registrationStart") || input.containsKey("registrationEnd")
                || input.containsKey("academicYearId");
        if (!touchesDates) {
            return;
        }
        Map<String, Object> current = requireRow(SEMESTER, id);
        validateSemesterDates(
                id,
                input.containsKey("academicYearId") ? text(input, "academicYearId", null)
                        : (String) current.get("academicYearId"),
                mergedUpdateValue(input, current, "startDate"),
                mergedUpdateValue(input, current, "endDate"),
                mergedUpdateValue(input, current, "registrationStart"),
                mergedUpdateValue(input, current, "registrationEnd"));
    }

    private static Object mergedUpdateValue(Map<String, Object> input, Map<String, Object> current, String field) {
        return input.containsKey(field) ? input.get(field) : current.get(field);
    }

    /** course credits stay inside the published 1..30 bound on create and update alike. */
    private static void requireCreditsInRange(int credits) {
        if (credits <= 0 || credits > 30) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_CREDITS", "credits must be between 1 and 30");
        }
    }

    private static void requireSectionCapacity(int capacity) {
        if (capacity < 1) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_CAPACITY", "Section capacity must be at least 1");
        }
    }

    private static void requireSemesterStatus(String status) {
        if (status == null || !SEMESTER_STATUSES.contains(status)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_SEMESTER_STATUS",
                    "status must be one of " + String.join(", ", SEMESTER_STATUSES));
        }
    }

    private static void requireSectionStatus(String status) {
        if (status == null || !SECTION_STATUSES.contains(status)) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_SECTION_STATUS",
                    "status must be one of " + String.join(", ", SECTION_STATUSES));
        }
    }

    /** Bogus foreign keys used to surface as an opaque 409 from the database constraint. */
    private void requireCatalogReference(String table, String field, String id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.NOT_FOUND, "REFERENCE_NOT_FOUND",
                    field + " '" + id + "' does not exist");
        }
    }

    /** A section cannot be scheduled into a classroom that has been deactivated. */
    private void requireActiveClassroom(String classroomId) {
        Boolean active;
        try {
            active = jdbc.queryForObject(
                    "SELECT \"isActive\" FROM " + CLASSROOM + " WHERE \"id\" = :id",
                    new MapSqlParameterSource("id", classroomId), Boolean.class);
        } catch (EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "REFERENCE_NOT_FOUND",
                    "classroomId '" + classroomId + "' does not exist");
        }
        if (!Boolean.TRUE.equals(active)) {
            throw problem(HttpStatus.CONFLICT, "CLASSROOM_INACTIVE",
                    "Classroom '" + classroomId + "' is inactive");
        }
    }

    /** Rooms assigned while the section was live must still be usable when it reopens. */
    private void requireScheduledRoomsActive(String sectionId) {
        List<String> classroomIds = jdbc.queryForList(
                "SELECT DISTINCT \"classroomId\" FROM " + SECTION_SCHEDULE + " WHERE \"sectionId\" = :sectionId",
                new MapSqlParameterSource("sectionId", sectionId), String.class);
        for (String classroomId : classroomIds) {
            requireActiveClassroom(classroomId);
        }
    }

    /** A section must not promise more seats than the room it meets in. */
    private void requireRoomFitsCapacity(String classroomId, int capacity) {
        Integer roomCapacity = jdbc.queryForObject(
                "SELECT \"capacity\" FROM " + CLASSROOM + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", classroomId),
                Integer.class);
        if (roomCapacity != null && capacity > roomCapacity) {
            throw problem(HttpStatus.BAD_REQUEST, "CAPACITY_EXCEEDS_ROOM",
                    "capacity exceeds the room capacity (" + roomCapacity + ") of classroom '" + classroomId + "'");
        }
    }

    private void validateSectionUpdate(String id, Map<String, Object> input) {
        if (input.containsKey("status")) {
            requireSectionStatus(text(input, "status", null));
        }
        boolean touchesCapacity = input.containsKey("capacity");
        boolean touchesRoom = input.containsKey("classroomId");
        if (touchesCapacity || touchesRoom) {
            Map<String, Object> current = requireRow(SECTION, id);
            int capacity = touchesCapacity
                    ? number(input, "capacity", 0)
                    : ((Number) current.get("capacity")).intValue();
            requireSectionCapacity(capacity);
            if (touchesCapacity) {
                // A section cannot shrink below the students already seated: the
                // count would silently contradict every capacity read.
                Object enrolledCount = current.get("enrolledCount");
                int enrolled = enrolledCount instanceof Number seats ? seats.intValue() : 0;
                if (capacity < enrolled) {
                    throw problem(HttpStatus.CONFLICT, "CAPACITY_BELOW_ENROLLED",
                            "Capacity cannot go below the number of enrolled students (" + enrolled + ")");
                }
            }
            String classroomId = touchesRoom
                    ? text(input, "classroomId", null)
                    : (String) current.get("classroomId");
            if (classroomId != null) {
                requireCatalogReference(CLASSROOM, "classroomId", classroomId);
                if (touchesRoom) {
                    requireActiveClassroom(classroomId);
                }
                requireRoomFitsCapacity(classroomId, capacity);
            }
        }
        String courseId = text(input, "courseId", null);
        if (input.containsKey("courseId") && courseId != null) {
            requireCatalogReference(COURSE, "courseId", courseId);
        }
        String semesterId = text(input, "semesterId", null);
        if (input.containsKey("semesterId") && semesterId != null) {
            requireCatalogReference(SEMESTER, "semesterId", semesterId);
        }
        String lecturerId = text(input, "lecturerId", null);
        if (input.containsKey("lecturerId") && lecturerId != null) {
            requireCatalogReference(LECTURER, "lecturerId", lecturerId);
        }
    }

    private Map<String, Object> requireRow(String table, String id) {
        try {
            return get(table, id);
        } catch (org.springframework.dao.EmptyResultDataAccessException exception) {
            throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
        }
    }

    /** Lenient ISO instant or calendar-date parse; anything else is rejected. */
    private static Instant parseTimestamp(Object value, String field) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof java.time.LocalDateTime localDateTime) {
            return localDateTime.toInstant(java.time.ZoneOffset.UTC);
        }
        String text = String.valueOf(value).trim();
        try {
            return Instant.parse(text);
        } catch (java.time.format.DateTimeParseException ignored) {
            try {
                return java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            } catch (java.time.format.DateTimeParseException alsoIgnored) {
                throw problem(HttpStatus.BAD_REQUEST, "INVALID_DATE_VALUE",
                        field + " '" + text + "' is not a valid ISO date");
            }
        }
    }

    /**
     * A reopen re-adds enrollment capacity to a semester, so it refuses only
     * a semester that already reached a terminal state — DRAFT, IN_PROGRESS
     * and every other non-terminal lifecycle state still allow it subject to
     * the conflict checks. Cancel is not gated: it only removes seats and
     * stays legal in any semester state.
     */
    private void requireNonTerminalSemesterForReopen(Object semesterId) {
        // The comparison runs in Java so it is dialect-neutral: PostgreSQL
        // TRIM strips only plain spaces, so a stored terminal value padded
        // with a tab, a newline or a Unicode space must normalize before it
        // is tested — a missing row refuses the same as before.
        List<String> statuses = jdbc.queryForList(
                "SELECT \"status\" FROM " + SEMESTER + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", semesterId), String.class);
        String status = statuses.isEmpty() ? null : statuses.get(0);
        if (status == null || SemesterStatuses.TERMINAL.contains(SemesterStatuses.normalize(status))) {
            throw problem(HttpStatus.CONFLICT, "SEMESTER_CLOSED", "Semester is closed for registration");
        }
    }

    private void requireExists(String table, String id) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                Long.class);
        if (count == null || count == 0) {
            throw problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found");
        }
    }

    private static String requiredScheduleText(Map<?, ?> schedule, String name) {
        String value = optionalScheduleText(schedule, name, null);
        if (value == null) {
            throw new IllegalArgumentException(name + " is required for each schedule");
        }
        return value;
    }

    /**
     * Parse a schedule clock value, accepting only the zero-padded HH:mm form
     * the VARCHAR columns are compared as everywhere downstream.
     */
    private static java.time.LocalTime parseScheduleTime(String value, String name) {
        if (!value.matches("\\d{2}:\\d{2}")) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE",
                    name + " must use zero-padded HH:mm (e.g. 07:00)");
        }
        try {
            return java.time.LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw problem(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE",
                    name + " must be a valid HH:mm time (e.g. 07:00)");
        }
    }

    private static String optionalScheduleText(Map<?, ?> schedule, String name, String fallback) {
        Object value = schedule.get(name);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static int requiredScheduleNumber(Map<?, ?> schedule, String name) {
        Object value = schedule.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException(name + " is required for each schedule");
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString());
    }

    private static MapSqlParameterSource params(Map<String, Object> input, String id) {
        return new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", input.get("name"))
                .addValue("nameEn", input.get("nameEn"))
                .addValue("nameVi", input.get("nameVi"))
                .addValue("code", text(input, "code", id))
                .addValue("description", input.get("description"))
                .addValue("descriptionEn", input.get("descriptionEn"))
                .addValue("descriptionVi", input.get("descriptionVi"));
    }

    private static String id(Map<String, Object> input) {
        Object value = input.get("id");
        return value == null || value.toString().isBlank() ? UUID.randomUUID().toString() : value.toString().trim();
    }

    private static String required(Map<String, Object> input, String name) {
        String value = text(input, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    private static String text(Map<String, Object> input, String name, String fallback) {
        Object value = input.get(name);
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static int number(Map<String, Object> input, String name, int fallback) {
        Object value = input.get(name);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }

    private static DomainException problem(HttpStatus status, String code, String message) {
        return new DomainException(status, code, message);
    }
}
