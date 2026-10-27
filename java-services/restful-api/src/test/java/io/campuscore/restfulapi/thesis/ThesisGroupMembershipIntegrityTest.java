package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Exercises replacement through the public mutation boundary with the migrated order constraints. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_group_membership_integrity;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGroupMembershipIntegrityTest {

    private static final String LEADER = "tmi-leader";
    private static final String SUPERVISOR = "tmi-supervisor";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'tmi-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'tmi-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'tmi-%'");
    }

    @Test
    void campusReplacementReusesTheVacantOrderWithoutChangingSurvivors() throws Exception {
        UUID groupId = threeMemberGroup();
        List<MemberRow> before = members(groupId);
        ensureActiveStudent("tmi-replacement");

        removeMember(groupId, "tmi-member-2", studentJwt(LEADER)).andExpect(status().isOk());
        addMember(groupId, "tmi-replacement", studentJwt(LEADER)).andExpect(status().isOk());

        assertReplacement(groupId, before, "tmi-member-2", "tmi-replacement");
        assertThat(approvalStatus(groupId)).isEqualTo("PENDING");
    }

    @Test
    void externalReplacementReusesTheVacantOrderAndKeepsItsDeclaredIdentity() throws Exception {
        UUID groupId = threeMemberGroup();
        List<MemberRow> before = members(groupId);

        removeMember(groupId, "tmi-member-2", studentJwt(LEADER)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"External replacement\",\"contact\":\"replacement@school.test\"}")
                        .with(studentJwt(LEADER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[?(@.isExternal == true)].displayName")
                        .value(org.hamcrest.Matchers.hasItem("External replacement")))
                .andExpect(jsonPath("$.members[?(@.isExternal == true)].contact")
                        .value(org.hamcrest.Matchers.hasItem("replacement@school.test")));

        String externalId = jdbc.queryForObject(
                "SELECT student_id FROM thesis.thesis_group_member WHERE group_id = ? AND is_external = TRUE",
                String.class, groupId);
        assertThat(externalId).startsWith("external-");
        assertReplacement(groupId, before, "tmi-member-2", externalId);
    }

    @Test
    void supervisorCanReplaceAnApprovedMemberWithoutFallingBelowThree() throws Exception {
        UUID groupId = groupOfSize(4);
        approveWithSupervisor(groupId);
        List<MemberRow> before = members(groupId);
        ensureActiveStudent("tmi-replacement");

        removeMember(groupId, "tmi-member-2", lecturerJwt(SUPERVISOR)).andExpect(status().isOk());
        assertThat(members(groupId)).hasSize(3);
        addMember(groupId, "tmi-replacement", lecturerJwt(SUPERVISOR)).andExpect(status().isOk());

        assertReplacement(groupId, before, "tmi-member-2", "tmi-replacement");
        assertThat(approvalStatus(groupId)).isEqualTo("APPROVED");
    }

    @Test
    void repeatedReplacementDoesNotExhaustTheFiveOrderSlots() throws Exception {
        UUID groupId = groupOfSize(5);
        List<MemberRow> before = members(groupId);
        String replaced = "tmi-member-2";

        for (int index = 0; index < 5; index++) {
            String replacement = "tmi-replacement-" + index;
            ensureActiveStudent(replacement);
            removeMember(groupId, replaced, studentJwt(LEADER)).andExpect(status().isOk());
            addMember(groupId, replacement, studentJwt(LEADER)).andExpect(status().isOk());
            assertReplacement(groupId, before, "tmi-member-2", replacement);
            replaced = replacement;
        }
    }

    @Test
    void approvedLeaderStillCannotChangeTheRoster() throws Exception {
        UUID groupId = groupOfSize(4);
        approveWithSupervisor(groupId);
        removeMember(groupId, "tmi-member-2", lecturerJwt(SUPERVISOR)).andExpect(status().isOk());
        ensureActiveStudent("tmi-replacement");
        List<MemberRow> before = members(groupId);

        addMember(groupId, "tmi-replacement", studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
        removeMember(groupId, "tmi-member-3", studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));

        assertThat(members(groupId)).containsExactlyElementsOf(before);
        assertThat(approvalStatus(groupId)).isEqualTo("APPROVED");
    }

    @Test
    void supervisorCanReduceAnApprovedRosterToThreeAndCannotGoLower() throws Exception {
        UUID groupId = groupOfSize(4);
        approveWithSupervisor(groupId);
        removeMember(groupId, "tmi-member-4", lecturerJwt(SUPERVISOR)).andExpect(status().isOk());
        List<MemberRow> before = members(groupId);
        removeMember(groupId, "tmi-member-2", lecturerJwt(SUPERVISOR)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
        removeMember(groupId, "tmi-member-3", lecturerJwt(SUPERVISOR)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
        assertThat(members(groupId)).containsExactlyElementsOf(before).hasSize(3);
        assertThat(approvalStatus(groupId)).isEqualTo("APPROVED");

        removeMember(groupId, LEADER, lecturerJwt(SUPERVISOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_CANNOT_BE_REMOVED"));
        assertThat(members(groupId)).containsExactlyElementsOf(before);
    }

    @Test
    void fullRosterAndLeaderRemovalAreStillRejectedWithoutWrites() throws Exception {
        UUID groupId = groupOfSize(5);
        ensureActiveStudent("tmi-extra");
        List<MemberRow> before = members(groupId);

        addMember(groupId, "tmi-extra", studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));
        removeMember(groupId, LEADER, studentJwt(LEADER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_CANNOT_BE_REMOVED"));

        assertThat(members(groupId)).containsExactlyElementsOf(before);
    }

    @Test
    void aClosedRoundStillRejectsSupervisorMembershipChanges() throws Exception {
        UUID groupId = groupOfSize(4);
        approveWithSupervisor(groupId);
        removeMember(groupId, "tmi-member-2", lecturerJwt(SUPERVISOR)).andExpect(status().isOk());
        ensureActiveStudent("tmi-replacement");
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED' "
                + "WHERE id = (SELECT round_id FROM thesis.thesis_group WHERE id = ?)", groupId);
        List<MemberRow> before = members(groupId);

        addMember(groupId, "tmi-replacement", lecturerJwt(SUPERVISOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_CLOSED"));
        removeMember(groupId, "tmi-member-3", lecturerJwt(SUPERVISOR))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_CLOSED"));

        assertThat(members(groupId)).containsExactlyElementsOf(before);
        assertThat(approvalStatus(groupId)).isEqualTo("APPROVED");
    }

    @Test
    void canceledMembersCanRejoinTheSameRoundWithoutDeletingTheirHistory() throws Exception {
        UUID canceled = threeMemberGroup();
        UUID round = jdbc.queryForObject("SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, canceled);
        List<MemberRow> history = members(canceled);
        cancel(canceled);

        createGroup(round, LEADER).andExpect(status().isOk());
        createGroup(round, "tmi-member-2").andExpect(status().isOk());

        assertThat(members(canceled)).containsExactlyElementsOf(history);
        mvc.perform(get("/api/v1/thesis/groups/{id}", canceled).with(studentJwt(LEADER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.memberStudentIds.length()").value(3));
    }

    @Test
    void repeatedCancelAndRejoinKeepsEveryHistoricalMembership() throws Exception {
        UUID round = insertLiveRound();
        ensureActiveStudent(LEADER);
        for (int iteration = 0; iteration < 3; iteration++) {
            createGroup(round, LEADER).andExpect(status().isOk());
            UUID current = jdbc.queryForObject("SELECT id FROM thesis.thesis_group WHERE round_id = ? AND status = 'DRAFT'",
                    UUID.class, round);
            cancel(current);
        }
        createGroup(round, LEADER).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE round_id = ? AND student_id = ?",
                Integer.class, round, LEADER)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = ? AND status = 'CANCELLED'",
                Integer.class, round)).isEqualTo(3);
    }

    @Test
    void canceledHistoryCannotBeReopenedOrHaveMembersRemovedByAdmin() throws Exception {
        UUID canceled = threeMemberGroup();
        List<MemberRow> history = members(canceled);
        cancel(canceled);
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", canceled)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DRAFT\"}").with(adminJwt()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
        removeMember(canceled, "tmi-member-2", adminJwt())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
        assertThat(members(canceled)).containsExactlyElementsOf(history);
    }

    @Test
    void activeMembershipStillRejectsASecondGroupWithoutWritingAnotherGroup() throws Exception {
        UUID group = threeMemberGroup();
        UUID round = jdbc.queryForObject("SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, group);
        createGroup(round, LEADER).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ALREADY_IN_GROUP"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_group WHERE round_id = ?", Integer.class, round))
                .isEqualTo(1);
    }

    @Test
    void anotherStudentCannotCancelTheGroupAndReleaseItsMembers() throws Exception {
        UUID group = threeMemberGroup();
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", group)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}")
                        .with(studentJwt("tmi-member-2")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_group WHERE id = ?", String.class, group))
                .isEqualTo("DRAFT");
        assertThat(members(group)).hasSize(3);
    }

    @Test
    void aGroupCarryingAReportCannotBeDeletedUntilTheReportIsRemoved() throws Exception {
        // FK RESTRICT (plan F5/I6): no path may silently cascade-delete a
        // submitted report — the group delete fails until the report row goes.
        UUID groupId = threeMemberGroup();
        UUID roundId = jdbc.queryForObject("SELECT round_id FROM thesis.thesis_group WHERE id = ?",
                UUID.class, groupId);
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Guarded report', 'https://drive.example.com/guarded.pdf')",
                UUID.randomUUID(), groupId, roundId, LEADER);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> jdbc.update("DELETE FROM thesis.thesis_group WHERE id = ?", groupId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        jdbc.update("DELETE FROM thesis.thesis_group_report WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE group_id = ?", groupId);
        assertThat(jdbc.update("DELETE FROM thesis.thesis_group WHERE id = ?", groupId)).isEqualTo(1);
    }

    @Test
    void adminArchiveSeesPendingAndTopiclessReportsWhileOtherDepartmentsStayBlind() throws Exception {
        // Archive visibility (plan F6): staff archive shows every report of
        // the round — including a group reopened to PENDING or one without a
        // topic — while lecturers keep their approval + department scope.
        UUID roundId = insertClosedRound();
        UUID approved = insertGroupWithTopic(roundId, "tmi-archive-leader-1", "APPROVED", "department-demo");
        UUID pending = insertGroupWithTopic(roundId, "tmi-archive-leader-2", "PENDING", "department-demo");
        UUID topicless = insertGroupWithTopic(roundId, "tmi-archive-leader-3", "PENDING", null);
        insertReportRow(approved, roundId);
        insertReportRow(pending, roundId);
        insertReportRow(topicless, roundId);
        ensureLecturer("tmi-lecturer-other", "department-other");

        // Admin sees every report of the round in both projections.
        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].groupId",
                        org.hamcrest.Matchers.containsInAnyOrder(
                                approved.toString(), pending.toString(), topicless.toString())));
        mvc.perform(get("/api/v1/thesis/rounds/{id}/repository", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        // Same-department lecturer keeps the approval scope: the approved
        // group's report is visible, the PENDING and topic-less ones are not.
        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(lecturerJwt(SUPERVISOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupId").value(approved.toString()));

        // Another department cannot enumerate any of them.
        mvc.perform(get("/api/v1/thesis/rounds/{id}/reports", roundId).with(lecturerJwt("tmi-lecturer-other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/v1/thesis/rounds/{id}/repository", roundId).with(lecturerJwt("tmi-lecturer-other")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private void cancel(UUID group) throws Exception {
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", group)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}").with(studentJwt(LEADER)))
                .andExpect(status().isOk());
    }

    private ResultActions createGroup(UUID round, String student) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups").contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundId\":\"" + round + "\"}").with(studentJwt(student)));
    }

    private void assertReplacement(UUID groupId, List<MemberRow> before, String removed, String replacement) {
        List<MemberRow> after = members(groupId);
        assertThat(after).hasSize(before.size())
                .containsAll(before.stream().filter(member -> !member.studentId().equals(removed)).toList());
        assertThat(after.stream().map(MemberRow::order).toList())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, before.size()).boxed().toList());
        assertThat(after.stream().filter(MemberRow::leader).toList()).hasSize(1);
        assertThat(after.stream().filter(member -> member.studentId().equals(replacement)).toList())
                .singleElement().satisfies(member -> {
                    assertThat(member.order()).isEqualTo(2);
                    assertThat(member.leader()).isFalse();
                });
    }

    private UUID threeMemberGroup() throws Exception {
        return groupOfSize(3);
    }

    private UUID groupOfSize(int memberCount) throws Exception {
        UUID roundId = insertLiveRound();
        ensureActiveStudent(LEADER);
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt(LEADER)))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        for (int order = 2; order <= memberCount; order++) {
            String studentId = "tmi-member-" + order;
            ensureActiveStudent(studentId);
            addMember(groupId, studentId, studentJwt(LEADER)).andExpect(status().isOk());
        }
        return groupId;
    }

    private void approveWithSupervisor(UUID groupId) throws Exception {
        UUID topicId = UUID.randomUUID();
        UUID roundId = jdbc.queryForObject(
                "SELECT round_id FROM thesis.thesis_group WHERE id = ?", UUID.class, groupId);
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Membership integrity topic', 'fixture', 4, 'PUBLISHED', ?)",
                topicId, roundId, "tmi-admin");
        ensureLecturer(SUPERVISOR);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, ?, 1)", UUID.randomUUID(), topicId, SUPERVISOR);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt(LEADER)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Administrative approval\"}")
                        .with(adminJwt()))
                .andExpect(status().isOk());
    }

    private UUID insertLiveRound() {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, 'Membership integrity round', 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(7_200)));
        return roundId;
    }

    private void ensureActiveStudent(String studentId) {
        String userId = "tmi-user-" + studentId;
        insertUser(userId, studentId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" "
                        + "(\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private void ensureLecturer(String lecturerId) {
        ensureLecturer(lecturerId, "department-demo");
    }

    private void ensureLecturer(String lecturerId, String departmentId) {
        String userId = "tmi-user-" + lecturerId;
        insertUser(userId, lecturerId + "@campuscore.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" "
                        + "(\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, ?, ?, TRUE)",
                lecturerId, userId, departmentId, "GV-" + lecturerId);
    }

    private UUID insertClosedRound() {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, 'Archive visibility round', 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_CLOSED')",
                roundId, Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(10_800)),
                Timestamp.from(now.minusSeconds(7_200)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(86_400)));
        return roundId;
    }

    private UUID insertGroupWithTopic(UUID roundId, String leaderId, String approvalStatus, String departmentId) {
        UUID topicId = null;
        if (departmentId != null) {
            topicId = UUID.randomUUID();
            jdbc.update("INSERT INTO thesis.thesis_topic "
                            + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                            + "VALUES (?, ?, ?, 'Archive fixture topic', 'fixture', 1, 'PUBLISHED', ?)",
                    topicId, roundId, departmentId, "tmi-admin");
        }
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
                groupId, roundId, leaderId, topicId, approvalStatus);
        jdbc.update("INSERT INTO thesis.thesis_group_member "
                        + "(id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leaderId);
        ensureActiveStudent(leaderId);
        return groupId;
    }

    private void insertReportRow(UUID groupId, UUID roundId) {
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, 'submitter', 'Archive report', 'https://drive.example.com/archive.pdf')",
                UUID.randomUUID(), groupId, roundId);
    }

    private void insertUser(String userId, String email) {
        jdbc.update("INSERT INTO campuscore_auth.\"User\" "
                        + "(\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", "
                        + "\"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Membership', 'Fixture', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, email);
    }

    private List<MemberRow> members(UUID groupId) {
        return jdbc.query("SELECT id, student_id, member_order, is_leader FROM thesis.thesis_group_member "
                        + "WHERE group_id = ? ORDER BY member_order",
                (row, index) -> new MemberRow(row.getObject("id", UUID.class), row.getString("student_id"),
                        row.getInt("member_order"), row.getBoolean("is_leader")), groupId);
    }

    private String approvalStatus(UUID groupId) {
        return jdbc.queryForObject("SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId);
    }

    private ResultActions addMember(UUID groupId, String studentId, RequestPostProcessor actor) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(actor));
    }

    private ResultActions removeMember(UUID groupId, String studentId, RequestPostProcessor actor) throws Exception {
        return mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, studentId).with(actor));
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "tmi-admin", "ADMIN");
    }

    private record MemberRow(UUID id, String studentId, int order, boolean leader) {
    }
}
