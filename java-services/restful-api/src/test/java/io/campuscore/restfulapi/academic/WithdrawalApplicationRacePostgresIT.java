package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.academic.registration.service.RegistrationService;
import io.campuscore.restfulapi.academic.registration.service.WithdrawalApplicationService;
import io.campuscore.restfulapi.exception.DomainException;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Real PostgreSQL wait-graph coverage for the approved course-withdrawal
 * (grade W) workflow. Every mutating path in registration must acquire locks
 * in the same order — Student first, then WithdrawalApplication, Enrollment,
 * Section — or an approve racing a cancel/drop closes a deadlock cycle and
 * Postgres kills one side with 40P01. Each race is driven to a deterministic
 * schedule by parking one worker on its first FOR UPDATE call.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=8",
        "deepseek.enabled=false"
})
@ActiveProfiles({"test", "persistence"})
class WithdrawalApplicationRacePostgresIT {

    private static final String REASON = "Withdrawal race coverage needs at least twenty characters.";

    @Autowired private RegistrationService registration;
    @Autowired private WithdrawalApplicationService withdrawals;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    /**
     * Approve vs cancel: the reviewer and the student race the same pending
     * application. Whichever commits first wins; the loser must see the
     * settled status and fail cleanly — never a 40P01 deadlock abort.
     */
    @Test
    void approveVsCancelSerializesWithoutDeadlock() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();
        String applicationId = withdrawals.submit(student, student, enrollment, REASON).id();

