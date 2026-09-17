/**
 * One source of truth for enrollment-status semantics.
 *
 * The backend counts a PENDING row against the section seat and the term
 * credit cap exactly like an ENROLLED one (RegistrationService seat/CAS
 * predicates all use `IN ('ENROLLED','PENDING','CONFIRMED')`), so every
 * active-courses total here must include it too — otherwise the dashboard
 * says "1 seat free" while registration says the round is full. The drop
 * gate accepts ENROLLED and PENDING only.
 */
export const ACTIVE_ENROLLMENT_STATUSES = ['ENROLLED', 'PENDING', 'CONFIRMED'] as const;

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
