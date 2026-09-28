package io.campuscore.restfulapi.thesis.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.campuscore.restfulapi.web.DomainException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Chatbot-excellence fallback chain against the real H2 knowledge base: when
 * the remote RAG gateway fails transiently, the JSON chat endpoint must answer
 * 200 with a degraded, locally-grounded response carrying a real KB citation —
 * never a 5xx and never an empty body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantRagFallbackWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RagAssistantGateway ragGateway;

    @Test
    void ragGatewayOutageAnswers200WithLocalGroundedCitation() throws Exception {
        when(ragGateway.enabled()).thenReturn(true);
        when(ragGateway.isTransientFailure(any())).thenReturn(true);
        when(ragGateway.chat(any(), anyString()))
                .thenThrow(new DomainException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_SERVICE_UNAVAILABLE",
                        "RAG service request failed"));

        UUID key = UUID.randomUUID();
        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student("fallback-owner-" + UUID.randomUUID()))
                        .contentType("application/json")
                        .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"clientRequestId\":\""
                                + key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.model").value("curated-lexical-rag"))
                .andExpect(jsonPath("$.citations").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].sourceId").isNotEmpty())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    void ragGatewayNoMatchFallsBackToTheLocalKnowledgeBase() throws Exception {
        when(ragGateway.enabled()).thenReturn(true);
        when(ragGateway.chat(any(), anyString())).thenReturn(new ThesisAssistantDtos.ChatResponse(
                "Mình chưa tìm thấy hướng dẫn phù hợp trong kho kiến thức công khai.",
                ThesisAssistantService.MODEL, false, "NO_MATCH", "vi",
                java.util.List.of()));

        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student("nomatch-owner-" + UUID.randomUUID()))
                        .contentType("application/json")
                        .content("{\"message\":\"Điều kiện đăng ký đề tài tốt nghiệp là gì?\",\"locale\":\"vi\",\"clientRequestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.citations").isNotEmpty());
    }

    private static RequestPostProcessor student(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
