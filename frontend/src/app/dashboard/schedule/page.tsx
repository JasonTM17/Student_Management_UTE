'use client';

import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import { Clock, MapPin } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { useRequireAuth } from '@/context/AuthContext';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { getLocalizedCourseLabel, getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { buildWeeklyGrid } from '@/lib/weekly-grid';
import { Enrollment, Semester } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

type DayAgendaItem = {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  startTime: string;
  endTime: string;
  dayOfWeek: number;
  building?: string;
  roomNumber?: string;
};

const dayNames = [
  'Sunday',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
];

/**
 * Token-based accent pairs that stay readable in light and dark themes. The
 * palette starts with the semantic primary pair and then uses hue pairs whose
 * text color swaps to a lighter step in dark mode.
 */
const weeklyAccents = [
  'bg-primary/15 text-primary border-primary/30',
  'bg-emerald-600/15 text-emerald-700 dark:text-emerald-300 border-emerald-600/30',
  'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
  'bg-sky-600/15 text-sky-700 dark:text-sky-300 border-sky-600/30',
  'bg-violet-600/15 text-violet-700 dark:text-violet-300 border-violet-600/30',
] as const;

/** Stable accent per course: char-code hash of the course code, deterministic across reloads. */
const accentForCourse = (courseCode: string): string => {
  let hash = 0;
  for (let index = 0; index < courseCode.length; index += 1) {
    hash += courseCode.charCodeAt(index);
  }
  return weeklyAccents[hash % weeklyAccents.length];
};

export default function SchedulePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  /** Loads semester choices and selects the best current term for the weekly agenda. */
  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
    const preferredSemesterId = pickPreferredSemesterId(response.data);
    if (preferredSemesterId) {
      setSelectedSemester((current) => current || preferredSemesterId);
    }
  }, []);

  /** Loads the student's enrollment records whose section schedules form the timetable. */
  const fetchEnrollments = useCallback(
    async (semesterId?: string) => {
      const response = await enrollmentsApi.getMyEnrollments(semesterId);
      setEnrollments(response);
    },
    [],
  );

  /** Loads schedule filters and maps failures to the page's truthful unavailable state. */
  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      await fetchSemesters();
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải bộ lọc thời khóa biểu.'
          : 'Schedule filters could not be loaded.',
      );
      setIsLoading(false);
    }
  }, [fetchSemesters, locale]);

  useEffect(() => {
    if (hasAccess) {
      void loadData();
    }
  }, [hasAccess, loadData]);

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    let cancelled = false;

    const run = async () => {
      setIsLoading(true);
      setError('');

      try {
        await fetchEnrollments(selectedSemester || undefined);
      } catch {
        if (!cancelled) {
          setError(
            locale === 'vi'
              ? 'Hiện chưa thể tải dữ liệu thời khóa biểu.'
              : 'Schedule data could not be loaded.',
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [fetchEnrollments, hasAccess, locale, selectedSemester]);

  /** Projects active enrollments into sorted weekday meeting cards for the selected term. */
  const agenda = useMemo(() => {
    const items: DayAgendaItem[] = [];

    enrollments
      .filter(
        (enrollment) =>
          enrollment.status === 'ENROLLED' ||
          enrollment.status === 'CONFIRMED' ||
          enrollment.status === 'PENDING',
      )
      .forEach((enrollment) => {
        const section = enrollment.section;
        section?.schedules?.forEach((schedule, index) => {
          items.push({
            id: `${enrollment.id}-${index}`,
            courseCode: section.course?.code ?? 'Course',
            courseName: section.course?.name ?? 'Unavailable',
            courseNameEn: section.course?.nameEn,
            courseNameVi: section.course?.nameVi,
            sectionNumber: section.sectionNumber,
            startTime: schedule.startTime,
            endTime: schedule.endTime,
            dayOfWeek: schedule.dayOfWeek === 0 ? 7 : schedule.dayOfWeek,
            building: schedule.classroom?.building,
            roomNumber: schedule.classroom?.roomNumber,
          });
        });
      });

    return items.sort((left, right) => {
      if (left.dayOfWeek !== right.dayOfWeek) {
        return left.dayOfWeek - right.dayOfWeek;
      }
      return (left.startTime || '').localeCompare(right.startTime || '');
    });
  }, [enrollments]);

  const agendaByDay = useMemo(() => {
    return agenda.reduce<Record<number, DayAgendaItem[]>>((groups, item) => {
      if (!groups[item.dayOfWeek]) {
        groups[item.dayOfWeek] = [];
      }

      groups[item.dayOfWeek].push(item);
      return groups;
    }, {});
  }, [agenda]);

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 't\u1ea5t c\u1ea3 h\u1ecdc k\u1ef3' : 'all terms',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all terms')
    );
  }, [locale, selectedSemester, semesters]);

  const localizedDayNames =
    locale === 'vi'
      ? ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy']
      : dayNames;

  const weeklyGrid = useMemo(() => buildWeeklyGrid(agenda), [agenda]);
  const gridDayLabels = [1, 2, 3, 4, 5, 6, 7].map(
    (day) => localizedDayNames[day % 7],
  );

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu sinh viên',
          title: 'Thời khóa biểu',
          description: `Giữ lịch học theo tuần cho ${selectedSemesterName} trong tầm tay cùng các tiện ích sinh viên cần dùng mỗi ngày.`,
          selectSemester: 'Chọn học kỳ cho thời khóa biểu',
          allSemesters: 'Tất cả học kỳ',
          openCourses: 'Mở môn học của tôi',
          loading: 'Đang tải thời khóa biểu',
          unavailableTitle: 'Thời khóa biểu chưa sẵn sàng',
          emptyTitle: 'Chưa có lớp học theo lịch',
          emptyDescription:
            'Khi lớp học phần được xác nhận, các buổi học sẽ xuất hiện tại đây theo lịch tuần.',
          browseSections: 'Xem lớp học phần',
          weeklyAgenda: 'Lịch học theo tuần',
          weeklyGrid: 'Lưới thời khóa biểu',
          upcomingClassList: 'Danh sách lớp học phần',
          noMeetings: 'Chưa có buổi học nào.',
          noSlot: 'Chưa có lịch dạy.',
          items: 'mục',
          item: 'mục',
          sectionPrefix: 'Lớp học phần',
          roomPending: 'Đang chờ thông tin phòng học',
        } as const
      : {
          eyebrow: 'Student area',
          title: 'Schedule',
          description: `Keep the weekly class agenda for ${selectedSemesterName} visible while the rest of the student area stays one click away.`,
          selectSemester: 'Select semester for schedule',
          allSemesters: 'All semesters',
          openCourses: 'Open my courses',
          loading: 'Loading schedule',
          unavailableTitle: 'Schedule unavailable',
          emptyTitle: 'No scheduled classes yet',
          emptyDescription:
            'Once sections are confirmed, their class meetings will appear here in a weekly agenda.',
          browseSections: 'Browse sections',
          weeklyAgenda: 'Weekly agenda',
          weeklyGrid: 'Weekly timetable grid',
          upcomingClassList: 'Class section list',
          noMeetings: 'No scheduled meetings.',
          noSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Class',
          roomPending: 'Room information pending',
        } as const;

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="min-w-0 sm:min-w-[300px]">
              <Select
                aria-label={copy.selectSemester}
                value={selectedSemester}
                onChange={(event) => setSelectedSemester(event.target.value)}
                options={[
                  { value: '', label: copy.allSemesters },
                  ...semesters.map((semester) => ({
                    value: semester.id,
                    label: getLocalizedName(locale, semester, semester.name),
                  })),
                ]}
              />
            </div>
            <LinkButton href="/dashboard/enrollments" variant="outline">
              {copy.openCourses}
            </LinkButton>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void loadData()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : agenda.length === 0 ? (
        <EmptyState
          icon={Clock}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
          }
        />
      ) : (
        <div className="grid min-w-0 items-start gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)] xl:gap-8">
          <Card variant="muted" className="min-w-0 overflow-hidden border-border/80 shadow-sm">
            <CardHeader className="border-b border-border/70 bg-card/50 pb-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <CardTitle className="text-xl font-bold flex items-center gap-2">
                  <Clock className="h-5 w-5 text-primary" />
                  {copy.weeklyAgenda}
                </CardTitle>
                <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {formatNumber(agenda.length)} {agenda.length === 1 ? copy.item : copy.items}
                </div>
              </div>
            </CardHeader>
            <CardContent className="p-4 sm:p-6">
              <div className="hidden md:block">
                <div
                  className="grid gap-2 overflow-x-auto pb-2"
                  style={{ gridTemplateColumns: '72px repeat(7, minmax(130px, 1fr))' }}
                  role="table"
                  aria-label={copy.weeklyGrid}
                >
                  <div style={{ gridColumn: 1, gridRow: 1 }} />
                  {gridDayLabels.map((dayName, dayIndex) => (
                    <div
                      key={`grid-${dayName}`}
                      className="rounded-lg bg-secondary/40 py-2 text-center text-xs font-bold uppercase tracking-wider text-foreground"
                      style={{ gridColumn: dayIndex + 2, gridRow: 1 }}
                    >
                      {dayName}
                    </div>
                  ))}
                  {weeklyGrid.slots.map((slot, slotIndex) => (
                    <Fragment key={`slot-${slot}`}>
                      <div
                        className="flex items-start justify-end pr-2 pt-2 text-xs font-semibold tabular-nums text-muted-foreground"
                        style={{ gridColumn: 1, gridRow: slotIndex + 2 }}
                      >
                        {slot}
                      </div>
                      {weeklyGrid.days.map((day, dayIndex) => {
                        const cellItems = weeklyGrid.cells[`${day}-${slot}`] ?? [];
                        if (cellItems.length === 0) {
                          return (
                            <div
                              key={`cell-${day}-${slot}`}
                              className="min-h-[105px] rounded-lg border border-border/70 bg-card/60 transition hover:border-primary/40 hover:bg-card/90"
                              style={{ gridColumn: dayIndex + 2, gridRow: slotIndex + 2 }}
                            />
                          );
                        }
                        // Every grid child is explicitly placed so a meeting
                        // covering several slot rows grows into one tall block
                        // without disturbing the other cells' placement.
                        const rowSpan = Math.max(...cellItems.map((item) => item.span ?? 1));
                        return (
                          <div
                            key={`cell-${day}-${slot}`}
                            className="flex min-w-0 flex-col gap-1.5"
                            style={{
                              gridColumn: dayIndex + 2,
                              gridRow: `${slotIndex + 2} / span ${rowSpan}`,
                            }}
                          >
                            {cellItems.map((item) => {
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
                              return (
                                <div
                                  key={item.id}
                                  className={`flex-1 rounded-lg border p-2 text-xs leading-tight transition hover:shadow-sm ${accentForCourse(item.courseCode)}`}
                                  title={`${item.courseCode} - ${item.startTime}-${item.endTime}`}
                                >
                                  <div className="flex items-center justify-between gap-1">
                                    <span className="font-mono text-[11px] font-bold">
                                      {item.courseCode}
                                    </span>
                                    <span className="rounded bg-background/60 px-1 py-0.5 text-[9px] font-medium text-foreground">
                                      {item.sectionNumber}
                                    </span>
                                  </div>
                                  <div
                                    className="mt-1 truncate text-[11px] font-semibold leading-snug text-foreground"
                                    title={courseName}
                                  >
                                    {courseName}
                                  </div>
                                  <div className="mt-1.5 flex flex-wrap items-center gap-1.5 text-[10px] text-muted-foreground">
                                    <span className="inline-flex items-center gap-1">
                                      <Clock className="h-3 w-3" />
                                      {item.startTime}-{item.endTime}
                                    </span>
                                    {item.roomNumber ? (
                                      <span className="inline-flex items-center gap-1 font-medium text-foreground/80">
                                        <MapPin className="h-3 w-3" />
                                        {item.building} {item.roomNumber}
                                      </span>
                                    ) : null}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        );
                      })}
                    </Fragment>
                  ))}
                </div>
              </div>

              <div className="grid gap-4 md:grid-cols-2 md:hidden">
                {weeklyGrid.days.map((dayOfWeek) => {
                  const dayName = localizedDayNames[dayOfWeek % 7];
                  const items = agendaByDay[dayOfWeek] ?? [];

                  return (
                    <div
                      key={dayName}
                      className="rounded-lg border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <h2 className="font-semibold text-foreground">{dayName}</h2>
                        <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                          {formatNumber(items.length)}{' '}
                          {items.length === 1 ? copy.item : copy.items}
                        </span>
                      </div>

                      {items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {copy.noMeetings}
                        </p>
                      ) : (
                        <div className="space-y-3">
                          {items.map((item) => (
                            <div
                              key={item.id}
                              className="rounded-lg border border-border/60 bg-secondary/30 px-4 py-3"
                            >
                              <div className="font-medium text-foreground">
                                {getLocalizedCourseLabel(
                                  locale,
                                  {
                                    code: item.courseCode,
                                    name: item.courseName,
                                    nameEn: item.courseNameEn,
                                    nameVi: item.courseNameVi,
                                  },
                                  `${item.courseCode} - ${item.courseName}`,
                                )}
                              </div>
                              <div className="mt-1 text-sm text-muted-foreground">
                                {copy.sectionPrefix} {item.sectionNumber}
                              </div>
                              <div className="mt-2 flex flex-wrap gap-3 text-sm text-muted-foreground">
                                <span className="inline-flex items-center gap-2">
                                  <Clock className="h-4 w-4" />
                                  {item.startTime} - {item.endTime}
                                </span>
                                {item.building && item.roomNumber ? (
                                  <span className="inline-flex items-center gap-2">
                                    <MapPin className="h-4 w-4" />
                                    {item.building} {item.roomNumber}
                                  </span>
                                ) : null}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          <Card variant="elevated" className="min-w-0">
            <CardHeader className="border-b border-border/70 pb-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg font-bold">{copy.upcomingClassList}</CardTitle>
                <span className="text-xs text-muted-foreground">
                  {formatNumber(agenda.length)} {agenda.length === 1 ? copy.item : copy.items}
                </span>
              </div>
            </CardHeader>
            <CardContent className="pt-6">
              <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
                {agenda.map((item) => (
                  <div
                    key={item.id}
                    className="rounded-lg border border-border/70 bg-card p-4 transition hover:border-primary/40 shadow-sm"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="font-mono text-xs font-bold text-primary">
                        {item.courseCode}
                      </span>
                      <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-foreground">
                        {copy.sectionPrefix} {item.sectionNumber}
                      </span>
                    </div>
                    <h4 className="mt-2 font-semibold text-foreground text-sm line-clamp-2">
                      {getLocalizedCourseLabel(
                        locale,
                        {
                          code: item.courseCode,
                          name: item.courseName,
                          nameEn: item.courseNameEn,
                          nameVi: item.courseNameVi,
                        },
                        `${item.courseCode} - ${item.courseName}`,
                      )}
                    </h4>
                    <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-muted-foreground border-t border-border/60 pt-2.5">
                      <span className="inline-flex items-center gap-1.5">
                        <Clock className="h-3.5 w-3.5 text-primary" />
                        {localizedDayNames[item.dayOfWeek % 7]} ({item.startTime} - {item.endTime})
                      </span>
                      {item.building && item.roomNumber ? (
                        <span className="inline-flex items-center gap-1.5 font-medium text-foreground/80">
                          <MapPin className="h-3.5 w-3.5 text-primary" />
                          {item.building} {item.roomNumber}
                        </span>
                      ) : (
                        <span className="text-muted-foreground text-[11px]">{copy.roomPending}</span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
