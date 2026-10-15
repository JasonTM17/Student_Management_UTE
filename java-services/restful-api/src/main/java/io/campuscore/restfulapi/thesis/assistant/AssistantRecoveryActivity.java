package io.campuscore.restfulapi.thesis.assistant;

import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("persistence")
public class AssistantRecoveryActivity {
    private static final long MIN_ACTIVE_WINDOW_MS = 90_000L;
    private static final long MAX_ACTIVE_WINDOW_MS = 900_000L;
    private static final long MAX_SAFETY_INTERVAL_MS = 3_600_000L;

    private final boolean enabled;
    private final long activeWindowNanos;
    private final long safetyIntervalNanos;
    private final LongSupplier nanoTime;

    private boolean swept;
    private boolean hasActivity;
    private boolean forcedDue;
    private long lastActivityNanos;
    private long lastSweepNanos;

    @Autowired
    public AssistantRecoveryActivity(
            @Value("${assistant.recovery.activity-aware:true}") boolean enabled,
            @Value("${assistant.recovery.active-window-ms:300000}") long activeWindowMs,
            @Value("${assistant.recovery.safety-interval-ms:900000}") long safetyIntervalMs) {
        this(enabled, activeWindowMs, safetyIntervalMs, System::nanoTime);
    }

    AssistantRecoveryActivity(boolean enabled, long activeWindowMs, long safetyIntervalMs,
            LongSupplier nanoTime) {
        if (activeWindowMs < MIN_ACTIVE_WINDOW_MS || activeWindowMs > MAX_ACTIVE_WINDOW_MS) {
            throw new IllegalArgumentException(
                    "assistant.recovery.active-window-ms must be between 90000 and 900000");
        }
        if (safetyIntervalMs < activeWindowMs || safetyIntervalMs > MAX_SAFETY_INTERVAL_MS) {
            throw new IllegalArgumentException(
                    "assistant.recovery.safety-interval-ms must be >= active-window-ms and <= 3600000");
        }
        this.enabled = enabled;
        this.activeWindowNanos = activeWindowMs * 1_000_000L;
        this.safetyIntervalNanos = safetyIntervalMs * 1_000_000L;
        this.nanoTime = nanoTime;
    }

    public synchronized void markActivity() {
        lastActivityNanos = nanoTime.getAsLong();
        hasActivity = true;
    }

    public synchronized boolean shouldSweep() {
        if (!enabled) {
            return true;
        }
        long now = nanoTime.getAsLong();
        if (!swept || forcedDue) {
            return true;
        }
        if (hasActivity && now - lastActivityNanos < activeWindowNanos) {
            return true;
        }
        return now - lastSweepNanos >= safetyIntervalNanos;
    }

    public synchronized void sweepSucceeded(boolean backlog) {
        lastSweepNanos = nanoTime.getAsLong();
        swept = true;
        forcedDue = backlog;
    }

    public synchronized void sweepFailed() {
        forcedDue = true;
    }
}
