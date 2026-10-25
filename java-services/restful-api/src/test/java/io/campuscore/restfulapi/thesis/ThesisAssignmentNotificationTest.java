package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Assignment signals: naming a counter-reviewer or seating a council member
 * hands that lecturer a deadline-bearing duty, so the assignment must reach
 * their inbox. Previously only the audit row knew about it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_assign_signal;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisAssignmentNotificationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(60) NOT NULL,
                    link VARCHAR(500),
                    announcement_id VARCHAR(120), source_key VARCHAR(160),
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM notifications.notification");
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'ans-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'ans-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'ans-%'"
                + " OR \"id\" LIKE 'thesis-fixture-lecturer-ans-%'");
    }

    @Test
    void reviewerAssignmentNotifiesTheNamedReviewer() throws Exception {
        String reviewer = "ans-reviewer-1";
        String reviewerUser = userIdOfLecturer(lecturer(reviewer));
        String outsiderUser = userIdOfLecturer(lecturer("ans-reviewer-2"));
        UUID roundId = insertRound("ans-round-review", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "ans-topic-review", "PUBLISHED");

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"" + reviewer + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsFor(reviewerUser);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("INFO");
        assertThat(rows.get(0).get("link")).isEqualTo("/dashboard/lecturer/thesis");
        assertThat(notificationsFor(outsiderUser)).isEmpty();
    }

    @Test
    void councilSeatNotifiesTheSeatedLecturer() throws Exception {
        String chairUser = userIdOfLecturer(lecturer("ans-chair-1"));
        UUID roundId = insertRound("ans-round-council", "REGISTRATION_CLOSED");
        UUID councilId = insertCouncil(roundId, "Hội đồng ans-1");

        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"ans-chair-1\",\"memberRole\":\"CHAIR\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsFor(chairUser);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("link")).isEqualTo("/dashboard/lecturer/thesis");
    }

    @Test
    void councilTopicAssignmentNotifiesSeatedMembers() throws Exception {
        String chairUser = userIdOfLecturer(lecturer("ans-chair-2"));
        String secretaryUser = userIdOfLecturer(lecturer("ans-secretary-2"));
        String memberUser = userIdOfLecturer(lecturer("ans-member-2"));
        String outsiderUser = userIdOfLecturer(lecturer("ans-outsider-2"));
        UUID roundId = insertRound("ans-round-council-topic", "REGISTRATION_CLOSED");
        UUID councilId = insertCouncil(roundId, "Hội đồng ans-2");
        UUID topicId = insertTopic(roundId, "ans-topic-council", "PUBLISHED");
        seat(councilId, "ans-chair-2", "CHAIR");
        seat(councilId, "ans-secretary-2", "SECRETARY");
        seat(councilId, "ans-member-2", "MEMBER");
        // The seating notices are not what this case measures.
        jdbc.update("DELETE FROM notifications.notification");

        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());

        assertThat(notificationsFor(chairUser)).isNotEmpty();
        assertThat(notificationsFor(secretaryUser)).isNotEmpty();
        assertThat(notificationsFor(memberUser)).isNotEmpty();
        assertThat(notificationsFor(outsiderUser)).isEmpty();
    }

    // ---------- fixtures ----------

    private List<Map<String, Object>> notificationsFor(String userId) {
        return jdbc.queryForList(
                "SELECT user_id, title, message, type, link FROM notifications.notification WHERE user_id = ?",
                userId);
    }

    private void seat(UUID councilId, String lecturerId, String role) throws Exception {
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"" + lecturerId + "\",\"memberRole\":\"" + role + "\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    private String lecturer(String lecturerId) {
        ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
        return lecturerId;
    }

    private String userIdOfLecturer(String lecturerId) {
        return jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?",
                String.class, lecturerId);
    }

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(30 * 86_400L)),
                Timestamp.from(now.minusSeconds(15 * 86_400L)),
                Timestamp.from(now.minusSeconds(14 * 86_400L)),
                Timestamp.from(now.minusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(20 * 86_400)),
                Timestamp.from(now.plusSeconds(30 * 86_400)),
                status);
        return roundId;
    }

    private UUID insertTopic(UUID roundId, String tag, String status) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'Assignment signal fixture', 2, ?, ?)",
                topicId, roundId, "Đề tài " + tag, status, UUID.randomUUID());
        return topicId;
    }

    private UUID insertCouncil(UUID roundId, String name) {
        UUID councilId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'ans-admin')",
                councilId, roundId, name);
        return councilId;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "admin-user", "ADMIN");
    }
}
