package io.campuscore.restfulapi.auth.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persistent hashed rate-limit buckets for public auth lifecycle endpoints. */
@Repository
@Profile("persistence")
public class AuthRateLimitRepository {

    private static final String TABLE = "\"campuscore_auth\".\"AuthRateLimitBucket\"";
    private final NamedParameterJdbcTemplate jdbc;
    private final boolean postgres;
    private final ConcurrentHashMap<String, Object> localLocks = new ConcurrentHashMap<>();

    public AuthRateLimitRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.postgres = isPostgres(jdbc.getJdbcTemplate().getDataSource());
    }

    public boolean consume(
            String scope,
            String bucketKeyHash,
            Instant windowStart,
            int maximum,
            Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("scope", scope)
                .addValue("bucketKeyHash", bucketKeyHash)
                .addValue("windowStart", localDateTime(windowStart))
                .addValue("maximum", maximum)
                .addValue("now", localDateTime(now));
        if (postgres) {
            List<Integer> counts = jdbc.query(
                    "INSERT INTO " + TABLE
                            + " (\"scope\", \"bucketKeyHash\", \"windowStart\", \"requestCount\", \"updatedAt\")"
                            + " VALUES (:scope, :bucketKeyHash, :windowStart, 1, :now)"
                            + " ON CONFLICT (\"scope\", \"bucketKeyHash\", \"windowStart\") DO UPDATE"
                            + " SET \"requestCount\" = " + TABLE + ".\"requestCount\" + 1, \"updatedAt\" = :now"
                            + " WHERE " + TABLE + ".\"requestCount\" < :maximum"
                            + " RETURNING \"requestCount\"",
                    parameters,
                    (rs, ignored) -> rs.getInt(1));
            return !counts.isEmpty();
        }

        String lockKey = scope + ':' + bucketKeyHash + ':' + windowStart;
        Object lock = localLocks.computeIfAbsent(lockKey, ignored -> new Object());
        synchronized (lock) {
            Integer count = jdbc.query(
                            "SELECT \"requestCount\" FROM " + TABLE
                                    + " WHERE \"scope\" = :scope AND \"bucketKeyHash\" = :bucketKeyHash"
                                    + " AND \"windowStart\" = :windowStart FOR UPDATE",
                            parameters,
                            (rs, ignored) -> rs.getInt(1))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (count == null) {
                jdbc.update(
                        "INSERT INTO " + TABLE
                                + " (\"scope\", \"bucketKeyHash\", \"windowStart\", \"requestCount\", \"updatedAt\")"
                                + " VALUES (:scope, :bucketKeyHash, :windowStart, 1, :now)",
                        parameters);
                return true;
            }
            if (count >= maximum) {
                return false;
            }
            jdbc.update(
                    "UPDATE " + TABLE + " SET \"requestCount\" = \"requestCount\" + 1, \"updatedAt\" = :now"
                            + " WHERE \"scope\" = :scope AND \"bucketKeyHash\" = :bucketKeyHash"
                            + " AND \"windowStart\" = :windowStart",
                    parameters);
            return true;
        }
    }

    private static boolean isPostgres(DataSource dataSource) {
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT)
                    .contains("postgres");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to identify auth rate-limit database", exception);
        }
    }

    private static LocalDateTime localDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
