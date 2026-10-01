package io.campuscore.restfulapi.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.academic.registration.RegistrationService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

/** Real PostgreSQL wait graph: enroll holds Student while drop starts.
 * The old drop held Enrollment before waiting for Student, creating a cycle
 * when enroll subsequently locked its active enrollments. No production hook.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.hikari.maximum-pool-size=8",
        "deepseek.enabled=false"
})
@ActiveProfiles({"test", "persistence"})
class AcademicEnrollmentDropLockOrderPostgresIT {
    @Autowired private RegistrationService registration;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void sameStudentCanEnrollAndDropConcurrentlyWithoutLockInversion() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 10);
        String student = fixture.seedStudent(1);
        String enrollment = registration.enroll(student, fixture.sectionId, List.of("STUDENT"), "seed-" + student).id();
        String nextCourse = fixture.courseId + "-next";
        String nextSection = fixture.sectionId + "-next";
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"code\",\"name\",\"credits\",\"departmentId\",\"isActive\")"
                + " VALUES (:id,:id,'Concurrent next course',3,:department,TRUE)",
                Map.of("id", nextCourse, "department", fixture.departmentId));
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"sectionNumber\",\"courseId\",\"semesterId\",\"capacity\",\"enrolledCount\",\"status\")"
                + " VALUES (:id,'02',:course,:semester,10,0,'OPEN')",
                Map.of("id", nextSection, "course", nextCourse, "semester", fixture.semesterId));

        CountDownLatch studentLocked = new CountDownLatch(1);
        CountDownLatch releaseEnroll = new CountDownLatch(1);
        AtomicInteger enrollPid = new AtomicInteger();
        AtomicInteger dropPid = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            String worker = Thread.currentThread().getName();
            if (worker.equals("audit-drop")) {
                dropPid.compareAndSet(0, jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
            }
            Object result = call.callRealMethod();
            if (worker.equals("audit-enroll") && sql.contains("academic.\"Student\"") && sql.contains("FOR UPDATE")) {
                enrollPid.set(jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
                studentLocked.countDown();
                if (!releaseEnroll.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("enroll barrier timeout");
            }
            return result;
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> enrolling = pool.submit(() -> {
                Thread.currentThread().setName("audit-enroll");
                registration.enroll(student, nextSection, List.of("STUDENT"), "next-" + student);
            });
            assertThat(studentLocked.await(10, TimeUnit.SECONDS)).as("enroll owns the Student row").isTrue();
            Future<?> dropping = pool.submit(() -> {
                Thread.currentThread().setName("audit-drop");
                registration.drop(enrollment, student, List.of("STUDENT"), "drop-" + student);
            });
            boolean waitingOnStudent = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!waitingOnStudent && System.nanoTime() < deadline) {
                if (dropPid.get() != 0) {
                    waitingOnStudent = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT :owner = ANY(pg_blocking_pids(:waiter))",
                            Map.of("owner", enrollPid.get(), "waiter", dropPid.get()), Boolean.class));
                }
                if (!waitingOnStudent) Thread.sleep(25);
            }
            assertThat(waitingOnStudent).as("Postgres proves drop is waiting for enroll's Student lock").isTrue();
            releaseEnroll.countDown();
            enrolling.get(20, TimeUnit.SECONDS);
            dropping.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"id\"=:id",
                    Map.of("id", enrollment), String.class)).isEqualTo("DROPPED");
            assertThat(jdbc.queryForObject("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\"=:id",
                    Map.of("id", fixture.sectionId), Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\"=:id",
                    Map.of("id", nextSection), Integer.class)).isEqualTo(1);
        } finally {
            releaseEnroll.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void differentStudentsCanEnrollAndDropASharedSectionWithoutRoundSectionCycle() throws Exception {
        var fixture = AcademicEnrollmentPostgresFixture.seed(jdbc, 10);
        String enrollingStudent = fixture.seedStudent(1);
        String droppingStudent = fixture.seedStudent(2);
        registration.enroll(enrollingStudent, fixture.sectionId, List.of("STUDENT"), "seed-" + enrollingStudent);
        String droppingEnrollment = registration.enroll(droppingStudent, fixture.sectionId,
                List.of("STUDENT"), "seed-" + droppingStudent).id();
        String nextCourse = fixture.courseId + "-next";
        String nextSection = fixture.sectionId + "-next";
        jdbc.update("INSERT INTO academic.\"Course\" (\"id\",\"code\",\"name\",\"credits\",\"departmentId\",\"isActive\")"
                + " VALUES (:id,:id,'Concurrent next course',3,:department,TRUE)",
                Map.of("id", nextCourse, "department", fixture.departmentId));
        jdbc.update("INSERT INTO academic.\"Section\" (\"id\",\"sectionNumber\",\"courseId\",\"semesterId\",\"capacity\",\"enrolledCount\",\"status\")"
                + " VALUES (:id,'02',:course,:semester,10,0,'OPEN')",
                Map.of("id", nextSection, "course", nextCourse, "semester", fixture.semesterId));

        CountDownLatch roundLocked = new CountDownLatch(1);
        CountDownLatch releaseEnroll = new CountDownLatch(1);
        AtomicInteger enrollPid = new AtomicInteger();
        AtomicInteger dropPid = new AtomicInteger();
        doAnswer(call -> {
            Object result = call.callRealMethod();
            String sql = call.getArgument(0);
            if (Thread.currentThread().getName().equals("audit-enroll-shared")
                    && sql.contains("academic.\"RegistrationRound\"") && sql.contains("FOR UPDATE")) {
                enrollPid.set(jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
                roundLocked.countDown();
                if (!releaseEnroll.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("round barrier timeout");
            }
            return result;
        }).when(jdbc).queryForList(anyString(), any(SqlParameterSource.class));
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("audit-drop-shared")) {
                dropPid.compareAndSet(0, jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
            }
            return call.callRealMethod();
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> enrolling = pool.submit(() -> {
                Thread.currentThread().setName("audit-enroll-shared");
                registration.enroll(enrollingStudent, nextSection, List.of("STUDENT"), "next-" + enrollingStudent);
            });
            assertThat(roundLocked.await(10, TimeUnit.SECONDS)).as("enroll owns the registration round").isTrue();
            Future<?> dropping = pool.submit(() -> {
                Thread.currentThread().setName("audit-drop-shared");
                registration.drop(droppingEnrollment, droppingStudent, List.of("STUDENT"), "drop-" + droppingStudent);
            });
            boolean waitingOnRound = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!waitingOnRound && System.nanoTime() < deadline) {
                if (dropPid.get() != 0) {
                    waitingOnRound = Boolean.TRUE.equals(jdbc.queryForObject(
                            "SELECT :owner = ANY(pg_blocking_pids(:waiter))",
                            Map.of("owner", enrollPid.get(), "waiter", dropPid.get()), Boolean.class));
                }
                if (!waitingOnRound) Thread.sleep(25);
            }
            assertThat(waitingOnRound).as("drop owns the shared Section and waits on enroll's Round lock").isTrue();
            releaseEnroll.countDown();
            enrolling.get(20, TimeUnit.SECONDS);
            dropping.get(20, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT \"status\" FROM academic.\"Enrollment\" WHERE \"id\"=:id",
                    Map.of("id", droppingEnrollment), String.class)).isEqualTo("DROPPED");
            assertThat(jdbc.queryForObject("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\"=:id",
                    Map.of("id", fixture.sectionId), Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT \"enrolledCount\" FROM academic.\"Section\" WHERE \"id\"=:id",
                    Map.of("id", nextSection), Integer.class)).isEqualTo(1);
        } finally {
            releaseEnroll.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
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
