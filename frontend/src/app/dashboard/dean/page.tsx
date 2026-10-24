'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Building2, ChevronDown, Crown, Users } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { thesisApi, type DeanGroup, type DeanOverview, type ThesisRound } from '@/lib/thesis-api';
import { mergeThesisRounds, sortThesisRoundsForOverview } from '@/lib/thesis-round';
import { cn } from '@/lib/utils';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { LocalizedLink } from '@/components/LocalizedLink';
import { StatusPill } from '@/components/ui/status-pill';
import type { StatusTone } from '@/components/ui/status';

function interpolate(template: string, values: Record<string, string>): string {
  return Object.entries(values).reduce(
    (text, [key, value]) => text.replaceAll(`{${key}}`, value),
    template,
  );
}

function statusTone(status: string): StatusTone {
  switch (status) {
    case 'APPROVED':
    case 'COMPLETED':
      return 'success';
    case 'PENDING':
    case 'SUBMITTED':
    case 'ASSIGNED':
      return 'warning';
    case 'REJECTED':
    case 'CANCELLED':
      return 'danger';
    default:
      return 'neutral';
  }
}

function StatCard({ label, value, tone }: { label: string; value: number; tone?: StatusTone }) {
  return (
    <Card className="border-border/70">
      <CardContent className="px-4 py-4">
        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
        <p
          className={cn(
            'mt-1 text-2xl font-bold tabular-nums',
            tone === 'warning' && 'text-status-warning-foreground',
            tone === 'success' && 'text-status-success-foreground',
            tone === 'danger' && 'text-status-danger-foreground',
          )}
        >
          {value}
        </p>
      </CardContent>
    </Card>
  );
}

