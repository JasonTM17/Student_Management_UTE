'use client';

import { FileCheck } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import type { I18nMessages } from '@/i18n/messages';
import type { ThesisCouncilScore, ThesisRound, ThesisTopic } from '@/lib/thesis-api';

interface GvpbReviewPanelProps {
  messages: I18nMessages;
  /** Topics whose gvpbLecturerId equals the caller's lecturer profile id. */
  reviewTopics: ThesisTopic[];
  round: ThesisRound | null;
  gvpbScores: Record<string, ThesisCouncilScore | null>;
  draftScores: Record<string, string>;
  draftComments: Record<string, string>;
  isActionPending: boolean;
  formatDateTime: (value: string | number | Date) => string;
  onScoreDraftChange: (topicId: string, value: string) => void;
  onCommentDraftChange: (topicId: string, value: string) => void;
  onSubmit: (topicId: string) => void | Promise<void>;
}

/** Counter-review (GVPB) workspace: the assigned reviewer grades each topic
 *  before the round's gvpbDeadline. Rendered only for topics the caller is
 *  assigned to review — the server enforces the same rule. */
export default function GvpbReviewPanel({
  messages,
  reviewTopics,
  round,
  gvpbScores,
  draftScores,
  draftComments,
  isActionPending,
  formatDateTime,
  onScoreDraftChange,
  onCommentDraftChange,
  onSubmit,
}: GvpbReviewPanelProps) {
  if (reviewTopics.length === 0) return null;
  const copy = messages.thesis.review;
  const deadline = round?.gvpbDeadline ?? null;
  const deadlinePassed = deadline != null && Date.parse(deadline) < Date.now();

  return (
    <Card className="rounded-xl border-border/80 shadow-xs">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <FileCheck className="h-5 w-5 text-primary" />
          {copy.title}
        </CardTitle>
        <CardDescription>
          {copy.description}
          {deadline ? ` · ${copy.deadline}: ${formatDateTime(deadline)}` : ''}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {reviewTopics.map((topic) => {
          const existing = gvpbScores[topic.id];
          const frozen = topic.finalScore != null || deadlinePassed;
          return (
            <div
              key={topic.id}
              className="rounded-xl border border-border/60 bg-muted/20 p-4 space-y-3"
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <h5 className="font-semibold text-foreground text-sm">{topic.title}</h5>
                  {topic.description ? (
                    <p className="text-xs text-muted-foreground line-clamp-2 mt-0.5" title={topic.description}>
                      {topic.description}
                    </p>
                  ) : null}
                </div>
                {existing ? (
                  <span className="shrink-0 rounded-md bg-status-success/15 px-2.5 py-1 text-xs font-semibold text-status-success-foreground border border-status-success/30">
                    {copy.submittedScore}: {existing.score}
                  </span>
                ) : null}
              </div>
              {frozen ? (
                <p className="text-xs text-muted-foreground italic">
                  {topic.finalScore != null ? copy.finalizedLocked : copy.deadlinePassed}
                </p>
              ) : (
                <div className="flex flex-wrap items-end gap-3">
                  <label className="text-xs font-medium text-foreground flex items-center gap-2">
                    {messages.thesis.grading.scoreInputLabel}:
                    <Input
                      type="number"
                      min="0"
                      max="10"
                      step="0.1"
                      placeholder="8.5"
                      value={draftScores[topic.id] ?? ''}
                      onChange={(e) => onScoreDraftChange(topic.id, e.target.value)}
                      className="h-8 w-24 text-xs font-semibold"
                      disabled={isActionPending}
                    />
                  </label>
                  <label className="text-xs font-medium text-foreground flex items-center gap-2 flex-1 min-w-[200px]">
                    {messages.thesis.grading.commentInputLabel}:
                    <Input
                      type="text"
                      maxLength={1000}
                      placeholder={messages.thesis.grading.commentPlaceholder}
                      value={draftComments[topic.id] ?? ''}
                      onChange={(e) => onCommentDraftChange(topic.id, e.target.value)}
                      className="h-8 flex-1 text-xs"
                      disabled={isActionPending}
                    />
                  </label>
                  <Button
                    type="button"
                    size="sm"
                    className="h-8 text-xs"
                    onClick={() => void onSubmit(topic.id)}
                    disabled={isActionPending || !draftScores[topic.id]}
                  >
                    {existing ? copy.updateScore : messages.thesis.grading.saveScore}
                  </Button>
                </div>
              )}
              {existing?.comment ? (
                <p className="text-xs text-muted-foreground italic">
                  {copy.yourComment}: {existing.comment}
                </p>
              ) : null}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
