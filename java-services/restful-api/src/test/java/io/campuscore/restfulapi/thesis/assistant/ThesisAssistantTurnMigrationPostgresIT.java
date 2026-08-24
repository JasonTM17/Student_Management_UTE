package io.campuscore.restfulapi.thesis.assistant;

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

/**
 * Dedicated PostgreSQL migration authority. The caller must supply a separate,
 * disposable database; this test never cleans or selects a developer database.
 */
@EnabledIfEnvironmentVariable(named = "ASSISTANT_MIGRATION_POSTGRES_URL", matches = "jdbc:postgresql:.+")
class ThesisAssistantTurnMigrationPostgresIT {

    @Test
    void recordedV11UpgradesForwardToV12WithoutRewritingHistory() throws Exception {
        String url = System.getenv("ASSISTANT_MIGRATION_POSTGRES_URL");
        String user = valueOr("ASSISTANT_MIGRATION_POSTGRES_USER", "postgres");
        String password = valueOr("ASSISTANT_MIGRATION_POSTGRES_PASSWORD", "postgres");

        Flyway toV11 = configured(url, user, password, "11");
        toV11.migrate();

        UUID conversation = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        UUID citation = UUID.randomUUID();
        Integer v11Checksum;
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("11", currentVersion(connection));
            v11Checksum = checksum(connection, "11");
            assertNotNull(v11Checksum);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.chat_conversation(id,owner_id,title,locale,expires_at) VALUES (?,?,?,?,?)")) {
                statement.setObject(1, conversation);
                statement.setString(2, "legacy-owner");
                statement.setString(3, "Legacy conversation");
                statement.setString(4, "en");
                statement.setTimestamp(5, Timestamp.from(Instant.now().plus(90, ChronoUnit.DAYS)));
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.chat_message(id,conversation_id,role,content,model,degraded,reason_code) VALUES (?,?,'ASSISTANT','legacy answer','legacy-model',FALSE,'ANSWERED')")) {
                statement.setObject(1, message);
                statement.setObject(2, conversation);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO assistant.chat_citation(id,message_id,document_id,slug,title,source,locale,excerpt) VALUES (?,?,?,?,?,?,?,?)")) {
                statement.setObject(1, citation);
                statement.setObject(2, message);
                statement.setObject(3, UUID.randomUUID());
                statement.setString(4, "legacy");
                statement.setString(5, "Legacy source");
                statement.setString(6, "legacy-office");
                statement.setString(7, "en");
                statement.setString(8, "legacy excerpt");
                statement.executeUpdate();
            }
        }

        Flyway toV12 = configured(url, user, password, "12");
        toV12.migrate();
        assertTrue(toV12.validateWithResult().validationSuccessful);

        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            assertEquals("12", currentVersion(connection));
            assertEquals(v11Checksum, checksum(connection, "11"));
            assertEquals("LEGACY_COMPLETED", scalar(connection,
                    "SELECT reason_code FROM assistant.chat_message WHERE id='" + message + "'"));
            assertEquals("LEGACY_SNAPSHOT", scalar(connection,
                    "SELECT source_kind FROM assistant.chat_citation WHERE id='" + citation + "'"));
            assertEquals("1", scalar(connection,
                    "SELECT COUNT(*)::text FROM information_schema.tables WHERE table_schema='assistant' AND table_name='chat_turn_ledger'"));
        }
    }

    private static Flyway configured(String url, String user, String password, String target) {
        return Flyway.configure()
                .dataSource(url, user, password)
                .locations("classpath:db/migration")
                .createSchemas(true)
                .defaultSchema("thesis")
                .schemas("thesis")
                .target(MigrationVersion.fromVersion(target))
                .cleanDisabled(true)
                .load();
    }

    private static String currentVersion(Connection connection) throws Exception {
        return scalar(connection,
                "SELECT version FROM thesis.flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1");
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
