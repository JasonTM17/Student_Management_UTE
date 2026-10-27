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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Governance release path for a mistaken group approval (task GV1 D1) plus the
 * accountable non-supervisor approval reason (task GV1 D3). Withdrawal mirrors
 * the approve round-state window, is supervisor/governance scoped, and refuses
 * once downstream work would be orphaned.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_group_approval_withdrawal;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGroupApprovalWithdrawalTest {

    private static final String LEADER = "wdr-leader";
    private static final String SUPERVISOR = "wdr-supervisor";
    private static final String OTHER_SUPERVISOR = "wdr-other-supervisor";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        // H2 migrations carry no notifications schema (production owns it), so
        // the same fixture table the notification tests use is created here.
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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityType\" = 'THESIS_GROUP'"
                + " OR \"actorId\" LIKE 'wdr-%' OR \"actorId\" LIKE 'thesis-fixture-lecturer-wdr-%'");
        jdbc.update("DELETE FROM notifications.notification WHERE user_id LIKE 'wdr-%'"
                + " OR user_id LIKE 'thesis-fixture-lecturer-wdr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'wdr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'wdr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\" LIKE 'wdr-%'"
                + " OR \"userId\" LIKE 'thesis-fixture-lecturer-wdr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'wdr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'thesis-fixture-lecturer-wdr-%'");
        jdbc.update("DELETE FROM academic.\"Curriculum\" WHERE \"id\" LIKE 'wdr-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'wdr-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'wdr-%'");
    }

    // ---------- D1: withdraw approval ----------

    @Test
    void supervisorWithdrawsApprovalAndRestoresExactlyThePreApprovalState() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        UUID groupId = approvedGroup(roundId, topicId, 3);
        supervise(topicId, SUPERVISOR);
        jdbc.update("UPDATE thesis.thesis_group SET size_exception_reason = 'legacy waiver',"
                + " size_exception_by = 'wdr-prior-admin', size_exception_at = CURRENT_TIMESTAMP WHERE id = ?",
                groupId);

        withdraw(groupId, lecturerJwt(SUPERVISOR), "Duyệt nhầm nhóm")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING"));

        var row = jdbc.queryForMap("SELECT approval_status, approved_by, approved_at, rejection_reason,"
                + " size_exception_reason, size_exception_by, size_exception_at, status, topic_id"
                + " FROM thesis.thesis_group WHERE id = ?", groupId);
        assertThat(row.get("approval_status")).isEqualTo("PENDING");
        assertThat(row.get("approved_by")).isNull();
        assertThat(row.get("approved_at")).isNull();
        assertThat(row.get("size_exception_reason")).isNull();
        assertThat(row.get("size_exception_by")).isNull();
        assertThat(row.get("size_exception_at")).isNull();
        assertThat(row.get("status")).isEqualTo("SUBMITTED");
        assertThat(row.get("topic_id")).isEqualTo(topicId);
        assertThat(memberCount(groupId)).isEqualTo(3);

        var audit = jdbc.queryForList("SELECT \"action\", \"actorId\", \"entityId\", \"summary\""
                + " FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_APPROVAL_WITHDRAWN'",
                groupId.toString());
        assertThat(audit).hasSize(1);
        assertThat(audit.getFirst().get("actorId")).isNotNull();
        assertThat((String) audit.getFirst().get("summary")).contains("Duyệt nhầm nhóm");

        Integer notifications = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE title = 'Nhóm khóa luận bị rút duyệt'",
                Integer.class);
        assertThat(notifications).isNotNull().isGreaterThanOrEqualTo(1);
    }

    @Test
    void withdrawalRequiresAReason() throws Exception {
        UUID groupId = approvedGroup(insertRound("REGISTRATION_OPEN"), null, 3);
        UUID topicId = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, groupId);
        supervise(topicId, SUPERVISOR);

        mvc.perform(post("/api/v1/thesis/groups/{id}/withdraw-approval", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(lecturerJwt(SUPERVISOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withdrawalRefusesGroupsThatAreNotApproved() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID pending = insertGroup(roundId, topicId, "PENDING", 3);
        withdraw(pending, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));

        UUID rejected = insertGroup(roundId, topicId, "REJECTED", 3);
        withdraw(rejected, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));
    }

    @Test
    void withdrawalRefusesOnceTheRoundIsTerminal() throws Exception {
        UUID groupId = approvedGroup(insertRound("CLOSED"), null, 3);
        UUID topicId = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, groupId);
        supervise(topicId, SUPERVISOR);
        withdraw(groupId, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));
    }

    @Test
    void withdrawalRefusesApprovedGroupThatFinishedProgress() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = approvedGroup(roundId, topicId, 3);
        jdbc.update("UPDATE thesis.thesis_group SET status = 'COMPLETED' WHERE id = ?", groupId);
        withdraw(groupId, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));
    }

    @Test
    void downstreamWorkBlocksWithdrawalWithCountsPerKind() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID councilId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, created_by)"
                + " VALUES (?, ?, 'Withdrawal council', 'wdr-admin')", councilId, roundId);

        // Each blocker kind refuses the withdrawal and reports its count.
        UUID reportGroup = approvedGroup(roundId, topicId, 3);
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url)"
                + " VALUES (?, ?, ?, 'wdr-leader', 'Progress report', 'https://example.test/r.pdf')",
                UUID.randomUUID(), reportGroup, roundId);
        withdraw(reportGroup, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.details.reports").value(1));

        UUID scoredTopicId = insertTopic(roundId, "department-demo");
        supervise(scoredTopicId, SUPERVISOR);
        UUID scoredGroup = approvedGroup(roundId, scoredTopicId, 3);
        UUID scoredTopic = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, scoredGroup);
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, score)"
                + " VALUES (?, ?, ?, 'wdr-scorer', 8.5)",
                UUID.randomUUID(), scoredTopic, councilId);
        withdraw(scoredGroup, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.details.scores").value(1));

        UUID councilTopicId = insertTopic(roundId, "department-demo");
        supervise(councilTopicId, SUPERVISOR);
        UUID councilGroup = approvedGroup(roundId, councilTopicId, 3);
        UUID councilTopic = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, councilGroup);
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by)"
                + " VALUES (?, ?, ?, 'wdr-admin')", UUID.randomUUID(), councilId, councilTopic);
        withdraw(councilGroup, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.details.councilTopics").value(1));

        UUID reviewedTopicId = insertTopic(roundId, "department-demo");
        supervise(reviewedTopicId, SUPERVISOR);
        UUID reviewedGroup = approvedGroup(roundId, reviewedTopicId, 3);
        UUID reviewedTopic = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, reviewedGroup);
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?",
                SUPERVISOR, reviewedTopic);
        withdraw(reviewedGroup, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.details.reviewers").value(1));

        UUID gradedTopicId = insertTopic(roundId, "department-demo");
        supervise(gradedTopicId, SUPERVISOR);
        UUID gradedGroup = approvedGroup(roundId, gradedTopicId, 3);
        UUID gradedTopic = (UUID) jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, gradedGroup);
        jdbc.update("UPDATE thesis.thesis_topic SET result_status = 'PASSED', final_score = 8.0 WHERE id = ?",
                gradedTopic);
        withdraw(gradedGroup, lecturerJwt(SUPERVISOR), "reason")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_WITHDRAWAL_BLOCKED"))
                .andExpect(jsonPath("$.details.results").value(1));
    }

    @Test
    void withdrawalAuthorizationFollowsGovernanceScopeOnly() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        seedSiblingAndForeignDepartments();
        UUID ownTopic = insertTopic(roundId, "department-demo");
        UUID foreignTopic = insertTopic(roundId, "wdr-dept-foreign");

        // Global admin may withdraw anywhere.
        UUID adminGroup = approvedGroup(roundId, ownTopic, 3);
        withdraw(adminGroup, adminJwt(), "mistake").andExpect(status().isOk());

        // A faculty dean withdraws only inside their faculty scope — even
        // when they personally supervise a foreign topic (no supervisor
        // escape for governance writes).
        String deanUser = ensureLecturer("wdr-dean", "department-demo");
        UUID ownFacultyGroup = approvedGroup(roundId, insertTopic(roundId, "wdr-dept-sibling"), 3);
        withdraw(ownFacultyGroup, deanJwt(deanUser), "mistake").andExpect(status().isOk());
        UUID foreignSupervised = approvedGroup(roundId, foreignTopic, 3);
        supervise(foreignTopic, "wdr-dean");
        withdraw(foreignSupervised, deanJwt(deanUser), "mistake")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("THESIS_GROUP_NOT_FOUND"));

        // A department head governs their own department only.
        String headUser = ensureLecturer("wdr-head", "wdr-dept-sibling");
        UUID sameDeptGroup = approvedGroup(roundId, insertTopic(roundId, "wdr-dept-sibling"), 3);
        withdraw(sameDeptGroup, departmentHeadJwt(headUser), "mistake").andExpect(status().isOk());
        UUID otherDeptGroup = approvedGroup(roundId, ownTopic, 3);
        withdraw(otherDeptGroup, departmentHeadJwt(headUser), "mistake")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("THESIS_GROUP_NOT_FOUND"));

        // A supervisor of a different topic and a student are refused.
        UUID topic = insertTopic(roundId, "department-demo");
        supervise(topic, SUPERVISOR);
        supervise(insertTopic(roundId, "department-demo"), OTHER_SUPERVISOR);
        withdraw(approvedGroup(roundId, topic, 3), lecturerJwt(OTHER_SUPERVISOR), "mistake")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_REVIEWER_REQUIRED"));
        withdraw(approvedGroup(roundId, topic, 3), studentJwt(LEADER), "mistake")
                .andExpect(status().isForbidden());
    }

    @Test
    void withdrawnGroupCanBeApprovedAgain() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = approvedGroup(roundId, topicId, 3);

        withdraw(groupId, lecturerJwt(SUPERVISOR), "mistake").andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .with(lecturerJwt(SUPERVISOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));
    }

    @Test
    void secondWithdrawalOfTheSameGroupConflicts() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = approvedGroup(roundId, topicId, 3);

        withdraw(groupId, lecturerJwt(SUPERVISOR), "first").andExpect(status().isOk());
        withdraw(groupId, lecturerJwt(SUPERVISOR), "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_STATE_CONFLICT"));
    }

    @Test
    void groupReadModelExposesWithdrawableFlagAndBlockerCounts() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = approvedGroup(roundId, topicId, 3);

        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalWithdrawal.withdrawable").value(true))
                .andExpect(jsonPath("$[0].approvalWithdrawal.reports").value(0))
                .andExpect(jsonPath("$[0].approvalWithdrawal.scores").value(0))
                .andExpect(jsonPath("$[0].approvalWithdrawal.councilTopics").value(0));

        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url)"
                + " VALUES (?, ?, ?, 'wdr-leader', 'Progress report', 'https://example.test/r.pdf')",
                UUID.randomUUID(), groupId, roundId);
        mvc.perform(get("/api/v1/thesis/groups").queryParam("roundId", roundId.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approvalWithdrawal.withdrawable").value(false))
                .andExpect(jsonPath("$[0].approvalWithdrawal.reports").value(1));
    }

    // ---------- D3: accountable governance-override approval ----------

    @Test
    void nonSupervisorApprovalRequiresAReasonAndIsAuditedAsGovernanceOverride() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = insertGroup(roundId, topicId, "PENDING", 3);

        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED"));

        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Administrative approval after petition\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        var audit = jdbc.queryForList("SELECT \"summary\" FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_APPROVED'", groupId.toString());
        assertThat(audit).hasSize(1);
        assertThat((String) audit.getFirst().get("summary"))
                .contains("governance override")
                .contains("Administrative approval after petition");
    }

    @Test
    void topicSupervisorApprovalStillNeedsNoReason() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        UUID topicId = insertTopic(roundId, "department-demo");
        supervise(topicId, SUPERVISOR);
        UUID groupId = insertGroup(roundId, topicId, "PENDING", 3);

        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(lecturerJwt(SUPERVISOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        var audit = jdbc.queryForList("SELECT \"summary\" FROM campuscore_audit.\"AdminAudit\""
                + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_APPROVED'", groupId.toString());
        assertThat(audit).hasSize(1);
        assertThat((String) audit.getFirst().get("summary")).doesNotContain("governance override");
    }

    @Test
    void deanSupervisorNeedsNoReasonButGovernanceDeanDoes() throws Exception {
        UUID roundId = insertRound("REGISTRATION_OPEN");
        seedSiblingAndForeignDepartments();
        String deanUser = ensureLecturer("wdr-dean", "department-demo");

        // The dean who personally supervises the topic stays a supervisor.
        UUID ownTopic = insertTopic(roundId, "department-demo");
        supervise(ownTopic, "wdr-dean");
        UUID ownGroup = insertGroup(roundId, ownTopic, "PENDING", 3);
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", ownGroup).with(deanJwt(deanUser)))
                .andExpect(status().isOk());

        // The dean acting on a group they do not supervise is governance.
        UUID otherTopic = insertTopic(roundId, "department-demo");
        supervise(otherTopic, SUPERVISOR);
        UUID otherGroup = insertGroup(roundId, otherTopic, "PENDING", 3);
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", otherGroup).with(deanJwt(deanUser)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED"));
    }

    // ---------- fixtures ----------

    private UUID insertRound(String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, 'Withdrawal round', 'KLTN', ?, ?, ?, ?, ?, ?)",
                roundId, Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(10_800)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(86_400)), status);
        return roundId;
    }

    private void seedSiblingAndForeignDepartments() {
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('wdr-dept-sibling', 'Sibling Department', 'WSIB', 'faculty-demo', TRUE)");
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                + "VALUES ('wdr-faculty-foreign', 'Foreign Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('wdr-dept-foreign', 'Foreign Department', 'WFRG', 'wdr-faculty-foreign', TRUE)");
    }

    private UUID insertTopic(UUID roundId, String departmentId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, 'Withdrawal topic', 'fixture', 4, 'PUBLISHED', 'wdr-admin')",
                topicId, roundId, departmentId);
        return topicId;
    }

    private void supervise(UUID topicId, String lecturerId) {
        ensureLecturer(lecturerId, "department-demo");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, ?, 1)", UUID.randomUUID(), topicId, lecturerId);
    }

    /** APPROVED groups land the roster first, then flip approval (V65 trigger). */
    private UUID approvedGroup(UUID roundId, UUID topicId, int size) {
        UUID groupId = insertGroup(roundId, topicId == null ? insertTopic(roundId, "department-demo") : topicId,
                "PENDING", size);
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED',"
                + " approved_by = 'wdr-prior-admin', approved_at = CURRENT_TIMESTAMP WHERE id = ?", groupId);
        return groupId;
    }

    private UUID insertGroup(UUID roundId, UUID topicId, String approvalStatus, int size) {
        UUID groupId = UUID.randomUUID();
        // Unique leader per group: the V65 validator forbids one student from
        // holding two active seats in the same round.
        String leader = "wdr-leader-" + groupId.toString().substring(0, 4);
        ensureActiveStudent(leader);
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
                groupId, roundId, leader, topicId, approvalStatus);
        for (int order = 1; order <= size; order++) {
            String student = order == 1 ? leader : "wdr-member-" + groupId.toString().substring(0, 4) + "-" + order;
            ensureActiveStudent(student);
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, student, order, order == 1);
        }
        return groupId;
    }

    private int memberCount(UUID groupId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        return count == null ? 0 : count;
    }

    private void ensureActiveStudent(String studentId) {
        if (Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM campuscore_auth.\"Student\" WHERE \"id\" = ?",
                Boolean.class, studentId))) {
            return;
        }
        String userId = "wdr-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Withdrawal', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "WDR-" + studentId);
    }

    private String ensureLecturer(String lecturerId, String departmentId) {
        String userId = "thesis-fixture-lecturer-" + lecturerId;
        String existing = jdbc.query("SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?",
                rs -> rs.next() ? rs.getString(1) : null, lecturerId);
        if (existing != null) {
            return existing;
        }
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Withdrawal', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" "
                        + "(\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, ?, ?, TRUE)",
                lecturerId, userId, departmentId, "EMP-" + lecturerId);
        return userId;
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(
            UUID groupId, RequestPostProcessor actor, String reason) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/withdraw-approval", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}")
                .with(actor));
    }

    private RequestPostProcessor studentJwt(String studentId) {
        ensureActiveStudent(studentId);
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor deanJwt(String userId) {
        return ThesisAuthorityFixture.role(jdbc, userId, "TRUONG_KHOA");
    }

    private RequestPostProcessor departmentHeadJwt(String userId) {
        return ThesisAuthorityFixture.role(jdbc, userId, "TRUONG_BO_MON");
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "wdr-admin", "ADMIN");
    }
}
