package io.campuscore.restfulapi.thesis.assistant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an Assistant SQL entry point that must run inside the RLS transaction boundary. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AssistantRlsBoundary {
    Access access() default Access.AUTO;

    enum Access {
        /** Use an installed trusted scope, otherwise the authenticated JWT subject. */
        AUTO,
        /** Admin-only knowledge authoring/release operations. */
        ADMIN_GOVERNANCE,
        /** Narrow scheduled lease-recovery and retention operations. */
        RETENTION,
        /** Narrow Supabase-to-Assistant knowledge projection operations. */
        KNOWLEDGE_PROJECTION
    }
}
