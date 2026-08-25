package io.campuscore.restfulapi.auth.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC boundary for hashed, one-time account lifecycle challenges. */
@Repository
@Profile("persistence")
public class AuthChallengeRepository {

    private static final String TABLE = "\"campuscore_auth\".\"AuthChallenge\"";

    private final NamedParameterJdbcTemplate jdbc;

    public AuthChallengeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(
            String challengeId,
            String userId,
            Purpose purpose,
            String tokenHash,
            Instant expiresAt,
            Instant createdAt) {
        invalidateActive(userId, purpose, createdAt);
        jdbc.update(
                "INSERT INTO " + TABLE
                        + " (\"id\", \"userId\", \"purpose\", \"tokenHash\", \"expiresAt\","
                        + " \"consumedAt\", \"attemptCount\", \"lastSentAt\", \"createdAt\")"
                        + " VALUES (:id, :userId, :purpose, :tokenHash, :expiresAt, NULL, 0, :sentAt, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("id", challengeId)
                        .addValue("userId", userId)
                        .addValue("purpose", purpose.name())
                        .addValue("tokenHash", tokenHash)
                        .addValue("expiresAt", localDateTime(expiresAt))
                        .addValue("sentAt", localDateTime(createdAt))
                        .addValue("createdAt", localDateTime(createdAt)));
    }

    public void invalidateActive(String userId, Purpose purpose, Instant at) {
        jdbc.update(
                "UPDATE " + TABLE + " SET \"consumedAt\" = :at"
                        + " WHERE \"userId\" = :userId AND \"purpose\" = :purpose"
                        + " AND \"consumedAt\" IS NULL",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("purpose", purpose.name())
                        .addValue("at", localDateTime(at)));
    }

    public Optional<Challenge> findByIdForUpdate(String challengeId, Purpose purpose) {
        return queryOne(
                "SELECT \"id\", \"userId\", \"purpose\", \"tokenHash\", \"expiresAt\","
                        + " \"consumedAt\", \"attemptCount\", \"lastSentAt\", \"createdAt\""
                        + " FROM " + TABLE
                        + " WHERE \"id\" = :id AND \"purpose\" = :purpose FOR UPDATE",
                new MapSqlParameterSource()
                        .addValue("id", challengeId)
                        .addValue("purpose", purpose.name()));
    }

    public Optional<Challenge> findLatestForUser(String userId, Purpose purpose, boolean lock) {
        return queryOne(
                "SELECT \"id\", \"userId\", \"purpose\", \"tokenHash\", \"expiresAt\","
                        + " \"consumedAt\", \"attemptCount\", \"lastSentAt\", \"createdAt\""
                        + " FROM " + TABLE
                        + " WHERE \"userId\" = :userId AND \"purpose\" = :purpose"
                        + " ORDER BY \"createdAt\" DESC LIMIT 1" + (lock ? " FOR UPDATE" : ""),
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("purpose", purpose.name()));
    }

    public boolean recordFailedAttempt(String challengeId, int attemptCount, Instant consumedAt) {
        return jdbc.update(
                "UPDATE " + TABLE + " SET \"attemptCount\" = :attemptCount, \"consumedAt\" = :consumedAt"
                        + " WHERE \"id\" = :id AND \"consumedAt\" IS NULL",
                new MapSqlParameterSource()
                        .addValue("id", challengeId)
                        .addValue("attemptCount", attemptCount)
                        .addValue("consumedAt", localDateTime(consumedAt))) == 1;
    }

    public boolean consume(String challengeId, Instant consumedAt) {
        return jdbc.update(
                "UPDATE " + TABLE + " SET \"consumedAt\" = :consumedAt"
                        + " WHERE \"id\" = :id AND \"consumedAt\" IS NULL",
                new MapSqlParameterSource()
                        .addValue("id", challengeId)
                        .addValue("consumedAt", localDateTime(consumedAt))) == 1;
    }

    private Optional<Challenge> queryOne(String sql, MapSqlParameterSource parameters) {
        return jdbc.query(
                        sql,
                        parameters,
                        (rs, ignored) -> new Challenge(
                                rs.getString("id"),
                                rs.getString("userId"),
                                Purpose.valueOf(rs.getString("purpose")),
                                rs.getString("tokenHash"),
                                instant(rs.getTimestamp("expiresAt")),
                                instant(rs.getTimestamp("consumedAt")),
                                rs.getInt("attemptCount"),
                                instant(rs.getTimestamp("lastSentAt")),
                                instant(rs.getTimestamp("createdAt"))))
                .stream()
                .findFirst();
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public enum Purpose {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }

    public record Challenge(
            String id,
            String userId,
            Purpose purpose,
            String tokenHash,
            Instant expiresAt,
            Instant consumedAt,
            int attemptCount,
            Instant lastSentAt,
            Instant createdAt) {
    }
}
