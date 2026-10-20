'use client';

import { useMemo, useRef, useState } from 'react';
import { BookOpen, CalendarDays, RefreshCw } from 'lucide-react';
import { useI18n } from '@/i18n';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { STATIC_STALE_TIME_MS, useApiQuery } from '@/lib/query';
import { getLocalizedName } from '@/lib/academic-content';
import { isActiveEnrollment } from '@/lib/enrollment-status';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { WeeklyGrid, dayLabel, ORDERED_DAYS, type WeeklyGridAgendaItem } from '@/components/schedule/weekly-grid';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select } from '@/components/ui/select';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';

/** Reads the signed-in student's active registrations for one available term,
 * independently of registration windows; shares scoped mutation cache and renders all meetings. */
export function RegisteredCoursesPanel({
  userId,
  active,
  initialSemesterId,
  selectedSemesterId,
  onSemesterChange,
  onRefresh,
}: {
  userId: string;
  active: boolean;
  initialSemesterId?: string;
  selectedSemesterId?: string;
  onSemesterChange?: (semesterId: string) => void;
  onRefresh?: (semesterId: string) => Promise<void>;
}) {
  const { locale, messages, formatNumber, formatDate } = useI18n();
  const copy = messages.courseRegistration;
  const [selectedSemester, setSelectedSemester] = useState('');
  const [refreshing, setRefreshing] = useState(false);
  const timetableRef = useRef<HTMLDivElement>(null);
  const semestersQuery = useApiQuery(
    ['registration', 'detail-semesters', userId],
    async () => (await semestersApi.getAll()).data ?? [],
    { enabled: active, staleTime: STATIC_STALE_TIME_MS },
  );
  const semesters = semestersQuery.data ?? [];
  const preferred = semesters.some((term) => term.id === initialSemesterId)
    ? initialSemesterId ?? ''
    : pickPreferredSemesterId(semesters);
  const requestedSemester = selectedSemesterId ?? selectedSemester;
  const semesterId = semesters.some((term) => term.id === requestedSemester) ? requestedSemester : preferred;
  const enrollmentsQuery = useApiQuery(
    ['registration', 'enrollments', semesterId, userId],
    () => enrollmentsApi.getMyEnrollments(semesterId),
    { enabled: active && Boolean(semesterId), staleTime: 2 * 60_000 },
  );
  const registered = useMemo(
    () => (enrollmentsQuery.data ?? []).filter(
      (item) => item.semesterId === semesterId && isActiveEnrollment(item.status),
    ),
    [enrollmentsQuery.data, semesterId],
  );
  const credits = registered.reduce((total, item) => total + (item.section?.course?.credits ?? 0), 0);
  const creditsKnown = registered.every((item) => item.section?.course?.credits !== undefined);
  const missingSchedules = registered.filter(
    (item) => !item.id.startsWith('pending:') && !item.section?.schedules?.length,
  ).length;
  const agenda = useMemo<WeeklyGridAgendaItem[]>(() => registered.flatMap((item) => {
    const section = item.section;
    const lecturer = section?.lecturer?.user;
    const lecturerName = lecturer ? [lecturer.lastName, lecturer.firstName].filter(Boolean).join(' ') : undefined;
    return (section?.schedules ?? []).map((schedule, index) => ({
      id: `${item.id}:${index}`,
      dayOfWeek: schedule.dayOfWeek === 0 ? 1 : schedule.dayOfWeek,
      startTime: schedule.startTime,
      endTime: schedule.endTime,
      courseCode: section?.course?.code ?? copy.detailsUnknown,
      courseName: section?.course?.name,
      courseNameEn: section?.course?.nameEn,
      courseNameVi: section?.course?.nameVi,
      sectionNumber: section?.sectionNumber ?? copy.detailsUnknown,
      credits: section?.course?.credits,
      lecturerName,
      roomNumber: schedule.classroom?.roomNumber,
      building: schedule.classroom?.building,
    }));
  }), [registered, copy.detailsUnknown]);

  const refresh = async () => {
    setRefreshing(true);
    try {
      await enrollmentsQuery.refetch();
      await onRefresh?.(semesterId);
    } finally {
      setRefreshing(false);
    }
  };

  if (semestersQuery.isLoading) return <LoadingState />;
  if (semestersQuery.isError) return (
    <ErrorState title={copy.registeredLoadFailed} description={copy.registeredLoadFailedDescription}
      onRetry={() => void semestersQuery.refetch()} />
  );
  if (!semesters.length) return <EmptyState icon={CalendarDays} title={copy.semestersEmptyTitle} description={copy.semestersEmptyDescription} />;

  return (
    <div className="space-y-5">
      <Card>
        <CardContent className="flex flex-col gap-4 p-4 sm:flex-row sm:items-end">
          <div className="w-full sm:max-w-sm">
            <Select label={copy.semester} value={semesterId}
              onChange={(event) => {
                if (onSemesterChange) onSemesterChange(event.target.value);
                else setSelectedSemester(event.target.value);
              }}
              options={semesters.map((term) => ({ value: term.id, label: getLocalizedName(locale, term, term.name) }))} />
          </div>
          <p className="min-w-0 flex-1 text-sm leading-6 text-muted-foreground">{copy.registeredDescription}</p>
          <Button type="button" variant="outline" disabled={enrollmentsQuery.isFetching || refreshing}
            onClick={() => void refresh()} className="gap-2">
            <RefreshCw className="h-4 w-4" aria-hidden="true" />{messages.common.actions.refresh}
          </Button>
        </CardContent>
      </Card>
      {enrollmentsQuery.isError && enrollmentsQuery.data ? (
        <div role="alert" className="space-y-2 rounded-lg border border-status-warning/40 bg-status-warning/10 p-3 text-sm">
          <p>{copy.registeredRefreshFailed}</p>
          <Button type="button" variant="outline" disabled={refreshing} onClick={() => void refresh()}>{messages.common.actions.retry}</Button>
        </div>
      ) : null}
      {enrollmentsQuery.isLoading ? <LoadingState /> : enrollmentsQuery.isError && !enrollmentsQuery.data ? (
        <ErrorState title={copy.registeredLoadFailed} description={copy.registeredLoadFailedDescription}
          onRetry={() => void enrollmentsQuery.refetch()} />
      ) : registered.length === 0 ? (
        <EmptyState icon={BookOpen} title={copy.registeredEmptyTitle} description={copy.registeredEmptyDescription} />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3 rounded-lg border border-primary/20 bg-primary/5 px-4 py-3" role="status">
            <BookOpen className="h-5 w-5 text-primary" aria-hidden="true" />
            <span className="font-semibold">{copy.groupSectionCount.replace('{count}', formatNumber(registered.length))}</span>
            <span className="text-sm">{creditsKnown ? `${formatNumber(credits)} ${copy.creditsUnit}` : copy.detailsCreditsUnavailable}</span>
            <Button type="button" variant="outline" size="sm" className="sm:ml-auto" onClick={() => {
              timetableRef.current?.scrollIntoView({ block: 'start' });
              timetableRef.current?.focus({ preventScroll: true });
            }}>{copy.registeredTimetableJump}</Button>
          </div>
          <ul data-testid="registered-courses-list" className="grid min-w-0 gap-4 lg:grid-cols-2">
            {registered.map((item) => {
              const section = item.section;
              const course = section?.course;
              const lecturer = section?.lecturer?.user;
              const lecturerName = lecturer ? [lecturer.lastName, lecturer.firstName].filter(Boolean).join(' ') : '';
              const processing = item.id.startsWith('pending:');
              const enrolledDate = new Date(item.enrolledAt);
              const schedules = [...(section?.schedules ?? [])].sort((a, b) =>
                ORDERED_DAYS.indexOf(a.dayOfWeek === 0 ? 1 : a.dayOfWeek) - ORDERED_DAYS.indexOf(b.dayOfWeek === 0 ? 1 : b.dayOfWeek)
                || a.startTime.localeCompare(b.startTime),
              );
              return (
                <li key={item.id} className="min-w-0 rounded-xl border border-border bg-card p-4 sm:p-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="break-all rounded-md bg-primary/10 px-2 py-1 font-mono text-xs font-semibold text-primary">{course?.code ?? copy.detailsUnknown}</span>
                    <span className="rounded-md bg-secondary px-2 py-1 text-xs font-semibold">{processing ? copy.detailsProcessing : messages.common.statuses[item.status]}</span>
                  </div>
                  <h2 className="mt-3 break-words text-base font-semibold">{getLocalizedName(locale, course, copy.detailsUnknown)}</h2>
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
                    <div className="min-w-0"><dt className="text-xs text-muted-foreground">{copy.columns.section}</dt><dd className="mt-1 break-all font-medium">{section?.sectionNumber ?? copy.detailsUnknown}</dd></div>
                    <div><dt className="text-xs text-muted-foreground">{copy.creditsUnit}</dt><dd className="mt-1 font-medium">{course?.credits === undefined ? copy.detailsUnknown : formatNumber(course.credits)}</dd></div>
                    <div className="min-w-0"><dt className="text-xs text-muted-foreground">{copy.detailsLecturer}</dt><dd className="mt-1 break-words">{lecturerName || copy.detailsUnknown}</dd></div>
                    <div><dt className="text-xs text-muted-foreground">{copy.detailsEnrolledAt}</dt><dd className="mt-1">{Number.isNaN(enrolledDate.getTime()) ? copy.detailsUnknown : formatDate(enrolledDate)}</dd></div>
                  </dl>
                  <div className="mt-4 border-t border-border pt-3">
                    <h3 className="mb-2 text-xs font-semibold text-muted-foreground">{copy.detailsMeetings}</h3>
                    {schedules.length ? <ul className="space-y-2 text-sm">{schedules.map((schedule, index) => (
                      <li key={schedule.id ?? index} className="rounded-lg bg-secondary/40 px-3 py-2">
                        <p className="font-medium">{dayLabel(locale, schedule.dayOfWeek === 0 ? 1 : schedule.dayOfWeek)} · {schedule.startTime.slice(0, 5)}–{schedule.endTime.slice(0, 5)}</p>
                        <p className="mt-0.5 break-words text-xs text-muted-foreground">
                          {schedule.classroom?.roomNumber ? copy.scheduleChipRoom.replace('{room}', schedule.classroom.roomNumber) : copy.detailsRoomPending}
                          {schedule.classroom?.building ? ` · ${schedule.classroom.building}` : ''}
                        </p>
                      </li>
                    ))}</ul> : <p className="text-sm text-muted-foreground">{processing ? copy.detailsProcessing : copy.scheduleNone}</p>}
                  </div>
                </li>
              );
            })}
          </ul>
          <Card ref={timetableRef} tabIndex={-1} data-testid="registered-timetable"
            className="min-w-0 scroll-mt-[calc(var(--portal-header-height)+1rem)] overflow-hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <CardHeader><CardTitle>{copy.registeredTimetableTitle}</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {missingSchedules > 0 ? <p className="text-sm text-muted-foreground" role="status">{copy.registeredMissingSchedules.replace('{count}', formatNumber(missingSchedules))}</p> : null}
              {agenda.length ? <WeeklyGrid items={agenda} locale={locale} labels={{
                gridAriaLabel: copy.registeredTimetableTitle,
                today: copy.timetableToday,
                sectionPrefix: copy.columns.section,
                roomPending: copy.detailsRoomPending,
                noSlot: copy.timetableNoMeetings,
                blockHint: '',
                item: copy.timetableMeeting,
                items: copy.timetableMeetings,
              }} /> : <p className="text-sm text-muted-foreground">{copy.registeredTimetableEmpty}</p>}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
