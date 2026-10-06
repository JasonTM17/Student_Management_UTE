'use client';

import { useEffect, useRef, useState } from 'react';
import { Clock, GraduationCap, MapPin, Users } from 'lucide-react';
import {
  buildWeeklyGrid,
  type WeeklyGridItem,
} from '@/lib/weekly-grid';
import { getLocalizedCourseLabel, getLocalizedName } from '@/lib/academic-content';
import type { Locale } from '@/i18n/config';
import { cn } from '@/lib/utils';

/**
 * Day names in CampusCore convention: 1=Sunday, 2=Monday .. 7=Saturday.
 * Shared with the registration page so schedule chips and the timetable grid
 * never disagree about a day number.
 */
export const DAY_LABELS_VI: Record<number, string> = {
  1: 'Chủ Nhật',
  2: 'Thứ Hai',
  3: 'Thứ Ba',
  4: 'Thứ Tư',
  5: 'Thứ Năm',
  6: 'Thứ Sáu',
  7: 'Thứ Bảy',
};

export const DAY_LABELS_EN: Record<number, string> = {
  1: 'Sunday',
  2: 'Monday',
  3: 'Tuesday',
  4: 'Wednesday',
  5: 'Thursday',
  6: 'Friday',
  7: 'Saturday',
};

export const SHORT_DAY_LABELS_VI: Record<number, string> = {
  1: 'CN',
  2: 'T2',
  3: 'T3',
  4: 'T4',
  5: 'T5',
  6: 'T6',
  7: 'T7',
};

export const SHORT_DAY_LABELS_EN: Record<number, string> = {
  1: 'Sun',
  2: 'Mon',
  3: 'Tue',
  4: 'Wed',
  5: 'Thu',
  6: 'Fri',
  7: 'Sat',
};

// Standard academic week display order: Monday to Sunday
export const ORDERED_DAYS = [2, 3, 4, 5, 6, 7, 1];

export function dayLabel(locale: Locale, day: number): string {
  return locale === 'vi' ? DAY_LABELS_VI[day] : DAY_LABELS_EN[day];
}

export function shortDayLabel(locale: Locale, day: number): string {
  return locale === 'vi' ? SHORT_DAY_LABELS_VI[day] : SHORT_DAY_LABELS_EN[day];
}

/**
 * Unified enterprise academic theme for all class blocks across all roles.
 * Clean, consistent, and distraction-free institutional styling without rainbow colors.
 */
const UNIFIED_COURSE_ACCENT =
  'bg-primary/[0.04] dark:bg-primary/[0.08] text-foreground border-primary/25 dark:border-primary/35 hover:border-primary/60 hover:bg-primary/[0.08] dark:hover:bg-primary/[0.12]';

/**
 * Register-rail previews: the hovered section glows in the institutional gold
 * so it reads as "not yours yet" next to registered blocks.
 */
const PREVIEW_ACCENT =
  'bg-[color-mix(in_srgb,var(--registration-gold)_16%,transparent)] border-[var(--registration-gold)] text-foreground';

/** Collision preview: striped danger block shown before the student confirms. */
const CONFLICT_ACCENT =
  'bg-status-danger/10 border-status-danger/60 text-foreground bg-[repeating-linear-gradient(45deg,hsl(var(--status-danger)/0.14)_0px,hsl(var(--status-danger)/0.14)_6px,transparent_6px,transparent_12px)]';

/**
 * Compact register-rail chips: thin side borders plus a strong 3px left accent
 * bar (institutional timetable convention) so status reads at a glance even
 * when the cell is only ~40px wide.
 */
const COMPACT_COURSE_ACCENT =
  'border-border/60 border-l-primary bg-primary/[0.07] dark:bg-primary/[0.12] text-foreground hover:bg-primary/[0.12] dark:hover:bg-primary/[0.18]';
const COMPACT_PREVIEW_ACCENT =
  'border-[var(--registration-gold)]/50 border-l-[var(--registration-gold)] bg-[color-mix(in_srgb,var(--registration-gold)_14%,transparent)] text-foreground';
