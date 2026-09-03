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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@SpringBootTest(properties = {"deepseek.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantContractTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void chatRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .contentType("application/json")
               .content("{\"message\":\"How do I choose a topic?\",\"locale\":\"en\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000001\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void chatReturnsCuratedAnswerWithCitation() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
               .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000002\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isString())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("thesis topic")))
                .andExpect(jsonPath("$.model").value("curated-lexical-rag"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reasonCode").value("PROVIDER_DISABLED"))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.citations[0].slug").value("en-topic-selection"));
    }

    @Test
    void chatSupportsVietnameseLocaleAndValidatesRequestShape() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
               .content("{\"message\":\"Em nên chọn đề tài thế nào?\",\"locale\":\"vi\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000003\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("đề tài")))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.reasonCode").value("PROVIDER_DISABLED"))
                .andExpect(jsonPath("$.citations[0].source").value("academic-office"));

        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
               .content("{\"message\":\"\",\"locale\":\"fr\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000004\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.message").value("message is required"))
                .andExpect(jsonPath("$.fields.locale").value("locale must be en or vi"));
    }

    @Test
    void noMatchDoesNotInventCitation() throws Exception {
        mvc.perform(post("/api/v1/thesis/assistant/chat")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_STUDENT")))
                        .contentType("application/json")
               .content("{\"message\":\"What is the weather tomorrow?\",\"locale\":\"en\",\"clientRequestId\":\"00000000-0000-4000-8000-000000000005\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("NO_MATCH"))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.degraded").value(false));
    }
}
