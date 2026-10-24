package io.campuscore.restfulapi.system.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.campuscore.restfulapi.system.repository.DatabaseProbeRepository;

/**
 * Scheduled database keep-alive heartbeat.
 * Periodically executes a lightweight query against PostgreSQL to keep pooled
 * connections warm. Disabled by default so the Neon free-tier compute may
 * autosuspend between requests; each probe counts as query activity and
 * would keep the compute awake.
 */
@Component
@Profile("persistence")
@ConditionalOnProperty(prefix = "app.keepalive", name = "enabled", havingValue = "true", matchIfMissing = false)
public class DatabaseKeepAliveScheduler {

    private static final Logger log = LoggerFactory.getLogger(DatabaseKeepAliveScheduler.class);

    private final DatabaseProbeRepository probe;

    public DatabaseKeepAliveScheduler(DatabaseProbeRepository probe) {
        this.probe = probe;
    }

    /**
     * Executes every 4 hours (14,400,000 ms) with an initial delay of 2 minutes after startup.
     */
    @Scheduled(
            fixedDelayString = "${app.keepalive.db-interval-ms:14400000}",
            initialDelayString = "${app.keepalive.db-initial-delay-ms:120000}")
    public void pingDatabase() {
        try {
            Integer result = probe.probe();
            log.info("Database keep-alive ping succeeded (probe: {})", result);
        } catch (DataAccessException ex) {
            log.warn("Database keep-alive ping encountered non-fatal error: {}", ex.getMessage());
        }
    }
}
