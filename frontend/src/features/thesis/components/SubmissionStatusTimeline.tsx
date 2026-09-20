'use client';

import { CircleDashed, MessageSquareText, Send, UploadCloud } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import type { StatusTone } from '@/components/ui/status';
import { cn } from '@/lib/utils';
import { formatReportFileSize, fillCopy } from '@/features/thesis/components/MetricCard';
import type { ThesisGroupReport } from '@/lib/thesis-api';

/**
 * Presentational "Trạng thái nộp" timeline from the Stitch board
 * `stitch-nop-bao-cao-desktop`: first submission, latest update, supervisor
 * feedback, plus the amber deadline countdown.
 *
 * It owns no state and fetches nothing: every string arrives through `copy`
 * (the i18n dictionary) and every value comes from the report/round the page
 * already loaded. A missing field renders a neutral state instead of a
 * placeholder date or an invented version number.
 */

/** All user-facing strings, resolved by the caller from `messages.thesisWorkflow.page`. */
export interface SubmissionStatusTimelineCopy {
  title: string;
  deadlineChip: string;
  overdueChip: string;
  notOpenChip: string;
  deadlineDateLabel: string;
  firstSubmissionLabel: string;
  latestUpdateLabel: string;
  submittedChip: string;
  updatedChip: string;
  feedbackLabel: string;
  awaitingChip: string;
  feedbackPendingNote: string;
  noSubmissionTitle: string;
  noSubmissionDescription: string;
  documentLabel: string;
}

interface SubmissionStatusTimelineProps {
  /** The group's report; `null` keeps the timeline in its not-submitted state. */
  report: ThesisGroupReport | null;
  /** The round's report-submission deadline, when the round publishes one. */
  deadlineAt?: string | null;
  /**
   * Whole days left until `deadlineAt`, resolved by the caller from the round
   * data. `null`/`undefined` means no deadline is configured, which must not
   * read as "overdue".
   */
  daysRemaining?: number | null;
  formatDateTime: (iso: string) => string;
  copy: SubmissionStatusTimelineCopy;
  locale: string;
}

interface TimelineEntry {
  /** Stable React key: `first-submission`, `latest-update` or `feedback`. */
  key: string;
  icon: LucideIcon;
  label: string;
  chipLabel: string;
  tone: StatusTone;
  timestamp?: string;
  documentName?: string | null;
  documentSize?: string | null;
  note?: string;
}

/**
 * Chip and dot colours come from the `status-*` design tokens. The shared
 * `statusToneClass` helper is not reused for the tint because its `/12`
 * background step is outside Tailwind's opacity scale and therefore never
 * generates CSS; the supported `/15` step is spelled out here instead.
 */
const CHIP_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success/15 border-status-success/30 text-status-success-foreground',
  warning: 'bg-status-warning/15 border-status-warning/30 text-status-warning-foreground',
  danger: 'bg-status-danger/15 border-status-danger/30 text-status-danger-foreground',
  info: 'bg-status-info/15 border-status-info/30 text-status-info-foreground',
  neutral: 'bg-status-neutral/15 border-status-neutral/30 text-status-neutral-foreground',
};

const DOT_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success',
  warning: 'bg-status-warning',
  danger: 'bg-status-danger',
  info: 'bg-status-info',
  neutral: 'bg-status-neutral',
};

const CHIP_BASE_CLASS =
  'inline-flex items-center rounded-md border px-2 py-0.5 text-[11px] font-semibold';

