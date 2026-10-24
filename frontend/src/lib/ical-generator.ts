import type { Enrollment, Semester } from '@/types/api';
import { isActiveEnrollment, isCancelledSection } from './enrollment-status';

export interface IcsEvent {
  uid: string;
  summary: string;
  description?: string;
  location?: string;
  dtstart: string; // YYYYMMDDTHHmmss
  dtend: string;   // YYYYMMDDTHHmmss
  rrule?: string;  // e.g. FREQ=WEEKLY;UNTIL=20261231T235959Z
}

/**
 * Escapes characters for RFC 5545 compliance:
 * Backslash, semicolon, comma, and newline characters must be escaped.
 */
export function escapeIcsText(text: string): string {
  if (!text) return '';
  return text
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\r?\n/g, '\\n');
}

/**
 * Pads a single or double digit number with leading zero.
 */
function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

/**
 * Formats a JavaScript Date object into RFC 5545 local datetime string: YYYYMMDDTHHmmss.
 */
export function formatIcsLocalDateTime(date: Date): string {
  const year = date.getFullYear();
  const month = pad(date.getMonth() + 1);
  const day = pad(date.getDate());
  const hours = pad(date.getHours());
  const minutes = pad(date.getMinutes());
  const seconds = pad(date.getSeconds());
  return `${year}${month}${day}T${hours}${minutes}${seconds}`;
}

/**
 * Formats a date into RFC 5545 UTC datetime string: YYYYMMDDTHHmmssZ.
 */
export function formatIcsUtcDateTime(date: Date): string {
  const year = date.getUTCFullYear();
  const month = pad(date.getUTCMonth() + 1);
  const day = pad(date.getUTCDate());
  const hours = pad(date.getUTCHours());
  const minutes = pad(date.getUTCMinutes());
  const seconds = pad(date.getUTCSeconds());
  return `${year}${month}${day}T${hours}${minutes}${seconds}Z`;
}

/**
 * Calculates the first occurrence date of a specific day of week
 * on or after the base start date.
 * dayOfWeek: 1 = Sunday, 2 = Monday, ..., 7 = Saturday.
 *
 * Both input and output are treated as civil (calendar) dates anchored at
 * UTC midnight — the function only ever reads/writes UTC fields. Callers
 * must parse date-only strings ("YYYY-MM-DD") with `new Date()` (which
 * yields UTC midnight) and must not mix in browser-local getDay/setDate,
 * otherwise users outside UTC see the event shifted by a day.
 */
export function findFirstOccurrenceDate(baseStartDate: Date, dayOfWeek: number): Date {
  const targetJsDay = dayOfWeek === 1 ? 0 : dayOfWeek - 1; // 0 = Sun, 1 = Mon, ..., 6 = Sat
  const currentJsDay = baseStartDate.getUTCDay();
  const diffDays = (targetJsDay - currentJsDay + 7) % 7;
  return new Date(Date.UTC(
    baseStartDate.getUTCFullYear(),
    baseStartDate.getUTCMonth(),
    baseStartDate.getUTCDate() + diffDays,
  ));
}

/**
 * Formats a civil date (held as a UTC-midnight Date) plus a wall-clock
 * HH:mm into an RFC 5545 local datetime string: YYYYMMDDTHHmmss.
 */
function formatCivilDateTime(civilDate: Date, hours: number, minutes: number): string {
  const year = civilDate.getUTCFullYear();
  const month = pad(civilDate.getUTCMonth() + 1);
  const day = pad(civilDate.getUTCDate());
  return `${year}${month}${day}T${pad(hours)}${pad(minutes)}00`;
}

// Asia/Ho_Chi_Minh has been a fixed UTC+07:00 offset (no DST) since 1975,
// so converting a VN civil end-of-day to a UTC instant is a constant shift.
const VIETNAM_UTC_OFFSET_MS = 7 * 60 * 60 * 1000;

/**
 * Today's civil date in Vietnam, expressed as a UTC-midnight Date.
 */
function vietnamCivilToday(): Date {
  const vnNow = new Date(Date.now() + VIETNAM_UTC_OFFSET_MS);
  return new Date(Date.UTC(vnNow.getUTCFullYear(), vnNow.getUTCMonth(), vnNow.getUTCDate()));
}

/**
 * Parses an API date value into a UTC-midnight Date holding the civil date.
 * Date-only strings ("YYYY-MM-DD") already parse as UTC midnight, but a
 * datetime without an explicit zone ("YYYY-MM-DDTHH:mm:ss") would parse as
 * browser-local — so the date portion is extracted and rebuilt in UTC.
 */
function parseCivilDateOnly(value: string): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value);
  if (!match) return undefined;
  const date = new Date(Date.UTC(+match[1], +match[2] - 1, +match[3]));
  return isNaN(date.getTime()) ? undefined : date;
}

/**
 * Parses "HH:mm" or "HH:mm:ss" string into hours and minutes.
 */
export function parseTimeString(timeStr?: string): { hours: number; minutes: number } {
  if (!timeStr) return { hours: 7, minutes: 0 };
  const parts = timeStr.split(':').map((p) => parseInt(p, 10));
  return {
    hours: isNaN(parts[0]) ? 7 : parts[0],
    minutes: isNaN(parts[1]) ? 0 : parts[1],
  };
}

/**
 * Builds RFC 5545 iCalendar events from student enrollments and semester schedule information.
 */
