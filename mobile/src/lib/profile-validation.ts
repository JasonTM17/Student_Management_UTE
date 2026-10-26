/**
 * Client-side mirror of the writable-profile and password constraints the
 * backend enforces (`UpdateProfileRequest` + `PasswordPolicy`), so a save
 * fails fast with a field hint instead of an opaque 400. The server stays
 * authoritative — the common-password denylist is only checked remotely.
 */

export const PHONE_MAX_LENGTH = 80;
export const ADDRESS_MAX_LENGTH = 500;
export const MIN_PASSWORD_LENGTH = 8;
export const MAX_PASSWORD_LENGTH = 128;
/** BCrypt truncates at 72 bytes; the backend rejects rather than truncating. */
export const MAX_PASSWORD_UTF8_BYTES = 72;

const MIN_LOCAL_PART_FOR_SUBSTRING = 4;
const LETTER_PATTERN = /\p{L}/u;
const DIGIT_PATTERN = /\p{Nd}/u;
// Same shape as the UpdateProfileRequest.phone pattern.
const PHONE_PATTERN = /^\+?[0-9][0-9\s()./-]{4,78}$/;

export type ProfileFieldIssue = 'PHONE_TOO_LONG' | 'PHONE_INVALID' | 'ADDRESS_TOO_LONG';

export function profilePhoneIssue(phone: string): ProfileFieldIssue | null {
  if (phone.length > PHONE_MAX_LENGTH) {
    return 'PHONE_TOO_LONG';
  }
  if (phone !== '' && !PHONE_PATTERN.test(phone)) {
    return 'PHONE_INVALID';
  }
  return null;
}

export function profileAddressIssue(address: string): ProfileFieldIssue | null {
  return address.length > ADDRESS_MAX_LENGTH ? 'ADDRESS_TOO_LONG' : null;
}

export type PasswordIssue =
  | 'PASSWORD_TOO_SHORT'
  | 'PASSWORD_TOO_LONG'
  | 'PASSWORD_CONTAINS_IDENTITY'
  | 'PASSWORD_TOO_WEAK'
  | 'PASSWORD_REUSED';

function utf8Length(value: string): number {
  return new TextEncoder().encode(value).length;
}

function emailLocalPart(email: string): string {
  const at = email.indexOf('@');
  return (at > 0 ? email.substring(0, at) : email).trim().toLowerCase();
}

/** First failing rule in the same order the backend checks them. */
export function passwordIssue(
  candidate: string,
  options: { email?: string; currentPassword?: string } = {},
): PasswordIssue | null {
  if (!candidate || candidate.length < MIN_PASSWORD_LENGTH) {
    return 'PASSWORD_TOO_SHORT';
  }
  if (candidate.length > MAX_PASSWORD_LENGTH || utf8Length(candidate) > MAX_PASSWORD_UTF8_BYTES) {
    return 'PASSWORD_TOO_LONG';
  }
  const email = options.email?.trim();
  if (email) {
    const normalized = candidate.toLowerCase();
    const localPart = emailLocalPart(email);
    if (
      localPart
      && (normalized === email.toLowerCase()
        || normalized === localPart
        || (localPart.length >= MIN_LOCAL_PART_FOR_SUBSTRING && normalized.includes(localPart)))
    ) {
      return 'PASSWORD_CONTAINS_IDENTITY';
    }
  }
  if (!LETTER_PATTERN.test(candidate) || !DIGIT_PATTERN.test(candidate)) {
    return 'PASSWORD_TOO_WEAK';
  }
  if (options.currentPassword !== undefined && candidate === options.currentPassword) {
    return 'PASSWORD_REUSED';
  }
  return null;
}
