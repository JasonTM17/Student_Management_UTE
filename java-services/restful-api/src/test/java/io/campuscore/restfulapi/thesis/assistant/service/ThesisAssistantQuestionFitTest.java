package io.campuscore.restfulapi.thesis.assistant.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.campuscore.restfulapi.thesis.assistant.dto.StreamCitation;
import io.campuscore.restfulapi.thesis.assistant.dto.StreamDelta;
import io.campuscore.restfulapi.thesis.assistant.dto.StreamEvent;
import io.campuscore.restfulapi.thesis.assistant.dto.StreamReplace;
import io.campuscore.restfulapi.thesis.assistant.model.CompletionRequest;
import io.campuscore.restfulapi.thesis.assistant.model.CompletionResult;
import io.campuscore.restfulapi.thesis.assistant.model.DispatchDecision;
import io.campuscore.restfulapi.thesis.assistant.model.KnowledgeDocument;
import io.campuscore.restfulapi.thesis.assistant.model.ProviderSegment;
import io.campuscore.restfulapi.thesis.assistant.model.Reservation;
import io.campuscore.restfulapi.thesis.assistant.model.ReservationStatus;
import io.campuscore.restfulapi.thesis.assistant.model.TerminalResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import io.campuscore.restfulapi.thesis.assistant.client.AssistantCompletionProvider;
import io.campuscore.restfulapi.thesis.assistant.client.DeepSeekClient;
import io.campuscore.restfulapi.thesis.assistant.exception.ProviderCancelledException;
import io.campuscore.restfulapi.thesis.assistant.exception.ProviderUnavailableException;
import io.campuscore.restfulapi.thesis.assistant.properties.AssistantProperties;
import io.campuscore.restfulapi.thesis.assistant.properties.DeepSeekProperties;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantKnowledgeRepository;
import io.campuscore.restfulapi.thesis.assistant.repository.ThesisAssistantTurnRepository;

class ThesisAssistantQuestionFitTest {
    private static final String QUESTION = "Giải thích ngắn gọn Dependency Injection trong Spring Boot và cho một ví dụ constructor injection.";

    private static ThesisAssistantService service(String content, String title) {
        var repository = mock(ThesisAssistantKnowledgeRepository.class);
        when(repository.search(anyString(), anyList(), anyInt(), eq("specialized"))).thenReturn(List.of(
                new KnowledgeDocument("11111111-1111-1111-1111-111111111111",
                        "focus-test", "vi", title, content, "curated", "SPECIALIZED",
                        null, null, null, null, null, null, null, null, 50)));
        return new ThesisAssistantService(repository, null, null, null, null, null, null,
                new AssistantProperties(6000, 2000, 20, 200, 90, true));
    }

