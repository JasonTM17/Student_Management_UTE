package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:enrollment_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicEnrollmentMutationPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void studentEnrollThenDropPersistsRegisteredThenDroppedState() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "enroll-open-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-open"))
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn();

        JsonNode body = objectMapper.readTree(enrolled.getResponse().getContentAsString());
        String enrollmentId = body.get("id").asText();
        assertThat(enrollmentId).isNotBlank();

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("ENROLLED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                        Integer.class,
                        "section-open"))
                .isEqualTo(1);

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("ENROLLED"));

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "drop-open-1"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("DROPPED");
        assertThat(jdbc.queryForObject(
                        "SELECT \"enrolledCount\" FROM \"academic\".\"Section\" WHERE \"id\" = ?",
                        Integer.class,
                        "section-open"))
                .isEqualTo(0);

        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(enrollmentId))
                .andExpect(jsonPath("$[0].status").value("DROPPED"));
    }

    @Test
    void enrollPersistsRegistrationSlipWithSha256Header() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "slip-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"));

        MvcResult slip = mvc.perform(get("/api/v1/me/registration/slip")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Content-SHA256"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        String sha256 = slip.getResponse().getHeader("X-Content-SHA256");
        assertThat(sha256).isNotBlank().matches("[0-9a-f]{64}");
        assertThat(slip.getResponse().getContentAsByteArray().length).isGreaterThan(0);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ? AND TRIM(\"sha256\") = ?",
                        Integer.class,
                        "student-1",
                        sha256))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT TRIM(\"slipSha256\") FROM \"academic\".\"RegistrationIdempotency\" WHERE \"ownerId\" = ? AND \"idempotencyKey\" = ?",
                        String.class,
                        "student-1",
                        "slip-enroll-1"))
                .isEqualTo(sha256);

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"RegistrationSlip\" WHERE \"studentId\" = ? AND TRIM(\"sha256\") = ?",
                        Integer.class,
                        "student-1",
                        sha256))
                .isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyIsRejectedOnCanonicalAndAlias() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSecondActiveRow() throws Exception {
        MvcResult first = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String enrollmentId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();
        MvcResult second = mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(enrollmentId);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Enrollment\" WHERE \"studentId\" = ? AND \"status\" = 'ENROLLED'",
                        Integer.class,
                        "student-1"))
                .isEqualTo(1);
    }

    @Test
    void overlappingScheduleIsConflictOnBothUrls() throws Exception {
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "overlap-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/enrollments/enroll")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "overlap-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-overlap\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"));
    }

    @Test
    void creditCapExceededOnCanonicalRoute() throws Exception {
        jdbc.update("UPDATE \"academic\".\"RegistrationRound\" SET \"creditLimit\" = 2 WHERE \"kind\" = 'REGISTRATION'");
        mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "credit-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CREDIT_CAP_EXCEEDED"));
    }

    @Test
    void adminDropCompletesIdempotencySoRetriesReplayInsteadOfConflicting() throws Exception {
        MvcResult enrolled = mvc.perform(post("/api/v1/me/enrollments")
                        .with(studentJwt("student-user-1", "student-1"))
                        .header("Idempotency-Key", "admin-drop-enroll-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectionId\":\"section-open\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENROLLED"))
                .andReturn();
        String enrollmentId = objectMapper.readTree(enrolled.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(adminJwt())
                        .header("Idempotency-Key", "admin-drop-1"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                        "SELECT \"state\" FROM \"academic\".\"RegistrationIdempotency\" WHERE \"ownerId\" = ? AND \"idempotencyKey\" = ?",
                        String.class,
                        enrollmentId,
                        "admin-drop-1"))
                .isEqualTo("COMPLETED");

        mvc.perform(post("/api/v1/enrollments/" + enrollmentId + "/drop")
                        .with(adminJwt())
                        .header("Idempotency-Key", "admin-drop-1"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                        String.class,
                        enrollmentId))
                .isEqualTo("DROPPED");
    }

    private static RequestPostProcessor studentJwt(String subject, String studentId) {
        return jwt().jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "studentId" VARCHAR(120) NOT NULL,
                    "curriculumId" VARCHAR(120) NOT NULL,
                    "year" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "admissionDate" TIMESTAMP NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120) NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "employeeId" VARCHAR(120) NOT NULL,
                    "isActive" BOOLEAN NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."AcademicYear" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "year" INTEGER NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "isCurrent" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(120) NOT NULL,
                    "nameEn" VARCHAR(120),
                    "nameVi" VARCHAR(120),
                    "type" VARCHAR(40) NOT NULL,
                    "academicYearId" VARCHAR(120) NOT NULL,
                    "startDate" TIMESTAMP NOT NULL,
                    "endDate" TIMESTAMP NOT NULL,
                    "registrationStart" TIMESTAMP,
                    "registrationEnd" TIMESTAMP,
                    "addDropStart" TIMESTAMP,
                    "addDropEnd" TIMESTAMP,
                    "status" VARCHAR(40) NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "credits" INTEGER NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120),
                    "isActive" BOOLEAN NOT NULL,
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Classroom" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "building" VARCHAR(120) NOT NULL,
                    "roomNumber" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."SectionSchedule" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "classroomId" VARCHAR(120) NOT NULL,
                    "dayOfWeek" INTEGER NOT NULL,
                    "startTime" VARCHAR(20) NOT NULL,
                    "endTime" VARCHAR(20) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "enrolledAt" TIMESTAMP NOT NULL,
                    "droppedAt" TIMESTAMP,
                    "gradeStatus" VARCHAR(40) NOT NULL,
                    "finalGrade" DECIMAL(5, 2),
                    "letterGrade" VARCHAR(10),
                    "courseId" VARCHAR(120) NOT NULL,
                    "roundId" VARCHAR(120),
                    "creditsSnapshot" INTEGER NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationRound" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(180) NOT NULL,
                    "kind" VARCHAR(40) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "windowStart" TIMESTAMP NOT NULL,
                    "windowEnd" TIMESTAMP NOT NULL,
                    "creditLimit" INTEGER NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationRoundCohort" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "roundId" VARCHAR(120) NOT NULL,
                    "curriculumId" VARCHAR(120),
                    "year" INTEGER,
                    "departmentId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."CourseRequirement" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "courseId" VARCHAR(120) NOT NULL,
                    "requiredCourseId" VARCHAR(120) NOT NULL,
                    "kind" VARCHAR(16) NOT NULL,
                    "minLetterGrade" VARCHAR(16)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationIdempotency" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "ownerId" VARCHAR(120) NOT NULL,
                    "idempotencyKey" VARCHAR(200) NOT NULL,
                    "requestHash" CHAR(64) NOT NULL,
                    "state" VARCHAR(40) NOT NULL,
                    "enrollmentId" VARCHAR(120),
                    "responseBody" TEXT,
                    "slipSha256" CHAR(64),
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE ("ownerId", "idempotencyKey")
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."EnrollmentEvent" (
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
                CREATE TABLE IF NOT EXISTS "academic"."RegistrationSlip" (
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
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"RegistrationSlip\"");
        jdbc.update("DELETE FROM \"academic\".\"EnrollmentEvent\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationIdempotency\"");
        jdbc.update("DELETE FROM \"academic\".\"CourseRequirement\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRoundCohort\"");
        jdbc.update("DELETE FROM \"academic\".\"RegistrationRound\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"auth\".\"User\"");
    }

    private void insertFixture() {
        LocalDateTime now = localDateTime(BASE_TIME);
        jdbc.update(
                "INSERT INTO \"auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                "student-user-1",
                "student1@campuscore.edu",
                "An",
                "Student");
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "student-1",
                "student-user-1",
                "S001",
                "curriculum-1",
                2,
                "ACTIVE",
                now,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ay-2026",
                2026,
                now,
                localDateTime(BASE_TIME.plusSeconds(31_536_000)),
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\","
                        + " \"endDate\", \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\","
                        + " \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "semester-1",
                "Fall 2026",
                "Fall 2026",
                "Hoc ky Thu 2026",
                "FALL",
                "ay-2026",
                now,
                localDateTime(BASE_TIME.plusSeconds(10_368_000)),
                null,
                null,
                null,
                null,
                "ACTIVE",
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-open",
                "SE402",
                "Web Application Development",
                "Web Application Development",
                "Phat trien ung dung web",
                null,
                null,
                null,
                3,
                "department-1",
                "semester-1",
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "section-open",
                "01",
                "course-open",
                "semester-1",
                null,
                null,
                35,
                0,
                "OPEN",
                0);
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "course-overlap",
                "SE403",
                "Systems",
                "Systems",
                "He thong",
                null,
                null,
                null,
                3,
                "department-1",
                "semester-1",
                true,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\", \"version\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "section-overlap",
                "01",
                "course-overlap",
                "semester-1",
                null,
                null,
                35,
                0,
                "OPEN",
                0);
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-open",
                "section-open",
                "room-1",
                2,
                "07:00",
                "09:30");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "sched-overlap",
                "section-overlap",
                "room-1",
                2,
                "08:00",
                "10:00");
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-open",
                "semester-1",
                "Registration",
                "REGISTRATION",
                "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")),
                28,
                0,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"RegistrationRound\""
                        + " (\"id\", \"semesterId\", \"name\", \"kind\", \"status\", \"windowStart\", \"windowEnd\","
                        + " \"creditLimit\", \"version\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "round-add-drop",
                "semester-1",
                "Add drop",
                "ADD_DROP",
                "OPEN",
                localDateTime(Instant.parse("2020-01-01T00:00:00Z")),
                localDateTime(Instant.parse("2030-01-01T00:00:00Z")),
                28,
                0,
                now,
                now);
        jdbc.update(
                "INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                "room-1",
                "A",
                "101");
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
