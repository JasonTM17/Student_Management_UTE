/** datetime-local contains browser-local wall time; preserve seconds and milliseconds. */
export function toThesisLocalInput(iso?: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime())) return '';
  const pad = (value: number, width = 2) => String(value).padStart(width, '0');
  return `${pad(date.getFullYear(), 4)}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}

/**
 * Phase windows are faculty deadlines, not viewer-local times: every role
 * reads the same 24-hour dd/MM/yyyy HH:mm wall clock in Asia/Bangkok, no
 * matter which timezone the browser sits in or which locale is active.
 */
export function formatThesisDateTime(value: string | number | Date | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '—';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).formatToParts(date);
  const pick = (type: string) => parts.find((part) => part.type === type)?.value ?? '';
  // hour12:false still emits "24" for midnight in some ICU versions.
  const hour = pick('hour') === '24' ? '00' : pick('hour');
  return `${pick('day')}/${pick('month')}/${pick('year')} ${hour}:${pick('minute')}`;
}

export interface ThesisWindowRemaining {
  days: number;
  hours: number;
  minutes: number;
}

/**
 * Time left until a deadline or window end, floored to whole minutes. Null
 * when the instant is missing, unreadable, or already in the past — callers
 * hide the line rather than show a stale countdown.
 */
export function thesisWindowRemaining(
  end: string | number | Date | null | undefined,
  now: number = Date.now(),
): ThesisWindowRemaining | null {
  if (end === null || end === undefined || end === '') return null;
  const endMs = new Date(end).getTime();
  if (!Number.isFinite(endMs)) return null;
  const left = Math.floor((endMs - now) / 60_000);
  if (left <= 0) return null;
  return {
    days: Math.floor(left / 1_440),
    hours: Math.floor((left % 1_440) / 60),
    minutes: left % 60,
  };
}
