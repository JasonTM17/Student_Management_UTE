package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.service.ThesisCouncilService;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
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

/** Real transactional service calls, paused after score permission checking.
 * Observe Postgres blocking rather than hoping two threads happen to overlap.
 */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisCouncilRosterRacePostgresIT {
    @Autowired private ThesisCouncilService councils;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void memberRemovalCannotRaceWithTheFirstScoreAndLeaveAnOrphanGrade() throws Exception {
        UUID round = UUID.randomUUID();
        UUID topic = UUID.randomUUID();
        UUID council = UUID.randomUUID();
        List<String> lecturers = jdbc.queryForList(
                "SELECT \"id\" FROM campuscore_auth.\"Lecturer\" WHERE \"isActive\" = TRUE ORDER BY \"id\" LIMIT 4",
                Map.of(), String.class);
        assertThat(lecturers).hasSize(4);
        String member = lecturers.get(3);
        // Copy a migrated seed's mandatory governance fields to a new identity.
        jdbc.update("INSERT INTO thesis.thesis_registration_round SELECT (jsonb_populate_record("
                + "NULL::thesis.thesis_registration_round,to_jsonb(r)||jsonb_build_object('id',CAST(:id AS text),'name','Roster race'))).*"
                + " FROM (SELECT * FROM thesis.thesis_registration_round LIMIT 1) r", Map.of("id", round));
        jdbc.update("INSERT INTO thesis.thesis_topic SELECT (jsonb_populate_record(NULL::thesis.thesis_topic,"
                + "to_jsonb(t)||jsonb_build_object('id',CAST(:id AS text),'round_id',CAST(:round AS text),"
                + "'title','Roster race topic','final_score',NULL,'final_score_finalized_at',NULL,'final_score_finalized_by',NULL))).*"
                + " FROM (SELECT * FROM thesis.thesis_topic LIMIT 1) t", Map.of("id", topic, "round", round));
        jdbc.update("INSERT INTO thesis.thesis_council (id,round_id,name,status,created_by) VALUES (:id,:round,'Roster race','ACTIVE','audit-admin')",
                Map.of("id", council, "round", round));
        for (int index = 0; index < 4; index++) {
            jdbc.update("INSERT INTO thesis.thesis_council_member (id,council_id,lecturer_id,member_role) VALUES (:id,:council,:lecturer,:role)",
                    Map.of("id", UUID.randomUUID(), "council", council, "lecturer", lecturers.get(index),
                            "role", index == 0 ? "CHAIR" : index == 1 ? "SECRETARY" : "MEMBER"));
        }
        jdbc.update("INSERT INTO thesis.thesis_council_topic (id,council_id,topic_id,assigned_by) VALUES (:id,:council,:topic,'audit-admin')",
                Map.of("id", UUID.randomUUID(), "council", council, "topic", topic));
        Jwt grader = actor("LECTURER", member);
        Jwt admin = actor("ADMIN", "");
        CountDownLatch permissionChecked = new CountDownLatch(1);
        CountDownLatch releaseScore = new CountDownLatch(1);
        AtomicInteger scorePid = new AtomicInteger();
        AtomicInteger removalPid = new AtomicInteger();
        doAnswer(call -> {
            String sql = call.getArgument(0);
            String worker = Thread.currentThread().getName();
            if (worker.equals("audit-remove")) {
                removalPid.compareAndSet(0, jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
            }
            if (worker.equals("audit-score") && sql.contains("FROM thesis.thesis_topic WHERE") && sql.contains("FOR UPDATE")) {
                scorePid.set(jdbc.getJdbcOperations().queryForObject("SELECT pg_backend_pid()", Integer.class));
                permissionChecked.countDown();
                if (!releaseScore.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("score barrier timeout");
            }
            return call.callRealMethod();
        }).when(jdbc).queryForMap(anyString(), any(SqlParameterSource.class));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var scoring = pool.submit(() -> {
                Thread.currentThread().setName("audit-score");
                councils.submitScore(council, topic, "DEFENSE", new BigDecimal("8"), grader);
            });
            assertThat(permissionChecked.await(10, TimeUnit.SECONDS)).isTrue();
            var removing = pool.submit(() -> {
                Thread.currentThread().setName("audit-remove");
                try { councils.removeMember(council, member, admin); return "REMOVED"; }
                catch (DomainException conflict) { return conflict.code(); }
            });
            boolean blocked = false;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!removing.isDone() && !blocked && System.nanoTime() < deadline) {
                if (removalPid.get() != 0) blocked = Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT :owner = ANY(pg_blocking_pids(:waiter))",
                        Map.of("owner", scorePid.get(), "waiter", removalPid.get()), Boolean.class));
                if (!blocked && !removing.isDone()) Thread.sleep(25);
            }
            assertThat(removing.isDone() || blocked).as("removal either commits or waits on the scorer").isTrue();
            releaseScore.countDown();
            scoring.get(20, TimeUnit.SECONDS);
            assertThat(removing.get(20, TimeUnit.SECONDS)).isIn("REMOVED", "COUNCIL_STATE_CONFLICT");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM thesis.thesis_topic_score s WHERE council_id=:id"
                    + " AND NOT EXISTS (SELECT 1 FROM thesis.thesis_council_member m WHERE m.council_id=s.council_id AND m.lecturer_id=s.lecturer_id)",
                    Map.of("id", council), Integer.class)).as("no grade authored by a removed council member").isZero();
        } finally {
            releaseScore.countDown(); pool.shutdownNow();
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
