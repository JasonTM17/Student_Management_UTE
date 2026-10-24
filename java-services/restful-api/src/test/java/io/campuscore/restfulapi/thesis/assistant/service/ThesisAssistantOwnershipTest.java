package io.campuscore.restfulapi.thesis.assistant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataAccessResourceFailureException;
import org.junit.jupiter.api.Test;
import io.campuscore.restfulapi.thesis.assistant.client.DeepSeekClient;
import io.campuscore.restfulapi.thesis.assistant.properties.AssistantProperties;
import io.campuscore.restfulapi.thesis.assistant.properties.DeepSeekProperties;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantKnowledgeRepository;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantRepository;

class ThesisAssistantOwnershipTest {
    @Test
    void chatUsesCallerOwnerWhenCreatingAndDeletingHistory() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        // Two-term corroboration ("học" + "phí" both whole-word hits) keeps the
        // confident-gate honest while still reaching the quota path.
        var document = new KnowledgeDocument(
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
        var document = new KnowledgeDocument(
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
        var document = new KnowledgeDocument(
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

    @Test
    void multiPartAnswerKeepsCitationsWhenOnlyOneSectionIsDenied() {
        // Live provider proof (session-race audit): a three-section grounded
        // answer that ended with one "Cổng học vụ chưa công bố" section lost
        // every citation. The grounded sections legitimately earned their
        // provenance — only a wholly-denied answer may shed citations.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        var document = new KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "hoc-phi", "vi", "Học phí học kỳ",
                "Grounded context", "office", "THESIS", null, null, null, null, null, null, null, null, 30);
        when(knowledge.search(anyString(), any(), anyInt())).thenReturn(List.of(document));
        when(history.ensureConversation(anyString(), any(), anyString(), anyInt())).thenReturn(UUID.randomUUID());
        when(history.appendMessage(any(), anyString(), anyString(), anyString(), anyBoolean(), anyString()))
                .thenReturn(UUID.randomUUID());
        when(history.consumeQuota(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(provider.complete(anyString(), anyString(), anyString()))
                .thenReturn("## Xử lý khi nhận điểm F\n\n"
                        + "- Học phần bị coi là không đạt khi điểm tổng kết dưới 4.0 (thang 10).\n"
                        + "- Bạn phải đăng ký học lại chính môn đó trong các học kỳ tiếp theo.\n\n"
                        + "## Lịch bảo vệ\n\n"
                        + "Cổng học vụ chưa công bố lịch bảo vệ khóa luận.");
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history,
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        var response = service.answer("học phí?", "vi", null, "owner-a");

        assertEquals("ANSWERED", response.reasonCode());
        org.junit.jupiter.api.Assertions.assertFalse(response.citations().isEmpty(),
                "a multi-part answer keeps the citations its grounded sections earned");
        assertEquals(1, response.citations().size());
    }

    @Test
    void wrapperSentenceWithGroundedContrastTailKeepsFixtureCitations() {
        // A marker-free wrapper sentence can hide a grounded independent claim
        // behind a contrast boundary: "Xin lỗi … nhưng học phí đã niêm yết"
        // must keep the citations the grounded tail earned, while a pure
        // wrapper tail ("và sẽ thông báo…") still strips them.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        String sourceId = "11111111-1111-1111-1111-111111111111";
        var document = new KnowledgeDocument(
                sourceId, "hoc-phi", "vi", "Học phí học kỳ",
                "Grounded context", "office", "THESIS", null, null, null, null, null, null, null, null, 30);
        when(knowledge.search(anyString(), any(), anyInt())).thenReturn(List.of(document));
        when(history.ensureConversation(anyString(), any(), anyString(), anyInt())).thenReturn(UUID.randomUUID());
        when(history.appendMessage(any(), anyString(), anyString(), anyString(), anyBoolean(), anyString()))
                .thenReturn(UUID.randomUUID());
        when(history.consumeQuota(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(provider.complete(anyString(), anyString(), anyString()))
                .thenReturn("Cổng học vụ chưa công bố lịch thi. Xin lỗi và sẽ thông báo khi có lịch.",
                        "Cổng học vụ chưa công bố lịch thi. Xin lỗi vì bất tiện nhưng học phí đã niêm yết.");
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history,
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        var pureDenial = service.answer("học phí?", "vi", null, "owner-a");
        assertEquals("ANSWERED", pureDenial.reasonCode());
        org.junit.jupiter.api.Assertions.assertTrue(pureDenial.citations().isEmpty(),
                "a pure denial keeps no citations: " + pureDenial.citations());

        var mixed = service.answer("học phí?", "vi", null, "owner-a");
        assertEquals("ANSWERED", mixed.reasonCode());
        org.junit.jupiter.api.Assertions.assertTrue(mixed.citations().stream()
                        .anyMatch(citation -> sourceId.equals(citation.sourceId())),
                "a grounded contrast tail keeps the retrieved source: " + mixed.citations());
    }
}
