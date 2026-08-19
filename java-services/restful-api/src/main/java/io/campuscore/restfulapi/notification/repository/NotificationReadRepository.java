package io.campuscore.restfulapi.notification.repository;

import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read adapter for the legacy notifications schema.
 *
 * <p>There is deliberately no JPA entity or Flyway migration here. The
 * monolith must not create, mutate, or become the owner of the legacy table as
 * part of this candidate.</p>
 */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.notifications-read", name = "enabled", havingValue = "true")
public class NotificationReadRepository {

    private static final String SELECT_COLUMNS = """
            id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at
            """;

    private static final RowMapper<NotificationResponse> ROW_MAPPER =
            NotificationReadRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationResponse> findMy(
            String userId,
            long offset,
            int limit,
            Boolean isRead) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM notifications.notification"
                + whereClause(isRead)
                + " ORDER BY created_at DESC"
                + " LIMIT :limit OFFSET :offset";
        return jdbc.query(
                sql,
                parameters(userId, offset, limit, isRead),
                ROW_MAPPER);
    }

    public long countMy(String userId, Boolean isRead) {
        String sql = "SELECT COUNT(*) FROM notifications.notification"
                + whereClause(isRead);
        Long count = jdbc.queryForObject(
                sql,
                parameters(userId, 0, 0, isRead),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    public long countUnread(String userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications.notification"
                        + " WHERE user_id = :userId AND is_read = FALSE",
                new MapSqlParameterSource("userId", userId),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    private MapSqlParameterSource parameters(
            String userId,
            long offset,
            int limit,
            Boolean isRead) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("offset", offset)
                .addValue("limit", limit);
        if (isRead != null) {
            parameters.addValue("isRead", isRead);
        }
        return parameters;
    }

    private String whereClause(Boolean isRead) {
        return isRead == null
                ? " WHERE user_id = :userId"
                : " WHERE user_id = :userId AND is_read = :isRead";
    }

    private static NotificationResponse mapRow(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return new NotificationResponse(
                resultSet.getString("id"),
                resultSet.getString("user_id"),
                resultSet.getString("title"),
                resultSet.getString("message"),
                resultSet.getString("type"),
                resultSet.getString("link"),
                resultSet.getBoolean("is_read"),
                instant(resultSet.getTimestamp("read_at")),
                instant(resultSet.getTimestamp("created_at")),
                instant(resultSet.getTimestamp("updated_at")));
    }

    private static java.time.Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
