package io.campuscore.restfulapi.academic.model;

import java.util.Set;

/** Shared lifecycle vocabulary used by persistence and service boundaries. */
public final class SemesterLifecycleStatuses {
    public static final Set<String> OPEN =
            Set.of("OPEN", "REGISTRATION_OPEN", "ADD_DROP_OPEN", "ACTIVE");

    private SemesterLifecycleStatuses() {
    }
}
