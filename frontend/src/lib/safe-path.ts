// Same-origin internal path allowlist for values that come from server data
// (e.g. notification `link`) or from free-text admin input. A value is only
// safe to emit as a local href when it starts with a single "/", carries no
// backslash (which some parsers treat as a path separator), and contains no
// control characters or whitespace. Rejecting at this layer means a stored
// "javascript:", "data:", or protocol-relative "//host" value can never be
// rendered as a navigable anchor.
const INTERNAL_PATH_PATTERN = /^\/(?!\/)[^\s\\\x00-\x1f\x7f]*$/u;

export function isSafeInternalPath(value: string | null | undefined): boolean {
  return typeof value === 'string' && INTERNAL_PATH_PATTERN.test(value);
}

export function safeInternalPath(
  value: string | null | undefined,
): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return INTERNAL_PATH_PATTERN.test(trimmed) ? trimmed : null;
}
