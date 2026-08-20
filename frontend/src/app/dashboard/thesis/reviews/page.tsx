'use client';

import { useEffect, useState } from 'react';
import {
  Check,
  ClipboardCheck,
  Save,
  Scale,
  Star,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import {
  thesisApi,
  type ThesisCouncil,
  type ThesisGroup,
  type ThesisRound,
} from '@/lib/thesis-api';

interface ReviewDraft {
  score: string;
  comment: string;
}

export default function ThesisReviewsPage() {
  const { user, isLecturer, isAdmin } = useAuth();
  const { messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [selectedCouncilId, setSelectedCouncilId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [reviews, setReviews] = useState<Record<string, ReviewDraft>>({});

  const canReview = isLecturer || isAdmin;

  useEffect(() => {
    let cancelled = false;
    const loadRounds = async () => {
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
    void loadRounds();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (!selectedRoundId) {
      setCouncils([]);
      setGroups([]);
      return;
    }
    let cancelled = false;
    const loadWorkspace = async () => {
      setError('');
      try {
        const [councilData, groupData] = await Promise.all([
          thesisApi.listCouncils(selectedRoundId),
          thesisApi.listGroups(selectedRoundId),
        ]);
        if (cancelled) return;
        setCouncils(councilData);
        setGroups(groupData);
        setSelectedCouncilId((curr) => curr || councilData[0]?.id || '');
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };
    void loadWorkspace();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, selectedRoundId]);

  const selectedCouncil = councils.find((c) => c.id === selectedCouncilId);

  const updateReview = (groupId: string, patch: Partial<ReviewDraft>) => {
    setReviews((prev) => ({
      ...prev,
      [groupId]: { score: prev[groupId]?.score ?? '', comment: prev[groupId]?.comment ?? '', ...patch },
    }));
  };

  const submitReview = async (groupId: string) => {
    if (!selectedCouncilId) return;
    const draft = reviews[groupId];
    const score = Number(draft?.score);
    if (!draft || Number.isNaN(score) || score < 0 || score > 10) {
      setError(messages.thesis.review.invalidScore);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.submitReview(selectedCouncilId, groupId, score, draft.comment);
      setSuccess(messages.thesis.review.submitted);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  if (isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (!canReview) {
    return (
      <ErrorState
        title={messages.thesis.review.unauthorized}
        description={messages.thesis.review.unauthorizedDescription}
      />
    );
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
        eyebrow={<SectionEyebrow>{messages.thesis.review.eyebrow}</SectionEyebrow>}
        title={messages.thesis.review.title}
        description={messages.thesis.review.description}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row">
            <label className="flex min-w-[14rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {messages.thesis.selectRound}
              <select
                value={selectedRoundId}
                onChange={(e) => {
                  setSelectedRoundId(e.target.value);
                  setSelectedCouncilId('');
                }}
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
            <label className="flex min-w-[14rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {messages.thesis.review.selectCouncil}
              <select
                value={selectedCouncilId}
                onChange={(e) => setSelectedCouncilId(e.target.value)}
                className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={messages.thesis.review.selectCouncil}
              >
                {councils.map((council) => (
                  <option key={council.id} value={council.id}>
                    {council.room ?? council.id.slice(0, 8)} — {council.status}
                  </option>
                ))}
              </select>
            </label>
          </div>
        }
      />

      {success ? (
        <div className="flex items-center gap-3 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
          <Check className="h-4 w-4" />
          {success}
        </div>
      ) : null}
      {error ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      {!selectedCouncil ? (
        <EmptyState
          icon={Scale}
          title={messages.thesis.review.noCouncil}
          description={messages.thesis.review.noCouncilDescription}
        />
      ) : (
        <div className="space-y-6">
          <Card className="overflow-hidden border-foreground/10 bg-[hsl(var(--foreground))] text-[hsl(var(--background))]">
            <CardContent className="grid gap-6 p-6 sm:p-8 lg:grid-cols-[1fr_1fr] lg:items-center">
              <div className="space-y-3">
                <div className="inline-flex items-center gap-2 rounded-full border border-white/15 bg-white/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-white/75">
                  <ClipboardCheck className="h-3.5 w-3.5 text-[hsl(var(--accent-warm))]" />
                  {selectedCouncil.status}
                </div>
                <h2 className="text-2xl font-semibold tracking-tight sm:text-3xl">
                  {messages.thesis.review.councilReview}
                </h2>
              </div>
              <div className="space-y-3">
                {selectedCouncil.room ? (
                  <div className="border-l border-white/20 pl-4">
                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/55">
                      {messages.thesis.review.room}
                    </p>
                    <p className="mt-1 text-lg font-semibold">{selectedCouncil.room}</p>
                  </div>
                ) : null}
                <div className="border-l border-white/20 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-white/55">
                    {messages.thesis.review.members}
                  </p>
                  <p className="mt-1 text-sm text-white/75">
                    {selectedCouncil.members.length} {messages.thesis.review.memberCount}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>{messages.thesis.review.groupsTitle}</CardTitle>
              <CardDescription>{messages.thesis.review.groupsDescription}</CardDescription>
            </CardHeader>
            <CardContent>
              {groups.length === 0 ? (
                <EmptyState
                  icon={Users}
                  title={messages.thesis.review.noGroups}
                  description={messages.thesis.review.noGroupsDescription}
                />
              ) : (
                <div className="space-y-4">
                  {groups.map((group, idx) => {
                    const draft = reviews[group.id];
                    return (
                      <div
                        key={group.id}
                        className="rounded-xl border border-border/70 bg-card p-5"
                      >
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex items-center gap-3">
                            <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                              {idx + 1}
                            </div>
                            <div>
                              <p className="text-sm font-semibold text-foreground">
                                {messages.thesis.review.groupLabel} #{idx + 1}
                              </p>
                              <p className="font-mono text-xs text-muted-foreground">
                                {group.id.slice(0, 8)}…
                              </p>
                            </div>
                          </div>
                          <div className="flex items-center gap-1 rounded-full bg-amber-500/12 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:text-amber-300">
                            <Star className="h-3 w-3" />
                            {draft?.score || '-'} / 10
                          </div>
                        </div>
                        <div className="mt-4 grid gap-4 sm:grid-cols-[10rem_1fr]">
                          <label className="flex flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                            {messages.thesis.review.score}
                            <Input
                              type="number"
                              min="0"
                              max="10"
                              step="0.1"
                              placeholder="0–10"
                              value={draft?.score ?? ''}
                              onChange={(e) => updateReview(group.id, { score: e.target.value })}
                              className="normal-case tracking-normal"
                            />
                          </label>
                          <label className="flex flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                            {messages.thesis.review.comment}
                            <Textarea
                              placeholder={messages.thesis.review.commentPlaceholder}
                              value={draft?.comment ?? ''}
                              onChange={(e) => updateReview(group.id, { comment: e.target.value })}
                              rows={2}
                              className="normal-case tracking-normal"
                            />
                          </label>
                        </div>
                        <div className="mt-4 flex justify-end">
                          <Button
                            type="button"
                            size="sm"
                            onClick={() => void submitReview(group.id)}
                            disabled={isSaving}
                          >
                            <Save className="mr-2 h-4 w-4" />
                            {messages.thesis.review.submit}
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  );
}
