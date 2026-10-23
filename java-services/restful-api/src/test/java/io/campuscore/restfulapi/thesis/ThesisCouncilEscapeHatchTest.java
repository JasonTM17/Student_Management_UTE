package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
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
import java.time.temporal.ChronoUnit;
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
        // The inbox lives outside the H2 thesis schema; the retract path
        // recalls tagged rows, so the test needs the table and its unique
        // (announcement_id, user_id) delivery index.
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
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS notification_announcement_user_uniq
                ON notifications.notification (announcement_id, user_id)
                """);
        jdbc.update("DELETE FROM notifications.notification");
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
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'esc-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'esc-%'");
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
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(roundId, now.minusSeconds(60), "reason", admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(roundId, now.minusSeconds(3_600), "reason", admin()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                roundId, now.plusSeconds(10 * 86_400), "reason", admin()))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("report date");

        mutations.extendGvpbDeadline(roundId, now.plusSeconds(2 * 86_400), "reviewer came back", admin());
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
                nckh, Instant.now().plusSeconds(86_400), "reason", admin()))
                .isInstanceOf(DomainException.class);
        UUID published = insertRound("Esc Published Round", "RESULTS_PUBLISHED",
                Instant.now().minusSeconds(3_600), Instant.now().plusSeconds(5 * 86_400));
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                published, Instant.now().plusSeconds(86_400), "reason", admin()))
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

        // While the counter-review window is still open the verdict is
        // provisional: the preview flags it separately and publish refuses
        // until the window closes — the reviewer may still overwrite.
        PublishResultsPreviewResponse openPreview = mutations.publishResultsPreview(roundId, admin());
        assertThat(openPreview.excludedTopics()).isEmpty();
        assertThat(openPreview.openReviewTopics()).extracting(t -> t.id()).containsExactly(failedTopic);
        assertThatThrownBy(() -> mutations.publishResults(roundId, null, admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("REVIEW_WINDOW_OPEN"));

        // Once the window closes the topic is a definite exclusion.
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(3_600)), roundId);
        PublishResultsPreviewResponse preview = mutations.publishResultsPreview(roundId, admin());
        assertThat(preview.ungradedGroupCount()).isZero();
        assertThat(preview.openReviewTopics()).isEmpty();
        assertThat(preview.excludedTopics()).extracting(t -> t.id()).containsExactly(failedTopic);
        assertThat(preview.excludedTopics().get(0).resultStatus()).isEqualTo("REVIEW_FAILED");

        // One permanently failed topic no longer holds the round hostage.
        mutations.publishResults(roundId, null, admin());
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
        mutations.publishResults(roundId, null, admin());
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

    @Test
    void withdrawnTopicRejectsEveryScoringPathUntilReinstated() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Withdrawn Scoring", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Withdrawn Council");
        seat(councilId, "esc-ws-chair", "CHAIR");
        seat(councilId, "esc-ws-secretary", "SECRETARY");
        seat(councilId, "esc-ws-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Withdrawn Score Topic");
        insertApprovedGroup(roundId, topicId, "esc-ws");
        assignCouncilTopic(councilId, topicId);
        ensureLecturer("esc-ws-reviewer");
        assignReviewerRow(topicId, "esc-ws-reviewer");

        mutations.withdrawTopic(topicId, "department pulled the topic", admin());

        // A withdrawn topic accepts no scores on any path: member defense
        // scores, the reviewer's counter-review, and finalize all refuse and
        // the stored WITHDRAWN outcome is never overwritten by GRADED.
        assertThatThrownBy(() -> submitDefense(councilId, topicId, "esc-ws-member", 8))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOPIC_WITHDRAWN"));
        assertThatThrownBy(() -> councils.submitReviewerScore(
                topicId, BigDecimal.valueOf(7), null, lecturer("esc-ws-reviewer")))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOPIC_WITHDRAWN"));
        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("esc-ws-chair")))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOPIC_WITHDRAWN"));
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isEqualTo("WITHDRAWN");

        // Reinstate is the only way back — audited — and then scoring works.
        mutations.reinstateTopic(topicId, "roster dispute resolved", admin());
        submitDefense(councilId, topicId, "esc-ws-member", 8);
    }

    @Test
    void waiverCannotOverruleAnExistingVerdict() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Waiver After Score", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc Scored Waive Topic");
        insertApprovedGroup(roundId, topicId, "esc-sv");
        ensureLecturer("esc-sv-reviewer");
        assignReviewerRow(topicId, "esc-sv-reviewer");

        // A below-threshold verdict is an academic record, not an obstacle a
        // waiver may erase: waiving after a score exists is refused and the
        // REVIEW_FAILED outcome stays.
        councils.submitReviewerScore(topicId, BigDecimal.valueOf(3.0), "yếu", lecturer("esc-sv-reviewer"));
        assertThatThrownBy(() -> councils.waiveCounterReview(topicId, "paper over the verdict", admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GVPB_SCORE_EXISTS"));
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isEqualTo("REVIEW_FAILED");

        // The only correction path is re-review inside the still-open window:
        // a passing overwrite clears the provisional outcome again.
        councils.submitReviewerScore(topicId, BigDecimal.valueOf(7.5), "đủ", lecturer("esc-sv-reviewer"));
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isNull();
    }

    @Test
    void scoredMemberAndLegacyKeyedMemberCannotBeReplaced() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Member Scores", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Esc Score Seat Council");
        seat(councilId, "esc-ms-chair", "CHAIR");
        seat(councilId, "esc-ms-secretary", "SECRETARY");
        seat(councilId, "esc-ms-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Esc Score Seat Topic");
        insertApprovedGroup(roundId, topicId, "esc-ms");
        assignCouncilTopic(councilId, topicId);
        ensureLecturer("esc-ms-new");

        // A score already cast belongs to the member who cast it: the seat is
        // never replaceable once that member's own defense score exists.
        submitDefense(councilId, topicId, "esc-ms-member", 8);
        assertThatThrownBy(() -> councils.replaceMember(
                councilId, "esc-ms-member", "esc-ms-new", "late swap", admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("COUNCIL_MEMBER_SCORED"));

        // The freeze check resolves the member under BOTH lecturer keys: a
        // seat stored under the legacy userId must not look departed.
        UUID legacyCouncil = insertCouncil(roundId, "Esc Legacy Key Council");
        seat(legacyCouncil, "esc-lk-chair", "CHAIR");
        seat(legacyCouncil, "esc-lk-secretary", "SECRETARY");
        seat(legacyCouncil, "esc-lk-member", "MEMBER");
        // Rewrite the member seat to the legacy userId form.
        jdbc.update("UPDATE thesis.thesis_council_member SET lecturer_id = ? "
                        + "WHERE council_id = ? AND member_role = 'MEMBER'",
                "esc-user-esc-lk-member", legacyCouncil);
        UUID legacyTopic = insertTopic(roundId, "Esc Legacy Key Topic");
        insertApprovedGroup(roundId, legacyTopic, "esc-lk");
        assignCouncilTopic(legacyCouncil, legacyTopic);
        ensureLecturer("esc-lk-new");
        // Score exists for the council but NOT from this member, and the
        // member is ACTIVE — the freeze check must still block the swap.
        submitDefense(legacyCouncil, legacyTopic, "esc-lk-chair", 7);
        assertThatThrownBy(() -> councils.replaceMember(
                legacyCouncil, "esc-user-esc-lk-member", "esc-lk-new", "swap", admin()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void gvpbExtensionRequiresReasonAndPinsReportDeadline() {
        Instant now = Instant.now();
        Instant oldDeadline = now.plusSeconds(86_400);
        UUID roundId = insertRound("Esc Round Report Pin", "REGISTRATION_CLOSED",
                oldDeadline, now.plusSeconds(5 * 86_400));
        // Fixture leaves report_deadline NULL, so report editing falls back to
        // gvpb_deadline: extending the review window must not reopen reports.
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                roundId, now.plusSeconds(2 * 86_400), "  ", admin()))
                .isInstanceOf(DomainException.class);
        mutations.extendGvpbDeadline(roundId, now.plusSeconds(2 * 86_400), "reviewer returned", admin());
        // The report window stayed pinned at the OLD counter-review deadline.
        assertThat(jdbc.queryForObject(
                "SELECT report_deadline FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId).toInstant())
                .isCloseTo(oldDeadline, within(1, ChronoUnit.MILLIS));
        assertThat(auditCount(roundId.toString(), "THESIS_ROUND_GVPB_DEADLINE_EXTENDED")).isEqualTo(1);
    }

    @Test
    void allExcludedPublicationRequiresTheExplicitConfirmation() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round All Excluded", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc All Withdrawn Topic");
        insertApprovedGroup(roundId, topicId, "esc-ae");
        mutations.withdrawTopic(topicId, "topic cannot be defended", admin());

        // Publishing an empty result set silently is refused without the
        // explicit flag; with it the publication proceeds and the audit names
        // the confirmation.
        assertThatThrownBy(() -> mutations.publishResults(roundId, null, admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RESULTS_ALL_EXCLUDED"));
        mutations.publishResults(roundId, true, admin());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId))
                .isEqualTo("RESULTS_PUBLISHED");
        Integer named = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"entityId\" = ? AND \"action\" = 'THESIS_RESULTS_PUBLISHED_WITH_EXCLUSIONS' "
                        + "AND \"summary\" LIKE '%confirmAllExcluded%'",
                Integer.class, roundId.toString());
        assertThat(named).isEqualTo(1);
    }

    @Test
    void withdrawAndReinstateRejectNonGovernanceActors() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Role Guard", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc Role Guard Topic");
        insertApprovedGroup(roundId, topicId, "esc-rg");
        ensureLecturer("esc-rg-lecturer");
        // The controller annotation is no longer the only check: the service
        // itself refuses a non-governance caller before touching the outcome.
        assertThatThrownBy(() -> mutations.withdrawTopic(
                topicId, "reason", lecturer("esc-rg-lecturer")))
                .isInstanceOf(DomainException.class);
        mutations.withdrawTopic(topicId, "admin withdraw", admin());
        assertThatThrownBy(() -> mutations.reinstateTopic(
                topicId, "reason", lecturer("esc-rg-lecturer")))
                .isInstanceOf(DomainException.class);
        assertThat(jdbc.queryForObject(
                "SELECT result_status FROM thesis.thesis_topic WHERE id = ?", String.class, topicId))
                .isEqualTo("WITHDRAWN");
    }

    @Test
    void lateTopicPublishesForNamedSupervisorAndAudits() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Late Topic", "REGISTRATION_OPEN",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        ensureLecturer("esc-lt-supervisor");
        var request = new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.LateTopicRequest(
                "department-demo", "Esc Late Topic", "Late topic description",
                2, "esc-lt-supervisor", "supervisor missed the window");
        var topic = mutations.createLateTopic(roundId, request, admin());
        assertThat(topic.status().name()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic_supervisor WHERE topic_id = ? AND lecturer_id = 'esc-lt-supervisor'",
                Integer.class, topic.id())).isEqualTo(1);
        assertThat(auditCount(topic.id().toString(), "THESIS_TOPIC_LATE_CREATED")).isEqualTo(1);

        // Validation still applies: unknown supervisor and a closed round both
        // refuse, and a non-governance caller is rejected at service level.
        assertThatThrownBy(() -> mutations.createLateTopic(roundId,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.LateTopicRequest(
                        "department-demo", "T", "D", 1, "esc-lt-missing", "reason"), admin()))
                .isInstanceOf(DomainException.class);
        UUID closedRound = insertRound("Esc Round Late Closed", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        assertThatThrownBy(() -> mutations.createLateTopic(closedRound, request, admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ROUND_STATE_CONFLICT"));
        ensureLecturer("esc-lt-lecturer");
        assertThatThrownBy(() -> mutations.createLateTopic(roundId, request, lecturer("esc-lt-lecturer")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rosterOverrideAddsStudentAfterCloseWithGuards() {
        Instant now = Instant.now();
        UUID closed = insertRound("Esc Round Roster Override", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(closed, "Esc Roster Override Topic");
        UUID groupId = insertGroupWithMembers(closed, topicId, "esc-ro", 3);
        ensureStudent("esc-ro-new");

        // Happy path: an active student joins an unscored group in a closed
        // round, audited with the reason.
        var added = mutations.overrideGroupRoster(groupId,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest(
                        "esc-ro-new", "medical leave resolved", null), admin());
        assertThat(added.memberStudentIds()).contains("esc-ro-new");
        assertThat(auditCount(groupId.toString(), "THESIS_GROUP_ROSTER_OVERRIDE")).isEqualTo(1);

        // Below-minimum results need the explicit size exception flag.
        UUID smallGroup = insertGroupWithMembers(closed, topicId, "esc-ro2", 1);
        ensureStudent("esc-ro2-new");
        assertThatThrownBy(() -> mutations.overrideGroupRoster(smallGroup,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest(
                        "esc-ro2-new", "reason", null), admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_SIZE_EXCEPTION_REQUIRED"));
        mutations.overrideGroupRoster(smallGroup,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest(
                        "esc-ro2-new", "partner dropped out", true), admin());
        assertThat(jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, smallGroup)).isEqualTo("partner dropped out");

        // Guards: an open round refuses, a scored topic refuses, a duplicate
        // member refuses.
        UUID openRound = insertRound("Esc Round Roster Open", "REGISTRATION_OPEN",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID openGroup = insertGroupWithMembers(openRound, insertTopic(openRound, "Esc Open Topic"), "esc-ro3", 3);
        ensureStudent("esc-ro3-new");
        assertThatThrownBy(() -> mutations.overrideGroupRoster(openGroup,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest(
                        "esc-ro3-new", "reason", null), admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ROUND_CLOSED"));
        UUID scoredCouncil = insertCouncil(closed, "Esc Scored Council");
        seat(scoredCouncil, "esc-ro-scorer", "MEMBER");
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score) "
                        + "VALUES (?, ?, ?, ?, 'DEFENSE', 8)",
                UUID.randomUUID(), topicId, scoredCouncil, "esc-ro-scorer");
        ensureStudent("esc-ro4-new");
        assertThatThrownBy(() -> mutations.overrideGroupRoster(groupId,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest(
                        "esc-ro4-new", "reason", null), admin()))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_SCORES_EXIST"));
    }

    @Test
    void retractPublicationRestoresClosedAndRequiresTypedConfirmation() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Retract", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc Retract Topic");
        insertApprovedGroup(roundId, topicId, "esc-rt");
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.0, "
                + "final_score_finalized_by = 'esc-chair', final_score_finalized_at = CURRENT_TIMESTAMP, "
                + "result_status = 'GRADED' WHERE id = ?", topicId);
        mutations.publishResults(roundId, null, admin());
        assertThat(jdbc.queryForObject(
                "SELECT results_published_at FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId)).isNotNull();
        // A "results published" inbox row tagged with the producer key must be
        // recalled by the retraction; unrelated rows stay.
        jdbc.update("INSERT INTO notifications.notification "
                        + "(id, user_id, title, message, type, link, source_key, is_read, created_at, updated_at) "
                        + "VALUES ('esc-rt-note', 'esc-rt-user', 'Kết quả khóa luận đã công bố', 'm', 'SUCCESS', "
                        + "'/dashboard/thesis', 'THESIS_RESULTS:" + roundId + "', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO notifications.notification "
                        + "(id, user_id, title, message, type, link, source_key, is_read, created_at, updated_at) "
                        + "VALUES ('esc-rt-other', 'esc-rt-user', 'other', 'm', 'INFO', NULL, 'OTHER', FALSE, "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        var req = new java.util.function.Function<String,
                io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RetractPublicationRequest>() {
            @Override
            public io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RetractPublicationRequest apply(String c) {
                return new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos
                        .RetractPublicationRequest("published before appeal resolved", c);
            }
        };
        // Typed confirmation and admin-only at service level.
        assertThatThrownBy(() -> mutations.retractResults(roundId, req.apply("yes"), admin()))
                .isInstanceOf(DomainException.class);
        ensureLecturer("esc-rt-dean-user");
        Jwt dean = Jwt.withTokenValue("escape-fixture").header("alg", "none")
                .subject("esc-rt-dean").claim("roles", List.of("TRUONG_KHOA")).build();
        assertThatThrownBy(() -> mutations.retractResults(roundId, req.apply("RETRACT"), dean))
                .isInstanceOf(DomainException.class);

        mutations.retractResults(roundId, req.apply("RETRACT"), admin());
        assertThat(jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId))
                .isEqualTo("REGISTRATION_CLOSED");
        assertThat(jdbc.queryForObject(
                "SELECT results_published_at FROM thesis.thesis_registration_round WHERE id = ?",
                Timestamp.class, roundId)).isNull();
        // Scores are never modified by the retraction.
        assertThat(jdbc.queryForObject(
                "SELECT final_score FROM thesis.thesis_topic WHERE id = ?", BigDecimal.class, topicId))
                .isEqualByComparingTo("8.00");
        assertThat(auditCount(roundId.toString(), "THESIS_RESULTS_RETRACTED")).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE id = 'esc-rt-note'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification WHERE id = 'esc-rt-other'", Integer.class))
                .isEqualTo(1);
        // Student visibility is governed by the round status: a retracted
        // round hides results again even though scores still exist.
        assertThatThrownBy(() -> councils.studentResults(roundId, "esc-rt-student-1"))
                .isInstanceOf(DomainException.class)
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("RESULTS_NOT_PUBLISHED"));
    }

    @Test
    void aForeignFacultyDeanCannotWithdrawOrReinstateAnotherFacultysTopic() {
        Instant now = Instant.now();
        // A resolved but foreign faculty scope: the dean's lecturer profile is
        // active and bound to a faculty that does not contain the topic's
        // department, so every topic-level governance write conceals as 404.
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\",\"name\",\"isActive\") "
                + "VALUES ('esc-faculty-foreign','Foreign faculty',TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"nameEn\",\"nameVi\",\"code\",\"facultyId\",\"isActive\") "
                + "VALUES ('esc-department-foreign','Foreign','Foreign','Foreign','FR','esc-faculty-foreign',TRUE)");
        String deanUser = "esc-user-foreign-dean";
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, 'esc-foreign-dean@fixture.test', 'test-password', 'Esc', 'Dean', 'ACTIVE', FALSE, "
                        + "FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                deanUser);
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES ('esc-foreign-dean','esc-user-foreign-dean','esc-department-foreign','GV-foreign-dean',TRUE)");
        Jwt foreignDean = Jwt.withTokenValue("escape-fixture").header("alg", "none")
                .subject(deanUser)
                .claim("roles", List.of("TRUONG_KHOA"))
                .claim("lecturerId", "esc-foreign-dean").build();

        UUID roundId = insertRound("Esc Round Foreign Dean", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Esc Foreign Dean Topic");
        insertApprovedGroup(roundId, topicId, "esc-fd");

        assertThatThrownBy(() -> mutations.withdrawTopic(topicId, "not their faculty", foreignDean))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOPIC_NOT_FOUND"));
        mutations.withdrawTopic(topicId, "admin withdraw", admin());
        assertThatThrownBy(() -> mutations.reinstateTopic(topicId, "not their faculty", foreignDean))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("TOPIC_NOT_FOUND"));

        // A dean with NO resolvable faculty profile fails closed the same way —
        // scope cannot be bypassed by an unbound JWT.
        Jwt unboundDean = Jwt.withTokenValue("escape-fixture").header("alg", "none")
                .subject("esc-nobody")
                .claim("roles", List.of("TRUONG_KHOA"))
                .claim("lecturerId", "esc-foreign-dean").build();
        assertThatThrownBy(() -> mutations.withdrawTopic(topicId, "no profile", unboundDean))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void withdrawnTopicOrphansApprovedGroupUntilAdminReassigns() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Orphaned Group", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID withdrawnTopic = insertTopic(roundId, "Esc Orphan Source");
        UUID targetTopic = insertTopic(roundId, "Esc Orphan Target");
        UUID groupId = insertGroupWithMembers(roundId, withdrawnTopic, "esc-og", 3);

        mutations.withdrawTopic(withdrawnTopic, "department pulled the topic", admin());

        // The dead end: every existing group path refuses a REGISTRATION_CLOSED
        // group — re-point needs the open window, reject needs a PENDING group,
        // and member edits are frozen — so the approved roster sits excluded
        // from publication with no way back to a live topic.
        assertThatThrownBy(() -> mutations.assignTopic(groupId,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicAssignmentRequest(targetTopic),
                admin()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ROUND_CLOSED"));
        assertThatThrownBy(() -> mutations.rejectGroup(groupId,
                new io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupRejectionRequest("orphaned"),
                admin()))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getCode()).isEqualTo("GROUP_APPROVAL_STATE_CONFLICT"));
        assertThatThrownBy(() -> mutations.removeMember(groupId, "esc-og-student-2", admin()))
                .isInstanceOf(DomainException.class);

        // The audited escape: re-point the group to a live topic in the same
        // round, keeping its roster approval — post-close there is no path to
        // re-approve, so a re-point that dropped approval would strand it again.
        reassign(groupId, targetTopic, "esc-og-admin")
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT topic_id FROM thesis.thesis_group WHERE id = ?", String.class, groupId))
                .isEqualTo(targetTopic.toString());
        assertThat(jdbc.queryForObject(
                "SELECT approval_status FROM thesis.thesis_group WHERE id = ?", String.class, groupId))
                .isEqualTo("APPROVED");
        assertThat(auditCount(groupId.toString(), "THESIS_GROUP_TOPIC_REASSIGNED")).isEqualTo(1);
    }

    @Test
    void topicReassignRefusesLiveTopicsScoredPairsAndForeignDeans() throws Exception {
        Instant now = Instant.now();
        UUID roundId = insertRound("Esc Round Reassign Guards", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID liveTopic = insertTopic(roundId, "Esc Reassign Live");
        UUID otherTopic = insertTopic(roundId, "Esc Reassign Other");
        UUID groupId = insertGroupWithMembers(roundId, liveTopic, "esc-rg", 3);

        // Not orphaned: a live topic keeps its binding.
        reassign(groupId, otherTopic, "esc-rg-admin")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_TOPIC_NOT_ORPHANED"));

        // Withdrawn but scored: the binding is frozen by the verdict.
        UUID scoredTopic = insertTopic(roundId, "Esc Reassign Scored");
        UUID scoredGroup = insertGroupWithMembers(roundId, scoredTopic, "esc-rg2", 3);
        UUID scoreCouncil = insertCouncil(roundId, "Esc Reassign Score Council");
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score, graded_at) "
                + "VALUES (?, ?, ?, 'esc-rg-grader', 'DEFENSE', 7.0, CURRENT_TIMESTAMP)",
                UUID.randomUUID(), scoredTopic, scoreCouncil);
        mutations.withdrawTopic(scoredTopic, "withdrawn after a stray score", admin());
        reassign(scoredGroup, otherTopic, "esc-rg-admin")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP_SCORES_EXIST"));

        // A foreign-faculty dean is concealed as not-found on the orphaned topic.
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\",\"name\",\"isActive\") "
                + "VALUES ('esc-faculty-rg','Reassign faculty',TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"nameEn\",\"nameVi\",\"code\",\"facultyId\",\"isActive\") "
                + "VALUES ('esc-department-rg','Reassign','Reassign','Reassign','RG','esc-faculty-rg',TRUE)");
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                        + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES ('esc-user-rg-dean', 'esc-rg-dean@fixture.test', 'test-password', 'Esc', 'RgDean', "
                        + "'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                + "VALUES ('esc-rg-dean','esc-user-rg-dean','esc-department-rg','GV-rg-dean',TRUE)");
        UUID thirdTopic = insertTopic(roundId, "Esc Reassign Third");
        UUID thirdGroup = insertGroupWithMembers(roundId, thirdTopic, "esc-rg3", 3);
        mutations.withdrawTopic(thirdTopic, "gone", admin());
        mvc.perform(post("/api/v1/thesis/groups/{id}/topic-reassign", thirdGroup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topicId\":\"" + otherTopic + "\",\"reason\":\"foreign dean\"}")
                        .with(ThesisAuthorityFixture.role(jdbc, "esc-user-rg-dean", "TRUONG_KHOA")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions reassign(
            UUID groupId, UUID targetTopicId, String adminSubject) throws Exception {
        return mvc.perform(post("/api/v1/thesis/groups/{id}/topic-reassign", groupId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicId\":\"" + targetTopicId + "\",\"reason\":\"orphan re-point\"}")
                .with(ThesisAuthorityFixture.role(jdbc, adminSubject, "ADMIN")));
    }

    // ---------- fixtures ----------

    private UUID insertGroupWithMembers(UUID roundId, UUID topicId, String tag, int memberCount) {
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'APPROVED')",
                groupId, roundId, tag + "-leader", topicId);
        for (int order = 1; order <= memberCount; order++) {
            ensureStudent(tag + "-student-" + order);
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, tag + "-student-" + order, order, order == 1);
        }
        return groupId;
    }

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
