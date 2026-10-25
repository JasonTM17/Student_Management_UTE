'use client';

import { useEffect, useState } from 'react';
import type { I18nMessages } from '@/i18n/messages';
import { thesisApi, type ThesisScoreBreakdown } from '@/lib/thesis-api';
import { fillCopy } from '@/features/thesis/components/thesis-helpers';

interface ScoreBreakdownPanelProps {
  messages: I18nMessages;
  topicId: string;
  /** Bump to refetch after the caller mutates a score or finalizes. */
  refreshKey?: string | number;
}

interface ThesisApiErrorShape {
  response?: { status?: number; data?: { code?: string } | null } | null;
}

/**
 * Per-topic score transparency readout. The server scopes the payload to the
 * caller: staff see every grader's score, comment and the pending list;
 * supervisors see the same after publication; participant students see only
 * names, roles, the count and the average. Denied callers and unpublished
 * results render nothing — the panel must not leak existence.
 */
export default function ScoreBreakdownPanel({
  messages,
  topicId,
  refreshKey,
}: ScoreBreakdownPanelProps) {
  const copy = messages.thesis.breakdown;
  const [data, setData] = useState<ThesisScoreBreakdown | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setFailed(false);
    thesisApi
      .scoreBreakdown(topicId)
      .then((breakdown) => {
        if (!cancelled) {
          setData(breakdown);
        }
      })
      .catch((caught: unknown) => {
        if (cancelled) {
          return;
        }
        const shape = caught as ThesisApiErrorShape | undefined;
        const status = shape?.response?.status;
        const code = shape?.response?.data?.code;
        setData(null);
        // Denied, concealed, or publication-gated callers get no widget at
        // all; a real outage still surfaces instead of a silent gap.
        setFailed(
          status !== 401 &&
            status !== 403 &&
            status !== 404 &&
            code !== 'RESULTS_NOT_PUBLISHED',
        );
      });
    return () => {
      cancelled = true;
    };
  }, [topicId, refreshKey]);

  if (failed) {
    return (
      <p
        role="alert"
        className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-2.5 text-xs text-status-warning-foreground"
      >
        {copy.loadFailed}
      </p>
    );
  }
  if (!data || (data.graders.length === 0 && (data.pendingGraders?.length ?? 0) === 0)) {
    return null;
  }

  const roleLabel = (role: string): string => {
    if (role === 'CHAIR') return messages.thesis.councils.roleChair;
    if (role === 'SECRETARY') return messages.thesis.councils.roleSecretary;
    if (role === 'MEMBER') return messages.thesis.councils.roleMember;
    if (role === 'GVPB_REVIEWER') return copy.roleReviewer;
    return role;
  };

  const pending = data.pendingGraders ?? [];

  return (
    <div
      className="rounded-lg border border-border/40 bg-background/60 p-2.5 text-xs space-y-1.5"
      data-testid="score-breakdown"
    >
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span className="font-semibold uppercase text-[11px] tracking-wider text-muted-foreground">
          {copy.title}
        </span>
        {data.average != null ? (
          <span className="text-muted-foreground">
            {copy.average}: <strong className="text-primary">{data.average}</strong>
          </span>
        ) : null}
        {data.finalScore != null ? (
          <span className="text-muted-foreground">
            {messages.thesis.results.finalScore}:{' '}
            <strong className="text-primary">{data.finalScore}</strong>
          </span>
        ) : null}
        <span className="text-muted-foreground">
          {fillCopy(copy.graderCount, { count: data.graderCount })}
        </span>
      </div>
      <ul className="space-y-1">
        {data.graders.map((grader, index) => (
          <li key={index} className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
            <span className="font-medium text-foreground">
              {grader.displayName || copy.unknownGrader}
            </span>
            <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
              {roleLabel(grader.role)}
            </span>
            {grader.score != null ? (
              <strong className="text-primary">{grader.score}</strong>
            ) : null}
            {grader.countedInAverage === false ? (
              <span className="italic text-muted-foreground">({copy.notCounted})</span>
            ) : null}
            {grader.comment ? (
              <span className="basis-full italic text-muted-foreground line-clamp-2">
                — {grader.comment}
              </span>
            ) : null}
          </li>
        ))}
        {pending.map((grader, index) => (
          <li
            key={`pending-${index}`}
            className="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-muted-foreground"
          >
            <span>{grader.displayName || copy.unknownGrader}</span>
            <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-medium">
              {roleLabel(grader.role)}
            </span>
            <span className="rounded bg-status-warning/15 px-1.5 py-0.5 text-[10px] font-medium text-status-warning-foreground">
              {copy.statusPending}
            </span>
          </li>
        ))}
      </ul>
      {!data.complete && pending.length > 0 ? (
        <p
          role="status"
          className="rounded-md border border-status-warning/30 bg-status-warning/10 px-2 py-1 text-[11px] text-status-warning-foreground"
        >
          {fillCopy(copy.missingScores, {
            names: pending
              .map((grader) => grader.displayName || copy.unknownGrader)
              .join(', '),
          })}
        </p>
      ) : null}
    </div>
  );
}
