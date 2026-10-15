import type { User } from '@/types/api';

export const LOGIN_PORTALS = ['student', 'lecturer', 'dean', 'admin'] as const;
export type LoginPortal = (typeof LOGIN_PORTALS)[number];

// The admin tab is intentionally not offered on the login form: ADMIN and
// SUPER_ADMIN accounts sign in from a visible tab and are routed by role.
// 'admin' remains a valid LoginPortal for deep links and guard redirects.
// 'dean' (Trưởng khoa) is visible because the dean dashboard is a distinct
// workspace with its own tab.
export const VISIBLE_LOGIN_PORTALS = ['student', 'lecturer', 'dean'] as const;

export function parseLoginPortal(value: string | null | undefined): LoginPortal {
  if (
    value === 'lecturer' ||
    value === 'dean' ||
    value === 'admin' ||
    value === 'student'
  ) {
    return value;
  }

  return 'student';
}

export function userRoles(user: Pick<User, 'role' | 'roles'>): string[] {
  if (user.roles?.length) {
    return user.roles;
  }

  return user.role ? [user.role] : [];
}

export function portalMatchesUser(
  portal: LoginPortal,
  user: Pick<User, 'role' | 'roles'>,
): boolean {
  const roles = userRoles(user);

  if (portal === 'admin') {
    return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN');
  }

  if (portal === 'dean') {
    return roles.includes('TRUONG_KHOA');
  }

  if (portal === 'lecturer') {
    return roles.includes('LECTURER') || roles.includes('TRUONG_BO_MON');
  }

  return roles.includes('STUDENT');
}

export function postLoginRoute(portal: LoginPortal): string {
  if (portal === 'admin') {
    return '/admin';
  }

  if (portal === 'dean') {
    return '/dashboard/dean';
  }

  if (portal === 'lecturer') {
    return '/dashboard/lecturer';
  }

  return '/dashboard';
}

export function portalFromUser(user: Pick<User, 'role' | 'roles'> | null | undefined): LoginPortal {
  if (!user) {
    return 'student';
  }

  const roles = userRoles(user);
  if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) {
    return 'admin';
  }

  if (roles.includes('TRUONG_KHOA')) {
    return 'dean';
  }

  if (roles.includes('LECTURER') || roles.includes('TRUONG_BO_MON')) {
    return 'lecturer';
  }

  return 'student';
}

export function portalFromPathname(pathname: string): LoginPortal {
  if (pathname.includes('/admin')) {
    return 'admin';
  }

  if (pathname.includes('/dashboard/dean')) {
    return 'dean';
  }

  // The thesis workspace belongs to department heads and deans; both
  // authenticate through lecturer-shaped portals.
  if (pathname.includes('/dashboard/lecturer') || pathname.includes('/dashboard/thesis')) {
    return 'lecturer';
  }

  return 'student';
}

export function loginHref(
  localize: (pathname: string) => string,
  portal: LoginPortal,
  reason?: string,
): string {
  const params = new URLSearchParams();
  params.set('portal', portal);
  if (reason) {
    params.set('reason', reason);
  }

  return `${localize('/login')}?${params.toString()}`;
}

export function isDemoUser(user: { email?: string } | null | undefined): boolean {
  if (!user?.email) return false;
  const email = user.email.toLowerCase().trim();
  return (
    email === 'student@campuscore.edu' ||
    email === 'lecturer@campuscore.edu' ||
    email === 'admin@campuscore.edu' ||
    email.endsWith('@campuscore.edu') ||
    email.includes('demo')
  );
}
