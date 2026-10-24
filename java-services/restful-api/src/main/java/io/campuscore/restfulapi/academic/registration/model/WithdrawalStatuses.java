package io.campuscore.restfulapi.academic.registration.model;

import java.util.Set;

/** Lifecycle states for the approved course-withdrawal (grade W) workflow. */
public final class WithdrawalStatuses {

    /** Enrollment still counts as registered; a withdrawal may act on these. */
    public static final Set<String> WITHDRAWABLE_ENROLLMENTS =
            Set.of("ENROLLED", "PENDING", "CONFIRMED");

    /**
     * Regular (non-summer) semester types eligible for a W withdrawal. The
     * persisted vocabulary is FIRST/SECOND (V5/V25 seeds, admin catalog
     * default); FALL/SPRING are accepted as legacy aliases the frontend
     * catalog still lists.
     */
    public static final Set<String> REGULAR_SEMESTER_TYPES =
            Set.of("FIRST", "SECOND", "FALL", "SPRING");

    public static final String APPLICATION_PENDING = "PENDING";

    private WithdrawalStatuses() {
    }
}
