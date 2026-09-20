'use client';

import { Check, ExternalLink, FileDown, FileText, Shield, Users } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import type { I18nMessages } from '@/i18n/messages';
import type { Lecturer } from '@/types/api';
import {
  type ThesisCouncil,
  type ThesisCouncilScore,
  type ThesisGroupReport,
  type ThesisTopic,
} from '@/lib/thesis-api';
import { fillCopy } from '@/features/thesis/components/MetricCard';

interface CouncilDefenseWorkspaceProps {
  messages: I18nMessages;
  councils: ThesisCouncil[];
  myLecturerId: string;
  topics: ThesisTopic[];
  lecturers: Lecturer[];
  councilScores: Record<string, ThesisCouncilScore[]>;
  topicSupervisors: Record<string, string[]>;
  topicReports: Record<string, ThesisGroupReport | null>;
  draftScores: Record<string, string>;
  isActionPending: boolean;
  showProfileClaimNotice: boolean;
  profileClaimMissingLabel: string;
  onScoreDraftChange: (key: string, value: string) => void;
  onSubmitScore: (councilId: string, topicId: string) => void | Promise<void>;
  onFinalizeScore: (councilId: string, topicId: string) => void | Promise<void>;
  onDownloadReport: (
    groupId: string,
    report: ThesisGroupReport | null | undefined,
  ) => void | Promise<void>;
}

/** Council defense grading workspace (R6-R8); every mutation is owned by the page. */
export default function CouncilDefenseWorkspace({
  messages,
  councils,
  myLecturerId,
  topics,
  lecturers,
  councilScores,
  topicSupervisors,
  topicReports,
  draftScores,
  isActionPending,
  showProfileClaimNotice,
  profileClaimMissingLabel,
  onScoreDraftChange,
  onSubmitScore,
  onFinalizeScore,
  onDownloadReport,
}: CouncilDefenseWorkspaceProps) {
  const pageCopy = messages.thesisWorkflow.page;

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
        {showProfileClaimNotice ? (
          <p className="rounded-lg border border-status-warning/30 bg-status-warning/10 px-4 py-3 text-sm text-foreground">
            {profileClaimMissingLabel}
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
                    const sups = topicSupervisors[tid] || [];
                    const isSupervisor =
                      sups.includes(myLecturerId) ||
                      topic?.createdBy === myLecturerId;

                    const isFinalized = topic?.finalScore != null;
                    // Mirror the backend eligibility rule exactly
                    // (ThesisCouncilService.eligibleGraderCount): a
                    // member is ineligible only when a supervisor row
                    // exists for them. Excluding topic.createdBy here
                    // made the button enable before the server agreed
                    // and finalize then failed with SCORES_INCOMPLETE.
                    const eligibleGraderCount = (council.members || []).filter(
                      (m) => !sups.includes(m.lecturerId),
                    ).length;
                    const submittedCount = scores.length;
                    const canFinalize =
                      isChair &&
                      !isFinalized &&
                      submittedCount >= eligibleGraderCount &&
                      eligibleGraderCount > 0;

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
                              <p className="text-xs text-muted-foreground line-clamp-2">
                                {topic.description}
                              </p>
                            )}
                            {topicReports[tid] ? (
                              <div className="pt-1.5 flex items-center gap-2">
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
                                    <span>{topicReports[tid]?.fileName}</span>
                                  </button>
                                ) : null}
                                {topicReports[tid]?.note ? (
                                  <span className="text-[11px] text-muted-foreground italic truncate max-w-xs">
                                    ({topicReports[tid]?.note})
                                  </span>
                                ) : null}
                              </div>
                            ) : null}
                          </div>
                          {isFinalized ? (
                            <div className="flex items-center gap-1.5 rounded-md bg-status-success/15 px-3 py-1 text-xs font-semibold text-status-success-foreground border border-status-success/30 shrink-0">
                              <Check className="h-3.5 w-3.5" />
                              {messages.thesis.grading.finalizedBadge}:{' '}
                              {topic.finalScore}
                            </div>
                          ) : null}
                        </div>

                        {/* Supervisor Constraint Warning (R8) */}
                        {isSupervisor ? (
                          <div className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-3 text-xs text-status-warning-foreground flex items-center gap-2">
                            <Shield className="h-4 w-4 shrink-0 text-status-warning" />
                            <span>
                              {messages.thesis.grading.supervisorCannotGrade}
                            </span>
                          </div>
                        ) : !isFinalized ? (
                          /* Grading Input for Eligible Council Member */
                          <div className="flex flex-wrap items-center gap-3 pt-1">
                            <label className="text-xs font-medium text-foreground flex items-center gap-2">
                              {messages.thesis.grading.scoreInputLabel}:
                              <Input
                                type="number"
                                min="0"
                                max="10"
                                step="0.1"
                                placeholder="8.5"
                                value={draftScores[key] ?? ''}
                                onChange={(e) =>
                                  onScoreDraftChange(key, e.target.value)
                                }
                                className="h-8 w-24 text-xs font-semibold"
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
                        {scores.length > 0 && (
                          <div className="rounded-lg bg-background/60 p-2.5 text-xs space-y-1.5 border border-border/40">
                            <span className="font-semibold text-muted-foreground uppercase text-[10px] tracking-wider">
                              {messages.thesis.grading.scoresListTitle}:
                            </span>
                            <div className="flex flex-wrap gap-2">
                              {scores.map((s, sIdx) => {
                                const lect = lecturers.find(
                                  (l) => l.id === s.lecturerId,
                                );
                                const name = lect?.user
                                  ? `${lect.user.lastName} ${lect.user.firstName}`
                                  : s.lecturerId;
                                return (
                                  <span
                                    key={sIdx}
                                    className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-[11px] font-medium text-foreground border border-border/50"
                                  >
                                    {name}:{' '}
                                    <strong className="ml-1 text-primary">
                                      {s.score}
                                    </strong>
                                  </span>
                                );
                              })}
                            </div>
                          </div>
                        )}

                        {/* Chair Finalize Action */}
                        {isChair && !isFinalized && (
                          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-t border-border/40 pt-3">
                            <p className="text-xs text-muted-foreground">
                              {submittedCount >= eligibleGraderCount
                                ? messages.thesis.grading.readyToFinalize
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
