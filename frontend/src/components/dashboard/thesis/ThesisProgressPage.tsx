'use client';

import { Check, Clock3, FileText, UsersRound } from 'lucide-react';
import { LinkButton } from '@/components/ui/link-button';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { cn } from '@/lib/utils';
import { useThesisWorkspace } from './useThesisWorkspace';

function getProgressIndex(
  hasRound: boolean,
  hasGroup: boolean,
  hasTopic: boolean,
  groupStatus?: string,
  roundStatus?: string,
) {
  if (groupStatus === 'COMPLETED' || roundStatus === 'CLOSED') return 4;
  if (groupStatus === 'APPROVED' || roundStatus === 'PROPOSALS_PUBLISHED') return 3;
  if (hasTopic) return 2;
  if (hasGroup) return 1;
  return hasRound ? 0 : -1;
}

export default function ThesisProgressPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth();
  const { messages } = useI18n();
  const workspace = useThesisWorkspace();
  const selectedRound = workspace.selectedRound;
  const progressIndex = getProgressIndex(
    Boolean(selectedRound),
    Boolean(workspace.currentGroup),
    Boolean(workspace.currentGroup?.topicId),
    workspace.currentGroup?.status,
    selectedRound?.status,
  );

  if (authLoading || !hasAccess || workspace.isLoading) {
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
        <div className="rounded-full bg-secondary px-3 py-1.5 text-sm font-medium text-foreground">
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
            const completed = progressIndex > index;
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
                  {current ? <p className="mt-1 text-sm leading-6 text-muted-foreground">{messages.thesis.roundStatus}: {workspace.statusLabel(selectedRound.status)}</p> : null}
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      {!workspace.currentGroup ? (
        <div className="flex items-start gap-4 rounded-lg border border-amber-500/25 bg-amber-500/10 p-5">
          <UsersRound className="mt-0.5 h-5 w-5 shrink-0 text-amber-700 dark:text-amber-300" />
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
                  {workspace.currentGroup.memberStudentIds.length} {messages.thesis.groups.toLowerCase()} · {workspace.statusLabel(workspace.currentGroup.status)}
                </p>
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
