package io.campuscore.restfulapi.engagement;

import static io.campuscore.restfulapi.testsupport.CurrentAccountJwt.fixtureJwt;

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
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:repair_AnnouncementStudentFanoutPersistenceTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
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
                announcement_id VARCHAR(120), source_key VARCHAR(160),
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

    @Autowired
    private io.campuscore.restfulapi.engagement.service.AnnouncementStudentNotifier notifier;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

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
                    "curriculumId" VARCHAR(120),
                    "createdAt" TIMESTAMP,
                    "updatedAt" TIMESTAMP
                )
                """);
        // Faculty-target resolution chain (V149) for the reader predicate;
        // the same Curriculum/Lecturer tables carry the department chain.
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Faculty\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"name\" VARCHAR(240),"
                + " \"isActive\" BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Department\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"name\" VARCHAR(240),"
                + " \"facultyId\" VARCHAR(120) NOT NULL,"
                + " \"isActive\" BOOLEAN NOT NULL DEFAULT TRUE)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Curriculum\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"name\" VARCHAR(240),"
                + " \"departmentId\" VARCHAR(120) NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Lecturer\" ("
                + "\"id\" VARCHAR(120) PRIMARY KEY, \"departmentId\" VARCHAR(120) NOT NULL)");
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
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureSchema(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR PRIMARY KEY, \"studentId\" VARCHAR, \"sectionId\" VARCHAR, \"semesterId\" VARCHAR, \"status\" VARCHAR)");
        for (int index = 1; index <= 4; index++) {
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.replaceRoles(jdbc, "fanout-u-" + index, "STUDENT");
        }
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
                        .content("{\"title\":\"Học phí học kỳ 1\",\"content\":\"" + content + "\",\"isGlobal\":true}"))
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
                        .content("{\"title\":\"Thông báo ảnh\",\"content\":\"" + content + "\",\"isGlobal\":true}"))
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
                        .content("{\"title\":\"Báo lỗi fan-out\",\"content\":\"Vẫn phải 201\",\"isGlobal\":true}"))
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
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                + " WHERE \"title\"='Báo lỗi fan-out' AND \"notifiedAt\" IS NULL", Integer.class));
    }

    @Test
    void sectionRecipientsMustBeEnrolledAndMatchYearAndActiveAccount() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" (\"id\" VARCHAR(120) PRIMARY KEY,"
                + " \"studentId\" VARCHAR(120), \"sectionId\" VARCHAR(120), \"semesterId\" VARCHAR(120), \"status\" VARCHAR(40))");
        jdbc.update("DELETE FROM academic.\"Enrollment\"");
        jdbc.update("INSERT INTO academic.\"Enrollment\" (\"id\",\"studentId\",\"sectionId\",\"semesterId\",\"status\") VALUES"
                + " ('in','fanout-st-1','section-1','semester-1','ENROLLED'),"
                + " ('drop','fanout-st-2','section-1','semester-1','DROPPED'),"
                + " ('other','fanout-st-3','section-2','semester-1','ENROLLED')");
        var record = scopedNotice("section-1", List.of(1, 2, 3), null, null, List.of("STUDENT"), false);
        assertEquals(1, notifier.fanOutToActiveStudents(record));
        assertEquals(List.of("fanout-u-1"), jdbc.queryForList("SELECT user_id FROM notifications.notification", String.class));
        jdbc.update("DELETE FROM notifications.notification");
        jdbc.update("UPDATE \"campuscore_auth\".\"User\" SET \"status\"='INACTIVE' WHERE \"id\"='fanout-u-1'");
        assertEquals(0, notifier.fanOutToActiveStudents(record));
        assertEquals(0, notifier.fanOutToActiveStudents(scopedNotice("section-1", List.of(2), null, null, List.of("STUDENT"), false)));
    }

    @Test
    void invisibleExpiredAndArchivedNoticesNeverEnterAnInbox() {
        assertEquals(0, notifier.fanOutToActiveStudents(scopedNotice(null, List.of(), java.time.Instant.EPOCH, null, List.of("STUDENT"), false)));
        assertEquals(0, notifier.fanOutToActiveStudents(scopedNotice(null, List.of(), null, java.time.Instant.EPOCH, List.of("STUDENT"), false)));
        assertEquals(0, notifier.fanOutToActiveStudents(scopedNotice(null, List.of(), null, null, List.of(), false)));
        assertEquals(0, notifier.fanOutToActiveStudents(scopedNotice(null, List.of(), null, null, List.of("LECTURER"), false)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification", Integer.class));
    }

    @Test
    void rollingBackAnnouncementCreationMustNotCommitNotificationContent() {
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try {
                mvc.perform(post("/api/v1/announcements").with(adminJwt("fanout-admin"))
                        .contentType("application/json").content("{\"title\":\"Rollback notice\",\"content\":\"Private preview\",\"isGlobal\":true}"))
                        .andExpect(status().isCreated());
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
            tx.setRollbackOnly();
        });
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM \"engagement\".\"Announcement\" WHERE \"title\"='Rollback notice'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE title='[Announcement] Rollback notice'", Integer.class));
    }

    @Test
    void revokedStudentRoleCannotReceivePrivateStudentNoticeThroughInbox() throws Exception {
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.student(jdbc, "fanout-u-1", "fanout-st-1", 1);
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "fanout-u-1", "revoked-lecturer");
        var currentLecturer = fixtureJwt(jdbc, "fanout-u-1", "LECTURER");
        mvc.perform(post("/api/v1/announcements").with(adminJwt("fanout-admin"))
                .contentType("application/json")
                .content("{\"title\":\"Private student notice\",\"content\":\"Private body\",\"isGlobal\":false,\"targetRoles\":[\"STUDENT\"],\"targetYears\":[1]}"))
                .andExpect(status().isCreated());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id='fanout-u-1'", Integer.class));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/announcements/my").with(currentLecturer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void dualRoleRecipientsMatchTheNoticeLecturerScope() throws Exception {
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.lecturer(jdbc, "fanout-u-1", "recipient-lecturer");
        fixtureJwt(jdbc, "fanout-u-1", "STUDENT", "LECTURER");
        jdbc.update("INSERT INTO academic.\"Enrollment\" VALUES ('scoped-enrollment', 'fanout-st-1', 'scoped-section', 'semester', 'ENROLLED')");
        mvc.perform(post("/api/v1/announcements").with(adminJwt("fanout-admin"))
                .contentType("application/json")
                .content("{\"title\":\"Other lecturer notice\",\"content\":\"Private body\",\"isGlobal\":false,\"targetRoles\":[\"STUDENT\"],\"targetYears\":[1],\"sectionId\":\"scoped-section\",\"lecturerId\":\"other-lecturer\"}"))
                .andExpect(status().isCreated());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notifications.notification WHERE user_id='fanout-u-1'", Integer.class));
    }

    @Test
    void archivingAnnouncementRecallsItsInboxNotifications() throws Exception {
        // A notice fanned out before the linkage column existed carries no
        // announcement_id; it is indistinguishable from authored inbox content
        // and must survive the recall even when the title matches exactly.
        jdbc.update("INSERT INTO notifications.notification "
                + "(id, user_id, title, message, type, link, is_read, created_at, updated_at) "
                + "VALUES ('legacy-unlinked', 'fanout-u-1', ?, 'older preview', 'INFO', "
                + "'/dashboard/announcements', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                "[Announcement] Recalled notice");

        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Recalled notice\",\"content\":\"Body\",\"isGlobal\":true}"))
                .andExpect(status().isCreated());

        String announcementId = jdbc.queryForObject(
                "SELECT \"id\" FROM \"engagement\".\"Announcement\" WHERE \"title\" = 'Recalled notice'",
                String.class);
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));

        mvc.perform(post("/api/v1/announcements/" + announcementId + "/archive")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Withdrawn\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE id = 'legacy-unlinked'",
                Integer.class));
    }

    @Test
    void legacyDeleteAlsoRecallsInboxNotifications() throws Exception {
        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Deleted notice\",\"content\":\"Body\",\"isGlobal\":true}"))
                .andExpect(status().isCreated());

        String announcementId = jdbc.queryForObject(
                "SELECT \"id\" FROM \"engagement\".\"Announcement\" WHERE \"title\" = 'Deleted notice'",
                String.class);
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/announcements/" + announcementId)
                        .with(adminJwt("fanout-admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Announcement deleted successfully"));

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));
    }

    @Test
    void restoringArchivedAnnouncementNeverDuplicatesInboxNotifications() throws Exception {
        mvc.perform(post("/api/v1/announcements")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"title\":\"Restored notice\",\"content\":\"Body\",\"isGlobal\":true}"))
                .andExpect(status().isCreated());

        String announcementId = jdbc.queryForObject(
                "SELECT \"id\" FROM \"engagement\".\"Announcement\" WHERE \"title\" = 'Restored notice'",
                String.class);
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));

        mvc.perform(post("/api/v1/announcements/" + announcementId + "/archive")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Withdrawn\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));

        mvc.perform(post("/api/v1/announcements/" + announcementId + "/restore")
                        .with(adminJwt("fanout-admin"))
                        .contentType("application/json")
                        .content("{\"reason\":\"Reviewed\",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAt").doesNotExist());

        // The notifiedAt marker stays stamped across archive/restore, so the
        // scheduled sweep can never refire this announcement into the inboxes.
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ?",
                Integer.class, announcementId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\""
                        + " WHERE \"id\" = ? AND \"notifiedAt\" IS NOT NULL",
                Integer.class, announcementId));
    }

    private static io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse scopedNotice(
            String section, List<Integer> years, java.time.Instant expires, java.time.Instant archived,
            List<String> roles, boolean global) {
        return new io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse(
                "scoped", "Private title", "Private content", "NORMAL", roles, years, List.of(), List.of(), global,
                null, expires, "publisher", null, null, section, null, null, null, null, null,
                java.time.Instant.now(), java.time.Instant.now(), 0, archived, null, null, null, null);
    }

    private RequestPostProcessor adminJwt(String subject) {
        return fixtureJwt(jdbc, subject, token -> token
                        .subject(subject)
                        .claim("email", subject + "@campuscore.edu")
                        .claim("roles", List.of("ADMIN")), "ADMIN")
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
