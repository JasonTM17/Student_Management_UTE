'use client';

import { Check, ExternalLink, FileDown, FileText, UsersRound, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/state-block';
import { MemberAvatars } from '@/components/thesis/MemberAvatars';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { SupervisedGroupMembers } from '@/components/dashboard/thesis/SupervisedGroupMembers';
import type { I18nMessages } from '@/i18n/messages';
import { type ThesisGroup, type ThesisGroupReport } from '@/lib/thesis-api';

interface SupervisedGroupListProps {
  messages: I18nMessages;
  title: string;
  description: string;
  groups: ThesisGroup[];
  reportsByGroup: Record<string, ThesisGroupReport | null>;
  isActionPending: boolean;
  getTopicTitle: (topicId?: string | null) => string;
  isRoundOpen: (group: ThesisGroup) => boolean;
  onGroupUpdated: (group: ThesisGroup) => void;
  onApprove: (groupId: string) => void | Promise<void>;
  onReject: (groupId: string) => void;
  onDownloadReport: (
    groupId: string,
    report: ThesisGroupReport | null | undefined,
  ) => void | Promise<void>;
}

/** The R3 supervision roster for the selected round; approvals stay in the page. */
export default function SupervisedGroupList({
  messages,
  title,
  description,
  groups,
  reportsByGroup,
  isActionPending,
  getTopicTitle,
  isRoundOpen,
  onGroupUpdated,
  onApprove,
  onReject,
  onDownloadReport,
}: SupervisedGroupListProps) {
  const pageCopy = messages.thesisWorkflow.page;

  return (
    <Card variant="muted" className="border-primary/20">
      <CardHeader>
        <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <CardTitle>{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {groups.length} {messages.thesis.groups.toLowerCase()}
          </span>
        </div>
      </CardHeader>
      <CardContent>
        {groups.length === 0 ? (
          <EmptyState
            icon={UsersRound}
            title={messages.thesis.noSupervisedGroups}
            description={messages.thesis.supervisedGroupsDescription}
            className="min-h-[160px]"
          />
        ) : (
          <div className="divide-y divide-border/60">
            {groups.map((group) => {
              const topicTitle = getTopicTitle(group.topicId);
              const isPending =
                group.status === 'SUBMITTED' && group.approvalStatus === 'PENDING';
              return (
                <div
                  key={group.id}
                  className="flex flex-col gap-4 py-4 first:pt-0 last:pb-0 lg:flex-row lg:items-center lg:justify-between"
                >
                  <div className="min-w-0 flex-1 space-y-1.5">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusBadge status={group.status} />
                      <StatusBadge status={group.approvalStatus} variant="approval" />
                    </div>
                    <h4 className="truncate text-sm font-semibold text-foreground">
                      {topicTitle}
                    </h4>
                    <div className="flex items-center gap-3 text-xs text-muted-foreground">
                      <span>
                        {messages.thesis.memberCount.replace(
                          '{count}',
                          String(group.memberStudentIds.length),
                        )}
                      </span>
                      <MemberAvatars memberIds={group.memberStudentIds} max={3} />
                    </div>
                    {group.rejectionReason ? (
                      <p className="text-xs italic text-destructive">
                        {messages.thesis.rejectionReason}: {group.rejectionReason}
                      </p>
                    ) : null}

                    <SupervisedGroupMembers
                      group={group}
                      roundOpen={isRoundOpen(group)}
                      onChanged={onGroupUpdated}
                    />

                    {group.approvalStatus === 'APPROVED' ? (
                      <div className="mt-2 flex flex-wrap items-center gap-2">
                        {reportsByGroup[group.id] ? (
                          <>
                            {/* A report is a link, an attached document,
                                or both (feedback item 7). */}
                            {reportsByGroup[group.id]?.url ? (
                              <a
                                href={reportsByGroup[group.id]?.url || undefined}
                                target="_blank"
                                rel="noreferrer"
                                className="inline-flex items-center gap-1.5 rounded-lg border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline transition-colors"
                              >
                                <FileText className="h-3.5 w-3.5" />
                                <span>{pageCopy.thesisReportLabel} {reportsByGroup[group.id]?.title || pageCopy.projectDocumentsFallback}</span>
                                <ExternalLink className="h-3 w-3" />
                              </a>
                            ) : null}
                            {reportsByGroup[group.id]?.fileName ? (
                              <button
                                type="button"
                                onClick={() => void onDownloadReport(group.id, reportsByGroup[group.id])}
                                className="inline-flex items-center gap-1.5 rounded-lg border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline transition-colors"
                              >
                                <FileDown className="h-3.5 w-3.5" />
                                <span>{reportsByGroup[group.id]?.fileName}</span>
                              </button>
                            ) : null}
                          </>
                        ) : (
                          <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">
                            <FileText className="h-3 w-3" />
                            {pageCopy.reportNotSubmitted}
                          </span>
                        )}
                      </div>
                    ) : null}
                  </div>

                  <div className="flex shrink-0 items-center gap-2">
                    {isPending ? (
                      <>
                        <Button
                          type="button"
                          size="sm"
                          className="bg-emerald-600 text-white hover:bg-emerald-700"
                          onClick={() => void onApprove(group.id)}
                          disabled={isActionPending}
                        >
                          <Check className="mr-1.5 h-4 w-4" />
                          {messages.thesis.approve}
                        </Button>
                        <Button
                          type="button"
                          size="sm"
                          variant="destructive"
                          onClick={() => onReject(group.id)}
                          disabled={isActionPending}
                        >
                          <X className="mr-1.5 h-4 w-4" />
                          {messages.thesis.reject}
                        </Button>
                      </>
                    ) : null}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