function GroupRow({ group, copy }: { group: DeanGroup; copy: Record<string, string> }) {
  const [open, setOpen] = useState(false);
  const detailId = `dean-group-${group.id}`;
  return (
    <div className="rounded-xl border border-border/70 bg-card">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-controls={detailId}
        className="flex w-full flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 text-left transition-colors hover:bg-muted/40 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
      >
        <div className="min-w-0 flex-1">
          <p
            className="truncate text-sm font-semibold text-foreground"
            title={group.topicTitle || copy.noTopic}
          >
            {group.topicTitle || copy.noTopic}
          </p>
          <p className="mt-0.5 text-xs text-muted-foreground">
            {group.departmentName || '—'} · {group.members.length} {copy.members.toLowerCase()}
          </p>
        </div>
        <div className="flex items-center gap-2">
          <StatusPill tone={statusTone(group.status)}>{copy[group.status] ?? group.status}</StatusPill>
          <StatusPill tone={statusTone(group.approvalStatus)}>
            {copy[group.approvalStatus] ?? group.approvalStatus}
          </StatusPill>
          <ChevronDown
            className={cn('h-4 w-4 text-muted-foreground transition-transform', open && 'rotate-180')}
            aria-hidden="true"
          />
        </div>
      </button>
      {open ? (
        <div id={detailId} className="border-t border-border/60 px-4 py-3">
          {group.topicTitle ? (
            <p className="mb-4 text-sm font-semibold text-foreground">{group.topicTitle}</p>
          ) : null}
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {copy.members}
              </p>
              <ul className="space-y-1.5">
                {group.members.map((member, index) => (
                  <li key={`${member.studentNumber ?? member.displayName ?? index}`} className="flex flex-wrap items-center gap-2 text-sm">
                    <span className="font-medium text-foreground">
                      {member.displayName || member.studentNumber || '—'}
                    </span>
                    {member.studentNumber ? (
                      <span className="font-mono text-xs text-muted-foreground">{member.studentNumber}</span>
                    ) : null}
                    {member.isLeader ? (
                      <StatusPill tone="info" icon={<Crown className="h-3 w-3" aria-hidden="true" />}>
                        {copy.leaderBadge}
                      </StatusPill>
                    ) : null}
                    {member.isExternal ? (
                      <StatusPill tone="neutral">{copy.externalBadge}</StatusPill>
                    ) : null}
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                {copy.supervisors}
              </p>
              {group.supervisors.length === 0 ? (
                <p className="text-sm text-muted-foreground">{copy.noSupervisor}</p>
              ) : (
                <ul className="space-y-1.5">
                  {group.supervisors.map((supervisor) => (
                    <li key={supervisor.lecturerId} className="text-sm">
                      <span className="font-medium text-foreground">
                        {supervisor.displayName || supervisor.lecturerId}
                      </span>
                      {supervisor.employeeId ? (
                        <span className="ml-2 font-mono text-xs text-muted-foreground">
                          {supervisor.employeeId}
                        </span>
                      ) : null}
                      {supervisor.departmentName ? (
                        <span className="block text-xs text-muted-foreground">
                          {supervisor.departmentName}
                        </span>
                      ) : null}
                    </li>
                  ))}
                </ul>
              )}
              {group.rejectionReason ? (
                <p className="mt-3 rounded-md bg-status-danger/10 px-3 py-2 text-xs text-status-danger-foreground">
                  {group.rejectionReason}
                </p>
              ) : null}
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

export default function DeanOverviewPage() {
  const { isFacultyHead, isLoading: authLoading, user } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const copy = messages.deanOverview;
  const statusCopy = copy.status as unknown as Record<string, string>;
  const tableCopy = copy.table as unknown as Record<string, string>;
  const searchParams = useSearchParams();
  const explicitRoundId = searchParams.get('roundId') || '';

  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [overview, setOverview] = useState<DeanOverview | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(false);
  const overviewRequest = useRef(0);

  useEffect(() => {
    if (!isFacultyHead) return;
    overviewRequest.current += 1;
    setRounds([]);
    setSelectedRoundId('');
    setOverview(null);
    setError(false);
    setIsLoading(true);
    let cancelled = false;
    Promise.all([
      thesisApi.listRounds(),
      // The faculty overview must stay reachable for archived rounds too —
      // closing does not change the numbers, it only ends the lifecycle.
      thesisApi.listRounds('CLOSED').catch(() => [] as ThesisRound[]),
    ])
      .then(([list, closed]) => {
        if (cancelled) return;
        const sorted = sortThesisRoundsForOverview(mergeThesisRounds(list, closed));
        setRounds(sorted);
        const preferred = explicitRoundId && sorted.some((r) => r.id === explicitRoundId)
          ? explicitRoundId
          : sorted[0]?.id ?? '';
        setSelectedRoundId(preferred);
        if (!preferred) setIsLoading(false);
      })
      .catch(() => {
        if (!cancelled) {
          setError(true);
          setIsLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [explicitRoundId, isFacultyHead, user?.id]);

  const loadOverview = useCallback((roundId: string) => {
    const sequence = ++overviewRequest.current;
    setOverview(null);
    setIsLoading(true);
    setError(false);
    thesisApi
      .deanOverview(roundId)
      .then((data) => {
        if (sequence === overviewRequest.current) {
          setOverview(data);
        }
      })
      .catch(() => {
        if (sequence !== overviewRequest.current) return;
        setOverview(null);
        setError(true);
      })
      .finally(() => {
        if (sequence === overviewRequest.current) {
          setIsLoading(false);
        }
      });
  }, []);

  useEffect(() => {
    if (!isFacultyHead || !selectedRoundId) return;
    loadOverview(selectedRoundId);
    return () => {
      overviewRequest.current += 1;
    };
  }, [isFacultyHead, selectedRoundId, loadOverview, user?.id]);

  const supervisorHeader = useMemo(
    () => interpolate(copy.supervisorsSubtitle, {}),
    [copy.supervisorsSubtitle],
  );

  useEffect(() => {
    if (authLoading) {
      return;
    }
    if (!user) {
      router.replace(`${href('/login')}?portal=dean&reason=session-expired`);
      return;
    }
    if (!isFacultyHead) {
      router.replace(href('/dashboard'));
    }
  }, [authLoading, href, isFacultyHead, router, user]);

  if (authLoading || !user || !isFacultyHead) {
    return <LoadingState label={copy.loading} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={copy.title}
        leadingContent={
          <div>
            <p className="text-xl font-bold text-foreground">{copy.title}</p>
            {overview ? (
              <p className="mt-1 flex items-center gap-1.5 text-sm text-muted-foreground">
                <Building2 className="h-4 w-4" aria-hidden="true" />
                {interpolate(copy.subtitle, { faculty: overview.faculty.facultyName })}
              </p>
            ) : null}
          </div>
        }
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <LocalizedLink
              href="/admin/thesis"
              className="inline-flex min-h-10 items-center gap-2 rounded-md border border-border/70 bg-card px-3 text-sm font-semibold text-primary transition-colors hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {copy.adminLink}
            </LocalizedLink>
            <Select
              aria-label={copy.roundLabel}
              value={selectedRoundId}
              onChange={(event) => setSelectedRoundId(event.target.value)}
              options={rounds.map((round) => ({ value: round.id, label: round.name }))}
              className="min-w-[220px]"
            />
          </div>
        }
      />

      {isLoading ? <LoadingState label={copy.loading} /> : null}
      {!isLoading && error ? (
        <ErrorState
          title={copy.title}
          description={copy.loadError}
          onRetry={() => (selectedRoundId ? loadOverview(selectedRoundId) : window.location.reload())}
        />
      ) : null}
      {!isLoading && !error && overview ? (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-7">
            <StatCard label={copy.stats.groups} value={overview.stats.totalGroups} />
            <StatCard label={copy.stats.pending} value={overview.stats.groupsPendingApproval} tone="warning" />
            <StatCard label={copy.stats.approved} value={overview.stats.groupsApproved} tone="success" />
            <StatCard label={copy.stats.withoutTopic} value={overview.stats.groupsWithoutTopic} />
            <StatCard label={copy.stats.members} value={overview.stats.totalMembers} />
            <StatCard label={copy.stats.topics} value={overview.stats.facultyTopics} />
            <StatCard label={copy.stats.supervisors} value={overview.stats.supervisorCount} />
          </div>

          {overview.stats.byDepartment.length > 1 ? (
            <Card className="border-border/70">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">{copy.table.departmentBreakdown}</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-wrap gap-2 pb-4">
                {overview.stats.byDepartment.map((dept) => (
                  <StatusPill key={dept.departmentId} tone="neutral">
                    {dept.departmentName}: {dept.groupCount} {copy.table.groupCount.toLowerCase()} ·{' '}
                    {dept.topicCount} {copy.table.topicCount.toLowerCase()}
                  </StatusPill>
                ))}
              </CardContent>
            </Card>
          ) : null}

          <Card className="border-border/70">
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">{copy.supervisorsTitle}</CardTitle>
              <p className="text-xs text-muted-foreground">{supervisorHeader}</p>
            </CardHeader>
            <CardContent className="pb-4">
              {overview.supervisors.length === 0 ? (
                <p className="text-sm text-muted-foreground">{copy.table.noSupervisor}</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[520px] text-left text-sm">
                    <thead>
                      <tr className="border-b border-border/70 text-xs uppercase tracking-wide text-muted-foreground">
                        <th className="py-2 pr-4 font-medium">{copy.table.lecturer}</th>
                        <th className="py-2 pr-4 font-medium">{copy.table.department}</th>
                        <th className="py-2 pr-4 text-right font-medium">{copy.table.supervisedTopics}</th>
                        <th className="py-2 text-right font-medium">{copy.table.supervisedGroups}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {overview.supervisors.map((supervisor) => (
                        <tr key={supervisor.lecturerId} className="border-b border-border/40 last:border-0">
                          <td className="py-2.5 pr-4">
                            <span className="font-medium text-foreground">
                              {supervisor.displayName || supervisor.lecturerId}
                            </span>
                            {supervisor.employeeId ? (
                              <span className="ml-2 font-mono text-xs text-muted-foreground">
                                {supervisor.employeeId}
                              </span>
                            ) : null}
                          </td>
                          <td className="py-2.5 pr-4 text-muted-foreground">
                            {supervisor.departmentName || '—'}
                          </td>
                          <td className="py-2.5 pr-4 text-right tabular-nums">
                            {supervisor.supervisedTopics}
                          </td>
                          <td className="py-2.5 text-right tabular-nums">
                            {supervisor.supervisedGroups}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>

          <section aria-labelledby="dean-groups-heading">
            <div className="mb-3 flex items-center gap-2">
              <Users className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
              <div>
                <h2 id="dean-groups-heading" className="text-sm font-semibold text-foreground">
                  {copy.groupsTitle}
                </h2>
                <p className="text-xs text-muted-foreground">
                  {interpolate(copy.groupsSubtitle, { faculty: overview.faculty.facultyName })}
                </p>
              </div>
            </div>
            {overview.groups.length === 0 ? (
              <EmptyState title={copy.emptyTitle} description={copy.emptyBody} />
            ) : (
              <div className="space-y-2">
                {overview.groups.map((group) => (
                  <GroupRow
                    key={group.id}
                    group={group}
                    copy={{ ...statusCopy, ...tableCopy }}
                  />
                ))}
              </div>
            )}
          </section>
        </>
      ) : null}
    </div>
  );
}
