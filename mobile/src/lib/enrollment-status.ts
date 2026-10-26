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

function hasStatus(set: ReadonlySet<string>, enrollment: EnrollmentLike | null | undefined): boolean {
  return (
    set.has(enrollment?.status ?? '')
    && enrollment?.section?.status !== 'CANCELLED'
  );
}

/** Live seat/credit rows — the same set the backend counts for capacity. */
export function isActiveEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return hasStatus(ACTIVE_ENROLLMENT_STATUSES, enrollment);
}

/** Statuses `POST /me/enrollments/{id}/drop` accepts. */
export function isDroppableEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return hasStatus(DROPPABLE_ENROLLMENT_STATUSES, enrollment);
}

/** Timetable rows: live terms plus completed history, like the web schedule. */
export function isScheduledEnrollment(enrollment: EnrollmentLike | null | undefined): boolean {
  return hasStatus(SCHEDULED_ENROLLMENT_STATUSES, enrollment);
}
