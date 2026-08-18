package io.campuscore.thesis.assistant;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssistantRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final StringRedisTemplate redis;

    public AssistantRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean allow(UUID userId, int limit) {
        if (userId == null || limit < 1) {
            return false;
        }

        try {
            String key = "campuscore:assistant:rate:" + userId;
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, WINDOW);
            }
            return count != null && count <= limit;
        } catch (RuntimeException ignored) {
            // Fail closed when the distributed limiter is unavailable.
            return false;
        }
    }
}
