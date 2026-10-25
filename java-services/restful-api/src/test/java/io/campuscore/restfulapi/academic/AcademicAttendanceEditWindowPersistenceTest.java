package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Edit-window boundary matrix. Campus days are computed in the same
 * Asia/Bangkok civil calendar the write service uses so the boundary day is
 * exercised exactly: day N (today minus editWindowDays) is editable, day N-1
 * is admin-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "attendance.edit-window-days=30",
        "spring.datasource.url=jdbc:h2:mem:attendance_edit_window;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicAttendanceEditWindowPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");
    private static final ZoneId CAMPUS_ZONE = ZoneId.of("Asia/Bangkok");

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
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.prepare(jdbc);
        insertUser("admin-user-1", "admin@example.test", "Admin", "Fixture");
        insertUser("sa-user-1", "sa@example.test", "Super", "Admin");
        insertUser("lecturer-user-1", "lecturer1@example.test", "Lecturer", "One");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "admin-user-1", "ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "sa-user-1", "SUPER_ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-1", "lecturer-1");
    }

    @Test
    void lecturerEditsTheOldestInWindowBoundaryDay() throws Exception {
        LocalDate boundaryDay = campusToday().minusDays(30);

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(boundaryDay, "PRESENT"))
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void lecturerCannotWriteBeforeTheWindowBoundary() throws Exception {
        LocalDate closedDay = campusToday().minusDays(31);

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(closedDay, "ABSENT"))
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_EDIT_WINDOW_CLOSED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1'",
                Integer.class)).isZero();
    }

    @Test
    void lecturerResetOnAPreWindowDateIsAlsoBlocked() throws Exception {
        LocalDate closedDay = campusToday().minusDays(31);

        // RESET deletes ride the same write path: the window guard sits ahead
        // of the per-record loop, so a delete on a closed day must not run.
        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(closedDay, "RESET"))
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_EDIT_WINDOW_CLOSED"));
    }

    @Test
    void adminAndSuperAdminBypassTheWindow() throws Exception {
        LocalDate closedDay = campusToday().minusDays(90);

        mvc.perform(put("/api/v1/attendance/section/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(closedDay, "PRESENT"))
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        mvc.perform(put("/api/v1/attendance/section/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(closedDay, "EXCUSED"))
                        .with(superAdminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void futureCampusDaysStayRejectedAheadOfTheWindow() throws Exception {
        LocalDate campusTomorrow = campusToday().plusDays(1);

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(campusTomorrow, "PRESENT"))
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_DATE"));
    }

    private static LocalDate campusToday() {
        return Instant.now().atZone(CAMPUS_ZONE).toLocalDate();
    }

    private static String payload(LocalDate date, String status) {
        return "{\"date\":\"" + date + "\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"" + status + "\"}]}";
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user-1")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor superAdminJwt() {
        return jwt().jwt(token -> token
                        .subject("sa-user-1")
                        .claim("roles", List.of("SUPER_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
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
                CREATE TABLE IF NOT EXISTS "academic"."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "code" VARCHAR(40) NOT NULL,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "credits" INTEGER NOT NULL,
                    "departmentId" VARCHAR(120) NOT NULL,
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
                    "enrolledAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."Attendance" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "date" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "notes" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"Attendance\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "Linh", "Nguyen");
        insertStudent("student-1", "student-user-1", "S001");
        insertCourse("course-1", "CS101", "Intro to Programming");
        insertSection("section-1", "A", "course-1", "semester-1", "lecturer-1");
        insertEnrollment("enroll-1", "student-1", "section-1", "ENROLLED");
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update(
                "INSERT INTO \"campuscore_auth\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, 'curr-1', 2026, 'ENROLLED', ?, ?, ?)",
                id, userId, studentNumber, Timestamp.from(BASE_TIME), Timestamp.from(BASE_TIME), Timestamp.from(BASE_TIME));
    }

    private void insertCourse(String id, String code, String name) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\" (\"id\", \"code\", \"name\", \"credits\", \"departmentId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, 3, 'dept-1', TRUE, ?, ?)",
                id, code, name, Timestamp.from(BASE_TIME), Timestamp.from(BASE_TIME));
    }

    private void insertSection(String id, String sectionNumber, String courseId, String semesterId, String lecturerId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\" (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, 40, 2, 'OPEN')",
                id, sectionNumber, courseId, semesterId, lecturerId);
    }

    private void insertEnrollment(String id, String studentId, String sectionId, String status) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\")"
                        + " VALUES (?, ?, ?, 'semester-1', ?, ?)",
                id, studentId, sectionId, status, Timestamp.from(BASE_TIME));
    }
}
