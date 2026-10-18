export const CSRF_COOKIE_NAME = 'cc_csrf';

// Module-level marker shared with the axios response interceptor: while a
// logout() call is in flight, the caller's own redirect (signed-out,
// password-rotated, or a custom target) owns navigation. Without this flag a
// concurrent in-flight request that 401s during the logout window fires the
// interceptor's session-death redirect and overwrites the intended target —
// observed as reason=session-expired replacing reason=signed-out when an
// admin opened /dashboard/sign-out directly.
let logoutInProgress = false;

export function markLogoutInProgress(): void {
  logoutInProgress = true;
}

export function clearLogoutInProgress(): void {
  logoutInProgress = false;
}

export function isLogoutInProgress(): boolean {
  return logoutInProgress;
}

export function cookieHasName(cookieSource: string, name: string): boolean {
  const escapedName = name.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  return new RegExp(`(?:^|; )${escapedName}=`).test(cookieSource);
}

export function hasCsrfSessionHint(cookieSource?: string): boolean {
  const source =
    cookieSource ??
    (typeof document !== 'undefined' ? document.cookie : '');
  return cookieHasName(source, CSRF_COOKIE_NAME);
}

export function readCsrfSessionHint(cookieSource?: string): string | null {
  const source =
    cookieSource ??
    (typeof document !== 'undefined' ? document.cookie : '');
  const escapedName = CSRF_COOKIE_NAME.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  const match = new RegExp(`(?:^|; )${escapedName}=([^;]*)`).exec(source);
  return match ? match[1] : null;
}
