package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;

class ThesisAssistantOwnershipTest {
    @Test
    void chatUsesCallerOwnerWhenCreatingAndDeletingHistory() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        // Two-term corroboration ("học" + "phí" both whole-word hits) keeps the
        // confident-gate honest while still reaching the quota path.
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "hoc-phi", "vi", "Học phí học kỳ",
                "Grounded context", "office", "THESIS", null, null, null, null, null, null, null, null, 30);
        when(knowledge.search(anyString(), any(), anyInt())).thenReturn(List.of(document));
        when(history.ensureConversation(eq("owner-a"), isNull(), eq("vi"), anyInt())).thenReturn(UUID.randomUUID());
        when(history.appendMessage(any(), anyString(), anyString(), anyString(), anyBoolean(), anyString())).thenReturn(UUID.randomUUID());
        when(history.deleteConversation(any(), eq("owner-a"))).thenReturn(1);
        when(history.consumeQuota(anyString(), anyInt(), anyInt())).thenReturn(false);
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history,
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        var response = service.answer("học phí?", "vi", null, "owner-a");
        assertEquals("QUOTA_EXCEEDED", response.reasonCode());
        verify(history).ensureConversation(eq("owner-a"), isNull(), eq("vi"), eq(90));
        service.deleteConversation(UUID.randomUUID(), "owner-a");
        verify(history).deleteConversation(any(), eq("owner-a"));
        verifyNoInteractions(provider);
    }

    @Test
    void historyOutageFallsBackToGroundedLexicalAnswer() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "hoc-phi", "vi", "Học phí học kỳ",
                "Grounded context", "office", "THESIS", null, null, null, null, null, null, null, null, 30);
        when(knowledge.search(anyString(), any(), anyInt())).thenReturn(List.of(document));
        when(history.ensureConversation(anyString(), any(), anyString(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("history unavailable"));
        ThesisAssistantService service = new ThesisAssistantService(knowledge, mock(DeepSeekClient.class), history,
                new DeepSeekProperties(false, "", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        var response = service.answer("học phí?", "vi", null, "owner-a");
        assertEquals("HISTORY_UNAVAILABLE", response.reasonCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.degraded());
        assertEquals("Grounded context", response.answer());
    }

    @Test
    void portalDenialAnswerCarriesNoCitations() {
        // Live audit: "CI/CD pipeline là gì" was answered "Cổng học vụ chưa
        // công bố nội dung về CI/CD" while still citing the retrieved
        // prerequisite-map catalog row — a citation implies the document is
        // the answer's source, which the denial explicitly disclaims.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "hoc-phi", "vi", "Học phí học kỳ",
                "Grounded context", "office", "THESIS", null, null, null, null, null, null, null, null, 30);
        when(knowledge.search(anyString(), any(), anyInt())).thenReturn(List.of(document));
        when(history.ensureConversation(anyString(), any(), anyString(), anyInt())).thenReturn(UUID.randomUUID());
        when(history.appendMessage(any(), anyString(), anyString(), anyString(), anyBoolean(), anyString()))
                .thenReturn(UUID.randomUUID());
        when(history.consumeQuota(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(provider.complete(anyString(), anyString(), anyString()))
                .thenReturn("Cổng học vụ chưa công bố nội dung về chủ đề này.");
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history,
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        var response = service.answer("học phí?", "vi", null, "owner-a");

        assertEquals("ANSWERED", response.reasonCode());
        org.junit.jupiter.api.Assertions.assertTrue(response.citations().isEmpty(),
                "a not-published answer must not carry retrieval citations: " + response.citations());
    }
}
