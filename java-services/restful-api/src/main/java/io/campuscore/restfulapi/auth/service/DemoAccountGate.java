package io.campuscore.restfulapi.auth.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Keeps the seeded showcase accounts in the state the deployment asked for.
 *
 * <p>The demo passwords are published in this repository's history, so an account
 * that is ACTIVE on a host reachable by the public is an open administrator
 * login. Nothing in the seed can express "active only where the operator asked
 * for it", so this gate re-applies the decision on every boot from
 * {@code campuscore.demo.accounts-enabled} (env {@code DEMO_ACCOUNTS_ENABLED}).
 * A Flyway migration cannot do this: a versioned migration runs once, and a
 * repeatable one only re-runs when its checksum changes, so neither reacts to a
 * changed environment variable.
 *
 * <p>The gate is fail-soft. Disabling the demo accounts is a hardening step, not
 * a correctness requirement, so a database hiccup must not stop the application
 * from starting.
 */
@Component
@Profile("persistence")
public class DemoAccountGate implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccountGate.class);

    /**
     * The accounts seeded for evaluation. Deliberately a fixed list: this gate
     * must never touch a real user, so it matches exact seeded addresses rather
     * than any pattern.
     */
    static final List<String> DEMO_EMAILS = List.of(
            "student@campuscore.edu", "lecturer@campuscore.edu", "admin@campuscore.edu");

    private static final String ACTIVE = "ACTIVE";
    /**
     * The same restrict state V48 applies to these accounts. Reusing LOCKED rather
     * than inventing a second "off" value keeps one convention in the repository
     * and keeps this gate consistent with the migration that locks them by default.
     */
    private static final String LOCKED = "LOCKED";

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean enabled;

    public DemoAccountGate(
            NamedParameterJdbcTemplate jdbc,
            @Value("${app.demo.accounts-enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        String status = enabled ? ACTIVE : LOCKED;
        try {
            int affected = jdbc.update(
                    "UPDATE campuscore_auth.\"User\" SET \"status\" = :status, \"failedLoginAttempts\" = 0,"
                            + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                            + " WHERE \"email\" IN (:emails) AND \"status\" <> :status",
                    new MapSqlParameterSource()
                            .addValue("status", status)
                            .addValue("emails", DEMO_EMAILS));
            if (affected > 0) {
                // Counts only: the account state is operational information, the
                // credentials are not.
                log.info("Demo account gate set {} seeded account(s) to {}", affected, status);
            }
        } catch (DataAccessException ex) {
            log.warn("Demo account gate skipped; seeded accounts keep their stored status");
        }
    }
}
