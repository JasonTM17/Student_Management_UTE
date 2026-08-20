package io.campuscore.restfulapi.engagement;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        "migration.engagement-write.enabled=true",
        "spring.flyway.enabled=false"
})
class AnnouncementWritePersistenceTest {

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
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt(String subject) {
        return jwt()
                .jwt(token -> token
                        .subject(subject)
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
