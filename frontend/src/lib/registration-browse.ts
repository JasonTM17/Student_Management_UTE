/**
 * Pure client-side helpers behind the browse-first course registration page.
 * Kept free of React and i18n so `node --test` can execute them directly.
 */

/** Backend enum for how a catalog section relates to the student's curriculum. */
export type CurriculumRelevance = 'MANDATORY' | 'ELECTIVE' | 'OUTSIDE';

export type CurriculumFilter = 'ALL' | CurriculumRelevance;

export type SectionSortMode = 'code' | 'credits' | 'seats';

export type SeatTier = 'open' | 'low' | 'full';

/** One class meeting as delivered by GET /me/registration/sections. */
export interface RegistrationSectionSchedule {
  /** CampusCore convention, verbatim from the backend: 1=Sunday .. 7=Saturday. */
  dayOfWeek: number;
  startTime: string; // "HH:MM"
  endTime: string; // "HH:MM"
  room: string | null;
  lecturer: string | null;
}

/** Structural slice of a catalog section the filters and sorter depend on. */
export interface BrowseSection {
  id: string;
  sectionNumber: string;
  courseId: string;
  courseCode: string;
  courseName: string;
  credits: number;
  capacity: number;
  enrolledCount: number;
  remainingSeats: number;
  status: string;
  scheduleConflict: boolean;
  alreadyEnrolled: boolean;
  curriculumRelevance?: CurriculumRelevance | string;
  schedules?: RegistrationSectionSchedule[] | null;
}

export interface CourseGroup<TSection extends BrowseSection = BrowseSection> {
  courseId: string;
  courseCode: string;
  courseName: string;
  credits: number;
  sections: TSection[];
}

/**
 * Lowercases and strips Vietnamese diacritics so "điện toán" matches
 * "Dien Toan" and vice versa. NFD splits combined vowels into base + combining
 * marks; đ is composed, not accented, so it maps explicitly to d.
 */
export function normalizeVietnameseText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'd')
    .toLowerCase();
}

/** Everything one search box matches against: code, section number, name. */
export function sectionSearchHaystack(section: BrowseSection): string {
  return normalizeVietnameseText(
    `${section.courseCode} ${section.sectionNumber} ${section.courseName}`,
  );
}

/** Empty queries match everything; otherwise code OR name substring, diacritics-insensitive. */
export function matchesSectionSearch(section: BrowseSection, rawQuery: string): boolean {
  const query = normalizeVietnameseText(rawQuery.trim());
  if (!query) {
    return true;
  }
  return sectionSearchHaystack(section).includes(query);
}

export function matchesCurriculumFilter(
  section: BrowseSection,
  filter: CurriculumFilter,
): boolean {
  return filter === 'ALL' || section.curriculumRelevance === filter;
}

/** Green when more than 30% of capacity remains; amber for 1-30%; gray at zero. */
export function seatTier(section: Pick<BrowseSection, 'remainingSeats' | 'capacity'>): SeatTier {
  if (section.remainingSeats <= 0) {
    return 'full';
  }
  if (section.capacity > 0) {
    return section.remainingSeats / section.capacity > 0.3 ? 'open' : 'low';
  }
  // Unknown capacity with seats left: stay conservative instead of promising green.
  return 'low';
}

/** Groups sections by course and sorts groups plus each group's sections. */
export function sortSectionGroups<TSection extends BrowseSection>(
  groups: CourseGroup<TSection>[],
  mode: SectionSortMode,
): CourseGroup<TSection>[] {
  const sortSections = (left: TSection, right: TSection): number => {
    if (mode === 'credits') {
      return right.credits - left.credits
        || left.courseCode.localeCompare(right.courseCode)
        || left.sectionNumber.localeCompare(right.sectionNumber);
    }
    if (mode === 'seats') {
      return right.remainingSeats - left.remainingSeats
        || left.courseCode.localeCompare(right.courseCode)
        || left.sectionNumber.localeCompare(right.sectionNumber);
    }
    return left.courseCode.localeCompare(right.courseCode)
      || left.sectionNumber.localeCompare(right.sectionNumber);
  };
  return groups
    .map((group) => ({ ...group, sections: [...group.sections].sort(sortSections) }))
    .sort((left, right) => {
      if (mode === 'credits') {
        return right.credits - left.credits || left.courseCode.localeCompare(right.courseCode);
      }
      if (mode === 'seats') {
        const leftSeats = left.sections.reduce((sum, section) => sum + section.remainingSeats, 0);
        const rightSeats = right.sections.reduce((sum, section) => sum + section.remainingSeats, 0);
        return rightSeats - leftSeats || left.courseCode.localeCompare(right.courseCode);
      }
      return left.courseCode.localeCompare(right.courseCode);
    });
}

