package io.campuscore.restfulapi.notification;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "migration.thesis-read.enabled=false",
        "migration.notifications-read.enabled=true",
        "spring.flyway.enabled=false"
})
class NotificationReadPersistenceTest {

    private static final String STUDENT = "student-user-1";
    private static final String OTHER_USER = "student-user-2";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareReadOnlyFixture() {
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
    void listUsesLegacyEnvelopeAndSubjectScopedPagination() throws Exception {
        Instant newest = Instant.parse("2026-08-19T10:00:00Z");
        insert("n-1", STUDENT, false, newest);
        insert("n-2", STUDENT, true, newest.minusSeconds(60));
        insert("n-3", STUDENT, false, newest.minusSeconds(120));
        insert("other-1", OTHER_USER, false, newest.plusSeconds(60));

        mvc.perform(get("/api/v1/notifications/my")
                        .queryParam("page", "1")
                        .queryParam("limit", "2")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value("n-1"))
                .andExpect(jsonPath("$.data[0].userId").value(STUDENT))
                .andExpect(jsonPath("$.data[0].isRead").value(false))
                .andExpect(jsonPath("$.meta.total").value(3))
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.limit").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(2));

        mvc.perform(get("/api/v1/notifications/my")
                        .queryParam("page", "2")
                        .queryParam("limit", "2")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("n-3"));
    }

    @Test
    void readFilterKeepsLegacyStrictBooleanParsing() throws Exception {
        Instant base = Instant.parse("2026-08-19T10:00:00Z");
        insert("unread", STUDENT, false, base);
        insert("read", STUDENT, true, base.minusSeconds(60));

        mvc.perform(get("/api/v1/notifications/my")
                        .queryParam("isRead", "true")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("read"));

        mvc.perform(get("/api/v1/notifications/my")
                        .queryParam("isRead", "TRUE")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("unread"));
    }

    @Test
    void unreadCountCannotBeChangedByAQueryUserId() throws Exception {
        Instant now = Instant.parse("2026-08-19T10:00:00Z");
        insert("mine", STUDENT, false, now);
        insert("other", OTHER_USER, false, now);

        mvc.perform(get("/api/v1/notifications/my/unread-count")
                        .queryParam("userId", OTHER_USER)
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void anonymousAndUnboundedRequestsFailClosed() throws Exception {
        mvc.perform(get("/api/v1/notifications/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mvc.perform(get("/api/v1/notifications/my")
                        .queryParam("limit", "101")
                        .with(jwt().jwt(token -> token.subject(STUDENT))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
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
}
