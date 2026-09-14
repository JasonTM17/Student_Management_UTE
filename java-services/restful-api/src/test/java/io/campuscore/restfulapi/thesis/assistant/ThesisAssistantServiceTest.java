package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessResourceFailureException;

class ThesisAssistantServiceTest {

    @Test
    void directFactLookupStaysOnRagWithoutProviderDispatch() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", "topic", "en", "Topic", "topic details", "office");
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(document));
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
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("curated-lexical-rag"),
                eq("topic details"), eq(false), eq("RAG_GROUNDED"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.TerminalResult(
                        conversation, message, "topic details", "curated-lexical-rag", false,
                        "RAG_GROUNDED", List.of(), false, "COMPLETED"));

        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        ChatResponse response = service.answer("topic", "en", null, "owner-rag", request);

        assertEquals("RAG_GROUNDED", response.reasonCode());
        assertEquals("curated-lexical-rag", response.model());
        assertTrue(!response.degraded());
        verify(turns, never()).dispatch(any(), anyString(), anyLong(), anyInt(), anyInt(), any());
        verifyNoInteractions(provider);
    }

    @Test
    void providerLengthStopFallsBackToGroundedAnswerWithExplicitReason() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        String sourceId = "44444444-4444-4444-4444-444444444444";
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                sourceId, "topic", "en", "Topic", "Grounded answer", "office");
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
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("curated-lexical-rag"),
                eq("Grounded answer"), eq(true), eq("PROVIDER_TRUNCATED"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.TerminalResult(
                        conversation, message, "Grounded answer", "curated-lexical-rag", true,
                        "PROVIDER_TRUNCATED", List.of(), false, "COMPLETED"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<ProviderSegment> sink = invocation.getArgument(1);
            sink.accept(new ProviderSegment(0, "Partial answer", List.of(sourceId)));
            return new CompletionResult("Partial answer", List.of(), "length");
        }).when(provider).complete(any(AssistantCompletionProvider.CompletionRequest.class),
                any(java.util.function.Consumer.class), any(java.util.function.BooleanSupplier.class));

        List<StreamEvent> events = new ArrayList<>();
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        ChatResponse response = service.answer("topic compare multiple conditions", "en", null, "owner-length", request, events::add);

        assertEquals("PROVIDER_TRUNCATED", response.reasonCode());
        assertTrue(response.degraded());
        assertEquals("Grounded answer", response.answer());
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && "Grounded answer".equals(replace.text())
                && "PROVIDER_TRUNCATED".equals(replace.reasonCode())));
    }

    @Test
    void databaseOutageReturnsExplicitDegradedResponseWithoutCitations() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        assertTrue(ThesisAssistantService.hasPublicScopeSignal("Điều kiện đăng ký đề tài là gì?"));

        ChatResponse response = new ThesisAssistantService(knowledge)
                .answer("Điều kiện đăng ký đề tài là gì?", "vi");

        assertTrue(response.degraded());
        assertEquals("KNOWLEDGE_UNAVAILABLE", response.reasonCode());
        assertEquals("curated-lexical-rag", response.model());
        assertEquals("vi", response.locale());
        assertTrue(response.citations().isEmpty());
    }

    @Test
    void technicalQuestionsAreRejectedBeforeKnowledgeLookup() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);

        ChatResponse response = new ThesisAssistantService(knowledge)
                .answer("Cho tôi lệnh curl để gọi API chatbot và lệnh docker compose.", "vi");

        assertEquals("TECHNICAL_REQUEST_BLOCKED", response.reasonCode());
        assertEquals(ThesisAssistantService.technicalOutputMessage("vi"), response.answer());
        assertTrue(response.degraded());
        verifyNoInteractions(knowledge);
    }

    @Test
    void unrelatedQuestionsDoNotRetrieveProviderContextOrCitations() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);

        ChatResponse response = new ThesisAssistantService(knowledge)
                .answer("Thời tiết hôm nay thế nào?", "vi");

        assertEquals("NO_MATCH", response.reasonCode());
        assertTrue(response.citations().isEmpty());
        assertTrue(response.answer().contains("chưa tìm thấy hướng dẫn phù hợp"));
        verifyNoInteractions(knowledge);
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
        String hardQuestion = "topic compare multiple conditions";
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("curated-lexical-rag"),
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
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        ChatResponse response = service.answer(hardQuestion, "en", null, "owner-a", request, events::add);

        assertEquals("PROVIDER_UNSAFE_OUTPUT", response.reasonCode());
        assertEquals("Grounded answer", response.answer());
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && "PROVIDER_UNSAFE_OUTPUT".equals(replace.reasonCode())));
        assertTrue(events.stream().noneMatch(event -> event instanceof StreamDelta delta
                && delta.text().contains("example.edu")));
        verify(turns).complete(eq(turn), eq("owner-a"), eq(1L), anyString(), eq("curated-lexical-rag"),
                eq("Grounded answer"), eq(true), eq("PROVIDER_UNSAFE_OUTPUT"), anyList(),
                any(java.util.function.Consumer.class));
    }

    @Test
    void providerTechnicalOutputIsReplacedBeforeItCanBeCommitted() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        DeepSeekClient provider = mock(DeepSeekClient.class);
        ThesisAssistantRepository history = mock(ThesisAssistantRepository.class);
        ThesisAssistantTurnRepository turns = mock(ThesisAssistantTurnRepository.class);
        ThesisAssistantCatalogRepository catalog = mock(ThesisAssistantCatalogRepository.class);
        var document = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "33333333-3333-3333-3333-333333333333", "topic", "en", "Topic", "Grounded answer", "office");
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
                "THESIS", "CURATED", "33333333-3333-3333-3333-333333333333", null, null, "hash", null, null, null);
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("curated-lexical-rag"),
                eq("Grounded answer"), eq(true), eq("PROVIDER_UNSAFE_OUTPUT"), anyList(),
                any(java.util.function.Consumer.class)))
                .thenReturn(new ThesisAssistantTurnRepository.TerminalResult(
                        conversation, message, "Grounded answer", "curated-lexical-rag", true,
                        "PROVIDER_UNSAFE_OUTPUT", List.of(citation), false, "COMPLETED"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<ProviderSegment> sink = invocation.getArgument(1);
            // The command name is deliberately split across provider segments.
            sink.accept(new ProviderSegment(0, "Here is a command: cur", List.of("33333333-3333-3333-3333-333333333333")));
            sink.accept(new ProviderSegment(1, "l https://campuscore.local/api/v1/assistant", List.of("33333333-3333-3333-3333-333333333333")));
            return new CompletionResult("Here is a command: curl https://campuscore.local/api/v1/assistant", List.of(), "stop");
        }).when(provider).complete(any(AssistantCompletionProvider.CompletionRequest.class),
                any(java.util.function.Consumer.class), any(java.util.function.BooleanSupplier.class));

        List<StreamEvent> events = new ArrayList<>();
        ThesisAssistantService service = new ThesisAssistantService(knowledge, provider, history, turns, catalog,
                new AssistantCancellationRegistry(),
                new DeepSeekProperties(true, "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        ChatResponse response = service.answer("topic compare multiple conditions", "en", null, "owner-b", request, events::add);

        assertTrue(response.degraded());
        assertTrue(response.answer().equals("Grounded answer"));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && "PROVIDER_UNSAFE_OUTPUT".equals(replace.reasonCode())));
        assertTrue(events.stream().noneMatch(event -> event instanceof StreamDelta delta
                && delta.text().contains("curl")));
        assertTrue(events.stream().noneMatch(event -> event instanceof StreamDelta delta
                && delta.text().contains("/api/v1")));
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
        String hardQuestion = "topic compare multiple conditions";
        when(turns.complete(eq(turn), anyString(), eq(1L), anyString(), eq("deepseek-v4-flash"),
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
                new AssistantProperties(6000, 2000, 20, 200, 90, true));

        assertThrows(DomainException.class,
                () -> service.answer(hardQuestion, "en", null, "owner-race", request, events::add));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamDelta));
        assertTrue(events.stream().anyMatch(event -> event instanceof StreamReplace replace
                && replace.text().isEmpty() && "TURN_TERMINAL_RACE".equals(replace.reasonCode())));
    }

    @Test
    void providerNumberGlueIsRepairedWithoutTouchingCodes() {
        // The model drops spaces around Vietnamese function words: "từ03 đến05".
        assertEquals("Mỗi hội đồng bảo vệ gồm từ 03 đến 05 thành viên",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Mỗi hội đồng bảo vệ gồm từ03 đến05 thành viên"));
        assertEquals("Tối đa 28 tín chỉ / học kỳ",
                ThesisAssistantService.normalizeNumberSpacing("Tối đa28 tín chỉ / học kỳ"));
        assertEquals("còn 13 tín chỉ",
                ThesisAssistantService.normalizeNumberSpacing("còn13 tín chỉ"));
        assertEquals("điểm D hoặc 4.0/10",
                ThesisAssistantService.normalizeNumberSpacing("điểm D hoặc4.0/10"));
        assertEquals("điểm D (tương đương 4.0/10)",
                ThesisAssistantService.normalizeNumberSpacing("điểm D (tương đương4.0/10)"));
        assertEquals("đạt 8.5 điểm rèn luyện và 3 môn tích lũy",
                ThesisAssistantService.normalizeNumberSpacing("đạt8.5 điểm rèn luyện và 3môn tích lũy"));
        assertEquals("mức 4 tín chỉ, học 3 tiết",
                ThesisAssistantService.normalizeNumberSpacing("mức4 tín chỉ, học 3tiết"));
        // Course codes and already-spaced text are untouched.
        assertEquals("Lớp SE101 học kỳ 2026 - 2027, phòng A101",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Lớp SE101 học kỳ 2026 - 2027, phòng A101"));
        assertEquals("KLTN 2026-2027",
                ThesisAssistantService.normalizeNumberSpacing("KLTN 2026-2027"));
        assertEquals("minimum of 14 credits, maximum of 24 credits, and up to 28 credits",
                ThesisAssistantService.normalizeNumberSpacing(
                        "minimum of14 credits, maximum of24 credits, and up to28 credits"));
        assertEquals("Credit limits: 14 to 24 credits; time 10:30 remains intact",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Credit limits:14 to 24 credits; time 10:30 remains intact"));
    }

    @Test
    void generalizedGlueRepairCoversWordsOutsideTheOldAllowlist() {
        // Function words that were absent from the closed allowlist ("trong",
        // "thang", "đủ") kept their digits attached in live production answers.
        assertEquals("phải hoàn thành nghĩa vụ học phí trong 4 tuần đầu tiên",
                ThesisAssistantService.normalizeNumberSpacing(
                        "phải hoàn thành nghĩa vụ học phí trong4 tuần đầu tiên"));
        assertEquals("Điểm F (dưới 4.0 thang 10) là không đạt.",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Điểm F (dưới 4.0 thang10) là không đạt."));
        assertEquals("Tích lũy đủ 100% số tín chỉ của chương trình đào tạo.",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Tích lũy đủ100% số tín chỉ của chương trình đào tạo."));
        // Identifiers and digit ranges must not gain a space.
        assertEquals("Môn SE101, phòng A101, lớp K20, mức 1- 2, từ 5 - 7 ngày",
                ThesisAssistantService.normalizeNumberSpacing(
                        "Môn SE101, phòng A101, lớp K20, mức 1- 2, từ 5 - 7 ngày"));

        // A heading glued to the following block is split back into two blocks.
        assertEquals("# Hạn nộp, hình thức nộp và gia hạn học phí\n\n**Hạn nộp học phí**",
                ThesisAssistantService.normalizeAssistantCopy(
                        "# Hạn nộp, hình thức nộp và gia hạn học phí**Hạn nộp học phí**", "vi"));
        assertEquals("## Xử lý điểm F – học lại\n\n- Điểm F (dưới 4.0 thang 10) là không đạt.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "## Xử lý điểm F – học lại- Điểm F (dưới 4.0 thang10) là không đạt.", "vi"));
        assertEquals("**Học phần tiên quyết**\n- Phải học và thi đạt",
                ThesisAssistantService.normalizeAssistantCopy(
                        "**Học phần tiên quyết**  - Phải học và thi đạt", "vi"));
        // A heading concatenated with a bullet via two spaces would otherwise
        // pull the bullet into the heading text.
        assertEquals("## Điều kiện tốt nghiệp\n- Tích lũy đủ 100% số tín chỉ.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "## Điều kiện tốt nghiệp  - Tích lũy đủ 100% số tín chỉ.", "vi"));
        // Heading markers and closing emphasis glued to running text regain their
        // block boundary.
        assertEquals("gia hạn học phí\n\n## Thời hạn nộp học phí",
                ThesisAssistantService.normalizeAssistantCopy(
                        "gia hạn học phí## Thời hạn nộp học phí", "vi"));
        assertEquals("**Hậu quả khi không đóng đúng hạn**\n\nSinh viên sẽ bị khóa đăng ký.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "**Hậu quả khi không đóng đúng hạn**Sinh viên sẽ bị khóa đăng ký.", "vi"));

        // A closing emphasis marker and an inline bold run stay untouched.
        assertEquals("đạt **4.0**/10 mới qua",
                ThesisAssistantService.normalizeAssistantCopy("đạt **4.0**/10 mới qua", "vi"));
        assertEquals("Quy định **quan trọng** cần lưu ý",
                ThesisAssistantService.normalizeAssistantCopy("Quy định **quan trọng** cần lưu ý", "vi"));

        // Counterexamples raised by an adversarial review of the first version of
        // these rules: identifiers, addresses, anchors and closing markers must
        // survive untouched.
        for (String preserved : java.util.List.of(
                "Môn IS101 (Hệ thống thông tin) là học phần bắt buộc",
                "Liên hệ hoten2020@student.hcmute.edu.vn để được hỗ trợ",
                "xem https://portal.example.vn/x?nam2026=1&page2. để biết thêm",
                "xem mục #muc4 trong quy chế",
                "**Điểm**4.0 là mức tối thiểu",
                "Nhóm SV- K20 tham gia báo cáo",
                "sinh viên self- study tại nhà",
                "TP.HCM - Thủ Đức là địa bàn chính",
                "Môn SE101 - Kỹ thuật phần mềm, phòng A101, lớp K20",
                "TOEIC 450 và chứng chỉ MOS/IC3",
                "thời hạn từ 5 - 7 ngày làm việc",
                "ca học 07:00 - 09:30 tại A101",
                "## Học phí - Học bổng là hai nội dung khác nhau",
                "C# là ngôn ngữ lập trình được dùng trong môn học",
                "xem mục #muc4 trong quy chế quy định")) {
            assertEquals(preserved,
                    ThesisAssistantService.normalizeAssistantCopy(preserved, "vi"), preserved);
        }
    }

    @Test
    void userFacingCopyDoesNotExposeRegistrationEnums() {
        assertEquals("Khi đợt bổ sung/rút học phần đang mở, bạn có thể đăng ký lớp còn chỗ.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "Khi ADD_DROP_OPEN, bạn có thể đăng ký lớp còn chỗ.", "vi"));
        assertEquals("During the open add/drop period, you can register for sections with seats.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "During ADD_DROP_OPEN, you can register for sections with seats.", "en"));
        assertEquals("When the main window ends but the add/drop period remains open, check the catalog.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "When the main window ends but the ADD_DROP remains open, check the catalog.", "en"));
        assertEquals("Check whether the registration period window remains open.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "Check whether the REGISTRATION period period period window remains open.", "en"));
        assertEquals("Khi đợt đăng ký chính kết thúc nhưng đợt bổ sung/rút học phần vẫn mở.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "Khi đợt REGISTRATION chính kết thúc nhưng đợt ADD_DROP vẫn mở.", "vi"));
        assertEquals("3. Xử lý các thông báo từ hệ thống\n\nNếu lớp đã đóng, hãy chọn lớp khác.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "3. Xử lý các thông báo từ hệ thống Nếu lớp đã đóng, hãy chọn lớp khác.", "vi"));
        assertEquals("# Đăng ký học phần trên CampusCore\n\nMở Cổng sinh viên, vào mục Đăng ký học phần.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "# Đăng ký học phần trên CampusCoreMở Cổng sinh viên, vào mục Đăng ký học phần.", "vi"));
        assertEquals("## When you see a system message\n\nIf the section is closed, choose another one.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "## When you see a system messageIf the section is closed, choose another one.", "en"));
        assertEquals("## Prerequisites and Corequisites\n\n- Prerequisite: pass the earlier course.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "## Prerequisites and Corequisites- Prerequisite: pass the earlier course.", "en"));
        assertEquals("**Thời gian đăng ký**\n\nCần kiểm tra thời gian trước khi xác nhận.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "**Thời gian đăng ký**Cần kiểm tra thời gian trước khi xác nhận.", "vi"));
        assertEquals("# Cách đăng ký học phần\n\nMở Cổng sinh viên, vào mục Đăng ký học phần.\n\n"
                        + "## Các bước đăng ký\n\n- Chọn đúng học kỳ cần đăng ký.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "# Cách đăng ký học phầnMở Cổng sinh viên, vào mục Đăng ký học phần.\n\n"
                                + "## Các bước đăng ký- Chọn đúng học kỳ cần đăng ký.", "vi"));
        assertEquals("## Khi gặp thông báo từ hệ thống\n\n- Nếu lớp đã đóng, hãy chọn lớp khác.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "## Khi gặp thông báo từ hệ thống- Nếu lớp đã đóng, hãy chọn lớp khác.", "vi"));
        Citation citation = new Citation("doc", "registration-window", "Đợt đăng ký", "registrar",
                "vi", "CampusCore hiển thị lớp thuộc đợt REGISTRATION. Đợt ADD_DROP vẫn mở.");
        Citation normalized = ThesisAssistantService.normalizeCitation(citation, "vi");
        assertEquals("CampusCore hiển thị lớp thuộc đợt đăng ký. Đợt bổ sung/rút học phần vẫn mở.",
                normalized.excerpt());
        assertEquals("# Registering for a Course\n\nOpen the Student Portal.\n\n"
                        + "## During Add/Drop\n\nWhile the main window has ended.\n\n"
                        + "## What Happens During Add/Drop\n\nSections are listed only while a registration period is active.\n\n"
                        + "## Related Rules to Keep in Mind\n\n- Prerequisites may block registration.\n\n"
                        + "## Prerequisites and Retakes\n\n- A prerequisite must be passed before enrollment.\n\n"
                        + "## If Registration Is Blocked\n\nThe system may report a closed section.\n\n"
                        + "Withdrawal is allowed during the first 2 weeks of a regular semester.\n\n"
                        + "## Credit Load Rules\n\n- The maximum is 24 credits.\n\n"
                        + "Credit limits: minimum of 14 credits and up to 28 credits. Summer terms allow 8 to 10 credits.",
                ThesisAssistantService.normalizeAssistantCopy(
                        "# Registering for a CourseOpen the Student Portal.\n\n"
                                + "## During Add/DropWhile the main window has ended.\n\n"
                                + "## What Happens During Add/DropSections are listed only while a registration period is active.\n\n"
                                + "## Related Rules to Keep in Mind- Prerequisites may block registration.\n\n"
                                + "## Prerequisites and Retakes- A prerequisite must be passed before enrollment.\n\n"
                                + "## If Registration Is BlockedThe system may report a closed section.\n\n"
                                + "Withdrawal is allowed during the first2 weeks of a regular semester.\n\n"
                                + "## Credit Load Rules- The maximum is24 credits.\n\n"
                                + "Credit limits: minimum of14 credits and up to28 credits. Summer terms allow8 to 10 credits.", "en"));
    }
}
