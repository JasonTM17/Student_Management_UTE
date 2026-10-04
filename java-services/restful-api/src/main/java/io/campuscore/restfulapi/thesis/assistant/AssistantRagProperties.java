package io.campuscore.restfulapi.thesis.assistant;

import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagProperties(
        String baseUrl,
        String serviceToken,
        boolean serviceMode,
        int connectTimeoutMs,
        int readTimeoutMs) {

    /** Placeholder values committed to the repo; they must never authenticate in any mode. */
    private static final Set<String> PLACEHOLDER_TOKENS = Set.of(
            "local-rag-service-token-change-me",
            "change-me-rag-service-token");

    /**
     * Token shipped as the docker-compose default for local development. It is
     * acceptable on a private dev network but must be rejected wherever
     * {@code security.jwt.reject-known-defaults=true} is enforced.
     */
    public static final String DEV_COMPOSE_DEFAULT_TOKEN = "dev-rag-service-token-rotate-before-exposing";

    @ConstructorBinding
    public AssistantRagProperties(String baseUrl, String serviceToken, boolean serviceMode,
            int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.serviceToken = serviceToken == null ? "" : serviceToken.trim();
        this.serviceMode = serviceMode;
        this.connectTimeoutMs = clamp(connectTimeoutMs, 250, 10_000);
        this.readTimeoutMs = clamp(readTimeoutMs, 1_000, 120_000);
    }

    public boolean enabled() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    public boolean tokenConfigured() {
        return serviceToken != null && !serviceToken.isBlank() && !PLACEHOLDER_TOKENS.contains(serviceToken);
    }

    public boolean tokenIsDevComposeDefault() {
        return DEV_COMPOSE_DEFAULT_TOKEN.equalsIgnoreCase(serviceToken);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
