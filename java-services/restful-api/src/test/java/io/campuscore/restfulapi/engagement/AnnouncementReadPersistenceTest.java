package io.campuscore.restfulapi.engagement;

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
        "migration.engagement-read.enabled=true",
        "spring.flyway.enabled=false"
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
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "engagement"."Announcement" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "title" VARCHAR(200) NOT NULL,
                    "content" VARCHAR(2000) NOT NULL,
                    "priority" VARCHAR(20) NOT NULL,
                    "targetRoles" VARCHAR ARRAY NOT NULL,
                    "targetYears" INTEGER ARRAY NOT NULL,
                    "isGlobal" BOOLEAN NOT NULL,
                    "publishAt" TIMESTAMP,
                    "expiresAt" TIMESTAMP,
                    "publishedBy" VARCHAR(120),
                    "semesterId" VARCHAR(120),
                    "semesterName" VARCHAR(200),
                    "sectionId" VARCHAR(120),
                    "sectionNumber" VARCHAR(80),
                    "courseCode" VARCHAR(80),
                    "courseName" VARCHAR(200),
                    "lecturerId" VARCHAR(120),
                    "lecturerDisplayName" VARCHAR(200),
                    "createdAt" TIMESTAMP NOT NULL,
                    "updatedAt" TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM \"engagement\".\"Announcement\"");
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
                        .with(jwt().jwt(token -> token
                                .subject("student-user")
                                .claim("email", "student@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2)))))
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
                        .with(jwt().jwt(token -> token
                                .subject("student-without-profile")
                                .claim("email", "student2@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "")
                                .claim("student", Map.of("year", 2)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-with-numeric-id")
                                .claim("email", "student3@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", 42)
                                .claim("student", Map.of("year", 2)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-with-fractional-year")
                                .claim("email", "student4@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", 2.5)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("student-with-overflow-year")
                                .claim("email", "student5@campuscore.edu")
                                .claim("roles", List.of("STUDENT"))
                                .claim("studentId", "student-profile")
                                .claim("student", Map.of("year", Long.MAX_VALUE)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void lecturerTargetCannotLeakAcrossLecturerClaims() throws Exception {
        insert(fixture("general", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T09:00:00Z"));
        insert(fixture("mine", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T10:00:00Z")
                .withLecturer("lecturer-1", "Lecturer One"));
        insert(fixture("other", false, new String[]{"LECTURER"}, new Integer[0], "2026-08-20T11:00:00Z")
                .withLecturer("lecturer-2", "Lecturer Two"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-user")
                                .claim("email", "lecturer@campuscore.edu")
                                .claim("roles", List.of("LECTURER"))
                                .claim("lecturerId", "lecturer-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("mine"))
                .andExpect(jsonPath("$.data[0].lecturer.id").value("lecturer-1"))
                .andExpect(jsonPath("$.data[1].id").value("general"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-without-profile")
                                .claim("email", "lecturer2@campuscore.edu")
                                .claim("roles", List.of("LECTURER")))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("lecturer-with-numeric-profile")
                                .claim("email", "lecturer3@campuscore.edu")
                                .claim("roles", List.of("LECTURER"))
                                .claim("lecturerId", 42))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")))
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
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("student-user")
                                        .claim("email", "student@campuscore.edu")
                                        .claim("roles", List.of("STUDENT")))
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
                        .with(jwt().jwt(token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("oldest"))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("limit", "201")
                        .with(jwt().jwt(token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("priority", "CRITICAL")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("priority", "")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("unknown", "value")
                        .with(jwt().jwt(token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/announcements/my")
                        .queryParam("page", "1", "2")
                        .with(jwt().jwt(token -> token
                                .subject("user")
                                .claim("email", "user@campuscore.edu"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void emptyAdminFiltersAreIgnoredAndIdentityClaimsAreRequired() throws Exception {
        insert(fixture("first", true, new String[0], new Integer[0], "2026-08-20T10:00:00Z"));
        insert(fixture("second", true, new String[0], new Integer[0], "2026-08-20T11:00:00Z"));

        mvc.perform(get("/api/v1/announcements")
                        .queryParam("semesterId", "")
                        .queryParam("sectionId", "")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("admin-user")
                                        .claim("email", "admin@campuscore.edu")
                                        .claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.total").value(2));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token.subject("missing-email"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("numeric-email")
                                .claim("email", 42))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .claim("sub", 42)
                                .claim("email", "numeric-sub@campuscore.edu"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .claim("sub", false)
                                .claim("email", "boolean-sub@campuscore.edu"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/announcements/my")
                        .with(jwt().jwt(token -> token
                                .subject("mixed-roles")
                                .claim("email", "mixed-roles@campuscore.edu")
                                .claim("roles", List.of("STUDENT", 42)))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousReadFailsClosed() throws Exception {
        mvc.perform(get("/api/v1/announcements/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
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
                        "isGlobal", "publishAt", "expiresAt", "publishedBy", "semesterId",
                        "semesterName", "sectionId", "sectionNumber", "courseCode", "courseName",
                        "lecturerId", "lecturerDisplayName", "createdAt", "updatedAt"
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, fixture.id());
                statement.setString(2, "Title " + fixture.id());
                statement.setString(3, "Content " + fixture.id());
                statement.setString(4, fixture.priority());
                statement.setArray(5, connection.createArrayOf("VARCHAR", fixture.roles()));
                statement.setArray(6, connection.createArrayOf("INTEGER", fixture.years()));
                statement.setBoolean(7, fixture.global());
                timestamp(statement, 8, fixture.publishAt());
                timestamp(statement, 9, fixture.expiresAt());
                statement.setString(10, "publisher-1");
                statement.setString(11, fixture.semesterId());
                statement.setString(12, fixture.semesterName());
                statement.setString(13, fixture.sectionId());
                statement.setString(14, fixture.sectionNumber());
                statement.setString(15, fixture.courseCode());
                statement.setString(16, fixture.courseName());
                statement.setString(17, fixture.lecturerId());
                statement.setString(18, fixture.lecturerDisplayName());
                timestamp(statement, 19, fixture.createdAt());
                timestamp(statement, 20, fixture.createdAt());
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(
                    index,
                    java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC),
                    Types.TIMESTAMP);
        }
    }

    private record AnnouncementFixture(
            String id,
            String priority,
            String[] roles,
            Integer[] years,
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
                    id, priority, roles, years, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withPriority(String priority) {
            return new AnnouncementFixture(
                    id, priority, roles, years, global, publishAt, expiresAt,
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
                    id, priority, roles, years, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }

        AnnouncementFixture withLecturer(String lecturerId, String lecturerDisplayName) {
            return new AnnouncementFixture(
                    id, priority, roles, years, global, publishAt, expiresAt,
                    semesterId, semesterName, sectionId, sectionNumber, courseCode,
                    courseName, lecturerId, lecturerDisplayName, createdAt);
        }
    }
}
