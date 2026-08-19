'use client';

import { CalendarDays, ClipboardCheck, MapPin, UsersRound } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { useEffect, useState } from 'react';
import { thesisApi, type ThesisCouncil } from '@/lib/thesis-api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisEvaluationPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth();
  const { formatDateTime, messages } = useI18n();
  const workspace = useThesisWorkspace();
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [councilsLoading, setCouncilsLoading] = useState(false);
  const [councilsError, setCouncilsError] = useState('');

  useEffect(() => {
    if (!workspace.selectedRoundId) {
      setCouncils([]);
      return;
    }
    let cancelled = false;
    setCouncilsLoading(true);
    setCouncilsError('');
    void thesisApi
      .listCouncils(workspace.selectedRoundId)
      .then((result) => {
        if (!cancelled) setCouncils(result);
      })
      .catch(() => {
        if (!cancelled) setCouncilsError(messages.thesis.loadFailed);
      })
      .finally(() => {
        if (!cancelled) setCouncilsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, workspace.selectedRoundId]);

  if (authLoading || !hasAccess || workspace.isLoading || councilsLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (workspace.error && workspace.rounds.length === 0) {
    return <ErrorState title={messages.thesis.loadFailed} description={workspace.error} onRetry={() => void workspace.reload()} />;
  }

  if (!workspace.selectedRound) {
    return (
      <div className="space-y-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{messages.thesis.navigation.evaluation}</SectionEyebrow>}
          title={messages.thesis.evaluationTitle}
          description={messages.thesis.evaluationDescription}
        />
        <EmptyState icon={ClipboardCheck} title={messages.thesis.noRound} description={messages.thesis.noTopicsDescription} />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.navigation.evaluation}</SectionEyebrow>}
        title={messages.thesis.evaluationTitle}
        description={messages.thesis.evaluationDescription}
        actions={
          <LocalizedLink href="/dashboard/thesis">
            <Button variant="outline">{messages.thesis.backToWorkspace}</Button>
          </LocalizedLink>
        }
      />

      <label className="flex max-w-sm flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
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

      {councilsError ? <ErrorState title={messages.thesis.loadFailed} description={councilsError} /> : null}

      <Card variant="muted">
        <CardHeader>
          <CardTitle>{messages.thesis.councilsTitle}</CardTitle>
          <CardDescription>{workspace.selectedRound.name}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {councils.length === 0 ? (
            <EmptyState icon={CalendarDays} title={messages.thesis.noCouncils} description={messages.thesis.evaluationDescription} className="min-h-[220px]" />
          ) : (
            councils.map((council) => (
              <article key={council.id} className="rounded-lg border border-border/70 bg-card p-5">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-3">
                    <div className="flex items-center gap-3">
                      <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary"><UsersRound className="h-5 w-5" /></div>
                      <div>
                        <h2 className="font-semibold text-foreground">{messages.thesis.councilsTitle}</h2>
                        <p className="text-sm text-muted-foreground">{messages.thesis.councilStatus}: {workspace.statusLabel(council.status)}</p>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                      {council.scheduledAt ? <span className="inline-flex items-center gap-2"><CalendarDays className="h-4 w-4" />{formatDateTime(council.scheduledAt)}</span> : null}
                      {council.room ? <span className="inline-flex items-center gap-2"><MapPin className="h-4 w-4" />{messages.thesis.room}: {council.room}</span> : null}
                    </div>
                  </div>
                  <span className="rounded-full bg-secondary px-3 py-1.5 text-xs font-semibold text-foreground">
                    {workspace.statusLabel(council.status)}
                  </span>
                </div>
              </article>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
