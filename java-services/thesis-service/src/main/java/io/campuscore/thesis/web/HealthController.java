package io.campuscore.thesis.web;

import java.sql.Connection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final String readinessKey;
    private final DataSource dataSource;
    private final StringRedisTemplate redis;

    public HealthController(
            @Value("${HEALTH_READINESS_KEY:}") String readinessKey,
            DataSource dataSource,
            StringRedisTemplate redis) {
        this.readinessKey = readinessKey;
        this.dataSource = dataSource;
        this.redis = redis;
    }

    @GetMapping("/liveness")
    public Map<String, Object> liveness() {
        return Map.of("status", "ok", "service", "thesis-service", "timestamp", Instant.now());
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness(@RequestHeader(value = "X-Health-Key", required = false) String suppliedKey) {
        if (readinessKey.isBlank()
                || suppliedKey == null
                || !MessageDigest.isEqual(
                        readinessKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Readiness key required");
        }
        if (!dependenciesReady()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Readiness dependencies unavailable");
        }
        return Map.of("status", "ready", "service", "thesis-service", "timestamp", Instant.now());
    }

    private boolean dependenciesReady() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }

        try {
            if (redis.getConnectionFactory() == null) {
                return false;
            }
            try (RedisConnection connection = redis.getConnectionFactory().getConnection()) {
                return "PONG".equalsIgnoreCase(connection.ping());
            }
        } catch (Exception ignored) {
            return false;
        }
    }
}