export function buildScheduleIcsEvents(
  enrollments: Enrollment[],
  semester?: Semester,
  locale: 'vi' | 'en' = 'vi',
): IcsEvent[] {
  const events: IcsEvent[] = [];

  // Determine semester start date or fallback to today in Vietnam. All
  // downstream math stays in UTC fields so the exported times are Vietnam
  // wall-clock regardless of the viewer's own timezone.
  const baseStartDate =
    (semester?.startDate ? parseCivilDateOnly(semester.startDate) : undefined)
    ?? vietnamCivilToday();

  // Determine semester end date for recurrence UNTIL. The end date is a
  // Vietnam civil date; the last valid instant is 23:59:59+07:00, emitted
  // as the equivalent UTC instant (16:59:59Z of the same day).
  let untilUtcStr: string | undefined;
  const endDate = semester?.endDate ? parseCivilDateOnly(semester.endDate) : undefined;
  if (endDate) {
    const vnEndOfDayUtc = new Date(
      Date.UTC(
        endDate.getUTCFullYear(),
        endDate.getUTCMonth(),
        endDate.getUTCDate(),
        23, 59, 59,
      ) - VIETNAM_UTC_OFFSET_MS,
    );
    untilUtcStr = formatIcsUtcDateTime(vnEndOfDayUtc);
  }

  const activeEnrollments = enrollments.filter(
    (e) =>
      (isActiveEnrollment(e.status) || e.status === 'COMPLETED') &&
      !isCancelledSection(e),
  );

  for (const enrollment of activeEnrollments) {
    const section = enrollment.section;
    if (!section) continue;

    const courseCode = section.course?.code ?? 'COURSE';
    const courseName =
      locale === 'vi'
        ? section.course?.nameVi || section.course?.name || courseCode
        : section.course?.nameEn || section.course?.name || courseCode;
    const sectionNumber = section.sectionNumber || '';
    const credits = section.course?.credits ?? 0;

    const lecturerName =
      (section.lecturer as { fullName?: string } | undefined)?.fullName ??
      (section.lecturer?.user
        ? `${section.lecturer.user.lastName ?? ''} ${section.lecturer.user.firstName ?? ''}`.trim()
        : '');

    const schedules = section.schedules ?? [];
    for (let i = 0; i < schedules.length; i++) {
      const schedule = schedules[i];
      const dow = schedule.dayOfWeek === 0 ? 1 : schedule.dayOfWeek;
      const firstDate = findFirstOccurrenceDate(baseStartDate, dow);

      const start = parseTimeString(schedule.startTime);
      const end = parseTimeString(schedule.endTime);

      const building = schedule.classroom?.building;
      const room = schedule.classroom?.roomNumber;
      const location = [room ? (locale === 'vi' ? `Phòng ${room}` : `Room ${room}`) : '', building]
        .filter(Boolean)
        .join(', ');

      const descLines: string[] = [];
      if (locale === 'vi') {
        descLines.push(`Môn học: ${courseName} (${courseCode})`);
        descLines.push(`Lớp học phần: ${sectionNumber}`);
        if (credits > 0) descLines.push(`Số tín chỉ: ${credits}`);
        if (lecturerName) descLines.push(`Giảng viên: ${lecturerName}`);
        if (location) descLines.push(`Địa điểm: ${location}`);
      } else {
        descLines.push(`Course: ${courseName} (${courseCode})`);
        descLines.push(`Section: ${sectionNumber}`);
        if (credits > 0) descLines.push(`Credits: ${credits}`);
        if (lecturerName) descLines.push(`Lecturer: ${lecturerName}`);
        if (location) descLines.push(`Location: ${location}`);
      }

      const summary = `[${courseCode}] ${courseName} - ${sectionNumber}`;
      const uid = `schedule-${enrollment.id}-${schedule.id || i}@campuscore.edu`;

      events.push({
        uid,
        summary,
        description: descLines.join('\n'),
        location,
        dtstart: formatCivilDateTime(firstDate, start.hours, start.minutes),
        dtend: formatCivilDateTime(firstDate, end.hours, end.minutes),
        rrule: untilUtcStr ? `FREQ=WEEKLY;UNTIL=${untilUtcStr}` : undefined,
      });
    }
  }

  return events;
}

/**
 * Generates RFC 5545 compliant VCALENDAR document string.
 */
export function generateIcsCalendar(
  events: IcsEvent[],
  calendarName = 'CampusUTE Timetable',
): string {
  const dtstamp = formatIcsUtcDateTime(new Date());
  const lines: string[] = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CampusUTE//Academic Timetable//VI',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    `X-WR-CALNAME:${escapeIcsText(calendarName)}`,
    'X-WR-TIMEZONE:Asia/Ho_Chi_Minh',
  ];

  for (const event of events) {
    lines.push('BEGIN:VEVENT');
    lines.push(`UID:${event.uid}`);
    lines.push(`DTSTAMP:${dtstamp}`);
    lines.push(`DTSTART;TZID=Asia/Ho_Chi_Minh:${event.dtstart}`);
    lines.push(`DTEND;TZID=Asia/Ho_Chi_Minh:${event.dtend}`);
    if (event.rrule) {
      lines.push(`RRULE:${event.rrule}`);
    }
    lines.push(`SUMMARY:${escapeIcsText(event.summary)}`);
    if (event.description) {
      lines.push(`DESCRIPTION:${escapeIcsText(event.description)}`);
    }
    if (event.location) {
      lines.push(`LOCATION:${escapeIcsText(event.location)}`);
    }
    lines.push('STATUS:CONFIRMED');
    lines.push('TRANSP:OPAQUE');
    lines.push('END:VEVENT');
  }

  lines.push('END:VCALENDAR');
  return lines.join('\r\n') + '\r\n';
}

/**
 * Triggers a browser download of an .ics file from generated text content.
 */
export function downloadIcsFile(filename: string, icsContent: string): void {
  if (typeof window === 'undefined') return;
  const blob = new Blob([icsContent], { type: 'text/calendar;charset=utf-8;' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', filename.endsWith('.ics') ? filename : `${filename}.ics`);
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}
