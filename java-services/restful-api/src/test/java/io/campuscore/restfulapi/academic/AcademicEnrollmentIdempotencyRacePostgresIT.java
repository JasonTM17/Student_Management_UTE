package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.web.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * PostgreSQL authority test for idempotency under concurrency: ten threads
 * replaying the SAME student + section + Idempotency-Key must leave exactly
 * one enrollment row. The unique index on
 * {@code RegistrationIdempotency(ownerId, idempotencyKey)} plus the
 * {@code INSERT ... ON CONFLICT DO NOTHING} claim is the mechanism; H2's
 * test-only fallback path cannot prove the Postgres behavior.
 *
 * <p><b>Environment contract</b> (opt-in, identical to
 * {@link AcademicEnrollmentSeatRacePostgresIT}): runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is set to a {@code jdbc:postgresql:} URL —
 * point it at a freshly recreated database, e.g.
 * {@code jdbc:postgresql://localhost:5432/campuscore_e2e}. Optional overrides:
 * {@code CAMPUSCORE_E2E_POSTGRES_USER} / {@code CAMPUSCORE_E2E_POSTGRES_PASSWORD}
 * (both default to {@code postgres}). CI without the env skips the IT.
 */
@EnabledIf(value = "io.campuscore.restfulapi.academic.AcademicEnrollmentIdempotencyRacePostgresIT#postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "deepseek.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@ActiveProfiles({"test", "persistence"})
class AcademicEnrollmentIdempotencyRacePostgresIT {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private RegistrationService registration;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> valueOr(
                "CAMPUSCORE_E2E_POSTGRES_URL", "jdbc:postgresql://localhost:5432/campuscore_e2e"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void tenConcurrentReplaysOfSameKeyPersistExactlyOneEnrollment() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 35);
        String studentId = fixture.seedStudent(1);
        String idempotencyKey = "it-idem-" + fixture.sectionId;

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < THREAD_COUNT; index++) {
                futures.add(pool.submit((Callable<Outcome>) () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return new Outcome(null, -1, "start barrier timeout");
                    }
                    try {
                        // Same student, same section, same key — a pure replay storm.
                        return new Outcome(
                                registration.enroll(studentId, fixture.sectionId, List.of("STUDENT"), idempotencyKey).id(),
                                200, "");
                    } catch (DomainException rejection) {
                        return new Outcome(null, rejection.getStatus().value(), rejection.code());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }

            // Every thread either succeeded (the claim winner or a replay of
            // the completed claim — a blocked claimant reads COMPLETED after
            // the winner commits, so both are correct) or was rejected with
            // the in-progress conflict. Anything else (5xx, lock timeout,
            // duplicate-key leakage) is a defect.
            assertThat(outcomes).hasSize(THREAD_COUNT);
            assertThat(outcomes.stream().filter(outcome -> outcome.enrollmentId() != null).count())
                    .isGreaterThanOrEqualTo(1);
            assertThat(outcomes.stream()
                            .filter(outcome -> outcome.enrollmentId() == null)
                            .allMatch(outcome -> outcome.status() == 409
                                    && "IDEMPOTENCY_IN_PROGRESS".equals(outcome.code())))
                    .isTrue();

            // ALL successful threads observed the SAME enrollment id — the
            // replay contract — and exactly one enrollment row exists.
            long distinctEnrollmentIds = outcomes.stream()
                    .map(Outcome::enrollmentId)
                    .filter(id -> id != null)
                    .distinct()
                    .count();
            assertThat(distinctEnrollmentIds).isEqualTo(1);

            Integer enrollmentRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"Enrollment\" WHERE \"studentId\" = :studentId",
                    Map.of("studentId", studentId), Integer.class);
            assertThat(enrollmentRows).isEqualTo(1);

            Map<String, Object> idempotency = jdbc.queryForMap(
                    "SELECT \"state\", \"enrollmentId\" FROM academic.\"RegistrationIdempotency\""
                            + " WHERE \"ownerId\" = :owner AND \"idempotencyKey\" = :key",
                    Map.of("owner", studentId, "key", idempotencyKey));
            assertThat(String.valueOf(idempotency.get("state"))).isEqualTo("COMPLETED");
            assertThat(String.valueOf(idempotency.get("enrollmentId")))
                    .isEqualTo(outcomes.stream().map(Outcome::enrollmentId)
                            .filter(id -> id != null).findFirst().orElseThrow());
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Opt-in like the existing Postgres ITs: runs only when the URL env is a jdbc:postgresql: target. */
    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private record Outcome(String enrollmentId, int status, String code) {
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
