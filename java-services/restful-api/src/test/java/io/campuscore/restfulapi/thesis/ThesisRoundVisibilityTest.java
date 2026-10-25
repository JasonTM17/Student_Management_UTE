package io.campuscore.restfulapi.thesis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A DRAFT round is an unannounced schedule still being shaped by governance:
 * names, types and windows can still change, so students and lecturers must
 * not see it in their round picker — they would plan around a deadline the
 * faculty never committed to. Only the roles that create and manage rounds
 * (ADMIN, SUPER_ADMIN, TRUONG_KHOA) may list drafts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:restful_api_thesis_round_visibility;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2"
})
class ThesisRoundVisibilityTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void prepareFixture() {
        jdbc.update("DELETE FROM thesis.thesis_group_member");
        jdbc.update("DELETE FROM thesis.thesis_group");
        jdbc.update("DELETE FROM thesis.thesis_topic_supervisor");
        jdbc.update("DELETE FROM thesis.thesis_topic");
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
        jdbc.update("DELETE FROM campuscore_auth.\"Lecturer\" WHERE \"id\" LIKE 'rv-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"Student\" WHERE \"id\" LIKE 'rv-%'");
        jdbc.update("DELETE FROM campuscore_auth.\"User\" WHERE \"id\" LIKE 'rv-%'"
                + " OR \"id\" LIKE 'thesis-fixture-lecturer-rv-%'");
    }

    @Test
    void studentDoesNotSeeDraftRounds() throws Exception {
        insertRound("rv-draft-round", "DRAFT");
        insertRound("rv-live-round", "REGISTRATION_OPEN");

        List<String> statuses = statusesAs(ThesisAuthorityFixture.student(jdbc, "rv-student-1"));
        assertThat(statuses).contains("REGISTRATION_OPEN");
        assertThat(statuses).doesNotContain("DRAFT");
    }

    @Test
    void lecturerDoesNotSeeDraftRounds() throws Exception {
        insertRound("rv-draft-round", "DRAFT");
        insertRound("rv-live-round", "PROPOSAL_OPEN");
        ThesisAuthorityFixture.lecturer(jdbc, "rv-lecturer-1");

        List<String> statuses = statusesAs(ThesisAuthorityFixture.lecturer(jdbc, "rv-lecturer-1"));
        assertThat(statuses).contains("PROPOSAL_OPEN");
        assertThat(statuses).doesNotContain("DRAFT");
    }

    @Test
    void anExplicitDraftQueryReturnsNothingToAStudent() throws Exception {
        insertRound("rv-draft-round", "DRAFT");

        MvcResult result = mvc.perform(get("/api/v1/thesis/rounds")
                        .param("status", "DRAFT")
                        .with(ThesisAuthorityFixture.student(jdbc, "rv-student-2")))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("[]");
    }

    @Test
    void governanceStillSeesDrafts() throws Exception {
        insertRound("rv-draft-round", "DRAFT");

        List<String> statuses = statusesAs(ThesisAuthorityFixture.role(jdbc, "rv-admin", "ADMIN"));
        assertThat(statuses).contains("DRAFT");
        List<String> deanStatuses = statusesAs(ThesisAuthorityFixture.role(jdbc, "rv-dean", "TRUONG_KHOA"));
        assertThat(deanStatuses).contains("DRAFT");
    }

    @Test
    void theRoundListRejectsUnauthenticatedCallers() throws Exception {
        mvc.perform(get("/api/v1/thesis/rounds"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- fixtures ----------

    private List<String> statusesAs(RequestPostProcessor caller) throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/thesis/rounds").with(caller))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return jdbc.query(
                "SELECT DISTINCT status FROM thesis.thesis_registration_round",
                (rs, i) -> rs.getString(1)).stream()
                .filter(s -> body.contains("\"status\":\"" + s + "\""))
                .toList();
    }

    private UUID insertRound(String name, String status) {
        UUID roundId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, lecturer_submit_start, lecturer_submit_end, "
                        + "registration_start, registration_end, gvpb_deadline, report_date, status) "
                        + "VALUES (?, ?, 'KLTN', ?, ?, ?, ?, ?, ?, ?)",
                roundId, name,
                Timestamp.from(now.minusSeconds(30 * 86_400L)),
                Timestamp.from(now.minusSeconds(15 * 86_400L)),
                Timestamp.from(now.minusSeconds(14 * 86_400L)),
                Timestamp.from(now.plusSeconds(14 * 86_400L)),
                Timestamp.from(now.plusSeconds(20 * 86_400L)),
                Timestamp.from(now.plusSeconds(30 * 86_400L)),
                status);
        return roundId;
    }
}
