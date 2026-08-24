package io.campuscore.restfulapi.thesis.assistant;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Deterministic H2 characterization for the V12 request/turn contract. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantTurnStateMachineTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private NamedParameterJdbcTemplate jdbc;

    @Test
    void sameKeyReplaysExactlyOneCommittedExchange() throws Exception {
        String owner = "state-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        String body = body("How do I choose a thesis topic?", key);

        String first = mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientRequestId").value(key.toString()))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.terminalStatus").value("COMPLETED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode firstJson = mapper.readTree(first);

        String second = mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true))
                .andExpect(jsonPath("$.messageId").value(firstJson.get("messageId").asText()))
                .andReturn().getResponse().getContentAsString();
        assertEquals(firstJson.get("answer").asText(), mapper.readTree(second).get("answer").asText());

        Integer ledgers = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:key",
                params(owner, key), Integer.class);
        Integer users = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message m JOIN assistant.chat_turn_ledger l ON l.turn_id=m.turn_id WHERE l.owner_id=:owner AND l.client_request_id=:key AND m.role='USER'",
                params(owner, key), Integer.class);
        Integer assistants = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_message m JOIN assistant.chat_turn_ledger l ON l.turn_id=m.turn_id WHERE l.owner_id=:owner AND l.client_request_id=:key AND m.role='ASSISTANT'",
                params(owner, key), Integer.class);
        assertEquals(1, ledgers);
        assertEquals(1, users);
        assertEquals(1, assistants);
    }

    @Test
    void sameKeyWithDifferentCanonicalPayloadIsConflict() throws Exception {
        String owner = "hash-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body("How do I choose a thesis topic?", key)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body("How do I prepare for a defense?", key)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void sensitivePromptIsRejectedBeforeLedgerOrProviderBoundary() throws Exception {
        String owner = "privacy-owner-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body("Email me at student@example.edu", key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("SENSITIVE_EMAIL"))
                .andExpect(jsonPath("$.terminalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.citations").isEmpty());
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:key", params(owner, key), Integer.class);
        assertEquals(0, count);
    }

    @Test
    void completedTurnCannotBeCancelledAndFeedbackIsOwnerScoped() throws Exception {
        String owner = "feedback-owner-" + UUID.randomUUID();
        String other = "feedback-other-" + UUID.randomUUID();
        UUID key = UUID.randomUUID();
        JsonNode result = mapper.readTree(mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body("How do I choose a thesis topic?", key)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        UUID messageId = UUID.fromString(result.get("messageId").asText());
        UUID conversationId = UUID.fromString(result.get("conversationId").asText());

        mvc.perform(post("/api/v1/thesis/assistant/requests/{id}/cancel", key).with(student(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TURN_COMPLETED"));
        mvc.perform(put("/api/v1/thesis/assistant/messages/{id}/feedback", messageId).with(student(other))
                        .contentType("application/json").content("{\"rating\":\"DOWN\",\"reason\":\"INCORRECT\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/thesis/assistant/messages/{id}/feedback", messageId).with(student(owner))
                        .contentType("application/json").content("{\"rating\":\"UP\",\"reason\":\"HELPFUL\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rating").value("UP"));
        mvc.perform(delete("/api/v1/thesis/assistant/messages/{id}/feedback", messageId).with(student(owner)))
                .andExpect(status().isNoContent());

        mvc.perform(delete("/api/v1/thesis/assistant/conversations/{id}", conversationId).with(student(owner)))
                .andExpect(status().isNoContent());
        Integer state = jdbc.queryForObject("SELECT COUNT(*) FROM assistant.chat_turn_ledger WHERE owner_id=:owner AND client_request_id=:key AND state='PURGED'", params(owner, key), Integer.class);
        assertEquals(1, state);
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(body("How do I choose a thesis topic?", key)))
                .andExpect(status().isGone());
    }

    @Test
    void missingClientRequestIdIsRejectedByPublicContract() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student("shape-owner-" + UUID.randomUUID()))
                        .contentType("application/json").content("{\"message\":\"topic\",\"locale\":\"en\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.clientRequestId").value("clientRequestId is required"));
    }

    @Test
    void explicitConversationStaysHiddenUntilTheFirstTerminalCommit() throws Exception {
        String owner = "pending-owner-" + UUID.randomUUID();
        String created = mvc.perform(post("/api/v1/thesis/assistant/conversations")
                        .with(student(owner)).contentType("application/json")
                        .content("{\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID conversation = UUID.fromString(mapper.readTree(created).get("id").asText());

        mvc.perform(get("/api/v1/thesis/assistant/conversations").with(student(owner)))
                .andExpect(status().isOk()).andExpect(content().string("[]"));

        UUID key = UUID.randomUUID();
        String payload = "{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"conversationId\":\""
                + conversation + "\",\"clientRequestId\":\"" + key + "\"}";
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").value(conversation.toString()));

        mvc.perform(get("/api/v1/thesis/assistant/conversations").with(student(owner)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(conversation.toString())));
    }

    @Test
    void historyUsesOwnerScopedCursorAndNewestMessagesFirstThenDisplayOrder() throws Exception {
        String owner = "history-owner-" + UUID.randomUUID();
        UUID conversation = null;
        for (int i = 0; i < 3; i++) {
            UUID key = UUID.randomUUID();
            String payload = body("How do I choose a thesis topic?", key);
            JsonNode response = mapper.readTree(mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                            .contentType("application/json").content(payload))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            conversation = UUID.fromString(response.get("conversationId").asText());
        }
        for (int i = 0; i < 2; i++) {
            UUID key = UUID.randomUUID();
            String payload = "{\"message\":\"How do I prepare for a thesis defense?\",\"locale\":\"en\",\"conversationId\":\"" + conversation + "\",\"clientRequestId\":\"" + key + "\"}";
            mvc.perform(post("/api/v1/thesis/assistant/chat").with(student(owner))
                            .contentType("application/json").content(payload)).andExpect(status().isOk());
        }
        String conversations = mvc.perform(get("/api/v1/thesis/assistant/conversations").with(student(owner)).param("limit", "2"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Next-Cursor"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(2, mapper.readTree(conversations).size());
        // A single conversation has six messages; the endpoint returns the newest
        // page reversed into chronological display order.
        String messages = mvc.perform(get("/api/v1/thesis/assistant/conversations/{id}/messages", conversation)
                        .with(student(owner)).param("limit", "2"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Next-Cursor"))
                .andReturn().getResponse().getContentAsString();
        JsonNode page = mapper.readTree(messages);
        assertEquals(2, page.size());
        assertTrue(java.time.Instant.parse(page.get(0).get("createdAt").asText())
                .compareTo(java.time.Instant.parse(page.get(1).get("createdAt").asText())) <= 0);
    }

    private static String body(String message, UUID key) {
        return "{\"message\":\"" + message.replace("\"", "\\\"") + "\",\"locale\":\"en\",\"clientRequestId\":\"" + key + "\"}";
    }

    private static MapSqlParameterSource params(String owner, UUID key) {
        return new MapSqlParameterSource().addValue("owner", owner).addValue("key", key);
    }

    private static RequestPostProcessor student(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
