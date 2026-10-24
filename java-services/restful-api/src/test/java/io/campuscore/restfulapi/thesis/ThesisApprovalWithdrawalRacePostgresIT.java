package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupApprovalWithdrawalRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.GroupRejectionRequest;
import io.campuscore.restfulapi.exception.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
 * Real-PostgreSQL race coverage for withdraw-approval: the mutation takes the
 * group row FOR UPDATE, then the round row FOR UPDATE — the same order as
 * approve/reject — so a concurrent lifecycle write serializes on one of the
 * two rows and the loser re-reads state instead of producing a torn
 * APPROVED/PENDING split. H2's lock emulation cannot prove the ordering.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other thesis
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database. Fixture rows use {@code gaw-} prefixed
 * markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisApprovalWithdrawalRacePostgresIT {

    private static final String WORKER_NAME = "gaw-worker";
    private static final String ROUND_NAME = "gaw-withdrawal-race";
    private static final String SUPERVISOR = "gaw-supervisor";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;
    private UUID topicId;
    private UUID groupId;

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
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, ROUND_NAME,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(1 * 86_400)), Timestamp.from(now.plusSeconds(2 * 86_400)),
                Timestamp.from(now.plusSeconds(5 * 86_400)), Timestamp.from(now.plusSeconds(10 * 86_400)));
        topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Gaw Topic', 'fixture', 2, 'PUBLISHED', 'gaw-admin')",
                topicId, roundId);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                + " VALUES (?, ?, ?, 1)", UUID.randomUUID(), topicId, SUPERVISOR);
        groupId = insertApprovedGroup(topicId, "gaw-a");
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'gaw-%'"
                        + " OR \"entityId\" IN (SELECT CAST(g.id AS text) FROM thesis.thesis_group g"
                        + " JOIN thesis.thesis_registration_round r ON r.id = g.round_id WHERE r.name = ?)",
                ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_council WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
    }

    @Test
    void doubleWithdrawalSerializesOnTheGroupRowExactlyOnce() throws Exception {
        // The worker pauses BEFORE its group FOR UPDATE; the main thread's
        // withdrawal commits first, so the worker must observe PENDING and
        // lose with the stable state-conflict code — never a double audit.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeGroupLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.withdrawGroupApproval(groupId,
                            new GroupApprovalWithdrawalRequest("duplicate withdrawal"), admin);
                    return "OK";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("second withdrawal parked before the group row lock")
                    .isTrue();
            mutations.withdrawGroupApproval(groupId,
                    new GroupApprovalWithdrawalRequest("mistaken approval"), admin);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("the serialized second withdrawal must hit the state guard")
                    .isEqualTo("GROUP_APPROVAL_STATE_CONFLICT");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                            + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_APPROVAL_WITHDRAWN'",
                    Integer.class, groupId.toString())).isEqualTo(1);
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void withdrawCommittedFirstLetsTheQueuedRejectDecideThePendingGroup() throws Exception {
        // Reject parks before the group lock; the withdrawal commits first.
        // The reject then re-reads the row, sees a legal PENDING group, and
        // completes — APPROVED -> PENDING -> REJECTED with no torn state.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeGroupLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.rejectGroup(groupId, new GroupRejectionRequest("topic quality"), admin);
                    return "OK";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("reject parked before the group row lock")
                    .isTrue();
            mutations.withdrawGroupApproval(groupId,
                    new GroupApprovalWithdrawalRequest("premature approval"), admin);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("reject serialized after the withdrawal and decided the pending group")
                    .isEqualTo("OK");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("REJECTED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void withdrawRefusesWhenACouncilAssignmentLandsMidTransaction() throws Exception {
        // The worker parks BEFORE its round lock while holding the group row.
        // A council-topic row for the bound topic commits meanwhile; when the
        // worker resumes, its blocker count must see the committed assignment
        // and refuse with the per-kind details.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.withdrawGroupApproval(groupId,
                            new GroupApprovalWithdrawalRequest("mistaken approval"), admin);
                    return "OK";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("withdrawal parked before the round row lock")
                    .isTrue();
            UUID councilId = UUID.randomUUID();
            jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, created_by)"
                            + " VALUES (?, ?, 'Gaw council', 'gaw-admin')", councilId, roundId);
            jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by)"
                            + " VALUES (?, ?, ?, 'gaw-admin')", UUID.randomUUID(), councilId, topicId);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome.toString()).startsWith("GROUP_WITHDRAWAL_BLOCKED");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("APPROVED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void withdrawRefusesOnceTheRoundLeavesTheReviewWindow() throws Exception {
        // The worker parks BEFORE its round lock; a concurrent round-close
        // transition (RESULTS_PUBLISHED) commits meanwhile. On resume the
        // worker re-reads the round status under the lock and refuses.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.withdrawGroupApproval(groupId,
                            new GroupApprovalWithdrawalRequest("mistaken approval"), admin);
                    return "OK";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("withdrawal parked before the round row lock")
                    .isTrue();
            jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED'"
                    + " WHERE id = ?", roundId);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome).isEqualTo("GROUP_APPROVAL_STATE_CONFLICT");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("APPROVED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void approveOfASiblingGroupSerializesOnTheRoundRowWithoutDeadlock() throws Exception {
        // Approve parks before its round lock holding a different group's row;
        // the withdrawal commits first, the approve then proceeds — same lock
        // order means neither transaction waits on the other's group row.
        Jwt admin = admin();
        UUID pendingTopic = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', 'Gaw Pending Topic', 'fixture', 2, 'PUBLISHED', 'gaw-admin')",
                pendingTopic, roundId);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order)"
                + " VALUES (?, ?, ?, 1)", UUID.randomUUID(), pendingTopic, SUPERVISOR);
        UUID pendingGroup = insertPendingGroup(pendingTopic, "gaw-b");
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.approveGroup(pendingGroup,
                            new GroupApprovalRequest(null, "governance approval"), admin);
                    return "OK";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("approve parked before the round row lock")
                    .isTrue();
            mutations.withdrawGroupApproval(groupId,
                    new GroupApprovalWithdrawalRequest("mistaken approval"), admin);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome).isEqualTo("OK");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, groupId)).isEqualTo("PENDING");
            assertThat(jdbc.queryForObject(
                    "SELECT approval_status FROM thesis.thesis_group WHERE id = ?",
                    String.class, pendingGroup)).isEqualTo("APPROVED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Parks the worker immediately before the group's FOR UPDATE read. */
    private void pauseWorkerBeforeGroupLock(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            if (WORKER_NAME.equals(Thread.currentThread().getName())
                    && sql.contains("thesis_group") && sql.contains("FOR UPDATE")
                    && !sql.contains("thesis_group_report") && !sql.contains("thesis_group_member")) {
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

    /** Parks the worker immediately before the round's FOR UPDATE read. */
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

    private UUID insertApprovedGroup(UUID topic, String tag) {
        UUID id = insertGroup(topic, tag, "PENDING");
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED',"
                + " approved_by = 'gaw-prior-admin', approved_at = CURRENT_TIMESTAMP WHERE id = ?", id);
        return id;
    }

    private UUID insertPendingGroup(UUID topic, String tag) {
        return insertGroup(topic, tag, "PENDING");
    }

    private UUID insertGroup(UUID topic, String tag, String approvalStatus) {
        UUID id = UUID.randomUUID();
        String leader = tag + "-leader";
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', ?)",
                id, roundId, leader, topic, approvalStatus);
        for (int order = 1; order <= 3; order++) {
            String student = order == 1 ? leader : tag + "-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), id, roundId, student, order, order == 1);
        }
        return id;
    }

    private Jwt admin() {
        return Jwt.withTokenValue("withdrawal-race-fixture").header("alg", "none")
                .subject("gaw-admin").claim("roles", List.of("ADMIN")).build();
    }
}
