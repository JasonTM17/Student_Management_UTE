package io.campuscore.restfulapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class CampusCoreFlywaySafetyConfigurationTest {

    @Test
    void hostedBaselineMustBeTheOnlyExactLocation() {
        Flyway hosted = Flyway.configure()
                .locations("classpath:db/supabase-baseline")
                .load();
        Flyway combined = Flyway.configure()
                .locations("classpath:db/migration", "classpath:db/supabase-baseline")
                .load();
        Flyway local = Flyway.configure()
                .locations("classpath:db/migration")
                .load();
        Flyway lookalike = Flyway.configure()
                .locations("filesystem:/opt/db/supabase-baseline-copy")
                .load();

        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(hosted)).isTrue();
        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(combined)).isFalse();
        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(local)).isFalse();
        assertThat(CampusCoreFlywaySafetyConfiguration.usesHostedBaseline(lookalike)).isFalse();
    }
}
