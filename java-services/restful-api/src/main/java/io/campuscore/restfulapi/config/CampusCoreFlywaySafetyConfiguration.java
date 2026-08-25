package io.campuscore.restfulapi.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
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

    @Bean
    FlywayMigrationStrategy campusCoreFlywayMigrationStrategy(DataSource dataSource) {
        return flyway -> {
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
        // The reviewed hosted command may expose both the legacy tree (so
        // Flyway can report it as ignored below the baseline) and the selected
        // schema-only baseline.  Presence of the baseline is the safety
        // switch; a configuration with no baseline is always rejected when
        // Supabase-managed schemas are detected.
        return locations.length > 0
                && Arrays.stream(locations)
                        .map(Location::getDescriptor)
                        .anyMatch(descriptor -> descriptor.contains("db/supabase-baseline"));
    }

    static boolean hasSupabaseManagedSchemas(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT)
                    .contains("postgres")) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT EXISTS (
                             SELECT 1
                               FROM pg_namespace n
                               JOIN pg_roles r ON r.oid = n.nspowner
                              WHERE n.nspname = 'auth' AND r.rolname = 'supabase_admin'
                           )
                       AND EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'storage')
                       AND EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'realtime')
                    """)) {
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() && result.getBoolean(1);
                }
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to verify managed-schema safety before Flyway", failure);
        }
    }
}
