package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.testsupport.CurrentAccountJwt;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Faculty-spec alignment regressions: the 3-5 member policy, the GVPB
 * counter-reviewer flow (assignment, deadline, supervisor exclusion, comment,
 * frozen reassignment), round update/cancel lifecycle, and approval audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_spec_alignment;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisSpecAlignmentTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
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
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'sa-%' OR \"actorId\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'sa-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" = 'department-other'");
    }

    // ---------- R1/R2: round update + cancel lifecycle ----------

    @Test
    void draftRoundScheduleCanBeAmendedAndCancelled() throws Exception {
        UUID roundId = insertRound("sa-round-edit", "DRAFT");

        Instant now = Instant.now();
        mvc.perform(put("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Amended round\",\"thesisType\":\"KLTN\","
                                + "\"lecturerSubmitStart\":\"" + now.minusSeconds(14_400) + "\","
                                + "\"lecturerSubmitEnd\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationStart\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationEnd\":\"" + now.plusSeconds(86_400) + "\","
                                + "\"gvpbDeadline\":\"" + now.plusSeconds(129_600) + "\","
                                + "\"reportDate\":\"" + now.plusSeconds(172_800) + "\","
                                + "\"defenseDate\":\"" + now.plusSeconds(216_000) + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amended round"));

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // A cancelled round is terminal: a second cancel is rejected.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict());
    }

    @Test
    void lecturersCannotUpdateOrCancelRounds() throws Exception {
        ensureLecturer("sa-lecturer-1");
        UUID roundId = insertRound("sa-round-guard", "DRAFT");
        Instant now = Instant.now();
        mvc.perform(put("/api/v1/thesis/rounds/{id}", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nope\",\"thesisType\":\"KLTN\","
                                + "\"lecturerSubmitStart\":\"" + now.minusSeconds(14_400) + "\","
                                + "\"lecturerSubmitEnd\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationStart\":\"" + now.minusSeconds(7_200) + "\","
                                + "\"registrationEnd\":\"" + now.plusSeconds(86_400) + "\","
                                + "\"gvpbDeadline\":\"" + now.plusSeconds(129_600) + "\","
                                + "\"reportDate\":\"" + now.plusSeconds(172_800) + "\","
                                + "\"defenseDate\":\"" + now.plusSeconds(216_000) + "\"}")
                        .with(lecturerJwt("sa-lecturer-1")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(lecturerJwt("sa-lecturer-1")))
                .andExpect(status().isForbidden());
    }

    // ---------- R8a: GVPB counter-review ----------

    @Test
    void gvpbAssignScoreReadAndFreeze() throws Exception {
        UUID roundId = insertRound("sa-gvpb-round", "REGISTRATION_CLOSED");
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("sa-supervisor-1");
        ensureLecturer("sa-reviewer-1");
        ensureLecturer("sa-outsider-1");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'sa-supervisor-1', 1)",
                UUID.randomUUID(), topicId);
        // Counter-review scoring requires an approved defended group.
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 'sa-gvpb-leader', ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, topicId);
        for (int order = 1; order <= 3; order++) {
            jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId,
                    order == 1 ? "sa-gvpb-leader" : "sa-gvpb-member-" + order, order, order == 1);
        }

        // A topic supervisor cannot be its own counter-reviewer.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-supervisor-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_REVIEW"));

        // Governance assigns the reviewer; the assignment is auditable.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-reviewer-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        assertThat(auditActions(topicId)).contains("GVPB_ASSIGNED");

        // An unrelated lecturer cannot submit or read the counter-review.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.0}")
                        .with(lecturerJwt("sa-outsider-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEWER_REQUIRED"));
        mvc.perform(get("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .with(lecturerJwt("sa-outsider-1")))
                .andExpect(status().isForbidden());

        // The reviewer submits score + comment; both are readable afterwards.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5,\"comment\":\"Đủ yêu cầu\"}")
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8.5))
                .andExpect(jsonPath("$.comment").value("Đủ yêu cầu"))
                .andExpect(jsonPath("$.component").value("GVPB"));
        mvc.perform(get("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(8.5));
        assertThat(auditActions(topicId)).contains("GVPB_SCORE_SUBMITTED");

        // The reviewer can revise before the deadline/finalization.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9.0}")
                        .with(lecturerJwt("sa-reviewer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(9.0));

        // Once a score exists the assignment is frozen.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-outsider-1\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GVPB_SCORE_SUBMITTED"));
    }

    @Test
    void gvpbScoreIsRejectedAfterTheDeadline() throws Exception {
        UUID roundId = insertRound("sa-gvpb-late", "REGISTRATION_CLOSED");
        // The schedule-order check requires registration_end <= gvpb_deadline,
        // so the whole tail of the window moves into the past together.
        Instant now = Instant.now();
        // dates_valid demands registration_end strictly after registration_start
        // (inserted at now-2h), and schedule_order demands end <= gvpb_deadline.
        jdbc.update("UPDATE thesis.thesis_registration_round "
                        + "SET registration_end = ?, gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(5_400)),
                Timestamp.from(now.minusSeconds(3_600)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("sa-reviewer-late");
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-reviewer-late\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.0}")
                        .with(lecturerJwt("sa-reviewer-late")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GVPB_DEADLINE_PASSED"));
    }

    // ---------- R4/R7: group cap + approval audit ----------

    @Test
    void aSixthMemberIsRejectedAndApprovalWritesAnAuditRow() throws Exception {
        UUID roundId = insertRound("sa-cap-round", "REGISTRATION_OPEN");
        ensureStudent("sa-leader-1");
        for (int i = 2; i <= 6; i++) {
            ensureStudent("sa-member-" + i);
        }
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("sa-leader-1")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        for (int i = 2; i <= 5; i++) {
            addMember(groupId, "sa-member-" + i).andExpect(status().isOk());
        }
        addMember(groupId, "sa-member-6")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_FULL"));

        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("sa-leader-1")))
                .andExpect(status().isOk());
        ensureLecturer("sa-dean-group-approval");
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Faculty approval\"}")
                        .with(ThesisAuthorityFixture.role(jdbc, "sa-user-sa-dean-group-approval", "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvalStatus").value("APPROVED"));

        List<String> actions = auditActions(groupId);
        assertThat(actions).contains("THESIS_GROUP_APPROVED");
    }

    // ---------- Defect 1: TRUONG_BO_MON RBAC & department scope ----------

    @Test
    void departmentHeadCanManageSupervisorsAndReadCouncilsAndRepository() throws Exception {
        UUID roundId = insertRound("sa-round-tbm", "REGISTRATION_OPEN");
        UUID topicId = insertPublishedTopic(roundId);

        ensureLecturer("sa-sup-a");
        ensureLecturer("sa-tbm-lecturer");

        RequestPostProcessor tbm = truongBoMonJwt("sa-tbm-lecturer", "department-demo");

        // TRUONG_BO_MON can set supervisors for their department topic
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"sa-sup-a\"]}")
                        .with(tbm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lecturerId").value("sa-sup-a"));

        // Department head from another department cannot change supervisors
        ensureDepartment("department-other");
        ensureLecturer("sa-tbm-other");
        RequestPostProcessor tbmOther = truongBoMonJwt("sa-tbm-other", "department-other");
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"sa-sup-a\"]}")
                        .with(tbmOther))
                .andExpect(status().isForbidden());

        // TRUONG_BO_MON can list councils
        mvc.perform(get("/api/v1/thesis/councils")
                        .param("roundId", roundId.toString())
                        .with(tbm))
                .andExpect(status().isOk());

        // TRUONG_BO_MON can list repository
        mvc.perform(get("/api/v1/thesis/rounds/{id}/repository", roundId)
                        .with(tbm))
                .andExpect(status().isOk());

        // TRUONG_BO_MON can propose topics for their department during PROPOSAL_OPEN
        UUID roundPropId = insertRoundProposalOpen("sa-round-tbm-prop");
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundPropId + "\",\"departmentId\":\"department-demo\","
                                + "\"title\":\"TBM Proposed Topic\",\"description\":\"TBM Desc\",\"maxGroups\":1}")
                        .with(tbm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("TBM Proposed Topic"));

        // Department head from another department cannot propose topics for department-demo
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundPropId + "\",\"departmentId\":\"department-demo\","
                                + "\"title\":\"Foreign TBM Topic\",\"description\":\"Foreign Desc\",\"maxGroups\":1}")
                        .with(tbmOther))
                .andExpect(status().isForbidden());

        // Setup approved group with report for topicId
        UUID grpId = insertApprovedGroupWithRoster(roundId, topicId, "sa-tbm-grp");
        UUID reportId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, file_name, file_type, file_size, file_data) "
                        + "VALUES (?, ?, ?, 'sa-tbm-grp-leader', 'Final Thesis Report', 'report.pdf', 'application/pdf', 4, ?)",
                reportId, grpId, roundId, "demo".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // TRUONG_BO_MON can read report metadata & download file for department topic
        mvc.perform(get("/api/v1/thesis/groups/{id}/report", grpId).with(tbm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final Thesis Report"));

        mvc.perform(get("/api/v1/thesis/topics/{id}/report", topicId).with(tbm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final Thesis Report"));

        mvc.perform(get("/api/v1/thesis/groups/{id}/report/file", grpId).with(tbm))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/thesis/reports/{id}/file", reportId).with(tbm))
                .andExpect(status().isOk());

        // Foreign department head cannot read reports of this department
        mvc.perform(get("/api/v1/thesis/groups/{id}/report", grpId).with(tbmOther))
                .andExpect(status().isNotFound());
    }

    // ---------- Defect 2: Council conflict of interest & minimum size ----------

    @Test
    void councilRejectsSupervisorInExecutiveRolesOrBelowMinEligibleGraders() throws Exception {
        UUID roundId = insertRound("sa-round-council-coi", "REGISTRATION_CLOSED");
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("sa-coi-sup");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, 'sa-coi-sup', 1)", UUID.randomUUID(), topicId);

        ensureLecturer("sa-coi-chair");
        ensureLecturer("sa-coi-sec");
        ensureLecturer("sa-coi-mem1");

        // 1. Council with supervisor as CHAIR -> assignTopic rejected
        UUID councilChairSup = createCouncil(roundId, "Council Chair Sup");
        seatMember(councilChairSup, "sa-coi-sup", "CHAIR");
        seatMember(councilChairSup, "sa-coi-sec", "SECRETARY");
        seatMember(councilChairSup, "sa-coi-mem1", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilChairSup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_CHAIR_COUNCIL"));

        // 2. Council with 3 members where 1 is supervisor as MEMBER -> eligible graders = 2 < 3 -> assignTopic rejected
        UUID councilSmallEligible = createCouncil(roundId, "Council Small Eligible");
        seatMember(councilSmallEligible, "sa-coi-chair", "CHAIR");
        seatMember(councilSmallEligible, "sa-coi-sec", "SECRETARY");
        seatMember(councilSmallEligible, "sa-coi-sup", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilSmallEligible)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_SIZE_INVALID"));

        // 3. Council with 3 independent members + topic assigned -> cannot seat supervisor
        UUID councilValid = createCouncil(roundId, "Council Valid");
        seatMember(councilValid, "sa-coi-chair", "CHAIR");
        seatMember(councilValid, "sa-coi-sec", "SECRETARY");
        seatMember(councilValid, "sa-coi-mem1", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilValid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Seating supervisor on council after topic assignment is rejected
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilValid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-coi-sup\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_CHAIR_COUNCIL"));

        // 4. Council with supervisor as SECRETARY -> assignTopic rejected
        UUID topicIdSec = insertPublishedTopic(roundId);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, 'sa-coi-sup', 1)", UUID.randomUUID(), topicIdSec);

        UUID councilSecSup = createCouncil(roundId, "Council Sec Sup");
        seatMember(councilSecSup, "sa-coi-chair", "CHAIR");
        seatMember(councilSecSup, "sa-coi-sup", "SECRETARY");
        seatMember(councilSecSup, "sa-coi-mem1", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilSecSup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicIdSec + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_CHAIR_COUNCIL"));

        // 5. Council where member removal drops eligible graders below 3 -> finalizeScores rejected with COUNCIL_SIZE_INVALID
        UUID topicIdFour = insertPublishedTopic(roundId);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                + "VALUES (?, ?, 'sa-coi-sup', 1)", UUID.randomUUID(), topicIdFour);

        ensureLecturer("sa-coi-mem2");
        UUID councilFour = createCouncil(roundId, "Council Four Graders");
        seatMember(councilFour, "sa-coi-chair", "CHAIR");
        seatMember(councilFour, "sa-coi-sec", "SECRETARY");
        seatMember(councilFour, "sa-coi-mem1", "MEMBER");
        seatMember(councilFour, "sa-coi-sup", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilFour)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicIdFour + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        insertApprovedGroupWithRoster(roundId, topicIdFour, "sa-coi-grp");

        // Remove eligible member sa-coi-mem1 -> council drops to 3 members, eligible graders = 2 < 3
        mvc.perform(delete("/api/v1/thesis/councils/{id}/members/{lecturerId}", councilFour, "sa-coi-mem1")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilFour, topicIdFour)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.0}")
                        .with(lecturerJwt("sa-coi-chair")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilFour, topicIdFour)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5}")
                        .with(lecturerJwt("sa-coi-sec")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilFour, topicIdFour)
                        .with(lecturerJwt("sa-coi-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_SIZE_INVALID"));
    }

    // ---------- Defect 3: Student self-leave ----------

    @Test
    void nonLeaderStudentCanSelfLeaveInDraftOrSubmittedState() throws Exception {
        UUID roundId = insertRound("sa-round-leave", "REGISTRATION_OPEN");
        ensureStudent("sa-leave-leader");
        ensureStudent("sa-leave-member");
        ensureStudent("sa-leave-other");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("sa-leave-leader")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);

        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"sa-leave-member\"}")
                        .with(studentJwt("sa-leave-leader")))
                .andExpect(status().isOk());

        // Other non-leader student cannot remove members
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "sa-leave-member")
                        .with(studentJwt("sa-leave-other")))
                .andExpect(status().isForbidden());

        // Non-leader member can leave on their own in DRAFT
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "sa-leave-member")
                        .with(studentJwt("sa-leave-member")))
                .andExpect(status().isOk());

        Integer memberCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?", Integer.class, groupId);
        assertThat(memberCount).isEqualTo(1);

        // Leader cannot remove themselves
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "sa-leave-leader")
                        .with(studentJwt("sa-leave-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_CANNOT_BE_REMOVED"));

        // Non-leader can self-leave in SUBMITTED state
        UUID roundSub = insertRound("sa-round-leave-sub", "REGISTRATION_OPEN");
        ensureStudent("sa-sub-leader");
        ensureStudent("sa-sub-member");
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundSub + "\"}")
                        .with(studentJwt("sa-sub-leader")))
                .andExpect(status().isOk());
        UUID groupSubId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundSub);
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupSubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"sa-sub-member\"}")
                        .with(studentJwt("sa-sub-leader")))
                .andExpect(status().isOk());
        UUID topicSub = insertPublishedTopic(roundSub);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupSubId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicSub + "\"}")
                        .with(studentJwt("sa-sub-leader")))
                .andExpect(status().isOk());
        // Group is now in SUBMITTED status and PENDING approval
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupSubId, "sa-sub-member")
                        .with(studentJwt("sa-sub-member")))
                .andExpect(status().isOk());
        Integer subCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?", Integer.class, groupSubId);
        assertThat(subCount).isEqualTo(1);

        // Non-leader CANNOT self-leave once group is APPROVED
        UUID groupAppId = insertApprovedGroupWithRoster(roundSub, topicSub, "sa-app-grp");
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupAppId, "sa-app-grp-m2")
                        .with(studentJwt("sa-app-grp-m2")))
                .andExpect(status().isForbidden());
    }

    // ---------- Defect 4: GVPB threshold & anti-lockout ----------

    @Test
    void finalizeScoresEnforcesGvpbThresholdInRoundsRequiringGvpb() throws Exception {
        UUID roundId = insertRound("sa-round-gvpb-gate", "REGISTRATION_CLOSED");
        UUID topicId = insertPublishedTopic(roundId);

        ensureLecturer("sa-gvpb-chair");
        ensureLecturer("sa-gvpb-sec");
        ensureLecturer("sa-gvpb-mem");
        ensureLecturer("sa-gvpb-reviewer");

        UUID councilId = createCouncil(roundId, "Council GVPB Gate");
        seatMember(councilId, "sa-gvpb-chair", "CHAIR");
        seatMember(councilId, "sa-gvpb-sec", "SECRETARY");
        seatMember(councilId, "sa-gvpb-mem", "MEMBER");

        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Assign GVPB reviewer
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"sa-gvpb-reviewer\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Create approved group with roster so scoring is permitted
        insertApprovedGroupWithRoster(roundId, topicId, "sa-gvpb-grp");

        // Council members submit defense scores
        for (String member : new String[]{"sa-gvpb-chair", "sa-gvpb-sec", "sa-gvpb-mem"}) {
            mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"score\":8.0}")
                            .with(lecturerJwt(member)))
                    .andExpect(status().isOk());
        }

        // 1. Finalize without GVPB score -> 409 REVIEW_SCORE_REQUIRED
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("sa-gvpb-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_SCORE_REQUIRED"));

        // 2. Submit GVPB score < 5.0 (4.9) -> finalize rejected with REVIEW_SCORE_BELOW_THRESHOLD
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, lecturer_id, component, score) "
                + "VALUES (?, ?, 'sa-gvpb-reviewer', 'GVPB', 4.9)", UUID.randomUUID(), topicId);

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("sa-gvpb-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_SCORE_BELOW_THRESHOLD"));

        // 3. Update GVPB score to boundary >= 5.0 (5.0) -> finalize succeeds
        jdbc.update("UPDATE thesis.thesis_topic_score SET score = 5.0 WHERE topic_id = ? AND component = 'GVPB'", topicId);

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("sa-gvpb-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(8.0))
                .andExpect(jsonPath("$.status").value("GRADED"));
    }

    // ---------- fixtures ----------

    private List<String> auditActions(UUID entityId) {
        return jdbc.queryForList(
                "SELECT \"action\" FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                String.class, entityId.toString());
    }

    private org.springframework.test.web.servlet.ResultActions addMember(UUID groupId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(studentJwt("sa-leader-1")));
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

    private UUID insertRoundProposalOpen(String name) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, 'PROPOSAL_OPEN')",
                roundId, name,
                Timestamp.from(now.minusSeconds(3_600)), Timestamp.from(now.plusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(3_600)), Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.plusSeconds(172_800)));
        return roundId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'fixture', 3, 'PUBLISHED', ?)",
                topicId, roundId, "sa-topic-" + topicId.toString().substring(0, 8), UUID.randomUUID());
        return topicId;
    }

    private void ensureStudent(String studentId) {
        String userId = "sa-user-" + studentId;
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
        String userId = "sa-user-" + lecturerId;
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
        // The dean scope resolves the faculty through a live lecturer profile;
        // a bare TRUONG_KHOA token without one fails closed. ensureUser runs
        // inside role() first, so the Lecturer row can reference it; the
        // second role() call rebuilds the JWT with the lecturerId claim.
        ThesisAuthorityFixture.role(jdbc, "sa-tk-user", "TRUONG_KHOA");
        Integer bound = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'sa-tk-lecturer'",
                Integer.class);
        if (bound == null || bound == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                    + "VALUES ('sa-tk-lecturer', 'sa-tk-user', 'department-demo', 'GV-sa-tk', TRUE)");
        }
        return ThesisAuthorityFixture.role(jdbc, "sa-tk-user", "TRUONG_KHOA");
    }

    private void ensureDepartment(String departmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\" = ?", Integer.class, departmentId);
        if (count == null || count == 0) {
            jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                    + "SELECT ?, 'Other Department', 'OD', \"facultyId\", TRUE FROM academic.\"Department\" WHERE \"id\" = 'department-demo'",
                    departmentId);
        }
    }

    private UUID createCouncil(UUID roundId, String name) throws Exception {
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"" + name + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = ?", UUID.class, name);
    }

    private void seatMember(UUID councilId, String lecturerId, String role) throws Exception {
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"" + lecturerId + "\",\"memberRole\":\"" + role + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
    }

    private RequestPostProcessor truongBoMonJwt(String lecturerId, String departmentId) {
        ensureLecturer(lecturerId);
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"departmentId\" = ? WHERE \"id\" = ?", departmentId, lecturerId);
        String subject = jdbc.queryForObject("SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?", String.class, lecturerId);
        AccountAuthorityFixture.replaceRoles(jdbc, subject, "TRUONG_BO_MON");
        return CurrentAccountJwt.currentJwt(jdbc, subject);
    }

    private UUID insertApprovedGroupWithRoster(UUID roundId, UUID topicId, String tag) {
        UUID groupId = UUID.randomUUID();
        String leader = tag + "-leader";
        ensureStudent(leader);
        ensureStudent(tag + "-m2");
        ensureStudent(tag + "-m3");
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, leader, topicId);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 1, TRUE)",
                UUID.randomUUID(), groupId, roundId, leader);
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 2, FALSE)",
                UUID.randomUUID(), groupId, roundId, tag + "-m2");
        jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                        + "VALUES (?, ?, ?, ?, 3, FALSE)",
                UUID.randomUUID(), groupId, roundId, tag + "-m3");
        return groupId;
    }
}
