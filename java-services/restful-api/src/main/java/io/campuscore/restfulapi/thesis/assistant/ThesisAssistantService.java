package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.ThesisAssistantDtos.ChatResponse;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Deterministic assistant foundation for the Java monolith.
 *
 * <p>This candidate intentionally does not call an external LLM provider. It
 * preserves the web/mobile API shape and gives safe academic guidance until a
 * later provider-backed wave has secrets, evals, canary and rollback evidence.</p>
 */
@Service
@ConditionalOnProperty(prefix = "migration.thesis-assistant", name = "enabled", havingValue = "true")
public class ThesisAssistantService {

    static final String LOCAL_MODEL = "campuscore-local-advisor-v1";

    public ChatResponse answer(String message, String locale) {
        String normalizedLocale = "vi".equals(locale) ? "vi" : "en";
        String normalizedMessage = message == null ? "" : message.trim();
        if (normalizedMessage.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
        if (normalizedMessage.length() > 2000) {
            throw new IllegalArgumentException("message must contain at most 2000 characters");
        }
        String lower = normalizedMessage.toLowerCase(Locale.ROOT);
        return new ChatResponse(response(lower, normalizedLocale), LOCAL_MODEL, true);
    }

    private static String response(String message, String locale) {
        if ("vi".equals(locale)) {
            return vietnameseResponse(message);
        }
        return englishResponse(message);
    }

    private static String vietnameseResponse(String message) {
        if (containsAny(message, "đề tài", "de tai", "topic", "thesis")) {
            return "Bạn nên chọn đề tài theo 3 tiêu chí: dữ liệu có thể kiểm chứng, phạm vi vừa sức trong học kỳ, "
                    + "và có người hướng dẫn phù hợp. Hãy bắt đầu bằng một câu hỏi nghiên cứu, sau đó chia thành "
                    + "mục tiêu, dữ liệu, phương pháp và tiêu chí nghiệm thu.";
        }
        if (containsAny(message, "nhóm", "group", "member", "thành viên")) {
            return "Với nhóm luận văn, hãy kiểm tra vòng đăng ký đang mở, số lượng thành viên tối đa, vai trò của "
                    + "từng bạn và trạng thái duyệt đề tài trước khi gửi yêu cầu mới.";
        }
        if (containsAny(message, "hội đồng", "council", "bảo vệ", "schedule", "lịch")) {
            return "Để chuẩn bị bảo vệ, hãy theo dõi hội đồng, phòng, thời gian, trạng thái chấm điểm và các nhận "
                    + "xét cần hoàn tất. Nếu lịch chưa xuất hiện, liên hệ cố vấn hoặc giáo vụ để xác nhận.";
        }
        return "Mình có thể giúp bạn định hướng luận văn, đăng ký nhóm, chuẩn bị bảo vệ và kiểm tra các bước học "
                + "vụ. Hãy mô tả mục tiêu hoặc vấn đề cụ thể, mình sẽ gợi ý bước tiếp theo thật ngắn gọn.";
    }

    private static String englishResponse(String message) {
        if (containsAny(message, "topic", "thesis", "proposal", "research")) {
            return "Choose a thesis topic by checking three things: verifiable data, a semester-sized scope, and an "
                    + "available supervisor. Start with one research question, then define objectives, data, method, "
                    + "and acceptance criteria.";
        }
        if (containsAny(message, "group", "member", "team")) {
            return "For thesis groups, confirm the registration round is open, the member limit is respected, roles "
                    + "are clear, and the selected topic is approved before submitting another action.";
        }
        if (containsAny(message, "council", "defense", "schedule", "room", "scoring")) {
            return "For defense preparation, track the council, room, time, scoring status, and required feedback. "
                    + "If the schedule is missing, ask your advisor or academic office to confirm the assignment.";
        }
        return "I can help with thesis planning, group registration, defense preparation, and academic next steps. "
                + "Share the concrete goal or blocker and I will suggest a concise next action.";
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
