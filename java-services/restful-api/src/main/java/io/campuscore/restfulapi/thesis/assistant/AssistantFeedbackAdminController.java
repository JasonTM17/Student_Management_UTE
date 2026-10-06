package io.campuscore.restfulapi.thesis.assistant;

import io.campuscore.restfulapi.thesis.assistant.AssistantFeedbackAdminService.FeedbackSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin review surface for assistant message feedback. */
@Tag(name = "Assistant Feedback (Admin)", description = "Tổng hợp đánh giá câu trả lời của trợ lý cho quản trị viên")
@RestController
@Profile("persistence")
@RequestMapping("/api/v1/admin/assistant/feedback")
public class AssistantFeedbackAdminController {

    private final AssistantFeedbackAdminService feedback;

    public AssistantFeedbackAdminController(AssistantFeedbackAdminService feedback) {
        this.feedback = feedback;
    }

    @Operation(summary = "Tổng hợp đánh giá trợ lý",
            description = "Totals by rating, reason breakdown and the most recent rated answers "
                    + "with capped question/answer previews (no rater identity).")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public FeedbackSummary summary(
            @Parameter(description = "Maximum recent rows to return (1-100, default 20)")
            @RequestParam(required = false) Integer limit) {
        return feedback.summary(limit);
    }
}