const COMPACT_CONFLICT_ACCENT =
  'border-status-danger/40 border-l-status-danger text-foreground bg-status-danger/10 bg-[repeating-linear-gradient(45deg,hsl(var(--status-danger)/0.12)_0px,hsl(var(--status-danger)/0.12)_6px,transparent_6px,transparent_12px)]';

export function accentForCourse(_courseCode?: string): string {
  return UNIFIED_COURSE_ACCENT;
}

/**
 * Section numbers may arrive fully qualified ("SE016-01") or bare ("01").
 * Return just the suffix for narrow chips; the course code already shows
 * beside it.
 */
function sectionSuffix(courseCode: string, sectionNumber?: string): string {
  if (!sectionNumber) return '';
  const escaped = courseCode.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return sectionNumber.replace(new RegExp(`^${escaped}[-\\s]?`), '') || sectionNumber;
}

export interface WeeklyGridLabels {
  /** Accessible name for the grid table (and mobile fallback). */
  gridAriaLabel: string;
  today: string;
  sectionPrefix: string;
  roomPending: string;
  noSlot: string;
  /** Hover hint appended to a block title, e.g. "click to view details". */
  blockHint: string;
  item: string;
  items: string;
  /**
   * Word after the enrollment figure in a block's footer meta
   * ("sinh viên" / "students"). Only lecturers pass this; student blocks show
   * `lecturerName` in the same slot instead.
   */
  studentsSuffix?: string;
}

/** Agenda projection: grid items plus the context fields blocks render. */
export type WeeklyGridAgendaItem = WeeklyGridItem & {
  lecturerName?: string;
  credits?: number;
  status?: string;
  /** Lecturer-context fields: carried through to the detail modal. */
  departmentName?: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  enrolledCount?: number;
  capacity?: number;
};

export interface WeeklyGridProps {
  items: WeeklyGridAgendaItem[];
  locale: Locale;
  labels: WeeklyGridLabels;
  /** CampusCore day number to badge as today; defaults to the real today. */
  todayDow?: number;
  /** Click handler for class blocks; receives the original agenda item. */
  onItemSelect?: (item: WeeklyGridAgendaItem) => void;
  /** Compact register-rail rendering: grid only, denser cells. */
  compact?: boolean;
  /** Item ids drawn with the gold preview treatment. */
  highlightedIds?: ReadonlySet<string>;
  /** Item ids drawn striped red as schedule conflicts. */
  conflictIds?: ReadonlySet<string>;
  className?: string;
}

