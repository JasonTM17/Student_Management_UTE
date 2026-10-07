package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.thesis.service.ThesisNotificationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Spec R7 thesis lifecycle notifications: the fan-out service resolves auth
 * user ids through committed thesis/auth rows and writes inbox rows with the
 * right type, link, and audience scoping. The H2 migration tree has no
 * notifications schema (production owns it), so the fixture table is created
 * per test like the notification write suite does.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_notifications;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisNotificationServiceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisNotificationService notifier;

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
                    is_read BOOLEAN NOT NULL DEFAULT FALSE,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.update("DELETE FROM notifications.notification");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'tn-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'tn-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'tn-%'");
    }

    @Test
    void approvedDecisionNotifiesEveryMemberWithSuccessType() {
        UUID roundId = insertRound("tn-round", "RESULTS_PUBLISHED");
        UUID topicId = insertTopic(roundId, "Đề tài demo Alpha");
        UUID groupId = insertGroup(roundId, "tn-student-1", topicId, "APPROVED");
        insertMember(groupId, roundId, "tn-student-1", 1);
        insertMember(groupId, roundId, "tn-student-2", 2);

        int written = notifier.notifyGroupDecision(groupId, true, null);

        assertThat(written).isEqualTo(2);
        List<String> rows = jdbc.queryForList(
                "SELECT title || '|' || type || '|' || link FROM notifications.notification ORDER BY user_id",
                String.class);
        assertThat(rows).containsExactly(
                "Nhóm khóa luận được duyệt|SUCCESS|/dashboard/thesis",
                "Nhóm khóa luận được duyệt|SUCCESS|/dashboard/thesis");
        String message = jdbc.queryForObject(
                "SELECT message FROM notifications.notification LIMIT 1", String.class);
        assertThat(message).contains("Đề tài demo Alpha");
    }

    @Test
    void rejectedDecisionCarriesReasonAndWarningType() {
        UUID roundId = insertRound("tn-round", "REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "Đề tài demo Beta");
        UUID groupId = insertGroup(roundId, "tn-student-1", topicId, "REJECTED");
        insertMember(groupId, roundId, "tn-student-1", 1);

        int written = notifier.notifyGroupDecision(groupId, false, "Nội dung chưa đủ chiều sâu");

        assertThat(written).isEqualTo(1);
        var row = jdbc.queryForMap(
                "SELECT title, message, type FROM notifications.notification LIMIT 1");
        assertThat(row.get("TITLE")).isEqualTo("Nhóm khóa luận bị từ chối");
        assertThat((String) row.get("MESSAGE")).contains("Nội dung chưa đủ chiều sâu");
        assertThat(row.get("TYPE")).isEqualTo("WARNING");
    }

    @Test
    void resultsPublishedOnlyReachesApprovedGroups() {
        UUID roundId = insertRound("tn-round", "RESULTS_PUBLISHED");
        UUID topicId = insertTopic(roundId, "Đề tài demo Gamma");
        UUID approvedGroup = insertGroup(roundId, "tn-student-1", topicId, "APPROVED");
        insertMember(approvedGroup, roundId, "tn-student-1", 1);
        insertMember(approvedGroup, roundId, "tn-student-2", 2);
        UUID rejectedGroup = insertGroup(roundId, "tn-student-3", topicId, "REJECTED");
        insertMember(rejectedGroup, roundId, "tn-student-3", 1);

        int written = notifier.notifyResultsPublished(roundId);

        assertThat(written).isEqualTo(2);
        List<String> userIds = jdbc.queryForList(
                "SELECT user_id FROM notifications.notification ORDER BY user_id", String.class);
        assertThat(userIds).containsExactly("tn-user-1", "tn-user-2");
    }

    @Test
    void scoreFinalizedNotifiesMembersAndSupervisorsOnce() {
        UUID roundId = insertRound("tn-round", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "Đề tài demo Delta");
        UUID groupId = insertGroup(roundId, "tn-student-1", topicId, "APPROVED");
        insertMember(groupId, roundId, "tn-student-1", 1);
        insertSupervisor(topicId, "tn-lecturer-1", 1);

        int written = notifier.notifyScoreFinalized(topicId);

        assertThat(written).isEqualTo(2);
        List<String> userIds = jdbc.queryForList(
                "SELECT user_id FROM notifications.notification ORDER BY user_id", String.class);
        assertThat(userIds).containsExactly("tn-lecturer-user-1", "tn-user-1");
        String message = jdbc.queryForObject(
                "SELECT message FROM notifications.notification LIMIT 1", String.class);
        // The chair's average must not leak: students only see scores after the
        // round publishes, so the body carries the topic title and no number.
        assertThat(message).contains("Đề tài demo Delta").doesNotContainPattern("\\d\\.\\d");
    }

    @Test
    void supervisorAssignmentNotifiesOnlyAssignedLecturers() {
        UUID roundId = insertRound("tn-round", "REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "Đề tài demo Epsilon");
        ensureLecturer("tn-lecturer-1");
        ensureLecturer("tn-lecturer-2");

        int written = notifier.notifySupervisorsAssigned(topicId, List.of("tn-lecturer-2"));

        assertThat(written).isEqualTo(1);
        var row = jdbc.queryForMap(
                "SELECT user_id, title, link FROM notifications.notification LIMIT 1");
        assertThat(row.get("USER_ID")).isEqualTo("tn-lecturer-user-2");
        assertThat(row.get("LINK")).isEqualTo("/dashboard/lecturer/thesis");
        assertThat((String) row.get("TITLE")).isEqualTo("Bạn được phân công hướng dẫn");
    }

    // ---------- fixtures ----------

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(86_400)), Timestamp.from(now.minusSeconds(43_200)),
                Timestamp.from(now.minusSeconds(43_200)), Timestamp.from(now.plusSeconds(86_400)),
                status);
        return roundId;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, 3, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID(), title, "Notification fixture topic", UUID.randomUUID());
        return topicId;
    }

    private UUID insertGroup(UUID roundId, String leaderStudentId, UUID topicId, String approvalStatus) {
        ensureStudent(leaderStudentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
                groupId, roundId, leaderStudentId, topicId, approvalStatus);
        return groupId;
    }

    private void insertMember(UUID groupId, UUID roundId, String studentId, int memberOrder) {
        ensureStudent(studentId);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, roundId, studentId, memberOrder, memberOrder == 1);
    }

    private void insertSupervisor(UUID topicId, String lecturerId, int order) {
        ensureLecturer(lecturerId);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), topicId, lecturerId, order);
    }

    private void ensureStudent(String studentId) {
        String userId = "tn-user-" + studentId.substring(studentId.lastIndexOf('-') + 1);
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"id\" = ?",
                Integer.class, studentId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Tn', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, userId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "tn-lecturer-user-" + lecturerId.substring(lecturerId.lastIndexOf('-') + 1);
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?",
                Integer.class, lecturerId);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Tn', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, userId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }
}
