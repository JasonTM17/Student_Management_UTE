package io.campuscore.restfulapi.academic;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
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
        "migration.academic-enrollment-read.enabled=true",
        "spring.flyway.enabled=false"
})
class AcademicEnrollmentReadPersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

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
    void studentSelfReadReturnsOnlyCurrentStudentEnrollmentsWithHydratedSection() throws Exception {
        mvc.perform(get("/api/v1/enrollments/my").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("enrollment-2"))
                .andExpect(jsonPath("$[0].studentId").value("student-1"))
                .andExpect(jsonPath("$[0].section.course.code").value("SE401"))
                .andExpect(jsonPath("$[0].section.course.nameEn").value("Web Development"))
                .andExpect(jsonPath("$[0].section.semester.nameEn").value("Fall 2026"))
                .andExpect(jsonPath("$[0].section.lecturer.user.email").value("lecturer@campuscore.edu"))
                .andExpect(jsonPath("$[0].section.schedules[0].classroom.roomNumber").value("A101"))
                .andExpect(jsonPath("$[1].id").value("enrollment-1"));

        mvc.perform(get("/api/v1/enrollments/my")
                        .queryParam("semesterId", "semester-missing")
                        .with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminListReadPreservesLegacyEnvelopeFiltersAndOrdering() throws Exception {
        mvc.perform(get("/api/v1/enrollments")
                        .queryParam("page", "1")
                        .queryParam("limit", "1")
                        .queryParam("courseId", "course-1")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("enrollment-3"))
                .andExpect(jsonPath("$.data[0].student.studentId").value("S002"))
                .andExpect(jsonPath("$.meta.total").value(2))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/enrollments")
                        .queryParam("status", "COMPLETED")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("enrollment-1"));
    }

    @Test
    void detailReadAllowsAdminsAndCurrentStudentButHidesOtherStudentRecords() throws Exception {
        mvc.perform(get("/api/v1/enrollments/enrollment-3").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("enrollment-3"))
                .andExpect(jsonPath("$.studentId").value("student-2"));

        mvc.perform(get("/api/v1/enrollments/enrollment-1").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("enrollment-1"));

        mvc.perform(get("/api/v1/enrollments/enrollment-3").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));
    }

    @Test
    void studentGradeTranscriptAndAdminGradeDetailReadsAreCovered() throws Exception {
        mvc.perform(get("/api/v1/enrollments/my/grades").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("enrollment-1"))
                .andExpect(jsonPath("$[0].courseCode").value("CS101"))
                .andExpect(jsonPath("$[0].finalGrade").value(88.5))
                .andExpect(jsonPath("$[0].letterGrade").value("A"));

        mvc.perform(get("/api/v1/enrollments/my/transcript").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.cumulativeGpa").value(4.0))
                .andExpect(jsonPath("$.summary.totalCreditsEarned").value(4))
                .andExpect(jsonPath("$.semesters[0].records[0].id").value("enrollment-1"));

        mvc.perform(get("/api/v1/grades/student-grades/enrollment/enrollment-1").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment.id").value("enrollment-1"))
                .andExpect(jsonPath("$.grades.length()").value(2))
                .andExpect(jsonPath("$.calculatedTotal").value(86.0))
                .andExpect(jsonPath("$.totalWeight").value(100.0));
    }

    @Test
    void lecturerGradeReadsAreScopedToOwnedSectionsAndRequireLecturerClaim() throws Exception {
        mvc.perform(get("/api/v1/grades/items/lecturer/my").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].section.id").value("section-1"));

        mvc.perform(get("/api/v1/grades/items/section/section-1").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(get("/api/v1/grades/student-grades/section/section-1").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].grades.length()").value(2))
                .andExpect(jsonPath("$[0].calculatedTotal").value(86.0))
                .andExpect(jsonPath("$[0].totalWeight").value(100.0));

        mvc.perform(get("/api/v1/grades/student-grades/enrollment/enrollment-1").with(lecturerJwt("lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollment.id").value("enrollment-1"));

        mvc.perform(get("/api/v1/grades/items/section/section-1").with(lecturerJwt("lecturer-other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/grades/student-grades/section/section-1").with(lecturerJwt("lecturer-other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(get("/api/v1/grades/student-grades/enrollment/enrollment-1").with(lecturerJwt("lecturer-other")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(get("/api/v1/grades/items/lecturer/my")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-user-1")
                                .claim("roles", List.of("LECTURER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));
    }

    @Test
    void enrollmentReadBoundaryFailsClosedForAnonymousRolesMissingClaimsAndBadQueries() throws Exception {
        mvc.perform(get("/api/v1/enrollments/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/enrollments").with(studentJwt("student-user-1", "student-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/enrollments/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-user-1")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"));

        mvc.perform(get("/api/v1/enrollments")
                        .queryParam("limit", "101")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/enrollments/my")
                        .queryParam("semesterId", "semester-1")
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
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."GradeItem" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "name" VARCHAR(120) NOT NULL,
                    "type" VARCHAR(40) NOT NULL,
                    "maxScore" DECIMAL(5, 2) NOT NULL,
                    "weight" DECIMAL(5, 2) NOT NULL,
                    "gradedAt" TIMESTAMP,
                    "createdAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "academic"."StudentGrade" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "gradeItemId" VARCHAR(120) NOT NULL,
                    "enrollmentId" VARCHAR(120) NOT NULL,
                    "score" DECIMAL(5, 2)
                )
                """);
    }

    private void clearTables() {
        jdbc.update("DELETE FROM \"academic\".\"StudentGrade\"");
        jdbc.update("DELETE FROM \"academic\".\"GradeItem\"");
        jdbc.update("DELETE FROM \"academic\".\"Enrollment\"");
        jdbc.update("DELETE FROM \"academic\".\"SectionSchedule\"");
        jdbc.update("DELETE FROM \"academic\".\"Section\"");
        jdbc.update("DELETE FROM \"academic\".\"Classroom\"");
        jdbc.update("DELETE FROM \"academic\".\"Course\"");
        jdbc.update("DELETE FROM \"academic\".\"Semester\"");
        jdbc.update("DELETE FROM \"academic\".\"AcademicYear\"");
        jdbc.update("DELETE FROM \"academic\".\"Lecturer\"");
        jdbc.update("DELETE FROM \"academic\".\"Student\"");
        jdbc.update("DELETE FROM \"academic\".\"User\"");
    }

    private void insertFixture() {
        insertUser("student-user-1", "student1@campuscore.edu", "An", "Student");
        insertUser("student-user-2", "student2@campuscore.edu", "Binh", "Student");
        insertUser("lecturer-user-1", "lecturer@campuscore.edu", "Lan", "Lecturer");
        insertStudent("student-1", "student-user-1", "S001");
        insertStudent("student-2", "student-user-2", "S002");
        jdbc.update(
                "INSERT INTO \"academic\".\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "lecturer-1",
                "lecturer-user-1",
                "department-1",
                "L001",
                true);
        jdbc.update(
                "INSERT INTO \"academic\".\"AcademicYear\""
                        + " (\"id\", \"year\", \"startDate\", \"endDate\", \"isCurrent\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                "ay-2026",
                2026,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME.plusSeconds(31_536_000)),
                true,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
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
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME.plusSeconds(10_368_000)),
                null,
                null,
                null,
                null,
                "ACTIVE",
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
        insertCourse("course-1", "CS101", "Intro", null, null, 4);
        insertCourse("course-2", "SE401", "Web Development", "Web Development", "Lap trinh web", 3);
        jdbc.update("INSERT INTO \"academic\".\"Classroom\" (\"id\", \"building\", \"roomNumber\") VALUES (?, ?, ?)",
                "room-1", "A", "A101");
        insertSection("section-1", "A", "course-1");
        insertSection("section-2", "B", "course-2");
        jdbc.update(
                "INSERT INTO \"academic\".\"SectionSchedule\" (\"id\", \"sectionId\", \"classroomId\", \"dayOfWeek\", \"startTime\", \"endTime\")"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                "schedule-1",
                "section-2",
                "room-1",
                2,
                "08:00",
                "10:00");
        insertEnrollment("enrollment-1", "student-1", "section-1", "COMPLETED", "PUBLISHED", "88.50", "A", 1);
        insertEnrollment("enrollment-2", "student-1", "section-2", "CONFIRMED", "DRAFT", null, null, 2);
        insertEnrollment("enrollment-3", "student-2", "section-1", "CONFIRMED", "DRAFT", null, null, 3);
        insertGradeItem("grade-item-1", "section-1", "Midterm", "MIDTERM", "100.00", "40.00", 1);
        insertGradeItem("grade-item-2", "section-1", "Final", "FINAL", "100.00", "60.00", 2);
        jdbc.update(
                "INSERT INTO \"academic\".\"StudentGrade\""
                        + " (\"id\", \"gradeItemId\", \"enrollmentId\", \"score\") VALUES (?, ?, ?, ?)",
                "student-grade-1",
                "grade-item-1",
                "enrollment-1",
                new java.math.BigDecimal("80.00"));
        jdbc.update(
                "INSERT INTO \"academic\".\"StudentGrade\""
                        + " (\"id\", \"gradeItemId\", \"enrollmentId\", \"score\") VALUES (?, ?, ?, ?)",
                "student-grade-2",
                "grade-item-2",
                "enrollment-1",
                new java.math.BigDecimal("90.00"));
    }

    private void insertUser(String id, String email, String firstName, String lastName) {
        jdbc.update("INSERT INTO \"academic\".\"User\" (\"id\", \"email\", \"firstName\", \"lastName\") VALUES (?, ?, ?, ?)",
                id, email, firstName, lastName);
    }

    private void insertStudent(String id, String userId, String studentNumber) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Student\""
                        + " (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"status\","
                        + " \"admissionDate\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                studentNumber,
                "curriculum-1",
                2,
                "ACTIVE",
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertCourse(String id, String code, String name, String nameEn, String nameVi, int credits) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Course\""
                        + " (\"id\", \"code\", \"name\", \"nameEn\", \"nameVi\", \"description\", \"descriptionEn\","
                        + " \"descriptionVi\", \"credits\", \"departmentId\", \"semesterId\", \"isActive\","
                        + " \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                code,
                name,
                nameEn,
                nameVi,
                null,
                null,
                null,
                credits,
                "department-1",
                "semester-1",
                true,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private void insertSection(String id, String sectionNumber, String courseId) {
        jdbc.update(
                "INSERT INTO \"academic\".\"Section\""
                        + " (\"id\", \"sectionNumber\", \"courseId\", \"semesterId\", \"lecturerId\", \"classroomId\","
                        + " \"capacity\", \"enrolledCount\", \"status\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionNumber,
                courseId,
                "semester-1",
                "lecturer-1",
                "room-1",
                30,
                2,
                "OPEN");
    }

    private void insertEnrollment(
            String id,
            String studentId,
            String sectionId,
            String status,
            String gradeStatus,
            String finalGrade,
            String letterGrade,
            long days) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO "academic"."Enrollment" (
                        "id", "studentId", "sectionId", "semesterId", "status", "enrolledAt",
                        "droppedAt", "gradeStatus", "finalGrade", "letterGrade", "createdAt", "updatedAt"
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, id);
                statement.setString(2, studentId);
                statement.setString(3, sectionId);
                statement.setString(4, "semester-1");
                statement.setString(5, status);
                statement.setObject(6, localDateTime(BASE_TIME.plusSeconds(days * 86_400)), Types.TIMESTAMP);
                statement.setNull(7, Types.TIMESTAMP);
                statement.setString(8, gradeStatus);
                if (finalGrade == null) {
                    statement.setNull(9, Types.DECIMAL);
                } else {
                    statement.setBigDecimal(9, new java.math.BigDecimal(finalGrade));
                }
                statement.setString(10, letterGrade);
                statement.setObject(11, localDateTime(BASE_TIME), Types.TIMESTAMP);
                statement.setObject(12, localDateTime(BASE_TIME), Types.TIMESTAMP);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private void insertGradeItem(
            String id,
            String sectionId,
            String name,
            String type,
            String maxScore,
            String weight,
            long days) {
        jdbc.update(
                "INSERT INTO \"academic\".\"GradeItem\""
                        + " (\"id\", \"sectionId\", \"name\", \"type\", \"maxScore\", \"weight\", \"gradedAt\", \"createdAt\")"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                sectionId,
                name,
                type,
                new java.math.BigDecimal(maxScore),
                new java.math.BigDecimal(weight),
                null,
                localDateTime(BASE_TIME.plusSeconds(days * 86_400)));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
