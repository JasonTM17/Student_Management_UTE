import type { User } from '@/types/api';

export const LOGIN_PORTALS = ['student', 'lecturer', 'admin'] as const;
export type LoginPortal = (typeof LOGIN_PORTALS)[number];

export function parseLoginPortal(value: string | null | undefined): LoginPortal {
  if (value === 'lecturer' || value === 'admin' || value === 'student') {
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

  if (portal === 'lecturer') {
    return roles.includes('LECTURER');
  }

  return roles.includes('STUDENT');
}

export function postLoginRoute(portal: LoginPortal): string {
  if (portal === 'admin') {
    return '/admin';
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

  if (roles.includes('LECTURER')) {
    return 'lecturer';
  }

  return 'student';
}

export function portalFromPathname(pathname: string): LoginPortal {
  if (pathname.includes('/admin')) {
    return 'admin';
  }

  if (pathname.includes('/dashboard/lecturer')) {
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
