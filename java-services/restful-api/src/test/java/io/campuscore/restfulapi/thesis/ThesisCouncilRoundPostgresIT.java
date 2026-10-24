package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.math.BigDecimal;
import java.net.URI;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisCouncilRoundPostgresIT {
    private static final int WAIT_SECONDS = 15;

    @Autowired private ThesisCouncilService councils;
    @Autowired private ThesisMutationService mutations;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_COUNCIL_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_COUNCIL_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void defenseScoreBlocksOnTheUncommittedCancelThenConflicts() throws Exception {
        Fixture fx = fixture("t1");
        assertCancelBlocksWriter(fx.roundId(), () -> {
            councils.submitScore(fx.councilId(), fx.topicId(), "DEFENSE", new BigDecimal("7"), memberJwt(fx.member()));
            return null;
        });
        assertThat(count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?", fx.topicId())).isZero();
        assertThat(jdbc.queryForObject("SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                Object.class, fx.topicId())).isNull();
        assertThat(count("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?",
                fx.topicId().toString())).isZero();
    }

    @Test
    void reviewerScoreBlocksOnTheUncommittedCancelThenConflicts() throws Exception {
        Fixture fx = fixture("t2");
        jdbc.update("UPDATE thesis.thesis_registration_round SET gvpb_deadline = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(86_400)), fx.roundId());
        jdbc.update("UPDATE thesis.thesis_topic SET gvpb_lecturer_id = ? WHERE id = ?",
                fx.reviewer(), fx.topicId());
        int baseline = auditCount(fx.topicId().toString());
        assertCancelBlocksWriter(fx.roundId(), () -> {
            councils.submitReviewerScore(fx.topicId(), new BigDecimal("7"), null, reviewerJwt(fx.reviewer()));
            return null;
        });
        assertThat(count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ? AND component = 'GVPB'",
                fx.topicId())).isZero();
        assertThat(auditCount(fx.topicId().toString())).isEqualTo(baseline);
    }

    @Test
    void finalizeBlocksOnTheUncommittedCancelThenConflicts() throws Exception {
        Fixture fx = fixture("t3");
        insertDefenseScore(fx, fx.chair(), new BigDecimal("7"));
        insertDefenseScore(fx, fx.secretary(), new BigDecimal("8"));
        insertDefenseScore(fx, fx.member(), new BigDecimal("9"));
        assertCancelBlocksWriter(fx.roundId(), () -> {
            councils.finalizeScores(fx.councilId(), fx.topicId(), chairJwt(fx.chair()));
            return null;
        });
        assertThat(count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?", fx.topicId()))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT final_score FROM thesis.thesis_topic WHERE id = ?",
                Object.class, fx.topicId())).isNull();
    }

    @Test
    void anInFlightScoreOwnsTheRoundLockAndBlocksTheCancellation() throws Exception {
        Fixture fx = fixture("t4");
        CountDownLatch writerHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseWriter = new CountDownLatch(1);
        CountDownLatch cancelAtUpdate = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        AtomicInteger cancelPid = new AtomicInteger();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().startsWith("pgw-t4")
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                Object result = invocation.callRealMethod();
                writerPid.set(backendPid());
                writerHoldsLock.countDown();
                if (!releaseWriter.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("writer release barrier timeout");
                }
                return result;
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().startsWith("pgc-t4")
                    && sql.contains("thesis_registration_round") && sql.contains("'CANCELLED'")) {
                cancelPid.set(backendPid());
                cancelAtUpdate.countDown();
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> writer = pool.submit(() -> {
                Thread.currentThread().setName("pgw-t4");
                try {
                    councils.submitScore(fx.councilId(), fx.topicId(), "DEFENSE", new BigDecimal("7"), memberJwt(fx.member()));
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            assertThat(writerHoldsLock.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the score transaction must hold the round row lock").isTrue();

            Future<?> canceller = pool.submit(() -> {
                Thread.currentThread().setName("pgc-t4");
                mutations.cancelRound(fx.roundId(), governanceJwt());
                return null;
            });
            assertThat(cancelAtUpdate.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the cancellation must reach its round UPDATE").isTrue();
            assertEventuallyBlocked(cancelPid.get(), writerPid.get(),
                    "the cancellation must wait on the in-flight score transaction");

            releaseWriter.countDown();
            assertThat(writer.get(WAIT_SECONDS, TimeUnit.SECONDS)).isNull();
            canceller.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(count("SELECT COUNT(*) FROM thesis.thesis_topic_score WHERE topic_id = ?", fx.topicId()))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_registration_round WHERE id = ?",
                    String.class, fx.roundId())).isEqualTo("CANCELLED");
        } finally {
            releaseWriter.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void finalizeIsSingleShotAndTerminalRoundsStillRefuseCancellation() {
        Fixture fx = fixture("t5");
        insertDefenseScore(fx, fx.chair(), new BigDecimal("8"));
        insertDefenseScore(fx, fx.secretary(), new BigDecimal("9"));
        insertDefenseScore(fx, fx.member(), new BigDecimal("10"));
        insertCounterReviewScore(fx, fx.reviewer(), new BigDecimal("8.5"));

        ThesisCouncilService.TopicResult result = councils.finalizeScores(fx.councilId(), fx.topicId(), chairJwt(fx.chair()));
        assertThat(result.finalScore()).isEqualByComparingTo(new BigDecimal("9.00"));
        assertThat(result.status()).isEqualTo("GRADED");
        assertThat(jdbc.queryForObject("SELECT result_status FROM thesis.thesis_topic WHERE id = ?",
                String.class, fx.topicId())).isEqualTo("GRADED");
        assertThat(count("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? AND \"action\" = 'COUNCIL_SCORE_FINALIZED'",
                fx.topicId().toString())).isEqualTo(1);

        assertThatThrownBy(() -> councils.finalizeScores(fx.councilId(), fx.topicId(), chairJwt(fx.chair())))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).code()).isEqualTo("SCORE_ALREADY_FINALIZED"));

        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'RESULTS_PUBLISHED' WHERE id = ?", fx.roundId());
        assertThatThrownBy(() -> mutations.cancelRound(fx.roundId(), governanceJwt()))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> assertThat(((DomainException) error).code()).isEqualTo("ROUND_STATE_CONFLICT"));
        assertThat(jdbc.queryForObject("SELECT status FROM thesis.thesis_registration_round WHERE id = ?",
                String.class, fx.roundId())).isEqualTo("RESULTS_PUBLISHED");
    }

    private void assertCancelBlocksWriter(UUID roundId, Callable<Void> writerCall) throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String cancelThread = "pgc-" + tag;
        String writerThread = "pgw-" + tag;
        CountDownLatch cancellerLocked = new CountDownLatch(1);
        CountDownLatch commitCancel = new CountDownLatch(1);
        CountDownLatch writerAtRoundLock = new CountDownLatch(1);
        AtomicInteger cancellerPid = new AtomicInteger();
        AtomicInteger writerPid = new AtomicInteger();

        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().equals(cancelThread)
                    && sql.contains("thesis_registration_round") && sql.contains("'CANCELLED'")) {
                cancellerPid.set(backendPid());
                Object result = invocation.callRealMethod();
                cancellerLocked.countDown();
                if (!commitCancel.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("cancel commit barrier timeout");
                }
                return result;
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().equals(writerThread)
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                writerPid.set(backendPid());
                writerAtRoundLock.countDown();
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> canceller = pool.submit(() -> {
                Thread.currentThread().setName(cancelThread);
                mutations.cancelRound(roundId, governanceJwt());
                return null;
            });
            assertThat(cancellerLocked.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the cancellation must hold the uncommitted round row lock").isTrue();

            Future<Throwable> writer = pool.submit(() -> {
                Thread.currentThread().setName(writerThread);
                try {
                    writerCall.call();
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            assertThat(writerAtRoundLock.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the writer must reach the round FOR UPDATE query").isTrue();
            assertEventuallyBlocked(writerPid.get(), cancellerPid.get(),
                    "pg_blocking_pids must show the writer blocked by the canceller backend");

            commitCancel.countDown();
            canceller.get(WAIT_SECONDS, TimeUnit.SECONDS);
            Throwable error = writer.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).isInstanceOf(DomainException.class);
            assertThat(((DomainException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(((DomainException) error).code()).isEqualTo("ROUND_STATE_CONFLICT");
        } finally {
            commitCancel.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("no transaction may be left dangling").isTrue();
        }
    }

    private int backendPid() {
        Integer pid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
        return pid == null ? -1 : pid;
    }

    private void assertEventuallyBlocked(int waiterPid, int expectedBlocker, String because)
            throws InterruptedException {
        assertThat(waiterPid).as("waiter backend pid captured").isPositive();
        assertThat(expectedBlocker).as("blocker backend pid captured").isPositive();
        for (int attempt = 0; attempt < 100; attempt++) {
            Boolean blocked = jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM unnest(pg_blocking_pids(?)) b WHERE b = ?)",
                    Boolean.class, waiterPid, expectedBlocker);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(because + " (waiter pid " + waiterPid + ", expected blocker pid " + expectedBlocker + ")");
    }

    private void insertDefenseScore(Fixture fx, String lecturerId, BigDecimal score) {
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score) "
                        + "VALUES (?, ?, ?, ?, 'DEFENSE', ?)",
                UUID.randomUUID(), fx.topicId(), fx.councilId(), lecturerId, score);
    }

    private void insertCounterReviewScore(Fixture fx, String lecturerId, BigDecimal score) {
        jdbc.update("INSERT INTO thesis.thesis_topic_score (id, topic_id, council_id, lecturer_id, component, score) "
                        + "VALUES (?, ?, ?, ?, 'GVPB', ?)",
                UUID.randomUUID(), fx.topicId(), fx.councilId(), lecturerId, score);
    }

    private Fixture fixture(String tag) {
        String suffix = UUID.randomUUID().toString().substring(0, 12);
        String deanUser = "tcr-" + tag + "-" + suffix + "-dean";
        String supervisor = "tcr-" + tag + "-" + suffix + "-sup";
        String chair = "tcr-" + tag + "-" + suffix + "-chair";
        String secretary = "tcr-" + tag + "-" + suffix + "-sec";
        String member = "tcr-" + tag + "-" + suffix + "-member";
        String reviewer = "tcr-" + tag + "-" + suffix + "-gvpb";
        insertUser(deanUser, deanUser + "@test.invalid");
        insertLecturer(supervisor);
        insertLecturer(chair);
        insertLecturer(secretary);
        insertLecturer(member);
        insertLecturer(reviewer);

        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, 'REGISTRATION_CLOSED')",
                roundId, "Council race " + tag,
                Timestamp.from(now.minusSeconds(14_400)), Timestamp.from(now.minusSeconds(10_800)),
                Timestamp.from(now.minusSeconds(10_800)), Timestamp.from(now.minusSeconds(3_600)),
                Timestamp.from(now.plusSeconds(172_800)));

        UUID councilId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_council (id, round_id, name, status, created_by) VALUES (?, ?, ?, 'ACTIVE', ?)",
                councilId, roundId, "Council " + tag, deanUser);
        seat(councilId, chair, "CHAIR");
        seat(councilId, secretary, "SECRETARY");
        seat(councilId, member, "MEMBER");

        UUID topicId = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id, round_id, department_id, title, description, max_groups, status, created_by) "
                        + "VALUES (?, ?, 'department-demo', ?, 'council race fixture', 1, 'PUBLISHED', ?)",
                topicId, roundId, "Council race topic " + tag, userOf(supervisor));
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id, topic_id, lecturer_id, supervisor_order) VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), topicId, supervisor);
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id, council_id, topic_id, assigned_by) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, topicId, deanUser);

        // Defense/GVPB scoring now requires an approved defended group. The
        // V65 membership-state constraint trigger validates APPROVED groups
        // at commit, so the group lands pending, the roster is filled, and
        // approval flips only once the cohort exists — the same order the
        // production flow uses.
        UUID groupId = UUID.randomUUID();
        String leader = "tcr-" + tag + "-" + suffix + "-leader";
        jdbc.update("INSERT INTO thesis.thesis_group (id, round_id, leader_student_id, topic_id, status, approval_status) "
                        + "VALUES (?, ?, ?, ?, 'SUBMITTED', 'PENDING')",
                groupId, roundId, leader, topicId);
        for (int order = 1; order <= 3; order++) {
            jdbc.update("INSERT INTO thesis.thesis_group_member (id, group_id, round_id, student_id, member_order, is_leader) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), groupId, roundId,
                    order == 1 ? leader : leader + "-" + order, order, order == 1);
        }
        jdbc.update("UPDATE thesis.thesis_group SET approval_status = 'APPROVED' WHERE id = ?", groupId);
        return new Fixture(roundId, councilId, topicId, chair, secretary, member, supervisor, reviewer, deanUser);
    }

    private void seat(UUID councilId, String lecturerId, String role) {
        jdbc.update("INSERT INTO thesis.thesis_council_member (id, council_id, lecturer_id, member_role) VALUES (?, ?, ?, ?)",
                UUID.randomUUID(), councilId, lecturerId, role);
    }

    private String userOf(String lecturerId) { return "tcr-user-" + lecturerId; }

    private void insertUser(String userId, String email) {
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\",\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") "
                        + "VALUES (?, ?, 'test', 'Race', 'Actor', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, email);
    }

    private void insertLecturer(String lecturerId) {
        insertUser(userOf(lecturerId), lecturerId + "@test.invalid");
        jdbc.update("INSERT INTO campuscore_auth.\"Lecturer\" (\"id\",\"userId\",\"departmentId\",\"employeeId\",\"isActive\") "
                        + "VALUES (?, ?, 'department-demo', ?, TRUE)",
                lecturerId, userOf(lecturerId), "EMP-" + lecturerId.substring(0, Math.min(lecturerId.length(), 40)));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private int auditCount(String entityId) {
        return count("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ?", entityId);
    }

    private static Jwt memberJwt(String lecturerId) {
        return lecturerJwt(lecturerId);
    }

    private static Jwt chairJwt(String lecturerId) {
        return lecturerJwt(lecturerId);
    }

    private static Jwt reviewerJwt(String lecturerId) {
        return lecturerJwt(lecturerId);
    }

    private static Jwt lecturerJwt(String lecturerId) {
        return Jwt.withTokenValue("test").header("alg", "none").subject("tcr-user-" + lecturerId)
                .claim("roles", List.of("LECTURER")).claim("lecturerId", lecturerId).build();
    }

    private static Jwt governanceJwt() {
        return Jwt.withTokenValue("test").header("alg", "none").subject("tcr-governance")
                .claim("roles", List.of("TRUONG_KHOA")).build();
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_COUNCIL_POSTGRES_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return false;
        }
        try {
            URI uri = URI.create(url.substring("jdbc:".length()));
            String host = uri.getHost();
            boolean loopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
            String path = uri.getPath();
            String database = path != null && path.startsWith("/") ? path.substring(1) : null;
            return loopback && database != null && database.startsWith("campuscore_council_race_");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void publishMustReScopeWhenTheDepartmentChangesBetweenGateAndLock() throws Exception {
        Fixture fx = fixture("pubscope");
        jdbc.update("UPDATE thesis.thesis_registration_round SET status = 'PROPOSAL_OPEN' WHERE id = ?",
                fx.roundId());
        jdbc.update("UPDATE thesis.thesis_topic SET status = 'DRAFT' WHERE id = ?", fx.topicId());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String departmentD2 = "dept-race-" + suffix;
        jdbc.update(
                "INSERT INTO academic.\"Department\" SELECT (jsonb_populate_record(NULL::academic.\"Department\", "
                        + "to_jsonb(d) || jsonb_build_object('id', ?, 'code', ?, 'name', ?, 'isActive', TRUE))).* "
                        + "FROM academic.\"Department\" d WHERE d.\"id\" = 'department-demo'",
                departmentD2, "RACE-" + suffix, "Race Department " + suffix);
        Jwt deptHead = Jwt.withTokenValue("test").header("alg", "none")
                .subject(userOf(fx.supervisor()))
                .claim("roles", List.of("TRUONG_BO_MON"))
                .claim("lecturerId", fx.supervisor())
                .build();
        int baselineAudits = auditCount(fx.topicId().toString());

        CountDownLatch gateReadD1 = new CountDownLatch(1);
        CountDownLatch releasePublisher = new CountDownLatch(1);
        String publisherThread = "pgw-pub-" + suffix;
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (Thread.currentThread().getName().equals(publisherThread)
                    && sql.contains("round_id") && sql.contains("department_id")
                    && sql.contains("thesis_topic")) {
                Object result = invocation.callRealMethod();
                gateReadD1.countDown();
                if (!releasePublisher.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("publisher release barrier timeout");
                }
                return result;
            }
            return invocation.callRealMethod();
        }).when(namedJdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> publisher = pool.submit(() -> {
                Thread.currentThread().setName(publisherThread);
                try {
                    mutations.publishTopic(fx.topicId(), deptHead);
                    return null;
                } catch (Throwable error) {
                    return error;
                }
            });
            assertThat(gateReadD1.await(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the publisher must read the department gate before the lock").isTrue();

            jdbc.update("UPDATE thesis.thesis_topic SET department_id = ? WHERE id = ?",
                    departmentD2, fx.topicId());
            releasePublisher.countDown();

            Throwable error = publisher.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(error).isInstanceOf(DomainException.class);
            assertThat(((DomainException) error).getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(((DomainException) error).code()).isEqualTo("TOPIC_APPROVER_REQUIRED");

            Map<String, Object> topic = jdbc.queryForMap(
                    "SELECT status, department_id FROM thesis.thesis_topic WHERE id = ?", fx.topicId());
            assertThat(String.valueOf(topic.get("status"))).isEqualTo("DRAFT");
            assertThat(String.valueOf(topic.get("department_id"))).isEqualTo(departmentD2);
            assertThat(count("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                            + "WHERE \"entityId\" = ? AND \"action\" = 'THESIS_TOPIC_APPROVED'",
                    fx.topicId().toString())).isZero();
            assertThat(auditCount(fx.topicId().toString())).isEqualTo(baselineAudits);
        } finally {
            releasePublisher.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS))
                    .as("the publisher transaction must not be left dangling").isTrue();
        }
    }

    private record Fixture(UUID roundId, UUID councilId, UUID topicId, String chair,
                           String secretary, String member, String supervisor, String reviewer, String deanUser) { }
}
