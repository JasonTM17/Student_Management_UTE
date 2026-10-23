package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.RosterOverrideRequest;
import io.campuscore.restfulapi.web.DomainException;
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
 * Real-PostgreSQL race coverage for the post-close roster override: a member
 * REMOVE from one group and an ADD of the same student into another group of
 * the same round both serialize on the round row FOR UPDATE, so the second
 * committer must observe the first one's committed membership — never a
 * duplicate seat and never a stale "already in a group" read. H2's lock
 * emulation cannot prove the ordering.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other thesis
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database — real Flyway migrations run on context
 * startup. Optional {@code CAMPUSCORE_E2E_POSTGRES_USER} /
 * {@code CAMPUSCORE_E2E_POSTGRES_PASSWORD} (defaults
 * {@code campuscore}/{@code postgres}). Fixture rows use {@code ror-}
 * prefixed markers and are deleted after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRosterOverrideRacePostgresIT {

    private static final String WORKER_NAME = "ror-worker";
    private static final String ROUND_NAME = "ror-roster-race";
    private static final String SHARED_STUDENT = "ror-shared";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;
    private UUID sourceGroupId;
    private UUID targetGroupId;

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
        UUID topicA = insertTopic("Ror Race Topic A");
        UUID topicB = insertTopic("Ror Race Topic B");
        // The shared student sits as a non-leader member of the source group;
        // the target group has a free slot. Four members on the source keeps
        // it at the approved minimum after the removal.
        sourceGroupId = insertApprovedGroup(topicA, "ror-a", SHARED_STUDENT);
        targetGroupId = insertApprovedGroup(topicB, "ror-b", null);
        ensureStudent(SHARED_STUDENT);
    }

    @AfterEach
    void cleanFixtures() {
        // Scope by the round NAME so leftovers from an aborted run (whose
        // roundId differs from this test's) are still swept.
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'ror-%'"
                        + " OR \"entityId\" IN (SELECT CAST(g.id AS text) FROM thesis.thesis_group g"
                        + " JOIN thesis.thesis_registration_round r ON r.id = g.round_id WHERE r.name = ?)",
                ROUND_NAME);
        // Groups first: members cascade, and the deferred membership validator
        // sees no group row at commit so it does not reject the teardown.
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'ror-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'ror-user-%'");
    }

    @Test
    void removeCommittedFirstLetsTheConcurrentAddReseatTheStudent() throws Exception {
        // Schedule: the add worker pauses right BEFORE its round FOR UPDATE —
        // authorized but holding no locks. The remove commits first, so the
        // worker's membership check must read the freed seat and succeed.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.overrideGroupRoster(targetGroupId,
                            new RosterOverrideRequest(SHARED_STUDENT, "re-seated", null), admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("add paused before the round row lock")
                    .isTrue();
            mutations.overrideGroupRoster(sourceGroupId,
                    new RosterOverrideRequest(SHARED_STUDENT, "REMOVE", "departed", null, null), admin);
            assertThat(seatCount()).isZero();
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("the add must see the freed seat once the remove commits")
                    .isEqualTo("OK");
            assertThat(seatCount()).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT group_id FROM thesis.thesis_group_member "
                            + "WHERE round_id = ? AND student_id = ?",
                    String.class, roundId, SHARED_STUDENT))
                    .isEqualTo(targetGroupId.toString());
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void addCommittedWhileTheSeatIsHeldFailsAndTheRemoveStillLands() throws Exception {
        // Reverse schedule: the remove pauses before its round lock, the add
        // runs first and must lose — the student still holds a seat in the
        // source group, so the unique-per-round membership rule fires.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    mutations.overrideGroupRoster(sourceGroupId,
                            new RosterOverrideRequest(SHARED_STUDENT, "REMOVE", "departed", null, null), admin);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("remove paused before the round row lock")
                    .isTrue();
            try {
                mutations.overrideGroupRoster(targetGroupId,
                        new RosterOverrideRequest(SHARED_STUDENT, "re-seated", null), admin);
                throw new AssertionError("the add should refuse while the seat is still held");
            } catch (DomainException expected) {
                assertThat(expected.code()).isEqualTo("STUDENT_ALREADY_IN_GROUP");
            }
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome).isEqualTo("OK");
            // Remove committed second: the seat is gone everywhere in the round.
            assertThat(seatCount()).isZero();
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Pauses the named worker immediately before any thesis_registration_round
     * FOR UPDATE read — the same interception the publication race IT uses,
     * valid because the override opens with that lock on both actions. The
     * worker's transaction is authorized but holds no locks when it parks, so
     * the main thread can commit without a deadlock.
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

    private int seatCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_group_member WHERE round_id = ? AND student_id = ?",
                Integer.class, roundId, SHARED_STUDENT);
        return count == null ? 0 : count;
    }

    private UUID insertTopic(String title) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic "
                        + "(id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'fixture', 2, 'PUBLISHED', 'ror-admin')",
                id, roundId, title);
        return id;
    }

    /**
     * The V65 membership-state trigger validates APPROVED groups at commit:
     * land the roster first, then flip the approval afterwards. {@code
     * extraMember} joins as a fourth, non-leader member.
     */
    private UUID insertApprovedGroup(UUID topicId, String tag, String extraMember) {
        UUID groupId = UUID.randomUUID();
        String leader = tag + "-leader";
        jdbc.update("INSERT INTO thesis.thesis_group "
                        + "(id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, leader, topicId);
        for (int order = 1; order <= 3; order++) {
            String student = order == 1 ? leader : tag + "-student-" + order;
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId, student, order, order == 1);
        }
        if (extraMember != null) {
            jdbc.update("INSERT INTO thesis.thesis_group_member "
                            + "(id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, 4, FALSE)",
                    UUID.randomUUID(), groupId, roundId, extraMember);
        }
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED' WHERE id = ?",
                groupId);
        return groupId;
    }

    private void ensureStudent(String studentId) {
        String userId = "ror-user-" + studentId;
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\","
                        + "\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?,'" + studentId + "@test.invalid','test','Roster','Race','ACTIVE',FALSE,FALSE,0,"
                        + "CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                userId);
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\") "
                        + "VALUES (?,?,'ROR-1','curriculum-demo',2,CURRENT_TIMESTAMP)",
                studentId, userId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("roster-race-fixture").header("alg", "none")
                .subject("ror-admin").claim("roles", List.of("ADMIN")).build();
    }
}
