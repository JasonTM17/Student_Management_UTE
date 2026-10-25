/**
 * Round-schedule ordering rules, mirroring
 * `ThesisMutationService.createRound`/`updateRound` and the
 * `thesis_round_schedule_order_valid` check constraint so the admin form can
 * name the first violated rule instead of bouncing off the server. The rule
 * keys double as the `details.rule` values the server echoes back on a
 * THESIS_ROUND_SCHEDULE_ORDER rejection, so one copy map covers both paths.
 */
export type ThesisRoundScheduleRule =
  | 'REGISTRATION_WINDOW'
  | 'LECTURER_WINDOW'
  | 'REGISTRATION_START_BEFORE_LECTURER_END'
  | 'PROPOSAL_PUBLISH_BEFORE_LECTURER_END'
  | 'GVPB_DEADLINE_BEFORE_REGISTRATION_END'
  | 'REPORT_DATE_BEFORE_GVPB_DEADLINE'
  | 'DEFENSE_DATE_BEFORE_REPORT_DATE';

export const THESIS_ROUND_SCHEDULE_ORDER_CODE = 'THESIS_ROUND_SCHEDULE_ORDER';

const ORDER_RULES: readonly ThesisRoundScheduleRule[] = [
  'REGISTRATION_WINDOW',
  'LECTURER_WINDOW',
  'REGISTRATION_START_BEFORE_LECTURER_END',
  'PROPOSAL_PUBLISH_BEFORE_LECTURER_END',
  'GVPB_DEADLINE_BEFORE_REGISTRATION_END',
  'REPORT_DATE_BEFORE_GVPB_DEADLINE',
  'DEFENSE_DATE_BEFORE_REPORT_DATE',
];

export interface ThesisRoundScheduleInput {
  registrationStart: Date | null;
  registrationEnd: Date | null;
  lecturerSubmitStart: Date | null;
  lecturerSubmitEnd: Date | null;
  proposalPublishAt?: Date | null;
  gvpbDeadline?: Date | null;
  reportDate?: Date | null;
  defenseDate?: Date | null;
}

/** datetime-local holds local wall time; empty or malformed input is absent. */
export function parseThesisLocalInput(value: string | null | undefined): Date | null {
  if (!value) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

const time = (value: Date | null | undefined): number =>
  value instanceof Date ? value.getTime() : Number.NaN;

/**
 * Returns the first rule the schedule violates, evaluated in the same order
 * the server checks them (each window's bounds first, then the cross-window
 * ordering), or null when the schedule is valid. Within a window the end must
 * be strictly after the start; across windows equality is allowed, matching
 * `Instant.isBefore` semantics and the `>=` check constraint.
 */
export function firstThesisRoundScheduleViolation(
  input: ThesisRoundScheduleInput,
): ThesisRoundScheduleRule | null {
  const regStart = time(input.registrationStart);
  const regEnd = time(input.registrationEnd);
  if (!(regEnd > regStart)) return 'REGISTRATION_WINDOW';
  const letStart = time(input.lecturerSubmitStart);
  const letEnd = time(input.lecturerSubmitEnd);
  if (!(letEnd > letStart)) return 'LECTURER_WINDOW';
  if (regStart < letEnd) return 'REGISTRATION_START_BEFORE_LECTURER_END';
  const proposal = time(input.proposalPublishAt);
  if (!Number.isNaN(proposal) && proposal < letEnd) {
    return 'PROPOSAL_PUBLISH_BEFORE_LECTURER_END';
  }
  const gvpb = time(input.gvpbDeadline);
  if (!Number.isNaN(gvpb) && gvpb < regEnd) {
    return 'GVPB_DEADLINE_BEFORE_REGISTRATION_END';
  }
  const report = time(input.reportDate);
  if (!Number.isNaN(report) && !Number.isNaN(gvpb) && report < gvpb) {
    return 'REPORT_DATE_BEFORE_GVPB_DEADLINE';
  }
  const defense = time(input.defenseDate);
  if (!Number.isNaN(defense) && !Number.isNaN(report) && defense < report) {
    return 'DEFENSE_DATE_BEFORE_REPORT_DATE';
  }
  return null;
}

/**
 * Extracts the schedule rule a THESIS_ROUND_SCHEDULE_ORDER envelope carries in
 * `details.rule`; any other error — or an unknown rule name — yields null so
 * the caller falls back to the standard code/kind message mapping.
 */
export function thesisRoundScheduleErrorRule(error: unknown): ThesisRoundScheduleRule | null {
  const data = (error as { response?: { data?: unknown } | null } | undefined)
    ?.response?.data;
  if (typeof data !== 'object' || data === null) return null;
  const envelope = data as { code?: unknown; details?: { rule?: unknown } | null };
  if (envelope.code !== THESIS_ROUND_SCHEDULE_ORDER_CODE) return null;
  const rule = envelope.details?.rule;
  return ORDER_RULES.includes(rule as ThesisRoundScheduleRule)
    ? (rule as ThesisRoundScheduleRule)
    : null;
}
