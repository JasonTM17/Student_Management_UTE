'use client';

import { useEffect, useState } from 'react';
import { ArrowLeft, FileStack } from 'lucide-react';
import { useParams, useSearchParams } from 'next/navigation';
import { LinkButton } from '@/components/ui/link-button';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { thesisApi, type ThesisTopic } from '@/lib/thesis-api';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicDetailPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages } = useI18n();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const topicId = Array.isArray(params.id) ? params.id[0] : params.id;
  const workspace = useThesisWorkspace(searchParams.get('roundId') ?? '');
  const [directTopic, setDirectTopic] = useState<ThesisTopic | null>(null);
  const [directLoading, setDirectLoading] = useState(false);

  useEffect(() => {
    if (!topicId || workspace.topics.some((item) => item.id === topicId)) {
      return;
    }
    let cancelled = false;
    setDirectLoading(true);
    thesisApi
      .getTopic(topicId)
      .then((t) => {
        if (!cancelled) setDirectTopic(t);
      })
      .catch(() => {
        if (!cancelled) setDirectTopic(null);
      })
      .finally(() => {
        if (!cancelled) setDirectLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [topicId, workspace.topics]);

  const topic = workspace.topics.find((item) => item.id === topicId) ?? directTopic;

  if (authLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (workspace.isLoading || (directLoading && !topic)) {
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
          <LinkButton href="/dashboard/thesis/topics" variant="outline">
            {messages.thesis.navigation.catalog}
          </LinkButton>
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
          <LinkButton
            href={`/dashboard/thesis/topics?roundId=${workspace.selectedRoundId}`}
            variant="outline"
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            {messages.thesis.navigation.catalog}
          </LinkButton>
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
            <LinkButton
              href={`/dashboard/thesis?roundId=${workspace.selectedRoundId}&topicId=${topic.id}`}
            >
              {messages.thesis.chooseTopic}
            </LinkButton>
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
              {workspace.selectedRound ? (
                <StatusBadge status={workspace.selectedRound.status} />
              ) : (
                <span className="font-semibold text-foreground">
                  {messages.common.statuses.UNKNOWN}
                </span>
              )}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
