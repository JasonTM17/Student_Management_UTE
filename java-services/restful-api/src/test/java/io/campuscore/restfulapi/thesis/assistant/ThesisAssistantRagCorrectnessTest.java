package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamDelta;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamDone;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamEvent;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamMeta;
import io.campuscore.restfulapi.web.DomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Correctness regression gate for the assistant RAG boundary: quota
 * exhaustion degrades instead of failing, the quota window buckets by the ICT
 * wall clock, the scope pre-gate can never be narrower than the alias
 * vocabulary retrieval uses, and the provider context is never sliced
 * mid-sentence.
 */
class ThesisAssistantRagCorrectnessTest {

    private ThesisAssistantService serviceWith(ThesisAssistantKnowledgeRepository knowledge,
            DeepSeekClient provider, ThesisAssistantRepository history,
            ThesisAssistantTurnRepository turns, ThesisAssistantCatalogRepository catalog) {
        return new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));
    }

    private ThesisAssistantKnowledgeRepository.KnowledgeDocument document(String slug, String title, String content) {
        return new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-" + "111111111111", slug, "en", title, content, "office");
    }

    @Test
    @DisplayName("CB-P2-5: quota exhaustion returns the grounded lexical answer with QUOTA_EXCEEDED, not a 429")
    void quotaExhaustionReturnsGroundedLexicalAnswerInsteadOf429() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(document("leave-of-absence", "Leave of absence", "Deferring results preserves credits.")));
        when(catalog.search(anyString(), anyList(), anyInt())).thenReturn(List.of());

        UUID request = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        when(turns.reserve(anyString(), eq(request), anyString(), isNull(), eq("en"), anyString(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.Reservation(
                        ThesisAssistantTurnRepository.ReservationStatus.NEW, turn, conversation, 1L, true, null, null, false));
        when(turns.markSnapshotReady(eq(turn), anyString(), eq(1L), anyString(),
                any(java.util.function.Consumer.class))).thenReturn(true);
        // The repository keeps the turn SNAPSHOT_READY and reports the quota block.
        when(turns.dispatch(eq(turn), anyString(), eq(1L), anyInt(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.DispatchDecision(false, false, "QUOTA_EXCEEDED"));
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("curated-lexical-rag"),
                anyString(), eq(true), eq("QUOTA_EXCEEDED"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.TerminalResult(
                        conversation, message, "Deferring results preserves credits.",
                        "curated-lexical-rag", true, "QUOTA_EXCEEDED", List.of(), false, "COMPLETED"));

        ThesisAssistantService service = serviceWith(knowledge, provider, history, turns, catalog);
        List<StreamEvent> events = new ArrayList<>();

        ChatResponse response = service.answer(
                "Can you explain the rules for credit withdrawal and deferment of results?",
                "en", null, "owner-quota", request, events::add);

        // Pre-fix behaviour threw DomainException 429 here and discarded the answer.
        assertEquals("QUOTA_EXCEEDED", response.reasonCode());
        assertTrue(response.degraded());
        assertEquals("Deferring results preserves credits.", response.answer());
        assertEquals("curated-lexical-rag", response.model());
        verifyNoInteractions(provider);
        // The stream still carries the grounded answer text so the client can render it.
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamMeta));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamDelta delta
                && delta.text().contains("Deferring results preserves credits.")));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamDone done
                && "QUOTA_EXCEEDED".equals(done.reasonCode()) && done.degraded()));
    }

    @Test
    @DisplayName("CB-P2-5: the quota bucket date follows the ICT wall clock near midnight")
    void quotaBucketDateUsesIctWallClock() {
        // 23:59:59 ICT on Jan 15 (16:59:59Z) must bucket to Jan 15...
        assertEquals(LocalDate.of(2026, 1, 15),
                AssistantTimezone.bucketDateAt(Instant.parse("2026-01-15T16:59:59Z")));
        // ...and 00:30 ICT on Jan 16 (17:30:00Z) must bucket to Jan 16, not Jan 15 (UTC).
        assertEquals(LocalDate.of(2026, 1, 16),
                AssistantTimezone.bucketDateAt(Instant.parse("2026-01-15T17:30:00Z")));
        // 07:00 ICT is exactly where the old UTC bucketing flipped.
        assertEquals(LocalDate.of(2026, 1, 16),
                AssistantTimezone.bucketDateAt(Instant.parse("2026-01-16T00:00:00Z")));
        assertEquals(LocalDate.now(AssistantTimezone.ZONE), AssistantTimezone.currentBucketDate());
    }

    @Test
    @DisplayName("CB-P2-3: every retrieval alias (folded and accented) is admitted by the scope pre-gate")
    void everyAliasIsAdmittedByThePublicScopeGate() {
        assertTrue(ThesisAssistantService.VIETNAMESE_FOLDED_PHRASE_ALIASES.size() >= 40);
        for (Map.Entry<String, String> alias : ThesisAssistantService.VIETNAMESE_FOLDED_PHRASE_ALIASES.entrySet()) {
            assertTrue(ThesisAssistantService.hasPublicScopeSignal(alias.getKey()),
                    "pre-gate must admit folded alias: " + alias.getKey());
            assertTrue(ThesisAssistantService.hasPublicScopeSignal(alias.getValue()),
                    "pre-gate must admit accented alias: " + alias.getValue());
        }
        // The in-corpus topics from the finding must reach retrieval now.
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("Bảo lưu thế nào?"));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("bao luu the nao"));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("xin giấy xác nhận sinh viên"));
        assertTrue(ThesisAssistantService.hasPublicScopeSignal("xet tuyen nhu the nao"));
        // Scoping is not disabled: small talk with no corpus vocabulary is still refused.
        assertFalse(ThesisAssistantService.hasPublicScopeSignal("chào bạn nhé"));
        assertFalse(ThesisAssistantService.hasPublicScopeSignal("hello there"));
    }

    @Test
    @DisplayName("CB-P3-2: the stop-word list carries the common Vietnamese function words in both views")
    void stopWordsCarryCommonVietnameseFunctionWords() {
        for (String word : List.of("bị", "bi", "đã", "da", "sẽ", "se", "phải", "phai", "một", "mot")) {
            assertTrue(ThesisAssistantService.STOP_WORDS.contains(word), "missing stop word: " + word);
        }
    }

    @Test
    @DisplayName("CB-P2-7: a context near the character budget contains only complete documents")
    void contextNearLimitContainsOnlyCompleteDocuments() {
        String docOneContent = "First document sentence one. First document sentence two. Final sentence of one.";
        String docTwoContent = "Second document opens here. Second document continues with more words and never fits.";
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = List.of(
                document("doc-one", "Doc One", docOneContent),
                document("doc-two", "Doc Two", docTwoContent));
        // Budget fits document one's whole block but not document two's.
        int budget = ("### Doc One\n".length() + docOneContent.length()) + 2
                + ("### Doc Two\n".length() + docTwoContent.length()) - 5;

        String context = ThesisAssistantService.buildGroundedContext(documents, budget);

        assertTrue(context.contains("Final sentence of one."), "complete first document must survive");
        assertFalse(context.contains("Doc Two"), "a document that does not fit must be dropped, not sliced");
        assertTrue(context.stripTrailing().endsWith("."),
                "context must end at a sentence boundary, never mid-word");
        assertTrue(context.length() <= budget);
    }

    @Test
    @DisplayName("CB-P2-7: a single oversized document truncates at a sentence boundary, never mid-sentence")
    void singleOversizedDocumentTruncatesAtSentenceBoundary() {
        String content = "Short opener. " + "A deliberately long sentence that will not fit inside the budget. ".repeat(6);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = List.of(
                document("big-doc", "Big Doc", content));

        String context = ThesisAssistantService.buildGroundedContext(documents, 80);

        assertFalse(context.isBlank());
        assertTrue(context.length() <= 80);
        assertTrue(context.stripTrailing().endsWith("."), "truncation must land on a sentence end");
        assertTrue(context.contains("Short opener."));
        assertFalse(context.endsWith("fit"), "must not end mid-sentence");
    }

    @Test
    @DisplayName("CB-P2-7: text with no sentence boundary inside the budget is omitted, never half-emitted")
    void textWithoutBoundaryIsDroppedNotSliced() {
        String noBoundary = "one-long-word-without-any-terminator".repeat(20);
        assertEquals("", ThesisAssistantService.truncateAtBoundary(noBoundary, 40));
        assertEquals("", ThesisAssistantService.truncateAtBoundary(null, 40));
        assertEquals("", ThesisAssistantService.truncateAtBoundary("abc", 0));
        assertEquals("Ok.", ThesisAssistantService.truncateAtBoundary("Ok. Tail", 4));
    }

    @Test
    @DisplayName("CB-P2-6: top-k is configurable and clamped to 3-10 with the historical default 5")
    void topKIsConfigurableAndClamped() {
        assertEquals(5, new AssistantProperties(6000, 2000, 20, 200, 90, true, null).topK());
        assertEquals(3, new AssistantProperties(6000, 2000, 20, 200, 90, true, 1).topK());
        assertEquals(10, new AssistantProperties(6000, 2000, 20, 200, 90, true, 50).topK());
        assertEquals(7, new AssistantProperties(6000, 2000, 20, 200, 90, true, 7).topK());
    }
}
