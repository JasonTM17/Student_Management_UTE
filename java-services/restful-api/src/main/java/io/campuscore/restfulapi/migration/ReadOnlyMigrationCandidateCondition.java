package io.campuscore.restfulapi.migration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class ReadOnlyMigrationCandidateCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return enabled(context, "migration.engagement-read.enabled")
                || enabled(context, "migration.notifications-read.enabled")
                || enabled(context, "migration.academic-read.enabled")
                || enabled(context, "migration.academic-enrollment-read.enabled")
                || enabled(context, "migration.people-read.enabled")
                || enabled(context, "migration.finance-read.enabled")
                || enabled(context, "migration.analytics-read.enabled")
                || enabled(context, "migration.auth-login.enabled");
    }

    private static boolean enabled(ConditionContext context, String property) {
        return context.getEnvironment().getProperty(property, Boolean.class, false);
    }
}
