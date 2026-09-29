package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationService;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.service.AcademicConductService;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService;
import io.campuscore.restfulapi.web.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Answers personal timetable questions (lịch học / thời khóa biểu / schedule),
 * personal grades and conduct (ĐRL) questions, and thesis supervision /
 * registration queries from the asker's real enrollments, grade rows, conduct
 * evaluations, teaching assignments, and thesis workloads. The public RAG
 * snapshot intentionally never sees personal rows, so this advisor provides
 * accurate, grounded answers.
 *
 * Intercepted answers are not charged against the daily RAG quota and are not
 * persisted as conversation turns; the controller falls back to the RAG path
 * whenever the question is not clearly a personal context question or the actor has
 * no personal context to answer from.
 */
@Service
@org.springframework.context.annotation.Profile("persistence")
public class AssistantPersonalContextAdvisor {

    private static final String MODEL = "campuscore-personal-context";
    private static final String REASON_CODE = "PERSONAL_CONTEXT";
    private static final String UNAVAILABLE_REASON_CODE = "PERSONAL_CONTEXT_UNAVAILABLE";
    private static final Logger LOG = LoggerFactory.getLogger(AssistantPersonalContextAdvisor.class);

    /** Enrollment statuses that still bind a seat, matching the web portal. */
    private static final Set<String> ACTIVE_ENROLLMENT_STATUSES = Set.of("ENROLLED", "CONFIRMED", "PENDING");

