package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Pins the published-grade lock: once a section's grades are published they
 * are the official record, so an API replay of the grade-save request must be
 * rejected with 409 GRADES_PUBLISHED_LOCKED instead of silently flipping the
 * row back to draft.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:grade_publication_lock;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class GradePublicationLockTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void replayingGradeSaveAfterPublicationIsRejectedAsLocked() throws Exception {
        String payload = """
                {"grades":[{"enrollmentId":"enrollment-1","processScore":8.0,"finalExamScore":9.0}]}
                """;

        mvc.perform(put("/api/v1/sections/section-1/grades")
                        .with(lecturerJwt("lecturer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Grades saved as draft"));

        mvc.perform(post("/api/v1/sections/section-1/grades/publish")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Grades published successfully"));

        // The published row is now the official record: the identical replay
        // must fail closed instead of rewriting history.
        mvc.perform(put("/api/v1/sections/section-1/grades")
                        .with(lecturerJwt("lecturer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADES_PUBLISHED_LOCKED"));
    }

    @Test
    void gradesAreRejectedForADroppedEnrollment() throws Exception {
        dropEnrollment1();

        mvc.perform(put("/api/v1/sections/section-1/grades")
                        .with(lecturerJwt("lecturer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grades":[{"enrollmentId":"enrollment-1","processScore":8.0,"finalExamScore":9.0}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_GRADEABLE"));

        // The dropped row keeps its empty grade record.
        assertEnrollmentGrade("enrollment-1", null, null, "DRAFT");
    }

    @Test
    void publishIgnoresDroppedRowsAndReportsNoPublishWhenOnlyTheyAreGraded() throws Exception {
        // A dropped enrollment whose components were recorded before the drop:
        // publish must not resurrect it as COMPLETED.
        dropEnrollment1();
        insertCompleteComponents("enrollment-1");

        mvc.perform(post("/api/v1/sections/section-1/grades/publish")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRADES_EMPTY"));

        assertEnrollmentStatus("enrollment-1", "DROPPED", "DRAFT");
    }

    @Test
    void publishCompletesOnlyTheGradeableEnrollments() throws Exception {
        insertEnrollment("enrollment-2-dropped", "DROPPED");
        insertCompleteComponents("enrollment-2-dropped");

        mvc.perform(put("/api/v1/sections/section-1/grades")
                        .with(lecturerJwt("lecturer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"grades":[{"enrollmentId":"enrollment-1","processScore":8.0,"finalExamScore":9.0}]}
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/sections/section-1/grades/publish")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Grades published successfully"));

        assertEnrollmentStatus("enrollment-1", "COMPLETED", "PUBLISHED");
        // The dropped row was fully graded, yet publish must leave it alone.
        assertEnrollmentStatus("enrollment-2-dropped", "DROPPED", "DRAFT");
    }

    private void dropEnrollment1() {
        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"status\" = 'DROPPED',"
                        + " \"droppedAt\" = ? WHERE \"id\" = 'enrollment-1'",
                timestamp(BASE_TIME));
    }

    private void insertEnrollment(String id, String status) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"droppedAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, 'student-1', 'section-1', 'semester-1', ?, ?, NULL, 'DRAFT', NULL, NULL, ?, ?)",
                id, status, timestamp(BASE_TIME), timestamp(BASE_TIME), timestamp(BASE_TIME));
    }

    /** Plants PROCESS + FINAL component scores directly (as an earlier save would have). */
    private void insertCompleteComponents(String enrollmentId) {
        jdbc.update(
                "MERGE INTO \"academic\".\"GradeItem\" (\"id\", \"sectionId\", \"name\", \"type\", \"maxScore\", \"weight\", \"gradedAt\")"
                        + " KEY (\"id\") VALUES (?, 'section-1', 'ĐQT', 'PROCESS', 10, 50, ?)",
                enrollmentId + "-process-50", timestamp(BASE_TIME));
        jdbc.update(
                "MERGE INTO \"academic\".\"GradeItem\" (\"id\", \"sectionId\", \"name\", \"type\", \"maxScore\", \"weight\", \"gradedAt\")"
                        + " KEY (\"id\") VALUES (?, 'section-1', 'ĐKTHP', 'FINAL', 10, 50, ?)",
                enrollmentId + "-final-50", timestamp(BASE_TIME));
        jdbc.update(
                "INSERT INTO \"academic\".\"StudentGrade\" (\"id\", \"enrollmentId\", \"gradeItemId\", \"score\") VALUES (?, ?, ?, 8.0)",
                enrollmentId + "-sg-p", enrollmentId, enrollmentId + "-process-50");
        jdbc.update(
                "INSERT INTO \"academic\".\"StudentGrade\" (\"id\", \"enrollmentId\", \"gradeItemId\", \"score\") VALUES (?, ?, ?, 9.0)",
                enrollmentId + "-sg-f", enrollmentId, enrollmentId + "-final-50");
    }

    private void assertEnrollmentStatus(String enrollmentId, String expectedStatus, String expectedGradeStatus) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT \"status\", \"gradeStatus\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                enrollmentId);
        org.assertj.core.api.Assertions.assertThat(row)
                .containsEntry("status", expectedStatus)
                .containsEntry("gradeStatus", expectedGradeStatus);
    }

    private void assertEnrollmentGrade(String enrollmentId, Object finalGrade, Object letterGrade, String gradeStatus) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT \"finalGrade\", \"letterGrade\", \"gradeStatus\" FROM \"academic\".\"Enrollment\" WHERE \"id\" = ?",
                enrollmentId);
        org.assertj.core.api.Assertions.assertThat(row)
                .containsEntry("finalGrade", finalGrade)
                .containsEntry("letterGrade", letterGrade)
                .containsEntry("gradeStatus", gradeStatus);
    }

    private static RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                        .subject("lecturer-user-1")
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(200) NOT NULL,
                    "firstName" VARCHAR(120) NOT NULL,
                    "lastName" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "code" VARCHAR(40) NOT NULL,
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "facultyId" VARCHAR(120),
                    "isActive" BOOLEAN
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
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
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
                CREATE TABLE IF NOT EXISTS "academic"."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionNumber" VARCHAR(40) NOT NULL,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "lecturerId" VARCHAR(120),
                    "classroomId" VARCHAR(120),
                    "capacity" INTEGER NOT NULL,
                    "enrolledCount" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL
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
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."GradeItem" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(180) NOT NULL,
                    "type" VARCHAR(60) NOT NULL,
                    "maxScore" DECIMAL(5, 2) NOT NULL,
                    "weight" DECIMAL(5, 2) NOT NULL,
                    "gradedAt" TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."StudentGrade" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "enrollmentId" VARCHAR(120) NOT NULL,
                    "gradeItemId" VARCHAR(120) NOT NULL,
                    "score" DECIMAL(5, 2)
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"StudentGrade\"");
        jdbc.update("DELETE FROM \"academic\".\"GradeItem\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "An", "Student");
        insertUser("lecturer-user-1", "lecturer@campuscore.edu", "Lan", "Lecturer");
        jdbc.update(
                "INSERT INTO \"academic\".\"Department\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\", \"descriptionEn\", \"descriptionVi\", \"facultyId\", \"isActive\")"
                        + " VALUES ('department-cs', 'CS', 'Computer Science', 'Computer Science', 'Khoa học máy tính', NULL, NULL, NULL, 'faculty-1', TRUE)");
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('student-1', 'student-user-1', 'S001', 'curriculum-1', 3, 'ACTIVE', ?, ?, ?)",
                timestamp(BASE_TIME), timestamp(BASE_TIME), timestamp(BASE_TIME));
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\""
                        + " (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES ('lecturer-1', 'lecturer-user-1', 'department-cs', 'L001', TRUE)");
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('ay-2026', 2026, ?, ?, TRUE, ?, ?)",
                timestamp(BASE_TIME), timestamp(BASE_TIME.plusSeconds(31_536_000)), timestamp(BASE_TIME), timestamp(BASE_TIME));
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\", \"endDate\","
                        + " \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\", \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('semester-1', 'Fall 2026', 'Fall 2026', 'Học kỳ Thu 2026', 'FALL', 'ay-2026', ?, ?, NULL, NULL, NULL, NULL, 'ACTIVE', ?, ?)",
                timestamp(BASE_TIME), timestamp(BASE_TIME.plusSeconds(10_368_000)), timestamp(BASE_TIME), timestamp(BASE_TIME));
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\", \"descriptionVi\","
                        + " \"credits\", \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('course-1', 'CS101', 'Intro to Programming', 'Intro to Programming', 'Nhập môn lập trình', NULL, NULL, NULL,"
                        + " 4, 'department-cs', 'semester-1', TRUE, ?, ?)",
                timestamp(BASE_TIME), timestamp(BASE_TIME));
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES ('section-1', 'A', 'course-1', 'semester-1', 'lecturer-1', NULL, 30, 1, 'OPEN')");
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"droppedAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"createdAt\", \"updatedAt\")"
                        + " VALUES ('enrollment-1', 'student-1', 'section-1', 'semester-1', 'ENROLLED', ?, NULL, 'DRAFT', NULL, NULL, ?, ?)",
                timestamp(BASE_TIME), timestamp(BASE_TIME), timestamp(BASE_TIME));
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private static Object timestamp(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
