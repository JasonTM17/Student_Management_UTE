package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.testsupport.AccountAuthorityFixture;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.service.ThesisSupervisorService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.TopicUpdateRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Deterministic stale-authorization reproduction for topic department
 * governance. The head worker passes its gate check against the topic's
 * original department, then is paused exactly before the round FOR UPDATE so
 * it holds no row locks; an admin commits a legitimate cross-department
 * transfer; the head then resumes. The paused request must lose with
 * TOPIC_OWNER_REQUIRED — a committed write proves the stale department read
 * authorized a mutation on a topic the head no longer governs.
 */
@SpringBootTest
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_topic_race;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=true", "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisTopicGovernanceRaceTest {

    private static final String DEPT_A = "department-demo";
    private static final String DEPT_B = "tgr-dept-b";
    private static final String HEAD_SUBJECT = "thesis-fixture-lecturer-tgr-head";
    private static final String WORKER_NAME = "tgr-head-worker";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @Autowired private ThesisSupervisorService supervisors;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;

    @BeforeEach
    void prepare() {
        Mockito.reset(namedJdbc);
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
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'tgr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'tgr-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE '%tgr-%'");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"Department\" WHERE \"id\"=?", Integer.class, DEPT_B) == 0) {
            jdbc.update("INSERT INTO academic.\"Department\" (\"id\",\"name\",\"code\",\"facultyId\",\"isActive\") "
                    + "SELECT 'tgr-dept-b','Race foreign department','TGB',\"facultyId\",TRUE FROM academic.\"Department\" WHERE \"id\"=?",
                    DEPT_A);
        }
        roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id,name,thesis_type,lecturer_submit_start,lecturer_submit_end,registration_start,registration_end,status) "
                        + "VALUES (?, 'Governance race','KLTN',?,?,?,?,'PROPOSAL_OPEN')", roundId,
                Timestamp.from(now.minusSeconds(3600)), Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200)), Timestamp.from(now.plusSeconds(14400)));
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
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-cand-1");
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-cand-2");
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
                    return supervisors.setSupervisors(topicId, List.of("tgr-cand-1", "tgr-cand-2"), head);
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
            assertThat(roster).containsExactly("tgr-owner");
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
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-cand-1");
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-cand-2");
        Object response = supervisors.setSupervisors(topicId, List.of("tgr-cand-1", "tgr-cand-2"), headJwt());
        assertThat(response).isNotNull();
        List<String> roster = jdbc.queryForList(
                "SELECT lecturer_id FROM thesis.thesis_topic_supervisor WHERE topic_id=? ORDER BY supervisor_order",
                String.class, topicId);
        assertThat(roster).containsExactly("tgr-cand-1", "tgr-cand-2");
    }

    /**
     * Pauses the named head worker immediately before the round FOR UPDATE
     * read. At that point the worker's transaction has authorized against the
     * original department but holds no row locks, so the admin transfer cannot
     * deadlock against it. Every other call and every other thread delegates
     * to the real method.
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
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-owner");
        ThesisAuthorityFixture.lecturer(jdbc, "tgr-head");
        jdbc.update("UPDATE campuscore_auth.\"Lecturer\" SET \"departmentId\"=? WHERE \"id\"=?", DEPT_A, "tgr-head");
        AccountAuthorityFixture.replaceRoles(jdbc, HEAD_SUBJECT, "TRUONG_BO_MON");
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_topic (id,round_id,department_id,title,description,max_groups,status,created_by) "
                        + "VALUES (?,?,?,'Governed topic','Original description',2,'DRAFT','thesis-fixture-lecturer-tgr-owner')",
                id, roundId, DEPT_A);
        jdbc.update("INSERT INTO thesis.thesis_topic_supervisor (id,topic_id,lecturer_id,supervisor_order) "
                + "VALUES (?,?,'tgr-owner',1)", UUID.randomUUID(), id);
        return id;
    }

    private static Jwt headJwt() {
        return Jwt.withTokenValue("tgr-test").header("alg", "none").subject(HEAD_SUBJECT)
                .claim("roles", List.of("TRUONG_BO_MON")).claim("lecturerId", "tgr-head").build();
    }

    private static Jwt adminJwt() {
        return Jwt.withTokenValue("tgr-test").header("alg", "none").subject("tgr-admin")
                .claim("roles", List.of("ADMIN")).build();
    }
}
