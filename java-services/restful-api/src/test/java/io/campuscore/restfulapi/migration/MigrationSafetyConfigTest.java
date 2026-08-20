package io.campuscore.restfulapi.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

class MigrationSafetyConfigTest {

    @Test
    void readOnlyCandidateStrategyRefusesFlywayMigration() {
        MigrationSafetyConfig config = new MigrationSafetyConfig();

        assertThrows(
                IllegalStateException.class,
                () -> config.rejectFlywayForReadOnlyCandidates().migrate(null));
    }

    @Test
    void readOnlyCandidateDisablesHibernateSchemaManagement() {
        MigrationSafetyConfig config = new MigrationSafetyConfig();
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");

        config.disableHibernateSchemaManagementForReadOnlyCandidates().customize(properties);

        assertEquals("none", properties.get("hibernate.hbm2ddl.auto"));
    }

    @Test
    void supportTicketWriteCandidateAlsoActivatesMigrationSafetyCondition() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("migration.engagement-write.enabled", "true");
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);

        assertTrue(new ReadOnlyMigrationCandidateCondition().matches(context, null));
    }
}
