package io.campuscore.restfulapi.thesis.assistant.repository;

import io.campuscore.restfulapi.thesis.assistant.dto.ReasonCount;
import io.campuscore.restfulapi.thesis.assistant.dto.RecentItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import io.campuscore.restfulapi.thesis.assistant.config.AssistantDatabaseConfiguration;

/**
 * Read-only aggregation statements over {@code assistant.chat_message_feedback}
 * for the admin quality console. Runs on the assistant datasource inside the
 * caller's ADMIN_GOVERNANCE boundary.
 */
@Repository
@Profile("persistence")
public class AssistantFeedbackReadRepository {

    static final int MAX_PREVIEW_CODE_POINTS = 280;

    private final NamedParameterJdbcTemplate jdbc;

    public AssistantFeedbackReadRepository(
            @Qualifier(AssistantDatabaseConfiguration.JDBC_TEMPLATE) NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> countByRating() {
        return jdbc.queryForList(
                "SELECT rating, COUNT(*) AS count FROM assistant.chat_message_feedback GROUP BY rating",
                new MapSqlParameterSource());
    }

    public List<ReasonCount> countByReason() {
        return jdbc.query(
                "SELECT COALESCE(reason, 'NONE') AS reason, COUNT(*) AS count"
                        + " FROM assistant.chat_message_feedback GROUP BY reason"
                        + " ORDER BY count DESC, reason",
                new MapSqlParameterSource(),
                (rs, i) -> new ReasonCount(rs.getString("reason"), rs.getLong("count")));
    }

    public List<RecentItem> findRecentItems(int limit) {
        return jdbc.query(
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
    }

    /** Truncates by code points so Vietnamese diacritics are never split. */
    private static String cap(String value) {
        if (value == null || value.codePointCount(0, value.length()) <= MAX_PREVIEW_CODE_POINTS) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, MAX_PREVIEW_CODE_POINTS));
    }
}
