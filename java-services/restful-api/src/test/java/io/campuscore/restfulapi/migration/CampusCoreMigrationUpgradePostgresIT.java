package io.campuscore.restfulapi.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Forward-only PostgreSQL authority for the supported V12 and V18 upgrade paths. */
class CampusCoreMigrationUpgradePostgresIT {

    @Test
    @EnabledIfEnvironmentVariable(named = "CAMPUSCORE_UPGRADE_V12_POSTGRES_URL", matches = "jdbc:postgresql:.+")
    void recordedV12UpgradesToV21AndPreservesAssistantData() throws Exception {
        String url = System.getenv("CAMPUSCORE_UPGRADE_V12_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_UPGRADE_POSTGRES_USER", "postgres");
        String password = valueOr("CAMPUSCORE_UPGRADE_POSTGRES_PASSWORD", "postgres");
        Flyway toV12 = configured(url, user, password, "12");
        toV12.migrate();

        UUID conversationId = UUID.randomUUID();
        Integer v12Checksum;
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("12", currentVersion(connection));
            v12Checksum = checksum(connection, "12");
            assertNotNull(v12Checksum);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO assistant.chat_conversation"
                            + " (id, owner_id, title, locale, expires_at) VALUES (?, ?, ?, ?, ?)")) {
                insert.setObject(1, conversationId);
                insert.setString(2, "upgrade-v12-owner");
                insert.setString(3, "V12 preserved conversation");
                insert.setString(4, "vi");
                insert.setTimestamp(5, Timestamp.from(Instant.now().plus(90, ChronoUnit.DAYS)));
                insert.executeUpdate();
            }
        }

        Flyway latest = configured(url, user, password, null);
        latest.migrate();
        assertTrue(latest.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("21", currentVersion(connection));
            assertEquals(v12Checksum, checksum(connection, "12"));
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*)::text FROM assistant.chat_conversation WHERE id='" + conversationId + "'"));
            assertAuthLifecycleTables(connection);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "CAMPUSCORE_UPGRADE_V18_POSTGRES_URL", matches = "jdbc:postgresql:.+")
    void recordedV18UpgradesToV21AndBackfillsTrustedActiveAccounts() throws Exception {
        String url = System.getenv("CAMPUSCORE_UPGRADE_V18_POSTGRES_URL");
        String user = valueOr("CAMPUSCORE_UPGRADE_POSTGRES_USER", "postgres");
        String password = valueOr("CAMPUSCORE_UPGRADE_POSTGRES_PASSWORD", "postgres");
        Flyway toV18 = configured(url, user, password, "18");
        toV18.migrate();

        Integer v18Checksum;
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("18", currentVersion(connection));
            v18Checksum = checksum(connection, "18");
            assertNotNull(v18Checksum);
            assertEquals("false", scalar(connection,
                    "SELECT \"emailVerified\"::text FROM auth.\"User\" WHERE \"id\"='student-user'"));
        }

        Flyway latest = configured(url, user, password, null);
        latest.migrate();
        assertTrue(latest.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("21", currentVersion(connection));
            assertEquals(v18Checksum, checksum(connection, "18"));
            assertEquals("true", scalar(connection,
                    "SELECT \"emailVerified\"::text FROM campuscore_auth.\"User\" WHERE \"id\"='student-user'"));
            assertAuthLifecycleTables(connection);
        }
    }

    private static void assertAuthLifecycleTables(Connection connection) throws Exception {
        assertEquals("2", scalar(connection,
                "SELECT COUNT(*)::text FROM information_schema.tables"
                        + " WHERE table_schema='campuscore_auth' AND table_name IN ('AuthChallenge','AuthRateLimitBucket')"));
        assertEquals("0", scalar(connection,
                "SELECT COUNT(*)::text FROM information_schema.columns"
                        + " WHERE table_schema='campuscore_auth' AND table_name='AuthChallenge'"
                        + " AND lower(column_name) IN ('token','rawtoken','raw_token')"));
        assertEquals("0", scalar(connection,
                "SELECT COUNT(*)::text FROM information_schema.tables"
                        + " WHERE table_schema='auth'"
                        + " AND table_name IN ('User','Role','Permission','UserRole','RolePermission',"
                        + "'Session','AuthChallenge','AuthRateLimitBucket')"));
    }

    private static Flyway configured(String url, String user, String password, String target) {
        var configuration = Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .cleanDisabled(true);
        if (target != null) {
            configuration.target(MigrationVersion.fromVersion(target));
        }
        return configuration.load();
    }

    private static String currentVersion(Connection connection) throws Exception {
        return scalar(connection,
                "SELECT version FROM thesis.flyway_schema_history"
                        + " WHERE success ORDER BY installed_rank DESC LIMIT 1");
    }

    private static Integer checksum(Connection connection, String version) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT checksum FROM thesis.flyway_schema_history WHERE version=? AND success")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : null;
            }
        }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static String valueOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
