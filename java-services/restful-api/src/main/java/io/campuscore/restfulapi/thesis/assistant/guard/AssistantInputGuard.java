package io.campuscore.restfulapi.thesis.assistant.guard;

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
    /**
     * Round-3 chat-8: bare tool/keyword mentions are split out of the technical
     * gate. "em muốn học về postgresql index", "hướng dẫn cài docker desktop"
     * and "jwt là gì" are legitimate study questions about the very courses the
     * portal teaches — they were refused with TECHNICAL_REQUEST_BLOCKED even in
     * the specialized corpus. A hard ask (an imperative hand-over of a command,
     * key or connection) is never exempted; only a bare token inside an explicit
     * learning/install-study context is.
     */
    private static final Pattern TECHNICAL_TOKENS = Pattern.compile(
            "(?i)(?:"
                    + "\\b(?:curl|wget|invoke-webrequest|iwr|docker(?:\\s+compose)?|docker-compose|kubectl|helm|psql|mysql|redis-cli|npm|pnpm|yarn|bun|npx|mvnw?|gradlew?|git\\s+(?:push|pull|clone|commit|rebase|merge|remote|repo|command)|powershell|pwsh|bash)\\b"
                    // Round-2 sweep chat-2: natural phrasing slipped past the
                    // old list ("Viết giúp câu JDBC connect tới postgres của
                    // trường") — bare jdbc/postgres tokens are technical here.
                    + "|\\bjdbc\\b|\\bpostgres(?:ql)?\\b"
                    + "|\\b(?:api\\s+(?:endpoint|endpoints|chatbot)|api\\s+key|system\\s+prompt|developer\\s+message|stack\\s+trace|traceback|deepseek(?:[- ]v?\\d+)?|provider|llm|jwt|database\\s+(?:password|credentials?))\\b"
                    + ")");
    private static final Pattern TECHNICAL_TOKENS_FOLDED = Pattern.compile(
            foldForMatching(TECHNICAL_TOKENS.pattern()));
    /** Study/install wording that softens a bare token into a course question. */
    private static final Pattern LEARNING_CONTEXT = Pattern.compile(
            "(?i)(?:\\b(?:h[oọ]c|hoc|learn(?:ing|s)?|stud(?:y|ying|ies))\\b"
                    + "|m[oô]n\\s*h[oọ]c|mon\\s*hoc"
                    + "|h[uư][ớơ]ng\\s*d[âăa]n\\s*c[àa]i(?:\\s*[đd][ăâa]t)?|huong\\s*dan\\s*cai(?:\\s*dat)?"
                    + "|c[àa]i\\s*[đd][ăâa]t|cai\\s*dat"
                    + "|ch[uư][oơ]ng\\s*tr[ìi]nh\\s*h[oọ]c|chuong\\s*trinh\\s*hoc"
                    + "|\\b(?:l[àa]\\s*g[ìi]|la\\s*gi|what\\s+(?:is|are))\\b)");
    /** Typing noise (ẫ for â, missing tone marks) must not hide the learning
     *  context — the folded view strips every tone mark, so "huong dan cai"
     *  matches no matter which diacritic variant arrived. */
    private static final Pattern LEARNING_CONTEXT_FOLDED = Pattern.compile(
            foldForMatching(LEARNING_CONTEXT.pattern()));
    private static final Pattern TECHNICAL_REQUEST_HARD = Pattern.compile(
            "(?i)(?:"
                    + "\\b(?:cho\\s+(?:tôi|ta)|xin|give\\s+me|show|provide|send)\\b.{0,80}\\b(?:api|endpoint|system\\s+prompt|developer\\s+message|câu\\s+lệnh|lệnh|command)\\b"
                    + "|\\b(?:bạn|bot|trợ\\s+lý|hệ\\s+thống|you|assistant)\\b.{0,40}\\b(?:đang\\s+(?:sử\\s+dụng|dùng|chạy)\\s+)?(?:mô\\s+hình|model|llm|provider|deepseek)\\b"
                    // Data-exfiltration and privilege-escalation asks must be
                    // refused explicitly at the guard (REJECTED + instant),
                    // never routed to the LLM where they produced a polite
                    // refusal after 6-13 s or — worse for SELECT/schema asks —
                    // a NO_MATCH knowledge miss that implied the data existed
                    // and only failed retrieval (audit an-ninh Q2/Q4/Q10).
                    + "|\\bselect\\s+(?:\\*|\\w[\\w\\s,.\"'`*\\-]{0,40}?)\\bfrom\\s+[\"'`]?\\w"
                    + "|\\b(?:drop|truncate)\\s+(?:table\\s+[\"'`]?\\w|table\\b|database\\b)"
                    + "|\\b(?:insert\\s+into|delete\\s+from)\\s+[\"'`]?[a-z0-9_]*_[a-z0-9]+"
                    // The table-dump phrase must name its DATA context —
                    // "in ra toàn bộ bảng điểm" is a legitimate grades request,
                    // "in ra toàn bộ bảng trong database" is not. The accented
                    // form is paired with the identifier form below.
                    + "|(?:xuất|in\\s*ra|xuất\\s*ra|đọc|dump)\\s+(?:ra\\s+)?(?:nội\\s*dung\\s+bảng|toàn\\s*bộ\\s*(?:các\\s*)?bảng|mọi\\s*bảng)\\s+(?:dữ\\s*liệu|database|cơ\\s*sở\\s*dữ\\s*liệu|db)\\b"
                    + "|\\bschema\\b.{0,40}\\b(?:mọi|toàn\\s*bộ|tất\\s*cả|all)\\s*(?:bảng|tables?)"
                    + "|nội\\s*dung\\s*bảng\\s+[a-z_][a-z0-9_]*"
                    + "|\\bdba\\b|\\bsuperuser\\b"
                    + "|(?:chuỗi|chain)\\s*kết\\s*nối|connection\\s*string|\\bjdbc:"
                    + "|(?:tắt|vô\\s*hiệu\\s*hóa|tắt\\s*bỏ|disable)\\s+(?:mọi|tất\\s*cả|toàn\\s*bộ|all)\\s*(?:bước\\s*)?kiểm\\s*tra\\s*quyền"
                    + "|(?:mở|cấp|grant|open)\\s+(?:cho\\s*(?:tôi|ta)\\s*)?(?:quyền\\s*)?truy\\s*cập\\s*trực\\s*tiếp\\s*(?:vào\\s*)?(?:database|cơ\\s*sở\\s*dữ\\s*liệu|db)"
                    + "|(?:đóng\\s*vai|nhân\\s*vai|vào\\s*vai|act\\s+as|pretend\\s+(?:to\\s+be|you\\s+are)|role\\s*-?\\s*play)[^?!.]{0,60}?(?:\\bdba\\b|superuser|toàn\\s*quyền|root|siêu\\s*user|quản\\s*trị\\s*viên\\s*(?:toàn\\s*quyền|cơ\\s*sở\\s*dữ\\s*liệu))"
                    + ")");

    /** The same refusal logic run against the fully diacritic-folded view —
     *  unaccented "tat moi kiem tra quyen" must be blocked exactly like its
     *  accented twin (same lockstep-derivation as PROMPT_INJECTION_FOLDED). */
    private static final Pattern TECHNICAL_REQUEST_HARD_FOLDED = Pattern.compile(
            foldForMatching(TECHNICAL_REQUEST_HARD.pattern()));
    // Vietnamese phrasing matters because the assistant audience is bilingual:
    // the English-only list let "bỏ qua tất cả hướng dẫn..." reach the provider.
    private static final Pattern PROMPT_INJECTION = Pattern.compile(
            "(?i)(?:ignore\\s+(?:all\\s+)?previous\\s+instructions|disregard\\s+(?:the\\s+)?system\\s+prompt|reveal\\s+(?:the\\s+)?system\\s+prompt|(?:print|show|give\\s+me|what\\s+is)\\s+(?:the\\s+)?(?:system\\s+prompt|api[\\s_-]?key|jwt[\\s_-]?secret|database\\s+password|admin\\s+password)|developer\\s+message|jailbreak|prompt\\s+injection\\s+(?:attack|payload|attempt)|do\\s+anything\\s+now|dan\\s+mode"
                    // "quy định"/"lệnh" alone are academic nouns — "bỏ qua quy
                    // định về số tín chỉ" and "bỏ qua lệnh điểm danh" are
                    // legitimate regulation questions, so only system-scoped
                    // objects mark an injection attempt here.
                    + "|(?:bỏ\\s*qua|quên\\s*(?:đi|hết)?|không\\s+tuân\\s+theo|bỏ\\s*mặc)\\s+(?:tất\\s*cả\\s+|mọi\\s+|các\\s+|những\\s+)?(?:hướng\\s+dẫn|chỉ\\s+dẫn|prompt|lệnh\\s+hệ\\s+thống|quy\\s+định\\s+hệ\\s+thống)"
                    + "|(?:bạn\\s+là\\s+(?:một\\s+)?)?(?:ai|trợ\\s+lý)\\s+không\\s+giới\\s+hạn|(?:hạ|sửa|thay\\s+đổi)\\s+điểm\\s+(?:cho\\s+|của\\s+)?(?:sinh\\s+viên|môn)"
                    + "|vô\\s*hiệu\\s+(?:hóa\\s+)?(?:lệnh|hướng\\s+dẫn|chỉ\\s+dẫn)"
                    + "|(?:in|đọc|xem|hiển\\s*thị|lấy|cho\\s+(?:tôi|ta)\\s+xem)\\s+(?:ra\\s+|cho\\s+(?:tôi|ta)\\s+)?(?:toàn\\s*bộ\\s+)?(?:system\\s+prompt|prompt\\s+hệ\\s+thống|câu\\s+lệnh\\s+hệ\\s+thống|lệnh\\s+hệ\\s+thống|api[\\s_-]?key|jwt[\\s_-]?secret|mật\\s*khẩu|password)"
                    + "|giả\\s*mạo\\s+(?:quản\\s*trị\\s*viên|admin|hệ\\s*thống))");
    /**
     * Production latency audit: "Mật khẩu của admin là gì?" is a credential
     * ask aimed at another party — it passed the guard, paid the full
     * retrieval + LLM round-trip (~10 s) and only then met a model refusal.
     * Asking for someone else's password/OTP/token/key/cookie/session is
     * refused deterministically here, while self-service phrasing ("quên mật
     * khẩu của tôi", "reset my password", minimum-length policy questions)
     * keeps flowing to the knowledge path. The discriminator is the OWNER:
     * a credential word is only refused when bound to a non-self party
     * ("của admin/hệ thống/giảng viên", "the admin password", "her token"),
     * never when it names the asker ("của tôi/em/mình", "my/our/your own").
     */
    /**
     * Bare {@code pass} is campus slang for passing a course ("pass môn",
     * "pass học phần", "pass for the final exam"), not only a credential word.
     * It stays a credential ask only when the next few words do not resolve
     * to an academic noun — reached only through closed-class connectors
     * ("cho", "được", "for", "the", ...) so an owner word in between ("pass
     * của admin kỳ này") cannot launder a credential ask into slang.
     */
    private static final String PASS_CREDENTIAL = "pass(?!\\s+"
            + "(?:(?:cho|được|duoc|for|the|a|an|in|on|my|this|that|each|every)\\s+){0,2}"
            + "(?:môn|mon|học\\s*phần|hoc\\s*phan|phần\\s*học|phan\\s*hoc|kỳ\\s*thi|ky\\s*thi"
            + "|kiểm\\s*tra|kiem\\s*tra|kỳ|ki|course|subject|exam|exams|test|tests|class|quiz|module|curriculum"
            + "|final|finals|midterm|bài|bai|assignment|homework"
            + "|credit|tín\\s*chỉ|tin\\s*chi|đại\\s*cương|dai\\s*cuong|mark|điểm|diem)\\b"
            // ...unless an owner word trails the noun: "pass for the course
            // admin" is a credential ask wearing academic word order.
            + "(?!'?s?\\s+(?:admin|administrator|administration|root|system|server|database|db"
            + "|lecturer|teacher|staff|bot|assistant|account|accounts|user|users|portal"
            + "|website|web|mod|moderator|owner|operator)\\b))";
    private static final Pattern CREDENTIAL_REQUEST = Pattern.compile(
            "(?i)(?:"
                    // VI: credential word (allowing a short qualifier gap such
                    // as "đăng nhập") owned by a non-self party. "của" is
                    // optional so direct phrasing like "mật khẩu admin" or
                    // "mật khẩu tài khoản admin" is refused the same way.
                    + "\\b(?:mật\\s*khẩu|mat\\s*khau|password|passkey|pass\\s*code|" + PASS_CREDENTIAL + "|pin\\s*code|pin|mã\\s*pin|ma\\s*pin|otp|mã\\s*xác\\s*(?:thực|nhận)|ma\\s*xac\\s*(?:thuc|nhan)"
                    + "|token|api[\\s_-]?key|apikey|secret|cookie|cookies|session|mã\\s*đăng\\s*nhập|ma\\s*dang\\s*nhap"
                    + "|phiên\\s*đăng\\s*nhập|phien\\s*dang\\s*nhap)"
                    + "(?:\\s+\\w+){0,3}?(?:\\s+của)?\\s+"
                    + "(?!(?:tôi|toi|mình|minh|em|tui|bản\\s*thân|ban\\s*than|chính\\s*mình|chinh\\s*minh|chính\\s*tôi|chinh\\s*toi)\\b)"
                    + "(?:(?:tài\\s*khoản|tai\\s*khoan)\\s+)?"
                    + "(?:admin|administrator|quản\\s*trị|quan\\s*tri|giảng\\s*viên|giang\\s*vien|giáo\\s*viên|giao\\s*vien"
                    + "|thầy|thay|cô|co|hệ\\s*thống|he\\s*thong|bạn|ban|sinh\\s*viên|sinh\\s*vien|học\\s*sinh|hoc\\s*sinh"
                    + "|người\\s*khác|nguoi\\s*khac|họ|ho|nó|no|ấy|ay|trường|truong|server|máy\\s*chủ|may\\s*chu"
                    + "|database|cơ\\s*sở\\s*dữ\\s*liệu|co\\s*so\\s*du\\s*lieu|csdl|phòng\\s*đào\\s*tạo|phong\\s*dao\\s*tao"
                    + "|ai\\s*đó|ai\\s*do|giáo\\s*vụ|giao\\s*vu|bot|trợ\\s*lý|tro\\s*ly|portal|cổng|website|web|tài\\s*khoản\\s*khác|tai\\s*khoan\\s*khac|người\\s*dùng\\s*khác|nguoi\\s*dung\\s*khac)\\b"
                    // VI: credential handed to a non-self recipient —
                    // "đổi mật khẩu cho bạn Nam". "cho tôi/em/mình" stays the
                    // asker's own self-service request.
                    + "|\\b(?:mật\\s*khẩu|mat\\s*khau|password|passkey|" + PASS_CREDENTIAL + "|pin|mã\\s*pin|ma\\s*pin|otp|token|mã\\s*xác\\s*thực|ma\\s*xac\\s*thuc"
                    + "|phiên\\s*đăng\\s*nhập|phien\\s*dang\\s*nhap)"
                    + "\\s+cho\\s+"
                    + "(?!(?:tôi|toi|mình|minh|em|tui|bản\\s*thân|ban\\s*than|chính\\s*mình|chinh\\s*minh)\\b"
                    + "|(?:tài\\s*khoản|tai\\s*khoan|email|account)\\s+(?:của\\s+|cua\\s+)?"
                    + "(?:tôi|toi|mình|minh|em|tui|bản\\s*thân|ban\\s*than|chính\\s*mình|chinh\\s*minh)\\b)\\w"
                    // EN: a non-self owner compounded on the credential —
                    // "the admin password", "system token", "her session".
                    + "|\\b(?:admin|administrator|root|system|server|database|db|lecturer|teacher|staff|bot|assistant"
                    + "|someone\\s+else|another\\s+(?:user|student|person)|other\\s+(?:user|student|person)"
                    + "|the\\s+(?:school|university)|their|his|her|its)(?:'s|'|s)?\\s+"
                    + "(?:password|passphrase|passcode|passkey|" + PASS_CREDENTIAL + "|pin|otp|token|api[\\s_-]?key|apikey|secret|credentials?|session\\s*(?:id|token)?|cookies?)"
                    // EN: reveal verbs may name the bot too ("tell me your
                    // password") — "your" only counts as non-self here.
                    + "|\\b(?:tell|show|give|reveal|share|print|leak|expose|what\\s+is|what'?s|whats)\\s+"
                    + "(?:me\\s+|us\\s+)?(?:the\\s+|a\\s+|an\\s+)?"
                    + "(?:admin|administrator|root|system|server|database|db|your|their|his|her|its"
                    + "|someone\\s+else'?s?|another\\s+(?:user|person)'?s?|other\\s+(?:user|student|person)'?s?"
                    + "|the\\s+(?:lecturer|teacher|bot|assistant)'?s?)(?:'s)?\\s+"
                    + "(?:password|passphrase|passcode|passkey|" + PASS_CREDENTIAL + "|pin|otp|token|api[\\s_-]?key|apikey|secret|credentials?)"
                    // EN: reset/change verbs keep "your" out — "reset your
                    // password" is self-service phrasing, not an exfil ask.
                    + "|\\b(?:reset|change|recover|retrieve|steal|crack|hack|bypass|brute\\s*force)\\s+"
                    + "(?:the\\s+|a\\s+|an\\s+)?"
                    + "(?:admin|administrator|root|system|server|database|db|their|his|her|its"
                    + "|someone\\s+else'?s?|another\\s+(?:user|person)'?s?|other\\s+(?:user|student|person)'?s?"
                    + "|the\\s+(?:lecturer|teacher|bot|assistant)'?s?)(?:'s)?\\s+"
                    + "(?:password|passphrase|passcode|passkey|" + PASS_CREDENTIAL + "|pin|otp|token|api[\\s_-]?key|apikey|secret|credentials?)"
                    // EN: credential "of/for" a non-self party — "password of
                    // the admin"; "of/for my/your own account" stays free.
                    + "|\\b(?:password|passphrase|passcode|passkey|" + PASS_CREDENTIAL + "|pin|otp|token|api[\\s_-]?key|apikey|secret|credentials?)\\s+"
                    + "(?:of|for|belonging\\s+to)\\s+"
                    + "(?!(?:my|mine|me|myself|your|yours|yourself|our|ours)\\b)"
                    + "(?:the\\s+|a\\s+|an\\s+)?[a-z]"
                    + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern CREDENTIAL_REQUEST_FOLDED = Pattern.compile(
            foldForMatching(CREDENTIAL_REQUEST.pattern()));
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
    public static String foldForMatching(String value) {
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
        if (credentialRequestDetected(normalized)) return new GuardResult(false, "CREDENTIAL_REQUEST", normalized);
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
        if (value == null) return false;
        String normalized = normalizeMessage(value);
        // Hard asks (hand me a command / key / connection, exfiltration,
        // privilege escalation) are technical in every phrasing.
        if (TECHNICAL_REQUEST_HARD.matcher(normalized).find()) return true;
        if (TECHNICAL_REQUEST_HARD_FOLDED.matcher(foldForMatching(normalized)).find()) return true;
        // Bare tokens become legitimate study questions when the message names
        // a learning or install-study context; without it the round-2 rule
        // stands (a bare jdbc/postgres mention stays technical).
        boolean learning = LEARNING_CONTEXT.matcher(normalized).find()
                || LEARNING_CONTEXT_FOLDED.matcher(foldForMatching(normalized)).find();
        if (learning) return false;
        if (TECHNICAL_TOKENS.matcher(normalized).find()) return true;
        // Same dual-view rule as injection: unaccented Vietnamese must not be
        // able to phrase a data-exfiltration ask past the guard.
        return TECHNICAL_TOKENS_FOLDED.matcher(foldForMatching(normalized)).find();
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

    /** Same dual-view rule as injection: unaccented Vietnamese credential
     *  asks ("mat khau cua admin") must meet the identical refusal. */
    private static boolean credentialRequestDetected(String normalized) {
        if (CREDENTIAL_REQUEST.matcher(normalized).find()) return true;
        return CREDENTIAL_REQUEST_FOLDED.matcher(foldForMatching(normalized)).find();
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
    private static final Pattern YEAR_RANGE = Pattern.compile("(?:19|20)\\d{2}\\s*[-–/]\\s*(?:19|20)?\\d{2}");

    private static boolean containsPhone(String normalized) {
        Matcher matcher = PHONE.matcher(normalized);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (isWithinUuid(normalized, matcher.start(), matcher.end())) continue;
            // Academic year ranges such as 2023 - 2024 or 2026-2027 are legitimate academic metadata, not phone numbers.
            // Round-3 chat-1: the range may sit NEXT TO a term number — the
            // candidate "1 2025-2026" from "GPA for semester 1 2025-2026" is a
            // term plus year range, not a dial string. Any candidate containing
            // a year range is exempt, wherever the range sits inside it.
            if (candidate.matches("(?:19|20)\\d{2}\\s*[-–/]\\s*(?:19|20)?\\d{2}")) continue;
            if (YEAR_RANGE.matcher(candidate).find()) continue;
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
