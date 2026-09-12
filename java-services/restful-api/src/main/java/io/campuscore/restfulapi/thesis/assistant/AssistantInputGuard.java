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
    private static final Pattern SECRET = Pattern.compile("(?i)\\b(?:bearer\\s+|sk-[a-z0-9_-]{12,}|api[_ -]?key\\s*[:=]|token\\s*[:=]|password\\s*[:=])");
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
     * Invisible formatting characters (soft hyphen, zero-width spaces and
     * joins, directional marks, BOM) split banned keywords across otherwise
     * matching spans — "ig\u200Bnore previous instructions" — so they are
     * stripped before NFC folding. Every pattern and the canonical idempotency
     * hash see the same folded text through this single choke point.
     */
    private static final Pattern INVISIBLE = Pattern.compile(
            "[\\u00AD\\u200B-\\u200F\\u2060-\\u2064\\u206A-\\u206F\\uFEFF]");

    private AssistantInputGuard() { }

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
        String sensitiveReason = sensitiveReason(normalized);
        if (sensitiveReason != null) return new GuardResult(false, sensitiveReason, normalized);
        if (PROMPT_INJECTION.matcher(normalized).find()) return new GuardResult(false, "PROMPT_INJECTION", normalized);
        return new GuardResult(true, null, normalized);
    }

    /**
     * Validates text before it becomes a public curated source.  This is kept
     * separate from the user-input guard so callers can report a governance
     * rejection without ever persisting or retrieving an obvious personal
     * identifier, credential, or prompt-injection payload.
     */
    public static GuardResult inspectPublicKnowledge(String value) {
        String normalized = normalizeMessage(value);
        String sensitiveReason = sensitiveReason(normalized);
        if (sensitiveReason != null) return new GuardResult(false, sensitiveReason, normalized);
        if (PROMPT_INJECTION.matcher(normalized).find()) return new GuardResult(false, "PROMPT_INJECTION", normalized);
        return new GuardResult(true, null, normalized);
    }

    /** Defensive read-time gate for legacy rows that predate the publish check. */
    public static boolean isPublicKnowledgeSafe(String value) {
        return inspectPublicKnowledge(value).allowed();
    }

    private static String sensitiveReason(String normalized) {
        if (EMAIL.matcher(normalized).find()) return "SENSITIVE_EMAIL";
        if (containsPhone(normalized)) return "SENSITIVE_PHONE";
        if (STUDENT_ID.matcher(normalized).find()) return "SENSITIVE_STUDENT_ID";
        if (SECRET.matcher(normalized).find()) return "SENSITIVE_CREDENTIAL";
        return null;
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
        return value != null && PROMPT_INJECTION.matcher(normalizeMessage(value)).find();
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
