package io.campuscore.restfulapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * PostgreSQL safety matrix for the managed-schema guard and callback path.
 * The URL must point to a disposable database; this class drops only its
 * CampusCore/test schemas and never targets a hosted project.
 */
@EnabledIfEnvironmentVariable(
        named = "CAMPUSCORE_FLYWAY_SAFETY_POSTGRES_URL",
        matches = "jdbc:postgresql:.+")
class CampusCoreFlywaySafetyPostgresIT {

    private static final List<String> SCHEMAS = List.of(
            "auth", "storage", "realtime", "supabase_migrations", "campuscore_auth",
            "academic", "assistant", "engagement", "notifications", "shadow_history", "thesis");

    @BeforeEach
    void prepareDisposableDatabase() throws Exception {
        resetSchemas();
    }

    @AfterEach
    void cleanDisposableSchemas() throws Exception {
        resetSchemas();
    }

    @Test
    void managedMarkersFailClosedEvenWhenStorageOrRealtimeIsMissing() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA auth");
            assertThat(CampusCoreFlywaySafetyConfiguration.hasSupabaseManagedSchemas(dataSource()))
                    .as("an auth-only/empty marker is unknown and must be blocked")
                    .isTrue();

            statement.execute("CREATE TABLE auth.users (id INTEGER PRIMARY KEY)");
            assertThat(CampusCoreFlywaySafetyConfiguration.hasSupabaseManagedSchemas(dataSource()))
                    .as("a managed auth relation must be blocked without storage/realtime")
                    .isTrue();

            statement.execute("DROP SCHEMA auth CASCADE");
            statement.execute("CREATE SCHEMA auth AUTHORIZATION pg_monitor");
            assertThat(CampusCoreFlywaySafetyConfiguration.hasSupabaseManagedSchemas(dataSource()))
                    .as("an alternate auth owner must be blocked")
                    .isTrue();

            statement.execute("DROP SCHEMA auth CASCADE");
            statement.execute("CREATE SCHEMA auth");
            statement.execute("CREATE TABLE auth.\"User\" (id VARCHAR(120) PRIMARY KEY)");
            statement.execute("CREATE TABLE auth.\"Session\" (id VARCHAR(120) PRIMARY KEY)");
            assertThat(CampusCoreFlywaySafetyConfiguration.hasSupabaseManagedSchemas(dataSource()))
                    .as("the reviewed legacy local signature is allowed")
                    .isFalse();

