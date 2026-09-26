package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.web.DomainException;
import java.util.ArrayList;
import java.util.List;
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
 * PostgreSQL authority test for the last-seat race — the H2-only proof in
 * {@code AcademicEnrollmentMutationPersistenceTest} cannot demonstrate
 * PostgreSQL row-lock serialization ({@code SELECT ... FOR UPDATE} + the
 * conditional {@code enrolledCount < capacity} UPDATE) on a real engine.
 *
 * <p><b>Environment contract</b> (opt-in, same convention as
 * {@code ThesisAssistantTurnLedgerPostgresIT}): the test runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is set to a {@code jdbc:postgresql:} URL;
 * CI without a Postgres service container leaves it unset and the IT is skipped.
 * Locally, point it at a freshly recreated database, e.g.
 * {@code CAMPUSCORE_E2E_POSTGRES_URL=jdbc:postgresql://localhost:5432/campuscore_e2e}.
 * Optional overrides:
 * <ul>
 *   <li>{@code CAMPUSCORE_E2E_POSTGRES_USER} — defaults to {@code postgres}</li>
 *   <li>{@code CAMPUSCORE_E2E_POSTGRES_PASSWORD} — defaults to {@code postgres}</li>
 * </ul>
 * Recreate the target database fresh before a run:
 * {@code psql -h localhost -U postgres -w -c "DROP DATABASE IF EXISTS campuscore_e2e;" -c "CREATE DATABASE campuscore_e2e OWNER postgres;"}
 */
@EnabledIf(value = "io.campuscore.restfulapi.academic.AcademicEnrollmentSeatRacePostgresIT#postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "deepseek.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=16"
})
@ActiveProfiles({"test", "persistence"})
class AcademicEnrollmentSeatRacePostgresIT {

    private static final int STUDENT_COUNT = 10;
    private static final int CAPACITY = 2;

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

    /**
     * Port of {@code tenStudentsCompetingForSameSectionAreSerializedBySectionLock}
     * onto PostgreSQL: ten distinct students fire one enroll call each against a
     * two-seat section; exactly two commit and the other eight receive a 409
     * business conflict, with the counter and the enrollment rows read back
     * from Postgres itself.
     */
    @Test
    void tenStudentsCompetingForSameSectionAreSerializedBySectionLockOnPostgres() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, CAPACITY);
        List<String> studentIds = new ArrayList<>();
        for (int index = 1; index <= STUDENT_COUNT; index++) {
            studentIds.add(fixture.seedStudent(index));
        }

        ExecutorService pool = Executors.newFixedThreadPool(STUDENT_COUNT);
        CountDownLatch ready = new CountDownLatch(STUDENT_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < STUDENT_COUNT; index++) {
                final String studentId = studentIds.get(index);
                final String idempotencyKey = "it-seat-" + fixture.sectionId + "-" + index;
                futures.add(pool.submit((Callable<Outcome>) () -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        return new Outcome(false, -1, "start barrier timeout");
                    }
                    try {
                        registration.enroll(studentId, fixture.sectionId, List.of("STUDENT"), idempotencyKey);
                        return new Outcome(true, 200, "");
                    } catch (DomainException rejection) {
                        return new Outcome(false, rejection.getStatus().value(), rejection.code());
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(outcomes).hasSize(STUDENT_COUNT);
            assertThat(outcomes.stream().filter(outcome -> outcome.enrolled()).count())
                    .as("exactly the section capacity succeeds")
                    .isEqualTo(CAPACITY);
            assertThat(outcomes.stream().filter(outcome -> !outcome.enrolled()).count())
                    .isEqualTo(STUDENT_COUNT - CAPACITY);
            // Every rejection must be a business conflict, never a 500 or a lock timeout.
            assertThat(outcomes.stream()
                            .filter(outcome -> !outcome.enrolled())
                            .allMatch(outcome -> outcome.status() == 409))
                    .isTrue();

            // Both final facts are read back from PostgreSQL.
            Integer enrolledCount = jdbc.queryForObject(
                    "SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = :id",
                    java.util.Map.of("id", fixture.sectionId), Integer.class);
            Integer activeEnrollments = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"Enrollment\""
                            + " WHERE \"sectionId\" = :id AND \"status\" = 'ENROLLED'",
                    java.util.Map.of("id", fixture.sectionId), Integer.class);
            assertThat(enrolledCount).isEqualTo(CAPACITY);
            assertThat(activeEnrollments).isEqualTo(CAPACITY);
            Integer duplicateStudents = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT \"studentId\" FROM academic.\"Enrollment\""
                            + " WHERE \"sectionId\" = :id AND \"status\" = 'ENROLLED'"
                            + " GROUP BY \"studentId\" HAVING COUNT(*) > 1) duplicates",
                    java.util.Map.of("id", fixture.sectionId), Integer.class);
            assertThat(duplicateStudents).isZero();
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

    private record Outcome(boolean enrolled, int status, String code) {
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
