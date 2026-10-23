package io.campuscore.restfulapi.academic.service;

import java.util.Set;

/** Semester lifecycle states shared by every academic write path. */
public final class SemesterStatuses {

    /** Semesters in which academic work (registration, grading, attendance) is still allowed. */
    public static final Set<String> OPEN =
            Set.of("OPEN", "REGISTRATION_OPEN", "ADD_DROP_OPEN", "ACTIVE");

    private SemesterStatuses() {
    }
}