    private static final Pattern SCHEDULE_INTENT = Pattern.compile(
            "lịch\\s*(?:học|dạy|giảng\\s*dạy|tuần|hôm\\s*nay|ngày\\s*mai|của\\s*tôi|thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|chủ\\s*nhật|t[2-7]|cn)"
                    + "|lich\\s*(?:hoc|day|giang\\s*day|tuan|hom\\s*nay|ngay\\s*mai|cua\\s*toi|thu\\s*[2-7]|thu\\s*(?:hai|ba|tu|bon|nam|sau|bay)|chu\\s*nhat|t[2-7]|cn)"
                    + "|thời\\s*(?:khoá|khóa|khoa)\\s*biểu|thoi\\s*khoa\\s*bieu|\\btkb\\b"
                    + "|(?:thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|hôm\\s*nay|ngày\\s*mai|chủ\\s*nhật|hom\\s*nay|ngay\\s*mai|chu\\s*nhat)\\s*(?:tôi\\s*)?(?:có\\s*)?(?:học|dạy|lịch|tiết|môn|buổi|ca)"
                    + "|học\\s*ngày\\s*nào|hoc\\s*ngay\\s*nao|m[oô]n\\s*nào\\s*học|mon\\s*nao\\s*hoc|tiết\\s*học|buổi\\s*học|ca\\s*học|ca\\s*dạy|tiết\\s*dạy"
                    + "|(my\\s+)?(class\\s+|teaching\\s+)?schedule|timetable|my\\s+classes|(classes|teaching)\\s+(today|tomorrow|on\\s+\\w+)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Exam-timetable questions ("Lịch thi cuối kỳ khi nào?") are a PUBLIC
     * knowledge topic, not the asker's teaching/attendance timetable. Checked
     * before {@link #SCHEDULE_INTENT} so the bare-noun fix above (which no
     * longer matches a lone "lịch") cannot send exam wording to the personal
     * path either, and so "lịch thi" never hijacks the personal answer.
     */
    private static final Pattern EXAM_SCHEDULE_INTENT = Pattern.compile(
            "lịch\\s*thi|lich\\s*thi"
                    + "|thi\\s*(?:cuối\\s*kỳ|cuối\\s*ky|kết\\s*thúc|học\\s*phần|hoc\\s*phan|tốt\\s*nghiệp|tot\\s*nghiep|lại|bù|bu)"
                    + "|thi\\s*(?:cuoi\\s*ky|ket\\s*thuc|tot\\s*nghiep)"
                    + "|kỳ\\s*thi|ky\\s*thi|khoá\\s*thi|khóa\\s*thi|khoa\\s*thi|phòng\\s*thi|phong\\s*thi"
                    + "|(?:final\\s+)?exam\\s*(?:schedule|timetable|period|session)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern FIRST_PERSON_PRONOUN = Pattern.compile(
            "(?U)\\b(?:tôi|toi|mình|minh|em|my|me|i)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern THESIS_ACTION_OR_OWNERSHIP = Pattern.compile(
            "của\\s*(?:tôi|mình|em)|cua\\s*(?:toi|minh|em)|do\\s*(?:tôi|mình|em)|do\\s*(?:toi|minh|em)"
                    + "|hướng\\s*dẫn|huong\\s*dan|phụ\\s*trách|phu\\s*trach|chấm|cham"
                    + "|\\bmy\\b|supervis\\w*|assigned\\s+to\\s+me",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern THESIS_NOUN = Pattern.compile(
            "đề\\s*tài|de\\s*tai|khóa\\s*luận|khoa\\s*luan|luận\\s*văn|luan\\s*van|đồ\\s*án|do\\s*an|tiểu\\s*luận|tieu\\s*luan|kltn|tlcn|hội\\s*đồng|hoi\\s*dong|\\bthesis\\b|\\btopics?\\b|\\bcouncils?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern THESIS_ARCHIVE_INTENT = Pattern.compile(
            "(?:khóa|khoá|năm)\\s*(?:trước|cũ|vừa\\s*qua|202\\d)|các\\s*năm\\s*trước|cựu\\s*sinh\\s*viên"
                    + "|tham\\s*khảo|tiêu\\s*biểu|xuất\\s*sắc|kho\\s*(?:lưu\\s*trữ|đề\\s*tài)|mẫu\\s*(?:đề\\s*tài|khóa\\s*luận)"
                    + "|đạt\\s*điểm\\s*cao|past\\s*thes(?:is|es)|previous\\s*(?:years?|cohorts?)|exemplary\\s*topics?|thesis\\s*archive",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern THESIS_POLICY_OR_GENERAL_INTENT = Pattern.compile(
            "điều\\s*kiện|dieu\\s*kien|quy\\s*định|quy\\s*dinh|quy\\s*chế|quy\\s*che|thủ\\s*tục|thu\\s*tuc|tiêu\\s*chí|tieu\\s*chi|hướng\\s*dẫn\\s*(?:chung|đăng\\s*ký)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS);

    /** Personal conduct (ĐRL) questions; checked before the broader grades intent. */
    private static final Pattern CONDUCT_INTENT = Pattern.compile(
            "điểm\\s*rèn\\s*luyện|diem\\s*ren\\s*luyen|\\bđrl\\b|\\bdrl\\b"
                    + "|xếp\\s*loại\\s*rèn\\s*luyện|xep\\s*loai\\s*ren\\s*luyen"
                    + "|conduct(?:\\s+(?:score|points?|rating|record))?|training\\s+points?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Personal academic-grades questions (bảng điểm, GPA, kết quả học tập). */
    private static final Pattern GRADES_INTENT = Pattern.compile(
            "bảng\\s*điểm|bang\\s*diem|học\\s*bạ|hoc\\s*ba"
                    + "|kết\\s*quả\\s*học\\s*tập|ket\\s*qua\\s*hoc\\s*tap"
                    + "|xếp\\s*loại\\s*học\\s*lực|xep\\s*loai\\s*hoc\\s*luc"
                    + "|\\bgpa\\b|\\bgpa\\s*của|\\bgpa\\s*cua"
                    + "|(?:điểm|diem)\\s*(?:số|so|tổng\\s*kết|tong\\s*ket|thành\\s*phần|thanh\\s*phan|quá\\s*trình|qua\\s*trinh|học\\s*kỳ|hoc\\s*ky|học\\s*tập|hoc\\s*tap)?"
                    + "|(?:my\\s+)?(?:grades?|scores?|marks?|transcript)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * First-person "which sections am I registered in?" listing questions
     * ("Học kỳ này tôi đang đăng ký những lớp học phần nào?"). A class/section
     * word followed by an interrogative, or a đăng ký/registered phrase next to
     * one — always combined with a first-person pronoun in
     * {@link #isEnrollmentListIntent(String)}.
     */
    private static final Pattern ENROLLMENT_LIST_INTENT = Pattern.compile(
            "(?:lớp|lop|học\\s*phần|hoc\\s*phan|class).*?(?:nào|nao|gì|gi|which)\\b"
                    + "|(?:đã\\s*)?đăng\\s*ký.*?(?:lớp|lop|học\\s*phần|hoc\\s*phan|class)"
                    + "|(?:da\\s*)?dang\\s*ky.*?(?:lop|hoc\\s*phan|class)"
                    + "|(?:lớp|lop|học\\s*phần|hoc\\s*phan).*?(?:đã\\s*)?đăng\\s*ký"
                    + "|registered\\s+(?:in|for)\\s+(?:any\\s+|my\\s+)?(?:classes|sections?|courses?)"
                    + "|registere?d\\s+(?:in|for)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * How-to / policy wording ("cách đăng ký", "hướng dẫn đăng ký", "thủ tục",
     * "làm sao…") turns an enrollment mention into a general knowledge
     * question; those stay on the RAG path instead of listing personal rows.
     */
    private static final Pattern ENROLLMENT_HOWTO_INTENT = Pattern.compile(
            "cách|cach|làm\\s*sao|lam\\s*sao|thế\\s*nào|the\\s*nao|như\\s*thế\\s*nào|nhu\\s*the\\s*nao"
                    + "|hướng\\s*dẫn|huong\\s*dan|thủ\\s*tục|thu\\s*tuc"
                    + "|quy\\s*định|quy\\s*dinh|điều\\s*kiện|dieu\\s*kien",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * "Tôi còn bao nhiêu tín chỉ được đăng ký nữa?" — the personal registration
     * budget question. Answered from the same read path the registration
     * summary endpoint uses (credit limit, used, remaining). Policy wording
     * about raising the limit stays on the knowledge path.
     */
    private static final Pattern CREDITS_REMAINING_INTENT = Pattern.compile(
            "còn\\s*bao\\s*nhiêu\\s*(?:được\\s*đăng\\s*ký\\s*)?tín\\s*chỉ|con\\s*bao\\s*nhieu\\s*(?:duoc\\s*dang\\s*ky\\s*)?tin\\s*chi"
                    + "|còn\\s*lạ[ií]\\s*(?:được\\s*)?bao\\s*nhiêu\\s*tín\\s*chỉ|con\\s*lai\\s*(?:duoc\\s*)?bao\\s*nhieu\\s*tin\\s*chi"
                    + "|còn\\s*thiếu\\s*(?:mấy|bao\\s*nhiêu)\\s*tín\\s*chỉ|con\\s*thieu\\s*(?:may|bao\\s*nhieu)\\s*tin\\s*chi"
                    + "|hạn\\s*mức\\s*tín\\s*chỉ|han\\s*muc\\s*tin\\s*chi"
                    + "|credits?\\s+(?:do\\s+i\\s+have\\s+)?(?:left|remaining)|remaining\\s+credits?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Credit-limit policy wording ("xin nâng hạn mức") stays on the knowledge path. */
    private static final Pattern CREDITS_POLICY_INTENT = Pattern.compile(
            "nâng\\s*hạn\\s*mức|nang\\s*han\\s*muc|quy\\s*trình|quy\\s*trinh|đơn\\s*xin|don\\s*xin",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A section / course code as printed on the portal ("SE013", "SE013-01"). */
    private static final Pattern SECTION_CODE = Pattern.compile(
            "(?<![A-Za-z0-9])[A-Z]{2}\\d{3}(?![0-9A-Za-z])");

    /** "Lớp SE013 học phòng nào, giờ nào?" — a specific section's room/time question. */
    private static final Pattern SECTION_DETAIL_HINT = Pattern.compile(
            "học\\s*phòng|hoc\\s*phong|ở\\s*phòng|o\\s*phong|phòng\\s*nào|phong\\s*nao|phòng\\s*học|phong\\s*hoc"
                    + "|giờ\\s*nào|gio\\s*nao|tiết\\s*nào|tiet\\s*nao|giờ\\s*học|gio\\s*hoc|ca\\s*nào|ca\\s*nao"
                    + "|lịch\\s*của\\s*lớp|lich\\s*cua\\s*lop|học\\s*ở\\s*đâu|hoc\\s*o\\s*dau|học\\s*thứ|hoc\\s*thu"
                    + "|(?:what|which)\\s+(?:room|time|period|building)|(?:when|what\\s+time)\\s+(?:is|does)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static boolean isConductIntent(String message) {
        return StringUtils.hasText(message) && CONDUCT_INTENT.matcher(message).find();
    }

    /** First-person listing of the asker's own current class sections. */
    private static boolean isEnrollmentListIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (ENROLLMENT_HOWTO_INTENT.matcher(message).find()) return false;
        if (isSectionDetailIntent(message)) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && ENROLLMENT_LIST_INTENT.matcher(message).find();
    }

    /**
     * Personal registration-budget questions answered from the registration
     * summary read path. How-to wording ("xin nâng hạn mức") stays knowledge.
     */
    private static boolean isCreditsRemainingIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (ENROLLMENT_HOWTO_INTENT.matcher(message).find()) return false;
        if (CREDITS_POLICY_INTENT.matcher(message).find()) return false;
        return CREDITS_REMAINING_INTENT.matcher(message).find();
    }

    /** "Lớp SE013 học phòng nào, giờ nào?" — a concrete section code plus room/time wording. */
    private static boolean isSectionDetailIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        return SECTION_CODE.matcher(message).find()
                && SECTION_DETAIL_HINT.matcher(message).find();
    }

    /** Grades intent requires a first-person marker so policy questions stay on the knowledge path. */
    private static boolean isGradesIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (isConductIntent(message)) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && GRADES_INTENT.matcher(message).find();
    }

    private static boolean isThesisPersonalIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (THESIS_ARCHIVE_INTENT.matcher(message).find() || THESIS_POLICY_OR_GENERAL_INTENT.matcher(message).find()) {
            return false;
        }
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && THESIS_ACTION_OR_OWNERSHIP.matcher(message).find()
                && THESIS_NOUN.matcher(message).find();
    }

    private static final String[] DAY_LABELS_VI =
            {"", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    private static final String[] DAY_LABELS_EN =
            {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private final AcademicEnrollmentReadService enrollments;
    private final AcademicSectionReadService sections;
    private final ThesisLecturerWorkloadService lecturerWorkload;
    private final AcademicConductService conductService;
    private final RegistrationService registrationService;
    private final NamedParameterJdbcTemplate jdbc;

    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections) {
        this(enrollments, sections, null, null, null);
    }

    /** Compatibility constructor retained for focused advisor tests. */
    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections,
            ThesisLecturerWorkloadService lecturerWorkload,
            NamedParameterJdbcTemplate jdbc,
            AcademicConductService conductService) {
        this(enrollments, sections, lecturerWorkload, jdbc, conductService, null);
    }

    @Autowired
    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections,
            @Autowired(required = false) ThesisLecturerWorkloadService lecturerWorkload,
            @Autowired(required = false) NamedParameterJdbcTemplate jdbc,
            @Autowired(required = false) AcademicConductService conductService,
            @Autowired(required = false) RegistrationService registrationService) {
        this.enrollments = enrollments;
        this.sections = sections;
        this.lecturerWorkload = lecturerWorkload;
        this.jdbc = jdbc;
        this.conductService = conductService;
        this.registrationService = registrationService;
    }

    /** True when the question is clearly about personal schedule, enrollment list, grades, conduct, or thesis status. */
    public boolean handles(String message) {
        if (message == null) return false;
        // Exam timetables are a public knowledge topic even though they share
        // the "lịch" noun with personal timetables — never answer them from
        // the asker's own teaching/attendance rows.
        if (EXAM_SCHEDULE_INTENT.matcher(message).find()) return false;
        return SCHEDULE_INTENT.matcher(message).find()
                || isThesisPersonalIntent(message)
                || isConductIntent(message)
                || isGradesIntent(message)
                || isEnrollmentListIntent(message)
                || isCreditsRemainingIntent(message)
                || isSectionDetailIntent(message);
    }

    /**
     * Personal timetable answer, or null when the asker has no personal
     * context (the caller should fall back to the public knowledge path).
     */
    public ChatResponse answer(ChatRequest request, Jwt actor) {
        String locale = normalizedLocale(request);
        String message = request != null ? request.message() : null;
        String answer;
        try {
            answer = composeAnswer(actor, locale, message);
        } catch (DataAccessException exception) {
            // A personal-data outage must not turn a normal assistant question into HTTP 500.
            // Do not fall through to public RAG: it must never invent or expose personal data.
            LOG.warn("personal schedule lookup failed with {}", exception.getClass().getSimpleName());
            return new ChatResponse(fallbackMessage(locale), MODEL, true, UNAVAILABLE_REASON_CODE,
                    locale, List.of());
        }
        if (answer == null) {
            return null;
        }
        if (!AssistantOutputGuard.isSafe(answer)) {
            answer = ThesisAssistantService.technicalOutputMessage(locale);
        }
        return new ChatResponse(answer, MODEL, false, REASON_CODE, locale, List.of());
    }

    /** Emits the personal answer over the SSE contract as meta → replace → done. */
    public void stream(ChatRequest request, Jwt actor, Consumer<ThesisAssistantService.StreamEvent> sink) {
        stream(answer(request, actor), request, sink);
    }

    /**
     * Streams an already-computed answer. Keeping answer and stream on the
     * same response prevents the controller from reading personal records
     * twice and makes JSON/SSE reason metadata identical.
     */
    public void stream(ChatResponse response, ChatRequest request,
            Consumer<ThesisAssistantService.StreamEvent> sink) {
        String locale = normalizedLocale(request);
        String answer = response == null ? fallbackMessage(locale) : response.answer();
        String reasonCode = response == null || !StringUtils.hasText(response.reasonCode())
                ? REASON_CODE : response.reasonCode();
        boolean degraded = response != null && response.degraded();
        if (!AssistantOutputGuard.isSafe(answer)) {
            answer = ThesisAssistantService.technicalOutputMessage(locale);
            reasonCode = "TECHNICAL_REQUEST_BLOCKED";
            degraded = true;
        }
        sink.accept(new ThesisAssistantService.StreamMeta(
                UUID.randomUUID(), request.clientRequestId(), null, null, MODEL, locale));
        sink.accept(new ThesisAssistantService.StreamReplace(answer, List.of(), reasonCode));
        sink.accept(new ThesisAssistantService.StreamDone(null, reasonCode, degraded, "COMPLETED"));
    }

    private String composeAnswer(Jwt actor, String locale, String message) {
        if (isThesisPersonalIntent(message)) {
            String lecturerId = claim(actor, "lecturerId");
            if (StringUtils.hasText(lecturerId) && lecturerWorkload != null) {
                return lecturerThesisAnswer(lecturerId, locale);
            }
            String studentId = claim(actor, "studentId");
            if (StringUtils.hasText(studentId) && jdbc != null) {
                return studentThesisAnswer(studentId, locale);
            }
            return null;
        }
        String studentId = claim(actor, "studentId");
        if (isConductIntent(message)) {
            // Conduct summaries are student-owned; staff questions fall through to RAG policy answers.
            if (StringUtils.hasText(studentId) && conductService != null) {
                return conductAnswer(studentId, locale);
            }
            return null;
        }
        if (isCreditsRemainingIntent(message)) {
            // The registration budget is student-owned; without the summary read
            // path (or a profile) the question falls back to the knowledge path.
            if (StringUtils.hasText(studentId) && registrationService != null) {
                return creditsRemainingAnswer(studentId, locale);
            }
            return null;
        }
        if (isSectionDetailIntent(message)) {
            // Section room/time lookups are answered from the student's own
            // registered sections first, then the published catalog.
            if (StringUtils.hasText(studentId)) {
                return sectionDetailAnswer(studentId, locale, message);
            }
            return null;
        }
        if (isGradesIntent(message)) {
            if (StringUtils.hasText(studentId)) {
                return gradesAnswer(studentId, locale);
            }
            return null;
        }
        if (isEnrollmentListIntent(message)) {
            // The section list is student-owned; staff questions fall through to RAG.
            if (StringUtils.hasText(studentId)) {
                return enrollmentListAnswer(studentId, locale);
            }
            return null;
        }
        Integer requestedDay = detectRequestedDay(message);
        if (StringUtils.hasText(studentId)) {
            return studentAnswer(studentId, locale, requestedDay);
        }
        String lecturerId = claim(actor, "lecturerId");
        if (StringUtils.hasText(lecturerId)) {
            return lecturerAnswer(lecturerId, locale, requestedDay);
        }
        return null;
    }

    /**
     * Personal grades answer grounded in the asker's real grade rows. The
     * cumulative GPA (4.0 scale) and credit totals are computed IN CODE across
     * every published semester — never summed by a model — and the latest
     * semester's figures are shown separately and labeled, because the two are
     * routinely confused (production audit D-Q3).
     */
    private String gradesAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        List<GradeSummary> grades = enrollments.findStudentGrades(studentId, null);
        if (grades == null || grades.isEmpty()) {
            return vi
                    ? "Bạn chưa có điểm học phần nào được ghi nhận. Điểm sẽ xuất hiện ở đây sau khi giảng viên nhập và cổng công bố."
                    : "You have no recorded course grades yet. Grades appear here once lecturers submit them and the portal publishes them.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Kết quả học tập của bạn:\n" : "Your academic results:\n");

        // Grades come ordered newest semester first, so the first group is the
        // latest semester and the running total is the cumulative record.
        Map<String, List<GradeSummary>> bySemester = new LinkedHashMap<>();
        for (GradeSummary grade : grades) {
            bySemester.computeIfAbsent(grade.semesterId(), ignored -> new ArrayList<>()).add(grade);
        }
        GradeTotals cumulative = new GradeTotals();
        GradeSummary latestName = null;
        for (List<GradeSummary> rows : bySemester.values()) {
            GradeTotals semester = new GradeTotals();
            rows.forEach(semester::add);
            cumulative.merge(semester);
            // The "latest semester" figures are the newest semester WITH
            // published grades — an in-progress current term (no letters yet)
            // must not shadow the last graded one.
            if (latestName == null && semester.gpaCredits > 0) {
                latestName = rows.isEmpty() ? null : rows.get(0);
            }
        }
        if (cumulative.gpaCredits == 0) {
            answer.append("\n").append(vi
                    ? "Chưa có học kỳ nào có điểm đã công bố — các học phần đang học sẽ xuất hiện sau khi có điểm.\n"
                    : "No published grades yet — in-progress courses will appear once graded.\n");
        } else {
            answer.append("\n").append(vi ? "Tích lũy: " : "Cumulative: ")
                    .append(cumulative.earnedCredits).append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                    .append(cumulative.gpa())
                    .append(vi ? " (thang 4)\n" : " (4.0 scale)\n");
            if (latestName != null) {
                GradeTotals latest = new GradeTotals();
                bySemester.get(latestName.semesterId()).forEach(latest::add);
                String latestLabel = vi
                        ? firstText(latestName.semesterNameVi(), latestName.semester())
                        : firstText(latestName.semesterNameEn(), latestName.semester());
                answer.append(vi ? "Học kỳ gần nhất (" : "Most recent semester (")
                        .append(StringUtils.hasText(latestLabel) ? latestLabel : (vi ? "học kỳ gần nhất" : "latest semester"))
                        .append(vi ? "): " : "): ")
                        .append(latest.earnedCredits).append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                        .append(latest.gpa())
                        .append(vi ? " (thang 4)\n" : " (4.0 scale)\n");
            }
        }

        int rendered = 0;
        int skipped = 0;
        for (GradeSummary grade : grades) {
            if (rendered >= 10) {
                skipped += 1;
                continue;
            }
            String course = courseName(grade.courseCode(), courseText(grade, vi), null);
            answer.append("\n• ").append(StringUtils.hasText(course) ? course : (vi ? "Học phần" : "Course"));
            answer.append(vi ? ": " : ": ");
            String letter = grade.letterGrade();
            if (StringUtils.hasText(letter)) {
                answer.append(vi ? "điểm " : "grade ").append(grade.finalGrade()).append(" (").append(letter).append(")");
            } else {
                answer.append(vi ? "chưa công bố" : "not published yet");
            }
            answer.append("\n");
            rendered += 1;
        }
        if (skipped > 0) {
            answer.append("\n").append(vi
                    ? "… và " + skipped + " học phần khác. Xem đầy đủ ở trang Bảng điểm."
                    : "… and " + skipped + " more courses. See the Grades page for the full list.");
        } else {
            answer.append(vi
                    ? "\nBạn có thể xem chi tiết từng cột điểm ở trang Bảng điểm."
                    : "\nYou can review each score component on the Grades page.");
        }
        answer.append(vi
                ? "\n(Số liệu GPA và tín chỉ được tính trực tiếp từ bảng điểm đã công bố của bạn.)"
                : "\n(GPA and credit figures are computed directly from your published transcript.)");
        return answer.toString();
    }

    /** Running GPA-4.0 totals over published grade rows (audit D-Q3: computed in code, never by the model). */
    private static final class GradeTotals {
        private BigDecimal points = BigDecimal.ZERO;
        private int gpaCredits;
        private int earnedCredits;

        private void add(GradeSummary grade) {
            String letter = grade.letterGrade();
            // Ungraded rows (letter null) contribute to neither GPA nor credits.
            if (letter == null) {
                return;
            }
            BigDecimal point = GRADE_POINTS.get(letter);
            if (point == null) {
                return;
            }
            points = points.add(point.multiply(BigDecimal.valueOf(grade.credits())));
            gpaCredits += grade.credits();
            if (!"F".equals(grade.letterGrade())) {
                earnedCredits += grade.credits();
            }
        }

        private void merge(GradeTotals other) {
            points = points.add(other.points);
            gpaCredits += other.gpaCredits;
            earnedCredits += other.earnedCredits;
        }

        private BigDecimal gpa() {
            return gpaCredits == 0
                    ? BigDecimal.ZERO.setScale(2)
                    : points.divide(BigDecimal.valueOf(gpaCredits), 2, RoundingMode.HALF_UP);
        }
    }

    /** The official HCMUTE 10-point letter → 4.0 table, matching AcademicEnrollmentReadService. */
    private static final Map<String, BigDecimal> GRADE_POINTS = Map.ofEntries(
            Map.entry("A+", BigDecimal.valueOf(4.0)),
            Map.entry("A", BigDecimal.valueOf(4.0)),
            Map.entry("A-", BigDecimal.valueOf(3.7)),
            Map.entry("B+", BigDecimal.valueOf(3.5)),
            Map.entry("B", BigDecimal.valueOf(3.0)),
            Map.entry("B-", BigDecimal.valueOf(2.7)),
            Map.entry("C+", BigDecimal.valueOf(2.5)),
            Map.entry("C", BigDecimal.valueOf(2.0)),
            Map.entry("C-", BigDecimal.valueOf(1.7)),
            Map.entry("D+", BigDecimal.valueOf(1.5)),
            Map.entry("D", BigDecimal.valueOf(1.0)),
            Map.entry("D-", BigDecimal.valueOf(0.7)),
            Map.entry("F", BigDecimal.ZERO));

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String courseText(GradeSummary grade, boolean vi) {
        String localized = vi ? grade.courseNameVi() : grade.courseNameEn();
        return StringUtils.hasText(localized) ? localized : grade.courseName();
    }

    /** Personal conduct (ĐRL) answer from the student's real semester evaluations. */
    private String conductAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        StudentConductSummaryDto summary;
        try {
            summary = conductService.studentSummary(studentId);
        } catch (org.springframework.web.server.ResponseStatusException exception) {
            return vi
                    ? "Bạn chưa có bản đánh giá điểm rèn luyện nào. Điểm rèn luyện được cập nhật sau mỗi học kỳ bởi cố vấn học tập."
                    : "You have no conduct evaluation on record yet. Conduct points are updated after each semester by your academic advisor.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Điểm rèn luyện của bạn:\n" : "Your conduct record:\n");
        if (summary.history() == null || summary.history().isEmpty()) {
            answer.append("\n").append(vi
                    ? "Chưa có đánh giá rèn luyện trong các học kỳ gần đây."
                    : "No conduct evaluation was recorded in recent semesters.");
            return answer.toString();
        }
        answer.append("\n").append(vi ? "Tổng kết: " : "Cumulative: ")
                .append(summary.cumulativeAverageScore())
                .append(" — ")
                .append(summary.cumulativeClassificationVi())
                .append("\n");
        var current = summary.currentSemester();
        if (current != null) {
            answer.append("\n").append(vi ? "Học kỳ gần nhất (" : "Most recent semester (")
                    .append(current.semesterName())
                    .append(vi ? "): " : "): ")
                    .append(current.totalScore())
                    .append(" — ")
                    .append(vi ? current.classificationVi() : current.classification())
                    .append("\n");
        }
        answer.append(vi
                ? "\nXem chi tiết 5 tiêu chí và hoạt động phong trào ở trang Điểm rèn luyện."
                : "\nSee the five criteria and activity list on the Conduct page.");
        return answer.toString();
    }

    /**
     * "Tôi còn bao nhiêu tín chỉ được đăng ký nữa?" — answered from the same
     * read path the /me/registration/summary endpoint uses, so limit, used and
     * remaining can never disagree with the portal (audit D-Q5). An approved
     * limit raise (30 instead of the 28 standard) is called out explicitly.
     */
    private String creditsRemainingAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        SummaryResponse summary;
        try {
            summary = registrationService.summary(studentId, null);
        } catch (DomainException exception) {
            // No open registration round (or no active profile): the personal
            // numbers do not exist right now. The knowledge path still answers
            // the credit-limit policy, so fall through instead of inventing one.
            LOG.info("registration summary unavailable for credits-remaining question: {}", exception.code());
            return null;
        }
        int remaining = Math.max(0, summary.creditsRemaining());
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Hạn mức đăng ký tín chỉ học kỳ này của bạn:\n"
                : "Your course registration budget for this term:\n");
        answer.append("\n• ").append(vi ? "Đã đăng ký: " : "Registered: ")
                .append(summary.creditsUsed()).append(vi ? " tín chỉ\n" : " credits\n");
        answer.append("• ").append(vi ? "Hạn mức: " : "Credit limit: ")
                .append(summary.creditLimit()).append(vi ? " tín chỉ\n" : " credits\n");
        answer.append("• ").append(vi ? "Còn lại có thể đăng ký: " : "Still available: ")
                .append(remaining).append(vi ? " tín chỉ\n" : " credits\n");
        if (summary.creditLimit() > CreditLimitApplicationService.STANDARD_LIMIT) {
            answer.append(vi
                    ? "\nHạn mức " + summary.creditLimit() + " tín chỉ là mức đã được Phòng Đào tạo phê duyệt nâng từ mức chuẩn "
                            + CreditLimitApplicationService.STANDARD_LIMIT + " tín chỉ.\n"
                    : "\nYour " + summary.creditLimit() + "-credit limit was approved by the Academic Affairs Office above the standard "
                            + CreditLimitApplicationService.STANDARD_LIMIT + " credits.\n");
        }
        answer.append(vi
                ? "\n(Số liệu tính trực tiếp từ hồ sơ đăng ký học phần của bạn.) Bạn có thể đăng ký thêm học phần ở khu Đăng ký học phần."
                : "\n(Figures come directly from your registration records.) You can add more sections in the Course Registration area.");
        return answer.toString();
    }

