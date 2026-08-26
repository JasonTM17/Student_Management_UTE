package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "assistant.rag")
public record AssistantRagProperties(
        String baseUrl,
        String serviceToken,
        boolean serviceMode,
        int connectTimeoutMs,
        int readTimeoutMs) {

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
        return serviceToken != null && !serviceToken.isBlank();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
