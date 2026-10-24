package io.campuscore.restfulapi.engagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.campuscore.restfulapi.engagement.service.AnnouncementWriteService;
import io.campuscore.restfulapi.engagement.dto.AnnouncementReadDtos.AnnouncementResponse;
import io.campuscore.restfulapi.engagement.dto.AnnouncementWriteDtos.CreateAnnouncementRequest;
import io.campuscore.restfulapi.engagement.dto.AnnouncementWriteDtos.UpdateAnnouncementRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Audience-update regressions: editing targetRoles/targetYears/sectionId or
 * isGlobal on an already-notified announcement must retract the inbox rows of
 * students who fell out of the audience, notify only the newly included ones,
 * and never leave two rows for the same student — including the deliberate
 * re-fan-out a future publishAt re-arms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:repair_AnnouncementAudienceUpdateTest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
class AnnouncementAudienceUpdateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AnnouncementWriteService announcements;

    @Autowired
    private io.campuscore.restfulapi.engagement.service.AnnouncementStudentNotifier notifier;

    @Autowired
    private io.campuscore.restfulapi.engagement.repository.AnnouncementWriteRepository announcementRepository;

    @BeforeEach
    void prepareAudienceFixture() {
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
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    link VARCHAR(500),
                    announcement_id VARCHAR(120),
                    source_key VARCHAR(160),
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS notification_announcement_user_uniq
                ON notifications.notification (announcement_id, user_id)
                """);
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
        jdbc.execute("CREATE TABLE IF NOT EXISTS academic.\"Enrollment\" "
                + "(\"id\" VARCHAR PRIMARY KEY, \"studentId\" VARCHAR, \"sectionId\" VARCHAR, "
                + "\"semesterId\" VARCHAR, \"status\" VARCHAR)");
        io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.ensureSchema(jdbc);
        jdbc.update("DELETE FROM notifications.notification");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\" WHERE \"id\" LIKE 'aud-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'aud-%'");
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"studentId\" LIKE 'aud-%'");
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User"
                ("id", "email", "password", "firstName", "lastName", "status", "emailVerified", "isSuperAdmin", "failedLoginAttempts", "createdAt", "updatedAt")
                VALUES
                ('aud-u-1', 'aud1@campuscore.edu', 'password', 'One', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-u-2', 'aud2@campuscore.edu', 'password', 'Two', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-u-3', 'aud3@campuscore.edu', 'password', 'Three', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-u-4', 'aud4@campuscore.edu', 'password', 'Four', 'Student', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO academic."Student" ("id", "userId", "studentId", "year", "status", "createdAt", "updatedAt")
                VALUES
                ('aud-st-1', 'aud-u-1', 'AU01', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-st-2', 'aud-u-2', 'AU02', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-st-3', 'aud-u-3', 'AU03', 1, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                ('aud-st-4', 'aud-u-4', 'AU04', 2, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        for (int index = 1; index <= 4; index++) {
            io.campuscore.restfulapi.testsupport.AccountAuthorityFixture.replaceRoles(
                    jdbc, "aud-u-" + index, "STUDENT");
        }
    }

    @AfterEach
    void cleanupAudienceRows() {
        jdbc.update("DELETE FROM notifications.notification");
        jdbc.update("DELETE FROM academic.\"Enrollment\" WHERE \"studentId\" LIKE 'aud-%'");
        jdbc.update("DELETE FROM academic.\"Student\" WHERE \"id\" LIKE 'aud-%'");
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\" WHERE \"id\" LIKE 'aud-%'");
    }

    @Test
    void shrinkingTheAudienceRetractsRemovedStudentsAndKeepsTheRest() {
        AnnouncementResponse created = createNotice(1);
        assertEquals(List.of("aud-u-1", "aud-u-3"), notifiedUsers(created.id()));

        ObjectNode shrink = MAPPER.createObjectNode();
        shrink.putArray("targetYears").add(2);
        update(created.id(), shrink);

        assertEquals(List.of("aud-u-2", "aud-u-4"), notifiedUsers(created.id()));
    }

    @Test
    void growingTheAudienceNotifiesOnlyNewlyAddedStudentsWithoutDuplicates() {
        AnnouncementResponse created = createNotice(1);
        assertEquals(List.of("aud-u-1", "aud-u-3"), notifiedUsers(created.id()));

        ObjectNode grow = MAPPER.createObjectNode();
        grow.putArray("targetYears").add(1).add(2);
        update(created.id(), grow);

        List<String> notified = notifiedUsers(created.id());
        assertEquals(List.of("aud-u-1", "aud-u-2", "aud-u-3", "aud-u-4"), notified);
        // One announcement, one row per student — the kept students were not
        // fanned out a second time.
        for (String userId : notified) {
            assertEquals(1, rowCount(created.id(), userId));
        }
    }

    @Test
    void roleRemovalRetractsEveryDeliveredNotice() {
        AnnouncementResponse created = createNotice(null);
        assertEquals(4, notifiedUsers(created.id()).size());

        ObjectNode roles = MAPPER.createObjectNode();
        roles.putArray("targetRoles").add("LECTURER");
        update(created.id(), roles);

        assertTrue(notifiedUsers(created.id()).isEmpty());
    }

    @Test
    void reschedulingReArmsNotificationButTheSecondFanoutStaysDeduplicated() {
        AnnouncementResponse created = createNotice(null);
        assertEquals(4, notifiedUsers(created.id()).size());

        // The deliberate re-arm: moving publishAt into the future clears
        // notifiedAt so the scheduled sweep fires again at the new time.
        update(created.id(), MAPPER.createObjectNode()
                .put("publishAt", "2999-01-01T00:00:00Z"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM \"engagement\".\"Announcement\" WHERE \"id\" = ? AND \"notifiedAt\" IS NOT NULL",
                Integer.class, created.id()));

        // When the new publish time arrives the sweep re-runs the fan-out;
        // nobody may receive a second inbox row for the same announcement.
        jdbc.update("UPDATE \"engagement\".\"Announcement\" SET \"publishAt\" = ? WHERE \"id\" = ?",
                java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(60)), created.id());
        int resent = notifier.fanOutToActiveStudents(
                announcementRepository.findById(created.id()).orElseThrow());
        assertEquals(0, resent);
        for (String userId : notifiedUsers(created.id())) {
            assertEquals(1, rowCount(created.id(), userId));
        }
    }

    // ---------- fixtures ----------

    private AnnouncementResponse createNotice(Integer year) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("title", "Audience notice");
        body.put("content", "Nội dung thông báo");
        body.put("priority", "NORMAL");
        body.putArray("targetRoles").add("STUDENT");
        if (year != null) {
            body.putArray("targetYears").add(year);
        }
        body.put("isGlobal", false);
        AnnouncementResponse created = announcements.create(
                "aud-admin", "Admin", CreateAnnouncementRequest.from(body));
        return created;
    }

    private void update(String id, ObjectNode patch) {
        patch.put("reason", "audience correction");
        patch.put("expectedVersion", version(id));
        announcements.update("aud-admin", "Admin", List.of("ADMIN"), null, id,
                UpdateAnnouncementRequest.from(patch));
    }

    private int version(String id) {
        Integer version = jdbc.queryForObject(
                "SELECT \"version\" FROM \"engagement\".\"Announcement\" WHERE \"id\" = ?",
                Integer.class, id);
        return version == null ? 0 : version;
    }

    private List<String> notifiedUsers(String announcementId) {
        return jdbc.queryForList(
                "SELECT user_id FROM notifications.notification WHERE announcement_id = ? ORDER BY user_id",
                String.class, announcementId);
    }

    private int rowCount(String announcementId, String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE announcement_id = ? AND user_id = ?",
                Integer.class, announcementId, userId);
        return count == null ? 0 : count;
    }
}
