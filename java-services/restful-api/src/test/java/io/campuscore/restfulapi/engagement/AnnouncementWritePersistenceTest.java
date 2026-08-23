package io.campuscore.restfulapi.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class AnnouncementWritePersistenceTest {

    private static final Instant BASE_TIME = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareWriteFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"Announcement\"");
        jdbc.execute("""
                CREATE TABLE "engagement"."Announcement" (
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
    }

    @Test
    void adminCreatesAnnouncementWithLegacyDefaultsAndPublisher() throws Exception {
        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Welcome back",
                                  "content": "The new semester starts next week."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Welcome back"))
                .andExpect(jsonPath("$.content").value("The new semester starts next week."))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.targetRoles.length()").value(0))
                .andExpect(jsonPath("$.targetYears.length()").value(0))
                .andExpect(jsonPath("$.isGlobal").value(false))
                .andExpect(jsonPath("$.publishedBy").value("admin-1"))
                .andExpect(jsonPath("$.semester").doesNotExist())
                .andExpect(jsonPath("$.section").doesNotExist())
                .andExpect(jsonPath("$.lecturer").doesNotExist());

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"title\" = 'Welcome back' AND \"priority\" = 'NORMAL'"
                        + " AND \"publishedBy\" = 'admin-1' AND CARDINALITY(\"targetRoles\") = 0"
                        + " AND CARDINALITY(\"targetYears\") = 0 AND \"isGlobal\" = FALSE",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, count);
    }

    @Test
    void adminCreatesTargetedAnnouncementWithDatesAndAcademicPointers() throws Exception {
        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-2"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Defense schedule",
                                  "content": "Council assignments are available.",
                                  "priority": "HIGH",
                                  "targetRoles": ["STUDENT", "LECTURER"],
                                  "targetYears": [3, 4],
                                  "isGlobal": true,
                                  "publishAt": "2026-08-20T08:00:00Z",
                                  "expiresAt": "2026-09-20T08:00:00Z",
                                  "semesterId": "semester-1",
                                  "sectionId": "section-1",
                                  "lecturerId": "lecturer-1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.targetRoles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.targetRoles[1]").value("LECTURER"))
                .andExpect(jsonPath("$.targetYears[0]").value(3))
                .andExpect(jsonPath("$.targetYears[1]").value(4))
                .andExpect(jsonPath("$.isGlobal").value(true))
                .andExpect(jsonPath("$.publishAt").value("2026-08-20T08:00:00.000Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-20T08:00:00.000Z"))
                .andExpect(jsonPath("$.semesterId").value("semester-1"))
                .andExpect(jsonPath("$.semester").doesNotExist())
                .andExpect(jsonPath("$.sectionId").value("section-1"))
                .andExpect(jsonPath("$.section").exists())
                .andExpect(jsonPath("$.lecturer.id").value("lecturer-1"))
                .andExpect(jsonPath("$.lecturer.displayName").doesNotExist());
    }

    @Test
    void createBoundaryFailsClosedForStudentMissingBodyAndInvalidValues() throws Exception {
        mvc.perform(post("/api/v1/announcements")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("student-1")
                                        .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .content("{\"title\":\"Nope\",\"content\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"content\":\"Missing title\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"title\":\"Nope\",\"content\":\"Nope\",\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Bad priority",
                                  "content": "Bad priority",
                                  "priority": "CRITICAL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Bad year",
                                  "content": "Bad year",
                                  "targetYears": [0]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Bad role",
                                  "content": "Bad role",
                                  "targetRoles": [7]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Bad fractional year",
                                  "content": "Bad fractional year",
                                  "targetYears": [1.9]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminUpdatesAnnouncementPartiallyWithoutChangingPublisherOrInventingNames() throws Exception {
        seedAnnouncement();

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-2"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Updated title",
                                  "content": "",
                                  "priority": "URGENT",
                                  "targetRoles": ["STUDENT"],
                                  "targetYears": [2],
                                  "isGlobal": true,
                                  "publishAt": "2026-08-21T08:00:00Z",
                                  "expiresAt": "2026-09-21T08:00:00Z",
                                  "semesterId": "semester-2",
                                  "sectionId": "section-2",
                                  "lecturerId": "lecturer-2"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("existing-announcement"))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.content").value(""))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.targetRoles[0]").value("STUDENT"))
                .andExpect(jsonPath("$.targetYears[0]").value(2))
                .andExpect(jsonPath("$.isGlobal").value(true))
                .andExpect(jsonPath("$.publishAt").value("2026-08-21T08:00:00.000Z"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-21T08:00:00.000Z"))
                .andExpect(jsonPath("$.publishedBy").value("admin-1"))
                .andExpect(jsonPath("$.semesterId").value("semester-2"))
                .andExpect(jsonPath("$.semester").doesNotExist())
                .andExpect(jsonPath("$.sectionId").value("section-2"))
                .andExpect(jsonPath("$.section").exists())
                .andExpect(jsonPath("$.lecturer.id").value("lecturer-2"))
                .andExpect(jsonPath("$.lecturer.displayName").doesNotExist());

        Integer updated = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"id\" = 'existing-announcement' AND \"title\" = 'Updated title'"
                        + " AND \"content\" = '' AND \"priority\" = 'URGENT'"
                        + " AND \"publishedBy\" = 'admin-1' AND CARDINALITY(\"targetRoles\") = 1"
                        + " AND CARDINALITY(\"targetYears\") = 1 AND \"createdAt\" = ?"
                        + " AND \"updatedAt\" > ?",
                Integer.class,
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
        org.junit.jupiter.api.Assertions.assertEquals(1, updated);
    }

    @Test
    void adminClearsAnnouncementNullableFieldsWithExplicitNulls() throws Exception {
        seedAnnouncement();

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-2"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "publishAt": null,
                                  "expiresAt": null,
                                  "semesterId": null,
                                  "sectionId": null,
                                  "lecturerId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("existing-announcement"))
                .andExpect(jsonPath("$.publishAt").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.semesterId").doesNotExist())
                .andExpect(jsonPath("$.semester").doesNotExist())
                .andExpect(jsonPath("$.sectionId").doesNotExist())
                .andExpect(jsonPath("$.section").doesNotExist())
                .andExpect(jsonPath("$.lecturer").doesNotExist());

        Integer cleared = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"id\" = 'existing-announcement'"
                        + " AND \"publishAt\" IS NULL AND \"expiresAt\" IS NULL"
                        + " AND \"semesterId\" IS NULL AND \"semesterName\" IS NULL"
                        + " AND \"sectionId\" IS NULL AND \"sectionNumber\" IS NULL"
                        + " AND \"courseCode\" IS NULL AND \"courseName\" IS NULL"
                        + " AND \"lecturerId\" IS NULL AND \"lecturerDisplayName\" IS NULL"
                        + " AND \"updatedAt\" > ?",
                Integer.class,
                localDateTime(BASE_TIME));
        org.junit.jupiter.api.Assertions.assertEquals(1, cleared);
    }

    @Test
    void updateBoundaryFailsClosedForStudentMissingAnnouncementAndInvalidValues() throws Exception {
        seedAnnouncement();

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("student-1")
                                        .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(put("/api/v1/announcements/missing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"title\":\"Missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"priority\":\"CRITICAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"unexpected\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"targetYears\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"targetRoles\":[7]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1"))
                        .contentType("application/json")
                        .content("{\"targetYears\":[1.9]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void adminDeletesAnnouncementWithLegacySuccessMessage() throws Exception {
        seedAnnouncement();

        mvc.perform(delete("/api/v1/announcements/existing-announcement")
                        .with(adminJwt("admin-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Announcement deleted successfully"));

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"id\" = 'existing-announcement'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(0, remaining);
    }

    @Test
    void deleteBoundaryFailsClosedForStudentAndMissingAnnouncement() throws Exception {
        seedAnnouncement();

        mvc.perform(delete("/api/v1/announcements/existing-announcement")
                        .with(jwt()
                                .jwt(token -> token
                                        .subject("student-1")
                                        .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(delete("/api/v1/announcements/missing-announcement")
                        .with(adminJwt("admin-1")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        Integer remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"id\" = 'existing-announcement'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, remaining);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt(String subject) {
        return jwt()
                .jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void seedAnnouncement() {
        jdbc.update(
                "INSERT INTO \"engagement\".\"Announcement\""
                        + " (\"id\", \"title\", \"content\", \"priority\", \"targetRoles\", \"targetYears\","
                        + " \"isGlobal\", \"publishAt\", \"expiresAt\", \"publishedBy\", \"semesterId\","
                        + " \"semesterName\", \"sectionId\", \"sectionNumber\", \"courseCode\", \"courseName\","
                        + " \"lecturerId\", \"lecturerDisplayName\", \"createdAt\", \"updatedAt\")"
                        + " VALUES (?, ?, ?, ?, ARRAY['ADMIN'], ARRAY[1], ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "existing-announcement",
                "Original title",
                "Original content",
                "NORMAL",
                false,
                null,
                null,
                "admin-1",
                "semester-1",
                "Semester 1",
                "section-1",
                "01",
                "CS101",
                "Web Programming",
                "lecturer-1",
                "Lecturer One",
                localDateTime(BASE_TIME),
                localDateTime(BASE_TIME));
    }

    private static java.time.LocalDateTime localDateTime(Instant value) {
        return java.time.LocalDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }
}
