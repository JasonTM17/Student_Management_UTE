package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import io.campuscore.restfulapi.thesis.repository.ThesisDatabaseClockRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The transition into REGISTRATION_OPEN used to require the scheduled start
 * even after lecturer submissions had ended. The admin therefore could not
 * use the early-open policy already applied to proposals. Once opened, the
 * registration status must still agree with the window checked by every
 * group and topic mutation ({@code requireRoundStatus}).
 *
 * <p>The authored schedule is the authority. An early open may move only
 * {@code registration_start} to the database's current time, and only after
 * {@code lecturer_submit_end}; that keeps the existing schedule-order check
 * intact while avoiding an hour-long dead period after proposals are published.
 * The original end remains fixed, and the adjustment is audited.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_round_schedule;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisRoundRegistrationScheduleTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ThesisDatabaseClockRepository databaseClock;

    @BeforeEach
    void cleanDatabase() {
        // thesis_group_report is ON DELETE RESTRICT -> delete reports before groups.
        jdbc.update("DELETE FROM thesis.thesis_group_report");
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rs-%'");
    }

    @Test
    void openingRegistrationBeforeTheLecturerWindowEndsIsRejectedWithActionableConflict() throws Exception {
        ensureActiveStudent("rs-future-student", "rs-user-future-student");
        UUID roundId = createFutureWindowRound();
        driveToProposalsPublished(roundId);
        Instant originalStart = registrationStart(roundId);
        Instant originalEnd = registrationEnd(roundId);
        int transitionAuditCountBeforeAttempt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? "
                        + "AND \"action\" = 'THESIS_ROUND_TRANSITION' ",
                Integer.class, roundId.toString());

        // Clamping to now here would violate registration_start >=
        // lecturer_submit_end, so the transition has a specific, actionable refusal.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_EARLY_OPEN_BLOCKED"))
                .andExpect(jsonPath("$.details.lecturerSubmitEnd").isNotEmpty());

        // Rejected means unchanged: the CAS update rolled back with the gate.
        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
        assertThat(registrationStart(roundId)).isEqualTo(originalStart);
        assertThat(registrationEnd(roundId)).isEqualTo(originalEnd);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM campuscore_audit.\"AdminAudit\" WHERE \"entityId\" = ? "
                        + "AND \"action\" = 'THESIS_ROUND_TRANSITION' ",
                Integer.class, roundId.toString())).isEqualTo(transitionAuditCountBeforeAttempt);
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-future-student")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_CLOSED"));
    }

    @Test
    void openingRegistrationEarlyAfterTheLecturerWindowEndsClampsStartAndAuditsBothSchedules() throws Exception {
        ensureActiveStudent("rs-early-student", "rs-user-early-student");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant lecturerEnd = now.minusSeconds(3_600);
        Instant originalStart = now.plusSeconds(3_600);
        UUID roundId = createRound("rs Early Open", lecturerEnd, originalStart,
                now.plusSeconds(2 * 86_400));
        driveToProposalsPublished(roundId);
        Instant originalEnd = registrationEnd(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));

        Instant clampedStart = registrationStart(roundId);
        assertThat(clampedStart).isBeforeOrEqualTo(Instant.now().plusSeconds(2));
        assertThat(clampedStart).isAfter(Instant.now().minusSeconds(10));
        assertThat(registrationEnd(roundId)).isEqualTo(originalEnd);
        assertThat(clampedStart).isAfterOrEqualTo(lecturerEnd);

        List<String> auditStates = jdbc.query(
                "SELECT \"beforeState\", \"afterState\" FROM campuscore_audit.\"AdminAudit\" "
                        + "WHERE \"action\" = 'THESIS_ROUND_SCHEDULE_ADJUSTED' AND \"entityId\" = ?",
                (rs, row) -> rs.getString(1) + " -> " + rs.getString(2), roundId.toString());
        assertThat(auditStates).hasSize(1);
        assertThat(auditStates.get(0)).contains(originalStart.toString()).contains(clampedStart.toString());

        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-early-student")))
                .andExpect(status().isOk());
    }

    @Test
    void openingRegistrationAfterTheScheduledWindowEndedIsRejected() throws Exception {
        UUID roundId = createExpiredWindowRound();
        driveToProposalsPublished(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"))
                .andExpect(jsonPath("$.details.registrationEnd").isNotEmpty());

        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
    }

    @Test
    void openingRegistrationInsideTheScheduledWindowSucceedsAndRegistrationActuallyWorks() throws Exception {
        ensureActiveStudent("rs-live-student", "rs-user-live-student");
        UUID roundId = createLiveWindowRound();
        driveToProposalsPublished(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));

        // The status now agrees with the observable behaviour: the CTA the
        // student sees is accepted by the server.
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-live-student")))
                .andExpect(status().isOk());
    }

    @Test
    void studentActionBeforeTheStoredWindowNamesWhenRegistrationOpens() throws Exception {
        // A round can sit in REGISTRATION_OPEN while the stored window has not
        // started (legacy data or a restored row): the refusal must say when
        // registration opens, not that it "closed".
        ensureActiveStudent("rs-early-student", "rs-user-early-student");
        Instant start = Instant.now().plusSeconds(3L * 24 * 60 * 60);
        insertOpenRound("rs Open Early Window", start, start.plusSeconds(14L * 24 * 60 * 60));

        UUID roundId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = ?",
                UUID.class, "rs Open Early Window");
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-early-student")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_NOT_OPEN"))
                .andExpect(jsonPath("$.details.opensAt").isString());
    }

    @Test
    void studentActionAfterTheStoredWindowReportsItClosed() throws Exception {
        // An open round whose stored end already passed must keep answering
        // REGISTRATION_WINDOW_CLOSED — the stale-open state the transition
        // gate prevents is exactly what this guard catches for seeded data.
        ensureActiveStudent("rs-late-student", "rs-user-late-student");
        Instant end = Instant.now().minusSeconds(86_400);
        insertOpenRound("rs Open Expired Window", end.minusSeconds(14L * 24 * 60 * 60), end);

        UUID roundId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = ?",
                UUID.class, "rs Open Expired Window");
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-late-student")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));
    }

    @Test
    void studentRegistrationUsesTheSameDatabaseClockThatOpenedTheRound() throws Exception {
        ensureActiveStudent("rs-skew-student", "rs-user-skew-student");
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant registrationStart = now.minusSeconds(180);
        Instant registrationEnd = now.minusSeconds(30);
        Instant lecturerEnd = registrationStart.minusSeconds(3_600);
        UUID roundId = createRound("rs Database Clock", lecturerEnd, registrationStart, registrationEnd);
        driveToProposalsPublished(roundId);

        Instant databaseNow = registrationStart.plusSeconds(90);
        assertThat(databaseNow).isBefore(registrationEnd);
        assertThat(Instant.now()).isAfter(registrationEnd);
        org.mockito.Mockito.doReturn(databaseNow).when(databaseClock).now();

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTRATION_OPEN"));
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-skew-student")))
                .andExpect(status().isOk());
    }

    @Test
    void jumpingStraightToRegistrationStillReportsTheStateConflictFirst() throws Exception {
        // The schedule gate must not displace the state machine: the phase order
        // is the more fundamental error and keeps its own code.
        UUID roundId = createLiveWindowRound();

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        assertThat(roundStatus(roundId)).isEqualTo("DRAFT");
    }

    @Test
    void roundReadIncludesDatabaseSampledServerTime() throws Exception {
        UUID roundId = createLiveWindowRound();
        Instant before = currentDatabaseTime();

        String response = mvc.perform(get("/api/v1/thesis/rounds").with(adminJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode round = tools.jackson.databind.json.JsonMapper.builder().build().readTree(response).get(0);
        assertThat(round).isNotNull();
        assertThat(round.get("id").asText()).isEqualTo(roundId.toString());
        assertThat(round.hasNonNull("serverNow")).isTrue();
        Instant serverNow = Instant.parse(round.get("serverNow").asText());
        Instant after = currentDatabaseTime();
        assertThat(serverNow).isBetween(before.minusSeconds(2), after.plusSeconds(2));
    }

    // ---------- fixtures ----------

    private UUID createFutureWindowRound() throws Exception {
        Instant lecturerEnd = Instant.now().plusSeconds(10L * 24 * 60 * 60);
        return createRound("rs Future Window", lecturerEnd, lecturerEnd.plusSeconds(3_600),
                lecturerEnd.plusSeconds(32L * 24 * 60 * 60));
    }

    private UUID createExpiredWindowRound() throws Exception {
        Instant lecturerEnd = Instant.now().minusSeconds(9L * 24 * 60 * 60);
        return createRound("rs Expired Window", lecturerEnd, lecturerEnd.plusSeconds(3_600),
                lecturerEnd.plusSeconds(7L * 24 * 60 * 60));
    }

    private UUID createLiveWindowRound() throws Exception {
        Instant lecturerEnd = Instant.now().minusSeconds(2L * 24 * 60 * 60);
        return createRound("rs Live Window", lecturerEnd, lecturerEnd.plusSeconds(3_600),
                lecturerEnd.plusSeconds(32L * 24 * 60 * 60));
    }

    /**
     * Creates a KLTN round through the API (so the authored schedule obeys the
     * real conditional-date rules) and returns its id. The registration window
     * starts an hour after the lecturer window closes and ends
     * {@code registrationLength} later, which lets a test place it in the past,
     * live, or in the future without touching the row afterwards.
     */
    private UUID createRound(String name, Instant lecturerSubmitEnd, Instant registrationStart,
                             Instant registrationEnd) throws Exception {
        String body = "{"
                + "\"name\":\"" + name + "\",\"thesisType\":\"KLTN\","
                + "\"lecturerSubmitStart\":\"" + lecturerSubmitEnd.minusSeconds(3_600) + "\","
                + "\"lecturerSubmitEnd\":\"" + lecturerSubmitEnd + "\","
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
        UUID roundId = jdbc.queryForObject(
                "SELECT id FROM thesis.thesis_registration_round WHERE name = ?", UUID.class, name);
        assertThat(roundId).isNotNull();
        return roundId;
    }

    /**
     * Seeds a round already in REGISTRATION_OPEN with an authored window —
     * the API cannot produce this state today (the transition enforces a live
     * window), which is exactly the stale combination the mutation guard must
     * still describe correctly.
     */
    private UUID insertOpenRound(String name, Instant registrationStart, Instant registrationEnd) {
        UUID roundId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, 'REGISTRATION_OPEN')",
                roundId, name,
                java.sql.Timestamp.from(registrationStart.minusSeconds(20L * 24 * 60 * 60)),
                java.sql.Timestamp.from(registrationStart.minusSeconds(6L * 24 * 60 * 60)),
                java.sql.Timestamp.from(registrationStart),
                java.sql.Timestamp.from(registrationEnd),
                java.sql.Timestamp.from(registrationEnd.plusSeconds(86_400)),
                java.sql.Timestamp.from(registrationEnd.plusSeconds(2 * 86_400)));
        return roundId;
    }

    private void driveToProposalsPublished(UUID roundId) throws Exception {
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/rounds/{id}/publish-proposals", roundId).with(adminJwt()))
                .andExpect(status().isOk());
    }

    private String roundStatus(UUID roundId) {
        return jdbc.queryForObject(
                "SELECT status FROM thesis.thesis_registration_round WHERE id = ?", String.class, roundId);
    }

    private Instant registrationStart(UUID roundId) {
        return jdbc.queryForObject(
                "SELECT registration_start FROM thesis.thesis_registration_round WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), roundId);
    }

    private Instant registrationEnd(UUID roundId) {
        return jdbc.queryForObject(
                "SELECT registration_end FROM thesis.thesis_registration_round WHERE id = ?",
                (rs, row) -> rs.getTimestamp(1).toInstant(), roundId);
    }

    private Instant currentDatabaseTime() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", (rs, row) -> rs.getTimestamp(1).toInstant());
    }

    private void ensureActiveStudent(String studentId, String userId) {
        jdbc.update(
                "INSERT INTO campuscore_auth.\"User\" (\"id\", \"email\", \"password\", \"firstName\", \"lastName\", \"status\", \"emailVerified\", \"isSuperAdmin\", \"failedLoginAttempts\", \"createdAt\", \"updatedAt\") "
                        + "VALUES (?, ?, 'test-password', 'Rs', 'Student', 'ACTIVE', FALSE, FALSE, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                userId, studentId + "@campuscore.edu");
        jdbc.update(
                "INSERT INTO campuscore_auth.\"Student\" (\"id\", \"userId\", \"studentId\", \"curriculumId\", \"year\", \"admissionDate\") "
                        + "VALUES (?, ?, ?, 'curriculum-demo', 2, CURRENT_TIMESTAMP)",
                studentId, userId, "CODE-" + studentId);
    }

    private RequestPostProcessor studentJwt(String studentId) {
        return ThesisAuthorityFixture.student(jdbc, studentId);
    }

    private RequestPostProcessor adminJwt() {
        return ThesisAuthorityFixture.role(jdbc, "rs-admin-user", "ADMIN");
    }
}
