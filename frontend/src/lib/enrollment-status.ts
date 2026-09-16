/**
 * One source of truth for enrollment-status semantics.
 *
 * The schedule, the enrollment counters and the drop affordance used to
 * disagree, so a pending enrollment could look like a real class while the
 * drop button stayed hidden. The backend accepts a drop only for `ENROLLED`
 * and `PENDING`, and only confirmed/enrolled rows count as active courses.
 */
export const ACTIVE_ENROLLMENT_STATUSES = ['CONFIRMED', 'ENROLLED'] as const;

/** Statuses the backend accepts on `POST /me/enrollments/{id}/drop`. */
export const DROPPABLE_ENROLLMENT_STATUSES = ['ENROLLED', 'PENDING'] as const;

export function isActiveEnrollment(status: string | undefined | null): boolean {
  return ACTIVE_ENROLLMENT_STATUSES.includes(
    (status ?? '') as (typeof ACTIVE_ENROLLMENT_STATUSES)[number],
  );
}

export function isDroppableEnrollment(status: string | undefined | null): boolean {
  return DROPPABLE_ENROLLMENT_STATUSES.includes(
    (status ?? '') as (typeof DROPPABLE_ENROLLMENT_STATUSES)[number],
  );
}
