import type { ThesisGroup, ThesisRound } from './thesis-api';

/**
 * Picks the round a participant's thesis workspace should open on. The generic
 * default ("newest open round") lands a student whose group lives in a closed
 * or published round on an empty workspace that claims they have no group —
 * so a round containing one of the caller's own groups always wins. Groups are
 * expected newest-first; the newest non-cancelled membership decides, with a
 * cancelled-only history still preferring the latest round it touched.
 * A membership inside a cancelled *round* is never preferred: the seat is
 * void and pinning the student there would hide the rounds they can join.
 */
export function pickPreferredThesisRound(
  rounds: ThesisRound[],
  myGroups: ReadonlyArray<Pick<ThesisGroup, 'roundId' | 'status'>> | null | undefined,
): string | undefined {
  if (!myGroups || myGroups.length === 0 || rounds.length === 0) return undefined;
  const statusByRoundId = new Map(rounds.map((round) => [round.id, round.status]));
  const usable = myGroups.filter((group) => statusByRoundId.has(group.roundId));
  if (usable.length === 0) return undefined;
  const inLiveRound = (group: Pick<ThesisGroup, 'roundId'>) =>
    statusByRoundId.get(group.roundId) !== 'CANCELLED';
  const active = usable.filter(
    (group) => group.status !== 'CANCELLED' && inLiveRound(group),
  );
  if (active.length > 0) return active[0].roundId;
  const live = usable.find(inLiveRound);
  return live?.roundId;
}

/** The restore decision an admin has to make for a cancelled round. */
export interface RoundRestorePlan {
  /** The status a restore with no explicit choice returns to; null when the
   * admin must pick one of {@link explicitTargets} instead. */
  implicitTarget: ThesisRound['cancelledFromStatus'] | null;
  /** Statuses the admin may explicitly choose; empty means a plain confirm. */
  explicitTargets: ThesisRound['status'][];
  /** True when no implicit restore exists and a choice is mandatory. */
  requiresChoice: boolean;
}

/**
 * Mirrors the backend restore rules so the admin dialog only offers targets
 * the server would accept: a recorded marker replays itself (or, when it is
 * REGISTRATION_OPEN whose window already passed, can only land on
 * REGISTRATION_CLOSED); a legacy row without a recorded status restores to
 * DRAFT only when no live groups survive, otherwise the admin must pick a
 * target at or after REGISTRATION_OPEN — the phase the groups prove.
 */
export function planThesisRoundRestore(
  round: Pick<ThesisRound, 'status' | 'cancelledFromStatus' | 'registrationStart' | 'registrationEnd'>,
  hasLiveGroups: boolean,
  now: number = Date.now(),
): RoundRestorePlan {
  const none: RoundRestorePlan = { implicitTarget: null, explicitTargets: [], requiresChoice: false };
  if (round.status !== 'CANCELLED') return none;
  const recorded = round.cancelledFromStatus ?? null;
  const start = round.registrationStart ? Date.parse(round.registrationStart) : NaN;
  const end = round.registrationEnd ? Date.parse(round.registrationEnd) : NaN;
  const windowLive = Number.isFinite(start) && Number.isFinite(end) && now >= start && now < end;
  if (recorded) {
    if (recorded === 'REGISTRATION_OPEN' && !windowLive) {
      return { implicitTarget: null, explicitTargets: ['REGISTRATION_CLOSED'], requiresChoice: true };
    }
    return { implicitTarget: recorded, explicitTargets: [], requiresChoice: false };
  }
  if (!hasLiveGroups) {
    return { implicitTarget: 'DRAFT', explicitTargets: [], requiresChoice: false };
  }
  const targets: RoundRestorePlan['explicitTargets'] = windowLive
    ? ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED']
    : ['REGISTRATION_CLOSED'];
  return { implicitTarget: null, explicitTargets: targets, requiresChoice: true };
}

/**
 * The unfiltered round list and the CLOSED-only fetch overlap whenever the
 * backend already ships archived rounds in the default response, so merging
 * keeps the first occurrence of each id — otherwise the same round renders
 * twice and React keys collide in pickers and dashboards.
 */
export function mergeThesisRounds(
  ...lists: ReadonlyArray<ReadonlyArray<ThesisRound>>
): ThesisRound[] {
  const byId = new Map<string, ThesisRound>();
  for (const list of lists) {
    for (const round of list) {
      if (!byId.has(round.id)) byId.set(round.id, round);
    }
  }
  return [...byId.values()];
}

/**
 * Interactive round pickers stay on live rounds: a CLOSED (archived) round is
 * resolvable data — results and history still render for it — but it is not
 * something a user picks to work in. The already-selected id is kept so the
 * picker still names the archived round being viewed instead of going blank.
 */
export function roundsForPicker(
  rounds: ReadonlyArray<ThesisRound>,
  selectedId?: string,
): ThesisRound[] {
  return rounds.filter((round) => round.status !== 'CLOSED' || round.id === selectedId);
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
