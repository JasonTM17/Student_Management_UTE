package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
        "migration.academic-waitlist-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AcademicWaitlistReadPersistenceTest {

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
    void studentSelfWaitlistReturnsOnlyActiveCurrentStudentEntriesWithHydratedSection() throws Exception {
        mvc.perform(get("/api/v1/waitlist/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("waitlist-my-new"))
                .andExpect(jsonPath("$[0].studentId").value("student-1"))
                .andExpect(jsonPath("$[0].section.course.code").value("SE401"))
                .andExpect(jsonPath("$[0].section.course.department.code").value("SWE"))
                .andExpect(jsonPath("$[0].section.semester.nameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].section.schedules[0].classroom.roomNumber").value("B202"))
                .andExpect(jsonPath("$[1].id").value("waitlist-my-old"));
    }

    @Test
    void adminListPreservesLegacyEnvelopeFiltersAndPositionOrdering() throws Exception {
        mvc.perform(get("/api/v1/waitlist")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .queryParam("sectionId", "section-1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("waitlist-section-first"))
                .andExpect(jsonPath("$.data[0].student.studentId").value("S002"))
                .andExpect(jsonPath("$.data[0].section.course.code").value("CS101"))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(3));
    }

    @Test
    void sectionAndDetailReadsPreserveLegacyActiveFilterAndNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/waitlist/section/section-1").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("waitlist-section-first"))
                .andExpect(jsonPath("$[0].student.user.email").value("student2@campuscore.edu"))
                .andExpect(jsonPath("$[1].id").value("waitlist-my-old"));

        mvc.perform(get("/api/v1/waitlist/waitlist-cancelled").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("waitlist-cancelled"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(get("/api/v1/waitlist/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    @Test
    void waitlistReadBoundaryFailsClosedForAnonymousRolesMissingClaimsAndBadQueries() throws Exception {
        mvc.perform(get("/api/v1/waitlist/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/waitlist").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/waitlist/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-user-1")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/waitlist")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/waitlist/my")
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
                CREATE TABLE IF NOT EXISTS "academic"."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(200) NOT NULL,
                    "nameEn" VARCHAR(200),
                    "nameVi" VARCHAR(200),
                    "code" VARCHAR(40) NOT NULL,
                    "description" VARCHAR(1000),
                    "descriptionEn" VARCHAR(1000),
                    "descriptionVi" VARCHAR(1000),
                    "facultyId" VARCHAR(120) NOT NULL,
                    "isActive" BOOLEAN NOT NULL
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
                    "status" VARCHAR(40) NOT NULL
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
                CREATE TABLE IF NOT EXISTS "academic"."Waitlist" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "position" INTEGER NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "addedAt" TIMESTAMP NOT NULL,
                    "convertedAt" TIMESTAMP
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"Waitlist\"");
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "Linh", "Nguyen");
        insertUser("student-user-2", "student2@campuscore.edu", "Minh", "Tran");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        jdbc.update(
                "INSERT INTO \"academic\".\"Department\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\", \"descriptionEn\", \"descriptionVi\", \"facultyId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "department-1", "Software Engineering", "Software Engineering", "Kỹ thuật phần mềm", "SWE",
                null, null, null, "faculty-1", true);
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\", \"endDate\","
                        + " \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\", \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "semester-1", "Fall 2026", "Fall 2026", "Học kỳ Thu 2026", "FALL", "year-1",
                BASE_TIME, BASE_TIME.plusSeconds(90L * 24 * 60 * 60),
                BASE_TIME.minusSeconds(7L * 24 * 60 * 60), BASE_TIME.plusSeconds(7L * 24 * 60 * 60),
                BASE_TIME.minusSeconds(24 * 60 * 60), BASE_TIME.plusSeconds(14L * 24 * 60 * 60),
                "REGISTRATION_OPEN", BASE_TIME, BASE_TIME);
        insertCourse("course-1", "CS101", "Intro to Programming", "Intro to Programming", "Nhập môn lập trình");
        insertCourse("course-2", "SE401", "Web Development", "Web Development", "Lập trình web");
        insertClassroom("room-1", "A", "A101");
        insertClassroom("room-2", "B", "B202");
        insertSection("section-1", "A", "course-1", "room-1", 30, 30);
        insertSection("section-2", "B", "course-2", "room-2", 25, 25);
        insertSchedule("schedule-1", "section-1", "room-1", 2, "08:00", "10:00");
        insertSchedule("schedule-2", "section-2", "room-2", 4, "13:00", "15:00");
        insertWaitlist("waitlist-section-first", "student-2", "section-1", 1, "ACTIVE", BASE_TIME.plusSeconds(120));
        insertWaitlist("waitlist-my-old", "student-1", "section-1", 2, "ACTIVE", BASE_TIME);
        insertWaitlist("waitlist-cancelled", "student-1", "section-1", 3, "CANCELLED", BASE_TIME.plusSeconds(240));
        insertWaitlist("waitlist-my-new", "student-1", "section-2", 1, "ACTIVE", BASE_TIME.plusSeconds(360));
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

    private void insertClassroom(String id, String building, String roomNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                id, building, roomNumber);
    }

    private void insertSection(
            String id,
            String sectionNumber,
            String courseId,
            String classroomId,
            int capacity,
            int enrolledCount) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sectionNumber, courseId, "semester-1", "lecturer-1", classroomId, capacity, enrolledCount, "OPEN");
    }

    private void insertSchedule(
            String id,
            String sectionId,
            String classroomId,
            int dayOfWeek,
            String startTime,
            String endTime) {
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id, sectionId, classroomId, dayOfWeek, startTime, endTime);
    }

    private void insertWaitlist(
            String id,
            String studentId,
            String sectionId,
            int position,
            String status,
            Instant addedAt) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Waitlist\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"position\", \"status\", \"addedAt\", \"convertedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, studentId, sectionId, position, status, addedAt, null);
    }
}
