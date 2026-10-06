package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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

/**
 * R7 fan-out regression: creating an announcement must write exactly one
 * in-app notification per ACTIVE student (joined to a real auth account), and
 * a broken notification side must never poison the announcement transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class AnnouncementStudentFanoutPersistenceTest {

    private static final String TITLE = "[Announcement] Học phí học kỳ 1";
    private static final String NOTIFICATIONS_DDL = """
            CREATE TABLE IF NOT EXISTS notifications.notification (
                id VARCHAR(120) PRIMARY KEY,
                user_id VARCHAR(120) NOT NULL,
                title VARCHAR(240) NOT NULL,
                message VARCHAR(2000) NOT NULL,
                type VARCHAR(40) NOT NULL,
                link VARCHAR(500),
                is_read BOOLEAN NOT NULL DEFAULT FALSE,
                read_at TIMESTAMP,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
            """;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFanoutFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"engagement\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS academic");
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"AnnouncementAudit\"");
        jdbc.execute("DROP TABLE IF EXISTS \"engagement\".\"Announcement\"");
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
                    "displayOrder" INTEGER,
                    "notifiedAt" TIMESTAMP WITH TIME ZONE
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
        // The fan-out roster reads academic."Student"; other classes drop and
        // recreate it too, so this fixture owns the full shape for its run.
        jdbc.execute("DROP TABLE IF EXISTS academic.\"Student\"");
        jdbc.execute("""
                CREATE TABLE academic."Student" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "userId" VARCHAR(120),
                    "studentId" VARCHAR(120),
                    "year" INTEGER,
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "admissionDate" TIMESTAMP,
                    "createdAt" TIMESTAMP,
                    "updatedAt" TIMESTAMP
                )
                """);
        // Auth table shape mirrors NotificationWritePersistenceTest so both
        // ordering hypotheses (this class first, or the auth fixtures first)
        // accept the seeded rows. It is never dropped here: other classes
        // rely on the auth accounts the H2 migrations seeded.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "password" VARCHAR(200) NOT NULL DEFAULT 'password',
                    "firstName" VARCHAR(120) NOT NULL DEFAULT 'Test',
                    "lastName" VARCHAR(120) NOT NULL DEFAULT 'User',
                    "avatar" VARCHAR(500),
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "emailVerified" BOOLEAN NOT NULL DEFAULT TRUE,
                    "isSuperAdmin" BOOLEAN NOT NULL DEFAULT FALSE,
                    "failedLoginAttempts" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("DROP TABLE IF EXISTS notifications.notification");
        jdbc.execute(NOTIFICATIONS_DDL);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\" WHERE \"id\" LIKE 'fanout-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'fanout-%'");
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User"
                ("id", "email", "password", "firstName", "lastName", "status", "emailVerified", "isSuperAdmin", "failedLoginAttempts", "createdAt", "updatedAt")
                VALUES
                ('fanout-u-1', 'fanout1@campuscore.edu', 'password', 'Một', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-u-2', 'fanout2@campuscore.edu', 'password', 'Hai', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-u-3', 'fanout3@campuscore.edu', 'password', 'Ba', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-u-4', 'fanout4@campuscore.edu', 'password', 'Bốn', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO academic."Student" ("id", "userId", "studentId", "year", "status", "createdAt", "updatedAt")
                VALUES
                ('fanout-st-1', 'fanout-u-1', 'DH01', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-st-2', 'fanout-u-2', 'DH02', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-st-3', 'fanout-u-3', 'DH03', 3, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-st-4', 'fanout-u-4', 'DH04', 4, 'GRADUATED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('fanout-st-5', 'fanout-u-ghost', 'DH05', 5, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }

    @AfterEach
    void cleanupFanoutRows() {
        // The fail-soft test drops this table on purpose; recreate it first so
        // the shared in-memory database is left in its ordinary shape for the
        // classes that run after this one.
        jdbc.execute(NOTIFICATIONS_DDL);
        jdbc.update("DELETE FROM notifications.notification WHERE title = ?", TITLE);
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'fanout-%'");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\" WHERE \"id\" LIKE 'fanout-%'");
    }

    @Test
    void creatingAnnouncementNotifiesExactlyTheActiveStudentsWithPrefixTitleAndCappedMessage() throws Exception {
        // 200 code points of "á": code-point truncation must not split a glyph.
        String content = "á".repeat(200);

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Học phí học kỳ 1\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Học phí học kỳ 1"));

        List<String> recipients = jdbc.queryForList(
                "SELECT user_id FROM notifications.notification WHERE title = ? ORDER BY user_id",
                String.class, TITLE);
        // 3 ACTIVE students with an auth account; the GRADUATED row and the
        // student whose userId has no auth account are both excluded.
        assertEquals(List.of("fanout-u-1", "fanout-u-2", "fanout-u-3"), recipients);

        Integer shaped = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification"
                        + " WHERE title = ? AND type = 'INFO' AND link = '/dashboard/announcements'"
                        + " AND is_read = FALSE",
                Integer.class, TITLE);
        assertEquals(3, shaped);

        Set<String> messages = new HashSet<>(jdbc.queryForList(
                "SELECT message FROM notifications.notification WHERE title = ?", String.class, TITLE));
        assertEquals(1, messages.size());
        assertEquals("á".repeat(160), messages.iterator().next());
    }

    @Test
    void notificationPreviewCarriesPlainTextAndNeverATruncatedTag() throws Exception {
        // A body that opens with a base64 <img> used to be capped at 160 code
        // points BEFORE tags were stripped, producing an unterminated "<img…"
        // fragment inside every student notification preview.
        String content = "<img src=\\\"data:image/png;base64,abcdef\\\" alt=\\\"x\\\">"
                + "<p><strong>Đợt đăng ký đồ án</strong> mở từ 20/01.</p>";

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Thông báo ảnh\",\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());

        List<String> messages = jdbc.queryForList(
                "SELECT DISTINCT message FROM notifications.notification WHERE title = ?",
                String.class, "[Announcement] Thông báo ảnh");
        assertEquals(1, messages.size());
        String preview = messages.get(0);
        org.junit.jupiter.api.Assertions.assertFalse(preview.contains("<"), preview);
        org.junit.jupiter.api.Assertions.assertFalse(preview.contains("base64"), preview);
        org.junit.jupiter.api.Assertions.assertTrue(preview.contains("Đợt đăng ký đồ án"), preview);
        org.junit.jupiter.api.Assertions.assertTrue(preview.length() <= 160, preview);
    }

    @Test
    void brokenFanoutLeavesTheAnnouncementAndItsAuditCommitted() throws Exception {
        // Fail-soft proof: the fan-out transaction target no longer exists.
        jdbc.execute("DROP TABLE notifications.notification");

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Báo lỗi fan-out\",\"content\":\"Vẫn phải 201\"}"))
                .andExpect(status().isCreated());

        Integer announcements = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\" WHERE \"title\" = 'Báo lỗi fan-out'",
                Integer.class);
        assertNotNull(announcements);
        assertEquals(1, announcements);
        Integer audits = jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"AnnouncementAudit\" a"
                        + " JOIN \"engagement\".\"Announcement\" n ON n.\"id\" = a.\"announcementId\""
                        + " WHERE n.\"title\" = 'Báo lỗi fan-out' AND a.\"action\" = 'CREATED'",
                Integer.class);
        assertNotNull(audits);
        assertEquals(1, audits);
    }

    private static RequestPostProcessor adminJwt(String subject) {
        return jwt()
                .jwt(token -> token
                        .subject(subject)
                        .claim("email", subject + "@campuscore.edu")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
