import type { ThesisGroup, ThesisRound } from './thesis-api';

/**
 * Picks the round a participant's thesis workspace should open on. The generic
 * default ("newest open round") lands a student whose group lives in a closed
 * or published round on an empty workspace that claims they have no group —
 * so a round containing one of the caller's own groups always wins. Groups are
 * expected newest-first; the newest non-cancelled membership decides, with a
 * cancelled-only history still preferring the latest round it touched.
 */
export function pickPreferredThesisRound(
  rounds: ThesisRound[],
  myGroups: ReadonlyArray<Pick<ThesisGroup, 'roundId' | 'status'>> | null | undefined,
): string | undefined {
  if (!myGroups || myGroups.length === 0 || rounds.length === 0) return undefined;
  const roundIds = new Set(rounds.map((round) => round.id));
  const usable = myGroups.filter((group) => roundIds.has(group.roundId));
  if (usable.length === 0) return undefined;
  const active = usable.filter((group) => group.status !== 'CANCELLED');
  return (active.length > 0 ? active[0] : usable[0]).roundId;
}

const ROUND_STATUS_PRIORITY: Readonly<Record<string, number>> = {
  REGISTRATION_OPEN: 0,
  REGISTRATION_CLOSED: 1,
  DEFENSE_SCHEDULED: 2,
  PROPOSAL_OPEN: 3,
  RESULTS_PUBLISHED: 4,
};

/**
 * Orders thesis rounds for staff/dean observation workspaces so active
 * registration/execution cohorts surface ahead of future proposal-only or
 * archived rounds, with KLTN preferred before other thesis types and newest
 * registrationStart breaking ties.
 */
export function sortThesisRoundsForOverview(
  rounds: ReadonlyArray<ThesisRound>,
): ThesisRound[] {
  return [...rounds].sort((a, b) => {
    const prioA = ROUND_STATUS_PRIORITY[a.status] ?? 5;
    const prioB = ROUND_STATUS_PRIORITY[b.status] ?? 5;
    if (prioA !== prioB) return prioA - prioB;
    const isKltnA = a.thesisType === 'KLTN' ? 0 : 1;
    const isKltnB = b.thesisType === 'KLTN' ? 0 : 1;
    if (isKltnA !== isKltnB) return isKltnA - isKltnB;
    return (
      new Date(b.registrationStart || 0).getTime() -
      new Date(a.registrationStart || 0).getTime()
    );
  });
}
