'use client';

import { AlertCircle, CheckCircle2 } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';
import { fillCopy } from '@/features/thesis/components/thesis-helpers';
import type { ThesisEligibility } from '@/lib/thesis-api';

/** Compact caller-scoped registration eligibility banner. Render only when the
 * backend populated `round.eligibility` — it is present for student callers on
 * covered round types and absent otherwise. */
export function ThesisEligibilityBanner({ eligibility }: { eligibility: ThesisEligibility }) {
  const { messages } = useI18n();
  const creditsLine = fillCopy(messages.thesis.eligibilityCredits, {
    earned: String(eligibility.earnedCredits),
    required: String(eligibility.requiredCredits),
  });
  const cpaLine = eligibility.cpa == null
    ? messages.thesis.eligibilityCpaMissing
    : fillCopy(messages.thesis.eligibilityCpa, {
      cpa: String(eligibility.cpa),
      required: String(eligibility.requiredCpa),
    });
  return (
    <div
      role="status"
      className={eligibility.eligible
        ? 'flex flex-col gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/[0.06] p-4 text-sm sm:flex-row sm:items-center sm:justify-between'
        : 'flex flex-col gap-2 rounded-xl border border-amber-500/40 bg-amber-500/[0.08] p-4 text-sm sm:flex-row sm:items-center sm:justify-between'}
    >
      <div className="flex items-start gap-3">
        {eligibility.eligible ? (
          <CheckCircle2 className="mt-0.5 h-5 w-5 shrink-0 text-emerald-600 dark:text-emerald-400" />
        ) : (
          <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-amber-600 dark:text-amber-400" />
        )}
        <div className="space-y-0.5">
          <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
            {messages.thesis.eligibilityTitle}
          </p>
          <p className="font-semibold text-foreground">
            {eligibility.eligible
              ? messages.thesis.eligibilityEligible
              : messages.thesis.eligibilityIneligible}
          </p>
          <p className="text-xs text-muted-foreground">
            {creditsLine} · {cpaLine}
          </p>
        </div>
      </div>
      {!eligibility.eligible ? (
        <LocalizedLink
          href="/dashboard/register"
          className="shrink-0 rounded-md border border-amber-500/40 bg-background px-3 py-1.5 text-xs font-semibold text-foreground transition-colors hover:bg-amber-500/10"
        >
          {messages.thesis.eligibilityAction}
        </LocalizedLink>
      ) : null}
    </div>
  );
}
