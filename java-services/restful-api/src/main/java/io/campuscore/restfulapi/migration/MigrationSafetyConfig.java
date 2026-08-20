package io.campuscore.restfulapi.migration;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Fail-closed migration controls for read-only strangler candidates. */
@Configuration(proxyBeanMethods = false)
@Profile("persistence")
public class MigrationSafetyConfig {

    /**
     * A read-only domain role must not need visibility into the thesis schema
     * merely because thesis JPA entities share this deployable.
     */
    @Bean
    @Conditional(ReadOnlyMigrationCandidateCondition.class)
    HibernatePropertiesCustomizer disableHibernateSchemaManagementForReadOnlyCandidates() {
        return properties -> properties.put("hibernate.hbm2ddl.auto", "none");
    }

    /**
     * Spring Boot invokes this strategy instead of {@code flyway.migrate()}.
     * Therefore an operator cannot accidentally combine a read-only candidate
     * flag with Flyway and mutate a legacy/shared schema during startup.
     */
    @Bean
    @Conditional(ReadOnlyMigrationCandidateCondition.class)
    FlywayMigrationStrategy rejectFlywayForReadOnlyCandidates() {
        return flyway -> {
            throw new IllegalStateException(
                    "Flyway must be disabled while a read-only migration candidate is enabled");
        };
    }
}
