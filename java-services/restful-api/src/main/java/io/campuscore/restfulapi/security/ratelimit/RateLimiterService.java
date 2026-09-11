package io.campuscore.restfulapi.security.ratelimit;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * High-performance, in-memory Sliding Window Rate Limiter.
 * Tracks timestamps per client key with zero third-party dependencies.
 */
@Service
public class RateLimiterService {

    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public RateLimiterService() {
        this(Clock.systemUTC());
    }

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    /**
     * Checks if a request should be allowed under the given limits.
     *
     * @param key           unique client key (e.g. "ip:1.2.3.4:AUTH_LOGIN" or "user:123:ENROLLMENT")
     * @param maxRequests   maximum number of requests allowed within the window
     * @param windowSeconds duration of the sliding window in seconds
     * @return RateLimitResult indicating allowed/blocked status and HTTP header values
     */
    public RateLimitResult tryAcquire(String key, int maxRequests, int windowSeconds) {
        long now = clock.millis();
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = now - windowMillis;

        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // 1. Evict expired timestamps from the beginning of the deque
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= windowStart) {
                timestamps.pollFirst();
            }

            // 2. Check current request count
            if (timestamps.size() < maxRequests) {
                timestamps.addLast(now);
                long remaining = maxRequests - timestamps.size();
                long oldest = timestamps.peekFirst();
                long resetEpochSeconds = (oldest + windowMillis + 999L) / 1000L;
                return RateLimitResult.allowed(maxRequests, remaining, resetEpochSeconds);
            } else {
                long oldest = timestamps.peekFirst();
                long retryAfterMillis = Math.max(1000L, (oldest + windowMillis) - now);
                long retryAfterSeconds = (retryAfterMillis + 999L) / 1000L;
                long resetEpochSeconds = (oldest + windowMillis + 999L) / 1000L;
                return RateLimitResult.blocked(maxRequests, resetEpochSeconds, retryAfterSeconds);
            }
        }
    }

    /**
     * Periodically removes idle/empty client buckets to avoid memory leaks.
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredBuckets() {
        long now = clock.millis();
        long maxRetention = 300000L; // 5 minutes of inactivity

        buckets.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                Long last = deque.peekLast();
                return last == null || (now - last) > maxRetention;
            }
        });
    }

    /**
     * Resets a specific key (useful for test assertions).
     */
    public void reset(String key) {
        buckets.remove(key);
    }

    /**
     * Clears all buckets (useful for test resets).
     */
    public void clear() {
        buckets.clear();
    }

    public int getActiveKeyCount() {
        return buckets.size();
    }
}
