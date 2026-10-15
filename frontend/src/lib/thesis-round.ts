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
