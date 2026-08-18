package io.campuscore.thesis.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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

    public HealthController(@Value("${HEALTH_READINESS_KEY:}") String readinessKey) {
        this.readinessKey = readinessKey;
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
        return Map.of("status", "ready", "service", "thesis-service", "timestamp", Instant.now());
    }
}
