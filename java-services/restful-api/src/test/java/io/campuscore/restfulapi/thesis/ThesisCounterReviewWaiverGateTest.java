package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisRoundReadService;
import io.campuscore.restfulapi.exception.DomainException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The counter-review waiver is reserved for a future postgraduate round
 * level: every shipped undergraduate round type always requires a
 * counter-reviewer, so the waive path refuses with
 * COUNTER_REVIEW_WAIVER_NOT_SUPPORTED and writes nothing. Revocation is never
 * gated — a waiver already stored on a topic can always be cleared — and a
 * topic without a waiver stays bound to the counter-review requirement when
 * the council finalizes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_waiver_gate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisCounterReviewWaiverGateTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ThesisCouncilService councils;
    @Autowired
    private ThesisRoundReadService roundReads;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'wg-%' OR \"actorId\" LIKE 'wg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'wg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'wg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'wg-%'");
    }

    @Test
    void waiveIsRefusedOnEveryShippedRoundType() {
        for (String type : List.of("MON_HOC", "NCKH", "TLCN", "KLTN")) {
            UUID roundId = insertRound("Wg Round " + type, type, "REGISTRATION_CLOSED",
                    Instant.now().plusSeconds(86_400));
            UUID topicId = insertTopic(roundId, "Wg Topic " + type);
            assignReviewerRow(topicId, "wg-rev-" + type.toLowerCase());

            assertThatThrownBy(() -> councils.waiveCounterReview(topicId, "gate off", admin()))
                    .isInstanceOf(DomainException.class)
                    .isInstanceOfSatisfying(DomainException.class,
                            e -> assertThat(e.getCode()).isEqualTo("COUNTER_REVIEW_WAIVER_NOT_SUPPORTED"));
            // The refusal must not write: no waiver marker, no audit row.
            assertThat(jdbc.queryForObject(
                    "SELECT gvpb_waived_reason FROM thesis.thesis_topic WHERE id = ?",
                    String.class, topicId)).isNull();
            assertThat(auditCount(topicId.toString(), "GVPB_WAIVED")).isZero();
        }
    }

    @Test
    void waiveEndpointAnswersTheStableConflictCode() throws Exception {
        UUID roundId = insertRound("Wg Round Api", "KLTN", "REGISTRATION_CLOSED",
                Instant.now().plusSeconds(86_400));
        UUID topicId = insertTopic(roundId, "Wg Api Topic");
        assignReviewerRow(topicId, "wg-rev-api");

        mvc.perform(post("/api/v1/thesis/topics/{id}/gvpb-waiver", topicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"gate off\"}")
                        .with(ThesisAuthorityFixture.role(jdbc, "wg-admin", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNTER_REVIEW_WAIVER_NOT_SUPPORTED"));
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_waived_reason FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isNull();
    }

    @Test
    void theRoleGateStillRunsBeforeTheCapabilityCheck() throws Exception {
        UUID roundId = insertRound("Wg Round Role", "KLTN", "REGISTRATION_CLOSED",
                Instant.now().plusSeconds(86_400));
        UUID topicId = insertTopic(roundId, "Wg Role Topic");
        assignReviewerRow(topicId, "wg-rev-role");

        mvc.perform(post("/api/v1/thesis/topics/{id}/gvpb-waiver", topicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")
                        .with(ThesisAuthorityFixture.student(jdbc, "wg-student")))
                .andExpect(status().isForbidden());
    }

    @Test
    void revokeStillClearsAStoredWaiverWhenTheGateIsOff() {
        UUID roundId = insertRound("Wg Round Revoke", "KLTN", "REGISTRATION_CLOSED",
                Instant.now().plusSeconds(86_400));
        UUID topicId = insertTopic(roundId, "Wg Revoke Topic");
        // A waiver granted while the capability was on stays valid data; the
        // operator must always be able to re-arm the counter-review.
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_waived_reason = 'legacy waiver', "
                + "gvpb_waived_by = 'wg-admin', gvpb_waived_at = CURRENT_TIMESTAMP WHERE id = ?",
                topicId);

        councils.revokeCounterReviewWaiver(topicId, "counter-review reinstated", admin());

        assertThat(jdbc.queryForObject(
                "SELECT gvpb_waived_reason FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isNull();
        assertThat(auditCount(topicId.toString(), "GVPB_WAIVER_REVOKED")).isEqualTo(1);
    }

    @Test
    void anUnwaivedTopicStillNeedsItsCounterReviewToFinalize() {
        UUID roundId = insertRound("Wg Round Finalize", "KLTN", "REGISTRATION_CLOSED",
                Instant.now().plusSeconds(86_400));
        UUID councilId = insertCouncil(roundId, "Wg Council");
        seat(councilId, "wg-chair", "CHAIR");
        seat(councilId, "wg-secretary", "SECRETARY");
        seat(councilId, "wg-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Wg Finalize Topic");
        insertApprovedGroup(roundId, topicId, "wg-fin");
        assignCouncilTopic(councilId, topicId);
        submitDefense(councilId, topicId, "wg-chair", 8);
        submitDefense(councilId, topicId, "wg-secretary", 8);
        submitDefense(councilId, topicId, "wg-member", 8);

        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("wg-chair")))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("REVIEW_SCORE_REQUIRED"));
        assertThat(jdbc.queryForObject(
                "SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                BigDecimal.class, topicId)).isNull();
    }

    @Test
    void theRoundReadModelReportsTheWaiverCapabilityOff() {
        for (String type : List.of("MON_HOC", "NCKH", "TLCN", "KLTN")) {
            UUID roundId = insertRound("Wg Read " + type, type, "DRAFT", null);
            assertThat(roundReads.get(roundId).counterReviewWaiverAllowed())
                    .as("round type %s", type)
                    .isFalse();
        }
    }

    // ---------- fixtures ----------

    private Jwt admin() {
        return Jwt.withTokenValue("waiver-gate-fixture").header("alg", "none")
                .subject("wg-admin").claim("roles", List.of("ADMIN")).build();
    }

    private Jwt lecturer(String lecturerId) {
        return Jwt.withTokenValue("waiver-gate-fixture").header("alg", "none")
                .subject("wg-user-" + lecturerId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private UUID insertRound(String name, String type, String status, Instant gvpbDeadline) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, type,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                gvpbDeadline == null ? null : Timestamp.from(gvpbDeadline), status);
        return id;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'Gate fixture topic', 2, 'PUBLISHED', 'wg-admin')",
                id, roundId, title);
        return id;
    }

    private void insertApprovedGroup(UUID roundId, UUID topicId, String tag) {
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, tag + "-leader", topicId);
        for (int order = 1; order <= 3; order++) {
            ensureStudent(tag + "-student-" + order);
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, tag + "-student-" + order, order, order == 1);
        }
    }

    private UUID insertCouncil(UUID roundId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'wg-admin')",
                id, roundId, name);
        return id;
    }

    private void seat(UUID councilId, String lecturerId, String role) {
        ensureLecturer(lecturerId);
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, role);
    }

    private void assignCouncilTopic(UUID councilId, UUID topicId) {
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (?, ?, ?, 'wg-admin')",
                UUID.randomUUID(), councilId, topicId);
    }

    private void assignReviewerRow(UUID topicId, String lecturerId) {
        ensureLecturer(lecturerId);
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?", lecturerId, topicId);
    }

    private void submitDefense(UUID councilId, UUID topicId, String lecturerId, int score) {
        councils.submitScore(councilId, topicId, "DEFENSE", BigDecimal.valueOf(score), lecturer(lecturerId));
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "wg-user-" + lecturerId;
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (users == null || users == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                            + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                            + "VALUES (?, ?, 'test-password', 'Gate', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    userId, lecturerId + "@fixture.test");
        }
        Integer lecturers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?", Integer.class, lecturerId);
        if (lecturers == null || lecturers == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                            + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                    lecturerId, userId, "GV-" + lecturerId);
        }
    }

    private void ensureStudent(String studentId) {
        String userId = "wg-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Gate', 'Student', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@fixture.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 4, CURRENT_TIMESTAMP)",
                studentId, userId, "WG-" + studentId);
    }

    private int auditCount(String entityId, String action) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = ?",
                Integer.class, entityId, action);
        return count == null ? 0 : count;
    }
}
