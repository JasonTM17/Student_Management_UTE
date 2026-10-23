package io.campuscore.restfulapi.academic.service;

import java.util.Set;

/** Semester lifecycle states shared by every academic write path. */
public final class SemesterStatuses {

    /** Semesters in which academic work (registration, grading, attendance) is still allowed. */
    public static final Set<String> OPEN =
            Set.of("OPEN", "REGISTRATION_OPEN", "ADD_DROP_OPEN", "ACTIVE");

    /**
     * Lifecycle states a semester never leaves. The column is free-form
     * (VARCHAR(40)) and the admin catalog only writes CLOSED, but the set
     * covers the closed/completed/archived vocabulary the rest of the
     * codebase uses so a direct write cannot silently reopen. Callers
     * compare against UPPER(TRIM(status)), so case or whitespace variants
     * of these values refuse the same way.
     */
    public static final Set<String> TERMINAL =
            Set.of("CLOSED", "COMPLETED", "ARCHIVED");

    private SemesterStatuses() {
    }
}