export function WeeklyGrid({
  items,
  locale,
  labels,
  todayDow,
  onItemSelect,
  compact = false,
  highlightedIds,
  conflictIds,
  className,
}: WeeklyGridProps) {
  const jsDay = new Date().getDay();
  const effectiveTodayDow = todayDow ?? (jsDay === 0 ? 1 : jsDay + 1);
  const weeklyGrid = buildWeeklyGrid(items);

  /**
   * The compact register rail lives in a narrow sidebar whose width depends
   * on the page grid, not the viewport — so measure the container itself.
   * Below ~480px the 7-column grid squeezes blocks under readable width, so
   * fall back to the day-grouped agenda list.
   */
  const railRef = useRef<HTMLDivElement>(null);
  const [railNarrow, setRailNarrow] = useState(
    () => typeof window !== 'undefined' && window.matchMedia('(max-width: 639px)').matches,
  );
  useEffect(() => {
    if (!compact || typeof ResizeObserver === 'undefined') return undefined;
    const el = railRef.current;
    if (!el) return undefined;
    const observer = new ResizeObserver(([entry]) => {
      setRailNarrow(entry.contentRect.width < 480);
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, [compact]);
  const compactNarrow = compact && railNarrow;
  const agendaByDay = items.reduce<Record<number, WeeklyGridAgendaItem[]>>((groups, item) => {
    const day = item.dayOfWeek === 0 ? 1 : item.dayOfWeek;
    if (!groups[day]) {
      groups[day] = [];
    }
    groups[day].push(item);
    return groups;
  }, {});

  const blockTone = (item: WeeklyGridItem): string => {
    if (conflictIds?.has(item.id ?? '')) {
      return compact ? COMPACT_CONFLICT_ACCENT : CONFLICT_ACCENT;
    }
    if (highlightedIds?.has(item.id ?? '')) {
      return compact ? COMPACT_PREVIEW_ACCENT : PREVIEW_ACCENT;
    }
    return compact ? COMPACT_COURSE_ACCENT : accentForCourse(item.courseCode);
  };

  return (
    <div className={cn('min-w-0', className)} ref={railRef}>
      {/* Desktop / Tablet Weekly Grid */}
      <div className={compact ? (compactNarrow ? 'hidden' : 'block') : 'hidden md:block'}>
        <div
          className={cn(
            'grid overflow-x-auto pb-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
            compact ? 'gap-1' : 'gap-2',
          )}
          tabIndex={0}
          style={{
            gridTemplateColumns: compact
              ? '34px repeat(7, minmax(0, 1fr))'
              : '72px repeat(7, minmax(130px, 1fr))',
          }}
          role="table"
          aria-label={labels.gridAriaLabel}
        >
          <div role="row" className="contents">
          <div role="columnheader" aria-label={locale === 'vi' ? 'Giờ' : 'Time'} style={{ gridColumn: 1, gridRow: 1 }} />
          {ORDERED_DAYS.map((dayNum, colIndex) => {
            const dayName = dayLabel(locale, dayNum);
            const isToday = dayNum === effectiveTodayDow;
            return (
              <div
                key={`grid-header-${dayNum}`}
                role="columnheader"
                aria-colindex={colIndex + 2}
                className={`rounded-lg py-2.5 px-1 text-center transition-all ${
                  isToday
                    ? 'border border-primary/50 bg-primary/10 text-primary shadow-sm font-black'
                    : 'bg-secondary/40 text-foreground font-bold'
                } ${compact ? 'text-[11px] uppercase tracking-wide py-1.5' : ''}`}
                style={{ gridColumn: colIndex + 2, gridRow: 1 }}
              >
                <div className={compact ? '' : 'text-xs uppercase tracking-wider'}>
                  {compact ? shortDayLabel(locale, dayNum) : dayName}
                </div>
                {isToday && !compact ? (
                  <span className="mt-0.5 inline-block rounded-md bg-primary px-2 py-0.5 text-[10px] font-bold text-primary-foreground">
                    {labels.today}
                  </span>
                ) : null}
              </div>
            );
          })}

          </div>
          {weeklyGrid.slots.map((slot, slotIndex) => (
            <div role="row" aria-rowindex={slotIndex + 2} className="contents" key={`slot-${slot}`}>
              <div
                className={`flex items-start justify-end text-xs font-semibold tabular-nums text-muted-foreground ${
                  compact ? 'pr-1 pt-1 text-[10px]' : 'pr-2 pt-2'
                }`}
                role="rowheader"
                style={{ gridColumn: 1, gridRow: slotIndex + 2 }}
              >
                {slot}
              </div>
              {ORDERED_DAYS.map((dayNum, colIndex) => {
                const cellItems = weeklyGrid.cells[`${dayNum}-${slot}`] ?? [];
                const isToday = dayNum === effectiveTodayDow;

                if (cellItems.length === 0) {
                  // Skip rendering cell if this time slot is covered by an earlier meeting spanning downwards
                  const isCoveredByEarlier = items.some(
                    (m) =>
                      (m.dayOfWeek === 0 ? 1 : m.dayOfWeek) === dayNum
                      && m.startTime < slot
                      && slot < m.endTime,
                  );
                  if (isCoveredByEarlier) {
                    return null;
                  }

                  return (
                    <div
                      key={`cell-${dayNum}-${slot}`}
                      role="cell"
                      aria-colindex={colIndex + 2}
                      className={cn(
                        'rounded-lg border border-dashed border-border/70 transition hover:border-primary/40 hover:bg-card/90',
                        compact
                          ? 'min-h-[40px]'
                          : 'min-h-[105px]',
                        isToday ? 'bg-primary/[0.02] border-primary/20' : 'bg-card/60',
                      )}
                      style={{ gridColumn: colIndex + 2, gridRow: slotIndex + 2 }}
                    />
                  );
                }

                const rowSpan = Math.max(...cellItems.map((item) => item.span ?? 1));
                return (
                  <div
                    key={`cell-${dayNum}-${slot}`}
                      role="cell"
                      aria-colindex={colIndex + 2}
                    className="flex min-w-0 flex-col gap-1.5"
                    aria-rowspan={rowSpan}
                    style={{
                      gridColumn: colIndex + 2,
                      gridRow: `${slotIndex + 2} / span ${rowSpan}`,
                    }}
                  >
                    {cellItems.map((item) => {
                      const agendaItem = item as WeeklyGridAgendaItem;
                      const courseName = getLocalizedName(
                        locale,
                        {
                          code: item.courseCode,
                          name: item.courseName,
                          nameEn: item.courseNameEn,
                          nameVi: item.courseNameVi,
                        },
                        item.courseName,
                      );

                      const isMorning = (item.startTime || '').localeCompare('12:00') < 0;
                      const isAfternoon =
                        (item.startTime || '').localeCompare('12:00') >= 0
                        && (item.startTime || '').localeCompare('18:00') < 0;
                      const shiftBadge = isMorning
                        ? (locale === 'vi' ? 'Sáng' : 'AM')
                        : isAfternoon
                        ? (locale === 'vi' ? 'Chiều' : 'PM')
                        : (locale === 'vi' ? 'Tối' : 'Eve');

                      if (compact) {
                        const room = item.roomNumber
                          ? `${item.building ? `${item.building}-` : ''}${item.roomNumber}`
                          : '';
                        // Show only the section suffix so the chip stays
                        // narrow enough to read.
                        const sec = sectionSuffix(item.courseCode, item.sectionNumber);
                        return (
                          <div
                            key={item.id}
                            role={onItemSelect ? 'button' : undefined}
                            tabIndex={onItemSelect ? 0 : undefined}
                            onKeyDown={onItemSelect ? (event) => {
                              if (event.key === 'Enter' || event.key === ' ') {
                                event.preventDefault();
                                onItemSelect(item);
                              }
                            } : undefined}
                            onClick={onItemSelect ? () => onItemSelect(item) : undefined}
                            className={cn(
                              'min-w-0 overflow-hidden rounded-md border-y border-r border-l-[3px] px-1 py-1 text-[11px] leading-tight transition-colors hover:shadow-sm',
                              onItemSelect ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring' : '',
                              blockTone(item),
                            )}
                            title={`${courseName} · ${item.courseCode}-${item.sectionNumber} · ${item.startTime}-${item.endTime}${room ? ` · ${room}` : ''}${labels.blockHint ? ` (${labels.blockHint})` : ''}`}
                          >
                            <div className="flex min-w-0 items-center gap-0.5">
                              <span className="min-w-0 truncate font-mono text-[11px] font-extrabold tracking-tight">
                                {item.courseCode}
                              </span>
                              <span className="shrink-0 rounded-sm bg-background/80 px-0.5 text-[9px] font-semibold text-foreground border border-border/40">
                                {sec}
                              </span>
                            </div>
                            <div className="mt-0.5 truncate whitespace-nowrap text-[10px] font-medium tabular-nums text-muted-foreground">
                              {item.startTime}
                              {room ? ` · ${room}` : ''}
                            </div>
                          </div>
                        );
                      }

                      return (
                        <div
                          key={item.id}
                          role={onItemSelect ? 'button' : undefined}
                          tabIndex={onItemSelect ? 0 : undefined}
                          onKeyDown={onItemSelect ? (event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              onItemSelect(item);
                            }
                          } : undefined}
                          onClick={onItemSelect ? () => onItemSelect(item) : undefined}
                          className={cn(
                            'flex-1 min-h-0 overflow-hidden rounded-lg border p-2.5 text-xs leading-tight transition-all hover:scale-[1.01] hover:shadow-md',
                            onItemSelect ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring' : '',
                            blockTone(item),
                          )}
                          title={`${item.courseCode} - ${item.startTime}-${item.endTime}${labels.blockHint ? ` (${labels.blockHint})` : ''}`}
                        >
                          <div className="flex items-center justify-between gap-1">
                            <span className="font-mono text-xs font-extrabold tracking-wide">
                              {item.courseCode}
                            </span>
                            <div className="flex items-center gap-1">
                              <span className="rounded bg-background/80 px-1.5 py-0.5 text-[10px] font-semibold text-foreground border border-border/40">
                                {sectionSuffix(item.courseCode, item.sectionNumber)}
                              </span>
                              <span className="rounded bg-primary/10 text-primary px-1.5 py-0.5 text-[10px] font-bold">
                                {shiftBadge}
                              </span>
                            </div>
                          </div>
                          <div
                            className="mt-1.5 font-bold text-xs leading-snug line-clamp-2 text-foreground"
                            title={courseName}
                          >
                            {courseName}
                          </div>
                          <div className="mt-2 space-y-1 border-t border-current/10 pt-1.5 text-[11px] text-muted-foreground">
                            <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                              <span className="inline-flex items-center gap-1 font-medium whitespace-nowrap">
                                <Clock className="h-3 w-3 text-primary" />
                                {item.startTime}-{item.endTime}
                              </span>
                              {item.roomNumber ? (
                                <span className="inline-flex items-center gap-1 font-bold text-foreground whitespace-nowrap">
                                  <MapPin className="h-3 w-3 text-primary" />
                                  {item.building ? `${item.building}-` : ''}
                                  {item.roomNumber}
                                </span>
                              ) : null}
                            </div>
                            {/* One context line only, and only in tall single-item
                                cells — a second line in a 1-slot or stacked cell
                                overflows the block. Lecturers see enrollment,
                                students see their lecturer's name. */}
                            {rowSpan >= 2 && cellItems.length === 1 ? (
                              agendaItem.lecturerName ? (
                                <div
                                  className="flex items-center gap-1 truncate text-foreground/80 font-medium"
                                  title={agendaItem.lecturerName}
                                >
                                  <GraduationCap className="h-3 w-3 shrink-0 text-primary" />
                                  <span className="truncate">{agendaItem.lecturerName}</span>
                                </div>
                              ) : agendaItem.enrolledCount !== undefined ? (
                                <div className="flex items-center gap-1 truncate text-foreground/80 font-medium">
                                  <Users className="h-3 w-3 shrink-0 text-primary" />
                                  <span className="truncate">
                                    {agendaItem.enrolledCount}
                                    {labels.studentsSuffix ? ` ${labels.studentsSuffix}` : ''}
                                  </span>
                                </div>
                              ) : null
                            ) : null}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      </div>

      {/* Compact rail in a narrow container: dense day-grouped agenda instead
          of a 7-column grid that would squeeze each block below readable
          width. */}
      {compact ? (
        <div className={cn('space-y-2', compactNarrow ? '' : 'hidden')} role="list" aria-label={labels.gridAriaLabel}>
          {ORDERED_DAYS.map((dayNum) => {
            const dayItems = agendaByDay[dayNum] ?? [];
            if (dayItems.length === 0) return null;
            const isToday = dayNum === effectiveTodayDow;
            return (
              <div
                key={`compact-day-${dayNum}`}
                className={cn(
                  'rounded-lg border px-2.5 py-2',
                  isToday ? 'border-primary/50 bg-primary/[0.04]' : 'border-border/60 bg-card/60',
                )}
              >
                <div className="mb-1 flex items-center gap-1.5">
                  <span className={cn('text-[11px] font-bold uppercase tracking-wide', isToday ? 'text-primary' : 'text-muted-foreground')}>
                    {shortDayLabel(locale, dayNum)}
                  </span>
                  {isToday ? (
                    <span className="rounded bg-primary px-1 py-px text-[10px] font-bold text-primary-foreground">
                      {labels.today}
                    </span>
                  ) : null}
                </div>
                <div className="space-y-1">
                  {dayItems.map((item) => (
                    <div
                      key={item.id}
                      role={onItemSelect ? 'button' : 'listitem'}
                      tabIndex={onItemSelect ? 0 : undefined}
                      onKeyDown={onItemSelect ? (event) => {
                        if (event.key === 'Enter' || event.key === ' ') {
                          event.preventDefault();
                          onItemSelect(item);
                        }
                      } : undefined}
                      onClick={onItemSelect ? () => onItemSelect(item) : undefined}
                      className={cn(
                        'flex min-w-0 items-center gap-2 rounded-md border-y border-r border-l-[3px] px-2 py-1 text-[11px] leading-tight',
                        onItemSelect ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring' : '',
                        blockTone(item),
                      )}
                    >
                      <span className="min-w-0 truncate font-mono font-bold">
                        {item.courseCode}
                      </span>
                      <span className="shrink-0 rounded-sm border border-border/40 bg-background/80 px-0.5 text-[10px] font-semibold">
                        {sectionSuffix(item.courseCode, item.sectionNumber)}
                      </span>
                      <span className="ml-auto inline-flex shrink-0 items-center gap-1 whitespace-nowrap tabular-nums text-muted-foreground">
                        <Clock className="h-3 w-3 text-primary" aria-hidden="true" />
                        {item.startTime}-{item.endTime}
                      </span>
                      {item.roomNumber ? (
                        <span className="inline-flex shrink-0 items-center gap-0.5 whitespace-nowrap font-semibold text-foreground">
                          <MapPin className="h-3 w-3 text-primary" aria-hidden="true" />
                          {item.building ? `${item.building}-` : ''}
                          {item.roomNumber}
                        </span>
                      ) : null}
                    </div>
                  ))}
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="grid gap-4 md:hidden">
          {ORDERED_DAYS.map((dayNum) => {
            const dayName = dayLabel(locale, dayNum);
            const dayItems = agendaByDay[dayNum] ?? [];
            const isToday = dayNum === effectiveTodayDow;

            return (
              <div
                key={`mobile-day-${dayNum}`}
                className={cn(
                  'rounded-lg border p-4 transition-all',
                  isToday ? 'border-primary/50 bg-primary/[0.04]' : 'border-border/70 bg-card',
                )}
              >
                <div className="mb-3 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <h3 className="font-bold text-foreground text-sm">{dayName}</h3>
                    {isToday ? (
                      <span className="rounded-md bg-primary px-2 py-0.5 text-[10px] font-bold text-primary-foreground">
                        {labels.today}
                      </span>
                    ) : null}
                  </div>
                  <span className="text-xs text-muted-foreground font-semibold">
                    {dayItems.length} {dayItems.length === 1 ? labels.item : labels.items}
                  </span>
                </div>

                {dayItems.length === 0 ? (
                  <p className="text-xs text-muted-foreground italic">{labels.noSlot}</p>
                ) : (
                  <div className="space-y-2.5">
                    {dayItems.map((item) => (
                      <div
                        key={item.id}
                        role={onItemSelect ? 'button' : undefined}
                          tabIndex={onItemSelect ? 0 : undefined}
                          onKeyDown={onItemSelect ? (event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault();
                              onItemSelect(item);
                            }
                          } : undefined}
                          onClick={onItemSelect ? () => onItemSelect(item) : undefined}
                        className={cn(
                          'rounded-lg border p-3 text-xs transition hover:shadow-sm',
                          onItemSelect ? 'cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring' : '',
                          blockTone(item),
                        )}
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-mono font-bold">{item.courseCode}</span>
                          <span className="rounded bg-background/60 px-1.5 py-0.5 text-[11px]">
                            {labels.sectionPrefix} {item.sectionNumber}
                          </span>
                        </div>
                        <div className="mt-1 font-semibold text-foreground text-xs">
                          {getLocalizedCourseLabel(
                            locale,
                            {
                              code: item.courseCode,
                              name: item.courseName,
                              nameEn: item.courseNameEn,
                              nameVi: item.courseNameVi,
                            },
                            item.courseName,
                          )}
                        </div>
                        <div className="mt-2 flex items-center gap-3 text-[11px] text-muted-foreground">
                          <span className="inline-flex items-center gap-1">
                            <Clock className="h-3 w-3" />
                            {item.startTime} - {item.endTime}
                          </span>
                          {item.roomNumber ? (
                            <span className="inline-flex items-center gap-1 font-medium text-foreground">
                              <MapPin className="h-3 w-3" />
                              {item.building ? `${item.building}-` : ''}
                              {item.roomNumber}
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 italic">
                              <MapPin className="h-3 w-3" />
                              {labels.roomPending}
                            </span>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
