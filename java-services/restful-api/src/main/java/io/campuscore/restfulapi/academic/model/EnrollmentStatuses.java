package io.campuscore.restfulapi.academic.model;

import java.util.List;

/** Enrollment lifecycle vocabulary shared by repository predicates. */
public final class EnrollmentStatuses {

    /**
     * Statuses under which an enrollment still counts as a live class
     * membership: the row holds its seat, appears on rosters and receives
     * section-scoped traffic. WITHDRAWN is deliberately absent — an approved
     * withdrawal releases the seat and survives only as transcript history
     * (letter grade W) and in the withdrawal application record.
     */
    public static final List<String> ACTIVE_STATUSES =
            List.of("ENROLLED", "PENDING", "CONFIRMED", "COMPLETED");

    /** The same set as an SQL IN-list fragment for JDBC predicates. */
    public static final String ACTIVE_STATUS_PREDICATE =
            "('" + String.join("', '", ACTIVE_STATUSES) + "')";

    private EnrollmentStatuses() {
    }
}
