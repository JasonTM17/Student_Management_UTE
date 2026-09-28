/**
 * Quick-fill credential guards for the login portal panel.
 *
 * Pure functions only — the node --test suite executes this module directly,
 * with no React and no browser globals. Kept out of `app/login/page.tsx`
 * because that page is asserted to stay free of password literals
 * (tests/demo-credentials-gate.test.js); the stale defaults live here instead.
 */

/**
 * Seed defaults that are known to have been rotated. A deployment whose
 * quick-fill panel still shows one of these is serving a stale value (for
 * example a Vercel env var that was never updated), so the UI warns next to
 * the credential instead of letting a visitor submit a password that cannot
 * work. This list is intentionally literal and tiny — it guards a courtesy
 * demo panel, nothing else.
 */
const STALE_DEMO_PASSWORDS: ReadonlySet<string> = new Set(['admin123']);

export function isStaleDemoPassword(password: string | undefined): boolean {
  return typeof password === 'string' && STALE_DEMO_PASSWORDS.has(password);
}
