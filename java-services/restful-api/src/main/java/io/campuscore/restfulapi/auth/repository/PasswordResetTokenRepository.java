package io.campuscore.restfulapi.auth.repository;

import io.campuscore.restfulapi.auth.model.TokenRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC adapter for campuscore_auth."PasswordResetToken" (V114): one-time
 * reset links for the self-service forgot-password flow. Only the SHA-256 hex
 * of each token is stored, so a leaked row is never a usable credential.
 * Timestamps are TIMESTAMP WITH TIME ZONE — see the note in
 * {@link TwoFactorChallengeRepository} on reading them as instants.
 */
@Repository
@Profile("persistence")
public class PasswordResetTokenRepository {

    private static final String TABLE = "\"campuscore_auth\".\"PasswordResetToken\"";
    private static final RowMapper<TokenRecord> TOKEN_MAPPER =
            PasswordResetTokenRepository::mapToken;

    private final NamedParameterJdbcTemplate jdbc;

    public PasswordResetTokenRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Persists a new token hash and returns its generated identifier. */
    public String insert(String userId, String tokenHash, Instant expiresAt, String requestIp) {
        String id = java.util.UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO " + TABLE
                        + " (\"id\", \"userId\", \"tokenHash\", \"expiresAt\", \"usedAt\", \"createdAt\", \"requestIp\")"
                        + " VALUES (:id, :userId, :tokenHash, :expiresAt, NULL, CURRENT_TIMESTAMP, :requestIp)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("tokenHash", tokenHash)
                        .addValue("expiresAt", offsetDateTime(expiresAt))
                        .addValue("requestIp", requestIp));
        return id;
    }

    public Optional<TokenRecord> findByTokenHash(String tokenHash) {
        List<TokenRecord> matches = jdbc.query(
                "SELECT \"id\", \"userId\", \"tokenHash\", \"expiresAt\", \"usedAt\", \"createdAt\", \"requestIp\""
                        + " FROM " + TABLE + " WHERE \"tokenHash\" = :tokenHash",
                new MapSqlParameterSource("tokenHash", tokenHash),
                TOKEN_MAPPER);
        return matches.stream().findFirst();
    }

    /**
     * Atomically consumes the token. The conditional UPDATE takes the row
     * lock on PostgreSQL, so two concurrent consume attempts cannot both win:
     * the loser re-evaluates the WHERE clause after the winner commits and
     * updates zero rows. Returns {@code true} only for the winner.
     */
    public boolean consumeIfUnusedAndUnexpired(String tokenHash, Instant now) {
        return jdbc.update(
                "UPDATE " + TABLE + " SET \"usedAt\" = :now"
                        + " WHERE \"tokenHash\" = :tokenHash"
                        + " AND \"usedAt\" IS NULL AND \"expiresAt\" > :now",
                new MapSqlParameterSource()
                        .addValue("tokenHash", tokenHash)
                        .addValue("now", offsetDateTime(now))) == 1;
    }

    /** Marks every still-unconsumed token of the user as used (rotation). */
    public void invalidateUnused(String userId, Instant usedAt) {
        jdbc.update(
                "UPDATE " + TABLE + " SET \"usedAt\" = :usedAt"
                        + " WHERE \"userId\" = :userId AND \"usedAt\" IS NULL",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("usedAt", offsetDateTime(usedAt)));
    }

    /** Tokens issued for the user since {@code since} — the hourly cap basis. */
    public int countRecentByUser(String userId, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE
                        + " WHERE \"userId\" = :userId AND \"createdAt\" >= :since",
                new MapSqlParameterSource()
                        .addValue("userId", userId)
                        .addValue("since", offsetDateTime(since)),
                Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Retention sweep: deletes tokens consumed before the cutoff or expired
     * unconsumed before it.
     *
     * @return the number of rows removed
     */
    public int purgeStale(Instant cutoff) {
        return jdbc.update(
                "DELETE FROM " + TABLE
                        + " WHERE (\"usedAt\" IS NOT NULL AND \"usedAt\" < :cutoff)"
                        + " OR (\"usedAt\" IS NULL AND \"expiresAt\" < :cutoff)",
                new MapSqlParameterSource("cutoff", offsetDateTime(cutoff)));
    }

    private static TokenRecord mapToken(ResultSet resultSet, int ignored) throws SQLException {
        return new TokenRecord(
                resultSet.getString("id"),
                resultSet.getString("userId"),
                resultSet.getString("tokenHash"),
                instant(resultSet.getTimestamp("expiresAt")),
                instant(resultSet.getTimestamp("usedAt")),
                instant(resultSet.getTimestamp("createdAt")),
                resultSet.getString("requestIp"));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static OffsetDateTime offsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
