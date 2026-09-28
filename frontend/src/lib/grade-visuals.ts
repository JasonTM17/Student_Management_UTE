import type { StudentTranscriptSemester } from '@/types/api';

/**
 * Pure helpers behind the grades-page visual layer (roadmap item 3.5,
 * "grades timeline"): score → colour tier, score → bar width, and the
 * transcript's per-semester rows reshaped into the compact progress-strip
 * cards. No React and no class-string logic beyond the tier → Tailwind map,
 * so `node --test` can exercise everything without a DOM.
 */

export type ScoreTier = 'strong' | 'fair' | 'weak';

/**
 * The 10-point bands the advisor spec asked for: ≥ 8 strong (green),
 * 5–7.9 fair (amber), < 5 weak (red). A score that is missing or not a
 * finite number has no tier — the caller renders nothing rather than
 * guessing a colour for an unpublished result.
 */
export function scoreTier(score: number | null | undefined): ScoreTier | null {
  if (typeof score !== 'number' || !Number.isFinite(score)) {
    return null;
  }
  if (score >= 8) {
    return 'strong';
  }
  if (score >= 5) {
    return 'fair';
  }
  return 'weak';
}

/**
 * Bar fill for a 0–10 score as a percentage of the track, clamped to
 * [0, 100] so out-of-range upstream values can never blow out the layout.
 * Missing scores yield 0 — callers hide the bar entirely in that case.
 */
export function scoreBarPercent(score: number | null | undefined): number {
  if (typeof score !== 'number' || !Number.isFinite(score)) {
    return 0;
  }
  return Math.min(100, Math.max(0, (score / 10) * 100));
}

/** Tier → solid bar fill, matching the status palette used across the portal. */
export const SCORE_TIER_BAR_CLASS: Record<ScoreTier, string> = {
  strong: 'bg-status-success',
  fair: 'bg-status-warning',
  weak: 'bg-status-danger',
};

export interface SemesterProgressCard {
  semesterId: string;
  semesterName: string;
  semesterNameEn: string | null;
  semesterNameVi: string | null;
  /** Credit-weighted 4.0 GPA, or null when the transcript carries no value. */
  gpa: number | null;
  creditsEarned: number;
}

/**
 * One compact card per transcript semester, in transcript order (the API
 * already returns semesters chronologically). A GPA of 0 with zero earned
 * credits is "no result yet", not a real 0.00 average, so it maps to null —
 * the strip then renders a dash instead of a fake GPA.
 */
export function semesterProgressCards(
  semesters: StudentTranscriptSemester[] | null | undefined,
): SemesterProgressCard[] {
  return (semesters ?? []).map((semester) => {
    const gpa =
      typeof semester.gpa === 'number'
      && Number.isFinite(semester.gpa)
      && semester.gpa > 0
        ? semester.gpa
        : null;
    const creditsEarned =
      typeof semester.creditsEarned === 'number' && Number.isFinite(semester.creditsEarned)
        ? semester.creditsEarned
        : 0;
    return {
      semesterId: semester.semesterId,
      semesterName: semester.semesterName,
      semesterNameEn: semester.semesterNameEn ?? null,
      semesterNameVi: semester.semesterNameVi ?? null,
      gpa,
      creditsEarned,
    };
  });
}
