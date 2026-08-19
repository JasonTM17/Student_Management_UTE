'use client';

import { ArrowLeft, FileStack } from 'lucide-react';
import { useParams, useSearchParams } from 'next/navigation';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicDetailPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth();
  const { messages } = useI18n();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const topicId = Array.isArray(params.id) ? params.id[0] : params.id;
  const workspace = useThesisWorkspace(searchParams.get('roundId') ?? '');
  const topic = workspace.topics.find((item) => item.id === topicId);

  if (authLoading || !hasAccess || workspace.isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (workspace.error && workspace.rounds.length === 0) {
    return <ErrorState title={messages.thesis.loadFailed} description={workspace.error} onRetry={() => void workspace.reload()} />;
  }

  if (!topic) {
    return (
      <EmptyState
        icon={FileStack}
        title={messages.thesis.noTopics}
        description={messages.thesis.noTopicsDescription}
        action={
          <LocalizedLink href="/dashboard/thesis/topics">
            <Button variant="outline">{messages.thesis.navigation.catalog}</Button>
          </LocalizedLink>
        }
      />
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.topicDetailTitle}</SectionEyebrow>}
        title={topic.title}
        description={messages.thesis.topicDetailDescription}
        actions={
          <LocalizedLink href={`/dashboard/thesis/topics?roundId=${workspace.selectedRoundId}`}>
            <Button variant="outline"><ArrowLeft className="mr-2 h-4 w-4" />{messages.thesis.navigation.catalog}</Button>
          </LocalizedLink>
        }
      />

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Card variant="muted">
          <CardHeader>
            <CardTitle>{topic.title}</CardTitle>
            <CardDescription>{topic.description}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="rounded-lg border border-primary/20 bg-primary/[0.035] p-5 text-sm leading-7 text-muted-foreground">
              {topic.description}
            </div>
            <LocalizedLink href={`/dashboard/thesis?roundId=${workspace.selectedRoundId}`}>
              <Button>{messages.thesis.chooseTopic}</Button>
            </LocalizedLink>
          </CardContent>
        </Card>

        <Card variant="elevated" className="h-fit">
          <CardHeader>
            <CardTitle className="text-lg">{messages.thesis.roundStatus}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            <div className="flex items-center justify-between gap-3 border-b border-border/70 pb-3">
              <span className="text-muted-foreground">{messages.thesis.groups}</span>
              <span className="font-semibold text-foreground">{topic.maxGroups}</span>
            </div>
            <div className="flex items-center justify-between gap-3">
              <span className="text-muted-foreground">{messages.thesis.roundStatus}</span>
              <span className="font-semibold text-primary">
                {workspace.selectedRound ? workspace.statusLabel(workspace.selectedRound.status) : '—'}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
