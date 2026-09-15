'use client';

import { useState } from 'react';
import { Loader2, Trash2, UserPlus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import { thesisApi, type ThesisGroup, type ThesisGroupMember, type ThesisStudentResult } from '@/lib/thesis-api';

interface SupervisedGroupMembersProps {
  group: ThesisGroup;
  /** Feedback item 5/11 UX: membership management only runs in an open round;
   *  the server still enforces this, the prop just pre-hides the controls. */
  roundOpen?: boolean;
  onChanged: (group: ThesisGroup) => void;
}

/**
 * Feedback items 5 and 11: the supervising lecturer completes and corrects a
 * group's roster. The server, not this component, decides who may manage
 * membership — a failed call surfaces its message here.
 */
export function SupervisedGroupMembers({ group, roundOpen = true, onChanged }: SupervisedGroupMembersProps) {
  const { messages, formatNumber } = useI18n();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ThesisStudentResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);
  const [externalName, setExternalName] = useState('');
  const [externalContact, setExternalContact] = useState('');
  const [isPending, setIsPending] = useState(false);
  const [error, setError] = useState('');

  const isFull = group.memberStudentIds.length >= 3;
  const members: ThesisGroupMember[] = group.members ?? [];

  const run = async (action: () => Promise<ThesisGroup>) => {
    setIsPending(true);
    setError('');
    try {
      onChanged(await action());
    } catch (requestError: unknown) {
      const code = (requestError as { response?: { data?: { message?: string } } })?.response?.data?.message;
      setError(code || messages.thesis.actionFailed);
    } finally {
      setIsPending(false);
    }
  };

  const search = async () => {
    const trimmed = query.trim();
    if (!trimmed) return;
    setIsSearching(true);
    setHasSearched(true);
    setError('');
    try {
      setResults(await thesisApi.searchStudents(trimmed));
    } catch {
      setResults([]);
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSearching(false);
    }
  };

  const addInternal = (studentId: string) => {
    void run(async () => {
      const next = await thesisApi.addMember(group.id, { studentId });
      setQuery('');
      setResults([]);
      setHasSearched(false);
      return next;
    });
  };

  const addExternal = () => {
    if (!externalName.trim()) return;
    void run(async () => {
      const next = await thesisApi.addMember(group.id, {
        displayName: externalName.trim(),
        contact: externalContact.trim() || undefined,
      });
      setExternalName('');
      setExternalContact('');
      return next;
    });
  };

  const remove = (studentId: string) => {
    void run(() => thesisApi.removeMember(group.id, studentId));
  };

  return (
    <div className="mt-2 rounded-lg border border-border/70 bg-card p-3">
      <button
        type="button"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary hover:underline"
      >
        <UserPlus className="h-3.5 w-3.5" />
        {messages.thesis.manageMembers}
        <span className="font-normal text-muted-foreground">
          ({formatNumber(group.memberStudentIds.length)}/3)
        </span>
      </button>

      {open ? (
        <div className="mt-3 space-y-3">
          <ul className="space-y-1.5">
            {members.map((member) => (
              <li
                key={member.studentId}
                className="flex items-center justify-between gap-2 rounded-md bg-secondary/40 px-2.5 py-1.5"
              >
                <span className="min-w-0 truncate text-xs text-foreground">
                  {member.displayName || member.studentNumber || member.studentId}
                  {member.studentNumber ? (
                    <span className="ml-1.5 font-mono text-[11px] text-muted-foreground">
                      {member.studentNumber}
                    </span>
                  ) : null}
                  {member.isLeader ? (
                    <span className="ml-1.5 rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold text-primary">
                      {messages.thesis.leaderBadge}
                    </span>
                  ) : null}
                </span>
                {member.isLeader ? null : (
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    className="h-7 w-7 p-0 text-destructive hover:bg-destructive/10"
                    onClick={() => remove(member.studentId)}
                    disabled={isPending}
                    aria-label={`${messages.thesis.removeMember}: ${member.displayName || member.studentId}`}
                  >
                    {isPending ? (
                      <Loader2 className="h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Trash2 className="h-3.5 w-3.5" />
                    )}
                  </Button>
                )}
              </li>
            ))}
          </ul>

          {!roundOpen ? (
            <p className="text-xs text-muted-foreground">
              {messages.thesis.membersClosedRound}
            </p>
          ) : isFull ? (
            <p className="text-xs text-muted-foreground">{messages.thesis.maxMembersReached}</p>
          ) : (
            <div className="space-y-2">
              <div className="flex gap-2">
                <Input
                  type="text"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      void search();
                    }
                  }}
                  placeholder={messages.thesis.memberSearchPlaceholder}
                  aria-label={messages.thesis.memberSearchPlaceholder}
                  disabled={isPending}
                  className="h-9 text-sm"
                />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => void search()}
                  disabled={isPending || !query.trim()}
                >
                  {isSearching ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
                  {messages.thesis.memberSearchAction}
                </Button>
              </div>

              {results.length > 0 ? (
                <ul className="space-y-1">
                  {results.slice(0, 5).map((student) => (
                    <li key={student.studentId} className="flex items-center justify-between gap-2">
                      <span className="min-w-0 truncate text-xs text-muted-foreground">
                        {[student.lastName, student.firstName].filter(Boolean).join(' ').trim() || student.email}
                        {student.studentNumber ? (
                          <span className="ml-1.5 font-mono text-[11px]">{student.studentNumber}</span>
                        ) : null}
                      </span>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="h-7"
                        onClick={() => addInternal(student.studentId)}
                        disabled={isPending}
                      >
                        {messages.thesis.memberAddAction}
                      </Button>
                    </li>
                  ))}
                </ul>
              ) : hasSearched && !isSearching ? (
                <p className="text-xs text-muted-foreground">{messages.thesis.memberSearchEmpty}</p>
              ) : null}

              <div className="flex flex-wrap items-center gap-2 border-t border-border/60 pt-2">
                <Input
                  type="text"
                  value={externalName}
                  onChange={(event) => setExternalName(event.target.value)}
                  placeholder={messages.thesis.externalMemberName}
                  aria-label={messages.thesis.externalMemberName}
                  disabled={isPending}
                  className="h-9 max-w-[13rem] text-sm"
                />
                <Input
                  type="text"
                  value={externalContact}
                  onChange={(event) => setExternalContact(event.target.value)}
                  placeholder={messages.thesis.externalMemberContact}
                  aria-label={messages.thesis.externalMemberContact}
                  disabled={isPending}
                  className="h-9 max-w-[13rem] text-sm"
                />
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={addExternal}
                  disabled={isPending || !externalName.trim()}
                  className={cn(!externalName.trim() && 'opacity-50')}
                >
                  {messages.thesis.memberAddAction}
                </Button>
              </div>
            </div>
          )}

          {error ? <p className="text-xs text-destructive">{error}</p> : null}
        </div>
      ) : null}
    </div>
  );
}
