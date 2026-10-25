package io.campuscore.restfulapi.thesis.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Database wall-clock access for lifecycle decisions and client clock samples. */
@Repository
public class ThesisDatabaseClockRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean postgres;

    public ThesisDatabaseClockRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        String databaseProduct = jdbc.getJdbcTemplate().execute(
                (org.springframework.jdbc.core.ConnectionCallback<String>)
                        connection -> connection.getMetaData().getDatabaseProductName());
        this.postgres = databaseProduct != null
                && databaseProduct.toLowerCase(Locale.ROOT).contains("postgres");
    }

    public Instant now() {
        String query = postgres ? "SELECT clock_timestamp()" : "SELECT CURRENT_TIMESTAMP";
        return jdbc.getJdbcTemplate().queryForObject(query,
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
    }

    /**
     * Atomically moves an early registration start to database wall time while
     * preserving the lecturer-end and registration-end schedule boundaries.
     * The caller holds the round row lock in its surrounding transaction.
     */
    public int clampRegistrationStartIfFuture(UUID roundId, Instant sampledNow) {
        if (postgres) {
            return jdbc.update(
                    "WITH effective_time AS MATERIALIZED (SELECT clock_timestamp() AS now) "
                            + "UPDATE thesis.thesis_registration_round "
                            + "SET registration_start = effective_time.now, updated_at = effective_time.now, "
                            + "version = version + 1 FROM effective_time "
                            + "WHERE id = :id AND registration_start > effective_time.now "
                            + "AND lecturer_submit_end <= effective_time.now "
                            + "AND registration_end > effective_time.now",
                    new MapSqlParameterSource("id", roundId));
        }
        return jdbc.update(
                "UPDATE thesis.thesis_registration_round "
                        + "SET registration_start = :databaseNow, updated_at = :databaseNow, "
                        + "version = version + 1 "
                        + "WHERE id = :id AND registration_start > :databaseNow "
                        + "AND lecturer_submit_end <= :databaseNow AND registration_end > :databaseNow",
                new MapSqlParameterSource("id", roundId)
                        .addValue("databaseNow", Timestamp.from(sampledNow)));
    }
}
