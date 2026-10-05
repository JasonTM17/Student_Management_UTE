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

    /**
     * Scope-aware split of {@link #INTERNAL_ENDPOINT}, mirroring the client
     * copy guard. A host-bearing URL or explicit host:port is live
     * infrastructure — banned in every scope. A bare {@code /api/vN} path is
     * ambiguous: in academic answers it still signals an internal leak, but
     * the specialized corpus legitimately teaches REST endpoint shapes in its
     * lessons, so the remote-RAG stream must not blank them there.
     * Sensitive segments stay banned in every scope so a specialized answer
     * cannot walk a learner into admin/internal routes.
     */
    private static final Pattern INTERNAL_ENDPOINT_HOST = Pattern.compile(
            "(?i)(?:https?://[^\\s)]+/api/v\\d(?:/|\\b)|\\b(?:localhost|127\\.0\\.0\\.1)\\s*:\\s*\\d{2,5})");

    private static final Pattern INTERNAL_API_PATH = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])/api/v\\d(?:/|\\b)");

    private static final Pattern INTERNAL_API_PATH_SENSITIVE = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])/api/v\\d/(?:(?:admin|assistant|internal|system|actuator|manage|debug|users?|auth|mail|thesis)[\\w-]*|me)(?:/|\\b)");

    private static final Pattern INTERNAL_DETAIL = Pattern.compile(
            "(?i)\\b(?:system\\s+prompt|developer\\s+message|retrieved\\s+context|api\\s+endpoint(?:s)?|curl\\s+commands?|docker\\s+compose(?:\\s+instructions?)?|stack\\s+trace|traceback|deepseek(?:[- ]v?\\d+)?|provider\\s+(?:error|response|model)|api\\s+key|jwt\\s+secret|bearer\\s+token|v4\\s+flash)\\b");

    /**
     * Split of {@link #INTERNAL_DETAIL} mirroring the client guard. The SECRET
     * half names the machinery — banned in every scope. The TECH half is
     * phrasing the specialized corpus legitimately TEACHES (Docker Compose,
     * pipelines), so a scope-aware boundary skips it there.
     */
    private static final Pattern INTERNAL_DETAIL_SECRET = Pattern.compile(
            "(?i)\\b(?:system\\s+prompt|developer\\s+message|stack\\s+trace|traceback|deepseek(?:[- ]v?\\d+)?|provider\\s+(?:error|response|model)|api\\s+key|jwt\\s+secret|bearer\\s+token|v4\\s+flash)\\b");

    private static final Pattern INTERNAL_DETAIL_TECH = Pattern.compile(
            "(?i)\\b(?:retrieved\\s+context|api\\s+endpoints?|curl\\s+commands?|docker\\s+compose(?:\\s+instructions?)?)\\b");

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

    /**
     * Scope-aware boundary for assistant output with no approved context —
     * the remote-RAG sink has no corpus excerpt to quote against, so it uses
     * scope instead. {@code specialized} keeps secrets, stack traces and
     * host-bound/sensitive endpoints blocked but allows the command-style and
     * bare-endpoint examples its corpus teaches; every other scope is the
     * strict {@link #isSafe(String)} boundary.
     *
     * <p>Note on authority: {@code scope} is self-asserted by the caller (the
     * request body carries it), not a privilege boundary — the specialized
     * surface is a student-facing feature. What stays banned under it is the
     * machinery tier (secrets, provider/model markers, stack traces, host-bound
     * URLs, sensitive route segments); what relaxes is phrasing the curated
     * corpus publishes. A remote service that bypasses its own
     * {@link #isSafeForAnswer} check is still held by those hard blocks here.
     */
    public static boolean isSafeForScope(String value, String scope) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (!"specialized".equalsIgnoreCase(scope)) {
            return isSafe(value);
        }
        String normalized = normalize(value);
        return !INTERNAL_ENDPOINT_HOST.matcher(normalized).find()
                && !INTERNAL_API_PATH_SENSITIVE.matcher(normalized).find()
                && !INTERNAL_DETAIL_SECRET.matcher(normalized).find()
                && !STACK_TRACE.matcher(normalized).find();
    }

    /**
     * Guard for provider-generated text where some triggering strings are
     * expected. The assistant answers from an approved, published corpus, so a
     * command or endpoint that is quoted from the retrieved context is a
     * citation, not a hallucination; only a match with no counterpart in
     * {@code approvedContext} is rejected.
     *
     * <p>This is what lets the SPECIALIZED domain answer its own DevOps, REST
     * and architecture topics while {@link #isSafe(String)} remains the boundary
     * everywhere else — including corpus admission, where there is no context to
     * quote from and no model output to second-guess.
     */
    public static boolean isSafeForAnswer(String value, String approvedContext) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (approvedContext == null || approvedContext.isBlank()) {
            return isSafe(value);
        }
        String normalizedValue = normalize(value);
        String approved = normalize(approvedContext);
        return !hasUnapprovedMatch(normalizedValue, approved, CODE_FENCE)
                && !hasUnapprovedMatch(normalizedValue, approved, SHELL_COMMAND)
                && !hasUnapprovedMatch(normalizedValue, approved, INLINE_COMMAND)
                && !hasUnapprovedMatch(normalizedValue, approved, SQL_COMMAND)
                && !hasUnapprovedMatch(normalizedValue, approved, INTERNAL_ENDPOINT)
                && !hasUnapprovedMatch(normalizedValue, approved, INTERNAL_DETAIL)
                && !hasUnapprovedMatch(normalizedValue, approved, STACK_TRACE);
    }

    /** True when the value matches a pattern with text absent from the approved context. */
    private static boolean hasUnapprovedMatch(String value, String approvedContext, Pattern pattern) {
        var matcher = pattern.matcher(value);
        while (matcher.find()) {
            String found = matcher.group().trim();
            if (!found.isEmpty() && !approvedContext.contains(found)) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String value) {
        return Normalizer.normalize(INVISIBLE.matcher(value).replaceAll(""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
