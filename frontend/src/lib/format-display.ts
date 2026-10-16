/** Renders a code/label only when it is a real value — some payloads ship the
 * literal strings "null"/"undefined" which are truthy but must not print. */
export function displayLabel(value: string | null | undefined, fallback = '—') {
  if (!value || value === 'null' || value === 'undefined') return fallback;
  return value;
}

/**
 * Renders a backend date only when the value actually parses.
 * `new Date(null)` is a valid epoch (01/01/1970) and literal "null"/"undefined"
 * strings parse to Invalid Date — both must render as the fallback instead.
 */
export function formatOptionalDate(
  formatDate: (value: string | number | Date) => string,
  value: string | number | Date | null | undefined,
  fallback = '—',
) {
  if (value === null || value === undefined || value === '') return fallback;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? fallback : formatDate(value);
}
