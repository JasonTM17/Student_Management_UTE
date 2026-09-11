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
  courseName?: string;
  courseNameEn?: string;
  courseNameVi?: string;
  /**
   * Number of consecutive slot rows covered by this meeting's
   * [startTime, endTime) window. Always populated by buildWeeklyGrid
   * (minimum 1); optional on the input shape so callers can pass raw
   * agenda items straight through.
   */
  span?: number;
}

export interface WeeklyGrid {
  /** CampusCore day numbers present in the grid: Sunday(1) through Saturday(7). */
  days: number[];
  /** Sorted unique start times; each becomes one grid row. */
  slots: string[];
  /** Items grouped by `${dayOfWeek}-${startTime}`; nothing is dropped. */
  cells: Record<string, WeeklyGridItem[]>;
}

/**
 * Builds a deterministic weekly grid from flat schedule items. The backend
 * stores Sunday as 1; legacy JS-shaped records may still send Sunday as 0.
 * Items with a dayOfWeek outside 1..7 or a missing start time are ignored.
 * Every accepted item appears exactly once in `cells`, augmented with a
 * computed `span` describing how many slot rows its [startTime, endTime)
 * window covers.
 */
export function buildWeeklyGrid(items: WeeklyGridItem[]): WeeklyGrid {
  const days = [1, 2, 3, 4, 5, 6, 7];
  const acceptedItems = items
    .map((item) => {
      const day = item.dayOfWeek === 0 ? 1 : item.dayOfWeek;
      const startTime = typeof item.startTime === 'string' ? item.startTime.trim() : '';
      const endTime = typeof item.endTime === 'string' ? item.endTime.trim() : '';
      return { ...item, dayOfWeek: day, startTime, endTime };
    })
    .filter((item) => days.includes(item.dayOfWeek) && Boolean(item.startTime));

  const slots = [...new Set(acceptedItems.map((item) => item.startTime))].sort((a, b) =>
    a.localeCompare(b),
  );
  const cells: Record<string, WeeklyGridItem[]> = {};

  acceptedItems.forEach((item) => {
    // Zero-padded "HH:MM" times order correctly as strings. A meeting counts
    // toward every slot row that starts at or after it and before it ends, so
    // a 07:00-09:30 block spanning a 08:00 slot renders two rows tall.
    const coveredSlots = slots.filter(
      (slot) => item.startTime <= slot && slot < item.endTime,
    ).length;
    const itemWithSpan: WeeklyGridItem = { ...item, span: Math.max(coveredSlots, 1) };
    const key = `${item.dayOfWeek}-${item.startTime}`;
    if (!cells[key]) {
      cells[key] = [];
    }
    cells[key].push(itemWithSpan);
  });

  return { days, slots, cells };
}
