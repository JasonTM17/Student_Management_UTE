import type { TwoFactorChallengeResponse } from '@/types/api';

/**
 * Shared, side-effect-free helpers for the opt-in email one-time-code flow.
 * Nothing here ever persists a code: the OTP lives in component state only
 * for the lifetime of one verification attempt and is never logged.
 */

export const TWO_FACTOR_CODE_LENGTH = 6;

/**
 * Mask an email for display, mirroring the backend shape "s***@gmail.com":
 * keep the first character of the local part, three asterisks, then the
 * domain. Falls back to masking the raw text when there is no "@" at all.
 */
export function maskEmail(email: string | null | undefined): string {
  const trimmed = (email ?? '').trim();
  if (!trimmed) {
    return '';
  }

  const atIndex = trimmed.indexOf('@');
  if (atIndex <= 0) {
    return `${trimmed.charAt(0)}***`;
  }

  return `${trimmed.charAt(0)}***@${trimmed.slice(atIndex + 1)}`;
}

/**
 * Normalize a typed or pasted one-time code: keep digits only (spaces,
 * dashes and other separators are dropped) and cap at the expected length.
 */
export function normalizeOtpCode(value: string | null | undefined): string {
  const digits = (value ?? '').replace(/\D+/g, '');
  return digits.slice(0, TWO_FACTOR_CODE_LENGTH);
}

/**
 * True when a login attempt answered with a two-factor challenge instead of
 * a session. Checks the discriminating flag plus the challenge id so a
 * malformed payload cannot be mistaken for a challenge.
 */
export function isTwoFactorChallenge(
  response: unknown,
): response is TwoFactorChallengeResponse {
  if (typeof response !== 'object' || response === null) {
    return false;
  }

  const candidate = response as {
    twoFactorRequired?: unknown;
    challengeId?: unknown;
  };
  return (
    candidate.twoFactorRequired === true &&
    typeof candidate.challengeId === 'string' &&
    candidate.challengeId.length > 0
  );
}
