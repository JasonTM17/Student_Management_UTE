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

const DATE_ONLY = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * Parses a value for display. Date-only strings are civil dates, not
 * instants: `new Date('2024-06-15')` is UTC midnight and renders as the
 * previous day for any browser west of UTC, so the parts are rebuilt as a
 * local calendar day instead. Datetimes keep instant semantics.
 */
export function parseDisplayDate(value: string | number | Date): Date {
  if (typeof value === 'string') {
    const dateOnly = DATE_ONLY.exec(value.trim());
    if (dateOnly) {
      return new Date(Number(dateOnly[1]), Number(dateOnly[2]) - 1, Number(dateOnly[3]));
    }
  }
  return new Date(value);
}
