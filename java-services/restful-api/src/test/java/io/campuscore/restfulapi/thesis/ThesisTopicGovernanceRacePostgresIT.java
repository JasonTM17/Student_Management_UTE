package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.exception.DomainException;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-PostgreSQL port of {@link ThesisTopicGovernanceRaceTest}: the same
 * stale-authorization schedule (head authorized on the pre-transfer
 * department, paused before the round FOR UPDATE so it holds no row locks,
 * admin commits the transfer, head resumes and must lose with
 * TOPIC_OWNER_REQUIRED) verified under real READ COMMITTED row locking —
 * H2's emulation cannot prove the Postgres behavior.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other thesis
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database — real Flyway migrations run on context
 * startup. Optional {@code CAMPUSCORE_E2E_POSTGRES_USER} /
 * {@code CAMPUSCORE_E2E_POSTGRES_PASSWORD} (defaults
 * {@code campuscore}/{@code postgres}). Fixture rows use {@code tgrp-}
 * prefixed ids and are deleted after every test so the disposable database
 * stays reusable.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisTopicGovernanceRacePostgresIT {

    private static final String DEPT_A = "department-demo";
    private static final String DEPT_B = "tgrp-dept-b";
    private static final String HEAD_SUBJECT = "thesis-fixture-lecturer-tgrp-head";
    private static final String WORKER_NAME = "tgrp-head-worker";
    private static final String ROUND_NAME = "tgrp-governance-race";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @Autowired private ThesisSupervisorService supervisors;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @BeforeEach
    void prepare() {
        Mockito.reset(namedJdbc);
        cleanFixtures();
        // Department B mirrors the seeded demo department's faculty so the
        // cross-department transfer targets a real row; the NOT EXISTS guard
        // keeps a leftover (or shared) copy from breaking the run.
        if (jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\"=?",
                Integer.class, DEPT_B) == 0) {
            jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"code\",\"facultyId\",\"isActive\") "
                    + "SELECT 'tgrp-dept-b','Race foreign department','TGPB',\"facultyId\",TRUE FROM academic.\"Department\" WHERE \"id\"=?",
                    DEPT_A);
        }
        roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id,name,thesis_type,lecturer_submit_start,lecturer_submit_end,registration_start,registration_end,status) "
                        + "VALUES (?, '" + ROUND_NAME + "','KLTN',?,?,?,?,'PROPOSAL_OPEN')", roundId,
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now.plusSeconds(14400)));
    }

    /**
     * Removes only this class's {@code tgrp-} fixtures: the target database is
     * shared with other disposable runs, so cleanup is marker-scoped instead
     * of the H2 test's full-table wipes. Auth rows cascade from the User
     * delete; topic/round rows are matched through the fixture markers.
     */
    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE '%tgrp-%' "
                + "OR \"entityId\" IN (SELECT CAST(id AS text) FROM thesis.thesis_topic WHERE created_by LIKE '%tgrp-%') "
                + "OR \"entityId\" IN (SELECT CAST(id AS text) FROM thesis.thesis_registration_round WHERE name='" + ROUND_NAME + "')");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor WHERE lecturer_id LIKE 'tgrp-%' "
                + "OR topic_id IN (SELECT id FROM thesis.thesis_topic WHERE created_by LIKE '%tgrp-%')");
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE created_by LIKE '%tgrp-%'");
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'tgrp-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'tgrp-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE '%tgrp-%'");
        jdbc.update("DELETE FROM academic.\"Department\" WHERE \"id\" = ?", DEPT_B);
    }

    @Test
    void pausedHeadCannotRewriteTopicAfterForeignTransfer() throws Exception {
        UUID topicId = draftTopic();
        Jwt head = headJwt();
        Jwt admin = adminJwt();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    return mutations.updateTopic(topicId,
                            new TopicUpdateRequest(DEPT_A, "Head rewrite", "Head description", 2), head);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("head authorized against the original department, then paused before the round lock")
                    .isTrue();
            // Commits while the head holds no locks: the topic now belongs to a
            // department the head does not govern.
            mutations.updateTopic(topicId,
                    new TopicUpdateRequest(DEPT_B, "Admin transfer", "Admin description", 2), admin);
            Map<String, Object> committed = jdbc.queryForMap(
                    "SELECT department_id, title FROM thesis.thesis_topic WHERE id=?", topicId);
            assertThat(committed.get("department_id")).isEqualTo(DEPT_B);
            assertThat(committed.get("title")).isEqualTo("Admin transfer");
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("a request authorized on the pre-transfer department must not write afterwards")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(((DomainException) outcome).code()).isEqualTo("TOPIC_OWNER_REQUIRED");
            Map<String, Object> persisted = jdbc.queryForMap(
                    "SELECT department_id, title FROM thesis.thesis_topic WHERE id=?", topicId);
            assertThat(persisted.get("department_id")).isEqualTo(DEPT_B);
            assertThat(persisted.get("title")).isEqualTo("Admin transfer");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void pausedHeadCannotReplaceSupervisorsAfterForeignTransfer() throws Exception {
        UUID topicId = draftTopic();
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-cand-1");
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-cand-2");
        Jwt head = headJwt();
        Jwt admin = adminJwt();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    return supervisors.setSupervisors(topicId, List.of("tgrp-cand-1", "tgrp-cand-2"), head);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("head authorized against the original department, then paused before the round lock")
                    .isTrue();
            mutations.updateTopic(topicId,
                    new TopicUpdateRequest(DEPT_B, "Admin transfer", "Admin description", 2), admin);
            assertThat(jdbc.queryForObject("SELECT department_id FROM thesis.thesis_topic WHERE id=?",
                    String.class, topicId)).isEqualTo(DEPT_B);
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("a request authorized on the pre-transfer department must not write afterwards")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).status()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(((DomainException) outcome).code()).isEqualTo("TOPIC_OWNER_REQUIRED");
            List<String> roster = jdbc.queryForList(
                    "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id=? ORDER BY supervisor_order",
                    String.class, topicId);
            assertThat(roster).containsExactly("tgrp-owner");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" "
                            + "WHERE \"entityId\"=? AND \"action\"='TOPIC_SUPERVISORS_UPDATED'",
                    Integer.class, topicId.toString()))
                    .as("the losing request must not leave a supervisor audit entry")
                    .isZero();
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void uncontendedHeadUpdatesOwnDepartmentDraft() {
        UUID topicId = draftTopic();
        Object response = mutations.updateTopic(topicId,
                new TopicUpdateRequest(DEPT_A, "Head rewrite", "Head description", 3), headJwt());
        assertThat(response).isNotNull();
        Map<String, Object> persisted = jdbc.queryForMap(
                "SELECT department_id, title FROM thesis.thesis_topic WHERE id=?", topicId);
        assertThat(persisted.get("department_id")).isEqualTo(DEPT_A);
        assertThat(persisted.get("title")).isEqualTo("Head rewrite");
    }

    @Test
    void uncontendedHeadReplacesOwnDepartmentSupervisors() {
        UUID topicId = draftTopic();
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-cand-1");
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-cand-2");
        Object response = supervisors.setSupervisors(topicId, List.of("tgrp-cand-1", "tgrp-cand-2"), headJwt());
        assertThat(response).isNotNull();
        List<String> roster = jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id=? ORDER BY supervisor_order",
                String.class, topicId);
        assertThat(roster).containsExactly("tgrp-cand-1", "tgrp-cand-2");
    }

    /**
     * Pauses the named head worker immediately before the round FOR UPDATE
     * read — the identical interception the H2 test uses, and valid on real
     * Postgres because the same {@link NamedParameterJdbcTemplate} signatures
     * carry the round-lock SQL. At that point the worker's transaction has
     * authorized against the original department but holds no row locks, so
     * the admin transfer cannot deadlock against it. Every other call and
     * every other thread delegates to the real method.
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

    /** DRAFT topic in department A owned by a different lecturer; the head is neither owner nor supervisor. */
    private UUID draftTopic() {
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-owner");
        ThesisAuthorityFixture.lecturer(jdbc, "tgrp-head");
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"departmentId\"=? WHERE \"id\"=?", DEPT_A, "tgrp-head");
        AccountAuthorityFixture.replaceRoles(jdbc, HEAD_SUBJECT, "TRUONG_BO_MON");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id,round_id,department_id,title,description,max_groups,status,created_by) "
                        + "VALUES (?,?,?,'Governed topic','Original description',2,'DRAFT','thesis-fixture-lecturer-tgrp-owner')",
                id, roundId, DEPT_A);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id,topic_id,lecturer_id,supervisor_order) "
                + "VALUES (?,?,'tgrp-owner',1)", UUID.randomUUID(), id);
        return id;
    }

    private static Jwt headJwt() {
        return Jwt.withTokenValue("tgrp-test").header("alg", "none").subject(HEAD_SUBJECT)
                .claim("roles", List.of("TRUONG_BO_MON")).claim("lecturerId", "tgrp-head").build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("tgrp-test").header("alg", "none").subject("tgrp-admin")
                .claim("roles", List.of("ADMIN")).build();
    }

    /** Opt-in like the sibling Postgres ITs: runs only when the URL env is a jdbc:postgresql: target. */
    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
