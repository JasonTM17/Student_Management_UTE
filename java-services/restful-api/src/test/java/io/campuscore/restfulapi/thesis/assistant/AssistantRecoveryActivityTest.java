package io.campuscore.restfulapi.thesis.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class AssistantRecoveryActivityTest {

    private final AtomicLong nanos = new AtomicLong(-5_000_000_000L);

    private AssistantRecoveryActivity activity(boolean enabled) {
        return new AssistantRecoveryActivity(enabled, 300_000L, 900_000L, nanos::get);
    }

    private void advanceMillis(long millis) {
        nanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    @Test
    void startupTickIsDueThenSuccessfulSweepIdles() {
        AssistantRecoveryActivity activity = activity(true);
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(false);
        assertFalse(activity.shouldSweep());
    }

    @Test
    void idleTicksAreSkippedUntilTheSafetyInterval() {
        AssistantRecoveryActivity activity = activity(true);
        activity.sweepSucceeded(false);
        advanceMillis(30_000);
        assertFalse(activity.shouldSweep());
        advanceMillis(270_000);
        assertFalse(activity.shouldSweep());
        advanceMillis(599_000);
        assertFalse(activity.shouldSweep());
        advanceMillis(1_000);
        assertTrue(activity.shouldSweep());
    }

    @Test
    void recentActivityKeepsTheGateDue() {
        AssistantRecoveryActivity activity = activity(true);
        activity.sweepSucceeded(false);
        assertFalse(activity.shouldSweep());
        activity.markActivity();
        assertTrue(activity.shouldSweep());
    }

    @Test
    void activityStaysDueUntilStrictlyFiveMinutesIdle() {
        AssistantRecoveryActivity activity = activity(true);
        activity.markActivity();
        activity.sweepSucceeded(false);
        advanceMillis(299_999);
        assertTrue(activity.shouldSweep());
        advanceMillis(1);
        assertFalse(activity.shouldSweep());
    }

    @Test
    void activityMarkedDuringOrAfterASweepIsNotCleared() {
        AssistantRecoveryActivity activity = activity(true);
        activity.markActivity();
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(false);
        assertTrue(activity.shouldSweep());
        advanceMillis(400_000);
        assertFalse(activity.shouldSweep());
        activity.markActivity();
        assertTrue(activity.shouldSweep());
    }

    @Test
    void failedSweepRetriesNextTickEvenPastTheActivityCutoff() {
        AssistantRecoveryActivity activity = activity(true);
        activity.markActivity();
        advanceMillis(290_000);
        assertTrue(activity.shouldSweep());
        activity.sweepFailed();
        advanceMillis(20_000);
        assertTrue(activity.shouldSweep());
        advanceMillis(600_000);
        assertTrue(activity.shouldSweep());
    }

    @Test
    void aFullBacklogPageForcesTheNextTickUntilItDrains() {
        AssistantRecoveryActivity activity = activity(true);
        activity.sweepSucceeded(true);
        advanceMillis(30_000);
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(true);
        advanceMillis(30_000);
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(false);
        advanceMillis(30_000);
        assertFalse(activity.shouldSweep());
    }

    @Test
    void disablingTheGateRestoresEveryTick() {
        AssistantRecoveryActivity activity = activity(false);
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(false);
        assertTrue(activity.shouldSweep());
        advanceMillis(3_600_000);
        assertTrue(activity.shouldSweep());
    }

    @Test
    void invalidWindowsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new AssistantRecoveryActivity(true, 89_999L, 900_000L, nanos::get));
        assertThrows(IllegalArgumentException.class,
                () -> new AssistantRecoveryActivity(true, 900_001L, 900_000L, nanos::get));
        assertThrows(IllegalArgumentException.class,
                () -> new AssistantRecoveryActivity(true, 300_000L, 299_999L, nanos::get));
        assertThrows(IllegalArgumentException.class,
                () -> new AssistantRecoveryActivity(true, 300_000L, 3_600_001L, nanos::get));
        assertDoesNotThrow(() -> new AssistantRecoveryActivity(true, 90_000L, 90_000L, nanos::get));
        assertDoesNotThrow(() -> new AssistantRecoveryActivity(true, 300_000L, 3_600_000L, nanos::get));
    }

    @Test
    void nanosecondWraparoundStillComparesCorrectly() {
        AtomicLong wrap = new AtomicLong(Long.MAX_VALUE - TimeUnit.MINUTES.toNanos(1));
        AssistantRecoveryActivity activity =
                new AssistantRecoveryActivity(true, 300_000L, 900_000L, wrap::get);
        activity.markActivity();
        activity.sweepSucceeded(false);
        // 2 minutes after the wrap: 3 min elapsed since the pre-wrap mark, so
        // due via the active window — the startup tick was already consumed.
        wrap.set(Long.MIN_VALUE + TimeUnit.MINUTES.toNanos(2));
        assertTrue(activity.shouldSweep());
        activity.sweepSucceeded(false);
        // Still due right after the sweep: only 3 min have elapsed since the
        // mark, which is inside the 5-minute activity window.
        assertTrue(activity.shouldSweep());
        // 6 minutes after the wrap: 7 min since the mark, past the window, and
        // 4 min since the last successful sweep, still under the safety cap.
        wrap.set(Long.MIN_VALUE + TimeUnit.MINUTES.toNanos(6));
        assertFalse(activity.shouldSweep());
        // 17 minutes after the wrap: 15 min since the post-wrap sweep.
        wrap.set(Long.MIN_VALUE + TimeUnit.MINUTES.toNanos(17));
        assertTrue(activity.shouldSweep());
    }

    @Test
    void concurrentMarksCannotLoseTheLatestTimestamp() throws Exception {
        // Each worker advances the clock before marking, so every mark sees a
        // distinct, strictly increasing sample; the lock must keep the newest.
        AssistantRecoveryActivity activity = activity(true);
        int threads = 16;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                nanos.incrementAndGet();
                activity.markActivity();
            });
            workers[i].start();
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        for (Thread worker : workers) {
            worker.join(10_000);
            assertFalse(worker.isAlive());
        }
        long latestMark = nanos.get();
        activity.sweepSucceeded(false);
        nanos.set(latestMark + TimeUnit.MILLISECONDS.toNanos(300_000) - 1);
        assertTrue(activity.shouldSweep());
        nanos.set(latestMark + TimeUnit.MILLISECONDS.toNanos(300_000));
        assertFalse(activity.shouldSweep());
    }
}
