package io.campuscore.restfulapi.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Prevents an operator mistake from replaying CampusCore's legacy auth
 * migrations against Supabase-managed schemas. Hosted databases must opt into
 * the schema-only {@code db/supabase-baseline} location explicitly.
 */
@Configuration
@Profile("persistence")
public class CampusCoreFlywaySafetyConfiguration {

    private static final String REVIEWED_HOSTED_BASELINE = "classpath:db/supabase-baseline";
    private static final int REVIEWED_B20_CHECKSUM = 1_841_726_166;
    private static final int REVIEWED_V21_CHECKSUM = -249_127_582;
    private static final String[] APPLICATION_SCHEMAS = {
            "academic", "assistant", "campuscore_auth", "engagement", "notifications", "thesis"
    };

    @Bean
    FlywayMigrationStrategy campusCoreFlywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            if (usesHostedBaseline(flyway)) {
                HostedHistoryState history = inspectHostedHistory(dataSource);
                if (history == HostedHistoryState.INCOMPATIBLE
                        || (history == HostedHistoryState.ABSENT
                                && hasUnexpectedApplicationState(dataSource, false))
                        || (history == HostedHistoryState.INITIALIZED
                                && hasUnexpectedApplicationState(dataSource, true))) {
                    throw new IllegalStateException(
                            "Refusing the new-target-only CampusCore Supabase baseline on a database with "
                                    + "incompatible Flyway history or pre-existing application state");
                }
            }
            if (!usesHostedBaseline(flyway) && hasSupabaseManagedSchemas(dataSource)) {
                throw new IllegalStateException(
                        "Refusing legacy CampusCore Flyway migrations against Supabase-managed schemas; "
                                + "set FLYWAY_LOCATIONS=classpath:db/supabase-baseline for the reviewed schema-only baseline");
            }
            flyway.migrate();
        };
    }

    static boolean usesHostedBaseline(Flyway flyway) {
        Location[] locations = flyway.getConfiguration().getLocations();
        // The baseline is an explicit, allowlisted location.  A combined
        // legacy+baseline configuration is deliberately rejected: it makes a
        // future migration addition easy to execute against a managed target.
        return locations.length == 1
                && Arrays.stream(locations)
                        .map(Location::getDescriptor)
                        .anyMatch(REVIEWED_HOSTED_BASELINE::equals);
    }

    static boolean hasSupabaseManagedSchemas(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT)
                    .contains("postgres")) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    WITH platform_schema_marker AS (
                        SELECT EXISTS (
                            SELECT 1
                              FROM pg_namespace
                             WHERE nspname IN ('storage', 'realtime', 'supabase_migrations')
                                OR nspname LIKE 'supabase\\_%' ESCAPE '\\'
                        ) AS present
                    ), platform_role_marker AS (
                        SELECT EXISTS (
                            SELECT 1
                              FROM pg_roles
                             WHERE rolname LIKE 'supabase\\_%' ESCAPE '\\'
                        ) AS present
                    ), auth_schema AS (
                        SELECT n.oid, pg_get_userbyid(n.nspowner) AS owner
                          FROM pg_namespace n
                         WHERE n.nspname = 'auth'
                    ), private_schema AS (
                        SELECT n.oid, pg_get_userbyid(n.nspowner) AS owner
                          FROM pg_namespace n
                         WHERE n.nspname = 'campuscore_auth'
                    ), auth_relations AS (
                        SELECT c.relname
                          FROM pg_class c
                          JOIN pg_namespace n ON n.oid = c.relnamespace
                         WHERE n.nspname = 'auth'
                           AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                    ), auth_relation_state AS (
                        SELECT EXISTS (SELECT 1 FROM auth_relations) AS has_any,
                               EXISTS (
                                   SELECT 1
                                     FROM auth_relations
                                    WHERE relname NOT IN (
                                        'User', 'Role', 'Permission', 'UserRole',
                                        'RolePermission', 'Student', 'Lecturer',
                                        'Session', 'AuthChallenge', 'AuthRateLimitBucket'
                                    )
                               ) AS has_unknown
                    ), auth_function_state AS (
                        SELECT EXISTS (
                            SELECT 1
                              FROM pg_proc p
                              JOIN pg_namespace n ON n.oid = p.pronamespace
                             WHERE n.nspname = 'auth'
                        ) AS has_any
                    ), private_signature AS (
                        SELECT p.oid IS NOT NULL
                               AND p.owner = current_user
                               AND NOT EXISTS (
                                   SELECT 1
                                     FROM pg_class c
                                     JOIN pg_namespace n ON n.oid = c.relnamespace
                                    WHERE n.nspname = 'campuscore_auth'
                                      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                                      AND c.relname NOT IN (
                                          'User', 'Role', 'Permission', 'UserRole',
                                          'RolePermission', 'Student', 'Lecturer',
                                          'Session', 'AuthChallenge', 'AuthRateLimitBucket'
                                      )
                               )
                               AND NOT EXISTS (
                                   SELECT 1
                                     FROM pg_proc f
                                     JOIN pg_namespace n ON n.oid = f.pronamespace
                                    WHERE n.nspname = 'campuscore_auth'
                               )
                               AND EXISTS (
                                   SELECT 1
                                     FROM pg_class c
                                     JOIN pg_namespace n ON n.oid = c.relnamespace
                                    WHERE n.nspname = 'campuscore_auth'
                                      AND c.relname = 'User'
                                      AND c.relkind IN ('r', 'p')
                               )
                               AND EXISTS (
                                   SELECT 1
                                     FROM pg_class c
                                     JOIN pg_namespace n ON n.oid = c.relnamespace
                                    WHERE n.nspname = 'campuscore_auth'
                                      AND c.relname = 'Session'
                                      AND c.relkind IN ('r', 'p')
                               ) AS trusted
                          FROM (SELECT 1) marker
                          LEFT JOIN private_schema p ON TRUE
                    )
                    SELECT ps.present
                           OR pr.present
                           OR (
                               a.oid IS NOT NULL
                               AND (
                                   a.owner IS DISTINCT FROM current_user
                                   OR ars.has_unknown
                                   OR afs.has_any
                                   OR (
                                       NOT ars.has_any
                                       AND NOT psg.trusted
                                   )
                               )
                           ) AS blocked
                      FROM platform_schema_marker ps
                      CROSS JOIN platform_role_marker pr
                      CROSS JOIN auth_relation_state ars
                      CROSS JOIN auth_function_state afs
                      CROSS JOIN private_signature psg
                      LEFT JOIN auth_schema a ON TRUE
                    """)) {
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean(1);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify managed-schema safety before Flyway", failure);
        }
    }

    static boolean hasIncompatibleFlywayHistory(DataSource dataSource) {
        return inspectHostedHistory(dataSource) == HostedHistoryState.INCOMPATIBLE;
    }

    static boolean hasUnexpectedApplicationState(DataSource dataSource, boolean allowFlywayHistory) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT)
                    .contains("postgres")) {
                return false;
            }
            String schemaList = String.join(",", Arrays.stream(APPLICATION_SCHEMAS)
                    .map(schema -> "'" + schema + "'")
                    .toList());
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT EXISTS (
                        SELECT 1
                          FROM pg_namespace n
                         WHERE n.nspname IN (%s)
                           AND (
                               ? = FALSE
                               OR n.nspname <> 'thesis'
                               OR EXISTS (
                                   SELECT 1
                                     FROM pg_class c
                                   WHERE c.relnamespace = n.oid
                                      AND c.relkind IN ('r', 'p', 'v', 'm', 'S', 'f')
                                      AND NOT (c.relname = 'flyway_schema_history'
                                               AND c.relkind IN ('r', 'p'))
                               )
                               OR EXISTS (
                                   SELECT 1 FROM pg_proc p WHERE p.pronamespace = n.oid
                               )
                               OR EXISTS (
                                   SELECT 1
                                     FROM pg_type t
                                    WHERE t.typnamespace = n.oid
                                      AND t.typtype IN ('d', 'e', 'm', 'r')
                               )
                           )
                    )
                    """.formatted(schemaList))) {
                statement.setBoolean(1, allowFlywayHistory);
                try (ResultSet result = statement.executeQuery()) {
                    return !result.next() || result.getBoolean(1);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify application-schema safety before hosted baseline", failure);
        }
    }

    static HostedHistoryState inspectHostedHistory(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT)
                    .contains("postgres")) {
                return HostedHistoryState.ABSENT;
            }
            try (PreparedStatement schemas = connection.prepareStatement("""
                    SELECT n.nspname
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE c.relname = 'flyway_schema_history'
                       AND c.relkind IN ('r', 'p')
                    """)) {
                try (ResultSet result = schemas.executeQuery()) {
                    HostedHistoryState state = HostedHistoryState.ABSENT;
                    while (result.next()) {
                        if (state != HostedHistoryState.ABSENT) {
                            return HostedHistoryState.INCOMPATIBLE;
                        }
                        String rawSchema = result.getString(1);
                        if (!"thesis".equals(rawSchema)) {
                            return HostedHistoryState.INCOMPATIBLE;
                        }
                        String schema = rawSchema.replace("\"", "\"\"");
                        try (PreparedStatement history = connection.prepareStatement(
                                "SELECT COUNT(*), "
                                        + "COUNT(*) FILTER (WHERE installed_rank = 0 "
                                        + "AND version IS NULL "
                                        + "AND description = '<< Flyway Schema Creation >>' "
                                        + "AND type = 'SCHEMA' "
                                        + "AND script = '\"thesis\"' "
                                        + "AND checksum IS NULL "
                                        + "AND success), "
                                        + "COUNT(*) FILTER (WHERE installed_rank = 1 "
                                        + "AND version = '20' "
                                        + "AND description = 'campuscore supabase baseline' "
                                        + "AND type = 'SQL_BASELINE' "
                                        + "AND script = 'B20__campuscore_supabase_baseline.sql' "
                                        + "AND checksum = " + REVIEWED_B20_CHECKSUM + " "
                                        + "AND success), "
                                        + "COUNT(*) FILTER (WHERE installed_rank = 2 "
                                        + "AND version = '21' "
                                        + "AND description = 'persist enrollment registration round' "
                                        + "AND type = 'SQL' "
                                        + "AND script = 'V21__persist_enrollment_registration_round.sql' "
                                        + "AND checksum = " + REVIEWED_V21_CHECKSUM + " "
                                        + "AND success) "
                                        + "FROM \"" + schema + "\".\"flyway_schema_history\"")) {
                            try (ResultSet counts = history.executeQuery()) {
                                if (counts.next()) {
                                    long total = counts.getLong(1);
                                    long schemaCreation = counts.getLong(2);
                                    long reviewedBaseline = counts.getLong(3);
                                    long reviewedV21 = counts.getLong(4);
                                    if (total == 1 && schemaCreation == 1 && reviewedBaseline == 0 && reviewedV21 == 0) {
                                        state = HostedHistoryState.INITIALIZED;
                                    } else if (total == 2 && schemaCreation == 1 && reviewedBaseline == 1 && reviewedV21 == 0) {
                                        state = HostedHistoryState.BASELINED;
                                    } else if (total == 3 && schemaCreation == 1 && reviewedBaseline == 1 && reviewedV21 == 1) {
                                        state = HostedHistoryState.SUCCEEDED;
                                    } else {
                                        return HostedHistoryState.INCOMPATIBLE;
                                    }
                                } else {
                                    return HostedHistoryState.INCOMPATIBLE;
                                }
                            }
                        }
                    }
                    return state;
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify Flyway history compatibility before baseline selection", failure);
        }
    }

    enum HostedHistoryState {
        ABSENT,
        INITIALIZED,
        BASELINED,
        SUCCEEDED,
        INCOMPATIBLE
    }
}
