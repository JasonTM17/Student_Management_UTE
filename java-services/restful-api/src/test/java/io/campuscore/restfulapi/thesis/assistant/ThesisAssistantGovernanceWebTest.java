package io.campuscore.restfulapi.thesis.assistant;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
        var initial = mvc.perform(post("/api/v1/thesis/assistant/chat/stream")
                        .with(jwt().jwt(token -> token.subject("student-stream"))
                                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
                        .accept("text/event-stream")
                        .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000010\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"type\":\"meta\"")))
                .andExpect(content().string(containsString("\"type\":\"delta\"")))
                .andExpect(content().string(containsString("\"type\":\"citation\"")))
                .andExpect(content().string(containsString("\"type\":\"done\"")));
    }

    private static RequestPostProcessor admin(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
