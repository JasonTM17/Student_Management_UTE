package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The transition into REGISTRATION_OPEN used to check only the current status,
 * while every registration mutation ({@code requireRoundStatus}) also demands
 * that "now" sits inside the stored registration window. An admin could
 * therefore publish a round the UI renders as "registration open" while every
 * group and topic action answered 409 REGISTRATION_WINDOW_CLOSED.
 *
 * <p>The authored schedule is the authority: registration opens only inside
 * {@code [registration_start, registration_end)}. Out-of-window transitions are
 * rejected instead of accepted, because the PROPOSAL_OPEN clamp cannot be
 * mirrored on {@code registration_start} — it must stay >=
 * {@code lecturer_submit_end} (two-phase order, thesis_round_schedule_order_valid)
 * — and silently moving it would rewrite the schedule students were shown.
 * A student never sees a registration CTA the server rejects.
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

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rs-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rs-%'");
    }

    @Test
    void openingRegistrationBeforeTheScheduledWindowIsRejectedAndNothingMoves() throws Exception {
        ensureActiveStudent("rs-future-student", "rs-user-future-student");
        UUID roundId = createFutureWindowRound();
        driveToProposalsPublished(roundId);

        // Pre-fix this returned 200 and set REGISTRATION_OPEN on a round whose
        // window had not started: the status advertised registration that the
        // very next group action rejected with REGISTRATION_WINDOW_CLOSED.
        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_NOT_OPEN"));

        // Rejected means unchanged: the CAS update rolled back with the gate.
        assertThat(roundStatus(roundId)).isEqualTo("PROPOSALS_PUBLISHED");
        mvc.perform(post("/api/v1/thesis/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}")
                        .with(studentJwt("rs-future-student")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_CLOSED"));
    }

    @Test
    void openingRegistrationAfterTheScheduledWindowEndedIsRejected() throws Exception {
        UUID roundId = createExpiredWindowRound();
        driveToProposalsPublished(roundId);

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REGISTRATION_WINDOW_CLOSED"));

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
    void jumpingStraightToRegistrationStillReportsTheStateConflictFirst() throws Exception {
        // The schedule gate must not displace the state machine: the phase order
        // is the more fundamental error and keeps its own code.
        UUID roundId = createLiveWindowRound();

        mvc.perform(post("/api/v1/thesis/rounds/{id}/open-registration", roundId).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUND_STATE_CONFLICT"));
        assertThat(roundStatus(roundId)).isEqualTo("DRAFT");
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
        return jwt().jwt(token -> token
                        .subject("rs-user-" + studentId)
                        .claim("roles", List.of("STUDENT"))
                        .claim("studentId", studentId))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }

    private RequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token
                        .subject("rs-admin-user")
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