    @Test
    void longDependencyQuestionMustNotReturnThePublishedBroadArchitectureArticle() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V93__specialized_domain_knowledge_markdown.sql"));
        int start = sql.indexOf("SET content = '## Kiến trúc");
        assertTrue(start > 0, "use the actual published architecture text");
        int end = sql.indexOf("',", start);
        String article = sql.substring(start + "SET content = '".length(), end);
        assertNull(service(article, "Monolith vs microservices architecture and Spring Boot")
                .lexicalFastPath(QUESTION, "vi", "specialized"));
    }

    @Test
    void anArticleMentioningDiWithoutTheRequestedConstructorExampleEscalates() {
        assertNull(service("## Spring Boot\nDependency Injection uses the IoC container. Spring Boot uses MVC.", "Dependency Injection in Spring Boot")
                .lexicalFastPath(QUESTION, "vi", "specialized"));
    }

    @Test
    void anUnrelatedCodeFenceDoesNotGroundAConstructorExampleRequest() {
        String article = "## Dependency Injection / constructor injection\nSpring Boot supports Dependency Injection and constructor injection.\n```java\nclass Empty { public Empty() {} }\n```";
        assertNull(service(article, "Dependency Injection in Spring Boot").lexicalFastPath(QUESTION, "vi", "specialized"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "class Example { public void Example(Object input) {} }",
            "class Example { /* public Example(Object input) {} */ }",
            "class Example { String example = \"class Fake { public Fake(Object input) {} }\"; }"
    })
    void methodsCommentsAndStringLiteralsCannotMasqueradeAsInjectingConstructors(String code) {
        String article = "## Dependency Injection / constructor injection\nSpring Boot supports Dependency Injection and constructor injection.\n```java\n" + code + "\n```";
        assertNull(service(article, "Dependency Injection in Spring Boot").lexicalFastPath(QUESTION, "vi", "specialized"));
    }

    @Test
    void degradedFallbackDoesNotCiteAnUnrelatedArchitectureArticle() {
        var response = service("## Spring Boot architecture\nSpring Boot uses MVC and repositories.", "Spring Boot architecture")
                .groundedFallback(QUESTION, "vi", false, "specialized");
        assertEquals("NO_MATCH", response.reasonCode());
        assertTrue(response.citations().isEmpty());
    }

    @Test
    void fullTurnWithoutProviderCannotBypassQuestionFitAndCiteArchitecture() throws Exception {
        assertFullTurn("vi", "disabled", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "quota", "truncated", "unavailable", "invalid", "unsafe"})
    void everyFullTurnFallbackKeepsAnswerAndProvenanceConsistent(String branch) throws Exception {
        for (String locale : List.of("vi", "en")) {
            assertFullTurn(locale, branch, false);
            assertFullTurn(locale, branch, true);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"vi", "en"})
    void questionFitDoesNotDiscardApprovedContextForSuccessfulSynthesis(String locale) throws Exception {
        assertFullTurn(locale, "answered", false);
    }

    private static void assertFullTurn(String locale, String branch, boolean matching) throws Exception {
        String sourceId = "11111111-1111-1111-1111-111111111111";
        String content = matching ? injectionSeed(locale)
                : "## Spring Boot architecture\nSpring Boot uses MVC and repositories.";
        String question = "vi".equals(locale) ? QUESTION
                : "Explain Dependency Injection in Spring Boot with a constructor injection example.";
        var knowledge = mock(ThesisAssistantKnowledgeRepository.class);
        when(knowledge.search(anyString(), anyList(), anyInt(), eq("specialized"))).thenReturn(List.of(
                new KnowledgeDocument(sourceId,
                        "focus-test", locale, matching ? "Dependency Injection / constructor injection in Spring Boot" : "Spring Boot architecture", content, "curated", "SPECIALIZED",
                        null, null, null, null, null, null, null, null, 50)));
        var turns = mock(ThesisAssistantTurnRepository.class);
        UUID turn = UUID.randomUUID(), conversation = UUID.randomUUID(), message = UUID.randomUUID();
        when(turns.reserve(anyString(), any(), anyString(), isNull(), anyString(), anyString(), anyInt()))
                .thenReturn(new Reservation(
                        ReservationStatus.NEW, turn, conversation, 1L, true, null, null, false));
        when(turns.markSnapshotReady(any(), anyString(), anyLong(), anyString())).thenReturn(true);
        when(turns.dispatch(any(), anyString(), anyLong(), anyInt(), anyInt()))
                .thenReturn(new DispatchDecision(!"quota".equals(branch),
                        !"quota".equals(branch), "quota".equals(branch) ? "QUOTA_EXCEEDED" : "DISPATCHED"));
        when(turns.complete(any(), anyString(), anyLong(), anyString(), anyString(), anyString(), anyBoolean(), anyString(), anyList()))
                .thenAnswer(invocation -> new TerminalResult(conversation, message,
                        invocation.getArgument(5), invocation.getArgument(4), invocation.getArgument(6),
                        invocation.getArgument(7), invocation.getArgument(8), false, "COMPLETED"));
        var provider = mock(DeepSeekClient.class);
        doAnswer(invocation -> {
            var request = invocation.getArgument(0, CompletionRequest.class);
            assertTrue(request.context().contains(content), "provider context remains the approved retrieval");
            assertEquals(List.of(sourceId), request.sourceIds());
            if ("unavailable".equals(branch)) throw new ProviderUnavailableException("fixture");
            @SuppressWarnings("unchecked")
            var sink = (java.util.function.Consumer<ProviderSegment>) invocation.getArgument(1);
            String text = "unsafe".equals(branch) ? "Contact student@example.com" : "Synthesized answer";
            sink.accept(new ProviderSegment(0, text,
                    List.of("invalid".equals(branch) ? "unapproved-source" : sourceId)));
            return new CompletionResult(text, List.of(),
                    "truncated".equals(branch) ? "length" : "stop");
        }).when(provider).complete(any(CompletionRequest.class),
                any(java.util.function.Consumer.class), any(java.util.function.BooleanSupplier.class));
        var assistant = new ThesisAssistantService(knowledge, provider, null, turns, null, null,
                new DeepSeekProperties(!"disabled".equals(branch), "fixture", "https://api.deepseek.com", "deepseek-v4-flash", 8000, 800),
                new AssistantProperties(6000, 2000, 20, 200, 90, true));
        var events = new java.util.ArrayList<StreamEvent>();
        var response = assistant.stream(question, locale, null, "owner-focus", UUID.randomUUID(), events::add, "specialized");
        String reason = switch (branch) {
            case "disabled" -> "PROVIDER_DISABLED";
            case "quota" -> "QUOTA_EXCEEDED";
            case "truncated" -> "PROVIDER_TRUNCATED";
            case "unsafe" -> "PROVIDER_UNSAFE_OUTPUT";
            case "answered" -> "ANSWERED";
            default -> "PROVIDER_UNAVAILABLE";
        };
        assertEquals(reason, response.reasonCode());
        assertEquals(!"answered".equals(branch), response.degraded());
        assertEquals(locale, response.locale());
        assertEquals("quota".equals(branch), response.resetAt() != null);
        verify(turns).complete(eq(turn), eq("owner-focus"), eq(1L), eq(question), anyString(),
                eq(response.answer()), eq(response.degraded()), eq(reason), eq(response.citations()));
        var visible = events.stream().filter(event -> event instanceof StreamDelta
                        || event instanceof StreamReplace).toList();
        assertFalse(visible.isEmpty());
        var last = visible.getLast();
        String finalText = last instanceof StreamReplace replace ? replace.text()
                : ((StreamDelta) last).text();
        List<String> finalSources = last instanceof StreamReplace replace ? replace.sourceIds()
                : ((StreamDelta) last).sourceIds();
        assertEquals(response.answer(), finalText);
        if ("answered".equals(branch)) {
            assertEquals("Synthesized answer", response.answer());
            assertEquals(List.of(sourceId), finalSources);
            assertEquals(1, response.citations().size());
            return;
        }
        assertFalse(response.answer().contains("Spring Boot architecture"));
        if (matching) {
            assertTrue(response.answer().contains("public OrderService(PaymentGateway gateway)"));
            assertEquals(List.of(sourceId), finalSources);
            assertEquals(1, response.citations().size());
        } else {
            assertTrue(response.answer().startsWith("vi".equals(locale)
                    ? "Mình chưa tìm thấy nội dung khớp câu hỏi này"
                    : "I could not find content matching this question"));
            assertTrue(finalSources.isEmpty());
            assertTrue(response.citations().isEmpty());
            assertTrue(events.stream().noneMatch(event -> event instanceof StreamCitation));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            QUESTION,
            "Explain Dependency Injection in Spring Boot with a constructor injection example.",
            "Constructor injection là gì? Cho ví dụ ngắn.",
            "Giai thich tiem phu thuoc va tiem qua ham khoi tao, cho vi du.",
            "Please explain this clearly and briefly for a beginner who is preparing a presentation on dependency injection and provide a constructor injection example."
    })
    void curatedInjectionArticleAnswersVariantsWithTheExampleAndCitation(String question) throws Exception {
        String content = injectionSeed("vi");
        var response = service(content, "Dependency Injection / constructor injection in Spring Boot")
                .lexicalFastPath(question, "vi", "specialized");
        assertNotNull(response);
        assertTrue(response.answer().contains("public OrderService(PaymentGateway gateway)"));
        assertTrue(response.answer().contains("private final PaymentGateway gateway"));
        assertEquals(1, response.citations().size());
    }

    @Test
    void longPreambleCannotCrowdNamedInjectionConceptsOutOfTheRetrievalWindow() {
        var terms = ThesisAssistantService.retrievalTerms("Please explain this clearly and briefly for a beginner who is preparing a presentation on dependency injection and provide a constructor injection example.");
        assertEquals(List.of("dependency injection", "constructor injection"), terms.subList(0, 2));
        assertTrue(terms.size() <= 16);
    }

    @Test
    void sqlInjectionDoesNotActivateSpringDependencyInjectionRules() {
        assertTrue(SpringInjectionQuestionFocus.retrievalPhrases("Explain SQL injection with an example").isEmpty());
        assertTrue(SpringInjectionQuestionFocus.coveredBy("Explain SQL injection with an example", "Use parameterized SQL"));
    }

    @Test
    void codeExampleInBothLocalesActuallyCompiles() throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "a JDK is required for the published Java example");
        for (String locale : List.of("vi", "en")) {
            String content = injectionSeed(locale);
            int start = content.indexOf("```java\n") + 8;
            String code = content.substring(start, content.indexOf("\n```", start));
            var directory = Files.createTempDirectory("campuscore-di-example-");
            var file = directory.resolve("Example.java");
            Files.writeString(file, code);
            try {
                assertEquals(0, compiler.run(null, null, null, "-classpath", System.getProperty("java.class.path"),
                        "-d", directory.toString(), file.toString()));
            } finally {
                try (var paths = Files.walk(directory)) {
                    for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            }
        }
    }

    private static String injectionSeed(String locale) throws Exception {
        var rows = new ObjectMapper().readTree(Files.readString(Path.of("../../supabase/seed/assistant-specialized-knowledge.json")));
        for (var row : rows) {
            if (row.path("slug").asText().equals("specialized-spring-dependency-constructor-injection-" + locale))
                return row.path("content").asText();
        }
        throw new AssertionError("Missing localized injection article");
    }
}
