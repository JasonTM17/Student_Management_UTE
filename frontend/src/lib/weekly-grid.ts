/**
 * Minimal structural view of one weekly class meeting. Matches the schedule
 * page's agenda projection without coupling the helper to its component types.
 */
export interface WeeklyGridItem {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  courseCode: string;
  sectionNumber: string;
  roomNumber?: string;
  /** Optional projection fields the schedule grid renders for context. */
  id?: string;
  building?: string;
}

export interface WeeklyGrid {
  /** ISO-style day numbers present in the grid, Monday(1) through Sunday(7). */
  days: number[];
  /** Sorted unique start times; each becomes one grid row. */
  slots: string[];
  /** Items grouped by `${dayOfWeek}-${startTime}`; nothing is dropped. */
  cells: Record<string, WeeklyGridItem[]>;
}

/**
 * Builds a deterministic weekly grid from flat schedule items. Items with a
 * dayOfWeek outside 1..7 or a missing start time are ignored; every accepted
 * item appears exactly once in `cells`.
 */
export function buildWeeklyGrid(items: WeeklyGridItem[]): WeeklyGrid {
  const days = [1, 2, 3, 4, 5, 6, 7];
  const acceptedItems = items
    .map((item) => {
      const day = item.dayOfWeek === 0 ? 7 : item.dayOfWeek;
      const startTime = typeof item.startTime === 'string' ? item.startTime.trim() : '';
      return { ...item, dayOfWeek: day, startTime };
    })
    .filter((item) => days.includes(item.dayOfWeek) && Boolean(item.startTime));

  const slots = [...new Set(acceptedItems.map((item) => item.startTime))].sort((a, b) =>
    a.localeCompare(b),
  );
  const cells: Record<string, WeeklyGridItem[]> = {};

  acceptedItems.forEach((item) => {
    const key = `${item.dayOfWeek}-${item.startTime}`;
    if (!cells[key]) {
      cells[key] = [];
    }
    cells[key].push(item);
  });

  return { days, slots, cells };
}
