package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import java.util.Set;

@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(
        boolean enabled,
        String apiKey,
        String baseUrl,
        String model,
        int timeoutMs,
        int maxOutputTokens,
        int maxResponseBytes,
        int maxFrameBytes,
        int maxConcurrent) {

    @ConstructorBinding
    public DeepSeekProperties(boolean enabled, String apiKey, String baseUrl, String model,
            int timeoutMs, int maxOutputTokens, int maxResponseBytes, int maxFrameBytes, int maxConcurrent) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
        this.model = model == null || model.isBlank() ? "deepseek-v4-flash" : model.trim();
        this.timeoutMs = clamp(timeoutMs <= 0 ? 15_000 : timeoutMs, 1_000, 60_000);
        this.maxOutputTokens = clamp(maxOutputTokens <= 0 ? 800 : maxOutputTokens, 1, 2_000);
        this.maxResponseBytes = clamp(maxResponseBytes <= 0 ? 512_000 : maxResponseBytes, 16_384, 2_000_000);
        this.maxFrameBytes = clamp(maxFrameBytes <= 0 ? 64_000 : maxFrameBytes, 1_024, 256_000);
        this.maxConcurrent = clamp(maxConcurrent <= 0 ? 4 : maxConcurrent, 1, 32);
    }

    public DeepSeekProperties(boolean enabled, String apiKey, String baseUrl, String model,
            int timeoutMs, int maxOutputTokens) {
        this(enabled, apiKey, baseUrl, model, timeoutMs, maxOutputTokens, 512_000, 64_000, 4);
    }

    public boolean usable() {
        return enabled && apiKey != null && !apiKey.isBlank() && modelAllowed();
    }

    /** Provider models are server-owned; clients/configuration cannot route to an arbitrary model. */
    public boolean modelAllowed() {
        // The public CampusCore contract is pinned to the non-thinking Flash
        // model for bounded cost/latency. Keep this allowlist exact so an env
        // typo or stale deployment cannot silently route paid traffic to a
        // different model.
        return Set.of("deepseek-v4-flash").contains(model);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
