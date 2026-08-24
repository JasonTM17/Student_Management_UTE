package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Deterministic proof that a terminal fence serializes against stream emission. */
class AssistantCancellationRegistryTest {

    @Test
    void fenceCannotRaceAnAlreadyEnteredEmissionAndBlocksLaterFrames() throws Exception {
        AssistantCancellationRegistry registry = new AssistantCancellationRegistry();
        UUID request = UUID.randomUUID();
        long generation = 7L;
        registry.register("owner", request, generation);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch fenceStarted = new CountDownLatch(1);
        CountDownLatch fenceDone = new CountDownLatch(1);
        AtomicBoolean emitted = new AtomicBoolean();
        Thread stream = new Thread(() -> assertTrue(registry.emitIfActive("owner", request, generation, () -> {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            emitted.set(true);
        })));
        stream.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        Thread fence = new Thread(() -> {
            fenceStarted.countDown();
            registry.fence("owner", request, generation);
            fenceDone.countDown();
        });
        fence.start();
        assertTrue(fenceStarted.await(5, TimeUnit.SECONDS));
        assertFalse(fenceDone.await(100, TimeUnit.MILLISECONDS), "fence must wait for an in-flight emission");
        release.countDown();
        stream.join(5_000L);
        fence.join(5_000L);

        assertTrue(emitted.get());
        assertFalse(registry.emitIfActive("owner", request, generation, () -> {
            throw new AssertionError("a fenced generation emitted a later frame");
        }));
    }
}
