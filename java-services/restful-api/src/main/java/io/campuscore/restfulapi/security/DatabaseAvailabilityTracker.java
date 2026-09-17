package io.campuscore.restfulapi.security;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory circuit breaker tracking database availability.
 * When a database outage is detected, subsequent degradable requests (e.g. RAG assistant)
 * bypass redundant connection timeouts immediately, and non-degradable requests fail fast.
 */
public final class DatabaseAvailabilityTracker {

    /** The outage window during which new requests treat the DB as still unavailable. */
    private static final long OUTAGE_COOLDOWN_MS = 5000L;
    private static final AtomicLong LAST_FAILURE_MS = new AtomicLong(0L);

    private DatabaseAvailabilityTracker() {
    }

    public static void recordFailure() {
        LAST_FAILURE_MS.set(System.currentTimeMillis());
    }

    public static void recordSuccess() {
        LAST_FAILURE_MS.set(0L);
    }

    public static boolean isRecentlyUnavailable() {
        long lastFailure = LAST_FAILURE_MS.get();
        return lastFailure > 0L && (System.currentTimeMillis() - lastFailure) < OUTAGE_COOLDOWN_MS;
    }
}
