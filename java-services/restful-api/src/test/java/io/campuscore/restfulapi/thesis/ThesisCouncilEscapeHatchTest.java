package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.web.DomainException;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Escape-hatch regressions: every wedge an authorized operator could not leave
 * through the API gets an audited, bounded way out — a missed counter-review
 * deadline, a sub-threshold counter-review blocking publication, an assigned
 * topic that can never move councils, and a roster seat held by a deactivated
 * lecturer. Each case asserted the old dead end first during development.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_escape_hatch;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisCouncilEscapeHatchTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ThesisCouncilService councils;
    @Autowired
    private ThesisMutationService mutations;

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
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'esc-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'esc-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'esc-%'");
    }

    @Test
    void missedCounterReviewDeadlineLocksReviewerUntilGovernanceExtendsIt() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Missed Deadline", "REGISTRATION_CLOSED",
                now.minusSeconds(3_600), now.plusSeconds(5 * 24 * 3_600));
        UUID topicId = insertTopic(roundId, "Esc Missed Deadline Topic");
        insertApprovedGroup(roundId, topicId, "esc-md");
        ensureLecturer("esc-md-reviewer");
        assignReviewerRow(topicId, "esc-md-reviewer");

        // The wedge: the assigned reviewer can never submit after the deadline.
        assertThatThrownBy(() -> councils.submitReviewerScore(
                topicId, BigDecimal.valueOf(7), null, lecturer("esc-md-reviewer")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("deadline");

        // Only governance may extend; a student is refused over HTTP.
        try {
            mvc.perform(put("/api/v1/thesis/rounds/{id}/gvpb-deadline", roundId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"gvpbDeadline\":\"" + now.plusSeconds(2 * 86_400) + "\"}")
                            .with(ThesisAuthorityFixture.student(jdbc, "esc-md-student-x")))
                    .andExpect(status().isForbidden());
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        // Invalid targets stay invalid: the past, the old deadline, and beyond
        // the report date all fail before the row moves.
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(roundId, now.minusSeconds(60), admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(roundId, now.minusSeconds(3_600), admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                roundId, now.plusSeconds(10 * 86_400), admin()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("report date");

        mutations.extendGvpbDeadline(roundId, now.plusSeconds(2 * 86_400), admin());
        councils.submitReviewerScore(topicId, BigDecimal.valueOf(7), null, lecturer("esc-md-reviewer"));
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ? AND component = 'GVPB'",
                Integer.class, topicId)).isEqualTo(1);
        assertThat(auditCount(roundId.toString(), "THESIS_ROUND_GVPB_DEADLINE_EXTENDED")).isEqualTo(1);
    }

    @Test
    void nonGvpbAndTerminalRoundsRejectDeadlineExtension() {
        UUID nckh = insertRoundOfType("Esc NCKH Round", "NCKH", "REGISTRATION_CLOSED", null, null);
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                nckh, Instant.now().plusSeconds(86_400), admin()))
                .isInstanceOf(DomainException.class);
        UUID published = insertRound("Esc Published Round", "RESULTS_PUBLISHED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(5 * 86_400));
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                published, Instant.now().plusSeconds(86_400), admin()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void subThresholdCounterReviewMarksTopicFailedAndStopsBlockingPublication() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Review Failed", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID failedTopic = insertTopic(roundId, "Esc Failed Review Topic");
        UUID gradedTopic = insertTopic(roundId, "Esc Graded Topic");
        insertApprovedGroup(roundId, failedTopic, "esc-rf-a");
        insertApprovedGroup(roundId, gradedTopic, "esc-rf-b");
        ensureLecturer("esc-rf-reviewer");
        assignReviewerRow(failedTopic, "esc-rf-reviewer");
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.5, "
                + "final_score_finalized_by = 'esc-chair', final_score_finalized_at = CURRENT_TIMESTAMP, "
                + "result_status = 'GRADED' WHERE id = ?", gradedTopic);

        councils.submitReviewerScore(failedTopic, BigDecimal.valueOf(3.0), "yếu", lecturer("esc-rf-reviewer"));

        // The failed outcome is stored and visible, not a silent wedge.
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, failedTopic))
                .isEqualTo("REVIEW_FAILED");

        PublishResultsPreviewResponse preview = mutations.publishResultsPreview(roundId);
        assertThat(preview.ungradedGroupCount()).isZero();
        assertThat(preview.excludedTopics()).extracting(t -> t.id()).containsExactly(failedTopic);
        assertThat(preview.excludedTopics().get(0).resultStatus()).isEqualTo("REVIEW_FAILED");

        // One permanently failed topic no longer holds the round hostage.
        mutations.publishResults(roundId, admin());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId))
                .isEqualTo("RESULTS_PUBLISHED");
        assertThat(auditCount(roundId.toString(), "THESIS_RESULTS_PUBLISHED_WITH_EXCLUSIONS")).isEqualTo(1);
    }

    @Test
    void waivedCounterReviewLetsCouncilFinalizeAndRevocationReArmsIt() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Waiver", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Waiver Council");
        seat(councilId, "esc-wv-chair", "CHAIR");
        seat(councilId, "esc-wv-secretary", "SECRETARY");
        seat(councilId, "esc-wv-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Waived Topic");
        insertApprovedGroup(roundId, topicId, "esc-wv");
        assignCouncilTopic(councilId, topicId);
        submitDefense(councilId, topicId, "esc-wv-chair", 8);
        submitDefense(councilId, topicId, "esc-wv-secretary", 8);
        submitDefense(councilId, topicId, "esc-wv-member", 8);

        // The wedge: no counter-reviewer ever submitted, so finalize refused.
        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("esc-wv-chair")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("counter-review");

        councils.waiveCounterReview(topicId, "reviewer unavailable past deadline", admin());
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_waived_reason FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isEqualTo("reviewer unavailable past deadline");
        assertThat(auditCount(topicId.toString(), "GVPB_WAIVED")).isEqualTo(1);

        var result = councils.finalizeScores(councilId, topicId, lecturer("esc-wv-chair"));
        assertThat(result.finalScore()).isEqualByComparingTo("8.00");

        // A second waiver attempt conflicts; revocation on a finalized topic too.
        assertThatThrownBy(() -> councils.waiveCounterReview(topicId, "again", admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> councils.revokeCounterReviewWaiver(topicId, "undo", admin()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void withdrawalRemovesTopicFromCompletenessAndReinstatementRestoresIt() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Withdraw", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc Withdrawn Topic");
        UUID gradedTopic = insertTopic(roundId, "Esc Graded Partner");
        insertApprovedGroup(roundId, topicId, "esc-wd-a");
        insertApprovedGroup(roundId, gradedTopic, "esc-wd-b");
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 7.0, "
                + "final_score_finalized_by = 'esc-chair', final_score_finalized_at = CURRENT_TIMESTAMP, "
                + "result_status = 'GRADED' WHERE id = ?", gradedTopic);

        mutations.withdrawTopic(topicId, "topic council could not be seated", admin());
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isEqualTo("WITHDRAWN");
        assertThat(auditCount(topicId.toString(), "THESIS_TOPIC_WITHDRAWN")).isEqualTo(1);
        assertThatThrownBy(() -> mutations.withdrawTopic(topicId, "again", admin()))
                .isInstanceOf(DomainException.class);

        mutations.reinstateTopic(topicId, "roster restored", admin());
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isNull();
        assertThat(auditCount(topicId.toString(), "THESIS_TOPIC_REINSTATED")).isEqualTo(1);

        mutations.withdrawTopic(topicId, "final withdrawal", admin());
        mutations.publishResults(roundId, admin());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId))
                .isEqualTo("RESULTS_PUBLISHED");
    }

    @Test
    void topicLeavesCouncilBeforeScoringAndMovesToAnother() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Move", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID firstCouncil = insertCouncil(roundId, "Esc Council One");
        UUID secondCouncil = insertCouncil(roundId, "Esc Council Two");
        UUID topicId = insertTopic(roundId, "Esc Moving Topic");
        insertApprovedGroup(roundId, topicId, "esc-mv");
        assignCouncilTopic(firstCouncil, topicId);

        councils.unassignTopic(firstCouncil, topicId, "rebalancing councils", admin());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = ?", Integer.class, topicId))
                .isZero();
        assertThat(auditCount(firstCouncil.toString(), "COUNCIL_TOPIC_UNASSIGNED")).isEqualTo(1);

        // The unique topic index no longer traps the move (a council must seat
        // a full roster before it may grade topics).
        seat(secondCouncil, "esc-mv-chair", "CHAIR");
        seat(secondCouncil, "esc-mv-secretary", "SECRETARY");
        seat(secondCouncil, "esc-mv-member", "MEMBER");
        councils.assignTopic(secondCouncil, topicId, admin());
        assertThat(jdbc.queryForObject(
                "SELECT council_id FROM thesis.thesis_council_topic WHERE topic_id = ?",
                String.class, topicId)).isEqualTo(secondCouncil.toString());

        // Once a defense score exists the topic is locked to its council.
        submitDefense(secondCouncil, topicId, "esc-mv-member", 7);
        assertThatThrownBy(() -> councils.unassignTopic(secondCouncil, topicId, "too late", admin()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("defense scores");
    }

    @Test
    void deactivatedSeatCanBeReplacedAndStopsInflatingExpectedGraders() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Roster", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Roster Council");
        seat(councilId, "esc-rs-chair", "CHAIR");
        seat(councilId, "esc-rs-secretary", "SECRETARY");
        seat(councilId, "esc-rs-departed", "MEMBER");
        seat(councilId, "esc-rs-member-4", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Roster Topic");
        insertApprovedGroup(roundId, topicId, "esc-rs");
        assignCouncilTopic(councilId, topicId);
        ensureLecturer("esc-rs-replacement");
        ensureLecturer("esc-rs-reviewer");
        assignReviewerRow(topicId, "esc-rs-reviewer");
        councils.submitReviewerScore(topicId, BigDecimal.valueOf(6), null, lecturer("esc-rs-reviewer"));
        submitDefense(councilId, topicId, "esc-rs-chair", 9);
        submitDefense(councilId, topicId, "esc-rs-secretary", 7);
        submitDefense(councilId, topicId, "esc-rs-member-4", 8);

        // Deactivate the fourth member: hasMember already blocks their submit,
        // and the eligible-grader set must not wait on them forever — the
        // council still holds the minimum three eligible graders.
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\" = FALSE WHERE \"id\" = 'esc-rs-departed'");
        assertThatThrownBy(() -> councils.submitScore(
                councilId, topicId, "DEFENSE", BigDecimal.valueOf(8), lecturer("esc-rs-departed")))
                .isInstanceOf(DomainException.class);
        var finalized = councils.finalizeScores(councilId, topicId, lecturer("esc-rs-chair"));
        assertThat(finalized.finalScore()).isEqualByComparingTo("8.00");

        // A fresh council: an inactive seat still swaps in place even though a
        // score row already exists — the departed member can never score again.
        UUID secondCouncil = insertCouncil(roundId, "Esc Roster Council Two");
        seat(secondCouncil, "esc-r2-chair", "CHAIR");
        seat(secondCouncil, "esc-r2-secretary", "SECRETARY");
        seat(secondCouncil, "esc-r2-departed", "MEMBER");
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\" = FALSE WHERE \"id\" = 'esc-r2-departed'");
        UUID secondTopic = insertTopic(roundId, "Esc Roster Topic Two");
        insertApprovedGroup(roundId, secondTopic, "esc-r2");
        assignCouncilTopic(secondCouncil, secondTopic);
        submitDefense(secondCouncil, secondTopic, "esc-r2-chair", 6);
        ensureLecturer("esc-r2-replacement");

        councils.replaceMember(secondCouncil, "esc-r2-departed", "esc-r2-replacement", "member deactivated", admin());
        assertThat(jdbc.queryForObject(
                "SELECT lecturer_id FROM thesis.thesis_council_member WHERE council_id = ? AND member_role = 'MEMBER'",
                String.class, secondCouncil)).isEqualTo("esc-r2-replacement");
        assertThat(auditCount(secondCouncil.toString(), "COUNCIL_MEMBER_REPLACED")).isEqualTo(1);
    }

    @Test
    void replacementRejectsDuplicateUnknownAndDependentIncomingMembers() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Replace", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Replace Council");
        seat(councilId, "esc-rp-chair", "CHAIR");
        seat(councilId, "esc-rp-secretary", "SECRETARY");
        seat(councilId, "esc-rp-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Replace Topic");
        insertApprovedGroup(roundId, topicId, "esc-rp");
        assignCouncilTopic(councilId, topicId);
        ensureLecturer("esc-rp-supervisor");
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) "
                        + "VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, "esc-rp-supervisor");
        ensureLecturer("esc-rp-inactive");
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"isActive\" = FALSE WHERE \"id\" = 'esc-rp-inactive'");

        // The seat cannot go to someone already on the council...
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-rp-member", "esc-rp-secretary", "dup", admin()))
                .isInstanceOf(DomainException.class);
        // ...to an inactive or unknown lecturer...
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-rp-member", "esc-rp-inactive", "inactive", admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-rp-member", "esc-rp-missing", "unknown", admin()))
                .isInstanceOf(DomainException.class);
        // ...to the topic's own supervisor (independence)...
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-rp-member", "esc-rp-supervisor", "independence", admin()))
                .isInstanceOf(DomainException.class);
        // ...or by a non-governance actor.
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-rp-member", "esc-rp-supervisor", "nope",
                lecturer("esc-rp-member")))
                .isInstanceOf(DomainException.class);
        assertThat(auditCount(councilId.toString(), "COUNCIL_MEMBER_REPLACED")).isZero();

        // The valid swap still works after every rejection.
        ensureLecturer("esc-rp-new");
        councils.replaceMember(councilId, "esc-rp-member", "esc-rp-new", "departure", admin());
        assertThat(jdbc.queryForObject(
                "SELECT lecturer_id FROM thesis.thesis_council_member WHERE council_id = ? AND member_role = 'MEMBER'",
                String.class, councilId)).isEqualTo("esc-rp-new");
    }

    @Test
    void escapeHatchRoutesRejectNonGovernanceCallers() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Authz", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Authz Council");
        seat(councilId, "esc-au-chair", "CHAIR");
        seat(councilId, "esc-au-secretary", "SECRETARY");
        seat(councilId, "esc-au-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Authz Topic");
        insertApprovedGroup(roundId, topicId, "esc-au");
        assignCouncilTopic(councilId, topicId);
        ensureStudent("esc-au-student");
        var student = ThesisAuthorityFixture.student(jdbc, "esc-au-student");

        mvc.perform(post("/api/v1/thesis/councils/{id}/topics/{topicId}/unassign", councilId, topicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").with(student))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/thesis/councils/{id}/members/{lecturerId}", councilId, "esc-au-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"esc-au-chair\",\"reason\":\"x\"}").with(student))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/topics/{id}/gvpb-waiver", topicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").with(student))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/thesis/topics/{id}/withdraw", topicId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}").with(student))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/thesis/rounds/{id}/gvpb-deadline", roundId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gvpbDeadline\":\"" + now.plusSeconds(2 * 86_400) + "\"}")
                        .with(ThesisAuthorityFixture.lecturer(jdbc, "esc-au-chair")))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_council_topic WHERE topic_id = ?",
                Integer.class, topicId)).isEqualTo(1);
    }

    // ---------- fixtures ----------

    private Jwt admin() {
        return Jwt.withTokenValue("escape-fixture").header("alg", "none")
                .subject("esc-admin").claim("roles", List.of("ADMIN")).build();
    }

    private Jwt lecturer(String lecturerId) {
        return Jwt.withTokenValue("escape-fixture").header("alg", "none")
                .subject("esc-user-" + lecturerId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    private UUID insertRound(String name, String status, Instant gvpbDeadline, Instant reportDate) {
        return insertRoundOfType(name, "KLTN", status, gvpbDeadline, reportDate);
    }

    private UUID insertRoundOfType(String name, String type, String status,
            Instant gvpbDeadline, Instant reportDate) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, type,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                gvpbDeadline == null ? null : Timestamp.from(gvpbDeadline),
                reportDate == null ? null : Timestamp.from(reportDate), status);
        return id;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'Escape fixture topic', 2, 'PUBLISHED', 'esc-admin')",
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
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, tag + "-student-" + order, order, order == 1);
        }
    }

    private UUID insertCouncil(UUID roundId, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) "
                        + "VALUES (?, ?, ?, 'ACTIVE', 'esc-admin')",
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
                        + "VALUES (?, ?, ?, 'esc-admin')",
                UUID.randomUUID(), councilId, topicId);
    }

    private void assignReviewerRow(UUID topicId, String lecturerId) {
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?", lecturerId, topicId);
    }

    private void submitDefense(UUID councilId, UUID topicId, String lecturerId, int score) {
        councils.submitScore(councilId, topicId, "DEFENSE", BigDecimal.valueOf(score), lecturer(lecturerId));
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "esc-user-" + lecturerId;
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (users == null || users == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                            + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                            + "VALUES (?, ?, 'test-password', 'Esc', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
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
        String userId = "esc-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Esc', 'Student', 'ACTIVE', FALSE, FALSE, 0, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@fixture.test");
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 4, CURRENT_TIMESTAMP)",
                studentId, userId, "ESC-" + studentId);
    }

    private int auditCount(String entityId, String action) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = ?",
                Integer.class, entityId, action);
        return count == null ? 0 : count;
    }
}
