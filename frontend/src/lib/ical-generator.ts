import type { Enrollment, Semester } from '@/types/api';
import { isActiveEnrollment } from './enrollment-status';

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
 */
export function findFirstOccurrenceDate(baseStartDate: Date, dayOfWeek: number): Date {
  const targetJsDay = dayOfWeek === 1 ? 0 : dayOfWeek - 1; // 0 = Sun, 1 = Mon, ..., 6 = Sat
  const currentJsDay = baseStartDate.getDay();
  const diffDays = (targetJsDay - currentJsDay + 7) % 7;
  const result = new Date(baseStartDate);
  result.setDate(baseStartDate.getDate() + diffDays);
  return result;
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

  // Determine semester start date or fallback to current semester start
  let baseStartDate = semester?.startDate ? new Date(semester.startDate) : new Date();
  if (isNaN(baseStartDate.getTime())) {
    baseStartDate = new Date();
  }

  // Determine semester end date for recurrence UNTIL
  let untilUtcStr: string | undefined;
  if (semester?.endDate) {
    const endDate = new Date(semester.endDate);
    if (!isNaN(endDate.getTime())) {
      // Set to 23:59:59 on the end date
      endDate.setHours(23, 59, 59, 999);
      untilUtcStr = formatIcsUtcDateTime(endDate);
    }
  }

  const activeEnrollments = enrollments.filter(
    (e) => isActiveEnrollment(e.status) || e.status === 'COMPLETED',
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

      const startDate = new Date(firstDate);
      startDate.setHours(start.hours, start.minutes, 0, 0);

      const endDate = new Date(firstDate);
      endDate.setHours(end.hours, end.minutes, 0, 0);

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
        dtstart: formatIcsLocalDateTime(startDate),
        dtend: formatIcsLocalDateTime(endDate),
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
  calendarName = 'CampusCore Timetable',
): string {
  const dtstamp = formatIcsUtcDateTime(new Date());
  const lines: string[] = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//CampusCore UTE//Academic Timetable//VI',
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
