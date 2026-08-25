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

    @Bean
    FlywayMigrationStrategy campusCoreFlywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
            if (usesHostedBaseline(flyway) && hasIncompatibleFlywayHistory(dataSource)) {
                throw new IllegalStateException(
                        "Refusing the new-target-only CampusCore Supabase baseline on a database with existing Flyway history");
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
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT)
                    .contains("postgres")) {
                return false;
            }
            try (PreparedStatement schemas = connection.prepareStatement("""
                    SELECT n.nspname
                      FROM pg_class c
                      JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE c.relname = 'flyway_schema_history'
                       AND c.relkind IN ('r', 'p')
                    """)) {
                try (ResultSet result = schemas.executeQuery()) {
                    while (result.next()) {
                        String schema = result.getString(1).replace("\"", "\"\"");
                        try (PreparedStatement history = connection.prepareStatement(
                                "SELECT COUNT(*) FILTER (WHERE version IS NOT NULL), "
                                        + "COUNT(*) FILTER (WHERE version = '20' "
                                        + "AND type = 'SQL_BASELINE' "
                                        + "AND script = 'B20__campuscore_supabase_baseline.sql' "
                                        + "AND success) "
                                        + "FROM \"" + schema + "\".\"flyway_schema_history\"")) {
                            try (ResultSet counts = history.executeQuery()) {
                                if (counts.next()) {
                                    long versioned = counts.getLong(1);
                                    long reviewedBaseline = counts.getLong(2);
                                    if (versioned > 0 && !(versioned == 1 && reviewedBaseline == 1)) {
                                        return true;
                                    }
                                } else {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify Flyway history compatibility before baseline selection", failure);
        }
    }
}