export interface CountdownParts {
  totalMs: number;
  days: number;
  hours: number;
  minutes: number;
  seconds: number;
}

/** Null once the deadline passed (or exactly now); otherwise whole-second parts. */
export function countdownParts(targetMs: number, nowMs: number): CountdownParts | null {
  const totalMs = Math.floor(targetMs - nowMs);
  if (totalMs <= 0) {
    return null;
  }
  const totalSeconds = Math.floor(totalMs / 1000);
  return {
    totalMs,
    days: Math.floor(totalSeconds / 86_400),
    hours: Math.floor((totalSeconds % 86_400) / 3_600),
    minutes: Math.floor((totalSeconds % 3_600) / 60),
    seconds: totalSeconds % 60,
  };
}

/** "05:12:55" — zero-padded clock digits used under the days unit. */
export function formatCountdownClock(parts: CountdownParts): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${pad(parts.hours)}:${pad(parts.minutes)}:${pad(parts.seconds)}`;
}

const pad2 = (value: number) => String(value).padStart(2, '0');

/**
 * Maps a stored day number into the grid convention shared with
 * weekly-grid.ts (1=Sunday .. 7=Saturday). Legacy JS-shaped rows may still
 * send Sunday as 0; the backend catalog emits 1..7 verbatim.
 */
export function normalizeDayOfWeek(day: number): number {
  return day === 0 ? 1 : day;
}

export interface TimeWindow {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
}

/**
 * True when the two meetings share a day and their [start, end) windows
 * overlap. "HH:MM" strings compare lexicographically, so no Date parsing.
 */
export function scheduleOverlaps(left: TimeWindow, right: TimeWindow): boolean {
  const leftDay = normalizeDayOfWeek(left.dayOfWeek);
  const rightDay = normalizeDayOfWeek(right.dayOfWeek);
  if (leftDay !== rightDay) {
    return false;
  }
  return left.startTime < right.endTime && right.startTime < left.endTime;
}

/**
 * First schedule of the candidate that collides with any existing meeting,
 * or null when the candidate slots cleanly into the week.
 */
export function findScheduleConflict(
  candidateSchedules: TimeWindow[] | null | undefined,
  existingSchedules: TimeWindow[] | null | undefined,
): RegistrationSectionSchedule | null {
  for (const candidate of candidateSchedules ?? []) {
    for (const existing of existingSchedules ?? []) {
      if (scheduleOverlaps(candidate, existing)) {
        return candidate as RegistrationSectionSchedule;
      }
    }
  }
  return null;
}

/** i18n keys for the registration-specific backend error codes. */
export type RegistrationErrorCopyKey =
  | 'sectionFull'
  | 'scheduleConflict'
  | 'creditCapExceeded'
  | 'prerequisiteUnmet'
  | 'corequisiteUnmet'
  | 'windowClosed'
  | 'cohortIneligible'
  | 'idempotencyRetry';

export type RegistrationErrorCopy = Record<RegistrationErrorCopyKey, string>;

const REGISTRATION_ERROR_KEYS: Partial<Record<string, RegistrationErrorCopyKey>> = {
  SECTION_FULL: 'sectionFull',
  SCHEDULE_CONFLICT: 'scheduleConflict',
  CREDIT_CAP_EXCEEDED: 'creditCapExceeded',
  PREREQUISITE_UNMET: 'prerequisiteUnmet',
  COREQUISITE_UNMET: 'corequisiteUnmet',
  WINDOW_CLOSED: 'windowClosed',
  COHORT_INELIGIBLE: 'cohortIneligible',
};

/**
 * Maps a backend error code to the registration-specific copy key, or null
 * when the caller should fall back to the shared campus-error mapper.
 * IDEMPOTENCY_* codes share one retry hint.
 */
export function resolveRegistrationActionMessage(
  code: string | null | undefined,
  copy: RegistrationErrorCopy,
): string | null {
  if (!code) {
    return null;
  }
  const directKey = REGISTRATION_ERROR_KEYS[code];
  if (directKey) {
    return copy[directKey];
  }
  if (code.startsWith('IDEMPOTENCY_')) {
    return copy.idempotencyRetry;
  }
  return null;
}

export { pad2 };
