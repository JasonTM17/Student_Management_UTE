package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class ThesisAssistantServiceTest {

    @Test
    void databaseOutageReturnsExplicitDegradedResponseWithoutCitations() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        ChatResponse response = new ThesisAssistantService(knowledge)
                .answer("Điều kiện đăng ký đề tài là gì?", "vi");

        assertTrue(response.degraded());
        assertEquals("KNOWLEDGE_UNAVAILABLE", response.reasonCode());
        assertEquals("curated-lexical-rag", response.model());
        assertEquals("vi", response.locale());
        assertTrue(response.citations().isEmpty());
    }
}
