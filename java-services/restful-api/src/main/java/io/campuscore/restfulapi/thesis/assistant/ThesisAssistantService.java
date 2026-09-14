package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.CompletionRequest;
import io.campuscore.restfulapi.thesis.assistant.AssistantCompletionProvider.ProviderSegment;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.Citation;
import io.campuscore.restfulapi.web.DomainException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Orchestrates lexical retrieval, fenced turns, and provider I/O outside transactions. */
@Service
@Profile("persistence")
public class ThesisAssistantService {
    static final String MODEL = "curated-lexical-rag";
    static final int TOP_K = 5;
    private static final String DEFAULT_LOCALE = "vi";

    /**
     * The provider intermittently glues numbers to adjacent Vietnamese words
     * ("gồm từ03 đến05"). A deterministic, whitelist-bounded spacing repair
     * runs at the provider boundary before the answer is streamed/committed.
     * Course-code style tokens (SE101, KLTN2026) are safe because only the
     * listed function words trigger the letter→digit direction, and the
     * digit→letter direction requires a complete following word.
     */
    private static final java.util.regex.Pattern NUMBER_GLUE_AFTER_WORD = java.util.regex.Pattern.compile(
            "(?<![\\p{L}\\p{N}_])(từ|đến|tới|đa|thiểu|khoảng|hơn|dưới|trên|gồm|bằng|tổng|cộng|còn|điểm|mức|đạt|hoặc|hoac|đương|duong)(?=\\p{N})",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern NUMBER_GLUE_BEFORE_WORD = java.util.regex.Pattern.compile(
            "(?<=\\p{N})(thành|người|tín|chỉ|nhóm|đề|ngày|giờ|phút|tuần|năm|tháng|buổi|ca|giảng|viên|sinh|phân|điểm|tiết|môn|lớp)(?![\\p{L}])",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern ENGLISH_NUMBER_GLUE_AFTER_WORD = java.util.regex.Pattern.compile(
            // Deliberately narrow and case-sensitive: a case-insensitive "is" used
            // to rewrite the course code "IS101" into "IS 101".
            "(?<![\\p{L}\\p{N}_])(?:of|from|to|up to|at least|at most|minimum|maximum)(?=\\d)");
    private static final java.util.regex.Pattern ENGLISH_NUMBER_GLUE_BEFORE_WORD = java.util.regex.Pattern.compile(
            "(?i)(?<=\\d)(?:credits?|courses?|weeks?|days?|students?|members?|terms?|months?)(?![\\p{L}])");
    /**
     * In Vietnamese prose a number is always separated from the preceding word
     * by a space, but provider output regularly glues them ("trong4 tuần",
     * "thang10", "đủ100%"). A word allowlist kept missing cases, so the rule is
     * inverted: any two-or-more letter run followed directly by a digit gets a
     * space, unless the run is an uppercase ASCII identifier.
     */
    private static final java.util.regex.Pattern WORD_GLUE_BEFORE_DIGIT = java.util.regex.Pattern.compile(
            "(?<![\\p{L}\\p{N}_/.-])(\\p{L}{2,})(\\p{N})");
    /** Uppercase ASCII runs are identifiers, not prose: SE101, V38, TOEIC, IC3. */
    private static final java.util.regex.Pattern ASCII_IDENTIFIER_WORD = java.util.regex.Pattern.compile("[A-Z]+");
    /** Characters that make up an identifier, URL, email address or anchor. */
    private static final java.util.regex.Pattern TOKEN_CHARS =
            java.util.regex.Pattern.compile("[A-Za-z0-9._+#?&=~/@%-]");
    /** A run carrying one of these is a link, address or anchor, not prose. */
    private static final java.util.regex.Pattern TOKEN_MARKERS = java.util.regex.Pattern.compile("[@?#&=]|://");
    private static final String EMPHASIS_MARKER = "**";
    private static final java.util.regex.Pattern WORD_CHAR_BEFORE =
            java.util.regex.Pattern.compile("[\\p{L}\\p{N}.]");
    private static final java.util.regex.Pattern CLAUSE_START =
            java.util.regex.Pattern.compile("[\\p{Lu}\\p{N}•*]");
    /**
     * A list marker that directly abuts the end of a word is concatenated output
     * ("học lại- Điểm F"). Whether it really is a bullet is decided in
     * {@link #repairBulletGlue(String)}, because abbreviated labels such as
     * "Nhóm SV- K20" and hyphenated prose such as "self- study" must survive.
     */
    private static final java.util.regex.Pattern BULLET_GLUE_CANDIDATE = java.util.regex.Pattern.compile(
            "(?<=[\\p{L}])-[ \\t]");
    /**
     * A list marker indented mid-line is also glued output, but only after a
     * clause terminator, so legitimate ranges such as "5 - 7 ngày" stay intact.
     */
    private static final java.util.regex.Pattern INLINE_LIST_GAP = java.util.regex.Pattern.compile(
            "(?<=[.:;!?*])[ \\t]+(?=-[ \\t])");
    /**
     * Two or more spaces before a list marker is concatenation rather than
     * intentional spacing: provider output produces "## Điều kiện tốt nghiệp  -
     * Tích lũy…", which would otherwise pull the first bullet into the heading.
     * A heading that legitimately uses " - " as a separator has single spaces.
     */
    private static final java.util.regex.Pattern DOUBLE_SPACE_LIST_GAP =
            java.util.regex.Pattern.compile("[ \\t]{2,}(?=-[ \\t])");
    /**
     * A heading marker glued directly after a word loses its block boundary
     * ("…gia hạn học phí## Thời hạn nộp học phí"). Requiring a lowercase letter
     * before the marker keeps "C# là…" intact, and requiring whitespace after it
     * keeps anchors such as "#muc4" intact.
     */
    private static final java.util.regex.Pattern HEADING_GLUE =
            java.util.regex.Pattern.compile("(?<=[\\p{Ll}])(#{1,6})[ \\t]+");
    private static final java.util.regex.Pattern NUMBER_GLUE_AFTER_LABEL_COLON = java.util.regex.Pattern.compile(
            "(?<=[\\p{L}])(:)(?=\\d)");
    private static final java.util.regex.Pattern HEADING_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)(\\d+\\.\\s+(?:Truy cập và chọn học phần|Đăng ký lớp|Xử lý các thông báo từ hệ thống|"
                    + "Lưu ý về thời gian đăng ký|Kiểm tra điều kiện học phần|Access and select courses|"
                    + "Register for a section|Handle system messages|Registration timing|Check course requirements|"
                    + "Lưu ý về học phần điều kiện|Course requirements))\\s+(?=\\p{Lu})",
            java.util.regex.Pattern.UNICODE_CASE);
    /**
     * Some provider responses correctly emit a Markdown heading marker but
     * still concatenate the first sentence onto the same line. Keep this
     * allowlist bounded to assistant section labels so ordinary title-case
     * prose is never split heuristically.
     */
    private static final java.util.regex.Pattern MARKDOWN_HEADING_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)^((?:#{1,6}[ \\t]+)(?:Cách đăng ký học phần trên CampusCore|Cách đăng ký học phần|"
                    + "Đăng ký học phần trên CampusCore|Đăng ký học phần|Các bước đăng ký|"
                    + "Khi gặp thông báo từ hệ thống|Khi gặp thông báo|Lưu ý về điều kiện học phần|"
                    + "Lưu ý về học phần điều kiện|Thời gian đăng ký|"
                    + "How to register for a course in CampusCore|Course registration on CampusCore|"
                    + "When you see a system message|If Registration Is Blocked|What Happens During Add/Drop|Add/Drop Period|"
                    + "Credit Load Rules|"
                    + "Related Rules to Keep in Mind|Prerequisites and Limits|Prerequisites and Retakes|"
                    + "Course requirements|Registration timing|"
                    + "Registering for a Course|Prerequisites and Related Requirements|During Add/Drop|"
                    + "Withdrawal and Credit Workload|Retakes and Grade Improvement|"
                    + "Prerequisites and Related Courses|Credit Limits))[ \\t]*(?=[\\p{Lu}\\p{N}•*-])",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    /** Restore a bullet boundary after an allowlisted sentence starter without touching hyphenated titles. */
    private static final java.util.regex.Pattern MARKDOWN_HEADING_DASH_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)^((?:#{1,6}[ \\t]+)[^\\r\\n]*?\\S)[ \\t]*(?=-[ \\t]+(?:A|An|Before|Check|Each|If|Open|Prerequisite|Prerequisites|Review|Sections|The|This|To|Use|While|When|You|Bạn|Các|Cần|Chọn|Hãy|Khi|Kiểm|Lớp|Mở|Nếu|Xem|Để|Đợt)\\b)",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern EMPHASIZED_HEADING_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)^((?:\\*\\*|__)(?:Cách đăng ký học phần trên CampusCore|Cách đăng ký học phần|"
                    + "Đăng ký học phần trên CampusCore|Đăng ký học phần|Các bước đăng ký|"
                    + "Khi gặp thông báo từ hệ thống|Khi gặp thông báo|Lưu ý về điều kiện học phần|"
                    + "Lưu ý về học phần điều kiện|Thời gian đăng ký|"
                    + "How to register for a course in CampusCore|Course registration on CampusCore|"
                    + "When you see a system message|If Registration Is Blocked|What Happens During Add/Drop|Add/Drop Period|"
                    + "Credit Load Rules|"
                    + "Related Rules to Keep in Mind|Prerequisites and Limits|Prerequisites and Retakes|"
                    + "Course requirements|Registration timing|"
                    + "Registering for a Course|Prerequisites and Related Requirements|During Add/Drop|"
                    + "Withdrawal and Credit Workload|Retakes and Grade Improvement|"
                    + "Prerequisites and Related Courses|Credit Limits)(?:\\*\\*|__))[ \\t]*(?=[\\p{Lu}\\p{N}•*-])",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern EMPHASIZED_HEADING_DASH_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)^((?:\\*\\*|__)[^\\r\\n]*?\\S)[ \\t]*(?=-[ \\t]+(?:A|An|Before|Check|Each|If|Open|Prerequisite|Prerequisites|Review|Sections|The|This|To|Use|While|When|You|Bạn|Các|Cần|Chọn|Hãy|Khi|Kiểm|Lớp|Mở|Nếu|Xem|Để|Đợt)\\b)",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern LABEL_SENTENCE_GLUE = java.util.regex.Pattern.compile(
            "(?m)(Lưu ý về học phần điều kiện|Course requirements)\\s+(?=\\p{Lu})",
            java.util.regex.Pattern.UNICODE_CASE);
    private static final java.util.regex.Pattern COURSE_CODE = java.util.regex.Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])[A-Z]{2,}[0-9]{2,}(?![\\p{L}\\p{N}_])");
    /**
     * Retrieval is deliberately scoped before the database query.  A generic
     * lexical overlap (for example "thời" or "hôm nay") is not evidence that
     * a public academic document answers the question.  Keep this vocabulary
     * broad enough for the campus services actually represented in the
     * corpus, while leaving greetings and unrelated small talk to the FE
     * resolver/no-match path.
     */
    private static final java.util.regex.Pattern PUBLIC_SCOPE_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}_])(?:academic|campus|course|courses|register|registered|registration|schedule|class|classes|grade|grades|gpa|transcript|credit|credits|semester|term|tuition|fee|fees|announcement|announcements|notice|notices|thesis|capstone|topic|topics|defen[cs]e|supervisor|supervision|lecturer|faculty|department|student|portal|curriculum|prerequisite|corequisite|exam|examination|scholarship|graduation|internship|library|dormitory|wifi|email|account|password|research|appeal|regrade|withdrawal|withdraw|retake|conduct|training|attendance|classroom|room|hoc|dang\\s+ky|dang\\s+nhap|lich|diem|bang\\s+diem|tin\\s+chi|hoc\\s+ky|hoc\\s+phi|thong\\s+bao|luan\\s+van|do\\s+an|de\\s+tai|bao\\s+ve|giang\\s+vien|khoa|bo\\s+mon|sinh\\s+vien|cong|chuong\\s+trinh|tien\\s+quyet|song\\s+hanh|thi|hoc\\s+bong|tot\\s+nghiep|thuc\\s+tap|thu\\s+vien|ky\\s+tuc\\s+xa|tai\\s+khoan|mat\\s+khau|nghien\\s+cuu|phuc\\s+khao|rut\\s+hoc\\s+phan|hoc\\s+lai|ren\\s+luyen|diem\\s+danh|phong)(?![\\p{L}\\p{N}_])");

    static String normalizeNumberSpacing(String text) {
        if (text == null || text.isBlank()) return text;
        String out = NUMBER_GLUE_AFTER_WORD.matcher(text).replaceAll("$1 ");
        out = NUMBER_GLUE_BEFORE_WORD.matcher(out).replaceAll(" $1");
        out = ENGLISH_NUMBER_GLUE_AFTER_WORD.matcher(out).replaceAll("$0 ");
        out = ENGLISH_NUMBER_GLUE_BEFORE_WORD.matcher(out).replaceAll(" $0");
        out = NUMBER_GLUE_AFTER_LABEL_COLON.matcher(out).replaceAll("$1 ");
        return separateWordsFromNumbers(out);
    }

    /**
     * Inserts the missing space between a prose word and a following digit while
     * leaving identifier-shaped tokens untouched — including URLs, email
     * addresses and anchors, whose runs carry a token marker.
     */
    static String separateWordsFromNumbers(String text) {
        if (text == null || text.isBlank()) return text;
        java.util.regex.Matcher matcher = WORD_GLUE_BEFORE_DIGIT.matcher(text);
        StringBuilder out = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            String word = matcher.group(1);
            if (ASCII_IDENTIFIER_WORD.matcher(word).matches()
                    || isTokenLike(text, matcher.start(), matcher.end())) {
                continue;
            }
            changed = true;
            matcher.appendReplacement(out,
                    java.util.regex.Matcher.quoteReplacement(word + " " + matcher.group(2)));
        }
        if (!changed) return text;
        matcher.appendTail(out);
        return out.toString();
    }

    /** True when the surrounding run is part of a link, address or anchor. */
    private static boolean isTokenLike(String text, int start, int end) {
        int left = start;
        while (left > 0 && TOKEN_CHARS.matcher(String.valueOf(text.charAt(left - 1))).matches()) left--;
        int right = end;
        while (right < text.length() && TOKEN_CHARS.matcher(String.valueOf(text.charAt(right))).matches()) right++;
        String run = text.substring(left, right);
        return TOKEN_MARKERS.matcher(run).find() || run.startsWith("#");
    }

    /**
     * Restores the block boundary when an OPENING emphasis marker abuts a word
     * ("học phí**Hạn nộp học phí**"). Markers are counted per line, so the
     * closing marker of a bold run stays where it is and "**Điểm**4.0" is not
     * mangled.
     */
    static String repairEmphasisGlue(String text) {
        if (text == null || text.isBlank() || !text.contains(EMPHASIS_MARKER)) return text;
        StringBuilder out = new StringBuilder(text.length());
        for (String line : text.split("\n", -1)) {
            int markers = 0;
            boolean openingAtLineStart = false;
            int index = 0;
            while (index < line.length()) {
                if (line.startsWith(EMPHASIS_MARKER, index)) {
                    char before = index > 0 ? line.charAt(index - 1) : ' ';
                    int afterIndex = index + EMPHASIS_MARKER.length();
                    char after = afterIndex < line.length() ? line.charAt(afterIndex) : ' ';
                    if (markers % 2 == 0) {
                        openingAtLineStart = index == 0;
                        if (Character.isLetter(before)
                                && (Character.isLetter(after) || Character.isDigit(after))) {
                            out.append("\n\n");
                        }
                    } else {
                        out.append(EMPHASIS_MARKER);
                        markers++;
                        index += EMPHASIS_MARKER.length();
                        // Text glued straight after a closing marker is a
                        // concatenated block ("**Hậu quả…**Sinh viên không đóng…").
                        // A bold heading opens at the line start, so it regains a
                        // block boundary; an inline run only gains the space. A
                        // digit after the marker means inline emphasis over a
                        // number ("**Điểm**4.0"), which stays as written.
                        if (Character.isLetter(after)) {
                            out.append(openingAtLineStart ? "\n\n" : " ");
                        }
                        continue;
                    }
                    out.append(EMPHASIS_MARKER);
                    markers++;
                    index += EMPHASIS_MARKER.length();
                    continue;
                }
                out.append(line.charAt(index));
                index++;
            }
            out.append('\n');
        }
        // split(-1) keeps a trailing empty segment, so one newline is surplus.
        return out.length() > 0 ? out.substring(0, out.length() - 1) : text;
    }

    /**
     * Restores a bullet boundary where a list marker was glued to a word
     * ("học lại- Điểm F") while leaving abbreviated labels ("Nhóm SV- K20") and
     * hyphenated prose ("self- study") alone.
     */
    static String repairBulletGlue(String text) {
        if (text == null || text.isBlank()) return text;
        java.util.regex.Matcher matcher = BULLET_GLUE_CANDIDATE.matcher(text);
        StringBuilder out = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            int start = matcher.start();
            while (start > 0 && WORD_CHAR_BEFORE.matcher(String.valueOf(text.charAt(start - 1))).matches()) {
                start--;
            }
            String precedingWord = text.substring(start, matcher.start());
            String following = text.substring(matcher.end());
            boolean abbreviation = precedingWord.matches("[A-Z]{2,}");
            boolean clauseStart = !following.isEmpty() && CLAUSE_START.matcher(String.valueOf(following.charAt(0))).matches();
            if (abbreviation || !clauseStart) continue;
            changed = true;
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement("\n\n- "));
        }
        if (!changed) return text;
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Keeps user-facing assistant copy free of internal enum names that are
     * useful to the API but confusing in a student-facing answer.
     */
    static String normalizeAssistantCopy(String text, String locale) {
        if (text == null || text.isBlank()) return text;
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        String out = normalizeNumberSpacing(text);
        out = HEADING_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = MARKDOWN_HEADING_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = MARKDOWN_HEADING_DASH_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = EMPHASIZED_HEADING_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = EMPHASIZED_HEADING_DASH_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = LABEL_SENTENCE_GLUE.matcher(out).replaceAll("$1\n\n");
        out = INLINE_LIST_GAP.matcher(out).replaceAll("\n");
        out = DOUBLE_SPACE_LIST_GAP.matcher(out).replaceAll("\n");
        out = HEADING_GLUE.matcher(out).replaceAll("\n\n$1 ");
        // Marker-aware passes run last: they need the emphasis state of the whole
        // line, which a single regex match window cannot express.
        out = repairBulletGlue(repairEmphasisGlue(out));
        if ("en".equals(normalizedLocale)) {
            return out
                    .replaceAll("(?i)\\bthe\\s+ADD_DROP_OPEN\\b", "the open add/drop period")
                    .replaceAll("\\bADD_DROP_OPEN\\b", "the open add/drop period")
                    .replaceAll("(?i)\\bthe\\s+REGISTRATION_OPEN\\b", "the open registration period")
                    .replaceAll("\\bREGISTRATION_OPEN\\b", "the open registration period")
                    .replaceAll("(?i)\\bthe\\s+ADD_DROP\\b", "the add/drop period")
                    .replaceAll("\\bADD_DROP\\b", "add/drop period")
                    .replaceAll("(?i)\\bthe\\s+REGISTRATION\\b", "the registration period")
                    .replaceAll("\\bREGISTRATION\\b", "registration period")
                    .replaceAll("(?i)\\bperiod(?:\\s+period)+\\b", "period");
        }
        return out
                .replaceAll("Đợt\\s+ADD_DROP_OPEN\\b", "Đợt bổ sung/rút học phần đang mở")
                .replaceAll("(?iu)đợt\\s+ADD_DROP_OPEN\\b", "đợt bổ sung/rút học phần đang mở")
                .replaceAll("\\bADD_DROP_OPEN\\b", "đợt bổ sung/rút học phần đang mở")
                .replaceAll("Đợt\\s+REGISTRATION_OPEN\\b", "Đợt đăng ký đang mở")
                .replaceAll("(?iu)đợt\\s+REGISTRATION_OPEN\\b", "đợt đăng ký đang mở")
                .replaceAll("\\bREGISTRATION_OPEN\\b", "đợt đăng ký đang mở")
                .replaceAll("Đợt\\s+ADD_DROP\\b", "Đợt bổ sung/rút học phần")
                .replaceAll("(?iu)đợt\\s+ADD_DROP\\b", "đợt bổ sung/rút học phần")
                .replaceAll("\\bADD_DROP\\b", "đợt bổ sung/rút học phần")
                .replaceAll("Đợt\\s+REGISTRATION\\b", "Đợt đăng ký")
                .replaceAll("(?iu)đợt\\s+REGISTRATION\\b", "đợt đăng ký")
                .replaceAll("\\bREGISTRATION\\b", "đợt đăng ký");
    }

    private final ThesisAssistantKnowledgeRepository knowledge;
    private final DeepSeekClient provider;
    private final ThesisAssistantRepository legacyHistory;
    private final ThesisAssistantTurnRepository turns;
    private final ThesisAssistantCatalogRepository catalog;
    private final AssistantCancellationRegistry cancellations;
    private final DeepSeekProperties deepSeek;
    private final AssistantProperties properties;

    /** Constructor retained for lexical/unit tests that do not load persistence beans. */
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge) {
        this.knowledge = knowledge;
        this.provider = null;
        this.legacyHistory = null;
        this.turns = null;
        this.catalog = null;
        this.cancellations = null;
        this.deepSeek = null;
        this.properties = null;
    }

    /** Compatibility constructor retained for the previous candidate tests. */
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge, DeepSeekClient provider,
            ThesisAssistantRepository history, DeepSeekProperties deepSeek, AssistantProperties properties) {
        this.knowledge = knowledge;
        this.provider = provider;
        this.legacyHistory = history;
        this.turns = null;
        this.catalog = null;
        this.cancellations = null;
        this.deepSeek = deepSeek;
        this.properties = properties;
    }

    @Autowired
    public ThesisAssistantService(ThesisAssistantKnowledgeRepository knowledge, DeepSeekClient provider,
            ThesisAssistantRepository history, ThesisAssistantTurnRepository turns,
            ThesisAssistantCatalogRepository catalog, AssistantCancellationRegistry cancellations,
            DeepSeekProperties deepSeek, AssistantProperties properties) {
        this.knowledge = knowledge;
        this.provider = provider;
        this.legacyHistory = history;
        this.turns = turns;
        this.catalog = catalog;
        this.cancellations = cancellations;
        this.deepSeek = deepSeek;
        this.properties = properties;
    }

    /** Pure lexical path used by tests and by safe fallback when persistence is unavailable. */
    public ChatResponse answer(String message, String locale) {
        return lexicalAnswer(message, locale);
    }

    /** Backward-compatible server entry point; new controllers pass the client key explicitly. */
    public ChatResponse answer(String message, String locale, String conversationId, String ownerId) {
        return answer(message, locale, conversationId, ownerId, UUID.randomUUID(), ignored -> { });
    }

    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, ignored -> { });
    }

    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> streamSink) {
        if (turns == null || properties == null) return legacyAnswer(message, locale, conversationId, ownerId);
        return execute(message, locale, conversationId, ownerId, clientRequestId, streamSink == null ? ignored -> { } : streamSink);
    }

    public ChatResponse stream(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, sink);
    }

    public ThesisAssistantTurnRepository.CancelResult cancel(UUID clientRequestId, String ownerId) {
        if (turns == null) return new ThesisAssistantTurnRepository.CancelResult(false, "TURN_NOT_FOUND");
        ThesisAssistantTurnRepository.TurnRow row = turns.findByRequest(ownerId, clientRequestId);
        if (row == null) throw problem(404, "TURN_NOT_FOUND", "Request was not found");
        return turns.cancel(row.turnId(), ownerId, clientRequestId,
                handle -> { if (cancellations != null) cancellations.fence(ownerId, handle.clientRequestId(), handle.leaseGeneration()); });
    }

    public int setFeedback(UUID messageId, String ownerId, String rating, String reason) {
        if (turns == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return turns.setFeedback(messageId, ownerId, rating, reason);
    }

    public int deleteFeedback(UUID messageId, String ownerId) {
        if (turns == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return turns.deleteFeedback(messageId, ownerId);
    }

    public List<ThesisAssistantRepository.Conversation> conversations(String ownerId) {
        if (legacyHistory == null) return List.of();
        return legacyHistory.conversations(ownerId);
    }

    public ThesisAssistantRepository.ConversationPage conversationPage(String ownerId, Integer limit, String cursor) {
        if (legacyHistory == null) return new ThesisAssistantRepository.ConversationPage(List.of(), null);
        return legacyHistory.conversations(ownerId, limit == null ? 20 : limit, cursor);
    }

    public String createConversation(String ownerId, String locale) {
        if (legacyHistory == null || properties == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        return legacyHistory.ensureConversation(ownerId, null, AssistantInputGuard.normalizeLocale(locale), properties.retentionDays()).toString();
    }

    public List<ThesisAssistantRepository.Message> messages(UUID conversationId, String ownerId) {
        if (legacyHistory == null) return List.of();
        return legacyHistory.messages(conversationId, ownerId);
    }

    public ThesisAssistantRepository.MessagePage messagePage(UUID conversationId, String ownerId, Integer limit, String cursor) {
        if (legacyHistory == null) return new ThesisAssistantRepository.MessagePage(List.of(), null);
        return legacyHistory.messagesPage(conversationId, ownerId, limit == null ? 50 : limit, cursor);
    }

    public void deleteConversation(UUID conversationId, String ownerId) {
        if (legacyHistory == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        if (turns != null && cancellations != null) {
            // Fence, tombstone, and physically delete under one database
            // transaction. This closes the gap where a new reserve could race
            // between the old two-step purge and history delete.
            turns.purgeAndDeleteConversation(conversationId, ownerId,
                    (owner, handle) -> { if (cancellations != null) cancellations.fence(owner, handle.clientRequestId(), handle.leaseGeneration()); });
            return;
        }
        if (legacyHistory.deleteConversation(conversationId, ownerId) == 0) throw problem(404, "CONVERSATION_NOT_FOUND", "Conversation not found");
    }

    private ChatResponse execute(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink) {
        if (clientRequestId == null) throw problem(400, "CLIENT_REQUEST_ID_REQUIRED", "clientRequestId is required");
        if (ownerId == null || ownerId.isBlank()) throw problem(401, "UNAUTHENTICATED", "Authentication is required");
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(message);
        UUID requestId = UUID.randomUUID();
        if (!guard.allowed()) {
            String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
            String blocked = guardMessage(guard.reasonCode(), normalizedLocale);
            emit(sink, new StreamError(guard.reasonCode(), false));
            return new ChatResponse(blocked, MODEL, true, guard.reasonCode(), normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "REJECTED", null, null);
        }
        String normalized = guard.normalizedMessage();
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        if (AssistantInputGuard.isTechnicalRequest(normalized)) {
            emit(sink, new StreamError("TECHNICAL_REQUEST_BLOCKED", false));
            return new ChatResponse(technicalOutputMessage(normalizedLocale), MODEL, true,
                    "TECHNICAL_REQUEST_BLOCKED", normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "REJECTED", null, null);
        }
        UUID requestedConversation = parseConversation(conversationId);
        LexicalResult lexical = retrieve(normalized, normalizedLocale);
        if (lexical.error()) {
            emit(sink, new StreamError("KNOWLEDGE_UNAVAILABLE", true));
            return new ChatResponse(lexical.answer(), MODEL, true, "KNOWLEDGE_UNAVAILABLE", normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "FAILED_PRE_DISPATCH", null, null);
        }
        // The lexical answer is taken from the top-ranked document. Keep the
        // fallback provenance equally precise instead of displaying every
        // retrieved candidate as if it contributed to the visible answer.
        List<Citation> fallbackCitations = lexical.citations().stream().limit(1).toList();
        List<String> fallbackSourceIds = fallbackCitations.stream()
                .map(Citation::sourceId)
                .filter(value -> value != null && !value.isBlank())
                .toList();

        String hash = AssistantInputGuard.canonicalHash(normalized, normalizedLocale, requestedConversation);
        String leaseOwner = "assistant-" + UUID.randomUUID();
        ThesisAssistantTurnRepository.Reservation reservation = cancellations == null
                ? turns.reserve(ownerId, clientRequestId, hash, requestedConversation, normalizedLocale,
                        leaseOwner, properties.retentionDays())
                : turns.reserve(ownerId, clientRequestId, hash, requestedConversation, normalizedLocale,
                        leaseOwner, properties.retentionDays(), this::fenceExpired);
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.REPLAY) {
            ThesisAssistantTurnRepository.ReplayResult replay = turns.replay(reservation.turnId(), ownerId);
            emitReplay(sink, replay, clientRequestId, requestId, normalizedLocale, reservation.turnId());
            return response(replay, clientRequestId, requestId, reservation.turnId(), true, normalizedLocale);
        }
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.ACTIVE) {
            throw problem(409, "TURN_IN_PROGRESS", "A turn with this conversation is already active");
        }
        if (reservation.status() == ThesisAssistantTurnRepository.ReservationStatus.AMBIGUOUS) {
            throw problem(409, "FAILED_AMBIGUOUS", "The provider outcome is ambiguous; automatic redispatch is disabled");
        }
        boolean snapshotReady = cancellations == null
                ? turns.markSnapshotReady(reservation.turnId(), ownerId, reservation.leaseGeneration(), lexical.snapshotHash())
                : turns.markSnapshotReady(reservation.turnId(), ownerId, reservation.leaseGeneration(), lexical.snapshotHash(), this::fenceExpired);
        if (!snapshotReady) {
            throw problem(409, "STALE_LEASE", "Turn lease is no longer current");
        }
        emit(sink, new StreamMeta(requestId, clientRequestId, reservation.turnId(), reservation.conversationId(), MODEL, normalizedLocale));

        boolean providerAttempt = false;
        boolean synthesisRequired = AssistantDifficultyRouter.requiresSynthesis(normalized, lexical.documents());
        String reason = lexical.documents().isEmpty() ? "NO_MATCH"
                : synthesisRequired ? "PROVIDER_DISABLED" : "RAG_GROUNDED";
        boolean degraded = synthesisRequired && !lexical.documents().isEmpty();
        String answer = lexical.answer();
        List<ProviderSegment> emittedSegments = new ArrayList<>();
        StringBuilder providerAnswer = new StringBuilder();
        int[] expectedSequence = { 0 };
        AtomicBoolean cancelToken = cancellations == null ? new AtomicBoolean(false)
                : cancellations.register(ownerId, clientRequestId, reservation.leaseGeneration());
        try {
            if (synthesisRequired && deepSeek != null && deepSeek.usable()) {
                int userDailyQuota = properties.quotaEnforced()
                        ? properties.userDailyQuota() : Integer.MAX_VALUE;
                int globalDailyQuota = properties.quotaEnforced()
                        ? properties.globalDailyQuota() : Integer.MAX_VALUE;
                ThesisAssistantTurnRepository.DispatchDecision dispatch = cancellations == null
                        ? turns.dispatch(reservation.turnId(), ownerId, reservation.leaseGeneration(),
                                userDailyQuota, globalDailyQuota)
                        : turns.dispatch(reservation.turnId(), ownerId, reservation.leaseGeneration(),
                                userDailyQuota, globalDailyQuota, this::fenceExpired);
                if (dispatch.dispatched()) {
                    providerAttempt = true;
                    try {
                        CompletionRequest request = new CompletionRequest(normalized, normalizedLocale, lexical.context(), lexical.sourceIds());
                        var result = provider.complete(request, segment -> {
                            if (cancelToken.get()) throw new CancellationException("assistant request cancelled");
                            Runnable acceptedEmission = () -> {
                                validateSegment(segment, lexical.sourceIds(), expectedSequence[0]);
                                // Inspect the complete prefix, not only each
                                // frame. A provider can fragment an email/phone/
                                // student id across otherwise innocuous SSE
                                // segments. Reject it before the unsafe frame
                                // crosses the stream boundary; any already-
                                // rendered safe prefix is replaced below.
                                String candidate = providerAnswer + segment.text();
                                if (!AssistantInputGuard.inspect(candidate).allowed()
                                        || !AssistantOutputGuard.isSafe(candidate)) {
                                    throw new ProviderOutputRejectedException();
                                }
                                providerAnswer.append(segment.text());
                                expectedSequence[0]++;
                                emittedSegments.add(segment);
                                emit(sink, new StreamDelta(segment.sequence(), segment.text(), segment.sourceIds()));
                            };
                            if (cancellations != null) {
                                if (!cancellations.emitIfActive(ownerId, clientRequestId,
                                        reservation.leaseGeneration(), acceptedEmission)) {
                                    throw new CancellationException("assistant request cancelled");
                                }
                            } else {
                                acceptedEmission.run();
                            }
                        }, cancelToken::get);
                        if (result == null) throw new DeepSeekClient.ProviderUnavailableException("provider returned no result");
                        // Only text that passed the segment/source gate may be
                        // committed. Do not trust a collector's separate answer
                        // field if it diverges from streamed segments.
                        if (emittedSegments.isEmpty()) throw new InvalidSegmentException();
                        String providerText = emittedSegments.stream().map(ProviderSegment::text)
                                .collect(Collectors.joining()).trim();
                        if (providerText.isBlank()) throw new InvalidSegmentException();
                        if ("length".equalsIgnoreCase(result.finishReason())) {
                            // A length stop is a valid upstream response, but it
                            // is not a complete answer. Replace the partial
                            // stream with the deterministic grounded fallback so
                            // the UI never presents truncated prose as final.
                            reason = "PROVIDER_TRUNCATED";
                            degraded = true;
                            answer = lexical.answer();
                            emit(sink, new StreamReplace(answer, fallbackSourceIds, reason));
                        } else {
                            answer = providerText;
                            // Deterministic spacing repair at the provider
                            // boundary. The streamed deltas may carry glued
                            // numbers; emit one replace frame so the rendered
                            // answer matches the committed one.
                            String repaired = normalizeAssistantCopy(answer, normalizedLocale);
                            if (!repaired.equals(answer)) {
                                answer = repaired;
                                emit(sink, new StreamReplace(answer, lexical.sourceIds(), "ANSWERED"));
                            }
                            reason = "ANSWERED";
                            degraded = false;
                        }
                    } catch (CancellationException | DeepSeekClient.ProviderCancelledException cancelled) {
                        throw problem(409, "TURN_CANCELLED", "Turn was cancelled");
                    } catch (DeepSeekClient.ProviderUnavailableException | InvalidSegmentException
                            | ProviderOutputRejectedException providerFailure) {
                        reason = providerFailure instanceof ProviderOutputRejectedException
                                ? "PROVIDER_UNSAFE_OUTPUT" : "PROVIDER_UNAVAILABLE";
                        degraded = true;
                        answer = lexical.answer();
                        emit(sink, new StreamReplace(answer, fallbackSourceIds, reason));
                    }
                } else if ("QUOTA_EXCEEDED".equals(dispatch.reasonCode())) {
                    throw problem(429, "QUOTA_EXCEEDED", "The daily assistant quota has been reached");
                } else if (!"DISPATCHED".equals(dispatch.reasonCode())) {
                    throw problem(409, dispatch.reasonCode(), "The assistant turn lease is no longer current");
                }
            }
            if (!providerAttempt && lexical.documents().isEmpty()) {
                degraded = false;
            }
            // A disabled provider still has a deterministic lexical answer. Emit it as a
            // normal delta so clients can render a useful fallback while retaining the
            // terminal degraded reason in the committed turn.
            if (!providerAttempt && ("PROVIDER_DISABLED".equals(reason) || "RAG_GROUNDED".equals(reason)
                    || "NO_MATCH".equals(reason))) {
                emit(sink, new StreamDelta(0, answer, fallbackSourceIds));
            }
            List<Citation> terminalCitations = "ANSWERED".equals(reason)
                    ? lexical.citations() : fallbackCitations;
            ThesisAssistantTurnRepository.TerminalResult terminal = cancellations == null
                    ? turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason, terminalCitations)
                    : turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason,
                            terminalCitations, this::fenceExpired);
            for (Citation citation : terminal.citations()) emit(sink, new StreamCitation(citation));
            emit(sink, new StreamDone(terminal.messageId(), reason, degraded, terminal.terminalStatus()));
            return new ChatResponse(terminal.answer(), terminal.model(), terminal.degraded(), terminal.reasonCode(), normalizedLocale,
                    terminal.citations(), requestId, clientRequestId, reservation.turnId(), false, terminal.terminalStatus(),
                    terminal.conversationId().toString(), terminal.messageId().toString());
        } catch (DomainException exception) {
            if (isTransientTerminalRace(exception.code())) {
                // A cancel, purge, or lease fence can win after one provider
                // delta crossed the transport boundary. Clear that transient
                // text before the stable error frame; the terminal CAS means
                // no USER/ASSISTANT/citation rows are visible for these paths.
                emit(sink, new StreamReplace("", List.of(), exception.code()));
            }
            throw exception;
        } finally {
            if (cancellations != null) cancellations.remove(ownerId, clientRequestId, reservation.leaseGeneration());
        }
    }

    private void fenceExpired(ThesisAssistantTurnRepository.ExpiredLease lease) {
        if (cancellations != null && lease != null) {
            cancellations.fence(lease.ownerId(), lease.clientRequestId(), lease.leaseGeneration());
        }
    }

    private static boolean isTransientTerminalRace(String code) {
        return "TURN_CANCELLED".equals(code)
                || "TURN_TERMINAL_RACE".equals(code)
                || "TURN_NOT_ACTIVE".equals(code)
                || "FAILED_AMBIGUOUS".equals(code)
                || "PURGED".equals(code);
    }

    private ChatResponse legacyAnswer(String message, String locale, String conversationId, String ownerId) {
        ChatResponse lexical = lexicalAnswer(message, locale);
        if (legacyHistory == null || ownerId == null || ownerId.isBlank() || lexical.degraded()) return lexical;
        String requestedLocale = AssistantInputGuard.normalizeLocale(locale);
        List<Citation> fallbackCitations = primaryCitations(lexical.citations());
        try {
            UUID conversation = legacyHistory.ensureConversation(ownerId, conversationId, requestedLocale,
                    properties == null ? 90 : properties.retentionDays());
            legacyHistory.appendMessage(conversation, "USER", AssistantInputGuard.normalizeMessage(message), MODEL, false, "RECEIVED");
            ChatResponse response = lexical;
            if (!lexical.citations().isEmpty() && provider != null && deepSeek != null && deepSeek.usable()
                    && legacyHistory.consumeQuota(ownerId, properties.userDailyQuota(), properties.globalDailyQuota())) {
                try {
                    String generated = normalizeAssistantCopy(provider.complete(message.trim(), lexical.citations().stream()
                            .map(citation -> citation.title() + "\n" + citation.excerpt())
                            .collect(Collectors.joining("\n\n")), requestedLocale), requestedLocale);
                    if (AssistantOutputGuard.isSafe(generated)) {
                        response = new ChatResponse(generated, deepSeek.model(), false, "ANSWERED", requestedLocale, lexical.citations());
                    } else {
                        response = new ChatResponse(technicalOutputMessage(requestedLocale), MODEL, true,
                                "PROVIDER_UNSAFE_OUTPUT", requestedLocale, fallbackCitations);
                    }
                } catch (DeepSeekClient.ProviderUnavailableException exception) {
                    response = new ChatResponse(lexical.answer(), MODEL, true, "PROVIDER_UNAVAILABLE", requestedLocale, fallbackCitations);
                }
            } else if (!lexical.citations().isEmpty()) {
                response = new ChatResponse(lexical.answer(), MODEL, true,
                        deepSeek == null || !deepSeek.usable() ? "PROVIDER_DISABLED" : "QUOTA_EXCEEDED", requestedLocale, fallbackCitations);
            }
            UUID messageId = legacyHistory.appendMessage(conversation, "ASSISTANT", response.answer(), response.model(), response.degraded(), response.reasonCode());
            legacyHistory.appendCitations(messageId, response.citations());
            return new ChatResponse(response.answer(), response.model(), response.degraded(), response.reasonCode(), response.locale(), response.citations(), conversation.toString(), messageId.toString());
        } catch (DataAccessException exception) {
            return new ChatResponse(lexical.answer(), MODEL, true, "HISTORY_UNAVAILABLE", requestedLocale, fallbackCitations);
        }
    }

    private LexicalResult retrieve(String message, String locale) {
        if (!hasPublicScopeSignal(message)) {
            return noMatchResult(locale);
        }
        List<String> terms = tokenize(message);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try {
            addDocuments(documents, seen, knowledge.search(locale, terms, TOP_K));
            String alternateLocale = DEFAULT_LOCALE.equals(locale) ? "en" : DEFAULT_LOCALE;
            if (documents.size() < TOP_K) addDocuments(documents, seen, knowledge.search(alternateLocale, terms, TOP_K - documents.size()));
        } catch (DataAccessException exception) {
            return new LexicalResult(unavailableMessage(locale), List.of(), List.of(), "", true, true);
        }
        if (catalog != null && documents.size() < TOP_K) {
            try {
                for (ThesisAssistantCatalogRepository.CatalogDocument row : catalog.search(locale, terms, TOP_K - documents.size())) {
                    String sourceId = row.entityType() + ":" + row.entityId();
                    ThesisAssistantKnowledgeRepository.KnowledgeDocument candidate = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                            sourceId, sourceId, locale, row.title(), row.text(), "academic-catalog", row.entityType(), row.entityId(), row.updatedAt() == null ? null : row.updatedAt().toInstant());
                    if (isPublicKnowledgeSafe(candidate) && seen.add(sourceId)) documents.add(candidate);
                }
            } catch (DataAccessException ignored) {
                // Public catalog is an additive adapter. A catalog outage must not
                // discard a valid curated answer or leak a database error to clients.
            }
        }
        documents = documents.stream().filter(document -> containsAnyTerm(document, terms)).limit(TOP_K).toList();
        List<Citation> citations = documents.stream().map(ThesisAssistantService::citation).toList();
        String answer = normalizeAssistantCopy(documents.isEmpty() ? noMatchMessage(locale) : documents.get(0).content(), locale);
        String context = documents.stream()
                .map(d -> "### " + safe(d.title()) + "\n" + safe(d.content()))
                .collect(Collectors.joining("\n\n"));
        if (properties != null && context.length() > properties.maxContextChars()) context = context.substring(0, properties.maxContextChars());
        List<String> sourceIds = citations.stream().map(Citation::sourceId).filter(value -> value != null && !value.isBlank()).toList();
        String snapshotMaterial = documents.stream().map(document -> String.join("|",
                safe(document.id()), safe(document.slug()), safe(document.locale()), safe(document.title()),
                safe(document.content()), safe(document.source()),
                safe(document.domain()),
                document.revisionId() == null ? "" : document.revisionId().toString(),
                document.revisionVersion() == null ? "" : document.revisionVersion().toString(),
                safe(document.catalogEntityType()), safe(document.catalogEntityId()),
                document.catalogUpdatedAt() == null ? "" : document.catalogUpdatedAt().toString(),
                safe(document.corpusVersion()), safe(document.corpusHash()),
                document.releaseId() == null ? "" : document.releaseId().toString()))
                .collect(Collectors.joining("\n"));
        return new LexicalResult(answer, documents, citations, context, false, false, sourceIds, sha256(snapshotMaterial));
    }

    private static LexicalResult noMatchResult(String locale) {
        String answer = noMatchMessage(locale);
        return new LexicalResult(answer, List.of(), List.of(), "", false, false, List.of(), sha256(answer));
    }

    static boolean hasPublicScopeSignal(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = Normalizer.normalize(message, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT);
        return PUBLIC_SCOPE_SIGNAL.matcher(folded).find() || COURSE_CODE.matcher(message).find();
    }

    private ChatResponse lexicalAnswer(String message, String locale) {
        String normalized = AssistantInputGuard.normalizeMessage(message);
        if (normalized.isBlank()) throw new IllegalArgumentException("message is required");
        if (properties != null && normalized.length() > properties.maxMessageChars()) throw new IllegalArgumentException("message must contain at most " + properties.maxMessageChars() + " characters");
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(normalized);
        if (!guard.allowed()) {
            String blocked = guardMessage(guard.reasonCode(), normalizedLocale);
            return new ChatResponse(blocked, MODEL, true, guard.reasonCode(), normalizedLocale, List.of());
        }
        if (AssistantInputGuard.isTechnicalRequest(normalized)) {
            return new ChatResponse(technicalOutputMessage(normalizedLocale), MODEL, true,
                    "TECHNICAL_REQUEST_BLOCKED", normalizedLocale, List.of());
        }
        LexicalResult result = retrieve(normalized, AssistantInputGuard.normalizeLocale(locale));
        if (result.error()) {
            return new ChatResponse(result.answer(), MODEL, true, "KNOWLEDGE_UNAVAILABLE", AssistantInputGuard.normalizeLocale(locale), List.of());
        }
        return new ChatResponse(result.answer(), MODEL, false, result.documents().isEmpty() ? "NO_MATCH" : "ANSWERED", AssistantInputGuard.normalizeLocale(locale), primaryCitations(result.citations()));
    }

    private static Citation citation(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        String sourceId = "academic-catalog".equals(document.source())
                ? document.catalogEntityType() + ":" + document.catalogEntityId() : document.id();
        String hash = sha256(String.join("|", sourceId, document.title(), document.content(), document.source(),
                document.locale(), document.revisionId() == null ? "" : document.revisionId().toString(),
                document.revisionVersion() == null ? "" : document.revisionVersion().toString(),
                document.catalogEntityType() == null ? "" : document.catalogEntityType(),
                document.catalogEntityId() == null ? "" : document.catalogEntityId(),
                document.catalogUpdatedAt() == null ? "" : document.catalogUpdatedAt().toString()));
        Citation citation;
        if ("ACADEMIC_CATALOG".equalsIgnoreCase(document.domain()) || "academic-catalog".equals(document.source())) {
            citation = new Citation(document.id(), document.slug(), document.title(), document.source(), document.locale(), excerpt(document.content()),
                    "ACADEMIC_CATALOG", "CATALOG", sourceId, null, null, hash, document.catalogEntityType(), document.catalogEntityId(),
                    document.catalogUpdatedAt() == null ? null : document.catalogUpdatedAt().toString(),
                    document.corpusVersion(), document.corpusHash(), document.releaseId());
        } else {
            UUID revision = parseUuid(document.id());
            citation = new Citation(document.id(), document.slug(), document.title(), document.source(), document.locale(), excerpt(document.content()),
                    document.domain() == null ? "THESIS" : document.domain(), "CURATED", sourceId, document.revisionId() == null ? revision : document.revisionId(),
                    document.revisionVersion(), hash, null, null, null,
                    document.corpusVersion(), document.corpusHash(), document.releaseId());
        }
        return normalizeCitation(citation, document.locale());
    }

    /** Keep persisted and streamed citation copy readable when source content contains API status names. */
    static Citation normalizeCitation(Citation citation, String locale) {
        if (citation == null) return null;
        String citationLocale = citation.locale() == null ? locale : citation.locale();
        return new Citation(citation.id(), citation.slug(), normalizeAssistantCopy(citation.title(), citationLocale),
                normalizeAssistantCopy(citation.source(), citationLocale), citation.locale(),
                normalizeAssistantCopy(citation.excerpt(), citationLocale), citation.domain(), citation.sourceKind(),
                citation.sourceId(), citation.revisionId(), citation.revisionVersion(), citation.snapshotHash(),
                citation.entityType(), citation.entityId(), citation.updatedAt(), citation.corpusVersion(),
                citation.corpusHash(), citation.releaseId());
    }

    private static void validateSegment(ProviderSegment segment, List<String> allowed, int expectedSequence) {
        if (segment == null || segment.text() == null || segment.text().isBlank()) throw new InvalidSegmentException();
        if (segment.sequence() != expectedSequence) throw new InvalidSegmentException();
        if (segment.sourceIds() == null || segment.sourceIds().isEmpty() || !allowed.containsAll(segment.sourceIds())) throw new InvalidSegmentException();
    }

    private static final class ProviderOutputRejectedException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static void emitReplay(Consumer<StreamEvent> sink, ThesisAssistantTurnRepository.ReplayResult replay, UUID clientRequestId,
            UUID requestId, String locale, UUID turnId) {
        emit(sink, new StreamMeta(requestId, clientRequestId, turnId, replay.conversationId(), replay.model(), locale));
        List<Citation> citations = replay.citations().stream().map(citation -> normalizeCitation(citation, locale)).toList();
        emit(sink, new StreamDelta(0, normalizeAssistantCopy(replay.answer(), locale), citations.stream().map(Citation::sourceId).toList()));
        citations.forEach(citation -> emit(sink, new StreamCitation(citation)));
        emit(sink, new StreamDone(replay.messageId(), replay.reasonCode(), replay.degraded(), replay.terminalStatus()));
    }

    private static void emit(Consumer<StreamEvent> sink, StreamEvent event) { if (sink != null) sink.accept(event); }

    private static ChatResponse response(ThesisAssistantTurnRepository.ReplayResult replay, UUID clientRequestId, UUID requestId,
            UUID turnId, boolean replayed, String locale) {
        List<Citation> citations = replay.citations().stream().map(citation -> normalizeCitation(citation, locale)).toList();
        return new ChatResponse(normalizeAssistantCopy(replay.answer(), locale), replay.model(), replay.degraded(), replay.reasonCode(), locale, citations,
                requestId, clientRequestId, turnId, replayed, replay.terminalStatus(), replay.conversationId().toString(), replay.messageId().toString());
    }

    private static UUID parseConversation(String value) { if (value == null || value.isBlank()) return null; try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { throw problem(400, "INVALID_CONVERSATION_ID", "conversationId must be a UUID"); } }
    private static UUID parseUuid(String value) { try { return value == null ? null : UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; } }
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); } }
    private static String excerpt(String content) { String value = content == null ? "" : content.replaceAll("\\s+", " ").trim(); return value.length() <= 280 ? value : value.substring(0, 277) + "..."; }
    private static boolean containsAnyTerm(ThesisAssistantKnowledgeRepository.KnowledgeDocument document, List<String> terms) { String searchable = (safe(document.title()) + " " + safe(document.content())).toLowerCase(Locale.ROOT); return terms.isEmpty() || terms.stream().anyMatch(searchable::contains); }
    private static void addDocuments(List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> target, Set<String> seen, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> candidates) {
        for (var candidate : candidates) {
            if (isPublicKnowledgeSafe(candidate) && seen.add(candidate.slug())) target.add(candidate);
        }
    }
    private static boolean isPublicKnowledgeSafe(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        return document != null
                && AssistantInputGuard.isPublicKnowledgeSafe(document.slug())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.title())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.content())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.source())
                && AssistantOutputGuard.isSafe(document.title())
                && AssistantOutputGuard.isSafe(document.content())
                && AssistantOutputGuard.isSafe(document.source());
    }
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "do", "does", "for", "how", "i", "is", "it", "of", "on", "or", "the", "to", "what", "when", "where", "why", "with",
            "em", "anh", "chi", "cho", "cua", "de", "la", "lam", "nen", "nhu", "nhung", "gi", "nao", "toi", "va", "ve", "voi",
            "của", "để", "là", "làm", "nên", "như", "những", "gì", "nào", "tôi", "và", "về", "với", "các", "có", "được", "không", "thì", "ra", "sao");
    private static List<String> tokenize(String message) {
        return java.util.Arrays.stream(message.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 2 && !STOP_WORDS.contains(term)).distinct().limit(16).toList();
    }
    private static List<Citation> primaryCitations(List<Citation> citations) {
        return citations == null || citations.isEmpty() ? List.of() : List.of(citations.get(0));
    }
    private static String noMatchMessage(String locale) { return "vi".equals(locale) ? "Mình chưa tìm thấy hướng dẫn phù hợp trong kho kiến thức công khai. Bạn thử nêu rõ học phần, học kỳ hoặc mục học vụ cần hỏi nhé." : "I could not find matching public guidance. Try naming the course, semester, or campus service you need."; }
    private static String unavailableMessage(String locale) { return "vi".equals(locale) ? "Kho kiến thức CampusCore hiện chưa khả dụng. Vui lòng thử lại sau." : "The CampusCore knowledge base is currently unavailable. Please try again later."; }
    private static String sensitiveMessage(String locale) { return "vi".equals(locale) ? "Vui lòng không nhập email, số điện thoại, mã sinh viên hoặc thông tin bí mật vào trợ lý." : "Please do not enter email addresses, phone numbers, student IDs, or secrets into the assistant."; }
    private static String promptInjectionMessage(String locale) { return "vi".equals(locale) ? "Trợ lý chỉ xử lý câu hỏi học vụ công khai và không thể thực hiện yêu cầu thay đổi chỉ dẫn hệ thống." : "The assistant only handles public academic questions and cannot follow requests to change its system instructions."; }
    static String guardMessage(String reasonCode, String locale) {
        if ("PROMPT_INJECTION".equals(reasonCode)) return promptInjectionMessage(locale);
        if ("TECHNICAL_REQUEST_BLOCKED".equals(reasonCode)) return technicalOutputMessage(locale);
        return sensitiveMessage(locale);
    }
    static String technicalOutputMessage(String locale) {
        return "vi".equals(locale)
                ? "Mình chỉ hỗ trợ thông tin học vụ công khai và không thể cung cấp chi tiết kỹ thuật nội bộ. Bạn hãy hỏi về đăng ký học phần, thời khóa biểu, điểm, thông báo hoặc khóa luận nhé."
                : "I can help with public academic information, but I cannot provide internal technical details. Ask about registration, schedules, grades, announcements, or your thesis journey.";
    }
    private static String safe(String value) { return value == null ? "" : value; }
    private static DomainException problem(int status, String code, String message) { return new DomainException(org.springframework.http.HttpStatus.valueOf(status), code, message); }

    public sealed interface StreamEvent permits StreamMeta, StreamDelta, StreamReplace, StreamCitation, StreamDone, StreamError { }
    public record StreamMeta(UUID requestId, UUID clientRequestId, UUID turnId, UUID conversationId, String model, String locale) implements StreamEvent {
        @JsonProperty("type") public String type() { return "meta"; }
    }
    public record StreamDelta(int sequence, String text, List<String> sourceIds) implements StreamEvent {
        @JsonProperty("type") public String type() { return "delta"; }
    }
    public record StreamReplace(String text, List<String> sourceIds, String reasonCode) implements StreamEvent {
        @JsonProperty("type") public String type() { return "replace"; }
    }
    public record StreamCitation(Citation citation) implements StreamEvent {
        @JsonProperty("type") public String type() { return "citation"; }
    }
    public record StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus) implements StreamEvent {
        @JsonProperty("type") public String type() { return "done"; }
    }
    public record StreamError(String code, boolean retryable) implements StreamEvent {
        @JsonProperty("type") public String type() { return "error"; }
    }
    private record LexicalResult(String answer, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents, List<Citation> citations, String context, boolean error, boolean ignored, List<String> sourceIds, String snapshotHash) {
        LexicalResult(String answer, List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents, List<Citation> citations, String context, boolean error, boolean ignored) { this(answer, documents, citations, context, error, ignored, List.of(), ""); }
    }
    private static final class InvalidSegmentException extends RuntimeException { }
}
