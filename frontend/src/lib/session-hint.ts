export const CSRF_COOKIE_NAME = 'cc_csrf';

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
