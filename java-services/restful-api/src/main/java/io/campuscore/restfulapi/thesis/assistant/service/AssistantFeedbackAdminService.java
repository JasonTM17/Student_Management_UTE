package io.campuscore.restfulapi.thesis.assistant.service;

import io.campuscore.restfulapi.thesis.assistant.dto.FeedbackSummary;
import io.campuscore.restfulapi.thesis.assistant.dto.ReasonCount;
import io.campuscore.restfulapi.thesis.assistant.dto.RecentItem;
import io.campuscore.restfulapi.thesis.assistant.dto.Totals;
import io.campuscore.restfulapi.thesis.assistant.repository.AssistantFeedbackReadRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import io.campuscore.restfulapi.thesis.assistant.rls.AssistantRlsBoundary;

/**
 * Read-only aggregation of {@code assistant.chat_message_feedback} for the
 * admin quality console. Feedback rows previously went nowhere: students could
 * rate answers but no surface exposed the totals, so negative signals were
 * invisible. The admin response deliberately carries no {@code owner_id} —
 * reviewers need message quality, not the identity of the student who rated —
 * and question/answer bodies are capped to a preview length.
 *
 * <p>Runs on the Assistant datasource inside an {@code ADMIN_GOVERNANCE}
 * boundary: the V106 {@code *_admin_read} policies grant that scope a
 * cross-owner SELECT while writes remain owner-scoped. Using the primary
 * datasource here would silently return zero rows under FORCE RLS.
 */
@Service
@Profile("persistence")
@AssistantRlsBoundary(access = AssistantRlsBoundary.Access.ADMIN_GOVERNANCE)
public class AssistantFeedbackAdminService {

    static final int DEFAULT_RECENT_LIMIT = 20;
    static final int MAX_RECENT_LIMIT = 100;

    private final AssistantFeedbackReadRepository repository;

    public AssistantFeedbackAdminService(AssistantFeedbackReadRepository repository) {
        this.repository = repository;
    }

    public FeedbackSummary summary(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_RECENT_LIMIT
                : Math.max(1, Math.min(MAX_RECENT_LIMIT, requestedLimit));

        long up = 0;
        long down = 0;
        for (Map<String, Object> row : repository.countByRating()) {
            long count = ((Number) row.get("count")).longValue();
            if ("UP".equals(row.get("rating"))) {
                up = count;
            } else if ("DOWN".equals(row.get("rating"))) {
                down = count;
            }
        }

        List<ReasonCount> byReason = repository.countByReason();

        List<RecentItem> recent = repository.findRecentItems(limit);

        List<ReasonCount> reasons = new ArrayList<>(byReason);
        return new FeedbackSummary(new Totals(up, down, up + down), List.copyOf(reasons), List.copyOf(recent));
    }
}
