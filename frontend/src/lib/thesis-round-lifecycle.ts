import type { ThesisPublishResultsPreview } from '@/lib/thesis-api';

export interface RegistrationOpeningSchedule {
  registrationStart: string;
  registrationEnd: string;
  lecturerSubmitEnd: string;
}

export interface RegistrationOpeningAvailability {
  disabled: boolean;
  reasonCode: 'REGISTRATION_EARLY_OPEN_BLOCKED' | 'REGISTRATION_WINDOW_CLOSED' | null;
  opensImmediately: boolean;
  effectiveStart: string;
  effectiveEnd: string;
}

/** Mirrors the server's early-open rule for an accessible admin explanation. */
export function registrationOpeningAvailability(
  schedule: RegistrationOpeningSchedule,
  now: number = Date.now(),
): RegistrationOpeningAvailability {
  const start = Date.parse(schedule.registrationStart);
  const end = Date.parse(schedule.registrationEnd);
  const lecturerEnd = Date.parse(schedule.lecturerSubmitEnd);
  const effectiveStart = Number.isFinite(start) && now < start
    ? new Date(now).toISOString()
    : schedule.registrationStart;

  if (!Number.isFinite(start) || !Number.isFinite(end) || now >= end) {
    return {
      disabled: true,
      reasonCode: 'REGISTRATION_WINDOW_CLOSED',
      opensImmediately: false,
      effectiveStart,
      effectiveEnd: schedule.registrationEnd,
    };
  }

  const opensImmediately = now < start;
  if (opensImmediately && (!Number.isFinite(lecturerEnd) || now < lecturerEnd)) {
    return {
      disabled: true,
      reasonCode: 'REGISTRATION_EARLY_OPEN_BLOCKED',
      opensImmediately,
      effectiveStart,
      effectiveEnd: schedule.registrationEnd,
    };
  }

  return {
    disabled: false,
    reasonCode: null,
    opensImmediately,
    effectiveStart,
    effectiveEnd: schedule.registrationEnd,
  };
}

export interface PublishResultsReadiness {
  approvedGroupCount: number;
  includedGroupCount: number;
  ungradedGroupCount: number;
  pendingApprovalGroupCount: number;
  openReviewTopics: unknown[];
}

export interface PublishResultsReadinessState {
  preview: ThesisPublishResultsPreview | null;
  loading: boolean;
  error: string;
}

export type PublishResultsReadinessByRound = Record<string, PublishResultsReadinessState>;

const EMPTY_PUBLISH_RESULTS_READINESS: PublishResultsReadinessState = {
  preview: null,
  loading: false,
  error: '',
};

export function publishResultsReadinessForRound(
  byRound: PublishResultsReadinessByRound,
  roundId: string,
): PublishResultsReadinessState {
  return byRound[roundId] ?? EMPTY_PUBLISH_RESULTS_READINESS;
}

export function mergePublishResultsReadiness(
  byRound: PublishResultsReadinessByRound,
  roundId: string,
  update: Partial<PublishResultsReadinessState>,
): PublishResultsReadinessByRound {
  return {
    ...byRound,
    [roundId]: {
      ...publishResultsReadinessForRound(byRound, roundId),
      ...update,
    },
  };
}

/** Estimate database wall time from its sample and the local response-receipt time. */
export interface DatabaseClockSample {
  serverNow: number;
  clientMonotonicAtReceipt: number;
}

export function databaseClockSample(
  serverNow: string | null | undefined,
  clientMonotonicAtReceipt: number,
): DatabaseClockSample | null {
  const parsed = Date.parse(serverNow ?? '');
  return Number.isFinite(parsed) ? { serverNow: parsed, clientMonotonicAtReceipt } : null;
}

export function estimatedServerNow(
  sample: DatabaseClockSample | null,
  clientMonotonicNow: number,
  fallbackClientNow: number = Date.now(),
): number {
  return sample
    ? sample.serverNow + Math.max(0, clientMonotonicNow - sample.clientMonotonicAtReceipt)
    : fallbackClientNow;
}

/** A GVPB extension must remain no later than the authored report date. */
export function gvpbDeadlineExtensionBlocker(
  reportDate: string | null | undefined,
  now: number = Date.now(),
): string | null {
  const reportDateAt = Date.parse(reportDate ?? '');
  return Number.isFinite(reportDateAt) && now >= reportDateAt ? reportDate ?? null : null;
}

export interface LateTopicAvailability {
  disabled: boolean;
  reasonCode: 'REGISTRATION_WINDOW_NOT_OPEN' | 'REGISTRATION_WINDOW_CLOSED' | null;
}

/** A late topic must be available before students lose the ability to register for it. */
export function lateTopicAvailability(
  status: string,
  registrationStart: string | null | undefined,
  registrationEnd: string | null | undefined,
  now: number = Date.now(),
): LateTopicAvailability {
  const start = Date.parse(registrationStart ?? '');
  const end = Date.parse(registrationEnd ?? '');
  if (!Number.isFinite(start)) {
    return { disabled: true, reasonCode: 'REGISTRATION_WINDOW_NOT_OPEN' };
  }
  if (!Number.isFinite(end) || now >= end) {
    return { disabled: true, reasonCode: 'REGISTRATION_WINDOW_CLOSED' };
  }
  if (status === 'REGISTRATION_OPEN' && now < start) {
    return { disabled: true, reasonCode: 'REGISTRATION_WINDOW_NOT_OPEN' };
  }
  return { disabled: false, reasonCode: null };
}

/** Keep UI disable reasons in the same order as the server publication gate. */
export function publishResultsBlockerCode(preview: PublishResultsReadiness): string | null {
  if (preview.openReviewTopics.length > 0) return 'REVIEW_WINDOW_OPEN';
  if (preview.approvedGroupCount <= 0) return 'RESULTS_NOT_READY';
  if (preview.includedGroupCount > 0 && preview.ungradedGroupCount === preview.includedGroupCount) {
    return 'RESULTS_NOT_READY';
  }
  if (preview.ungradedGroupCount > 0) return 'SCORES_INCOMPLETE';
  if (preview.pendingApprovalGroupCount > 0) return 'GROUPS_PENDING_APPROVAL';
  return null;
}