    /**
     * "Lớp SE013 học phòng nào, giờ nào?" — resolves the code against the
     * student's own registered sections first (same data path as the timetable,
     * audit D-Q7), then the published catalog. An unknown code says so and
     * points to the registration page without claiming a data absence that the
     * catalog contradicts.
     */
    private String sectionDetailAnswer(String studentId, String locale, String message) {
        boolean vi = "vi".equals(locale);
        java.util.regex.Matcher matcher = SECTION_CODE.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String code = matcher.group();
        List<EnrollmentResponse> active = enrollments.findStudentEnrollments(studentId, null).stream()
                .filter(item -> ACTIVE_ENROLLMENT_STATUSES.contains(item.status()))
                .toList();
        List<EnrollmentResponse> currentTerm = currentTermEnrollments(active);
        EnrollmentResponse registered = matchByCode(currentTerm, code)
                .or(() -> matchByCode(active, code))
                .orElse(null);
        if (registered != null && registered.section() != null) {
            String courseCode = registered.section().course() != null ? registered.section().course().code() : null;
            String courseName = courseLabel(registered.section().course(), locale);
            String label = courseCode != null
                    ? courseName != null ? courseCode + " - " + courseName : courseCode
                    : courseName;
            StringBuilder answer = new StringBuilder();
            answer.append(vi ? "Thông tin lớp " : "Details for section ").append(code);
            if (StringUtils.hasText(label)) {
                answer.append(" — ").append(label);
            }
            if (StringUtils.hasText(registered.section().sectionNumber())) {
                answer.append(vi ? " (lớp " : " (section ").append(registered.section().sectionNumber()).append(")");
            }
            answer.append(vi ? ":\n" : ":\n");
            List<SectionScheduleResponse> schedules = registered.section().schedules() == null
                    ? List.of() : registered.section().schedules();
            if (schedules.isEmpty()) {
                answer.append("\n").append(vi
                        ? "Lớp này chưa có lịch học được công bố. Bạn xem lại ở trang Thời khóa biểu sau khi lịch được cập nhật."
                        : "This section has no published schedule yet. Check the Schedule page once it is updated.");
                return answer.toString();
            }
            schedules.stream()
                    .sorted(Comparator.comparingInt(SectionScheduleResponse::dayOfWeek)
                            .thenComparing(SectionScheduleResponse::startTime))
                    .forEach(schedule -> {
                        int day = schedule.dayOfWeek() == 0 ? 7 : schedule.dayOfWeek();
                        answer.append("\n• ").append((vi ? DAY_LABELS_VI : DAY_LABELS_EN)[day])
                                .append(" ").append(schedule.startTime()).append("-").append(schedule.endTime());
                        if (schedule.classroom() != null) {
                            String room = trimJoin(schedule.classroom().building(), schedule.classroom().roomNumber());
                            if (StringUtils.hasText(room)) {
                                answer.append(vi ? " (phòng " : " (room ").append(room).append(")");
                            }
                        }
                        answer.append("\n");
                    });
            answer.append(vi
                    ? "\n(theo thời khóa biểu đã đăng ký của bạn)"
                    : "\n(from your registered timetable)");
            return answer.toString();
        }

        // Not among the student's sections: check the published catalog before
        // claiming anything about the code.
        if (jdbc == null) {
            return vi
                    ? "Mình chưa tra cứu được danh mục lớp lúc này. Bạn kiểm tra mã lớp " + code
                            + " ở khu Đăng ký học phần nhé."
                    : "I could not check the section catalog right now. Please verify section " + code
                            + " in the Course Registration area.";
        }
        List<Map<String, Object>> catalogRows = jdbc.queryForList(
                "SELECT section.\"sectionNumber\" AS section_number, course.\"code\" AS course_code,"
                        + " course.\"name\" AS course_name, course.\"nameEn\" AS course_name_en, course.\"nameVi\" AS course_name_vi,"
                        + " semester.\"name\" AS semester_name, semester.\"nameVi\" AS semester_name_vi, semester.\"nameEn\" AS semester_name_en,"
                        + " schedule.\"dayOfWeek\" AS schedule_day, schedule.\"startTime\" AS schedule_start, schedule.\"endTime\" AS schedule_end,"
                        + " classroom.\"building\" AS room_building, classroom.\"roomNumber\" AS room_number"
                        + " FROM academic.\"Section\" section"
                        + " JOIN academic.\"Course\" course ON course.\"id\" = section.\"courseId\""
                        + " LEFT JOIN academic.\"Semester\" semester ON semester.\"id\" = section.\"semesterId\""
                        + " LEFT JOIN academic.\"SectionSchedule\" schedule ON schedule.\"sectionId\" = section.\"id\""
                        + " LEFT JOIN academic.\"Classroom\" classroom ON classroom.\"id\" = schedule.\"classroomId\""
                        + " WHERE section.\"status\" <> 'ARCHIVED'"
                        + " AND (UPPER(course.\"code\") = :code OR UPPER(section.\"sectionNumber\") IN (:code, :codePrefix))",
                new MapSqlParameterSource()
                        .addValue("code", code)
                        .addValue("codePrefix", code + "-%"));
        if (catalogRows.isEmpty()) {
            return vi
                    ? "Mình không tìm thấy mã lớp " + code + " trong danh mục học phần hiện có. "
                            + "Bạn kiểm tra lại mã ở khu Đăng ký học phần nhé."
                    : "I could not find section " + code + " in the current course catalog. "
                            + "Please double-check the code in the Course Registration area.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Lớp " : "Section ").append(code);
        String courseName = vi
                ? firstText(text(catalogRows.get(0), "course_name_vi"), firstText(text(catalogRows.get(0), "course_name"), text(catalogRows.get(0), "course_name_en")))
                : firstText(text(catalogRows.get(0), "course_name_en"), text(catalogRows.get(0), "course_name"));
        if (StringUtils.hasText(courseName)) {
            answer.append(" — ").append(courseName);
        }
        answer.append(vi
                ? " có trong danh mục, nhưng bạn chưa đăng ký lớp này.\n\nLịch học được công bố:"
                : " exists in the catalog, but you are not registered in it.\n\nPublished schedule:");
        boolean hasSchedule = false;
        for (Map<String, Object> row : catalogRows) {
            Object day = row.get("schedule_day");
            if (day == null) {
                continue;
            }
            hasSchedule = true;
            int dayNumber = ((Number) day).intValue();
            answer.append("\n• ").append((vi ? DAY_LABELS_VI : DAY_LABELS_EN)[dayNumber == 0 ? 7 : dayNumber])
                    .append(" ").append(text(row, "schedule_start")).append("-").append(text(row, "schedule_end"));
            String room = trimJoin(text(row, "room_building"), text(row, "room_number"));
            if (StringUtils.hasText(room)) {
                answer.append(vi ? " (phòng " : " (room ").append(room).append(")");
            }
            answer.append("\n");
        }
        if (!hasSchedule) {
            answer.append("\n• ").append(vi
                    ? "chưa có lịch học được công bố cho lớp này."
                    : "no schedule published for this section yet.");
        }
        answer.append(vi
                ? "\n\nNếu muốn học lớp này, bạn đăng ký ở khu Đăng ký học phần khi đợt còn mở."
                : "\n\nTo take this section, register for it in the Course Registration area while a round is open.");
        return answer.toString();
    }

