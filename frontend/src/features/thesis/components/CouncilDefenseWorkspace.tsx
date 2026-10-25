'use client';

import { Check, ExternalLink, FileDown, FileText, Shield, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { ErrorState } from '@/components/ui/state-block';
import type { I18nMessages } from '@/i18n/messages';
import type { Lecturer } from '@/types/api';
import {
  type ThesisCouncil,
  type ThesisCouncilScore,
  type ThesisGroupReport,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';
import { fillCopy } from '@/features/thesis/components/thesis-helpers';
import { plainTextThesisDescription } from '@/lib/thesis-text';

interface CouncilDefenseWorkspaceProps {
  messages: I18nMessages;
  /** Selected round; once it is terminal (CANCELLED/CLOSED/RESULTS_PUBLISHED)
   * every mutation affordance below is suppressed — the workspace itself
   * stays readable. */
  round?: ThesisRound | null;
  councils: ThesisCouncil[];
  myLecturerId: string;
  topics: ThesisTopic[];
  lecturers: Lecturer[];
  /** Localized message while the council listing itself failed; an outage is
   * not an empty council list. */
  councilLoadError?: string;
  onRetryCouncils?: () => void;
  councilScores: Record<string, ThesisCouncilScore[]>;
  /** Keys whose score list failed to load — distinct from an empty list. */
  councilScoreErrors: Record<string, boolean>;
  topicSupervisors: Record<string, string[]>;
  topicReports: Record<string, ThesisGroupReport | null>;
  topicReportErrors: Record<string, boolean>;
  onRetryTopicReport: (topicId: string) => void | Promise<void>;
  draftScores: Record<string, string>;
  draftComments: Record<string, string>;
  isActionPending: boolean;
  showProfileClaimNotice: boolean;
  profileClaimMissingLabel: string;
  onScoreDraftChange: (key: string, value: string) => void;
  onCommentDraftChange: (key: string, value: string) => void;
  onSubmitScore: (councilId: string, topicId: string) => void | Promise<void>;
  onFinalizeScore: (councilId: string, topicId: string) => void | Promise<void>;
  onDownloadReport: (
    groupId: string,
    report: ThesisGroupReport | null | undefined,
  ) => void | Promise<void>;
}

/** Mirrors ThesisCouncilService.MIN_COUNCIL_SIZE: a finalize needs at least
 * three eligible graders. */
const MIN_ELIGIBLE_GRADERS = 3;

/** Council defense grading workspace (R6-R8); every mutation is owned by the page. */
export default function CouncilDefenseWorkspace({
  messages,
  round,
  councils,
  myLecturerId,
  topics,
  lecturers,
  councilLoadError,
  onRetryCouncils,
  councilScores,
  councilScoreErrors,
  topicSupervisors,
  topicReports,
  topicReportErrors,
  onRetryTopicReport,
  draftScores,
  draftComments,
  isActionPending,
  showProfileClaimNotice,
  profileClaimMissingLabel,
  onScoreDraftChange,
  onCommentDraftChange,
  onSubmitScore,
  onFinalizeScore,
  onDownloadReport,
}: CouncilDefenseWorkspaceProps) {
  const pageCopy = messages.thesisWorkflow.page;
  const roundTerminal = round?.status === 'CANCELLED'
    || round?.status === 'CLOSED'
    || round?.status === 'RESULTS_PUBLISHED';

  return (
    <Card className="rounded-xl border-border/80 shadow-xs">
      <CardHeader>
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2">
              <Users className="h-5 w-5 text-primary" />
              {messages.thesis.grading.title}
            </CardTitle>
            <CardDescription>{messages.thesis.grading.description}</CardDescription>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-6">
        {councilLoadError ? (
          <ErrorState
            title={messages.thesis.grading.title}
            description={councilLoadError}
            onRetry={onRetryCouncils}
            className="min-h-0"
          />
        ) : null}
        {showProfileClaimNotice ? (
          <p className="rounded-lg border border-status-warning/30 bg-status-warning/10 px-4 py-3 text-sm text-foreground">
            {profileClaimMissingLabel}
          </p>
        ) : null}
        {round?.status === 'CANCELLED' ? (
          <p className="rounded-lg border border-status-warning/30 bg-status-warning/10 px-4 py-3 text-sm text-foreground">
            {messages.thesis.grading.roundCancelled}
          </p>
        ) : null}
        {councils.length === 0 && !councilLoadError && !showProfileClaimNotice ? (
          <p className="rounded-lg border border-border/70 bg-muted/40 px-4 py-3 text-sm text-muted-foreground">
            {messages.thesis.councils.noVisibleCouncils}
          </p>
        ) : null}
        {councils.map((council) => {
          const myMembership = council.members?.find(
            (m) => m.lecturerId === myLecturerId,
          );
          const isChair = myMembership?.memberRole === 'CHAIR';
          const topicIds =
            (council as unknown as { topicIds?: string[] }).topicIds || [];

          return (
            <div
              key={council.id}
              className="rounded-xl border border-border/70 bg-card p-5 space-y-4"
            >
              <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between border-b border-border/50 pb-3">
                <div>
                  <h4 className="font-semibold text-foreground text-base">
                    {council.name}
                  </h4>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {pageCopy.yourRoleLabel}{' '}
                    <span className="font-medium text-primary">
                      {myMembership?.memberRole === 'CHAIR'
                        ? messages.thesis.councils.roleChair
                        : myMembership?.memberRole === 'SECRETARY'
                        ? messages.thesis.councils.roleSecretary
                        : myMembership
                        ? messages.thesis.councils.roleMember
                        : pageCopy.roleAdmin}
                    </span>
                  </p>
                </div>
                <span className="text-xs font-medium text-muted-foreground">
                  {fillCopy(pageCopy.assignedTopicsCount, { count: topicIds.length })}
                </span>
              </div>

              {topicIds.length === 0 ? (
                <p className="text-xs text-muted-foreground italic py-2">
                  {messages.thesis.councils.noAssignedTopics}
                </p>
              ) : (
                <div className="space-y-4">
                  {topicIds.map((tid: string) => {
                    const topic = topics.find((t) => t.id === tid);
                    const key = `${council.id}:${tid}`;
                    const scores = councilScores[key] || [];
                    const scoresFailed = Boolean(councilScoreErrors[key]);
                    const sups = topicSupervisors[tid] || [];
                    const isSupervisor = sups.includes(myLecturerId);

                    const isFinalized = topic?.finalScore != null;
                    // Mirror the backend eligibility rule exactly
                    // (ThesisCouncilService.eligibleGraderCount +
                    // requireNotSupervisor): a member is ineligible only
                    // when a supervisor row exists for them.
                    // topic.createdBy must not count — the creator can be
                    // removed as supervisor yet stays required for
                    // finalize, which would wedge the score count.
                    const eligibleGraderIds = new Set(
                      (council.members || [])
                        .filter((m) => !sups.includes(m.lecturerId))
                        .map((m) => m.lecturerId),
                    );
                    const eligibleGraderCount = eligibleGraderIds.size;
                    // Only unique DEFENSE rows from eligible members count —
                    // GVPB reviews, non-member submissions and duplicates must
                    // not inflate the total (backend
                    // submittedEligibleScoreCount does the same).
                    const submittedCount = new Set(
                      scores
                        .filter(
                          (s): s is ThesisCouncilScore & { lecturerId: string } =>
                            s.component === 'DEFENSE' &&
                            s.lecturerId != null &&
                            eligibleGraderIds.has(s.lecturerId),
                        )
                        .map((s) => s.lecturerId),
                    ).size;
                    const canFinalize =
                      isChair &&
                      !isSupervisor &&
                      !isFinalized &&
                      !scoresFailed &&
                      eligibleGraderCount >= MIN_ELIGIBLE_GRADERS &&
                      submittedCount >= eligibleGraderCount;

                    return (
                      <div
                        key={tid}
                        className="rounded-xl border border-border/60 bg-muted/20 p-4 space-y-3"
                      >
                        <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
                          <div className="space-y-1">
                            <h5 className="font-semibold text-foreground text-sm">
                              {topic?.title || tid}
                            </h5>
                            {topic?.description && (
                              <p className="text-xs text-muted-foreground line-clamp-2" title={plainTextThesisDescription(topic.description)}>
                                {plainTextThesisDescription(topic.description)}
                              </p>
                            )}
                            {topicReportErrors[tid] ? (
                              <div role="alert" className="flex flex-wrap items-center gap-2 pt-1.5 text-sm text-destructive">
                                <span>{messages.thesis.report.loadFailed}</span>
                                <Button type="button" variant="outline" size="sm" onClick={() => void onRetryTopicReport(tid)}>
                                  {messages.common.actions.retry}
                                </Button>
                              </div>
                            ) : topicReports[tid] ? (
                              <div className="pt-1.5 flex flex-wrap items-center gap-2">
                                {/* A report is a link, an attached document,
                                    or both (feedback item 7). */}
                                {topicReports[tid]?.url ? (
                                  <a
                                    href={topicReports[tid]?.url || undefined}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center gap-1.5 rounded-md border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline"
                                  >
                                    <FileText className="h-3.5 w-3.5" />
                                    <span>{pageCopy.thesisDocumentLabel} {topicReports[tid]?.title || pageCopy.thesisReportFallback}</span>
                                    <ExternalLink className="h-3 w-3" />
                                  </a>
                                ) : null}
                                {topicReports[tid]?.fileName ? (
                                  <button
                                    type="button"
                                    onClick={() => {
                                      const report = topicReports[tid];
                                      // The download route is group-scoped:
                                      // the topic report carries the group id.
                                      if (report?.groupId) {
                                        void onDownloadReport(report.groupId, report);
                                      }
                                    }}
                                    className="inline-flex items-center gap-1.5 rounded-md border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline"
                                  >
                                    <FileDown className="h-3.5 w-3.5" />
                                    <span className="max-w-[16rem] truncate" title={topicReports[tid]?.fileName}>{topicReports[tid]?.fileName}</span>
                                  </button>
                                ) : null}
                                {topicReports[tid]?.note ? (
                                  <span className="text-[11px] text-muted-foreground italic truncate max-w-xs" title={topicReports[tid]?.note}>
                                    ({topicReports[tid]?.note})
                                  </span>
                                ) : null}
                              </div>
                            ) : null}
                          </div>
                          {isFinalized ? (
                            <div className="flex items-center gap-1.5 rounded-md bg-status-success/15 px-3 py-1 text-xs font-semibold text-status-success-foreground border border-status-success/30 shrink-0">
                              <Check className="h-3.5 w-3.5" />
                              {messages.thesis.grading.finalizedBadge} (Trung bình cộng):{' '}
                              <span className="font-mono">{topic.finalScore}</span>
                            </div>
                          ) : null}
                        </div>

                        {/* Supervisor Constraint Warning (R8). The score input
                            is member-only: governance staff and non-member
                            lecturers would otherwise see an input the server
                            rejects with COUNCIL_MEMBER_REQUIRED. */}
                        {isSupervisor ? (
                          <div className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-3 text-xs text-status-warning-foreground flex items-center gap-2">
                            <Shield className="h-4 w-4 shrink-0 text-status-warning" />
                            <span>
                              {messages.thesis.grading.supervisorCannotGrade}
                            </span>
                          </div>
                        ) : !isFinalized && !roundTerminal && myMembership ? (
                          /* Grading Input for Eligible Council Member */
                          <div className="flex flex-wrap items-end gap-3 pt-1">
                            <label className="text-xs font-medium text-foreground flex items-center gap-2">
                              {messages.thesis.grading.scoreInputLabel}:
                              <Input
                                type="number"
                                min="0"
                                max="10"
                                step="0.1"
                                value={draftScores[key] ?? ''}
                                onChange={(e) =>
                                  onScoreDraftChange(key, e.target.value)
                                }
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
                                value={draftComments[key] ?? ''}
                                onChange={(e) =>
                                  onCommentDraftChange(key, e.target.value)
                                }
                                className="h-8 flex-1 text-xs"
                                disabled={isActionPending}
                              />
                            </label>
                            <Button
                              type="button"
                              size="sm"
                              className="h-8 text-xs"
                              onClick={() => void onSubmitScore(council.id, tid)}
                              disabled={isActionPending || !draftScores[key]}
                            >
                              {messages.thesis.grading.saveScore}
                            </Button>
                          </div>
                        ) : null}

                        {/* Scores Breakdown */}
                        {scoresFailed ? (
                          <p className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-2.5 text-xs text-status-warning-foreground">
                            {messages.thesis.grading.scoresLoadFailed}
                          </p>
                        ) : scores.length > 0 && (
                          <div className="rounded-lg bg-background/60 p-2.5 text-xs space-y-2 border border-border/40">
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <span className="font-semibold text-muted-foreground uppercase text-[11px] tracking-wider">
                                {messages.thesis.grading.scoresListTitle} ({scores.length} điểm đã chấm):
                              </span>
                              {(() => {
                                const defenseScores = scores.filter(
                                  (s) => s.component === 'DEFENSE' && s.score != null,
                                );
                                if (defenseScores.length > 0) {
                                  const rawAvg =
                                    defenseScores.reduce(
                                      (acc, curr) => acc + Number(curr.score),
                                      0,
                                    ) / defenseScores.length;
                                  const displayAvg = rawAvg.toFixed(2);
                                  return (
                                    <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300">
                                      <span>
                                        {isFinalized
                                          ? 'Điểm chốt (Trung bình cộng):'
                                          : 'Trung bình cộng tạm tính:'}
                                      </span>
                                      <span className="rounded bg-emerald-500/15 border border-emerald-500/30 px-2 py-0.5 font-mono text-sm font-bold text-emerald-700 dark:text-emerald-300">
                                        {isFinalized && topic?.finalScore != null
                                          ? topic.finalScore
                                          : displayAvg}
                                      </span>
                                    </span>
                                  );
                                }
                                return null;
                              })()}
                            </div>
                            <div className="space-y-1.5 pt-1">
                              {scores.map((s, sIdx) => {
                                const lect = lecturers.find(
                                  (l) => l.id === s.lecturerId,
                                );
                                const name = lect?.user
                                  ? `${lect.user.lastName} ${lect.user.firstName}`
                                  : s.lecturerId;
                                return (
                                  <div
                                    key={sIdx}
                                    className="flex flex-col sm:flex-row sm:items-center justify-between gap-1.5 rounded-md bg-muted/60 px-3 py-1.5 text-xs text-foreground border border-border/50"
                                  >
                                    <div className="flex items-center gap-2 shrink-0">
                                      <span className="font-semibold text-foreground">{name}:</span>
                                      <span className="rounded bg-primary/10 px-2 py-0.5 font-mono font-bold text-primary">
                                        {s.score}
                                      </span>
                                    </div>
                                    {s.comment ? (
                                      <span className="text-muted-foreground italic text-xs break-words sm:text-right">
                                        &ldquo;{s.comment}&rdquo;
                                      </span>
                                    ) : null}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}

                        {/* Chair Finalize Action — suppressed while the score
                            list failed to load so a stale 0-of-N count cannot
                            invite a doomed finalize request. */}
                        {isChair && !isFinalized && !scoresFailed && !roundTerminal && (
                          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-t border-border/40 pt-3">
                            <p className="text-xs text-muted-foreground">
                              {canFinalize
                                ? messages.thesis.grading.readyToFinalize
                                : eligibleGraderCount < MIN_ELIGIBLE_GRADERS
                                  ? messages.common.campusErrors.codes.COUNCIL_SIZE_INVALID
                                  : messages.thesis.grading.waitingGrades
                                      .replace('{submitted}', String(submittedCount))
                                      .replace('{total}', String(eligibleGraderCount))}
                            </p>
                            <Button
                              type="button"
                              size="sm"
                              variant={canFinalize ? 'default' : 'outline'}
                              onClick={() => void onFinalizeScore(council.id, tid)}
                              disabled={isActionPending || !canFinalize}
                            >
                              {messages.thesis.grading.finalizeButton}
                            </Button>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
