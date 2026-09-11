package io.campuscore.restfulapi.security.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterServiceTest {

    static class TestClock extends Clock {
        private final AtomicLong millis;

        TestClock(long initialMillis) {
            this.millis = new AtomicLong(initialMillis);
        }

        void advanceSeconds(long seconds) {
            millis.addAndGet(seconds * 1000L);
        }

        void advanceMillis(long delta) {
            millis.addAndGet(delta);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Test
    void allowsRequestsUpToLimitAndTracksRemaining() {
        TestClock clock = new TestClock(1000000000L);
        RateLimiterService limiter = new RateLimiterService(clock);

        String key = "ip:192.168.1.1:AUTH_LOGIN";
        int limit = 5;
        int windowSeconds = 60;

        for (int i = 1; i <= limit; i++) {
            RateLimitResult result = limiter.tryAcquire(key, limit, windowSeconds);
            assertTrue(result.allowed(), "Request " + i + " should be allowed");
            assertEquals(limit - i, result.remaining(), "Remaining count should decrease");
            assertEquals(limit, result.limit());
            assertEquals(0, result.retryAfterSeconds());
        }

        // 6th request must be blocked
        RateLimitResult blocked = limiter.tryAcquire(key, limit, windowSeconds);
        assertFalse(blocked.allowed(), "6th request exceeding limit should be blocked");
        assertEquals(0, blocked.remaining());
        assertTrue(blocked.retryAfterSeconds() > 0, "Retry-After should be positive");
    }

    @Test
    void unblocksAfterSlidingWindowPasses() {
        TestClock clock = new TestClock(1000000000L);
        RateLimiterService limiter = new RateLimiterService(clock);

        String key = "user:student-1:ENROLL";
        int limit = 2;
        int windowSeconds = 10;

        assertTrue(limiter.tryAcquire(key, limit, windowSeconds).allowed());
        assertTrue(limiter.tryAcquire(key, limit, windowSeconds).allowed());
        assertFalse(limiter.tryAcquire(key, limit, windowSeconds).allowed());

        // Advance clock by 11 seconds
        clock.advanceSeconds(11);

        // Should be allowed again
        RateLimitResult refreshed = limiter.tryAcquire(key, limit, windowSeconds);
        assertTrue(refreshed.allowed(), "Should be allowed after window expires");
        assertEquals(1, refreshed.remaining());
    }

    @Test
    void isolatesDifferentKeys() {
        TestClock clock = new TestClock(1000000000L);
        RateLimiterService limiter = new RateLimiterService(clock);

        String key1 = "ip:10.0.0.1:AUTH_LOGIN";
        String key2 = "ip:10.0.0.2:AUTH_LOGIN";
        int limit = 1;
        int windowSeconds = 60;

        assertTrue(limiter.tryAcquire(key1, limit, windowSeconds).allowed());
        assertFalse(limiter.tryAcquire(key1, limit, windowSeconds).allowed());

        // Key 2 has its own independent bucket
        assertTrue(limiter.tryAcquire(key2, limit, windowSeconds).allowed());
        assertFalse(limiter.tryAcquire(key2, limit, windowSeconds).allowed());
    }

    @Test
    void resetClearsBucketForTesting() {
        RateLimiterService limiter = new RateLimiterService();
        String key = "test:reset";

        assertTrue(limiter.tryAcquire(key, 1, 60).allowed());
        assertFalse(limiter.tryAcquire(key, 1, 60).allowed());

        limiter.reset(key);
        assertTrue(limiter.tryAcquire(key, 1, 60).allowed());
    }
}
