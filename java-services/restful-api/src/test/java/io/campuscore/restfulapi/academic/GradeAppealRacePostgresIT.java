package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.academic.dto.AcademicMutationDtos.GradeUpdate;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.CreateRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.ReviewRequest;
import io.campuscore.restfulapi.academic.dto.GradeAppealDtos.UnpublishRequest;
import io.campuscore.restfulapi.academic.service.AcademicMutationService;
import io.campuscore.restfulapi.academic.service.GradeAppealService;
import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Deterministic PostgreSQL wait-graph coverage for appeal resolution and grade writes. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=8",
        "deepseek.enabled=false"
})
@ActiveProfiles({"test", "persistence"})
class GradeAppealRacePostgresIT {

    private static final String APPEAL_REASON = "Please review this final assessment and marking record.";
    private static final String REVIEW_REASON = "The verified marking record supports this review decision.";

    @Autowired private RegistrationService registration;
    @Autowired private GradeAppealService appeals;
    @Autowired private AcademicMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void reviewVersusCancelSerializesOnStudentAndSettlesOnce() throws Exception {
        AppealFixture fixture = publishedAppeal();
        Barrier review = parkOnFirstLock("appeal-review");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> resolving = pool.submit(() -> run("appeal-review", () -> {
                appeals.review(fixture.appealId, new ReviewRequest("ACCEPTED", new BigDecimal("9.00"), REVIEW_REASON),
                        "it-reviewer", null, true);
                return "ACCEPTED";
            }));
            assertThat(review.locked.await(10, TimeUnit.SECONDS)).as("review holds the Student row").isTrue();
            Future<Object> cancelling = pool.submit(() -> run("appeal-cancel", () -> {
                appeals.cancel(fixture.appealId, fixture.studentId, fixture.studentUserId);
                return "CANCELLED";
            }));
            assertThat(waitingOn(review.pid)).as("cancel waits behind review's Student lock").isTrue();
            review.resume.countDown();

            Object reviewOutcome = resolving.get(30, TimeUnit.SECONDS);
            Object cancelOutcome = cancelling.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(reviewOutcome, cancelOutcome);
            assertThat(reviewOutcome).isEqualTo("ACCEPTED");
            assertThat(code(cancelOutcome)).isEqualTo("GRADE_APPEAL_NOT_PENDING");
            assertThat(appealStatus(fixture.appealId)).isEqualTo("ACCEPTED");
            assertThat(gradeStatus(fixture.enrollmentId)).isEqualTo("PUBLISHED");
            assertThat(finalGrade(fixture.enrollmentId)).isEqualByComparingTo("9.00");
        } finally {
            review.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentDoubleSubmitLeavesOnePendingAppeal() throws Exception {
        AppealFixture fixture = publishedEnrollment();
        Barrier first = parkOnFirstLock("appeal-submit-a");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> submitA = pool.submit(() -> run("appeal-submit-a", () -> {
                appeals.submit(fixture.studentId, fixture.studentUserId,
                        new CreateRequest(fixture.enrollmentId, APPEAL_REASON));
                return "SUBMITTED";
            }));
            assertThat(first.locked.await(10, TimeUnit.SECONDS)).as("first submit holds the Student row").isTrue();
            Future<Object> submitB = pool.submit(() -> run("appeal-submit-b", () -> {
                appeals.submit(fixture.studentId, fixture.studentUserId,
                        new CreateRequest(fixture.enrollmentId, APPEAL_REASON));
                return "SUBMITTED";
            }));
            assertThat(waitingOn(first.pid)).as("second submit waits behind the first Student lock").isTrue();
            first.resume.countDown();

            Object outcomeA = submitA.get(30, TimeUnit.SECONDS);
            Object outcomeB = submitB.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(outcomeA, outcomeB);
            int successes = ("SUBMITTED".equals(outcomeA) ? 1 : 0) + ("SUBMITTED".equals(outcomeB) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(code("SUBMITTED".equals(outcomeA) ? outcomeB : outcomeA))
                    .isEqualTo("GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
            assertThat(appealCount(fixture.enrollmentId)).isEqualTo(1);
            assertThat(pendingAppealCount(fixture.enrollmentId)).isEqualTo(1);
            assertThat(gradeStatus(fixture.enrollmentId)).isEqualTo("APPEALED");
        } finally {
            first.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twoConcurrentResolutionsHaveOneWinnerAndOneAuditTransition() throws Exception {
        AppealFixture fixture = publishedAppeal();
        Barrier first = parkOnFirstLock("appeal-resolve-a");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> accept = pool.submit(() -> run("appeal-resolve-a", () -> {
                appeals.review(fixture.appealId, new ReviewRequest("ACCEPTED", new BigDecimal("9.00"), REVIEW_REASON),
                        "it-reviewer-a", null, true);
                return "ACCEPTED";
            }));
            assertThat(first.locked.await(10, TimeUnit.SECONDS)).as("first resolution holds the Student row").isTrue();
            Future<Object> reject = pool.submit(() -> run("appeal-resolve-b", () -> {
                appeals.review(fixture.appealId, new ReviewRequest("REJECTED", null, REVIEW_REASON),
                        "it-reviewer-b", null, true);
                return "REJECTED";
            }));
            assertThat(waitingOn(first.pid)).as("second resolution waits behind the first Student lock").isTrue();
            first.resume.countDown();

            Object acceptOutcome = accept.get(30, TimeUnit.SECONDS);
            Object rejectOutcome = reject.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(acceptOutcome, rejectOutcome);
            int successes = ("ACCEPTED".equals(acceptOutcome) ? 1 : 0) + ("REJECTED".equals(rejectOutcome) ? 1 : 0);
            assertThat(successes).isEqualTo(1);
            assertThat(code("ACCEPTED".equals(acceptOutcome) ? rejectOutcome : acceptOutcome))
                    .isEqualTo("GRADE_APPEAL_NOT_PENDING");
            assertThat(appealStatus(fixture.appealId)).isIn("ACCEPTED", "REJECTED");
            assertThat(auditCount(fixture.appealId, "GRADE_APPEAL_ACCEPTED")
                    + auditCount(fixture.appealId, "GRADE_APPEAL_REJECTED")).isEqualTo(1);
        } finally {
            first.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void publishedGradeEditRacingFirstAppealIsRejected() throws Exception {
        AppealFixture fixture = publishedEnrollment();
        Barrier edit = parkOnFirstLock("appeal-edit");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> editing = pool.submit(() -> run("appeal-edit", () -> {
                mutations.updateGrades(fixture.sectionId, null, true, "it-admin",
                        List.of(new GradeUpdate(fixture.enrollmentId, new BigDecimal("8.00"), new BigDecimal("9.00"))));
                return "EDITED";
            }));
            assertThat(edit.locked.await(10, TimeUnit.SECONDS)).as("grade edit holds the Student row").isTrue();
            Future<Object> submitting = pool.submit(() -> run("appeal-submit", () -> {
                appeals.submit(fixture.studentId, fixture.studentUserId,
                        new CreateRequest(fixture.enrollmentId, APPEAL_REASON));
                return "SUBMITTED";
            }));
            assertThat(waitingOn(edit.pid)).as("appeal submit waits behind grade edit's Student lock").isTrue();
            edit.resume.countDown();

            Object editOutcome = editing.get(30, TimeUnit.SECONDS);
            Object submitOutcome = submitting.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(editOutcome, submitOutcome);
            assertThat(code(editOutcome)).isEqualTo("GRADES_PUBLISHED_LOCKED");
            assertThat(submitOutcome).isEqualTo("SUBMITTED");
            assertThat(gradeStatus(fixture.enrollmentId)).isEqualTo("APPEALED");
            assertThat(finalGrade(fixture.enrollmentId)).isEqualByComparingTo("8.00");
        } finally {
            edit.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void unpublishVersusFirstAppealLeavesExactlyOneValidOutcome() throws Exception {
        AppealFixture fixture = publishedEnrollment();
        Barrier unpublish = parkOnFirstLock("appeal-unpublish");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> removing = pool.submit(() -> run("appeal-unpublish", () -> {
                return appeals.unpublish(fixture.sectionId,
                        new UnpublishRequest(List.of(fixture.enrollmentId), REVIEW_REASON), "it-admin")
                        .unpublishedCount();
            }));
            assertThat(unpublish.locked.await(10, TimeUnit.SECONDS))
                    .as("unpublish holds the Student row before taking Section").isTrue();
            Future<Object> submitting = pool.submit(() -> run("appeal-submit", () -> {
                appeals.submit(fixture.studentId, fixture.studentUserId,
                        new CreateRequest(fixture.enrollmentId, APPEAL_REASON));
                return "SUBMITTED";
            }));
            assertThat(waitingOn(unpublish.pid)).as("appeal submit waits behind unpublish's Student lock").isTrue();
            unpublish.resume.countDown();

            Object unpublishOutcome = removing.get(30, TimeUnit.SECONDS);
            Object submitOutcome = submitting.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(unpublishOutcome, submitOutcome);
            assertThat(unpublishOutcome).isEqualTo(1);
            assertThat(code(submitOutcome)).isEqualTo("GRADE_APPEAL_ENROLLMENT_INELIGIBLE");
            assertThat(gradeStatus(fixture.enrollmentId)).isEqualTo("DRAFT");
            assertThat(appealCount(fixture.enrollmentId)).isZero();
        } finally {
            unpublish.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void sectionUnpublishRefreshesPublishedGradesAfterWaitingForPublisher() throws Exception {
        SectionPublicationFixture fixture = sectionWithOnePublishedAndOneDraftGrade();
        Barrier publish = parkOnFirstLock("appeal-publish");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> publishing = pool.submit(() -> run("appeal-publish", () -> {
                mutations.publishGrades(fixture.sectionId, null, true, "it-admin");
                return "PUBLISHED";
            }));
            assertThat(publish.locked.await(10, TimeUnit.SECONDS)).as("publisher holds the Section row").isTrue();
            Future<Object> unpublishing = pool.submit(() -> run("appeal-unpublish", () -> {
                return appeals.unpublish(fixture.sectionId,
                        new UnpublishRequest(List.of(), REVIEW_REASON), "it-admin").unpublishedCount();
            }));
            assertThat(waitingOn(publish.pid)).as("unpublish waits behind the publisher's Section lock").isTrue();
            publish.resume.countDown();

            Object publishOutcome = publishing.get(30, TimeUnit.SECONDS);
            Object unpublishOutcome = unpublishing.get(30, TimeUnit.SECONDS);
            assertNoDeadlock(publishOutcome, unpublishOutcome);
            assertThat(publishOutcome).isEqualTo("PUBLISHED");
            assertThat(unpublishOutcome).isEqualTo(2);
            assertThat(gradeStatus(fixture.firstEnrollmentId)).isEqualTo("DRAFT");
            assertThat(gradeStatus(fixture.secondEnrollmentId)).isEqualTo("DRAFT");
        } finally {
            publish.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private AppealFixture publishedAppeal() {
        AppealFixture fixture = publishedEnrollment();
        String appealId = appeals.submit(fixture.studentId, fixture.studentUserId,
                new CreateRequest(fixture.enrollmentId, APPEAL_REASON)).id();
        return new AppealFixture(fixture.sectionId, fixture.studentId, fixture.studentUserId,
                fixture.enrollmentId, appealId);
    }

    private AppealFixture publishedEnrollment() {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String studentId = fixture.seedStudent(1);
        String userId = jdbc.queryForObject("SELECT \"userId\" FROM academic.\"Student\" WHERE \"id\"=:id",
                Map.of("id", studentId), String.class);
        String enrollmentId = registration.enroll(studentId, fixture.sectionId, List.of("STUDENT"),
                "appeal-seed-" + fixture.sectionId).id();
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='COMPLETED', \"gradeStatus\"='PUBLISHED',"
                        + " \"finalGrade\"=8.00, \"letterGrade\"='B', \"gradePublishedAt\"=:publishedAt"
                        + " WHERE \"id\"=:id",
                Map.of("id", enrollmentId, "publishedAt", Timestamp.from(Instant.now().minusSeconds(3600))));
        return new AppealFixture(fixture.sectionId, studentId, userId, enrollmentId, null);
    }

    private SectionPublicationFixture sectionWithOnePublishedAndOneDraftGrade() {
        AcademicEnrollmentPostgresFixture fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String firstStudentId = fixture.seedStudent(1);
        String secondStudentId = fixture.seedStudent(2);
        String firstEnrollmentId = registration.enroll(firstStudentId, fixture.sectionId, List.of("STUDENT"),
                "appeal-publish-seed-a-" + fixture.sectionId).id();
        String secondEnrollmentId = registration.enroll(secondStudentId, fixture.sectionId, List.of("STUDENT"),
                "appeal-publish-seed-b-" + fixture.sectionId).id();
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='COMPLETED', \"gradeStatus\"='PUBLISHED',"
                        + " \"finalGrade\"=8.00, \"letterGrade\"='B', \"gradePublishedAt\"=:publishedAt"
                        + " WHERE \"id\"=:id",
                Map.of("id", firstEnrollmentId, "publishedAt", Timestamp.from(Instant.now().minusSeconds(3600))));
        jdbc.update("UPDATE academic.\"Enrollment\" SET \"status\"='COMPLETED', \"gradeStatus\"='DRAFT',"
                        + " \"finalGrade\"=8.00, \"letterGrade\"='B', \"gradePublishedAt\"=NULL"
                        + " WHERE \"id\"=:id", Map.of("id", secondEnrollmentId));
        String processItemId = "process-" + UUID.randomUUID();
        String finalItemId = "final-" + UUID.randomUUID();
        jdbc.update("INSERT INTO academic.\"GradeItem\" (\"id\", \"sectionId\", \"name\", \"type\","
                        + " \"maxScore\", \"weight\") VALUES (:id, :sectionId, 'Process', 'PROCESS', 10, 50),"
                        + " (:finalId, :sectionId, 'Final', 'FINAL', 10, 50)",
                Map.of("id", processItemId, "finalId", finalItemId, "sectionId", fixture.sectionId));
        for (String enrollmentId : List.of(firstEnrollmentId, secondEnrollmentId)) {
            jdbc.update("INSERT INTO academic.\"StudentGrade\" (\"id\", \"enrollmentId\", \"gradeItemId\", \"score\")"
                            + " VALUES (:processGrade, :enrollmentId, :processItem, 8),"
                            + " (:finalGrade, :enrollmentId, :finalItem, 8)",
                    Map.of("processGrade", "process-grade-" + UUID.randomUUID(),
                            "finalGrade", "final-grade-" + UUID.randomUUID(), "enrollmentId", enrollmentId,
                            "processItem", processItemId, "finalItem", finalItemId));
        }
        return new SectionPublicationFixture(fixture.sectionId, firstEnrollmentId, secondEnrollmentId);
    }

    private String userFor(String studentId) {
        return jdbc.queryForObject("SELECT \"userId\" FROM academic.\"Student\" WHERE \"id\"=:id",
                Map.of("id", studentId), String.class);
    }

    private Barrier parkOnFirstLock(String worker) {
        Barrier barrier = new Barrier();
        AtomicBoolean once = new AtomicBoolean();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            parkIfNeeded(worker, barrier, once, call.getArgument(0));
            return result;
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(call -> {
            Object result = call.callRealMethod();
            parkIfNeeded(worker, barrier, once, call.getArgument(0));
            return result;
        }).when(jdbc).queryForList(anyString(), any(SqlParameterSource.class));
        return barrier;
    }

    private void parkIfNeeded(String worker, Barrier barrier, AtomicBoolean once, String sql) throws Exception {
        if (worker.equals(Thread.currentThread().getName()) && sql.contains("FOR UPDATE")
                && once.compareAndSet(false, true)) {
            barrier.pid.set(backendPid());
            barrier.locked.countDown();
            if (!barrier.resume.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException(worker + " lock barrier timed out");
            }
        }
    }

    private Object run(String name, java.util.concurrent.Callable<Object> action) {
        Thread.currentThread().setName(name);
        try {
            return action.call();
        } catch (Exception failure) {
            return failure;
        }
    }

    private boolean waitingOn(AtomicInteger ownerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM pg_stat_activity waiter"
                            + " WHERE waiter.wait_event_type = 'Lock'"
                            + " AND :owner = ANY(pg_blocking_pids(waiter.pid)))",
                    Map.of("owner", ownerPid.get()), Boolean.class);
            if (Boolean.TRUE.equals(waiting)) return true;
            Thread.sleep(25);
        }
        return false;
    }

    private int backendPid() {
        Integer pid = jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class);
        return pid == null ? 0 : pid;
    }

    private String appealStatus(String id) {
        return jdbc.queryForObject("SELECT \"status\" FROM academic.\"GradeAppeal\" WHERE \"id\"=:id",
                Map.of("id", id), String.class);
    }

    private String gradeStatus(String id) {
        return jdbc.queryForObject("SELECT \"gradeStatus\" FROM academic.\"Enrollment\" WHERE \"id\"=:id",
                Map.of("id", id), String.class);
    }

    private BigDecimal finalGrade(String id) {
        return jdbc.queryForObject("SELECT \"finalGrade\" FROM academic.\"Enrollment\" WHERE \"id\"=:id",
                Map.of("id", id), BigDecimal.class);
    }

    private int auditCount(String appealId, String action) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\""
                        + " WHERE \"entityType\"='GradeAppeal' AND \"entityId\"=:id AND \"action\"=:action",
                Map.of("id", appealId, "action", action), Integer.class);
        return count == null ? 0 : count;
    }

    private int appealCount(String enrollmentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"GradeAppeal\" WHERE \"enrollmentId\"=:id",
                Map.of("id", enrollmentId), Integer.class);
        return count == null ? 0 : count;
    }

    private int pendingAppealCount(String enrollmentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM academic.\"GradeAppeal\""
                        + " WHERE \"enrollmentId\"=:id AND \"status\"='PENDING'",
                Map.of("id", enrollmentId), Integer.class);
        return count == null ? 0 : count;
    }

    private static void assertNoDeadlock(Object... outcomes) {
        for (Object outcome : outcomes) {
            assertThat(outcome instanceof Throwable failure
                    && (String.valueOf(failure).contains("deadlock") || String.valueOf(failure).contains("40P01")))
                    .as("outcome is not a Postgres deadlock: %s", outcome).isFalse();
        }
    }

    private static String code(Object outcome) {
        return outcome instanceof DomainException domain ? domain.code() : String.valueOf(outcome);
    }

    private static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record AppealFixture(String sectionId, String studentId, String studentUserId,
            String enrollmentId, String appealId) { }

    private record SectionPublicationFixture(String sectionId, String firstEnrollmentId, String secondEnrollmentId) { }

    private static final class Barrier {
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicInteger pid = new AtomicInteger();
    }
}
