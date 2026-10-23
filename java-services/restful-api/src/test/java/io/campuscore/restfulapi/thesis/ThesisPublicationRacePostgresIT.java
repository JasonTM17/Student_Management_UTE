package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-PostgreSQL race coverage for the publication transition: a topic
 * withdraw and a result publish both serialize on the round row FOR UPDATE,
 * so whichever commits second must observe the first one's committed state
 * under real READ COMMITTED row locking — H2's lock emulation cannot prove
 * it.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other thesis
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database — real Flyway migrations run on context
 * startup. Optional {@code CAMPUSCORE_E2E_POSTGRES_USER} /
 * {@code CAMPUSCORE_E2E_POSTGRES_PASSWORD} (defaults
 * {@code campuscore}/{@code postgres}). Fixture rows use {@code pbr-}
 * prefixed markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisPublicationRacePostgresIT {

    private static final String WORKER_NAME = "pbr-worker";
    private static final String ROUND_NAME = "pbr-publication-race";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;
    private UUID topicId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void prepare() {
        Mockito.reset(namedJdbc);
        cleanFixtures();
        Instant now = Instant.now();
        roundId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, 'REGISTRATION_CLOSED')",
                roundId, ROUND_NAME,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                Timestamp.from(now.plusSeconds(86_400)), Timestamp.from(now.plusSeconds(5 * 86_400)));
        topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by, "
                        + "final_score, final_score_finalized_by, final_score_finalized_at, result_status) "
                        + "VALUES (?, ?, 'department-demo', 'Pbr Race Topic', 'fixture', 2, 'PUBLISHED', 'pbr-admin', "
                        + "8.00, 'pbr-chair', CURRENT_TIMESTAMP, 'GRADED')",
                topicId, roundId);
        // The V65 membership-state trigger validates APPROVED groups at commit:
        // land the roster first, then flip the approval afterwards.
        UUID groupId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 'pbr-leader', ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, topicId);
        for (int order = 1; order <= 3; order++) {
            String student = order == 1 ? "pbr-leader" : "pbr-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, student, order, order == 1);
        }
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED' WHERE id = ?",
                groupId);
    }

    @AfterEach
    void cleanFixtures() {
        // Scope by the round NAME so leftovers from an aborted run (whose
        // roundId differs from this test's) are still swept.
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'pbr-%'"
                        + " OR \"entityId\" IN (SELECT CAST(id AS text) FROM thesis.thesis_registration_round"
                        + " WHERE name = ?)"
                        + " OR \"entityId\" IN (SELECT CAST(t.id AS text) FROM thesis.thesis_topic t"
                        + " JOIN thesis.thesis_registration_round r ON r.id = t.round_id WHERE r.name = ?)",
                ROUND_NAME, ROUND_NAME);
        // Child rows before the round they reference. Groups first: members
        // cascade, and the deferred membership validator sees no group row at
        // commit so it does not reject the teardown.
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
    }

    @Test
    void publishCommittedFirstForcesTheConcurrentWithdrawToFail() throws Exception {
        // Schedule: the withdraw worker is paused right BEFORE its round FOR
        // UPDATE inside requireMutableRound — it has authorized but holds no
        // locks. Publish then commits the RESULTS_PUBLISHED transition; when
        // the worker resumes its locked read must see the terminal status.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    // Withdraw must target an ungraded topic; use a second
                    // graded-free topic so the graded one stays publishable.
                    UUID otherTopic = insertUngradedTopic();
                    return mutations.withdrawTopic(otherTopic, "concurrent withdraw", admin);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("withdraw paused before the round row lock")
                    .isTrue();
            mutations.publishResults(roundId, admin);
            assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("a withdraw that arrives after publication must lose")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("ROUND_STATE_CONFLICT");
            assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void withdrawCommittedFirstExcludesTheTopicFromThePublishRead() throws Exception {
        // Reverse schedule: publish is paused BEFORE its round FOR UPDATE, the
        // withdraw commits, then publish resumes and must read the committed
        // WITHDRAWN outcome under the lock — never a stale pre-withdraw state.
        Jwt admin = admin();
        UUID otherTopic = insertUngradedTopic();
        UUID otherGroup = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, 'pbr-leader-2', ?, 'SUBMITTED', 'PENDING')",
                otherGroup, roundId, otherTopic);
        for (int order = 1; order <= 3; order++) {
            String student = order == 1 ? "pbr-leader-2" : "pbr-b-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), otherGroup, roundId, student, order, order == 1);
        }
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED' WHERE id = ?",
                otherGroup);
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    return mutations.publishResults(roundId, null, admin);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("publish paused before the round row lock")
                    .isTrue();
            mutations.withdrawTopic(otherTopic, "withdrawn before publish", admin);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            // The withdraw won the lock first: publish then reads the topic as
            // excluded. It still publishes the graded topic, and names the
            // withdrawn one in the exclusions audit — never stranded.
            assertThat(outcome).isNotInstanceOf(DomainException.class);
            assertThat(roundStatus()).isEqualTo("RESULTS_PUBLISHED");
            Integer exclusionAudit = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                            + "WHERE \"entityId\" = ? AND \"action\" = 'THESIS_RESULTS_PUBLISHED_WITH_EXCLUSIONS'",
                    Integer.class, roundId.toString());
            assertThat(exclusionAudit).isEqualTo(1);
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Pauses the named worker immediately before any thesis_registration_round
     * FOR UPDATE read — the same interception the other race ITs use, valid
     * because both transitions open with that lock. The worker's transaction
     * is authorized but holds no locks when it parks, so the main thread can
     * commit its transition without a deadlock.
     */
    private void pauseWorkerBeforeRoundLock(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            if (WORKER_NAME.equals(Thread.currentThread().getName())
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                paused.countDown();
                if (!resume.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("race barrier timeout");
                }
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    private UUID insertUngradedTopic() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Pbr Concurrent Topic', 'fixture', 1, 'PUBLISHED', 'pbr-admin')",
                id, roundId);
        return id;
    }

    private String roundStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("publication-race-fixture").header("alg", "none")
                .subject("pbr-admin").claim("roles", List.of("ADMIN")).build();
    }
}
