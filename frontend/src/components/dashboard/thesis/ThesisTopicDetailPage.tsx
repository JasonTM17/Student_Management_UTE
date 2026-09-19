'use client';

import { useEffect, useState } from 'react';
import { ArrowLeft, CheckCircle2, FileStack } from 'lucide-react';
import { useParams, useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
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
  const [isChoosing, setIsChoosing] = useState(false);
  const [actionError, setActionError] = useState('');

  /**
   * Topic selection belongs to the student who will work on it. Lecturers
   * propose topics, so they never get a selection control here.
   */
  const isStudent = Boolean(user?.roles?.includes('STUDENT'));
  const myGroup = workspace.currentGroup;
  const groupTopicId = myGroup?.topicId ?? null;
  const alreadyChosen = Boolean(groupTopicId) && groupTopicId === topicId;
  // The backend assigns topics only while registration is open (assignTopic
  // rejects PROPOSALS_PUBLISHED), so the affordance must match exactly.
  const roundAllowsChoice = workspace.selectedRound?.status === 'REGISTRATION_OPEN';
  const canChoose = isStudent && Boolean(myGroup) && !alreadyChosen && roundAllowsChoice;

  const chooseThisTopic = async () => {
    if (!myGroup) return;
    setIsChoosing(true);
    setActionError('');
    try {
      await thesisApi.assignTopic(myGroup.id, topicId);
      await workspace.refreshWorkspace();
    } catch {
      setActionError(messages.thesis.actionFailed);
    } finally {
      setIsChoosing(false);
    }
  };

  const { topics, selectedRoundId, setSelectedRoundId } = workspace;

  useEffect(() => {
    if (!topicId || topics.some((item) => item.id === topicId)) {
      return;
    }
    let cancelled = false;
    setDirectLoading(true);
    thesisApi
      .getTopic(topicId)
      .then((t) => {
        if (!cancelled) {
          setDirectTopic(t);
          if (t?.roundId && t.roundId !== selectedRoundId) {
            setSelectedRoundId(t.roundId);
          }
        }
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
  }, [topicId, topics, selectedRoundId, setSelectedRoundId]);

  useEffect(() => {
    const matched = topics.find((item) => item.id === topicId);
    if (matched?.roundId && matched.roundId !== selectedRoundId) {
      setSelectedRoundId(matched.roundId);
    }
  }, [topicId, topics, selectedRoundId, setSelectedRoundId]);

  const topic = topics.find((item) => item.id === topicId) ?? directTopic;

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
            <CardDescription>{messages.thesis.topicDetailDescription}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            {/* This is the page students read a topic on, so the description
                renders as rich content rather than escaped plain text. */}
            <div className="rich-html-content rounded-lg border border-primary/20 bg-primary/[0.035] p-5 text-sm leading-7 text-muted-foreground">
              <RichContentRenderer content={topic.description} />
            </div>
            {alreadyChosen ? (
              <span className="inline-flex items-center gap-2 rounded-lg border border-primary/30 bg-primary/[0.06] px-3 py-2 text-sm font-semibold text-primary">
                <CheckCircle2 className="h-4 w-4" />
                {messages.thesis.topicAlreadyChosen}
              </span>
            ) : canChoose ? (
              <div className="space-y-2">
                <Button onClick={() => void chooseThisTopic()} disabled={isChoosing}>
                  {isChoosing ? messages.thesis.choosingTopic : messages.thesis.chooseTopic}
                </Button>
                {actionError ? <p className="text-sm text-destructive">{actionError}</p> : null}
              </div>
            ) : null}
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
