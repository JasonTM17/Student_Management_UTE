package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * H2 persistence coverage for the approved course-withdrawal (grade W)
 * workflow: submit, duplicate-pending, cancel, approve, reject, window/semester
 * gates, ownership, role matrix, seat release, slip/event rows, audit and
 * notifications all run against the real SQL of
 * {@code academic."WithdrawalApplication"} (migration-h2 V145).
 */
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:withdrawal_application;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class WithdrawalApplicationPersistenceTest {

    private static final String PREFIX = "wdw-";
    private static final String STUDENT_USER_1 = "wdw-user-1";
    private static final String STUDENT_USER_2 = "wdw-user-2";
    private static final String ADMIN_USER = "wdw-admin-1";
    private static final String LECTURER_USER = "wdw-lecturer-1";
    private static final String STUDENT_1 = "wdw-student-1";
    private static final String STUDENT_2 = "wdw-student-2";
    private static final String SEM_OPEN = "wdw-sem-open";
    private static final String SEM_LATE = "wdw-sem-late";
    private static final String SEM_CLOSED = "wdw-sem-closed";
    private static final String SEM_SUMMER = "wdw-sem-summer";
    private static final String COURSE = "wdw-course-1";
    private static final String SEC_1 = "wdw-sec-1";
    private static final String SEC_LATE = "wdw-sec-late";
    private static final String SEC_CLOSED = "wdw-sec-closed";
    private static final String SEC_SUMMER = "wdw-sec-summer";
    private static final String SEC_B = "wdw-sec-b";
    private static final String ENR_1 = "wdw-enr-1";
    private static final String ENR_2 = "wdw-enr-2";
    private static final String ENR_LATE = "wdw-enr-late";
    private static final String ENR_CLOSED = "wdw-enr-closed";
    private static final String ENR_SUMMER = "wdw-enr-summer";
    private static final String ENR_DROPPED = "wdw-enr-dropped";
    private static final String REASON = "I need to withdraw from this course for medical reasons.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void fixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_audit\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("CREATE TABLE IF NOT EXISTS campuscore_audit.\"AdminAudit\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"actorId\" VARCHAR(120), \"actorLabel\" VARCHAR(240),"
                + " \"action\" VARCHAR(48) NOT NULL, \"entityType\" VARCHAR(48) NOT NULL, \"entityId\" VARCHAR(120),"
                + " \"summary\" VARCHAR(500), \"beforeState\" TEXT, \"afterState\" TEXT,"
                + " \"createdAt\" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(16) NOT NULL,
                    link VARCHAR(240),
                    announcement_id VARCHAR(120), source_key VARCHAR(160),
                    is_read BOOLEAN DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120),
                    "studentId" VARCHAR(64)
                )
                """);
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"userId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"studentId\" VARCHAR(64)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(120),
                    "type" VARCHAR(40),
                    "status" VARCHAR(40),
                    "startDate" TIMESTAMP,
                    "endDate" TIMESTAMP
                )
                """);
        jdbc.execute("ALTER TABLE academic.\"Semester\" ADD COLUMN IF NOT EXISTS \"type\" VARCHAR(40)");
        jdbc.execute("ALTER TABLE academic.\"Semester\" ADD COLUMN IF NOT EXISTS \"startDate\" TIMESTAMP");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40),
                    "name" VARCHAR(240),
                    "credits" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "courseId" VARCHAR(120),
                    "semesterId" VARCHAR(120),
                    "sectionNumber" VARCHAR(40),
                    "capacity" INT DEFAULT 50,
                    "enrolledCount" INT DEFAULT 0,
                    "status" VARCHAR(40) DEFAULT 'OPEN',
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"courseId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"semesterId\" VARCHAR(120)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"sectionNumber\" VARCHAR(40)");
        jdbc.execute("ALTER TABLE academic.\"Section\" ADD COLUMN IF NOT EXISTS \"status\" VARCHAR(40)");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "enrolledAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "droppedAt" TIMESTAMP,
                    "gradeStatus" VARCHAR(40) NOT NULL DEFAULT 'NOT_GRADED',
                    "finalGrade" DECIMAL(5, 2),
                    "letterGrade" VARCHAR(10),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."EnrollmentEvent" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "enrollmentId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "action" VARCHAR(16) NOT NULL,
                    "actorId" VARCHAR(120) NOT NULL,
                    "requestHash" CHAR(64),
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."RegistrationSlip" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120) NOT NULL,
                    "sha256" CHAR(64) NOT NULL,
                    "byteSize" INTEGER NOT NULL,
                    "payload" BYTEA NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

        deleteFixtureRows();
        seedFixture();
    }

    private void deleteFixtureRows() {
        jdbc.update("DELETE FROM academic.\"WithdrawalApplication\" WHERE \"studentId\" LIKE 'wdw-%'"
                + " OR \"enrollmentId\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"EnrollmentEvent\" WHERE \"enrollmentId\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"RegistrationSlip\" WHERE \"studentId\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"id\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"Section\" WHERE \"id\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"Course\" WHERE \"id\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"Semester\" WHERE \"id\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'wdw-%'");
        jdbc.update("DELETE FROM notifications.notification WHERE user_id LIKE 'wdw-%'");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'wdw-%'");
    }

    private void seedFixture() {
        AccountAuthorityFixture.ensureSchema(jdbc);
        AccountAuthorityFixture.ensureUser(jdbc, STUDENT_USER_1);
        AccountAuthorityFixture.ensureUser(jdbc, STUDENT_USER_2);
        AccountAuthorityFixture.student(jdbc, STUDENT_USER_1, STUDENT_1, 3);
        AccountAuthorityFixture.student(jdbc, STUDENT_USER_2, STUDENT_2, 3);

        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                STUDENT_1, STUDENT_USER_1, "WDW-STU-1");
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"userId\", \"studentId\") VALUES (?, ?, ?)",
                STUDENT_2, STUDENT_USER_2, "WDW-STU-2");

        insertSemester(SEM_OPEN, "WDW Open Term", "FIRST", "OPEN", Instant.now().minus(3, ChronoUnit.DAYS));
        insertSemester(SEM_LATE, "WDW Late Term", "FIRST", "OPEN", Instant.now().minus(30, ChronoUnit.DAYS));
        insertSemester(SEM_CLOSED, "WDW Closed Term", "FIRST", "CLOSED", Instant.now().minus(3, ChronoUnit.DAYS));
        insertSemester(SEM_SUMMER, "WDW Summer Term", "SUMMER", "OPEN", Instant.now().minus(3, ChronoUnit.DAYS));

        jdbc.update("INSERT INTO academic.\"Course\" (\"id\", \"code\", \"name\", \"credits\") VALUES (?, ?, ?, ?)",
                COURSE, "WDW-101", "WDW Course", 3);

        insertSection(SEC_1, SEM_OPEN, 2);
        insertSection(SEC_LATE, SEM_LATE, 1);
        insertSection(SEC_CLOSED, SEM_CLOSED, 1);
        insertSection(SEC_SUMMER, SEM_SUMMER, 1);
        insertSection(SEC_B, SEM_OPEN, 1);

        insertEnrollment(ENR_1, STUDENT_1, SEC_1, SEM_OPEN, "ENROLLED");
        insertEnrollment(ENR_2, STUDENT_2, SEC_1, SEM_OPEN, "ENROLLED");
        insertEnrollment(ENR_LATE, STUDENT_1, SEC_LATE, SEM_LATE, "ENROLLED");
        insertEnrollment(ENR_CLOSED, STUDENT_1, SEC_CLOSED, SEM_CLOSED, "ENROLLED");
        insertEnrollment(ENR_SUMMER, STUDENT_1, SEC_SUMMER, SEM_SUMMER, "ENROLLED");
        insertEnrollment(ENR_DROPPED, STUDENT_1, SEC_B, SEM_OPEN, "DROPPED");

        jdbc.update("INSERT INTO academic.\"RegistrationSlip\""
                        + " (\"id\", \"studentId\", \"semesterId\", \"roundId\", \"sha256\", \"byteSize\", \"payload\")"
                        + " VALUES ('wdw-slip-1', ?, ?, 'wdw-round-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 10, ?)",
                STUDENT_1, SEM_OPEN, new byte[] {1, 2, 3});
    }

    private void insertSemester(String id, String name, String type, String status, Instant start) {
        jdbc.update("INSERT INTO academic.\"Semester\""
                        + " (\"id\", \"name\", \"type\", \"status\", \"startDate\", \"endDate\") VALUES (?, ?, ?, ?, ?, ?)",
                id, name, type, status, Timestamp.from(start), Timestamp.from(start.plus(90, ChronoUnit.DAYS)));
    }

    private void insertSection(String id, String semesterId, int enrolled) {
        jdbc.update("INSERT INTO academic.\"Section\""
                        + " (\"id\", \"courseId\", \"semesterId\", \"sectionNumber\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, 'S1', 30, ?, 'OPEN')",
                id, COURSE, semesterId, enrolled);
    }

    private void insertEnrollment(String id, String studentId, String sectionId, String semesterId, String status) {
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\") VALUES (?, ?, ?, ?, ?)",
                id, studentId, sectionId, semesterId, status);
    }

    private RequestPostProcessor student1() {
        return CurrentAccountJwt.fixtureJwt(jdbc, STUDENT_USER_1, "STUDENT");
    }

    private RequestPostProcessor student2() {
        return CurrentAccountJwt.fixtureJwt(jdbc, STUDENT_USER_2, "STUDENT");
    }

    private RequestPostProcessor admin() {
        return CurrentAccountJwt.fixtureJwt(jdbc, ADMIN_USER, "ADMIN");
    }

    private RequestPostProcessor superAdmin() {
        return CurrentAccountJwt.fixtureJwt(jdbc, "wdw-sadmin-1", "SUPER_ADMIN");
    }

    private RequestPostProcessor lecturer() {
        return CurrentAccountJwt.fixtureJwt(jdbc, LECTURER_USER, "LECTURER");
    }

    private String submit(String enrollmentId) throws Exception {
        return submitAs(enrollmentId, student1());
    }

    private String submitAs(String enrollmentId, RequestPostProcessor who) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + enrollmentId + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String review(String applicationId, String decision) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + applicationId + "/review")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"" + decision + "\",\"note\":\"Reviewed by academic affairs\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private final tools.jackson.databind.ObjectMapper om = new tools.jackson.databind.ObjectMapper();

    private String json(String body, String field) throws Exception {
        return om.readTree(body).get(field).asText();
    }

    // ---------- window probe ----------

    @Test
    void windowReportsOpenForRegularSemesterInsideWindow() throws Exception {
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(student1())
                        .param("semesterId", SEM_OPEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterId").value(SEM_OPEN))
                .andExpect(jsonPath("$.regularSemester").value(true))
                .andExpect(jsonPath("$.semesterOpen").value(true))
                .andExpect(jsonPath("$.open").value(true))
                .andExpect(jsonPath("$.windowStart").isString())
                .andExpect(jsonPath("$.windowEnd").isString());
    }

    @Test
    void windowFlagsLateClosedAndNonRegularSemesters() throws Exception {
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(student1()).param("semesterId", SEM_LATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularSemester").value(true))
                .andExpect(jsonPath("$.semesterOpen").value(true))
                .andExpect(jsonPath("$.open").value(false));
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(student1()).param("semesterId", SEM_SUMMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularSemester").value(false))
                .andExpect(jsonPath("$.open").value(false));
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(student1()).param("semesterId", SEM_CLOSED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semesterOpen").value(false))
                .andExpect(jsonPath("$.open").value(false));
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(student1()).param("semesterId", "wdw-sem-missing"))
                .andExpect(status().isNotFound());
    }

    // ---------- submit ----------

    @Test
    void submitPersistsPendingApplicationWithResponseFields() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_1 + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value(STUDENT_1))
                .andExpect(jsonPath("$.studentCode").value("WDW-STU-1"))
                .andExpect(jsonPath("$.enrollmentId").value(ENR_1))
                .andExpect(jsonPath("$.semesterId").value(SEM_OPEN))
                .andExpect(jsonPath("$.semesterName").value("WDW Open Term"))
                .andExpect(jsonPath("$.courseCode").value("WDW-101"))
                .andExpect(jsonPath("$.sectionNumber").value("S1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andReturn();
        String id = om.readTree(result.getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT \"status\", \"reason\" FROM academic.\"WithdrawalApplication\" WHERE \"id\" = ?", id);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("reason")).isEqualTo(REASON);

        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityType\" = 'WithdrawalApplication' AND \"entityId\" = ?"
                        + " AND \"action\" = 'WITHDRAWAL_SUBMITTED'",
                Integer.class, id);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void duplicatePendingSubmitIsConflict() throws Exception {
        submit(ENR_1);
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_1 + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_EXISTS"));
        Integer pending = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"WithdrawalApplication\""
                        + " WHERE \"enrollmentId\" = ? AND \"status\" = 'PENDING'",
                Integer.class, ENR_1);
        assertThat(pending).isEqualTo(1);
    }

    @Test
    void submitRejectsInvalidReasonAndUnknownEnrollment() throws Exception {
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_1 + "\",\"reason\":\"too short\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"wdw-enr-missing\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    }

    @Test
    void submitRejectsLateClosedSummerAndInactiveEnrollments() throws Exception {
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_LATE + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_WINDOW_CLOSED"));
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_CLOSED + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEMESTER_CLOSED"));
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_SUMMER + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_SEMESTER_INELIGIBLE"));
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_DROPPED + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_ACTIVE"));
    }

    @Test
    void submitRejectsForeignEnrollment() throws Exception {
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_2 + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_FORBIDDEN"));
    }

    // ---------- cancel ----------

    @Test
    void studentCancelsPendingApplication() throws Exception {
        String id = submit(ENR_1);
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications/" + id + "/cancel")
                        .with(student1()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT \"status\", \"reviewedBy\", \"reviewedAt\" FROM academic.\"WithdrawalApplication\""
                        + " WHERE \"id\" = ?", id);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(row.get("reviewedBy")).isNull();
        assertThat(row.get("reviewedAt")).isNull();

        mvc.perform(post("/api/v1/me/registration/withdrawal-applications/" + id + "/cancel")
                        .with(student1()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_NOT_PENDING"));
    }

    @Test
    void studentCannotCancelForeignApplication() throws Exception {
        String id = submit(ENR_1);
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications/" + id + "/cancel")
                        .with(student2()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_FORBIDDEN"));
    }

    // ---------- review ----------

    @Test
    void adminApproveMarksWithdrawnReleasesSeatAndWritesSideEffects() throws Exception {
        String id = submit(ENR_1);
        String body = review(id, "APPROVED");

        assertThat(json(body, "status")).isEqualTo("APPROVED");
        assertThat(json(body, "reviewedBy")).isEqualTo(ADMIN_USER);
        assertThat(om.readTree(body).get("reviewedAt").isTextual()).isTrue();

        Map<String, Object> enrollment = jdbc.queryForMap(
                "SELECT \"status\", \"letterGrade\", \"gradeStatus\", \"gradePublishedAt\""
                        + " FROM academic.\"Enrollment\" WHERE \"id\" = ?",
                ENR_1);
        assertThat(enrollment.get("status")).isEqualTo("WITHDRAWN");
        assertThat(enrollment.get("letterGrade")).isEqualTo("W");
        assertThat(enrollment.get("gradeStatus")).isEqualTo("PUBLISHED");
        assertThat(enrollment.get("gradePublishedAt")).isNotNull();

        Integer seats = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?", Integer.class, SEC_1);
        assertThat(seats).isEqualTo(1);

        Integer slips = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"RegistrationSlip\""
                        + " WHERE \"studentId\" = ? AND \"semesterId\" = ?",
                Integer.class, STUDENT_1, SEM_OPEN);
        assertThat(slips).isEqualTo(0);

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT \"action\", \"actorId\", \"studentId\", \"sectionId\" FROM academic.\"EnrollmentEvent\""
                        + " WHERE \"enrollmentId\" = ?", ENR_1);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("action")).isEqualTo("WITHDRAW");
        assertThat(events.get(0).get("actorId")).isEqualTo(ADMIN_USER);

        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityType\" = 'WithdrawalApplication' AND \"entityId\" = ?"
                        + " AND \"action\" = 'WITHDRAWAL_APPROVED'",
                Integer.class, id);
        assertThat(audits).isEqualTo(1);

        Integer notices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE user_id = ? AND type = 'SUCCESS'",
                Integer.class, STUDENT_USER_1);
        assertThat(notices).isEqualTo(1);
    }

    @Test
    void adminRejectKeepsEnrollmentActive() throws Exception {
        String id = submit(ENR_1);
        String body = review(id, "REJECTED");
        assertThat(json(body, "status")).isEqualTo("REJECTED");
        assertThat(json(body, "reviewerNote")).isEqualTo("Reviewed by academic affairs");

        Map<String, Object> enrollment = jdbc.queryForMap(
                "SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"id\" = ?", ENR_1);
        assertThat(enrollment.get("status")).isEqualTo("ENROLLED");

        Integer seats = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = ?", Integer.class, SEC_1);
        assertThat(seats).isEqualTo(2);

        Integer notices = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE user_id = ? AND type = 'WARNING'",
                Integer.class, STUDENT_USER_1);
        assertThat(notices).isEqualTo(1);
    }

    @Test
    void secondReviewOfSettledApplicationIsConflict() throws Exception {
        String id = submit(ENR_1);
        review(id, "APPROVED");
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"note\":\"late change\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_NOT_PENDING"));
    }

    @Test
    void reviewValidationRejectsBadDecisionAndLongNote() throws Exception {
        String id = submit(ENR_1);
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_DECISION_INVALID"));
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"note\":\"" + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/wdw-app-missing/review")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_NOT_FOUND"));
    }

    @Test
    void reviewAllowedAfterWindowCloses() throws Exception {
        String id = submit(ENR_1);
        jdbc.update("UPDATE academic.\"Semester\" SET \"startDate\" = ? WHERE \"id\" = ?",
                Timestamp.from(Instant.now().minus(60, ChronoUnit.DAYS)), SEM_OPEN);
        review(id, "APPROVED");
        String status = jdbc.queryForObject(
                "SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"id\" = ?", String.class, ENR_1);
        assertThat(status).isEqualTo("WITHDRAWN");
    }

    // ---------- lists ----------

    @Test
    void studentListScopesToCaller() throws Exception {
        submit(ENR_1);
        String body2 = mvc.perform(get("/api/v1/me/registration/withdrawal-applications")
                        .with(student1()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(body2).size()).isEqualTo(1);
        String body3 = mvc.perform(get("/api/v1/me/registration/withdrawal-applications")
                        .with(student2()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(body3).size()).isEqualTo(0);
    }

    @Test
    void adminListFiltersByStatus() throws Exception {
        String id = submit(ENR_1);
        submitAs(ENR_2, student2());
        review(id, "APPROVED");

        String pending = mvc.perform(get("/api/v1/admin/registration/withdrawal-applications")
                        .with(admin()).param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(pending).size()).isEqualTo(1);

        String all = mvc.perform(get("/api/v1/admin/registration/withdrawal-applications")
                        .with(admin()).param("status", "ALL"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(all).size()).isEqualTo(2);

        mvc.perform(get("/api/v1/admin/registration/withdrawal-applications")
                        .with(admin()).param("status", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_APPLICATION_STATUS_INVALID"));
    }

    // ---------- role matrix ----------

    @Test
    void studentEndpointsRequireStudentRole() throws Exception {
        mvc.perform(get("/api/v1/me/registration/withdrawal-window")
                        .with(admin()).param("semesterId", SEM_OPEN))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/registration/withdrawal-applications").with(lecturer()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(lecturer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_1 + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications/" + "wdw-app-x" + "/cancel")
                        .with(admin()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/me/registration/withdrawal-window").param("semesterId", SEM_OPEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRejectStudentAndLecturer() throws Exception {
        String id = submit(ENR_1);
        mvc.perform(get("/api/v1/admin/registration/withdrawal-applications").with(student1()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(student1())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(lecturer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/registration/withdrawal-applications/" + id + "/review")
                        .with(superAdmin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void studentWithoutProfileClaimGetsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/me/registration/withdrawal-applications")
                        .with(CurrentAccountJwt.fixtureJwt(jdbc, "wdw-noprofile-1", "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enrollmentId\":\"" + ENR_1 + "\",\"reason\":\"" + REASON + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_FOUND"));
    }
}
