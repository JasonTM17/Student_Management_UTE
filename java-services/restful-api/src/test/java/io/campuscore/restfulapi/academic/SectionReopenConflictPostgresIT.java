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
    private static final String REOPEN_NAME = "it-reopen-worker";
    private static final String ENROLL_NAME = "it-enroll-worker";
    private static final String CANCEL_NAME = "it-cancel-worker";
    private static final String EDIT_NAME = "it-edit-worker";

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
     * The reopen gate used to share the enroll path's open-semester list, so a
     * cancelled section could not be restored in a DRAFT or IN_PROGRESS
     * semester even though no terminal state had been reached. Only the
     * terminal lifecycle states now refuse the write; every earlier state
     * reopens subject to the conflict checks.
     */
    @Test
    void reopenRefusesOnlyInTerminalSemesterStates() {
        for (String status : List.of("DRAFT", "UPCOMING", "OPEN", "REGISTRATION_OPEN",
                "ADD_DROP_OPEN", "ACTIVE", "IN_PROGRESS")) {
            AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
            jdbc.update("UPDATE academic.\"Semester\" SET \"status\" = :st WHERE \"id\" = :id",
                    new MapSqlParameterSource().addValue("st", status).addValue("id", fixture.semesterId));
            catalog.update(SECTION_TABLE, fixture.sectionId, Map.of("status", "CANCELLED"));
            assertThat(sectionStatus(fixture.sectionId)).isEqualTo("CANCELLED");

            Map<String, Object> reopened = catalog.update(SECTION_TABLE, fixture.sectionId,
                    Map.of("status", "OPEN"));
            assertThat(String.valueOf(reopened.get("status")))
                    .as("reopen must be allowed in %s", status)
                    .isEqualTo("OPEN");
        }

        for (String status : List.of("CLOSED", "COMPLETED", "ARCHIVED")) {
            AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
            jdbc.update("UPDATE academic.\"Semester\" SET \"status\" = :st WHERE \"id\" = :id",
                    new MapSqlParameterSource().addValue("st", status).addValue("id", fixture.semesterId));
            // Cancelling still only removes seats, so it stays legal here too.
            catalog.update(SECTION_TABLE, fixture.sectionId, Map.of("status", "CANCELLED"));
            DomainException refusal = catchReopen(fixture.sectionId, Map.of("status", "OPEN"));
            assertThat(refusal)
                    .as("reopen must refuse in %s", status)
                    .isNotNull();
            assertThat(refusal.code()).isEqualTo("SEMESTER_CLOSED");
            assertThat(sectionStatus(fixture.sectionId)).isEqualTo("CANCELLED");
        }
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
     * A stale section edit carrying {@code status:'OPEN'} can queue behind a
     * concurrent cancel: the edit's unlocked pre-read still sees OPEN, so it
     * never enters the reopen path — no student locks, no conflict scan, no
     * acknowledgement — and the write then stamps its stale status back over
     * the committed CANCELLED. The status write must verify the observed
     * state instead: once the cancel commits the guarded update matches
     * nothing and the edit refuses with SECTION_STATUS_CHANGED, while the
     * student spends the freed credits elsewhere under a cap of 3.
     */
    @Test
    void staleEditQueuedBehindACancelCannotReopenTheSection() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String section = fixture.sectionId;
        String otherSection = otherCourseSection(fixture, "cv");

        registration.enroll(student, section, List.of("STUDENT"), "it-cv-" + section);
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"creditLimit\" = 3 WHERE \"id\" = :id",
                new MapSqlParameterSource("id", fixture.roundId));

        CountDownLatch cancelPaused = new CountDownLatch(1);
        CountDownLatch cancelResume = new CountDownLatch(1);
        CountDownLatch editAtUpdate = new CountDownLatch(1);
        barrierCancelAtSlipDeleteAndEditAtUpdate(cancelPaused, cancelResume, editAtUpdate);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var cancel = pool.submit(() -> {
                Thread.currentThread().setName(CANCEL_NAME);
                try {
                    catalog.update(SECTION_TABLE, section, Map.of("status", "CANCELLED"));
                    return "CANCELLED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(cancelPaused.await(10, TimeUnit.SECONDS))
                    .as("cancel parked at its slip purge still holding the section row").isTrue();
            var edit = pool.submit(() -> {
                Thread.currentThread().setName(EDIT_NAME);
                try {
                    catalog.update(SECTION_TABLE, section, Map.of("status", "OPEN"));
                    return "EDITED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            // The edit read the stale OPEN row and is now queued on the
            // section row lock, never having entered the reopen path.
            assertThat(editAtUpdate.await(10, TimeUnit.SECONDS))
                    .as("edit issued its section update behind the cancel").isTrue();
            cancelResume.countDown();
            assertThat(cancel.get(30, TimeUnit.SECONDS)).isEqualTo("CANCELLED");

            Object outcome = edit.get(30, TimeUnit.SECONDS);
            assertThat(outcome)
                    .as("stale edit must refuse, not resurrect the cancelled section: %s", outcome)
                    .isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("SECTION_STATUS_CHANGED");
            assertThat(((DomainException) outcome).getStatus().value()).isEqualTo(409);
            assertThat(sectionStatus(section)).isEqualTo("CANCELLED");

            // The freed seat is then spent elsewhere: the failed edit held no
            // student row and no phantom reopen counted against the cap.
            registration.enroll(student, otherSection, List.of("STUDENT"), "it-cv-enroll");
            assertThat(activeCredits(student, fixture.semesterId)).isEqualTo(3);
            assertThat(acknowledgements(section)).isZero();
        } finally {
            cancelResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * A slip render can sit between its active-set read and the persist
     * INSERT while a reopen commits: without the student's row lock the
     * render used to write a slip missing the reopened course and every
     * later download kept serving it. With the student row share-locked
     * first — the same global order the reopen's conflict pass takes — the
     * reopen queues behind the render and its slip purge then removes the
     * render's output, so the next download re-renders the committed set.
     */
    @Test
    void slipRenderQueuedAheadOfAReopenCannotServeAStaleSet() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String section = fixture.sectionId;
        String otherSection = otherCourseSection(fixture, "sl");

        registration.enroll(student, section, List.of("STUDENT"), "it-sl-" + section);
        registration.enroll(student, otherSection, List.of("STUDENT"), "it-sl-" + otherSection);
        catalog.update(SECTION_TABLE, section, Map.of("status", "CANCELLED"));
        // A render-path race needs the worker to miss the fast path and
        // rebuild the slip itself rather than serve a persisted row.
        jdbc.update("DELETE FROM academic.\"RegistrationSlip\" WHERE \"studentId\" = :st",
                new MapSqlParameterSource("st", student));

        CountDownLatch renderPaused = new CountDownLatch(1);
        CountDownLatch renderResume = new CountDownLatch(1);
        pauseSlipWorkerBeforePersist(renderPaused, renderResume);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var render = pool.submit(() -> {
                Thread.currentThread().setName(WORKER_NAME);
                try {
                    registration.slip(student, fixture.semesterId);
                    return "RENDERED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(renderPaused.await(10, TimeUnit.SECONDS))
                    .as("render parked before the slip insert holding its locks").isTrue();
            var reopen = pool.submit(() -> {
                Thread.currentThread().setName(REOPEN_NAME);
                try {
                    catalog.update(SECTION_TABLE, section, Map.of("status", "OPEN"));
                    return "REOPENED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            // The reopen's affected-student FOR UPDATE must queue behind the
            // render's share lock — a reopen that commits here proves the
            // render never serialized on the student row.
            try {
                Object early = reopen.get(3, TimeUnit.SECONDS);
                throw new AssertionError(
                        "reopen committed while the render still held the student share lock: " + early);
            } catch (java.util.concurrent.TimeoutException blocked) {
                // queued on the Student row lock — the serialized order
            }
            renderResume.countDown();
            assertThat(render.get(30, TimeUnit.SECONDS)).isEqualTo("RENDERED");
            assertThat(reopen.get(30, TimeUnit.SECONDS)).isEqualTo("REOPENED");

            // The reopen's slip purge removes what the parked render wrote,
            // so the next download re-renders the committed set — reopened
            // course included — instead of serving the stale slip.
            Integer persisted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"RegistrationSlip\" WHERE \"studentId\" = :st",
                    new MapSqlParameterSource("st", student), Integer.class);
            assertThat(persisted).as("stale render must not survive the reopen").isZero();
            var served = registration.slip(student, fixture.semesterId);
            Integer servedFromPersisted = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"RegistrationSlip\" WHERE \"studentId\" = :st"
                            + " AND TRIM(\"sha256\") = :sha",
                    new MapSqlParameterSource().addValue("st", student).addValue("sha", served.sha256()),
                    Integer.class);
            assertThat(servedFromPersisted)
                    .as("the re-rendered slip persists for later downloads").isEqualTo(1);
        } finally {
            renderResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Two barriers in one schedule: the cancel worker parks before its slip
     * purge while holding the section row, and the edit worker only signals
     * when it issues its section UPDATE — the row lock then does the waiting.
     */
    private void barrierCancelAtSlipDeleteAndEditAtUpdate(CountDownLatch cancelPaused,
            CountDownLatch cancelResume, CountDownLatch editAtUpdate) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            String thread = Thread.currentThread().getName();
            if (CANCEL_NAME.equals(thread)
                    && sql.startsWith("DELETE FROM \"academic\".\"RegistrationSlip\"")
                    && sql.contains(":sectionId")) {
                await(cancelPaused, cancelResume);
            } else if (EDIT_NAME.equals(thread)
                    && sql.startsWith("UPDATE \"academic\".\"Section\"")) {
                editAtUpdate.countDown();
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
    }

    /** Parks the slip worker immediately before the persist INSERT. */
    private void pauseSlipWorkerBeforePersist(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            if (WORKER_NAME.equals(Thread.currentThread().getName())
                    && sql.contains("INSERT INTO academic.\"RegistrationSlip\"")) {
                await(paused, resume);
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
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

    /**
     * The reopen's conflict scan must evaluate the same registration set the
     * enroll path publishes under the Student FOR UPDATE. Parking the reopen
     * after its scan but before commit used to leave a window where a
     * concurrent enroll committed unseen: the student ended over the cap with
     * no acknowledgement on the audit trail. With the affected student rows
     * locked before the section write, the enroll serializes behind the
     * reopen and is then refused by the very cap it would have skipped.
     */
    @Test
    void enrollQueuedBehindAnUncommittedReopenCannotEscapeTheCap() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;
        String otherSection = otherCourseSection(fixture, "ra");

        registration.enroll(student, cancelled, List.of("STUDENT"), "it-ra-" + cancelled);
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"creditLimit\" = 3 WHERE \"id\" = :id",
                new MapSqlParameterSource("id", fixture.roundId));

        CountDownLatch reopenPaused = new CountDownLatch(1);
        CountDownLatch reopenResume = new CountDownLatch(1);
        barrierReopenAtSlipDelete(reopenPaused, reopenResume);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var reopen = pool.submit(() -> {
                Thread.currentThread().setName(REOPEN_NAME);
                try {
                    catalog.update(SECTION_TABLE, cancelled, Map.of("status", "OPEN"));
                    return "REOPENED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(reopenPaused.await(10, TimeUnit.SECONDS))
                    .as("reopen parked at the slip delete still holding its locks").isTrue();
            var enroll = pool.submit(() -> {
                Thread.currentThread().setName(ENROLL_NAME);
                try {
                    registration.enroll(student, otherSection, List.of("STUDENT"), "it-ra-enroll");
                    return "ENROLLED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            // The enroll must wait on the student's row lock until the reopen
            // commits; before the locks it committed inside ~10ms.
            try {
                Object early = enroll.get(3, TimeUnit.SECONDS);
                throw new AssertionError(
                        "enroll committed while the reopen still held the student lock: " + early);
            } catch (java.util.concurrent.TimeoutException blocked) {
                // queued on the Student row lock — the serialized order
            }
            reopenResume.countDown();
            assertThat(reopen.get(30, TimeUnit.SECONDS)).isEqualTo("REOPENED");
            Object enrollOutcome = enroll.get(30, TimeUnit.SECONDS);
            assertThat(enrollOutcome).isInstanceOf(DomainException.class);
            assertThat(((DomainException) enrollOutcome).code()).isEqualTo("CREDIT_CAP_EXCEEDED");
            assertThat(activeCredits(student, fixture.semesterId)).isEqualTo(3);
            assertThat(acknowledgements(cancelled)).isZero();
        } finally {
            reopenResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * The opposite interleaving: the enroll lands while the reopen is parked
     * immediately before its affected-student FOR UPDATE — the reopen holds
     * nothing yet — so the commit must land inside the conflict scan, which
     * then refuses with REOPEN_CONFLICTS instead of restoring the cap breach.
     */
    @Test
    void reopenEvaluatesAnEnrollThatCommittedBeforeItsStudentLocks() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;
        String otherSection = otherCourseSection(fixture, "rb");

        registration.enroll(student, cancelled, List.of("STUDENT"), "it-rb-" + cancelled);
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));
        jdbc.update("UPDATE academic.\"RegistrationRound\" SET \"creditLimit\" = 3 WHERE \"id\" = :id",
                new MapSqlParameterSource("id", fixture.roundId));

        CountDownLatch atStudentLock = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        barrierReopenBeforeStudentLocks(atStudentLock, resume);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var reopen = pool.submit(() -> {
                Thread.currentThread().setName(REOPEN_NAME);
                try {
                    catalog.update(SECTION_TABLE, cancelled, Map.of("status", "OPEN"));
                    return "REOPENED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(atStudentLock.await(10, TimeUnit.SECONDS))
                    .as("reopen reached the affected-student lock boundary").isTrue();
            // The reopen holds nothing at the barrier, so this enroll commits
            // first — and the locked scan must now see it.
            registration.enroll(student, otherSection, List.of("STUDENT"), "it-rb-enroll");
            resume.countDown();
            Object outcome = reopen.get(30, TimeUnit.SECONDS);
            assertThat(outcome).isInstanceOf(DomainException.class);
            assertThat(((DomainException) outcome).code()).isEqualTo("REOPEN_CONFLICTS");
            assertThat(sectionStatus(cancelled)).isEqualTo("CANCELLED");
            assertThat(activeCredits(student, fixture.semesterId)).isEqualTo(3);
            assertThat(acknowledgements(cancelled)).isZero();
        } finally {
            resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * The deadlock schedule: the same-course replacement enroll holds the
     * Student FOR UPDATE while the reopen starts. The reopen must wait on
     * that row BEFORE it takes the section write lock — taking it afterwards
     * closes a Student -> Section cycle against the enroll's retire UPDATE
     * and Postgres kills one side with 40P01. Under the right order the
     * enroll retires the preserved seat first, the reopen then finds no
     * active row and lands cleanly.
     */
    @Test
    void sameCourseReplacementSerializesAgainstReopenWithoutDeadlock() throws Exception {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 30);
        String student = fixture.seedStudent(1);
        String cancelled = fixture.sectionId;
        String replacement = secondSection(fixture, 30);

        registration.enroll(student, cancelled, List.of("STUDENT"), "it-rc-" + cancelled);
        catalog.update(SECTION_TABLE, cancelled, Map.of("status", "CANCELLED"));

        CountDownLatch enrollAtEnrollments = new CountDownLatch(1);
        CountDownLatch enrollResume = new CountDownLatch(1);
        CountDownLatch reopenAtStudentLock = new CountDownLatch(1);
        barrierRetireVersusReopen(enrollAtEnrollments, enrollResume, reopenAtStudentLock);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var enroll = pool.submit(() -> {
                Thread.currentThread().setName(ENROLL_NAME);
                try {
                    registration.enroll(student, replacement, List.of("STUDENT"), "it-rc-enroll");
                    return "ENROLLED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            // Parked before its active-enrollment lock read, the enroll
            // already holds Student and the replacement Section.
            assertThat(enrollAtEnrollments.await(10, TimeUnit.SECONDS))
                    .as("enroll parked holding the student lock").isTrue();
            var reopen = pool.submit(() -> {
                Thread.currentThread().setName(REOPEN_NAME);
                try {
                    catalog.update(SECTION_TABLE, cancelled, Map.of("status", "OPEN"));
                    return "REOPENED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(reopenAtStudentLock.await(10, TimeUnit.SECONDS))
                    .as("reopen issued its affected-student lock read").isTrue();
            // It must be queued on the student row — a reopen that holds the
            // section while waiting here would deadlock the retire below.
            assertThat(reopen.isDone()).isFalse();
            enrollResume.countDown();
            Object enrollOutcome = enroll.get(30, TimeUnit.SECONDS);
            Object reopenOutcome = reopen.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(enrollOutcome))
                    .as("enroll finished without a deadlock abort: %s", enrollOutcome).isFalse();
            assertThat(deadlockDetected(reopenOutcome))
                    .as("reopen finished without a deadlock abort: %s", reopenOutcome).isFalse();
            assertThat(enrollOutcome).isEqualTo("ENROLLED");
            assertThat(reopenOutcome).isEqualTo("REOPENED");
            assertThat(preservedStatus(cancelled, student)).isEqualTo("DROPPED");
            assertThat(sectionStatus(cancelled)).isEqualTo("OPEN");
        } finally {
            enrollResume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /** Parks the reopen worker immediately before the post-scan slip DELETE. */
    private void barrierReopenAtSlipDelete(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            if (REOPEN_NAME.equals(Thread.currentThread().getName())
                    && sql.startsWith("DELETE FROM \"academic\".\"RegistrationSlip\"")
                    && sql.contains(":sectionId")) {
                await(paused, resume);
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).update(anyString(), any(SqlParameterSource.class));
    }

    /** Parks the reopen worker immediately before the affected-student FOR UPDATE. */
    private void barrierReopenBeforeStudentLocks(CountDownLatch paused, CountDownLatch resume) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            if (REOPEN_NAME.equals(Thread.currentThread().getName())
                    && sql.contains("\"Student\"") && sql.contains("FOR UPDATE")
                    && sql.contains(":studentIds")) {
                await(paused, resume);
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForList(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc)
                .queryForList(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    /**
     * Two barriers in one schedule: the enroll worker parks before its
     * active-enrollment lock read (already holding Student + the replacement
     * Section), and the reopen worker only signals when it issues its
     * affected-student lock read — the row lock itself then does the waiting.
     */
    private void barrierRetireVersusReopen(CountDownLatch enrollAtEnrollments,
            CountDownLatch enrollResume, CountDownLatch reopenAtStudentLock) {
        Answer<Object> pause = call -> {
            String sql = call.getArgument(0);
            String thread = Thread.currentThread().getName();
            if (ENROLL_NAME.equals(thread) && sql.contains("course.\"credits\"")
                    && sql.contains("FOR UPDATE OF enrollment")) {
                await(enrollAtEnrollments, enrollResume);
            } else if (REOPEN_NAME.equals(thread) && sql.contains("\"Student\"")
                    && sql.contains("FOR UPDATE") && sql.contains(":studentIds")) {
                reopenAtStudentLock.countDown();
            }
            return call.callRealMethod();
        };
        doAnswer(pause).when(namedJdbc).queryForList(anyString(), any(SqlParameterSource.class));
        doAnswer(pause).when(namedJdbc)
                .queryForList(anyString(), any(SqlParameterSource.class), any(Class.class));
    }

    private static void await(CountDownLatch paused, CountDownLatch resume) throws InterruptedException {
        paused.countDown();
        if (!resume.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("race barrier timeout");
        }
    }

    private static boolean deadlockDetected(Object outcome) {
        return outcome instanceof Throwable failure
                && (String.valueOf(failure).contains("deadlock")
                || String.valueOf(failure).contains("40P01"));
    }

    /** A second section on a DIFFERENT course in the same semester. */
    private String otherCourseSection(AcademicEnrollmentPostgresFixture fixture, String tag) {
        String courseId = "course-" + tag + "-" + fixture.sectionId;
        String sectionId = "section-" + tag + "-" + fixture.sectionId;
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"code\",\"name\",\"credits\",\"departmentId\",\"isActive\")"
                        + " VALUES (:id,:code,'Other',3,:dept,TRUE)",
                new MapSqlParameterSource()
                        .addValue("id", courseId)
                        .addValue("code", "X" + tag + fixture.sectionId.substring(fixture.sectionId.length() - 8))
                        .addValue("dept", fixture.departmentId));
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"sectionNumber\",\"courseId\",\"semesterId\","
                        + "\"capacity\",\"enrolledCount\",\"status\",\"version\")"
                        + " VALUES (:id,'09',:courseId,:semesterId,30,0,'OPEN',0)",
                new MapSqlParameterSource()
                        .addValue("id", sectionId)
                        .addValue("courseId", courseId)
                        .addValue("semesterId", fixture.semesterId));
        return sectionId;
    }

    private int activeCredits(String studentId, String semesterId) {
        Integer credits = jdbc.queryForObject(
                "SELECT COALESCE(SUM(course.\"credits\"), 0) FROM academic.\"Enrollment\" e"
                        + " JOIN academic.\"Section\" s ON s.\"id\" = e.\"sectionId\""
                        + " JOIN academic.\"Course\" course ON course.\"id\" = s.\"courseId\""
                        + " WHERE e.\"studentId\" = :st AND e.\"semesterId\" = :sem"
                        + " AND e.\"status\" IN ('ENROLLED', 'PENDING', 'CONFIRMED')"
                        + " AND s.\"status\" <> 'CANCELLED'",
                new MapSqlParameterSource().addValue("st", studentId).addValue("sem", semesterId),
                Integer.class);
        return credits == null ? 0 : credits;
    }

    private int acknowledgements(String sectionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityId\" = :id AND \"action\" = 'SECTION_REOPEN_CONFLICTS_ACKNOWLEDGED'",
                new MapSqlParameterSource("id", sectionId), Integer.class);
        return count == null ? 0 : count;
    }

    private String sectionStatus(String sectionId) {
        return jdbc.queryForObject("SELECT \"status\" FROM academic.\"Section\" WHERE \"id\" = :id",
                new MapSqlParameterSource("id", sectionId), String.class);
    }

    private String preservedStatus(String sectionId, String studentId) {
        return jdbc.queryForObject(
                "SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"sectionId\" = :s AND \"studentId\" = :st",
                new MapSqlParameterSource().addValue("s", sectionId).addValue("st", studentId), String.class);
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
