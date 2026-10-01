package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.academic.registration.CreditLimitApplicationService;
import io.campuscore.restfulapi.academic.registration.RegistrationDtos.SummaryResponse;
import io.campuscore.restfulapi.academic.registration.RegistrationService;
import io.campuscore.restfulapi.academic.service.AcademicAttendanceReadService;
import io.campuscore.restfulapi.academic.service.AcademicConductService;
import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicAttendanceReadDtos.AttendanceResponse;
import io.campuscore.restfulapi.academic.web.AcademicConductDtos.StudentConductSummaryDto;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.GradeSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerGradingSectionResponse;
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

    /**
     * Thesis group size requirement. Mirrors the private
     * {@code ThesisMutationService.MIN_GROUP_MEMBERS}/{@code MAX_GROUP_MEMBERS}
     * (thesis/service/ThesisMutationService.java:44-45) and the same 3–4 band
     * the progress read path flags as {@code GROUP_INVALID_MEMBER_COUNT}.
     */
    private static final int GROUP_MIN_MEMBERS = 3;
    private static final int GROUP_MAX_MEMBERS = 4;

    private static final Pattern SCHEDULE_INTENT = Pattern.compile(
            "lịch\\s*(?:học|dạy|giảng\\s*dạy|tuần|hôm\\s*nay|ngày\\s*mai|của\\s*tôi|thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|chủ\\s*nhật|t[2-7]|cn)"
                    + "|lich\\s*(?:hoc|day|giang\\s*day|tuan|hom\\s*nay|ngay\\s*mai|cua\\s*toi|thu\\s*[2-7]|thu\\s*(?:hai|ba|tu|bon|nam|sau|bay)|chu\\s*nhat|t[2-7]|cn)"
                    + "|thời\\s*(?:khoá|khóa|khoa)\\s*biểu|thoi\\s*khoa\\s*bieu|\\btkb\\b"
                    + "|(?:thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|hôm\\s*nay|ngày\\s*mai|chủ\\s*nhật|hom\\s*nay|ngay\\s*mai|chu\\s*nhat)"
                    // Audit ca-nhan Q2 / giang-vien Q7: "Hôm nay tôi có lớp học
                    // không?" and "Thứ Hai hàng tuần tôi có môn nào, học mấy giờ,
                    // ở phòng nào?" fell to the KB path because the noun group
                    // had no "lớp" and the week-qualifier / first-person gap was
                    // hard-capped at adjacent whitespace.
                    + "(?:\\s*(?:hàng|mỗi|hang|moi)\\s*(?:tuần|ngày|tuan|ngay))?"
                    + "(?:[^?!.]{0,15}?tôi)?\\s*(?:có\\s*)?(?:học|dạy|lịch|tiết|môn|buổi|ca|lớp|lop\\b)"
                    + "|học\\s*ngày\\s*nào|hoc\\s*ngay\\s*nao|m[oô]n\\s*nào\\s*học|mon\\s*nao\\s*hoc|tiết\\s*học|buổi\\s*học|ca\\s*học|ca\\s*dạy|tiết\\s*dạy"
                    // Lecturer teaching-load phrasings ("Kỳ này tôi phụ trách
                    // dạy những lớp học phần nào?") are the asker's own
                    // teaching timetable, not a public catalog question.
                    + "|phụ\\s*trách\\s*(?:dạy|giảng)|phu\\s*trach\\s*(?:day|giang)"
                    + "|lớp\\s*(?:học\\s*phần\\s*)?nào[^?!.]{0,40}?(?:dạy|phụ\\s*trách)|lop\\s*(?:hoc\\s*phan\\s*)?nao[^?!.]{0,40}?(?:day|phu\\s*trach)"
                    // xrole-6: "Học kỳ này tôi phụ trách những lớp nào?" — the
                    // verb sits BEFORE the lớp noun, which both lớp-first
                    // alternatives above miss, and there is no "dạy" after
                    // phụ trách for the verb-first line to catch. The
                    // interrogative "nào" stays required so a public "ai phụ
                    // trách lớp này" question does not become a personal
                    // timetable request. Wukong: the branch must carry a
                    // first-person marker — a third-person "Giáo viên phụ trách
                    // lớp nào?" is public catalog knowledge, not the asker's
                    // own timetable.
                    + "|tôi\\s*[^?!.]{0,12}?phụ\\s*trách[^?!.]{0,25}?lớp\\s*(?:học\\s*phần\\s*)?nào"
                    + "|toi\\s*[^?!.]{0,12}?phu\\s*trach[^?!.]{0,25}?lop\\s*(?:hoc\\s*phan\\s*)?nao"
                    + "|(my\\s+)?(class\\s+|teaching\\s+)?schedule|timetable|my\\s+classes|(classes|teaching)\\s+(today|tomorrow|on\\s+\\w+)"
                    // Round-3 chat-4: "Do I have class today?" and "What classes
                    // do I have today?" fell to the KB path and the model denied
                    // any schedule exists — while the Vietnamese twin answered
                    // correctly. Both natural EN phrasings route to the personal
                    // timetable (detectRequestedDay already understands today).
                    + "|(?:do|does)\\s+i\\s+(?:have|got)\\s+(?:any\\s+)?(?:class(?:es)?|teaching|lessons?)\\b"
                    + "|(?:what|which)\\s+classes?\\s+(?:do\\s+i\\s+have|am\\s+i\\s+(?:taking|having))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Policy wording that turns a supervision phrase into a general knowledge
     * question. "Trường quy định bao nhiêu sinh viên hướng dẫn tối đa?" asks
     * about the rule, not the asker's own workload, so it must stay on the
     * knowledge path (same convention as {@link #THESIS_POLICY_OR_GENERAL_INTENT}).
     */
    private static final Pattern WORDING_POLICY_INTENT = Pattern.compile(
            "quy\\s*định|quy\\s*dinh|điều\\s*kiện|dieu\\s*kien|quy\\s*chế|quy\\s*che"
                    // The trailing \b on the tone-free "toi da" is load-bearing:
                    // without it the pattern matched the "da" inside "dang" and
                    // blocked "nhung nhom sinh vien nao toi dang huong dan" —
                    // an ordinary supervision question that merely begins the
                    // word "đang", not the policy word "tối đa". The accented
                    // "tối đa" needs no boundary because the diacritics already
                    // make it a distinct token.
                    + "|tối\\s*đa|toi\\s*da\\b|hạn\\s*mức|han\\s*muc|chính\\s*sách",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lecturer supervision-workload phrasings ("Khối lượng hướng dẫn của tôi
     * hiện tại là bao nhiêu?", "Tôi đang hướng dẫn tổng cộng bao nhiêu sinh
     * viên?") are personal workload questions even without a thesis noun.
     */
    private static final Pattern LECTURER_WORKLOAD_INTENT = Pattern.compile(
            "khối\\s*lượng\\s*(?:hướng\\s*dẫn|công\\s*tác)|khoi\\s*luong\\s*(?:huong\\s*dan|cong\\s*tac)"
                    + "|hướng\\s*dẫn\\s*tổng\\s*cộng|huong\\s*dan\\s*tong\\s*cong"
                    + "|bao\\s*nhiêu\\s*sinh\\s*viên\\s*hướng\\s*dẫn|bao\\s*nhieu\\s*sinh\\s*vien\\s*huong\\s*dan"
                    // xrole-7: "Những nhóm sinh viên nào tôi đang hướng dẫn?" —
                    // the group noun sits before the verb, and the ongoing-verb
                    // form sits before the group/student noun. Both orders route
                    // to the existing workload composer (topics + group counts);
                    // the first-person pronoun and policy-wording gates in
                    // isLecturerWorkloadIntent still exclude rule questions like
                    // "Trường quy định bao nhiêu nhóm hướng dẫn tối đa?".
                    + "|(?:nhóm|nhom)[^?!.]{0,40}?(?:hướng\\s*dẫn|huong\\s*dan)"
                    + "|(?:đang|dang)\\s*(?:hướng\\s*dẫn|huong\\s*dan)[^?!.]{0,40}?(?:nhóm|nhom|sinh\\s*viên|sinh\\s*vien)"
                    + "|(?:my|total)\\s+(?:thesis\\s+)?workload|supervis\\w*\\s+(?:how\\s+many|total)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lecturer grade-entry phrasings ("Điểm học phần tôi phụ trách hiện đã có
     * chưa?") ask about the sections the asker teaches, not the asker's own
     * transcript — gated against {@link #isGradesIntent(String)}.
     */
    private static final Pattern LECTURER_GRADING_INTENT = Pattern.compile(
            "điểm\\s*học\\s*phần[^?!.]{0,30}(?:phụ\\s*trách|dạy)"
                    + "|(?:phụ\\s*trách|dạy)[^?!.]{0,30}điểm[^?!.]{0,30}(?:đã\\s*)?(?:có|nhập|chưa)"
                    + "|grading\\s+status",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Attendance wording next to a concrete section code ("SE401 ... vắng mặt"). */
    private static final Pattern ATTENDANCE_WORDING_INTENT = Pattern.compile(
            "vắng|nghỉ|học\\s*đủ|học\\s*du|chuyên\\s*cần|chuyen\\s*can|absent|attendance",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Teaching-list hint inside an enrollment-list phrasing — a lecturer asking
     * "Kỳ này tôi phụ trách dạy những lớp học phần nào?" wants their own
     * teaching assignment list, not the student enrollment list.
     */
    private static final Pattern LECTURER_TEACHING_LIST_HINT = Pattern.compile(
            "phụ\\s*trách\\s*dạy|phu\\s*trach\\s*day"
                    + "|lớp\\s*(?:học\\s*phần\\s*)?nào[^?!.]{0,40}?(?:dạy|phụ\\s*trách)"
                    + "|lop\\s*(?:hoc\\s*phan\\s*)?nao[^?!.]{0,40}?(?:day|phu\\s*trach)"
                    // "Học kỳ này tôi phụ trách những lớp nào?" — phụ trách sits
                    // BEFORE the lớp noun, which the lớp-first alternatives miss.
                    + "|phụ\\s*trách[^?!.]{0,25}?lớp|phu\\s*trach[^?!.]{0,25}?lop",
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
                    // The suffix group is deliberately NOT optional: with a
                    // trailing "?" the bare word "điểm" matched every message
                    // containing it ("điểm chuẩn ngành X") and the first-person
                    // gate then served the student's own transcript instead of
                    // the public knowledge path. A bare "điểm" only counts as a
                    // personal-grades signal when a possessive follows.
                    + "|(?:điểm|diem|điem)\\s*(?:số|so|tổng\\s*kết|tong\\s*ket|thành\\s*phần|thanh\\s*phan|quá\\s*trình|qua\\s*trinh|học\\s*kỳ|hoc\\s*ky|học\\s*tập|hoc\\s*tap)"
                    // Audit quét toàn hệ thống chatbot-1: the hybrid
                    // missing-diacritic form "điem" (đ kept, tone dropped) is
                    // the most common fast-typing variant and matched neither
                    // stem; CASE_INSENSITIVE does not fold đ↔d.
                    + "|(?:điểm|diem|điem)\\s*(?:của|cua)\\s*(?:tôi|mình|em|toi|minh)"
                    // Audit ca-nhan Q6: "Điểm các môn của tôi trong học kỳ 2..."
                    // — the possessive can sit a few words after "điểm"; the
                    // short lazy gap keeps it personal while the first-person
                    // gate in isGradesIntent still excludes "điểm chuẩn..."
                    // policy wording with no "của tôi".
                    + "|(?:điểm|diem|điem)[^?!.]{0,12}?(?:của|cua)\\s*(?:tôi|mình|em)\\b"
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
            // Round-3 chat-2: "môn" (subject) joins the noun group —
            // "Học kỳ 1 năm học 2025-2026 tôi học những môn nào?" hit the KB
            // path, which answered "chưa công bố danh sách môn" while the
            // transcript carried six graded courses for exactly that semester.
            "(?:lớp|lop|học\\s*phần|hoc\\s*phan|môn|mon|class|course|subject).*?(?:nào|nao|gì|gi|which)\\b"
                    + "|(?:đã\\s*)?đăng\\s*ký.*?(?:lớp|lop|học\\s*phần|hoc\\s*phan|class)"
                    + "|(?:da\\s*)?dang\\s*ky.*?(?:lop|hoc\\s*phan|class)"
                    + "|(?:lớp|lop|học\\s*phần|hoc\\s*phan).*?(?:đã\\s*)?đăng\\s*ký"
                    + "|registered\\s+(?:in|for)\\s+(?:any\\s+|my\\s+)?(?:classes|sections?|courses?)"
                    + "|registere?d\\s+(?:in|for)\\b"
                    // Round-3 chat-5: "Which classes am I taking?" — the
                    // interrogative sits BEFORE the noun, which the noun-first
                    // alternative above cannot reach, and the existing EN
                    // branches only know "registered in/for".
                    + "|(?:which|what)\\s+(?:classes?|courses?|subjects?)\\s+(?:am\\s+i|do\\s+i)\\b"
                    + "|am\\s+i\\s+(?:taking|attending|enrolled\\s+in)[^?!.]{0,20}?(?:class|course|subject)",
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
            // The verb phrase may sit either side of the quantity:
            // "còn bao nhiêu tín chỉ" AND "còn được đăng ký bao nhiêu tín chỉ nữa"
            // (the latter fell to the KB path and answered "no data" while
            // /me/registration/summary reports creditsRemaining).
            "còn\\s*(?:được\\s*đăng\\s*ký\\s*)?(?:thêm\\s*)?bao\\s*nhiêu\\s*(?:nữa\\s*)?tín\\s*chỉ"
                    + "|con\\s*(?:duoc\\s*dang\\s*ky\\s*)?(?:them\\s*)?bao\\s*nhieu\\s*(?:nua\\s*)?tin\\s*chi"
                    + "|còn\\s*lạ[ií]\\s*(?:được\\s*)?bao\\s*nhiêu\\s*tín\\s*chỉ|con\\s*lai\\s*(?:duoc\\s*)?bao\\s*nhieu\\s*tin\\s*chi"
                    + "|còn\\s*thiếu\\s*(?:mấy|bao\\s*nhiêu)\\s*tín\\s*chỉ|con\\s*thieu\\s*(?:may|bao\\s*nhieu)\\s*tin\\s*chi"
                    + "|hạn\\s*mức\\s*tín\\s*chỉ|han\\s*muc\\s*tin\\s*chi"
                    // Round-3 chat-3: "How many credits remain?" — the verb
                    // form "remain" was absent while "remaining" and the VI
                    // twin both matched; the EN question got an LLM denial
                    // contradicting /me/registration/summary (creditsRemaining).
                    + "|credits?\\s+(?:do\\s+i\\s+have\\s+)?(?:left|remaining|remains?|to\\s+register)|remaining\\s+credits?"
                    + "|how\\s+many\\s+credits\\s+(?:can|do)\\s+i\\s+(?:still\\s+)?(?:register|take)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Credit-limit policy wording ("xin nâng hạn mức") stays on the knowledge path. */
    private static final Pattern CREDITS_POLICY_INTENT = Pattern.compile(
            "nâng\\s*hạn\\s*mức|nang\\s*han\\s*muc|quy\\s*trình|quy\\s*trinh|đơn\\s*xin|don\\s*xin",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Accumulated-credits questions ("Tôi đã tích lũy được bao nhiêu tín chỉ?")
     * ask for the transcript's cumulative earned total (38 credits, best attempt
     * per course) — a different number from the registration budget, and the
     * audit caught it being answered with the credit-LIMIT regulation instead
     * (ca-nhan Q11, RAG_GROUNDED fallback oan).
     */
    private static final Pattern CREDITS_ACCUMULATED_INTENT = Pattern.compile(
            "(?:đã|da)\\s*(?:tích\\s*lũy|tich\\s*luy|tích\\s*luy|hoàn\\s*thành|hoan\\s*thanh)"
                    + "|(?:tích\\s*lũy|tich\\s*luy|tích\\s*luy)[^?!.]{0,20}?(?:bao\\s*nhiêu|bao\\s*nhieu|may|được|duoc)"
                    + "|(?:bao\\s*nhiêu|bao\\s*nhieu|how\\s+many)\\s*(?:tín\\s*chỉ|tin\\s*chi|credits?)[^?!.]{0,25}?(?:đã|da|tích\\s*lũy|tich\\s*luy|accumulated|earned)"
                    + "|how\\s+many\\s+credits[^?!.]{0,20}?(?:have\\s+i|did\\s+i|i)\\s*(?:accumulated|earned)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Lecturer teaching-load credits ("Học kỳ này tôi dạy tất cả bao nhiêu tín
     * chỉ?") — sum of the credits field over the asker's assigned sections.
     * The system holds the data (the same schedule the timetable answer is
     * built from), so the LLM's "không có thông tin" was a fallback oan
     * (giang-vien Q7, high).
     */
    private static final Pattern LECTURER_TEACHING_CREDITS_INTENT = Pattern.compile(
            "(?:dạy|day|giảng|giang|phụ\\s*trách|phu\\s*trach)[^?!.]{0,30}?(?:bao\\s*nhiêu|mấy|may|tổng\\s*số|tong\\s*so)"
                    + "[^?!.]{0,15}?(?:tín\\s*chỉ|tin\\s*chi|credits?)"
                    + "|(?:bao\\s*nhiêu|mấy|may|tổng\\s*số|tong\\s*so)\\s*(?:tín\\s*chỉ|tin\\s*chi|credits?)[^?!.]{0,30}?(?:dạy|day|giảng|giang|phụ\\s*trách|phu\\s*trach)"
                    + "|how\\s+many\\s+credits[^?!.]{0,30}?(?:teach|lectur)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Identity phrasing inside a supervision question ("Tôi đang hướng dẫn
     * những sinh viên nào?") wants the student NAMES, not the workload counts
     * the generic supervision answer prints (giang-vien Q5, medium).
     */
    private static final Pattern ADVISEE_ROSTER_INTENT = Pattern.compile(
            "(?:những\\s*sinh\\s*viên|nhung\\s*sinh\\s*viên|các\\s*sinh\\s*viên|cac\\s*sinh\\s*viên|sv)[^?!.]{0,25}?(?:nào|nao|ai|được|duoc)"
                    + "|(?:hướng\\s*dẫn|huong\\s*dan)[^?!.]{0,25}?(?:những\\s*ai|who|which\\s+students?|advisee)"
                    + "|(?:who|which)\\s+(?:students?|advisees?)[^?!.]{0,25}?(?:supervis|advise|am\\s+i\\s+teaching)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Graduation-credit questions that carry the requirement number in the
     * sentence ("Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?").
     * Two or three digits directly before "tín chỉ ... tốt nghiệp". A bare
     * "còn thiếu bao nhiêu tín chỉ" without the graduation number keeps the
     * registration-budget answer. Checked BEFORE
     * {@link #isCreditsRemainingIntent(String)} so the semester limit cannot
     * hijack the question.
     */
    private static final Pattern GRADUATION_CREDITS_INTENT = Pattern.compile(
            "(\\d{2,3})\\s*tín\\s*ch[^?!.]{0,15}tốt\\s*nghiệp"
                    + "|(\\d{2,3})\\s*tin\\s*chi[^?!.]{0,15}tot\\s*nghiep",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * Round-3 chat-6: a semester the asker NAMED ("Học kỳ 1 năm học 2025-2026",
     * "semester 2 2025-2026"). The grades answer used to ignore it and always
     * print the newest semester — asking for HK1 (GPA 3.16) returned HK2 (3.03)
     * with no disclaimer. When a named semester parses, grades and course-list
     * answers filter to it; when it cannot be found in the transcript, the
     * answer says so instead of silently substituting another semester.
     */
    private static final Pattern NAMED_SEMESTER = Pattern.compile(
            "(?:h[oô]c\\s*k[ìy]|hoc\\s*ky)\\s*([1-3])(?:\\s*n[aă]m\\s*h[oô]c\\s*)?\\s*((?:19|20)\\d{2})\\s*[-–]\\s*((?:19|20)?\\d{2})"
                    + "|semester\\s*([1-3])[^\\d?!.]{0,24}((?:19|20)\\d{2})\\s*[-–]\\s*((?:19|20)?\\d{2})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** A named semester parsed out of the message: term number plus starting year. */
    private record NamedSemester(int term, String yearStart) { }

    private static NamedSemester parseNamedSemester(String message) {
        if (!StringUtils.hasText(message)) return null;
        java.util.regex.Matcher matcher = NAMED_SEMESTER.matcher(message);
        if (!matcher.find()) return null;
        String term = matcher.group(1) != null ? matcher.group(1) : matcher.group(4);
        String yearStart = matcher.group(2) != null ? matcher.group(2) : matcher.group(5);
        if (term == null || yearStart == null) return null;
        return new NamedSemester(Integer.parseInt(term), yearStart);
    }

    /** True when a grade row's semester label IS the named semester (folded compare). */
    private static boolean isNamedSemesterRow(GradeSummary grade, NamedSemester semester) {
        if (semester == null || grade == null) return false;
        String label = firstText(grade.semesterNameVi(), firstText(grade.semesterNameEn(), grade.semester()));
        if (!StringUtils.hasText(label)) return false;
        String folded = AssistantInputGuard.foldForMatching(label)
                .replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
        boolean termHit = folded.matches(".*\\b(?:hoc ky|semester)\\s*" + semester.term() + "\\b.*");
        return termHit && folded.contains(semester.yearStart());
    }

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
        // Requirement wording ("What courses do I need to graduate?") is a
        // curriculum-policy question, not a listing of current registrations.
        if (Pattern.compile("need(?:s)?\\s+to\\s+(?:take|complete|graduate)|to\\s+graduate"
                        + "|ph[ảa]i\\s*h[oọ]c|để\\s*tốt\\s*nghiệp|de\\s*tot\\s*nghiep",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(message).find()) {
            return false;
        }
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

    /**
     * "Tôi đã tích lũy được bao nhiêu tín chỉ?" — accumulated, not remaining.
     * Policy/graduation and how-to wording keep their own (or the knowledge)
     * path; the first-person gate stops public "sinh viên tích lũy bao nhiêu
     * tín chỉ để tốt nghiệp" rule questions from opening the personal record.
     */
    private static boolean isCreditsAccumulatedIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (isCreditsRemainingIntent(message)) return false;
        if (isGraduationCreditsIntent(message)) return false;
        if (ENROLLMENT_HOWTO_INTENT.matcher(message).find()) return false;
        if (CREDITS_POLICY_INTENT.matcher(message).find()) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && CREDITS_ACCUMULATED_INTENT.matcher(message).find();
    }

    /** Lecturer teaching-credits total; policy/how-to wording stays knowledge. */
    private static boolean isLecturerTeachingCreditsIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (ENROLLMENT_HOWTO_INTENT.matcher(message).find()) return false;
        if (CREDITS_POLICY_INTENT.matcher(message).find()) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && LECTURER_TEACHING_CREDITS_INTENT.matcher(message).find();
    }

    /** Supervision question that asks for the students' identities (roster). */
    private static boolean isAdviseeRosterIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (WORDING_POLICY_INTENT.matcher(message).find()) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && Pattern.compile("hướng\\s*dẫn|huong\\s*dan|supervis|advise",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(message).find()
                && ADVISEE_ROSTER_INTENT.matcher(message).find();
    }

    /**
     * "Tôi học còn bao nhiêu môn chưa có điểm?" — count of in-progress
     * enrolled courses still awaiting published grades (audit quét toàn hệ
     * thống chatbot-2: the question fell to the prerequisite-regulation KB
     * while the transcript data showed exactly 5 pending courses). The
     * FIRST_PERSON gate keeps the public rule question on the knowledge path.
     */
    private static final Pattern PENDING_GRADES_INTENT = Pattern.compile(
            "(?:bao\\s*nhiêu|mấy|bao\\s*nhieu|how\\s+many)[^?!.]{0,30}?(?:môn|mon|học\\s*phần|hoc\\s*phan|courses?|subjects?)"
                    + "[^?!.]{0,35}?(?:chưa\\s*có\\s*điểm|chua\\s*co\\s*diem|chưa\\s*có\\s*điểm\\s*công\\s*bố|no\\s+grade|not\\s+graded|awaiting\\s+grade)"
                    + "|(?:chưa\\s*có\\s*điểm|chua\\s*co\\s*diem)[^?!.]{0,30}?(?:bao\\s*nhiêu|mấy|bao\\s*nhieu)"
                    + "|how\\s+many\\s+(?:of\\s+my\\s+)?courses?[^?!.]{0,30}?(?:still\\s+)?(?:have\\s+no|without)\\s+grade",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static boolean isPendingGradesIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (ENROLLMENT_HOWTO_INTENT.matcher(message).find()) return false;
        if (CREDITS_POLICY_INTENT.matcher(message).find()) return false;
        // Round-2 sweep chat-3: Vietnamese drops the pronoun constantly
        // ("còn bao nhiêu môn chưa có điểm") — the FIRST_PERSON gate used to
        // route exactly that to the KB miss. The pattern itself is specific
        // enough (môn + chưa có điểm) that the pronoun gate is redundant.
        return PENDING_GRADES_INTENT.matcher(message).find();
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

    /** Lecturer supervision-workload question; policy wording stays on the knowledge path. */
    private static boolean isLecturerWorkloadIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (WORDING_POLICY_INTENT.matcher(message).find()) return false;
        // How-to / advice wording ("ở trang nào", "có nên ... mới") is a
        // knowledge question even when it names supervision groups — answering
        // it with the asker's own workload list was a Wukong-flagged drift.
        if (Pattern.compile("trang\\s*nào|có\\s*nên|nên\\s*không|ở\\s*đâu",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(message).find()) {
            return false;
        }
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && LECTURER_WORKLOAD_INTENT.matcher(message).find();
    }

    /** Lecturer grade-entry question; the asker's own transcript wording wins instead. */
    private static boolean isLecturerGradingIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (isGradesIntent(message)) return false;
        return FIRST_PERSON_PRONOUN.matcher(message).find()
                && LECTURER_GRADING_INTENT.matcher(message).find();
    }

    /** Attendance wording next to a concrete section code; policy wording stays on the knowledge path. */
    private static boolean isAttendanceIntent(String message) {
        if (!StringUtils.hasText(message)) return false;
        if (WORDING_POLICY_INTENT.matcher(message).find()) return false;
        return SECTION_CODE.matcher(message).find()
                && ATTENDANCE_WORDING_INTENT.matcher(message).find();
    }

    /** "…đủ 143 tín chỉ tốt nghiệp?" — carries the graduation requirement number in the sentence. */
    private static boolean isGraduationCreditsIntent(String message) {
        return StringUtils.hasText(message) && GRADUATION_CREDITS_INTENT.matcher(message).find();
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
    private final AcademicAttendanceReadService academicAttendance;
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

    /** Compatibility constructor retained for focused advisor tests. */
    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections,
            ThesisLecturerWorkloadService lecturerWorkload,
            NamedParameterJdbcTemplate jdbc,
            AcademicConductService conductService,
            RegistrationService registrationService) {
        this(enrollments, sections, lecturerWorkload, jdbc, conductService, registrationService, null);
    }

    @Autowired
    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections,
            @Autowired(required = false) ThesisLecturerWorkloadService lecturerWorkload,
            @Autowired(required = false) NamedParameterJdbcTemplate jdbc,
            @Autowired(required = false) AcademicConductService conductService,
            @Autowired(required = false) RegistrationService registrationService,
            @Autowired(required = false) AcademicAttendanceReadService academicAttendance) {
        this.enrollments = enrollments;
        this.sections = sections;
        this.lecturerWorkload = lecturerWorkload;
        this.jdbc = jdbc;
        this.conductService = conductService;
        this.registrationService = registrationService;
        this.academicAttendance = academicAttendance;
    }

    /** True when the question is clearly about personal schedule, enrollment list, grades, conduct, or thesis status. */
    public boolean handles(String message) {
        if (message == null) return false;
        // Exam timetables are a public knowledge topic even though they share
        // the "lịch" noun with personal timetables — never answer them from
        // the asker's own teaching/attendance rows.
        if (EXAM_SCHEDULE_INTENT.matcher(message).find()) return false;
        return SCHEDULE_INTENT.matcher(message).find()
                || isLecturerWorkloadIntent(message)
                || isLecturerGradingIntent(message)
                || isAttendanceIntent(message)
                || isThesisPersonalIntent(message)
                || isConductIntent(message)
                || isGradesIntent(message)
                || isEnrollmentListIntent(message)
                || isGraduationCreditsIntent(message)
                || isCreditsRemainingIntent(message)
                || isCreditsAccumulatedIntent(message)
                || isLecturerTeachingCreditsIntent(message)
                || isPendingGradesIntent(message)
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
                    locale, List.of(), UUID.randomUUID(),
                    request != null ? request.clientRequestId() : null, null, false, null, null, null);
        }
        if (answer == null) {
            return null;
        }
        if (!AssistantOutputGuard.isSafe(answer)) {
            answer = ThesisAssistantService.technicalOutputMessage(locale);
        }
        // xrole-15: the client correlates intercepted personal answers with its
        // pending request by clientRequestId, exactly like the RAG and rejected
        // paths (ThesisAssistantController echoes request.clientRequestId()).
        // The six-argument constructor left it null, so the answer could not be
        // matched to the request it belongs to.
        return new ChatResponse(answer, MODEL, false, REASON_CODE, locale, List.of(),
                UUID.randomUUID(), request != null ? request.clientRequestId() : null,
                null, false, null, null, null);
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
        String lecturerId = claim(actor, "lecturerId");
        // Lecturer-only branches: each one must require BOTH the lecturer
        // profile claim AND its service. A student-actor JWT (no lecturerId)
        // falls through to RAG instead of hitting a read service that would
        // throw 403 (requireProfileId) on the missing profile claim — answer()
        // only catches DataAccessException, so that 403 would escape as an
        // HTTP error for an innocent question.
        if (isLecturerWorkloadIntent(message)) {
            if (StringUtils.hasText(lecturerId) && lecturerWorkload != null) {
                // Identity phrasing gets a named roster, not the count
                // boilerplate the generic supervision answer prints
                // (audit giang-vien Q5).
                if (isAdviseeRosterIntent(message) && jdbc != null) {
                    return adviseeRosterAnswer(lecturerId, locale);
                }
                return lecturerThesisAnswer(lecturerId, locale);
            }
            return null;
        }
        if (isLecturerTeachingCreditsIntent(message)) {
            if (StringUtils.hasText(lecturerId) && sections != null) {
                return lecturerTeachingCreditsAnswer(lecturerId, locale);
            }
            return null;
        }
        if (isLecturerGradingIntent(message)) {
            if (StringUtils.hasText(lecturerId) && sections != null) {
                return lecturerGradingAnswer(lecturerId, locale);
            }
            return null;
        }
        if (isAttendanceIntent(message)) {
            if (StringUtils.hasText(lecturerId) && academicAttendance != null) {
                return lecturerAttendanceAnswer(lecturerId, locale, message);
            }
            return null;
        }
        if (isGraduationCreditsIntent(message)) {
            // The graduation-credit gap is student-owned; without a student
            // profile the question falls back to the knowledge path.
            if (StringUtils.hasText(studentId)) {
                return graduationCreditsAnswer(studentId, locale, message);
            }
            return null;
        }
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
        if (isCreditsAccumulatedIntent(message)) {
            // Accumulated credits are the transcript summary's earned total —
            // best attempt per course — answered from the same read path the
            // transcript page uses (audit ca-nhan Q11: the question fell to
            // the credit-limit regulation instead).
            if (StringUtils.hasText(studentId)) {
                return accumulatedCreditsAnswer(studentId, locale);
            }
            return null;
        }
        if (isPendingGradesIntent(message)) {
            if (StringUtils.hasText(studentId)) {
                return pendingGradesAnswer(studentId, locale);
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
                return gradesAnswer(studentId, locale, message);
            }
            return null;
        }
        // Production audit Q2/Q6: "Hôm nay tôi có lớp (học) không?" reads as an
        // enrollment-list question (lớp + interrogative) but it is a
        // timetable-EXISTENCE question — for a student the enrollment branch
        // dumped the whole list, and for a lecturer it matched neither the
        // student list nor the teaching-list hint and died at RAG with
        // NO_MATCH. A day word plus "có lớp/tiết/buổi/ca" (and no registration
        // verb) routes to the day-filtered timetable instead.
        Integer existenceDay = detectRequestedDay(message);
        if (existenceDay != null
                && Pattern.compile("(?:có|đang có|co|dang co)\\s*(?:lớp|tiết|buổi|ca|môn|lop|tiet|buoi|mon)\\b",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(message).find()
                && !Pattern.compile("đăng\\s*ký|dang\\s*ky", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                        .matcher(message).find()) {
            if (StringUtils.hasText(studentId)) {
                return studentAnswer(studentId, locale, existenceDay);
            }
            if (StringUtils.hasText(lecturerId) && sections != null) {
                return lecturerAnswer(lecturerId, locale, existenceDay);
            }
            return null;
        }
        if (isEnrollmentListIntent(message)) {
            // Round-3 chat-2: a NAMED semester in the question asks about that
            // semester's courses (the transcript's published rows are the
            // durable record), not the current-term registration list.
            NamedSemester namedSemester = parseNamedSemester(message);
            if (namedSemester != null && StringUtils.hasText(studentId)) {
                return namedSemesterCourseListAnswer(studentId, locale, namedSemester);
            }
            // The section list is student-owned. A lecturer asking "Kỳ này tôi
            // phụ trách dạy những lớp học phần nào?" wants their teaching
            // assignments, so the teaching-list phrasing answers from the
            // lecturer schedule; other lecturer phrasings fall through to RAG.
            if (StringUtils.hasText(studentId)) {
                return enrollmentListAnswer(studentId, locale);
            }
            if (StringUtils.hasText(lecturerId) && sections != null
                    && LECTURER_TEACHING_LIST_HINT.matcher(message).find()) {
                return lecturerAnswer(lecturerId, locale, null);
            }
            // A lecturer phrasing without teaching-list vocabulary ("thứ 5 tôi
            // dạy môn nào?") keeps its old route: the personal teaching
            // timetable below. Returning null here used to be unreachable for
            // students and wrong for lecturers once "môn" joined the noun
            // group (round-3 chat-2/5).
        }
        Integer requestedDay = detectRequestedDay(message);
        if (StringUtils.hasText(studentId)) {
            return studentAnswer(studentId, locale, requestedDay);
        }
        if (StringUtils.hasText(lecturerId)) {
            return lecturerAnswer(lecturerId, locale, requestedDay);
        }
        return null;
    }

    /**
     * Personal grades answer grounded in the asker's real grade rows. The
     * cumulative line is the transcript summary itself — the same
     * best-attempt-per-course figures the portal transcript shows — never a
     * sum over every attempt and never model-computed; the latest semester's
     * figures are shown separately and labeled, because the two are routinely
     * confused (production audit D-Q3; audit ca-nhan Q5: summing retake
     * attempts contradicted the transcript page).
     */
    private String gradesAnswer(String studentId, String locale, String message) {
        boolean vi = "vi".equals(locale);
        NamedSemester requested = parseNamedSemester(message);
        List<GradeSummary> grades = enrollments.findStudentGrades(studentId, null);
        if (grades == null || grades.isEmpty()) {
            return vi
                    ? "Bạn chưa có điểm học phần nào được ghi nhận. Điểm sẽ xuất hiện ở đây sau khi giảng viên nhập và cổng công bố."
                    : "You have no recorded course grades yet. Grades appear here once lecturers submit them and the portal publishes them.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Kết quả học tập của bạn:\n" : "Your academic results:\n");

        // The cumulative line is the transcript summary itself (best attempt per
        // course under the retake policy), NOT a sum over every attempt: the
        // old all-rows accumulation counted retaken courses twice (57 credits / GPA
        // 3.07 vs the portal's 38 / 3.11) and contradicted the transcript page
        // the student sees side by side (production audit ca-nhan Q5, high).
        TranscriptResponse transcript = enrollments.findStudentTranscript(studentId);
        TranscriptSummary cumulative = transcript == null ? null : transcript.summary();
        boolean fromTranscriptSummary = cumulative != null;
        if (cumulative == null) {
            // Defensive fallback: the transcript read path is unavailable.
            // Compute cumulative from every published row so the answer still
            // carries real numbers; this is the all-attempt basis and is
            // deliberately labeled as such in the closing note.
            GradeTotals fallback = new GradeTotals();
            grades.forEach(fallback::add);
            cumulative = new TranscriptSummary(fallback.gpa(), fallback.earnedCredits, fallback.gpaCredits, null);
        }

        // Grades come ordered newest semester first, so the first group is the
        // latest semester and the running total is the cumulative record.
        Map<String, List<GradeSummary>> bySemester = new LinkedHashMap<>();
        for (GradeSummary grade : grades) {
            bySemester.computeIfAbsent(grade.semesterId(), ignored -> new ArrayList<>()).add(grade);
        }

        // Round-3 chat-6: when the asker NAMED a semester, answer THAT semester.
        // The old behavior always printed the newest semester, so "Học kỳ 1
        // (GPA 3.16)" was answered with HK2's 3.03 and no disclaimer.
        List<GradeSummary> requestedRows = null;
        String requestedLabel = null;
        if (requested != null) {
            for (List<GradeSummary> rows : bySemester.values()) {
                if (!rows.isEmpty() && isNamedSemesterRow(rows.get(0), requested)) {
                    requestedRows = rows;
                    GradeSummary probe = rows.get(0);
                    requestedLabel = vi
                            ? firstText(probe.semesterNameVi(), probe.semester())
                            : firstText(probe.semesterNameEn(), probe.semester());
                    break;
                }
            }
        }
        if (requested != null && requestedRows != null) {
            GradeTotals term = new GradeTotals();
            requestedRows.forEach(term::add);
            answer = new StringBuilder();
            answer.append(vi ? "Kết quả " : "Results for ");
            answer.append(StringUtils.hasText(requestedLabel)
                    ? requestedLabel
                    : (vi ? "học kỳ đã hỏi" : "the requested semester"));
            answer.append(vi ? " của bạn:\n" : ":\n");
            if (term.gpaCredits == 0) {
                answer.append("\n").append(vi
                        ? "Học kỳ này chưa có học phần nào có điểm công bố.\n"
                        : "No published grades in this semester yet.\n");
            } else {
                answer.append("\n").append(vi ? "Số học kỳ này: " : "This semester: ")
                        .append(term.earnedCredits).append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                        .append(term.gpa())
                        .append(vi ? " (thang 4)\n" : " (4.0 scale)\n");
            }
            if (cumulative != null && cumulative.totalCreditsAttempted() > 0) {
                answer.append("\n").append(vi ? "Tích lũy toàn khóa: " : "Cumulative: ")
                        .append(cumulative.totalCreditsEarned())
                        .append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                        .append(cumulative.cumulativeGpa())
                        .append(vi ? " (thang 4)\n" : " (4.0 scale)\n");
            }
            int rendered = 0;
            for (GradeSummary grade : requestedRows) {
                if (rendered >= 10) {
                    answer.append("\n").append(vi
                            ? "… và " + (requestedRows.size() - rendered) + " học phần khác."
                            : "… and " + (requestedRows.size() - rendered) + " more courses.");
                    break;
                }
                appendGradeLine(answer, grade, vi);
                rendered += 1;
            }
            answer.append(vi
                    ? "\n(Bạn có thể xem chi tiết từng cột điểm của học kỳ này ở trang Bảng điểm.)"
                    : "\n(Review each score component for this semester on the Grades page.)");
            return answer.toString();
        }
        if (requested != null) {
            answer.append("\n").append(vi
                    ? "Mình không tìm thấy Học kỳ " + requested.term() + " (năm " + requested.yearStart()
                            + ") trong bảng điểm của bạn — dưới đây là số liệu tích lũy và học kỳ gần nhất.\n"
                    : "I could not find semester " + requested.term() + " (" + requested.yearStart()
                            + ") in your transcript — showing cumulative and the latest semester instead.\n");
        }

        GradeSummary latestName = null;
        for (List<GradeSummary> rows : bySemester.values()) {
            GradeTotals semester = new GradeTotals();
            rows.forEach(semester::add);
            // The "latest semester" figures are the newest semester WITH
            // published grades — an in-progress current term (no letters yet)
            // must not shadow the last graded one.
            if (latestName == null && semester.gpaCredits > 0) {
                latestName = rows.isEmpty() ? null : rows.get(0);
            }
        }
        if (cumulative == null || cumulative.totalCreditsAttempted() <= 0) {
            answer.append("\n").append(vi
                    ? "Chưa có học kỳ nào có điểm đã công bố — các học phần đang học sẽ xuất hiện sau khi có điểm.\n"
                    : "No published grades yet — in-progress courses will appear once graded.\n");
        } else {
            answer.append("\n").append(vi ? "Tích lũy: " : "Cumulative: ")
                    .append(cumulative.totalCreditsEarned()).append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                    .append(cumulative.cumulativeGpa())
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
        for (GradeSummary grade : grades) {
            if (rendered >= 10) {
                answer.append("\n").append(vi
                        ? "… và " + (grades.size() - rendered) + " học phần khác. Xem đầy đủ ở trang Bảng điểm."
                        : "… and " + (grades.size() - rendered) + " more courses. See the Grades page for the full list.");
                break;
            }
            appendGradeLine(answer, grade, vi);
            rendered += 1;
        }
        if (rendered < 10) {
            answer.append(vi
                    ? "\nBạn có thể xem chi tiết từng cột điểm ở trang Bảng điểm."
                    : "\nYou can review each score component on the Grades page.");
        }
        answer.append(vi
                ? (fromTranscriptSummary
                        ? "\n(Số liệu tích lũy lấy từ bản tóm tắt Bảng điểm của bạn: GPA và tín chỉ tính theo điểm tốt nhất mỗi môn — chính sách học lại.)"
                        : "\n(Số liệu cộng trực tiếp từ các học phần đã có điểm; bản tóm tắt ở trang Bảng điểm là nguồn chính thức.)")
                : (fromTranscriptSummary
                        ? "\n(Cumulative figures come from your transcript summary: GPA and credits use your best attempt per course (retake policy).)"
                        : "\n(Figures sum every published graded row; the Transcript page summary is the authoritative source.)"));
        return answer.toString();
    }

    /** One "• COURSE: grade (letter)" bullet; shared by both grades renderers. */
    private static void appendGradeLine(StringBuilder answer, GradeSummary grade, boolean vi) {
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
            // The approval claim must be backed by the application record —
            // the summary response itself only reports the number, and a
            // round-wide limit raise (no student application) would have made
            // "đã được Phòng Đào tạo phê duyệt" a fabricated provenance
            // (audit ca-nhan Q4, low). Ask the ledger; fall back to neutral
            // wording whenever the lookup is unavailable.
            boolean raisedByApplication = approvedLimitRaiseExists(studentId, summary.roundId());
            answer.append(vi
                    ? (raisedByApplication
                            ? "\nHạn mức " + summary.creditLimit() + " tín chỉ của bạn áp dụng theo đơn xin nâng hạn mức đã được duyệt (mức chuẩn của đợt là "
                                    + CreditLimitApplicationService.STANDARD_LIMIT + " tín chỉ).\n"
                            : "\nHạn mức áp dụng cho đợt đăng ký hiện tại là " + summary.creditLimit()
                                    + " tín chỉ (mức chuẩn " + CreditLimitApplicationService.STANDARD_LIMIT + " tín chỉ).\n")
                    : (raisedByApplication
                            ? "\nYour " + summary.creditLimit() + "-credit limit follows an approved application (the round's standard is "
                                    + CreditLimitApplicationService.STANDARD_LIMIT + " credits).\n"
                            : "\nThe limit for the current registration round is " + summary.creditLimit()
                                    + " credits (standard " + CreditLimitApplicationService.STANDARD_LIMIT + ").\n"));
        }
        answer.append(vi
                ? "\n(Số liệu tính trực tiếp từ hồ sơ đăng ký học phần của bạn.) Bạn có thể đăng ký thêm học phần ở khu Đăng ký học phần."
                : "\n(Figures come directly from your registration records.) You can add more sections in the Course Registration area.");
        return answer.toString();
    }

    /**
     * "Tôi đã tích lũy được bao nhiêu tín chỉ?" — the transcript summary's
     * cumulative earned total (best attempt per course under the retake
     * policy), the same numbers the portal transcript shows. Never the
     * credit-LIMIT regulation, which is what the KB path answered instead
     * (audit ca-nhan Q11).
     */
    private String accumulatedCreditsAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        TranscriptResponse transcript = enrollments.findStudentTranscript(studentId);
        TranscriptSummary summary = transcript == null ? null : transcript.summary();
        if (summary == null || summary.totalCreditsEarned() <= 0 && summary.totalCreditsAttempted() <= 0) {
            return vi
                    ? "Bạn chưa có tín chỉ tích lũy nào được công bố. Điểm học phần sẽ xuất hiện trên bảng điểm sau khi công bố, khi đó số tín chỉ tích lũy của bạn sẽ hiện ở đây."
                    : "You have no published accumulated credits yet. Earned credits appear once your course grades are published on the transcript.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Theo bảng điểm đã công bố, bạn đã tích lũy được " + summary.totalCreditsEarned() + " tín chỉ"
                : "According to your published transcript, you have accumulated " + summary.totalCreditsEarned() + " credits");
        if (summary.cumulativeGpa() != null) {
            answer.append(vi ? ", GPA tích lũy " : ", cumulative GPA ")
                    .append(summary.cumulativeGpa())
                    .append(vi ? " (thang 4)." : " (4.0 scale).");
        } else {
            answer.append(vi ? "." : ".");
        }
        answer.append(vi
                ? "\n(Số liệu tính theo điểm tốt nhất mỗi môn — chính sách học lại, trùng với trang Bảng điểm.)"
                : "\n(Figures count your best attempt per course (retake policy) and match the Transcript page.)");
        return answer.toString();
    }

    /**
     * "Tôi học còn bao nhiêu môn chưa có điểm?" — counted IN CODE over the
     * asker's active enrollments whose grades are not published yet, listing
     * the courses by name (audit quét toàn hệ thống chatbot-2: the question
     * fell to the prerequisite KB while exactly 5 in-progress courses were
     * pending).
     */
    private String pendingGradesAnswer(String studentId, String locale) {
        boolean vi = "vi".equals(locale);
        List<EnrollmentResponse> active = enrollments.findStudentEnrollments(studentId, null).stream()
                .filter(item -> ACTIVE_ENROLLMENT_STATUSES.contains(item.status()))
                .toList();
        List<EnrollmentResponse> pending = active.stream()
                .filter(item -> !"PUBLISHED".equalsIgnoreCase(item.gradeStatus()))
                .toList();
        if (active.isEmpty()) {
            return vi
                    ? "Bạn hiện không có học phần nào đang học, nên không có môn nào chờ điểm."
                    : "You have no in-progress courses right now, so no grades are pending.";
        }
        if (pending.isEmpty()) {
            return vi
                    ? "Tất cả " + active.size() + " học phần đang học của bạn đều đã có điểm công bố."
                    : "All " + active.size() + " of your in-progress courses already have published grades.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Trong " + active.size() + " học phần đang học, bạn còn " + pending.size()
                        + " môn chưa có điểm công bố:\n"
                : "Of your " + active.size() + " in-progress courses, " + pending.size()
                        + " still await published grades:\n");
        int rendered = 0;
        for (EnrollmentResponse item : pending) {
            if (rendered++ >= 10) {
                answer.append("\n• ").append(vi
                        ? "… và " + (pending.size() - rendered + 1) + " môn khác."
                        : "… and " + (pending.size() - rendered + 1) + " more courses.");
                break;
            }
            String courseCode = item.section() != null && item.section().course() != null
                    ? item.section().course().code() : null;
            String courseTitle = item.section() != null && item.section().course() != null
                    ? courseLabel(item.section().course(), locale) : null;
            answer.append("\n• ").append(courseName(courseCode, courseTitle, null));
        }
        answer.append(vi
                ? "\n\nĐiểm sẽ xuất hiện ở trang Điểm số ngay khi giảng viên nhập và Phòng Đào tạo công bố."
                : "\n\nGrades appear on the Grades page as soon as lecturers submit and the office publishes them.");
        return answer.toString();
    }

    /**
     * "Học kỳ này tôi dạy tất cả bao nhiêu tín chỉ?" — summed IN CODE over the
     * asker's distinct assigned sections (the same schedule source the teaching
     * timetable answer uses; one row per section, credits carried per course).
     * The LLM claimed no such data existed (audit giang-vien Q7) while
     * GET /sections/my/schedule shows it.
     */
    private String lecturerTeachingCreditsAnswer(String lecturerId, String locale) {
        boolean vi = "vi".equals(locale);
        List<LecturerScheduleResponse> teaching = sections.findLecturerSchedule(lecturerId, null);
        if (teaching == null || teaching.isEmpty()) {
            return vi
                    ? "Học kỳ này bạn chưa được phân công lớp học phần nào, nên tổng số tín chỉ giảng dạy là 0."
                    : "You are not assigned to any sections this term, so your teaching credit total is 0.";
        }
        Map<String, LecturerScheduleResponse> distinctSections = new LinkedHashMap<>();
        for (LecturerScheduleResponse row : teaching) {
            String key = firstText(row.sectionId(), firstText(row.id(),
                    row.courseCode() + "|" + row.sectionNumber()));
            distinctSections.putIfAbsent(key, row);
        }
        int totalCredits = 0;
        for (LecturerScheduleResponse row : distinctSections.values()) {
            totalCredits += Math.max(0, row.credits());
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Học kỳ này bạn đang dạy " + distinctSections.size() + " lớp học phần, tổng cộng "
                        + totalCredits + " tín chỉ:\n"
                : "This term you teach " + distinctSections.size() + " sections, "
                        + totalCredits + " credits in total:\n");
        int rendered = 0;
        for (LecturerScheduleResponse row : distinctSections.values()) {
            if (rendered++ >= 12) {
                answer.append("\n• ").append(vi
                        ? "… và " + (distinctSections.size() - rendered + 1) + " lớp khác."
                        : "… and " + (distinctSections.size() - rendered + 1) + " more sections.");
                break;
            }
            answer.append("\n• ")
                    .append(courseName(row.courseCode(),
                            vi ? row.courseNameVi() : row.courseNameEn(), row.courseName()));
            if (StringUtils.hasText(row.sectionNumber())) {
                answer.append(vi ? " (lớp " : " (section ").append(row.sectionNumber()).append(")");
            }
            answer.append(vi ? " — " : " — ").append(row.credits())
                    .append(vi ? " tín chỉ" : " credits");
        }
        answer.append(vi
                ? "\n\n(Tổng tính trên từng lớp học phần được phân công; xem chi tiết ở trang Lịch giảng dạy.)"
                : "\n\n(Total sums each assigned section once; see the Teaching Schedule page.)");
        return answer.toString();
    }

    /**
     * "Tôi đang hướng dẫn những sinh viên nào?" — names every member of every
     * group attached to the asker's supervised topics, read from the same
     * thesis_group_member projection the group pages use (member identity from
     * the linked student profile, external members by recorded display name).
     * Counts-only boilerplate left the "những sinh viên nào" half of the
     * question unanswered (audit giang-vien Q5).
     */
    private String adviseeRosterAnswer(String lecturerId, String locale) {
        boolean vi = "vi".equals(locale);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT ts.topic_id, t.title AS topic_title, g.id AS group_id, "
                        + "g.approval_status AS group_approval_status, "
                        + "m.student_id, m.display_name, m.is_external, m.member_order, "
                        + "s.\"studentId\" AS student_number, "
                        + "u.\"firstName\" AS first_name, u.\"lastName\" AS last_name "
                        + "FROM thesis.thesis_topic_supervisor ts "
                        + "JOIN thesis.thesis_topic t ON t.id = ts.topic_id "
                        + "LEFT JOIN thesis.thesis_group g ON g.topic_id = t.id "
                        + "LEFT JOIN thesis.thesis_group_member m ON m.group_id = g.id "
                        + "LEFT JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id "
                        + "LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\" "
                        + "WHERE ts.lecturer_id = :lecturerId "
                        + "ORDER BY t.title, g.id, m.member_order",
                new MapSqlParameterSource("lecturerId", lecturerId));
        if (rows == null || rows.isEmpty()) {
            return vi
                    ? "Hiện bạn chưa có nhóm sinh viên nào gắn với đề tài hướng dẫn, nên chưa có danh sách để liệt kê. "
                            + "Khi sinh viên lập nhóm và chọn bạn làm giảng viên hướng dẫn, danh sách sẽ xuất hiện ở đây."
                    : "No student groups are attached to your supervised topics yet, so there is no roster to list. "
                            + "Once students form groups and assign you as supervisor, the roster will appear here.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Sinh viên bạn đang hướng dẫn:\n" : "Students you are supervising:\n");
        String currentTopic = null;
        int names = 0;
        for (Map<String, Object> row : rows) {
            String topic = text(row, "topic_title");
            if (!StringUtils.hasText(topic)) {
                topic = vi ? "Đề tài chưa đặt tên" : "Untitled topic";
            }
            if (!topic.equals(currentTopic)) {
                currentTopic = topic;
                answer.append("\n• ").append(topic).append("\n");
            }
            String name = memberName(row);
            String studentNumber = text(row, "student_number");
            String label = firstText(name, firstText(studentNumber, text(row, "student_id")));
            if (!StringUtils.hasText(label)) {
                continue;
            }
            answer.append("    - ").append(label);
            if (truthy(row.get("is_external"))) {
                answer.append(vi ? " (thành viên ngoài)" : " (external member)");
            }
            answer.append("\n");
            names++;
        }
        if (names == 0) {
            return vi
                    ? "Các đề tài bạn hướng dẫn chưa có nhóm nào được ghi nhận thành viên; hiện chỉ có danh sách đề tài. "
                            + "Bạn có thể duyệt và theo dõi nhóm ở trang Quản lý Khóa luận."
                    : "None of your supervised topics have group members recorded yet; only the topic list exists. "
                            + "You can review and approve groups on the Thesis Management page.";
        }
        answer.append(vi
                ? "\nDanh sách lấy từ hồ sơ nhóm do chính sinh viên và Phòng Đào tạo cập nhật."
                : "\nThe roster reflects group records maintained by students and the Academic Affairs Office.");
        return answer.toString();
    }

    /**
     * True only when an APPROVED credit-limit application exists for this
     * student and round. A missing ledger handle or any read failure answers
     * FALSE — the composer then uses neutral wording instead of asserting a
     * provenance the advisor cannot see.
     */
    private boolean approvedLimitRaiseExists(String studentId, String roundId) {
        if (jdbc == null || !StringUtils.hasText(studentId) || !StringUtils.hasText(roundId)) {
            return false;
        }
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM academic.\"CreditLimitApplication\" "
                            + "WHERE \"studentId\" = :studentId AND \"roundId\" = :roundId AND \"status\" = 'APPROVED'",
                    new MapSqlParameterSource("studentId", studentId).addValue("roundId", roundId),
                    Integer.class);
            return count != null && count > 0;
        } catch (RuntimeException exception) {
            LOG.info("credit-limit application provenance unavailable: {}", exception.getClass().getSimpleName());
            return false;
        }
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
            // Workload totals are summed IN CODE over the asker's own topics —
            // group counts are per student GROUP (the unit the workload query
            // aggregates), so the wording says "nhóm sinh viên", never a bare
            // student headcount.
            int totalGroups = workload.topics().stream()
                    .mapToInt(ThesisLecturerWorkloadService.SupervisedTopic::groupCount).sum();
            int pendingGroups = workload.topics().stream()
                    .mapToInt(ThesisLecturerWorkloadService.SupervisedTopic::pendingGroupCount).sum();
            answer.append(vi
                    ? "Bạn đang hướng dẫn tổng cộng " + totalGroups + " nhóm sinh viên trên "
                            + workload.topics().size() + " đề tài, trong đó " + pendingGroups
                            + " nhóm đang chờ duyệt.\n"
                    : "You are supervising " + totalGroups + " student groups across "
                            + workload.topics().size() + " topics, with " + pendingGroups
                            + " group(s) pending approval.\n");
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

    /**
     * "Điểm học phần tôi phụ trách hiện đã có chưa?" — grade-entry status of
     * the sections the asker teaches, read from the same workspace path the
     * /sections/my/grading endpoint uses. An empty assignment list says so
     * instead of inventing zero sections.
     */
    private String lecturerGradingAnswer(String lecturerId, String locale) {
        boolean vi = "vi".equals(locale);
        List<LecturerGradingSectionResponse> rows = sections.findLecturerGradingSections(lecturerId, null);
        if (rows == null || rows.isEmpty()) {
            return vi
                    ? "Bạn chưa được phân công nhập điểm học phần nào kỳ này."
                    : "You are not assigned to enter grades for any course section this term.";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Tình trạng nhập điểm các lớp học phần bạn phụ trách:\n"
                : "Grade-entry status for the sections you teach:\n");
        for (LecturerGradingSectionResponse row : rows) {
            String courseName = vi
                    ? firstText(row.courseNameVi(), firstText(row.courseName(), row.courseNameEn()))
                    : firstText(row.courseNameEn(), firstText(row.courseName(), row.courseNameVi()));
            String label = StringUtils.hasText(row.courseCode())
                    ? (StringUtils.hasText(courseName) ? row.courseCode() + " - " + courseName : row.courseCode())
                    : courseName;
            answer.append("\n• ").append(StringUtils.hasText(label) ? label : (vi ? "Học phần" : "Course"));
            if (StringUtils.hasText(row.sectionNumber())) {
                answer.append(vi ? " (lớp " : " (section ").append(row.sectionNumber()).append(")");
            }
            answer.append(vi ? ": " : ": ")
                    .append(row.gradedCount()).append("/").append(row.enrolledCount())
                    .append(vi ? " SV đã có điểm, " : " students graded, ")
                    .append(row.publishedCount()).append(vi ? " đã công bố" : " published")
                    .append(" — ").append(row.gradeStatus()).append("\n");
        }
        answer.append(vi
                ? "\nBạn có thể nhập và công bố điểm ở trang Nhập điểm."
                : "\nYou can enter and publish grades on the Grade Entry page.");
        return answer.toString();
    }

    /**
     * "Sinh viên lớp SE401 hôm nay có ai vắng mặt không?" — resolves the asked
     * code against the lecturer's own teaching assignments first; a code
     * outside those assignments is reported as such instead of guessed. The
     * day is the asker's "today" on the campus calendar
     * ({@link AssistantTimezone#ZONE}, the same source the timetable uses).
     */
    private String lecturerAttendanceAnswer(String lecturerId, String locale, String message) {
        boolean vi = "vi".equals(locale);
        java.util.regex.Matcher matcher = SECTION_CODE.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String code = matcher.group();
        String sectionId = resolveLecturerSectionId(lecturerId, code);
        if (sectionId == null) {
            return vi
                    ? "Mình không tìm thấy lớp " + code
                            + " trong các lớp học phần bạn đang phụ trách kỳ này, nên chưa tra được điểm danh. "
                            + "Bạn kiểm tra lại mã lớp ở trang Lịch giảng dạy nhé."
                    : "I could not find section " + code
                            + " among the sections you teach this term, so I cannot look up its attendance. "
                            + "Please double-check the code on the Teaching Schedule page.";
        }
        java.time.LocalDate today = java.time.LocalDate.now(AssistantTimezone.ZONE);
        String dayLabel = today.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
        List<AttendanceResponse> rows = academicAttendance.findLecturerAttendance(lecturerId, sectionId, today.toString());
        if (rows == null || rows.isEmpty()) {
            return vi
                    ? "Mình chưa thấy dữ liệu điểm danh nào cho lớp " + code + " ngày " + dayLabel
                            + ". Buổi học có thể chưa được điểm danh."
                    : "I have no attendance records for section " + code + " on " + dayLabel + " yet.";
        }
        List<AttendanceResponse> absent = rows.stream()
                .filter(row -> "ABSENT".equalsIgnoreCase(row.status()))
                .toList();
        if (absent.isEmpty()) {
            return vi
                    ? "Không có sinh viên nào vắng mặt ở lớp " + code + " ngày " + dayLabel + "."
                    : "No students were absent in section " + code + " on " + dayLabel + ".";
        }
        StringBuilder answer = new StringBuilder();
        answer.append(vi
                ? "Sinh viên vắng mặt lớp " + code + " ngày " + dayLabel + ":\n"
                : "Students absent in section " + code + " on " + dayLabel + ":\n");
        for (AttendanceResponse row : absent) {
            String name = studentDisplayName(row);
            answer.append("\n• ").append(StringUtils.hasText(name) ? name : (vi ? "Sinh viên" : "Student"));
            String studentCode = row.student() != null && StringUtils.hasText(row.student().studentId())
                    ? row.student().studentId() : row.studentId();
            if (StringUtils.hasText(studentCode)) {
                answer.append(" (").append(studentCode).append(")");
            }
            if (StringUtils.hasText(row.notes())) {
                answer.append(vi ? " — ghi chú: " : " — notes: ").append(row.notes());
            }
            answer.append("\n");
        }
        answer.append(vi
                ? "\nBạn có thể cập nhật điểm danh ở trang Điểm danh."
                : "\nYou can update attendance on the Attendance page.");
        return answer.toString();
    }

    /** The lecturer's assigned section whose course code or section number matches the asked code. */
    private String resolveLecturerSectionId(String lecturerId, String code) {
        String normalized = code.toUpperCase(java.util.Locale.ROOT);
        for (LecturerScheduleResponse section : sections.findLecturerSchedule(lecturerId, null)) {
            String sectionNumber = section.sectionNumber();
            if (normalized.equalsIgnoreCase(section.courseCode())
                    || normalized.equalsIgnoreCase(sectionNumber)
                    || (StringUtils.hasText(sectionNumber)
                            && sectionNumber.toUpperCase(java.util.Locale.ROOT).startsWith(normalized + "-"))) {
                return section.sectionId();
            }
        }
        return null;
    }

    private static String studentDisplayName(AttendanceResponse row) {
        var user = row.student() != null ? row.student().user() : null;
        if (user == null) {
            return null;
        }
        String last = user.lastName();
        String first = user.firstName();
        if (!StringUtils.hasText(last)) {
            return first;
        }
        if (!StringUtils.hasText(first)) {
            return last;
        }
        return last + " " + first;
    }

    /**
     * "Còn thiếu bao nhiêu tín chỉ để đủ 143 tín chỉ tốt nghiệp?" — the gap to
     * the graduation requirement named in the sentence, computed from the
     * student's own published grade rows (the same source and the same
     * GRADE_POINTS math the GPA answer uses, never a model estimate). Only a
     * requirement larger than the accumulated credits reports a gap.
     */
    private String graduationCreditsAnswer(String studentId, String locale, String message) {
        boolean vi = "vi".equals(locale);
        java.util.regex.Matcher matcher = GRADUATION_CREDITS_INTENT.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        int required = Integer.parseInt(digits);
        List<GradeSummary> grades = enrollments.findStudentGrades(studentId, null);
        if (grades == null || grades.isEmpty()) {
            return vi
                    ? "Bạn chưa có tín chỉ tích lũy nào được công bố, nên mình chưa thể tính khoảng cách tới mức "
                            + required + " tín chỉ tốt nghiệp. Số liệu sẽ có sau khi điểm của bạn được công bố."
                    : "You have no published credits yet, so I cannot compute the gap to the " + required
                            + "-credit graduation requirement.";
        }
        // Same best-attempt-per-course basis as the grades answer and the
        // transcript page: graduation progress must never count a retake
        // twice against the degree total (audit ca-nhan Q5 high finding).
        TranscriptResponse transcript = enrollments.findStudentTranscript(studentId);
        TranscriptSummary transcriptSummary = transcript == null ? null : transcript.summary();
        int earned;
        if (transcriptSummary != null) {
            earned = transcriptSummary.totalCreditsEarned();
        } else {
            GradeTotals cumulative = new GradeTotals();
            grades.forEach(cumulative::add);
            earned = cumulative.earnedCredits;
        }
        if (required > earned) {
            return vi
                    ? "Để đủ " + required + " tín chỉ tốt nghiệp, bạn còn thiếu " + (required - earned)
                            + " tín chỉ (đã tích lũy " + earned + "/" + required + ")."
                    : "To reach the " + required + "-credit graduation requirement, you still need " + (required - earned)
                            + " credits (accumulated " + earned + "/" + required + ").";
        }
        return vi
                ? "Bạn đã tích lũy đủ " + earned + " tín chỉ, đáp ứng mức " + required + " tín chỉ tốt nghiệp."
                : "You have accumulated " + earned + " credits, which meets the " + required
                        + "-credit graduation requirement.";
    }

    /**
     * xrole-4: "Nhóm luận văn của tôi là nhóm nào, có những ai?" — beyond the
     * topic line the answer now names the group the asker belongs to: the
     * asker's role (leader or member, read from {@code leader_student_id} /
     * {@code is_leader}), the member roster and headcount compared with the
     * 3–4 requirement, and the approval status with its reason when one is
     * stored. Every value is echoed from the same group read path the web
     * portal uses (thesis_group / thesis_group_member joined to the student
     * profiles, cf. ThesisGroupReadRepository.hydrate) — no name, role, or
     * count is ever invented; unreadable member rows are simply omitted.
     */
    private String studentThesisAnswer(String studentId, String locale) {
        if (jdbc == null) return null;
        boolean vi = "vi".equals(locale);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT g.id AS group_id, g.status AS group_status, g.approval_status, "
                        + "g.leader_student_id, g.rejection_reason, "
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
        // The roster query mirrors ThesisGroupReadRepository.hydrate: member
        // identity comes from the linked student profile, external members from
        // their recorded display name.
        List<Object> groupIds = rows.stream()
                .map(row -> row.get("group_id"))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(row -> (Object) row)
                .toList();
        Map<String, List<Map<String, Object>>> rosterByGroup = new LinkedHashMap<>();
        if (!groupIds.isEmpty()) {
            for (Map<String, Object> member : jdbc.queryForList(
                    "SELECT m.group_id, m.student_id, m.is_leader, m.member_order, "
                            + "m.display_name, m.is_external, "
                            + "s.\"studentId\" AS student_number, "
                            + "u.\"firstName\" AS first_name, u.\"lastName\" AS last_name "
                            + "FROM thesis.thesis_group_member m "
                            + "LEFT JOIN campuscore_auth.\"Student\" s ON s.\"id\" = m.student_id "
                            + "LEFT JOIN campuscore_auth.\"User\" u ON u.\"id\" = s.\"userId\" "
                            + "WHERE m.group_id IN (:ids) "
                            + "ORDER BY m.group_id, m.member_order",
                    new MapSqlParameterSource("ids", groupIds))) {
                String groupId = text(member, "group_id");
                if (!StringUtils.hasText(groupId)) {
                    continue;
                }
                rosterByGroup.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(member);
            }
        }

        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Thông tin đăng ký khóa luận của bạn:\n" : "Your thesis registration status:\n");
        for (Map<String, Object> row : rows) {
            String roundName = (String) row.get("round_name");
            String topicTitle = (String) row.get("topic_title");
            String approvalStatus = (String) row.get("approval_status");
            String rejectionReason = text(row, "rejection_reason");
            String leaderStudentId = text(row, "leader_student_id");
            Object finalScore = row.get("final_score");

            answer.append("\n• ").append(StringUtils.hasText(roundName) ? roundName : (vi ? "Đợt khóa luận" : "Thesis Round"));
            if (StringUtils.hasText(topicTitle)) {
                answer.append(vi ? "\n  - Đề tài: " : "\n  - Topic: ").append(topicTitle);
            } else {
                answer.append(vi ? "\n  - Đề tài: Chưa chọn đề tài" : "\n  - Topic: Not selected yet");
            }

            List<Map<String, Object>> members = rosterByGroup
                    .getOrDefault(text(row, "group_id"), List.of()).stream()
                    .filter(member -> StringUtils.hasText(text(member, "student_id"))
                            || StringUtils.hasText(memberName(member)))
                    .toList();
            boolean askerIsLeader = studentId.equalsIgnoreCase(leaderStudentId)
                    || members.stream().anyMatch(member ->
                            studentId.equalsIgnoreCase(text(member, "student_id")) && truthy(member.get("is_leader")));
            if (!members.isEmpty()) {
                int count = members.size();
                answer.append(vi ? "\n  - Nhóm: " : "\n  - Group: ").append(count)
                        .append(vi ? " thành viên (yêu cầu " : " members (requirement ")
                        .append(GROUP_MIN_MEMBERS).append("-").append(GROUP_MAX_MEMBERS)
                        .append(vi ? ")" : ")");
                if (count < GROUP_MIN_MEMBERS) {
                    answer.append(vi
                            ? " — còn thiếu " + (GROUP_MIN_MEMBERS - count) + " so với tối thiểu " + GROUP_MIN_MEMBERS
                            : " — " + (GROUP_MIN_MEMBERS - count) + " short of the minimum of " + GROUP_MIN_MEMBERS);
                } else if (count <= GROUP_MAX_MEMBERS) {
                    answer.append(vi ? " — đủ số lượng theo yêu cầu" : " — size requirement met");
                } else {
                    answer.append(vi
                            ? " — vượt quá tối đa " + GROUP_MAX_MEMBERS
                            : " — above the maximum of " + GROUP_MAX_MEMBERS);
                }
                answer.append(vi ? "\n  - Vai trò của bạn: " : "\n  - Your role: ")
                        .append(askerIsLeader
                                ? (vi ? "Nhóm trưởng" : "Group leader")
                                : (vi ? "Thành viên" : "Member"));
                answer.append(vi ? "\n  - Thành viên:\n" : "\n  - Members:\n");
                int order = 1;
                for (Map<String, Object> member : members) {
                    String name = memberName(member);
                    String studentNumber = text(member, "student_number");
                    // Never fall back to the raw internal student_id: the chat
                    // answer is user-facing copy, and an internal identifier is
                    // not something to surface when the roster lacks a name.
                    String label = firstText(name, studentNumber);
                    if (!StringUtils.hasText(label)) {
                        continue;
                    }
                    answer.append("    ").append(order++).append(". ").append(label);
                    boolean memberLeader = truthy(member.get("is_leader"))
                            || (StringUtils.hasText(leaderStudentId)
                                    && leaderStudentId.equalsIgnoreCase(text(member, "student_id")));
                    if (memberLeader) {
                        answer.append(vi ? " (nhóm trưởng)" : " (leader)");
                    }
                    answer.append("\n");
                }
            } else if (StringUtils.hasText(leaderStudentId)) {
                answer.append(vi ? "\n  - Vai trò của bạn: " : "\n  - Your role: ")
                        .append(askerIsLeader
                                ? (vi ? "Nhóm trưởng" : "Group leader")
                                : (vi ? "Thành viên" : "Member"));
            }
            if (StringUtils.hasText(approvalStatus)) {
                answer.append(vi ? "\n  - Trạng thái duyệt: " : "\n  - Approval status: ").append(approvalStatus);
                if (StringUtils.hasText(rejectionReason)) {
                    answer.append(vi ? " — lý do: " : " — reason: ").append(rejectionReason);
                }
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

    /**
     * Member display name from the roster row, matching the portal read path:
     * external members carry their recorded display name, registered students
     * the family-name-first profile name (same order as
     * {@link #studentDisplayName}). Null when the row carries no name at all —
     * never a placeholder invented for a missing person.
     */
    private static String memberName(Map<String, Object> member) {
        if (truthy(member.get("is_external"))) {
            String display = text(member, "display_name");
            return StringUtils.hasText(display) ? display : null;
        }
        String first = text(member, "first_name");
        String last = text(member, "last_name");
        if (!StringUtils.hasText(last)) {
            return StringUtils.hasText(first) ? first : null;
        }
        if (!StringUtils.hasText(first)) {
            return last;
        }
        return last + " " + first;
    }

    /** JDBC booleans arrive as Boolean or "true" depending on the driver/case. */
    private static boolean truthy(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
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
     * Round-3 chat-2: "Học kỳ 1 năm học 2025-2026 tôi học những môn nào?" —
     * the courses of the NAMED semester, taken from the same published grade
     * rows the transcript shows (the durable record of what was taken). A
     * semester with no rows says so and falls back to the current-term list
     * instead of claiming the portal has no data (the old KB answer asserted
     * "chưa công bố danh sách môn" while six graded courses existed).
     */
    private String namedSemesterCourseListAnswer(String studentId, String locale, NamedSemester semester) {
        boolean vi = "vi".equals(locale);
        List<GradeSummary> grades = enrollments.findStudentGrades(studentId, null);
        List<GradeSummary> rows = grades == null ? List.of() : grades.stream()
                .filter(grade -> isNamedSemesterRow(grade, semester))
                .toList();
        if (rows.isEmpty()) {
            return vi
                    ? "Bảng điểm của bạn chưa có học phần nào cho Học kỳ " + semester.term()
                            + " (bắt đầu năm " + semester.yearStart() + ").\n\n"
                            + enrollmentListAnswer(studentId, locale)
                    : "Your transcript has no courses recorded for semester " + semester.term()
                            + " (starting " + semester.yearStart() + ").\n\n"
                            + enrollmentListAnswer(studentId, locale);
        }
        GradeTotals term = new GradeTotals();
        rows.forEach(term::add);
        String label = vi
                ? firstText(rows.get(0).semesterNameVi(), rows.get(0).semester())
                : firstText(rows.get(0).semesterNameEn(), rows.get(0).semester());
        StringBuilder answer = new StringBuilder();
        answer.append(vi ? "Các học phần của bạn ở " : "Your courses in ");
        answer.append(StringUtils.hasText(label) ? label : (vi ? "học kỳ đã hỏi" : "the requested semester"));
        answer.append(vi ? ":\n" : ":\n");
        if (term.gpaCredits > 0) {
            answer.append("\n").append(vi ? "Số học kỳ này: " : "This semester: ")
                    .append(term.earnedCredits).append(vi ? " tín chỉ, GPA " : " credits, GPA ")
                    .append(term.gpa()).append(vi ? " (thang 4)\n" : " (4.0 scale)\n");
        }
        int rendered = 0;
        for (GradeSummary grade : rows) {
            if (rendered >= 10) {
                answer.append("\n• … ").append(vi
                        ? "và " + (rows.size() - rendered) + " học phần khác."
                        : "and " + (rows.size() - rendered) + " more courses.");
                break;
            }
            String course = courseName(grade.courseCode(), courseText(grade, vi), null);
            answer.append("\n• ").append(StringUtils.hasText(course) ? course : (vi ? "Học phần" : "Course"));
            String letter = grade.letterGrade();
            if (StringUtils.hasText(letter)) {
                answer.append(vi ? ": điểm " : ": grade ").append(grade.finalGrade())
                        .append(" (").append(letter).append(")");
            } else {
                answer.append(vi ? ": chưa công bố" : ": not published yet");
            }
            answer.append("\n");
            rendered += 1;
        }
        answer.append(vi
                ? "\n(Danh sách lấy từ Bảng điểm — kết quả đã công bố của học kỳ này.)"
                : "\n(Listed from your transcript — the published results for this semester.)");
        return answer.toString();
    }

    /**
     * Credit total for the listed enrollments, counted the same way the
     * registration summary endpoint counts it: the CURRENT course credits of
     * each enrolled section (section -> course), not the enrollment-time
     * snapshot — a snapshot raised after registration would drift from the
     * summary the student sees on the registration page.
     */
    private int sumRegisteredCredits(List<EnrollmentResponse> currentTerm) {
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
