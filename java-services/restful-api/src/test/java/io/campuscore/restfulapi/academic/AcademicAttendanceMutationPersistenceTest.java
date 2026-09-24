package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:attendance_mutation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AcademicAttendanceMutationPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-21T00:00:00Z");

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
    void lecturerCanRecordAttendanceForOwnSection() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": "Có mặt đúng giờ"},
                        {"studentId": "student-2", "status": "ABSENT", "notes": "Vắng không phép"}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"))
                .andExpect(jsonPath("$.updatedCount").value(2));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"status\" = 'PRESENT'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void recordAttendanceIsIdempotentOnReplay() throws Exception {
        String initialPayload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null},
                        {"studentId": "student-2", "status": "ABSENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialPayload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk());

        String updatePayload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null},
                        {"studentId": "student-2", "status": "EXCUSED", "notes": "Có đơn xin phép"}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        Integer totalOnDate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1'",
                Integer.class);
        assertThat(totalOnDate).isEqualTo(2);

        String student2Status = jdbc.queryForObject(
                "SELECT \"status\" FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"studentId\" = 'student-2'",
                String.class);
        assertThat(student2Status).isEqualTo("EXCUSED");
    }

    @Test
    void foreignLecturerIsForbiddenWithSectionForbiddenCode() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-2")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SECTION_FORBIDDEN"));
    }

    @Test
    void studentCannotRecordAttendance() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentNotEnrolledReturnsBadRequest() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-3", "status": "PRESENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"));
    }

    @Test
    void nonExistentSectionReturnsNotFound() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": []
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/non-existent-section")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECTION_NOT_FOUND"));
    }

    @Test
    void futureDateReturnsBadRequest() throws Exception {
        LocalDate farFuture = LocalDate.now().plusDays(10);
        String payload = String.format("""
                {
                    "date": "%s",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null}
                    ]
                }
                """, farFuture);

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_DATE"));
    }

    @Test
    void adminCanRecordAttendanceForAnySection() throws Exception {
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/section/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));
    }

    private static RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user-1")
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

    private static RequestPostProcessor studentJwt(String userId, String studentId) {
        return jwt().jwt(token -> token
                        .subject(userId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
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
                    "date" TIMESTAMP NOT NULL,
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
        insertUser("student-user-2", "student2@campuscore.edu", "Minh", "Tran");
        insertUser("student-user-3", "student3@campuscore.edu", "An", "Le");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        insertStudent("student-3", "student-user-3", "S003");
        insertCourse("course-1", "CS101", "Intro to Programming");
        insertSection("section-1", "A", "course-1", "semester-1", "lecturer-1");
        insertSection("section-2", "B", "course-1", "semester-1", "lecturer-2");
        insertEnrollment("enroll-1", "student-1", "section-1", "ENROLLED");
        insertEnrollment("enroll-2", "student-2", "section-1", "ENROLLED");
        insertEnrollment("enroll-3", "student-3", "section-2", "ENROLLED");
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
