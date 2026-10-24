package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RetractPublicationRequest;
import io.campuscore.restfulapi.thesis.dto.ThesisMutationDtos.RoundClosureRequest;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-PostgreSQL race coverage for the round end-of-life: close and retract
 * both serialize on the round row FOR UPDATE, so whichever commits first
 * decides the loser's outcome — a retract that lands first turns the close
 * into ROUND_STATE_CONFLICT, and a close that lands first makes a late
 * mutation see the terminal CLOSED state under the same lock — never an
 * inconsistent half-state. H2's lock emulation cannot prove either.
 *
 * <p><b>Environment contract</b> (opt-in, same as the other thesis
 * {@code *PostgresIT} classes): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL
 * pointing at a disposable database — real Flyway migrations run on context
 * startup. Fixture rows use {@code cl1-} prefixed markers and are deleted
 * after every test.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRoundCloseRacePostgresIT {

    private static final String WORKER_NAME = "cl1-worker";
    private static final String ROUND_NAME = "cl1-close-race";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    private UUID roundId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
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
        // Published beyond the 7-day retract window? No — inside it, so a
        // retract is a live contender for the same row lock as the close.
        jdbc.update("INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, status, results_published_at) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, 'RESULTS_PUBLISHED', ?)",
                roundId, ROUND_NAME,
                Timestamp.from(now.minusSeconds(10 * 86_400)), Timestamp.from(now.minusSeconds(9 * 86_400)),
                Timestamp.from(now.minusSeconds(8 * 86_400)), Timestamp.from(now.minusSeconds(7 * 86_400)),
                Timestamp.from(now.plusSeconds(86_400)),
                Timestamp.from(now.minusSeconds(3_600)));
    }

    @AfterEach
    void cleanFixtures() {
        jdbc.update("DELETE FROM campuscore_audit.\"AdminAudit\" WHERE \"actorId\" LIKE 'cl1-%'"
                        + " OR \"entityId\" IN (SELECT CAST(id AS text) FROM thesis.thesis_registration_round"
                        + " WHERE name = ?)",
                ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_group_member WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_topic WHERE round_id IN "
                + "(SELECT id FROM thesis.thesis_registration_round WHERE name = ?)", ROUND_NAME);
        jdbc.update("DELETE FROM thesis.thesis_registration_round WHERE name = ?", ROUND_NAME);
    }

    @Test
    void retractCommittedFirstForcesTheConcurrentCloseToFail() throws Exception {
        // The close worker is paused right BEFORE its round FOR UPDATE — it
        // has authorized but holds no locks. The retract then commits the
        // RESULTS_PUBLISHED -> REGISTRATION_CLOSED transition; when the close
        // resumes, its locked read must see the committed non-published state.
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    return mutations.closeRound(roundId, new RoundClosureRequest("archive"), admin);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("close paused before the round row lock")
                    .isTrue();
            mutations.retractResults(roundId, new RetractPublicationRequest("premature", "RETRACT"), admin);
            assertThat(roundStatus()).isEqualTo("REGISTRATION_CLOSED");
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("a close that arrives after a retract must lose")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("ROUND_STATE_CONFLICT");
            assertThat(roundStatus()).isEqualTo("REGISTRATION_CLOSED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closeCommittedFirstFailsTheConcurrentRetract() throws Exception {
        // Reverse schedule: retract is paused BEFORE its round FOR UPDATE, the
        // close commits, then retract resumes and must read the committed
        // CLOSED outcome under the lock — never a stale pre-close state.
        // The fixture is republished past the grace window first so the close
        // is allowed, while the retract side is already doomed either way.
        jdbc.update("UPDATE thesis.thesis_registration_round "
                        + "SET results_published_at = CURRENT_TIMESTAMP - interval '30 days' WHERE id = ?",
                roundId);
        Jwt admin = admin();
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseWorkerBeforeRoundLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    return mutations.retractResults(roundId,
                            new RetractPublicationRequest("late", "RETRACT"), admin);
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(paused.await(10, TimeUnit.SECONDS))
                    .as("retract paused before the round row lock")
                    .isTrue();
            mutations.closeRound(roundId, new RoundClosureRequest("archive"), admin);
            assertThat(roundStatus()).isEqualTo("CLOSED");
            resume.countDown();
            Object outcome = worker.get(20, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("a retract that arrives after a close must lose")
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("ROUND_STATE_CONFLICT");
            assertThat(roundStatus()).isEqualTo("CLOSED");
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Pauses the named worker immediately before any thesis_registration_round
     * FOR UPDATE read — the same interception the other race ITs use, valid
     * because close and retract both open with that lock. The worker's
     * transaction is authorized but holds no locks when it parks, so the main
     * thread can commit its transition without a deadlock.
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

    private String roundStatus() {
        return jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId);
    }

    private Jwt admin() {
        return Jwt.withTokenValue("close-race-fixture").header("alg", "none")
                .subject("cl1-admin").claim("roles", List.of("ADMIN")).build();
    }
}
