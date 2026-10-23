package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.web.DomainException;
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
 * Real transactional service calls, paused after the restore's cross-round
 * seat count. The registration side must be seen waiting on the member's
 * Student row lock the restore holds, and afterwards no student may sit in
 * two live rounds at once.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisRoundRestoreRacePostgresIT {
    @Autowired private ThesisMutationService mutations;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void aRestoreCannotSeatAStudentTwiceWhenARegistrationOverlapsIt() throws Exception {
        String student = jdbc.queryForObject(
                "SELECT s.\"id\" FROM campuscore_auth.\"Student\" s WHERE s.\"status\" = 'ACTIVE' "
                        + "AND NOT EXISTS (SELECT 1 FROM thesis.thesis_group_member m "
                        + "WHERE m.student_id = s.\"id\" AND m.active_participation) "
                        + "ORDER BY s.\"id\" LIMIT 1",
                Map.of(), String.class);
        assertThat(student).as("seed must carry an active student without an active seat").isNotBlank();

        UUID cancelledRound = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_registration_round SELECT (jsonb_populate_record("
                        + "NULL::thesis.thesis_registration_round,to_jsonb(r)||jsonb_build_object('id',CAST(:id AS text),"
                        + "'name','Restore race source','status','CANCELLED',"
                        + "'cancelled_from_status','REGISTRATION_CLOSED'))).*"
                        + " FROM (SELECT * FROM thesis.thesis_registration_round LIMIT 1) r",
                Map.of("id", cancelledRound));
        UUID openRound = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_registration_round SELECT (jsonb_populate_record("
                        + "NULL::thesis.thesis_registration_round,to_jsonb(r)||jsonb_build_object('id',CAST(:id AS text),"
                        + "'name','Restore race target','status','REGISTRATION_OPEN','cancelled_from_status',NULL))).*"
                        + " FROM (SELECT * FROM thesis.thesis_registration_round LIMIT 1) r",
                Map.of("id", openRound));
        jdbc.update("UPDATE thesis.thesis_registration_round SET "
                        + "lecturer_submit_start = now() - interval '30 days', "
                        + "lecturer_submit_end = now() - interval '1 day', "
                        + "registration_start = now() - interval '1 day', "
                        + "registration_end = now() + interval '7 days', "
                        + "gvpb_deadline = now() + interval '30 days' WHERE id = :id",
                Map.of("id", openRound));

        UUID group = UUID.randomUUID();
        jdbc.update("INSERT INTO thesis.thesis_group (id,round_id,leader_student_id,status,approval_status)"
                        + " VALUES (:id,:round,:student,'DRAFT','PENDING')",
                Map.of("id", group, "round", cancelledRound, "student", student));
        jdbc.update("INSERT INTO thesis.thesis_group_member (id,group_id,round_id,student_id,member_order,is_leader)"
                        + " VALUES (:id,:group,:round,:student,1,TRUE)",
                Map.of("id", UUID.randomUUID(), "group", group, "round", cancelledRound, "student", student));

        Jwt admin = actor("ADMIN", "");
        Jwt studentActor = Jwt.withTokenValue("audit-test").header("alg", "none").subject(student)
                .claim("roles", List.of("STUDENT")).claim("studentId", student).build();
        CountDownLatch seatCounted = new CountDownLatch(1);
        CountDownLatch releaseRestore = new CountDownLatch(1);
        CountDownLatch studentLockIssued = new CountDownLatch(1);
        AtomicInteger restorePid = new AtomicInteger();
        AtomicInteger registerPid = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            String worker = Thread.currentThread().getName();
            if (worker.equals("restore-worker") && sql.contains("JOIN thesis.thesis_group_member other")) {
                restorePid.set(jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
                seatCounted.countDown();
                if (!releaseRestore.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("restore barrier timeout");
                }
            }
            if (worker.equals("register-worker")
                    && sql.contains("campuscore_auth.\"Student\"") && sql.contains("FOR UPDATE")) {
                registerPid.set(jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
                studentLockIssued.countDown();
            }
            return call.callRealMethod();
        }).when(jdbc).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var restoring = pool.submit(() -> {
                Thread.currentThread().setName("restore-worker");
                mutations.restoreRound(cancelledRound, null, admin);
            });
            assertThat(seatCounted.await(10, TimeUnit.SECONDS))
                    .as("restore reaches its seat-conflict count holding the round and member locks")
                    .isTrue();
            var registering = pool.submit(() -> {
                Thread.currentThread().setName("register-worker");
                try {
                    mutations.createGroup(new GroupCreateRequest(openRound), studentActor);
                    return "REGISTERED";
                } catch (DomainException conflict) {
                    return conflict.code();
                }
            });
            assertThat(studentLockIssued.await(10, TimeUnit.SECONDS))
                    .as("registration reaches its Student row lock while the restore is paused")
                    .isTrue();
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!blocked && System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT :owner = ANY(pg_blocking_pids(:waiter))",
                        Map.of("owner", restorePid.get(), "waiter", registerPid.get()), Boolean.class));
                if (!blocked && !registering.isDone()) Thread.sleep(25);
            }
            assertThat(blocked)
                    .as("registration must wait on the Student lock the restore still holds")
                    .isTrue();
            releaseRestore.countDown();
            restoring.get(20, TimeUnit.SECONDS);
            assertThat(registering.get(20, TimeUnit.SECONDS))
                    .as("the late registration sees the restored round live and refuses the seat")
                    .isEqualTo("STUDENT_ACTIVE_IN_OTHER_GROUP");
            Integer liveSeats = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM thesis.thesis_group_member m "
                            + "JOIN thesis.thesis_group g ON g.id = m.group_id AND g.status <> 'CANCELLED' "
                            + "JOIN thesis.thesis_registration_round r ON r.id = m.round_id "
                            + "WHERE m.student_id = :student AND m.active_participation "
                            + "AND r.status IN ('DRAFT','PROPOSAL_OPEN','PROPOSALS_PUBLISHED',"
                            + "'REGISTRATION_OPEN','REGISTRATION_CLOSED')",
                    Map.of("student", student), Integer.class);
            assertThat(liveSeats).as("the student may hold a seat in only one live round").isEqualTo(1);
        } finally {
            releaseRestore.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static Jwt actor(String role, String lecturer) {
        return Jwt.withTokenValue("audit-test").header("alg", "none").subject("audit-user")
                .claim("roles", List.of(role)).claim("lecturerId", lecturer).build();
    }
    static boolean postgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.startsWith("jdbc:postgresql:");
    }
    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value;
    }
}
