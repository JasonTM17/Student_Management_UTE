/**
 * Enrollment-status semantics mirrored from
 * frontend/src/lib/enrollment-status.ts and the backend predicates:
 * ENROLLED, PENDING and CONFIRMED rows all occupy a seat, while the drop
 * endpoint accepts ENROLLED and PENDING only. `/enrollments/my` returns
 * DROPPED, WITHDRAWN and CANCELLED history alongside the live rows, so
 * every active list has to filter them out instead of trusting the feed.
 */

const ACTIVE_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'PENDING', 'CONFIRMED']);
const DROPPABLE_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'PENDING']);
const SCHEDULED_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'PENDING', 'CONFIRMED', 'COMPLETED']);

export interface EnrollmentLike {
  status?: string | null;
  section?: { status?: string | null } | null;
}

/**
 * Status-only membership in the seat-counting set. The enrolled/drop
 * decision uses this rather than the section-aware variant so a section an
 * admin cancelled underneath the student stays droppable — the student
 * must never be trapped by a cancellation outside an open round.
 */
export function isActiveEnrollmentStatus(status: string | null | undefined): boolean {
  return ACTIVE_ENROLLMENT_STATUSES.has(status ?? '');
}

/** Live seat/credit rows — the same set the backend counts for capacity. */
export function isActiveEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return isActiveEnrollmentStatus(enrollment?.status)
    && enrollment?.section?.status !== 'CANCELLED';
}

/** Statuses `POST /me/enrollments/{id}/drop` accepts. */
export function isDroppableEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return (
    DROPPABLE_ENROLLMENT_STATUSES.has(enrollment?.status ?? '')
    && enrollment?.section?.status !== 'CANCELLED'
  );
}

/** Timetable rows: live terms plus completed history, like the web schedule. */
export function isScheduledEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return (
    SCHEDULED_ENROLLMENT_STATUSES.has(enrollment?.status ?? '')
    && enrollment?.section?.status !== 'CANCELLED'
  );
}
