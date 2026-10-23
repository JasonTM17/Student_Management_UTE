package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest;
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
 * Real-PostgreSQL proofs for the roster override that H2 cannot host: the V141
 * deferred size-exception validator only raises at COMMIT on a real engine,
 * and the group->round lock order only means something under real row locks.
 *
 * <p><b>Environment contract</b> (opt-in, same convention as
 * {@code ThesisRosterOverrideRacePostgresIT}): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database. Fixture rows use {@code rlo-} prefixed
 * markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRosterLockOrderPostgresIT {

    private static final String WORKER_NAME = "rlo-worker";
    private static final String APPROVER_NAME = "rlo-approver";
    private static final String ROUND_NAME = "rlo-lock-order";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;

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
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'rlo-%'"
                        + " OR \"entityId\" IN (SELECT CAST(g.id AS text) FROM thesis.thesis_group g"
                        + " JOIN thesis.thesis_registration_round r ON r.id = g.round_id WHERE r.name = ?)",
                ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rlo-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rlo-user-%'");
    }

    /**
     * A documented exception on a PENDING group used to write size_exception_*
     * unconditionally; V141's deferred validator then aborted the COMMIT
     * because a non-approved group may not carry the marker. The exception now
     * lives on the audit row alone until the group is approved.
     */
    @Test
    void pendingGroupAcceptsDocumentedSizeExceptionWithoutTheMarker() {
        UUID topicId = insertTopic("Rlo Pending Topic");
        UUID groupId = insertGroup(topicId, "rlo-p", 1);
        String addedStudent = "rlo-added-student";
        ensureStudent(addedStudent);

        mutations.overrideGroupRoster(groupId,
                new RosterOverrideRequest(addedStudent, "documented below-minimum exception", true),
                admin());

        Integer members = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id = ?",
                Integer.class, groupId);
        assertThat(members).isEqualTo(2);
        String marker = jdbc.queryForObject(
                "SELECT size_exception_reason FROM thesis.thesis_group WHERE id = ?",
                String.class, groupId);
        assertThat(marker)
                .as("a non-approved group keeps the exception on the audit row, never the column")
                .isNull();
        Integer auditRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = ? AND \"action\" = 'THESIS_GROUP_ROSTER_OVERRIDE'",
                Integer.class, groupId.toString());
        assertThat(auditRows).isEqualTo(1);
    }

    /**
     * Lock-order proof: the override used to take round -> group while
     * approveGroup takes group -> round — a guaranteed deadlock when the two
     * paths interleave on one group. Two barriers make the schedule
     * deterministic: the override parks immediately before its group FOR
     * UPDATE (under the old order already holding the round; under the fixed
     * order holding nothing), and the approver parks immediately before its
     * round FOR UPDATE holding the group. Releasing both at once leaves each
     * side wanting the other's row under the old order — Postgres aborts one
     * with 40P01 — while under group -> round the parked worker holds nothing,
     * the approver commits first, and the worker follows.
     */
    @Test
    void overridePausedBeforeGroupLockSerializesWithApproveWithoutDeadlock() throws Exception {
        UUID topicId = insertTopic("Rlo Deadlock Topic");
        UUID groupId = insertGroup(topicId, "rlo-d", 3);
        ensureStudent("rlo-lock-student");
        Jwt admin = admin();

        CountDownLatch workerPaused = new CountDownLatch(1);
        CountDownLatch workerResume = new CountDownLatch(1);
        CountDownLatch approverPaused = new CountDownLatch(1);
        CountDownLatch approverResume = new CountDownLatch(1);
        barrierAtLocks(workerPaused, workerResume, approverPaused, approverResume);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.overrideGroupRoster(groupId,
                            new RosterOverrideRequest("rlo-lock-student", "post-close seat", null),
                            admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(workerPaused.await(10, TimeUnit.SECONDS))
                    .as("override parked before its group lock")
                    .isTrue();
            var approver = pool.submit(() -> {
                Thread.currentThread().setName(APPROVER_NAME);
                try {
                    mutations.approveGroup(groupId, admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(approverPaused.await(10, TimeUnit.SECONDS))
                    .as("approver holds the group, parked before the round lock")
                    .isTrue();
            // Release both: under round -> group each side now wants the row
            // the other holds (40P01); under group -> round the worker held
            // nothing and serializes behind the finished approver.
            workerResume.countDown();
            approverResume.countDown();
            Object overrideOutcome = worker.get(30, TimeUnit.SECONDS);
            Object approveOutcome = approver.get(30, TimeUnit.SECONDS);
            assertThat(overrideOutcome).as("override completes: %s", overrideOutcome).isEqualTo("OK");
            assertThat(approveOutcome).as("approve completes: %s", approveOutcome).isEqualTo("OK");
        } finally {
            workerResume.countDown();
            approverResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Parks each named thread immediately before one lock acquisition, so the
     * locks it already holds at that instant are exactly the order under
     * test: the override before {@code thesis_group ... FOR UPDATE} and the
     * approver before {@code thesis_registration_round ... FOR UPDATE}.
     * Member-row locks ({@code thesis_group_member}) are never intercepted.
     */
    private void barrierAtLocks(CountDownLatch workerPaused, CountDownLatch workerResume,
            CountDownLatch approverPaused, CountDownLatch approverResume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            String thread = Thread.currentThread().getName();
            if (WORKER_NAME.equals(thread)
                    && sql.contains("FROM thesis.thesis_group WHERE id") && sql.contains("FOR UPDATE")) {
                await(workerPaused, workerResume);
            } else if (APPROVER_NAME.equals(thread)
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                await(approverPaused, approverResume);
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    private static void await(CountDownLatch paused, CountDownLatch resume) throws InterruptedException {
        paused.countDown();
        if (!resume.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("race barrier timeout");
        }
    }

    private UUID insertTopic(String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'fixture', 2, 'PUBLISHED', 'rlo-admin')",
                id, roundId, title);
        return id;
    }

    private UUID insertGroup(UUID topicId, String tag, int memberCount) {
        UUID groupId = UUID.randomUUID();
        String leader = tag + "-leader";
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, leader, topicId);
        for (int order = 1; order <= memberCount; order++) {
            String student = order == 1 ? leader : tag + "-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, student, order, order == 1);
        }
        return groupId;
    }

    private void ensureStudent(String studentId) {
        String userId = "rlo-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + studentId + "@test.invalid','test','Lock','Order','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\") "
                        + "VALUES (?,?,'RLO-1','curriculum-demo',2,CURRENT_TIMESTAMP)",
                studentId, userId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("roster-lock-fixture").header("alg", "none")
                .subject("rlo-admin").claim("roles", List.of("ADMIN")).build();
    }
}
