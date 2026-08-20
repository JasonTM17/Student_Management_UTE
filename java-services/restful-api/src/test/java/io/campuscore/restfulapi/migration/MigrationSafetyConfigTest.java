package io.campuscore.restfulapi.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MigrationSafetyConfigTest {

    @Test
    void engagementReadStrategyRefusesFlywayMigration() {
        MigrationSafetyConfig config = new MigrationSafetyConfig();

        assertThrows(
                IllegalStateException.class,
                () -> config.rejectFlywayForEngagementRead().migrate(null));
    }

    @Test
    void engagementReadDisablesHibernateSchemaManagement() {
        MigrationSafetyConfig config = new MigrationSafetyConfig();
        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "validate");

        config.disableHibernateSchemaManagementForEngagementRead().customize(properties);

        assertEquals("none", properties.get("hibernate.hbm2ddl.auto"));
    }
}
