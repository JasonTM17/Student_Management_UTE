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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cross-role phase signal: when governance moves a round into a new phase the
 * roles that must act next receive an in-app notification. Every lifecycle
 * transition previously told only the publishing path, so lecturers learned
 * about an open proposal window and students about an open registration
 * window only by reloading the workspace.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_phase_signal;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisPhaseTransitionNotificationTest {

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
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'phs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'phs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'phs-%'"
                + " OR \"id\" LIKE 'thesis-fixture-lecturer-phs-%'");
    }

    @Test
    void openProposalsNotifiesActiveLecturersOnly() throws Exception {
        String lecturerUser = userIdOfLecturer(lecturer("phs-lecturer-active"));
        insertInactiveLecturer("phs-lecturer-inactive", "phs-user-inactive");
        UUID roundId = insertRound("phs-round-proposals", "DRAFT", false);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsFor(lecturerUser);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("type")).isEqualTo("INFO");
        assertThat(rows.get(0).get("link")).isEqualTo("/dashboard/lecturer/thesis");
        assertThat(notificationsFor("phs-user-inactive")).isEmpty();
    }

    @Test
    void openRegistrationNotifiesActiveStudents() throws Exception {
        String studentUser = userIdOfStudent(student("phs-student-open"));
        insertInactiveStudent("phs-student-inactive", "phs-user-student-inactive");
        UUID roundId = insertRound("phs-round-registration", "DRAFT", true);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        List<Map<String, Object>> rows = notificationsFor(studentUser);
        assertThat(rows).isNotEmpty();
        assertThat(rows.stream()
                .anyMatch(row -> "INFO".equals(row.get("type"))
                        && "/dashboard/thesis".equals(row.get("link"))))
                .isTrue();
        assertThat(notificationsFor("phs-user-student-inactive")).isEmpty();
    }

    @Test
    void publishProposalsNotifiesStudentsAboutTheCatalog() throws Exception {
        String studentUser = userIdOfStudent(student("phs-student-catalog"));
        UUID roundId = insertRound("phs-round-catalog", "DRAFT", true);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        assertThat(notificationsFor(studentUser)).isNotEmpty();
    }

    @Test
    void cancelNotifiesOnlyRoundParticipants() throws Exception {
        String memberUser = userIdOfStudent(student("phs-student-member"));
        String outsiderUser = userIdOfStudent(student("phs-student-outsider"));
        UUID roundId = insertRound("phs-round-cancel", "REGISTRATION_OPEN", true);
        UUID topicId = insertTopic(roundId, "phs-topic", "PUBLISHED");
        UUID groupId = insertGroup(roundId, "phs-student-member", topicId, "APPROVED");
        insertMember(groupId, roundId, "phs-student-member", 1);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId)
                        .with(adminJwt()))
                .andExpect(status().isOk());

        assertThat(notificationsFor(memberUser)).isNotEmpty();
        assertThat(notificationsFor(outsiderUser)).isEmpty();
    }

    // ---------- fixtures ----------

    private List<Map<String, Object>> notificationsFor(String userId) {
        return jdbc.queryForList(
                "SELECT user_id, title, message, type, link FROM notifications.notification WHERE user_id = ?",
                userId);
    }

    private String student(String studentId) {
        ThesisAuthorityFixture.student(jdbc, studentId);
        return studentId;
    }

    private String lecturer(String lecturerId) {
        ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
        return lecturerId;
    }

    private String userIdOfStudent(String studentId) {
        return jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Student\" WHERE \"id\" = ?",
                String.class, studentId);
    }

    private String userIdOfLecturer(String lecturerId) {
        return jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?",
                String.class, lecturerId);
    }

    private void insertInactiveLecturer(String lecturerId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Phs', 'Inactive', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@fixture.test");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, FALSE)",
                lecturerId, userId, "PHS-" + lecturerId);
    }

    private void insertInactiveStudent(String studentId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Phs', 'Inactive', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@fixture.test");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\", \"status\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP, 'SUSPENDED')",
                studentId, userId, "PHS-" + studentId);
    }

    /**
     * {@code registrationLive} selects a schedule that satisfies the
     * thesis_round_schedule_order_valid constraint ({@code registration_start
     * >= lecturer_submit_end}) while containing "now" inside the registration
     * window — required for the REGISTRATION_OPEN transition. Otherwise the
     * lecturer window stays live and registration sits in the future.
     */
    private UUID insertRound(String name, String status, boolean registrationLive) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant lecturerEnd = registrationLive
                ? now.minusSeconds(15 * 86_400)
                : now.plusSeconds(14 * 86_400);
        Instant registrationStart = registrationLive
                ? now.minusSeconds(3_600)
                : lecturerEnd.plusSeconds(86_400);
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(30 * 86_400L)),
                Timestamp.from(lecturerEnd),
                Timestamp.from(registrationStart),
                Timestamp.from(registrationStart.plusSeconds(30 * 86_400)),
                Timestamp.from(registrationStart.plusSeconds(45 * 86_400)),
                Timestamp.from(registrationStart.plusSeconds(60 * 86_400)),
                status);
        return roundId;
    }

    private UUID insertTopic(UUID roundId, String tag, String status) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'Phase signal fixture', 2, ?, ?)",
                topicId, roundId, "Đề tài " + tag, status, UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroup(UUID roundId, String leaderStudentId, UUID topicId, String approvalStatus) {
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
                groupId, roundId, leaderStudentId, topicId, approvalStatus);
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, String studentId, int memberOrder) {
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, roundId, studentId, memberOrder, memberOrder == 1);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "admin-user", "ADMIN");
    }
}
