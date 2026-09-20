'use client';

import {
  ArrowUpRight,
  Building2,
  Check,
  CircleDot,
  Shield,
  Trash2,
  UserPlus,
  UsersRound,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState } from '@/components/ui/state-block';
import { LocalizedLink } from '@/components/LocalizedLink';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import type { I18nMessages } from '@/i18n/messages';
import { type ThesisGroup, type ThesisGroupMember } from '@/lib/thesis-api';
import { renderInlineBold } from '@/features/thesis/components/MetricCard';

/** A roster row with its display label resolved by the page (never invented here). */
export interface StudentGroupMemberRow extends ThesisGroupMember {
  displayName: string;
  identifier: string;
}

interface StudentGroupCardProps {
  messages: I18nMessages;
  group: ThesisGroup | null;
  groupStatusLabel: string;
  roleLabel: string;
  topicTitle: string | null;
  supervisors: string[];
  members: StudentGroupMemberRow[];
  canCreateGroup: boolean;
  canManageMembers: boolean;
  isActionPending: boolean;
  onCreateGroup: () => void;
  onAddMember: () => void;
  onRemoveMember: (studentId: string) => void | Promise<void>;
  /** The report panel stays with the page: it owns submission state and rights. */
  reportSection?: React.ReactNode;
}

export default function StudentGroupCard({
  messages,
  group,
  groupStatusLabel,
  roleLabel,
  topicTitle,
  supervisors,
  members,
  canCreateGroup,
  canManageMembers,
  isActionPending,
  onCreateGroup,
  onAddMember,
  onRemoveMember,
  reportSection,
}: StudentGroupCardProps) {
  const pageCopy = messages.thesisWorkflow.page;

  return (
    <Card variant="muted" className="h-full">
      <CardHeader>
        <CardTitle>{messages.thesis.groupsTitle}</CardTitle>
        <CardDescription>{messages.thesis.groupsDescription}</CardDescription>
      </CardHeader>
      <CardContent>
        {!group ? (
          <EmptyState
            icon={UsersRound}
            title={messages.thesis.noGroup}
            description={messages.thesis.noGroupDescription}
            action={
              canCreateGroup ? (
                <Button type="button" onClick={onCreateGroup} disabled={isActionPending}>
                  {messages.thesis.createGroup}
                  <ArrowUpRight className="ml-2 h-4 w-4" />
                </Button>
              ) : undefined
            }
            className="min-h-[280px] border-none bg-transparent px-0 py-0"
          />
        ) : (
          <div className="space-y-5">
            <div className="flex flex-col gap-3 rounded-xl border border-border/70 bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {messages.thesis.groupsTitle}
                  </span>
                  <span className="rounded-md bg-secondary px-2 py-0.5 text-xs font-medium text-foreground">
                    {groupStatusLabel}
                  </span>
                  <StatusBadge status={group.approvalStatus} variant="approval" />
                  <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[11px] font-semibold text-foreground">
                    {pageCopy.yourRoleLabel}{' '}
                    {roleLabel}
                  </span>
                </div>
                <h4 className="text-sm font-semibold text-foreground truncate">
                  {topicTitle ?? (
                    // Selection lives in the catalog now, so the empty
                    // state is an actionable link rather than a label.
                    <LocalizedLink
                      href="/dashboard/thesis/topics"
                      className="text-primary hover:underline"
                    >
                      {messages.thesis.chooseTopic}
                    </LocalizedLink>
                  )}
                </h4>
              </div>

              {canManageMembers && members.length < 4 ? (
                <Button
                  type="button"
                  size="sm"
                  onClick={onAddMember}
                  disabled={isActionPending}
                  className="shrink-0 gap-1.5"
                >
                  <UserPlus className="h-4 w-4" />
                  {messages.thesis.addMember}
                </Button>
              ) : null}
            </div>

            {/* Supervisor Info (Rule R3) */}
            {supervisors.length > 0 && (
              <div className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/20 bg-primary/[0.04] px-3 py-2 text-xs">
                <span className="font-bold text-foreground">{pageCopy.supervisorLabel}</span>
                <span className="font-semibold text-primary">{supervisors.join(' · ')}</span>
              </div>
            )}

            {/* Approval Status Guidance (Rule R4) */}
            {group.approvalStatus === 'APPROVED' ? (
              <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-800 dark:text-emerald-300 flex items-start gap-2">
                <Check className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400 mt-0.5" />
                <div>
                  {renderInlineBold(pageCopy.topicApprovedNotice)}
                </div>
              </div>
            ) : group.approvalStatus === 'PENDING' ? (
              <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-800 dark:text-amber-300 flex items-start gap-2">
                <CircleDot className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
                <div>
                  {renderInlineBold(pageCopy.topicPendingNotice)}
                </div>
              </div>
            ) : (
              <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive flex items-start gap-2">
                <Shield className="h-4 w-4 shrink-0 mt-0.5" />
                <div>
                  <strong>{pageCopy.topicRejectedLabel}</strong> {group.rejectionReason || pageCopy.rejectedTopicFallback}
                </div>
              </div>
            )}

            {/* Member List Header & Count */}
            <div className="space-y-3">
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium text-foreground">
                  {messages.thesis.memberCount.replace('{count}', String(members.length))}
                </span>
                <span className="text-xs text-muted-foreground">
                  {members.length >= 4 ? messages.thesis.maxMembersReached : ''}
                </span>
              </div>

              {/* Detailed member items */}
              <div className="space-y-2">
                {members.map((member) => (
                  <div
                    key={member.studentId || member.displayName}
                    className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-card p-3 shadow-2xs transition-colors hover:border-primary/40"
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                        {member.displayName.slice(0, 2).toUpperCase()}
                      </div>
                      <div className="min-w-0 space-y-0.5">
                        <div className="flex flex-wrap items-center gap-1.5">
                          <span className="text-sm font-semibold text-foreground">
                            {member.displayName}
                          </span>
                          {member.isLeader ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-amber-500/15 px-2 py-0.5 text-[11px] font-semibold text-amber-700 dark:text-amber-300">
                              <Shield className="h-3 w-3" />
                              {messages.thesis.leaderBadge}
                            </span>
                          ) : (
                            <span className="rounded-md bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                              {messages.thesis.memberBadge}
                            </span>
                          )}
                          {member.isExternal ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-indigo-500/15 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 dark:text-indigo-300">
                              <Building2 className="h-3 w-3" />
                              {messages.thesis.externalBadge}
                            </span>
                          ) : null}
                        </div>
                        <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
                          {member.identifier && !member.isExternal ? (
                            <span>MSSV: <strong className="font-mono text-foreground/80">{member.identifier}</strong></span>
                          ) : null}
                          {member.contact ? (
                            <span>{member.contact}</span>
                          ) : null}
                        </div>
                      </div>
                    </div>

                    {/* Remove button for leader during open registration */}
                    {canManageMembers && !member.isLeader ? (
                      <Button
                        type="button"
                        size="sm"
                        variant="ghost"
                        onClick={() => void onRemoveMember(member.studentId)}
                        disabled={isActionPending}
                        className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive hover:bg-destructive/10 shrink-0"
                        title={messages.thesis.removeMember}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>

            {reportSection}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
