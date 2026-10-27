package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Score transparency read model (SCORE1): GET
 * /api/v1/thesis/topics/{id}/score-breakdown answers who graded a topic,
 * under which role, with what score and comment, the exact finalize average,
 * and which eligible graders are still missing.
 *
 * <p>Every visibility cell of the Team Lead matrix is exercised through
 * MockMvc so the role gate, the concealment rules, and the student redaction
 * are pinned end to end: staff and council full view, supervisor gated by
 * publication, participant students redacted after publication, everyone else
 * refused, and hidden topics concealed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_score_breakdown;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisScoreBreakdownTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ThesisCouncilService councils;
    private final ObjectMapper mapper = tools.jackson.databind.json.JsonMapper.builder().build();

    @BeforeEach
    void cleanDatabase() {
        // The inbox lives outside the H2 thesis schema; finalize fans out a
        // score-finalized notification after commit, so the test needs the
        // table (same stub ThesisCouncilEscapeHatchTest uses).
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS notifications");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS notifications.notification (
                    id VARCHAR(120) PRIMARY KEY,
                    user_id VARCHAR(120) NOT NULL,
                    title VARCHAR(240) NOT NULL,
                    message VARCHAR(2000) NOT NULL,
                    type VARCHAR(40) NOT NULL,
                    link VARCHAR(500),
                    announcement_id VARCHAR(120),
                    source_key VARCHAR(160),
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
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'sb-%' OR \"actorId\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"UserRole\" WHERE \"userId\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'sb-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'sb-%'");
    }

    // ---------- staff full view ----------

    @Test
    void adminReadsTheFullBreakdown() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.councilId").value(f.councilId.toString()))
                .andExpect(jsonPath("$.councilName").value("Sb Council"))
                .andExpect(jsonPath("$.graderCount").value(4))
                .andExpect(jsonPath("$.complete").value(false))
                .andReturn();
        Map<String, Object> body = body(result);
        List<Map<String, Object>> graders = gradersOf(body);
        // Seat order: chair, secretary, member, then the counter-review row.
        assertThat(graders).extracting(g -> g.get("role"))
                .containsExactly("CHAIR", "SECRETARY", "MEMBER", "GVPB_REVIEWER");
        assertThat(graders).extracting(g -> g.get("displayName"))
                .containsExactly("Sb Chair", "Sb Secretary", "Sb Member", "Sb Reviewer");
        // Full view carries scores and comments verbatim.
        assertThat(graders.get(0).get("score")).isEqualTo(7.0);
        assertThat(graders.get(0).get("comment")).isEqualTo("chair note");
        assertThat(graders.get(0).get("scoredAt")).isNotNull();
        assertThat(graders.get(3).get("score")).isEqualTo(6.0);
        // The missing fourth member is named for the chair.
        List<Map<String, Object>> pending = pendingOf(body);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).get("displayName")).isEqualTo("Sb Late");
        assertThat(pending.get(0).get("role")).isEqualTo("MEMBER");
        // The average counts only the three submitted eligible defense scores.
        assertThat(new BigDecimal(body.get("average").toString()))
                .isEqualByComparingTo(new BigDecimal("8.17"));
    }

    @Test
    void superAdminReadsTheFullBreakdown() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-super-admin", "SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graderCount").value(4));
    }

    @Test
    void deanOfTheTopicsFacultyReadsTheFullBreakdown() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        String deanUser = ensureLecturer("sb-dean-own", "department-demo");
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graders[0].score").value(7.0))
                .andExpect(jsonPath("$.graders[0].comment").value("chair note"));
    }

    @Test
    void deanOfAForeignFacultyIsDenied() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        seedForeignDepartment();
        String deanUser = ensureLecturer("sb-dean-foreign", "sb-dept-foreign");
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, deanUser, "TRUONG_KHOA")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_SCORE_ACCESS_REQUIRED"));
    }

    @Test
    void departmentHeadOfTheTopicsDepartmentReadsFull() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        String headUser = ensureLecturer("sb-head-own", "department-demo");
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, headUser, "TRUONG_BO_MON")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graderCount").value(4));
    }

    @Test
    void otherDepartmentHeadIsDenied() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        seedForeignDepartment();
        String headUser = ensureLecturer("sb-head-foreign", "sb-dept-foreign");
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, headUser, "TRUONG_BO_MON")))
                .andExpect(status().isForbidden());
    }

    @Test
    void everyCouncilSeatReadsTheFullBreakdown() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        for (String member : List.of("sb-chair", "sb-secretary", "sb-member")) {
            mvc.perform(breakdown(f.topicId)
                            .with(ThesisAuthorityFixture.lecturer(jdbc, member)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.graders[0].comment").value("chair note"))
                    .andExpect(jsonPath("$.pendingGraders[0].displayName").value("Sb Late"));
        }
    }

    @Test
    void theAssignedReviewerReadsTheFullBreakdown() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-reviewer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graders[3].role").value("GVPB_REVIEWER"))
                .andExpect(jsonPath("$.graders[3].score").value(6.0));
    }

    // ---------- supervisor gated by publication ----------

    @Test
    void supervisorWaitsForPublicationThenReadsFull() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        insertSupervisor(f.topicId, "sb-supervisor");
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-supervisor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));

        publishRound(f.roundId);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-supervisor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graders[0].comment").value("chair note"));
    }

    // ---------- participant student redaction ----------

    @Test
    void participantStudentSeesNamesAndRolesButNeverAScoreOrComment() throws Exception {
        Fixture f = scenario("RESULTS_PUBLISHED", true, true);
        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.student(jdbc, "sb-student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graderCount").value(4))
                .andExpect(jsonPath("$.average").value(8.17))
                .andExpect(jsonPath("$.finalScore").value(8.17))
                .andReturn();
        Map<String, Object> body = body(result);
        List<Map<String, Object>> graders = gradersOf(body);
        assertThat(graders).extracting(g -> g.get("role"))
                .containsExactly("CHAIR", "SECRETARY", "MEMBER", "GVPB_REVIEWER");
        assertThat(graders).extracting(g -> g.get("displayName"))
                .containsExactly("Sb Chair", "Sb Secretary", "Sb Member", "Sb Reviewer");
        for (Map<String, Object> grader : graders) {
            assertThat(grader.get("score")).as("score leaked to student").isNull();
            assertThat(grader.get("comment")).as("comment leaked to student").isNull();
            assertThat(grader.get("scoredAt")).as("scoredAt leaked to student").isNull();
            assertThat(grader.get("countedInAverage")).isNull();
        }
        assertThat(pendingOf(body)).isEmpty();
        assertThat(body.get("finalizedBy")).as("finalizer leaked to student").isNull();
        assertThat(body.get("finalizedAt")).isNull();
    }

    @Test
    void participantStudentBeforePublicationGetsResultsNotPublished() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.student(jdbc, "sb-student-1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULTS_NOT_PUBLISHED"));
    }

    @Test
    void aStudentOutsideTheGroupIsDenied() throws Exception {
        Fixture f = scenario("RESULTS_PUBLISHED", true, true);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.student(jdbc, "sb-student-outsider")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_SCORE_ACCESS_REQUIRED"));
    }

    // ---------- everyone else ----------

    @Test
    void anUnrelatedLecturerIsDenied() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-bystander")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COUNCIL_SCORE_ACCESS_REQUIRED"));
    }

    @Test
    void anAnonymousCallerIsRejected() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", true, false);
        mvc.perform(breakdown(f.topicId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownTopicAnswersNotFoundForEveryRole() throws Exception {
        UUID missing = UUID.randomUUID();
        mvc.perform(breakdown(missing)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-404", "ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
        mvc.perform(breakdown(missing)
                        .with(ThesisAuthorityFixture.student(jdbc, "sb-student-404")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aHiddenTopicStaysConcealed() throws Exception {
        UUID roundId = insertRound("Sb Hidden Round", "DRAFT");
        UUID topicId = insertTopic(roundId, "department-demo", "Sb Hidden Topic", "DRAFT");
        // The topic exists but is a draft: unrelated callers get the topic-read
        // 404, not a visibility hint.
        mvc.perform(breakdown(topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-bystander-hidden")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
        mvc.perform(breakdown(topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-hidden", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graders").isEmpty())
                .andExpect(jsonPath("$.complete").value(false));
    }

    // ---------- arithmetic parity with finalize ----------

    @Test
    void theBreakdownAverageEqualsTheFinalizedAverage() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", false, false);
        submitDefense(f, "sb-chair", 7, null);
        submitDefense(f, "sb-secretary", 8.5, null);
        submitDefense(f, "sb-member", 9, null);
        submitDefense(f, "sb-late", 9, null);
        councils.submitReviewerScore(f.topicId, BigDecimal.valueOf(7), "reviewer note",
                lecturer("sb-reviewer"));
        councils.finalizeScores(f.councilId, f.topicId, lecturer("sb-chair"));

        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-final", "ADMIN")))
                .andExpect(status().isOk())
                // 33.5 / 4 = 8.375 rounds HALF_UP to 8.38 — the exact value the
                // finalize wrote to final_score.
                .andExpect(jsonPath("$.finalScore").value(8.38))
                .andExpect(jsonPath("$.average").value(8.38))
                .andExpect(jsonPath("$.complete").value(true))
                .andExpect(jsonPath("$.finalizedBy").value("sb-user-sb-chair"))
                .andExpect(jsonPath("$.resultStatus").value("GRADED"))
                .andExpect(jsonPath("$.pendingGraders").isEmpty())
                .andReturn();
        assertThat(body(result).get("finalizedAt")).isNotNull();
    }

    @Test
    void aSupervisorMembersScoreIsListedButExcludedFromTheAverage() throws Exception {
        UUID roundId = insertRound("Sb Exclusion Round", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "department-demo", "Sb Exclusion Topic", "PUBLISHED");
        UUID councilId = insertCouncil(roundId);
        seat(councilId, "sb-chair", "CHAIR");
        seat(councilId, "sb-secretary", "SECRETARY");
        seat(councilId, "sb-member", "MEMBER");
        seat(councilId, "sb-sup-member", "MEMBER");
        assignCouncilTopic(councilId, topicId);
        insertApprovedGroup(roundId, topicId, "sb-ex");
        insertSupervisor(topicId, "sb-sup-member");
        submitDefense2(councilId, topicId, "sb-chair", 6, null);
        submitDefense2(councilId, topicId, "sb-secretary", 6, null);
        submitDefense2(councilId, topicId, "sb-member", 6, null);
        // The supervisor never submits through the API (SUPERVISOR_CANNOT_GRADE);
        // a stale row can only arrive directly.
        jdbc.update("INSERT INTO thesis.thesis_topic_score "
                        + "(id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (?, ?, ?, 'sb-sup-member', 'DEFENSE', 10.0, 'stale row')",
                UUID.randomUUID(), topicId, councilId);
        submitReviewerRow(topicId, "sb-reviewer-ex", 6, null);
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = 'sb-reviewer-ex' WHERE id = ?",
                topicId);

        MvcResult result = mvc.perform(breakdown(topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-ex", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graderCount").value(5))
                .andExpect(jsonPath("$.average").value(6.00))
                .andReturn();
        List<Map<String, Object>> graders = gradersOf(body(result));
        Map<String, Object> stale = graders.stream()
                .filter(g -> "sb-sup-member".equals(g.get("lecturerId")))
                .findFirst().orElseThrow();
        assertThat(stale.get("countedInAverage")).isEqualTo(Boolean.FALSE);
        // The supervisor seat is not an expected grader, so it is not pending
        // either, and the eligible roster is fully scored.
        assertThat(body(result).get("complete")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void anInactiveMembersScoreIsListedButExcluded() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", false, false);
        submitDefense(f, "sb-chair", 8, null);
        submitDefense(f, "sb-secretary", 8, null);
        submitDefense(f, "sb-member", 8, null);
        submitDefense(f, "sb-late", 10, null);
        submitReviewerRow(f.topicId, "sb-reviewer", 6, null);
        // A member who can no longer grade leaves the eligible set; their
        // stored score stays visible but out of the average.
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\" = FALSE WHERE \"id\" = 'sb-late'");

        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-inactive", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average").value(8.00))
                .andReturn();
        Map<String, Object> body = body(result);
        List<Map<String, Object>> graders = gradersOf(body);
        assertThat(graders.stream()
                .filter(g -> "sb-late".equals(g.get("lecturerId")))
                .findFirst().orElseThrow().get("countedInAverage"))
                .isEqualTo(Boolean.FALSE);
        // The ineligible member is not pending, and the expected trio scored.
        assertThat(pendingOf(body)).isEmpty();
        assertThat(body.get("complete")).isEqualTo(Boolean.TRUE);
    }

    @Test
    void missingGradersAreNamedBeforeFinalize() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", false, false);
        submitDefense(f, "sb-chair", 9, null);
        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "sb-chair")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andReturn();
        List<Map<String, Object>> pending = pendingOf(body(result));
        assertThat(pending).extracting(g -> g.get("displayName"))
                .containsExactlyInAnyOrder("Sb Secretary", "Sb Late", "Sb Member", "Sb Reviewer");
        // Seat order: secretary first, the two member seats after, the
        // counter-reviewer last.
        assertThat(pending.get(0).get("role")).isEqualTo("SECRETARY");
        assertThat(pending.subList(1, 3)).extracting(g -> g.get("role"))
                .containsOnly("MEMBER");
        assertThat(pending.get(3).get("role")).isEqualTo("GVPB_REVIEWER");
    }

    @Test
    void anUngradedTopicListsEveryExpectedGraderAsPending() throws Exception {
        Fixture f = scenario("REGISTRATION_CLOSED", false, false);
        MvcResult result = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-empty", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graderCount").value(0))
                .andExpect(jsonPath("$.complete").value(false))
                .andReturn();
        Map<String, Object> body = body(result);
        assertThat(body.get("average")).isNull();
        assertThat(gradersOf(body)).isEmpty();
        // Four eligible members plus the assigned counter-reviewer.
        assertThat(pendingOf(body)).hasSize(5);
    }

    @Test
    void aWithdrawnTopicStillReadsItsStoredOutcome() throws Exception {
        Fixture f = scenario("RESULTS_PUBLISHED", true, true);
        jdbc.update("UPDATE thesis.thesis_topic SET result_status = 'WITHDRAWN', "
                + "result_reason = 'governance withdrawal' WHERE id = ?", f.topicId);
        mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.role(jdbc, "sb-admin-wd", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.graderCount").value(4));
        MvcResult student = mvc.perform(breakdown(f.topicId)
                        .with(ThesisAuthorityFixture.student(jdbc, "sb-student-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("WITHDRAWN"))
                .andReturn();
        assertThat(gradersOf(body(student)).get(0).get("score")).isNull();
    }

    // ---------- fixtures ----------

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder breakdown(UUID topicId) {
        return get("/api/v1/thesis/topics/{id}/score-breakdown", topicId);
    }

    private record Fixture(UUID roundId, UUID topicId, UUID councilId) { }

    /**
     * A KLTN round with a future counter-review deadline, a published topic
     * inside the demo department, a four-member council, an approved group of
     * three students, and an assigned counter-reviewer. When {@code scored}
     * is set the first three members have graded (7 with "chair note", 8.5, 9)
     * and the reviewer has filed a 6; {@code finalized} additionally stores
     * the finalized outcome rows a real finalize would write. Score rows go in
     * directly so a published round can hold them.
     */
    private Fixture scenario(String roundStatus, boolean scored, boolean finalized) {
        UUID roundId = insertRound("Sb Round " + roundStatus, roundStatus);
        UUID topicId = insertTopic(roundId, "department-demo", "Sb Topic", "PUBLISHED");
        UUID councilId = insertCouncil(roundId);
        seat(councilId, "sb-chair", "CHAIR");
        seat(councilId, "sb-secretary", "SECRETARY");
        seat(councilId, "sb-member", "MEMBER");
        seat(councilId, "sb-late", "MEMBER");
        assignCouncilTopic(councilId, topicId);
        insertApprovedGroup(roundId, topicId, "sb");
        assignReviewerRow(topicId, "sb-reviewer");
        if (scored) {
            scoreRow(councilId, topicId, "sb-chair", "DEFENSE", 7, "chair note");
            scoreRow(councilId, topicId, "sb-secretary", "DEFENSE", 8.5, null);
            scoreRow(councilId, topicId, "sb-member", "DEFENSE", 9, null);
            submitReviewerRow(topicId, "sb-reviewer", 6, "reviewer note");
        }
        if (finalized) {
            jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.17, "
                            + "final_score_finalized_by = 'sb-user-sb-chair', "
                            + "final_score_finalized_at = CURRENT_TIMESTAMP, result_status = 'GRADED' "
                            + "WHERE id = ?", topicId);
        }
        return new Fixture(roundId, topicId, councilId);
    }

    private void publishRound(UUID roundId) {
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED' WHERE id = ?",
                roundId);
    }

    private void scoreRow(UUID councilId, UUID topicId, String lecturerId,
            String component, double score, String comment) {
        jdbc.update("INSERT INTO thesis.thesis_topic_score "
                        + "(id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), topicId, councilId, lecturerId, component,
                BigDecimal.valueOf(score), comment);
    }

    private void submitReviewerRow(UUID topicId, String lecturerId, double score, String comment) {
        ensureLecturer(lecturerId, "department-demo");
        jdbc.update("INSERT INTO thesis.thesis_topic_score "
                        + "(id, topic_id, council_id, lecturer_id, component, score, comment) "
                        + "VALUES (?, ?, NULL, ?, 'GVPB', ?, ?)",
                UUID.randomUUID(), topicId, lecturerId, BigDecimal.valueOf(score), comment);
    }

    private void submitDefense2(UUID councilId, UUID topicId, String lecturerId, double score, String comment) {
        councils.submitScore(councilId, topicId, "DEFENSE", BigDecimal.valueOf(score), comment,
                lecturer(lecturerId));
    }

    private void submitDefense(Fixture f, String lecturerId, double score, String comment) {
        submitDefense2(f.councilId, f.topicId, lecturerId, score, comment);
    }

    private Jwt lecturer(String lecturerId) {
        return Jwt.withTokenValue("breakdown-fixture").header("alg", "none")
                .subject("sb-user-" + lecturerId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private void seedForeignDepartment() {
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\", \"name\", \"isActive\") "
                + "VALUES ('sb-faculty-foreign', 'Foreign Faculty', TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\", \"name\", \"code\", \"facultyId\", \"isActive\") "
                + "VALUES ('sb-dept-foreign', 'Foreign Department', 'FRG', 'sb-faculty-foreign', TRUE)");
    }

    private UUID insertRound(String name, String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?)",
                id, name,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                Timestamp.from(now.plusSeconds(86_400)), status);
        return id;
    }

    private UUID insertTopic(UUID roundId, String departmentId, String title, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, ?, ?, 'Breakdown fixture topic', 2, ?, 'sb-admin')",
                id, roundId, departmentId, title, status);
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

    private UUID insertCouncil(UUID roundId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, 'Sb Council', 'ACTIVE', 'sb-admin')",
                id, roundId);
        return id;
    }

    private void seat(UUID councilId, String lecturerId, String role) {
        ensureLecturer(lecturerId, "department-demo");
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) "
                        + "VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, role);
    }

    private void assignCouncilTopic(UUID councilId, UUID topicId) {
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) "
                        + "VALUES (?, ?, ?, 'sb-admin')",
                UUID.randomUUID(), councilId, topicId);
    }

    private void assignReviewerRow(UUID topicId, String lecturerId) {
        ensureLecturer(lecturerId, "department-demo");
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?", lecturerId, topicId);
    }

    private void insertSupervisor(UUID topicId, String lecturerId) {
        ensureLecturer(lecturerId, "department-demo");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, lecturerId);
    }

    /** Lecturer profile in the given department plus its bound user id. */
    private String ensureLecturer(String lecturerId, String departmentId) {
        String userId = "sb-user-" + lecturerId;
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (users == null || users == 0) {
            String display = lecturerId.substring(3);
            String first = display.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + display.substring(1);
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                            + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                            + "VALUES (?, ?, 'test-password', ?, 'Sb', 'ACTIVE', FALSE, FALSE, 0, "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    userId, lecturerId + "@fixture.test", first);
        }
        Integer lecturers = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Lecturer\" WHERE \"id\" = ?", Integer.class, lecturerId);
        if (lecturers == null || lecturers == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\", \"userId\", \"departmentId\", \"employeeId\", \"isActive\") "
                            + "VALUES (?, ?, ?, ?, TRUE)",
                    lecturerId, userId, departmentId, "SB-" + lecturerId);
        }
        return userId;
    }

    private void ensureStudent(String studentId) {
        String userId = "sb-user-" + studentId;
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (users == null || users == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                            + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                            + "VALUES (?, ?, 'test-password', 'Break', 'Student', 'ACTIVE', FALSE, FALSE, 0, "
                            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    userId, studentId + "@fixture.test");
        }
        Integer students = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"Student\" WHERE \"id\" = ?", Integer.class, studentId);
        if (students == null || students == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                            + "VALUES (?, ?, ?, 'curriculum-demo', 4, CURRENT_TIMESTAMP)",
                    studentId, userId, "SB-" + studentId);
        }
    }

    private Map<String, Object> body(MvcResult result) throws Exception {
        return mapper.readValue(result.getResponse().getContentAsString(),
                new TypeReference<>() { });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> gradersOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("graders");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> pendingOf(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("pendingGraders");
    }
}
