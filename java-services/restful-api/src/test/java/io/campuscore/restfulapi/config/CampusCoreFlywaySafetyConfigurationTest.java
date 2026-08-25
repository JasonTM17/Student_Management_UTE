package io.campuscore.restfulapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CampusCoreFlywaySafetyConfigurationTest {

    @Test
    void hostedBaselineMustBePresentWhenLocationsAreConfigured() {
        Flyway hosted = Flyway.configure()
                .locations("classpath:db/migration", "classpath:db/supabase-baseline")
                .load();
        Flyway local = Flyway.configure()
                .locations("classpath:db/migration")
                .load();

        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(hosted)).isTrue();
        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(local)).isFalse();
    }
}
