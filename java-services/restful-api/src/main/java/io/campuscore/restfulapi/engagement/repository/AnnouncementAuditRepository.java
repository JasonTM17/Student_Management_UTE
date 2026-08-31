package io.campuscore.restfulapi.engagement.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.campuscore.restfulapi.engagement.web.AnnouncementReadDtos.AnnouncementHistoryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable audit persistence for Admin announcement governance actions. */
@Repository
@Profile("persistence")
public class AnnouncementAuditRepository {

    private static final String TABLE = "\"engagement\".\"AnnouncementAudit\"";
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AnnouncementAuditRepository(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void append(AuditCommand command) {
        jdbc.update(
                "INSERT INTO " + TABLE + " ("
                        + "\"id\", \"announcementId\", \"action\", \"actorId\", \"actorLabel\", \"reason\", "
                        + "\"version\", \"beforeState\", \"afterState\", \"createdAt\") "
                        + "VALUES (:id, :announcementId, :action, :actorId, :actorLabel, :reason, "
                        + ":version, :beforeState, :afterState, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", command.id())
                        .addValue("announcementId", command.announcementId())
                        .addValue("action", command.action())
                        .addValue("actorId", command.actorId())
                        .addValue("actorLabel", command.actorLabel())
                        .addValue("reason", command.reason())
                        .addValue("version", command.version())
                        .addValue("beforeState", command.beforeState())
                        .addValue("afterState", command.afterState())
                        .addValue(
                                "createdAt",
                                LocalDateTime.ofInstant(command.createdAt(), ZoneOffset.UTC)));
    }

    public List<AnnouncementHistoryResponse> findByAnnouncementId(
            String announcementId,
            long offset,
            int limit) {
        return jdbc.query(
                "SELECT \"id\", \"announcementId\", \"action\", \"actorId\", \"actorLabel\", \"reason\", "
                        + "\"version\", \"beforeState\", \"afterState\", \"createdAt\" FROM " + TABLE
                        + " WHERE \"announcementId\" = :announcementId"
                        + " ORDER BY \"createdAt\" DESC, \"id\" DESC"
                        + " LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource()
                        .addValue("announcementId", announcementId)
                        .addValue("limit", limit)
                        .addValue("offset", offset),
                (resultSet, ignored) -> mapRow(resultSet));
    }

    public long countByAnnouncementId(String announcementId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE
                        + " WHERE \"announcementId\" = :announcementId",
                new MapSqlParameterSource("announcementId", announcementId),
                Long.class);
        return Objects.requireNonNullElse(count, 0L);
    }

    private AnnouncementHistoryResponse mapRow(ResultSet resultSet) throws SQLException {
        return new AnnouncementHistoryResponse(
                resultSet.getString("id"),
                resultSet.getString("announcementId"),
                resultSet.getString("action"),
                resultSet.getString("actorId"),
                resultSet.getString("actorLabel"),
                resultSet.getString("reason"),
                resultSet.getInt("version"),
                instant(resultSet, "createdAt"),
                parse(resultSet.getString("beforeState")),
                parse(resultSet.getString("afterState")));
    }

    private JsonNode parse(String value) {
        if (value == null || value.isBlank()) {
            return NullNode.instance;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored announcement audit snapshot is invalid", exception);
        }
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(
                column,
                Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record AuditCommand(
            String id,
            String announcementId,
            String action,
            String actorId,
            String actorLabel,
            String reason,
            int version,
            String beforeState,
            String afterState,
            Instant createdAt) {
    }
}
