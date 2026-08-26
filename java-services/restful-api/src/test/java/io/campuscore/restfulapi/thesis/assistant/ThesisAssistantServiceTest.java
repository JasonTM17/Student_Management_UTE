package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.CompletionResult;
import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.ProviderSegment;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamDelta;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamEvent;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamReplace;
import io.campuscore.restfulapi.web.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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

    @Test
    void preReservationDisconnectBlocksWorkerBeforeKnowledgeProviderQuotaOrLedgerReservation() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        AssistantCancellationRegistry cancellations = new AssistantCancellationRegistry();
        UUID request = UUID.randomUUID();
        when(turns.cancelBeforeReservation(anyString(), eq(request), anyString(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.CancelResult(true, "CANCELLED"));

        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                cancellations,
                new DeepSeekProperties(false, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90));

        service.cancelBeforeStart(request, "owner-disconnect", "topic", "en", null);

        when(turns.reserve(anyString(), eq(request), anyString(), isNull(), eq("en"), anyString(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenThrow(new DomainException(HttpStatus.GONE, "TURN_PURGED", "Request key is no longer replayable"));

        assertThrows(DomainException.class,
                () -> service.stream("topic", "en", null, "owner-disconnect", request, ignored -> { }));
        verifyNoInteractions(knowledge, provider, catalog, history);
    }

    @Test
    void malformedConversationDoesNotLeakPreReservationCancellationFence() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        AssistantCancellationRegistry cancellations = new AssistantCancellationRegistry();
        UUID request = UUID.randomUUID();
        String owner = "owner-malformed-conversation";

        ThesisAssistantService service = new ThesisAssistantService(knowledge, mock(DeepSeekClient.class),
                mock(ThesisAssistantRepository.class), turns, mock(ThesisAssistantCatalogRepository.class),
                cancellations,
                new DeepSeekProperties(false, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90));

        DomainException invalid = assertThrows(DomainException.class,
                () -> service.cancelBeforeStart(request, owner, "topic", "en", "not-a-uuid"));

        assertEquals("INVALID_CONVERSATION_ID", invalid.code());
        assertFalse(cancellations.isPreCancelled(owner, request));
    }

    @Test
    void cancelledGenerationIsRemovedWhenCancellationWinsImmediatelyAfterRegistration() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        AssistantCancellationRegistry cancellations = new AssistantCancellationRegistry();
        UUID request = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        when(turns.reserve(anyString(), eq(request), anyString(), isNull(), eq("en"), anyString(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.Reservation(
                        ThesisAssistantTurnRepository.ReservationStatus.NEW, turn, conversation, 1L, true, null, null, false));
        // Model the cancel CAS winning before the worker's first cancellation
        // check. The service must not leave the generation handle resident.
        cancellations.fence("owner-immediate-cancel", request, 1L);

        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                cancellations,
                new DeepSeekProperties(false, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90));

        DomainException cancelled = assertThrows(DomainException.class,
                () -> service.stream("topic", "en", null, "owner-immediate-cancel", request, ignored -> { }));
        assertEquals("TURN_CANCELLED", cancelled.code());
        AtomicBoolean replacement = cancellations.register("owner-immediate-cancel", request, 1L);
        assertTrue(!replacement.get(), "the cancelled generation handle must have been removed");
        cancellations.remove("owner-immediate-cancel", request, 1L);
        verifyNoInteractions(knowledge, provider, catalog, history);
    }

    @Test
    void legacyUnsafeKnowledgeIsFilteredBeforeLexicalFallback() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                        "unsafe", "unsafe", "vi", "Registration contact", "Student email student@example.edu", "curated")));

        ChatResponse response = new ThesisAssistantService(knowledge)
                .answer("email", "vi");

        assertEquals("NO_MATCH", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        assertTrue(!response.answer().contains("student@example.edu"));
    }

    @Test
    void providerOutputPrivacyIsCheckedAcrossFragmentedSegmentsBeforeCommit() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "topic", "en", "Topic", "Grounded answer", "office");
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(document));
        when(catalog.search(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(provider.model()).thenReturn("deepseek-v4-flash");

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
        when(turns.dispatch(eq(turn), anyString(), eq(1L), anyInt(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.DispatchDecision(true, true, "DISPATCHED"));
        Citation citation = new Citation("doc", "topic", "Topic", "office", "en", "Grounded answer",
                "THESIS", "CURATED", "11111111-1111-1111-1111-111111111111", null, null, "hash", null, null, null);
        when(turns.complete(eq(turn), anyString(), eq(1L), eq("topic"), eq("curated-lexical-rag"),
                eq("Grounded answer"), eq(true), eq("PROVIDER_UNSAFE_OUTPUT"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.TerminalResult(
                        conversation, message, "Grounded answer", "curated-lexical-rag", true,
                        "PROVIDER_UNSAFE_OUTPUT", List.of(citation), false, "COMPLETED"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<ProviderSegment> sink = invocation.getArgument(1);
            // The identifier is deliberately split across frames; checking only
            // one frame would miss it.
            sink.accept(new ProviderSegment(0, "Contact student@", List.of("11111111-1111-1111-1111-111111111111")));
            sink.accept(new ProviderSegment(1, "example.edu", List.of("11111111-1111-1111-1111-111111111111")));
            return new CompletionResult("Contact student@example.edu", List.of(), "stop");
        }).when(provider).complete(
                any(AssistantCompletionProvider.CompletionRequest.class),
                any(java.util.function.Consumer.class),
                any(java.util.function.BooleanSupplier.class));

        List<StreamEvent> events = new ArrayList<>();
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90));

        ChatResponse response = service.answer("topic", "en", null, "owner-a", request, events::add);

        assertEquals("PROVIDER_UNSAFE_OUTPUT", response.reasonCode());
        assertEquals("Grounded answer", response.answer());
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && "PROVIDER_UNSAFE_OUTPUT".equals(replace.reasonCode())));
        assertTrue(events.stream().noneMatch(event -> event instanceof StreamDelta delta
                && delta.text().contains("example.edu")));
        verify(turns).complete(eq(turn), eq("owner-a"), eq(1L), eq("topic"), eq("curated-lexical-rag"),
                eq("Grounded answer"), eq(true), eq("PROVIDER_UNSAFE_OUTPUT"), anyList(),
                any(java.util.function.Consumer.class));
    }

    @Test
    void terminalRaceClearsAlreadyStreamedTextBeforePropagatingTheStableError() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "22222222-2222-2222-2222-222222222222", "topic", "en", "Topic", "Grounded answer", "office");
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(document));
        when(catalog.search(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(provider.model()).thenReturn("deepseek-v4-flash");

        UUID request = UUID.randomUUID();
        UUID turn = UUID.randomUUID();
        UUID conversation = UUID.randomUUID();
        when(turns.reserve(anyString(), eq(request), anyString(), isNull(), eq("en"), anyString(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.Reservation(
                        ThesisAssistantTurnRepository.ReservationStatus.NEW, turn, conversation, 1L, true, null, null, false));
        when(turns.markSnapshotReady(eq(turn), anyString(), eq(1L), anyString(),
                any(java.util.function.Consumer.class))).thenReturn(true);
        when(turns.dispatch(eq(turn), anyString(), eq(1L), anyInt(), anyInt(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.DispatchDecision(true, true, "DISPATCHED"));
        when(turns.complete(eq(turn), anyString(), eq(1L), eq("topic"), eq("deepseek-v4-flash"),
                eq("grounded answer"), eq(false), eq("ANSWERED"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenThrow(new DomainException(HttpStatus.CONFLICT, "TURN_TERMINAL_RACE", "cancel won"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<ProviderSegment> sink = invocation.getArgument(1);
            sink.accept(new ProviderSegment(0, "grounded answer", List.of("22222222-2222-2222-2222-222222222222")));
            return new CompletionResult("grounded answer", List.of(), "stop");
        }).when(provider).complete(any(AssistantCompletionProvider.CompletionRequest.class),
                any(java.util.function.Consumer.class), any(java.util.function.BooleanSupplier.class));

        List<StreamEvent> events = new ArrayList<>();
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90));

        assertThrows(DomainException.class,
                () -> service.answer("topic", "en", null, "owner-race", request, events::add));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamDelta));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && replace.text().isEmpty() && "TURN_TERMINAL_RACE".equals(replace.reasonCode())));
    }
}
