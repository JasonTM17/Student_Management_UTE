package io.campuscore.restfulapi.thesis;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .contentType("application/json")
                        .content("{\"message\":\"How do I choose a topic?\",\"locale\":\"en\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void chatReturnsCuratedAnswerWithCitation() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("thesis topic")))
                .andExpect(jsonPath("$.model").value("curated-lexical-rag"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.citations[0].slug").value("en-topic-selection"));
    }

    @Test
    void chatSupportsVietnameseLocaleAndValidatesRequestShape() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"Em nên chọn đề tài thế nào?\",\"locale\":\"vi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("đề tài")))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.citations[0].source").value("academic-office"));

        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"\",\"locale\":\"fr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.message").value("message is required"))
                .andExpect(jsonPath("$.fields.locale").value("locale must be en or vi"));
    }

    @Test
    void noMatchDoesNotInventCitation() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"message\":\"What is the weather tomorrow?\",\"locale\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("NO_MATCH"))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.degraded").value(false));
    }
}
