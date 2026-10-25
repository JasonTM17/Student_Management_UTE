package io.campuscore.restfulapi.engagement;

import static io.campuscore.restfulapi.testsupport.CurrentAccountJwt.fixtureJwt;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:repair_AnnouncementReadPersistenceTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AnnouncementReadPersistenceTest {

    private static final Instant PAST = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareReadOnlyFixture() {
        io.campuscore.restfulapi.security.availability.DatabaseAvailabilityTracker.recordSuccess();
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        // The account-state gate consults the issuing table on every business
        // request and fails closed when the store is unreadable, so this
        // fixture must own a readable (empty) auth user table.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "password" VARCHAR(200),
                    "firstName" VARCHAR(120),
                    "lastName" VARCHAR(120),
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "isSuperAdmin" BOOLEAN,
                    "failedLoginAttempts" INTEGER,
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("DELETE FROM \"campuscore_auth\".\"User\"");
        // The reader scopes section-targeted notices to enrolled students, so the
        // fixture owns the enrollment table it consults (the H2 mirror excludes it
        // by design; Postgres V14 owns the real unique indexes).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Enrollment" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "studentId" VARCHAR(120) NOT NULL,
                    "sectionId" VARCHAR(120) NOT NULL,
                    "semesterId" VARCHAR(120) NOT NULL,
                    "status" VARCHAR(40) NOT NULL,
                    "registrationSource" VARCHAR(40)
                )
                """);
        jdbc.execute("DELETE FROM academic.\"Enrollment\"");
        // Faculty-target resolution chain (V149): Student -> Curriculum ->
        // Department -> facultyId, plus the lecturer's own department.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Faculty" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "facultyId" VARCHAR(120) NOT NULL,
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "curriculumId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "departmentId" VARCHAR(120) NOT NULL
                )
                """);
        jdbc.execute("DELETE FROM academic.\"Lecturer\"");
        jdbc.execute("DELETE FROM academic.\"Student\"");
        jdbc.execute("DELETE FROM academic.\"Curriculum\"");
        jdbc.execute("DELETE FROM academic.\"Department\"");
        jdbc.execute("DELETE FROM academic.\"Faculty\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"AnnouncementAudit\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"Announcement\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."Announcement" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "title" VARCHAR(240) NOT NULL,
                    "content" TEXT NOT NULL,
                    "slug" VARCHAR(240),
                    "priority" VARCHAR(20) NOT NULL,
                    "targetRoles" VARCHAR ARRAY NOT NULL,
                    "targetYears" INTEGER ARRAY NOT NULL,
                    "targetDepartments" VARCHAR ARRAY NOT NULL DEFAULT ARRAY[],
                    "targetFaculties" VARCHAR ARRAY,
                    "isGlobal" BOOLEAN NOT NULL,
                    "publishAt" TIMESTAMP WITH TIME ZONE,
                    "expiresAt" TIMESTAMP WITH TIME ZONE,
                    "publishedBy" VARCHAR(120),
                    "semesterId" VARCHAR(120),
                    "semesterName" VARCHAR(200),
                    "sectionId" VARCHAR(120),
                    "sectionNumber" VARCHAR(80),
                    "courseCode" VARCHAR(80),
                    "courseName" VARCHAR(200),
                    "lecturerId" VARCHAR(120),
                    "lecturerDisplayName" VARCHAR(200),
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "updatedAt" TIMESTAMP WITH TIME ZONE NOT NULL,
                    "version" INTEGER NOT NULL DEFAULT 0,
                    "archivedAt" TIMESTAMP WITH TIME ZONE,
                    "archivedBy" VARCHAR(120),
                    "displayOrder" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Faculty" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Department" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "facultyId" VARCHAR(120),
                    "isActive" BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Curriculum" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "name" VARCHAR(240),
                    "departmentId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Lecturer" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "departmentId" VARCHAR(120)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS academic."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "curriculumId" VARCHAR(120)
                )
                """);
        jdbc.execute("ALTER TABLE academic.\"Student\" ADD COLUMN IF NOT EXISTS \"curriculumId\" VARCHAR(120)");
        fixtureJwt(jdbc, "student-user", "STUDENT");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.student(jdbc, "student-user", "student-profile", 2);
        fixtureJwt(jdbc, "lecturer-user", "LECTURER");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "lecturer-user", "lecturer-1");
    }

    @Test
    void myAnnouncementsPreserveRoleYearAndTimeIsolation() throws Exception {
        insert(fixture("global", true, new String[0], new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("role-year", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T10:00:00Z"));
        insert(fixture("wrong-role", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T11:00:00Z"));
        insert(fixture("wrong-year", false, new String[]{"STUDENT"}, new Integer[]{3}, "2026-08-20T12:00:00Z"));
        insert(fixture("future", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T13:00:00Z")
                .withWindow(FUTURE, null));
        insert(fixture("expired", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T14:00:00Z")
                .withWindow(PAST, PAST.plusSeconds(60)));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("role-year"))
                .andExpect(jsonPath("$.data[0].targetRoles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.data[0].targetYears[0]").value(2))
                .andExpect(jsonPath("$.data[0].publishAt").value("2020-01-01T00:00:00.000Z"))
                .andExpect(jsonPath("$.data[0].expiresAt").value("2100-01-01T00:00:00.000Z"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-08-20T10:00:00.000Z"))
                .andExpect(jsonPath("$.data[0].updatedAt").value("2026-08-20T10:00:00.000Z"))
                .andExpect(jsonPath("$.data[1].id").value("global"))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-without-profile", token -> token
                                .subject("student-without-profile")
                                .claim("email", "student2@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-with-numeric-id", token -> token
                                .subject("student-with-numeric-id")
                                .claim("email", "student3@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", 42)
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-with-fractional-year", token -> token
                                .subject("student-with-fractional-year")
                                .claim("email", "student4@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2.5)), "STUDENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-with-overflow-year", token -> token
                                .subject("student-with-overflow-year")
                                .claim("email", "student5@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", Long.MAX_VALUE)), "STUDENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    @Test
    void departmentTargetsRestrictStudentAndLecturerFeedsByResolvedDepartment() throws Exception {
        // student-profile reaches dept-it through Student → Curriculum;
        // lecturer-1 reaches dept-cs through Lecturer.departmentId.
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"departmentId\") VALUES ('curr-it', 'dept-it')");
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"curriculumId\") VALUES ('student-profile', 'curr-it')");
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"departmentId\") VALUES ('lecturer-1', 'dept-cs')");

        insert(fixture("dept-match", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T09:00:00Z"));
        insert(fixture("dept-miss", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T10:00:00Z"));
        insert(fixture("dept-none", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T11:00:00Z"));
        insert(fixture("dept-lecturer-match", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T12:00:00Z"));
        insert(fixture("dept-lecturer-miss", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T13:00:00Z"));
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"targetDepartments\" = ARRAY['dept-it']"
                + " WHERE \"id\" IN ('dept-match', 'dept-lecturer-miss')");
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"targetDepartments\" = ARRAY['dept-cs']"
                + " WHERE \"id\" IN ('dept-miss', 'dept-lecturer-match')");

        // The dept-it student sees the matching and the untargeted notice but
        // never the dept-cs one — dimensions stay ANDed.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("dept-none"))
                .andExpect(jsonPath("$.data[0].targetDepartments.length()").value(0))
                .andExpect(jsonPath("$.data[1].id").value("dept-match"))
                .andExpect(jsonPath("$.data[1].targetDepartments[0]").value("dept-it"));

        // A student whose curriculum resolves no department (or who has no
        // academic row at all) can only see untargeted notices.
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureUser(jdbc, "orphan-user");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.student(jdbc, "orphan-user", "orphan-profile", 2);
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "orphan-user", token -> token
                                .subject("orphan-user")
                                .claim("email", "orphan@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "orphan-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("dept-none"));

        // The lecturer feed applies the same AND through Lecturer.departmentId.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "lecturer-user", "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("dept-lecturer-match"))
                .andExpect(jsonPath("$.data[0].targetDepartments[0]").value("dept-cs"));

        // Single-announcement reads honor the same visibility predicate.
        mvc.perform(get("/api/v1/announcements/dept-miss")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/dept-match")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk());
    }

    @Test
    void departmentTargetsHideFromViewersWithoutDepartmentIdentity() throws Exception {
        // Viewers whose token carries no studentId or lecturerId (admin, dean,
        // head-of-department) resolve no department EXISTS clause; the feed must
        // then show them only untargeted notices, never every department's.
        insert(fixture("dept-it-global", true, new String[0], new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("untargeted-global", true, new String[0], new Integer[0], "2026-08-20T10:00:00Z"));
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"targetDepartments\" = ARRAY['dept-it']"
                + " WHERE \"id\" = 'dept-it-global'");

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "dept-less-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("untargeted-global"));

        // Single reads honor the same audience boundary for non-admin roles:
        // a head-of-department token with no resolvable department gets 404.
        mvc.perform(get("/api/v1/announcements/dept-it-global")
                        .with(fixtureJwt(jdbc, "dept-less-head", "TRUONG_BO_MON")))
                .andExpect(status().isNotFound());
    }

    @Test
    void droppedEnrollmentDoesNotGrantSectionNoticeVisibility() throws Exception {
        insert(fixture("dropped-section", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-dropped", "03", "CS103", "Networks"));
        insert(fixture("still-enrolled", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-active", "04", "CS104", "Databases"));
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "enrollment-dropped", "student-profile", "section-dropped", "semester-1", "DROPPED");
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "enrollment-active", "student-profile", "section-active", "semester-1", "ENROLLED");

        // A withdrawn student is not enrolled: only the active section's notice
        // is visible, which matches how credit load is computed elsewhere.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("still-enrolled"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void withdrawnEnrollmentDoesNotGrantSectionNoticeVisibility() throws Exception {
        insert(fixture("withdrawn-section", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-withdrawn", "05", "CS105", "Security"));
        insert(fixture("still-enrolled", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-active", "04", "CS104", "Databases"));
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "enrollment-withdrawn", "student-profile", "section-withdrawn", "semester-1", "WITHDRAWN");
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "enrollment-active", "student-profile", "section-active", "semester-1", "ENROLLED");

        // A withdrawn enrollment is transcript history, not a live seat: only
        // the active section's notice may reach the student inbox.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("still-enrolled"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void sectionScopedNoticeOnlyReachesEnrolledStudents() throws Exception {
        insert(fixture("own-section", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-owned", "01", "CS101", "Web Programming"));
        insert(fixture("other-section", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-other", "02", "CS102", "Data Structures"));
        insert(fixture("whole-cohort", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T12:00:00Z"));
        jdbc.update("INSERT INTO academic.\"Enrollment\""
                        + " (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\")"
                        + " VALUES (?, ?, ?, ?, ?)",
                "enrollment-1", "student-profile", "section-owned", "semester-1", "ENROLLED");

        // createdAt DESC: the cohort-wide notice first, then the notice for the
        // section the student is actually enrolled in. The notice scoped to
        // another section must not appear at all.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("whole-cohort"))
                .andExpect(jsonPath("$.data[1].id").value("own-section"))
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void lecturerTargetCannotLeakAcrossLecturerClaims() throws Exception {
        insert(fixture("general", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("mine", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withLecturer("lecturer-1", "Lecturer One"));
        insert(fixture("other", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withLecturer("lecturer-2", "Lecturer Two"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "lecturer-user", token -> token
                                .subject("lecturer-user")
                                .claim("email", "lecturer@campuscore.edu")
                                .claim("roles", List.of("LECTURER"))
                                .claim("lecturerId", "lecturer-1"), "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("mine"))
                .andExpect(jsonPath("$.data[0].lecturer.id").value("lecturer-1"))
                .andExpect(jsonPath("$.data[1].id").value("general"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "lecturer-without-profile", token -> token
                                .subject("lecturer-without-profile")
                                .claim("email", "lecturer2@campuscore.edu")
                                .claim("roles", List.of("LECTURER")), "LECTURER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "lecturer-with-numeric-profile", token -> token
                                .subject("lecturer-with-numeric-profile")
                                .claim("email", "lecturer3@campuscore.edu")
                                .claim("roles", List.of("LECTURER"))
                                .claim("lecturerId", 42), "LECTURER")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    @Test
    void adminListingPreservesFiltersEnvelopeAndDerivedObjects() throws Exception {
        insert(fixture("matching", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withPriority("HIGH")
                .withAcademicContext("semester-1", "Semester 1", "section-1", "01", "CS101", "Web Programming")
                .withLecturer("lecturer-1", "Lecturer One"));
        insert(fixture("other", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withPriority("NORMAL"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("semesterId", "semester-1")
                        .queryParam("sectionId", "section-1")
                        .queryParam("priority", "HIGH")
                        .with(fixtureJwt(jdbc, "admin-user", token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")), "ADMIN")
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("matching"))
                .andExpect(jsonPath("$.data[0].semesterName").value("Semester 1"))
                .andExpect(jsonPath("$.data[0].semester.name").value("Semester 1"))
                .andExpect(jsonPath("$.data[0].section.sectionNumber").value("01"))
                .andExpect(jsonPath("$.data[0].section.course.code").value("CS101"))
                .andExpect(jsonPath("$.data[0].lecturer.displayName").value("Lecturer One"))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(20))
                .andExpect(jsonPath("$.meta.totalPages").value(1));

        mvc.perform(get("/api/v1/announcements")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                        .subject("student-user")
                                        .claim("email", "student@campuscore.edu")
                                        .claim("roles", List.of("STUDENT")), "STUDENT")
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void paginationIsOneBasedAndBoundedLikeTheNestContract() throws Exception {
        insert(fixture("newest", true, new String[0], new Integer[0], "2026-08-20T12:00:00Z"));
        insert(fixture("middle", true, new String[0], new Integer[0], "2026-08-20T11:00:00Z"));
        insert(fixture("oldest", true, new String[0], new Integer[0], "2026-08-20T10:00:00Z"));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("page", "2")
                        .queryParam("limit", "2")
                        .with(fixtureJwt(jdbc, "user", token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("oldest"))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("limit", "201")
                        .with(fixtureJwt(jdbc, "user", token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"), "ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("priority", "CRITICAL")
                        .with(fixtureJwt(jdbc, "admin-user", token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")), "ADMIN")
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("priority", "")
                        .with(fixtureJwt(jdbc, "admin-user", token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")), "ADMIN")
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("unknown", "value")
                        .with(fixtureJwt(jdbc, "user", token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("page", "1", "2")
                        .with(fixtureJwt(jdbc, "user", token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminSearchMatchesFoldedMultiTokenQueriesAcrossTitleAndContent() throws Exception {
        insert(fixture("java-notice", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withAcademicContext("semester-1", "Semester 1", "section-1", "01", "SE401", "Lập trình Java nâng cao"));
        insert(fixture("exam-notice", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T11:00:00Z"));
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"title\" = 'Lịch thi giữa kỳ' WHERE \"id\" = 'exam-notice'");

        var admin = fixtureJwt(jdbc, "admin-user", token -> token
                        .subject("admin-user")
                        .claim("email", "admin@campuscore.edu")
                        .claim("roles", List.of("ADMIN")), "ADMIN")
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // Folded diacritics: "lich thi" matches the Vietnamese title.
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("search", "lich thi")
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("exam-notice"))
                .andExpect(jsonPath("$.meta.total").value(1));

        // Multi-token across snapshot columns: course code + course name token.
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("search", "SE401 java")
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("java-notice"))
                .andExpect(jsonPath("$.meta.total").value(1));

        // Content column is searchable too.
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("search", "Content exam")
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("exam-notice"));

        // Unknown token matches nothing; empty search keeps the full listing.
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("search", "tuyet-doi-khong-trung")
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0))
                .andExpect(jsonPath("$.meta.total").value(0));
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("search", "")
                        .with(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(2));
    }

    @Test
    void emptyAdminFiltersAreIgnoredAndIdentityClaimsAreRequired() throws Exception {
        insert(fixture("first", true, new String[0], new Integer[0], "2026-08-20T10:00:00Z"));
        insert(fixture("second", true, new String[0], new Integer[0], "2026-08-20T11:00:00Z"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("semesterId", "")
                        .queryParam("sectionId", "")
                        .with(fixtureJwt(jdbc, "admin-user", token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")), "ADMIN")
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "missing-email", token -> token.subject("missing-email").claims(claims -> claims.remove("email")))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "numeric-email", token -> token
                                .subject("numeric-email")
                                .claim("email", 42))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "user", token -> token
                                .claim("sub", 42)
                                .claim("email", "numeric-sub@campuscore.edu"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "mixed-roles", token -> token
                                .claim("sub", false)
                                .claim("email", "boolean-sub@campuscore.edu"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "mixed-roles", token -> token
                                .subject("mixed-roles")
                                .claim("email", "mixed-roles@campuscore.edu")
                                .claim("roles", List.of("STUDENT", 42)), "STUDENT")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_INVALID"));
    }

    @Test
    void anonymousReadFailsClosed() throws Exception {
        mvc.perform(get("/api/v1/announcements/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void lecturerDetailUsesTheSameAudienceAsFeedAndPreservesOwnerManagement() throws Exception {
        insert(fixture("private-students", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("staff-visible", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("staff-expired", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T09:00:00Z")
                .withWindow(PAST, PAST));
        insert(fixture("staff-own", false, new String[]{"STUDENT"}, new Integer[0], "2026-08-20T09:00:00Z")
                .withLecturer("lecturer-1", "Lecturer One").withWindow(FUTURE, null));
        var lecturer = fixtureJwt(jdbc, "lecturer-user", token -> token.subject("lecturer-user")
                .claim("email", "lecturer@campuscore.edu").claim("roles", List.of("LECTURER"))
                .claim("lecturerId", "lecturer-1"), "LECTURER")
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
        mvc.perform(get("/api/v1/announcements/private-students").with(lecturer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/staff-expired").with(lecturer))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/staff-visible").with(lecturer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("staff-visible"));
        mvc.perform(get("/api/v1/announcements/staff-own").with(lecturer))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/announcements/private-students").with(fixtureJwt(jdbc, "admin-user", token -> token
                .subject("admin-user").claim("email", "admin@campuscore.edu").claim("roles", List.of("ADMIN")), "ADMIN")
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void currentMultiRoleStaffCanReadVisibleDetailsWithoutLosingStudentAudienceScope() throws Exception {
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.student(jdbc, "multi-role-user", "multi-student", 2);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "multi-role-user", "multi-lecturer");
        insert(fixture("multi-global", true, new String[0], new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("multi-own-section", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T09:00:00Z")
                .withAcademicContext("semester", "Semester", "multi-section", "01", "CS101", "Web"));
        insert(fixture("multi-other-section", false, new String[]{"STUDENT"}, new Integer[]{2}, "2026-08-20T09:00:00Z")
                .withAcademicContext("semester", "Semester", "other-section", "02", "CS102", "Networks"));
        insert(fixture("multi-other-year", false, new String[]{"STUDENT"}, new Integer[]{3}, "2026-08-20T09:00:00Z"));
        jdbc.update("INSERT INTO academic.\"Enrollment\" (\"id\", \"studentId\", \"sectionId\", \"semesterId\", \"status\") VALUES ('multi-enrollment', 'multi-student', 'multi-section', 'semester', 'ENROLLED')");
        var lecturerStudent = fixtureJwt(jdbc, "multi-role-user", "STUDENT", "LECTURER");
        mvc.perform(get("/api/v1/announcements/multi-global").with(lecturerStudent)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/announcements/multi-own-section").with(lecturerStudent)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/announcements/multi-other-section").with(lecturerStudent)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/announcements/multi-other-year").with(lecturerStudent)).andExpect(status().isNotFound());
        var adminStudent = fixtureJwt(jdbc, "multi-role-user", "STUDENT", "ADMIN");
        mvc.perform(get("/api/v1/announcements/multi-global").with(adminStudent)).andExpect(status().isOk());
    }

    @Test
    void facultyTargetedNoticesOnlyReachViewersOfThatFaculty() throws Exception {
        // faculty-f1 <- department-f1 <- curriculum-f1 <- the student profile;
        // faculty-f2 <- department-f2 <- curriculum-f2 <- the second student.
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\") VALUES ('faculty-f1', 'Faculty 1')");
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\") VALUES ('faculty-f2', 'Faculty 2')");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\") VALUES ('department-f1', 'Department 1', 'faculty-f1')");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"facultyId\") VALUES ('department-f2', 'Department 2', 'faculty-f2')");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") VALUES ('curriculum-f1', 'Curriculum 1', 'department-f1')");
        jdbc.update("INSERT INTO academic.\"Curriculum\" (\"id\", \"name\", \"departmentId\") VALUES ('curriculum-f2', 'Curriculum 2', 'department-f2')");
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"curriculumId\") VALUES ('student-profile', 'curriculum-f1')");
        jdbc.update("INSERT INTO academic.\"Student\" (\"id\", \"curriculumId\") VALUES ('student-other', 'curriculum-f2')");
        // The fixture lecturer sits in faculty 1 through their own department.
        jdbc.update("INSERT INTO academic.\"Lecturer\" (\"id\", \"departmentId\") VALUES ('lecturer-1', 'department-f1')");

        insert(fixture("untargeted", true, new String[0], new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("for-f1", true, new String[0], new Integer[0], "2026-08-20T10:00:00Z")
                .withFaculties(new String[]{"faculty-f1"}));
        insert(fixture("for-f2", true, new String[0], new Integer[0], "2026-08-20T11:00:00Z")
                .withFaculties(new String[]{"faculty-f2"}));
        // A mistyped faculty id matches nobody — it narrows to nothing.
        insert(fixture("for-ghost", true, new String[0], new Integer[0], "2026-08-20T12:00:00Z")
                .withFaculties(new String[]{"faculty-unknown"}));
        // OR within the faculty dimension: either listed faculty admits the viewer.
        insert(fixture("for-f1-or-f2", true, new String[0], new Integer[0], "2026-08-20T12:30:00Z")
                .withFaculties(new String[]{"faculty-f2", "faculty-f1"}));
        // AND across dimensions: the faculty matches but the department does
        // not, so the row stays hidden from the f1 viewers.
        insert(fixture("for-f1-wrong-dept", true, new String[0], new Integer[0], "2026-08-20T13:00:00Z")
                .withFaculties(new String[]{"faculty-f1"}));
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"targetDepartments\" = ARRAY['department-f2']"
                + " WHERE \"id\" = 'for-f1-wrong-dept'");

        // Student of faculty 1: the untargeted notice and their faculty's.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-user", token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("for-f1-or-f2"))
                .andExpect(jsonPath("$.data[1].id").value("for-f1"))
                .andExpect(jsonPath("$.data[1].targetFaculties[0]").value("faculty-f1"))
                .andExpect(jsonPath("$.data[2].id").value("untargeted"))
                .andExpect(jsonPath("$.meta.total").value(3));

        // Lecturer of faculty 1 sees the same split through their department.
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "lecturer-user", token -> token
                                .subject("lecturer-user")
                                .claim("email", "lecturer@campuscore.edu")
                                .claim("roles", List.of("LECTURER"))
                                .claim("lecturerId", "lecturer-1"), "LECTURER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("for-f1-or-f2"))
                .andExpect(jsonPath("$.data[1].id").value("for-f1"))
                .andExpect(jsonPath("$.data[2].id").value("untargeted"));

        // A student whose profile has no academic row resolves no faculty and
        // only sees the untargeted notice.
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.student(
                jdbc, "student-nofac-user", "student-nofac", 2);
        mvc.perform(get("/api/v1/announcements/my")
                        .with(fixtureJwt(jdbc, "student-nofac-user", token -> token
                                .subject("student-nofac-user")
                                .claim("email", "student-nofac@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-nofac")
                                .claim("student", Map.of("year", 2)), "STUDENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("untargeted"));
    }

    private AnnouncementFixture fixture(
            String id,
            boolean global,
            String[] roles,
            Integer[] years,
            String createdAt) {
        return new AnnouncementFixture(
                id,
                "NORMAL",
                roles,
                years,
                new String[0],
                global,
                PAST,
                FUTURE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Instant.parse(createdAt));
    }

    private void insert(AnnouncementFixture fixture) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO "engagement"."Announcement" (
                        "id", "title", "content", "priority", "targetRoles", "targetYears",
                        "targetFaculties",
                        "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
                        "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
                        "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt"
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, fixture.id());
                statement.setString(2, "Title " + fixture.id());
                statement.setString(3, "Content " + fixture.id());
                statement.setString(4, fixture.priority());
                statement.setArray(5, connection.createArrayOf("VARCHAR", fixture.roles()));
                statement.setArray(6, connection.createArrayOf("INTEGER", fixture.years()));
                statement.setArray(7, connection.createArrayOf("VARCHAR", fixture.faculties()));
                statement.setBoolean(8, fixture.global());
                timestamp(statement, 9, fixture.publishAt());
                timestamp(statement, 10, fixture.expiresAt());
                statement.setString(11, "publisher-1");
                statement.setString(12, fixture.semesterId());
                statement.setString(13, fixture.semesterName());
                statement.setString(14, fixture.sectionId());
                statement.setString(15, fixture.sectionNumber());
                statement.setString(16, fixture.courseCode());
                statement.setString(17, fixture.courseName());
                statement.setString(18, fixture.lecturerId());
                statement.setString(19, fixture.lecturerDisplayName());
                timestamp(statement, 20, fixture.createdAt());
                timestamp(statement, 21, fixture.createdAt());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(
                    index,
                    java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC),
                    Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    private record AnnouncementFixture(
            String id,
            String priority,
            String[] roles,
            Integer[] years,
            String[] faculties,
            boolean global,
            Instant publishAt,
            Instant expiresAt,
            String semesterId,
            String semesterName,
            String sectionId,
            String sectionNumber,
            String courseCode,
            String courseName,
            String lecturerId,
            String lecturerDisplayName,
            Instant createdAt) {

        AnnouncementFixture withWindow(Instant publishAt, Instant expiresAt) {
            return new AnnouncementFixture(
                    id, priority, roles, years, faculties, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withPriority(String priority) {
            return new AnnouncementFixture(
                    id, priority, roles, years, faculties, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withAcademicContext(
                String semesterId,
                String semesterName,
                String sectionId,
                String sectionNumber,
                String courseCode,
                String courseName) {
            return new AnnouncementFixture(
                    id, priority, roles, years, faculties, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withLecturer(String lecturerId, String lecturerDisplayName) {
            return new AnnouncementFixture(
                    id, priority, roles, years, faculties, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withFaculties(String[] faculties) {
            return new AnnouncementFixture(
                    id, priority, roles, years, faculties, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }
    }
}
