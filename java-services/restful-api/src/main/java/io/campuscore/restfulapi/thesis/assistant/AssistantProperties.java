package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        int maxContextChars,
        int maxMessageChars,
        int userDailyQuota,
        int globalDailyQuota,
        int retentionDays,
        boolean quotaEnforced,
        Integer topK) {

    /** Retrieval window default, exposed as {@code assistant.top-k}. */
    public static final int DEFAULT_TOP_K = 5;

    @ConstructorBinding
    public AssistantProperties {
        maxContextChars = clamp(maxContextChars, 256, 6_000);
        maxMessageChars = clamp(maxMessageChars, 64, 2_000);
        userDailyQuota = clamp(userDailyQuota, 1, 20);
        globalDailyQuota = clamp(globalDailyQuota, 1, 200);
        retentionDays = clamp(retentionDays, 1, 90);
        // Absent configuration binds null, which keeps the historical default
        // instead of clamping 0 up to the floor.
        topK = clamp(topK == null ? DEFAULT_TOP_K : topK, 3, 10);
    }

    /** Convenience constructor preserving the pre-top-k arity for tests. */
    public AssistantProperties(int maxContextChars, int maxMessageChars, int userDailyQuota,
            int globalDailyQuota, int retentionDays, boolean quotaEnforced) {
        this(maxContextChars, maxMessageChars, userDailyQuota, globalDailyQuota, retentionDays,
                quotaEnforced, DEFAULT_TOP_K);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
