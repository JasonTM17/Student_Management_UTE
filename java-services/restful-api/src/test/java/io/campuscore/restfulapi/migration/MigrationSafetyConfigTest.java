package io.campuscore.restfulapi.migration;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MigrationSafetyConfigTest {

    @Test
    void engagementReadStrategyRefusesFlywayMigration() {
        MigrationSafetyConfig config = new MigrationSafetyConfig();

        assertThrows(
                IllegalStateException.class,
                () -> config.rejectFlywayForEngagementRead().migrate(null));
    }
}