            statement.execute("DROP SCHEMA auth CASCADE");
            statement.execute("CREATE SCHEMA auth");
            statement.execute("CREATE SCHEMA campuscore_auth");
            statement.execute("CREATE TABLE campuscore_auth.\"User\" (id VARCHAR(120) PRIMARY KEY)");
            statement.execute("CREATE TABLE campuscore_auth.\"Session\" (id VARCHAR(120) PRIMARY KEY)");
            assertThat(CampusCoreFlywaySafetyConfiguration.hasSupabaseManagedSchemas(dataSource()))
                    .as("the reviewed post-V20 private signature is allowed")
                    .isFalse();
        }
    }

    @Test
    void callbacksBlockDirectStyleFlywayAndAllowExistingLocalHistory() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA auth");
            statement.execute("CREATE TABLE auth.users (id INTEGER PRIMARY KEY)");
        }

        assertThatThrownBy(() -> configuredFlyway().migrate())
                .hasMessageContaining("Legacy CampusCore migrations are blocked");

        resetSchemas();
        Flyway local = configuredFlyway();
        local.migrate();
        assertThat(local.info().current().getVersion().getVersion()).isEqualTo("21");
        assertThat(CampusCoreFlywaySafetyConfiguration.hasIncompatibleFlywayHistory(dataSource())).isTrue();
        assertThatThrownBy(() -> configuredBaseline().migrate())
                .hasMessageContaining("exact Flyway schema marker and B20 history");

        // A marker introduced after an existing V20 history is still blocked
        // before validation; no lower-version V0 migration is required.
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS auth CASCADE");
            statement.execute("CREATE SCHEMA auth");
            statement.execute("CREATE TABLE auth.users (id INTEGER PRIMARY KEY)");
        }
        assertThatThrownBy(() -> configuredFlyway().validate())
                .hasMessageContaining("Legacy CampusCore migrations are blocked");
        assertThatThrownBy(() -> configuredFlyway().migrate())
                .hasMessageContaining("Legacy CampusCore migrations are blocked");

        resetSchemas();
        Flyway existing = configuredFlyway();
        existing.migrate();
        assertThat(existing.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void versionlessRepeatableHistoryIsRejectedBeforeBaselineEvenWhenValidationIsDisabled() throws Exception {
        configuredRepeatable().migrate();

        assertThat(CampusCoreFlywaySafetyConfiguration.inspectHostedHistory(dataSource()))
                .isEqualTo(CampusCoreFlywaySafetyConfiguration.HostedHistoryState.INCOMPATIBLE);
        assertThat(CampusCoreFlywaySafetyConfiguration.hasIncompatibleFlywayHistory(dataSource())).isTrue();
        assertThatThrownBy(() -> configuredBaseline(false).migrate())
                .hasMessageContaining("exact Flyway schema marker and B20 history");
        assertThat(schemaExists("academic")).isFalse();
        assertThat(schemaExists("assistant")).isFalse();
    }

    @Test
    void checksumMismatchAndPreExistingApplicationObjectsAreRejectedFailClosed() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA academic");
            statement.execute("CREATE TABLE academic.preexisting_marker (id INTEGER PRIMARY KEY)");
        }
        assertThatThrownBy(() -> configuredBaseline(false).migrate())
                .hasMessageContaining("pre-existing application objects");

        resetSchemas();
        configuredBaseline().migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("UPDATE thesis.flyway_schema_history SET checksum = 7 WHERE version = '20'");
        }
        assertThat(CampusCoreFlywaySafetyConfiguration.hasIncompatibleFlywayHistory(dataSource())).isTrue();
        assertThatThrownBy(() -> configuredBaseline(false).migrate())
                .hasMessageContaining("exact Flyway schema marker and B20 history");
    }

    @Test
    void duplicateFlywayHistoryTablesAreRejectedBeforeBaselineValidation() throws Exception {
        configuredBaseline(false).migrate();
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA shadow_history");
            statement.execute("CREATE TABLE shadow_history.flyway_schema_history (LIKE thesis.flyway_schema_history INCLUDING ALL)");
        }

        assertThat(CampusCoreFlywaySafetyConfiguration.inspectHostedHistory(dataSource()))
                .isEqualTo(CampusCoreFlywaySafetyConfiguration.HostedHistoryState.INCOMPATIBLE);
        assertThatThrownBy(() -> configuredBaseline(false).migrate())
                .hasMessageContaining("exactly one Flyway history table");
    }

    private static Flyway configuredFlyway() {
        return Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .cleanDisabled(true)
                .load();
    }

    private static Flyway configuredBaseline() {
        return configuredBaseline(true);
    }

    private static Flyway configuredBaseline(boolean validateOnMigrate) {
        return Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/supabase-baseline")
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .cleanDisabled(true)
                .validateOnMigrate(validateOnMigrate)
                .load();
    }

    private static Flyway configuredRepeatable() {
        return Flyway.configure()
                .dataSource(dataSource())
                .locations("classpath:db/flyway-safety-repeatable")
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .cleanDisabled(true)
                .load();
    }

    private static boolean schemaExists(String schema) throws Exception {
        try (Connection connection = connection(); var statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = ?)")) {
            statement.setString(1, schema);
            try (var result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }

    private static DriverManagerDataSource dataSource() {
        String url = required("CAMPUSCORE_FLYWAY_SAFETY_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_FLYWAY_SAFETY_POSTGRES_USER", "postgres");
        String password = valueOr("CAMPUSCORE_FLYWAY_SAFETY_POSTGRES_PASSWORD", "postgres");
        return new DriverManagerDataSource(url, user, password);
    }

    private static Connection connection() throws Exception {
        return dataSource().getConnection();
    }

    private static void resetSchemas() throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            for (String schema : SCHEMAS) {
                statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
            }
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the disposable PostgreSQL safety test");
        }
        return value;
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
