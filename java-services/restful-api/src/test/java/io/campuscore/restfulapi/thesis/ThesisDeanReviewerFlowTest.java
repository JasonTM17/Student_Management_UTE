package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.PublishResultsPreviewResponse;
import io.campuscore.restfulapi.thesis.model.TopicStatus;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisTopicService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Counter-reviewer regressions for the dean flow: a TRUONG_KHOA assigned as
 * GVPB reviewer reaches the assignment through the catalog and score read even
 * when the topic sits in a foreign faculty (the assignment is the grant, like
 * the report read), a profile with no live bound account is refused at
 * assignment, and finalize/publication refusals name the reviewer and the
 * ungraded topics instead of a generic stop. Each case asserted the old dead
 * end first during development.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_dean_reviewer;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisDeanReviewerFlowTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ThesisCouncilService councils;
    @Autowired
    private ThesisMutationService mutations;
    @Autowired
    private ThesisTopicService topicReads;

    @BeforeEach
    void cleanDatabase() {
        // The reviewer-assignment notice fans out after commit; the inbox lives
        // outside the H2 thesis schema, so the test needs the table present.
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
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'drf-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'drf-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'drf-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" LIKE 'drf-%'");
        jdbc.update("DELETE FROM academic.\"Faculty\" WHERE \"id\" LIKE 'drf-%'");
    }

    @Test
    void assignedDeanReachesForeignFacultyReviewTopic() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Drf Round Cross Faculty", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        // The topic lives in the fixture faculty; the dean's profile is bound
        // to a different faculty, so faculty scope alone would conceal it.
        UUID topicId = insertTopic(roundId, "Drf Cross-Faculty Topic");
        UUID untouchedTopic = insertTopic(roundId, "Drf Other Topic");
        insertApprovedGroup(roundId, topicId, "drf-cf-a");
        insertApprovedGroup(roundId, untouchedTopic, "drf-cf-b");
        ensureLecturer("drf-other-reviewer");
        assignReviewerRow(untouchedTopic, "drf-other-reviewer");
        assignReviewerRow(topicId, "drf-fdean");
        Jwt dean = foreignDean();

        // Old behavior: the dean's catalog hid the assignment and the score
        // read 404'd even though submission was accepted.
        assertThat(topicReads.list(roundId, TopicStatus.PUBLISHED, dean))
                .extracting(t -> t.id())
                .containsExactly(topicId);

        councils.submitReviewerScore(topicId, BigDecimal.valueOf(7), "ok", dean);
        var own = councils.reviewerScore(topicId, dean);
        assertThat(own.score()).isEqualByComparingTo("7.00");
        assertThat(own.lecturerId()).isEqualTo("drf-fdean");

        // Faculty scope still binds the dean on a topic they do not review.
        assertThatThrownBy(() -> councils.reviewerScore(untouchedTopic, dean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("TOPIC_NOT_FOUND"));
        assertThatThrownBy(() -> councils.submitReviewerScore(
                untouchedTopic, BigDecimal.valueOf(8), null, dean))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("REVIEWER_REQUIRED"));
    }

    @Test
    void assignReviewerRefusesProfileWhoseAccountCannotAuthenticate() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Drf Round Inactive Reviewer", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID topicId = insertTopic(roundId, "Drf Inactive Reviewer Topic");
        insertApprovedGroup(roundId, topicId, "drf-ia");
        // The profile exists but its bound account is deactivated: login can
        // never mint the lecturerId claim, so the reviewer could never score.
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                + "VALUES ('drf-user-inactive', 'drf-inactive@fixture.test', 'test-password', 'Drf', 'Inactive', "
                + "'INACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                + "VALUES ('drf-inactive-reviewer','drf-user-inactive','department-demo','GV-drf-inactive',TRUE)");

        assertThatThrownBy(() -> councils.assignReviewer(topicId, "drf-inactive-reviewer", admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException denied = (DomainException) failure;
                    assertThat(denied.code()).isEqualTo("LECTURER_ACCOUNT_REQUIRED");
                    assertThat(denied.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isNull();

        // A live profile is still assignable.
        ensureLecturer("drf-active-reviewer");
        councils.assignReviewer(topicId, "drf-active-reviewer", admin());
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isEqualTo("drf-active-reviewer");
    }

    @Test
    void finalizeFailureNamesReviewerAndScoreShortfall() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Drf Round Finalize", "REGISTRATION_CLOSED",
                now.plusSeconds(86_400), now.plusSeconds(5 * 86_400));
        UUID councilId = insertCouncil(roundId, "Drf Finalize Council");
        seat(councilId, "drf-fz-chair", "CHAIR");
        seat(councilId, "drf-fz-secretary", "SECRETARY");
        seat(councilId, "drf-fz-member", "MEMBER");
        UUID topicId = insertTopic(roundId, "Drf Finalize Topic");
        insertApprovedGroup(roundId, topicId, "drf-fz");
        assignCouncilTopic(councilId, topicId);
        ensureLecturer("drf-fz-reviewer");
        assignReviewerRow(topicId, "drf-fz-reviewer");
        submitDefense(councilId, topicId, "drf-fz-chair", 8);
        submitDefense(councilId, topicId, "drf-fz-secretary", 8);

        // Two of three eligible members scored: the refusal carries counts.
        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("drf-fz-chair")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException denied = (DomainException) failure;
                    assertThat(denied.code()).isEqualTo("SCORES_INCOMPLETE");
                    assertThat(denied.getDetails())
                            .containsEntry("submitted", 2)
                            .containsEntry("expected", 3);
                });

        submitDefense(councilId, topicId, "drf-fz-member", 8);

        // Old behavior: a bare "GVPB score required" stop. Now the reviewer is
        // named (display name in the message, stable id in details) so the
        // chair knows who to chase or reassign.
        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("drf-fz-chair")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException denied = (DomainException) failure;
                    assertThat(denied.code()).isEqualTo("REVIEW_SCORE_REQUIRED");
                    assertThat(denied.getMessage()).contains("Drf");
                    assertThat(denied.getMessage()).doesNotContain("(drf-fz-reviewer)");
                    assertThat(denied.getDetails())
                            .containsEntry("reviewerId", "drf-fz-reviewer")
                            .containsKey("reviewer");
                });

        councils.submitReviewerScore(topicId, BigDecimal.valueOf(3), "yếu", lecturer("drf-fz-reviewer"));
        assertThatThrownBy(() -> councils.finalizeScores(councilId, topicId, lecturer("drf-fz-chair")))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException denied = (DomainException) failure;
                    assertThat(denied.code()).isEqualTo("REVIEW_SCORE_BELOW_THRESHOLD");
                    assertThat(denied.getDetails()).containsKey("score");
                });
    }

    @Test
    void publishPreviewAndRefusalNameTheUngradedTopics() {
        Instant now = Instant.now();
        UUID roundId = insertRound("Drf Round Publish", "REGISTRATION_CLOSED",
                now.minusSeconds(3_600), now.plusSeconds(5 * 86_400));
        UUID ungraded = insertTopic(roundId, "Drf Ungraded Topic");
        UUID graded = insertTopic(roundId, "Drf Graded Topic");
        insertApprovedGroup(roundId, ungraded, "drf-pub-a");
        insertApprovedGroup(roundId, graded, "drf-pub-b");
        jdbc.update("UPDATE thesis.thesis_topic SET final_score = 8.0, "
                + "final_score_finalized_by = 'drf-chair', final_score_finalized_at = CURRENT_TIMESTAMP, "
                + "result_status = 'GRADED' WHERE id = ?", graded);

        PublishResultsPreviewResponse preview = mutations.publishResultsPreview(roundId, admin());
        assertThat(preview.ungradedTopics())
                .extracting(t -> t.id())
                .containsExactly(ungraded);
        assertThat(preview.ungradedTopics().get(0).title()).isEqualTo("Drf Ungraded Topic");

        // The refusal tells the operator exactly which topic to chase instead
        // of the old bare "some groups are ungraded" stop.
        assertThatThrownBy(() -> mutations.publishResults(roundId, null, admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException denied = (DomainException) failure;
                    assertThat(denied.code()).isEqualTo("SCORES_INCOMPLETE");
                    assertThat(denied.getMessage()).contains("Drf Ungraded Topic");
                    assertThat(denied.getMessage()).doesNotContain("Drf Graded Topic");
                });
    }

    // ---------- fixtures ----------

    private Jwt admin() {
        return Jwt.withTokenValue("drf-fixture").header("alg", "none")
                .subject("drf-admin").claim("roles", List.of("ADMIN")).build();
    }

    private Jwt lecturer(String lecturerId) {
        return Jwt.withTokenValue("drf-fixture").header("alg", "none")
                .subject("drf-user-" + lecturerId)
                .claim("roles", List.of("LECTURER"))
                .claim("lecturerId", lecturerId).build();
    }

    /** A TRUONG_KHOA+LECTURER bound to a faculty other than the fixture's. */
    private Jwt foreignDean() {
        jdbc.update("INSERT INTO academic.\"Faculty\" (\"id\",\"name\",\"isActive\") "
                + "VALUES ('drf-faculty-foreign','Foreign faculty',TRUE)");
        jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"nameEn\",\"nameVi\",\"code\",\"facultyId\",\"isActive\") "
                + "VALUES ('drf-department-foreign','Foreign','Foreign','Foreign','FR','drf-faculty-foreign',TRUE)");
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                + "VALUES ('drf-user-fdean', 'drf-fdean@fixture.test', 'test-password', 'Drf', 'FDean', 'ACTIVE', "
                + "FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                + "VALUES ('drf-fdean','drf-user-fdean','drf-department-foreign','GV-drf-fdean',TRUE)");
        return Jwt.withTokenValue("drf-fixture").header("alg", "none")
                .subject("drf-user-fdean")
                .claim("roles", List.of("LECTURER", "TRUONG_KHOA"))
                .claim("lecturerId", "drf-fdean").build();
    }

    private UUID insertRound(String name, String status, Instant gvpbDeadline, Instant reportDate) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, "KLTN",
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
                        + "VALUES (?, ?, 'department-demo', ?, 'Drf fixture topic', 2, 'PUBLISHED', 'drf-admin')",
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
                        + "VALUES (?, ?, ?, 'ACTIVE', 'drf-admin')",
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
                        + "VALUES (?, ?, ?, 'drf-admin')",
                UUID.randomUUID(), councilId, topicId);
    }

    private void assignReviewerRow(UUID topicId, String lecturerId) {
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?", lecturerId, topicId);
    }

    private void submitDefense(UUID councilId, UUID topicId, String lecturerId, int score) {
        councils.submitScore(councilId, topicId, "DEFENSE", BigDecimal.valueOf(score), lecturer(lecturerId));
    }

    private void ensureLecturer(String lecturerId) {
        String userId = "drf-user-" + lecturerId;
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_auth.\"User\" WHERE \"id\" = ?", Integer.class, userId);
        if (users == null || users == 0) {
            jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", "
                            + "\"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                            + "VALUES (?, ?, 'test-password', 'Drf', 'Lecturer', 'ACTIVE', FALSE, FALSE, 0, "
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
}
