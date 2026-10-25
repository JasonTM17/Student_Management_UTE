package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.repository.ThesisDatabaseClockRepository;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.mockito.Mockito;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PostgreSQL proof for the schedule-order boundary of an early registration
 * open. The enabled guard permits only a disposable hot4_* database on native
 * localhost; ordinary H2 tests cover the HTTP behavior in the default suite.
 */
@EnabledIf("hot4PostgresTargetConfigured")
@SpringBootTest(properties = {"spring.flyway.locations=classpath:db/migration", "deepseek.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisRoundEarlyOpenPostgresIT {

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private DataSource dataSource;
    @MockitoSpyBean private ThesisDatabaseClockRepository databaseClock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CAMPUSCORE_E2E_POSTGRES_URL"));
        registry.add("spring.datasource.username", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_USER", "postgres"));
        registry.add("spring.datasource.password", () -> valueOr("CAMPUSCORE_E2E_POSTGRES_PASSWORD", "postgres"));
    }

    static boolean hot4PostgresTargetConfigured() {
        String url = System.getenv("CAMPUSCORE_E2E_POSTGRES_URL");
        return url != null && url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/hot4_[a-zA-Z0-9_]+(?:\\?.*)?");
    }

    private static String valueOr(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Test
    void earlyOpenMovesOnlyRegistrationStartAndPreservesTheDatabaseOrderCheck() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant lecturerEnd = now.minusSeconds(3_600);
        Instant originalStart = now.plusSeconds(3_600);
        Instant originalEnd = now.plusSeconds(2 * 86_400);
        UUID roundId = createRound("HOT4 early open " + UUID.randomUUID(), lecturerEnd, originalStart, originalEnd);
        driveToProposalsPublished(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));

        Instant actualStart = instantColumn("registration_start", roundId);
        assertThat(actualStart).isAfterOrEqualTo(lecturerEnd);
        assertThat(actualStart).isBeforeOrEqualTo(Instant.now());
        assertThat(actualStart).isAfter(Instant.now().minusSeconds(30));
        assertThat(instantColumn("registration_end", roundId)).isEqualTo(originalEnd);
        assertThat(scheduleOrderIsValid(roundId)).isTrue();

        var audit = jdbc.queryForMap(
                "SELECT \"beforeState\", \"afterState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_ROUND_SCHEDULE_ADJUSTED' AND \"entityId\" = ?",
                roundId.toString());
        assertThat((String) audit.get("beforeState")).contains(originalStart.toString());
        assertThat((String) audit.get("afterState")).contains(actualStart.toString());
        assertThat((String) audit.get("afterState")).contains(originalEnd.toString());

        Instant clockBefore = databaseNow();
        String readResponse = mvc.perform(get("/api/v1/thesis/rounds").with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Instant clockAfter = databaseNow();
        JsonNode round = null;
        for (JsonNode candidate : new ObjectMapper().readTree(readResponse)) {
            if (roundId.toString().equals(candidate.path("id").asText())) {
                round = candidate;
                break;
            }
        }
        assertThat(round).isNotNull();
        assertThat(round.hasNonNull("serverNow")).isTrue();
        assertThat(Instant.parse(round.get("serverNow").asText()))
                .isBetween(clockBefore.minusSeconds(2), clockAfter.plusSeconds(2));
    }

    @Test
    void registrationOpeningCrossingItsStartBeforeTheClampUpdateStillSucceeds() throws Exception {
        Instant setupNow = databaseNow();
        Instant lecturerEnd = setupNow.minusSeconds(3_600);
        Instant initialStart = setupNow.plusSeconds(3_600);
        Instant initialEnd = setupNow.plusSeconds(86_400);
        UUID roundId = createRound("HOT4 start boundary " + UUID.randomUUID(), lecturerEnd,
                initialStart, initialEnd);
        driveToProposalsPublished(roundId);

        Instant boundaryNow = databaseNow();
        Instant originalStart = boundaryNow.plusSeconds(2);
        Instant originalEnd = boundaryNow.plusSeconds(60);
        jdbc.update("UPDATE thesis.thesis_registration_round SET registration_start = ?, registration_end = ? WHERE id = ?",
                Timestamp.from(originalStart), Timestamp.from(originalEnd), roundId);

        AtomicBoolean firstRead = new AtomicBoolean(true);
        Mockito.doAnswer(invocation -> {
            if (firstRead.compareAndSet(true, false)) {
                awaitDatabaseTime(originalStart.plusMillis(25), Duration.ofSeconds(10));
                return originalStart.minusMillis(1);
            }
            return invocation.callRealMethod();
        }).when(databaseClock).now();

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));

        assertThat(instantColumn("registration_start", roundId)).isEqualTo(originalStart);
        assertThat(instantColumn("registration_end", roundId)).isEqualTo(originalEnd);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? "
                        + "AND \"action\" = 'THESIS_ROUND_SCHEDULE_ADJUSTED'",
                Integer.class, roundId.toString())).isZero();
    }

    @Test
    void earlyOpenBeforeLecturerWindowEndIsRefusedWithoutChangingTheSchedule() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant lecturerEnd = now.plusSeconds(3_600);
        Instant originalStart = now.plusSeconds(7_200);
        Instant originalEnd = now.plusSeconds(3 * 86_400);
        UUID roundId = createRound("HOT4 blocked open " + UUID.randomUUID(), lecturerEnd, originalStart, originalEnd);
        driveToProposalsPublished(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_EARLY_OPEN_BLOCKED"))
                .andExpect(jsonPath("$.details.lecturerSubmitEnd").isNotEmpty());

        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
        assertThat(instantColumn("registration_start", roundId)).isEqualTo(originalStart);
        assertThat(instantColumn("registration_end", roundId)).isEqualTo(originalEnd);
        assertThat(scheduleOrderIsValid(roundId)).isTrue();
    }

    @Test
    void registrationOpenWaitingPastWindowEndRechecksWithFreshDatabaseTime() throws Exception {
        Instant setupNow = databaseNow();
        Instant lecturerEnd = setupNow.minusSeconds(3_600);
        Instant originalStart = setupNow.plusSeconds(60);
        Instant originalEnd = setupNow.plusSeconds(120);
        UUID roundId = createRound("HOT4 lock wait " + UUID.randomUUID(), lecturerEnd,
                originalStart, originalEnd);
        driveToProposalsPublished(roundId);

        Instant lockWaitNow = databaseNow();
        // Keep the request's transaction-start timestamp inside the authored
        // window, then hold the row until the database wall clock is past its
        // end. A transaction-time check would incorrectly accept this open.
        Instant scheduledStart = lockWaitNow.minusSeconds(3);
        Instant scheduledEnd = lockWaitNow.plusSeconds(6);
        assertThat(lockWaitNow).isAfterOrEqualTo(scheduledStart).isBefore(scheduledEnd);
        jdbc.update("UPDATE thesis.thesis_registration_round SET registration_start = ?, registration_end = ? WHERE id = ?",
                Timestamp.from(scheduledStart), Timestamp.from(scheduledEnd), roundId);
        Integer originalVersion = jdbc.queryForObject(
                "SELECT version FROM thesis.thesis_registration_round WHERE id = ?", Integer.class, roundId);
        var admin = adminJwt();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Connection lockOwner = dataSource.getConnection()) {
            lockOwner.setAutoCommit(false);
            int blockerPid;
            try (PreparedStatement pidQuery = lockOwner.prepareStatement("SELECT pg_backend_pid()");
                    ResultSet pidResult = pidQuery.executeQuery()) {
                assertThat(pidResult.next()).isTrue();
                blockerPid = pidResult.getInt(1);
            }
            try (PreparedStatement lockRound = lockOwner.prepareStatement(
                    "SELECT id FROM thesis.thesis_registration_round WHERE id = ? FOR UPDATE")) {
                lockRound.setObject(1, roundId);
                try (ResultSet locked = lockRound.executeQuery()) {
                    assertThat(locked.next()).isTrue();
                }
            }

            Future<Void> opening = worker.submit(() -> {
                mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(admin))
                        .andExpect(status().isConflict())
                        .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));
                return null;
            });
            assertThat(awaitWaitingOn(blockerPid, Duration.ofSeconds(2)))
                    .as("the registration transition must be waiting on the held round row lock")
                    .isTrue();
            assertThat(databaseNow()).isBefore(scheduledEnd);
            awaitDatabaseTime(scheduledEnd.plusMillis(100), Duration.ofSeconds(10));
            lockOwner.commit();

            opening.get(15, TimeUnit.SECONDS);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
        assertThat(instantColumn("registration_start", roundId)).isEqualTo(scheduledStart);
        assertThat(instantColumn("registration_end", roundId)).isEqualTo(scheduledEnd);
        assertThat(jdbc.queryForObject("SELECT version FROM thesis.thesis_registration_round WHERE id = ?",
                Integer.class, roundId)).isEqualTo(originalVersion);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? "
                        + "AND \"action\" = 'THESIS_ROUND_TRANSITION' "
                        + "AND \"summary\" = 'Round transitioned PROPOSALS_PUBLISHED -> REGISTRATION_OPEN'",
                Integer.class, roundId.toString())).isZero();
    }

    @Test
    void lateTopicRequestStalledAcrossRegistrationEndRollsBack() throws Exception {
        Instant setupNow = databaseNow();
        Instant lecturerEnd = setupNow.minusSeconds(3_600);
        Instant registrationStart = setupNow.minusSeconds(300);
        Instant initialEnd = setupNow.plusSeconds(86_400);
        UUID roundId = createRound("HOT4 late-topic deadline " + UUID.randomUUID(), lecturerEnd,
                registrationStart, initialEnd);
        driveToProposalsPublished(roundId);

        String supervisorId = "hot4-late-supervisor-" + UUID.randomUUID();
        ThesisAuthorityFixture.lecturer(jdbc, supervisorId);
        Instant registrationEnd = databaseNow().plusSeconds(6);
        jdbc.update("UPDATE thesis.thesis_registration_round SET registration_end = ? WHERE id = ?",
                Timestamp.from(registrationEnd), roundId);
        String title = "HOT4 late topic " + UUID.randomUUID();
        String reason = "supervisor missed the proposal window";
        String requestBody = "{"
                + "\"departmentId\":\"department-demo\","
                + "\"title\":\"" + title + "\","
                + "\"description\":\"Late topic deadline regression\","
                + "\"maxGroups\":2,"
                + "\"supervisorId\":\"" + supervisorId + "\","
                + "\"reason\":\"" + reason + "\"}";
        var admin = adminJwt();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (Connection lockOwner = dataSource.getConnection()) {
            lockOwner.setAutoCommit(false);
            int blockerPid;
            try (PreparedStatement pidQuery = lockOwner.prepareStatement("SELECT pg_backend_pid()");
                    ResultSet pidResult = pidQuery.executeQuery()) {
                assertThat(pidResult.next()).isTrue();
                blockerPid = pidResult.getInt(1);
            }
            try (PreparedStatement lockTopicTable = lockOwner.prepareStatement(
                    "LOCK TABLE thesis.thesis_topic IN ACCESS EXCLUSIVE MODE")) {
                lockTopicTable.execute();
            }

            Future<org.springframework.test.web.servlet.MvcResult> creation = worker.submit(() -> mvc.perform(
                    post("/api/v1/thesis/rounds/{id}/late-topic", roundId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody)
                            .with(admin)).andReturn());
            assertThat(awaitWaitingOn(blockerPid, Duration.ofSeconds(2)))
                    .as("late-topic creation must reach the topic write after its initial window check")
                    .isTrue();
            assertThat(databaseNow()).isBefore(registrationEnd);
            awaitDatabaseTime(registrationEnd.plusMillis(100), Duration.ofSeconds(10));
            lockOwner.commit();

            var response = creation.get(15, TimeUnit.SECONDS).getResponse();
            assertThat(response.getStatus()).isEqualTo(409);
            assertThat(new ObjectMapper().readTree(response.getContentAsString()).path("code").asText())
                    .isEqualTo("REGISTRATION_WINDOW_CLOSED");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.thesis_topic WHERE round_id = ? AND title = ?",
                Integer.class, roundId, title)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"action\" = ? AND \"summary\" = ?",
                Integer.class, "THESIS_TOPIC_LATE_CREATED",
                "Late topic created for supervisor " + supervisorId + "; reason: " + reason)).isZero();
        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
    }

    private UUID createRound(String name, Instant lecturerEnd, Instant registrationStart, Instant registrationEnd)
            throws Exception {
        String body = "{"
                + "\"name\":\"" + name + "\",\"thesisType\":\"KLTN\","
                + "\"lecturerSubmitStart\":\"" + lecturerEnd.minusSeconds(3_600) + "\","
                + "\"lecturerSubmitEnd\":\"" + lecturerEnd + "\","
                + "\"registrationStart\":\"" + registrationStart + "\","
                + "\"registrationEnd\":\"" + registrationEnd + "\","
                + "\"gvpbDeadline\":\"" + registrationEnd.plusSeconds(86_400) + "\","
                + "\"reportDate\":\"" + registrationEnd.plusSeconds(2 * 86_400) + "\","
                + "\"defenseDate\":\"" + registrationEnd.plusSeconds(3 * 86_400) + "\"}";
        mvc.perform(post("/api/v1/thesis/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(adminJwt()))
                .andExpect(status().isOk());
        return jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = ?", UUID.class, name);
    }

    private void driveToProposalsPublished(UUID roundId) throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
    }

    private Instant instantColumn(String column, UUID roundId) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM thesis.thesis_registration_round WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), roundId);
    }

    private boolean scheduleOrderIsValid(UUID roundId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT registration_start >= lecturer_submit_end FROM thesis.thesis_registration_round WHERE id = ?",
                Boolean.class, roundId));
    }

    private String roundStatus(UUID roundId) {
        return jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId);
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT clock_timestamp()", (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
    }

    private boolean awaitWaitingOn(int blockerPid, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM pg_stat_activity waiter "
                            + "WHERE waiter.wait_event_type = 'Lock' "
                            + "AND ? = ANY(pg_blocking_pids(waiter.pid)))",
                    Boolean.class, blockerPid);
            if (Boolean.TRUE.equals(waiting)) return true;
            Thread.sleep(20);
        }
        return false;
    }

    private void awaitDatabaseTime(Instant deadline, Duration timeout) throws InterruptedException {
        long waitDeadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < waitDeadline) {
            if (!databaseNow().isBefore(deadline)) return;
            Thread.sleep(20);
        }
        throw new AssertionError("database time did not reach the scheduled registration end");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "hot4-thesis-admin", "ADMIN");
    }
}
