'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { CalendarCheck, ClipboardCheck } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { metricToneClass, type StatusTone } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { attendanceApi, type AttendanceRecord, type AttendanceSummary } from '@/lib/api';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

const RECENT_SESSIONS_CAP = 10;

// Statuses the backend is known to emit today; anything else renders as a
// neutral badge with the raw value rather than crashing or inventing a label.
const STATUS_TONES: Record<string, StatusTone> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  LATE: 'warning',
  EXCUSED: 'info',
};

function isFiniteNumber(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function formatSessionDate(value: string | null | undefined) {
  return value && !Number.isNaN(new Date(value).getTime());
}

export default function AttendancePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatDate, formatNumber, messages } = useI18n();
  const copy = messages.attendance;
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [summary, setSummary] = useState<AttendanceSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const loadGeneration = useRef(0);

  const fetchAttendance = useCallback(async () => {
    const generation = ++loadGeneration.current;
    setIsLoading(true);
    setError('');

    try {
      // The two reads are separate endpoints with separate contracts; one
      // shared generation check keeps a slow response from overwriting a
      // newer refresh of the other.
      const [recordsResult, summaryResult] = await Promise.all([
        attendanceApi.my(),
        attendanceApi.mySummary(),
      ]);
      if (generation !== loadGeneration.current) return;
      setRecords(recordsResult);
      setSummary(summaryResult);
    } catch {
      if (generation !== loadGeneration.current) return;
      setError(copy.errorDescription);
    } finally {
      if (generation === loadGeneration.current) {
        setIsLoading(false);
      }
    }
  }, [copy.errorDescription]);

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    void fetchAttendance();
  }, [fetchAttendance, hasAccess]);

  const totals = useMemo(() => {
    const base = { total: 0, present: 0, absent: 0, late: 0, excused: 0 };
    const summed = summary.reduce(
      (accumulator, row) => ({
        total: accumulator.total + (isFiniteNumber(row.total) ? row.total : 0),
        present: accumulator.present + (isFiniteNumber(row.present) ? row.present : 0),
        absent: accumulator.absent + (isFiniteNumber(row.absent) ? row.absent : 0),
        late: accumulator.late + (isFiniteNumber(row.late) ? row.late : 0),
        excused: accumulator.excused + (isFiniteNumber(row.excused) ? row.excused : 0),
      }),
      base,
    );
    // Weighted across sections: present ÷ all recorded sessions. The label
    // says exactly that so the card never claims a server-side rate that
    // counts late or excused sessions differently.
    const presentRate =
      summed.total > 0 ? Math.round((summed.present / summed.total) * 100) : null;
    return { ...summed, presentRate };
  }, [summary]);

  const recentRecords = useMemo(() => {
    return [...records]
      .sort((left, right) => {
        const leftTime = new Date(left.date).getTime();
        const rightTime = new Date(right.date).getTime();
        if (Number.isNaN(leftTime) || Number.isNaN(rightTime)) return 0;
        return rightTime - leftTime;
      })
      .slice(0, RECENT_SESSIONS_CAP);
  }, [records]);

  const statusLabel = (status: string | null | undefined) => {
    const key = (status ?? '').toUpperCase();
    const known = copy.statuses[key as keyof typeof copy.statuses];
    if (known) return known;
    // Unknown enum values: print the raw token, never a crash and never a
    // guessed translation.
    return status || messages.common.statuses.UNKNOWN;
  };

  const statusTone = (status: string | null | undefined): StatusTone =>
    STATUS_TONES[(status ?? '').toUpperCase()] ?? 'neutral';

  const rateTone = (rate: number): StatusTone =>
    rate >= 90 ? 'success' : rate >= 70 ? 'warning' : 'danger';

  const rateFillClass = (rate: number) => {
    const tone = rateTone(rate);
    if (tone === 'success') return 'bg-status-success';
    if (tone === 'warning') return 'bg-status-warning';
    return 'bg-status-danger';
  };

  const sectionLabel = (record: AttendanceRecord) => {
    const course = record.section?.course;
    const code = course?.code ?? '';
    const sectionNumber = record.section?.sectionNumber ?? '';
    const composed = [code, sectionNumber].filter(Boolean).join(' · ');
    return composed || copy.cardSectionLabel;
  };

  const courseName = (row: AttendanceSummary) =>
    getLocalizedFlatLabel(locale, row.courseName, row.courseNameEn, row.courseNameVi, row.courseCode);

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const isEmpty = !isLoading && !error && summary.length === 0 && records.length === 0;

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchAttendance()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : isEmpty ? (
        <EmptyState
          icon={ClipboardCheck}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
        />
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.statsPresentRate}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {totals.presentRate === null ? '—' : `${formatNumber(totals.presentRate)}%`}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">
                    {copy.statsPresentRateHint}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass(totals.presentRate === null ? 'neutral' : rateTone(totals.presentRate))}`}>
                  <CalendarCheck className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.statsTotalSessions}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(totals.total)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('info')}`}>
                  <ClipboardCheck className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.statsPresent}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(totals.present)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('success')}`}>
                  <CalendarCheck className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.statsAbsentLate}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(totals.absent + totals.late)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass(totals.absent + totals.late > 0 ? 'warning' : 'neutral')}`}>
                  <ClipboardCheck className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          {summary.length > 0 ? (
            <Card variant="muted">
              <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <SectionEyebrow>{copy.byCourseEyebrow}</SectionEyebrow>
                  <CardTitle className="mt-1 text-xl">{copy.byCourseTitle}</CardTitle>
                </div>
              </CardHeader>
              <CardContent>
                <div
                  className="space-y-3 md:hidden"
                  role="list"
                  aria-label={copy.byCourseTitle}
                >
                  {summary.map((row) => (
                    <article
                      key={`${row.sectionId}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {row.courseCode}
                          </p>
                          <h3 className="mt-1 break-words font-semibold text-foreground">
                            {courseName(row)}
                          </h3>
                        </div>
                        <span className="shrink-0 text-sm font-semibold tabular-nums text-foreground">
                          {formatNumber(row.attendanceRate)}%
                        </span>
                      </div>
                      <div className="mt-3 h-2 w-full overflow-hidden rounded-full bg-secondary">
                        <div
                          className={`h-full rounded-full ${rateFillClass(row.attendanceRate)} transition-all duration-300`}
                          style={{ width: `${Math.max(0, Math.min(100, row.attendanceRate))}%` }}
                        />
                      </div>
                      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm sm:grid-cols-3">
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.tableHeaders.sessions}
                          </dt>
                          <dd className="mt-1 tabular-nums text-foreground">{formatNumber(row.total)}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.tableHeaders.present}
                          </dt>
                          <dd className="mt-1 tabular-nums text-foreground">{formatNumber(row.present)}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.tableHeaders.late}
                          </dt>
                          <dd className="mt-1 tabular-nums text-foreground">{formatNumber(row.late)}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.tableHeaders.absent}
                          </dt>
                          <dd className="mt-1 tabular-nums text-foreground">{formatNumber(row.absent)}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.tableHeaders.excused}
                          </dt>
                          <dd className="mt-1 tabular-nums text-foreground">{formatNumber(row.excused)}</dd>
                        </div>
                      </dl>
                    </article>
                  ))}
                </div>
                <div className="hidden overflow-x-auto md:block">
                  <table className="w-full min-w-[760px] text-sm">
                    <thead>
                      <tr className="border-b border-border/70 text-left text-muted-foreground">
                        <th className="px-2 py-3 font-medium">{copy.tableHeaders.course}</th>
                        <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.sessions}</th>
                        <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.present}</th>
                        <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.late}</th>
                        <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.absent}</th>
                        <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.excused}</th>
                        <th className="px-2 py-3 text-right font-medium">{copy.tableHeaders.rate}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60">
                      {summary.map((row) => (
                        <tr key={row.sectionId} className="transition-colors hover:bg-secondary/30">
                          <td className="px-2 py-3">
                            <span className="block font-medium text-foreground">{row.courseCode}</span>
                            <span className="block truncate text-muted-foreground">{courseName(row)}</span>
                          </td>
                          <td className="px-2 py-3 text-center tabular-nums text-muted-foreground">
                            {formatNumber(row.total)}
                          </td>
                          <td className="px-2 py-3 text-center tabular-nums text-foreground font-medium">
                            {formatNumber(row.present)}
                          </td>
                          <td className="px-2 py-3 text-center tabular-nums text-muted-foreground">
                            {formatNumber(row.late)}
                          </td>
                          <td className="px-2 py-3 text-center tabular-nums text-muted-foreground">
                            {formatNumber(row.absent)}
                          </td>
                          <td className="px-2 py-3 text-center tabular-nums text-muted-foreground">
                            {formatNumber(row.excused)}
                          </td>
                          <td className="px-2 py-3">
                            <div className="flex items-center justify-end gap-3">
                              <div className="h-2 w-24 overflow-hidden rounded-full bg-secondary">
                                <div
                                  className={`h-full rounded-full ${rateFillClass(row.attendanceRate)} transition-all duration-300`}
                                  style={{
                                    width: `${Math.max(0, Math.min(100, row.attendanceRate))}%`,
                                  }}
                                />
                              </div>
                              <span className="w-12 text-right font-semibold tabular-nums text-foreground">
                                {formatNumber(row.attendanceRate)}%
                              </span>
                            </div>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          ) : null}

          <Card variant="muted">
            <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <SectionEyebrow>{copy.recentEyebrow}</SectionEyebrow>
                <CardTitle className="mt-1 text-xl">{copy.recentTitle}</CardTitle>
              </div>
              {records.length > RECENT_SESSIONS_CAP ? (
                <div className="text-sm text-muted-foreground">
                  {copy.showingRecent
                    .replace('{count}', formatNumber(RECENT_SESSIONS_CAP))
                    .replace('{total}', formatNumber(records.length))}
                </div>
              ) : null}
            </CardHeader>
            <CardContent>
              {recentRecords.length === 0 ? (
                <p className="text-sm text-muted-foreground">{copy.recentEmpty}</p>
              ) : (
                <ul className="divide-y divide-border/60" role="list">
                  {recentRecords.map((record) => (
                    <li key={record.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
                      <span className="min-w-[8.5rem] text-sm font-medium tabular-nums text-foreground">
                        {formatSessionDate(record.date)
                          ? formatDate(record.date)
                          : '—'}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm text-foreground">
                          {sectionLabel(record)}
                        </span>
                        {record.section?.course
                          ? (
                            <span className="block truncate text-xs text-muted-foreground">
                              {getLocalizedFlatLabel(
                                locale,
                                record.section.course.name ?? '',
                                record.section.course.nameEn,
                                record.section.course.nameVi,
                                record.section.course.code ?? '',
                              )}
                            </span>
                          ) : null}
                      </span>
                      <span className="min-w-0 max-w-[16rem] flex-none">
                        <span className="block truncate text-xs text-muted-foreground" title={record.notes ?? ''}>
                          {record.notes || copy.noNotes}
                        </span>
                      </span>
                      <span
                        className={`shrink-0 whitespace-nowrap rounded-md px-2.5 py-1 text-xs font-medium ${metricToneClass(statusTone(record.status))}`}
                      >
                        {statusLabel(record.status)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
