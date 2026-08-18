'use client';

import { useEffect, useState } from 'react';
import { ArrowUpRight, CalendarDays, Check, CircleDot, ClipboardCheck, FileStack, Gauge, UsersRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import {
  thesisApi,
  type ThesisGroup,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';

function statusClass(status: string) {
  if (['APPROVED', 'RESULTS_PUBLISHED', 'PROPOSALS_PUBLISHED'].includes(status)) {
    return 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300';
  }
  if (['REJECTED', 'CANCELLED'].includes(status)) {
    return 'bg-red-500/12 text-red-700 dark:text-red-300';
  }
  return 'bg-amber-500/12 text-amber-700 dark:text-amber-300';
}

export default function ThesisPage() {
  const { user, isStudent } = useAuth();
  const { formatDateTime, messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isActionPending, setIsActionPending] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  useEffect(() => {
    let cancelled = false;

    const loadRounds = async () => {
      setIsLoading(true);
      setError('');
      try {
        const nextRounds = await thesisApi.listRounds();
        if (cancelled) return;
        setRounds(nextRounds);
        setSelectedRoundId((current) => current || nextRounds[0]?.id || '');
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    void loadRounds();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (!selectedRoundId) {
      setTopics([]);
      setGroups([]);
      return;
    }

    let cancelled = false;
    const loadWorkspace = async () => {
      setError('');
      try {
        const [nextTopics, nextGroups] = await Promise.all([
          thesisApi.listTopics(selectedRoundId),
          thesisApi.listGroups(selectedRoundId),
        ]);
        if (cancelled) return;
        setTopics(nextTopics);
        setGroups(nextGroups);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };

    void loadWorkspace();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, selectedRoundId]);

  const selectedRound = rounds.find((round) => round.id === selectedRoundId);
  const studentId = user?.studentId ?? '';
  const currentGroup = groups.find(
    (group) => group.leaderStudentId === studentId || group.memberStudentIds.includes(studentId),
  );
  const statusLabel = (status: string) =>
    messages.thesis.status[status as keyof typeof messages.thesis.status] ?? status;

  const refreshGroups = async () => {
    if (!selectedRoundId) return;
    setGroups(await thesisApi.listGroups(selectedRoundId));
  };

  const createGroup = async () => {
    if (!selectedRoundId) return;
    setIsActionPending(true);
    setActionError('');
    try {
      await thesisApi.createGroup(selectedRoundId);
      await refreshGroups();
    } catch {
      setActionError(messages.thesis.actionFailed);
    } finally {
      setIsActionPending(false);
    }
  };

  const chooseTopic = async (topicId: string) => {
    if (!currentGroup) return;
    setIsActionPending(true);
    setActionError('');
    try {
      await thesisApi.assignTopic(currentGroup.id, topicId);
      await refreshGroups();
    } catch {
      setActionError(messages.thesis.actionFailed);
    } finally {
      setIsActionPending(false);
    }
  };

  if (isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (error && rounds.length === 0) {
    return (
      <ErrorState
        title={messages.thesis.loadFailed}
        description={error}
        retryLabel={messages.thesis.retry}
        onRetry={() => window.location.reload()}
      />
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.eyebrow}</SectionEyebrow>}
        title={messages.thesis.title}
        description={messages.thesis.description}
        actions={
          <label className="flex min-w-[15rem] flex-col gap-2 text-left text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {messages.thesis.selectRound}
            <select
              value={selectedRoundId}
              onChange={(event) => setSelectedRoundId(event.target.value)}
              className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={messages.thesis.selectRound}
            >
              {rounds.map((round) => (
                <option key={round.id} value={round.id}>
                  {round.name}
                </option>
              ))}
            </select>
          </label>
        }
      />

      {!selectedRound ? (
        <EmptyState
          icon={FileStack}
          title={messages.thesis.noRound}
          description={messages.thesis.noTopicsDescription}
        />
      ) : (
        <>
          <Card className="overflow-hidden border-foreground/10 bg-[hsl(var(--foreground))] text-[hsl(var(--background))]">
            <CardContent className="relative grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.2fr_0.8fr] lg:items-end">
              <div className="space-y-4">
                <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-white/75">
                  <CircleDot className="h-3.5 w-3.5 text-[hsl(var(--accent-warm))]" />
                  {selectedRound.thesisType}
                </div>
                <div>
                  <p className="text-sm text-white/65">{messages.thesis.roundStatus}</p>
                  <h2 className="mt-2 text-3xl font-semibold tracking-tight sm:text-4xl">
                    {selectedRound.name}
                  </h2>
                </div>
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                <div className="border-l border-white/20 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/55">
                    {messages.thesis.roundStatus}
                  </p>
                  <p className="mt-2 text-lg font-semibold">{statusLabel(selectedRound.status)}</p>
                </div>
                <div className="border-l border-white/20 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/55">
                    {messages.thesis.registrationWindow}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-white/75">
                    {formatDateTime(selectedRound.registrationStart)}
                    <span className="mx-1 text-white/40">→</span>
                    {formatDateTime(selectedRound.registrationEnd)}
                  </p>
                </div>
              </div>
              <div className="pointer-events-none absolute -bottom-16 right-8 h-48 w-48 rounded-full border border-white/10 sm:right-24" />
            </CardContent>
          </Card>

          {error ? <ErrorState title={messages.thesis.loadFailed} description={error} /> : null}
          {actionError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {actionError}
            </div>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard label={messages.thesis.roundStatus} value={statusLabel(selectedRound.status)} icon={<CalendarDays className="h-5 w-5" />} tone="warm" />
            <MetricCard label={messages.thesis.topics} value={topics.length} icon={<FileStack className="h-5 w-5" />} tone="cool" />
            <MetricCard label={messages.thesis.groups} value={groups.length} icon={<UsersRound className="h-5 w-5" />} tone="success" />
            <MetricCard label={messages.thesis.groupsTitle} value={currentGroup ? currentGroup.memberStudentIds.length : 0} icon={<Check className="h-5 w-5" />} tone="violet" />
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <LocalizedLink
              href="/dashboard/thesis/progress"
              className="group flex items-center gap-4 rounded-lg border border-border/70 bg-card p-5 transition-colors hover:border-primary/45 hover:bg-secondary/30"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-amber-500/12 text-amber-700 dark:text-amber-300">
                <Gauge className="h-5 w-5" />
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-sm font-semibold text-foreground transition-colors group-hover:text-primary">
                  {messages.thesis.progress.title}
                </h3>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {messages.thesis.progress.description}
                </p>
              </div>
              <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </LocalizedLink>
            <LocalizedLink
              href="/dashboard/thesis/reviews"
              className="group flex items-center gap-4 rounded-lg border border-border/70 bg-card p-5 transition-colors hover:border-primary/45 hover:bg-secondary/30"
            >
              <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-cyan-500/12 text-cyan-700 dark:text-cyan-300">
                <ClipboardCheck className="h-5 w-5" />
              </div>
              <div className="min-w-0 flex-1">
                <h3 className="text-sm font-semibold text-foreground transition-colors group-hover:text-primary">
                  {messages.thesis.review.title}
                </h3>
                <p className="mt-1 text-sm leading-6 text-muted-foreground">
                  {messages.thesis.review.description}
                </p>
              </div>
              <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
            </LocalizedLink>
          </div>

          <div className="grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
            <Card variant="muted" className="h-full">
              <CardHeader>
                <CardTitle>{messages.thesis.groupsTitle}</CardTitle>
                <CardDescription>{messages.thesis.groupsDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {!currentGroup ? (
                  <EmptyState
                    icon={UsersRound}
                    title={messages.thesis.noGroup}
                    description={messages.thesis.noGroupDescription}
                    action={
                      isStudent && selectedRound.status === 'REGISTRATION_OPEN' ? (
                        <Button type="button" onClick={() => void createGroup()} disabled={isActionPending}>
                          {messages.thesis.createGroup}
                          <ArrowUpRight className="ml-2 h-4 w-4" />
                        </Button>
                      ) : undefined
                    }
                    className="min-h-[280px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  <div className="space-y-5">
                    <div className="flex items-start justify-between gap-4 rounded-xl border border-border/70 bg-card p-4">
                      <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                          {messages.thesis.groupsTitle}
                        </p>
                        <p className="mt-2 break-all font-mono text-xs text-muted-foreground">{currentGroup.id}</p>
                      </div>
                      <span className={cn('rounded-full px-2.5 py-1 text-xs font-semibold', statusClass(currentGroup.approvalStatus))}>
                        {statusLabel(currentGroup.approvalStatus)}
                      </span>
                    </div>
                    <div className="space-y-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="text-muted-foreground">{messages.thesis.memberCount.replace('{count}', String(currentGroup.memberStudentIds.length))}</span>
                        <span className="font-medium text-foreground">{currentGroup.topicId ? messages.thesis.status.SUBMITTED : messages.thesis.chooseTopic}</span>
                      </div>
                      <div className="flex gap-2">
                        {currentGroup.memberStudentIds.map((memberId, index) => (
                          <div key={memberId} className="flex h-10 w-10 items-center justify-center rounded-full border border-border bg-secondary text-xs font-semibold text-foreground" title={memberId}>
                            {index + 1}
                          </div>
                        ))}
                        {Array.from({ length: 3 - currentGroup.memberStudentIds.length }).map((_, index) => (
                          <div key={`empty-${index}`} className="flex h-10 w-10 items-center justify-center rounded-full border border-dashed border-border text-xs text-muted-foreground">
                            +
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            <Card className="h-full">
              <CardHeader>
                <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {topics.length === 0 ? (
                  <EmptyState icon={FileStack} title={messages.thesis.noTopics} description={messages.thesis.noTopicsDescription} className="min-h-[280px]" />
                ) : (
                  <div className="grid gap-3 sm:grid-cols-2">
                    {topics.map((topic) => {
                      const selected = currentGroup?.topicId === topic.id;
                      return (
                        <article key={topic.id} className={cn('group flex min-h-[190px] flex-col rounded-xl border p-4 transition-colors', selected ? 'border-primary bg-primary/5' : 'border-border/70 bg-card hover:border-primary/45 hover:bg-secondary/30')}>
                          <div className="flex items-start justify-between gap-3">
                            <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">{topic.maxGroups} {messages.thesis.groups.toLowerCase()}</span>
                            {selected ? <Check className="h-4 w-4 text-emerald-600" /> : <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />}
                          </div>
                          <h3 className="mt-4 line-clamp-2 text-base font-semibold leading-6 text-foreground">{topic.title}</h3>
                          <p className="mt-2 line-clamp-3 text-sm leading-6 text-muted-foreground">{topic.description}</p>
                          {currentGroup && !selected && currentGroup.status !== 'APPROVED' ? (
                            <Button type="button" size="sm" variant="outline" className="mt-auto w-full" onClick={() => void chooseTopic(topic.id)} disabled={isActionPending}>
                              {messages.thesis.chooseTopic}
                            </Button>
                          ) : null}
                        </article>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}

function MetricCard({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  tone: 'warm' | 'cool' | 'success' | 'violet';
}) {
  const toneClasses = {
    warm: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
    cool: 'bg-cyan-500/12 text-cyan-700 dark:text-cyan-300',
    success: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    violet: 'bg-violet-500/12 text-violet-700 dark:text-violet-300',
  };

  return (
    <Card variant="elevated">
      <CardContent className="flex items-start justify-between gap-4 pt-6">
        <div className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-lg', toneClasses[tone])}>{icon}</div>
        <div className="min-w-0 text-right">
          <div className="break-words text-2xl font-semibold tracking-tight text-foreground">{value}</div>
          <div className="mt-1 text-sm text-muted-foreground">{label}</div>
        </div>
      </CardContent>
    </Card>
  );
}
