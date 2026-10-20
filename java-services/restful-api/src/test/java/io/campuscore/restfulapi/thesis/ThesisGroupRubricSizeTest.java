package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Public-API evidence for the user's current 3-5 member rubric policy. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_group_rubric_size;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGroupRubricSizeTest {

    private static final String LEADER = "rgs-leader";
    private static final String SUPERVISOR = "rgs-supervisor";

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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'rgs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rgs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rgs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rgs-%' OR \"id\" LIKE 'thesis-fixture-lecturer-rgs-%'");
    }

    @ParameterizedTest(name = "unfinished draft of {0} member(s) stays usable")
    @ValueSource(ints = {1, 2})
    void unfinishedDraftRemainsValid(int memberCount) throws Exception {
        UUID groupId = createGroup(memberCount);

        assertThat(group(groupId)).containsEntry("status", "DRAFT").containsEntry("approval_status", "PENDING");
        assertRoster(groupId, memberCount);
        progress(groupId).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(memberCount))
                .andExpect(jsonPath("$.currentMilestone").value("GROUP_CREATED"))
                .andExpect(jsonPath("$.attentionState").value("NONE"));
    }

    @ParameterizedTest(name = "submitted group of {0} member(s) cannot be approved")
    @ValueSource(ints = {1, 2})
    void undersizedApprovalIsRejectedWithoutWrites(int memberCount) throws Exception {
        UUID groupId = createGroup(memberCount);
        assignTopicWithSupervisor(groupId);
        Snapshot before = snapshot(groupId);

        approve(groupId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));

        assertThat(snapshot(groupId)).isEqualTo(before);
        assertThat(group(groupId)).containsEntry("status", "SUBMITTED").containsEntry("approval_status", "PENDING");
    }

    @ParameterizedTest(name = "a complete {0}-member group can be approved")
    @ValueSource(ints = {3, 4, 5})
    void completedRostersCanBeApprovedAndShowApprovedProgress(int memberCount) throws Exception {
        UUID groupId = createGroup(memberCount);
        assignTopicWithSupervisor(groupId);

        approve(groupId).andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        assertRoster(groupId, memberCount);
        // Membership and topic assignment are now audited alongside approval;
        // the implicit leader row in createGroup is the only unaudited seat.
        List<String> expectedActions = new ArrayList<>(
                Collections.nCopies(memberCount - 1, "THESIS_GROUP_MEMBER_ADDED"));
        expectedActions.add("THESIS_GROUP_TOPIC_ASSIGNED");
        expectedActions.add("THESIS_GROUP_APPROVED");
        assertThat(auditActions(groupId)).containsExactlyElementsOf(expectedActions);
        progress(groupId).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(memberCount))
                .andExpect(jsonPath("$.currentMilestone").value("GROUP_APPROVED"))
                .andExpect(jsonPath("$.attentionState").value("NONE"));
    }

    @ParameterizedTest(name = "migration-marked historical {0}-member group keeps published progress")
    @ValueSource(ints = {1, 2})
    void historicalSizeExceptionPreservesPublishedProgress(int memberCount) throws Exception {
        UUID groupId = createGroup(memberCount);
        assignTopicWithSupervisor(groupId);
        UUID roundId = roundId(groupId);
        UUID topicId = jdbc.queryForObject("SELECT topic_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId);
        UUID councilId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, 'Historical council', 'ACTIVE', 'rgs-admin')", councilId, roundId);
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (?, ?, ?, 'rgs-admin')", UUID.randomUUID(), councilId, topicId);
        // Read-path fixture for the migration's marked historical state. H2
        // does not prove the PostgreSQL marker/membership mutation guards.
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED', "
                + "legacy_group_size_exception = TRUE WHERE id = ?", groupId);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.25, result_status = 'GRADED' WHERE id = ?", topicId);
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED' WHERE id = ?", roundId);
        insertHistoricalReport(groupId);

        progress(groupId).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(memberCount))
                .andExpect(jsonPath("$.attentionState").value("NONE"))
                .andExpect(jsonPath("$.currentMilestone").value("RESULTS_PUBLISHED"))
                .andExpect(jsonPath("$.finalScore").value(8.25));

        jdbc.update("UPDATE thesis.thesis_group SET legacy_group_size_exception = FALSE WHERE id = ?", groupId);
        progress(groupId).andExpect(status().isOk())
                .andExpect(jsonPath("$.attentionState").value("GROUP_INVALID_MEMBER_COUNT"))
                .andExpect(jsonPath("$.currentMilestone").value("TOPIC_ASSIGNED"));
    }

    @Test
    void sixthMemberIsRejectedWithoutWrites() throws Exception {
        UUID groupId = createGroup(5);
        student("rgs-member-6");
        Snapshot before = snapshot(groupId);

        addMember(groupId, "rgs-member-6", student(LEADER))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_FULL"));

        assertThat(snapshot(groupId)).isEqualTo(before);
        assertRoster(groupId, 5);
    }

    @Test
    void approvedThreeMemberGroupCannotDropBelowMinimum() throws Exception {
        UUID groupId = createGroup(3);
        assignTopicWithSupervisor(groupId);
        approve(groupId).andExpect(status().isOk());
        insertHistoricalReport(groupId);
        Snapshot before = snapshot(groupId);

        removeMember(groupId, "rgs-member-3", lecturer(SUPERVISOR))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));

        assertThat(snapshot(groupId)).isEqualTo(before);
        assertRoster(groupId, 3);
    }

    @Test
    void supervisorMayReduceApprovedFourToThreeWithoutRewritingSurvivorsOrReport() throws Exception {
        UUID groupId = createGroup(4);
        assignTopicWithSupervisor(groupId);
        approve(groupId).andExpect(status().isOk());
        insertHistoricalReport(groupId);
        Snapshot before = snapshot(groupId);

        removeMember(groupId, "rgs-member-4", lecturer(SUPERVISOR))
                .andExpect(status().isOk()).andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        assertThat(members(groupId)).containsExactlyElementsOf(before.members().stream()
                .filter(member -> !"rgs-member-4".equals(member.get("student_id"))).toList());
        assertThat(reports(groupId)).containsExactlyElementsOf(before.reports());
        // The roster history is preserved plus the new removal audit row.
        List<String> expectedAudit = new ArrayList<>(before.auditActions());
        expectedAudit.add("THESIS_GROUP_MEMBER_REMOVED");
        assertThat(auditActions(groupId)).containsExactlyElementsOf(expectedAudit);
        assertRoster(groupId, 3);
        assertThat(group(groupId)).containsEntry("approval_status", "APPROVED");
    }

    @Test
    void currentAuthorityLeaderOverlapAndReportOwnerRulesRemainEnforced() throws Exception {
        UUID groupId = createGroup(3);
        student("rgs-outsider");
        Snapshot draft = snapshot(groupId);

        createGroup(roundId(groupId), LEADER).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_IN_GROUP"));
        removeMember(groupId, LEADER, student(LEADER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_CANNOT_BE_REMOVED"));
        addMember(groupId, "rgs-outsider", student("rgs-member-2")).andExpect(status().isForbidden());
        approve(groupId).andExpect(status().isConflict());
        assertThat(snapshot(groupId)).isEqualTo(draft);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = ?",
                Integer.class, roundId(groupId))).isEqualTo(1);

        assignTopicWithSupervisor(groupId);
        approve(groupId).andExpect(status().isOk());
        Snapshot approved = snapshot(groupId);
        removeMember(groupId, "rgs-member-3", student(LEADER)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
        addMember(groupId, "rgs-outsider", lecturer("rgs-unassigned-lecturer")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://school.test/member-report.pdf\"}")
                        .with(student("rgs-member-2")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
        assertThat(snapshot(groupId)).isEqualTo(approved);
    }

    private UUID createGroup(int memberCount) throws Exception {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, 'Rubric group policy', 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(7_200)));
        createGroup(roundId, LEADER).andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject("SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        for (int order = 2; order <= memberCount; order++) {
            String studentId = "rgs-member-" + order;
            student(studentId);
            addMember(groupId, studentId, student(LEADER)).andExpect(status().isOk());
        }
        return groupId;
    }

    private ResultActions createGroup(UUID roundId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\":\"" + roundId + "\"}").with(student(studentId)));
    }

    private void assignTopicWithSupervisor(UUID groupId) throws Exception {
        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Rubric topic', 'fixture', 4, 'PUBLISHED', 'rgs-admin')",
                topicId, roundId(groupId));
        lecturer(SUPERVISOR);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, ?, 1)", UUID.randomUUID(), topicId, SUPERVISOR);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}").with(student(LEADER)))
                .andExpect(status().isOk());
    }

    private void insertHistoricalReport(UUID groupId) {
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Retained report', 'https://school.test/retained.pdf')",
                UUID.randomUUID(), groupId, roundId(groupId), LEADER);
    }

    private ResultActions approve(UUID groupId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                .with(ThesisAuthorityFixture.role(jdbc, "rgs-admin", "ADMIN")));
    }

    private ResultActions progress(UUID groupId) throws Exception {
        return mvc.perform(get("/api/v1/thesis/me/progress").param("roundId", roundId(groupId).toString())
                .with(student(LEADER)));
    }

    private ResultActions addMember(UUID groupId, String studentId, RequestPostProcessor actor) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}").with(actor));
    }

    private ResultActions removeMember(UUID groupId, String studentId, RequestPostProcessor actor) throws Exception {
        return mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, studentId).with(actor));
    }

    private RequestPostProcessor student(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturer(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private UUID roundId(UUID groupId) {
        return jdbc.queryForObject("SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId);
    }

    private Map<String, Object> group(UUID groupId) {
        return jdbc.queryForMap("SELECT * FROM thesis.thesis_group WHERE id = ?", groupId);
    }

    private List<Map<String, Object>> members(UUID groupId) {
        return jdbc.queryForList("SELECT * FROM thesis.thesis_group_member WHERE group_id = ? ORDER BY member_order", groupId);
    }

    private List<Map<String, Object>> reports(UUID groupId) {
        return jdbc.queryForList("SELECT * FROM thesis.thesis_group_report WHERE group_id = ? ORDER BY id", groupId);
    }

    private List<String> auditActions(UUID groupId) {
        return jdbc.queryForList("SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"entityId\" = ? ORDER BY \"createdAt\", \"id\"", String.class, groupId.toString());
    }

    private Snapshot snapshot(UUID groupId) {
        return new Snapshot(group(groupId), members(groupId), reports(groupId), auditActions(groupId));
    }

    private void assertRoster(UUID groupId, int memberCount) {
        List<Map<String, Object>> roster = members(groupId);
        assertThat(roster).hasSize(memberCount);
        assertThat(roster.stream().filter(member -> Boolean.TRUE.equals(member.get("is_leader"))).toList())
                .singleElement().satisfies(leader -> assertThat(leader).containsEntry("student_id", LEADER)
                        .containsEntry("member_order", 1));
        assertThat(roster.stream().map(member -> member.get("member_order")).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, memberCount).boxed().toList());
        assertThat(roster.stream().map(member -> member.get("student_id")).toList()).doesNotHaveDuplicates();
    }

    private record Snapshot(Map<String, Object> group, List<Map<String, Object>> members,
            List<Map<String, Object>> reports, List<String> auditActions) { }
}