    /** The student's enrollment whose course code or section number matches the asked code. */
    private static java.util.Optional<EnrollmentResponse> matchByCode(List<EnrollmentResponse> candidates, String code) {
        String normalized = code.toUpperCase(java.util.Locale.ROOT);
        return candidates.stream()
                .filter(item -> item.section() != null)
                .filter(item -> {
                    String courseCode = item.section().course() != null ? item.section().course().code() : null;
                    String sectionNumber = item.section().sectionNumber();
                    return normalized.equalsIgnoreCase(courseCode)
                            || normalized.equalsIgnoreCase(sectionNumber)
                            || (StringUtils.hasText(sectionNumber)
                                    && sectionNumber.toUpperCase(java.util.Locale.ROOT).startsWith(normalized + "-"));
                })
                .findFirst();
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private String lecturerThesisAnswer(String lecturerId, String locale) {        if (lecturerWorkload == null) return null;
        boolean vi = "vi".equals(locale);
        var workload = lecturerWorkload.workload(lecturerId);
        if (workload == null || (workload.topics().isEmpty() && workload.councils().isEmpty() && workload.gradingTasks().isEmpty())) {
            return vi
                    ? "Hiện tại bạn chưa có đề tài khóa luận nào đang hướng dẫn hoặc hội đồng bảo vệ nào được phân công."
                    : "You are not currently supervising any thesis topics or assigned to any thesis defense councils.";
        }
        StringBuilder answer = new StringBuilder();
        if (!workload.topics().isEmpty()) {
            answer.append(vi ? "Danh sách đề tài khóa luận bạn đang hướng dẫn:\n" : "Thesis topics you are supervising:\n");
            for (var topic : workload.topics()) {
                answer.append("\n• ").append(topic.title());
                if (StringUtils.hasText(topic.roundName())) {
                    answer.append(vi ? " (Đợt: " : " (Round: ").append(topic.roundName()).append(")");
                }
                answer.append(vi ? "\n  - Trạng thái: " : "\n  - Status: ").append(topic.topicStatus());
                answer.append(vi ? " | Số nhóm: " : " | Groups: ").append(topic.groupCount());
                if (topic.pendingGroupCount() > 0) {
                    answer.append(vi ? " (" + topic.pendingGroupCount() + " nhóm chờ duyệt)" : " (" + topic.pendingGroupCount() + " pending approval)");
                }
                answer.append("\n");
            }
        }
        if (!workload.councils().isEmpty()) {
            if (answer.length() > 0) answer.append("\n");
            answer.append(vi ? "Hội đồng đánh giá bạn tham gia:\n" : "Defense councils you are assigned to:\n");
            for (var council : workload.councils()) {
                answer.append("\n• ").append(council.name());
                if (StringUtils.hasText(council.memberRole())) {
                    answer.append(vi ? " — Vai trò: " : " — Role: ").append(council.memberRole());
                }
                if (StringUtils.hasText(council.roundName())) {
                    answer.append(vi ? " (Đợt: " : " (Round: ").append(council.roundName()).append(")");
                }
                answer.append(vi ? "\n  - Số đề tài: " : "\n  - Topics: ").append(council.topicCount());
                answer.append("\n");
            }
        }
        if (!workload.gradingTasks().isEmpty()) {
            long pendingGrading = workload.gradingTasks().stream().filter(g -> g.myScoreRows() == 0).count();
            if (pendingGrading > 0) {
                if (answer.length() > 0) answer.append("\n");
                answer.append(vi
                        ? "Bạn có " + pendingGrading + " đề tài cần chấm điểm trong hội đồng.\n"
                        : "You have " + pendingGrading + " topics pending score entry in your councils.\n");
            }
        }
        answer.append(vi
                ? "\nBạn có thể xem chi tiết và xét duyệt nhóm tại trang Quản lý Khóa luận."
                : "\nYou can review details and approve student groups on the Thesis Management page.");
        return answer.toString();
    }

    private String studentThesisAnswer(String studentId, String locale) {
        if (jdbc == null) return null;
        boolean vi = "vi".equals(locale);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT g.id AS group_id, g.status AS group_status, g.approval_status, "
                        + "t.id AS topic_id, t.title AS topic_title, t.status AS topic_status, "
                        + "t.final_score, r.name AS round_name, r.status AS round_status "
                        + "FROM thesis.thesis_group_member m "
                        + "JOIN thesis.thesis_group g ON g.id = m.group_id "
                        + "JOIN thesis.thesis_registration_round r ON r.id = m.round_id "
                        + "LEFT JOIN thesis.thesis_topic t ON t.id = g.topic_id "
                        + "WHERE m.student_id = :studentId "
                        + "ORDER BY r.created_at DESC",
                new MapSqlParameterSource("studentId", studentId));
        if (rows.isEmpty()) {
            // Audit D-Q6: when there are no registration rows the answer says so
            // plainly; nothing may be invented (topic, status, cohort year).
            return vi
                    ? "Bạn chưa đăng ký đồ án/luận văn trong đợt nào. "
                            + "Khi có đợt mở đăng ký, bạn sẽ xem và đăng ký ở trang Khóa luận tốt nghiệp."
                    : "You have not registered for a thesis or capstone project in any round yet. "
                            + "You can view and register in open rounds on the Thesis page.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Thông tin đăng ký khóa luận của bạn:\n" : "Your thesis registration status:\n");
        for (Map<String, Object> row : rows) {
            String roundName = (String) row.get("round_name");
            String topicTitle = (String) row.get("topic_title");
            String approvalStatus = (String) row.get("approval_status");
            Object finalScore = row.get("final_score");

            answer.append("\n• ").append(StringUtils.hasText(roundName) ? roundName : (vi ? "Đợt khóa luận" : "Thesis Round"));
            if (StringUtils.hasText(topicTitle)) {
                answer.append(vi ? "\n  - Đề tài: " : "\n  - Topic: ").append(topicTitle);
            } else {
                answer.append(vi ? "\n  - Đề tài: Chưa chọn đề tài" : "\n  - Topic: Not selected yet");
            }
            if (StringUtils.hasText(approvalStatus)) {
                answer.append(vi ? "\n  - Trạng thái duyệt: " : "\n  - Approval status: ").append(approvalStatus);
            }
            if (finalScore != null) {
                answer.append(vi ? "\n  - Điểm tổng kết: " : "\n  - Final score: ").append(finalScore);
            }
            answer.append("\n");
        }
        answer.append(vi
                ? "\nBạn có thể xem chi tiết tiến độ tại trang Khóa luận tốt nghiệp."
                : "\nYou can track your thesis progress on the Thesis page.");
        return answer.toString();
    }

