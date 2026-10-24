package io.campuscore.restfulapi.thesis.assistant.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zaxxer.hikari.HikariConfig;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Regression for the production boot outage: a syntactically valid assistant
 * datasource whose dedicated runtime login cannot connect (unprovisioned
 * tenant/role, unreachable host) used to let Hikari's initialization failure
 * kill the whole application. The Wave 1.2 contract is that only the assistant
 * degrades — the bean must return a fail-closed
 * {@link UnavailableAssistantDataSource} so the RLS verifier marks the state
 * DEGRADED and chat endpoints answer 503 while the rest of the API keeps
 * serving.
 */
class AssistantDatabaseConfigurationTest {

    private final AssistantDatabaseConfiguration configuration = new AssistantDatabaseConfiguration();

    @Test
    void anUnreachableRuntimeLoginDegradesToAFailClosedDataSource() {
        DataSource dataSource = configuration.assistantDataSource(
                "jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime.testref",
                "x",
                1,
                1000,
                false);

        assertInstanceOf(UnavailableAssistantDataSource.class, dataSource);
    }

    @Test
    void unprovisionedConfigurationDegradesInsteadOfThrowing() {
        assertDegraded("", "campuscore_assistant_runtime", "x", 4, 15_000L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres", "postgres", "x", 4, 15_000L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime", "", 4, 15_000L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime", "x", 0, 15_000L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime", "x", 5, 15_000L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime", "x", 4, 0L);
        assertDegraded("jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime", "x", 4, 60_001L);
    }

    private void assertDegraded(String url, String username, String password,
            int maximumPoolSize, long connectionTimeoutMs) {
        assertInstanceOf(UnavailableAssistantDataSource.class,
                configuration.assistantDataSource(
                        url, username, password, maximumPoolSize, connectionTimeoutMs, 60_000L, 0L, false));
    }

    @Test
    void invalidPoolTimingsFailFastEvenWhenProvisioningWouldDegrade() {
        assertThrows(IllegalArgumentException.class, () -> configuration.assistantDataSource(
                "", "", "", 0, 0L, 9_999L, 0L, false));
        assertThrows(IllegalArgumentException.class, () -> configuration.assistantDataSource(
                "", "", "", 0, 0L, 60_000L, 29_999L, false));
    }

    @Test
    void credentialsEmbeddedInUrlRemainAHardFailure() {
        assertThrows(IllegalStateException.class, () -> configuration.assistantDataSource(
                "jdbc:postgresql://127.0.0.1:1/postgres?password=x",
                "campuscore_assistant_runtime.testref",
                "x",
                1,
                1000,
                false));
    }

    @Test
    void builtPoolConfigCarriesTheAutosuspendFriendlyDefaults() {
        HikariConfig config = AssistantDatabaseConfiguration.newAssistantPoolConfig(
                "jdbc:postgresql://127.0.0.1:1/postgres",
                "campuscore_assistant_runtime",
                "x",
                4,
                15_000L,
                60_000L,
                0L);

        assertEquals(4, config.getMaximumPoolSize());
        assertEquals(0, config.getMinimumIdle());
        assertEquals(60_000L, config.getIdleTimeout());
        assertEquals(0L, config.getKeepaliveTime());
        assertEquals(15_000L, config.getConnectionTimeout());
    }

    @Test
    void invalidPoolTimingsFailFastBeforeAnyConnectionAttempt() {
        assertThrows(IllegalArgumentException.class, () -> AssistantDatabaseConfiguration.newAssistantPoolConfig(
                "jdbc:postgresql://127.0.0.1:1/postgres", "campuscore_assistant_runtime", "x",
                4, 15_000L, 9_999L, 0L));
        assertThrows(IllegalArgumentException.class, () -> AssistantDatabaseConfiguration.newAssistantPoolConfig(
                "jdbc:postgresql://127.0.0.1:1/postgres", "campuscore_assistant_runtime", "x",
                4, 15_000L, 60_000L, 29_999L));

        AssistantDatabaseConfiguration.newAssistantPoolConfig(
                "jdbc:postgresql://127.0.0.1:1/postgres", "campuscore_assistant_runtime", "x",
                4, 15_000L, 60_000L, 30_000L);
        AssistantDatabaseConfiguration.newAssistantPoolConfig(
                "jdbc:postgresql://127.0.0.1:1/postgres", "campuscore_assistant_runtime", "x",
                4, 15_000L, 10_000L, 0L);
    }
}
