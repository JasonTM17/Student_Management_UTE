package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real transactional service calls against PostgreSQL, paused while the round
 * row lock is held. A concurrent restore must wait on the lock and then find
 * the row gone (ROUND_NOT_FOUND — never a resurrected round), and a concurrent
 * second delete must lose the same serialization with a clean 404.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRoundDeleteRacePostgresIT {
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void aRestoreWaitingOnTheDeleteCannotResurrectTheRound() throws Exception {
        UUID round = insertCancelledClone("Delete race restore source");
        Jwt admin = actor("ADMIN");

        CountDownLatch roundLocked = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        AtomicInteger deletePid = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            Object result = call.callRealMethod();
            if (Thread.currentThread().getName().equals("delete-worker")
                    && sql.contains("thesis_registration_round") && sql.contains("FOR UPDATE")) {
                deletePid.set(jdbc.getJdbcOperations()
                        .queryForObject("SELECT pg_backend_pid()", Integer.class));
                roundLocked.countDown();
                if (!releaseDelete.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("delete barrier timeout");
                }
            }
            return result;
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var deleting = pool.submit(() -> {
                Thread.currentThread().setName("delete-worker");
                mutations.deleteRound(round, "created by mistake", admin);
            });
            assertThat(roundLocked.await(10, TimeUnit.SECONDS))
                    .as("delete reaches its FOR UPDATE round lock").isTrue();

            var restoring = pool.submit(() -> {
                Thread.currentThread().setName("restore-worker");
                try {
                    mutations.restoreRound(round, null, admin);
                    return "RESTORED";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });

            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!blocked && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_stat_activity w "
                                + "WHERE w.wait_event_type = 'Lock' "
                                + "AND :owner = ANY(pg_blocking_pids(w.pid)))",
                        Map.of("owner", deletePid.get()), Boolean.class));
                if (!blocked && !restoring.isDone()) Thread.sleep(25);
            }
            assertThat(blocked)
                    .as("restore must wait on the round row lock the delete still holds")
                    .isTrue();

            releaseDelete.countDown();
            deleting.get(20, TimeUnit.SECONDS);
            assertThat(restoring.get(20, TimeUnit.SECONDS))
                    .as("the late restore sees the row deleted, not resurrected")
                    .isEqualTo("ROUND_NOT_FOUND");
            Integer rounds = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_registration_round WHERE id = :id",
                    Map.of("id", round), Integer.class);
            assertThat(rounds).isZero();
        } finally {
            releaseDelete.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aSecondDeleteLosesTheRowLockAndAnswersNotFound() throws Exception {
        UUID round = insertCancelledClone("Delete race second delete");
        Jwt admin = actor("ADMIN");

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                try {
                    mutations.deleteRound(round, "created by mistake", admin);
                    return "DELETED";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            var second = pool.submit(() -> {
                try {
                    mutations.deleteRound(round, "created by mistake", admin);
                    return "DELETED";
                } catch (DomainException failure) {
                    return failure.code();
                }
            });
            List<String> outcomes = List.of(
                    first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(outcomes).containsExactlyInAnyOrder("DELETED", "ROUND_NOT_FOUND");
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Clones any seeded round row into an empty CANCELLED round (the clone
     * shares no dependent rows, so it is a valid delete candidate). */
    private UUID insertCancelledClone(String name) {
        UUID round = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_registration_round SELECT (jsonb_populate_record("
                        + "NULL::thesis.thesis_registration_round,to_jsonb(r)||jsonb_build_object('id',CAST(:id AS text),"
                        + "'name',:name,'status','CANCELLED','cancelled_from_status','DRAFT'))).*"
                        + " FROM (SELECT * FROM thesis.thesis_registration_round LIMIT 1) r",
                Map.of("id", round, "name", name));
        return round;
    }

    private static Jwt actor(String role) {
        return Jwt.withTokenValue("delete-race").header("alg", "none").subject("audit-user")
                .claim("roles", List.of(role)).build();
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