        Barrier approve = parkOnFirstLock("wdw-approve");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> approving = pool.submit(() -> {
                Thread.currentThread().setName("wdw-approve");
                try {
                    withdrawals.review(applicationId, "APPROVED", "wd2-reviewer-1", "ok");
                    return "APPROVED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(approve.locked.await(10, TimeUnit.SECONDS)).as("approve parked holding its first lock").isTrue();
            Future<Object> cancelling = pool.submit(() -> {
                Thread.currentThread().setName("wdw-cancel");
                try {
                    withdrawals.cancel(applicationId, student, student);
                    return "CANCELLED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            // Give the cancel worker a moment to take its own first lock (the
            // Student row, free under the old Application-first order) and to
            // reach its blocking call before the approve side resumes.
            Thread.sleep(600);
            approve.resume.countDown();
            Object approveOutcome = approving.get(30, TimeUnit.SECONDS);
            Object cancelOutcome = cancelling.get(30, TimeUnit.SECONDS);

            assertThat(deadlockDetected(approveOutcome))
                    .as("approve finished without a deadlock abort: %s", approveOutcome).isFalse();
            assertThat(deadlockDetected(cancelOutcome))
                    .as("cancel finished without a deadlock abort: %s", cancelOutcome).isFalse();

            String status = applicationStatus(applicationId);
            if ("APPROVED".equals(approveOutcome)) {
                assertThat(status).isEqualTo("APPROVED");
                assertThat(conflict(cancelOutcome)).isTrue();
                assertThat(enrollmentStatus(enrollment)).isEqualTo("WITHDRAWN");
                assertThat(enrolledCount(fixture.sectionId)).isZero();
                assertThat(eventCount(enrollment)).isEqualTo(1);
            } else {
                assertThat(approveOutcome).isInstanceOf(DomainException.class);
                assertThat(status).isEqualTo("CANCELLED");
                assertThat(enrollmentStatus(enrollment)).isEqualTo("ENROLLED");
                assertThat(enrolledCount(fixture.sectionId)).isEqualTo(1);
            }
        } finally {
            approve.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Approve-in-flight vs drop: the approve holds the Student row first, the
     * drop queues behind it, then sees WITHDRAWN and refuses — the seat is
     * released exactly once, by the withdrawal.
     */
    @Test
    void dropBehindApproveSeesWithdrawnAndRefuses() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();
        String applicationId = withdrawals.submit(student, student, enrollment, REASON).id();

        Barrier approve = parkOnFirstLock("wdw-approve");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> approving = pool.submit(() -> {
                Thread.currentThread().setName("wdw-approve");
                try {
                    withdrawals.review(applicationId, "APPROVED", "wd2-reviewer-1", "ok");
                    return "APPROVED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(approve.locked.await(10, TimeUnit.SECONDS)).as("approve parked holding Student").isTrue();
            Future<Object> dropping = pool.submit(() -> {
                Thread.currentThread().setName("wdw-drop");
                try {
                    registration.drop(enrollment, student, List.of("STUDENT"), "wd2-drop-" + enrollment);
                    return "DROPPED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            boolean dropWaiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!dropWaiting && System.nanoTime() < deadline) {
                dropWaiting = waitingOn(approve.pid, "wdw-drop");
                if (!dropWaiting) Thread.sleep(25);
            }
            assertThat(dropWaiting).as("drop queues behind approve's Student lock").isTrue();
            approve.resume.countDown();

            Object approveOutcome = approving.get(30, TimeUnit.SECONDS);
            Object dropOutcome = dropping.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(approveOutcome)).as("approve: %s", approveOutcome).isFalse();
            assertThat(deadlockDetected(dropOutcome)).as("drop: %s", dropOutcome).isFalse();
            assertThat(approveOutcome).isEqualTo("APPROVED");
            assertThat(code(dropOutcome)).isEqualTo("ENROLLMENT_NOT_ACTIVE");
            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            assertThat(enrollmentStatus(enrollment)).isEqualTo("WITHDRAWN");
            assertThat(enrolledCount(fixture.sectionId)).isZero();
            assertThat(eventCount(enrollment)).isEqualTo(1);
        } finally {
            approve.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * The mirror schedule: drop commits first, then the approve sees the
     * DROPPED enrollment under its lock and refuses — the application stays
     * PENDING and no seat is released twice.
     */
    @Test
    void approveBehindDropSeesDroppedEnrollmentAndRefuses() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();
        String applicationId = withdrawals.submit(student, student, enrollment, REASON).id();

        Barrier drop = parkOnFirstLock("wdw-drop");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> dropping = pool.submit(() -> {
                Thread.currentThread().setName("wdw-drop");
                try {
                    registration.drop(enrollment, student, List.of("STUDENT"), "wd2-drop-" + enrollment);
                    return "DROPPED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(drop.locked.await(10, TimeUnit.SECONDS)).as("drop parked holding Student").isTrue();
            Future<Object> approving = pool.submit(() -> {
                Thread.currentThread().setName("wdw-approve");
                try {
                    withdrawals.review(applicationId, "APPROVED", "wd2-reviewer-1", "ok");
                    return "APPROVED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            boolean approveWaiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!approveWaiting && System.nanoTime() < deadline) {
                approveWaiting = waitingOn(drop.pid, "wdw-approve");
                if (!approveWaiting) Thread.sleep(25);
            }
            assertThat(approveWaiting).as("approve queues behind drop's Student lock").isTrue();
            drop.resume.countDown();

            Object dropOutcome = dropping.get(30, TimeUnit.SECONDS);
            Object approveOutcome = approving.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(dropOutcome)).as("drop: %s", dropOutcome).isFalse();
            assertThat(deadlockDetected(approveOutcome)).as("approve: %s", approveOutcome).isFalse();
            assertThat(dropOutcome).isEqualTo("DROPPED");
            assertThat(code(approveOutcome)).isEqualTo("ENROLLMENT_NOT_ACTIVE");
            assertThat(applicationStatus(applicationId)).isEqualTo("PENDING");
            assertThat(enrollmentStatus(enrollment)).isEqualTo("DROPPED");
            assertThat(enrolledCount(fixture.sectionId)).isZero();
            assertThat(eventCount(enrollment)).as("no WITHDRAW event when the drop won").isZero();
        } finally {
            drop.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Two concurrent approvals of the same application: exactly one reviewer
     * wins the PENDING -> APPROVED transition; the seat is released once and
     * only one WITHDRAW event lands.
     */
    @Test
    void twoConcurrentApprovalsSettleExactlyOnce() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();
        String applicationId = withdrawals.submit(student, student, enrollment, REASON).id();

        Barrier first = parkOnFirstLock("wdw-approve-a");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> approveA = pool.submit(() -> {
                Thread.currentThread().setName("wdw-approve-a");
                try {
                    withdrawals.review(applicationId, "APPROVED", "wd2-reviewer-a", "ok");
                    return "APPROVED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(first.locked.await(10, TimeUnit.SECONDS)).as("first approver parked").isTrue();
            Future<Object> approveB = pool.submit(() -> {
                Thread.currentThread().setName("wdw-approve-b");
                try {
                    withdrawals.review(applicationId, "APPROVED", "wd2-reviewer-b", "ok");
                    return "APPROVED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            Thread.sleep(400);
            first.resume.countDown();

            Object outcomeA = approveA.get(30, TimeUnit.SECONDS);
            Object outcomeB = approveB.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(outcomeA)).as("approve-a: %s", outcomeA).isFalse();
            assertThat(deadlockDetected(outcomeB)).as("approve-b: %s", outcomeB).isFalse();
            int approved = ("APPROVED".equals(outcomeA) ? 1 : 0) + ("APPROVED".equals(outcomeB) ? 1 : 0);
            assertThat(approved).as("exactly one approve wins: %s / %s", outcomeA, outcomeB).isEqualTo(1);
            assertThat(code("APPROVED".equals(outcomeA) ? outcomeB : outcomeA))
                    .isEqualTo("WITHDRAWAL_APPLICATION_NOT_PENDING");

            assertThat(applicationStatus(applicationId)).isEqualTo("APPROVED");
            assertThat(enrollmentStatus(enrollment)).isEqualTo("WITHDRAWN");
            assertThat(enrolledCount(fixture.sectionId)).isZero();
            assertThat(eventCount(enrollment)).isEqualTo(1);
        } finally {
            first.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Submit vs drop, drop first: once the drop commits the enrollment is
     * DROPPED and the submit must refuse — no pending withdrawal may hang on
     * a non-active enrollment.
     */
    @Test
    void submitBehindDropSeesDroppedEnrollmentAndRefuses() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();

        Barrier drop = parkOnFirstLock("wdw-drop");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> dropping = pool.submit(() -> {
                Thread.currentThread().setName("wdw-drop");
                try {
                    registration.drop(enrollment, student, List.of("STUDENT"), "wd2-drop-" + enrollment);
                    return "DROPPED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(drop.locked.await(10, TimeUnit.SECONDS)).as("drop parked holding Student").isTrue();
            Future<Object> submitting = pool.submit(() -> {
                Thread.currentThread().setName("wdw-submit");
                try {
                    withdrawals.submit(student, student, enrollment, REASON).id();
                    return "SUBMITTED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            boolean submitWaiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!submitWaiting && System.nanoTime() < deadline) {
                submitWaiting = waitingOn(drop.pid, "wdw-submit");
                if (!submitWaiting) Thread.sleep(25);
            }
            assertThat(submitWaiting).as("submit queues behind drop's Student lock").isTrue();
            drop.resume.countDown();

            Object dropOutcome = dropping.get(30, TimeUnit.SECONDS);
            Object submitOutcome = submitting.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(dropOutcome)).as("drop: %s", dropOutcome).isFalse();
            assertThat(deadlockDetected(submitOutcome)).as("submit: %s", submitOutcome).isFalse();
            assertThat(dropOutcome).isEqualTo("DROPPED");
            assertThat(code(submitOutcome)).isEqualTo("ENROLLMENT_NOT_ACTIVE");
            assertThat(pendingCount(enrollment)).isZero();
            assertThat(enrolledCount(fixture.sectionId)).isZero();
        } finally {
            drop.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Submit vs drop, submit first: the application lands PENDING and the
     * drop then proceeds on the still-active enrollment — both committed,
     * seat released by the drop, application left for the reviewer.
     */
    @Test
    void dropBehindSubmitLeavesPendingApplicationOnDroppedEnrollment() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();

        Barrier submit = parkOnFirstLock("wdw-submit");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> submitting = pool.submit(() -> {
                Thread.currentThread().setName("wdw-submit");
                try {
                    withdrawals.submit(student, student, enrollment, REASON).id();
                    return "SUBMITTED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(submit.locked.await(10, TimeUnit.SECONDS)).as("submit parked holding Student").isTrue();
            Future<Object> dropping = pool.submit(() -> {
                Thread.currentThread().setName("wdw-drop");
                try {
                    registration.drop(enrollment, student, List.of("STUDENT"), "wd2-drop-" + enrollment);
                    return "DROPPED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            boolean dropWaiting = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
            while (!dropWaiting && System.nanoTime() < deadline) {
                dropWaiting = waitingOn(submit.pid, "wdw-drop");
                if (!dropWaiting) Thread.sleep(25);
            }
            assertThat(dropWaiting).as("drop queues behind submit's Student lock").isTrue();
            submit.resume.countDown();

            Object submitOutcome = submitting.get(30, TimeUnit.SECONDS);
            Object dropOutcome = dropping.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(submitOutcome)).as("submit: %s", submitOutcome).isFalse();
            assertThat(deadlockDetected(dropOutcome)).as("drop: %s", dropOutcome).isFalse();
            assertThat(submitOutcome).isEqualTo("SUBMITTED");
            assertThat(dropOutcome).isEqualTo("DROPPED");
            assertThat(pendingCount(enrollment)).isEqualTo(1);
            assertThat(enrollmentStatus(enrollment)).isEqualTo("DROPPED");
            assertThat(enrolledCount(fixture.sectionId)).isZero();
        } finally {
            submit.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    /**
     * Two concurrent submits of the same enrollment: the Student lock
     * serializes them, the second sees the committed PENDING row (or hits the
     * partial unique index) and fails WITHDRAWAL_APPLICATION_EXISTS.
     */
    @Test
    void twoConcurrentSubmitsCreateExactlyOnePendingApplication() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 4);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "wd2-seed-" + student).id();

        Barrier first = parkOnFirstLock("wdw-submit-a");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> submitA = pool.submit(() -> {
                Thread.currentThread().setName("wdw-submit-a");
                try {
                    withdrawals.submit(student, student, enrollment, REASON).id();
                    return "SUBMITTED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            assertThat(first.locked.await(10, TimeUnit.SECONDS)).as("submit-a parked holding Student").isTrue();
            Future<Object> submitB = pool.submit(() -> {
                Thread.currentThread().setName("wdw-submit-b");
                try {
                    withdrawals.submit(student, student, enrollment, REASON).id();
                    return "SUBMITTED";
                } catch (RuntimeException failure) {
                    return failure;
                }
            });
            Thread.sleep(400);
            first.resume.countDown();

            Object outcomeA = submitA.get(30, TimeUnit.SECONDS);
            Object outcomeB = submitB.get(30, TimeUnit.SECONDS);
            assertThat(deadlockDetected(outcomeA)).as("submit-a: %s", outcomeA).isFalse();
            assertThat(deadlockDetected(outcomeB)).as("submit-b: %s", outcomeB).isFalse();
            int submitted = ("SUBMITTED".equals(outcomeA) ? 1 : 0) + ("SUBMITTED".equals(outcomeB) ? 1 : 0);
            assertThat(submitted).as("exactly one submit wins: %s / %s", outcomeA, outcomeB).isEqualTo(1);
            assertThat(code("SUBMITTED".equals(outcomeA) ? outcomeB : outcomeA))
                    .isEqualTo("WITHDRAWAL_APPLICATION_EXISTS");
            assertThat(pendingCount(enrollment)).isEqualTo(1);
        } finally {
            first.resume.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    // ---------- instrumentation helpers ----------

    /**
     * Parks the named worker inside its first FOR UPDATE call: the lock it
     * just took is held while the test drives the competitor to its own
     * blocking call, then {@code resume} releases it.
     */
    private Barrier parkOnFirstLock(String worker) {
        Barrier barrier = new Barrier();
        AtomicBoolean once = new AtomicBoolean();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (worker.equals(Thread.currentThread().getName())
                    && sql.contains("FOR UPDATE")
                    && once.compareAndSet(false, true)) {
                barrier.pid.set(backendPid());
                barrier.locked.countDown();
                if (!barrier.resume.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(worker + " barrier timeout");
                }
            }
            return result;
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (worker.equals(Thread.currentThread().getName())
                    && sql.contains("FOR UPDATE")
                    && once.compareAndSet(false, true)) {
                barrier.pid.set(backendPid());
                barrier.locked.countDown();
                if (!barrier.resume.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(worker + " barrier timeout");
                }
            }
            return result;
        }).when(jdbc).queryForList(anyString(), any(SqlParameterSource.class));
        return barrier;
    }

    /** True once some backend is blocked by {@code ownerPid}. */
    private boolean waitingOn(AtomicInteger ownerPid, String waiterName) {
        if (ownerPid.get() == 0) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_stat_activity waiter"
                        + " WHERE waiter.wait_event_type = 'Lock'"
                        + " AND :owner = ANY(pg_blocking_pids(waiter.pid)))",
                Map.of("owner", ownerPid.get()), Boolean.class));
    }

    private int backendPid() {
        Integer pid = jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class);
        return pid == null ? 0 : pid;
    }

    private static boolean deadlockDetected(Object outcome) {
        return outcome instanceof Throwable failure
                && (String.valueOf(failure).contains("deadlock")
                || String.valueOf(failure).contains("40P01"));
    }

    private static boolean conflict(Object outcome) {
        return outcome instanceof DomainException domain
                && domain.getStatus() == HttpStatus.CONFLICT;
    }

    private static String code(Object outcome) {
        return outcome instanceof DomainException domain ? domain.code() : String.valueOf(outcome);
    }

    private String applicationStatus(String id) {
        return jdbc.queryForObject(
                "SELECT \"status\" FROM academic.\"WithdrawalApplication\" WHERE \"id\" = :id",
                Map.of("id", id), String.class);
    }

    private String enrollmentStatus(String id) {
        return jdbc.queryForObject(
                "SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"id\" = :id",
                Map.of("id", id), String.class);
    }

    private int enrolledCount(String sectionId) {
        Integer count = jdbc.queryForObject(
                "SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\" = :id",
                Map.of("id", sectionId), Integer.class);
        return count == null ? -1 : count;
    }

    private int eventCount(String enrollmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"EnrollmentEvent\""
                        + " WHERE \"enrollmentId\" = :id AND \"action\" = 'WITHDRAW'",
                Map.of("id", enrollmentId), Integer.class);
        return count == null ? -1 : count;
    }

    private int pendingCount(String enrollmentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM academic.\"WithdrawalApplication\""
                        + " WHERE \"enrollmentId\" = :id AND \"status\" = 'PENDING'",
                Map.of("id", enrollmentId), Integer.class);
        return count == null ? -1 : count;
    }

    private static final class Barrier {
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        final AtomicInteger pid = new AtomicInteger();
    }

    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
