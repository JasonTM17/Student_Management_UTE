package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.thesis.domain.ThesisTopic;
import io.campuscore.restfulapi.thesis.repository.ThesisTopicRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisTopicPersistenceTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ThesisTopicRepository topics;

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void cleanDatabase() {
        topics.deleteAll();
        jdbc.update("DELETE FROM thesis.thesis_registration_round");
    }

    @Test
    void flywayCreatesTheLegacyCompatibleThesisSchema() {
        Integer topicTableCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE LOWER(TABLE_SCHEMA) = 'thesis' AND LOWER(TABLE_NAME) = 'thesis_topic'",
                Integer.class);
        Integer migrationCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM thesis.flyway_schema_history WHERE version = '1'",
                Integer.class);

        assertEquals(1, topicTableCount);
        assertEquals(1, migrationCount);
    }

    @Test
    void publishedTopicsUseTheSingleAppRestContract() throws Exception {
        UUID roundId = insertRound();
        ThesisTopic draft = new ThesisTopic(
                roundId,
                UUID.randomUUID(),
                "A draft topic",
                "Draft description",
                2,
                UUID.randomUUID());
        topics.save(draft);

        ThesisTopic published = new ThesisTopic(
                roundId,
                UUID.randomUUID(),
                "A published topic",
                "Published description",
                3,
                UUID.randomUUID());
        published.publish();
        topics.save(published);

        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", roundId.toString())
                        .with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("A published topic"))
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"));
    }

    @Test
    void thesisReadPathRejectsAnonymousRequests() throws Exception {
        mvc.perform(get("/api/v1/thesis/topics")
                        .queryParam("roundId", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private UUID insertRound() {
        UUID roundId = UUID.randomUUID();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Instant end = Instant.parse("2026-02-01T00:00:00Z");
        jdbc.update(
                "INSERT INTO thesis.thesis_registration_round "
                        + "(id, name, thesis_type, registration_start, registration_end, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                roundId,
                "2026 Capstone",
                "CAPSTONE",
                Timestamp.from(start),
                Timestamp.from(end),
                "OPEN");
        return roundId;
    }
}
