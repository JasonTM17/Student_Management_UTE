package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Cancellation is reversible: cancelRound records the status it replaces, the
 * restore endpoint replays it (DRAFT for rows cancelled before the marker
 * column existed), and the flip is refused when students already hold an
 * active seat in another live round or when the round froze historical
 * small-group data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_round_restore;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisRoundRestoreTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'rr-%' OR \"actorId\" LIKE 'rr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rr-%'");
    }

    @Test
    void cancelCapturesThePriorStatusAndRestoreReplaysIt() throws Exception {
        UUID roundId = insertRound("rr-round-cancelled", "REGISTRATION_CLOSED");

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledFromStatus").value("REGISTRATION_CLOSED"));
        assertThat(jdbc.queryForObject(
                "SELECT cancelled_from_status FROM thesis.thesis_registration_round WHERE id = ?",
                String.class, roundId)).isEqualTo("REGISTRATION_CLOSED");

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"))
                .andExpect(jsonPath("$.cancelledFromStatus").doesNotExist());
        assertThat(jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_registration_round WHERE id = ?",
                Long.class, roundId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT cancelled_from_status FROM thesis.thesis_registration_round WHERE id = ?",
                String.class, roundId)).isNull();

        List<String> actions = auditActions(roundId);
        assertThat(actions).contains("THESIS_ROUND_CANCELLED", "THESIS_ROUND_RESTORED");

        // A restored round is live again: it can be cancelled a second time,
        // and that cancel records the same previous status.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledFromStatus").value("REGISTRATION_CLOSED"));
    }

    @Test
    void onlyACancelledRoundCanBeRestored() throws Exception {
        UUID roundId = insertRound("rr-round-live", "REGISTRATION_CLOSED");

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
    }

    @Test
    void aSecondRestoreIsRejected() throws Exception {
        UUID roundId = insertRound("rr-round-restored", "DRAFT");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
    }

    @Test
    void aLegacyCancelledRowRestoresToDraft() throws Exception {
        // Rows cancelled before the marker column existed carry NULL; they
        // restore to DRAFT so the admin re-advances the round deliberately
        // instead of guessing a status nobody recorded.
        UUID roundId = insertRound("rr-round-legacy", "DRAFT");
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', "
                + "cancelled_from_status = NULL, version = version + 1 WHERE id = ?", roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void anExplicitLiveTargetOverridesTheRecordedStatus() throws Exception {
        UUID roundId = insertRound("rr-round-target", "REGISTRATION_OPEN");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Terminal statuses are never valid restore targets.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CLOSED\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"DRAFT\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void restoreIsRejectedWhenAMemberJoinedAnotherLiveRound() throws Exception {
        UUID cancelled = insertRound("rr-round-old", "REGISTRATION_CLOSED");
        UUID otherRound = insertRound("rr-round-new", "REGISTRATION_OPEN");
        ensureStudent("rr-joiner");
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, 'rr-joiner', 'SUBMITTED', 'PENDING')",
                groupId, cancelled);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 'rr-joiner', 1, TRUE)",
                UUID.randomUUID(), groupId, cancelled);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", cancelled).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // The cancelled round holds no seat: the student can register in the
        // other open round while it stays cancelled.
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + otherRound + "\"}")
                        .with(studentJwt("rr-joiner")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", cancelled).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_MEMBER_CONFLICT"));

        // Cancelling the other round frees the seat; the restore then succeeds.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", otherRound).with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", cancelled).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
    }

    @Test
    void restoreRefusesToReopenAFrozenHistoricalGroup() throws Exception {
        UUID roundId = insertRound("rr-round-frozen", "REGISTRATION_CLOSED");
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, "
                        + "approval_status, legacy_group_size_exception) "
                        + "VALUES (?, ?, 'rr-frozen-leader', 'SUBMITTED', 'APPROVED', TRUE)",
                groupId, roundId);
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_FROZEN_HISTORY"));
    }

    @Test
    void studentsAndLecturersCannotRestore() throws Exception {
        ensureStudent("rr-student-guard");
        ensureLecturer("rr-lecturer-guard");
        UUID roundId = insertRound("rr-round-guard", "DRAFT");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(studentJwt("rr-student-guard")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(lecturerJwt("rr-lecturer-guard")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelledRoundProgressReportsTheCancellation() throws Exception {
        UUID roundId = insertRound("rr-round-progress", "REGISTRATION_OPEN");
        ensureStudent("rr-progress-student");
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, 'rr-progress-student', 'SUBMITTED', 'PENDING')",
                groupId, roundId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, 'rr-progress-student', 1, TRUE)",
                UUID.randomUUID(), groupId, roundId);

        mvc.perform(get("/api/v1/thesis/me/progress").param("roundId", roundId.toString())
                        .with(studentJwt("rr-progress-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationState").value("PARTICIPATING"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/thesis/me/progress").param("roundId", roundId.toString())
                        .with(studentJwt("rr-progress-student")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roundStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.participationState").value("NOT_PARTICIPATING"))
                .andExpect(jsonPath("$.attentionState").value("ROUND_CANCELLED"));
    }

    // ---------- fixtures ----------

    private List<String> auditActions(UUID entityId) {
        return jdbc.queryForList(
                "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                String.class, entityId.toString());
    }

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(7_200)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(172_800)),
                status);
        return roundId;
    }

    private void ensureStudent(String studentId) {
        String userId = "rr-user-" + studentId;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Spec', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "rr-user-" + lecturerId;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Spec', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" "
                        + "(\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor truongKhoaJwt() {
        ThesisAuthorityFixture.role(jdbc, "rr-user-tk", "TRUONG_KHOA");
        Integer bound = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'rr-tk-lecturer'",
                Integer.class);
        if (bound == null || bound == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                    + "VALUES ('rr-tk-lecturer', 'rr-user-tk', 'department-demo', 'GV-rr-tk', TRUE)");
        }
        return ThesisAuthorityFixture.role(jdbc, "rr-user-tk", "TRUONG_KHOA");
    }
}
