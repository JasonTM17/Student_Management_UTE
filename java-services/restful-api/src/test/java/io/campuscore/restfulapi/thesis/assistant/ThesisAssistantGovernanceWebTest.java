package io.campuscore.restfulapi.thesis.assistant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantGovernanceWebTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    private UUID createdDocument;

    @AfterEach
    void cleanup() {
        if (createdDocument != null) {
            jdbc.update("DELETE FROM assistant.knowledge_document WHERE id=:id",
                    new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", createdDocument));
        }
    }

    @Test
    void adminCrudRequiresSecondReviewerAndArchivesPreviousPublication() throws Exception {
        String slug = "test-governance-" + UUID.randomUUID();
        String create = "{\"slug\":\"" + slug + "\",\"locale\":\"en\",\"title\":\"Draft title\",\"content\":\"Grounded draft content\",\"source\":\"test\",\"priority\":10}";
        String created = mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge")
                        .with(admin("admin-a"))
                        .contentType("application/json").content(create))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        createdDocument = UUID.fromString(mapper.readTree(created).get("documentId").asText());

        mvc.perform(put("/api/v1/admin/thesis/assistant/knowledge/{id}", createdDocument)
                        .with(admin("admin-a"))
                        .contentType("application/json")
                        .content("{\"slug\":\"" + slug + "-v2\",\"locale\":\"en\",\"title\":\"Updated title\",\"content\":\"Updated grounded content\",\"source\":\"test\",\"priority\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"));

        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge/{id}/submit", createdDocument)
                        .with(admin("admin-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING_REVIEW"));

        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge/{id}/publish", createdDocument)
                        .with(admin("admin-a")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_SECOND_REVIEW_REQUIRED"));

        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge/{id}/publish", createdDocument)
                        .with(admin("admin-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        mvc.perform(get("/api/v1/admin/thesis/assistant/knowledge/{id}", createdDocument)
                        .with(admin("admin-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(slug + "-v2"))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        Integer published = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PUBLISHED'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", createdDocument), Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, published);

        mvc.perform(put("/api/v1/admin/thesis/assistant/knowledge/{id}", createdDocument)
                        .with(admin("admin-a"))
                        .contentType("application/json")
                        .content("{\"slug\":\"" + slug + "-v3\",\"locale\":\"en\",\"title\":\"Third title\",\"content\":\"Third grounded content\",\"source\":\"test\",\"priority\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DRAFT"));
        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge/{id}/submit", createdDocument)
                        .with(admin("admin-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING_REVIEW"));
        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge/{id}/publish", createdDocument)
                        .with(admin("admin-b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PUBLISHED"));

        Integer publishedAfterArchive = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_document_revision WHERE document_id=:id AND state='PUBLISHED'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", createdDocument), Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, publishedAfterArchive);
        Integer archived = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assistant.knowledge_document_audit a JOIN assistant.knowledge_document_revision r ON r.id=a.revision_id WHERE r.document_id=:id AND a.action='ARCHIVE'",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource("id", createdDocument), Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, archived);
    }

    @Test
    void adminPriorityIsBoundedAndStudentsCannotReadKnowledgeAdminApi() throws Exception {
        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge")
                        .with(admin("admin-a"))
                        .contentType("application/json")
                        .content("{\"slug\":\"bad-priority\",\"locale\":\"en\",\"title\":\"x\",\"content\":\"y\",\"source\":\"test\",\"priority\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mvc.perform(get("/api/v1/admin/thesis/assistant/knowledge")
                        .with(jwt().jwt(token -> token.subject("student-a"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminKnowledgeRejectsSensitiveContentBeforeItCanBePublished() throws Exception {
        mvc.perform(post("/api/v1/admin/thesis/assistant/knowledge")
                        .with(admin("admin-a"))
                        .contentType("application/json")
                        .content("{\"slug\":\"privacy-negative\",\"locale\":\"en\",\"title\":\"Public guidance\",\"content\":\"Contact student@example.edu for details\",\"source\":\"test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KNOWLEDGE_PRIVACY_REJECTED"));
    }

    @Test
    void streamIncludesDiscriminatedMetaDeltaCitationAndDoneEvents() throws Exception {
        // SSE completes on the stream executor, so the assertions must run on
        // the async-dispatched response, not on the initial empty one.
        var result = mvc.perform(post("/api/v1/thesis/assistant/chat/stream")
                        .with(jwt().jwt(token -> token.subject("student-stream"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000010\"}"))
                .andExpect(status().isOk())
                .andReturn();
        result.getAsyncResult(30_000);
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"type\":\"meta\"")))
                .andExpect(content().string(containsString("\"type\":\"delta\"")))
                .andExpect(content().string(containsString("\"type\":\"citation\"")))
                .andExpect(content().string(containsString("\"type\":\"done\"")));
    }

    @Test
    void feedbackAdminSummaryAggregatesAcrossOwnersButStudentsAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/assistant/feedback")
                        .with(jwt().jwt(token -> token.subject("student-a"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"))))
                .andExpect(status().isForbidden());

        UUID conversation = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID question = UUID.randomUUID();
        UUID answer = UUID.randomUUID();
        var params = new org.springframework.jdbc.core.namedparam.MapSqlParameterSource();
        jdbc.update("INSERT INTO assistant.chat_conversation(id,owner_id,locale,state,expires_at)"
                        + " VALUES (:id,:owner,'en','ACTIVE',CURRENT_TIMESTAMP+30)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("id", conversation).addValue("owner", "feedback-owner-a"));
        try {
            jdbc.update("INSERT INTO assistant.chat_message(id,conversation_id,turn_id,role,content,model,degraded,reason_code)"
                            + " VALUES (:id,:conversation,:turn,'USER','seeded question','-',FALSE,'-')",
                    params.addValue("id", question).addValue("conversation", conversation).addValue("turn", turn));
            jdbc.update("INSERT INTO assistant.chat_message(id,conversation_id,turn_id,role,content,model,degraded,reason_code)"
                            + " VALUES (:id,:conversation,:turn,'ASSISTANT','seeded answer','m',FALSE,'ANSWERED')",
                    params.addValue("id", answer));
            jdbc.update("INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason)"
                            + " VALUES (:message,:owner,'UP','HELPFUL')",
                    params.addValue("message", answer).addValue("owner", "feedback-owner-a"));
            jdbc.update("INSERT INTO assistant.chat_message_feedback(message_id,owner_id,rating,reason)"
                            + " VALUES (:message,:owner,'DOWN','INCORRECT')",
                    params.addValue("owner", "feedback-owner-b"));

            // Two distinct owners' rows land in one admin summary — the
            // ADMIN_GOVERNANCE policy set is what makes the cross-owner read
            // possible on a FORCE-RLS datasource. Filter by the seeded
            // message id because other tests may share the fixture database.
            mvc.perform(get("/api/v1/admin/assistant/feedback").with(admin("admin-a")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totals.total", org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                    .andExpect(jsonPath("$.recent[?(@.messageId=='" + answer + "')].rating",
                            org.hamcrest.Matchers.containsInAnyOrder("UP", "DOWN")))
                    .andExpect(jsonPath("$.recent[?(@.messageId=='" + answer + "')].questionPreview",
                            org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("seeded question"))));
        } finally {
            jdbc.update("DELETE FROM assistant.chat_conversation WHERE id=:id", params);
        }
    }

    private static RequestPostProcessor admin(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
