package io.campuscore.restfulapi.auth.repository;

import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Set-based account-state statements behind {@code DemoAccountGate}. Columns
 * are referenced through the full table name (no alias) so the same statement
 * runs unchanged on PostgreSQL and H2.
 */
@Repository
@Profile("persistence & !test")
public class DemoAccountRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DemoAccountRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One bulk update applying a single status to every demo email that is not already in it. */
    public int setStatus(List<String> emails, String status) {
        return jdbc.update(
                "UPDATE campuscore_auth.\"User\" SET \"status\" = :status, \"failedLoginAttempts\" = 0,"
                        + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"email\" IN (:emails) AND \"status\" <> :status",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("emails", emails));
    }

    public int activateScoped(List<String> emails, String status, Set<String> roles) {
        return jdbc.update(
                "UPDATE campuscore_auth.\"User\" SET \"status\" = :active, \"failedLoginAttempts\" = 0,"
                        + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"email\" IN (:emails) AND \"status\" <> :active"
                        + " AND EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                        + " JOIN campuscore_auth.\"Role\" r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = campuscore_auth.\"User\".\"id\" AND r.\"name\" IN (:roles))",
                new MapSqlParameterSource()
                        .addValue("active", status)
                        .addValue("emails", emails)
                        .addValue("roles", roles));
    }

    public int lockUnscoped(List<String> emails, String status, Set<String> roles) {
        return jdbc.update(
                "UPDATE campuscore_auth.\"User\" SET \"status\" = :locked, \"failedLoginAttempts\" = 0,"
                        + " \"lockedUntil\" = NULL, \"updatedAt\" = CURRENT_TIMESTAMP"
                        + " WHERE \"email\" IN (:emails) AND \"status\" <> :locked"
                        + " AND NOT EXISTS (SELECT 1 FROM campuscore_auth.\"UserRole\" ur"
                        + " JOIN campuscore_auth.\"Role\" r ON r.\"id\" = ur.\"roleId\""
                        + " WHERE ur.\"userId\" = campuscore_auth.\"User\".\"id\" AND r.\"name\" IN (:roles))",
                new MapSqlParameterSource()
                        .addValue("locked", status)
                        .addValue("emails", emails)
                        .addValue("roles", roles));
    }
}
