'use client';

import { ArrowUpRight, FileStack } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { cn } from '@/lib/utils';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicCatalogPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth();
  const { messages } = useI18n();
  const workspace = useThesisWorkspace();

  if (authLoading || !hasAccess || workspace.isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (workspace.error && workspace.rounds.length === 0) {
    return (
      <ErrorState
        title={messages.thesis.loadFailed}
        description={workspace.error}
        retryLabel={messages.thesis.retry}
        onRetry={() => void workspace.reload()}
      />
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.navigation.catalog}</SectionEyebrow>}
        title={messages.thesis.catalogTitle}
        description={messages.thesis.catalogDescription}
        actions={
          <LocalizedLink href="/dashboard/thesis">
            <Button variant="outline">{messages.thesis.backToWorkspace}</Button>
          </LocalizedLink>
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
            {workspace.rounds.map((round) => (
              <option key={round.id} value={round.id}>{round.name}</option>
            ))}
          </select>
        </label>
        {workspace.selectedRound ? (
          <span className="rounded-full bg-secondary px-3 py-1.5 text-sm font-medium text-foreground">
            {workspace.statusLabel(workspace.selectedRound.status)}
          </span>
        ) : null}
      </div>

      {workspace.error ? (
        <ErrorState title={messages.thesis.loadFailed} description={workspace.error} />
      ) : workspace.topics.length === 0 ? (
        <EmptyState
          icon={FileStack}
          title={messages.thesis.noTopics}
          description={messages.thesis.noTopicsDescription}
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
            <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {workspace.topics.map((topic) => (
              <LocalizedLink
                key={topic.id}
                href={`/dashboard/thesis/topics/${topic.id}?roundId=${workspace.selectedRoundId}`}
                className="group flex min-h-[210px] flex-col rounded-lg border border-border/70 bg-card p-5 transition-colors hover:border-primary/50 hover:bg-primary/[0.025] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                <div className="flex items-start justify-between gap-3">
                  <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                    {topic.maxGroups} {messages.thesis.groups.toLowerCase()}
                  </span>
                  <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                </div>
                <h2 className="mt-5 line-clamp-3 text-lg font-semibold leading-7 text-foreground">
                  {topic.title}
                </h2>
                <p className="mt-2 line-clamp-4 text-sm leading-6 text-muted-foreground">
                  {topic.description}
                </p>
                <span className={cn('mt-auto pt-5 text-sm font-semibold text-primary')}>
                  {messages.thesis.topicDetailTitle}
                </span>
              </LocalizedLink>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
