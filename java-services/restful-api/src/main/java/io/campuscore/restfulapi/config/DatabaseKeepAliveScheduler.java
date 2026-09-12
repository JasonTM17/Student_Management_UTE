package io.campuscore.restfulapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled database keep-alive heartbeat.
 * Periodically executes a lightweight query against PostgreSQL to maintain
 * connection pool vitality and prevent Supabase free-tier dormancy.
 */
@Component
@Profile("persistence")
public class DatabaseKeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseKeepAliveScheduler.class);

    private final JdbcOperations jdbc;

    public DatabaseKeepAliveScheduler(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Executes every 4 hours (14,400,000 ms) with an initial delay of 2 minutes after startup.
     */
    @Scheduled(
            fixedDelayString = "${app.keepalive.db-interval-ms:14400000}",
            initialDelayString = "${app.keepalive.db-initial-delay-ms:120000}")
    public void pingDatabase() {
        try {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM engagement.\"Announcement\"", Integer.class);
            log.info("Database keep-alive ping succeeded (announcements count: {})", count);
        } catch (DataAccessException ex) {
            log.warn("Database keep-alive ping encountered non-fatal error: {}", ex.getMessage());
        }
    }
}
