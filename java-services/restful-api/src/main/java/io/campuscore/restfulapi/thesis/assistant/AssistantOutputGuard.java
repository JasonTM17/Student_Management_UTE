package io.campuscore.restfulapi.thesis.assistant;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic last-mile guard for text that is about to become assistant
 * output. Provider instructions are useful guidance, but this boundary is the
 * authority that prevents commands and implementation details from reaching a
 * student-facing stream or persisted answer.
 */
public final class AssistantOutputGuard {

    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\u206A-\\u206F\\uFEFF]");

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)(?:```|~~~\\s*(?:\\w+)?(?:\\R|$))");

    private static final Pattern SHELL_COMMAND = Pattern.compile(
            "(?im)(?:^|\\R)\\s*(?:[-•*]\\s*)?(?:[$>#]\\s*)?(?:curl|wget|invoke-webrequest|iwr|docker(?:\\s+compose)?|docker-compose|npm|pnpm|yarn|bun|npx|mvnw?|gradlew?|git|kubectl|helm|psql|mysql|redis-cli|python(?:3)?|node|powershell|pwsh|bash|sh)\\b");

    private static final Pattern INLINE_COMMAND = Pattern.compile(
            "(?i)\\b(?:curl|wget|invoke-webrequest|docker(?:\\s+compose)?|docker-compose|kubectl|psql|mysql|redis-cli)\\s+(?:https?://|[/-]|(?:compose|run|up|down|build|command|commands|example|instructions?)\\b)");

    private static final Pattern SQL_COMMAND = Pattern.compile(
            "(?im)(?:^|\\R)\\s*(?:select|insert|update|delete|drop|alter|create)\\s+(?:from|into|table|database|schema|index|view|users?|assistant|chat|\\*)");

    private static final Pattern INTERNAL_ENDPOINT = Pattern.compile(
            "(?i)(?:https?://[^\\s)]+/api/v\\d(?:/|\\b)|(?<![\\p{L}\\p{N}_])/api/v\\d(?:/|\\b)|\\b(?:localhost|127\\.0\\.0\\.1)\\s*:\\s*\\d{2,5})");

    private static final Pattern INTERNAL_DETAIL = Pattern.compile(
            "(?i)\\b(?:system\\s+prompt|developer\\s+message|retrieved\\s+context|api\\s+endpoint(?:s)?|curl\\s+commands?|docker\\s+compose(?:\\s+instructions?)?|stack\\s+trace|traceback|deepseek(?:[- ]v?\\d+)?|provider\\s+(?:error|response|model)|api\\s+key|jwt\\s+secret|bearer\\s+token|v4\\s+flash)\\b");

    private static final Pattern STACK_TRACE = Pattern.compile(
            "(?i)(?:exception\\s+in\\s+thread|traceback\\s*\\(most\\s+recent\\s+call\\s+last\\)|\\bat\\s+[\\w.$]+\\([^\\n)]*:\\d+[:)]|caused\\s+by:)" );

    private AssistantOutputGuard() {
    }

    /** Returns true when text contains no known command or implementation leak. */
    public static boolean isSafe(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = normalize(value);
        return !CODE_FENCE.matcher(normalized).find()
                && !SHELL_COMMAND.matcher(normalized).find()
                && !INLINE_COMMAND.matcher(normalized).find()
                && !SQL_COMMAND.matcher(normalized).find()
                && !INTERNAL_ENDPOINT.matcher(normalized).find()
                && !INTERNAL_DETAIL.matcher(normalized).find()
                && !STACK_TRACE.matcher(normalized).find();
    }

    static String normalize(String value) {
        return Normalizer.normalize(INVISIBLE.matcher(value).replaceAll(""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
