'use client';

import { useEffect, useState } from 'react';
import { ArrowLeft, CheckCircle2, FileStack } from 'lucide-react';
import { useParams, useSearchParams } from 'next/navigation';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { thesisApi, type ThesisTopic } from '@/lib/thesis-api';
import { campusErrorCode } from '@/lib/campus-error';
import { useThesisWorkspace } from './useThesisWorkspace';

export default function ThesisTopicDetailPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth();
  const { messages, locale } = useI18n();
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const topicId = Array.isArray(params.id) ? params.id[0] : params.id;
  const workspace = useThesisWorkspace(searchParams.get('roundId') ?? '');
  // The fetched topic is paired with the id it was fetched for: without that,
  // navigating /topics/<A> → /topics/<B> kept rendering A's content under B's
  // URL while the Choose button assigned B (route id), so a student could read
  // one topic and commit to another.
  const [directTopic, setDirectTopic] = useState<{ id: string; topic: ThesisTopic | null } | null>(null);
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
  // rejects PROPOSALS_PUBLISHED), so the affordance must match exactly. A
  // cancelled group cannot pick — the backend answers 409, so the button is
  // replaced by an explanatory note instead of firing a doomed request.
  const roundAllowsChoice = workspace.selectedRound?.status === 'REGISTRATION_OPEN';
  const groupCancelled = myGroup?.status === 'CANCELLED';
  // An ineligible student cannot pick a topic either (same policy as group
  // creation); the thesis page's eligibility card carries the numbers.
  const callerEligible = workspace.selectedRound?.eligibility?.eligible !== false;
  const canChoose =
    isStudent && Boolean(myGroup) && !groupCancelled && !alreadyChosen
    && roundAllowsChoice && callerEligible;

  const chooseThisTopic = async () => {
    if (!myGroup) return;
    setIsChoosing(true);
    setActionError('');
    try {
      await thesisApi.assignTopic(myGroup.id, topicId);
      await workspace.refreshWorkspace();
    } catch (error) {
      const code = campusErrorCode(error);
      const codes = messages.common.campusErrors.codes as Record<string, string>;
      setActionError(
        code === 'TOPIC_FULL'
          ? messages.thesis.topicFull
          : (code ? codes[code] : undefined) ?? messages.thesis.actionFailed,
      );
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
          setDirectTopic({ id: topicId, topic: t });
          if (t?.roundId && t.roundId !== selectedRoundId) {
            setSelectedRoundId(t.roundId);
          }
        }
      })
      .catch(() => {
        if (!cancelled) setDirectTopic({ id: topicId, topic: null });
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

  const topic = topics.find((item) => item.id === topicId)
    ?? (directTopic !== null && directTopic.id === topicId ? directTopic.topic : null);

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

      {/* The page header above already carries the topic title and summary, so
          the banner stays purely visual instead of repeating them. */}
      <div className="relative h-40 overflow-hidden rounded-2xl border border-border/80 bg-card shadow-xs sm:h-48">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/images/banners/thesis_defense_hall.jpg"
          alt={locale === 'vi' ? 'Hội trường bảo vệ khóa luận HCMUTE' : 'HCMUTE thesis defense hall'}
          className="h-full w-full object-cover object-center transition duration-500 hover:scale-105"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />
        <div className="absolute bottom-2 left-3 rounded-md bg-background/85 px-2 py-0.5 text-[11px] font-semibold text-foreground backdrop-blur-xs">
          {locale === 'vi' ? 'Hội đồng Đánh giá Khóa luận HCMUTE' : 'HCMUTE Thesis Evaluation Council'}
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <Card variant="muted">
          <CardContent className="space-y-5 pt-6">
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
            ) : isStudent && groupCancelled ? (
              <p className="rounded-lg border border-border/70 bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
                {messages.thesis.progressGroupCancelled}
              </p>
            ) : isStudent && !myGroup ? (
              // Without a group the choose button can never appear, so the
              // student needs the actual prerequisite, not a silent dead end.
              <p className="rounded-lg border border-border/70 bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
                {messages.thesis.noGroup} {messages.thesis.noGroupDescription}
              </p>
            ) : null}
          </CardContent>
        </Card>

        <Card variant="elevated" className="h-fit">
          <CardHeader>
            <CardTitle className="text-lg">{messages.thesis.topicDetailTitle}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 text-sm">
            <div className="flex items-center justify-between gap-3 border-b border-border/70 pb-3">
              <span className="text-muted-foreground">{messages.thesis.detail.maxGroups}</span>
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
