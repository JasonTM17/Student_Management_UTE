package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.service.AdminCatalogMutationService;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real-PostgreSQL proofs for the cancelled-section lifecycle the H2 fixture
 * cannot host: the partial unique index on (student, course, semester) only
 * exists on the real engine, and the reopen-conflict scan reads real rows.
 *
 * <p><b>Environment contract</b>: runs ONLY when
 * {@code CAMPUSCORE_E2E_POSTGRES_URL} is a {@code jdbc:postgresql:} URL at a
 * disposable database — real Flyway migrations run on context startup.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class SectionReopenConflictPostgresIT {

    private static final String SECTION_TABLE = "\"academic\".\"Section\"";
    private static final String WORKER_NAME = "src-slip-worker";

    @Autowired private RegistrationService registration;
    @Autowired private AdminCatalogMutationService catalog;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @MockitoSpyBean private NamedParameterJdbcTemplate namedJdbc;

    @org.junit.jupiter.api.BeforeEach
    void resetSpy() {
        Mockito.reset(namedJdbc);
    }

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

    /**
     * The preserved enrollment in a cancelled section still holds the partial
     * unique slot while active — the replacement enroll used to collide with
     * 23505 at insert. The old row must be retired as DROPPED with a REPLACED
     * event inside the same locked transaction, and a later reopen must not
     * resurrect it.
     */
    @Test
    void enrollingTheReplacementSectionRetiresTheCancelledSeat() {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;
        String replacement = secondSection(fixture, 30);

        registration.enroll(student, cancelled, List.of("STUDENT"), "it-g-" + cancelled);
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));

        // Under the partial unique index this insert deadlocks on the
        // preserved active row unless the service retires it first.
        registration.enroll(student, replacement, List.of("STUDENT"), "it-g-" + replacement);

        String oldStatus = jdbc.queryForObject(
                "SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"sectionId\" = :s AND \"studentId\" = :st",
                new MapSqlParameterSource().addValue("s", cancelled).addValue("st", student), String.class);
        assertThat(oldStatus).isEqualTo("DROPPED");
        Integer replacedEvents = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"EnrollmentEvent\" WHERE \"studentId\" = :st"
                        + " AND \"sectionId\" = :s AND \"action\" = 'REPLACED'",
                new MapSqlParameterSource().addValue("st", student).addValue("s", cancelled), Integer.class);
        assertThat(replacedEvents).isEqualTo(1);
        Integer newActive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Enrollment\" WHERE \"sectionId\" = :s AND \"studentId\" = :st"
                        + " AND \"status\" = 'ENROLLED'",
                new MapSqlParameterSource().addValue("s", replacement).addValue("st", student), Integer.class);
        assertThat(newActive).isEqualTo(1);
        Integer oldCount = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = :s",
                new MapSqlParameterSource("s", cancelled), Integer.class);
        assertThat(oldCount).isZero();

        // A later reopen must not resurrect the retired seat.
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "OPEN"));
        Integer resurrected = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"Enrollment\" WHERE \"sectionId\" = :s AND \"studentId\" = :st"
                        + " AND \"status\" <> 'DROPPED'",
                new MapSqlParameterSource().addValue("s", cancelled).addValue("st", student), Integer.class);
        assertThat(resurrected).isZero();
    }

    /**
     * The cancel window stops counting the preserved seat, so a student can
     * spend those credits on a different course. Reopening the cancelled
     * section would then push them over the cap: the server must refuse with
     * REOPEN_CONFLICTS carrying the per-student detail until the operator
     * acknowledges it with a recorded reason.
     */
    @Test
    void reopenAfterCancellationReportsConflictsAndHonorsAcknowledgement() {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;

        registration.enroll(student, cancelled, List.of("STUDENT"), "it-h-" + cancelled);
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));

        // The cancelled seat no longer counts, so the student spends those
        // credits on a different course — a same-course replacement would
        // have retired the preserved row instead (covered above).
        String otherCourse = "course-h-" + fixture.sectionId;
        String otherSection = "section-h-" + fixture.sectionId;
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"code\",\"name\",\"credits\",\"departmentId\",\"isActive\")"
                        + " VALUES (:id,:code,'Other',3,:dept,TRUE)",
                new MapSqlParameterSource()
                        .addValue("id", otherCourse)
                        .addValue("code", "X" + otherCourse.substring(otherCourse.length() - 8))
                        .addValue("dept", fixture.departmentId));
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"sectionNumber\",\"courseId\",\"semesterId\","
                        + "\"capacity\",\"enrolledCount\",\"status\",\"version\")"
                        + " VALUES (:id,'09',:courseId,:semesterId,30,0,'OPEN',0)",
                new MapSqlParameterSource()
                        .addValue("id", otherSection)
                        .addValue("courseId", otherCourse)
                        .addValue("semesterId", fixture.semesterId));
        registration.enroll(student, otherSection, List.of("STUDENT"), "it-h-" + otherSection);

        // 3 credits elsewhere + 3 in the reopened course exceed a cap of 3.
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"creditLimit\" = 3 WHERE \"id\" = :id",
                new MapSqlParameterSource("id", fixture.roundId));

        // Without acknowledgement the reopen is refused with the detail.
        DomainException refusal = catchReopen(cancelled, Map.of("status", "OPEN"));
        assertThat(refusal).isNotNull();
        assertThat(refusal.code()).isEqualTo("REOPEN_CONFLICTS");
        assertThat(refusal.getStatus().value()).isEqualTo(409);
        assertThat(refusal.getDetails()).isNotNull();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> conflicts =
                (List<Map<String, Object>>) refusal.getDetails().get("conflicts");
        assertThat(conflicts).isNotEmpty();
        assertThat(conflicts.stream().map(c -> String.valueOf(c.get("studentId"))))
                .contains(student);
        assertThat(conflicts.get(0).get("reasons").toString()).contains("CREDIT_CAP");

        // Acknowledgement still requires a reason.
        DomainException noReason = catchReopen(cancelled,
                Map.of("status", "OPEN", "acknowledgeConflicts", true));
        assertThat(noReason).isNotNull();
        assertThat(noReason.code()).isEqualTo("REOPEN_CONFLICTS");

        // Acknowledged with a reason: the reopen lands and is audited.
        Map<String, Object> reopened = catalog.update(SECTION_TABLE, cancelled,
                Map.of("status", "OPEN", "acknowledgeConflicts", true, "reason", "capacity reviewed"));
        assertThat(String.valueOf(reopened.get("status"))).isEqualTo("OPEN");
    }

    /**
     * A slip rendered from pre-cancel state must not commit over the cancel:
     * the render locks the affected section rows and re-reads the active set
     * under the lock, so parking the slip worker before its section FOR
     * UPDATE leaves it holding nothing while the cancel commits — the re-read
     * then finds no active enrollment and the request 404s instead of
     * persisting a slip that still lists the cancelled course. Before the
     * lock-and-recheck the worker's stale read survived to the INSERT and the
     * slip row persisted.
     */
    @Test
    void slipRenderedDuringCancelDoesNotPersistTheCancelledCourse() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;
        registration.enroll(student, cancelled, List.of("STUDENT"), "it-i-" + cancelled);
        // The enroll already persisted a slip; a render-path race needs the
        // worker to miss that fast path and rebuild the slip itself.
        jdbc.update("DELETE FROM academic.\"RegistrationSlip\" WHERE \"studentId\" = :st",
                new MapSqlParameterSource("st", student));

        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        pauseSlipWorkerBeforeSectionLock(paused, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var worker = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    registration.slip(student, fixture.semesterId);
                    return "OK";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            boolean parked = paused.await(10, TimeUnit.SECONDS);
            if (!parked) {
                Object early = worker.isDone() ? worker.get() : "still running (not done)";
                throw new AssertionError("slip render never reached the lock barrier; worker outcome: " + early);
            }
            // The cancel lands while the render holds nothing; it also clears
            // persisted slips as part of its own transaction.
            catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));
            resume.countDown();
            Object outcome = worker.get(30, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("re-read under the lock sees the cancel: %s", outcome)
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("ROUND_NOT_FOUND");
            Integer persisted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"RegistrationSlip\" WHERE \"studentId\" = :st",
                    new MapSqlParameterSource("st", student), Integer.class);
            assertThat(persisted).as("no slip claiming the cancelled seat may persist").isZero();
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Parks the named worker at the last point where a cancel can still land
     * first: before the slip render's section {@code FOR UPDATE} read (the
     * lock-and-recheck path) or, if that path were absent, before the slip
     * INSERT — the two shapes this schedule can take. A worker parked there
     * holds no row locks, so the main-thread cancel always commits first.
     */
    private void pauseSlipWorkerBeforeSectionLock(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            boolean barrier = (sql.contains("FROM academic.\"Section\"") && sql.contains("FOR SHARE"))
                    || (sql.contains("INSERT INTO academic.\"RegistrationSlip\""));
            if (WORKER_NAME.equals(Thread.currentThread().getName()) && barrier) {
                paused.countDown();
                if (!resume.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("race barrier timeout");
                }
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForList(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
    }

    private DomainException catchReopen(String sectionId, Map<String, Object> patch) {
        try {
            catalog.update(SECTION_TABLE, sectionId, patch);
            return null;
        } catch (DomainException failure) {
            return failure;
        }
    }

    private String secondSection(AcademicEnrollmentPostgresFixture fixture, int capacity) {
        String id = "section-b-" + fixture.sectionId;
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"sectionNumber\",\"courseId\",\"semesterId\","
                        + "\"capacity\",\"enrolledCount\",\"status\",\"version\")"
                        + " VALUES (:id,'02',:courseId,:semesterId,:capacity,0,'OPEN',0)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("courseId", fixture.courseId)
                        .addValue("semesterId", fixture.semesterId)
                        .addValue("capacity", capacity));
        return id;
    }
}
