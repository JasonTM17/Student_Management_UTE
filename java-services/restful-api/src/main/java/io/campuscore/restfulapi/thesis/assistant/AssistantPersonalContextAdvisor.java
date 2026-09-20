package io.campuscore.restfulapi.thesis.assistant;

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
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.TranscriptResponse;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import io.campuscore.restfulapi.thesis.service.ThesisLecturerWorkloadService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
            "lịch\\s*(?:học|dạy|giảng\\s*dạy|tuần|hôm\\s*nay|ngày\\s*mai|của\\s*tôi|thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|chủ\\s*nhật|t[2-7]|cn)?"
                    + "|lich\\s*(?:hoc|day|giang\\s*day|tuan|hom\\s*nay|ngay\\s*mai|cua\\s*toi|thu\\s*[2-7]|thu\\s*(?:hai|ba|tu|bon|nam|sau|bay)|chu\\s*nhat|t[2-7]|cn)?"
                    + "|thời\\s*(?:khoá|khóa|khoa)\\s*biểu|thoi\\s*khoa\\s*bieu|\\btkb\\b"
                    + "|(?:thứ\\s*[2-7]|thứ\\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|hôm\\s*nay|ngày\\s*mai|chủ\\s*nhật|hom\\s*nay|ngay\\s*mai|chu\\s*nhat)\\s*(?:tôi\\s*)?(?:có\\s*)?(?:học|dạy|lịch|tiết|môn|buổi|ca)"
                    + "|học\\s*ngày\\s*nào|hoc\\s*ngay\\s*nao|m[oô]n\\s*nào\\s*học|mon\\s*nao\\s*hoc|tiết\\s*học|buổi\\s*học|ca\\s*học|ca\\s*dạy|tiết\\s*dạy"
                    + "|(my\\s+)?(class\\s+|teaching\\s+)?schedule|timetable|my\\s+classes|(classes|teaching)\\s+(today|tomorrow|on\\s+\\w+)",
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
            "đề\\s*tài|de\\s*tai|khóa\\s*luận|khoa\\s*luan|đồ\\s*án|do\\s*an|tiểu\\s*luận|tieu\\s*luan|kltn|tlcn|hội\\s*đồng|hoi\\s*dong|\\bthesis\\b|\\btopics?\\b|\\bcouncils?\\b",
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

    private static boolean isConductIntent(String message) {
        return StringUtils.hasText(message) && CONDUCT_INTENT.matcher(message).find();
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
    private final NamedParameterJdbcTemplate jdbc;

    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections) {
        this(enrollments, sections, null, null, null);
    }

    @Autowired
    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections,
            @Autowired(required = false) ThesisLecturerWorkloadService lecturerWorkload,
            @Autowired(required = false) NamedParameterJdbcTemplate jdbc,
            @Autowired(required = false) AcademicConductService conductService) {
        this.enrollments = enrollments;
        this.sections = sections;
        this.lecturerWorkload = lecturerWorkload;
        this.jdbc = jdbc;
        this.conductService = conductService;
    }

    /** True when the question is clearly about personal schedule, grades, conduct, or thesis status. */
    public boolean handles(String message) {
        return message != null && (SCHEDULE_INTENT.matcher(message).find()
                || isThesisPersonalIntent(message)
                || isConductIntent(message)
                || isGradesIntent(message));
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
        if (isGradesIntent(message)) {
            if (StringUtils.hasText(studentId)) {
                return gradesAnswer(studentId, locale);
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

    /** Personal grades answer grounded in the asker's real grade rows and transcript. */
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

        TranscriptResponse transcript = enrollments.findStudentTranscript(studentId);
        if (transcript != null && transcript.summary() != null) {
            answer.append("\n")
                    .append(vi ? "Tích lũy: GPA " : "Cumulative: GPA ")
                    .append(transcript.summary().cumulativeGpa())
                    .append(vi ? " | " : " | ")
                    .append(transcript.summary().totalCreditsEarned())
                    .append(vi ? " tín chỉ đạt" : " credits earned")
                    .append(vi ? " / " : " / ")
                    .append(transcript.summary().totalCreditsAttempted())
                    .append(vi ? " thực học\n" : " attempted\n");
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
        return answer.toString();
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

    private String lecturerThesisAnswer(String lecturerId, String locale) {
        if (lecturerWorkload == null) return null;
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
            return vi
                    ? "Bạn hiện chưa đăng ký tham gia nhóm hoặc đề tài khóa luận nào. Bạn có thể theo dõi các đợt mở đăng ký tại trang Khóa luận tốt nghiệp."
                    : "You are not currently registered in any thesis group or topic. You can check open registration rounds on the Thesis page.";
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
        Instant currentTermStart = active.stream()
                .map(item -> item.section() != null && item.section().semester() != null
                        ? item.section().semester().startDate()
                        : null)
                .filter(Instant.class::isInstance)
                .map(Instant.class::cast)
                .max(Comparator.naturalOrder())
                .orElse(null);
        List<EnrollmentResponse> currentTerm = active.stream()
                .filter(item -> item.section() != null && item.section().semester() != null
                        && item.section().semester().startDate().equals(currentTermStart))
                .toList();

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
