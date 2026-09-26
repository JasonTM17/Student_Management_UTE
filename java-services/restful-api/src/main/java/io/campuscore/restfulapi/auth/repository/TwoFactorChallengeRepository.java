package io.campuscore.restfulapi.auth.repository;

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
 * JDBC adapter for campuscore_auth."TwoFactorChallenge" (V85): one-time
 * email OTP challenges for the ENABLE opt-in and the LOGIN second factor.
 * The six-digit code itself is never stored — only its SHA-256 hex digest.
 */
@Repository
@Profile("persistence")
public class TwoFactorChallengeRepository {

    public static final String PURPOSE_LOGIN = "LOGIN";
    public static final String PURPOSE_ENABLE = "ENABLE";

    private static final String TABLE = "\"campuscore_auth\".\"TwoFactorChallenge\"";
    private static final RowMapper<ChallengeRecord> CHALLENGE_MAPPER =
            TwoFactorChallengeRepository::mapChallenge;

    private final NamedParameterJdbcTemplate jdbc;

    public TwoFactorChallengeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Persists a new challenge and returns its generated identifier. */
    public String insert(String userId, String purpose, String codeHash, Instant expiresAt) {
        String id = java.util.UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO " + TABLE
                        + " (\"id\", \"userId\", \"purpose\", \"codeHash\", \"expiresAt\", \"attempts\", \"createdAt\")"
                        + " VALUES (:id, :userId, :purpose, :codeHash, :expiresAt, 0, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("userId", userId)
                        .addValue("purpose", purpose)
                        .addValue("codeHash", codeHash)
                        .addValue("expiresAt", offsetDateTime(expiresAt)));
        return id;
    }

    public Optional<ChallengeRecord> findById(String id) {
        List<ChallengeRecord> matches = jdbc.query(
                "SELECT \"id\", \"userId\", \"purpose\", \"codeHash\", \"expiresAt\", \"consumedAt\","
                        + " \"attempts\", \"createdAt\" FROM " + TABLE
                        + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                CHALLENGE_MAPPER);
        return matches.stream().findFirst();
    }

    /**
     * Atomically bumps the wrong-code counter for an unconsumed challenge.
     * Returns the new attempts value, or {@code -1} when the challenge was
     * already consumed (or does not exist) so the caller can treat it as
     * invalid instead of racing a consumed row.
     */
    public int incrementAttempts(String id) {
        int updated = jdbc.update(
                "UPDATE " + TABLE + " SET \"attempts\" = \"attempts\" + 1"
                        + " WHERE \"id\" = :id AND \"consumedAt\" IS NULL",
                new MapSqlParameterSource("id", id));
        if (updated == 0) {
            return -1;
        }
        Integer attempts = jdbc.queryForObject(
                "SELECT \"attempts\" FROM " + TABLE + " WHERE \"id\" = :id",
                new MapSqlParameterSource("id", id),
                Integer.class);
        return attempts == null ? -1 : attempts;
    }

    /** Marks the challenge as used (success, lock-out, or manual consumption). */
    public void consume(String id, Instant consumedAt) {
        jdbc.update(
                "UPDATE " + TABLE + " SET \"consumedAt\" = :consumedAt"
                        + " WHERE \"id\" = :id AND \"consumedAt\" IS NULL",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("consumedAt", offsetDateTime(consumedAt)));
    }

    private static ChallengeRecord mapChallenge(java.sql.ResultSet resultSet, int ignored)
            throws java.sql.SQLException {
        return new ChallengeRecord(
                resultSet.getString("id"),
                resultSet.getString("userId"),
                resultSet.getString("purpose"),
                resultSet.getString("codeHash"),
                instant(resultSet.getTimestamp("expiresAt")),
                instant(resultSet.getTimestamp("consumedAt")),
                resultSet.getInt("attempts"),
                instant(resultSet.getTimestamp("createdAt")));
    }

    /**
     * TIMESTAMP WITH TIME ZONE columns carry an absolute instant; reading them
     * through {@code Timestamp#toLocalDateTime()} re-interpreted that
     * wall-clock as UTC and shifted expiry windows by the JVM offset (a
     * challenge that had just expired could still verify on a +07 JVM). The
     * driver-provided offset is authoritative in both directions.
     */
    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static OffsetDateTime offsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record ChallengeRecord(
            String id,
            String userId,
            String purpose,
            String codeHash,
            Instant expiresAt,
            Instant consumedAt,
            int attempts,
            Instant createdAt) {
    }
}