export default function SubmissionStatusTimeline({
  report,
  deadlineAt,
  daysRemaining,
  formatDateTime,
  copy,
  locale,
}: SubmissionStatusTimelineProps) {
  // `updatedAt` drifting away from `submittedAt` is the only version evidence
  // the API exposes, so the second node appears only after a real update.
  const isUpdated = Boolean(report?.updatedAt && report.updatedAt !== report.submittedAt);
  const documentName = report?.fileName ?? null;
  const documentSize = documentName ? formatReportFileSize(report?.fileSize, locale) : null;

  const entries: TimelineEntry[] = report
    ? [
        {
          key: 'first-submission',
          icon: Send,
          label: copy.firstSubmissionLabel,
          chipLabel: copy.submittedChip,
          tone: 'success',
          timestamp: report.submittedAt,
          documentName,
          documentSize,
        },
        ...(isUpdated
          ? [
              {
                key: 'latest-update',
                icon: UploadCloud,
                label: fillCopy(copy.latestUpdateLabel, { version: 2 }),
                chipLabel: copy.updatedChip,
                tone: 'info' as StatusTone,
                timestamp: report.updatedAt,
                documentName,
                documentSize,
              },
            ]
          : []),
        {
          key: 'feedback',
          icon: MessageSquareText,
          label: copy.feedbackLabel,
          // The report payload carries no feedback field yet, so the honest
          // state is "awaiting" rather than a fabricated review.
          chipLabel: copy.awaitingChip,
          tone: 'warning',
          note: copy.feedbackPendingNote,
        },
      ]
    : [];

  const deadlineTone: StatusTone =
    daysRemaining == null ? 'neutral' : daysRemaining < 0 ? 'danger' : 'warning';
  const deadlineLabel =
    daysRemaining == null
      ? copy.notOpenChip
      : daysRemaining < 0
        ? copy.overdueChip
        : fillCopy(copy.deadlineChip, { days: daysRemaining });

  return (
    <div className="rounded-xl border border-border/70 bg-card p-4">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
        <h5 className="flex items-center gap-2 text-sm font-semibold text-foreground">
          <CircleDashed className="h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
          {copy.title}
        </h5>
        <span className={cn(CHIP_BASE_CLASS, CHIP_CLASS[deadlineTone])}>{deadlineLabel}</span>
      </div>

      {entries.length === 0 ? (
        <div className="mt-3 rounded-lg border border-dashed border-border/70 bg-muted/40 p-3">
          <p className="text-sm font-medium text-foreground">{copy.noSubmissionTitle}</p>
          <p className="mt-0.5 text-xs leading-5 text-muted-foreground">
            {copy.noSubmissionDescription}
          </p>
        </div>
      ) : (
        <ol className="mt-4 space-y-4">
          {entries.map((entry, index) => {
            const Icon = entry.icon;
            const isLast = index === entries.length - 1;
            return (
              <li key={entry.key} className="relative pl-6">
                {!isLast ? (
                  <span
                    aria-hidden="true"
                    className="absolute left-[10px] top-5 -bottom-4 w-px bg-border"
                  />
                ) : null}
                <span
                  aria-hidden="true"
                  className={cn(
                    'absolute left-1 top-1 h-3 w-3 rounded-full ring-2 ring-card',
                    DOT_CLASS[entry.tone],
                  )}
                />
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="text-sm font-semibold text-foreground">{entry.label}</span>
                  <span className={cn(CHIP_BASE_CLASS, CHIP_CLASS[entry.tone])}>
                    {entry.chipLabel}
                  </span>
                </div>
                {entry.timestamp ? (
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {formatDateTime(entry.timestamp)}
                  </p>
                ) : null}
                {entry.documentName ? (
                  <div className="mt-2 flex items-center gap-1.5 rounded-md border border-border/60 bg-muted/40 px-2.5 py-1.5 text-xs text-muted-foreground">
                    <Icon className="h-3.5 w-3.5 shrink-0 text-primary" aria-hidden="true" />
                    <span className="text-[11px] font-semibold uppercase tracking-[0.08em]">
                      {copy.documentLabel}
                    </span>
                    <span className="min-w-0 truncate text-foreground" title={entry.documentName}>
                      {entry.documentName}
                    </span>
                    {entry.documentSize ? (
                      <span className="shrink-0">({entry.documentSize})</span>
                    ) : null}
                  </div>
                ) : null}
                {entry.note ? (
                  <p className="mt-2 rounded-md border border-dashed border-border/70 bg-muted/40 px-2.5 py-2 text-xs italic leading-5 text-muted-foreground">
                    {entry.note}
                  </p>
                ) : null}
              </li>
            );
          })}
        </ol>
      )}

      {deadlineAt ? (
        <div className="mt-4 flex items-center justify-between gap-2 border-t border-border/60 pt-3 text-xs">
          <span className="text-muted-foreground">{copy.deadlineDateLabel}</span>
          <span className="font-semibold text-foreground">{formatDateTime(deadlineAt)}</span>
        </div>
      ) : null}
    </div>
  );
}
