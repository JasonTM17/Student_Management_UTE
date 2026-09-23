'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ClipboardCheck, RefreshCw } from 'lucide-react';

import { useAuth } from '@/context/AuthContext';
import { attendanceApi, type AttendanceRecord } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminPaginationFooter,
  AdminTableCard,
  AdminTableScroll,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { metricToneClass, type StatusTone } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import { getLocalizedFlatLabel } from '@/lib/academic-content';

const PAGE_LIMIT = 20;

// Statuses seen from the backend today; anything else falls back to a
// neutral badge carrying the raw value.
const STATUS_TONES: Record<string, StatusTone> = {
  PRESENT: 'success',
  ABSENT: 'danger',
  LATE: 'warning',
  EXCUSED: 'info',
};

function formatVietnameseName(user?: { firstName?: string; lastName?: string } | null) {
  if (!user) return '';
  const last = user.lastName?.trim() || '';
  const first = user.firstName?.trim() || '';
  if (last && first) return `${last} ${first}`;
  return last || first;
}

export default function AdminAttendancePage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatDate, formatNumber, href, locale, messages } = useI18n();
  const copy = messages.admin.attendance;
  const router = useRouter();
  const [records, setRecords] = useState<AttendanceRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchRecords = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await attendanceApi.adminList({ page, limit: PAGE_LIMIT });
      setRecords(response.data || []);
      setTotalPages(response.meta?.totalPages || 1);
      setTotal(response.meta?.total || 0);
    } catch {
      setError(copy.errorDescription);
    } finally {
      setIsLoading(false);
    }
  }, [copy.errorDescription, page]);

  useEffect(() => {
    if (canAccess) {
      void fetchRecords();
    }
  }, [canAccess, fetchRecords]);

  const statusLabel = (status: string | null | undefined) => {
    const key = (status ?? '').toUpperCase();
    const known = copy.statuses[key as keyof typeof copy.statuses];
    if (known) return known;
    return status || messages.common.statuses.UNKNOWN;
  };

  const statusTone = (status: string | null | undefined): StatusTone =>
    STATUS_TONES[(status ?? '').toUpperCase()] ?? 'neutral';

  const studentLabel = (record: AttendanceRecord) =>
    formatVietnameseName(record.student?.user) ||
    record.student?.user?.email ||
    record.studentId;

  const studentCodeLabel = (record: AttendanceRecord) =>
    record.student?.studentId || record.studentId;

  const sectionLabel = (record: AttendanceRecord) => {
    const composed = [
      record.section?.course?.code,
      record.section?.sectionNumber,
    ]
      .filter(Boolean)
      .join(' · ');
    return composed || copy.unknownSection;
  };

  const courseLabel = (record: AttendanceRecord) =>
    record.section?.course
      ? getLocalizedFlatLabel(
          locale,
          record.section.course.name ?? '',
          record.section.course.nameEn,
          record.section.course.nameVi,
          record.section.course.code ?? '',
        )
      : '';

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  return (
    <AdminFrame title={copy.title} description={copy.description}>
      <div className="space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="text-sm text-muted-foreground">
            {formatNumber(total)} {copy.recordsCount}
          </div>
          <Button
            variant="outline"
            onClick={() => void fetchRecords()}
            disabled={isLoading}
          >
            <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
            {copy.refresh}
          </Button>
        </div>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchRecords()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : records.length === 0 ? (
          <EmptyState
            icon={ClipboardCheck}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
          />
        ) : (
          <AdminTableCard
            title={copy.tableTitle}
            footer={
              <AdminPaginationFooter
                summary={copy.pageSummary
                  .replace('{page}', formatNumber(page))
                  .replace('{totalPages}', formatNumber(totalPages))}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
              />
            }
          >
            <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
              {records.map((record) => (
                <article
                  key={`${record.id}-mobile`}
                  className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                  role="listitem"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                        {studentCodeLabel(record)}
                      </p>
                      <h3 className="mt-1 break-words font-semibold text-foreground">
                        {studentLabel(record)}
                      </h3>
                      <p className="mt-1 break-words text-sm text-muted-foreground">
                        {record.student?.user?.email || copy.noEmail}
                      </p>
                    </div>
                    <span
                      className={`shrink-0 whitespace-nowrap rounded-md px-2.5 py-1 text-xs font-medium ${metricToneClass(statusTone(record.status))}`}
                    >
                      {statusLabel(record.status)}
                    </span>
                  </div>
                  <dl className="mt-4 grid gap-3 border-t border-border/60 pt-3 text-sm sm:grid-cols-2">
                    <div className="min-w-0">
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.headers.section}
                      </dt>
                      <dd className="mt-1 break-words text-foreground">{sectionLabel(record)}</dd>
                    </div>
                    <div className="min-w-0">
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.headers.date}
                      </dt>
                      <dd className="mt-1 break-words tabular-nums text-foreground">
                        {formatDate(record.date)}
                      </dd>
                    </div>
                    <div className="min-w-0 sm:col-span-2">
                      <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                        {copy.headers.notes}
                      </dt>
                      <dd className="mt-1 break-words text-foreground">
                        {record.notes || copy.noNotes}
                      </dd>
                    </div>
                  </dl>
                </article>
              ))}
            </div>
            <AdminTableScroll className="hidden md:block">
              <table className="w-full min-w-[900px] text-sm">
                <thead>
                  <tr className="border-b border-border/70 text-left text-muted-foreground">
                    <th className="px-2 py-3 font-medium">{copy.headers.student}</th>
                    <th className="px-2 py-3 font-medium">{copy.headers.section}</th>
                    <th className="px-2 py-3 font-medium">{copy.headers.date}</th>
                    <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.headers.notes}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {records.map((record) => (
                    <tr key={record.id} className="transition-colors hover:bg-secondary/30">
                      <td className="px-2 py-4">
                        <div className="space-y-1">
                          <p className="font-medium text-foreground">{studentLabel(record)}</p>
                          <p className="text-xs text-muted-foreground">
                            {record.student?.user?.email || copy.noEmail}
                          </p>
                          <p className="inline-flex rounded-md bg-secondary px-2 py-0.5 text-[11px] font-semibold text-foreground">
                            {studentCodeLabel(record)}
                          </p>
                        </div>
                      </td>
                      <td className="px-2 py-4">
                        <p className="font-medium text-foreground">{sectionLabel(record)}</p>
                        {courseLabel(record) ? (
                          <p className="truncate text-xs text-muted-foreground">
                            {courseLabel(record)}
                          </p>
                        ) : null}
                      </td>
                      <td className="px-2 py-4 tabular-nums text-muted-foreground">
                        {formatDate(record.date)}
                      </td>
                      <td className="px-2 py-4">
                        <span
                          className={`inline-flex whitespace-nowrap rounded-md px-2.5 py-1 text-xs font-medium ${metricToneClass(statusTone(record.status))}`}
                        >
                          {statusLabel(record.status)}
                        </span>
                      </td>
                      <td className="px-2 py-4 text-right">
                        <span
                          className="inline-block max-w-[14rem] truncate align-middle text-muted-foreground"
                          title={record.notes ?? ''}
                        >
                          {record.notes || copy.noNotes}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>
    </AdminFrame>
  );
}
