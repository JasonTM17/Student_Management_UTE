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
        Integer topK,
        Boolean lexicalFastPath,
        Integer lexicalConfidentScore) {

    /** Retrieval window default, exposed as {@code assistant.top-k}. */
    public static final int DEFAULT_TOP_K = 5;

    /**
     * Documents injected into the provider prompt (and surfaced as citations).
     * Retrieval still fetches the wider top-k window for scoped filters; only
     * the prompt material is trimmed to this many top ranked sources.
     */
    public static final int PROMPT_DOCUMENT_LIMIT = 3;

    /**
     * Default retrieval score a top document must reach before the lexical
     * fast path answers without the remote RAG round-trip. The score is the
     * repository's own ranking expression summed over retrieval terms
     * (title substring 3, content substring 1, title whole-word 4, content
     * whole-word 2). A single term can contribute at most 10 — so eleven
     * means at least TWO distinct query terms independently corroborate the
     * top document. That corroboration gate is what rejects incidental
     * vocabulary collisions like "công thức nấu phở bò" matching the
     * "Công nghệ thông tin" document on the bare syllable "công" (10 = old
     * threshold), while seeded campus topics ("đăng ký học phần", "học phí",
     * "nghỉ học") still clear it by a wide margin through multiple terms.
     */
    public static final int DEFAULT_LEXICAL_CONFIDENT_SCORE = 11;

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
        // The lexical fast path is ON by default (env ASSISTANT_LEXICAL_FAST_PATH);
        // an explicit false is the only way to switch it off.
        lexicalFastPath = lexicalFastPath == null || lexicalFastPath;
        lexicalConfidentScore = lexicalConfidentScore == null
                ? DEFAULT_LEXICAL_CONFIDENT_SCORE
                : Math.max(0, Math.min(100, lexicalConfidentScore));
    }

    /** Convenience constructor preserving the pre-top-k arity for tests. */
    public AssistantProperties(int maxContextChars, int maxMessageChars, int userDailyQuota,
            int globalDailyQuota, int retentionDays, boolean quotaEnforced) {
        this(maxContextChars, maxMessageChars, userDailyQuota, globalDailyQuota, retentionDays,
                quotaEnforced, DEFAULT_TOP_K);
    }

    /** Arity bridge for the top-k era; the fast-path switches keep their defaults. */
    public AssistantProperties(int maxContextChars, int maxMessageChars, int userDailyQuota,
            int globalDailyQuota, int retentionDays, boolean quotaEnforced, Integer topK) {
        this(maxContextChars, maxMessageChars, userDailyQuota, globalDailyQuota, retentionDays,
                quotaEnforced, topK, null, null);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
