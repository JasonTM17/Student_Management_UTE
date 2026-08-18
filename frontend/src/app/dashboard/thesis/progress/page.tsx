'use client';

import { useEffect, useState } from 'react';
import {
  CalendarCheck,
  CheckCircle2,
  Circle,
  CircleDot,
  Clock,
  FileCheck,
  FileText,
  GraduationCap,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import { thesisApi, type ThesisGroup, type ThesisRound } from '@/lib/thesis-api';

interface Milestone {
  id: string;
  label: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  date?: string | null;
  done: boolean;
  active: boolean;
}

export default function ThesisProgressPage() {
  const { user, isStudent } = useAuth();
  const { formatDateTime, messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setIsLoading(true);
      setError('');
      try {
        const data = await thesisApi.listRounds();
        if (cancelled) return;
        setRounds(data);
        setSelectedRoundId((curr) => curr || data[0]?.id || '');
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (!selectedRoundId) {
      setGroups([]);
      return;
    }
    let cancelled = false;
    const load = async () => {
      try {
        const data = await thesisApi.listGroups(selectedRoundId);
        if (cancelled) return;
        setGroups(data);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, selectedRoundId]);

  const studentId = user?.studentId ?? '';
  const currentGroup = groups.find(
    (g) => g.leaderStudentId === studentId || g.memberStudentIds.includes(studentId),
  );
  const selectedRound = rounds.find((r) => r.id === selectedRoundId);

  const milestones: Milestone[] = [
    {
      id: 'proposal',
      label: messages.thesis.progress.proposal,
      description: messages.thesis.progress.proposalDescription,
      icon: FileText,
      done: true,
      active: false,
    },
    {
      id: 'approval',
      label: messages.thesis.progress.approval,
      description: messages.thesis.progress.approvalDescription,
      icon: FileCheck,
      done: !!currentGroup && currentGroup.approvalStatus !== 'PENDING',
      active: !!currentGroup && currentGroup.approvalStatus === 'PENDING',
    },
    {
      id: 'midterm',
      label: messages.thesis.progress.midterm,
      description: messages.thesis.progress.midtermDescription,
      icon: Users,
      date: selectedRound?.reportDate,
      done: false,
      active: currentGroup?.approvalStatus === 'APPROVED' && !currentGroup.topicId,
    },
    {
      id: 'defense',
      label: messages.thesis.progress.defense,
      description: messages.thesis.progress.defenseDescription,
      icon: GraduationCap,
      date: selectedRound?.defenseDate,
      done: false,
      active: false,
    },
    {
      id: 'result',
      label: messages.thesis.progress.result,
      description: messages.thesis.progress.resultDescription,
      icon: CalendarCheck,
      done: false,
      active: false,
    },
  ];

  const completedCount = milestones.filter((m) => m.done).length;
  const progressPercent = Math.round((completedCount / milestones.length) * 100);

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
        eyebrow={<SectionEyebrow>{messages.thesis.progress.eyebrow}</SectionEyebrow>}
        title={messages.thesis.progress.title}
        description={messages.thesis.progress.description}
        actions={
          isStudent ? (
            <label className="flex min-w-[15rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {messages.thesis.selectRound}
              <select
                value={selectedRoundId}
                onChange={(e) => setSelectedRoundId(e.target.value)}
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
          ) : undefined
        }
      />

      {!selectedRound ? (
        <EmptyState
          icon={Clock}
          title={messages.thesis.progress.noRound}
          description={messages.thesis.progress.noRoundDescription}
        />
      ) : (
        <>
          <Card className="overflow-hidden border-foreground/10 bg-[hsl(var(--foreground))] text-[hsl(var(--background))]">
            <CardContent className="grid gap-6 p-6 sm:p-8 lg:grid-cols-[1fr_auto] lg:items-center">
              <div className="space-y-3">
                <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-white/75">
                  <CircleDot className="h-3.5 w-3.5 text-[hsl(var(--accent-warm))]" />
                  {selectedRound.thesisType}
                </div>
                <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
                  {selectedRound.name}
                </h2>
              </div>
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/55">
                  {messages.thesis.progress.overall}
                </p>
                <p className="text-4xl font-semibold">{progressPercent}%</p>
                <div className="h-2 w-40 overflow-hidden rounded-full bg-white/15">
                  <div
                    className="h-full rounded-full bg-[hsl(var(--accent-warm))] transition-all"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{messages.thesis.progress.timeline}</CardTitle>
              <CardDescription>{messages.thesis.progress.timelineDescription}</CardDescription>
            </CardHeader>
            <CardContent>
              <ol className="relative space-y-0 border-l-2 border-border/60 pl-6">
                {milestones.map((milestone) => {
                  const Icon = milestone.icon;
                  return (
                    <li key={milestone.id} className="pb-8 last:pb-0">
                      <div
                        className={cn(
                          'absolute -left-[11px] flex h-5 w-5 items-center justify-center rounded-full border-2',
                          milestone.done
                            ? 'border-emerald-500 bg-emerald-500 text-white'
                            : milestone.active
                              ? 'border-[hsl(var(--accent-warm))] bg-[hsl(var(--accent-warm))] text-white'
                              : 'border-border bg-card text-muted-foreground',
                        )}
                      >
                        {milestone.done ? (
                          <CheckCircle2 className="h-3 w-3" />
                        ) : milestone.active ? (
                          <CircleDot className="h-3 w-3" />
                        ) : (
                          <Circle className="h-3 w-3" />
                        )}
                      </div>
                      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                        <div className="flex items-center gap-2">
                          <Icon
                            className={cn(
                              'h-4 w-4',
                              milestone.done
                                ? 'text-emerald-600'
                                : milestone.active
                                  ? 'text-[hsl(var(--accent-warm))]'
                                  : 'text-muted-foreground',
                            )}
                          />
                          <h3
                            className={cn(
                              'text-sm font-semibold',
                              milestone.done || milestone.active
                                ? 'text-foreground'
                                : 'text-muted-foreground',
                            )}
                          >
                            {milestone.label}
                          </h3>
                        </div>
                        {milestone.date ? (
                          <span className="text-xs text-muted-foreground">
                            {formatDateTime(milestone.date)}
                          </span>
                        ) : null}
                      </div>
                      <p className="mt-1 text-sm leading-6 text-muted-foreground">
                        {milestone.description}
                      </p>
                    </li>
                  );
                })}
              </ol>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