    private String studentAnswer(String studentId, String locale, Integer requestedDay) {
        List<EnrollmentResponse> active = enrollments.findStudentEnrollments(studentId, null).stream()
                .filter(item -> ACTIVE_ENROLLMENT_STATUSES.contains(item.status()))
                .toList();
        if (active.isEmpty()) {
            return noClassesMessage(locale);
        }
        List<EnrollmentResponse> currentTerm = currentTermEnrollments(active);

        List<Slot> slots = new ArrayList<>();
        for (EnrollmentResponse item : currentTerm) {
            if (item.section() == null) continue;
            String courseCode = item.section().course() != null ? item.section().course().code() : null;
            String courseName = courseLabel(item.section().course(), locale);
            String label = courseCode != null
                    ? courseName != null ? courseCode + " - " + courseName : courseCode
                    : courseName;
            for (SectionScheduleResponse schedule : item.section().schedules()) {
                slots.add(new Slot(
                        schedule.dayOfWeek(),
                        schedule.startTime(),
                        schedule.endTime(),
                        schedule.classroom() != null
                                ? trimJoin(schedule.classroom().building(), schedule.classroom().roomNumber())
                                : null,
                        label,
                        item.section().sectionNumber()));
            }
        }
        if (slots.isEmpty()) {
            return noClassesMessage(locale);
        }
        String termName = currentTerm.get(0).section().semester() != null
                ? semesterLabel(currentTerm.get(0).section().semester(), locale)
                : null;
        return timetableAnswer(slots, termName, locale, requestedDay, false);
    }

