package io.campuscore.restfulapi.thesis.assistant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.UUID;
import java.util.regex.Pattern;

/** Deterministic privacy and idempotency normalization at the provider boundary. */
public final class AssistantInputGuard {
    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[\\w.+-]+@[\\w-]+\\.[\\w.-]+\\b");
    private static final Pattern PHONE = Pattern.compile("(?<![A-Za-z0-9])(?:\\+?\\d[\\d .()-]{7,}\\d)(?![A-Za-z0-9])");
    private static final Pattern UUID_TOKEN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}(?![A-Za-z0-9])");
    private static final Pattern STUDENT_ID = Pattern.compile("(?i)\\b(?:student\\s*id|mssv|ma\\s*sv|sinh\\s*vien)\\s*[:#-]?\\s*[a-z0-9-]*\\d[a-z0-9-]{3,20}\\b");
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(?:bearer\\s+|sk-[a-z0-9_-]{12,}|api[_ -]?key\\s*[:：=＝]|token\\s*[:：=＝]|pass(?:word|wd)\\s*[:：=＝])");
    private static final Pattern TECHNICAL_REQUEST = Pattern.compile(
            "(?i)(?:"
                    + "\\b(?:curl|wget|invoke-webrequest|iwr|docker(?:\\s+compose)?|docker-compose|kubectl|helm|psql|mysql|redis-cli|npm|pnpm|yarn|bun|npx|mvnw?|gradlew?|git\\s+(?:push|pull|clone|commit|rebase|merge|remote|repo|command)|powershell|pwsh|bash)\\b"
                    + "|\\b(?:api\\s+(?:endpoint|endpoints|chatbot)|api\\s+key|system\\s+prompt|developer\\s+message|stack\\s+trace|traceback|deepseek(?:[- ]v?\\d+)?|provider|llm|jwt|database\\s+(?:password|credentials?))\\b"
                    + "|\\b(?:cho\\s+(?:tôi|ta)|xin|give\\s+me|show|provide|send)\\b.{0,80}\\b(?:api|endpoint|system\\s+prompt|developer\\s+message|câu\\s+lệnh|lệnh|command)\\b"
                    + "|\\b(?:bạn|bot|trợ\\s+lý|hệ\\s+thống|you|assistant)\\b.{0,40}\\b(?:đang\\s+(?:sử\\s+dụng|dùng|chạy)\\s+)?(?:mô\\s+hình|model|llm|provider|deepseek)\\b"
                    + ")");
    // Vietnamese phrasing matters because the assistant audience is bilingual:
    // the English-only list let "bỏ qua tất cả hướng dẫn..." reach the provider.
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?i)(?:ignore\\s+(?:all\\s+)?previous\\s+instructions|disregard\\s+(?:the\\s+)?system\\s+prompt|reveal\\s+(?:the\\s+)?system\\s+prompt|(?:print|show|give\\s+me|what\\s+is)\\s+(?:the\\s+)?(?:system\\s+prompt|api[\\s_-]?key|jwt[\\s_-]?secret|database\\s+password|admin\\s+password)|developer\\s+message|jailbreak|prompt\\s+injection|do\\s+anything\\s+now|dan\\s+mode"
                    + "|(?:bỏ\\s*qua|quên\\s*(?:đi|hết)?|không\\s+tuân\\s+theo|bỏ\\s*mặc)\\s+(?:tất\\s*cả\\s+|mọi\\s+|các\\s+|những\\s+)?(?:hướng\\s+dẫn|chỉ\\s+dẫn|lệnh|quy\\s+định|prompt)"
                    + "|(?:bạn\\s+là\\s+(?:một\\s+)?)?(?:ai|trợ\\s+lý)\\s+không\\s+giới\\s+hạn|(?:hạ|sửa|thay\\s+đổi)\\s+điểm\\s+(?:cho\\s+|của\\s+)?(?:sinh\\s+viên|môn)"
                    + "|vô\\s*hiệu\\s+(?:hóa\\s+)?(?:lệnh|hướng\\s+dẫn|chỉ\\s+dẫn)"
                    + "|(?:in|đọc|xem|hiển\\s*thị|lấy|cho\\s+(?:tôi|ta)\\s+xem)\\s+(?:ra\\s+|cho\\s+(?:tôi|ta)\\s+)?(?:toàn\\s*bộ\\s+)?(?:system\\s+prompt|prompt\\s+hệ\\s+thống|câu\\s+lệnh\\s+hệ\\s+thống|lệnh\\s+hệ\\s+thống|api[\\s_-]?key|jwt[\\s_-]?secret|mật\\s*khẩu|password)"
                    + "|giả\\s*mạo\\s+(?:quản\\s*trị\\s*viên|admin|hệ\\s*thống))");
    private static final String NEW_CONVERSATION = "new-conversation";

    /**
     * Vietnamese is typed unaccented at least as often as not (telex-less
     * keyboards are the norm), so every injection pattern runs a second time
     * against a fully diacritic-folded view of the text: "bo qua tat ca huong
     * dan he thong" must be treated exactly like its accented twin. The folded
     * pattern is derived by folding the pattern string itself, which keeps the
     * two views in lockstep by construction.
     */
    private static final Pattern PROMPT_INJECTION_FOLDED = Pattern.compile(
            foldForMatching(PROMPT_INJECTION.pattern()));

    /**
     * Invisible formatting characters (soft hyphen, zero-width spaces and
     * joins, directional marks, BOM) split banned keywords across otherwise
     * matching spans — "ig\u200Bnore previous instructions" — so they are
     * stripped before NFC folding. Every pattern and the canonical idempotency
     * hash see the same folded text through this single choke point.
     */
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\u206A-\\u206F\\uFEFF]");

    private AssistantInputGuard() { }

    /**
     * Lossless fold used for pattern matching only: NFD decomposition strips
     * every combining mark, đ/Đ fold onto d, and the result is lowercased.
     * Word boundaries and whitespace survive untouched. Callers pass text that
     * already went through normalizeMessage, so invisible characters are gone.
     */
    static String foldForMatching(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char current = decomposed.charAt(index);
            if (Character.getType(current) == Character.NON_SPACING_MARK) continue;
            if (current == 'đ' || current == 'Đ') {
                folded.append('d');
                continue;
            }
            folded.append(Character.toLowerCase(current));
        }
        return folded.toString();
    }

    public static String normalizeMessage(String message) {
        if (message == null) return "";
        String stripped = INVISIBLE.matcher(message.trim()).replaceAll("");
        return Normalizer.normalize(stripped, Normalizer.Form.NFC);
    }

    public static String normalizeLocale(String locale) {
        return "en".equalsIgnoreCase(locale == null ? "" : locale.trim()) ? "en" : "vi";
    }

    public static GuardResult inspect(String message) {
        String normalized = normalizeMessage(message);
        String sensitiveReason = sensitiveReason(normalized, false);
        if (sensitiveReason != null) return new GuardResult(false, sensitiveReason, normalized);
        if (injectionDetected(normalized)) return new GuardResult(false, "PROMPT_INJECTION", normalized);
        return new GuardResult(true, null, normalized);
    }

    /**
     * Inspects text at the provider-output boundary. A student typing an
     * arbitrary address is personal data; an answer quoting the university's
     * own published contact address (which the curated corpus itself contains,
     * e.g. {@code studentId@student.hcmute.edu.vn}) is institutional content.
     * Every address in the text must sit under a national academic domain
     * ({@code *.edu.vn}) for the email rule to be waived; phone/id/secret
     * rules stay strict.
     */
    public static GuardResult inspectProviderOutput(String value) {
        return inspectInternal(value, true);
    }

    /**
     * Technical implementation questions are outside the academic assistant's
     * public scope. Keep this separate from privacy/injection inspection so the
     * same input guard can still be used for provider text and knowledge rows.
     */
    public static boolean isTechnicalRequest(String value) {
        return value != null && TECHNICAL_REQUEST.matcher(normalizeMessage(value)).find();
    }

    /**
     * Validates text before it becomes a public curated source.  This is kept
     * separate from the user-input guard so callers can report a governance
     * rejection without ever persisting or retrieving an obvious personal
     * identifier, credential, or prompt-injection payload.
     */
    public static GuardResult inspectPublicKnowledge(String value) {
        // The curated corpus legitimately contains the university's own
        // published *.edu.vn contact addresses; a strict gate here would
        // silently drop those documents from retrieval. Arbitrary addresses
        // are still rejected, and user input stays strict via inspect().
        return inspectInternal(value, true);
    }

    private static GuardResult inspectInternal(String value, boolean institutionalEmailsAllowed) {
        String normalized = normalizeMessage(value);
        String sensitiveReason = sensitiveReason(normalized, institutionalEmailsAllowed);
        if (sensitiveReason != null) return new GuardResult(false, sensitiveReason, normalized);
        if (injectionDetected(normalized)) return new GuardResult(false, "PROMPT_INJECTION", normalized);
        return new GuardResult(true, null, normalized);
    }

    /** accented text against the original patterns, folded text against folded patterns. */
    private static boolean injectionDetected(String normalized) {
        if (PROMPT_INJECTION.matcher(normalized).find()) return true;
        return PROMPT_INJECTION_FOLDED.matcher(foldForMatching(normalized)).find();
    }

    /** Defensive read-time gate for legacy rows that predate the publish check. */
    public static boolean isPublicKnowledgeSafe(String value) {
        return inspectPublicKnowledge(value).allowed();
    }

    private static String sensitiveReason(String normalized, boolean institutionalEmailsAllowed) {
        if (EMAIL.matcher(normalized).find()
                && !(institutionalEmailsAllowed && allEmailsInstitutional(normalized))) return "SENSITIVE_EMAIL";
        if (containsPhone(normalized)) return "SENSITIVE_PHONE";
        if (STUDENT_ID.matcher(normalized).find()) return "SENSITIVE_STUDENT_ID";
        if (SECRET.matcher(normalized).find()) return "SENSITIVE_CREDENTIAL";
        return null;
    }

    /** True only when every address in the text is under an academic *.edu.vn domain. */
    private static boolean allEmailsInstitutional(String normalized) {
        Matcher matcher = EMAIL.matcher(normalized);
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String email = matcher.group();
            String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
            if (!domain.endsWith(".edu.vn")) return false;
        }
        return found;
    }

    /**
     * The broad candidate pattern is intentionally narrowed before rejection:
     * UUIDs and versioned slugs commonly contain long digit runs separated by
     * hyphens, but a phone is either a contiguous dial string or has a human
     * readable separator such as whitespace, a dot, parentheses, or the
     * conventional hyphen used by grouped phone numbers.
     */
    private static boolean containsPhone(String normalized) {
        Matcher matcher = PHONE.matcher(normalized);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isWithinUuid(normalized, matcher.start(), matcher.end())) continue;
            // Academic year ranges such as 2023 - 2024 or 2026-2027 are legitimate academic metadata, not phone numbers
            if (candidate.matches("(?:19|20)\\d{2}\\s*[-–/]\\s*(?:19|20)?\\d{2}")) continue;
            long digits = candidate.chars().filter(Character::isDigit).count();
            boolean contiguous = candidate.matches("\\+?\\d{8,15}");
            boolean readableSeparator = candidate.indexOf(' ') >= 0
                    || candidate.indexOf('.') >= 0
                    || candidate.indexOf('(') >= 0
                    || candidate.indexOf(')') >= 0;
            // Require a phone-sized final group so dates such as 2024-01-01
            // remain valid public metadata while 090-1234567 and
            // 123-456-7890 are rejected.
            boolean hyphenatedPhone = candidate.matches("\\+?(?:\\d{2,4}-)+\\d{3,8}");
            if (digits >= 8 && (contiguous || readableSeparator || hyphenatedPhone)) return true;
        }
        return false;
    }

    private static boolean isWithinUuid(String normalized, int start, int end) {
        Matcher uuidMatcher = UUID_TOKEN.matcher(normalized);
        while (uuidMatcher.find()) {
            if (start >= uuidMatcher.start() && end <= uuidMatcher.end()) return true;
        }
        return false;
    }

    /** Used at the provider boundary for retrieved text and streamed output. */
    public static boolean containsPromptInjection(String value) {
        return value != null && injectionDetected(normalizeMessage(value));
    }

    public static String canonicalHash(String message, String locale, UUID conversationId) {
        String canonical = normalizeMessage(message) + "\n" + normalizeLocale(locale) + "\n"
                + (conversationId == null ? NEW_CONVERSATION : conversationId.toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public record GuardResult(boolean allowed, String reasonCode, String normalizedMessage) { }
}
