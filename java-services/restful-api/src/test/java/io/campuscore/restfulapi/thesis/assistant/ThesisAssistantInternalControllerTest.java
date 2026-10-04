package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.web.DomainException;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class ThesisAssistantInternalControllerTest {

    @Test
    void internalOwnerScopeIsClearedBetweenTasksIncludingFailedGeneration() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        List<Runnable> tasks = new ArrayList<>();
        Executor controlledExecutor = tasks::add;
        var controller = new ThesisAssistantInternalController(service,
                new AssistantRagProperties("", "internal-token", true, 1_000, 30_000), controlledExecutor);
        List<AssistantRlsContext.Identity> identities = new ArrayList<>();
        Mockito.doAnswer(invocation -> {
            identities.add(AssistantRlsContext.current());
            if (identities.size() == 2) throw new DomainException(
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "ASSISTANT_UNAVAILABLE", "Unavailable");
            return null;
        }).when(service).stream(anyString(), anyString(), any(), anyString(), any(), any(), any());
        for (String owner : List.of("owner-a", "owner-b")) {
            controller.stream(new ChatRequest("Quy chế học vụ", "vi"), "internal-token", owner);
            Runnable task = tasks.remove(0);
            task.run();
            assertEquals(null, AssistantRlsContext.current());
            assertTrue(!((FutureTask<?>) task).isCancelled());
        }
        assertEquals("owner-a", identities.get(0).ownerId());
        assertEquals("owner-b", identities.get(1).ownerId());
        assertEquals(AssistantRlsContext.Scope.INTERNAL_OWNER, identities.get(0).scope());
        assertEquals(AssistantRlsContext.Scope.INTERNAL_OWNER, identities.get(1).scope());
        assertTrue(!identities.get(0).admin() && !identities.get(1).admin());
    }

    @Test
    void invalidStreamTokenOrMissingOwnerNeverSubmitsAWorker() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        List<Runnable> tasks = new ArrayList<>();
        var controller = new ThesisAssistantInternalController(service,
                new AssistantRagProperties("", "internal-token", true, 1_000, 30_000), tasks::add);
        ChatRequest request = new ChatRequest("Quy chế học vụ", "vi");
        assertEquals("RAG_SERVICE_UNAUTHORIZED", assertThrows(DomainException.class,
                () -> controller.stream(request, "wrong-token", "owner-a")).code());
        assertEquals("RAG_SERVICE_UNAUTHORIZED", assertThrows(DomainException.class,
                () -> controller.stream(request, null, "owner-a")).code());
        assertEquals("UNAUTHENTICATED", assertThrows(DomainException.class,
                () -> controller.stream(request, "internal-token", null)).code());
        assertTrue(tasks.isEmpty());
        verifyNoInteractions(service);
    }

    @Test
    void httpStreamFlushesBeforeGenerationCompletesInTheVerifiedInternalOwnerScope() throws Exception {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        AssistantStreamExecutor streams = new AssistantStreamExecutor("assistant-stream-test-", 1);
        var controller = new ThesisAssistantInternalController(service,
                new AssistantRagProperties("", "internal-token", true, 1_000, 30_000), streams);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        CountDownLatch firstDeltaSent = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        AtomicReference<AssistantRlsContext.Identity> identity = new AtomicReference<>();
        Mockito.doAnswer(invocation -> {
            Consumer<ThesisAssistantService.StreamEvent> sink = invocation.getArgument(5);
            identity.set(AssistantRlsContext.current());
            sink.accept(new ThesisAssistantService.StreamMeta(UUID.randomUUID(), invocation.getArgument(4),
                    null, null, "test-provider", "vi"));
            sink.accept(new ThesisAssistantService.StreamDelta(0, "first grounded answer", List.of()));
            firstDeltaSent.countDown();
            assertTrue(releaseGeneration.await(5, TimeUnit.SECONDS));
            sink.accept(new ThesisAssistantService.StreamDone(null, "ANSWERED", false, "COMPLETED"));
            return null;
        }).when(service).stream(anyString(), anyString(), any(), anyString(), any(), any(), any());
        var requests = Executors.newSingleThreadExecutor();
        try {
            var response = requests.submit(() -> mvc.perform(post("/internal/rag/assistant/chat/stream")
                    .header("X-Rag-Service-Token", "internal-token")
                    .header("X-Assistant-Owner", "owner-a")
                    .contentType("application/json")
                    .content("{\"message\":\"Quy chế học vụ\",\"locale\":\"vi\",\"clientRequestId\":\""
                            + UUID.randomUUID() + "\"}"))
                    .andReturn());
            assertTrue(firstDeltaSent.await(2, TimeUnit.SECONDS));
            var result = response.get(500, TimeUnit.MILLISECONDS);
            assertTrue(result.getRequest().isAsyncStarted());
            assertTrue(result.getResponse().getContentAsString().contains("first grounded answer"));
            assertEquals("owner-a", identity.get().ownerId());
            assertEquals(AssistantRlsContext.Scope.INTERNAL_OWNER, identity.get().scope());
            releaseGeneration.countDown();
            result.getAsyncResult(2_000);
            assertTrue(mvc.perform(asyncDispatch(result)).andReturn().getResponse()
                    .getContentAsString().contains("event:done"));
        } finally {
            releaseGeneration.countDown();
            requests.shutdownNow();
            assertTrue(requests.awaitTermination(2, TimeUnit.SECONDS));
            streams.close();
        }
    }

    @Test
    void rejectsMissingTokenBeforeCallingAssistantService() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.chat(new ChatRequest("Xin chào", "vi", UUID.randomUUID(), null), null, "owner-a"));

        assertEquals("RAG_SERVICE_UNAUTHORIZED", exception.code());
        verifyNoInteractions(service);
}
    @Test
    void rejectsMissingOwnerBeforeCallingAssistantService() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);

        DomainException exception = assertThrows(DomainException.class,
                () -> controller.chat(new ChatRequest("Xin chào", "vi", UUID.randomUUID(), null), "internal-token", null));

        assertEquals("UNAUTHENTICATED", exception.code());
        verifyNoInteractions(service);
    }

    @Test
    void delegatesChatWithVerifiedInternalTokenAndOwner() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);
        UUID requestId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("Xin chào", "vi", requestId, null);
        ChatResponse expected = new ChatResponse("ok", "curated-lexical-rag", false, "ANSWERED", "vi", List.of());
        when(service.answer("Xin chào", "vi", null, "owner-a", requestId, (String) null)).thenReturn(expected);

        ChatResponse response = controller.chat(request, "internal-token", "owner-a");

        assertEquals(expected, response);
        verify(service).answer("Xin chào", "vi", null, "owner-a", requestId, (String) null);
    }

    @Test
    void forwardsTheSpecializedScopeToTheAssistantService() {
        ThesisAssistantService service = Mockito.mock(ThesisAssistantService.class);
        ThesisAssistantInternalController controller = controller(service);
        UUID requestId = UUID.randomUUID();
        ChatRequest request = new ChatRequest("SOLID là gì?", "vi", requestId, null, "specialized");
        ChatResponse expected = new ChatResponse("ok", "curated-lexical-rag", false, "ANSWERED", "vi", List.of());
        when(service.answer("SOLID là gì?", "vi", null, "owner-a", requestId, "specialized"))
                .thenReturn(expected);

        ChatResponse response = controller.chat(request, "internal-token", "owner-a");

        assertEquals(expected, response);
        verify(service).answer("SOLID là gì?", "vi", null, "owner-a", requestId, "specialized");
    }

    private ThesisAssistantInternalController controller(ThesisAssistantService service) {
        return new ThesisAssistantInternalController(service,
                new AssistantRagProperties("", "internal-token", true, 1_000, 30_000));
    }
}
