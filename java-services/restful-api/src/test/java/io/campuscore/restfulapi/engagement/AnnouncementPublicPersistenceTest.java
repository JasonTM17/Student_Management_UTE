package io.campuscore.restfulapi.engagement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
 * The anonymous homepage feed must expose only editorially PUBLISHED, globally
 * visible, unexpired announcements, ordered by the administrator display order
 * (rows without one last) then newest first — and must never require a token.
 * The PATCH /order route reorders the feed and stays admin-gated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:announcement_public;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AnnouncementPublicPersistenceTest {

    private static final Instant PAST = Instant.parse("2020-01-01T00:00:00Z");
    private static final Instant FUTURE = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant BASE = Instant.parse("2026-08-20T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void preparePublicFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
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
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"AnnouncementAudit\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"Announcement\"");
        // Schema mirrors V3 + V15 + V64 (status, rich media, displayOrder) so the
        // public feed and the PATCH /order route run against production columns.
        jdbc.execute("""
                CREATE TABLE "engagement"."Announcement" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "title" VARCHAR(240) NOT NULL,
                    "content" TEXT NOT NULL,
                    "priority" VARCHAR(20) NOT NULL,
                    "targetRoles" VARCHAR ARRAY NOT NULL,
                    "targetYears" INTEGER ARRAY NOT NULL,
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
                    "categoryId" VARCHAR(60),
                    "status" VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
                    "coverImageUrl" VARCHAR(500),
                    "summary" VARCHAR(500),
                    "slug" VARCHAR(240),
                    "displayOrder" INTEGER
                )
                """);
        jdbc.execute("""
                CREATE TABLE "engagement"."AnnouncementAudit" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "announcementId" VARCHAR(120) NOT NULL,
                    "action" VARCHAR(20) NOT NULL,
                    "actorId" VARCHAR(120) NOT NULL,
                    "actorLabel" VARCHAR(240),
                    "reason" VARCHAR(500) NOT NULL,
                    "version" INTEGER NOT NULL,
                    "beforeState" CLOB,
                    "afterState" CLOB,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL
                )
                """);
    }

    private void insert(
            String id, String status, boolean isGlobal, Instant publishAt,
            Instant expiresAt, Instant archivedAt, Integer displayOrder, Instant createdAt) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            try (java.sql.PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO "engagement"."Announcement" (
                        "id", "title", "content", "priority", "targetRoles", "targetYears",
                        "isGlobal", "publishAt", "expiresAt", "publishedBy", "createdAt",
                        "updatedAt", "version", "archivedAt", "status", "displayOrder")
                    VALUES (?, ?, ?, 'NORMAL', ?, ?, ?, ?, ?, 'admin-user', ?, ?, 0, ?, ?, ?)
                    """)) {
                statement.setString(1, id);
                statement.setString(2, "News " + id);
                statement.setString(3, "<p>Body of " + id + "</p>");
                statement.setArray(4, connection.createArrayOf("VARCHAR", new String[0]));
                statement.setArray(5, connection.createArrayOf("INTEGER", new Integer[0]));
                statement.setBoolean(6, isGlobal);
                timestamp(statement, 7, publishAt);
                timestamp(statement, 8, expiresAt);
                timestamp(statement, 9, createdAt);
                timestamp(statement, 10, createdAt);
                timestamp(statement, 11, archivedAt);
                statement.setString(12, status);
                if (displayOrder == null) {
                    statement.setNull(13, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(13, displayOrder);
                }
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void timestamp(java.sql.PreparedStatement statement, int index, Instant value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setObject(
                    index,
                    java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC),
                    java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        }
    }

    @Test
    void anonymousFeedReturnsOnlyPublishedGlobalUnexpiredRowsInDisplayOrder() throws Exception {
        insert("published-no-order", "PUBLISHED", true, PAST, null, null, null,
                BASE.plusSeconds(900));
        insert("published-ordered", "PUBLISHED", true, PAST, null, null, 2,
                BASE.plusSeconds(600));
        insert("published-first", "PUBLISHED", true, PAST, null, null, 1,
                BASE.plusSeconds(300));
        insert("draft", "DRAFT", true, PAST, null, null, 1, BASE);
        insert("scoped", "PUBLISHED", false, PAST, null, null, 1, BASE);
        insert("archived", "PUBLISHED", true, PAST, null, BASE, 1, BASE);
        insert("future", "PUBLISHED", true, FUTURE, null, null, 1, BASE);
        insert("expired", "PUBLISHED", true, PAST, PAST, null, 1, BASE);

        mvc.perform(get("/api/v1/announcements/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("published-first"))
                .andExpect(jsonPath("$.data[1].id").value("published-ordered"))
                .andExpect(jsonPath("$.data[2].id").value("published-no-order"))
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].targetRoles").doesNotExist())
                .andExpect(jsonPath("$.data[0].targetYears").doesNotExist())
                .andExpect(jsonPath("$.data[0].isGlobal").doesNotExist())
                .andExpect(jsonPath("$.data[0].title").value("News published-first"))
                .andExpect(jsonPath("$.data[0].content").value("<p>Body of published-first</p>"))
                .andExpect(jsonPath("$.data[0].publishAt").value("2020-01-01T00:00:00.000Z"))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(9))
                .andExpect(jsonPath("$.meta.totalPages").value(1));

        // Pagination honours the same ordering: page 2, one row per page is the
        // second display-ordered announcement, not the newest unordered one.
        mvc.perform(get("/api/v1/announcements/public")
                        .queryParam("page", "2")
                        .queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("published-ordered"))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.totalPages").value(3));

        mvc.perform(get("/api/v1/announcements/public")
                        .queryParam("page", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCanAssignDisplayOrderAndLecturerOrStudentCannot() throws Exception {
        insert("reorder-me", "PUBLISHED", true, PAST, null, null, null, BASE);

        mvc.perform(patch("/api/v1/announcements/reorder-me/order")
                        .contentType("application/json")
                        .content("{\"displayOrder\": 3}")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("reorder-me"));

        Integer displayOrder = jdbc.queryForObject(
                "SELECT \"displayOrder\" FROM \"engagement\".\"Announcement\" WHERE \"id\" = 'reorder-me'",
                Integer.class);
        org.assertj.core.api.Assertions.assertThat(displayOrder).isEqualTo(3);

        // A non-admin token is refused even if the filter chain admitted it.
        mvc.perform(patch("/api/v1/announcements/reorder-me/order")
                        .contentType("application/json")
                        .content("{\"displayOrder\": 1}")
                        .with(jwt().jwt(token -> token
                                .subject("student-1")
                                .claim("email", "student-1@campuscore.edu")
                                .claim("roles", java.util.List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        // Validation: negative and missing values are rejected with 400.
        mvc.perform(patch("/api/v1/announcements/reorder-me/order")
                        .contentType("application/json")
                        .content("{\"displayOrder\": -2}")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/announcements/reorder-me/order")
                        .contentType("application/json")
                        .content("{}")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(patch("/api/v1/announcements/unknown/order")
                        .contentType("application/json")
                        .content("{\"displayOrder\": 1}")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminListKeepsDisplayOrderAheadOfRecency() throws Exception {
        insert("newest-unordered", "PUBLISHED", true, PAST, null, null, null,
                BASE.plusSeconds(900));
        insert("ordered-two", "PUBLISHED", true, PAST, null, null, 2, BASE);
        insert("ordered-one", "PUBLISHED", true, PAST, null, null, 1, BASE);
        insert("ordered-zero", "PUBLISHED", true, PAST, null, null, 0,
                BASE.minusSeconds(900));

        // Page order must be displayOrder 0, 1, 2, then the unordered newest row.
        mvc.perform(get("/api/v1/announcements")
                        .queryParam("status", "ACTIVE")
                        .with(jwt().jwt(token -> token
                                .subject("admin-1")
                                .claim("email", "admin-1@campuscore.edu")
                                .claim("roles", java.util.List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").value("ordered-zero"))
                .andExpect(jsonPath("$.data[1].id").value("ordered-one"))
                .andExpect(jsonPath("$.data[2].id").value("ordered-two"))
                .andExpect(jsonPath("$.data[3].id").value("newest-unordered"));
    }
}
