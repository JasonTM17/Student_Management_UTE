package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.exception.DomainException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private ThesisCouncilService councils;

    @Autowired
    private PlatformTransactionManager txManager;

    @MockitoSpyBean
    private NamedParameterJdbcTemplate namedJdbc;

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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THESIS_ROUND_SCHEDULE_ORDER"))
                .andExpect(jsonPath("$.details.rule").value("REGISTRATION_START_BEFORE_LECTURER_END"));

        // Each window must also end strictly after it starts.
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Inverted Window")
                                .replace("\"registrationEnd\":\"2027-02-15T00:00:00Z\"",
                                        "\"registrationEnd\":\"2027-01-10T00:00:00Z\""))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THESIS_ROUND_SCHEDULE_ORDER"))
                .andExpect(jsonPath("$.details.rule").value("REGISTRATION_WINDOW"));

        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Valid"))
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.thesisType").value("KLTN"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        // The update path names the violated rule the same way.
        UUID validId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = 'Gov Round Valid'",
                UUID.class);
        mvc.perform(put("/api/v1/thesis/rounds/{id}", validId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRoundBody("KLTN", "Gov Round Valid")
                                .replace("\"registrationStart\":\"2027-01-15T00:00:00Z\"",
                                        "\"registrationStart\":\"2027-01-14T00:00:00Z\""))
                        .with(truongKhoaJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THESIS_ROUND_SCHEDULE_ORDER"))
                .andExpect(jsonPath("$.details.rule").value("REGISTRATION_START_BEFORE_LECTURER_END"));
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

        // A live lecturer window accepts proposals; approval is a governance action.
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
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId).with(truongKhoaJwt()))
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

    @Test
    void cancelledRoundRejectsSupervisorRosterAndTopicPublishing() throws Exception {
        ensureLecturer("gov-lecturer-1");
        ensureLecturer("gov-lecturer-2");
        UUID roundId = insertRound("Gov Terminal Roster Round", "PROPOSAL_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(post("/api/v1/thesis/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(topicBody(roundId, "Terminal Roster Topic"))
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isOk());
        UUID topicId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_topic WHERE title = 'Terminal Roster Topic'", UUID.class);
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'CANCELLED' WHERE id = ?", roundId);

        mvc.perform(put("/api/v1/thesis/topics/{id}/supervisors", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supervisorIds\":[\"gov-lecturer-1\",\"gov-lecturer-2\"]}")
                        .with(lecturerJwt("gov-lecturer-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_ACCEPTING_PROPOSALS"));
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

        // The supervisor may grow an approved three-member roster to four.
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
    void approvedGroupRosterCannotFallBelowThreeOrLoseItsLeader() throws Exception {
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

        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "gov-shrink-member-2")
                        .with(lecturerJwt("gov-shrink-supervisor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOO_SMALL"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?", Integer.class, groupId))
                .isEqualTo(3);
        mvc.perform(delete("/api/v1/thesis/groups/{id}/members/{studentId}", groupId, "gov-shrink-leader")
                        .with(lecturerJwt("gov-shrink-supervisor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEADER_CANNOT_BE_REMOVED"));
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
    void aLeaderOnlyGroupMustCompleteItsRosterBeforeApproval() throws Exception {
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
        assertThat(jdbc.queryForObject("SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId)).isEqualTo("PENDING");
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
        insertMember(groupId, roundId, "gov-member-report-3", 3);

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
        insertMember(frozenGroup, frozenRound, "gov-frozen-member-2", 2);
        insertMember(frozenGroup, frozenRound, "gov-frozen-member-3", 3);

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
        insertApprovedGroupWithRoster(roundId, topicId, "gov-score");

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

    // ---------- K16: score audit trail and SUPER_ADMIN parity ----------

    @Test
    void councilScoreWritesAndFinalizationLeaveAnAdminAuditTrail() throws Exception {
        UUID roundId = insertRound("Gov Audit Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        for (String lecturer : new String[] {"gov-audit-chair", "gov-audit-secretary", "gov-audit-member"}) {
            ensureLecturer(lecturer);
        }

        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng audit\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng audit'", UUID.class);
        councilSeat(councilId, "gov-audit-chair", "CHAIR");
        councilSeat(councilId, "gov-audit-secretary", "SECRETARY");
        councilSeat(councilId, "gov-audit-member", "MEMBER");
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-audit");

        // First score: no outgoing value; the trace carries the recorded one.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-audit-chair")))
                .andExpect(status().isOk());
        Map<String, Object> first = auditRow(topicId, "COUNCIL_SCORE_SUBMITTED", "score=7");
        assertThat(first.get("actorId")).isEqualTo("gov-lecturer-user-gov-audit-chair");
        assertThat(first.get("entityType")).isEqualTo("THESIS_TOPIC");
        assertThat(String.valueOf(first.get("beforeState"))).contains("score=null");
        assertThat(String.valueOf(first.get("afterState"))).contains("score=7");

        // The DELETE-then-INSERT overwrite must still record old -> new.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5}")
                        .with(lecturerJwt("gov-audit-chair")))
                .andExpect(status().isOk());
        Map<String, Object> overwrite = auditRow(topicId, "COUNCIL_SCORE_SUBMITTED", "score=8.5");
        assertThat(String.valueOf(overwrite.get("beforeState"))).contains("score=7");
        assertThat(String.valueOf(overwrite.get("afterState"))).contains("score=8.5");

        // A refused finalize writes no finalization trace.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-audit-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCORES_INCOMPLETE"));
        assertThat(auditRows(topicId, "COUNCIL_SCORE_FINALIZED")).isEmpty();

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8}")
                        .with(lecturerJwt("gov-audit-secretary")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9}")
                        .with(lecturerJwt("gov-audit-member")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-audit-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalScore").value(8.5));

        Map<String, Object> finalized = auditRow(topicId, "COUNCIL_SCORE_FINALIZED", "final_score=8.5");
        assertThat(finalized.get("actorId")).isEqualTo("gov-lecturer-user-gov-audit-chair");
        assertThat(String.valueOf(finalized.get("beforeState"))).contains("final_score=null");
        assertThat(String.valueOf(finalized.get("afterState"))).contains("result_status=GRADED");
    }

    @Test
    void superAdminCountsAsGovernanceStaffForCouncilsAndScores() throws Exception {
        UUID roundId = insertRound("Gov Super Admin Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        for (String lecturer : new String[] {"gov-sa-chair", "gov-sa-secretary", "gov-sa-member"}) {
            ensureLecturer(lecturer);
        }

        // Governance role: SUPER_ADMIN creates the council and assigns the topic.
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng super admin\"}")
                        .with(superAdminJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng super admin'", UUID.class);
        councilSeat(councilId, "gov-sa-chair", "CHAIR", superAdminJwt());
        councilSeat(councilId, "gov-sa-secretary", "SECRETARY", superAdminJwt());
        councilSeat(councilId, "gov-sa-member", "MEMBER", superAdminJwt());
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(superAdminJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-sa");

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8}")
                        .with(lecturerJwt("gov-sa-chair")))
                .andExpect(status().isOk());

        // The controller advertised SUPER_ADMIN on this route while the service answered 403.
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .with(superAdminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].score").value(8));
    }

    // ---------- R8b: cross-faculty concealment and workflow invariants ----------

    @Test
    void aDeanCannotReadOrRestaffAForeignFacultyCouncilOrScores() throws Exception {
        // A second faculty gives the foreign dean a resolved scope that still
        // excludes the fixture council; without a profile the check fails
        // closed before concealment is even evaluated.
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\",\"name\",\"isActive\") "
                + "VALUES ('gov-faculty-foreign','Khoa ngoài phạm vi',TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"nameEn\",\"nameVi\",\"code\",\"facultyId\",\"isActive\") "
                + "VALUES ('gov-department-foreign','Foreign','Foreign','Foreign','FR','gov-faculty-foreign',TRUE)");
        ThesisAuthorityFixture.role(jdbc, "gov-foreign-dean-user", "TRUONG_KHOA");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                + "VALUES ('gov-foreign-dean-lecturer','gov-foreign-dean-user','gov-department-foreign','GV-foreign-dean',TRUE)");
        var foreignDean = ThesisAuthorityFixture.role(jdbc, "gov-foreign-dean-user", "TRUONG_KHOA");

        UUID roundId = insertRound("Gov Foreign Council Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        CouncilFixture fixture = councilFixture(roundId, "gov-foreign");

        // Every roster/read surface answers the concealment 404, never data.
        mvc.perform(get("/api/v1/thesis/councils/{id}", fixture.councilId()).with(foreignDean))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", fixture.councilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-foreign-chair\",\"memberRole\":\"MEMBER\"}")
                        .with(foreignDean))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/thesis/councils/{id}/members/{lecturerId}",
                        fixture.councilId(), "gov-foreign-member").with(foreignDean))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", fixture.councilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + fixture.topicId() + "\"}")
                        .with(foreignDean))
                .andExpect(status().isNotFound());
        // Unpublished scores conceal through the same topic scope.
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores",
                        fixture.councilId(), fixture.topicId()).with(foreignDean))
                .andExpect(status().isNotFound());
        // The round listing silently drops councils outside the dean's faculty.
        mvc.perform(get("/api/v1/thesis/councils")
                        .queryParam("roundId", roundId.toString())
                        .with(foreignDean))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
        // The owning dean still sees everything, proving the scope is
        // faculty-based rather than a blanket council denial.
        mvc.perform(get("/api/v1/thesis/councils/{id}", fixture.councilId()).with(truongKhoaJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void aPendingGroupCannotSkipApprovalStraightToCompleted() throws Exception {
        UUID roundId = insertRound("Gov Wedge Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureStudent("gov-wedge-leader", "gov-wedge-user", "gov-wedge-leader@campuscore.edu");
        UUID pendingGroup = insertGroup(roundId, "gov-wedge-leader", topicId, "PENDING");
        insertMember(pendingGroup, roundId, "gov-wedge-leader", 1);

        // COMPLETED before approval would wedge the group forever: approval
        // requires SUBMITTED status, which COMPLETED no longer reports.
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", pendingGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}")
                        .with(studentJwt("gov-wedge-leader")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));

        // The approved path remains open once governance signs off.
        ensureStudent("gov-done-leader", "gov-done-user", "gov-done-leader@campuscore.edu");
        UUID approvedGroup = insertGroup(roundId, "gov-done-leader", topicId, "APPROVED");
        insertMember(approvedGroup, roundId, "gov-done-leader", 1);
        insertMember(approvedGroup, roundId, "gov-done-peer-2", 2);
        insertMember(approvedGroup, roundId, "gov-done-peer-3", 3);
        mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", approvedGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"COMPLETED\"}")
                        .with(studentJwt("gov-done-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void theCounterReviewerCannotAlsoSitOnTheDefendingCouncil() throws Exception {
        UUID roundId = insertRound("Gov Independence Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        // Counter-review assignment only exists on rounds with a GVPB deadline.
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        CouncilFixture fixture = councilFixture(roundId, "gov-indep");

        // Edge 1: a seated council member cannot be named counter-reviewer of
        // a topic their council already defends.
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", fixture.topicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-indep-member\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEWER_MUST_BE_INDEPENDENT"));

        // Edge 2: the member slips in as reviewer while the topic is not yet
        // assigned; the assignment itself must then refuse the conflict.
        UUID secondTopic = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", secondTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-indep-member\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", fixture.councilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + secondTopic + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEWER_MUST_BE_INDEPENDENT"));

        // Edge 3: an outsider reviews a topic already on the council; seating
        // them afterwards is refused the same way.
        ensureLecturer("gov-indep-outsider");
        UUID thirdTopic = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", fixture.councilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + thirdTopic + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", thirdTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-indep-outsider\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", fixture.councilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-indep-outsider\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEWER_MUST_BE_INDEPENDENT"));
    }

    @Test
    void aDeactivatedSupervisorCannotKeepApprovingGroups() throws Exception {
        UUID roundId = insertRound("Gov Deactivated Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("gov-deactivated-sup");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'gov-deactivated-sup', 1)",
                UUID.randomUUID(), topicId);
        ensureStudent("gov-deactivated-leader", "gov-deactivated-user", "gov-deactivated-leader@campuscore.edu");
        UUID groupId = insertGroup(roundId, "gov-deactivated-leader", topicId, "PENDING");
        insertMember(groupId, roundId, "gov-deactivated-leader", 1);
        insertMember(groupId, roundId, "gov-deactivated-peer-2", 2);
        insertMember(groupId, roundId, "gov-deactivated-peer-3", 3);

        // The live supervisor approves normally first.
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", groupId).with(lecturerJwt("gov-deactivated-sup")))
                .andExpect(status().isOk());

        // Deactivating the profile must revoke the same authority instantly.
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\" = FALSE WHERE \"id\" = 'gov-deactivated-sup'");
        ensureStudent("gov-deactivated-leader-2", "gov-deactivated-user-2", "gov-deactivated-leader-2@campuscore.edu");
        UUID secondGroup = insertGroup(roundId, "gov-deactivated-leader-2", topicId, "PENDING");
        insertMember(secondGroup, roundId, "gov-deactivated-leader-2", 1);
        insertMember(secondGroup, roundId, "gov-deactivated-peer-4", 2);
        insertMember(secondGroup, roundId, "gov-deactivated-peer-5", 3);
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", secondGroup).with(lecturerJwt("gov-deactivated-sup")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFinalizedTopicCannotAbsorbNewGroupsThroughApprovalOrReassignment() throws Exception {
        // The publication gate counts every approved group on a topic as
        // graded, so a group admitted after finalization would inherit a
        // defense score it never earned — both entry points must refuse.
        UUID roundId = insertRound("Gov Graded Topic Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID gradedTopic = insertPublishedTopic(roundId);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.5, result_status = 'GRADED' WHERE id = ?",
                gradedTopic);

        ensureStudent("gov-graded-leader", "gov-graded-user", "gov-graded-leader@campuscore.edu");
        UUID pendingGroup = insertGroup(roundId, "gov-graded-leader", gradedTopic, "PENDING");
        insertMember(pendingGroup, roundId, "gov-graded-leader", 1);
        insertMember(pendingGroup, roundId, "gov-graded-peer-2", 2);
        insertMember(pendingGroup, roundId, "gov-graded-peer-3", 3);
        mvc.perform(post("/api/v1/thesis/groups/{id}/approve", pendingGroup).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_ALREADY_GRADED"));
        assertThat(jdbc.queryForObject(
                "SELECT approval_status FROM thesis.thesis_group WHERE id = ?", String.class, pendingGroup))
                .isEqualTo("PENDING");

        // The same wedge applies to a pending group switching onto the topic.
        ensureStudent("gov-graded-mover", "gov-graded-user-m", "gov-graded-mover@campuscore.edu");
        UUID otherTopic = insertPublishedTopic(roundId);
        UUID mover = insertGroup(roundId, "gov-graded-mover", otherTopic, "PENDING");
        insertMember(mover, roundId, "gov-graded-mover", 1);
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic", mover)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + gradedTopic + "\"}")
                        .with(studentJwt("gov-graded-mover")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_ALREADY_GRADED"));
        assertThat(jdbc.queryForObject(
                "SELECT topic_id FROM thesis.thesis_group WHERE id = ?", String.class, mover))
                .isEqualTo(otherTopic.toString());
    }

    @Test
    void aCompletedGroupsRosterIsFinalEvenForItsSupervisor() throws Exception {
        UUID roundId = insertRound("Gov Completed Roster Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("gov-completed-sup");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, 'gov-completed-sup', 1)",
                UUID.randomUUID(), topicId);
        ensureStudent("gov-completed-leader", "gov-completed-user", "gov-completed-leader@campuscore.edu");
        UUID groupId = insertGroup(roundId, "gov-completed-leader", topicId, "APPROVED");
        insertMember(groupId, roundId, "gov-completed-leader", 1);
        insertMember(groupId, roundId, "gov-completed-peer-2", 2);
        insertMember(groupId, roundId, "gov-completed-peer-3", 3);
        jdbc.update("UPDATE thesis.thesis_group SET status = 'COMPLETED' WHERE id = ?", groupId);

        ensureStudent("gov-completed-new", "gov-completed-user-n", "gov-completed-new@campuscore.edu");
        mvc.perform(post("/api/v1/thesis/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":\"gov-completed-new\"}")
                        .with(lecturerJwt("gov-completed-sup")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_STATE_CONFLICT"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId)).isEqualTo(3);
    }

    @Test
    void teammateSearchStaysClosedOutsideRegistrationAndNeverMatchesEmail() throws Exception {
        insertRound("Gov Search Closed Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        ensureStudent("gov-search-target", "gov-search-user-t", "unique-target-mail@campuscore.edu");
        ensureStudent("gov-search-caller", "gov-search-user-c", "gov-search-caller@campuscore.edu");

        // No REGISTRATION_OPEN round: the directory stays closed entirely.
        mvc.perform(get("/api/v1/thesis/students/search").param("q", "CODE-gov-search")
                        .with(studentJwt("gov-search-caller")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        insertRound("Gov Search Open Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        mvc.perform(get("/api/v1/thesis/students/search").param("q", "CODE-gov-search-target")
                        .with(studentJwt("gov-search-caller")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value("gov-search-target"));

        // An email fragment must not act as an existence oracle.
        mvc.perform(get("/api/v1/thesis/students/search").param("q", "unique-target-mail")
                        .with(studentJwt("gov-search-caller")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aLegacyKeyedCouncilMemberStillBlocksTheCounterReviewerSeat() throws Exception {
        // Older rows keyed council members by User.id instead of Lecturer.id;
        // the independence check must resolve both forms or a reviewer could
        // sit inside the council grading the same topic.
        UUID roundId = insertRound("Gov Legacy Council Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        CouncilFixture fixture = councilFixture(roundId, "gov-legacy");

        ensureLecturer("gov-legacy-reviewer");
        String reviewerUserId = jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'gov-legacy-reviewer'",
                String.class);
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (?, ?, ?, 'MEMBER')",
                UUID.randomUUID(), fixture.councilId(), reviewerUserId);

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", fixture.topicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-legacy-reviewer\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEWER_MUST_BE_INDEPENDENT"));

        // A properly independent reviewer still assigns cleanly.
        ensureLecturer("gov-legacy-independent");
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", fixture.topicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-legacy-independent\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void aTopicReportReadPrefersTheCallersOwnGroup() throws Exception {
        // max_groups lets several approved groups share one topic; a member of
        // the second group must land on their own report, not the first.
        UUID roundId = insertRound("Gov Shared Topic Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureStudent("gov-shared-leader-a", "gov-shared-user-a", "gov-shared-leader-a@campuscore.edu");
        ensureStudent("gov-shared-leader-b", "gov-shared-user-b", "gov-shared-leader-b@campuscore.edu");
        UUID groupA = insertGroup(roundId, "gov-shared-leader-a", topicId, "APPROVED");
        insertMember(groupA, roundId, "gov-shared-leader-a", 1);
        insertMember(groupA, roundId, "gov-shared-peer-a2", 2);
        insertMember(groupA, roundId, "gov-shared-peer-a3", 3);
        // Force the older group to be picked first by the fallback ordering.
        Thread.sleep(5);
        UUID groupB = insertGroup(roundId, "gov-shared-leader-b", topicId, "APPROVED");
        insertMember(groupB, roundId, "gov-shared-leader-b", 1);
        insertMember(groupB, roundId, "gov-shared-peer-b2", 2);
        insertMember(groupB, roundId, "gov-shared-peer-b3", 3);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Report A', 'https://example.test/a.pdf')",
                UUID.randomUUID(), groupA, roundId, "gov-shared-leader-a");
        jdbc.update(
                "INSERT INTO thesis.thesis_group_report (id, group_id, round_id, submitted_by, title, url) "
                        + "VALUES (?, ?, ?, ?, 'Report B', 'https://example.test/b.pdf')",
                UUID.randomUUID(), groupB, roundId, "gov-shared-leader-b");

        mvc.perform(get("/api/v1/thesis/topics/{id}/report", topicId)
                        .with(studentJwt("gov-shared-leader-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Report B"));
    }

    @Test
    void publicationRefusesWhileAnyGroupStillAwaitsApproval() throws Exception {
        UUID roundId = insertRound("Gov Pending Publish Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);
        ensureStudent("gov-pub-leader", "gov-pub-user", "gov-pub-leader@campuscore.edu");
        ensureStudent("gov-pub-member-2", "gov-pub-user-2", "gov-pub-member-2@campuscore.edu");
        ensureStudent("gov-pub-member-3", "gov-pub-user-3", "gov-pub-member-3@campuscore.edu");
        UUID approvedGroup = insertGroup(roundId, "gov-pub-leader", topicId, "APPROVED");
        insertMember(approvedGroup, roundId, "gov-pub-leader", 1);
        insertMember(approvedGroup, roundId, "gov-pub-member-2", 2);
        insertMember(approvedGroup, roundId, "gov-pub-member-3", 3);
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.0, result_status = 'GRADED' WHERE id = ?", topicId);

        // A graded round still cannot publish while a second group is PENDING:
        // the pending group would be stranded without a publishable outcome.
        ensureStudent("gov-pub-pending", "gov-pub-user-p", "gov-pub-pending@campuscore.edu");
        UUID secondTopic = insertPublishedTopic(roundId);
        UUID pendingGroup = insertGroup(roundId, "gov-pub-pending", secondTopic, "PENDING");
        insertMember(pendingGroup, roundId, "gov-pub-pending", 1);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-results", roundId).with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPS_PENDING_APPROVAL"));

        // Rejecting it clears the gate; a cancelled pending group is ignored.
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'REJECTED' WHERE id = ?", pendingGroup);
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-results", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESULTS_PUBLISHED"));
    }

    @Test
    void scoringIsRefusedUntilAGroupIsApprovedOnTheTopic() throws Exception {
        UUID roundId = insertRound("Gov Unapproved Score Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureLecturer("gov-noscore-chair");
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng chưa có nhóm\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng chưa có nhóm'", UUID.class);
        ensureLecturer("gov-noscore-secretary");
        ensureLecturer("gov-noscore-member");
        councilSeat(councilId, "gov-noscore-chair", "CHAIR");
        councilSeat(councilId, "gov-noscore-secretary", "SECRETARY");
        councilSeat(councilId, "gov-noscore-member", "MEMBER");
        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());

        // Without an approved group a defense score cannot be pre-baked.
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8}")
                        .with(lecturerJwt("gov-noscore-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_APPROVED"));

        // Approving the group unlocks grading.
        insertApprovedGroupWithRoster(roundId, topicId, "gov-noscore");
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8}")
                        .with(lecturerJwt("gov-noscore-chair")))
                .andExpect(status().isOk());
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
        insertMember(groupId, roundId, "gov-result-member-2", 2);
        insertMember(groupId, roundId, "gov-result-member-3", 3);
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
    void facultyHeadReadsOnlyOwnFacultyThesisGroups() throws Exception {
        // TRUONG_KHOA reads are scoped to the dean's faculty, resolved through
        // the caller's lecturer profile; member contacts are withheld and a
        // governance caller without a profile fails closed (covered by
        // ThesisGroupFacultyScopeTest).
        String deanUser = "gov-faculty-reader-user";
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Gov', 'Dean', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                deanUser, "gov-faculty-reader@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                        + "VALUES (?, ?, 'department-demo', 'GV-gov-faculty-reader', TRUE)",
                "gov-faculty-reader-lecturer", deanUser);
        UUID roundId = insertRound("Gov Faculty Head Group Read", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        ensureStudent("gov-faculty-reader-leader", "gov-faculty-reader-leader-user",
                "gov-faculty-reader-leader@campuscore.edu");
        UUID ownTopic = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Gov Faculty Topic', 'fixture', 1, 'PUBLISHED', ?)",
                ownTopic, roundId, UUID.randomUUID());
        UUID ownGroup = insertGroup(roundId, "gov-faculty-reader-leader", ownTopic, "APPROVED");
        insertMember(ownGroup, roundId, "gov-faculty-reader-leader", 1);

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(ownGroup)).isNotEmpty())
                .andExpect(jsonPath("$[*].members[*].contact")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));
    }

    @Test
    void personalProgressRequiresPublishedPersonalResult() throws Exception {
        ensureStudent("gov-progress-leader", "gov-progress-leader-user", "gov-progress-leader@campuscore.edu");
        ensureStudent("gov-progress-member", "gov-progress-member-user", "gov-progress-member@campuscore.edu");
        ensureStudent("gov-progress-member-3", "gov-progress-member-3-user", "gov-progress-member-3@campuscore.edu");
        UUID pendingRound = insertRound("Gov Progress Published Without Result", "RESULTS_PUBLISHED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        UUID pendingTopic = insertPublishedTopic(pendingRound);
        UUID pendingGroup = insertGroup(pendingRound, "gov-progress-leader", pendingTopic, "APPROVED");
        insertMember(pendingGroup, pendingRound, "gov-progress-leader", 1);
        insertMember(pendingGroup, pendingRound, "gov-progress-member", 2);
        insertMember(pendingGroup, pendingRound, "gov-progress-member-3", 3);
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
    void personalProgressKeepsAnUnfinishedLeaderOnlyRosterUsable() throws Exception {
        // A pending roster may be incomplete without being invalid; only
        // approved new groups must meet the three-member floor.
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
                .andExpect(jsonPath("$.attentionState").value("NONE"));
    }

    @Test
    void personalProgressAndGroupListPreferActiveMembershipOverNewerCancelledGroup() throws Exception {
        // A student who cancels a newer group and keeps an older active
        // membership in the same round must not be shadowed by the cancelled
        // row just because it was created later.
        ensureStudent("gov-progress-multi-leader", "gov-progress-multi-leader-user",
                "gov-progress-multi-leader@campuscore.edu");
        UUID roundId = insertRound("Gov Progress Multi Group Round", "REGISTRATION_OPEN",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        UUID topicId = insertPublishedTopic(roundId);

        UUID olderActiveGroup = insertGroup(roundId, "gov-progress-multi-leader", topicId, "PENDING");
        insertMember(olderActiveGroup, roundId, "gov-progress-multi-leader", 1);
        jdbc.update(
                "UPDATE thesis.thesis_group SET created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(7_200)), olderActiveGroup);

        // The unique active-membership index only counts rows whose
        // active_participation marker is TRUE; a cancelled membership keeps a
        // FALSE marker, so the member row is written directly to reflect the
        // historical roster left behind by cancellation.
        UUID newerCancelledGroup = insertGroup(roundId, "gov-progress-multi-leader", topicId, "PENDING");
        jdbc.update(
                "UPDATE thesis.thesis_group SET status = 'CANCELLED', created_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), newerCancelledGroup);
        jdbc.update(
                "INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader, active_participation) "
                        + "VALUES (?, ?, ?, ?, ?, ?, FALSE)",
                UUID.randomUUID(), newerCancelledGroup, roundId, "gov-progress-multi-leader", 1, true);

        mvc.perform(get("/api/v1/thesis/me/progress")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-progress-multi-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participationState").value("PARTICIPATING"))
                .andExpect(jsonPath("$.groupId").value(olderActiveGroup.toString()))
                .andExpect(jsonPath("$.groupStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.attentionState").value("NONE"));

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", roundId.toString())
                        .with(studentJwt("gov-progress-multi-leader")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(olderActiveGroup.toString()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[1].id").value(newerCancelledGroup.toString()))
                .andExpect(jsonPath("$[1].status").value("CANCELLED"));
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

    @Test
    void cancelledRoundsRefuseCouncilMutationsAndLeaveHistoryUntouched() throws Exception {
        UUID roundId = insertRound("Gov Terminal Council Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        for (String lecturer : new String[] {"gov-term-chair", "gov-term-secretary", "gov-term-member", "gov-term-candidate", "gov-term-outsider"}) {
            ensureLecturer(lecturer);
        }
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng terminal\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng terminal'", UUID.class);
        councilSeat(councilId, "gov-term-chair", "CHAIR");
        councilSeat(councilId, "gov-term-secretary", "SECRETARY");
        councilSeat(councilId, "gov-term-member", "MEMBER");
        UUID assignedTopic = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + assignedTopic + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID spareTopic = insertPublishedTopic(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?",
                String.class, roundId)).isEqualTo("CANCELLED");

        int councilAudits = auditCount(councilId.toString());
        int topicAudits = auditCount(assignedTopic.toString());
        long assignedVersion = jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_topic WHERE id = ?", Long.class, assignedTopic);

        for (var staff : new org.springframework.test.web.servlet.request.RequestPostProcessor[] {
                truongKhoaJwt(), adminJwt(), superAdminJwt()}) {
            mvc.perform(post("/api/v1/thesis/councils")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng terminal extra\"}")
                            .with(staff))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        }

        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-term-candidate\",\"memberRole\":\"MEMBER\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        mvc.perform(delete("/api/v1/thesis/councils/{id}/members/{lecturerId}", councilId, "gov-term-member")
                        .with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + spareTopic + "\"}")
                        .with(superAdminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, assignedTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-term-member")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, assignedTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":6}")
                        .with(lecturerJwt("gov-term-outsider")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_MEMBER_REQUIRED"));
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, assignedTopic)
                        .with(lecturerJwt("gov-term-member")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_ROLE_REQUIRED"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = ?",
                Integer.class, councilId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = ?",
                Integer.class, councilId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                Integer.class, assignedTopic)).isEqualTo(0);
        assertThat(jdbc.queryForObject(
                "SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                Object.class, assignedTopic)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_topic WHERE id = ?",
                Long.class, assignedTopic)).isEqualTo(assignedVersion);
        assertThat(auditCount(councilId.toString())).isEqualTo(councilAudits);
        assertThat(auditCount(assignedTopic.toString())).isEqualTo(topicAudits);

        mvc.perform(get("/api/v1/thesis/councils/{id}", councilId)
                        .with(lecturerJwt("gov-term-chair")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("gov-term-chair")));
        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, assignedTopic)
                        .with(lecturerJwt("gov-term-chair")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/thesis/councils").param("roundId", roundId.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(councilId.toString())));

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", assignedTopic)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-term-candidate\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
    }

    @Test
    void cancelledRoundsRefuseFinalizationEvenWithCompleteScores() throws Exception {
        UUID roundId = insertRound("Gov Terminal Finalize Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        for (String lecturer : new String[] {"gov-fin-chair", "gov-fin-secretary", "gov-fin-member"}) {
            ensureLecturer(lecturer);
        }
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng finalize\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng finalize'", UUID.class);
        councilSeat(councilId, "gov-fin-chair", "CHAIR");
        councilSeat(councilId, "gov-fin-secretary", "SECRETARY");
        councilSeat(councilId, "gov-fin-member", "MEMBER");
        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-fin");

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-fin-chair")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8}")
                        .with(lecturerJwt("gov-fin-secretary")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":9}")
                        .with(lecturerJwt("gov-fin-member")))
                .andExpect(status().isOk());
        int submittedAudits = auditRows(topicId, "COUNCIL_SCORE_SUBMITTED").size();

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());
        long versionBeforeFinalize = jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_topic WHERE id = ?", Long.class, topicId);

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize", councilId, topicId)
                        .with(lecturerJwt("gov-fin-chair")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        assertThat(jdbc.queryForObject(
                "SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                Object.class, topicId)).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_topic WHERE id = ?",
                Long.class, topicId)).isEqualTo(versionBeforeFinalize);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                Integer.class, topicId)).isEqualTo(3);
        assertThat(auditRows(topicId, "COUNCIL_SCORE_FINALIZED")).isEmpty();
        assertThat(auditRows(topicId, "COUNCIL_SCORE_SUBMITTED")).hasSize(submittedAudits);
    }

    @Test
    void closedRoundsRefuseCounterReviewAssignmentAndScoring() throws Exception {
        UUID roundId = insertRound("Gov Closed GVPB Round", "CLOSED",
                Instant.now().minusSeconds(7_200), Instant.now().minusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("gov-closed-reviewer");
        int topicAudits = auditCount(topicId.toString());

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-closed-reviewer\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = 'gov-closed-reviewer' WHERE id = ?", topicId);
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-closed-reviewer")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ? AND component = 'GVPB'",
                Integer.class, topicId)).isEqualTo(0);
        assertThat(auditRows(topicId, "GVPB_ASSIGNED")).isEmpty();
        assertThat(auditRows(topicId, "GVPB_SCORE_SUBMITTED")).isEmpty();
        assertThat(auditCount(topicId.toString())).isEqualTo(topicAudits);
    }

    @Test
    void defenseScoreBoundsRejectUnderflowAndPrecisionLossExactly() throws Exception {
        UUID roundId = insertRound("Gov Bounds Defense Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        for (String lecturer : new String[] {"gov-bd-chair", "gov-bd-secretary", "gov-bd-member"}) {
            ensureLecturer(lecturer);
        }
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng bounds\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = 'Hội đồng bounds'", UUID.class);
        councilSeat(councilId, "gov-bd-chair", "CHAIR");
        councilSeat(councilId, "gov-bd-secretary", "SECRETARY");
        councilSeat(councilId, "gov-bd-member", "MEMBER");
        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-bd");

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"-1e-1000\"}")
                        .with(lecturerJwt("gov-bd-chair")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"10.0000000000000000001\"}")
                        .with(lecturerJwt("gov-bd-chair")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                Integer.class, topicId)).isEqualTo(0);
        assertThat(auditRows(topicId, "COUNCIL_SCORE_SUBMITTED")).isEmpty();

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}")
                        .with(lecturerJwt("gov-bd-chair")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"8.5\"}")
                        .with(lecturerJwt("gov-bd-secretary")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":10}")
                        .with(lecturerJwt("gov-bd-member")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"0\"}")
                        .with(lecturerJwt("gov-bd-chair")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5}")
                        .with(lecturerJwt("gov-bd-secretary")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"10\"}")
                        .with(lecturerJwt("gov-bd-member")))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                Integer.class, topicId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = ? AND lecturer_id = 'gov-bd-chair'",
                BigDecimal.class, topicId)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject(
                "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = ? AND lecturer_id = 'gov-bd-secretary'",
                BigDecimal.class, topicId)).isEqualByComparingTo("8.5");
        assertThat(jdbc.queryForObject(
                "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = ? AND lecturer_id = 'gov-bd-member'",
                BigDecimal.class, topicId)).isEqualByComparingTo("10");
    }

    @Test
    void reviewerScoreBoundsRejectUnderflowAndPrecisionLossExactly() throws Exception {
        UUID roundId = insertRound("Gov Bounds GVPB Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("gov-bound-reviewer");
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-bound-reviewer\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-bound");

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"-1e-1000\"}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"10.0000000000000000001\"}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ? AND component = 'GVPB'",
                Integer.class, topicId)).isEqualTo(0);
        assertThat(auditRows(topicId, "GVPB_SCORE_SUBMITTED")).isEmpty();

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":0}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"8.5\"}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":10}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"0\"}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":8.5}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer-score", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":\"10\"}")
                        .with(lecturerJwt("gov-bound-reviewer")))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT score FROM thesis.thesis_topic_score WHERE topic_id = ? AND component = 'GVPB'",
                BigDecimal.class, topicId)).isEqualByComparingTo("10");
    }

    @Test
    void closedAndPublishedRoundsRefuseCouncilWrites() throws Exception {
        for (String terminalStatus : new String[] {"CLOSED", "RESULTS_PUBLISHED"}) {
            String tag = "gov-ts-" + terminalStatus.toLowerCase().replace('_', '-');
            UUID roundId = insertRound("Gov Terminal " + terminalStatus, "REGISTRATION_CLOSED",
                    Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
            ensureLecturer(tag + "-candidate");
            CouncilFixture fixture = councilFixture(roundId, tag);
            UUID spareTopic = insertPublishedTopic(roundId);
            jdbc.update("UPDATE thesis.thesis_registration_round SET status = ? WHERE id = ?",
                    terminalStatus, roundId);

            mvc.perform(post("/api/v1/thesis/councils")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng " + tag + "-extra\"}")
                            .with(truongKhoaJwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
            mvc.perform(post("/api/v1/thesis/councils/{id}/members", fixture.councilId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lecturerId\":\"" + tag + "-candidate\",\"memberRole\":\"MEMBER\"}")
                            .with(adminJwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
            mvc.perform(post("/api/v1/thesis/councils/{id}/topics", fixture.councilId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"topicId\":\"" + spareTopic + "\"}")
                            .with(truongKhoaJwt()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
            mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores",
                            fixture.councilId(), fixture.topicId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"score\":7}")
                            .with(lecturerJwt(tag + "-member")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
            mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/finalize",
                            fixture.councilId(), fixture.topicId())
                            .with(lecturerJwt(tag + "-chair")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_council_member WHERE council_id = ?",
                    Integer.class, fixture.councilId())).isEqualTo(3);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE council_id = ?",
                    Integer.class, fixture.councilId())).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                    Integer.class, fixture.topicId())).isEqualTo(0);
            assertThat(jdbc.queryForObject(
                    "SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                    Object.class, fixture.topicId())).isNull();
        }
    }

    @Test
    void councilCreationOnAnUnknownRoundIsNotFound() throws Exception {
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + UUID.randomUUID() + "\",\"name\":\"Hội đồng mồ côi\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUND_NOT_FOUND"));
    }

    @Test
    void aPendingScoreBlocksBehindTheRoundLockUntilCancellationCommits() throws Exception {
        UUID roundId = insertRound("Gov Blocking Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        CouncilFixture fixture = councilFixture(roundId, "gov-blk");
        Jwt memberActor = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("gov-lecturer-user-gov-blk-member")
                .claim("lecturerId", "gov-blk-member")
                .claim("roles", List.of("LECTURER"))
                .build();

        assertRoundLockBlocksCancelWriter(roundId, fixture.topicId(), () -> {
            councils.submitScore(fixture.councilId(), fixture.topicId(), "DEFENSE",
                    BigDecimal.valueOf(7), memberActor);
            return null;
        });
    }

    @Test
    void aPendingReviewerScoreBlocksBehindTheRoundLockUntilCancellationCommits() throws Exception {
        UUID roundId = insertRound("Gov Blocking GVPB Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), roundId);
        UUID topicId = insertPublishedTopic(roundId);
        ensureLecturer("gov-blk-reviewer");
        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"gov-blk-reviewer\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, "gov-blk");
        Jwt reviewerActor = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("gov-lecturer-user-gov-blk-reviewer")
                .claim("lecturerId", "gov-blk-reviewer")
                .claim("roles", List.of("LECTURER"))
                .build();

        assertRoundLockBlocksCancelWriter(roundId, topicId, () -> {
            councils.submitReviewerScore(topicId, BigDecimal.valueOf(7), null, reviewerActor);
            return null;
        });
    }

    private void assertRoundLockBlocksCancelWriter(UUID roundId, UUID topicId,
            Callable<Void> writerCall) throws Exception {
        CountDownLatch roundLocked = new CountDownLatch(1);
        CountDownLatch roundQueryEntered = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch writerDone = new CountDownLatch(1);
        CountDownLatch commitCancel = new CountDownLatch(1);
        int topicAudits = auditCount(topicId.toString());

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                roundQueryEntered.countDown();
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> canceller = executor.submit(() -> {
                new TransactionTemplate(txManager).execute(status -> {
                    jdbc.update(
                            "UPDATE thesis.thesis_registration_round SET status = 'CANCELLED', version = version + 1 WHERE id = ?",
                            roundId);
                    roundLocked.countDown();
                    try {
                        commitCancel.await(15, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            });
            assertThat(roundLocked.await(15, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> writer = executor.submit(() -> {
                writerStarted.countDown();
                try {
                    writerCall.call();
                    return null;
                } catch (Throwable error) {
                    return error;
                } finally {
                    writerDone.countDown();
                }
            });

            assertThat(writerStarted.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(roundQueryEntered.await(15, TimeUnit.SECONDS))
                    .as("the writer must reach the round row-lock query")
                    .isTrue();
            assertThat(writerDone.await(500, TimeUnit.MILLISECONDS))
                    .as("the writer must still be waiting on the uncommitted round lock")
                    .isFalse();
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                    Integer.class, topicId)).isEqualTo(0);

            commitCancel.countDown();
            canceller.get(15, TimeUnit.SECONDS);
            Throwable error = writer.get(15, TimeUnit.SECONDS);
            assertThat(error).isInstanceOf(DomainException.class);
            assertThat(((DomainException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(((DomainException) error).getCode()).isEqualTo("ROUND_STATE_CONFLICT");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                    Integer.class, topicId)).isEqualTo(0);
            assertThat(auditCount(topicId.toString())).isEqualTo(topicAudits);
        } finally {
            commitCancel.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(15, TimeUnit.SECONDS))
                    .as("the cancellation and writer transactions must not be left dangling")
                    .isTrue();
        }
    }

    @Test
    void aScoreCommittedBeforeCancellationRemainsHistorical() throws Exception {
        UUID roundId = insertRound("Gov Blocking Control Round", "REGISTRATION_CLOSED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
        CouncilFixture fixture = councilFixture(roundId, "gov-ctl");

        mvc.perform(post("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores",
                        fixture.councilId(), fixture.topicId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":7}")
                        .with(lecturerJwt("gov-ctl-member")))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/thesis/rounds/{id}/cancel", roundId).with(truongKhoaJwt()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/thesis/councils/{councilId}/topics/{topicId}/scores",
                        fixture.councilId(), fixture.topicId())
                        .with(lecturerJwt("gov-ctl-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].lecturerId").value("gov-ctl-member"))
                .andExpect(jsonPath("$[0].score").value(7));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?",
                Integer.class, fixture.topicId())).isEqualTo(1);
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
                        + "VALUES (?, ?, 'department-demo', ?, ?, ?, 'PUBLISHED', ?)",
                topicId, roundId, "Gov Results Topic", "Results fixture topic", 1, UUID.randomUUID());
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
        councilSeat(councilId, lecturerId, role, truongKhoaJwt());
    }

    private void councilSeat(UUID councilId, String lecturerId, String role,
            org.springframework.test.web.servlet.request.RequestPostProcessor actor) throws Exception {
        mvc.perform(post("/api/v1/thesis/councils/{id}/members", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"" + lecturerId + "\",\"memberRole\":\"" + role + "\"}")
                        .with(actor))
                .andExpect(status().isOk());
    }

    private record CouncilFixture(UUID councilId, UUID topicId) { }

    private CouncilFixture councilFixture(UUID roundId, String tag) throws Exception {
        ensureLecturer(tag + "-chair");
        ensureLecturer(tag + "-secretary");
        ensureLecturer(tag + "-member");
        mvc.perform(post("/api/v1/thesis/councils")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"name\":\"Hội đồng " + tag + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        UUID councilId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_council WHERE name = ?", UUID.class, "Hội đồng " + tag);
        councilSeat(councilId, tag + "-chair", "CHAIR");
        councilSeat(councilId, tag + "-secretary", "SECRETARY");
        councilSeat(councilId, tag + "-member", "MEMBER");
        UUID topicId = insertPublishedTopic(roundId);
        mvc.perform(post("/api/v1/thesis/councils/{id}/topics", councilId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + topicId + "\"}")
                        .with(truongKhoaJwt()))
                .andExpect(status().isOk());
        insertApprovedGroupWithRoster(roundId, topicId, tag);
        return new CouncilFixture(councilId, topicId);
    }

    /**
     * Defense and counter-review scores only exist once a group has been
     * approved onto the topic, so every scoring fixture needs a defended
     * roster: an APPROVED group with the contract's minimum three members.
     */
    private UUID insertApprovedGroupWithRoster(UUID roundId, UUID topicId, String tag) {
        String leader = tag + "-leader";
        UUID groupId = insertGroup(roundId, leader, topicId, "APPROVED");
        insertMember(groupId, roundId, leader, 1);
        insertMember(groupId, roundId, tag + "-member-2", 2);
        insertMember(groupId, roundId, tag + "-member-3", 3);
        return groupId;
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
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor lecturerJwt(String lecturerId) {
        return ThesisAuthorityFixture.lecturer(jdbc, lecturerId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor truongKhoaJwt() {
        // A dean token without a bound, active lecturer profile fails closed
        // on topic-level scope checks; give the fixture a live profile in
        // department-demo so its faculty contains the fixture topics.
        Integer bound = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'gov-truongkhoa'",
                Integer.class);
        if (bound == null || bound == 0) {
            ensureLecturer("gov-truongkhoa");
        }
        String userId = jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = 'gov-truongkhoa'",
                String.class);
        return ThesisAuthorityFixture.role(jdbc, userId, "TRUONG_KHOA");
    }

    /** Group governance requires a live faculty profile before testing round-state guards. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor scopedTruongKhoaJwt(String lecturerId) {
        ensureLecturer(lecturerId);
        String userId = jdbc.queryForObject(
                "SELECT \"userId\" FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?", String.class, lecturerId);
        return ThesisAuthorityFixture.role(jdbc, userId, "TRUONG_KHOA");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "admin-user", "ADMIN");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor superAdminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "super-admin-user", "SUPER_ADMIN");
    }

    private int auditCount(String entityId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                Integer.class, entityId);
        return count == null ? 0 : count;
    }

    /** Audit rows of one action for one topic, newest or oldest order irrelevant. */
    private List<Map<String, Object>> auditRows(UUID topicId, String action) {
        return jdbc.queryForList(
                "SELECT \"actorId\", \"entityType\", \"entityId\", \"beforeState\", \"afterState\" "
                        + "FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = ?",
                topicId.toString(), action);
    }

    /** The audit row whose after-state carries the given fragment. */
    private Map<String, Object> auditRow(UUID topicId, String action, String afterStateFragment) {
        return auditRows(topicId, action).stream()
                .filter(row -> String.valueOf(row.get("afterState")).contains(afterStateFragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No " + action + " audit row with afterState containing " + afterStateFragment));
    }

    @Test
    void terminalRoundsFreezeTopicUpdates() throws Exception {
        for (String terminal : new String[] {"CANCELLED", "RESULTS_PUBLISHED", "CLOSED"}) {
            String tag = "gov-tput-" + terminal.toLowerCase();
            ensureLecturer(tag);
            UUID roundId = insertRound("Gov Terminal PUT " + terminal, "PROPOSAL_OPEN",
                    Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
            String title = "Terminal PUT Topic " + terminal;
            mvc.perform(post("/api/v1/thesis/topics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(topicBody(roundId, title))
                            .with(lecturerJwt(tag)))
                    .andExpect(status().isOk());
            UUID topicId = jdbc.queryForObject(
                    "SELECT id FROM thesis.thesis_topic WHERE title = ?", UUID.class, title);
            long version = jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_topic WHERE id = ?", Long.class, topicId);
            int audits = auditCount(topicId.toString());
            jdbc.update(
                    "UPDATE thesis.thesis_registration_round SET status = ? WHERE id = ?",
                    terminal, roundId);

            mvc.perform(put("/api/v1/thesis/topics/{id}", topicId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"departmentId\":\"department-demo\",\"title\":\"Terminal changed\","
                                    + "\"description\":\"Synthetic\",\"maxGroups\":2}")
                            .with(lecturerJwt(tag)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

            assertThat(jdbc.queryForObject(
                    "SELECT title FROM thesis.thesis_topic WHERE id = ?",
                    String.class, topicId)).isEqualTo(title);
            assertThat(jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_topic WHERE id = ?",
                    Long.class, topicId)).isEqualTo(version);
            assertThat(auditCount(topicId.toString())).isEqualTo(audits);
        }
    }

    @Test
    void terminalRoundsFreezeTopicArchive() throws Exception {
        for (String terminal : new String[] {"CANCELLED", "RESULTS_PUBLISHED", "CLOSED"}) {
            String tag = "gov-tarc-" + terminal.toLowerCase();
            ensureLecturer(tag);
            UUID roundId = insertRound("Gov Terminal Archive " + terminal, "PROPOSAL_OPEN",
                    Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
            String title = "Terminal Archive Topic " + terminal;
            mvc.perform(post("/api/v1/thesis/topics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(topicBody(roundId, title))
                            .with(lecturerJwt(tag)))
                    .andExpect(status().isOk());
            UUID topicId = jdbc.queryForObject(
                    "SELECT id FROM thesis.thesis_topic WHERE title = ?", UUID.class, title);
            long version = jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_topic WHERE id = ?", Long.class, topicId);
            int audits = auditCount(topicId.toString());
            jdbc.update(
                    "UPDATE thesis.thesis_registration_round SET status = ? WHERE id = ?",
                    terminal, roundId);

            mvc.perform(post("/api/v1/thesis/topics/{id}/archive", topicId)
                            .with(lecturerJwt(tag)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM thesis.thesis_topic WHERE id = ?",
                    String.class, topicId)).isEqualTo("DRAFT");
            assertThat(jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_topic WHERE id = ?",
                    Long.class, topicId)).isEqualTo(version);
            assertThat(auditCount(topicId.toString())).isEqualTo(audits);
        }
    }

    @Test
    void terminalRoundsFreezeGroupProgress() throws Exception {
        for (String terminal : new String[] {"CANCELLED", "RESULTS_PUBLISHED", "CLOSED"}) {
            String tag = "gov-tprog-" + terminal.toLowerCase();
            ensureLecturer(tag + "-lecturer");
            ensureStudent(tag + "-1", tag + "-user-1", tag + "-1@campuscore.edu");
            ensureStudent(tag + "-2", tag + "-user-2", tag + "-2@campuscore.edu");
            ensureStudent(tag + "-3", tag + "-user-3", tag + "-3@campuscore.edu");
            UUID roundId = insertRound("Gov Terminal Progress " + terminal, "PROPOSAL_OPEN",
                    Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
            String title = "Terminal Progress Topic " + terminal;
            mvc.perform(post("/api/v1/thesis/topics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(topicBody(roundId, title))
                            .with(lecturerJwt(tag + "-lecturer")))
                    .andExpect(status().isOk());
            UUID topicId = jdbc.queryForObject(
                    "SELECT id FROM thesis.thesis_topic WHERE title = ?", UUID.class, title);
            mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId).with(truongKhoaJwt()))
                    .andExpect(status().isOk());
            UUID groupId = insertGroup(roundId, tag + "-1", topicId, "PENDING");
            insertMember(groupId, roundId, tag + "-1", 1);
            insertMember(groupId, roundId, tag + "-2", 2);
            insertMember(groupId, roundId, tag + "-3", 3);
            long version = jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_group WHERE id = ?", Long.class, groupId);
            int audits = auditCount(groupId.toString());
            jdbc.update(
                    "UPDATE thesis.thesis_registration_round SET status = ? WHERE id = ?",
                    terminal, roundId);

            mvc.perform(patch("/api/v1/thesis/groups/{id}/progress", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"DRAFT\"}")
                            .with(scopedTruongKhoaJwt(tag + "-dean")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

            assertThat(jdbc.queryForObject(
                    "SELECT status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("SUBMITTED");
            assertThat(jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_group WHERE id = ?",
                    Long.class, groupId)).isEqualTo(version);
            assertThat(auditCount(groupId.toString())).isEqualTo(audits);
        }
    }

    @Test
    void terminalRoundsFreezeGroupRejection() throws Exception {
        for (String terminal : new String[] {"CANCELLED", "RESULTS_PUBLISHED", "CLOSED"}) {
            String tag = "gov-trej-" + terminal.toLowerCase();
            ensureLecturer(tag + "-lecturer");
            ensureStudent(tag + "-1", tag + "-user-1", tag + "-1@campuscore.edu");
            ensureStudent(tag + "-2", tag + "-user-2", tag + "-2@campuscore.edu");
            ensureStudent(tag + "-3", tag + "-user-3", tag + "-3@campuscore.edu");
            UUID roundId = insertRound("Gov Terminal Reject " + terminal, "PROPOSAL_OPEN",
                    Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(3_600));
            String title = "Terminal Reject Topic " + terminal;
            mvc.perform(post("/api/v1/thesis/topics")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(topicBody(roundId, title))
                            .with(lecturerJwt(tag + "-lecturer")))
                    .andExpect(status().isOk());
            UUID topicId = jdbc.queryForObject(
                    "SELECT id FROM thesis.thesis_topic WHERE title = ?", UUID.class, title);
            mvc.perform(post("/api/v1/thesis/topics/{id}/publish", topicId).with(truongKhoaJwt()))
                    .andExpect(status().isOk());
            UUID groupId = insertGroup(roundId, tag + "-1", topicId, "PENDING");
            insertMember(groupId, roundId, tag + "-1", 1);
            insertMember(groupId, roundId, tag + "-2", 2);
            insertMember(groupId, roundId, tag + "-3", 3);
            long version = jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_group WHERE id = ?", Long.class, groupId);
            int audits = auditCount(groupId.toString());
            jdbc.update(
                    "UPDATE thesis.thesis_registration_round SET status = ? WHERE id = ?",
                    terminal, roundId);

            mvc.perform(post("/api/v1/thesis/groups/{id}/reject", groupId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Synthetic rejection\"}")
                            .with(scopedTruongKhoaJwt(tag + "-dean")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));

            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject(
                    "SELECT version FROM thesis.thesis_group WHERE id = ?",
                    Long.class, groupId)).isEqualTo(version);
            assertThat(auditCount(groupId.toString())).isEqualTo(audits);
        }
    }
}
