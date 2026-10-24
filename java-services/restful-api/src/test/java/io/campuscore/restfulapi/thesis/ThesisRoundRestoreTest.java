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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

    @MockitoSpyBean
    private NamedParameterJdbcTemplate jdbcSpy;

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
        assertThat(auditAfterState(roundId)).contains("target_source=RECORDED");

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
    void aLegacyCancelledRowWithoutGroupsRestoresToDraft() throws Exception {
        // Rows cancelled before the marker column existed carry NULL; with no
        // live groups nothing proves the round ever opened, so DRAFT is the
        // only target that fabricates no progress.
        UUID roundId = insertRound("rr-round-legacy", "DRAFT");
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', "
                + "cancelled_from_status = NULL, version = version + 1 WHERE id = ?", roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(auditAfterState(roundId)).contains("target_source=LEGACY_DEFAULT");
    }

    @Test
    void aLegacyCancelledRowWithLiveGroupsRequiresAnExplicitTarget() throws Exception {
        UUID roundId = insertRound("rr-round-legacy-groups", "DRAFT");
        insertGroupWithMember(roundId, "rr-legacy-student", "APPROVED");
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', "
                + "cancelled_from_status = NULL, version = version + 1 WHERE id = ?", roundId);

        // The live groups prove registration ran, so restoring straight to
        // DRAFT would silently re-hold seats the admin never chose to revive.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_TARGET_REQUIRED"));

        // Evidence floor: groups exist, so the round was at least
        // REGISTRATION_OPEN — nothing earlier is an honest target.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"DRAFT\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_TARGET_INVALID"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REGISTRATION_CLOSED\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
        assertThat(auditAfterState(roundId)).contains("target_source=EXPLICIT");
    }

    @Test
    void anExplicitTargetMayOnlyReplayOrAdvancePastAnOpenRegistration() throws Exception {
        UUID roundId = insertRound("rr-round-target", "REGISTRATION_OPEN");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Terminal statuses are never valid restore targets.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"CLOSED\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        // A backward target would roll the status over later-phase data.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"DRAFT\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_TARGET_INVALID"));

        // The one override: REGISTRATION_OPEN whose window may have passed can
        // land directly on REGISTRATION_CLOSED.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REGISTRATION_CLOSED\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
        assertThat(auditAfterState(roundId)).contains("target_source=EXPLICIT");
    }

    @Test
    void aTargetEarlierOrLaterThanTheRecordedMarkerIsRejected() throws Exception {
        UUID roundId = insertRound("rr-round-skip", "REGISTRATION_CLOSED");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Backward over registration data.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"PROPOSAL_OPEN\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_TARGET_INVALID"));

        // Skip-ahead: a closed-registration round cannot return to
        // RESULTS-adjacent ground it never reached (and terminal targets are
        // rejected outright as 400).
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REGISTRATION_OPEN\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_RESTORE_TARGET_INVALID"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
    }

    @Test
    void anOpenRegistrationCancelledAfterItsWindowClosedRestoresAsClosed() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("rr-round-expired", "REGISTRATION_OPEN",
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(3_600)));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledFromStatus").value("REGISTRATION_OPEN"));

        // The recorded target is unreachable: the window it would reopen is
        // already gone (and a cancelled round rejects schedule edits).
        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStatus\":\"REGISTRATION_CLOSED\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_CLOSED"));
    }

    @Test
    void restoreLocksMemberStudentRowsBeforeCountingSeatConflicts() throws Exception {
        // The race this pins: a concurrent createGroup holds the Student row
        // while it reads this round's still-CANCELLED status. Restore must take
        // the same Student lock first so that registration either commits (and
        // is counted) or waits until the flip makes the round live again.
        UUID roundId = insertRound("rr-round-lockorder", "REGISTRATION_CLOSED");
        ensureStudent("rr-lockorder-student");
        insertGroupWithMember(roundId, "rr-lockorder-student", "PENDING");
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());
        Mockito.clearInvocations(jdbcSpy);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/restore", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        List<String> statements = Mockito.mockingDetails(jdbcSpy).getInvocations().stream()
                .map(invocation -> invocation.getArguments())
                .filter(arguments -> arguments.length > 0 && arguments[0] instanceof String)
                .map(arguments -> (String) arguments[0])
                .toList();
        int studentLock = indexWhere(statements,
                sql -> sql.contains("campuscore_auth.\"Student\"") && sql.contains("FOR UPDATE"));
        int seatCount = indexWhere(statements,
                sql -> sql.contains("JOIN thesis.thesis_group_member other"));
        assertThat(studentLock)
                .as("the member Student-row lock must be issued inside restore")
                .isGreaterThanOrEqualTo(0);
        assertThat(seatCount)
                .as("the cross-round seat-conflict count must run inside restore")
                .isGreaterThanOrEqualTo(0);
        assertThat(studentLock)
                .as("the Student-row lock must be acquired before the seat-conflict count")
                .isLessThan(seatCount);
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

    private String auditAfterState(UUID entityId) {
        return jdbc.queryForObject(
                "SELECT \"afterState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"entityId\" = ? AND \"action\" = 'THESIS_ROUND_RESTORED' "
                        + "ORDER BY \"createdAt\" DESC LIMIT 1",
                String.class, entityId.toString());
    }

    private static int indexWhere(List<String> values, java.util.function.Predicate<String> predicate) {
        for (int index = 0; index < values.size(); index++) {
            if (predicate.test(values.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private UUID insertGroupWithMember(UUID roundId, String studentId, String groupStatus) {
        ensureStudent(studentId);
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'PENDING')",
                groupId, roundId, studentId, groupStatus);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, studentId);
        return groupId;
    }

    private UUID insertRound(String name, String status) {
        Instant now = Instant.now();
        return insertRound(name, status,
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(86_400)));
    }

    private UUID insertRound(String name, String status, Timestamp registrationStart, Timestamp registrationEnd) {
        UUID roundId = UUID.randomUUID();
        // Schedule-order constraint: lecturer_submit_end <= registration_start
        // and registration_end <= gvpb_deadline, so every bound derives from
        // the caller's window instead of a second Instant.now().
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(registrationStart.toInstant().minusSeconds(7_200)), registrationStart,
                registrationStart, registrationEnd,
                Timestamp.from(registrationEnd.toInstant().plusSeconds(86_400)),
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
