package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.academic-attendance-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AcademicAttendanceReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareReadOnlyFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"academic\"");
        createTables();
        clearTables();
        insertFixture();
    }

    @Test
    void studentSelfAttendanceAndSummaryPreserveSemesterAndSectionFilters() throws Exception {
        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("attendance-3"))
                .andExpect(jsonPath("$[0].section.course.code").value("CS101"))
                .andExpect(jsonPath("$[0].student.user.email").value("student1@campuscore.edu"))
                .andExpect(jsonPath("$[2].id").value("attendance-1"));

        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("sectionId", "section-spring")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("attendance-spring"));

        mvc.perform(get("/api/v1/attendance/my/summary")
                        .queryParam("semesterId", "semester-1")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sectionId").value("section-1"))
                .andExpect(jsonPath("$[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$[0].total").value(3))
                .andExpect(jsonPath("$[0].present").value(1))
                .andExpect(jsonPath("$[0].late").value(1))
                .andExpect(jsonPath("$[0].attendanceRate").value(33));
    }

    @Test
    void adminListPreservesLegacyEnvelopeFiltersDateAndDescendingDateOrdering() throws Exception {
        mvc.perform(get("/api/v1/attendance")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .queryParam("sectionId", "section-1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("attendance-3"))
                .andExpect(jsonPath("$.data[0].student.studentId").value("S001"))
                .andExpect(jsonPath("$.data[0].section.course.nameVi").value("Nhập môn lập trình"))
                .andExpect(jsonPath("$.meta.total").value(5))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        mvc.perform(get("/api/v1/attendance")
                        .queryParam("date", "2026-08-21")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].date").value("2026-08-21T00:00:00.000Z"));
    }

    @Test
    void lecturerAndSectionReadsPreserveOwnershipOrderingAndSectionSummaryFormula() throws Exception {
        mvc.perform(get("/api/v1/attendance/lecturer/my").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].id").value("attendance-3"))
                .andExpect(jsonPath("$[?(@.sectionId == 'section-other')]").isEmpty());

        mvc.perform(get("/api/v1/attendance/section/section-1")
                        .queryParam("date", "2026-08-21")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].student.user.firstName").value("An"))
                .andExpect(jsonPath("$[1].student.user.firstName").value("Linh"))
                .andExpect(jsonPath("$[2].student.user.firstName").value("Minh"));

        mvc.perform(get("/api/v1/attendance/section/section-1/summary").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"))
                .andExpect(jsonPath("$.totalSessions").value(3))
                .andExpect(jsonPath("$.totalRecords").value(5))
                .andExpect(jsonPath("$.present").value(2))
                .andExpect(jsonPath("$.absent").value(1))
                .andExpect(jsonPath("$.late").value(2))
                .andExpect(jsonPath("$.attendanceRate").value(80));
    }

    @Test
    void detailNotFoundAndReadBoundaryFailuresStayExplicit() throws Exception {
        mvc.perform(get("/api/v1/attendance/attendance-1").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("attendance-1"))
                .andExpect(jsonPath("$.notes").value("On time"))
                .andExpect(jsonPath("$.section.course.code").value("CS101"));

        mvc.perform(get("/api/v1/attendance/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/attendance/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/attendance")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/attendance/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-user-1")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/attendance")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/attendance/my")
                        .queryParam("unexpected", "value")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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

    private static RequestPostProcessor lecturerJwt(String lecturerId) {
        return jwt().jwt(token -> token
                        .subject("lecturer-user-1")
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."User" (
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
                CREATE TABLE IF NOT EXISTS "academic"."Attendance" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "date" TIMESTAMP NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "notes" VARCHAR(1000),
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"Attendance\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "Linh", "Nguyen");
        insertUser("student-user-2", "student2@campuscore.edu", "Minh", "Tran");
        insertUser("student-user-3", "student3@campuscore.edu", "An", "Le");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        insertStudent("student-3", "student-user-3", "S003");
        insertCourse("course-1", "CS101", "Intro to Programming", "Intro to Programming", "Nhập môn lập trình");
        insertCourse("course-2", "SE401", "Web Development", "Web Development", "Lập trình web");
        insertSection("section-1", "A", "course-1", "semester-1", "lecturer-1");
        insertSection("section-2", "B", "course-2", "semester-1", "lecturer-1");
        insertSection("section-spring", "C", "course-2", "semester-2", "lecturer-1");
        insertSection("section-other", "D", "course-1", "semester-1", "lecturer-2");
        insertAttendance("attendance-1", "student-1", "section-1", BASE_TIME, "PRESENT", "On time");
        insertAttendance("attendance-2", "student-1", "section-1", BASE_TIME.plusSeconds(24 * 60 * 60), "ABSENT", null);
        insertAttendance("attendance-3", "student-1", "section-1", BASE_TIME.plusSeconds(2L * 24 * 60 * 60), "LATE", "Traffic");
        insertAttendance("attendance-spring", "student-1", "section-spring", BASE_TIME.plusSeconds(3600), "EXCUSED", null);
        insertAttendance("attendance-section-minh", "student-2", "section-1", BASE_TIME, "PRESENT", null);
        insertAttendance("attendance-section-an", "student-3", "section-1", BASE_TIME, "LATE", null);
        insertAttendance("attendance-section2", "student-2", "section-2", BASE_TIME.plusSeconds(7200), "PRESENT", null);
        insertAttendance("attendance-other-lecturer", "student-2", "section-other", BASE_TIME, "PRESENT", null);
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update(
                "INSERT INTO \"academic\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, studentNumber, "curriculum-1", 3, "ACTIVE", BASE_TIME, BASE_TIME, BASE_TIME);
    }

    private void insertCourse(String id, String code, String name, String nameEn, String nameVi) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\", \"descriptionVi\","
                        + " \"credits\", \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, name, nameEn, nameVi, null, null, null, 4, "department-1", "semester-1", true, BASE_TIME, BASE_TIME);
    }

    private void insertSection(String id, String sectionNumber, String courseId, String semesterId, String lecturerId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, sectionNumber, courseId, semesterId, lecturerId, 30, 12, "OPEN");
    }

    private void insertAttendance(
            String id,
            String studentId,
            String sectionId,
            Instant date,
            String status,
            String notes) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Attendance\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"date\", \"status\", \"notes\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, studentId, sectionId, utcTimestamp(date), status, notes, utcTimestamp(BASE_TIME));
    }

    private static Timestamp utcTimestamp(Instant instant) {
        return Timestamp.valueOf(instant.atOffset(ZoneOffset.UTC).toLocalDateTime());
    }
}
