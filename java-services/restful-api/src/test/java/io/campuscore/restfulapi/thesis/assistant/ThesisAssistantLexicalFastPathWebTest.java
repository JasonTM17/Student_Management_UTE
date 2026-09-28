package io.campuscore.restfulapi.thesis.assistant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Lexical-first fast path end to end against the real H2 knowledge base: a
 * confident curated-KB question must answer 200, non-degraded, with the
 * curated-lexical-fast model and a real citation — and the remote RAG gateway
 * must never be contacted. The test profile switches the fast path off by
 * default (see application-test.yml); this class flips it back on explicitly.
 */
@SpringBootTest(properties = "assistant.lexical-fast-path=true")
@AutoConfigureMockMvc
@ActiveProfiles({"test", "persistence"})
class ThesisAssistantLexicalFastPathWebTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private RagAssistantGateway ragGateway;

    @Test
    void confidentKnowledgeQuestionAnswersFromTheCuratedCorpusWithoutTheGateway() throws Exception {
        when(ragGateway.enabled()).thenReturn(true);

        mvc.perform(post("/api/v1/thesis/assistant/chat").with(student("fastpath-owner-" + UUID.randomUUID()))
                        .contentType("application/json")
                        .content("{\"message\":\"How do I choose a thesis topic?\",\"locale\":\"en\",\"clientRequestId\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reasonCode").value("ANSWERED"))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.model").value("curated-lexical-fast"))
                .andExpect(jsonPath("$.citations").isNotEmpty())
                .andExpect(jsonPath("$.citations[0].sourceId").isNotEmpty())
                .andExpect(jsonPath("$.answer").isNotEmpty());

        verify(ragGateway, org.mockito.Mockito.never()).chat(any(), anyString());
    }

    private static RequestPostProcessor student(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_STUDENT"));
    }
}
