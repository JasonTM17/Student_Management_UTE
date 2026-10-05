package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamDelta;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamDone;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamEvent;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantService.StreamMeta;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Lexical-first fast path (chatbot latency): a confident curated-KB match must
 * answer immediately without contacting the remote RAG gateway, while weak,
 * scoped-out and guarded questions escalate along the unchanged RAG-first chain.
 */
class ThesisAssistantLexicalFastPathTest {

    private static final AssistantProperties DEFAULTS =
            new AssistantProperties(6000, 2000, 20, 200, 90, true);

    private static ThesisAssistantKnowledgeRepository.KnowledgeDocument document(
            String slug, String title, String content, String domain, int lexicalScore) {
        return new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                "11111111-1111-1111-1111-111111111111", slug, "vi", title, content, "curated",
                domain, null, null, null, null, null, null, null, null, lexicalScore);
    }

    private static ThesisAssistantService service(ThesisAssistantKnowledgeRepository knowledge,
            AssistantProperties properties) {
        return new ThesisAssistantService(knowledge, null, null, null, null, null, null, properties);
    }

    @Test
    void confidentLexicalMatchAnswersImmediatelyFromTheCuratedCorpus() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("registration-howto", "Đăng ký học phần trên CampusCore",
                        "Mở Cổng sinh viên, chọn học kỳ, tìm học phần và bấm Đăng ký ở lớp còn chỗ.",
                        "REGISTRATION", 24)));

        ChatResponse fast = service(knowledge, DEFAULTS)
                .lexicalFastPath("Đăng ký học phần thế nào?", "vi", null);

        assertEquals("ANSWERED", fast.reasonCode());
        assertEquals(ThesisAssistantService.FAST_PATH_MODEL, fast.model());
        assertFalse(fast.degraded());
        assertEquals(1, fast.citations().size());
        assertEquals("11111111-1111-1111-1111-111111111111", fast.citations().get(0).sourceId());
        assertTrue(fast.answer().contains("Cổng sinh viên"));
    }

    @Test
    void weakLexicalScoreEscalatesInsteadOfAnswering() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("incidental", "Điều kiện bảo vệ khóa luận",
                        "Sinh viên phải tích lũy tối thiểu 75% số tín chỉ.", "POLICY", 3)));

        assertNull(service(knowledge, DEFAULTS).lexicalFastPath("Đăng ký học phần thế nào?", "vi", null));
    }

    @Test
    void singleTermCeilingScoreEscalatesInsteadOfAnswering() {
        // Live bug: "công thức nấu phở bò" matched the "Công nghệ thông tin"
        // document solely through the bare syllable "công" — one term can
        // contribute at most 10 (3+1+4+2), which used to equal the gate.
        // Confident fast-path matches must corroborate with a second term.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("it-services", "Dịch vụ Công nghệ thông tin, Email sinh viên",
                        "Tài khoản email, mạng Wi-Fi công cộng và hỗ trợ công nghệ.", "POLICY", 10)));

        assertNull(service(knowledge, DEFAULTS).lexicalFastPath("Công thức nấu phở bò?", "vi", null));
    }

    @Test
    void aggregateScoreAboveThresholdStillEscalatesWithASingleShortStrongTerm() {
        // The live "công thức nấu phở bò" shape: one bare syllable ("lịch")
        // whole-word-matches the holiday doc while the question's real topic
        // ("thi nấu phở") hits nothing. Aggregate 13 clears the confident
        // score — only the corroboration gate catches the compound fragment.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("holiday-schedule", "Lịch nghỉ lễ chính thức",
                        "Lịch nghỉ lễ chính thức trong năm học.", "POLICY", 13)));

        assertNull(service(knowledge, DEFAULTS).lexicalFastPath("Lịch thi nấu phở?", "vi", null));
    }

    @Test
    void twoGenuineWholeWordHitsStillAnswerOnTheFastPath() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("exam-schedule", "Lịch thi cuối kỳ và các kỳ thi",
                        "Sinh viên xem lịch thi trên cổng thông tin.", "POLICY", 30)));

        ChatResponse fast = service(knowledge, DEFAULTS)
                .lexicalFastPath("Lịch thi khi nào?", "vi", null);

        assertEquals("ANSWERED", fast.reasonCode());
        assertEquals(ThesisAssistantService.FAST_PATH_MODEL, fast.model());
    }

    @Test
    void singleSpecificTermCorroboratesAlone() {
        // "dormitory" is a 9-char whole-word title hit — intrinsically
        // specific, unlike the "công"-inside-"Công nghệ" fragment. One such
        // term is corroboration enough for the fast path.
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("dormitory", "Dormitory and on-campus housing",
                        "Dormitory registration opens per semester on the portal.", "POLICY", 13)));

        ChatResponse fast = service(knowledge, DEFAULTS)
                .lexicalFastPath("dormitory registration", "en", null);

        assertEquals("ANSWERED", fast.reasonCode());
    }

    @Test
    void emptyLexicalWindowEscalates() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of());

        assertNull(service(knowledge, DEFAULTS).lexicalFastPath("Học phí được tính thế nào?", "vi", null));
    }

    @Test
    void offTopicQuestionNeverOpensTheKnowledgeRepository() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("whatever", "Không liên quan", "nội dung", "POLICY", 50)));

        assertNull(service(knowledge, DEFAULTS)
                .lexicalFastPath("Gợi ý mình xem phim gì tối nay?", "vi", null));
        // The public-scope pre-gate rejects the question before any database read.
        verify(knowledge, never()).search(anyString(), anyList(), anyInt());
        verify(knowledge, never()).search(anyString(), anyList(), anyInt(), anyString());
    }

    @Test
    void newStudentPolicyVocabularyReachesTheAcademicCorpus() {
        List<String> topics = List.of("Kỷ luật", "Chứng thực", "Chứng nhận", "Thẻ sinh viên",
                "Thẻ thư viện", "Trung tâm thông báo", "Hoàn học phí", "Hoàn phí", "Học kỳ phụ",
                "Chuyển đổi tín chỉ", "Chuyển ngành", "Xếp loại tốt nghiệp", "Thời gian đào tạo");
        for (String topic : topics) {
            ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
            when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                    document("policy-topic", topic, "Hướng dẫn " + topic + ": liên hệ đơn vị phụ trách.",
                            "POLICY", 50)));
            ChatResponse response = service(knowledge, DEFAULTS).lexicalFastPath(topic + "?", "vi", null);
            assertTrue(response != null && "ANSWERED".equals(response.reasonCode()), topic);
            verify(knowledge, org.mockito.Mockito.atLeastOnce()).search(anyString(), anyList(), anyInt());
        }
    }

    @Test
    void registrationHowToCannotGrabAPolicyDocumentOnTheFastPath() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("tuition-policy", "Chính sách học phí",
                        "Học phí nộp theo học kỳ, không quy định về đăng ký lớp.", "POLICY", 30)));

        // The registration scope filter empties the window: a how-to question
        // must escalate to remote RAG rather than cite an unrelated policy doc.
        assertNull(service(knowledge, DEFAULTS).lexicalFastPath("Đăng ký học phần ở đâu?", "vi", null));
    }

    @Test
    void fastPathHonorsTheSpecializedScope() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt(), eq("specialized"))).thenReturn(List.of(
                document("devops-runbook", "CI/CD pipeline cho sinh viên",
                        "Quy trình CI/CD dựng môi trường chạy lại được.", "SPECIALIZED", 30)));

        ChatResponse fast = service(knowledge, DEFAULTS)
                .lexicalFastPath("CI/CD pipeline là gì?", "vi", "specialized");

        assertEquals("ANSWERED", fast.reasonCode());
        assertTrue(fast.answer().contains("CI/CD"));
        // Both the requested-locale and the alternate-locale window are read
        // inside the specialized scope; neither may leak into the academic corpus.
        verify(knowledge, org.mockito.Mockito.atLeastOnce())
                .search(anyString(), anyList(), anyInt(), eq("specialized"));
        verify(knowledge, never()).search(anyString(), anyList(), anyInt());
    }

    @Test
    void flagOffKeepsTheFastPathSilent() {
        ThesisAssistantKnowledgeRepository knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt())).thenReturn(List.of(
                document("registration-howto", "Đăng ký học phần trên CampusCore",
                        "Mở Cổng sinh viên và bấm Đăng ký.", "REGISTRATION", 24)));
        AssistantProperties disabled = new AssistantProperties(6000, 2000, 20, 200, 90, true,
                null, false, 10);

        assertNull(service(knowledge, disabled).lexicalFastPath("Đăng ký học phần thế nào?", "vi", null));
    }

    @Test
    void fastPathPropertiesBindWithSensibleDefaultsAndClamps() {
        AssistantProperties absent = new AssistantProperties(6000, 2000, 20, 200, 90, true, null);
        assertEquals(true, absent.lexicalFastPath());
        assertEquals(AssistantProperties.DEFAULT_LEXICAL_CONFIDENT_SCORE, absent.lexicalConfidentScore());

        AssistantProperties explicit = new AssistantProperties(6000, 2000, 20, 200, 90, true,
                5, false, 42);
        assertEquals(false, explicit.lexicalFastPath());
        assertEquals(42, explicit.lexicalConfidentScore());

        AssistantProperties clamped = new AssistantProperties(6000, 2000, 20, 200, 90, true,
                5, true, 500);
        assertEquals(100, clamped.lexicalConfidentScore());
    }

    // ------------------------------------------------------------------
    // Controller wiring: both the JSON and the SSE remote-RAG routes.
    // ------------------------------------------------------------------

    @Test
    void jsonFastPathAnswersWithoutCallingTheRagGateway() {
        ThesisAssistantService assistant = mock(ThesisAssistantService.class);
        RagAssistantGateway ragGateway = mock(RagAssistantGateway.class);
        ThesisAssistantController controller = new ThesisAssistantController(assistant, ragGateway);
        when(ragGateway.enabled()).thenReturn(true);
        Citation citation = new Citation("kb-9", "registration-howto", "Đăng ký học phần",
                "Cẩm nang", "vi", "Các bước đăng ký học phần");
        when(assistant.lexicalFastPath(anyString(), anyString(), any())).thenReturn(new ChatResponse(
                "Các bước đăng ký học phần...", ThesisAssistantService.FAST_PATH_MODEL, false,
                "ANSWERED", "vi", List.of(citation)));

        ChatResponse response = controller.chat(request("Đăng ký học phần thế nào?"), actor(), new MockHttpServletRequest());

        assertEquals("ANSWERED", response.reasonCode());
        assertEquals(ThesisAssistantService.FAST_PATH_MODEL, response.model());
        assertFalse(response.degraded());
        assertEquals("kb-9", response.citations().get(0).sourceId());
        verify(ragGateway, never()).chat(any(), anyString());
        verify(assistant, never()).groundedFallback(anyString(), anyString());
    }

    @Test
    void jsonLexicalMissEscalatesToTheRemoteGateway() {
        ThesisAssistantService assistant = mock(ThesisAssistantService.class);
        RagAssistantGateway ragGateway = mock(RagAssistantGateway.class);
        ThesisAssistantController controller = new ThesisAssistantController(assistant, ragGateway);
        when(ragGateway.enabled()).thenReturn(true);
        when(assistant.lexicalFastPath(anyString(), anyString(), any())).thenReturn(null);
        when(ragGateway.chat(any(), anyString())).thenReturn(new ChatResponse(
                "Câu trả lời từ RAG.", "deepseek-v4-flash", false, "ANSWERED", "vi", List.of()));

        ChatResponse response = controller.chat(request("Quy trình xin nghỉ học thế nào?"), actor(), new MockHttpServletRequest());

        assertEquals("ANSWERED", response.reasonCode());
        assertEquals("deepseek-v4-flash", response.model());
        verify(ragGateway).chat(any(), anyString());
    }

    @Test
    void jsonFastPathProbeFailureStillEscalates() {
        ThesisAssistantService assistant = mock(ThesisAssistantService.class);
        RagAssistantGateway ragGateway = mock(RagAssistantGateway.class);
        ThesisAssistantController controller = new ThesisAssistantController(assistant, ragGateway);
        when(ragGateway.enabled()).thenReturn(true);
        when(assistant.lexicalFastPath(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("probe failed"));
        when(ragGateway.chat(any(), anyString())).thenReturn(new ChatResponse(
                "Câu trả lời từ RAG.", "deepseek-v4-flash", false, "ANSWERED", "vi", List.of()));

        ChatResponse response = controller.chat(request("Học phí tính thế nào?"), actor(), new MockHttpServletRequest());

        assertEquals("ANSWERED", response.reasonCode());
        verify(ragGateway).chat(any(), anyString());
    }

    @Test
    void sseFastPathEmitsACompleteNonDegradedStreamWithoutTheGateway() {
        ThesisAssistantService assistant = mock(ThesisAssistantService.class);
        RagAssistantGateway ragGateway = mock(RagAssistantGateway.class);
        ThesisAssistantController controller = new ThesisAssistantController(assistant, ragGateway);
        when(ragGateway.enabled()).thenReturn(true);
        Citation citation = new Citation("kb-10", "registration-howto", "Đăng ký học phần",
                "Cẩm nang", "vi", "Các bước đăng ký học phần");
        when(assistant.lexicalFastPath(anyString(), anyString(), any())).thenReturn(new ChatResponse(
                "Các bước đăng ký học phần...", ThesisAssistantService.FAST_PATH_MODEL, false,
                "ANSWERED", "vi", List.of(citation)));

        ChatRequest request = request("Đăng ký học phần thế nào?");
        ChatResponse fastPath = assistant.lexicalFastPath(request.message(), request.locale(), request.scope());
        List<StreamEvent> events = new ArrayList<>();
        controller.emitLexicalFastPath(fastPath, request, "vi", events::add);

        assertEquals(4, events.size());
        assertTrue(events.get(0) instanceof StreamMeta meta
                && ThesisAssistantService.FAST_PATH_MODEL.equals(meta.model()));
        assertTrue(events.get(1) instanceof StreamDelta delta
                && delta.text().contains("Các bước đăng ký học phần"));
        assertTrue(events.get(2) instanceof ThesisAssistantService.StreamCitation);
        assertTrue(events.get(3) instanceof StreamDone done
                && !done.degraded() && "ANSWERED".equals(done.reasonCode()));
        verify(ragGateway, never()).stream(any(), anyString(), any());
    }

    @Test
    void sseFastPathProbeFailureStillEscalatesToTheGatewayStream() {
        ThesisAssistantService assistant = mock(ThesisAssistantService.class);
        RagAssistantGateway ragGateway = mock(RagAssistantGateway.class);
        ThesisAssistantController controller = new ThesisAssistantController(assistant, ragGateway);
        when(ragGateway.enabled()).thenReturn(true);
        when(assistant.lexicalFastPath(anyString(), anyString(), any()))
                .thenThrow(new IllegalStateException("probe failed"));
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<ThesisAssistantService.StreamEvent> sink = invocation.getArgument(2);
            sink.accept(new ThesisAssistantService.StreamDelta(0, "Trả lời từ RAG.", List.of()));
            sink.accept(new ThesisAssistantService.StreamDone(null, "ANSWERED", false, "COMPLETED"));
            return null;
        }).when(ragGateway).stream(any(), anyString(), any());

        List<StreamEvent> events = new ArrayList<>();
        controller.streamRemoteWithFallback(request("Quy trình xin nghỉ học thế nào?"), "owner-miss", events::add, new MockHttpServletRequest());

        assertTrue(events.stream().anyMatch(event -> event instanceof ThesisAssistantService.StreamDelta delta
                && delta.text().contains("Trả lời từ RAG.")));
        verify(ragGateway).stream(any(), anyString(), any());
    }

    private static ChatRequest request(String message) {
        return new ChatRequest(message, "vi", UUID.randomUUID(), null);
    }

    private static Jwt actor() {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(600),
                Map.of("alg", "HS256"), Map.of("sub", "owner-fast", "roles", List.of("STUDENT")));
    }
}
