package io.campuscore.restfulapi.thesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "THESIS_READ_ENABLED=true",
    "FLYWAY_ENABLED=false"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "THESIS_RESTORE_SMOKE", matches = "true")
@ActiveProfiles("persistence")
class ThesisReadOnlyRestoreSmokeTest {

    private static final UUID ROUND_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TOPIC_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GROUP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID COUNCIL_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void restoredSnapshotIsReadableThroughTheReadOnlyJavaCandidate() throws Exception {
        assertEquals("campuscore_ro_reader", jdbc.queryForObject("select current_user", String.class));
        assertEquals("on", jdbc.queryForObject("select current_setting('default_transaction_read_only')", String.class));
        assertEquals("5s", jdbc.queryForObject("select current_setting('statement_timeout')", String.class));
        assertEquals(
                ROUND_ID,
                UUID.fromString(jdbc.queryForObject(
                        "select id::text from thesis.thesis_registration_round where id = ?",
                        String.class,
                        ROUND_ID)));

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", ROUND_ID.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TOPIC_ID.toString()));

        mvc.perform(get("/api/v1/thesis/groups")
                        .queryParam("roundId", ROUND_ID.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(GROUP_ID.toString()));

        mvc.perform(get("/api/v1/thesis/councils")
                        .queryParam("roundId", ROUND_ID.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(COUNCIL_ID.toString()));
    }
}
