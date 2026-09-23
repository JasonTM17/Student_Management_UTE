package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Course-brief governance arc (plans/20260909-pdf-requirements-audit):
 * constrained round types with two ordered windows (R1/R2), one-to-two
 * supervisors (R3), single-active-group students (R4), leader-only reports
 * (R5), councils of three to five with chair and secretary (R6), average
 * finalization (R7), the supervisor grading exclusion (R8), and published
 * student results (R9). Every guarded case failed before the remediation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_governance;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisGovernanceIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
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
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'gov-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'gov-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'gov-%'");
    }

    // ---------- R1/R2: round types, windows, phase order ----------

    @Test
    void roundCreationRejectsUnknownTypesAndConditionalDeadlineMixups() throws Exception {
        // LECTURER lost round authority to TRUONG_KHOA (R1/R12).
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Lecturer"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("FOO", "Gov Round Bad Type"))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        // TLCN without the GVPB deadline is invalid; NCKH must not carry one.
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("TLCN", "Gov Round TLCN Missing")
                                .replace("\"gvpbDeadline\":\"2027-03-01T00:00:00Z\",", ""))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("NCKH", "Gov Round NCKH With Deadline"))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        // KLTN without the council report date is invalid.
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round KLTN Missing Report")
                                .replace(",\"reportDate\":\"2027-03-10T00:00:00Z\"", ""))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        // The lecturer window must close before student registration opens.
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Overlapping Windows")
                                .replace("\"lecturerSubmitEnd\":\"2027-01-15T00:00:00Z\"",
                                        "\"lecturerSubmitEnd\":\"2027-01-20T00:00:00Z\""))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Valid"))
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thesisType").value("KLTN"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void lecturerProposalWindowGatesTopicCreationAndPublishing() throws Exception {
        ensureLecturer("gov-lecturer-1");
        // A closed lecturer window rejects topic submission (failed before the fix).
        UUID closedRound = insertRound("Gov Round Closed Window", "PROPOSAL_OPEN",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(closedRound, "Closed Window Topic"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LECTURER_WINDOW_CLOSED"));

        // A live lecturer window accepts submission and publishing.
        UUID openRound = insertRound("Gov Round Open Window", "PROPOSAL_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(openRound, "Open Window Topic"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isOk());
        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Open Window Topic'", UUID.class);
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId).with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isOk());

        // After the catalog is published no further topic submission happens.
        UUID registrationRound = insertRound("Gov Round Registration", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(registrationRound, "Late Topic"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_ACCEPTING_PROPOSALS"));
    }

    // ---------- R3: one to two supervisors ----------

    @Test
    void supervisorListsAcceptOneOrTwoActiveLecturersOnly() throws Exception {
        UUID roundId = insertRound("Gov Supervisor Round", "PROPOSAL_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureLecturer("gov-lecturer-1");
        ensureLecturer("gov-lecturer-2");
        ensureLecturer("gov-lecturer-3");
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Supervised Topic"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isOk());
        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Supervised Topic'", UUID.class);

        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"gov-lecturer-1\",\"gov-lecturer-2\",\"gov-lecturer-3\"]}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[]}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"gov-lecturer-1\",\"gov-lecturer-2\"]}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].lecturerId").value("gov-lecturer-1"));

        // A student or unrelated lecturer cannot rewrite supervision.
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"gov-lecturer-2\"]}")
                        .with(studentJwt(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());

        UUID councilId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) VALUES (?, ?, ?, 'ACTIVE', ?)",
                councilId, roundId, "Supervisor Freeze Council", "test-admin");
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, topicId, "test-admin");
        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"gov-lecturer-2\"]}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_STATE_CONFLICT"));
    }

    // ---------- Feedback items 5 and 11: the topic supervisor manages membership ----------

    @Test
    void supervisingLecturerCanCompleteAnApprovedGroupsMembership() throws Exception {
        UUID roundId = insertRound("Gov Supervisor Member Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureLecturer("gov-supervisor-1");
        ensureLecturer("gov-outsider-1");
        ensureStudent("gov-member-1", "gov-user-1", "gov-member-1@campuscore.edu");
        ensureStudent("gov-member-2", "gov-user-2", "gov-member-2@campuscore.edu");
        ensureStudent("gov-member-3", "gov-user-3", "gov-member-3@campuscore.edu");
        ensureStudent("gov-member-4", "gov-user-4", "gov-member-4@campuscore.edu");
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-member-1", topicId, "APPROVED");
        insertMember(groupId, roundId, "gov-member-1", 1);
        insertMember(groupId, roundId, "gov-member-2", 2);
        insertMember(groupId, roundId, "gov-member-3", 3);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'gov-supervisor-1', 1)",
                UUID.randomUUID(), topicId);

        // The supervisor may complete a valid 3-member approved roster to four.
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"gov-member-4\"}")
                        .with(lecturerJwt("gov-supervisor-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(4));

        // A lecturer who does not supervise this topic is still locked out.
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"gov-member-5\"}")
                        .with(lecturerJwt("gov-outsider-1")))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "gov-member-4")
                        .with(lecturerJwt("gov-supervisor-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(3));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?", Integer.class, groupId))
                .isEqualTo(3);

        // The leader's own path still works through the same endpoint.
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"gov-member-4\"}")
                        .with(studentJwt("gov-member-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
    }

    @Test
    void approvedGroupCannotShrinkBelowThreeMembers() throws Exception {
        UUID roundId = insertRound("Gov Approved Roster Shrink", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureLecturer("gov-shrink-supervisor");
        ensureStudent("gov-shrink-leader", "gov-shrink-leader-user", "gov-shrink-leader@campuscore.edu");
        ensureStudent("gov-shrink-member-2", "gov-shrink-member-2-user", "gov-shrink-member-2@campuscore.edu");
        ensureStudent("gov-shrink-member-3", "gov-shrink-member-3-user", "gov-shrink-member-3@campuscore.edu");
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-shrink-leader", topicId, "APPROVED");
        insertMember(groupId, roundId, "gov-shrink-leader", 1);
        insertMember(groupId, roundId, "gov-shrink-member-2", 2);
        insertMember(groupId, roundId, "gov-shrink-member-3", 3);
        jdbc.update(
                "INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'gov-shrink-supervisor', 1)",
                UUID.randomUUID(), topicId);

        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "gov-shrink-member-3")
                        .with(lecturerJwt("gov-shrink-supervisor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?", Integer.class, groupId))
                .isEqualTo(3);
    }

    // ---------- R4: single active group per student ----------

    @Test
    void studentsCannotHoldGroupsInTwoRoundsThatAreStillActive() throws Exception {
        UUID roundA = insertRound("Gov Active Round A", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID roundB = insertRound("Gov Active Round B", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureStudent("gov-member-1", "gov-user-1", "gov-member-1@campuscore.edu");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundA + "\"}")
                        .with(studentJwt("gov-member-1")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundB + "\"}")
                        .with(studentJwt("gov-member-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STUDENT_ACTIVE_IN_OTHER_GROUP"));
    }

    @Test
    void memberOrdersStayUniqueAfterRemovals() throws Exception {
        UUID roundId = insertRound("Gov Member Order Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureStudent("gov-member-2", "gov-user-2", "gov-member-2@campuscore.edu");
        ensureStudent("gov-member-3", "gov-user-3", "gov-member-3@campuscore.edu");
        ensureStudent("gov-member-4", "gov-user-4", "gov-member-4@campuscore.edu");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("gov-member-2")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        addMember(groupId, "gov-member-3").andExpect(status().isOk());
        addMember(groupId, "gov-member-4").andExpect(status().isOk());

        String memberThreeId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group_member WHERE group_id = ? AND student_id = 'gov-member-3'",
                String.class, groupId);
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE id = ?", memberThreeId);

        String replacement = "gov-member-" + UUID.randomUUID().toString().substring(0, 8);
        ensureStudent(replacement, "gov-user-" + replacement, replacement + "@campuscore.edu");
        addMember(groupId, replacement).andExpect(status().isOk());

        List<Integer> orders = jdbc.queryForList(
                "SELECT member_order FROM thesis.thesis_group_member WHERE group_id = ? ORDER BY member_order",
                Integer.class, groupId);
        long duplicates = orders.stream().distinct().count();
        org.junit.jupiter.api.Assertions.assertEquals(orders.size(), duplicates,
                "member orders must stay unique after removal + re-add");
    }

    @Test
    void aLeaderOnlyGroupCannotBeApproved() throws Exception {
        UUID roundId = insertRound("Gov Tiny Group Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureStudent("gov-member-5", "gov-user-5", "gov-member-5@campuscore.edu");

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("gov-member-5")))
                .andExpect(status().isOk());
        UUID groupId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_group WHERE round_id = ?", UUID.class, roundId);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(studentJwt("gov-member-5")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
    }

    // ---------- R5: leader-only report submission ----------

    @Test
    void reportsAreLeaderOnlyAndFrozenAfterTheGvpbDeadline() throws Exception {
        UUID roundId = insertRound("Gov Report Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-leader-report", topicId, "APPROVED");
        insertMember(groupId, roundId, "gov-leader-report", 1);
        ensureStudent("gov-leader-report", "gov-user-report", "gov-leader@campuscore.edu");
        ensureStudent("gov-peer-report", "gov-user-peer", "gov-peer@campuscore.edu");
        insertMember(groupId, roundId, "gov-peer-report", 2);
        ensureStudent("gov-member-report-3", "gov-user-member-report-3", "gov-member-report-3@campuscore.edu");
        ensureStudent("gov-member-report-4", "gov-user-member-report-4", "gov-member-report-4@campuscore.edu");
        insertMember(groupId, roundId, "gov-member-report-3", 3);
        insertMember(groupId, roundId, "gov-member-report-4", 4);

        // A non-leader member cannot submit (R5).
        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/report.pdf\"}")
                        .with(studentJwt("gov-peer-report")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_OWNER_REQUIRED"));

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/report.pdf\",\"title\":\"Final report\"}")
                        .with(studentJwt("gov-leader-report")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://drive.example.com/report.pdf"));

        mvc.perform(get("/api/v1/thesis/groups/{id}/report", groupId)
                        .with(studentJwt("gov-leader-report")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Final report"));
        mvc.perform(get("/api/v1/thesis/groups/{id}/report", groupId)
                        .with(studentJwt("gov-peer-report")))
                .andExpect(status().isOk());

        UUID frozenRound = insertRound("Gov Frozen Report Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1_800)), frozenRound);
        UUID frozenTopic = insertPublishedTopic(frozenRound);
        UUID frozenGroup = insertGroup(frozenRound, "gov-leader-frozen", frozenTopic, "APPROVED");
        insertMember(frozenGroup, frozenRound, "gov-leader-frozen", 1);
        ensureStudent("gov-frozen-member-2", "gov-frozen-user-2", "gov-frozen-member-2@campuscore.edu");
        ensureStudent("gov-frozen-member-3", "gov-frozen-user-3", "gov-frozen-member-3@campuscore.edu");
        ensureStudent("gov-frozen-member-4", "gov-frozen-user-4", "gov-frozen-member-4@campuscore.edu");
        insertMember(frozenGroup, frozenRound, "gov-frozen-member-2", 2);
        insertMember(frozenGroup, frozenRound, "gov-frozen-member-3", 3);
        insertMember(frozenGroup, frozenRound, "gov-frozen-member-4", 4);

        mvc.perform(post("/api/v1/thesis/groups/{id}/report", frozenGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://drive.example.com/late.pdf\"}")
                        .with(studentJwt("gov-leader-frozen")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_DEADLINE_PASSED"));
    }

    // ---------- R6: council formation ----------

    @Test
    void councilSeatsFillInOrderAndStayWithinThreeToFive() throws Exception {
        UUID roundId = insertRound("Gov Council Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        for (int i = 1; i <= 7; i++) {
            ensureLecturer("gov-council-" + i);
        }

        // Students cannot form councils.
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng A\"}")
                        .with(studentJwt(UUID.randomUUID().toString())))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng A\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng A'", UUID.class);

        // Secretary before chair is refused (deterministic formation order).
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-2\",\"memberRole\":\"SECRETARY\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_INCOMPLETE"));

        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-1\",\"memberRole\":\"CHAIR\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-2\",\"memberRole\":\"SECRETARY\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-3\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-4\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-5\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-council-6\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_SIZE_INVALID"));
    }

    // ---------- R6: council roster read access ----------

    @Test
    void councilRostersAreOnlyReadableByMembersAndGovernanceStaff() throws Exception {
        UUID roundId = insertRound("Gov Council Read Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        for (int i = 1; i <= 4; i++) {
            ensureLecturer("gov-read-" + i);
        }
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng đọc\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng đọc'", UUID.class);
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-read-1\",\"memberRole\":\"CHAIR\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-read-2\",\"memberRole\":\"SECRETARY\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // A student used to read every council roster and pre-publication
        // topic assignment; the boundary now refuses the role outright.
        mvc.perform(get("/api/v1/thesis/councils").param("roundId", roundId.toString())
                        .with(studentJwt("gov-read-student")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/thesis/councils/{id}", councilId)
                        .with(studentJwt("gov-read-student")))
                .andExpect(status().isForbidden());

        // A lecturer outside the council sees an empty list and a 403 detail,
        // never a foreign roster.
        mvc.perform(get("/api/v1/thesis/councils").param("roundId", roundId.toString())
                        .with(lecturerJwt("gov-read-3")))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"));
        mvc.perform(get("/api/v1/thesis/councils/{id}", councilId)
                        .with(lecturerJwt("gov-read-3")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_MEMBER_REQUIRED"));

        // A sitting member reads their own council through both legs.
        mvc.perform(get("/api/v1/thesis/councils").param("roundId", roundId.toString())
                        .with(lecturerJwt("gov-read-1")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(councilId.toString())));
        mvc.perform(get("/api/v1/thesis/councils/{id}", councilId)
                        .with(lecturerJwt("gov-read-2")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("gov-read-1")));

        // Governance staff keep the full list.
        mvc.perform(get("/api/v1/thesis/councils").param("roundId", roundId.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(councilId.toString())));
    }

    // ---------- R6/R7/R8: assignment, scoring, finalize ----------

    @Test
    void scoringExcludesSupervisorsAndTheChairFreezesTheAverageOnce() throws Exception {
        UUID roundId = insertRound("Gov Scoring Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, 1)", UUID.randomUUID(), topicId, "gov-scoring-supervisor");
        for (String lecturer : new String[] {"gov-score-chair", "gov-score-secretary", "gov-score-member", "gov-scoring-supervisor"}) {
            ensureLecturer(lecturer);
        }

        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng chấm\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng chấm'", UUID.class);
        councilSeat(councilId, "gov-score-chair", "CHAIR");
        councilSeat(councilId, "gov-score-secretary", "SECRETARY");
        councilSeat(councilId, "gov-score-member", "MEMBER");
        // The supervisor also sits on the council; the exclusion must still
        // refuse to let them grade their own topic.
        councilSeat(councilId, "gov-scoring-supervisor", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/thesis/councils/{id}/members/{lecturerId}", councilId, "gov-score-secretary")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_SECRETARY_REQUIRED"));

        // Brief R8: the supervisor cannot grade their own topic.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9}")
                        .with(lecturerJwt("gov-scoring-supervisor")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SUPERVISOR_CANNOT_GRADE"));

        // Non-members cannot grade either.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_MEMBER_REQUIRED"));

        // Out-of-range scores are rejected.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":11}")
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"not-a-number\"}")
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7,\"component\":\"proposal\"}")
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/thesis/councils/{id}/members/{lecturerId}", councilId, "gov-score-chair")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COUNCIL_STATE_CONFLICT"));

        // Raw committee scores stay staff/council-only; students use /me/results after publication.
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .with(studentJwt("gov-score-student")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_SCORE_ACCESS_REQUIRED"));
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(7));

        // A chair cannot finalize a partial committee result.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORES_INCOMPLETE"));

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5}")
                        .with(lecturerJwt("gov-score-secretary")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9}")
                        .with(lecturerJwt("gov-score-member")))
                .andExpect(status().isOk());

        // Only the chair finalizes, and the average equals the component mean.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-score-secretary")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_ROLE_REQUIRED"));
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(8.17));

        // Finalization is a one-time CAS event.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-score-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_ALREADY_FINALIZED"));
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":10}")
                        .with(lecturerJwt("gov-score-member")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORE_ALREADY_FINALIZED"));
    }

    // ---------- R9: publication and student results ----------

    @Test
    void resultsPublishAfterGradesAndStudentsReadTheirOwnRow() throws Exception {
        UUID roundId = insertRound("Gov Results Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-result-leader", topicId, "APPROVED");
        ensureStudent("gov-result-leader", "gov-user-result", "gov-result-leader@campuscore.edu");
        insertMember(groupId, roundId, "gov-result-leader", 1);
        ensureStudent("gov-result-member-2", "gov-user-result-2", "gov-result-member-2@campuscore.edu");
        ensureStudent("gov-result-member-3", "gov-user-result-3", "gov-result-member-3@campuscore.edu");
        ensureStudent("gov-result-member-4", "gov-user-result-4", "gov-result-member-4@campuscore.edu");
        insertMember(groupId, roundId, "gov-result-member-2", 2);
        insertMember(groupId, roundId, "gov-result-member-3", 3);
        insertMember(groupId, roundId, "gov-result-member-4", 4);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.5, result_status = 'GRADED' WHERE id = ?", topicId);

        // Nothing graded -> publication refused.
        UUID emptyRound = insertRound("Gov Results Empty", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-results", emptyRound).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULTS_NOT_READY"));

        // One graded topic is not enough while another approved group waits.
        UUID pendingTopicId = insertPublishedTopic(roundId);
        UUID pendingGroup = insertGroup(roundId, "gov-result-pending", pendingTopicId, "APPROVED");
        ensureStudent("gov-result-pending-2", "gov-user-result-pending-2", "gov-result-pending-2@campuscore.edu");
        ensureStudent("gov-result-pending-3", "gov-user-result-pending-3", "gov-result-pending-3@campuscore.edu");
        insertMember(pendingGroup, roundId, "gov-result-pending", 1);
        insertMember(pendingGroup, roundId, "gov-result-pending-2", 2);
        insertMember(pendingGroup, roundId, "gov-result-pending-3", 3);
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-results", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORES_INCOMPLETE"));
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 7.5, result_status = 'GRADED' WHERE id = ?", pendingTopicId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-results", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULTS_PUBLISHED"));

        mvc.perform(get("/api/v1/thesis/me/results")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-result-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].topicTitle").value("Gov Results Topic"))
                .andExpect(jsonPath("$[0].finalScore").value(8.5));

        // A student without membership reads nothing.
        ensureStudent("gov-outsider", "gov-user-outsider", "gov-outsider@campuscore.edu");
        mvc.perform(get("/api/v1/thesis/me/results")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-outsider")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // Before publication the endpoint refuses to leak scores.
        UUID hiddenRound = insertRound("Gov Results Hidden", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(get("/api/v1/thesis/me/results")
                        .queryParam("roundId", hiddenRound.toString())
                        .with(studentJwt("gov-result-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));
    }

    @Test
    void personalProgressReportsNonParticipationInsteadOfInferringCompletion() throws Exception {
        ensureStudent("gov-progress-outsider", "gov-progress-outsider-user", "gov-progress-outsider@campuscore.edu");
        UUID roundId = insertRound("Gov Progress Historical Round", "PROPOSALS_PUBLISHED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));

        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-progress-outsider")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationState").value("NOT_PARTICIPATING"))
                .andExpect(jsonPath("$.currentMilestone").value("ROUND_SELECTED"))
                .andExpect(jsonPath("$.completedMilestones.length()").value(0))
                .andExpect(jsonPath("$.attentionState").value("NOT_PARTICIPATING"));
    }

    @Test
    void facultyHeadCanReadThesisGroupsAcrossTheRound() throws Exception {
        UUID roundId = insertRound("Gov Faculty Head Group Read", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void personalProgressRequiresPublishedPersonalResult() throws Exception {
        ensureStudent("gov-progress-leader", "gov-progress-leader-user", "gov-progress-leader@campuscore.edu");
        ensureStudent("gov-progress-member", "gov-progress-member-user", "gov-progress-member@campuscore.edu");
        ensureStudent("gov-progress-member-3", "gov-progress-member-3-user", "gov-progress-member-3@campuscore.edu");
        ensureStudent("gov-progress-member-4", "gov-progress-member-4-user", "gov-progress-member-4@campuscore.edu");
        UUID pendingRound = insertRound("Gov Progress Published Without Result", "RESULTS_PUBLISHED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID pendingTopic = insertPublishedTopic(pendingRound);
        UUID pendingGroup = insertGroup(pendingRound, "gov-progress-leader", pendingTopic, "APPROVED");
        insertMember(pendingGroup, pendingRound, "gov-progress-leader", 1);
        insertMember(pendingGroup, pendingRound, "gov-progress-member", 2);
        insertMember(pendingGroup, pendingRound, "gov-progress-member-3", 3);
        insertMember(pendingGroup, pendingRound, "gov-progress-member-4", 4);
        UUID pendingCouncil = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) VALUES (?, ?, ?, 'ACTIVE', ?)",
                pendingCouncil, pendingRound, "Gov Progress Council", "test-admin");
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), pendingCouncil, pendingTopic, "test-admin");
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Gov progress report', 'https://example.test/gov-progress-report.pdf')",
                UUID.randomUUID(), pendingGroup, pendingRound, "gov-progress-leader");

        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", pendingRound.toString())
                        .with(studentJwt("gov-progress-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationState").value("PARTICIPATING"))
                .andExpect(jsonPath("$.currentMilestone").value("COUNCIL_ASSIGNED"))
                .andExpect(jsonPath("$.attentionState").value("RESULT_NOT_AVAILABLE"));

        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.25, result_status = 'GRADED' WHERE id = ?",
                pendingTopic);
        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", pendingRound.toString())
                        .with(studentJwt("gov-progress-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMilestone").value("RESULTS_PUBLISHED"))
                .andExpect(jsonPath("$.completedMilestones", org.hamcrest.Matchers.hasItem("RESULTS_PUBLISHED")))
                .andExpect(jsonPath("$.finalScore").value(8.25))
                .andExpect(jsonPath("$.attentionState").value("NONE"));
    }

    @Test
    void personalProgressWarnsWhenParticipatingGroupHasIncompleteRoster() throws Exception {
        ensureStudent("gov-progress-incomplete-leader", "gov-progress-incomplete-leader-user",
                "gov-progress-incomplete-leader@campuscore.edu");
        UUID roundId = insertRound("Gov Progress Incomplete Roster", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-progress-incomplete-leader", topicId, "PENDING");
        insertMember(groupId, roundId, "gov-progress-incomplete-leader", 1);

        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-progress-incomplete-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationState").value("PARTICIPATING"))
                .andExpect(jsonPath("$.memberCount").value(1))
                .andExpect(jsonPath("$.attentionState").value("GROUP_INVALID_MEMBER_COUNT"));
    }

    @Test
    void personalProgressStopsAtMissingApprovalWhenLaterReportEvidenceExists() throws Exception {
        ensureStudent("gov-progress-inconsistent-leader", "gov-progress-inconsistent-leader-user",
                "gov-progress-inconsistent-leader@campuscore.edu");
        ensureStudent("gov-progress-inconsistent-member", "gov-progress-inconsistent-member-user",
                "gov-progress-inconsistent-member@campuscore.edu");
        ensureStudent("gov-progress-inconsistent-member-3", "gov-progress-inconsistent-member-3-user",
                "gov-progress-inconsistent-member-3@campuscore.edu");
        UUID roundId = insertRound("Gov Progress Inconsistent Evidence", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        UUID groupId = insertGroup(roundId, "gov-progress-inconsistent-leader", topicId, "PENDING");
        insertMember(groupId, roundId, "gov-progress-inconsistent-leader", 1);
        insertMember(groupId, roundId, "gov-progress-inconsistent-member", 2);
        insertMember(groupId, roundId, "gov-progress-inconsistent-member-3", 3);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Retained report', 'https://example.test/retained-report.pdf')",
                UUID.randomUUID(), groupId, roundId, "gov-progress-inconsistent-leader");

        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-progress-inconsistent-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentMilestone").value("TOPIC_ASSIGNED"))
                .andExpect(jsonPath("$.completedMilestones", org.hamcrest.Matchers.hasItem("TOPIC_ASSIGNED")))
                .andExpect(jsonPath("$.completedMilestones", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("REPORT_SUBMITTED"))))
                .andExpect(jsonPath("$.attentionState").value("PROGRESS_INCONSISTENT"));
    }

    // ---------- fixtures ----------

    private String validRoundBody(String type, String name) {
        return "{\"name\":\"" + name + "\",\"thesisType\":\"" + type + "\"," +
                "\"lecturerSubmitStart\":\"2026-12-01T00:00:00Z\"," +
                "\"lecturerSubmitEnd\":\"2027-01-15T00:00:00Z\"," +
                "\"registrationStart\":\"2027-01-15T00:00:00Z\"," +
                "\"registrationEnd\":\"2027-02-15T00:00:00Z\"," +
                "\"gvpbDeadline\":\"2027-03-01T00:00:00Z\"," +
                "\"reportDate\":\"2027-03-10T00:00:00Z\"," +
                "\"defenseDate\":\"2027-03-20T00:00:00Z\"}";
    }

    private String topicBody(UUID roundId, String title) {
        return "{\"roundId\":\"" + roundId + "\",\"departmentId\":\"department-demo\"," +
                "\"title\":\"" + title + "\",\"description\":\"Governance fixture topic\",\"maxGroups\":2}";
    }

    private UUID insertRound(String name, String status, Instant windowStart, Instant windowEnd) {
        UUID roundId = UUID.randomUUID();
        Instant lecturerStart;
        Instant lecturerEnd;
        Instant registrationStart;
        Instant registrationEnd;
        if ("PROPOSAL_OPEN".equals(status)) {
            lecturerStart = windowStart;
            lecturerEnd = windowEnd;
            registrationStart = windowEnd;
            registrationEnd = windowEnd.plusSeconds(31L * 24 * 60 * 60);
        } else {
            registrationStart = windowStart;
            registrationEnd = windowEnd;
            lecturerStart = windowStart.minusSeconds(7_200);
            lecturerEnd = windowStart.minusSeconds(3_600);
        }
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                roundId, name, "KLTN",
                Timestamp.from(lecturerStart), Timestamp.from(lecturerEnd),
                Timestamp.from(registrationStart), Timestamp.from(registrationEnd),
                status);
        return roundId;
    }

    private UUID insertPublishedTopic(UUID roundId) {
        UUID topicId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'PUBLISHED', ?)",
                topicId, roundId, UUID.randomUUID(), "Gov Results Topic", "Results fixture topic", 1, UUID.randomUUID());
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

    private void councilSeat(UUID councilId, String lecturerId, String role) throws Exception {
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"" + lecturerId + "\",\"memberRole\":\"" + role + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions addMember(UUID groupId, String studentId) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\"}")
                .with(studentJwt(studentId.equals("gov-member-2") ? "gov-member-2" : leaderOf(groupId))));
    }

    private String leaderOf(UUID groupId) {
        return jdbc.queryForObject(
                "SELECT leader_student_id FROM thesis.thesis_group WHERE id = ?", String.class, groupId);
    }

    private void ensureStudent(String studentId, String userId, String email) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Gov', 'Member', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, email);
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "gov-lecturer-user-" + lecturerId;
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Gov', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, lecturerId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userId, "GV-" + lecturerId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor studentJwt(String studentId) {
        return jwt().jwt(token -> token
                        .subject("gov-user-" + studentId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor lecturerJwt(String userId) {
        return jwt().jwt(token -> token
                        .subject(userId)
                        .claim("roles", List.of("LECTURER"))
                        .claim("lecturerId", userId))
                .authorities(new SimpleGrantedAuthority("ROLE_LECTURER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor truongKhoaJwt() {
        return jwt().jwt(token -> token
                        .subject("truongkhoa-user")
                        .claim("roles", List.of("TRUONG_KHOA")))
                .authorities(new SimpleGrantedAuthority("ROLE_TRUONG_KHOA"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
