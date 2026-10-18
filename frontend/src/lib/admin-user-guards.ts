/**
 * Mirrors the backend ceiling in `AdminUserMutationService`
 * (`SUPER_ADMIN_ONLY_ROLES`): a plain administrator may not mutate an
 * account holding one of these roles — the row actions stay disabled and
 * the API independently enforces the same limit with 403 ROLE_ESCALATION.
 */
const SUPER_ADMIN_ONLY_ROLES = new Set([
  'ADMIN',
  'SUPER_ADMIN',
  'TRUONG_KHOA',
  'TRUONG_BO_MON',
]);

export type ManagedRole =
  | 'STUDENT'
  | 'LECTURER'
  | 'ADMIN'
  | 'TRUONG_KHOA'
  | 'TRUONG_BO_MON'
  | 'SUPER_ADMIN';

/**
 * Highest managed role wins, independent of array order or letter case: a dean
 * or department-head pair (TRUONG_KHOA/TRUONG_BO_MON + LECTURER) reads as the
 * head role, mixed legacy data surfaces the most privileged supported role,
 * and custom-only sets return null so the caller disables role editing instead
 * of guessing a STUDENT default.
 */
const MANAGED_ROLE_PRIORITY: readonly ManagedRole[] = [
  'SUPER_ADMIN',
  'ADMIN',
  'TRUONG_KHOA',
  'TRUONG_BO_MON',
  'LECTURER',
  'STUDENT',
];

export function primaryManagedRole(
  roles: readonly string[] | null | undefined,
): ManagedRole | null {
  const held = new Set(
    (roles ?? [])
      .map((role) => role.trim().toUpperCase())
      .filter(Boolean),
  );
  for (const role of MANAGED_ROLE_PRIORITY) {
    if (held.has(role)) {
      return role;
    }
  }
  return null;
}

export function isPrivilegedAccountTarget(
  roles: readonly string[] | null | undefined,
): boolean {
  return (roles ?? []).some((role) => {
    const normalized = role.trim().toUpperCase();
    if (!normalized) {
      return false;
    }
    return SUPER_ADMIN_ONLY_ROLES.has(normalized) || primaryManagedRole([normalized]) === null;
  });
}
