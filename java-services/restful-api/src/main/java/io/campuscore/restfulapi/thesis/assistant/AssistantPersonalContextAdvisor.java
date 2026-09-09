package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.academic.service.AcademicEnrollmentReadService;
import io.campuscore.restfulapi.academic.service.AcademicSectionReadService;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.ClassroomSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.CourseSummary;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.EnrollmentResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SectionScheduleResponse;
import io.campuscore.restfulapi.academic.web.AcademicEnrollmentReadDtos.SemesterSummary;
import io.campuscore.restfulapi.academic.web.AcademicSectionReadDtos.LecturerScheduleResponse;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatRequest;
import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Answers personal timetable questions (lịch học / thời khóa biểu / schedule)
 * from the asker's real enrollments or teaching assignments. The public RAG
 * snapshot intentionally never sees personal rows, so without this advisor the
 * assistant could only reply that it cannot see personal schedules.
 *
 * Intercepted answers are not charged against the daily RAG quota and are not
 * persisted as conversation turns; the controller falls back to the RAG path
 * whenever the question is not clearly a timetable question or the actor has
 * no personal context to answer from.
 */
@Service
@org.springframework.context.annotation.Profile("persistence")
public class AssistantPersonalContextAdvisor {

    private static final String MODEL = "campuscore-personal-context";
    private static final String REASON_CODE = "PERSONAL_CONTEXT";

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

    private static final String[] DAY_LABELS_VI =
            {"", "Chủ Nhật", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy"};
    private static final String[] DAY_LABELS_EN =
            {"", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};

    private final AcademicEnrollmentReadService enrollments;
    private final AcademicSectionReadService sections;

    public AssistantPersonalContextAdvisor(
            AcademicEnrollmentReadService enrollments,
            AcademicSectionReadService sections) {
        this.enrollments = enrollments;
        this.sections = sections;
    }

    /** True when the question is clearly about the asker's personal timetable. */
    public boolean handles(String message) {
        return message != null && SCHEDULE_INTENT.matcher(message).find();
    }

    /**
     * Personal timetable answer, or null when the asker has no personal
     * context (the caller should fall back to the public knowledge path).
     */
    public ChatResponse answer(ChatRequest request, Jwt actor) {
        String locale = normalizedLocale(request);
        String message = request != null ? request.message() : null;
        String answer = composeAnswer(actor, locale, message);
        if (answer == null) {
            return null;
        }
        return new ChatResponse(answer, MODEL, false, REASON_CODE, locale, List.of());
    }

    /** Emits the personal answer over the SSE contract as meta → replace → done. */
    public void stream(ChatRequest request, Jwt actor, Consumer<ThesisAssistantService.StreamEvent> sink) {
        String locale = normalizedLocale(request);
        String message = request != null ? request.message() : null;
        String answer = composeAnswer(actor, locale, message);
        if (answer == null) {
            answer = fallbackMessage(locale);
        }
        sink.accept(new ThesisAssistantService.StreamMeta(
                UUID.randomUUID(), request.clientRequestId(), null, null, MODEL, locale));
        sink.accept(new ThesisAssistantService.StreamReplace(answer, List.of(), REASON_CODE));
        sink.accept(new ThesisAssistantService.StreamDone(null, "COMPLETED", false, "COMPLETED"));
    }

    private String composeAnswer(Jwt actor, String locale, String message) {
        Integer requestedDay = detectRequestedDay(message);
        String studentId = claim(actor, "studentId");
        if (StringUtils.hasText(studentId)) {
            return studentAnswer(studentId, locale, requestedDay);
        }
        String lecturerId = claim(actor, "lecturerId");
        if (StringUtils.hasText(lecturerId)) {
            return lecturerAnswer(lecturerId, locale, requestedDay);
        }
        return null;
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
            java.time.DayOfWeek dow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).getDayOfWeek();
            return dow == java.time.DayOfWeek.SUNDAY ? 1 : dow.getValue() + 1;
        }
        if (lower.contains("ngày mai") || lower.contains("ngay mai") || lower.contains("tomorrow")) {
            java.time.DayOfWeek dow = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).getDayOfWeek();
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
                ? "Mình chưa xem được lịch học cá nhân của bạn lúc này. Bạn thử lại sau hoặc mở trang Thời khóa biểu nhé."
                : "I could not read your personal schedule right now. Please try again later or open the Schedule page.";
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
