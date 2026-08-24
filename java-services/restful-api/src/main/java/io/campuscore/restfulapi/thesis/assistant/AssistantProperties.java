package io.campuscore.restfulapi.thesis.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        int maxContextChars,
        int maxMessageChars,
        int userDailyQuota,
        int globalDailyQuota,
        int retentionDays) {

    public AssistantProperties {
        maxContextChars = clamp(maxContextChars, 256, 6_000);
        maxMessageChars = clamp(maxMessageChars, 64, 2_000);
        userDailyQuota = clamp(userDailyQuota, 1, 20);
        globalDailyQuota = clamp(globalDailyQuota, 1, 200);
        retentionDays = clamp(retentionDays, 1, 90);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
