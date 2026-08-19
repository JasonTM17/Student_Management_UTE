package io.campuscore.restfulapi.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
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

    public HealthController(@Value("${health.readiness-key:}") String readinessKey) {
        this.readinessKey = readinessKey;
    }

    @GetMapping("/liveness")
    public Map<String, Object> liveness() {
        return Map.of(
                "status", "ok",
                "service", "restful-api",
                "timestamp", Instant.now());
    }

    @GetMapping("/readiness")
    public Map<String, Object> readiness(
            @RequestHeader(value = "X-Health-Key", required = false) String suppliedKey) {
        if (readinessKey.isBlank()
                || suppliedKey == null
                || !MessageDigest.isEqual(
                        readinessKey.getBytes(StandardCharsets.UTF_8),
                        suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Readiness key required");
        }

        return Map.of(
                "status", "ready",
                "service", "restful-api",
                "dependencies", List.of("application-shell"),
                "timestamp", Instant.now());
    }
}
