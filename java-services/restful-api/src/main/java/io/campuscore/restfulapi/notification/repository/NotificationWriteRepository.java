package io.campuscore.restfulapi.notification.repository;

import io.campuscore.restfulapi.notification.web.NotificationReadDtos.NotificationResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC write adapter for the legacy notifications table. */
@Repository
@Profile("persistence")
@ConditionalOnProperty(prefix = "migration.notifications-write", name = "enabled", havingValue = "true")
public class NotificationWriteRepository {

    private static final String TABLE = "notifications.notification";

    private static final String SELECT_COLUMNS = """
            id, user_id, title, message, type, link, is_read, read_at, created_at, updated_at
            """;

    private static final RowMapper<NotificationResponse> ROW_MAPPER =
            NotificationWriteRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationWriteRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<NotificationResponse> findOwned(String userId, String notificationId) {
        return jdbc.query(
                        "SELECT " + SELECT_COLUMNS
                                + " FROM " + TABLE
                                + " WHERE id = :id AND user_id = :userId",
                        new MapSqlParameterSource()
                                .addValue("id", notificationId)
                                .addValue("userId", userId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    public Optional<NotificationResponse> findById(String notificationId) {
        return jdbc.query(
                        "SELECT " + SELECT_COLUMNS
                                + " FROM " + TABLE
                                + " WHERE id = :id",
                        new MapSqlParameterSource("id", notificationId),
                        ROW_MAPPER)
                .stream()
                .findFirst();
    }

    public void markRead(String notificationId) {
        jdbc.update(
                "UPDATE " + TABLE
                        + " SET is_read = TRUE, read_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE id = :id",
                new MapSqlParameterSource("id", notificationId));
    }

    public int markAllRead(String userId) {
        return jdbc.update(
                "UPDATE " + TABLE
                        + " SET is_read = TRUE, read_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE user_id = :userId AND is_read = FALSE",
                new MapSqlParameterSource("userId", userId));
    }

    public void delete(String notificationId) {
        jdbc.update(
                "DELETE FROM " + TABLE + " WHERE id = :id",
                new MapSqlParameterSource("id", notificationId));
    }

    public void update(UpdateNotificationCommand command) {
        List<String> assignments = new ArrayList<>();
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", command.id());
        add(assignments, parameters, "user_id", "userId", command.userId());
        add(assignments, parameters, "title", "title", command.title());
        add(assignments, parameters, "message", "message", command.message());
        add(assignments, parameters, "type", "type", command.type());
        add(assignments, parameters, "link", "link", command.link());
        if (assignments.isEmpty()) {
            return;
        }
        assignments.add("updated_at = CURRENT_TIMESTAMP");
        jdbc.update(
                "UPDATE " + TABLE + " SET " + String.join(", ", assignments) + " WHERE id = :id",
                parameters);
    }

    private static void add(
            List<String> assignments,
            MapSqlParameterSource parameters,
            String column,
            String parameter,
            PatchValue<String> value) {
        if (!value.present()) {
            return;
        }
        assignments.add(column + " = :" + parameter);
        parameters.addValue(parameter, value.value());
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

    public record UpdateNotificationCommand(
            String id,
            PatchValue<String> userId,
            PatchValue<String> title,
            PatchValue<String> message,
            PatchValue<String> type,
            PatchValue<String> link) {
    }

    public record PatchValue<T>(boolean present, T value) {

        public static <T> PatchValue<T> omitted() {
            return new PatchValue<>(false, null);
        }

        public static <T> PatchValue<T> present(T value) {
            return new PatchValue<>(true, value);
        }
    }
}
