'use client';

import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, ClipboardCheck, RefreshCw } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  adminRegistrationApi,
  semestersApi,
  type CurriculumDefaultAdminRunResponse,
} from '@/lib/api';
import type { Semester } from '@/types/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminTableCard, AdminToolbarCard } from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { campusErrorMessage } from '@/lib/campus-error';

export default function AdminCurriculumDefaultsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { href, locale, messages, formatDateTime, formatNumber } = useI18n();
  const router = useRouter();
  const copy = messages.admin.curriculumDefaults;
  const campusErrorCodes = messages.common.campusErrors.codes as Readonly<Record<string, string>>;
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [semesterId, setSemesterId] = useState('');
  const [batchSize, setBatchSize] = useState(50);
  const [batchCursor, setBatchCursor] = useState('');
  const [recentRuns, setRecentRuns] = useState<Awaited<ReturnType<typeof adminRegistrationApi.curriculumDefaultRuns>>['runs']>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<CurriculumDefaultAdminRunResponse | null>(null);
  const [dryRunResult, setDryRunResult] = useState<CurriculumDefaultAdminRunResponse | null>(null);

  useEffect(() => {
    if (authLoading || isLoggingOut) return;
    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }
    if (!isAdmin && !isSuperAdmin) router.replace(href('/dashboard'));
  }, [authLoading, href, isAdmin, isLoggingOut, isSuperAdmin, router, user]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [semesterResponse, runResponse] = await Promise.all([
        semestersApi.getAll({ forceRefresh: true }),
        adminRegistrationApi.curriculumDefaultRuns(),
      ]);
      const available = semesterResponse.data ?? [];
      setSemesters(available);
      setSemesterId((current) => current || available.find((semester) => semester.status !== 'CLOSED')?.id || available[0]?.id || '');
      setRecentRuns(runResponse.runs);
    } catch (cause) {
      setError(campusErrorMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setLoading(false);
    }
  }, [copy.loadFailed, messages.common.campusErrors]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const run = async (mode: 'dry-run' | 'apply') => {
    if (!semesterId) return;
    setBusy(true);
    setError('');
    try {
      const payload = {
        semesterId,
        batchSize,
        ...(batchCursor ? { cursor: batchCursor } : {}),
      };
      if (mode === 'dry-run') {
        const dryRun = await adminRegistrationApi.dryRunCurriculumDefaults(payload);
        setDryRunResult(dryRun);
        setResult(dryRun);
        toast.success(copy.dryRunReady);
        return;
      }
      const applied = await adminRegistrationApi.applyCurriculumDefaults(payload);
      setDryRunResult(null);
      setResult(applied);
      setBatchCursor(applied.nextCursor ?? '');
      toast.success(copy.applyComplete);
      const refreshed = await adminRegistrationApi.curriculumDefaultRuns();
      setRecentRuns(refreshed.runs);
    } catch (cause) {
      setError(campusErrorMessage(cause, messages.common.campusErrors, copy.runFailed));
    } finally {
      setBusy(false);
    }
  };

  const changeSemester = (value: string) => {
    setSemesterId(value);
    setBatchCursor('');
    setDryRunResult(null);
    setResult(null);
  };

  if (authLoading || isLoggingOut) return <LoadingState label={messages.common.states.loadingContent} />;
  if (!user || !canAccess) return <WorkspaceForbiddenState signedIn={Boolean(user)} />;

  const selectedSemester = semesters.find((semester) => semester.id === semesterId);
  const semesterLabel = (semester: Semester) => locale === 'vi'
    ? semester.nameVi || semester.name
    : semester.nameEn || semester.name;
  const canApply = Boolean(dryRunResult && dryRunResult.semesterId === semesterId && dryRunResult.dryRun && dryRunResult.courses > 0);

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={(
        <Button type="button" variant="outline" onClick={() => void load()} disabled={loading || busy}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          {copy.refresh}
        </Button>
      )}
    >
      <div className="space-y-6" data-testid="curriculum-defaults-panel">
        <AdminToolbarCard>
          <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_auto] md:items-end">
            <div className="min-w-0">
              <label htmlFor="curriculum-defaults-semester" className="mb-2 block text-sm font-semibold text-foreground">
                {copy.selectRound}
              </label>
              <select
                id="curriculum-defaults-semester"
                value={semesterId}
                onChange={(event) => changeSemester(event.target.value)}
                disabled={loading || semesters.length === 0 || busy}
                className="min-h-11 w-full rounded-md border border-input bg-background px-3 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring md:max-w-xl"
              >
                <option value="">{copy.selectRoundPlaceholder}</option>
                {semesters.map((semester) => (
                  <option key={semester.id} value={semester.id}>
                    {semesterLabel(semester)} · {semester.status}
                  </option>
                ))}
              </select>
              {selectedSemester ? (
                <p className="mt-2 text-xs text-muted-foreground">
                  {selectedSemester.name} · {selectedSemester.startDate.slice(0, 10)} – {selectedSemester.endDate.slice(0, 10)}
                </p>
              ) : null}
            </div>
            <label className="max-w-36 space-y-2 text-sm font-medium text-foreground">
              <span>{copy.batchSize}</span>
              <input
                type="number"
                min={1}
                max={200}
                step={1}
                value={batchSize}
                onChange={(event) => setBatchSize(Math.max(1, Math.min(200, Number(event.target.value) || 1)))}
                disabled={busy}
                className="min-h-11 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
            </label>
          </div>
          <p className="mt-3 text-sm leading-6 text-muted-foreground">{copy.batchHelp}</p>
          <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:flex-wrap">
            <Button type="button" variant="outline" className="min-h-11" onClick={() => void run('dry-run')} disabled={loading || busy || !semesterId}>
              <CalendarClock className="mr-2 h-4 w-4" aria-hidden="true" />
              {copy.dryRun}
            </Button>
            <Button type="button" className="min-h-11" onClick={() => void run('apply')} disabled={loading || busy || !canApply}>
              <ClipboardCheck className="mr-2 h-4 w-4" aria-hidden="true" />
              {busy ? copy.applying : copy.apply}
            </Button>
          </div>
        </AdminToolbarCard>

        {error ? <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} /> : null}
        {loading ? <LoadingState label={messages.common.states.loadingContent} /> : null}

        {result ? (
          <Card aria-labelledby="curriculum-defaults-results-title">
            <CardHeader className="border-b border-border/70">
              <CardTitle as="h2" id="curriculum-defaults-results-title" className="text-lg">{copy.results}</CardTitle>
              <p role="status" className="text-sm text-muted-foreground">
                {result.dryRun ? copy.dryRunReady : copy.applyComplete}
              </p>
            </CardHeader>
            <CardContent className="space-y-4 p-4 sm:p-5">
              <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {[
                  [copy.students, result.students],
                  [copy.courses, result.courses],
                  [copy.wouldRegister, result.wouldRegister],
                  [copy.registered, result.registered],
                  [copy.skipped, result.skipped],
                  [copy.failedStudents, result.failedStudents],
                ].map(([label, count]) => (
                  <div key={String(label)} className="rounded-lg border border-border/70 bg-secondary/30 p-3">
                    <dt className="text-xs font-semibold text-muted-foreground">{label}</dt>
                    <dd className="mt-1 text-xl font-bold tabular-nums text-foreground">{formatNumber(Number(count))}</dd>
                  </div>
                ))}
              </dl>
              {result.skippedByReason.length > 0 ? (
                <div>
                  <h3 className="text-sm font-semibold text-foreground">{copy.skippedByReason}</h3>
                  <ul className="mt-2 flex flex-wrap gap-2">
                    {result.skippedByReason.map((reason) => (
                      <li key={reason.reasonCode} className="rounded-md border border-border bg-background px-3 py-2 text-sm">
                        <span className="font-medium">{campusErrorCodes[reason.reasonCode] ?? copy.runFailed}</span>
                        <span className="ml-2 tabular-nums text-muted-foreground">{formatNumber(reason.count)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ) : null}
              {result.hasMore ? (
                <div className="rounded-lg border border-primary/25 bg-primary/[0.04] p-3 text-sm">
                  <p className="font-medium text-foreground">{copy.moreAvailable}</p>
                  {result.nextCursor ? <p className="mt-2 break-all font-mono text-xs text-muted-foreground">{copy.nextCursor}: {result.nextCursor}</p> : null}
                </div>
              ) : null}
              {result.runId ? <p className="break-all text-xs text-muted-foreground">{copy.runId}: {result.runId}</p> : null}
            </CardContent>
          </Card>
        ) : null}

        <AdminTableCard title={copy.lastRuns} titleAs="h2">
          {recentRuns.length === 0 ? (
            <EmptyState icon={ClipboardCheck} title={copy.noRuns} description={copy.noRunsDescription} />
          ) : (
            <div className="space-y-3 p-3 sm:hidden" role="list" aria-label={copy.lastRuns}>
              {recentRuns.map((run) => (
                <article key={run.id} role="listitem" className="rounded-lg border border-border/70 bg-card p-3">
                  <p className="text-sm font-semibold text-foreground">{run.summary || copy.applyComplete}</p>
                  <p className="mt-1 text-xs text-muted-foreground">{formatDateTime(run.createdAt)}</p>
                  <p className="mt-1 break-all font-mono text-[11px] text-muted-foreground">{run.id}</p>
                </article>
              ))}
            </div>
          )}
          {recentRuns.length > 0 ? (
            <div className="hidden overflow-x-auto sm:block">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border/70 bg-secondary/40 text-xs text-muted-foreground">
                    <th scope="col" className="px-4 py-3 font-semibold">{copy.runId}</th>
                    <th scope="col" className="px-4 py-3 font-semibold">{copy.results}</th>
                    <th scope="col" className="px-4 py-3 font-semibold">{copy.createdAt}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {recentRuns.map((run) => (
                    <tr key={run.id}>
                      <td className="max-w-56 break-all px-4 py-3 font-mono text-xs">{run.id}</td>
                      <td className="px-4 py-3 text-foreground">{run.summary || copy.applyComplete}</td>
                      <td className="whitespace-nowrap px-4 py-3 text-muted-foreground">{formatDateTime(run.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
        </AdminTableCard>
      </div>
    </AdminFrame>
  );
}
