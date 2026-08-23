package io.campuscore.restfulapi.migration;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class ReadOnlyMigrationCandidateCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        // The course API owns the Flyway-managed schema; legacy candidate
        // flags remain safety-gated when this explicit runtime mode is off.
        // Course-owned thesis routes are gated independently and are not part
        // of the legacy migration-candidate set this condition protects.
        if (enabled(context, "migration.course-api.enabled")) {
            return false;
        }
        return enabled(context, "migration.engagement-read.enabled")
                || enabled(context, "migration.engagement-write.enabled")
                || enabled(context, "migration.notifications-read.enabled")
                || enabled(context, "migration.notifications-write.enabled")
                || enabled(context, "migration.academic-read.enabled")
                || enabled(context, "migration.academic-context.enabled")
                || enabled(context, "migration.academic-enrollment-read.enabled")
                || enabled(context, "migration.academic-schedule-read.enabled")
                || enabled(context, "migration.academic-waitlist-read.enabled")
                || enabled(context, "migration.academic-attendance-read.enabled")
                || enabled(context, "migration.academic-section-read.enabled")
                || enabled(context, "migration.people-read.enabled")
                || enabled(context, "migration.finance-read.enabled")
                || enabled(context, "migration.analytics-read.enabled");
    }

    private static boolean enabled(ConditionContext context, String property) {
        return context.getEnvironment().getProperty(property, Boolean.class, false);
    }
}
