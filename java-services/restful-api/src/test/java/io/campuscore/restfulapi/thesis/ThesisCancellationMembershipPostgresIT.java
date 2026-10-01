package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import io.campuscore.restfulapi.thesis.domain.GroupStatus;
import io.campuscore.restfulapi.thesis.service.ThesisMutationService;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.GroupCreateRequest;
import io.campuscore.restfulapi.thesis.web.ThesisMutationDtos.ProgressRequest;
import io.campuscore.restfulapi.web.DomainException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

/** Real service transactions against migrated PostgreSQL, including a deterministic Student-lock schedule. */
@EnabledIf("postgresTargetConfigured")
@SpringBootTest(properties = { "spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false" })
@ActiveProfiles({"test", "persistence"})
class ThesisCancellationMembershipPostgresIT {
    @Autowired private ThesisMutationService mutations;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @MockitoSpyBean private NamedParameterJdbcTemplate jdbcSpy;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "campuscore"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    @Test
    void cancellationKeepsHistoryReleasesTheSeatAndFreezesTheRoster() {
        Fixture fixture = fixture();
        UUID cancelled = mutations.createGroup(new GroupCreateRequest(fixture.round()), student(fixture.student())).id();
        mutations.updateProgress(cancelled, new ProgressRequest(GroupStatus.CANCELLED), student(fixture.student()));

        assertThat(count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE group_id=:group AND active_participation=FALSE",
                Map.of("group", cancelled))).isEqualTo(1);
        UUID replacement = mutations.createGroup(new GroupCreateRequest(fixture.round()), student(fixture.student())).id();
        assertThat(count("SELECT COUNT(*) FROM thesis.thesis_group_member WHERE round_id=:round AND student_id=:student",
                Map.of("round", fixture.round(), "student", fixture.student()))).isEqualTo(2);
        assertThatThrownBy(() -> mutations.updateProgress(cancelled, new ProgressRequest(GroupStatus.DRAFT), admin()))
                .isInstanceOf(DomainException.class).satisfies(error -> assertThat(((DomainException) error).code()).isEqualTo("GROUP_STATE_CONFLICT"));
        assertThatThrownBy(() -> mutations.removeMember(cancelled, fixture.student(), admin()))
                .isInstanceOf(DomainException.class).satisfies(error -> assertThat(((DomainException) error).code()).isEqualTo("GROUP_STATE_CONFLICT"));
        assertThat(replacement).isNotEqualTo(cancelled);
    }

    @Test
    void sameStudentConcurrentCreatesWaitOnTheStudentRowThenOneReceivesTheBusinessConflict() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEnteringStudentCheck = new CountDownLatch(1);
        doAnswer(call -> {
            String sql = call.getArgument(0);
            String worker = Thread.currentThread().getName();
            if (sql.contains("FROM campuscore_auth.\"Student\"") && sql.contains("FOR UPDATE")
                    && worker.startsWith("cancel-membership-")) {
                if (worker.endsWith("first")) {
                    Object result = call.callRealMethod();
                    firstLocked.countDown();
                    if (!releaseFirst.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("student-lock barrier timeout");
                    return result;
                }
                secondEnteringStudentCheck.countDown();
            }
            return call.callRealMethod();
        }).when(jdbcSpy).queryForObject(anyString(), any(SqlParameterSource.class), any(Class.class));

        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> {
                Thread.currentThread().setName("cancel-membership-first");
                return createCode(fixture);
            });
            assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
            var second = pool.submit(() -> {
                Thread.currentThread().setName("cancel-membership-second");
                return createCode(fixture);
            });
            assertThat(secondEnteringStudentCheck.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(250);
            assertThat(second.isDone()).as("second create waits for the first transaction's Student FOR UPDATE lock").isFalse();
            releaseFirst.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo("CREATED");
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo("STUDENT_ALREADY_IN_GROUP");
        } finally {
            releaseFirst.countDown(); pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String createCode(Fixture fixture) {
        try {
            mutations.createGroup(new GroupCreateRequest(fixture.round()), student(fixture.student()));
            return "CREATED";
        } catch (DomainException exception) {
            return exception.code();
        }
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        String student = "tcm-" + suffix;
        String user = "tcm-user-" + suffix;
        UUID round = UUID.randomUUID();
        jdbc.update("INSERT INTO campuscore_auth.\"User\" (\"id\",\"email\",\"password\",\"firstName\",\"lastName\",\"status\",\"emailVerified\",\"isSuperAdmin\",\"failedLoginAttempts\",\"createdAt\",\"updatedAt\") VALUES (:id,:email,'test','Thesis','Race','ACTIVE',FALSE,FALSE,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                Map.of("id", user, "email", student + "@test.invalid"));
        jdbc.update("INSERT INTO campuscore_auth.\"Student\" (\"id\",\"userId\",\"studentId\",\"curriculumId\",\"year\",\"admissionDate\") VALUES (:id,:user,:number,'curriculum-demo',2,CURRENT_TIMESTAMP)",
                Map.of("id", student, "user", user, "number", "TCM-" + suffix));
        Instant now = Instant.now();
        jdbc.update("INSERT INTO thesis.thesis_registration_round (id,name,thesis_type,lecturer_submit_start,lecturer_submit_end,registration_start,registration_end,gvpb_deadline,status) VALUES (:id,'Cancellation membership', 'KLTN',:lecturerStart,:lecturerEnd,:registrationStart,:registrationEnd,:deadline,'REGISTRATION_OPEN')",
                Map.of("id", round,
                        "lecturerStart", Timestamp.from(now.minusSeconds(14_400)),
                        "lecturerEnd", Timestamp.from(now.minusSeconds(10_800)),
                        "registrationStart", Timestamp.from(now.minusSeconds(7_200)),
                        "registrationEnd", Timestamp.from(now.plusSeconds(3_600)),
                        "deadline", Timestamp.from(now.plusSeconds(7_200))));
        return new Fixture(round, student, user);
    }

    private int count(String sql, Map<String, ?> params) { return jdbc.queryForObject(sql, params, Integer.class); }
    private static Jwt student(String student) { return Jwt.withTokenValue("test").header("alg", "none").subject("tcm-user-" + student.substring(4)).claim("roles", List.of("STUDENT")).claim("studentId", student).build(); }
    private static Jwt admin() { return Jwt.withTokenValue("test").header("alg", "none").subject("tcm-admin").claim("roles", List.of("ADMIN")).build(); }
    static boolean postgresTargetConfigured() { String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"); return url != null && url.startsWith("jdbc:postgresql:"); }
    private static String valueOr(String key, String fallback) { String value = System.getenv(key); return value == null || value.isBlank() ? fallback : value; }
    private record Fixture(UUID round, String student, String user) { }
}