    /**
     * "Học kỳ này tôi đang đăng ký những lớp học phần nào?" — the asker's
     * current-term section list built from real enrollments with the same
     * active-status filter and semester selection the timetable uses. The
     * credit total is summed in CODE from the student's own registration rows
     * (the creditsSnapshot the registration endpoint counts) and the source is
     * labeled, so the model can never add or invent the number (audit D-Q1).
     */
    private String enrollmentListAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        List<EnrollmentResponse> active = enrollments.findStudentEnrollments(studentId, null).stream()
                .filter(item -> ACTIVE_ENROLLMENT_STATUSES.contains(item.status()))
                .toList();
        List<EnrollmentResponse> currentTerm = currentTermEnrollments(active);
        StringBuilder answer = new StringBuilder();
        int rendered = 0;
        for (EnrollmentResponse item : currentTerm) {
            if (item.section() == null) continue;
            String courseCode = item.section().course() != null ? item.section().course().code() : null;
            String courseName = courseLabel(item.section().course(), locale);
            String label = courseCode != null
                    ? courseName != null ? courseCode + " - " + courseName : courseCode
                    : courseName;
            answer.append("\n• ").append(StringUtils.hasText(label) ? label : (vi ? "Học phần" : "Course"));
            if (StringUtils.hasText(item.section().sectionNumber())) {
                answer.append(vi ? " — lớp " : " — section ").append(item.section().sectionNumber());
            }
            answer.append(vi ? " — trạng thái " : " — status ").append(item.status());
            answer.append("\n");
            rendered += 1;
        }
        if (rendered == 0) {
            return noEnrollmentsMessage(vi);
        }
        int credits = sumRegisteredCredits(currentTerm);
        answer.insert(0, vi
                ? "Học kỳ này bạn đang đăng ký " + rendered + " lớp (" + credits + " tín chỉ theo dữ liệu đăng ký của bạn):\n"
                : "This term you are registered in " + rendered + " sections (" + credits
                        + " credits, from your registration records):\n");
        answer.append(vi
                ? "\n(Tổng tín chỉ được tính trực tiếp từ hồ sơ đăng ký học phần của bạn.)"
                : "\n(The credit total is computed directly from your registration records.)");
        answer.append(vi
                ? "\nBạn có thể xem lịch học dạng lưới ở trang Thời khóa biểu."
                : "\nYou can see these sections as a weekly grid on the Schedule page.");
        return answer.toString();
    }

    /**
     * Credit total for the listed enrollments, counted the same way the
     * registration summary endpoint counts it: SUM(creditsSnapshot) over the
     * student's own active rows. Falls back to the current course catalog
     * credits when the ledger is unavailable.
     */
    private int sumRegisteredCredits(List<EnrollmentResponse> currentTerm) {
        List<String> ids = currentTerm.stream()
                .map(EnrollmentResponse::id)
                .filter(StringUtils::hasText)
                .toList();
        if (jdbc != null && !ids.isEmpty()) {
            try {
                Integer snapshot = jdbc.queryForObject(
                        "SELECT COALESCE(SUM(\"creditsSnapshot\"), 0) FROM academic.\"Enrollment\" WHERE \"id\" IN (:ids)",
                        new MapSqlParameterSource("ids", ids),
                        Integer.class);
                if (snapshot != null) {
                    return snapshot;
                }
            } catch (RuntimeException exception) {
                LOG.warn("creditsSnapshot lookup failed with {}; falling back to course credits",
                        exception.getClass().getSimpleName());
            }
        }
        return currentTerm.stream()
                .filter(item -> item.section() != null && item.section().course() != null)
                .mapToInt(item -> item.section().course().credits())
                .sum();
    }

    /**
     * The latest-semester slice of the active enrollments — the same
     * current-semester selection the personal timetable uses.
     */
    private static List<EnrollmentResponse> currentTermEnrollments(List<EnrollmentResponse> active) {
        Instant currentTermStart = active.stream()
                .map(item -> item.section() != null && item.section().semester() != null
                        ? item.section().semester().startDate()
                        : null)
                .filter(Instant.class::isInstance)
                .map(Instant.class::cast)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return active.stream()
                .filter(item -> item.section() != null && item.section().semester() != null
                        && item.section().semester().startDate().equals(currentTermStart))
                .toList();
    }

    private String lecturerAnswer(String lecturerId, String locale, Integer requestedDay) {
        List<LecturerScheduleResponse> teaching = sections.findLecturerSchedule(lecturerId, null);
        if (teaching.isEmpty()) {
            return noClassesMessage(locale);
        }
        List<Slot> slots = new ArrayList<>();
        for (LecturerScheduleResponse section : teaching) {
            String label = courseName(section.courseCode(), locale.equals("en")
                    ? section.courseNameEn() : section.courseNameVi(), section.courseName());
            for (io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.SectionScheduleResponse schedule
                    : section.schedules()) {
                slots.add(new Slot(
                        schedule.dayOfWeek(),
                        schedule.startTime(),
                        schedule.endTime(),
                        schedule.classroom() != null
                                ? trimJoin(schedule.classroom().building(), schedule.classroom().roomNumber())
                                : null,
                        label,
                        section.sectionNumber()));
            }
        }
        if (slots.isEmpty()) {
            return noClassesMessage(locale);
        }
        return timetableAnswer(slots, null, locale, requestedDay, true);
    }

    private String timetableAnswer(List<Slot> slots, String termName, String locale, Integer requestedDay, boolean isLecturer) {
        boolean vi = "vi".equals(locale);
        if (requestedDay != null) {
            int targetDay = requestedDay == 0 ? 7 : requestedDay;
            String dayLabel = (vi ? DAY_LABELS_VI : DAY_LABELS_EN)[targetDay];
            List<Slot> daySlots = slots.stream()
                    .filter(s -> (s.dayOfWeek() == 0 ? 7 : s.dayOfWeek()) == targetDay)
                    .sorted(Comparator.comparing(Slot::startTime))
                    .toList();

            if (daySlots.isEmpty()) {
                if (isLecturer) {
                    return vi
                            ? "Theo lịch phân công hiện tại, bạn không có ca giảng dạy nào vào " + dayLabel + ".\n\n"
                                    + "Bạn có thể xem lịch các ngày khác ở trang Lịch giảng dạy."
                            : "According to your current teaching assignments, you have no teaching sessions on " + dayLabel + ".\n\n"
                                    + "You can check other days on the Teaching Schedule page.";
                } else {
                    return vi
                            ? "Theo thời khóa biểu hiện tại, bạn không có lịch học vào " + dayLabel + ".\n\n"
                                    + "Bạn có thể xem lịch các ngày khác ở trang Thời khóa biểu."
                            : "According to your current timetable, you have no classes on " + dayLabel + ".\n\n"
                                    + "You can check your schedule for other days on the Schedule page.";
                }
            }

            StringBuilder answer = new StringBuilder();
            if (isLecturer) {
                answer.append(vi
                        ? "Lịch giảng dạy " + dayLabel + " của bạn:\n"
                        : "Your " + dayLabel + " teaching schedule:\n");
            } else {
                answer.append(vi
                        ? "Lịch học " + dayLabel + " của bạn"
                        : "Your " + dayLabel + " class schedule");
                if (StringUtils.hasText(termName)) {
                    answer.append(" (" + termName + ")");
                }
                answer.append(":\n");
            }

            for (Slot slot : daySlots) {
                answer.append("\n• ").append(dayLabel)
                        .append(" ").append(slot.startTime()).append("-").append(slot.endTime());
                if (StringUtils.hasText(slot.label())) {
                    answer.append(" — ").append(slot.label());
                }
                if (StringUtils.hasText(slot.room())) {
                    answer.append(vi ? " (phòng " : " (room ").append(slot.room()).append(")");
                }
                answer.append("\n");
            }

            answer.append(vi
                    ? (isLecturer ? "\nBạn có thể xem lịch dạng lưới ở trang Lịch giảng dạy." : "\nBạn có thể xem lịch dạng lưới ở trang Thời khóa biểu.")
                    : (isLecturer ? "\nYou can see this as a weekly grid on the Teaching Schedule page." : "\nYou can see this as a weekly grid on the Schedule page."));
            return answer.toString();
        }

        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? (isLecturer ? "Lịch giảng dạy của bạn" : "Lịch học cá nhân của bạn")
                : (isLecturer ? "Your teaching schedule" : "Your personal class schedule"));
        if (StringUtils.hasText(termName)) {
            answer.append(vi ? " (" + termName + ")" : " (" + termName + ")");
        }
        answer.append(vi ? ":\n" : ":\n");
        slots.stream()
                .sorted(Comparator
                        .comparingInt(Slot::dayOfWeek)
                        .thenComparing(Slot::startTime))
                .forEach(slot -> {
                    int day = slot.dayOfWeek() == 0 ? 7 : slot.dayOfWeek();
                    String dayLabel = (vi ? DAY_LABELS_VI : DAY_LABELS_EN)[day];
                    answer.append("\n• ").append(dayLabel)
                            .append(" ").append(slot.startTime()).append("-").append(slot.endTime());
                    if (StringUtils.hasText(slot.label())) {
                        answer.append(" — ").append(slot.label());
                    }
                    if (StringUtils.hasText(slot.room())) {
                        answer.append(vi ? " (phòng " : " (room ").append(slot.room()).append(")");
                    }
                    answer.append("\n");
                });
        answer.append(vi
                ? (isLecturer ? "\nBạn có thể xem lịch dạng lưới ở trang Lịch giảng dạy." : "\nBạn có thể xem lịch dạng lưới ở trang Thời khóa biểu.")
                : (isLecturer ? "\nYou can see this as a weekly grid on the Teaching Schedule page." : "\nYou can see this as a weekly grid on the Schedule page."));
        return answer.toString();
    }

    private static Integer detectRequestedDay(String message) {
        if (!StringUtils.hasText(message)) return null;
        String lower = message.toLowerCase();

        if (lower.contains("hôm nay") || lower.contains("hom nay") || lower.contains("today")) {
            java.time.DayOfWeek dow = java.time.LocalDate.now(AssistantTimezone.ZONE).getDayOfWeek();
            return dow == java.time.DayOfWeek.SUNDAY ? 1 : dow.getValue() + 1;
        }
        if (lower.contains("ngày mai") || lower.contains("ngay mai") || lower.contains("tomorrow")) {
            java.time.DayOfWeek dow = java.time.LocalDate.now(AssistantTimezone.ZONE).plusDays(1).getDayOfWeek();
            return dow == java.time.DayOfWeek.SUNDAY ? 1 : dow.getValue() + 1;
        }

        if (Pattern.compile("thứ\\s*(?:hai|2)|\\bt2\\b|monday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 2;
        if (Pattern.compile("thứ\\s*(?:ba|3)|\\bt3\\b|tuesday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 3;
        if (Pattern.compile("thứ\\s*(?:tư|bốn|4)|\\bt4\\b|wednesday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 4;
        if (Pattern.compile("thứ\\s*(?:năm|5)|\\bt5\\b|thursday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 5;
        if (Pattern.compile("thứ\\s*(?:sáu|6)|\\bt6\\b|friday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 6;
        if (Pattern.compile("thứ\\s*(?:bảy|7)|\\bt7\\b|saturday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 7;
        if (Pattern.compile("chủ\\s*nhật|chu\\s*nhat|\\bcn\\b|sunday", Pattern.CASE_INSENSITIVE).matcher(lower).find()) return 1;

        return null;
    }

    private static String courseLabel(CourseSummary course, String locale) {
        if (course == null) {
            return null;
        }
        String localized = "en".equals(locale) ? course.nameEn() : course.nameVi();
        return StringUtils.hasText(localized) ? localized : course.name();
    }

    private static String courseName(String code, String localized, String fallback) {
        String name = StringUtils.hasText(localized) ? localized : fallback;
        return StringUtils.hasText(code)
                ? StringUtils.hasText(name) ? code + " - " + name : code
                : name;
    }

    private static String semesterLabel(SemesterSummary semester, String locale) {
        if (semester == null) {
            return null;
        }
        return "en".equals(locale)
                ? StringUtils.hasText(semester.nameEn()) ? semester.nameEn() : semester.name()
                : StringUtils.hasText(semester.nameVi()) ? semester.nameVi() : semester.name();
    }

    private static String noClassesMessage(String locale) {
        return "vi".equals(locale)
                ? "Bạn hiện chưa có lớp học phần nào đang hoạt động, nên mình chưa có lịch học để hiển thị. "
                        + "Bạn có thể đăng ký học phần ở khu Đăng ký học phần."
                : "You have no active course sections right now, so there is no personal schedule to show. "
                        + "You can register for sections in the Course Registration area.";
    }

    /** Friendly empty answer for the enrollment-list intent. */
    private static String noEnrollmentsMessage(boolean vi) {
        return vi
                ? "Bạn chưa đăng ký lớp học phần nào trong học kỳ hiện tại. "
                        + "Bạn có thể đăng ký học phần ở khu Đăng ký học phần."
                : "You are not registered in any course section for the current term. "
                        + "You can register for sections in the Course Registration area.";
    }

    private static String fallbackMessage(String locale) {
        return "vi".equals(locale)
                ? "Mình chưa xem được dữ liệu cá nhân của bạn lúc này. Bạn thử lại sau, hoặc mở trang Thời khóa biểu / Bảng điểm để xem trực tiếp nhé."
                : "I could not read your personal records right now. Please try again later, or check the Schedule / Grades pages directly.";
    }

    private static String normalizedLocale(ChatRequest request) {
        return request != null && "en".equalsIgnoreCase(request.locale()) ? "en" : "vi";
    }

    private static String claim(Jwt actor, String name) {
        if (actor == null) {
            return null;
        }
        Object value = actor.getClaims().get(name);
        return value instanceof String text && StringUtils.hasText(text) ? text : null;
    }

    private static String trimJoin(String left, String right) {
        if (!StringUtils.hasText(left)) {
            return StringUtils.hasText(right) ? right : null;
        }
        if (!StringUtils.hasText(right)) {
            return left;
        }
        return left + " " + right;
    }

    private record Slot(int dayOfWeek, String startTime, String endTime, String room, String label, String sectionNumber) { }
}
