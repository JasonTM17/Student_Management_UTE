package io.campuscore.restfulapi.thesis.assistant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

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

    static final int MAX_PREVIEW_CODE_POINTS = 280;
    static final int DEFAULT_RECENT_LIMIT = 20;
    static final int MAX_RECENT_LIMIT = 100;

    private final NamedParameterJdbcTemplate jdbc;

    public AssistantFeedbackAdminService(
            @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public FeedbackSummary summary(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_RECENT_LIMIT
                : Math.max(1, Math.min(MAX_RECENT_LIMIT, requestedLimit));

        long up = 0;
        long down = 0;
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT rating, COUNT(*) AS count FROM assistant.chat_message_feedback GROUP BY rating",
                new MapSqlParameterSource())) {
            long count = ((Number) row.get("count")).longValue();
            if ("UP".equals(row.get("rating"))) {
                up = count;
            } else if ("DOWN".equals(row.get("rating"))) {
                down = count;
            }
        }

        List<ReasonCount> byReason = jdbc.query(
                "SELECT COALESCE(reason, 'NONE') AS reason, COUNT(*) AS count"
                        + " FROM assistant.chat_message_feedback GROUP BY reason"
                        + " ORDER BY count DESC, reason",
                new MapSqlParameterSource(),
                (rs, i) -> new ReasonCount(rs.getString("reason"), rs.getLong("count")));

        List<RecentItem> recent = jdbc.query(
                "SELECT f.message_id, f.rating, f.reason, f.updated_at,"
                        + " m.content AS answer, m.model, m.degraded, m.reason_code,"
                        + " (SELECT u.content FROM assistant.chat_message u"
                        + "  WHERE u.turn_id = m.turn_id AND u.role = 'USER'"
                        + "  ORDER BY u.created_at LIMIT 1) AS question"
                        + " FROM assistant.chat_message_feedback f"
                        + " JOIN assistant.chat_message m ON m.id = f.message_id"
                        + " ORDER BY f.updated_at DESC, f.created_at DESC, f.message_id LIMIT :limit",
                new MapSqlParameterSource("limit", limit),
                (rs, i) -> new RecentItem(
                        rs.getObject("message_id", UUID.class),
                        rs.getString("rating"),
                        rs.getString("reason"),
                        rs.getTimestamp("updated_at") == null ? null
                                : rs.getTimestamp("updated_at").toInstant(),
                        cap(rs.getString("question")),
                        cap(rs.getString("answer")),
                        rs.getString("model"),
                        rs.getBoolean("degraded"),
                        rs.getString("reason_code")));

        List<ReasonCount> reasons = new ArrayList<>(byReason);
        return new FeedbackSummary(new Totals(up, down, up + down), List.copyOf(reasons), List.copyOf(recent));
    }

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String cap(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= MAX_PREVIEW_CODE_POINTS) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_PREVIEW_CODE_POINTS));
    }

    public record Totals(long up, long down, long total) { }

    public record ReasonCount(String reason, long count) { }

    public record RecentItem(
            UUID messageId,
            String rating,
            String reason,
            Instant updatedAt,
            String questionPreview,
            String answerPreview,
            String model,
            boolean degraded,
            String reasonCode) { }

    public record FeedbackSummary(Totals totals, List<ReasonCount> byReason, List<RecentItem> recent) { }
}
