package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Regression for keeping provider-backed SSE orchestration off the request thread. */
class ThesisAssistantSseControllerTest {

    @Test
    void streamReturnsBeforeTheBoundedWorkerCompletes() throws Exception {
        ThesisAssistantService service = mock(ThesisAssistantService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        TaskExecutor executor = command -> {
            Thread thread = new Thread(command, "test-assistant-stream");
            worker.set(thread);
            thread.start();
        };
        doAnswer(invocation -> {
            started.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
            finished.countDown();
            return new ChatResponse("ok", "fixture", false, "ANSWERED", "vi", List.of());
        }).when(service).stream(anyString(), anyString(), any(), anyString(), any(UUID.class), any(Consumer.class));

        UUID clientRequestId = UUID.randomUUID();
        Jwt actor = Jwt.withTokenValue("fixture-token").header("alg", "none").subject("owner-a").build();
        SseEmitter emitter = new ThesisAssistantController(service, executor)
                .stream(new ChatRequest("How?", "vi", clientRequestId, null), actor, null);

        assertTrue(emitter != null);
        assertTrue(started.await(1, TimeUnit.SECONDS), "stream work should be scheduled asynchronously");
        assertTrue(finished.getCount() == 1, "request must not wait for provider work");
        release.countDown();
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        verify(service).stream(anyString(), anyString(), any(), anyString(), any(UUID.class), any(Consumer.class));
        Thread running = worker.get();
        if (running != null) running.join(2_000L);
    }
}
