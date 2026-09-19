package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.academic.service.AdminAnalyticsService;
import java.time.OffsetDateTime;
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

/**
 * Admin analytics overview: security gate plus aggregate correctness on a
 * representative campus fixture. Definitions exercised here are the ones
 * documented on the DTOs (enrollment seat-holding statuses, graded =
 * COMPLETED or PUBLISHED/APPEALED, students counted through their curriculum).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admin_analytics_overview;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AdminAnalyticsOverviewTest {

    private static final OffsetDateTime OLD_START =
            OffsetDateTime.parse("2025-09-01T00:00:00Z");
    private static final OffsetDateTime CURRENT_START =
            OffsetDateTime.parse("2026-02-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AdminAnalyticsService service;

    @BeforeEach
    void prepareAcademicFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.execute("DELETE FROM \"campuscore_auth\".\"User\"");
        io.campuscore.restfulapi.security.DatabaseAvailabilityTracker.recordSuccess();
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Enrollment\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Section\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Student\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Lecturer\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Course\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Curriculum\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Semester\"");
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Department\"");
        jdbc.execute("""
                CREATE TABLE academic."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "nameVi" VARCHAR(180),
                    "code" VARCHAR(40) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Semester" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(180) NOT NULL,
                    "startDate" TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "curriculumId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "title" VARCHAR(160)
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Course" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Section" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "courseId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE academic."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "gradeStatus" VARCHAR(40) NOT NULL,
                    "letterGrade" VARCHAR(16)
                )
                """);
    }

    private void seedCampus() {
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"nameVi\", \"code\") "
                + "VALUES ('dept-cntt', 'Cong nghe thong tin', 'Công nghệ thông tin', 'CNTT')");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"nameVi\", \"code\") "
                + "VALUES ('dept-ck', 'Co khi', 'Cơ khí', 'CK')");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"departmentId\") "
                + "VALUES ('curr-cntt', 'dept-cntt')");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"departmentId\") "
                + "VALUES ('curr-ck', 'dept-ck')");
        jdbc.update("INSERT INTO academic.\"Semester\" (\"id\", \"name\", \"startDate\") "
                + "VALUES ('sem-1', 'HK1 2025-2026', ?)", OLD_START);
        jdbc.update("INSERT INTO academic.\"Semester\" (\"id\", \"name\", \"startDate\") "
                + "VALUES ('sem-2', 'HK2 2025-2026', ?)", CURRENT_START);

        // Four CNTT students, one CK student.
        for (int i = 1; i <= 4; i++) {
            jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"curriculumId\") "
                    + "VALUES (?, 'curr-cntt')", "student-" + i);
        }
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"curriculumId\") "
                + "VALUES ('student-5', 'curr-ck')");

        jdbc.update("INSERT INTO academic.\"Course\" (\"id\", \"departmentId\") "
                + "VALUES ('course-cntt-1', 'dept-cntt')");
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\", \"departmentId\") "
                + "VALUES ('course-cntt-2', 'dept-cntt')");
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\", \"courseId\", \"semesterId\") "
                + "VALUES ('section-1', 'course-cntt-1', 'sem-2')");
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\", \"courseId\", \"semesterId\") "
                + "VALUES ('section-2', 'course-cntt-2', 'sem-1')");

        // Two professors and one lecturer without a title.
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"title\") VALUES ('lec-1', 'PROFESSOR')");
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"title\") VALUES ('lec-2', 'PROFESSOR')");
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"title\") VALUES ('lec-3', NULL)");

        // Current semester: 3 live enrollments for 2 distinct students, 2 graded.
        enrollment("enr-1", "student-1", "section-1", "sem-2", "ENROLLED", "PUBLISHED", "A");
        enrollment("enr-2", "student-1", "section-1", "sem-2", "ENROLLED", "PUBLISHED", "B+");
        enrollment("enr-3", "student-2", "section-1", "sem-2", "ENROLLED", "GRADING", null);
        // A dropped seat must not count anywhere.
        enrollment("enr-4", "student-3", "section-1", "sem-2", "DROPPED", "DRAFT", null);
        // Past semester: one completed enrollment for student-1.
        enrollment("enr-5", "student-1", "section-2", "sem-1", "COMPLETED", "PUBLISHED", "A+");
    }

    private void enrollment(
            String id, String studentId, String sectionId, String semesterId,
            String status, String gradeStatus, String letterGrade) {
        jdbc.update("""
                INSERT INTO academic."Enrollment"
                    ("id", "studentId", "sectionId", "semesterId", "status", "gradeStatus", "letterGrade")
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, studentId, sectionId, semesterId, status, gradeStatus, letterGrade);
    }

    @Test
    void overviewIsAdminOnly() throws Exception {
        seedCampus();

        mvc.perform(get("/api/v1/academic/distribution-overview/overview"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/academic/distribution-overview/overview")
                        .with(jwt().jwt(token -> token
                                .subject("student-1")
                                .claim("email", "student-1@campuscore.edu")
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/academic/distribution-overview/overview")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments[0].code").value("CNTT"))
                .andExpect(jsonPath("$.departments[0].students").value(4))
                .andExpect(jsonPath("$.departments[0].courses").value(2))
                .andExpect(jsonPath("$.departments[0].sections").value(2))
                .andExpect(jsonPath("$.departments[1].code").value("CK"))
                .andExpect(jsonPath("$.departments[1].students").value(1))
                .andExpect(jsonPath("$.semesterTrends[0].semester").value("HK1 2025-2026"))
                .andExpect(jsonPath("$.semesterTrends[0].count").value(1))
                .andExpect(jsonPath("$.semesterTrends[0].completionRate").value(100.0))
                .andExpect(jsonPath("$.semesterTrends[0].activeStudents").value(1))
                .andExpect(jsonPath("$.semesterTrends[1].semester").value("HK2 2025-2026"))
                .andExpect(jsonPath("$.semesterTrends[1].count").value(3))
                .andExpect(jsonPath("$.semesterTrends[1].completionRate").value(66.7))
                .andExpect(jsonPath("$.semesterTrends[1].activeStudents").value(2))
                .andExpect(jsonPath("$.facultyRanks[0].rank").value("PROFESSOR"))
                .andExpect(jsonPath("$.facultyRanks[0].count").value(2))
                .andExpect(jsonPath("$.facultyRanks[1].rank").value("UNSPECIFIED"))
                .andExpect(jsonPath("$.facultyRanks[1].count").value(1))
                .andExpect(jsonPath("$.gradeDistribution[0].grade").value("A+"))
                .andExpect(jsonPath("$.gradeDistribution[0].count").value(1))
                .andExpect(jsonPath("$.gradeDistribution[1].grade").value("A"))
                .andExpect(jsonPath("$.gradeDistribution[1].count").value(1))
                .andExpect(jsonPath("$.gradeDistribution[2].grade").value("B+"))
                .andExpect(jsonPath("$.gradeDistribution[2].count").value(1))
                .andExpect(jsonPath("$.totals.students").value(5))
                .andExpect(jsonPath("$.totals.lecturers").value(3))
                .andExpect(jsonPath("$.totals.courses").value(2))
                .andExpect(jsonPath("$.totals.enrollments").value(4));
    }

    @Test
    void serviceAggregatesMatchTheDocumentedDefinitions() {
        seedCampus();

        var overview = service.overview();

        // Departments sorted by students descending.
        assertThat(overview.departments()).hasSize(2);
        assertThat(overview.departments().get(0).code()).isEqualTo("CNTT");
        assertThat(overview.departments().get(0).students()).isEqualTo(4);
        assertThat(overview.departments().get(1).students()).isEqualTo(1);

        // Trends ascend chronologically.
        assertThat(overview.semesterTrends()).hasSize(2);
        assertThat(overview.semesterTrends().get(0).semester()).isEqualTo("HK1 2025-2026");
        assertThat(overview.semesterTrends().get(0).count()).isEqualTo(1);
        assertThat(overview.semesterTrends().get(0).completionRate()).isEqualTo(100.0);
        assertThat(overview.semesterTrends().get(1).semester()).isEqualTo("HK2 2025-2026");
        assertThat(overview.semesterTrends().get(1).count()).isEqualTo(3);
        // 2 of 3 live enrollments graded (PUBLISHED), one still GRADING.
        assertThat(overview.semesterTrends().get(1).completionRate()).isEqualTo(66.7);
        assertThat(overview.semesterTrends().get(1).activeStudents()).isEqualTo(2);

        // Ranks ordered by count then name.
        assertThat(overview.facultyRanks()).hasSize(2);
        assertThat(overview.facultyRanks().get(0).rank()).isEqualTo("PROFESSOR");
        assertThat(overview.facultyRanks().get(0).count()).isEqualTo(2);
        assertThat(overview.facultyRanks().get(1).rank()).isEqualTo("UNSPECIFIED");

        // Canonical letter band order: A+ before A before B+.
        assertThat(overview.gradeDistribution())
                .extracting(entry -> entry.grade())
                .containsExactly("A+", "A", "B+");

        // Dropped rows are not counted in the totals.
        assertThat(overview.totals().students()).isEqualTo(5);
        assertThat(overview.totals().lecturers()).isEqualTo(3);
        assertThat(overview.totals().courses()).isEqualTo(2);
        assertThat(overview.totals().enrollments()).isEqualTo(4);
    }
}
