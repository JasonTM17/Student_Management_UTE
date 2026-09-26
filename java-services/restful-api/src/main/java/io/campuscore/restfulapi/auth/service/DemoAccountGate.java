package io.campuscore.restfulapi.auth.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
 * <p>Optional role scope: {@code campuscore.demo.roles} (env
 * {@code DEMO_ACCOUNTS_ROLES}, comma-separated role names such as
 * {@code STUDENT} or {@code STUDENT,LECTURER}). When enabled AND a scope is
 * set, only demo accounts whose role (via {@code campuscore_auth."UserRole"} →
 * {@code campuscore_auth."Role"."name"}) is in the list become ACTIVE; every
 * other demo account is LOCKED. An empty scope keeps the historical behavior of
 * activating every demo account. The scope never widens the population: the
 * email list below stays the only selector, so a real user can never be
 * touched by either branch.
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
     *
     * <p>Covers the three .edu showcase accounts (V50) and the runbook-documented
     * .demo accounts activated by V82 (the Four-Eyes second approver and the
     * council examiners). {@code DemoAccountGateRunbookParityTest} pins this list
     * to V82's, so the migration and the gate cannot drift apart.
     */
    static final List<String> DEMO_EMAILS = List.of(
            "student@campuscore.edu",
            "lecturer@campuscore.edu",
            "admin@campuscore.edu",
            "admin002@campuscore.demo",
            "lecturer002@campuscore.demo",
            "lecturer003@campuscore.demo",
            "lecturer004@campuscore.demo",
            "lecturer005@campuscore.demo",
            "lecturer006@campuscore.demo",
            "lecturer007@campuscore.demo",
            "lecturer008@campuscore.demo",
            "lecturer009@campuscore.demo",
            "lecturer010@campuscore.demo",
            "lecturer011@campuscore.demo",
            "lecturer012@campuscore.demo");

    private static final String ACTIVE = "ACTIVE";
    /**
     * The same restrict state V48 applies to these accounts. Reusing LOCKED rather
     * than inventing a second "off" value keeps one convention in the repository
     * and keeps this gate consistent with the migration that locks them by default.
     */
    private static final String LOCKED = "LOCKED";

    private final NamedParameterJdbcTemplate jdbc;
    private final boolean enabled;
    /** Uppercased role names from {@code app.demo.roles}; empty means every demo role. */
    private final Set<String> roleScope;

    public DemoAccountGate(
            NamedParameterJdbcTemplate jdbc,
            @Value("${app.demo.accounts-enabled:false}") boolean enabled,
            @Value("${app.demo.roles:}") String roles) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.roleScope = parseRoles(roles);
    }

    /**
     * Role names are case-insensitive and whitespace tolerant; blank entries
     * are dropped so a stray comma cannot widen the scope. Unknown names are
     * kept as-is: the SQL matches on {@code Role."name"}, so a typo simply
     * matches nothing (never the whole population).
     */
    static Set<String> parseRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(roles.split(","))
                .map(role -> role.trim().toUpperCase(Locale.ROOT))
                .filter(role -> !role.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!enabled) {
                // Disabled wins over any role scope: the demo is off entirely.
                int affected = setStatus(DEMO_EMAILS, LOCKED);
                if (affected > 0) {
                    log.info("Demo account gate set {} seeded account(s) to {}", affected, LOCKED);
                }
                return;
            }
            if (roleScope.isEmpty()) {
                int affected = setStatus(DEMO_EMAILS, ACTIVE);
                if (affected > 0) {
                    log.info("Demo account gate set {} seeded account(s) to {}", affected, ACTIVE);
                }
                return;
            }
            // Scoped activation: two set-based updates, still no per-row work.
            // A demo account whose roles are all outside the scope is LOCKED;
            // one with at least one in-scope role is ACTIVE. A demo account
            // with no role rows fails the EXISTS and lands on LOCKED, which is
            // the safe side for an account nobody can authenticate into anyway.
            // Columns are referenced through the full table name (no alias) so
            // the same statement runs unchanged on PostgreSQL and H2.
            int activated = jdbc.update(
                    "UPDATE campuscore_auth.\"User\" SET \"status\" = :active, \"failedLoginAttempts\" = 0,"
                            + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                            + " WHERE \"email\" IN (:emails) AND \"status\" <> :active"
                            + " AND EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                            + " JOIN campuscore_auth.\"Role\" r ON r.\"id\" = ur.\"roleId\""
                            + " WHERE ur.\"userId\" = campuscore_auth.\"User\".\"id\" AND r.\"name\" IN (:roles))",
                    new MapSqlParameterSource()
                            .addValue("active", ACTIVE)
                            .addValue("emails", DEMO_EMAILS)
                            .addValue("roles", roleScope));
            int locked = jdbc.update(
                    "UPDATE campuscore_auth.\"User\" SET \"status\" = :locked, \"failedLoginAttempts\" = 0,"
                            + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                            + " WHERE \"email\" IN (:emails) AND \"status\" <> :locked"
                            + " AND NOT EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                            + " JOIN campuscore_auth.\"Role\" r ON r.\"id\" = ur.\"roleId\""
                            + " WHERE ur.\"userId\" = campuscore_auth.\"User\".\"id\" AND r.\"name\" IN (:roles))",
                    new MapSqlParameterSource()
                            .addValue("locked", LOCKED)
                            .addValue("emails", DEMO_EMAILS)
                            .addValue("roles", roleScope));
            if (activated + locked > 0) {
                // Counts only: the account state is operational information, the
                // credentials are not.
                log.info("Demo account gate scoped to {} activated {} and locked {} seeded account(s)",
                        roleScope, activated, locked);
            }
        } catch (DataAccessException ex) {
            log.warn("Demo account gate skipped; seeded accounts keep their stored status");
        }
    }

    /** One bulk update applying a single status to every demo email that is not already in it. */
    private int setStatus(List<String> emails, String status) {
        return jdbc.update(
                "UPDATE campuscore_auth.\"User\" SET \"status\" = :status, \"failedLoginAttempts\" = 0,"
                        + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"email\" IN (:emails) AND \"status\" <> :status",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("emails", emails));
    }
}
