package io.campuscore.restfulapi.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.flyway.enabled=false"
})
class NotificationWritePersistenceTest {

    private static final String STUDENT = "student-user-1";
    private static final String OTHER_USER = "student-user-2";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareWriteFixture() {
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
    }

    @Test
    void markReadUpdatesOnlyOwnedNotificationAndReturnsLegacyShape() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("mine-unread", STUDENT, false, now);
        insert("other-unread", OTHER_USER, false, now.plusSeconds(60));

        mvc.perform(patch("/api/v1/notifications/my/mine-unread/read")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mine-unread"))
                .andExpect(jsonPath("$.userId").value(STUDENT))
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").exists());

        assertThat(readFlag("mine-unread")).isTrue();
        assertThat(readAt("mine-unread")).isNotNull();
        assertThat(readFlag("other-unread")).isFalse();
        assertThat(readAt("other-unread")).isNull();
    }

    @Test
    void markReadFailsClosedForMissingOrOtherUserNotification() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("other-unread", OTHER_USER, false, now);

        mvc.perform(patch("/api/v1/notifications/my/missing/read")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        mvc.perform(patch("/api/v1/notifications/my/other-unread/read")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"));

        assertThat(readFlag("other-unread")).isFalse();
    }

    @Test
    void markAllReadCountsOnlyCurrentUsersUnreadNotifications() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("mine-unread-1", STUDENT, false, now);
        insert("mine-unread-2", STUDENT, false, now.minusSeconds(60));
        insert("mine-read", STUDENT, true, now.minusSeconds(120));
        insert("other-unread", OTHER_USER, false, now.plusSeconds(60));

        mvc.perform(patch("/api/v1/notifications/my/read-all")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        assertThat(unreadCount(STUDENT)).isZero();
        assertThat(unreadCount(OTHER_USER)).isEqualTo(1);
    }

    @Test
    void adminCreatePreservesLegacyDefaultsAndResponseShape() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "student-user-1",
                                  "title": "Grade published",
                                  "message": "Your course grade is now available.",
                                  "type": "SUCCESS",
                                  "link": "/dashboard/grades"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").value(STUDENT))
                .andExpect(jsonPath("$.title").value("Grade published"))
                .andExpect(jsonPath("$.message").value("Your course grade is now available."))
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.link").value("/dashboard/grades"))
                .andExpect(jsonPath("$.isRead").value(false))
                .andExpect(jsonPath("$.readAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());

        assertThat(totalRows()).isEqualTo(1);
        assertThat(unreadCount(STUDENT)).isEqualTo(1);
    }

    @Test
    void adminCreateFailsClosedForInvalidBodyAndStudentRole() throws Exception {
        mvc.perform(post("/api/v1/notifications")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "student-user-1",
                                  "title": "Payment posted",
                                  "message": "Your tuition payment was received.",
                                  "type": "ALERT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/notifications")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "student-user-1",
                                  "message": "Missing title",
                                  "type": "INFO"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/notifications")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unexpected\":\"value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(post("/api/v1/notifications")
                        .with(jwt().jwt(token -> token
                                .subject(STUDENT)
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "student-user-1",
                                  "title": "Student create",
                                  "message": "Forbidden",
                                  "type": "INFO"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(totalRows()).isZero();
    }

    @Test
    void deleteMyNotificationPreservesLegacySuccessAndOwnershipBoundary() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("mine", STUDENT, false, now);
        insert("other", OTHER_USER, false, now.plusSeconds(60));

        mvc.perform(delete("/api/v1/notifications/my/mine")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification deleted successfully"));

        mvc.perform(delete("/api/v1/notifications/my/other")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HTTP_403"))
                .andExpect(jsonPath("$.message").value("Cannot delete this notification"));

        assertThat(rowCount("mine")).isZero();
        assertThat(rowCount("other")).isEqualTo(1);
    }

    @Test
    void adminDeletePreservesLegacySuccessAndNotFoundBoundary() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("target", STUDENT, false, now);
        insert("other", OTHER_USER, false, now.plusSeconds(60));

        mvc.perform(delete("/api/v1/notifications/target").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification deleted successfully"));

        mvc.perform(delete("/api/v1/notifications/missing").with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"))
                .andExpect(jsonPath("$.message").value("Notification not found"));

        mvc.perform(delete("/api/v1/notifications/other")
                        .with(jwt().jwt(token -> token
                                .subject(STUDENT)
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(rowCount("target")).isZero();
        assertThat(rowCount("other")).isEqualTo(1);
    }

    @Test
    void adminUpdatePreservesPartialLegacyShapeAndAllowsLinkClear() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("target", STUDENT, false, now);

        mvc.perform(put("/api/v1/notifications/target")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "student-user-2",
                                  "title": "Updated title",
                                  "message": "Updated message",
                                  "type": "SUCCESS",
                                  "link": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("target"))
                .andExpect(jsonPath("$.userId").value(OTHER_USER))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.message").value("Updated message"))
                .andExpect(jsonPath("$.type").value("SUCCESS"))
                .andExpect(jsonPath("$.link").doesNotExist())
                .andExpect(jsonPath("$.isRead").value(false));

        assertThat(textValue("target", "user_id")).isEqualTo(OTHER_USER);
        assertThat(textValue("target", "title")).isEqualTo("Updated title");
        assertThat(textValue("target", "message")).isEqualTo("Updated message");
        assertThat(textValue("target", "type")).isEqualTo("SUCCESS");
        assertThat(textValue("target", "link")).isNull();
    }

    @Test
    void adminUpdateFailsClosedForInvalidBodyMissingNotificationAndStudentRole() throws Exception {
        Instant now = Instant.parse("2026-08-21T01:00:00Z");
        insert("target", STUDENT, false, now);

        mvc.perform(put("/api/v1/notifications/target")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"ALERT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/notifications/target")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"unexpected\":\"value\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(put("/api/v1/notifications/missing")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("HTTP_404"))
                .andExpect(jsonPath("$.message").value("Notification not found"));

        mvc.perform(put("/api/v1/notifications/target")
                        .with(jwt().jwt(token -> token
                                .subject(STUDENT)
                                .claim("roles", List.of("STUDENT")))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Student edit\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        assertThat(textValue("target", "title")).isEqualTo("Title target");
        assertThat(textValue("target", "type")).isEqualTo("INFO");
    }

    private void insert(String id, String userId, boolean read, Instant createdAt) {
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
                read,
                read ? Timestamp.from(createdAt.plusSeconds(1)) : null,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt));
    }

    private boolean readFlag(String id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT is_read FROM notifications.notification WHERE id = ?",
                Boolean.class,
                id));
    }

    private Timestamp readAt(String id) {
        return jdbc.queryForObject(
                "SELECT read_at FROM notifications.notification WHERE id = ?",
                Timestamp.class,
                id);
    }

    private int unreadCount(String userId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE user_id = ? AND is_read = FALSE",
                Integer.class,
                userId);
        return count == null ? 0 : count;
    }

    private int totalRows() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification",
                Integer.class);
        return count == null ? 0 : count;
    }

    private int rowCount(String id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE id = ?",
                Integer.class,
                id);
        return count == null ? 0 : count;
    }

    private String textValue(String id, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM notifications.notification WHERE id = ?",
                String.class,
                id);
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
