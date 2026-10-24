package io.campuscore.restfulapi.system.repository;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Repository;

/**
 * The {@code SELECT 1} liveness statement shared by the readiness probe and
 * the keep-alive heartbeat.
 */
@Repository
public class DatabaseProbeRepository {

    private final JdbcOperations jdbc;

    public DatabaseProbeRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    public Integer probe() {
        return jdbc.queryForObject("SELECT 1", Integer.class);
    }
}
