package io.campuscore.restfulapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * K14: an admin creating, editing or deleting a notification must leave an
 * {@code campuscore_audit."AdminAudit"} row naming the actor and the affected
 * notification, while the response contracts stay exactly as they were.
 *
 * <p>The audit snapshot must stay free of the notification body: title and
 * message can carry student-specific content, so only ownership and type are
 * recorded.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class NotificationWriteAuditPersistenceTest {

    private static final String STUDENT = "audit-student-1";
    private static final String OTHER_USER = "audit-student-2";
    private static final String ADMIN = "audit-admin-1";
    private static final String GRADE_MESSAGE = "Your course grade is now available.";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareAuditFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    link VARCHAR(500),
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM notifications.notification");

        // AccountStateFilter reads the account row for every /api/v1/** request.
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS \"campuscore_auth\"");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS "campuscore_auth"."User" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "email" VARCHAR(320) NOT NULL,
                    "password" VARCHAR(200) NOT NULL DEFAULT 'password',
                    "firstName" VARCHAR(120) NOT NULL DEFAULT 'Test',
                    "lastName" VARCHAR(120) NOT NULL DEFAULT 'User',
                    "status" VARCHAR(40) NOT NULL DEFAULT 'ACTIVE',
                    "mustChangePassword" BOOLEAN NOT NULL DEFAULT FALSE,
                    "emailVerified" BOOLEAN NOT NULL DEFAULT TRUE,
                    "isSuperAdmin" BOOLEAN NOT NULL DEFAULT FALSE,
                    "failedLoginAttempts" INTEGER NOT NULL DEFAULT 0,
                    "createdAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    "updatedAt" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("DELETE FROM \"campuscore_auth\".\"User\" WHERE \"id\" IN (?, ?, ?)",
                STUDENT, OTHER_USER, ADMIN);
        jdbc.update("""
                INSERT INTO "campuscore_auth"."User"
                ("id", "email", "password", "firstName", "lastName", "status", "emailVerified", "isSuperAdmin", "failedLoginAttempts", "createdAt", "updatedAt")
                VALUES
                (?, ?, 'password', 'Test', 'User', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                (?, ?, 'password', 'Test', 'User', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                (?, ?, 'password', 'Test', 'User', 'ACTIVE', true, false, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                STUDENT, "audit-student1@campuscore.edu",
                OTHER_USER, "audit-student2@campuscore.edu",
                ADMIN, "audit-admin@campuscore.edu");

        jdbc.execute("CREATE SCHEMA IF NOT EXISTS campuscore_audit");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campuscore_audit."AdminAudit" (
                    "id" VARCHAR(120) PRIMARY KEY,
                    "actorId" VARCHAR(120),
                    "actorLabel" VARCHAR(240),
                    "action" VARCHAR(48) NOT NULL,
                    "entityType" VARCHAR(48) NOT NULL,
                    "entityId" VARCHAR(120),
                    "summary" VARCHAR(500),
                    "beforeState" TEXT,
                    "afterState" TEXT,
                    "createdAt" TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\"");
    }

    @Test
    void adminCreateRecordsNotificationCreatedAuditWithoutTheMessageBody() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "audit-student-1",
                                  "title": "Grade published",
                                  "message": "Your course grade is now available.",
                                  "type": "SUCCESS",
                                  "link": "/dashboard/grades"
                                }
                                """))
                .andExpect(status().isCreated());

        String id = onlyNotificationId();
        assertThat(auditCount("NOTIFICATION_CREATED", id)).isEqualTo(1);
        assertThat(auditText("NOTIFICATION_CREATED", id, "actorId")).isEqualTo(ADMIN);
        assertThat(auditText("NOTIFICATION_CREATED", id, "entityType")).isEqualTo("NOTIFICATION");
        String afterState = auditText("NOTIFICATION_CREATED", id, "afterState");
        assertThat(afterState).contains(STUDENT).contains("SUCCESS");
        assertThat(afterState).doesNotContain(GRADE_MESSAGE);
        assertThat(auditText("NOTIFICATION_CREATED", id, "beforeState")).isNull();
    }

    @Test
    void adminUpdateRecordsNotificationUpdatedAuditWithBeforeAndAfterOwnership() throws Exception {
        insert("audit-target", STUDENT, Instant.parse("2026-08-21T01:00:00Z"));

        mvc.perform(put("/api/v1/notifications/audit-target")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "audit-student-2",
                                  "title": "Updated title",
                                  "type": "SUCCESS"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(auditCount("NOTIFICATION_UPDATED", "audit-target")).isEqualTo(1);
        assertThat(auditText("NOTIFICATION_UPDATED", "audit-target", "actorId")).isEqualTo(ADMIN);
        assertThat(auditText("NOTIFICATION_UPDATED", "audit-target", "entityType")).isEqualTo("NOTIFICATION");
        String beforeState = auditText("NOTIFICATION_UPDATED", "audit-target", "beforeState");
        String afterState = auditText("NOTIFICATION_UPDATED", "audit-target", "afterState");
        assertThat(beforeState).contains(STUDENT).contains("INFO");
        assertThat(afterState).contains(OTHER_USER).contains("SUCCESS");
        // The before-state is the truth that lets a reviewer undo: it must keep
        // the previous owner even after the row itself changed.
        assertThat(beforeState).doesNotContain(OTHER_USER);
        assertThat(afterState).doesNotContain("Updated title");
    }

    @Test
    void adminDeleteRecordsNotificationDeletedAuditWithActorAndBeforeState() throws Exception {
        insert("audit-doomed", STUDENT, Instant.parse("2026-08-21T01:00:00Z"));

        mvc.perform(delete("/api/v1/notifications/audit-doomed").with(adminJwt()))
                .andExpect(status().isOk());

        assertThat(auditCount("NOTIFICATION_DELETED", "audit-doomed")).isEqualTo(1);
        assertThat(auditText("NOTIFICATION_DELETED", "audit-doomed", "actorId")).isEqualTo(ADMIN);
        assertThat(auditText("NOTIFICATION_DELETED", "audit-doomed", "entityType")).isEqualTo("NOTIFICATION");
        assertThat(auditText("NOTIFICATION_DELETED", "audit-doomed", "beforeState")).contains(STUDENT).contains("INFO");
        assertThat(auditText("NOTIFICATION_DELETED", "audit-doomed", "afterState")).isNull();
    }

    @Test
    void missingNotificationDeleteWritesNoAuditRow() throws Exception {
        mvc.perform(delete("/api/v1/notifications/audit-missing").with(adminJwt()))
                .andExpect(status().isNotFound());

        assertThat(auditCount("NOTIFICATION_DELETED", "audit-missing")).isZero();
    }

    private String onlyNotificationId() {
        return jdbc.queryForObject(
                "SELECT id FROM notifications.notification WHERE user_id = ?",
                String.class,
                STUDENT);
    }

    private int auditCount(String action, String entityId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"action\" = ? AND \"entityId\" = ?",
                Integer.class,
                action,
                entityId);
        return count == null ? 0 : count;
    }

    private String auditText(String action, String entityId, String column) {
        return jdbc.queryForObject(
                "SELECT \"" + column + "\" FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"action\" = ? AND \"entityId\" = ?",
                String.class,
                action,
                entityId);
    }

    private void insert(String id, String userId, Instant createdAt) {
        jdbc.update(
                "INSERT INTO notifications.notification "
                        + "(id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                "Title " + id,
                "Message " + id,
                "INFO",
                null,
                false,
                null,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject(ADMIN)
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
