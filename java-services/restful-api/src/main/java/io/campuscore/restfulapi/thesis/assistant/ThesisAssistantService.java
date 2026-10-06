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
import java.util.Map;
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
    /** Default retrieval window when no properties are bound (lexical-only constructor). */
    static final int DEFAULT_TOP_K = AssistantProperties.DEFAULT_TOP_K;
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
    /**
     * Grade-table glue "B+:3.5" / "C+:2.5" — the colon sits after a sign, not
     * a letter, so the label rule above misses it. Times ("07:30") keep their
     * digit-before-colon and stay untouched. (Audit kien-thuc Q10.)
     */
    private static final java.util.regex.Pattern NUMBER_GLUE_AFTER_SIGN_COLON = java.util.regex.Pattern.compile(
            "(?<=[A-Za-z][+\\-])(:)(?=\\d)");
    /**
     * Mid-token lowercase→uppercase jump: the provider concatenates Vietnamese
     * word pairs without a space ("dựngRiêng"). The split is restricted to
     * letter runs that carry at least one non-ASCII character: pure camelCase
     * identifiers ("CampusCore", "DeepSeek", "iPhone") are legitimate and must
     * never be broken. Unaccented ASCII joins ("thiSinh") are covered by the
     * heading allowlist rules instead. (Audit kien-thuc Q11;
     * assistant-output-guard.ts mirrors this rule.)
     */
    private static final java.util.regex.Pattern WORD_CAP_JUMP = java.util.regex.Pattern.compile(
            "(?<=[\\p{Ll}])(?=[\\p{Lu}][\\p{Ll}])");

    static String repairCapitalWordGlue(String text) {
        if (text == null || text.isEmpty()) return text;
        java.util.regex.Matcher matcher = WORD_CAP_JUMP.matcher(text);
        StringBuilder out = new StringBuilder();
        int appended = 0;
        while (matcher.find()) {
            int pos = matcher.start();
            int left = pos;
            while (left > 0 && Character.isLetter(text.charAt(left - 1))) left--;
            // The split is decided by the LOWERCASE side of the jump: a
            // Vietnamese glue word carries its own diacritics ("dựngRiêng").
            // A pure-ASCII left side is a camelCase identifier running into
            // the next word ("CampusCoreMở") — splitting there would break the
            // brand token, and the heading allowlist rules cover that join
            // with a block break instead.
            String before = text.substring(left, pos);
            boolean vietnameseBefore = before.chars().anyMatch(ch -> ch > 127);
            if (!vietnameseBefore) continue;
            out.append(text, appended, pos).append(' ');
            appended = pos;
        }
        if (appended == 0) return text;
        out.append(text, appended, text.length());
        return out.toString();
    }
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
                    // Audit kien-thuc Q9 run 2 emitted "# Thủ tục xin hoãn thiSinh viên vắng thi…"
                    + "Thủ tục xin hoãn thi|Thủ tục hoãn thi|Quy định hoãn thi|Chính sách miễn giảm học phí|"
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
     * Stop words for retrieval-term extraction. Vietnamese function words are
     * listed in both accented and folded form because the folded query view is
     * filtered through the same set.
     */
    static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "can", "could", "do", "does", "for", "how", "i", "is", "it", "may", "me", "of", "on", "or", "please", "should", "tell", "the", "to", "what", "when", "where", "why", "with", "would",
            "em", "anh", "chi", "cho", "cua", "de", "la", "lam", "nen", "nhu", "nhung", "gi", "nao", "toi", "va", "ve", "voi",
            "bi", "bị", "da", "đã", "se", "sẽ", "phai", "phải", "mot", "một",
            "của", "để", "là", "làm", "nên", "như", "những", "gì", "nào", "tôi", "và", "về", "với", "các", "có", "được", "không", "thì", "ra", "sao");
    /**
     * Folded-phrase aliases: an unaccented keyboard phrase is expanded into its
     * accented corpus twin before lexical search. This map is ALSO the source
     * of the public-scope pre-gate vocabulary (see {@link #PUBLIC_SCOPE_SIGNAL}),
     * so an alias added for retrieval automatically admits its topic past the
     * gate — the two vocabularies cannot drift apart.
     */
    static final Map<String, String> VIETNAMESE_FOLDED_PHRASE_ALIASES = Map.ofEntries(
            Map.entry("dang ky", "đăng ký"),
            Map.entry("hoc phan", "học phần"),
            Map.entry("lich hoc", "lịch học"),
            Map.entry("thoi khoa bieu", "thời khóa biểu"),
            Map.entry("diem", "điểm"),
            Map.entry("thong bao", "thông báo"),
            Map.entry("khoa luan", "khóa luận"),
            Map.entry("hoc vu", "học vụ"),
            Map.entry("tin chi", "tín chỉ"),
            Map.entry("mon hoc", "môn học"),
            Map.entry("lop hoc phan", "lớp học phần"),
            Map.entry("thuc tap", "thực tập"),
            Map.entry("bao luu", "bảo lưu"),
            Map.entry("bao ve", "bảo vệ"),
            Map.entry("giang vien", "giảng viên"),
            Map.entry("chuong trinh", "chương trình"),
            Map.entry("hoc bong", "học bổng"),
            Map.entry("hoc phi", "học phí"),
            Map.entry("tai khoan", "tài khoản"),
            Map.entry("mat khau", "mật khẩu"),
            Map.entry("thu tuc", "thủ tục"),
            Map.entry("quy dinh", "quy định"),
            Map.entry("dieu kien", "điều kiện"),
            Map.entry("toi da", "tối đa"),
            Map.entry("toi thieu", "tối thiểu"),
            Map.entry("gioi han", "giới hạn"),
            Map.entry("khoi luong", "khối lượng"),
            Map.entry("hoc ky", "học kỳ"),
            Map.entry("nam hoc", "năm học"),
            Map.entry("ky hoc", "kỳ học"),
            Map.entry("thoi gian", "thời gian"),
            Map.entry("ket qua", "kết quả"),
            // Regulation phrases that unaccented keyboards hit most: without
            // these the folded query missed the corpus its accented twin hit.
            Map.entry("hoc lai", "học lại"),
            Map.entry("cai thien", "cải thiện"),
            Map.entry("canh bao", "cảnh báo"),
            Map.entry("tien quyet", "tiên quyết"),
            Map.entry("hoc truoc", "học trước"),
            Map.entry("song hanh", "song hành"),
            Map.entry("tot nghiep", "tốt nghiệp"),
            Map.entry("chuan dau ra", "chuẩn đầu ra"),
            Map.entry("ren luyen", "rèn luyện"),
            Map.entry("xep loai", "xếp loại"),
            Map.entry("do an", "đồ án"),
            Map.entry("de tai", "đề tài"),
            Map.entry("hoi dong", "hội đồng"),
            Map.entry("phan bien", "phản biện"),
            Map.entry("diem so", "điểm số"),
            Map.entry("diem chuan", "điểm chuẩn"),
            Map.entry("hinh thuc", "hình thức"),
            Map.entry("quy che", "quy chế"),
            // Frequent unaccented phrases that previously degraded to noisy
            // per-syllable matches.
            Map.entry("sinh vien", "sinh viên"),
            Map.entry("xet tuyen", "xét tuyển"),
            Map.entry("giay xac nhan", "giấy xác nhận"),
            Map.entry("giay to", "giấy tờ"),
            Map.entry("nghi hoc", "nghỉ học"),
            Map.entry("lam lai", "làm lại"),
            // Round-4 format sweep: unaccented "tu van" folds onto the same
            // advisory phrase (the bare syllables are retrieval noise).
            Map.entry("tu van", "tư vấn"),
            // Backlog sweep: "CSDL" is the acronym students actually type, but
            // the corpus spells the concept out ("Cơ sở dữ liệu quan hệ") — the
            // bare acronym matched nothing and the query fell to NO_MATCH. The
            // folded key also covers the accented phrase and keyboards without
            // diacritics, since folding happens before the lookup.
            Map.entry("csdl", "cơ sở dữ liệu"),
            Map.entry("co so du lieu", "cơ sở dữ liệu"),
            // V95 corpus completion: the new student-policy documents answer
            // these phrases. The folded key doubles as retrieval alias AND
            // public-scope signal (the pre-gate derives from this map), so a
            // "kỷ luật" question reaches its document instead of being refused
            // before the query ran.
            Map.entry("ky luat", "kỷ luật"),
            Map.entry("chung thuc", "chứng thực"),
            Map.entry("chung nhan", "chứng nhận"),
            Map.entry("the sinh vien", "thẻ sinh viên"),
            Map.entry("the thu vien", "thẻ thư viện"),
            Map.entry("trung tam thong bao", "trung tâm thông báo"),
            Map.entry("hoan hoc phi", "hoàn học phí"),
            Map.entry("hoan phi", "hoàn phí"),
            Map.entry("hoc ky phu", "học kỳ phụ"),
            Map.entry("chuyen doi tin chi", "chuyển đổi tín chỉ"),
            Map.entry("chuyen nganh", "chuyển ngành"),
            Map.entry("xep loai tot nghiep", "xếp loại tốt nghiệp"),
            Map.entry("thoi gian dao tao", "thời gian đào tạo"));
    /**
     * Multi-part acronyms that the {@code [^\p{L}\p{N}]+} splitter shreds. The
     * corpus spells the concept "CI/CD", so every folded spelling collapses onto
     * that one canonical retrieval term instead of the pair "ci"/"cd", which only
     * ever match inside unrelated words. Keys are matched on the same folded
     * phrase source the Vietnamese aliases use, so slash and hyphen spellings are
     * equivalent; the list order is the emitted term order.
     */
    static final List<Map.Entry<String, String>> FOLDED_ACRONYM_PHRASES = List.of(
            Map.entry("ci cd", "ci/cd"),
            Map.entry("cicd", "ci/cd"),
            Map.entry("dev ops", "devops"));
    /**
     * Curated campus topic vocabulary for the pre-gate, kept as data so the
     * pattern below can be rebuilt from it.
     */
    static final List<String> PUBLIC_SCOPE_TOPICS = List.of(
            "academic", "campus", "course", "courses", "register", "registered", "registration", "schedule",
            "class", "classes", "grade", "grades", "gpa", "transcript", "credit", "credits", "semester", "term",
            "tuition", "fee", "fees", "announcement", "announcements", "notice", "notices", "thesis", "capstone",
            "topic", "topics", "defen[cs]e", "supervisor", "supervision", "lecturer", "faculty", "department",
            "student", "portal", "curriculum", "prerequisite", "corequisite", "exam", "examination", "scholarship",
            "graduation", "internship", "library", "dormitory", "wifi", "email", "account", "password", "research",
            "appeal", "regrade", "withdrawal", "withdraw", "retake", "conduct", "training", "attendance",
            "classroom", "room", "phan",
            "hoc", "dang\\s+ky", "dang\\s+nhap", "lich", "diem", "bang\\s+diem", "tin\\s+chi", "hoc\\s+ky",
            "hoc\\s+phi", "thong\\s+bao", "luan\\s+van", "do\\s+an", "de\\s+tai", "bao\\s+ve", "giang\\s+vien",
            // "Trường có những ngành nào?" was refused by this gate before the
            // query ran: truong/nganh/chuyen-nganh/dao-tao are core campus
            // vocabulary and the V87 faculties-and-majors document answers them.
            "truong", "nganh", "chuyen\\s+nganh", "nganh\\s+dao\\s+tao", "dao\\s+tao",
            "major", "majors", "school",
            // Bare "cong" once admitted "công thức nấu phở bò" (công thức =
            // recipe): the syllable only means campus vocabulary inside its
            // compounds, so the gate takes the compounds, not the fragment.
            "khoa", "bo\\s+mon", "sinh\\s+vien", "cong\\s+(?:nghe|ty|tac|cu|no)", "chuong\\s+trinh", "tien\\s+quyet", "song\\s+hanh",
            "thi", "hoc\\s+bong", "tot\\s+nghiep", "thuc\\s+tap", "thu\\s+vien", "ky\\s+tuc\\s+xa",
            "tai\\s+khoan", "mat\\s+khau", "nghien\\s+cuu", "phuc\\s+khao", "rut\\s+hoc\\s+phan", "hoc\\s+lai",
            "ren\\s+luyen", "diem\\s+danh", "phong", "giay\\s+xac\\s+nhan",
            // The SPECIALIZED corpus publishes the DevOps/CI-CD document, so the
            // gate has to admit its vocabulary or the document can never be
            // reached: a "CI/CD pipeline" question was refused before the query
            // ever ran.
            "devops", "ci[/+-]cd", "cicd", "ci", "cd", "pipeline", "pipelines",
            "docker", "container", "containers", "kubernetes", "deploy", "deployment",
            "deployments", "runbook",
            // Round-2 sweep static-3: course-name vocabulary missing from the
            // gate — "Nhập môn lập trình là gì?" was hijacked into the general
            // fallback while the SPECIALIZED corpus owns a document for it.
            "lap\\s*trinh", "giai\\s*thuat", "co\\s*so\\s*du\\s*lieu", "csdl",
            "ky\\s*thuat\\s*phan\\s*mem", "he\\s*dieu\\s*hanh", "mang\\s*may\\s*tinh",
            "tri\\s*tue\\s*nhan\\s*tao", "kien\\s*truc\\s*may\\s*tinh", "an\\s*toan\\s*thong\\s*tin",
            "solid", "design\\s*pattern", "oop", "tdd", "unit\\s*test");
    /**
     * Retrieval is deliberately scoped before the database query.  A generic
     * lexical overlap (for example "thời" or "hôm nay") is not evidence that
     * a public academic document answers the question.  The vocabulary is
     * DERIVED, not hand-maintained: it is the union of the curated campus
     * topics above and every folded phrase alias that lexical retrieval can
     * expand. Deriving the gate from the same alias source retrieval uses makes
     * it structurally impossible for the gate to be narrower than the
     * vocabulary it guards — an alias added for retrieval automatically admits
     * its topic past this pre-gate instead of being refused before search.
     */
    private static final java.util.regex.Pattern PUBLIC_SCOPE_SIGNAL = buildPublicScopeSignal();

    /**
     * A folded-alias hit is only evidence of campus scope when the raw message
     * does not carry a conflicting accented reading. Accent folding collapses
     * unrelated words onto academic keys — "đồ ăn" (food) folds to the same
     * "do an" as "đồ án" — so a hit is disqualified when the conflict pattern
     * matches without the rescue (the genuinely accented academic form) also
     * matching. Conflicts written in folded form are checked against the folded
     * view; accent-sensitive conflicts must be written accented and only ever
     * match the raw text.
     */
    private record AmbiguousScopeHit(java.util.regex.Pattern conflict, java.util.regex.Pattern rescue) { }

    static final Map<String, AmbiguousScopeHit> AMBIGUOUS_SCOPE_HITS = Map.of(
            "do an", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("đồ\\s+ăn",
                            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE),
                    java.util.regex.Pattern.compile("đồ\\s+án",
                            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)),
            "ket qua", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("ket\\s+qua\\s+(?:xo\\s+so|so\\s+xo|bong\\s+da|tran\\s+dau)"), null),
            "bao ve", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("bao\\s+ve\\s+(?:moi\\s+truong|dong\\s+vat|thuc\\s+vat|tre\\s+em|thu\\s+cung|suc\\s+khoe)"), null),
            "giay to", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("giay\\s+to\\s+(?:xe\\b|o\\s*to|oto|may\\b|nha|dat|tuy\\s+than|ca\\s+nhan)"), null),
            "canh bao", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("canh\\s+bao\\s+(?:lu\\b|lut|mua|bao\\b|giong|ngap|song\\s+than|thien\\s+tai|chay|dich|dong\\s+dat)"), null),
            "xep loai", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("xep\\s+loai\\s+(?:phim|sach|truyen|game|khach\\s+san|nha\\s+hang|quan\\s+an|san\\s+pham)"), null),
            "de tai", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("de\\s+tai\\s+(?:van\\s+hoc|truyen|phim|tho\\b)"), null),
            // "môi trường" (environment) is not "trường" (school) — the compound
            // is the only common non-campus reading of the bare signal.
            "truong", new AmbiguousScopeHit(
                    java.util.regex.Pattern.compile("moi\\s+truong"), null));

    static java.util.regex.Pattern buildPublicScopeSignal() {
        java.util.LinkedHashSet<String> signals = new java.util.LinkedHashSet<>(PUBLIC_SCOPE_TOPICS);
        VIETNAMESE_FOLDED_PHRASE_ALIASES.keySet().forEach(foldedPhrase ->
                signals.add(foldedPhrase.trim().replaceAll("\\s+", "\\\\s+")));
        String alternation = String.join("|", signals);
        return java.util.regex.Pattern.compile(
                "(?i)(?<![\\p{L}\\p{N}_])(?:" + alternation + ")(?![\\p{L}\\p{N}_])");
    }
    private static final java.util.regex.Pattern REGISTRATION_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:register(?:ed|ing)?|registration|enroll(?:ed|ing|ment)?|enrolment|add\\s*[/-]?\\s*drop)\\b|\\b(?:dang\\s+ky|hoc\\s+phan|mon(?:\\s+hoc)?)\\b)");
    private static final java.util.regex.Pattern COURSE_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:course|courses|class|classes|section|sections|module|modules)\\b|\\b(?:hoc\\s+phan|mon(?:\\s+hoc)?)\\b|\\badd\\s*[/-]?\\s*drop\\b)");
    private static final java.util.regex.Pattern REGISTRATION_TIME_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:deadline|window|period|when|date|dates)\\b|\\b(?:khi\\s+nao|bao\\s+gio|thoi\\s+diem|han\\s+(?:chot|dang\\s+ky)|dot\\s+dang\\s+ky)\\b)");
    private static final java.util.regex.Pattern CREDIT_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:credit|credits)\\b|\\b(?:tin\\s+chi)\\b)");
    private static final java.util.regex.Pattern CREDIT_LIMIT_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:cap|maximum|minimum|limit|limits|quota|workload)\\b|\\b(?:han\\s+muc|toi\\s+(?:da|thieu)|gioi\\s+han|khoi\\s+luong)\\b)");
    /**
     * Certificate-intent vocabulary ("xin giấy xác nhận sinh viên", "enrollment
     * certificate"). It collides with the student-card document — that title
     * packs "xác nhận / thủ tục / sinh viên" and outranked the certificate
     * document on a certificate question (responsive audit F01).
     */
    private static final java.util.regex.Pattern CERTIFICATE_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:certificate|certification|certified\\s+copy|enrollment\\s+(?:verification|certificate|letter)|verification\\s+letter|proof\\s+of\\s+(?:enrollment|enrolment|student\\s+status))\\b"
                    + "|\\b(?:giay\\s+xac\\s+nhan|giay\\s+chung\\s+nhan|chung\\s+thuc|xac\\s+nhan\\s+sinh\\s+vien)\\b)");
    /**
     * A certificate mentioned as supporting paperwork inside another topic does
     * not make the question a certificate request: exam deferral ("giấy xác
     * nhận y tế"), dormitory confirmation, insurance and card reissue all keep
     * their own documents.
     */
    private static final java.util.regex.Pattern CERTIFICATE_TOPIC_VETO = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:thi|du\\s+thi|hoan\\s+thi|vang\\s+thi|thi\\s+bu|ky\\s+thi|giam\\s+thi|phong\\s+thi|exam|exams|midterm|finals?)\\b"
                    + "|\\b(?:ky\\s+tuc\\s+xa|ktx|noi\\s+tru|dormitory|dorm)\\b"
                    + "|\\b(?:bao\\s+hiem|bhyt|insurance)\\b"
                    + "|\\bthe(?:\\s+sinh\\s+vien|\\s+thu\\s+vien)?\\s+(?:bi\\s+)?(?:mat|hong|het\\s+han|cap\\s+lai|lam\\s+lai)\\b"
                    + "|\\b(?:mat|lam\\s+mat|hong|cap\\s+lai|lam\\s+lai)\\s+the\\b"
                    + "|\\b(?:lost|stolen|damaged|reissue|reissued|replace|replacement|expired)\\s+(?:the\\s+)?(?:student\\s+|library\\s+)?card\\b"
                    + "|\\b(?:student|library)\\s+card\\s+(?:is\\s+)?(?:lost|stolen|damaged|expired)\\b"
                    + "|\\bcard\\s+(?:reissue|replacement|renewal)\\b)");
    /** Title-level topic marker a certificate document must carry. */
    private static final java.util.regex.Pattern CERTIFICATE_TITLE = java.util.regex.Pattern.compile(
            "(?i)(?:\\bcertificates?\\b|\\bcertification\\b|\\bgiay\\s+xac\\s+nhan\\b|\\bgiay\\s+chung\\s+nhan\\b|\\bgiay\\s+to\\b|\\bchung\\s+thuc\\b)");
    // F04 multi-intent families: the lexical fast path is single-topic by
    // design, so a question that clearly joins two topic families ("lịch tuần
    // này, và xem điểm ở đâu?") must skip it — the provider composes both
    // parts; a joined two-topic fast-path answer silently drops one intent.
    private static final java.util.regex.Pattern INTENT_SCHEDULE = java.util.regex.Pattern.compile(
            "(?i)\\b(?:lich\\s+hoc|thoi\\s+khoa\\s+bieu|tkb|tiet\\s+hoc|buoi\\s+hoc|lich\\s+trong"
                    + "|co\\s+lich|lich\\s+tuan|lich\\s+hom\\s+nay|lich\\s+ngay\\s+mai"
                    + "|schedule|timetable|class\\s+schedule|classes?\\s+this\\s+week)\\b");
    private static final java.util.regex.Pattern INTENT_EXAM = java.util.regex.Pattern.compile(
            "(?i)\\b(?:lich\\s+thi|ky\\s+thi|thi\\s+cuoi\\s+ky|thi\\s+giua\\s+ky|phong\\s+thi|exam(?:\\s+schedule)?s?|midterm|finals?)\\b");
    private static final java.util.regex.Pattern INTENT_GRADES = java.util.regex.Pattern.compile(
            "(?i)\\b(?:diem(?:\\s+so|\\s+thi|\\s+tong\\s+ket)?|gpa|bang\\s+diem|grades?|scores?|transcript)\\b");
    private static final java.util.regex.Pattern INTENT_FEES = java.util.regex.Pattern.compile(
            "(?i)\\b(?:hoc\\s+phi|tuition|fees?|cong\\s+no|mien\\s+giam\\s+hoc\\s+phi|biet\\s+phi)\\b");
    /**
     * A real joiner — mid-string punctuation or a conjunction — separates the
     * intents. A trailing "?"/"!" separates nothing, so the mark must be
     * followed by more content to count ("lịch gì, và điểm?" joins at the
     * comma; a lone trailing "?" on a one-topic question does not).
     */
    private static final java.util.regex.Pattern MULTI_INTENT_JOINER = java.util.regex.Pattern.compile(
            "(?i)(?:[,;!?](?=\\s*\\S)|\\b(?:va|còn|con|cung|and|also|ngoai\\s+ra|dong\\s+thoi)\\b)");
    /** Section labels let a scoped answer omit adjacent policy material from the same source. */
    private static final java.util.regex.Pattern CREDIT_LIMIT_SECTION = java.util.regex.Pattern.compile(
            "(?iu)\\b(?:giới\\s+hạn\\s+(?:tín\\s+chỉ|khối\\s+lượng)|credit\\s+(?:limits?|workload))\\s*[:\\-]");
    private static final java.util.regex.Pattern NUMBERED_SECTION = java.util.regex.Pattern.compile(
            "(?m)\\s+(?=\\d{1,2}\\.\\s+)");
    private static final java.util.regex.Pattern THESIS_SIGNAL = java.util.regex.Pattern.compile(
            "(?i)(?:\\b(?:thesis|capstone|report|defen[cs]e|council|reviewer)\\b|\\b(?:do\\s+an|khoa\\s+luan|bao\\s+cao|bao\\s+ve|hoi\\s+dong|phan\\s+bien)\\b)");

    static String normalizeNumberSpacing(String text) {
        if (text == null || text.isBlank()) return text;
        String out = NUMBER_GLUE_AFTER_WORD.matcher(text).replaceAll("$1 ");
        out = NUMBER_GLUE_BEFORE_WORD.matcher(out).replaceAll(" $1");
        out = ENGLISH_NUMBER_GLUE_AFTER_WORD.matcher(out).replaceAll("$0 ");
        out = ENGLISH_NUMBER_GLUE_BEFORE_WORD.matcher(out).replaceAll(" $0");
        out = NUMBER_GLUE_AFTER_LABEL_COLON.matcher(out).replaceAll("$1 ");
        out = NUMBER_GLUE_AFTER_SIGN_COLON.matcher(out).replaceAll("$1 ");
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
        // Mid-word lowercase→uppercase jump (audit kien-thuc Q11: the model
        // emits concatenated Vietnamese pairs — "dựngRiêng"). Pure camelCase
        // identifiers are left intact; the ASCII-only join "hoãn thiSinh" is
        // split by the heading allowlist below.
        out = repairCapitalWordGlue(out);
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
                    .replaceAll("(?i)\\bperiod(?:\\s+period)+\\b", "period")
                    // Meta-voice backstop (audit kien-thuc): even with the
                    // prompt prohibition, the model kept referring to its own
                    // information set ("not in the available/provided
                    // information"). Rewrite the known variants to speak as
                    // the portal.
                    .replaceAll("(?i)not (?:available|contained|present|found|listed) (?:in|from) the (?:available|provided|published)(?: academic)? (?:information|data|context|sources)",
                            "not published by the portal")
                    .replaceAll("(?i)\\bin the (?:available|provided)(?: academic)? (?:information|data|sources)\\b",
                            "in the published academic information");
        }
        return out
                .replaceAll("(?iu)chưa có trong thông tin(?: được công bố)?(?: ở đây| tại đây)?",
                        "chưa được công bố")
                .replaceAll("(?iu)không có trong dữ liệu học vụ(?: của bạn)?(?: mà tôi hỗ trợ)?",
                        "chưa được Cổng học vụ công bố")
                .replaceAll("(?iu)(?:dữ liệu học vụ) mà tôi hỗ trợ", "dữ liệu học vụ công khai")
                .replaceAll("(?iu)chưa(?: được)? quy định trong thông tin(?: hiện có| được cung cấp)",
                        "chưa được quy định")
                .replaceAll("(?iu)\\bchưa được nêu\\b", "chưa công bố")
                .replaceAll("(?iu)\\bTôi chỉ có thể giúp(?: về)?", "Mình chỉ hỗ trợ")
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

    /** Retrieval window; clamped 3-10 by {@link AssistantProperties}. */
    private int topK() {
        return properties == null ? DEFAULT_TOP_K : properties.topK();
    }

    /**
     * Documents actually injected into the provider prompt (and shown as
     * citations). Retrieval keeps its wider {@code topK} candidate window for
     * scoping filters, but the model only ever sees the top ranked sources —
     * five citations of prompt material were pure latency for direct lookups.
     */
    static final int PROMPT_DOCUMENT_LIMIT = AssistantProperties.PROMPT_DOCUMENT_LIMIT;

    /** Wall-clock budget for the local lexical fallback when the RAG gateway is down. */
    // Ceiling, not a wait: healthy retrieval answers in well under 100ms. The
    // budget must still cover the degenerate case — with the database just
    // stopped, Hikari spends ~2-3s evicting its dead pooled connections before
    // retrieval can fail, and a 1s ceiling turned that outage into a
    // TimeoutException, masking KNOWLEDGE_UNAVAILABLE behind the curated
    // fallback (compose outage probe regression).
    public static final long LOCAL_FALLBACK_BUDGET_MS = 5_000L;

    /**
     * The instant the student's daily quota resets: next midnight Asia/
     * Ho_Chi_Minh, matching the quota bucket key in AssistantTimezone.
     */
    static java.time.Instant nextQuotaResetAt() {
        return AssistantTimezone.currentBucketDate().plusDays(1)
                .atStartOfDay(AssistantTimezone.ZONE).toInstant();
    }

    /**
     * Local-grounded fallback for the JSON path when the remote RAG gateway
     * fails transiently, times out, or returns NO_MATCH: run the local lexical
     * KB search within a small budget and answer from the top published
     * document WITH citations. This mirrors the PROVIDER_DISABLED local-grounded
     * pattern: degraded=true, the curated-lexical model name marks the source,
     * and the answer is never empty — a failed gateway degrades to the curated
     * fallback text below instead of a 5xx.
     */
    public ChatResponse groundedFallback(String message, String locale) {
        return groundedFallback(message, locale, false);
    }

    public ChatResponse groundedFallback(String message, String locale, boolean dbDownAtRequestStart) {
        return groundedFallback(message, locale, dbDownAtRequestStart, null);
    }

    /**
     * @param dbDownAtRequestStart true when the caller proved the database was
     *        already unavailable when this request began (the account-state
     *        filter recorded a failure after the request started). Layered
     *        timeouts (gateway 15s > local budget 5s > Hikari 15s) mean a
     *        stopped database surfaces here only as a budget timeout long
     *        after the availability cooldown expired — the flag carries the
     *        proof, and the outage contract (KNOWLEDGE_UNAVAILABLE, degraded,
     *        no citations) must win over the curated content fallback.
     * @param scope request scope; "specialized" skips the technical gate in
     *        the lexical path (round-3 chat-8)
     */
    public ChatResponse groundedFallback(String message, String locale, boolean dbDownAtRequestStart, String scope) {
        return groundedFallback(message, locale, dbDownAtRequestStart, scope, false);
    }

    /**
     * @param remoteNoMatch true when the authoritative remote service answered
     *        cleanly and simply had no matching document. In that case a local
     *        miss is an honest "not found" — not an outage — so the copy must
     *        not claim the knowledge base is unreachable.
     */
    public ChatResponse groundedFallback(String message, String locale, boolean dbDownAtRequestStart,
            String scope, boolean remoteNoMatch) {
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        // A stopped database fails retrieval slower than every layered timeout
        // (gateway 15s > local budget 5s > Hikari 15s), so the outage must be
        // recognised BEFORE any retrieval attempt: the tracker already knows.
        if (io.campuscore.restfulapi.security.DatabaseAvailabilityTracker.isRecentlyUnavailable()) {
            return knowledgeUnavailableResponse(locale, null);
        }
        ChatResponse lexical;
        try {
            final String normalized = AssistantInputGuard.normalizeMessage(message);
            // Same propagation contract as the fast path: the knowledge search
            // resolves the caller's identity from the SecurityContext, which a
            // pooled ForkJoin worker does not inherit — without the snapshot
            // the local-KB fallback tier silently degraded to curated content.
            var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
            lexical = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        var snapshot = new org.springframework.security.core.context.SecurityContextImpl(
                                securityContext.getAuthentication());
                        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
                        org.springframework.security.core.context.SecurityContextHolder.setContext(snapshot);
                        try {
                            return lexicalAnswer(normalized, normalizedLocale, scope);
                        } finally {
                            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
                        }
                    })
                    .get(LOCAL_FALLBACK_BUDGET_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return curatedFallback(normalizedLocale);
        } catch (Exception exception) {
            // Budget timeout / rejection: answer anyway, never throw. When the
            // database was already down when the request began, the precise
            // outage contract wins over the curated content fallback.
            if (dbDownAtRequestStart) {
                return knowledgeUnavailableResponse(locale, null);
            }
            return curatedFallback(normalizedLocale);
        }
        if ("ANSWERED".equals(lexical.reasonCode()) && !lexical.citations().isEmpty()) {
            // Local KB answered within budget: keep the top answer with its
            // citations, mark degraded so clients know the remote RAG path was
            // bypassed (existing local-grounded convention).
            return withDegraded(lexical);
        }
        if ("KNOWLEDGE_UNAVAILABLE".equals(lexical.reasonCode())) {
            // The knowledge STORE itself is down (both remote and local read
            // the same database). Relay the precise outage contract instead of
            // masking it as a content miss: the runtime probes pin
            // KNOWLEDGE_UNAVAILABLE + degraded + no citations, and a curated
            // ANSWERED here made a DB outage indistinguishable from "no
            // matching document".
            return withDegraded(lexical);
        }
        if (dbDownAtRequestStart) {
            return knowledgeUnavailableResponse(locale, null);
        }
        return remoteNoMatch ? noMatchFallback(normalizedLocale) : curatedFallback(normalizedLocale);
    }

    /** Model name marking a lexical fast-path answer from the reviewed curated corpus. */
    public static final String FAST_PATH_MODEL = "curated-lexical-fast";

    /** Model name marking a locally-composed conversational reply (greeting, thanks, identity). */
    public static final String CONVERSATIONAL_MODEL = "campuscore-conversational";

    /**
     * Conversational openers ("hi", "Xin chào, bạn có thể giúp gì cho tôi?",
     * "Cảm ơn") are personal-data-free small talk: they must NOT fall through
     * to the knowledge fallback ("Mình chưa tìm thấy hướng dẫn...") that a
     * retrieval miss produces. Resolved here with a fixed persona reply — no
     * retrieval, no provider, effectively instant — and returned with reason
     * code CONVERSATIONAL. The caller runs the full input guard BEFORE this
     * tier, so the text below never sees hostile input.
     *
     * @return a grounded local reply, or {@code null} when the message is a
     *         real academic question that must go down the knowledge path.
     */
    /** Echo-capable form: the JSON path returns the tier answer directly, so
     *  the client's correlation id must survive into the response (audit
     *  ca-nhan Q12 returned clientRequestId=null on a CONVERSATIONAL answer). */
    static ChatResponse conversationalAnswer(String message, String locale) {
        return conversationalAnswer(message, locale, null);
    }

    static ChatResponse conversationalAnswer(String message, String locale, java.util.UUID clientRequestId) {
        if (message == null || message.isBlank()) return null;
        // Wukong round-9 F8: normalize like the other tiers — NFD and
        // invisible-separator input used to skip the openers entirely and
        // fall to the provider path ("cảm\u200Bơn", NFD "xin chào").
        String normalized = AssistantInputGuard.normalizeMessage(message)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[!.,;:?~\\s]+$", "").trim();
        if (normalized.isEmpty()) return null;
        boolean vi = "vi".equals(AssistantInputGuard.normalizeLocale(locale));
        boolean matches = (CONVERSATIONAL_OPENERS_PATTERN.matcher(normalized).find())
                || (CONVERSATIONAL_IDENTITY_PATTERN.matcher(normalized).find())
                || (CONVERSATIONAL_CAPABILITY_PATTERN.matcher(normalized).find())
                || (CONVERSATIONAL_THANKS_PATTERN.matcher(normalized).find())
                || (CONVERSATIONAL_BYE_PATTERN.matcher(normalized).find());
        if (!matches) return null;
        // Coverage check (Wukong round-7 A8): the tier must apply only when
        // the WHOLE message is conversational. "Cảm ơn, lịch học của tôi" and
        // "Thanks, what is my schedule?" open with a thanks token and used to
        // return the boilerplate, hijacking the real question — remove every
        // conversational span plus polite filler and require that nothing but
        // punctuation survives.
        String residue = CONVERSATIONAL_ANY_PATTERN.matcher(normalized).replaceAll(" ");
        residue = CONVERSATIONAL_FILLER.matcher(residue).replaceAll(" ");
        // \p{S} joins the residue strip: "hi 👋" / "chào 😊" are genuine
        // small talk and no emoji is academic content (Wukong round-8 L1).
        if (!residue.replaceAll("[\\s\\p{P}\\p{S}]+", "").isEmpty()) return null;
        String text;
        if (CONVERSATIONAL_THANKS_PATTERN.matcher(normalized).find()) {
            text = vi
                    ? "Không có gì ạ! Nếu bạn cần tra cứu thêm về học phần, lịch học, điểm, điểm rèn luyện hay quy chế học vụ, cứ hỏi mình nhé."
                    : "You're welcome! If you need anything else about sections, timetables, grades, conduct scores, or academic regulations, just ask.";
        } else if (CONVERSATIONAL_BYE_PATTERN.matcher(normalized).find()) {
            text = vi
                    ? "Tạm biệt bạn! Khi nào cần hỗ trợ học vụ, bạn quay lại hỏi mình bất cứ lúc nào nhé."
                    : "Goodbye! Come back any time you need help with academic questions.";
        } else if (CONVERSATIONAL_IDENTITY_PATTERN.matcher(normalized).find()
                || CONVERSATIONAL_CAPABILITY_PATTERN.matcher(normalized).find()) {
            text = vi
                    ? "Mình là trợ lý AI của Cổng học vụ CampusUTE, được cấu hình chuyên sâu cho dữ liệu của trường và của chính tài khoản bạn.\n\n"
                            + "Mình có thể giúp:\n"
                            + "• Tra cứu lớp học phần, giảng viên, phòng học và thời khóa biểu cá nhân.\n"
                            + "• Tính hạn mức đăng ký tín chỉ còn lại của bạn.\n"
                            + "• Tổng hợp điểm, GPA và điểm rèn luyện theo học kỳ.\n"
                            + "• Hướng dẫn quy trình đăng ký đề tài khóa luận và theo dõi nhóm.\n"
                            + "• Giải đáp quy chế, quy định học vụ công khai.\n\n"
                            + "Bạn hỏi bằng tiếng Việt hoặc tiếng Anh đều được nhé."
                    : "I'm the AI assistant of the CampusUTE portal, configured with the university's data and your own account context.\n\n"
                            + "I can help with:\n"
                            + "• Looking up sections, lecturers, rooms and your personal timetable.\n"
                            + "• Calculating your remaining registration credit budget.\n"
                            + "• Summarizing your grades, GPA and conduct score by semester.\n"
                            + "• Guiding you through thesis topic registration and group tracking.\n"
                            + "• Answering public academic regulations.\n\n"
                            + "Ask me in Vietnamese or English.";
        } else {
            text = vi
                    ? "Chào bạn! Mình là trợ lý học vụ của CampusUTE.\n\n"
                            + "Bạn có thể hỏi mình về:\n"
                            + "• Lớp học phần, thời khóa biểu, phòng học của bạn.\n"
                            + "• Điểm số, GPA, điểm rèn luyện.\n"
                            + "• Hạn mức tín chỉ còn được đăng ký.\n"
                            + "• Quy chế, quy trình đăng ký học phần và khóa luận.\n\n"
                            + "Bạn muốn tìm hiểu điều gì trước?"
                    : "Hello! I'm the CampusUTE academic assistant.\n\n"
                            + "You can ask me about:\n"
                            + "• Your sections, timetable and classrooms.\n"
                            + "• Grades, GPA and conduct scores.\n"
                            + "• Your remaining registration credit budget.\n"
                            + "• Regulations and how to register for sections or thesis topics.\n\n"
                            + "What would you like to know first?";
        }
        return new ChatResponse(text, CONVERSATIONAL_MODEL, false, "CONVERSATIONAL",
                AssistantInputGuard.normalizeLocale(locale), List.of(),
                java.util.UUID.randomUUID(), clientRequestId, null, false, "COMPLETED", null, null);
    }

    /**
     * General-knowledge fallback for OFF-TOPIC questions ("Con gà có mấy cái
     * chân"): carries no academic signal, so the campus corpus could never
     * answer it and the old chain ended in the "Mình chưa tìm thấy..." KB
     * miss. The provider now answers it directly with the general prompt —
     * no retrieved context, no citations, no quota charge, never persisted.
     * Academic-signal questions return {@code null} and keep the grounded RAG
     * path; scope='specialized' also returns {@code null} because the
     * professional corpus is the RIGHT source there — the lexical gate cannot
     * see specialist terms like "SOLID" and used to hijack those questions
     * into ungrounded general prose (round-2 sweep chat-1); a provider miss
     * also returns {@code null} so the caller falls back to the normal chain
     * (owner request 2026-09-30).
     */
    public ChatResponse generalAnswerIfOffTopic(String message, String locale, UUID clientRequestId, String scope) {
        return generalAnswerIfOffTopic(message, locale, clientRequestId, scope, null);
    }

    /**
     * @param ownerId the authenticated caller. Off-topic provider calls skip
     *        the turn ledger, so they used to skip the daily quota as well —
     *        an authenticated caller could burn unbounded provider tokens.
     *        The standalone charge shares the same USER/GLOBAL buckets as the
     *        ledger dispatch; when a caller is known and quota is enforced,
     *        the call is paid for or refused.
     */
    public ChatResponse generalAnswerIfOffTopic(String message, String locale, UUID clientRequestId, String scope,
            String ownerId) {
        return generalAnswerIfOffTopic(message, locale, clientRequestId, scope, ownerId, null);
    }

    public ChatResponse generalAnswerIfOffTopic(String message, String locale, UUID clientRequestId, String scope,
            String ownerId, String requestHash) {
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        String normalized = AssistantInputGuard.normalizeMessage(message);
        if (normalized.isBlank()) return null;
        if (hasPublicScopeSignal(normalized)) return null;
        if ("specialized".equalsIgnoreCase(scope)) return null;
        if (deepSeek == null || !deepSeek.usable()) return null;
        // Key consumption/conflict applies whenever the ledger is wired —
        // independent of the quota gate, so quotaEnforced=false does not
        // silently skip the idempotency contract.
        if (turns != null && clientRequestId != null && requestHash != null
                && org.springframework.util.StringUtils.hasText(ownerId)) {
            // Same key with a different payload conflicts exactly like the
            // ledgered paths; an identical replay legitimately computes a
            // fresh answer and pays for the fresh provider call — the same
            // replay semantics the personal-context path documents.
            try {
                enforcePersonalIdempotency(ownerId, clientRequestId, requestHash);
            } catch (DataAccessException ignored) {
                // A degraded ledger read must not 500 the call — the
                // bucket charge below remains the real quota gate.
            }
        }
        java.time.LocalDate chargedDate = null;
        boolean chargeAttempted = false;
        if (turns != null && properties != null && properties.quotaEnforced()
                && org.springframework.util.StringUtils.hasText(ownerId)) {
            try {
                chargedDate = turns.chargeStandaloneQuota(ownerId, properties.userDailyQuota(),
                        properties.globalDailyQuota());
                chargeAttempted = true;
            } catch (DataAccessException exception) {
                // An accounting outage must not block a working provider;
                // the charge is best-effort and the outage is short-lived.
                // chargeAttempted stays false so the request is NOT refused
                // as quota-exceeded — a metering outage is not a denial.
                org.slf4j.LoggerFactory.getLogger(ThesisAssistantService.class)
                        .warn("off-topic quota charge failed with {}", exception.getClass().getSimpleName());
            }
            if (chargeAttempted && chargedDate == null) {
                boolean vi = "vi".equals(normalizedLocale);
                String text = vi
                        ? "Bạn đã đạt hạn mức câu hỏi trong ngày. Hạn mức sẽ được làm mới sau nửa đêm — bạn quay lại vào ngày mai nhé."
                        : "You have reached the daily question limit. The quota resets after midnight — please come back tomorrow.";
                return new ChatResponse(text, MODEL, true, "QUOTA_EXCEEDED", normalizedLocale, List.of(),
                        UUID.randomUUID(), clientRequestId, null, false, "COMPLETED", null, null,
                        nextQuotaResetAt());
            }
        }
        String generated;
        try {
            generated = provider.complete(new DeepSeekClient.CompletionRequest(
                    normalized, normalizedLocale, "", List.of(), DeepSeekClient.generalPrompt(normalizedLocale)),
                    ignored -> { }).answer();
        } catch (DeepSeekClient.ProviderUnavailableException | DeepSeekClient.ProviderCancelledException
                | InvalidSegmentException | ProviderOutputRejectedException exception) {
            // The caller falls through to the ledgered RAG path, which charges
            // its own quota unit — refund the standalone charge so one user
            // request is never billed twice. Only a charge that actually
            // committed is refunded (a failed charge attempt left the buckets
            // untouched) and the refund targets the charge's own bucket date.
            if (chargedDate != null) {
                try {
                    turns.refundStandaloneQuota(ownerId, chargedDate);
                } catch (DataAccessException refundFailure) {
                    // Residual: a metering outage right here leaves the
                    // standalone +1 in place and the ledgered path still
                    // charges its own unit — one over-counted request during
                    // a simultaneous accounting outage is accepted.
                    org.slf4j.LoggerFactory.getLogger(ThesisAssistantService.class)
                            .warn("standalone quota refund failed with {}",
                                    refundFailure.getClass().getSimpleName());
                }
            }
            return null;
        }
        generated = normalizeAssistantCopy(generated, normalizedLocale);
        if (turns != null && clientRequestId != null && requestHash != null
                && org.springframework.util.StringUtils.hasText(ownerId)) {
            try {
                turns.recordStandaloneTurn(ownerId, clientRequestId, requestHash);
            } catch (DataAccessException ignored) {
                // The answer is already computed; a key-consumption outage
                // must not fail it — the next payload on this key just won't
                // conflict.
            }
        }
        if (!AssistantOutputGuard.isSafeForAnswer(generated, normalized)) {
            return new ChatResponse(technicalOutputMessage(normalizedLocale), MODEL, true,
                    "PROVIDER_UNSAFE_OUTPUT", normalizedLocale, List.of());
        }
        // GENERAL_ANSWER, not ANSWERED: the provider replied without any
        // retrieved context, so the response must not claim reviewed-guidance
        // provenance (same honest-labeling class as the curated fallback).
        return new ChatResponse(generated, provider.model(), false, "GENERAL_ANSWER", normalizedLocale, List.of(),
                UUID.randomUUID(), clientRequestId, null, false, "COMPLETED", null, null);
    }

    /**
     * Emits a fully local response (conversational tier) as a complete SSE
     * conversation turn: meta with the response's own model, one replace
     * delta, and a completed done frame. Used by the stream endpoint for
     * answers that never touch retrieval or the provider.
     */
    static void streamLocalResponse(ChatResponse response, UUID clientRequestId, java.util.function.Consumer<StreamEvent> sink) {
        sink.accept(new StreamMeta(UUID.randomUUID(), clientRequestId, null, null,
                response.model(), response.locale()));
        sink.accept(new StreamReplace(response.answer(), List.of(), response.reasonCode()));
        sink.accept(new StreamDone(null, response.reasonCode(), response.degraded(), "COMPLETED"));
    }

    private static java.util.regex.Pattern patternOf(List<String> alternatives) {
        // UNICODE_CHARACTER_CLASS makes \b treat Vietnamese letters as word
        // characters: without it, "hiện" matched \bhi\b because "ệ" is a
        // non-word ASCII boundary — the greeting tier hijacked real questions
        // containing it.
        return java.util.regex.Pattern.compile(String.join("|", alternatives),
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE
                        | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);
    }

    private static final List<String> CONVERSATIONAL_OPENERS = List.of(
            "\\bhi\\b", "\\bhello\\b", "\\bhey\\b", "\\balo\\b", "\\bchao\\b",
            "chào\\b", "xin\\s*chào", "chào\\s*bạn", "chào\\s*bộ\\s*phận",
            "chào\\s*buổi\\s*(?:sáng|trưa|chiều|tối)", "chao\\s*buoi",
            "\\bhalo\\b",
            // "hai" as a greeting must not hijack the weekday word: the audit's
            // "Thứ Hai hàng tuần tôi có môn nào..." opened with the self-intro
            // because \bhai\b matched the "Hai" in "Thứ Hai" (case-insensitive
            // word boundary). The lookbehinds reject "thứ "/"thu " (accented and
            // unaccented) directly before the token; each is fixed width, which
            // Java lookbehind requires.
            "(?<!thứ\\s)(?<!thu\\s)\\bhai\\b",
            "\\bhihi\\b", "\\blol\\b");

    private static final List<String> CONVERSATIONAL_IDENTITY = List.of(
            "bạn\\s*là\\s*(?:ai|cái\\s*gì|gì)", "ban\\s*la\\s*(?:ai|gi)",
            "bạn\\s*là\\s*(?:trợ\\s*lý|ai\\s*vậy|ai\\s*thế)", "\\bwho\\s+are\\s+you\\b",
            "bạn\\s*hoạt\\s*động\\s*(?:như\\s*thế\\s*nào|ra\\s*sao)", "cách\\s*bạn\\s*hoạt\\s*động");

    private static final List<String> CONVERSATIONAL_CAPABILITY = List.of(
            "bạn\\s*(?:có\\s*thể|có\\s*thể\\s*giúp|làm\\s*được)\\s*(?:gì|những\\s*gì|cái\\s*gì|gì\\s*được)",
            "bạn\\s*giúp\\s*(?:gì|được\\s*gì|cái\\s*gì)",
            "(?:giúp|help)\\s*(?:mình|tôi|me|my)\\s*(?:gì|với\\s*gì)",
            "\\bwhat\\s+(?:can|do)\\s+you\\s+(?:help|do)\\b", "bạn\\s*biết\\s*(?:gì|những\\s*gì)");

    private static final List<String> CONVERSATIONAL_THANKS = List.of(
            "cảm\\s*ơn", "cam\\s*on", "\\bcám\\s*ơn\\b", "\\bthanks?\\b", "\\bthank\\s*you\\b",
            "\\bthx\\b", "\\bty\\b", "\\bot çok\\b", "\\bhiểu\\s*rồi\\b", "\\bok\\s*cảm\\s*ơn\\b");

    private static final List<String> CONVERSATIONAL_BYE = List.of(
            // "bai" (bye transliteration) was dropped: it collides with the
            // real academic noun "bài" (assignment/test paper) — "bai cua
            // toi" used to get whole-message coverage via the filler words
            // and return the goodbye boilerplate (Wukong round-8 M6).
            "tạm\\s*biệt", "tam\\s*biet", "\\bbye\\b", "\\bgoodbye\\b", "\\bsee\\s+you\\b",
            "hẹn\\s*gặp\\s*lại", "hen\\s*gap\\s*lai");

    // Precompiled once — conversationalAnswer re-ran patternOf() on every
    // message (~5 Pattern.compile calls per request, audit S8).
    private static final java.util.regex.Pattern CONVERSATIONAL_OPENERS_PATTERN = patternOf(CONVERSATIONAL_OPENERS);
    private static final java.util.regex.Pattern CONVERSATIONAL_IDENTITY_PATTERN = patternOf(CONVERSATIONAL_IDENTITY);
    private static final java.util.regex.Pattern CONVERSATIONAL_CAPABILITY_PATTERN = patternOf(CONVERSATIONAL_CAPABILITY);
    private static final java.util.regex.Pattern CONVERSATIONAL_THANKS_PATTERN = patternOf(CONVERSATIONAL_THANKS);
    private static final java.util.regex.Pattern CONVERSATIONAL_BYE_PATTERN = patternOf(CONVERSATIONAL_BYE);

    /** Every conversational alternative in one pattern — the coverage check strips all of them at once. */
    private static final java.util.regex.Pattern CONVERSATIONAL_ANY_PATTERN = patternOf(
            java.util.stream.Stream.of(CONVERSATIONAL_OPENERS, CONVERSATIONAL_IDENTITY,
                            CONVERSATIONAL_CAPABILITY, CONVERSATIONAL_THANKS, CONVERSATIONAL_BYE)
                    .flatMap(List::stream).toList());

    /**
     * Politeness/vocative padding that may surround a conversational token
     * without making the message a real question ("cảm ơn bạn nhé", "ok
     * cảm ơn", "Bạn là ai vậy?"). Deliberately excludes every content word —
     * anything left after stripping makes the message a real query, not
     * small talk.
     */
    private static final java.util.regex.Pattern CONVERSATIONAL_FILLER = java.util.regex.Pattern.compile(
            "\\b(?:bạn|ban|mình|minh|tôi|toi|em|anh|chị|chi|cô|thầy|ad|admin|bot|bác|bac"
                    + "|ơi|oi|ạ|a|nhé|nhe|nha|nhỉ|nhi|nhiều|nhieu|lắm|lam|rồi|roi|đi|vậy|vay"
                    + "|thôi|luôn|đấy|đó|hén|hen|được|duoc|cho|của|cua|nữa|nua|vui|lòng|long|mến|men"
                    + "|ok|okay|okie|pls|please|me|my|you|your|dear|friend|friends|sis|bro|guys"
                    // Wukong round-9 F9: Vietnamese intensifiers — "cảm ơn
                    // rất nhiều / quá / vô cùng" left residue and missed the
                    // tier.
                    + "|rất|rat|quá|qua|vô\\s*cùng|vo\\s*cung"
                    + "|with|so|very|much|lot|lots|for|help)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE
                    | java.util.regex.Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Lexical-first fast path (chatbot latency): run the same local retrieval the
     * fallback chain uses, and when the top document is a CONFIDENT match, answer
     * from the reviewed curated corpus immediately instead of paying the remote
     * RAG → DeepSeek round-trip (6.9–9.2 s measured; this path answers in well
     * under a second).
     *
     * <p>Confidence is the retrieval engine's own score: the repository ranks
     * candidates with a per-term expression (title substring 3, content substring
     * 1, title whole-word 4, content whole-word 2) and now surfaces that score.
     * A top document at or above {@code assistant.lexical-confident-score} means
     * the question's retrieval phrases hit the document's title and content —
     * the seeded campus topics clear it; incidental single-term overlaps do not.
     *
     * <p>The gate is deliberately placed AFTER every structural pre-check: the
     * public-scope signal, the registration/credit-limit scoping and the
     * sensitive/injection input guard all run inside {@link #retrieve}, so an
     * off-topic, policy-scoped-out or guarded question degrades to an empty or
     * low-scoring window and returns {@code null} — the caller then escalates to
     * remote RAG exactly as before.
     *
     * @return a non-degraded ANSWERED response carrying real citations and the
     *         {@link #FAST_PATH_MODEL} model name, or {@code null} when the
     *         caller must proceed with the existing RAG-first chain.
     */
    public ChatResponse lexicalFastPath(String message, String locale, String scope) {
        if (properties == null || !properties.lexicalFastPath()) return null;
        String normalized = AssistantInputGuard.normalizeMessage(message);
        if (normalized.isBlank()) return null;
        // The remote gateway enforces its own limit; an oversized message is
        // that path's contract, not the local retrieval's.
        if (normalized.length() > properties.maxMessageChars()) return null;
        // F04: a joined two-topic question needs composition — escalate to the
        // provider instead of answering whichever family scores higher.
        if (isMultiIntentQuery(normalized)) return null;
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        LexicalResult result;
        // The retrieval runs off the servlet thread, but the knowledge search
        // sits behind an RLS boundary that resolves the caller's identity from
        // the SecurityContext. ForkJoin workers are pooled and do not inherit
        // it, so the fast path used to succeed or fail depending on which
        // worker happened to pick the task (course-code questions that widened
        // the window were the reproducible victims). Carry the caller's
        // context into the task explicitly and clear it afterwards.
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            result = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> {
                        // Install a snapshot, not the caller's live instance: on
                        // a slow retrieval the servlet thread may swap or clear
                        // its own context while this worker is still reading.
                        var snapshot = new org.springframework.security.core.context.SecurityContextImpl(
                                securityContext.getAuthentication());
                        var previous = org.springframework.security.core.context.SecurityContextHolder.getContext();
                        org.springframework.security.core.context.SecurityContextHolder.setContext(snapshot);
                        try {
                            return retrieve(normalized, normalizedLocale, scope);
                        } finally {
                            org.springframework.security.core.context.SecurityContextHolder.setContext(previous);
                        }
                    })
                    .get(LOCAL_FALLBACK_BUDGET_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception exception) {
            // Timeout, rejection, or an unexpected retrieval failure: a fast
            // path must never turn into an error, only into an escalation.
            return null;
        }
        if (result.error() || result.documents().isEmpty()) return null;
        ThesisAssistantKnowledgeRepository.KnowledgeDocument top = result.documents().get(0);
        // A per-course prerequisite question with the generated map on top is
        // answered deterministically regardless of the score gate: the map is
        // machine-generated from the requirement table, and the provider path
        // cannot be trusted with it — the map exceeds the context budget and
        // truncation silently dropped the requested course's section.
        if (isPrerequisiteQuery(normalized) && isPrerequisiteMapDocument(top)) {
            String section = prerequisiteAnswer(normalized, top);
            if (section != null) {
                return new ChatResponse(normalizeAssistantCopy(section, normalizedLocale), FAST_PATH_MODEL,
                        false, "ANSWERED", normalizedLocale, primaryCitations(result.citations()));
            }
        }
        // Aggregate score alone cannot separate a topic hit from generic
        // morpheme collisions: "công thức nấu phở bò" reached 13 on the IT
        // services document through "công" (10) + "thức" (3 inside "chính
        // thức"). A confident match therefore needs term corroboration —
        // see hasTermCorroboration for the exact rule.
        if (top.lexicalScore() < properties.lexicalConfidentScore()
                || !hasTermCorroboration(top, retrievalTerms(normalized))) return null;
        return new ChatResponse(result.answer(), FAST_PATH_MODEL, false, "ANSWERED",
                normalizedLocale, primaryCitations(result.citations()));
    }

    /**
     * Corroboration gate for the curated fast path and the degraded lexical
     * fallback: the top document must be backed by REAL term overlap, not one
     * stray morpheme. A term is "strong" when it contributes >= 6 of the
     * per-term maximum 10 — i.e. it matched as a whole word, not inside
     * somebody else's compound.
     *
     * Corroborated when EITHER:
     *   - two distinct terms are strong (genuine multi-term topic questions
     *     like "đăng ký học phần thế nào" clear this trivially), OR
     *   - a single strong term is intrinsically specific: a multi-word alias
     *     phrase ("đăng ký"), a canonical acronym ("ci/cd"), or a >= 7-char
     *     word ("dormitory", "registration") — none of which can be an
     *     incidental compound fragment the way "công" was inside "Công nghệ".
     *
     * Short bare syllables ("công", "hướng", "trường") can only corroborate
     * in pairs — measured against the live corpus this kills every observed
     * false positive ("công thức nấu phở" -> IT doc, "xem phim" -> study
     * duration doc) while genuine campus queries keep the fast path.
     * Mirrors {@link ThesisAssistantKnowledgeRepository#termContribution} so
     * the gate cannot drift from the retrieval engine.
     */
    private static boolean hasTermCorroboration(
            ThesisAssistantKnowledgeRepository.KnowledgeDocument document, List<String> terms) {
        if (document == null || terms == null) return false;
        int strong = 0;
        for (String term : terms.stream().filter(term -> term.length() >= 2).distinct().limit(16).toList()) {
            if (ThesisAssistantKnowledgeRepository.termContribution(
                    document.title(), document.content(), term) >= 6) {
                strong++;
                if (term.indexOf(' ') >= 0 || term.indexOf('/') >= 0 || term.length() >= 7) {
                    return true;
                }
            }
        }
        return strong >= 2;
    }

    private static ChatResponse withDegraded(ChatResponse response) {
        return new ChatResponse(response.answer(), response.model(), true, response.reasonCode(),
                response.locale(), response.citations(), response.requestId(), response.clientRequestId(),
                response.turnId(), response.replayed(), response.terminalStatus(), response.conversationId(),
                response.messageId(), response.resetAt());
    }

    /**
     * Deterministic Vietnamese/English fallback answer used when even the local
     * KB has nothing: a brief apology, three portal pointers, and the Phòng Đào
     * tạo contact. Never empty, never a 5xx.
     */
    static ChatResponse curatedFallback(String locale) {
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        boolean vi = "vi".equals(normalizedLocale);
        String text = vi
                ? "Mình rất tiếc, trợ lý chưa kết nối được kho kiến thức để tra cứu câu hỏi này lúc này. "
                        + "Bạn có thể thử lại sau ít phút, hoặc:\n\n"
                        + "• Xem quy chế và hướng dẫn học vụ ở mục Cẩm nang sinh viên.\n"
                        + "• Tra cứu lớp học phần và thời khóa biểu ở trang Đăng ký học phần / Thời khóa biểu.\n"
                        + "• Theo dõi các thông báo mới nhất ở trang Thông báo.\n\n"
                        + "Nếu vẫn chưa được giải quyết, bạn hãy liên hệ Phòng Đào tạo để được hỗ trợ nhé."
                : "Sorry, the assistant could not reach the knowledge base for this question right now. "
                        + "Please try again in a few minutes, or:\n\n"
                        + "• Read academic regulations in the Student Handbook section.\n"
                        + "• Check sections and timetables on the Course Registration / Schedule pages.\n"
                        + "• Follow the latest updates on the Announcements page.\n\n"
                        + "If the issue persists, please contact the Academic Affairs Office.";
        return new ChatResponse(text, MODEL, true, "NO_MATCH", normalizedLocale, List.of());
    }

    /**
     * Honest "no answer in the corpus" reply for the remote-healthy path: the
     * authoritative service was reachable and simply had no matching document,
     * so the copy must not claim an outage. degraded=false — nothing failed.
     */
    static ChatResponse noMatchFallback(String locale) {
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        boolean vi = "vi".equals(normalizedLocale);
        String text = vi
                ? "Mình chưa tìm thấy thông tin phù hợp cho câu hỏi này trong tài liệu học vụ hiện có. Bạn có thể:\n\n"
                        + "• Diễn đạt lại câu hỏi với từ khóa cụ thể hơn (ví dụ: “điều kiện tốt nghiệp”, “hạn nộp đề cương”).\n"
                        + "• Xem quy chế và hướng dẫn học vụ ở mục Cẩm nang sinh viên.\n"
                        + "• Liên hệ Phòng Đào tạo nếu cần hỗ trợ trực tiếp."
                : "I could not find an answer to that question in the available academic documents. You can:\n\n"
                        + "• Rephrase the question with more specific keywords.\n"
                        + "• Browse regulations and guides in the Student Handbook section.\n"
                        + "• Contact the Academic Affairs Office for direct support.";
        return new ChatResponse(text, MODEL, false, "NO_MATCH", normalizedLocale, List.of());
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
        return answer(message, locale, conversationId, ownerId, clientRequestId, streamSink, null);
    }

    /** Scoped JSON entry point without a stream sink. */
    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, String scope) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, ignored -> { }, scope);
    }

    /**
     * Full entry point with an optional retrieval scope. {@code scope} only
     * narrows the curated corpus (see ThesisAssistantKnowledgeRepository#search);
     * null keeps the default academic behaviour.
     */
    public ChatResponse answer(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> streamSink, String scope) {
        if (turns == null || properties == null) return legacyAnswer(message, locale, conversationId, ownerId);
        return execute(message, locale, conversationId, ownerId, clientRequestId,
                streamSink == null ? ignored -> { } : streamSink, scope);
    }

    public ChatResponse stream(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, sink);
    }

    public ChatResponse stream(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink, String scope) {
        return answer(message, locale, conversationId, ownerId, clientRequestId, sink, scope);
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
        return conversationPage(ownerId, limit, cursor, null);
    }

    public ThesisAssistantRepository.ConversationPage conversationPage(String ownerId, Integer limit, String cursor, String scope) {
        if (legacyHistory == null) return new ThesisAssistantRepository.ConversationPage(List.of(), null);
        return legacyHistory.conversations(ownerId, limit == null ? 20 : limit, cursor, scope);
    }

    public String createConversation(String ownerId, String locale) {
        return createConversation(ownerId, locale, null);
    }

    public String createConversation(String ownerId, String locale, String scope) {
        if (legacyHistory == null || properties == null) throw problem(503, "ASSISTANT_UNAVAILABLE", "Assistant persistence is unavailable");
        UUID id = legacyHistory.ensureConversation(ownerId, null, AssistantInputGuard.normalizeLocale(locale), properties.retentionDays());
        // V105: an explicitly created specialized conversation must be stamped
        // now — reserve() only stamps rows it creates itself.
        if ("specialized".equalsIgnoreCase(scope) && turns != null) {
            turns.stampConversationScope(id, scope);
        }
        return id.toString();
    }

    /**
     * Round-3 chat-7: records both sides of an intercepted PERSONAL_CONTEXT
     * turn so it shows up in /conversations/{id}/messages like every KB turn.
     * The conversation must exist and belong to the caller — requireOwnedConversation
     * throws 404 CONVERSATION_NOT_FOUND otherwise, matching the KB path's
     * behavior for a deleted conversationId. Returns the conversation id for
     * the response echo, or null when history persistence is not configured.
     */
    /**
     * Round-3 cb3-10: ledger parity for the personal-context path. A reused
     * clientRequestId with a DIFFERENT payload conflicts exactly like the KB
     * path's reserve (409 IDEMPOTENCY_CONFLICT); an exact replay falls through
     * and gets a freshly computed answer — for personal data that is the
     * correct replay semantics (the numbers may legitimately have changed).
     * Silent no-op when the ledger is not configured (legacy/test profiles).
     */
    public void enforcePersonalIdempotency(String ownerId, UUID clientRequestId, String canonicalHash) {
        if (turns == null || clientRequestId == null) return;
        try {
            String existing = turns.requestHashOf(ownerId, clientRequestId);
            if (existing != null && !existing.equals(canonicalHash)) {
                throw problem(409, "IDEMPOTENCY_CONFLICT", "Request key was already used with a different payload");
            }
        } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            // First use of this key — nothing to conflict with.
        }
    }

    /**
     * Result of {@link #recordPersonalTurn}: the conversation the turn landed
     * in plus the persisted assistant message id so responses can stamp both —
     * the stream's done frame needs messageId for feedback to bind.
     */
    public record PersonalTurn(String conversationId, String messageId) { }

    /**
     * Runs under the assistant transaction so a mid-write failure rolls both
     * message rows back instead of persisting a user question with no answer
     * while the response reports null ids.
     */
    @org.springframework.transaction.annotation.Transactional(
            transactionManager = AssistantDatabaseConfiguration.TRANSACTION_MANAGER)
    public PersonalTurn recordPersonalTurn(String ownerId, String conversationId, String message,
            String answer, String locale, String reasonCode) {
        if (legacyHistory == null) return null;
        // A provided conversationId must exist and belong to the caller (KB-path
        // 404 parity); a conversation-less personal turn is still answered —
        // only its history has nowhere to land.
        UUID conversation = parseConversation(conversationId);
        if (conversation != null) {
            legacyHistory.requireOwnedConversation(conversation, ownerId);
        }
        String model = "campuscore-personal-context";
        UUID assistantMessage = null;
        if (conversation != null) {
            legacyHistory.appendMessage(conversation, "USER",
                    AssistantInputGuard.normalizeMessage(message), model, false, "RECEIVED");
            assistantMessage = legacyHistory.appendMessage(conversation, "ASSISTANT", answer, model, false,
                    reasonCode == null || reasonCode.isBlank() ? "PERSONAL_CONTEXT" : reasonCode);
        }
        return conversation == null ? null
                : new PersonalTurn(conversation.toString(),
                        assistantMessage == null ? null : assistantMessage.toString());
    }

    /**
     * Consume the idempotency key for a personal-context turn — deliberately
     * separate from {@link #recordPersonalTurn} so a ledger failure cannot
     * poison the message transaction (a REQUIRED join marks it rollback-only
     * even when the caller catches the exception).
     */
    public void recordPersonalTurnKey(String ownerId, UUID clientRequestId, String requestHash,
            PersonalTurn turn) {
        if (turns == null || clientRequestId == null || requestHash == null) return;
        UUID conversation = turn == null ? null : parseConversation(turn.conversationId());
        UUID assistantMessage = turn == null ? null : parseConversation(turn.messageId());
        try {
            // Round-3 cb3-10: consume the idempotency key like every KB turn
            // so a personal-vs-personal key reuse conflicts instead of
            // silently passing — including the conversation-less turns.
            turns.recordCompletedPersonalTurn(ownerId, clientRequestId, requestHash,
                    conversation, assistantMessage);
        } catch (DataAccessException ignored) {
            // The turn is already persisted and returned; a ledger outage
            // must not fail it — the key just stays unconsumed.
        }
    }

    public List<ThesisAssistantRepository.Message> messages(UUID conversationId, String ownerId) {
        if (legacyHistory == null) return List.of();
        return legacyHistory.messages(conversationId, ownerId);
    }

    public ThesisAssistantRepository.MessagePage messagePage(UUID conversationId, String ownerId, Integer limit, String cursor) {
        if (legacyHistory == null) return new ThesisAssistantRepository.MessagePage(List.of(), null);
        // The page query itself silently scopes to the owner, which used to
        // make a foreign conversation read answer 200 [] while DELETE on the
        // same id answered 404. Assert ownership first so both verbs agree.
        legacyHistory.requireOwnedConversation(conversationId, ownerId);
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
        return execute(message, locale, conversationId, ownerId, clientRequestId, sink, null);
    }

    private ChatResponse execute(String message, String locale, String conversationId, String ownerId,
            UUID clientRequestId, Consumer<StreamEvent> sink, String scope) {
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
        UUID requestedConversation = parseConversation(conversationId);
        // V105: the corpus is pinned to the conversation, not the request. A
        // conversation created under scope='specialized' keeps retrieving from
        // the specialized corpus even when a surface (e.g. the academic panel
        // history) resumes it without repeating the scope.
        String storedScope = requestedConversation != null && legacyHistory != null
                ? legacyHistory.conversationScope(requestedConversation, ownerId)
                : null;
        String effectiveScope = storedScope != null && !storedScope.isBlank() ? storedScope : scope;
        // Round-3 chat-8: same scope rule as the controller — the specialized
        // scope serves the curated DevOps/REST corpus, so its requests must
        // not be re-blocked here after the controller already passed them.
        if (!"specialized".equalsIgnoreCase(effectiveScope) && AssistantInputGuard.isTechnicalRequest(normalized)) {
            emit(sink, new StreamError("TECHNICAL_REQUEST_BLOCKED", false));
            return new ChatResponse(technicalOutputMessage(normalizedLocale), MODEL, true,
                    "TECHNICAL_REQUEST_BLOCKED", normalizedLocale,
                    List.of(), requestId, clientRequestId, null, false, "REJECTED", null, null);
        }
        LexicalResult lexical = retrieve(normalized, normalizedLocale, effectiveScope);
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
        if (reservation.createdConversation() && turns != null) {
            turns.stampConversationScope(reservation.conversationId(), scope);
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
        java.time.Instant quotaResetAt = null;
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
                                // The OUTPUT-side inspection waives institutional
                                // *.edu.vn addresses, which the curated corpus
                                // itself contains and an answer may legitimately
                                // quote; arbitrary addresses and every other
                                // sensitive class still reject.
                                String candidate = providerAnswer + segment.text();
                                if (!AssistantInputGuard.inspectProviderOutput(candidate).allowed()
                                        // The retrieved context is the approved
                                        // corpus, so a command or endpoint quoted
                                        // from it is a citation; only invented
                                        // content is rejected.
                                        || !AssistantOutputGuard.isSafeForAnswer(candidate, lexical.context())) {
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
                    // Quota exhaustion must not throw away the grounded answer
                    // that was already retrieved. The turn stays SNAPSHOT_READY
                    // (the repository no longer downgrades it), so the lexical
                    // answer is completed with the quota-degraded reason instead
                    // of hard-failing with a 429: a deterministic, cited,
                    // corpus-grounded answer is strictly better than an error,
                    // and the degraded flag is the honest signal.
                    reason = "QUOTA_EXCEEDED";
                    degraded = true;
                    answer = lexical.answer();
                    quotaResetAt = nextQuotaResetAt();
                } else if (!"DISPATCHED".equals(dispatch.reasonCode())) {
                    throw problem(409, dispatch.reasonCode(), "The assistant turn lease is no longer current");
                }
            }
            if (!providerAttempt && lexical.documents().isEmpty()) {
                degraded = false;
            }
            // A disabled provider or an exhausted quota still has a deterministic
            // lexical answer. Emit it as a normal delta so clients can render a
            // useful fallback while retaining the terminal degraded reason in
            // the committed turn.
            if (!providerAttempt && ("PROVIDER_DISABLED".equals(reason) || "RAG_GROUNDED".equals(reason)
                    || "NO_MATCH".equals(reason) || "QUOTA_EXCEEDED".equals(reason))) {
                emit(sink, new StreamDelta(0, answer, fallbackSourceIds));
            }
            // A portal-denial answer ("Cổng học vụ chưa công bố …") means the
            // provider explicitly said the retrieval did not cover the topic —
            // attaching those documents as citations would fabricate grounding
            // the answer itself disclaims (live audit: "CI/CD pipeline là gì"
            // cited the prerequisite-map catalog row under a not-published
            // answer).
            List<Citation> terminalCitations = "ANSWERED".equals(reason)
                    ? (isPortalDenialAnswer(answer) ? List.of() : lexical.citations())
                    : fallbackCitations;
            ThesisAssistantTurnRepository.TerminalResult terminal = cancellations == null
                    ? turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason, terminalCitations)
                    : turns.complete(reservation.turnId(), ownerId, reservation.leaseGeneration(), normalized,
                            reason.equals("ANSWERED") ? deepSeek.model() : MODEL, answer, degraded, reason,
                            terminalCitations, this::fenceExpired);
            for (Citation citation : terminal.citations()) emit(sink, new StreamCitation(citation));
            emit(sink, new StreamDone(terminal.messageId(), reason, degraded, terminal.terminalStatus(),
                    "QUOTA_EXCEEDED".equals(reason) ? quotaResetAt : null));
            return new ChatResponse(terminal.answer(), terminal.model(), terminal.degraded(), terminal.reasonCode(), normalizedLocale,
                    terminal.citations(), requestId, clientRequestId, reservation.turnId(), false, terminal.terminalStatus(),
                    terminal.conversationId().toString(), terminal.messageId().toString(),
                    "QUOTA_EXCEEDED".equals(reason) ? quotaResetAt : null);
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
                    if (AssistantOutputGuard.isSafeForAnswer(generated, lexical.citations().stream()
                            .map(citation -> citation.title() + "\n" + citation.excerpt())
                            .collect(Collectors.joining("\n\n")))) {
                        response = new ChatResponse(generated, deepSeek.model(), false, "ANSWERED", requestedLocale,
                                isPortalDenialAnswer(generated) ? List.of() : lexical.citations());
                    } else {
                        response = new ChatResponse(technicalOutputMessage(requestedLocale), MODEL, true,
                                "PROVIDER_UNSAFE_OUTPUT", requestedLocale, fallbackCitations);
                    }
                } catch (DeepSeekClient.ProviderUnavailableException exception) {
                    response = new ChatResponse(lexical.answer(), MODEL, true, "PROVIDER_UNAVAILABLE", requestedLocale, fallbackCitations);
                }
            } else if (!lexical.citations().isEmpty()) {
                String degradedReason = deepSeek == null || !deepSeek.usable()
                        ? "PROVIDER_DISABLED" : "QUOTA_EXCEEDED";
                response = new ChatResponse(lexical.answer(), MODEL, true,
                        degradedReason, requestedLocale, fallbackCitations);
                if ("QUOTA_EXCEEDED".equals(degradedReason)) {
                    response = response.withResetAt(nextQuotaResetAt());
                }
            }
            UUID messageId = legacyHistory.appendMessage(conversation, "ASSISTANT", response.answer(), response.model(), response.degraded(), response.reasonCode());
            legacyHistory.appendCitations(messageId, response.citations());
            return new ChatResponse(response.answer(), response.model(), response.degraded(), response.reasonCode(), response.locale(), response.citations(), conversation.toString(), messageId.toString())
                    .withResetAt(response.resetAt());
        } catch (DataAccessException exception) {
            return new ChatResponse(lexical.answer(), MODEL, true, "HISTORY_UNAVAILABLE", requestedLocale, fallbackCitations);
        }
    }

    private LexicalResult retrieve(String message, String locale) {
        return retrieve(message, locale, null);
    }

    private LexicalResult retrieve(String message, String locale, String scope) {
        boolean specializedScope = "specialized".equalsIgnoreCase(scope);
        // The academic signal gate keeps off-topic questions out of the broad
        // campus corpus. The specialized corpus is a narrow, curated
        // professional set, so there the retrieval result itself is the filter:
        // a question that matches nothing yields the honest NO_MATCH answer.
        if (!specializedScope && !hasPublicScopeSignal(message)) {
            return noMatchResult(locale);
        }
        List<String> terms = retrievalTerms(message);
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        // Scoped intents need a wider candidate window before their semantic
        // filter runs. A noisy top-five window could otherwise hide the one
        // authoritative credit-limit document behind broad "học kỳ" matches.
        int topK = topK();
        int retrievalLimit = isCreditLimitQuery(message) || isPrerequisiteQuery(message)
                || isCertificateQuery(message) ? topK * 2 : topK;
        try {
            // Unscoped retrieval keeps the historical three-argument call so the
            // published contract (and its tests) stays byte-identical; only the
            // specialized scope opts into the domain-filtered overload.
            addDocuments(documents, seen, specializedScope
                    ? knowledge.search(locale, terms, retrievalLimit, scope)
                    : knowledge.search(locale, terms, retrievalLimit));
            String alternateLocale = DEFAULT_LOCALE.equals(locale) ? "en" : DEFAULT_LOCALE;
            if (documents.size() < topK) {
                addDocuments(documents, seen, specializedScope
                        ? knowledge.search(alternateLocale, terms, topK - documents.size(), scope)
                        : knowledge.search(alternateLocale, terms, topK - documents.size()));
            }
        } catch (DataAccessException exception) {
            return new LexicalResult(unavailableMessage(locale), List.of(), List.of(), "", true, true);
        }
        // The academic catalog adapter is campus-scoped; specialized retrieval
        // must never dilute a professional answer with course-catalog rows.
        if (!specializedScope && catalog != null && documents.size() < topK) {
            try {
                for (ThesisAssistantCatalogRepository.CatalogDocument row : catalog.search(locale, terms, topK - documents.size())) {
                    String sourceId = row.entityType() + ":" + row.entityId();
                    ThesisAssistantKnowledgeRepository.KnowledgeDocument candidate = new ThesisAssistantKnowledgeRepository.KnowledgeDocument(
                            sourceId, sourceId, locale, row.title(), row.text(), "academic-catalog", row.entityType(), row.entityId(), row.updatedAt() == null ? null : row.updatedAt().toInstant());
                    if (isPublicKnowledgeSafe(candidate) && seen.add(sourceId)) documents.add(candidate);
                }
            } catch (DataAccessException | DomainException ignored) {
                // Public catalog is an additive adapter. A catalog outage must not
                // discard a valid curated answer or leak a database error to clients.
                // DomainException: the catalog search resolves the RLS identity, and
                // the async fast-path thread carries no SecurityContext — that used
                // to blow up the whole lexical window for course-code questions
                // (the only ones that fell below the window and entered the adapter).
            }
        }
        // Prerequisite questions carry noisy short tokens ("có", "môn"), so the
        // map may rank past the default window; keep the widened candidate set
        // for them exactly like the search limit above does. The wrapper list
        // must stay MUTABLE: the prerequisite code-lookup adds re-searched
        // documents into this list downstream, and Stream.toList() yields an
        // immutable one (UnsupportedOperationException at addDocuments).
        documents = new ArrayList<>(documents.stream().filter(document -> containsAnyTerm(document, terms))
                .limit(isPrerequisiteQuery(message) || isCertificateQuery(message) ? topK * 2 : topK).toList());
        // A joined two-family question ("đăng ký học phần, và xin giấy xác
        // nhận") needs grounding from BOTH sides; scoping to one family strips
        // the other half's citations, while skipping scoping entirely lets an
        // off-topic document win the ranking. Union the detected families that
        // have a document predicate; questions joining only predicate-less
        // families keep the unfiltered window.
        boolean multiIntent = isMultiIntentQuery(message);
        if (multiIntent) {
            // Per-family representation: each DETECTED family contributes its
            // best-ranked document ahead of the rest, so a joined question
            // ("phúc khảo điểm thế nào, và lịch thi cuối kỳ khi nào?") grounds
            // BOTH halves inside PROMPT_DOCUMENT_LIMIT. The old union dropped
            // every non-matching document and had no predicate for the
            // exam/grades/fees families, so the second intent could be
            // crowded out of the prompt window entirely. Families without a
            // predicate keep their retrieval share through the backfill.
            String folded = foldForMatching(message);
            List<java.util.function.Predicate<ThesisAssistantKnowledgeRepository.KnowledgeDocument>> familyPredicates =
                    new ArrayList<>();
            if (isCertificateQuery(message)) familyPredicates.add(ThesisAssistantService::isCertificateDocument);
            if (isCourseRegistrationQuery(message)) familyPredicates.add(
                    document -> "REGISTRATION".equalsIgnoreCase(safe(document.domain())));
            if (isCreditLimitQuery(message)) familyPredicates.add(ThesisAssistantService::isCreditLimitDocument);
            if (isPrerequisiteQuery(message)) familyPredicates.add(ThesisAssistantService::isPrerequisiteMapDocument);
            if (INTENT_EXAM.matcher(folded).find()) familyPredicates.add(ThesisAssistantService::isExamDocument);
            if (INTENT_GRADES.matcher(folded).find()) familyPredicates.add(ThesisAssistantService::isGradesDocument);
            if (INTENT_FEES.matcher(folded).find()) familyPredicates.add(ThesisAssistantService::isFeesDocument);
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> ordered = new ArrayList<>();
            for (java.util.function.Predicate<ThesisAssistantKnowledgeRepository.KnowledgeDocument> predicate
                    : familyPredicates) {
                documents.stream().filter(predicate).findFirst().ifPresent(document -> {
                    if (!ordered.contains(document)) ordered.add(document);
                });
            }
            if (!ordered.isEmpty()) {
                for (ThesisAssistantKnowledgeRepository.KnowledgeDocument document : documents) {
                    if (!ordered.contains(document)) ordered.add(document);
                }
                documents = ordered;
            }
        } else if (isCreditLimitQuery(message)) {
            documents = documents.stream()
                    .filter(ThesisAssistantService::isCreditLimitDocument)
                    .toList();
        } else if (isPrerequisiteQuery(message)) {
            // A per-course prerequisite question must be answered from the
            // generated map, not from whichever policy document also says
            // "tiên quyết" in its title. Fail open: without the map in the
            // window (fresh DB, missing migration) the policy doc keeps
            // answering the generic rule.
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> prerequisiteDocuments = documents.stream()
                    .filter(ThesisAssistantService::isPrerequisiteMapDocument)
                    .toList();
            if (prerequisiteDocuments.isEmpty()) {
                // Deterministic code lookup: generic ranking noise (short
                // tokens such as "có"/"môn") can bury the map behind broader
                // documents, and only the map carries the requested course
                // code, so a code-only re-search usually surfaces it. The
                // addDocuments seen-set skips a map that the first pass
                // already fetched but the window cut — behavior then degrades
                // honestly to the policy answer instead of a wrong citation.
                String courseCode = firstCourseCode(message);
                if (courseCode != null) {
                    try {
                        addDocuments(documents, seen, knowledge.search(locale,
                                List.of(foldForMatching(courseCode)), 3));
                    } catch (DataAccessException ignored) {
                        // The window stays as-is; the caller degrades honestly.
                    }
                    prerequisiteDocuments = documents.stream()
                            .filter(ThesisAssistantService::isPrerequisiteMapDocument)
                            .toList();
                }
                // A prerequisite question WITHOUT a code ("môn nào có tiên
                // quyết?") matches the policy/map vocabulary but ranks badly
                // against the specialized corpus on that page — ground it in
                // the map + policy documents instead of the curated fallback.
                if (courseCode == null) {
                    try {
                        addDocuments(documents, seen, knowledge.search(locale,
                                List.of("tiên quyết", "prerequisite"), 4));
                    } catch (DataAccessException ignored) {
                        // Same honest degradation as above.
                    }
                    prerequisiteDocuments = documents.stream()
                            .filter(d -> isPrerequisiteMapDocument(d)
                                    || (d.slug() != null
                                            && d.slug().startsWith("prerequisite-prior-corequisite")))
                            .toList();
                    if (!prerequisiteDocuments.isEmpty()) {
                        documents = prerequisiteDocuments;
                    }
                }
            }
            if (!prerequisiteDocuments.isEmpty()) {
                documents = prerequisiteDocuments;
            }
        } else if (isCertificateQuery(message)) {
            // F01: "xin giấy xác nhận sinh viên" lost to the card-reissue
            // document whose title happens to pack "xác nhận / thủ tục /
            // sinh viên". A certificate question must cite the certificate
            // document (title-level topic), or honestly report NO_MATCH —
            // never answer the wrong document.
            documents = documents.stream()
                    .filter(ThesisAssistantService::isCertificateDocument)
                    .toList();
        } else if (isCourseRegistrationQuery(message)) {
            List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> registrationDocuments = documents.stream()
                    .filter(document -> "REGISTRATION".equalsIgnoreCase(safe(document.domain())))
                    .toList();
            // If the scoped search did not find a registration document, fail
            // closed with no citation rather than displaying policy documents
            // that happen to mention a deadline, classes, or credit counts.
            documents = registrationDocuments;
        }
        // The model prompt only carries the top ranked documents: five cited
        // sources were pure prompt weight for direct lookups. Retrieval keeps
        // its wider candidate window above; this trims what is injected.
        List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> promptDocuments = documents.stream()
                .limit(PROMPT_DOCUMENT_LIMIT).toList();
        List<Citation> citations = promptDocuments.stream().map(ThesisAssistantService::citation).toList();
        String answer = normalizeAssistantCopy(documents.isEmpty() ? noMatchMessage(locale)
                : lexicalAnswerFor(message, documents.get(0)), locale);
        String context = buildGroundedContext(promptDocuments,
                properties == null ? Integer.MAX_VALUE : properties.maxContextChars());
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

    /**
     * Builds the provider context under a character budget without slicing a
     * document mid-word. Whole documents are accumulated while they fit; the
     * first document that would overflow the remaining budget is dropped, not
     * truncated. A single document longer than the whole budget is cut at the
     * last sentence or paragraph boundary inside the limit — and if no such
     * boundary exists the document is omitted entirely, because a half-sentence
     * must never reach the model as a "complete" source.
     */
    static String buildGroundedContext(List<ThesisAssistantKnowledgeRepository.KnowledgeDocument> documents,
            int maxContextChars) {
        if (maxContextChars <= 0) return "";
        StringBuilder context = new StringBuilder();
        for (ThesisAssistantKnowledgeRepository.KnowledgeDocument document : documents) {
            String block = "### " + safe(document.title()) + "\n" + safe(document.content());
            if (context.length() == 0) {
                String fitted = truncateAtBoundary(block, maxContextChars);
                if (fitted.isBlank()) continue;
                context.append(fitted);
                continue;
            }
            if (context.length() + 2 + block.length() > maxContextChars) break;
            context.append("\n\n").append(block);
        }
        return context.toString();
    }

    /** A sentence terminator followed by whitespace, or a paragraph break. */
    private static final java.util.regex.Pattern SENTENCE_BOUNDARY =
            java.util.regex.Pattern.compile("[.!?](?=\\s)|\\R");

    static String truncateAtBoundary(String text, int limit) {
        if (text == null) return "";
        if (limit <= 0) return "";
        if (text.length() <= limit) return text;
        String cut = text.substring(0, limit);
        java.util.regex.Matcher matcher = SENTENCE_BOUNDARY.matcher(cut);
        int end = -1;
        while (matcher.find()) end = matcher.end();
        if (end <= 0) return "";
        return cut.substring(0, end).stripTrailing();
    }

    static boolean hasPublicScopeSignal(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = foldForMatching(message);
        java.util.regex.Matcher scope = PUBLIC_SCOPE_SIGNAL.matcher(folded);
        while (scope.find()) {
            String hit = scope.group().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            if (!isAmbiguousScopeHit(hit, folded, message)) return true;
        }
        return COURSE_CODE.matcher(message).find();
    }

    /**
     * True when the matched scope signal is a known ambiguous folded key and the
     * message carries the conflicting non-academic reading without the accented
     * academic rescue — e.g. "đồ ăn gì hôm nay" hits "do an" but is a food
     * question, while "đồ ăn và đồ án" is still admitted by the rescue. An
     * unaccented query cannot disambiguate and keeps the benefit of the doubt.
     */
    static boolean isAmbiguousScopeHit(String foldedHit, String foldedSource, String rawSource) {
        AmbiguousScopeHit ambiguity = AMBIGUOUS_SCOPE_HITS.get(foldedHit);
        if (ambiguity == null) return false;
        boolean conflict = ambiguity.conflict().matcher(foldedSource).find()
                || ambiguity.conflict().matcher(rawSource).find();
        return conflict
                && (ambiguity.rescue() == null || !ambiguity.rescue().matcher(rawSource).find());
    }

    /**
     * Identifies the public course-registration topic so unrelated documents
     * cannot become citations merely because they share broad words such as
     * "deadline" or "classes". Thesis registration keeps its own domain.
     */
    static boolean isCourseRegistrationQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = foldForMatching(message);
        if (THESIS_SIGNAL.matcher(folded).find() && !COURSE_SIGNAL.matcher(folded).find()) return false;
        boolean registration = REGISTRATION_SIGNAL.matcher(folded).find();
        return registration && (COURSE_SIGNAL.matcher(folded).find() || REGISTRATION_TIME_SIGNAL.matcher(folded).find());
    }
    static boolean isCreditLimitQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = foldForMatching(message);
        return CREDIT_SIGNAL.matcher(folded).find() && CREDIT_LIMIT_SIGNAL.matcher(folded).find();
    }

    /**
     * A request for a student certificate/verification document. Card-loss
     * phrasing is excluded — "mất thẻ sinh viên thì xin giấy xác nhận ở đâu"
     * still asks about the card procedure, and the exam/deferral documents own
     * any question where the certificate is supporting paperwork ("giấy xác
     * nhận y tế kèm đơn hoãn thi"). Everything else about certificates must
     * cite the certificate document, never the card one.
     */
    static boolean isCertificateQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = foldForMatching(message);
        return CERTIFICATE_SIGNAL.matcher(folded).find()
                && !CERTIFICATE_TOPIC_VETO.matcher(folded).find();
    }

    /**
     * The certificate topic must be visible in the document title, not just
     * mentioned incidentally — the card-reissue document quotes "giấy xác
     * nhận" inside its procedure text but is ABOUT the card. An empty filtered
     * window degrades to NO_MATCH rather than a wrong-topic citation.
     */
    static boolean isCertificateDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        if (document == null) return false;
        return CERTIFICATE_TITLE.matcher(foldForMatching(safe(document.title()))).find();
    }

    /** Exam-family KB documents: the V87 exam schedule plus the exam rules. */
    static boolean isExamDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        if (document == null) return false;
        String slug = safe(document.slug());
        return slug.startsWith("campus-exam-schedule") || slug.startsWith("exam-regulations")
                || slug.startsWith("exam-deferral") || slug.startsWith("lich-thi-");
    }

    /** Grades-family KB documents, including the phúc khảo/appeals procedure. */
    static boolean isGradesDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        if (document == null) return false;
        String slug = safe(document.slug());
        return slug.startsWith("grades-transcript") || slug.startsWith("grade-f-retake")
                || slug.startsWith("gpa-letter-conversion") || slug.startsWith("academic-appeals")
                || slug.startsWith("curriculum-gpa-weighting");
    }

    /** Fees-family KB documents (tuition/công nợ policy). */
    static boolean isFeesDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        return document != null && safe(document.slug()).startsWith("campus-tuition");
    }

    /**
     * Two distinct topic families joined by a real connector ("tuần này có
     * lịch gì, và xem điểm ở đâu?"). Family counting alone stays precise —
     * "điểm và GPA" is one family, "mở bảng điểm xem điểm" is one family —
     * and the joiner requirement keeps single-topic questions like "lịch học
     * kỳ này" from being treated as compound.
     */
    static boolean isMultiIntentQuery(String message) {
        if (message == null || message.isBlank()) return false;
        String folded = foldForMatching(message);
        int families = 0;
        if (INTENT_SCHEDULE.matcher(folded).find()) families++;
        if (INTENT_EXAM.matcher(folded).find()) families++;
        if (INTENT_GRADES.matcher(folded).find()) families++;
        if (INTENT_FEES.matcher(folded).find()) families++;
        if (isCourseRegistrationQuery(message)) families++;
        if (isCertificateQuery(message)) families++;
        // Kongming C4: the credit-limit/prerequisite predicates must be
        // reachable as detectors too — "SE101 tiên quyết gì, và học phí bao
        // nhiêu?" previously counted one family and dropped the fees half.
        if (isCreditLimitQuery(message)) families++;
        if (isPrerequisiteQuery(message)) families++;
        if (families < 2) return false;
        return MULTI_INTENT_JOINER.matcher(folded).find();
    }

    /**
     * A per-course prerequisite question names a course code and asks about its
     * "tiên quyết" chain ("SE421 có môn tiên quyết gì?"). The code term alone
     * contributes at most a couple of retrieval points, so without this scoped
     * selection the generic policy document — whose title repeats the phrase —
     * outranks the generated map and the answer degrades to "chưa công bố".
     */
    static boolean isPrerequisiteQuery(String message) {
        if (message == null || message.isBlank()) return false;
        if (!COURSE_CODE.matcher(message).find()) return false;
        String folded = foldForMatching(message);
        return folded.contains("tien quyet")
                || folded.contains("mon tien quyet")
                || folded.contains("hoc phan tien quyet")
                || folded.contains("prerequisite");
    }

    static boolean isPrerequisiteMapDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        return document != null && "campuscore-prerequisite-map".equals(document.source());
    }

    /** First course-code token in the message (SE421, AI401, ...), or null. */
    static String firstCourseCode(String message) {
        if (message == null) return null;
        java.util.regex.Matcher code = COURSE_CODE.matcher(message);
        return code.find() ? code.group() : null;
    }

    /**
     * Answers a per-course prerequisite question from the generated map's own
     * section for the named course: deterministic, provider-free, and always
     * in sync with the registration table the map was generated from. Returns
     * null when the map has no section for the requested code so the caller
     * falls back to the full document.
     */
    static String prerequisiteAnswer(String message,
            ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        java.util.regex.Matcher code = COURSE_CODE.matcher(message);
        if (!code.find()) return null;
        String requested = code.group().toUpperCase(java.util.Locale.ROOT);
        String content = safe(document.content());
        java.util.regex.Matcher section = java.util.regex.Pattern
                .compile("(?m)^- \\*\\*" + java.util.regex.Pattern.quote(requested) + "\\*\\*")
                .matcher(content);
        if (!section.find()) {
            // The map is generated from the full requirement table, so a
            // missing section means the course is a chain head — state that
            // honestly instead of falling back to the whole map.
            return "vi".equals(document.locale())
                    ? "Theo bảng ràng buộc đăng ký hiện hành, học phần " + requested
                            + " **không có học phần tiên quyết** — bạn có thể đăng ký trực tiếp khi lớp mở."
                    : "Under the current registration requirements, " + requested
                            + " **has no prerequisite** — you can register it directly when a section opens.";
        }
        int end = content.length();
        java.util.regex.Matcher next = java.util.regex.Pattern
                .compile("(?m)^- \\*\\*|^## ")
                .matcher(content);
        next.region(section.end(), content.length());
        if (next.find()) end = next.start();
        String body = content.substring(section.start(), end).strip();
        if ("vi".equals(document.locale())) {
            return "Học phần tiên quyết của **" + requested + "** (theo bảng ràng buộc đăng ký):\n\n"
                    + body
                    + "\n\nBạn phải hoàn thành và đạt học phần tiên quyết (không tính F/W) trước khi được đăng ký "
                    + requested + ".";
        }
        return "Prerequisites of **" + requested + "** (from the registration requirements):\n\n"
                + body
                + "\n\nYou must complete the prerequisite with a passing grade (F/W do not count) before registering "
                + requested + ".";
    }

    /** Picks the per-course map section when the question names a course. */
    private static String lexicalAnswerFor(String message,
            ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        if (isPrerequisiteQuery(message) && isPrerequisiteMapDocument(document)) {
            String section = prerequisiteAnswer(message, document);
            if (section != null) return section;
        }
        return answerFromDocument(message, document);
    }

    /**
     * The academic system prompt instructs the model to answer "Cổng học vụ
     * chưa công bố …" / "the portal has not published …" when the retrieved
     * context does not cover the topic. That honest denial must not carry the
     * retrieval citations — they would present documents the answer disclaims
     * as its sources. Folding first so the marker is recognized with or
     * without diacritics.
     */
    static boolean isPortalDenialAnswer(String answer) {
        if (answer == null || answer.isBlank()) return false;
        String folded = foldForMatching(answer);
        return folded.contains("chua cong bo")
                || folded.contains("chua duoc cong bo")
                || folded.contains("has not published")
                || folded.contains("not published by the portal");
    }

    /**
     * Keep a credit-limit answer grounded in a document that actually defines
     * the limit. Other academic documents often mention credits or a minimum
     * threshold incidentally (for example thesis eligibility), which is not
     * enough to present them as sources for a capacity question.
     */
    static boolean isCreditLimitDocument(ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        if (document == null) return false;
        String folded = foldForMatching(safe(document.title()) + " " + safe(document.content()));
        boolean hasCreditTerm = folded.contains("tin chi") || folded.matches(".*\\bcredits?\\b.*");
        boolean hasLimitTopic = folded.contains("han muc")
                || folded.contains("gioi han")
                || folded.contains("khoi luong")
                || folded.contains("credit cap")
                || folded.contains("credit limit")
                || (folded.contains("toi da") && folded.contains("toi thieu"))
                || (folded.contains("maximum") && folded.contains("minimum"));
        return hasCreditTerm && hasLimitTopic;
    }

    /**
     * Keep scoped credit-limit answers concise while preserving the exact facts
     * from the cited document. If the source has no explicit section marker,
     * fall back to the full source instead of guessing which sentence is safe.
     */
    private static String answerFromDocument(String message,
            ThesisAssistantKnowledgeRepository.KnowledgeDocument document) {
        String content = safe(document.content()).trim();
        if (isCreditLimitQuery(message) && !content.isBlank()) {
            java.util.regex.Matcher section = CREDIT_LIMIT_SECTION.matcher(content);
            if (section.find()) {
                int end = content.length();
                java.util.regex.Matcher nextSection = NUMBERED_SECTION.matcher(content);
                nextSection.region(section.end(), content.length());
                if (nextSection.find()) end = nextSection.start();
                String scoped = content.substring(section.end(), end).trim();
                if (!scoped.isBlank()) {
                    String heading = "en".equalsIgnoreCase(safe(document.locale()))
                            ? "Credit limits" : "Giới hạn tín chỉ";
                    return "**" + heading + "**\n\n" + scoped;
                }
            }
        }
        // Round-4 format sweep: degraded answers (QUOTA_EXCEEDED, provider
        // truncated/unavailable) serve the top document verbatim. Give the raw
        // content the same structure the LLM pathway produces — a title
        // heading and one bullet per numbered point — so the panel renders a
        // readable answer instead of one inline "1. … 2. …" paragraph.
        return formatDocumentAnswer(document, content);
    }

    /**
     * Deterministic markdown shaping for a document answer: a {@code # }
     * heading from the document title, then each top-level {@code N. }
     * segment as a {@code - } bullet with its lead label bolded. Facts are
     * never rewritten — only separators are added. Content that already
     * carries markdown headings, or has no numbered segments, passes through.
     */
    static String formatDocumentAnswer(ThesisAssistantKnowledgeRepository.KnowledgeDocument document, String content) {
        if (content == null || content.isBlank()) return content == null ? "" : content;
        if (content.contains("\n#") || content.startsWith("# ")) return content;
        java.util.List<Integer> marks = new java.util.ArrayList<>();
        int cursor = 0;
        int expected = 1;
        while (expected <= 9) {
            // The first segment may open the content directly ("1. …") with no
            // leading space to anchor the generic marker.
            int at = expected == 1 && content.startsWith("1. ")
                    ? 0
                    : content.indexOf(" " + expected + ". ", cursor);
            if (at < 0) break;
            int textStart = at + Integer.toString(expected).length() + 2;
            marks.add(textStart);
            cursor = textStart;
            expected += 1;
        }
        if (marks.isEmpty()) return content;
        String title = safe(document == null ? null : document.title()).trim();
        StringBuilder shaped = new StringBuilder();
        if (!title.isEmpty()) {
            shaped.append("# ").append(title).append("\n\n");
        }
        for (int index = 0; index < marks.size(); index++) {
            int start = marks.get(index);
            int end = index + 1 < marks.size() ? marks.get(index + 1) - 1 : content.length();
            String segment = content.substring(start, end).trim();
            if (segment.isEmpty()) continue;
            shaped.append("- ").append(formatSegmentLabel(segment)).append('\n');
        }
        return shaped.toString().stripTrailing();
    }

    /** "Chuẩn hóa: 1NF (giá trị nguyên tử)…" → "**Chuẩn hóa**: 1NF …"; a long or colon-less lead stays plain. */
    private static String formatSegmentLabel(String segment) {
        int colon = segment.indexOf(": ");
        if (colon <= 0 || colon > 60) return segment;
        String label = segment.substring(0, colon).trim();
        if (label.isEmpty() || label.contains(";") || label.contains("(")) return segment;
        return "**" + label + "**: " + segment.substring(colon + 2).trim();
    }

    private ChatResponse lexicalAnswer(String message, String locale) {
        return lexicalAnswer(message, locale, null);
    }

    private ChatResponse lexicalAnswer(String message, String locale, String scope) {
        String normalized = AssistantInputGuard.normalizeMessage(message);
        if (normalized.isBlank()) throw new IllegalArgumentException("message is required");
        if (properties != null && normalized.length() > properties.maxMessageChars()) throw new IllegalArgumentException("message must contain at most " + properties.maxMessageChars() + " characters");
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        AssistantInputGuard.GuardResult guard = AssistantInputGuard.inspect(normalized);
        if (!guard.allowed()) {
            String blocked = guardMessage(guard.reasonCode(), normalizedLocale);
            return new ChatResponse(blocked, MODEL, true, guard.reasonCode(), normalizedLocale, List.of());
        }
        // Round-3 chat-8: parity with the controller and execute() gates — the
        // specialized scope answers from the curated DevOps/REST corpus.
        if (!"specialized".equalsIgnoreCase(scope) && AssistantInputGuard.isTechnicalRequest(normalized)) {
            return new ChatResponse(technicalOutputMessage(normalizedLocale), MODEL, true,
                    "TECHNICAL_REQUEST_BLOCKED", normalizedLocale, List.of());
        }
        // Round-4 latent fix: the scope was only consulted for the technical
        // gate and then dropped — every specialized question reaching this
        // path (groundedFallback, legacyAnswer) searched the DEFAULT corpus,
        // which excludes domain SPECIALIZED, so the curated professional set
        // was silently unreachable here. Pass the scope through like the fast
        // path does.
        LexicalResult result = retrieve(normalized, AssistantInputGuard.normalizeLocale(locale), scope);
        if (result.error()) {
            return new ChatResponse(result.answer(), MODEL, true, "KNOWLEDGE_UNAVAILABLE", AssistantInputGuard.normalizeLocale(locale), List.of());
        }
        if (!result.documents().isEmpty()) {
            ThesisAssistantKnowledgeRepository.KnowledgeDocument top = result.documents().get(0);
            // Catalog rows carry no lexical score (structured authoritative
            // records — the catalog search already verified term overlap), so
            // the corroboration gate applies to curated-corpus answers only.
            // Without it a degraded remote chain still cited the weak top hit
            // ("công thức nấu phở" -> IT services doc) exactly like the fast
            // path once did.
            boolean curated = !"academic-catalog".equals(top.source());
            int confident = properties != null ? properties.lexicalConfidentScore()
                    : AssistantProperties.DEFAULT_LEXICAL_CONFIDENT_SCORE;
            if (curated && (top.lexicalScore() < confident
                    || !hasTermCorroboration(top, retrievalTerms(normalized)))) {
                return new ChatResponse(noMatchMessage(normalizedLocale), MODEL, false,
                        "NO_MATCH", normalizedLocale, List.of());
            }
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
        // Sensitive-data and injection screening still applies. AssistantOutputGuard
        // deliberately does NOT: the corpus has already passed knowledge governance
        // (authored, reviewed and published through a release), and the SPECIALIZED
        // domain legitimately contains "docker compose", "API key" and similar
        // strings that a software-engineering answer must be allowed to quote.
        // Applying the output boundary here silently removed 7 of the 24 published
        // SPECIALIZED documents from retrieval. Model output is still guarded at
        // the stream boundary with isSafeForAnswer, which rejects anything the
        // model invents beyond this corpus.
        return document != null
                && AssistantInputGuard.isPublicKnowledgeSafe(document.slug())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.title())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.content())
                && AssistantInputGuard.isPublicKnowledgeSafe(document.source());
    }
    static List<String> retrievalTerms(String message) {
        String source = message == null ? "" : message;
        List<String> baseTerms = java.util.Arrays.stream(source.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 2 && !STOP_WORDS.contains(term)).distinct().limit(16).toList();
        List<String> foldedTerms = java.util.Arrays.stream(foldForMatching(source).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 2 && !STOP_WORDS.contains(term)).distinct().toList();
        String foldedPhraseSource = " " + foldForMatching(source)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim() + " ";
        List<String> expanded = new ArrayList<>();
        // Accented phrase aliases come FIRST: the repository keeps the first
        // 16 terms, so base tokens must never crowd the aliases that an
        // unaccented query depends on.
        VIETNAMESE_FOLDED_PHRASE_ALIASES.forEach((foldedPhrase, accentedPhrase) -> {
            if (foldedPhraseSource.contains(" " + foldedPhrase + " ")
                    && !isAmbiguousScopeHit(foldedPhrase, foldedPhraseSource, source)) {
                expanded.add(accentedPhrase);
            }
        });
        // Acronyms follow the aliases for the same crowding reason, and are
        // matched on the same folded phrase source, so "CI/CD", "CI-CD" and
        // "ci cd" are one concept here rather than two noise tokens.
        List<String> acronymTerms = new ArrayList<>();
        for (Map.Entry<String, String> acronym : FOLDED_ACRONYM_PHRASES) {
            if (foldedPhraseSource.contains(" " + acronym.getKey() + " ")
                    && !acronymTerms.contains(acronym.getValue())) {
                acronymTerms.add(acronym.getValue());
                expanded.add(acronym.getValue());
            }
        }
        List<String> shreddedAcronymParts = acronymTerms.stream()
                .flatMap(term -> java.util.Arrays.stream(term.split("[^\\p{L}\\p{N}]+")))
                .toList();
        for (String term : baseTerms) {
            // "ci" and "cd" alone match inside "decision" or "province"; the
            // canonical acronym term above already carries their meaning.
            if (shreddedAcronymParts.contains(term)) continue;
            expanded.add(term);
            switch (term) {
                case "enroll", "enrolled", "enrolling", "enrollment", "enrolment" -> expanded.addAll(List.of("registration", "register"));
                case "register", "registered", "registering", "registration" -> expanded.addAll(List.of("enroll", "enrollment"));
                case "class", "classes" -> expanded.addAll(List.of("course", "courses", "section", "sections"));
                case "course", "courses" -> expanded.addAll(List.of("class", "classes", "section", "sections"));
                case "deadline", "deadlines" -> expanded.addAll(List.of("window", "period"));
                case "window", "windows" -> expanded.addAll(List.of("deadline", "period"));
                case "đăng" -> expanded.add("đăng ký");
                case "ký" -> expanded.add("đăng ký");
                case "học" -> expanded.add("học phần");
                case "phần" -> expanded.add("học phần");
                // Round-4 format sweep: "tư vấn" is one concept; the bare
                // syllables are noise that substring-matched "truy vấn" and
                // "phỏng vấn", ranking the wrong doc #1 under quota (prod
                // screenshot: DevOps question answered with the CSDL doc).
                case "tư" -> expanded.add("tư vấn");
                case "vấn" -> expanded.add("tư vấn");
                default -> { }
            }
        }
        if ((baseTerms.contains("đăng") && baseTerms.contains("ký"))
                || (foldedTerms.contains("dang") && foldedTerms.contains("ky"))) expanded.add("đăng ký");
        if ((baseTerms.contains("tư") && baseTerms.contains("vấn"))
                || (foldedTerms.contains("tu") && foldedTerms.contains("van"))) {
            expanded.add("tư vấn");
            // The bare syllables only substring-hit unrelated nouns ("truy
            // vấn", "phỏng vấn", "tư duy"); the phrase term supersedes them.
            expanded.removeIf(term -> term.equals("tư") || term.equals("vấn")
                    || term.equals("tu") || term.equals("van"));
        }
        if ((baseTerms.contains("học") && baseTerms.contains("phần"))
                || (foldedTerms.contains("hoc") && foldedTerms.contains("phan"))) expanded.add("học phần");
        return expanded.stream().distinct().limit(16).toList();
    }
    private static String foldForMatching(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('\u0111', 'd')
                .replace('\u0110', 'D')
                .toLowerCase(Locale.ROOT);
    }
    private static List<Citation> primaryCitations(List<Citation> citations) {
        return citations == null || citations.isEmpty() ? List.of() : List.of(citations.get(0));
    }
    /**
     * A knowledge miss must still teach the user what IS askable: the generic
     * "not found" line alone read as broken for open-ended questions, so the
     * reply now lists the portal's covered topics as concrete prompts.
     */
    private static String noMatchMessage(String locale) {
        return "vi".equals(locale)
                ? "Mình chưa tìm thấy nội dung khớp câu hỏi này trong kho kiến thức công khai. "
                        + "Bạn thử hỏi lại theo một trong các chủ đề mình trả lời tốt nhé:\n\n"
                        + "• Lớp học phần, giảng viên, phòng học và thời khóa biểu cá nhân.\n"
                        + "• Điểm số, GPA, điểm rèn luyện theo học kỳ.\n"
                        + "• Hạn mức tín chỉ còn được đăng ký và cách đăng ký học phần.\n"
                        + "• Các khoa, ngành đào tạo và danh mục học phần của trường.\n"
                        + "• Quy trình đăng ký đề tài khóa luận và quy chế học vụ.\n"
                        + "• Lịch thi, học phí, điều kiện xét tốt nghiệp, ký túc xá."
                : "I could not find content matching this question in the public knowledge base. "
                        + "Try asking about a topic I answer well:\n\n"
                        + "• Your sections, lecturers, rooms and personal timetable.\n"
                        + "• Your grades, GPA and conduct score by semester.\n"
                        + "• Your remaining registration credit budget and how to register.\n"
                        + "• The university's faculties, majors and course catalog.\n"
                        + "• Thesis topic registration and academic regulations.\n"
                        + "• Exam schedules, tuition, graduation requirements, dormitory.";
    }
    private static String unavailableMessage(String locale) { return "vi".equals(locale) ? "Kho kiến thức CampusCore hiện chưa khả dụng. Vui lòng thử lại sau." : "The CampusCore knowledge base is currently unavailable. Please try again later."; }

    /**
     * Canonical degraded answer for a knowledge-store outage reaching the
     * controller layer (ledger reserve/complete DataAccessExceptions on the
     * local path). Must stay byte-compatible with the outage contract the
     * compose runtime probes and {@code databaseOutageReturnsExplicitDegraded
     * ResponseWithoutCitations} pin: KNOWLEDGE_UNAVAILABLE, degraded, no
     * citations — not the curated fallback, which carries reasonCode NO_MATCH.
     */
    public static ChatResponse knowledgeUnavailableResponse(String locale, java.util.UUID clientRequestId) {
        String normalizedLocale = AssistantInputGuard.normalizeLocale(locale);
        return new ChatResponse(unavailableMessage(normalizedLocale), MODEL, true, "KNOWLEDGE_UNAVAILABLE",
                normalizedLocale, List.of(), java.util.UUID.randomUUID(), clientRequestId,
                null, false, "FAILED_PRE_DISPATCH", null, null);
    }

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
    public record StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus, java.time.Instant resetAt) implements StreamEvent {
        /** Pre-resetAt arity retained for existing emitters; {@code resetAt} stays null. */
        public StreamDone(UUID messageId, String reasonCode, boolean degraded, String terminalStatus) {
            this(messageId, reasonCode, degraded, terminalStatus, null);
        }

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
