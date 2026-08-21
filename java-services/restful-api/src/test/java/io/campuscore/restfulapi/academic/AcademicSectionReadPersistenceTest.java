package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
        "migration.academic-section-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AcademicSectionReadPersistenceTest {

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
    void listSectionsPreservesLegacyEnvelopeFiltersOrderingAndHydration() throws Exception {
        mvc.perform(get("/api/v1/sections")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .queryParam("departmentId", "department-swe")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("section-2"))
                .andExpect(jsonPath("$.data[0].sectionId").value("section-2"))
                .andExpect(jsonPath("$.data[0].course.code").value("SE401"))
                .andExpect(jsonPath("$.data[0].course.department.code").value("SWE"))
                .andExpect(jsonPath("$.data[0].semester.nameEn").value("Fall 2026"))
                .andExpect(jsonPath("$.data[0].lecturer.user.email").value("lecturer@campuscore.edu"))
                .andExpect(jsonPath("$.data[0].classroom.roomNumber").value("B202"))
                .andExpect(jsonPath("$.data[0].schedules[0].roomNumber").value("B202"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(2));
    }

    @Test
    void lecturerScheduleKeepsLecturerSemesterScopeScheduleOrderAndActiveEnrollmentCount() throws Exception {
        mvc.perform(get("/api/v1/sections/my/schedule")
                        .queryParam("semesterId", "semester-1")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").value("section-1"))
                .andExpect(jsonPath("$[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$[0].enrolledCount").value(2))
                .andExpect(jsonPath("$[0].schedules[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$[0].schedules[0].startTime").value("08:00"))
                .andExpect(jsonPath("$[0].schedules[1].dayOfWeek").value(4))
                .andExpect(jsonPath("$[1].sectionId").value("section-2"))
                .andExpect(jsonPath("$[?(@.sectionId == 'section-other')]").isEmpty())
                .andExpect(jsonPath("$[?(@.sectionId == 'section-spring')]").isEmpty());
    }

    @Test
    void lecturerGradingCalculatesLegacyCountsAndPublishState() throws Exception {
        mvc.perform(get("/api/v1/sections/my/grading")
                        .queryParam("semesterId", "semester-1")
                        .with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sectionId").value("section-1"))
                .andExpect(jsonPath("$[0].enrolledCount").value(2))
                .andExpect(jsonPath("$[0].gradedCount").value(2))
                .andExpect(jsonPath("$[0].publishedCount").value(1))
                .andExpect(jsonPath("$[0].gradeStatus").value("ALL_GRADED"))
                .andExpect(jsonPath("$[0].canPublish").value(true))
                .andExpect(jsonPath("$[1].sectionId").value("section-2"))
                .andExpect(jsonPath("$[1].enrolledCount").value(0))
                .andExpect(jsonPath("$[1].gradeStatus").value("NONE"))
                .andExpect(jsonPath("$[1].canPublish").value(false));
    }

    @Test
    void detailAndSectionGradesPreserveLegacyShapeAndNotFoundEnvelope() throws Exception {
        mvc.perform(get("/api/v1/sections/section-1").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("section-1"))
                .andExpect(jsonPath("$.course.nameVi").value("Nhập môn lập trình"))
                .andExpect(jsonPath("$.course.department.nameVi").value("Khoa học máy tính"))
                .andExpect(jsonPath("$.lecturer.user.firstName").value("Lan"))
                .andExpect(jsonPath("$.schedules.length()").value(2));

        mvc.perform(get("/api/v1/sections/section-1/grades").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sectionId").value("section-1"))
                .andExpect(jsonPath("$.lecturerName").value("Lan Lecturer"))
                .andExpect(jsonPath("$.enrollments.length()").value(2))
                .andExpect(jsonPath("$.enrollments[0].studentName").value("An Student"))
                .andExpect(jsonPath("$.enrollments[0].finalGrade").value(91.50))
                .andExpect(jsonPath("$.enrollments[1].studentCode").value("S002"))
                .andExpect(jsonPath("$.enrollments[1].gradeStatus").value("DRAFT"));

        mvc.perform(get("/api/v1/sections/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    @Test
    void sectionReadBoundaryFailsClosedForAnonymousRolesMissingClaimsAndBadQueries() throws Exception {
        mvc.perform(get("/api/v1/sections/my/schedule"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/sections/my/schedule").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/sections/my/grading")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-user-1")
                                .claim("roles", List.of("LECTURER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/sections")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/sections")
                        .queryParam("unexpected", "value")
                        .with(adminJwt()))
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
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"Department\"");
        jdbc.update("DELETE FROM \"academic\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "An", "Student");
        insertUser("student-user-2", "student2@campuscore.edu", "Binh", "Student");
        insertUser("student-user-3", "student3@campuscore.edu", "Cuong", "Student");
        insertUser("lecturer-user-1", "lecturer@campuscore.edu", "Lan", "Lecturer");
        insertUser("lecturer-user-2", "lecturer2@campuscore.edu", "Minh", "Lecturer");
        insertDepartment("department-cs", "CS", "Computer Science", "Computer Science", "Khoa học máy tính");
        insertDepartment("department-swe", "SWE", "Software Engineering", "Software Engineering", "Kỹ thuật phần mềm");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        insertStudent("student-3", "student-user-3", "S003");
        insertLecturer("lecturer-1", "lecturer-user-1", "department-cs", "L001");
        insertLecturer("lecturer-2", "lecturer-user-2", "department-swe", "L002");
        insertAcademicYear();
        insertSemester("semester-1", "Fall 2026", "Fall 2026", "Học kỳ Thu 2026", BASE_TIME);
        insertSemester("semester-2", "Spring 2027", "Spring 2027", "Học kỳ Xuân 2027", BASE_TIME.plusSeconds(15_552_000));
        insertCourse("course-1", "CS101", "Intro to Programming", "Intro to Programming", "Nhập môn lập trình", 4, "department-cs");
        insertCourse("course-2", "SE401", "Web Development", "Web Development", "Lập trình web", 3, "department-swe");
        insertClassroom("room-1", "A", "A101");
        insertClassroom("room-2", "B", "B202");
        insertSection("section-1", "A", "course-1", "semester-1", "lecturer-1", "room-1", 30, 9);
        insertSection("section-2", "B", "course-2", "semester-1", "lecturer-1", "room-2", 25, 7);
        insertSection("section-spring", "C", "course-2", "semester-2", "lecturer-1", "room-2", 25, 4);
        insertSection("section-other", "D", "course-1", "semester-1", "lecturer-2", "room-1", 30, 3);
        insertSchedule("schedule-1b", "section-1", "room-1", 4, "13:00", "15:00");
        insertSchedule("schedule-1a", "section-1", "room-1", 2, "08:00", "10:00");
        insertSchedule("schedule-2", "section-2", "room-2", 3, "09:00", "11:00");
        insertSchedule("schedule-spring", "section-spring", "room-2", 5, "09:00", "11:00");
        insertEnrollment("enrollment-1", "student-1", "section-1", "CONFIRMED", "PUBLISHED", "91.50", "A");
        insertEnrollment("enrollment-2", "student-2", "section-1", "COMPLETED", "DRAFT", "82.00", "B");
        insertEnrollment("enrollment-dropped", "student-3", "section-1", "DROPPED", "DRAFT", null, null);
        insertEnrollment("enrollment-pending", "student-3", "section-2", "PENDING", "DRAFT", null, null);
        insertEnrollment("enrollment-other", "student-1", "section-other", "CONFIRMED", "DRAFT", null, null);
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update("INSERT INTO \"academic\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertDepartment(String id, String code, String name, String nameEn, String nameVi) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Department\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"code\", \"description\", \"descriptionEn\", \"descriptionVi\", \"facultyId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, nameEn, nameVi, code, null, null, null, "faculty-1", true);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\", \"admissionDate\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, userId, studentNumber, "curriculum-1", 3, "ACTIVE", BASE_TIME, BASE_TIME, BASE_TIME);
    }

    private void insertLecturer(String id, String userId, String departmentId, String employeeId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                id, userId, departmentId, employeeId, true);
    }

    private void insertAcademicYear() {
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ay-2026", 2026, BASE_TIME, BASE_TIME.plusSeconds(31_536_000), true, BASE_TIME, BASE_TIME);
    }

    private void insertSemester(String id, String name, String nameEn, String nameVi, Instant startDate) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Semester\""
                        + " (\"id\", \"name\", \"nameEn\", \"nameVi\", \"type\", \"academicYearId\", \"startDate\", \"endDate\","
                        + " \"registrationStart\", \"registrationEnd\", \"addDropStart\", \"addDropEnd\", \"status\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, nameEn, nameVi, "FALL", "ay-2026", startDate, startDate.plusSeconds(10_368_000),
                null, null, null, null, "ACTIVE", BASE_TIME, BASE_TIME);
    }

    private void insertCourse(
            String id,
            String code,
            String name,
            String nameEn,
            String nameVi,
            int credits,
            String departmentId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\", \"descriptionVi\","
                        + " \"credits\", \"departmentId\", \"semesterId\", \"isActive\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, code, name, nameEn, nameVi, null, null, null, credits, departmentId, "semester-1", true, BASE_TIME, BASE_TIME);
    }

    private void insertClassroom(String id, String building, String roomNumber) {
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                id, building, roomNumber);
    }

    private void insertSection(
            String id,
            String sectionNumber,
            String courseId,
            String semesterId,
            String lecturerId,
            String classroomId,
            int capacity,
            int enrolledCount) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\", \"capacity\", \"enrolledCount\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, sectionNumber, courseId, semesterId, lecturerId, classroomId, capacity, enrolledCount, "OPEN");
    }

    private void insertSchedule(String id, String sectionId, String classroomId, int dayOfWeek, String startTime, String endTime) {
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\""
                        + " (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id, sectionId, classroomId, dayOfWeek, startTime, endTime);
    }

    private void insertEnrollment(
            String id,
            String studentId,
            String sectionId,
            String status,
            String gradeStatus,
            String finalGrade,
            String letterGrade) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\", \"enrolledAt\", \"droppedAt\","
                        + " \"gradeStatus\", \"finalGrade\", \"letterGrade\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                studentId,
                sectionId,
                sectionId.equals("section-spring") ? "semester-2" : "semester-1",
                status,
                BASE_TIME,
                null,
                gradeStatus,
                finalGrade == null ? null : new BigDecimal(finalGrade),
                letterGrade,
                BASE_TIME,
                BASE_TIME);
    }
}
