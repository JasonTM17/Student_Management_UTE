/**
 * Value model for DateTime24Field: a local wall-clock string
 * `YYYY-MM-DDTHH:mm` — the same shape `datetime-local` produced — with ''
 * meaning unset. Stored values may still carry seconds/milliseconds; they are
 * displayed to the minute and only normalized when the user actually edits.
 */

export interface DateTime24Parts {
  /** 'YYYY-MM-DD' — the field only emits a value once a date is chosen. */
  date: string;
  /** '00'–'23' or '' when unset/invalid. */
  hour: string;
  /** '00'–'59' or '' when unset/invalid. */
  minute: string;
}

export type DateTime24Part = 'date' | 'hour' | 'minute';

export interface DateTime24Edit {
  /** Value for onChange — null means the current value stays untouched. */
  emit: string | null;
  /** Time picked before a date exists; consumed by the next date pick. */
  pendingHour: string;
  pendingMinute: string;
}

const DATE_ONLY = /^(\d{4}-\d{2}-\d{2})$/;
const DATE_TIME = /^(\d{4}-\d{2}-\d{2})[T ](\d{1,2}):(\d{1,2})(?::\d{1,2}(?:\.\d{1,6})?)?$/;

const norm = (raw: string, max: number): string => {
  if (!/^\d{1,2}$/.test(raw)) return '';
  const value = Number(raw);
  return value <= max ? String(value).padStart(2, '0') : '';
};

/** Splits a stored value into display parts; unknown shapes read as unset. */
export function splitDateTime24(value: string | null | undefined): DateTime24Parts {
  const text = (value ?? '').trim();
  if (!text) return { date: '', hour: '', minute: '' };
  const dateTime = DATE_TIME.exec(text);
  if (dateTime) {
    return {
      date: dateTime[1],
      hour: norm(dateTime[2], 23),
      minute: norm(dateTime[3], 59),
    };
  }
  const dateOnly = DATE_ONLY.exec(text);
  if (dateOnly) return { date: dateOnly[1], hour: '', minute: '' };
  return { date: '', hour: '', minute: '' };
}

/** Composes the canonical `YYYY-MM-DDTHH:mm`; '' while no date is chosen. */
export function composeDateTime24(date: string, hour = '', minute = ''): string {
  if (!DATE_ONLY.test(date)) return '';
  return `${date}T${norm(hour, 23) || '00'}:${norm(minute, 59) || '00'}`;
}

/**
 * Applies one sub-control edit to the current value. The result says whether
 * to emit (`emit` non-null) and carries the time the user pre-picked while no
 * date existed yet (`pendingHour`/`pendingMinute`). Sub-minute precision of a
 * stored value is never reproduced: once the user edits, the emitted string is
 * canonical minute precision.
 */
export function editDateTime24(
  current: string,
  part: DateTime24Part,
  next: string,
  pending: { hour: string; minute: string } = { hour: '', minute: '' },
): DateTime24Edit {
  const parsed = splitDateTime24(current);
  if (part === 'date') {
    if (next === '') {
      return { emit: '', pendingHour: '', pendingMinute: '' };
    }
    if (!DATE_ONLY.test(next)) {
      // A native date control only reports '' or a real day; ignore malformed.
      return { emit: null, pendingHour: pending.hour, pendingMinute: pending.minute };
    }
    const hour = parsed.date ? parsed.hour || '00' : pending.hour || '00';
    const minute = parsed.date ? parsed.minute || '00' : pending.minute || '00';
    return { emit: `${next}T${hour}:${minute}`, pendingHour: '', pendingMinute: '' };
  }
  const clean = norm(next, part === 'hour' ? 23 : 59);
  if (!parsed.date) {
    return {
      emit: null,
      pendingHour: part === 'hour' ? clean : pending.hour,
      pendingMinute: part === 'minute' ? clean : pending.minute,
    };
  }
  const hour = part === 'hour' ? clean || '00' : parsed.hour || '00';
  const minute = part === 'minute' ? clean || '00' : parsed.minute || '00';
  return { emit: `${parsed.date}T${hour}:${minute}`, pendingHour: pending.hour, pendingMinute: pending.minute };
}
