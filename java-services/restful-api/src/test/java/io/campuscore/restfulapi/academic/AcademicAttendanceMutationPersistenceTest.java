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
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.prepare(jdbc);
        insertUser("admin-user-1", "admin@example.test", "Admin", "Fixture");
        insertUser("lecturer-user-1", "lecturer1@example.test", "Lecturer", "One");
        insertUser("lecturer-user-2", "lecturer2@example.test", "Lecturer", "Two");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "admin-user-1", "ADMIN");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.role(jdbc, "student-user-1", "STUDENT");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-1", "lecturer-1");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user-2", "lecturer-2");
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
    void lateHourInstantReadsAndResetsTheSameCampusDayWithoutTouchingThePreviousDay() throws Exception {
        for (String[] entry : new String[][] {{"2026-08-25", "PRESENT"}, {"2026-08-25T23:00:00Z", "ABSENT"}}) {
            mvc.perform(put("/api/v1/attendance/sections/section-1")
                            .with(lecturerJwt("lecturer-1")).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"date\":\"" + entry[0] + "\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"" + entry[1] + "\"}]}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-25T23:00:00Z")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ABSENT"));
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-26")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ABSENT"));
        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .with(lecturerJwt("lecturer-1")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-25T23:00:00Z\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"RESET\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-26")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-25")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("PRESENT"));
    }

    @Test
    void duplicateStudentIdInOnePayloadIsDedupedLastWriteWins() throws Exception {
        // A payload naming the same student twice used to hit the unique key
        // and surface as a 500. The contract is JSON-object semantics: the
        // last entry wins, exactly one row persists.
        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT"},
                        {"studentId": "student-2", "status": "PRESENT"},
                        {"studentId": "student-1", "status": "ABSENT", "notes": "ra về giữa giờ"}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(2));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1'",
                        Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "SELECT \"status\" FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"studentId\" = 'student-1'",
                        String.class))
                .isEqualTo("ABSENT");
    }

    @Test
    void isoInstantAndDateOnlyReferToOneCampusDayIncludingLegacyUtcKeys() throws Exception {
        mvc.perform(put("/api/v1/attendance/sections/section-1").with(lecturerJwt("lecturer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-08-25T07:00:00Z\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"PRESENT\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-25")
                .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // Simulate a pre-fix non-midnight persisted timestamp without a data migration.
        jdbc.update("UPDATE \"academic\".\"Attendance\" SET \"date\"=? WHERE \"sectionId\"='section-1'",
                java.time.OffsetDateTime.parse("2026-08-25T18:30:00Z"));
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-25T23:00:00Z")
                .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // This instant belongs to Aug25 in the campus timezone, so its legacy key remains readable.
        mvc.perform(get("/api/v1/attendance/section/section-1").param("date", "2026-08-25T07:00:00Z")
                .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/v1/attendance/sections/section-1").with(lecturerJwt("lecturer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-08-25\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"ABSENT\"}]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT \"date\" FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1'", java.time.OffsetDateTime.class).toInstant())
                .isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
        jdbc.update("UPDATE \"academic\".\"Attendance\" SET \"date\"=? WHERE \"sectionId\"='section-1'",
                java.time.OffsetDateTime.parse("2026-08-25T18:30:00Z"));
        mvc.perform(put("/api/v1/attendance/sections/section-1").with(lecturerJwt("lecturer-1"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-08-25\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"RESET\"}]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1'", Integer.class))
                .isZero();
    }

    @Test
    void resetEntryDeletesTheSavedRecord() throws Exception {
        String initialPayload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "PRESENT"},
                        {"studentId": "student-2", "status": "ABSENT"}
                    ]
                }
                """;
        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initialPayload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk());

        // Round-2 sweep write-1: a RESET entry must DELETE the saved row for
        // that student instead of being ignored (the old contract kept the
        // stale record while the UI reported the reset as saved).
        String resetPayload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-1", "status": "RESET"},
                        {"studentId": "student-2", "status": "ABSENT"}
                    ]
                }
                """;
        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetPayload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1));

        Integer gone = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"studentId\" = 'student-1'",
                Integer.class);
        assertThat(gone).isZero();

        Integer kept = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"studentId\" = 'student-2' AND \"status\" = 'ABSENT'",
                Integer.class);
        assertThat(kept).isEqualTo(1);
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
    void withdrawnStudentCannotBeMarked() throws Exception {
        // After an approved withdrawal the enrollment is WITHDRAWN (letter W):
        // no longer an attending seat, so the write path must refuse marks
        // exactly like a dropped or never-enrolled student.
        jdbc.update("UPDATE \"academic\".\"Enrollment\" SET \"status\" = 'WITHDRAWN'"
                + " WHERE \"id\" = 'enroll-2'");

        String payload = """
                {
                    "date": "2026-08-25",
                    "records": [
                        {"studentId": "student-2", "status": "ABSENT", "notes": null}
                    ]
                }
                """;

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STUDENT_NOT_ENROLLED"));

        Integer written = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\" = 'section-1' AND \"studentId\" = 'student-2'",
                Integer.class);
        assertThat(written).isZero();
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
    void campusTodayIsAllowedButCampusTomorrowIsRejected() throws Exception {
        LocalDate campusToday = Instant.now()
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDate();
        LocalDate campusTomorrow = campusToday.plusDays(1);

        // The boundary is the normalized campus day, not now()+24h: a request
        // for today must pass while tomorrow must fail at every wall-clock hour.
        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + campusToday + "\",\"records\":[{\"studentId\":\"student-1\",\"status\":\"PRESENT\"}]}")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/attendance/sections/section-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + campusTomorrow + "\",\"records\":[{\"studentId\":\"student-2\",\"status\":\"PRESENT\"}]}")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ATTENDANCE_DATE"));

        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1' AND \"studentId\"='student-1'",
                        Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM \"academic\".\"Attendance\" WHERE \"sectionId\"='section-1' AND \"studentId\"='student-2'",
                        Integer.class))
                .isZero();
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
                        .subject("lecturer-2".equals(lecturerId) ? "lecturer-user-2" : "lecturer-user-1")
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", lecturerId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private static RequestPostProcessor studentJwt(String userId, String studentId) {
        return jwt().jwt(token -> token
                        .subject(userId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("student", java.util.Map.of("year", 2026))
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
