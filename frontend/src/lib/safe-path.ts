// Same-origin internal path allowlist for values that come from server data
// (e.g. notification `link`) or from free-text admin input. A value is only
// safe to emit as a local href when it starts with a single "/", carries no
// backslash (which some parsers treat as a path separator), no "." or ".."
// path segment, no percent-encoded separator or dot (%2e/%2f/%5c/%25 — the
// encodings a downstream decode would turn into one), and no Unicode
// separator, format, or control code points. Rejecting at this layer means a
// stored "javascript:", "data:", traversal, or protocol-relative "//host"
// value can never be rendered as a navigable anchor. The shared corpus at
// java-services/restful-api/src/test/resources/notification/link-corpus.json
// is the contract the Java NotificationLinkPolicy and migration V142 mirror.
const INTERNAL_PATH_PATTERN = /^\/(?!\/)[^\\\p{C}\p{Z}]*$/u;
const DOT_SEGMENT_PATTERN = /(^|\/)\.\.?(?:\/|$)/;
const ENCODED_SEPARATOR_OR_DOT_PATTERN = /%(?:2e|2f|5c|25)/i;

function passesAllowlist(value: string): boolean {
  return (
    INTERNAL_PATH_PATTERN.test(value) &&
    !DOT_SEGMENT_PATTERN.test(value) &&
    !ENCODED_SEPARATOR_OR_DOT_PATTERN.test(value)
  );
}

export function isSafeInternalPath(value: string | null | undefined): boolean {
  return typeof value === 'string' && passesAllowlist(value);
}

export function safeInternalPath(
  value: string | null | undefined,
): string | null {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return passesAllowlist(trimmed) ? trimmed : null;
}
