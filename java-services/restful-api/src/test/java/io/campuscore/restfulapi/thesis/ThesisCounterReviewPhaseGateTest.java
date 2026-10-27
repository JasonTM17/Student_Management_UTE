package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.exception.DomainException;
import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Round types without a counter-review phase (MON_HOC, NCKH) reject the
 * reviewer assignment, the waiver of a requirement that does not exist, and
 * both deadline escape hatches with a stable 409 business code — not the
 * generic VALIDATION_ERROR that told the operator to "check the fields".
 * The waiver gate needs the capability override on so the nothing-to-waive
 * refusal past it is observable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:thesis_gvpb_phase_gate;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "thesis.governance.counter-review-waiver.enabled=true"
})
class ThesisCounterReviewPhaseGateTest {

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
        jdbc.update("DELETE FROM thesis.thesis_topic_score");
        jdbc.update("DELETE FROM thesis.thesis_council_topic");
        jdbc.update("DELETE FROM thesis.thesis_council_member");
        jdbc.update("DELETE FROM thesis.thesis_council");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" LIKE 'pg-%' OR \"actorId\" LIKE 'pg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'pg-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'pg-%'");
    }

    @Test
    void assignReviewerNamesTheMissingCounterReviewPhase() {
        UUID roundId = insertRound("Pg Mon Hoc Round", "MON_HOC", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "Pg Mon Hoc Topic");

        assertThatThrownBy(() -> councils.assignReviewer(topicId, "pg-reviewer", admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException refused = (DomainException) failure;
                    assertThat(refused.code()).isEqualTo("ROUND_HAS_NO_COUNTER_REVIEW");
                    assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_lecturer_id FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isNull();
    }

    @Test
    void assignReviewerEndpointAnswersTheStableConflictCode() throws Exception {
        UUID roundId = insertRound("Pg Api Round", "NCKH", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "Pg Api Topic");

        mvc.perform(post("/api/v1/thesis/topics/{id}/reviewer", topicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lecturerId\":\"pg-reviewer\"}")
                        .with(ThesisAuthorityFixture.role(jdbc, "pg-admin", "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_HAS_NO_COUNTER_REVIEW"));
    }

    @Test
    void waiverOfANonexistentCounterReviewNamesTheMissingRequirement() {
        UUID roundId = insertRound("Pg Waive Round", "MON_HOC", "REGISTRATION_CLOSED");
        UUID topicId = insertTopic(roundId, "Pg Waive Topic");

        assertThatThrownBy(() -> councils.waiveCounterReview(topicId, "nothing to waive", admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> {
                    DomainException refused = (DomainException) failure;
                    assertThat(refused.code()).isEqualTo("TOPIC_HAS_NO_COUNTER_REVIEW");
                    assertThat(refused.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(jdbc.queryForObject(
                "SELECT gvpb_waived_reason FROM thesis.thesis_topic WHERE id = ?",
                String.class, topicId)).isNull();
        assertThat(auditCount(topicId.toString(), "GVPB_WAIVED")).isZero();
    }

    @Test
    void deadlineEscapeHatchesNameTheMissingCounterReviewPhase() {
        UUID closed = insertRound("Pg Closed Round", "MON_HOC", "REGISTRATION_CLOSED");
        assertThatThrownBy(() -> mutations.extendReportDeadline(
                closed, Instant.now().plusSeconds(86_400), admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("ROUND_HAS_NO_COUNTER_REVIEW"));

        UUID live = insertRound("Pg Live Round", "NCKH", "REGISTRATION_OPEN");
        assertThatThrownBy(() -> mutations.extendGvpbDeadline(
                live, Instant.now().plusSeconds(86_400), "keep the window open", admin()))
                .isInstanceOf(DomainException.class)
                .satisfies(failure -> assertThat(((DomainException) failure).code())
                        .isEqualTo("ROUND_HAS_NO_COUNTER_REVIEW"));
    }

    // ---------- fixtures ----------

    private Jwt admin() {
        return Jwt.withTokenValue("phase-gate-fixture").header("alg", "none")
                .subject("pg-admin").claim("roles", List.of("ADMIN")).build();
    }

    private UUID insertRound(String name, String thesisType, String status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, name, thesisType,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                status);
        return id;
    }

    private UUID insertTopic(UUID roundId, String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'Phase gate fixture topic', 2, 'PUBLISHED', 'pg-admin')",
                id, roundId, title);
        return id;
    }

    private int auditCount(String entityId, String action) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = ?",
                Integer.class, entityId, action);
        return count == null ? 0 : count;
    }
}
