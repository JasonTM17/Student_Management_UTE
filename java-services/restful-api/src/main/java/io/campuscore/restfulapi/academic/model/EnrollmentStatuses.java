package io.campuscore.restfulapi.academic.model;

/** Enrollment lifecycle vocabulary shared by repository predicates. */
public final class EnrollmentStatuses {

    /**
     * Statuses under which an enrollment still counts as a live class
     * membership: the row holds its seat, appears on rosters and receives
     * section-scoped traffic. WITHDRAWN is deliberately absent — an approved
     * withdrawal releases the seat and survives only as transcript history
     * (letter grade W) and in the withdrawal application record. The value is
     * an SQL IN-list fragment because every consumer is a JDBC predicate.
     */
    public static final String ACTIVE_STATUS_PREDICATE =
            "('ENROLLED', 'PENDING', 'CONFIRMED', 'COMPLETED')";

    private EnrollmentStatuses() {
    }
}
