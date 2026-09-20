'use client';

import { useEffect, useState } from 'react';
import { Check, Clock3, FileText, UsersRound } from 'lucide-react';
import { LinkButton } from '@/components/ui/link-button';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { cn } from '@/lib/utils';
import { thesisApi, type ThesisProgressResponse } from '@/lib/thesis-api';
import { useThesisWorkspace } from './useThesisWorkspace';

const PROGRESS_MILESTONES = [
  'ROUND_SELECTED',
  'GROUP_CREATED',
  'TOPIC_ASSIGNED',
  'GROUP_APPROVED',
  'REPORT_SUBMITTED',
  'COUNCIL_ASSIGNED',
  'SCORE_FINALIZED',
  'RESULTS_PUBLISHED',
] as const;

export default function ThesisProgressPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { locale, messages } = useI18n();
  const isStudent = Boolean(user?.roles?.includes('STUDENT'));
  const workspace = useThesisWorkspace('', { topicsEnabled: false, groupsEnabled: false });
  const selectedRound = workspace.selectedRound;
  const [progress, setProgress] = useState<ThesisProgressResponse | null>(null);
  const [progressLoading, setProgressLoading] = useState(false);
  const [progressError, setProgressError] = useState('');

  useEffect(() => {
    if (!isStudent || !selectedRound?.id) {
      setProgress(null);
      setProgressError('');
      return;
    }
    let cancelled = false;
    setProgressLoading(true);
    setProgressError('');
    void thesisApi.getMyProgress(selectedRound.id)
      .then((next) => {
        if (!cancelled) setProgress(next);
      })
      .catch(() => {
        if (!cancelled) {
          setProgress(null);
          setProgressError(messages.thesis.loadFailed);
        }
      })
      .finally(() => {
        if (!cancelled) setProgressLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isStudent, messages.thesis.loadFailed, selectedRound?.id]);

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (workspace.isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (workspace.error && workspace.rounds.length === 0) {
    return <ErrorState title={messages.thesis.loadFailed} description={workspace.error} onRetry={() => void workspace.reload()} />;
  }

  if (!selectedRound) {
    return (
      <div className="space-y-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{messages.thesis.navigation.progress}</SectionEyebrow>}
          title={messages.thesis.progressTitle}
          description={messages.thesis.progressDescription}
        />
        <EmptyState icon={Clock3} title={messages.thesis.noRound} description={messages.thesis.noTopicsDescription} />
      </div>
    );
  }

  if (!isStudent) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (progressLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (progressError || !progress) {
    return (
      <div className="space-y-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{messages.thesis.navigation.progress}</SectionEyebrow>}
          title={messages.thesis.progressTitle}
          description={messages.thesis.progressDescription}
        />
        <ErrorState
          title={messages.thesis.loadFailed}
          description={progressError || messages.thesis.loadFailed}
        />
      </div>
    );
  }

  const progressIndex = Math.max(PROGRESS_MILESTONES.indexOf(progress.currentMilestone), 0);
  const completedMilestones = new Set(progress.completedMilestones);
  const hasGroup = progress.participationState === 'PARTICIPATING';
  const attentionMessage = progress.attentionState === 'NOT_PARTICIPATING'
    ? messages.thesis.progressNotParticipating
    : progress.attentionState === 'RESULT_NOT_AVAILABLE'
      ? messages.thesis.progressResultNotAvailable
      : progress.attentionState === 'GROUP_INVALID_MEMBER_COUNT'
        ? messages.thesis.progressDataIntegrity
        : progress.attentionState === 'PROGRESS_INCONSISTENT'
          ? messages.thesis.progressInconsistent
        : progress.attentionState === 'GROUP_REJECTED'
          ? messages.thesis.progressGroupRejected
          : progress.attentionState === 'GROUP_CANCELLED'
            ? messages.thesis.progressGroupCancelled
            : '';

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.navigation.progress}</SectionEyebrow>}
        title={messages.thesis.progressTitle}
        description={messages.thesis.progressDescription}
        actions={
          <LinkButton href="/dashboard/thesis" variant="outline">
            {messages.thesis.backToWorkspace}
          </LinkButton>
        }
      />

      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <label className="flex min-w-[15rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
          {messages.thesis.selectRound}
          <select
            value={workspace.selectedRoundId}
            onChange={(event) => workspace.setSelectedRoundId(event.target.value)}
            className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
            aria-label={messages.thesis.selectRound}
          >
            {workspace.rounds.map((round) => <option key={round.id} value={round.id}>{round.name}</option>)}
          </select>
        </label>
        <div className="rounded-md bg-secondary px-3 py-1.5 text-sm font-medium text-foreground">
          {messages.thesis.progressCurrentStage}: {messages.thesis.progressSteps[Math.max(progressIndex, 0)]}
        </div>
      </div>

      {workspace.error ? <ErrorState title={messages.thesis.loadFailed} description={workspace.error} /> : null}

      <Card variant="muted">
        <CardHeader>
          <CardTitle>{selectedRound.name}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {messages.thesis.progressSteps.map((step, index) => {
            const completed = completedMilestones.has(PROGRESS_MILESTONES[index]);
            const current = progressIndex === index;
            return (
              <div key={step} className="flex items-start gap-4">
                <div className="flex flex-col items-center">
                  <div
                    className={cn(
                      'flex h-10 w-10 items-center justify-center rounded-full border text-sm font-semibold',
                      completed
                        ? 'border-primary bg-primary text-primary-foreground'
                        : current
                          ? 'border-primary bg-primary/10 text-primary'
                          : 'border-border bg-card text-muted-foreground',
                    )}
                  >
                    {completed ? <Check className="h-4 w-4" /> : index + 1}
                  </div>
                  {index < messages.thesis.progressSteps.length - 1 ? (
                    <div className={cn('mt-2 h-8 w-px', completed ? 'bg-primary' : 'bg-border')} />
                  ) : null}
                </div>
                <div className="min-w-0 flex-1 pb-4">
                  <p className={cn('font-semibold', current || completed ? 'text-foreground' : 'text-muted-foreground')}>
                    {step}
                  </p>
                  {current ? <p className="mt-1 text-sm leading-6 text-muted-foreground">{messages.thesis.roundStatus}: {workspace.statusLabel(progress.roundStatus)}</p> : null}
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      {attentionMessage ? (
        <div className="flex items-start gap-4 rounded-lg border border-status-warning/25 bg-status-warning/12 p-5">
          <Clock3 className="mt-0.5 h-5 w-5 shrink-0 text-status-warning" />
          <div>
            <p className="font-semibold text-foreground">{messages.thesis.progressAttention}</p>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">{attentionMessage}</p>
          </div>
        </div>
      ) : null}

      {!hasGroup ? (
        <div className="flex items-start gap-4 rounded-lg border border-status-warning/25 bg-status-warning/12 p-5">
          <UsersRound className="mt-0.5 h-5 w-5 shrink-0 text-status-warning" />
          <div>
            <p className="font-semibold text-foreground">{messages.thesis.noGroup}</p>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">{messages.thesis.progressGroupRequired}</p>
          </div>
        </div>
      ) : (
        <Card variant="elevated">
          <CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <FileText className="mt-0.5 h-5 w-5 text-primary" />
              <div>
                <p className="font-semibold text-foreground">{messages.thesis.groupsTitle}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  {progress.memberCount} {locale === 'vi' ? 'thành viên' : 'members'} · {workspace.statusLabel(progress.userReportedGroupStatus || progress.groupStatus || 'PENDING')}
                </p>
                {progress.topicTitle ? <p className="mt-1 text-sm text-muted-foreground">{progress.topicTitle}</p> : null}
              </div>
            </div>
            <LinkButton href="/dashboard/thesis" variant="outline">
              {messages.thesis.backToWorkspace}
            </LinkButton>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
