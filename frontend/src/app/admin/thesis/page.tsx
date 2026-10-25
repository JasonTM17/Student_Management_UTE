'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Archive,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronUp,
  CircleDot,
  FileStack,
  ExternalLink,
  Plus,
  RefreshCw,
  Trash2,
  UserPlus,
  Users,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminMetricCard } from '@/components/admin/AdminSurface';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import { cn } from '@/lib/utils';
import { toThesisLocalInput as toLocalInput } from '@/lib/thesis-datetime';
import { mergeThesisRounds, planThesisRoundRestore, sortThesisRoundsForOverview } from '@/lib/thesis-round';
import {
  firstThesisRoundScheduleViolation,
  parseThesisLocalInput,
  thesisRoundScheduleErrorRule,
} from '@/lib/thesis-round-schedule';
import {
  assignTopicConflict,
  councilMemberConflict,
  reviewerConflict,
} from '@/lib/thesis-picker-rules';
import {
  EmptyState,
  ForbiddenState,
  LoadingState,
} from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { useI18n } from '@/i18n';
import { fetchAllPages, lecturersApi } from '@/lib/api';
import { campusCodeMessage } from '@/lib/campus-error';
import {
  databaseClockSample,
  estimatedServerNow,
  gvpbDeadlineExtensionBlocker,
  lateTopicAvailability,
  mergePublishResultsReadiness,
  publishResultsBlockerCode,
  publishResultsReadinessForRound,
  registrationOpeningAvailability,
} from '@/lib/thesis-round-lifecycle';
import type {
  DatabaseClockSample,
  PublishResultsReadinessState,
} from '@/lib/thesis-round-lifecycle';
import type { Lecturer } from '@/types/api';
import {
  thesisApi,
  type ThesisCloseRegistrationPreview,
  type ThesisPublishResultsPreview,
  type ThesisCouncil,
  type ThesisCouncilMemberRole,
  type ThesisGroup,
  type ThesisRound,
  type ThesisRoundStatus,
  type ThesisTopic,
} from '@/lib/thesis-api';

/** Shape of the API error envelope (`{ code, message }`) used for domain conflicts. */
interface ThesisApiErrorShape {
  response?: { data?: { code?: string; details?: Record<string, unknown> | null } | null } | null;
}

function getThesisErrorCode(error: unknown): string {
  const data = (error as ThesisApiErrorShape | undefined)?.response?.data;
  return data?.code ?? '';
}

function getThesisErrorDetails(error: unknown): Record<string, unknown> {
  const details = (error as ThesisApiErrorShape | undefined)?.response?.data?.details;
  return details && typeof details === 'object' ? details : {};
}

function formatRoundInstant(locale: string, iso: string): string {
  const date = new Date(iso);
  if (!Number.isFinite(date.getTime())) return '—';
  const tag = locale === 'vi' ? 'vi-VN' : 'en-US';
  return new Intl.DateTimeFormat(tag, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date);
}

/**
 * Round window timestamps follow the ACTIVE app locale, not the browser's:
 * a Vietnamese page must never render "9/15/2026, 7:00:00 AM".
 */
function formatRoundWindow(locale: string, startIso: string, endIso: string): string {
  const tag = locale === 'vi' ? 'vi-VN' : 'en-US';
  const formatter = new Intl.DateTimeFormat(tag, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  });
  return `${formatter.format(new Date(startIso))} → ${formatter.format(new Date(endIso))}`;
}

export default function AdminThesisPage() {
  const {
    user,
    isAdmin,
    isLecturer,
    isFacultyHead,
    isLoading: isAuthLoading,
    isLoggingOut,
    isSuperAdmin,
  } = useAuth();
  const { locale, messages } = useI18n();
  const formatAdminInstant = (iso: string): string =>
    Number.isFinite(Date.parse(iso))
      ? formatRoundInstant(locale, iso)
      : messages.thesis.admin.unknownScheduleTime;
  const campusErrorForCode = (code: string): string | undefined =>
    messages.common.campusErrors.codes[code as keyof typeof messages.common.campusErrors.codes];
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [, setAdminClockTick] = useState(0);
  const serverClockSampleRef = useRef<DatabaseClockSample | null>(null);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [expandedRoundId, setExpandedRoundId] = useState<string | null>(null);
  const expandedRoundRef = useRef<string | null>(null);
  const detailRequestRef = useRef(0);
  const [detailLoading, setDetailLoading] = useState(false);
  const [publishReadinessByRound, setPublishReadinessByRound] = useState<Record<string, PublishResultsReadinessState>>({});
  const updatePublishReadiness = (
    roundId: string,
    update: Partial<PublishResultsReadinessState>,
  ) => setPublishReadinessByRound((current) => mergePublishResultsReadiness(current, roundId, update));
  const currentAdminTime = useCallback(
    () => estimatedServerNow(serverClockSampleRef.current, performance.now()),
    [],
  );

  useEffect(() => {
    const timer = window.setInterval(() => setAdminClockTick((tick) => tick + 1), 30_000);
    return () => window.clearInterval(timer);
  }, [currentAdminTime]);

  // Create round form state
  const [formName, setFormName] = useState('');
  const [formType, setFormType] = useState('MON_HOC');
  const [formStart, setFormStart] = useState('');
  const [formEnd, setFormEnd] = useState('');
  const [formLecturerStart, setFormLecturerStart] = useState('');
  const [formLecturerEnd, setFormLecturerEnd] = useState('');
  const [formProposal, setFormProposal] = useState('');
  const [formGvpb, setFormGvpb] = useState('');
  const [formReportDate, setFormReportDate] = useState('');
  const [formDefenseDate, setFormDefenseDate] = useState('');

  // Council management state
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [lecturerLoadError, setLecturerLoadError] = useState(false);
  const [showCreateCouncilModal, setShowCreateCouncilModal] = useState(false);
  const [councilNameInput, setCouncilNameInput] = useState('');
  const [targetRoundIdForCouncil, setTargetRoundIdForCouncil] = useState('');
  const [showAddMemberModal, setShowAddMemberModal] = useState(false);
  const [targetCouncilIdForMember, setTargetCouncilIdForMember] = useState('');
  const [selectedLecturerId, setSelectedLecturerId] = useState('');
  const [showAssignTopicModal, setShowAssignTopicModal] = useState(false);
  const [targetCouncilIdForTopic, setTargetCouncilIdForTopic] = useState('');
  const [selectedTopicIdToAssign, setSelectedTopicIdToAssign] = useState('');
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // Edit-round modal state (schedule amendment while DRAFT/PROPOSAL_OPEN)
  const [showEditRoundModal, setShowEditRoundModal] = useState(false);
  const [editingRoundId, setEditingRoundId] = useState('');

  // Extend-report-deadline modal state (the F1 escape hatch for a
  // REGISTRATION_CLOSED round whose gvpbDeadline already passed).
  const [showExtendDeadlineModal, setShowExtendDeadlineModal] = useState(false);
  const [extendDeadlineRoundId, setExtendDeadlineRoundId] = useState('');
  const [extendDeadlineKind, setExtendDeadlineKind] = useState<'report' | 'gvpb'>('report');
  const [extendDeadlineValue, setExtendDeadlineValue] = useState('');
  const [extendDeadlineError, setExtendDeadlineError] = useState('');

  // Failure inside a modal must be readable inside that modal: the page-level
  // `error` banner renders behind the modal overlay, so a refused submit used
  // to look like nothing happened. Cleared on each open and each retry.
  const [modalError, setModalError] = useState('');

  // Reason-bearing governance actions share one modal: unassign a topic,
  // replace a council seat, waive/restore a counter-review, withdraw or
  // reinstate a topic. Each call is audited server-side with the reason.
  const [reasonAction, setReasonAction] = useState<{
    kind: 'unassignTopic' | 'replaceMember' | 'waiveGvpb' | 'revokeGvpbWaiver' | 'withdrawTopic' | 'reinstateTopic'
        | 'lateTopic' | 'rosterOverride' | 'topicReassign' | 'retractResults' | 'deleteRound'
        | 'closeRound' | 'reopenRound';
    roundId: string;
    roundName?: string;
    councilId?: string;
    topicId?: string;
    lecturerId?: string;
  } | null>(null);
  const [reasonText, setReasonText] = useState('');
  const [replacementLecturerId, setReplacementLecturerId] = useState('');
  const [reasonActionError, setReasonActionError] = useState('');
  const [extendDeadlineReason, setExtendDeadlineReason] = useState('');
  const [lateTopicForm, setLateTopicForm] = useState({
    departmentId: '', title: '', description: '', supervisorId: '', maxGroups: '1',
  });
  const [overrideForm, setOverrideForm] = useState({
    groupId: '', studentId: '', action: 'ADD' as 'ADD' | 'REMOVE', successorId: '', allowSize: false,
  });
  const [reassignForm, setReassignForm] = useState({ groupId: '', topicId: '' });
  const [retractConfirmation, setRetractConfirmation] = useState('');

  // Archived rounds stay out of the default list (the server filters them);
  // this toggle merges the explicit status=CLOSED query back in so the
  // archive is one click away without cluttering the active view.
  const [showClosed, setShowClosed] = useState(false);

  // Restore-with-target modal: shown only when no implicit restore exists —
  // a legacy cancellation that still holds groups, or a REGISTRATION_OPEN
  // marker whose window already passed.
  const [restoreTargetRound, setRestoreTargetRound] = useState<ThesisRound | null>(null);
  const [restoreTargets, setRestoreTargets] = useState<ThesisRoundStatus[]>([]);
  const [restoreStatusDraft, setRestoreStatusDraft] = useState<ThesisRoundStatus | ''>('');

  // GVPB counter-reviewer assignment drafts, keyed by topic id
  const [reviewerDrafts, setReviewerDrafts] = useState<Record<string, string>>({});
  // topicId -> supervisor lecturer ids (either stored key form), lazily fetched
  // so the person pickers can explain disabled options; empty while loading.
  const [topicSupervisors, setTopicSupervisors] = useState<Record<string, string[]>>({});
  const topicSupervisorRequestsRef = useRef(new Set<string>());

  const thesisAdminAccess = Boolean(isAdmin || isSuperAdmin || isFacultyHead);
  const canAccess = Boolean(user && thesisAdminAccess);

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const [data, closed] = await Promise.all([
        thesisApi.listRounds(),
        showClosed ? thesisApi.listRounds('CLOSED') : Promise.resolve([]),
      ]);
      const receivedAt = performance.now();
      const sample = mergeThesisRounds(data, closed).find((round) => round.serverNow)?.serverNow;
      serverClockSampleRef.current = databaseClockSample(sample, receivedAt);
      setAdminClockTick((tick) => tick + 1);
      setRounds(sortThesisRoundsForOverview(mergeThesisRounds(data, closed)));
    } catch {
      setError(messages.thesis.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [messages.thesis.loadFailed, showClosed]);

  useEffect(() => {
    if (canAccess) void loadData();
  }, [canAccess, loadData]);

  /** Council lecturer roster: a failure must surface with a retry, not an empty select. */
  const loadLecturers = async () => {
    try {
      // Walk all pages — a single 100-row page made lecturers past that bound
      // impossible to seat on a council or assign as supervisor.
      setLecturers(await fetchAllPages<Lecturer>((p, l) => lecturersApi.getAll({ page: p, limit: l })));
      setLecturerLoadError(false);
    } catch {
      setLecturerLoadError(true);
    }
  };

  /**
   * Warms the per-topic supervisor cache the pickers read for disabled-with-
   * reason hints. Fire-and-forget: the backend remains the authority, so a
   * topic whose rows have not landed yet simply shows no disabled hint.
   */
  const ensureTopicSupervisors = useCallback((topicIds: string[]) => {
    for (const topicId of topicIds) {
      if (topicSupervisorRequestsRef.current.has(topicId)) continue;
      topicSupervisorRequestsRef.current.add(topicId);
      void thesisApi
        .listSupervisors(topicId)
        .then((sups) => {
          setTopicSupervisors((prev) => ({
            ...prev,
            [topicId]: sups.map((s) => s.lecturerId),
          }));
        })
        .catch(() => {
          // Drop the in-flight mark so the next picker open can retry.
          topicSupervisorRequestsRef.current.delete(topicId);
        });
    }
  }, []);

  const loadRoundDetail = async (roundId: string) => {
    if (expandedRoundRef.current !== roundId) return;
    const requestId = ++detailRequestRef.current;
    const isCurrent = () => requestId === detailRequestRef.current && expandedRoundRef.current === roundId;
    const detailedRound = rounds.find((round) => round.id === roundId);
    setDetailLoading(true);
    setTopics([]);
    setGroups([]);
    setCouncils([]);
    setError('');
    updatePublishReadiness(roundId, {
      preview: null,
      error: '',
      loading: detailedRound?.status === 'REGISTRATION_CLOSED',
    });
    try {
      // Draft proposals are exactly what a DRAFT/PROPOSAL_OPEN round holds, so
      // the detail panel must merge both statuses or pending proposals read as
      // an empty round (same merge the staff workspace does).
      const [published, drafts, g, c] = await Promise.all([
        thesisApi.listTopics(roundId, 'PUBLISHED'),
        thesisApi.listTopics(roundId, 'DRAFT'),
        thesisApi.listGroups(roundId),
        thesisApi.listCouncils(roundId),
      ]);
      if (!isCurrent()) return;
      const byId = new Map<string, (typeof published)[number]>();
      for (const topic of [...published, ...drafts]) byId.set(topic.id, topic);
      setTopics(Array.from(byId.values()));
      setGroups(g);
      if (expandedRoundRef.current === roundId) setCouncils(c);
      ensureTopicSupervisors(c.flatMap((council) => council.topicIds ?? []));
      if (lecturers.length === 0) {
        await loadLecturers();
      }
      if (detailedRound?.status === 'REGISTRATION_CLOSED') {
        try {
          const preview = await thesisApi.publishResultsPreview(roundId);
          if (!isCurrent()) return;
          updatePublishReadiness(roundId, { preview, error: '', loading: false });
        } catch (previewError: unknown) {
          if (isCurrent()) updatePublishReadiness(roundId, {
            error: transitionErrorMessage(previewError),
            loading: false,
          });
        } finally {
          if (isCurrent()) updatePublishReadiness(roundId, { loading: false });
        }
      }
    } catch {
      if (isCurrent()) {
        setError(messages.thesis.loadFailed);
        updatePublishReadiness(roundId, { loading: false });
      }
    } finally {
      if (isCurrent()) setDetailLoading(false);
    }
  };

  useEffect(() => () => {
    detailRequestRef.current += 1;
    expandedRoundRef.current = null;
  }, []);

  /** Translate domain refusals for every admin lifecycle surface. */
  const transitionErrorMessage = (
    err: unknown,
    context?: 'registration-open' | 'restore' | 'late-topic',
  ): string => {
    const code = getThesisErrorCode(err);
    const details = getThesisErrorDetails(err);
    if (code === 'REGISTRATION_EARLY_OPEN_BLOCKED') {
      const lecturerEnd = details.lecturerSubmitEnd;
      const time = typeof lecturerEnd === 'string'
        ? formatAdminInstant(lecturerEnd)
        : messages.thesis.admin.unknownScheduleTime;
      return messages.common.campusErrors.codes[code]
        ?.replace('{end}', time)
        ?? messages.thesis.admin.registrationEarlyOpenBlocked.replace('{end}', time);
    }
    if (code === 'REGISTRATION_WINDOW_CLOSED') {
      if (context === 'restore') return messages.thesis.admin.restoreRegistrationWindowClosed;
      if (context === 'late-topic') {
        const registrationEnd = details.registrationEnd;
        if (typeof registrationEnd !== 'string' || !Number.isFinite(Date.parse(registrationEnd))) {
          return messages.thesis.admin.lateTopicRegistrationUnavailable;
        }
        const message = details.roundStatus === 'PROPOSALS_PUBLISHED'
          ? messages.thesis.admin.lateTopicRegistrationClosedBeforeOpen
          : messages.thesis.admin.lateTopicRegistrationClosed;
        return message.replace('{end}', formatAdminInstant(registrationEnd));
      }
      if (context !== 'registration-open') return messages.thesis.admin.registrationWindowUnavailable;
      const registrationEnd = details.registrationEnd;
      return typeof registrationEnd === 'string'
        ? messages.thesis.admin.registrationWindowExpired.replace(
            '{end}', formatAdminInstant(registrationEnd),
          )
        : messages.thesis.admin.registrationWindowUnavailable;
    }
    if (code === 'REGISTRATION_WINDOW_NOT_OPEN') {
      if (context === 'restore') return messages.thesis.admin.restoreRegistrationWindowNotStarted;
      if (context === 'late-topic') {
        const registrationStart = details.registrationStart;
        return typeof registrationStart === 'string' && Number.isFinite(Date.parse(registrationStart))
          ? messages.thesis.admin.lateTopicRegistrationNotStarted.replace(
              '{start}', formatAdminInstant(registrationStart),
            )
          : messages.thesis.admin.lateTopicRegistrationUnavailable;
      }
      return messages.thesis.admin.registrationWindowNotStarted;
    }
    if (code === 'RETRACT_WINDOW_OPEN') {
      const endsAt = details.retractWindowEndsAt;
      return typeof endsAt === 'string'
        ? messages.common.campusErrors.codes[code]?.replace('{end}', formatAdminInstant(endsAt))
          ?? messages.thesis.admin.transitionFailure
        : messages.thesis.admin.transitionFailure;
    }
    if (code === 'VALIDATION_ERROR' && typeof details.rule === 'string') {
      const message = messages.thesis.admin.deadlineExtensionRules[
        details.rule as keyof typeof messages.thesis.admin.deadlineExtensionRules
      ];
      if (message) {
        const date = typeof details.currentDeadline === 'string'
          ? details.currentDeadline
          : typeof details.reportDate === 'string' ? details.reportDate : '';
        return message.replace('{date}', date ? formatAdminInstant(date) : messages.thesis.admin.unknownScheduleTime);
      }
    }
    return campusCodeMessage(err, messages.common.campusErrors, messages.thesis.admin.transitionFailure);
  };

  const refreshPublishResultsPreview = async (roundId: string) => {
    const requestId = detailRequestRef.current;
    const isCurrent = () => requestId === detailRequestRef.current
      && expandedRoundRef.current === roundId;
    updatePublishReadiness(roundId, { loading: true, error: '' });
    try {
      const preview = await thesisApi.publishResultsPreview(roundId);
      if (isCurrent()) updatePublishReadiness(roundId, { preview, error: '' });
    } catch (previewError: unknown) {
      if (isCurrent()) updatePublishReadiness(roundId, { error: transitionErrorMessage(previewError) });
    } finally {
      if (isCurrent()) updatePublishReadiness(roundId, { loading: false });
    }
  };

  /**
   * A schedule-order rejection carries the violated rule in details.rule, so
   * the round modals can show the same precise copy the client-side schedule
   * check produces; every other failure keeps the standard code/kind mapping.
   */
  const roundMutationErrorMessage = (err: unknown): string => {
    const rule = thesisRoundScheduleErrorRule(err);
    return (rule && messages.thesis.admin.scheduleOrder[rule])
      || transitionErrorMessage(err);
  };

  const handleCreateCouncil = async (roundId: string) => {
    if (!councilNameInput.trim()) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.createCouncil(roundId, councilNameInput.trim());
      setSuccess(messages.thesis.councils.councilCreatedSuccess);
      setCouncilNameInput('');
      setShowCreateCouncilModal(false);
      const c = await thesisApi.listCouncils(roundId);
      if (expandedRoundRef.current === roundId) setCouncils(c);
    } catch (err: unknown) {
      setModalError(transitionErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const handleAddMember = async (councilId: string, roundId: string) => {
    if (!selectedLecturerId) return;
    const council = councils.find((c) => c.id === councilId);
    const members = council?.members || [];
    let nextRole: ThesisCouncilMemberRole = 'MEMBER';
    if (!members.some((m) => m.memberRole === 'CHAIR')) {
      nextRole = 'CHAIR';
    } else if (!members.some((m) => m.memberRole === 'SECRETARY')) {
      nextRole = 'SECRETARY';
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.addCouncilMember(councilId, selectedLecturerId, nextRole);
      setSuccess(messages.thesis.councils.memberAddedSuccess);
      setSelectedLecturerId('');
      setShowAddMemberModal(false);
      const c = await thesisApi.listCouncils(roundId);
      if (expandedRoundRef.current === roundId) setCouncils(c);
    } catch (err: unknown) {
      setModalError(transitionErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveMember = async (councilId: string, lecturerId: string, roundId: string) => {
    const confirmed = await confirm({
      title: messages.thesis.councils.removeMember,
      message: messages.thesis.councils.removeMemberConfirm,
      confirmText: messages.common.actions.confirm,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!confirmed) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.removeCouncilMember(councilId, lecturerId);
      setSuccess(messages.thesis.councils.memberRemovedSuccess);
      const c = await thesisApi.listCouncils(roundId);
      if (expandedRoundRef.current === roundId) setCouncils(c);
    } catch (err: unknown) {
      setError(transitionErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const handleAssignTopic = async (councilId: string, roundId: string) => {
    if (!selectedTopicIdToAssign) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.assignTopicToCouncil(councilId, selectedTopicIdToAssign);
      setSuccess(messages.thesis.councils.assignSuccess);
      setSelectedTopicIdToAssign('');
      setShowAssignTopicModal(false);
      const c = await thesisApi.listCouncils(roundId);
      if (expandedRoundRef.current === roundId) setCouncils(c);
    } catch (assignErr) {
      setModalError(transitionErrorMessage(assignErr));
    } finally {
      setIsSaving(false);
    }
  };

  const toggleExpand = (roundId: string) => {
    if (expandedRoundId === roundId) {
      expandedRoundRef.current = null;
      detailRequestRef.current += 1;
      setDetailLoading(false);
      setExpandedRoundId(null);
    } else {
      expandedRoundRef.current = roundId;
      setExpandedRoundId(roundId);
      void loadRoundDetail(roundId);
    }
  };

  // The conditional fields mirror RoundType rules on the server: TLCN/KLTN
  // require the GVPB deadline and the council report date, KLTN additionally
  // requires the defense date. Submitting without them used to bounce off the
  // backend as a generic failure — validate them here so the form says what
  // is missing.
  const needsGvpb = formType === 'TLCN' || formType === 'KLTN';
  const needsDefense = formType === 'KLTN';

  const createRound = async () => {
    if (!formName || !formStart || !formEnd || !formLecturerStart || !formLecturerEnd
        || (needsGvpb && (!formGvpb || !formReportDate))
        || (needsDefense && !formDefenseDate)) {
      setModalError(messages.thesis.admin.createIncomplete);
      return;
    }
    const schedule = {
      lecturerSubmitStart: parseThesisLocalInput(formLecturerStart),
      lecturerSubmitEnd: parseThesisLocalInput(formLecturerEnd),
      registrationStart: parseThesisLocalInput(formStart),
      registrationEnd: parseThesisLocalInput(formEnd),
      proposalPublishAt: parseThesisLocalInput(formProposal),
      gvpbDeadline: parseThesisLocalInput(needsGvpb ? formGvpb : ''),
      reportDate: parseThesisLocalInput(needsGvpb ? formReportDate : ''),
      defenseDate: parseThesisLocalInput(needsDefense ? formDefenseDate : ''),
    };
    const scheduleViolation = firstThesisRoundScheduleViolation(schedule);
    if (scheduleViolation) {
      setModalError(messages.thesis.admin.scheduleOrder[scheduleViolation]);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.createRound({
        name: formName,
        thesisType: formType,
        registrationStart: new Date(formStart).toISOString(),
        registrationEnd: new Date(formEnd).toISOString(),
        lecturerSubmitStart: new Date(formLecturerStart).toISOString(),
        lecturerSubmitEnd: new Date(formLecturerEnd).toISOString(),
        proposalPublishAt: schedule.proposalPublishAt?.toISOString(),
        gvpbDeadline: needsGvpb && formGvpb ? schedule.gvpbDeadline?.toISOString() : undefined,
        reportDate:
          needsGvpb && formReportDate
            ? schedule.reportDate?.toISOString()
            : undefined,
        defenseDate:
          needsDefense && formDefenseDate
            ? schedule.defenseDate?.toISOString()
            : undefined,
      });
      setSuccess(messages.thesis.admin.created);
      setShowCreateModal(false);
      setFormName('');
      setFormStart('');
      setFormEnd('');
      setFormLecturerStart('');
      setFormLecturerEnd('');
      setFormProposal('');
      setFormGvpb('');
      setFormReportDate('');
      setFormDefenseDate('');
      await loadData();
    } catch (err: unknown) {
      setModalError(roundMutationErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const transitionRound = async (
    roundId: string,
    action: 'open' | 'close' | 'publish' | 'open-proposals' | 'publish-results',
    round?: ThesisRound,
  ) => {
    setError('');
    setSuccess('');
    const registrationOpenWindow = action === 'open' && round
      ? registrationOpeningAvailability(round, currentAdminTime())
      : null;
    if (registrationOpenWindow?.disabled && registrationOpenWindow.reasonCode) {
      const details = registrationOpenWindow.reasonCode === 'REGISTRATION_EARLY_OPEN_BLOCKED'
        ? { lecturerSubmitEnd: round?.lecturerSubmitEnd }
        : { registrationEnd: round?.registrationEnd };
      setError(transitionErrorMessage({
        response: { status: 409, data: { code: registrationOpenWindow.reasonCode, details } },
      }, 'registration-open'));
      return;
    }
    // Lifecycle transitions move every group and lecturer in the round, so a
    // single misclick cannot silently open registration or publish results.
    // Closing registration must never be blind (F2): fetch the server-side
    // consequence preview first so the dialog shows real group/report counts.
    let closePreview: ThesisCloseRegistrationPreview | null = null;
    if (action === 'close') {
      try {
        closePreview = await thesisApi.closeRegistrationPreview(roundId);
      } catch (previewError: unknown) {
        setError(transitionErrorMessage(previewError));
        return;
      }
    }
    // Publishing must name its exclusions: preview which topics sit outside
    // the completeness requirement so the confirm is never blind. A preview
    // failure or known blocker must stop before confirmation and mutation.
    let publishPreview: ThesisPublishResultsPreview | null = null;
    let resultPreview: ThesisPublishResultsPreview;
    if (action === 'publish-results') {
      updatePublishReadiness(roundId, { loading: true, error: '' });
      try {
        resultPreview = await thesisApi.publishResultsPreview(roundId);
        publishPreview = resultPreview;
        updatePublishReadiness(roundId, { preview: resultPreview, error: '' });
      } catch (previewError: unknown) {
        const message = transitionErrorMessage(previewError);
        updatePublishReadiness(roundId, { error: message });
        setError(message);
        return;
      } finally {
        updatePublishReadiness(roundId, { loading: false });
      }
      const blockerCode = publishResultsBlockerCode(resultPreview);
      if (blockerCode) {
        const blockerMessage = campusErrorForCode(blockerCode)
          ?? messages.thesis.admin.transitionFailure;
        setError(blockerMessage);
        return;
      }
    }
    let message = transitionConfirmCopy(action);
    let warning: string | undefined;
    if (action === 'open' && registrationOpenWindow && round) {
      message = [
        message,
        messages.thesis.admin.openRegistrationEffectiveWindow.replace(
          '{window}',
          formatRoundWindow(locale, registrationOpenWindow.effectiveStart, registrationOpenWindow.effectiveEnd),
        ),
      ].join(' ');
      if (registrationOpenWindow.opensImmediately) {
        message = [
          message,
          messages.thesis.admin.openRegistrationEarlyHint.replace(
            '{start}', formatRoundInstant(locale, round.registrationStart),
          ),
        ].join(' ');
      }
    }
    if (publishPreview && publishPreview.excludedTopics.length > 0) {
      message = [
        message,
        messages.thesis.admin.publishPreviewExcluded.replace(
          '{titles}',
          publishPreview.excludedTopics.map((t) => t.title).join(', '),
        ),
      ].join(' ');
    }
    if (publishPreview && publishPreview.openReviewTopics.length > 0) {
      message = [
        message,
        messages.thesis.admin.publishReviewOpen.replace(
          '{titles}',
          publishPreview.openReviewTopics.map((t) => t.title).join(', '),
        ),
      ].join(' ');
    }
    // A publication where every approved group is excluded needs the explicit
    // server-side flag — gated behind its own confirmation below.
    const allExcluded = publishPreview != null
      && publishPreview.approvedGroupCount > 0
      && publishPreview.includedGroupCount === 0;
    if (closePreview) {
      const adminCopy = messages.thesis.admin;
      message = [
        message,
        adminCopy.closePreviewGroups.replace('{count}', String(closePreview.groupCount)),
        adminCopy.closePreviewApproved.replace('{count}', String(closePreview.approvedGroupCount)),
        adminCopy.closePreviewPending.replace('{count}', String(closePreview.pendingGroupCount)),
        adminCopy.closePreviewReports.replace('{count}', String(closePreview.reportCount)),
      ].join(' ');
      if (closePreview.reportDeadlinePassed) {
        warning = adminCopy.closePreviewDeadlinePassed;
      }
    }
    const confirmed = await confirm({
      title: messages.thesis.admin.transitionConfirmTitle,
      message,
      confirmText: messages.common.actions.confirm,
      cancelText: messages.common.actions.cancel,
      variant: action === 'publish-results' || action === 'close' ? 'destructive' : 'default',
      warning,
    });
    if (!confirmed) return;
    // The server-side flag only goes out when the user explicitly confirms the
    // all-excluded consequence — never inferred silently from preview data.
    let confirmAllExcluded: boolean | undefined;
    if (allExcluded) {
      confirmAllExcluded = await confirm({
        title: messages.thesis.admin.transitionConfirmTitle,
        message: messages.thesis.admin.publishAllExcluded,
        confirmText: messages.common.actions.confirm,
        cancelText: messages.common.actions.cancel,
        variant: 'destructive',
      });
      if (!confirmAllExcluded) return;
    }
    setIsSaving(true);
    try {
      if (action === 'open-proposals') {
        await thesisApi.openProposals(roundId);
        setSuccess(messages.thesis.admin.proposalsOpened);
      } else if (action === 'open') {
        await thesisApi.openRegistration(roundId);
        setSuccess(messages.thesis.admin.registrationOpened);
      } else if (action === 'close') {
        await thesisApi.closeRegistration(roundId);
        setSuccess(messages.thesis.admin.registrationClosed);
      } else if (action === 'publish') {
        await thesisApi.publishProposals(roundId);
        setSuccess(messages.thesis.admin.proposalsPublished);
      } else {
        await thesisApi.publishResults(roundId, confirmAllExcluded);
        setSuccess(messages.thesis.admin.resultsPublished);
      }
      await loadData();
    } catch (err: unknown) {
      setError(transitionErrorMessage(err, action === 'open' ? 'registration-open' : undefined));
    } finally {
      setIsSaving(false);
    }
  };

  const transitionConfirmCopy = (
    action: 'open' | 'close' | 'publish' | 'open-proposals' | 'publish-results',
  ): string => {
    const copy = messages.thesis.admin.transitionConfirm;
    if (action === 'open-proposals') return copy.openProposals;
    if (action === 'open') return copy.openRegistration;
    if (action === 'close') return copy.closeRegistration;
    if (action === 'publish') return copy.publishProposals;
    return copy.publishResults;
  };


  /** Opens the deadline-extension modal prefilled with the current deadline. */
  const openExtendDeadlineModal = (round: ThesisRound, kind: 'report' | 'gvpb' = 'report') => {
    setExtendDeadlineRoundId(round.id);
    setExtendDeadlineKind(kind);
    setExtendDeadlineValue(toLocalInput(kind === 'gvpb' ? round.gvpbDeadline : round.reportDeadline ?? round.gvpbDeadline));
    setExtendDeadlineReason('');
    setExtendDeadlineError('');
    setShowExtendDeadlineModal(true);
  };

  const extendReportDeadline = async () => {
    const parsed = extendDeadlineValue ? new Date(extendDeadlineValue) : null;
    if (
      !extendDeadlineRoundId ||
      !parsed ||
      Number.isNaN(parsed.getTime()) ||
      parsed.getTime() <= currentAdminTime()
    ) {
      setExtendDeadlineError(messages.thesis.admin.reportDeadlineInvalid);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    setExtendDeadlineError('');
    try {
      if (extendDeadlineKind === 'gvpb') {
        if (!extendDeadlineReason.trim()) {
          setExtendDeadlineError(messages.thesis.admin.governanceReasonRequired);
          return;
        }
        await thesisApi.extendGvpbDeadline(extendDeadlineRoundId, parsed.toISOString(), extendDeadlineReason.trim());
        setSuccess(messages.thesis.admin.gvpbDeadlineSuccess);
      } else {
        await thesisApi.extendReportDeadline(extendDeadlineRoundId, parsed.toISOString());
        setSuccess(messages.thesis.admin.reportDeadlineSuccess);
      }
      setShowExtendDeadlineModal(false);
      setExtendDeadlineRoundId('');
      await loadData();
      if (expandedRoundId === extendDeadlineRoundId) {
        await loadRoundDetail(extendDeadlineRoundId);
      }
    } catch (err: unknown) {
      setExtendDeadlineError(transitionErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  /** The edit modal reuses the create-form state; opening it loads the round. */
  const openEditRoundModal = (round: ThesisRound) => {
    setEditingRoundId(round.id);
    setFormName(round.name);
    setFormType(round.thesisType || 'MON_HOC');
    setFormStart(toLocalInput(round.registrationStart));
    setFormEnd(toLocalInput(round.registrationEnd));
    setFormLecturerStart(toLocalInput(round.lecturerSubmitStart));
    setFormLecturerEnd(toLocalInput(round.lecturerSubmitEnd));
    setFormProposal(toLocalInput(round.proposalPublishAt));
    setFormGvpb(toLocalInput(round.gvpbDeadline));
    setFormReportDate(toLocalInput(round.reportDate));
    setFormDefenseDate(toLocalInput(round.defenseDate));
    setModalError('');
    setShowEditRoundModal(true);
  };

  const updateRound = async () => {
    if (!editingRoundId) return;
    if (!formName || !formStart || !formEnd || !formLecturerStart || !formLecturerEnd
        || (needsGvpb && (!formGvpb || !formReportDate))
        || (needsDefense && !formDefenseDate)) {
      setModalError(messages.thesis.admin.createIncomplete);
      return;
    }
    const schedule = {
      lecturerSubmitStart: parseThesisLocalInput(formLecturerStart),
      lecturerSubmitEnd: parseThesisLocalInput(formLecturerEnd),
      registrationStart: parseThesisLocalInput(formStart),
      registrationEnd: parseThesisLocalInput(formEnd),
      proposalPublishAt: parseThesisLocalInput(formProposal),
      gvpbDeadline: parseThesisLocalInput(needsGvpb ? formGvpb : ''),
      reportDate: parseThesisLocalInput(needsGvpb ? formReportDate : ''),
      defenseDate: parseThesisLocalInput(needsDefense ? formDefenseDate : ''),
    };
    const scheduleViolation = firstThesisRoundScheduleViolation(schedule);
    if (scheduleViolation) {
      setModalError(messages.thesis.admin.scheduleOrder[scheduleViolation]);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.updateRound(editingRoundId, {
        name: formName,
        thesisType: formType,
        registrationStart: new Date(formStart).toISOString(),
        registrationEnd: new Date(formEnd).toISOString(),
        lecturerSubmitStart: new Date(formLecturerStart).toISOString(),
        lecturerSubmitEnd: new Date(formLecturerEnd).toISOString(),
        proposalPublishAt: schedule.proposalPublishAt?.toISOString(),
        gvpbDeadline: needsGvpb && formGvpb ? schedule.gvpbDeadline?.toISOString() : undefined,
        reportDate: needsGvpb && formReportDate ? schedule.reportDate?.toISOString() : undefined,
        defenseDate: needsDefense && formDefenseDate ? schedule.defenseDate?.toISOString() : undefined,
      });
      setSuccess(messages.thesis.admin.editRoundSuccess);
      setShowEditRoundModal(false);
      setEditingRoundId('');
      await loadData();
      if (expandedRoundId === editingRoundId) {
        await loadRoundDetail(editingRoundId);
      }
    } catch (err: unknown) {
      setModalError(roundMutationErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const cancelRound = async (roundId: string) => {
    const confirmed = await confirm({
      title: messages.thesis.admin.cancelRoundTitle,
      message: messages.thesis.admin.cancelRoundConfirm,
      confirmText: messages.thesis.admin.cancelRound,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!confirmed) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.cancelRound(roundId);
      setSuccess(messages.thesis.admin.roundCancelled);
      await loadData();
    } catch (err: unknown) {
      setError(transitionErrorMessage(err));
    } finally {
      setIsSaving(false);
    }
  };

  const performRestore = async (roundId: string, targetStatus?: ThesisRoundStatus) => {
    setIsSaving(true);
    setError('');
    setSuccess('');
    setModalError('');
    try {
      await thesisApi.restoreRound(roundId, targetStatus);
      setSuccess(messages.thesis.admin.roundRestored);
      setRestoreTargetRound(null);
      await loadData();
      if (expandedRoundId === roundId) {
        await loadRoundDetail(roundId);
      }
    } catch (err: unknown) {
      // Restore runs both from the target-picker modal and the plain confirm
      // flow — write both sinks so the failure is visible on either path.
      setError(transitionErrorMessage(err, 'restore'));
      setModalError(transitionErrorMessage(err, 'restore'));
    } finally {
      setIsSaving(false);
    }
  };

  const restoreRound = async (round: ThesisRound) => {
    // Whether a choice is required depends on the live groups still attached,
    // so fetch them fresh — the roster can change while the row sits cancelled.
    let hasLiveGroups = false;
    try {
      hasLiveGroups = (await thesisApi.listGroups(round.id))
        .some((group) => group.status !== 'CANCELLED');
    } catch {
      setError(messages.thesis.loadFailed);
      return;
    }
    const plan = planThesisRoundRestore(round, hasLiveGroups, currentAdminTime());
    if (plan.requiresChoice) {
      setModalError('');
      setRestoreTargetRound(round);
      setRestoreTargets(plan.explicitTargets);
      setRestoreStatusDraft(plan.explicitTargets[0]);
      return;
    }
    const confirmed = await confirm({
      title: messages.thesis.admin.restoreRoundTitle,
      message: round.cancelledFromStatus
        ? messages.thesis.admin.restoreRoundConfirm
        : messages.thesis.admin.restoreRoundConfirmLegacy,
      confirmText: messages.thesis.admin.restoreRound,
      cancelText: messages.common.actions.cancel,
    });
    if (!confirmed) return;
    await performRestore(round.id);
  };

  const handleAssignReviewer = async (topicId: string, roundId: string) => {
    const lecturerId = reviewerDrafts[topicId];
    if (!lecturerId) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.assignReviewer(topicId, lecturerId);
      setSuccess(messages.thesis.admin.reviewerAssigned);
      await loadRoundDetail(roundId);
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      setError(
        code === 'SUPERVISOR_CANNOT_REVIEW'
          ? messages.thesis.admin.reviewerIsSupervisor
          : code === 'GVPB_SCORE_SUBMITTED'
            ? messages.thesis.admin.reviewerFrozen
            : transitionErrorMessage(err),
      );
    } finally {
      setIsSaving(false);
    }
  };

  const openReasonAction = (action: NonNullable<typeof reasonAction>) => {
    setReasonAction(action);
    setReasonText('');
    setReplacementLecturerId('');
    setLateTopicForm({ departmentId: '', title: '', description: '', supervisorId: '', maxGroups: '1' });
    setOverrideForm({ groupId: '', studentId: '', action: 'ADD', successorId: '', allowSize: false });
    setReassignForm({ groupId: '', topicId: '' });
    setRetractConfirmation('');
    setReasonActionError('');
  };

  const submitReasonAction = async () => {
    const action = reasonAction;
    if (!action) return;
    const reason = reasonText.trim();
    if (!reason || reason.length > 500) {
      setReasonActionError(messages.thesis.admin.governanceReasonRequired);
      return;
    }
    if (action.kind === 'replaceMember' && !replacementLecturerId) {
      setReasonActionError(messages.thesis.councils.selectLecturer);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    setReasonActionError('');
    try {
      if (action.kind === 'unassignTopic' && action.councilId && action.topicId) {
        await thesisApi.unassignCouncilTopic(action.councilId, action.topicId, reason);
        setSuccess(messages.thesis.councils.topicUnassignedSuccess);
      } else if (action.kind === 'replaceMember' && action.councilId && action.lecturerId) {
        await thesisApi.replaceCouncilMember(action.councilId, action.lecturerId, replacementLecturerId, reason);
        setSuccess(messages.thesis.councils.memberReplacedSuccess);
      } else if (action.kind === 'waiveGvpb' && action.topicId) {
        await thesisApi.waiveCounterReview(action.topicId, reason);
        setSuccess(messages.thesis.admin.governanceActionSuccess);
      } else if (action.kind === 'revokeGvpbWaiver' && action.topicId) {
        await thesisApi.revokeCounterReviewWaiver(action.topicId, reason);
        setSuccess(messages.thesis.admin.governanceActionSuccess);
      } else if (action.kind === 'withdrawTopic' && action.topicId) {
        await thesisApi.withdrawTopic(action.topicId, reason);
        setSuccess(messages.thesis.admin.governanceActionSuccess);
      } else if (action.kind === 'reinstateTopic' && action.topicId) {
        await thesisApi.reinstateTopic(action.topicId, reason);
        setSuccess(messages.thesis.admin.governanceActionSuccess);
      } else if (action.kind === 'lateTopic') {
        const maxGroups = Number.parseInt(lateTopicForm.maxGroups, 10);
        if (!lateTopicForm.departmentId.trim() || !lateTopicForm.title.trim()
            || !lateTopicForm.description.trim() || !lateTopicForm.supervisorId.trim()
            || !Number.isFinite(maxGroups) || maxGroups < 1 || maxGroups > 20) {
          setReasonActionError(messages.thesis.admin.governanceReasonRequired);
          return;
        }
        await thesisApi.createLateTopic(action.roundId, {
          departmentId: lateTopicForm.departmentId.trim(),
          title: lateTopicForm.title.trim(),
          description: lateTopicForm.description.trim(),
          maxGroups,
          supervisorId: lateTopicForm.supervisorId.trim(),
          reason,
        });
        setSuccess(messages.thesis.admin.lateTopicSuccess);
      } else if (action.kind === 'rosterOverride') {
        if (!overrideForm.groupId.trim() || !overrideForm.studentId.trim()) {
          setReasonActionError(messages.thesis.admin.governanceReasonRequired);
          return;
        }
        await thesisApi.overrideGroupRoster(overrideForm.groupId.trim(), {
          studentId: overrideForm.studentId.trim(),
          action: overrideForm.action,
          reason,
          allowSizeException: overrideForm.allowSize,
          ...(overrideForm.action === 'REMOVE' && overrideForm.successorId.trim()
            ? { successorStudentId: overrideForm.successorId.trim() }
            : {}),
        });
        setSuccess(messages.thesis.admin.rosterOverrideSuccess);
      } else if (action.kind === 'topicReassign') {
        if (!reassignForm.groupId.trim() || !reassignForm.topicId.trim()) {
          setReasonActionError(messages.thesis.admin.governanceReasonRequired);
          return;
        }
        await thesisApi.reassignOrphanedTopic(reassignForm.groupId.trim(), {
          topicId: reassignForm.topicId.trim(),
          reason,
        });
        setSuccess(messages.thesis.admin.topicReassignSuccess);
      } else if (action.kind === 'retractResults') {
        if (retractConfirmation.trim() !== 'RETRACT') {
          setReasonActionError(messages.thesis.admin.retractResultsConfirmLabel);
          return;
        }
        await thesisApi.retractResults(action.roundId, reason, 'RETRACT');
        setSuccess(messages.thesis.admin.retractResultsSuccess);
      } else if (action.kind === 'deleteRound') {
        if (retractConfirmation.trim() !== action.roundName) {
          setReasonActionError(messages.thesis.admin.deleteRoundConfirmLabel);
          return;
        }
        await thesisApi.deleteRound(action.roundId, reason);
        setSuccess(messages.thesis.admin.roundDeleted);
      } else if (action.kind === 'closeRound') {
        if (retractConfirmation.trim() !== action.roundName) {
          setReasonActionError(messages.thesis.admin.closeRoundConfirmLabel);
          return;
        }
        await thesisApi.closeRound(action.roundId, reason);
        setSuccess(messages.thesis.admin.roundClosed);
      } else if (action.kind === 'reopenRound') {
        await thesisApi.reopenRound(action.roundId, reason);
        setSuccess(messages.thesis.admin.roundReopened);
      } else {
        return;
      }
      setReasonAction(null);
      if (action.kind === 'deleteRound' && expandedRoundRef.current === action.roundId) {
        expandedRoundRef.current = null;
        setExpandedRoundId(null);
      }
      await loadData();
      if (action.kind !== 'deleteRound') {
        await loadRoundDetail(action.roundId);
      }
    } catch (err: unknown) {
      setReasonActionError(transitionErrorMessage(
        err,
        action.kind === 'lateTopic' ? 'late-topic' : undefined,
      ));
    } finally {
      setIsSaving(false);
    }
  };

  const deleteBlockerSummary = (round: ThesisRound): string => {
    const info = round.deletion;
    if (!info) return '';
    const copy = messages.thesis.admin;
    const parts: string[] = [];
    if (info.topics > 0) parts.push(copy.deleteBlockerTopics.replace('{count}', String(info.topics)));
    if (info.groups > 0) parts.push(copy.deleteBlockerGroups.replace('{count}', String(info.groups)));
    if (info.members > 0) parts.push(copy.deleteBlockerMembers.replace('{count}', String(info.members)));
    if (info.reports > 0) parts.push(copy.deleteBlockerReports.replace('{count}', String(info.reports)));
    if (info.councils > 0) parts.push(copy.deleteBlockerCouncils.replace('{count}', String(info.councils)));
    return parts.join(' · ');
  };

  const reasonActionTitle = reasonAction
    ? {
        unassignTopic: messages.thesis.councils.unassignTopicTitle,
        replaceMember: messages.thesis.councils.replaceMemberTitle,
        waiveGvpb: messages.thesis.admin.waiveGvpbTitle,
        revokeGvpbWaiver: messages.thesis.admin.revokeGvpbWaiverTitle,
        withdrawTopic: messages.thesis.admin.withdrawTopicTitle,
        reinstateTopic: messages.thesis.admin.reinstateTopicTitle,
        lateTopic: messages.thesis.admin.lateTopicTitle,
        rosterOverride: messages.thesis.admin.rosterOverrideTitle,
        topicReassign: messages.thesis.admin.topicReassignTitle,
        retractResults: messages.thesis.admin.retractResultsTitle,
        deleteRound: messages.thesis.admin.deleteRoundTitle,
        closeRound: messages.thesis.admin.closeRoundTitle,
        reopenRound: messages.thesis.admin.reopenRoundTitle,
      }[reasonAction.kind]
    : '';

  if (isAuthLoading || isLoggingOut) {
    return (
      <AdminFrame title={messages.thesis.admin.title} description={messages.thesis.admin.description}>
        <LoadingState label={messages.thesis.loading} />
      </AdminFrame>
    );
  }

  if (!user) {
    return (
      <AdminFrame title={messages.thesis.admin.title} description={messages.thesis.admin.description}>
        <ForbiddenState
          title={messages.thesis.admin.forbiddenTitle}
          description={messages.thesis.admin.forbiddenDescription}
          action={
            <LinkButton href="/login?portal=admin" variant="outline">
              {messages.common.actions.signIn}
            </LinkButton>
          }
        />
      </AdminFrame>
    );
  }

  if (!canAccess) {
    return (
      <AdminFrame title={messages.thesis.admin.title} description={messages.thesis.admin.description}>
        <ForbiddenState
          title={messages.thesis.admin.forbiddenTitle}
          description={messages.thesis.admin.forbiddenDescription}
          action={
            <LinkButton href={isLecturer ? '/dashboard/lecturer' : '/dashboard'} variant="outline">
              {messages.thesis.admin.returnToWorkspace}
            </LinkButton>
          }
        />
      </AdminFrame>
    );
  }

  if (isLoading && rounds.length === 0) {
    return (
      <AdminFrame title={messages.thesis.admin.title} description={messages.thesis.admin.description}>
        <LoadingState label={messages.thesis.loading} />
      </AdminFrame>
    );
  }

  const openRounds = rounds.filter((r) => r.status === 'REGISTRATION_OPEN').length;
  // `topics`/`groups` are filled by loadRoundDetail for the one expanded
  // round only — they are not global totals. An unlabeled number therefore
  // lied twice: 0 at rest, and one round's count otherwise. Name the round
  // the numbers belong to, and show a dash until a round is expanded (no
  // extra API calls; the counts simply do not exist yet).
  const detailRound = rounds.find((round) => round.id === expandedRoundId) ?? null;
  const totalTopics = topics.length;
  const totalGroups = groups.length;

  return (
    <AdminFrame
      title={messages.thesis.admin.title}
      description={messages.thesis.admin.description}
      actions={
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm font-medium text-foreground">
            <input
              type="checkbox"
              checked={showClosed}
              onChange={(e) => setShowClosed(e.target.checked)}
              className="h-4 w-4 rounded border-input accent-primary"
            />
            {messages.thesis.admin.showClosedRounds}
          </label>
          <Button type="button" onClick={() => { setModalError(''); setShowCreateModal(true); }} disabled={!canAccess}>
            <Plus className="mr-2 h-4 w-4" />
            {messages.thesis.admin.createRound}
          </Button>
        </div>
      }
    >
      {error ? (
        <div role="alert" className="mb-6 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}
      {success ? (
        <div className="mb-6 flex items-center gap-3 rounded-lg border border-status-success/30 bg-status-success/15 px-4 py-3 text-sm text-status-success-foreground">
          <Check className="h-4 w-4" />
          {success}
        </div>
      ) : null}

      <div className="space-y-8">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <AdminMetricCard
            label={messages.thesis.admin.totalRounds}
            value={rounds.length}
            icon={<CalendarDays className="h-5 w-5" />}
            toneClassName={metricToneClass('info')}
          />
          <AdminMetricCard
            label={messages.thesis.admin.openRounds}
            value={openRounds}
            icon={<CircleDot className="h-5 w-5" />}
            toneClassName={metricToneClass('success')}
          />
          <AdminMetricCard
            label={
              detailRound
                ? messages.thesis.admin.topicsForRound.replace('{round}', detailRound.name)
                : messages.thesis.topics
            }
            value={detailRound && !detailLoading ? totalTopics : '—'}
            icon={<FileStack className="h-5 w-5" />}
            toneClassName={metricToneClass('warning')}
          />
          <AdminMetricCard
            label={
              detailRound
                ? messages.thesis.admin.groupsForRound.replace('{round}', detailRound.name)
                : messages.thesis.groups
            }
            value={detailRound && !detailLoading ? totalGroups : '—'}
            icon={<UsersRound className="h-5 w-5" />}
            toneClassName={metricToneClass('neutral')}
          />
        </div>

        {rounds.length === 0 ? (
          <EmptyState
            icon={CalendarDays}
            title={messages.thesis.admin.noRounds}
            description={messages.thesis.admin.noRoundsDescription}
          />
        ) : (
          <div className="space-y-4">
            {rounds.map((round) => {
              const isExpanded = expandedRoundId === round.id;
              const lifecycleNow = currentAdminTime();
              const registrationOpenWindow = registrationOpeningAvailability(round, lifecycleNow);
              const lateTopicAvailabilityState = lateTopicAvailability(
                round.status, round.registrationStart, round.registrationEnd, lifecycleNow,
              );
              const lateTopicUnavailableReason = lateTopicAvailabilityState.reasonCode === 'REGISTRATION_WINDOW_CLOSED'
                ? Number.isFinite(Date.parse(round.registrationEnd))
                  ? (round.status === 'PROPOSALS_PUBLISHED'
                    ? messages.thesis.admin.lateTopicRegistrationClosedBeforeOpen
                    : messages.thesis.admin.lateTopicRegistrationClosed).replace(
                      '{end}', formatAdminInstant(round.registrationEnd),
                    )
                  : messages.thesis.admin.lateTopicRegistrationUnavailable
                : lateTopicAvailabilityState.reasonCode === 'REGISTRATION_WINDOW_NOT_OPEN'
                  ? Number.isFinite(Date.parse(round.registrationStart))
                    ? messages.thesis.admin.lateTopicRegistrationNotStarted.replace(
                        '{start}', formatAdminInstant(round.registrationStart),
                      )
                    : messages.thesis.admin.lateTopicRegistrationUnavailable
                  : '';
              const gvpbExtensionUnavailableAt = gvpbDeadlineExtensionBlocker(round.reportDate, lifecycleNow);
              const gvpbExtensionUnavailableReason = gvpbExtensionUnavailableAt
                ? messages.thesis.admin.gvpbExtensionReportDatePassed.replace(
                    '{date}', formatAdminInstant(gvpbExtensionUnavailableAt),
                  )
                : '';
              const registrationOpenReason = registrationOpenWindow.reasonCode === 'REGISTRATION_EARLY_OPEN_BLOCKED'
                ? messages.common.campusErrors.codes.REGISTRATION_EARLY_OPEN_BLOCKED
                  ?.replace('{end}', formatAdminInstant(round.lecturerSubmitEnd))
                : registrationOpenWindow.reasonCode === 'REGISTRATION_WINDOW_CLOSED'
                  ? messages.thesis.admin.registrationWindowExpired.replace(
                      '{end}', formatAdminInstant(round.registrationEnd),
                    )
                  : registrationOpenWindow.opensImmediately
                    ? messages.thesis.admin.openRegistrationEarlyHint.replace(
                        '{start}', formatRoundInstant(locale, round.registrationStart),
                      )
                    : '';
              const restoreBlockedMessages = (round.restoreBlockers ?? []).flatMap((code) => {
                const message = messages.common.campusErrors.codes[code];
                return message ? [message] : [];
              });
              const restoreBlockerId = `restore-blockers-${round.id}`;
              const publishReadiness = publishResultsReadinessForRound(publishReadinessByRound, round.id);
              const publishResultsBlocker = publishReadiness.preview
                ? publishResultsBlockerCode(publishReadiness.preview)
                : null;
              const publishResultsDisabledReason = publishReadiness.error
                || (publishReadiness.loading
                  ? messages.thesis.admin.checkingPublishReadiness
                  : publishReadiness.preview
                    ? publishResultsBlocker
                      ? campusErrorForCode(publishResultsBlocker)
                        ?? messages.thesis.admin.transitionFailure
                      : ''
                    : messages.thesis.admin.publishPreviewUnavailable);
              const canManageGlobalLifecycle = Boolean(isAdmin || isSuperAdmin);
              const retractUnavailableReason = !canManageGlobalLifecycle
                ? messages.thesis.admin.globalAdminRequired
                : round.closure == null
                  ? messages.thesis.admin.retractWindowUnavailable
                  : round.closure.closeAllowed !== false
                    ? messages.thesis.admin.retractWindowClosed
                    : '';
              const closeRoundUnavailableReason = [
                !canManageGlobalLifecycle ? messages.thesis.admin.globalAdminRequired : '',
                round.closure?.closeAllowed === false
                  ? round.closure.closeBlocker === 'RETRACT_WINDOW_OPEN'
                    ? messages.thesis.admin.closeBlockedRetractWindow.replace(
                        '{end}',
                        round.closure.retractWindowEndsAt
                          ? formatAdminInstant(round.closure.retractWindowEndsAt)
                          : messages.thesis.admin.unknownScheduleTime,
                      )
                    : messages.thesis.admin.closeStatusUnavailable
                  : '',
              ].filter(Boolean).join(' ');
              const deleteUnavailableReason = round.deletion?.deletable
                ? messages.thesis.admin.deleteRoundAvailable
                : [
                    messages.thesis.admin.deleteRoundUnavailable,
                    deleteBlockerSummary(round),
                  ].filter(Boolean).join(' ');
              // Mirrors ThesisCouncilService.lockMutableRound: once a round is
              // terminal every council control below can only be refused, so
              // the card keeps the roster readable but drops the actions.
              const councilLocked =
                round.status === 'CANCELLED'
                || round.status === 'CLOSED'
                || round.status === 'RESULTS_PUBLISHED';
              return (
                <Card key={round.id} className="overflow-hidden">
                  <CardHeader
                    className="cursor-pointer transition-colors hover:bg-secondary/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    role="button"
                    tabIndex={0}
                    aria-expanded={isExpanded}
                    onClick={() => toggleExpand(round.id)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter' || event.key === ' ') {
                        event.preventDefault();
                        toggleExpand(round.id);
                      }
                    }}
                  >
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex items-center gap-4">
                        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
                          <CalendarDays className="h-5 w-5" />
                        </div>
                        <div>
                          <CardTitle className="text-lg">{round.name}</CardTitle>
                          <CardDescription className="mt-1">
                            {messages.thesis.admin.thesisTypeOptions[round.thesisType as keyof typeof messages.thesis.admin.thesisTypeOptions] ?? round.thesisType} · {messages.thesis.admin.registrationWindowLabel}: {formatRoundWindow(locale, round.registrationStart, round.registrationEnd)}
                          </CardDescription>
                        </div>
                      </div>
                      <div className="flex items-center gap-3">
                        <StatusBadge status={round.status} />
                        {isExpanded ? (
                          <ChevronUp className="h-5 w-5 text-muted-foreground" />
                        ) : (
                          <ChevronDown className="h-5 w-5 text-muted-foreground" />
                        )}
                      </div>
                    </div>
                  </CardHeader>
                  {isExpanded ? (
                    <CardContent className="space-y-4 border-t border-border/60 bg-secondary/20">
                      {detailLoading ? <p role="status" className="pt-4 text-sm text-muted-foreground">{messages.common.states.loading}</p> : null}
                      <div className="flex flex-wrap items-center justify-between gap-2 pt-2">
                        <div className="flex flex-wrap gap-2">
                          {round.status === 'DRAFT' ? (
                            <Button type="button" size="sm" variant="outline" onClick={() => void transitionRound(round.id, 'open-proposals')} disabled={isSaving}>
                              {messages.thesis.admin.openProposals}
                            </Button>
                          ) : null}
                          {round.status === 'PROPOSAL_OPEN' ? (
                            <Button type="button" size="sm" onClick={() => void transitionRound(round.id, 'publish')} disabled={isSaving}>
                              {messages.thesis.admin.publishProposals}
                            </Button>
                          ) : null}
                          {round.status === 'PROPOSALS_PUBLISHED' ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => void transitionRound(round.id, 'open', round)}
                                disabled={isSaving || registrationOpenWindow.disabled}
                                aria-describedby={`registration-open-reason-${round.id}`}
                              >
                                {messages.thesis.admin.openRegistration}
                              </Button>
                              {registrationOpenReason ? (
                                <p
                                  id={`registration-open-reason-${round.id}`}
                                  role={registrationOpenWindow.disabled ? 'alert' : 'status'}
                                  className="max-w-md text-xs text-muted-foreground"
                                >
                                  {registrationOpenReason}
                                </p>
                              ) : <span id={`registration-open-reason-${round.id}`} className="sr-only">{messages.thesis.admin.openRegistrationAvailable}</span>}
                            </div>
                          ) : null}
                          {round.status === 'REGISTRATION_OPEN' ? (
                            <Button type="button" size="sm" variant="outline" onClick={() => void transitionRound(round.id, 'close')} disabled={isSaving}>
                              {messages.thesis.admin.closeRegistration}
                            </Button>
                          ) : null}
                          {round.status === 'REGISTRATION_CLOSED' ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                onClick={() => void transitionRound(round.id, 'publish-results')}
                                disabled={isSaving || publishReadiness.loading || Boolean(publishResultsBlocker)}
                                aria-describedby={`publish-results-reason-${round.id}`}
                              >
                                {messages.thesis.admin.publishResults}
                              </Button>
                              {publishResultsBlocker || publishReadiness.error ? (
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="ghost"
                                  onClick={() => void refreshPublishResultsPreview(round.id)}
                                  disabled={isSaving || publishReadiness.loading}
                                >
                                  {messages.thesis.admin.refreshPublishReadiness}
                                </Button>
                              ) : null}
                              {publishResultsDisabledReason ? (
                                <p
                                  id={`publish-results-reason-${round.id}`}
                                  role={publishReadiness.error || publishResultsBlocker ? 'alert' : 'status'}
                                  className="max-w-md text-xs text-muted-foreground"
                                >
                                  {publishResultsDisabledReason}
                                </p>
                              ) : <span id={`publish-results-reason-${round.id}`} className="sr-only">{messages.thesis.admin.publishResultsAvailable}</span>}
                            </div>
                          ) : null}
                          {/* F1 escape hatch: a closed round whose report
                              deadline passed gets a visible way to reopen the
                              submission window without a code change. */}
                          {round.status === 'REGISTRATION_CLOSED' && round.gvpbDeadline ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openExtendDeadlineModal(round)}
                              disabled={isSaving}
                            >
                              {messages.thesis.admin.reportDeadlineExtend}
                            </Button>
                          ) : null}
                          {/* Missed counter-review (GVPB) deadline wedge: a live
                              round gets an audited forward-only extension so a
                              lapsed reviewer deadline cannot strand grading. */}
                          {(round.status === 'PROPOSALS_PUBLISHED' ||
                            round.status === 'REGISTRATION_OPEN' ||
                            round.status === 'REGISTRATION_CLOSED') &&
                          round.gvpbDeadline ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => openExtendDeadlineModal(round, 'gvpb')}
                                disabled={isSaving || Boolean(gvpbExtensionUnavailableReason)}
                                aria-describedby={`gvpb-deadline-reason-${round.id}`}
                              >
                                {messages.thesis.admin.gvpbDeadlineExtend}
                              </Button>
                              {gvpbExtensionUnavailableReason ? (
                                <p id={`gvpb-deadline-reason-${round.id}`} role="status" className="max-w-md text-xs text-muted-foreground">
                                  {gvpbExtensionUnavailableReason}
                                </p>
                              ) : <span id={`gvpb-deadline-reason-${round.id}`} className="sr-only">{messages.thesis.admin.gvpbExtensionAvailable}</span>}
                            </div>
                          ) : null}
                          {/* Late-topic escape: a supervisor who missed the
                              proposal window still gets a publishable topic
                              while students can register (audited). */}
                          {(round.status === 'PROPOSALS_PUBLISHED' ||
                            round.status === 'REGISTRATION_OPEN') ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => openReasonAction({ kind: 'lateTopic', roundId: round.id })}
                                disabled={isSaving || lateTopicAvailabilityState.disabled}
                                aria-describedby={`late-topic-reason-${round.id}`}
                              >
                                {messages.thesis.admin.lateTopic}
                              </Button>
                              {lateTopicUnavailableReason ? (
                                <p id={`late-topic-reason-${round.id}`} role="status" className="max-w-md text-xs text-muted-foreground">
                                  {lateTopicUnavailableReason}
                                </p>
                              ) : <span id={`late-topic-reason-${round.id}`} className="sr-only">{messages.thesis.admin.lateTopicAvailable}</span>}
                            </div>
                          ) : null}
                          {/* Roster override: a group still below size after
                              registration closed gets an audited admin add. */}
                          {round.status === 'REGISTRATION_CLOSED' ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openReasonAction({ kind: 'rosterOverride', roundId: round.id })}
                              disabled={isSaving}
                            >
                              {messages.thesis.admin.rosterOverride}
                            </Button>
                          ) : null}
                          {round.status === 'REGISTRATION_CLOSED' ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openReasonAction({ kind: 'topicReassign', roundId: round.id })}
                              disabled={isSaving}
                            >
                              {messages.thesis.admin.topicReassign}
                            </Button>
                          ) : null}
                          {round.status === 'RESULTS_PUBLISHED' ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="ghost"
                                className="text-destructive hover:text-destructive"
                                onClick={() => openReasonAction({ kind: 'retractResults', roundId: round.id })}
                                disabled={isSaving || !canManageGlobalLifecycle || round.closure?.closeAllowed !== false}
                                aria-describedby={`retract-results-reason-${round.id}`}
                              >
                                {messages.thesis.admin.retractResults}
                              </Button>
                              {retractUnavailableReason ? (
                                <p id={`retract-results-reason-${round.id}`} role="status" className="max-w-md text-xs text-muted-foreground">
                                  {retractUnavailableReason}
                                </p>
                              ) : <span id={`retract-results-reason-${round.id}`} className="sr-only">{messages.thesis.admin.retractResultsAvailable}</span>}
                            </div>
                          ) : null}
                          {round.status === 'RESULTS_PUBLISHED' ? (
                            <div className="flex flex-col gap-1">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => openReasonAction({
                                  kind: 'closeRound', roundId: round.id, roundName: round.name,
                                })}
                                disabled={isSaving || !canManageGlobalLifecycle || round.closure?.closeAllowed === false}
                                aria-describedby={`close-round-reason-${round.id}`}
                              >
                                {messages.thesis.admin.closeRound}
                              </Button>
                              {closeRoundUnavailableReason ? (
                                <p id={`close-round-reason-${round.id}`} role="status" className="max-w-md text-xs text-muted-foreground">
                                  {closeRoundUnavailableReason}
                                </p>
                              ) : <span id={`close-round-reason-${round.id}`} className="sr-only">{messages.thesis.admin.closeRoundAvailable}</span>}
                            </div>
                          ) : null}
                          {round.status === 'CLOSED' && round.closure?.reopenAllowed !== false ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => openReasonAction({ kind: 'reopenRound', roundId: round.id })}
                              disabled={isSaving || !canManageGlobalLifecycle}
                              aria-describedby={`reopen-round-reason-${round.id}`}
                            >
                              {messages.thesis.admin.reopenRound}
                            </Button>
                          ) : null}
                          {round.status === 'CLOSED' && round.closure?.reopenAllowed !== false ? (
                            <span id={`reopen-round-reason-${round.id}`} role={!canManageGlobalLifecycle ? 'status' : undefined} className={!canManageGlobalLifecycle ? 'text-xs text-muted-foreground' : 'sr-only'}>
                              {!canManageGlobalLifecycle ? messages.thesis.admin.globalAdminRequired : messages.thesis.admin.reopenRoundAvailable}
                            </span>
                          ) : null}
                          {(round.status === 'DRAFT' || round.status === 'PROPOSAL_OPEN' || round.status === 'PROPOSALS_PUBLISHED') ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              onClick={() => openEditRoundModal(round)}
                              disabled={isSaving}
                            >
                              {messages.thesis.admin.editRound}
                            </Button>
                          ) : null}
                          {round.status === 'CANCELLED' ? (
                            <>
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                onClick={() => void restoreRound(round)}
                                disabled={isSaving || restoreBlockedMessages.length > 0}
                                aria-describedby={restoreBlockedMessages.length > 0 ? restoreBlockerId : undefined}
                              >
                                {messages.thesis.admin.restoreRound}
                              </Button>
                              {restoreBlockedMessages.length > 0 ? (
                                <p id={restoreBlockerId} role="alert" className="w-full text-sm text-destructive">
                                  {restoreBlockedMessages.join(' ')}
                                </p>
                              ) : null}
                            </>
                          ) : null}
                          {round.status !== 'RESULTS_PUBLISHED'
                          && round.status !== 'CLOSED'
                          && round.status !== 'CANCELLED' ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              className="text-destructive hover:text-destructive"
                              onClick={() => void cancelRound(round.id)}
                              disabled={isSaving}
                            >
                              {messages.thesis.admin.cancelRound}
                            </Button>
                          ) : null}
                          {round.deletion ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="ghost"
                              className="text-destructive hover:text-destructive"
                              onClick={() => openReasonAction({
                                kind: 'deleteRound', roundId: round.id, roundName: round.name,
                              })}
                              disabled={isSaving || !round.deletion.deletable}
                              aria-describedby={`delete-round-reason-${round.id}`}
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                              {messages.thesis.admin.deleteRound}
                            </Button>
                          ) : null}
                          {round.deletion ? (
                            <span
                              id={`delete-round-reason-${round.id}`}
                              role={!round.deletion.deletable ? 'status' : undefined}
                              className={!round.deletion.deletable ? 'text-xs text-muted-foreground' : 'sr-only'}
                            >
                              {round.deletion.deletable
                                ? messages.thesis.admin.deleteRoundAvailable
                                : deleteUnavailableReason}
                            </span>
                          ) : null}
                        </div>

                        <div className="flex flex-wrap items-center gap-2">
                          <LocalizedLink
                            href={`/dashboard/thesis?roundId=${round.id}&tab=repository`}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary hover:bg-primary/20 hover:border-primary transition-all shadow-2xs"
                          >
                            <Archive className="h-3.5 w-3.5" />
                            <span>{locale === 'vi' ? 'Kho lưu trữ luận án & báo cáo →' : 'Thesis & Report Archive →'}</span>
                          </LocalizedLink>
                          <LocalizedLink
                            href={`/dashboard/thesis?roundId=${round.id}`}
                            className="inline-flex items-center gap-1.5 rounded-lg border border-border/80 bg-card px-3 py-1.5 text-xs font-semibold text-primary hover:bg-secondary/60 hover:border-primary/40 transition-all shadow-2xs"
                          >
                            <ExternalLink className="h-3.5 w-3.5" />
                            <span>{locale === 'vi' ? 'Quản lý Đề tài & Nhóm trong Workspace →' : 'Manage Topics & Groups in Workspace →'}</span>
                          </LocalizedLink>
                        </div>
                      </div>

                      {/* Defense Councils Management Section */}
                      <div className="mt-6 border-t border-border/60 pt-6">
                        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between pb-4">
                          <div>
                            <h3 className="text-base font-semibold text-foreground flex items-center gap-2">
                              <Users className="h-4 w-4 text-primary" />
                              {messages.thesis.councils.title}
                            </h3>
                            <p className="text-xs text-muted-foreground mt-0.5">
                              {messages.thesis.councils.description}
                            </p>
                          </div>
                          {!councilLocked ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => {
                                setModalError('');
                                setTargetRoundIdForCouncil(round.id);
                                setShowCreateCouncilModal(true);
                              }}
                            >
                              <Plus className="mr-1.5 h-3.5 w-3.5" />
                              {messages.thesis.councils.createCouncil}
                            </Button>
                          ) : null}
                        </div>

                        {councils.length === 0 ? (
                          <div className="rounded-xl border border-dashed border-border/80 p-6 text-center text-sm text-muted-foreground bg-card/50">
                            {messages.thesis.councils.noCouncils}
                          </div>
                        ) : (
                          <div className="grid gap-4 md:grid-cols-2">
                            {councils.map((council) => {
                              const members = council.members || [];
                              const hasChair = members.some((m) => m.memberRole === 'CHAIR');
                              const hasSecretary = members.some((m) => m.memberRole === 'SECRETARY');
                              const isComplete = members.length >= 3 && hasChair && hasSecretary;
                              const topicIds = (council as unknown as { topicIds?: string[] }).topicIds || [];

                              return (
                                <div
                                  key={council.id}
                                  className="rounded-xl border border-border/80 bg-card p-4 shadow-xs space-y-4"
                                >
                                  {/* Council Header */}
                                  <div className="flex items-center justify-between gap-2 border-b border-border/50 pb-3">
                                    <div>
                                      <h4 className="font-medium text-foreground">{council.name}</h4>
                                      <p className="text-xs text-muted-foreground mt-0.5">
                                        {messages.thesis.councils.membersCount.replace('{count}', String(members.length))}
                                      </p>
                                    </div>
                                    <span
                                      className={cn(
                                        'inline-flex items-center rounded-md px-2.5 py-0.5 text-xs font-medium',
                                        isComplete
                                          ? 'bg-status-success/15 text-status-success-foreground border border-status-success/30'
                                          : 'bg-status-warning/15 text-status-warning-foreground border border-status-warning/30'
                                      )}
                                    >
                                      {isComplete
                                        ? messages.thesis.councils.councilCompleteBadge
                                        : messages.thesis.councils.councilIncompleteBadge.replace('{count}', String(members.length))}
                                    </span>
                                  </div>

                                  {/* Council Members */}
                                  <div className="space-y-2">
                                    <div className="flex items-center justify-between">
                                      <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                        {messages.thesis.councils.membersList}
                                      </span>
                                      {members.length < 5 && !councilLocked && (
                                        <Button
                                          type="button"
                                          variant="ghost"
                                          size="sm"
                                          className="h-7 text-xs px-2 text-primary"
                                          onClick={() => {
                                            setModalError('');
                                            setTargetCouncilIdForMember(council.id);
                                            setTargetRoundIdForCouncil(round.id);
                                            setShowAddMemberModal(true);
                                          }}
                                        >
                                          <UserPlus className="mr-1 h-3 w-3" />
                                          {messages.thesis.councils.addMember}
                                        </Button>
                                      )}
                                    </div>

                                    {members.length === 0 ? (
                                      <p className="text-xs text-muted-foreground italic">
                                        {messages.thesis.councils.councilSeatOrderHint}
                                      </p>
                                    ) : (
                                      <div className="space-y-1.5">
                                        {members.map((m) => {
                                          const lect = lecturers.find((l) => l.id === m.lecturerId);
                                          const lectName = lect?.user
                                            ? `${lect.user.lastName} ${lect.user.firstName}`
                                            : m.lecturerId;
                                          const roleLabel =
                                            m.memberRole === 'CHAIR'
                                              ? messages.thesis.councils.roleChair
                                              : m.memberRole === 'SECRETARY'
                                              ? messages.thesis.councils.roleSecretary
                                              : messages.thesis.councils.roleMember;

                                          return (
                                            <div
                                              key={m.lecturerId}
                                              className="flex items-center justify-between rounded-lg bg-muted/40 px-2.5 py-1.5 text-xs"
                                            >
                                              <div className="flex items-center gap-2">
                                                <span
                                                  className={cn(
                                                    'rounded px-1.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider',
                                                    m.memberRole === 'CHAIR'
                                                      ? 'bg-primary/20 text-primary border border-primary/30'
                                                      : m.memberRole === 'SECRETARY'
                                                      ? 'bg-status-info/20 text-status-info-foreground border border-status-info/30'
                                                      : 'bg-muted text-muted-foreground border border-border/60'
                                                  )}
                                                >
                                                  {roleLabel}
                                                </span>
                                                <span className="font-medium text-foreground">{lectName}</span>
                                                {lect?.employeeId && (
                                                  <span className="text-muted-foreground">({lect.employeeId})</span>
                                                )}
                                              </div>
                                              {!councilLocked ? (
                                                <div className="flex items-center gap-1">
                                                  <button
                                                    type="button"
                                                    className="text-muted-foreground hover:text-primary p-1 transition-colors"
                                                    title={messages.thesis.councils.replaceMember}
                                                    aria-label={messages.thesis.councils.replaceMember}
                                                    onClick={() =>
                                                      openReasonAction({
                                                        kind: 'replaceMember',
                                                        roundId: round.id,
                                                        councilId: council.id,
                                                        lecturerId: m.lecturerId,
                                                      })
                                                    }
                                                    disabled={isSaving}
                                                  >
                                                    <RefreshCw className="h-3.5 w-3.5" />
                                                  </button>
                                                  <button
                                                    type="button"
                                                    className="text-muted-foreground hover:text-destructive p-1 transition-colors"
                                                    title={messages.thesis.councils.removeMember}
                                                    aria-label={messages.thesis.councils.removeMember}
                                                    onClick={() => void handleRemoveMember(council.id, m.lecturerId, round.id)}
                                                    disabled={isSaving}
                                                  >
                                                    <Trash2 className="h-3.5 w-3.5" />
                                                  </button>
                                                </div>
                                              ) : null}
                                            </div>
                                          );
                                        })}
                                      </div>
                                    )}
                                  </div>

                                  {/* Assigned Topics */}
                                  <div className="space-y-2 border-t border-border/40 pt-3">
                                    <div className="flex items-center justify-between">
                                      <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                                        {messages.thesis.councils.assignedTopics} ({topicIds.length})
                                      </span>
                                      {isComplete && !councilLocked && (
                                        <Button
                                          type="button"
                                          variant="ghost"
                                          size="sm"
                                          className="h-7 text-xs px-2 text-primary"
                                          onClick={() => {
                                            setModalError('');
                                            setTargetCouncilIdForTopic(council.id);
                                            setTargetRoundIdForCouncil(round.id);
                                            // Warm supervisor rows for every
                                            // topic the modal may offer so the
                                            // disabled reasons land together.
                                            const assigned = new Set(
                                              councils.flatMap((c) => c.topicIds ?? []),
                                            );
                                            ensureTopicSupervisors(
                                              topics
                                                .filter(
                                                  (t) =>
                                                    !assigned.has(t.id) &&
                                                    t.finalScore == null &&
                                                    (t.status === 'PUBLISHED' ||
                                                      t.status === 'APPROVED'),
                                                )
                                                .map((t) => t.id),
                                            );
                                            setShowAssignTopicModal(true);
                                          }}
                                        >
                                          <Plus className="mr-1 h-3 w-3" />
                                          {messages.thesis.councils.assignTopic}
                                        </Button>
                                      )}
                                    </div>

                                    {topicIds.length === 0 ? (
                                      <p className="text-xs text-muted-foreground italic">
                                        {messages.thesis.councils.noAssignedTopics}
                                      </p>
                                    ) : (
                                      <div className="space-y-1.5">
                                        {topicIds.map((tid: string) => {
                                          const top = topics.find((t) => t.id === tid);
                                          const reviewerId = top?.gvpbLecturerId ?? '';
                                          const reviewerLecturer = reviewerId
                                            ? lecturers.find((l) => l.id === reviewerId)
                                            : undefined;
                                          const reviewerName = reviewerLecturer?.user
                                            ? `${reviewerLecturer.user.lastName} ${reviewerLecturer.user.firstName}`
                                            : reviewerId;
                                          return (
                                            <div
                                              key={tid}
                                              className="rounded-lg border border-border/50 bg-background/50 px-2.5 py-1.5 text-xs space-y-1.5"
                                            >
                                              <div className="flex items-center justify-between">
                                                <span className="font-medium text-foreground line-clamp-1">
                                                  {top?.title || tid}
                                                </span>
                                                {top?.finalScore != null && (
                                                  <span className="shrink-0 rounded bg-status-success/20 px-1.5 py-0.5 text-[11px] font-semibold text-status-success-foreground">
                                                    {messages.thesis.councils.finalScorePoints.replace('{score}', String(top.finalScore))}
                                                  </span>
                                                )}
                                              </div>
                                              {/* GVPB counter-reviewer (spec: one reviewer per defended topic) */}
                                              {!councilLocked ? (
                                              <div className="flex items-center gap-1.5">
                                                <Combobox
                                                  aria-label={messages.thesis.admin.assignReviewer}
                                                  className="h-7 text-xs"
                                                  wrapperClassName="flex-1"
                                                  value={reviewerDrafts[tid] ?? reviewerId}
                                                  onChange={(next) =>
                                                    setReviewerDrafts((prev) => ({
                                                      ...prev,
                                                      [tid]: next,
                                                    }))
                                                  }
                                                  allowClear
                                                  placeholder={`-- ${messages.thesis.admin.assignReviewer} --`}
                                                  options={lecturers.map((l) => {
                                                    const conflict = reviewerConflict({
                                                      lecturer: l,
                                                      supervisorIds: topicSupervisors[tid] ?? [],
                                                      councilMemberIds: (council.members ?? []).map(
                                                        (m) => m.lecturerId,
                                                      ),
                                                    });
                                                    return {
                                                      value: l.id,
                                                      label: l.user
                                                        ? `${l.user.lastName} ${l.user.firstName}`
                                                        : l.id,
                                                      description: l.department?.name,
                                                      keywords: [l.employeeId, l.user?.email]
                                                        .filter(Boolean)
                                                        .join(' '),
                                                      disabled: conflict != null,
                                                      disabledReason: conflict
                                                        ? messages.thesis.councils.conflicts[conflict]
                                                        : undefined,
                                                    };
                                                  })}
                                                />
                                                <Button
                                                  type="button"
                                                  variant="ghost"
                                                  size="sm"
                                                  className="h-7 px-2 text-xs text-primary"
                                                  disabled={
                                                    isSaving ||
                                                    !(reviewerDrafts[tid] ?? reviewerId) ||
                                                    (reviewerDrafts[tid] ?? reviewerId) === reviewerId
                                                  }
                                                  onClick={() => void handleAssignReviewer(tid, round.id)}
                                                >
                                                  {reviewerId
                                                    ? messages.thesis.admin.reassignReviewer
                                                    : messages.thesis.admin.assignReviewer}
                                                </Button>
                                              </div>
                                              ) : null}
                                              {reviewerId ? (
                                                <p className="text-[11px] text-muted-foreground">
                                                  {messages.thesis.admin.currentReviewer}: {reviewerName}
                                                </p>
                                              ) : null}
                                              {top?.resultStatus === 'REVIEW_FAILED' ? (
                                                <span className="inline-block rounded bg-destructive/10 px-1.5 py-0.5 text-[11px] font-semibold text-destructive">
                                                  {messages.thesis.admin.resultReviewFailed}
                                                </span>
                                              ) : null}
                                              {top?.resultStatus === 'WITHDRAWN' ? (
                                                <span className="inline-block rounded bg-muted px-1.5 py-0.5 text-[11px] font-semibold text-muted-foreground">
                                                  {messages.thesis.admin.resultWithdrawn}
                                                </span>
                                              ) : null}
                                              {top?.gvpbWaivedReason ? (
                                                <span className="inline-block rounded bg-status-info/20 px-1.5 py-0.5 text-[11px] font-semibold text-status-info-foreground">
                                                  {messages.thesis.admin.gvpbWaivedBadge}
                                                </span>
                                              ) : null}
                                              {top ? (
                                                <div className="flex flex-wrap gap-1">
                                                  {top.finalScore == null && !councilLocked ? (
                                                    <Button
                                                      type="button"
                                                      variant="ghost"
                                                      size="sm"
                                                      className="h-7 px-2 text-xs text-muted-foreground"
                                                      disabled={isSaving}
                                                      onClick={() =>
                                                        openReasonAction({
                                                          kind: 'unassignTopic',
                                                          roundId: round.id,
                                                          councilId: council.id,
                                                          topicId: tid,
                                                        })
                                                      }
                                                    >
                                                      {messages.thesis.councils.unassignTopic}
                                                    </Button>
                                                  ) : null}
                                                  {top.finalScore == null && !councilLocked &&
                                                  (top.gvpbWaivedReason != null || round.counterReviewWaiverAllowed) ? (
                                                    top.gvpbWaivedReason ? (
                                                      <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        className="h-7 px-2 text-xs text-muted-foreground"
                                                        disabled={isSaving}
                                                        onClick={() =>
                                                          openReasonAction({
                                                            kind: 'revokeGvpbWaiver',
                                                            roundId: round.id,
                                                            topicId: tid,
                                                          })
                                                        }
                                                      >
                                                        {messages.thesis.admin.revokeGvpbWaiver}
                                                      </Button>
                                                    ) : (
                                                      <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        className="h-7 px-2 text-xs text-muted-foreground"
                                                        disabled={isSaving}
                                                        onClick={() =>
                                                          openReasonAction({
                                                            kind: 'waiveGvpb',
                                                            roundId: round.id,
                                                            topicId: tid,
                                                          })
                                                        }
                                                      >
                                                        {messages.thesis.admin.waiveGvpb}
                                                      </Button>
                                                    )
                                                  ) : null}
                                                  {top.finalScore == null && !councilLocked ? (
                                                    top.resultStatus === 'WITHDRAWN' ? (
                                                      <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        className="h-7 px-2 text-xs text-primary"
                                                        disabled={isSaving}
                                                        onClick={() =>
                                                          openReasonAction({
                                                            kind: 'reinstateTopic',
                                                            roundId: round.id,
                                                            topicId: tid,
                                                          })
                                                        }
                                                      >
                                                        {messages.thesis.admin.reinstateTopic}
                                                      </Button>
                                                    ) : (
                                                      <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="sm"
                                                        className="h-7 px-2 text-xs text-destructive"
                                                        disabled={isSaving}
                                                        onClick={() =>
                                                          openReasonAction({
                                                            kind: 'withdrawTopic',
                                                            roundId: round.id,
                                                            topicId: tid,
                                                          })
                                                        }
                                                      >
                                                        {messages.thesis.admin.withdrawTopic}
                                                      </Button>
                                                    )
                                                  ) : null}
                                                </div>
                                              ) : null}
                                            </div>
                                          );
                                        })}
                                      </div>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    </CardContent>
                  ) : null}
                </Card>
              );
            })}
          </div>
        )}
      </div>

      <Modal
        isOpen={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        title={messages.thesis.admin.createRound}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.roundName}
            <Input placeholder={messages.thesis.admin.roundNamePlaceholder} value={formName} onChange={(e) => setFormName(e.target.value)} />
          </label>
          <Select
            label={messages.thesis.admin.thesisType}
            value={formType}
            onChange={(e) => {
              const next = e.target.value;
              setFormType(next);
              // Conditional fields would otherwise smuggle stale values into a
              // type where the server rejects them (e.g. a KLTN GVPB deadline
              // on a MON_HOC round).
              if (next !== 'TLCN' && next !== 'KLTN') {
                setFormGvpb('');
                setFormReportDate('');
              }
              if (next !== 'KLTN') {
                setFormDefenseDate('');
              }
            }}
            options={[
              { value: 'MON_HOC', label: messages.thesis.admin.thesisTypeOptions.MON_HOC },
              { value: 'NCKH', label: messages.thesis.admin.thesisTypeOptions.NCKH },
              { value: 'TLCN', label: messages.thesis.admin.thesisTypeOptions.TLCN },
              { value: 'KLTN', label: messages.thesis.admin.thesisTypeOptions.KLTN },
            ]}
          />
          <div>
            <p className="text-sm font-medium text-foreground">{messages.thesis.admin.lecturerWindow}</p>
            <div className="mt-2 grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitStart}
                <Input type="datetime-local" step="60" value={formLecturerStart} onChange={(e) => setFormLecturerStart(e.target.value)} />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitEnd}
                <Input type="datetime-local" step="60" value={formLecturerEnd} onChange={(e) => setFormLecturerEnd(e.target.value)} />
              </label>
            </div>
          </div>
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.proposalPublishAt}
            <Input type="datetime-local" step="60" value={formProposal} onChange={(e) => setFormProposal(e.target.value)} />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationStart}
              <Input type="datetime-local" step="60" value={formStart} onChange={(e) => setFormStart(e.target.value)} />
            </label>
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationEnd}
              <Input type="datetime-local" step="60" value={formEnd} onChange={(e) => setFormEnd(e.target.value)} />
            </label>
          </div>
          {needsGvpb ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.gvpbDeadline} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formGvpb} onChange={(e) => setFormGvpb(e.target.value)} required />
            </label>
          ) : null}
          {needsGvpb ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.reportDate} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formReportDate} onChange={(e) => setFormReportDate(e.target.value)} required />
            </label>
          ) : null}
          {needsDefense ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.defenseDate} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formDefenseDate} onChange={(e) => setFormDefenseDate(e.target.value)} required />
            </label>
          ) : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setShowCreateModal(false)}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="button" onClick={() => void createRound()} disabled={isSaving}>
              {messages.thesis.admin.createRound}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Create Council Modal */}
      <Modal
        isOpen={showCreateCouncilModal}
        onClose={() => setShowCreateCouncilModal(false)}
        title={messages.thesis.councils.createCouncil}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.councils.councilName}
            <Input
              placeholder={messages.thesis.councils.councilNamePlaceholder}
              value={councilNameInput}
              onChange={(e) => setCouncilNameInput(e.target.value)}
            />
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setShowCreateCouncilModal(false)}>
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              onClick={() => void handleCreateCouncil(targetRoundIdForCouncil)}
              disabled={isSaving || !councilNameInput.trim()}
            >
              {messages.thesis.councils.createCouncil}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Add Council Member Modal */}
      <Modal
        isOpen={showAddMemberModal}
        onClose={() => setShowAddMemberModal(false)}
        title={messages.thesis.councils.addMember}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          <p className="text-xs text-muted-foreground">
            {messages.thesis.councils.councilSeatOrderHint}
          </p>
          {(() => {
            const activeCouncil = councils.find((c) => c.id === targetCouncilIdForMember);
            const members = activeCouncil?.members || [];
            const nextRole: ThesisCouncilMemberRole = !members.some((m) => m.memberRole === 'CHAIR')
              ? 'CHAIR'
              : !members.some((m) => m.memberRole === 'SECRETARY')
              ? 'SECRETARY'
              : 'MEMBER';
            const nextRoleLabel =
              nextRole === 'CHAIR'
                ? messages.thesis.councils.roleChair
                : nextRole === 'SECRETARY'
                ? messages.thesis.councils.roleSecretary
                : messages.thesis.councils.roleMember;

            const existingLecturerIds = new Set(members.map((m) => m.lecturerId));
            const availableLecturers = lecturers.filter((l) => !existingLecturerIds.has(l.id));
            const councilTopics = (activeCouncil?.topicIds ?? [])
              .map((tid) => topics.find((t) => t.id === tid))
              .filter((t): t is ThesisTopic => Boolean(t));

            return (
              <>
                <div className="rounded-lg border border-border/60 bg-muted/30 p-3 text-xs flex items-center justify-between">
                  <span className="text-muted-foreground">{messages.thesis.councils.nextSeatRequired}</span>
                  <span className="font-semibold text-primary">{nextRoleLabel}</span>
                </div>
                <Combobox
                  label={messages.thesis.councils.selectLecturer}
                  value={selectedLecturerId}
                  onChange={(next) => setSelectedLecturerId(next)}
                  placeholder={`-- ${messages.thesis.councils.selectLecturer} --`}
                  allowClear
                  emptyLabel={`-- ${messages.thesis.councils.selectLecturer} --`}
                  options={availableLecturers.map((l) => {
                    const conflict = councilMemberConflict({
                      lecturer: l,
                      assignedTopics: councilTopics,
                      topicSupervisors,
                    });
                    return {
                      value: l.id,
                      label: l.user ? `${l.user.lastName} ${l.user.firstName}` : l.id,
                      description: [l.department?.name, l.employeeId || l.user?.email]
                        .filter(Boolean)
                        .join(' · '),
                      keywords: [l.employeeId, l.user?.email].filter(Boolean).join(' '),
                      disabled: conflict != null,
                      disabledReason: conflict
                        ? messages.thesis.councils.conflicts[conflict]
                        : undefined,
                    };
                  })}
                />
                {lecturerLoadError ? (
                  <div className="flex items-center justify-between gap-3 rounded-md border border-status-warning/30 bg-status-warning/10 px-3 py-2 text-xs text-status-warning-foreground">
                    <span>{messages.thesis.councils.lecturerLoadFailed}</span>
                    <Button
                      type="button"
                      variant="outline"
                      className="h-7 px-2 text-xs"
                      onClick={() => void loadLecturers()}
                    >
                      {messages.common.actions.retry}
                    </Button>
                  </div>
                ) : null}
                <div className="flex justify-end gap-2 pt-2">
                  <Button type="button" variant="outline" onClick={() => setShowAddMemberModal(false)}>
                    {messages.common.actions.cancel}
                  </Button>
                  <Button
                    type="button"
                    onClick={() => void handleAddMember(targetCouncilIdForMember, targetRoundIdForCouncil)}
                    disabled={isSaving || !selectedLecturerId}
                  >
                    {messages.thesis.councils.addMember}
                  </Button>
                </div>
              </>
            );
          })()}
        </div>
      </Modal>

      {/* Edit Round Modal — reuses the create-form fields, prefilled */}
      <Modal
        isOpen={showEditRoundModal}
        onClose={() => {
          setShowEditRoundModal(false);
          setEditingRoundId('');
        }}
        title={messages.thesis.admin.editRoundTitle}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.roundName}
            <Input placeholder={messages.thesis.admin.roundNamePlaceholder} value={formName} onChange={(e) => setFormName(e.target.value)} />
          </label>
          <Select
            label={messages.thesis.admin.thesisType}
            value={formType}
            onChange={(e) => {
              const next = e.target.value;
              setFormType(next);
              if (next !== 'TLCN' && next !== 'KLTN') {
                setFormGvpb('');
                setFormReportDate('');
              }
              if (next !== 'KLTN') {
                setFormDefenseDate('');
              }
            }}
            options={[
              { value: 'MON_HOC', label: messages.thesis.admin.thesisTypeOptions.MON_HOC },
              { value: 'NCKH', label: messages.thesis.admin.thesisTypeOptions.NCKH },
              { value: 'TLCN', label: messages.thesis.admin.thesisTypeOptions.TLCN },
              { value: 'KLTN', label: messages.thesis.admin.thesisTypeOptions.KLTN },
            ]}
          />
          <div>
            <p className="text-sm font-medium text-foreground">{messages.thesis.admin.lecturerWindow}</p>
            <div className="mt-2 grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitStart}
                <Input type="datetime-local" step="60" value={formLecturerStart} onChange={(e) => setFormLecturerStart(e.target.value)} />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitEnd}
                <Input type="datetime-local" step="60" value={formLecturerEnd} onChange={(e) => setFormLecturerEnd(e.target.value)} />
              </label>
            </div>
          </div>
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.proposalPublishAt}
            <Input type="datetime-local" step="60" value={formProposal} onChange={(e) => setFormProposal(e.target.value)} />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationStart}
              <Input type="datetime-local" step="60" value={formStart} onChange={(e) => setFormStart(e.target.value)} />
            </label>
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationEnd}
              <Input type="datetime-local" step="60" value={formEnd} onChange={(e) => setFormEnd(e.target.value)} />
            </label>
          </div>
          {needsGvpb ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.gvpbDeadline} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formGvpb} onChange={(e) => setFormGvpb(e.target.value)} required />
            </label>
          ) : null}
          {needsGvpb ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.reportDate} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formReportDate} onChange={(e) => setFormReportDate(e.target.value)} required />
            </label>
          ) : null}
          {needsDefense ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.defenseDate} <span className="text-destructive">*</span>
              <Input type="datetime-local" step="60" value={formDefenseDate} onChange={(e) => setFormDefenseDate(e.target.value)} required />
            </label>
          ) : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => {
              setShowEditRoundModal(false);
              setEditingRoundId('');
            }}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="button" onClick={() => void updateRound()} disabled={isSaving}>
              {messages.thesis.admin.editRound}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Assign Topic Modal */}
      <Modal
        isOpen={showAssignTopicModal}
        onClose={() => setShowAssignTopicModal(false)}
        title={messages.thesis.councils.assignTopic}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          {(() => {
            const allAssigned = new Set<string>();
            for (const c of councils) {
              for (const tid of (c as unknown as { topicIds?: string[] }).topicIds || []) {
                allAssigned.add(tid);
              }
            }
            // The backend only accepts PUBLISHED/APPROVED topics — the merged
            // detail list also carries DRAFT proposals for review, and showing
            // one here just produced a guaranteed TOPIC_NOT_PUBLISHED 409.
            const assignableTopics = topics.filter(
              (t) =>
                !allAssigned.has(t.id) &&
                t.finalScore == null &&
                (t.status === 'PUBLISHED' || t.status === 'APPROVED')
            );
            const targetCouncil = councils.find((c) => c.id === targetCouncilIdForTopic);
            const targetMemberIds = (targetCouncil?.members ?? []).map((m) => m.lecturerId);
            const targetOfficerIds = (targetCouncil?.members ?? [])
              .filter((m) => m.memberRole === 'CHAIR' || m.memberRole === 'SECRETARY')
              .map((m) => m.lecturerId);

            return (
              <>
                <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                  {messages.thesis.councils.selectTopic}
                  {assignableTopics.length === 0 ? (
                    <p className="text-xs text-muted-foreground italic py-2">
                      {messages.thesis.councils.noAssignedTopics}
                    </p>
                  ) : (
                    <Combobox
                      aria-label={messages.thesis.councils.selectTopic}
                      value={selectedTopicIdToAssign}
                      onChange={(next) => setSelectedTopicIdToAssign(next)}
                      placeholder={`-- ${messages.thesis.councils.selectTopic} --`}
                      allowClear
                      emptyLabel={`-- ${messages.thesis.councils.selectTopic} --`}
                      options={assignableTopics.map((t) => {
                        const conflict = assignTopicConflict({
                          topicSupervisorIds: topicSupervisors[t.id] ?? [],
                          reviewerId: t.gvpbLecturerId,
                          memberIds: targetMemberIds,
                          officerIds: targetOfficerIds,
                          lecturers,
                        });
                        return {
                          value: t.id,
                          label: t.title,
                          disabled: conflict != null,
                          disabledReason: conflict
                            ? messages.thesis.councils.conflicts[conflict]
                            : undefined,
                        };
                      })}
                    />
                  )}
                </label>
                <div className="flex justify-end gap-2 pt-2">
                  <Button type="button" variant="outline" onClick={() => setShowAssignTopicModal(false)}>
                    {messages.common.actions.cancel}
                  </Button>
                  <Button
                    type="button"
                    onClick={() => void handleAssignTopic(targetCouncilIdForTopic, targetRoundIdForCouncil)}
                    disabled={isSaving || !selectedTopicIdToAssign}
                  >
                    {messages.thesis.councils.assignTopic}
                  </Button>
                </div>
              </>
            );
          })()}
        </div>
      </Modal>
      {/* Extend Report Deadline Modal — the audited escape hatch for a closed
          round whose report deadline already passed. */}
      <Modal
        isOpen={showExtendDeadlineModal}
        onClose={() => {
          if (!isSaving) {
            setShowExtendDeadlineModal(false);
            setExtendDeadlineRoundId('');
          }
        }}
        title={
          extendDeadlineKind === 'gvpb'
            ? messages.thesis.admin.gvpbDeadlineModalTitle
            : messages.thesis.admin.reportDeadlineModalTitle
        }
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {extendDeadlineKind === 'gvpb'
              ? messages.thesis.admin.gvpbDeadlineModalDescription
              : messages.thesis.admin.reportDeadlineModalDescription}
          </p>
          {extendDeadlineError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {extendDeadlineError}
            </div>
          ) : null}
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {extendDeadlineKind === 'gvpb'
              ? messages.thesis.admin.gvpbDeadline
              : messages.thesis.admin.reportDeadline}
            <Input
              type="datetime-local" step="0.001"
              value={extendDeadlineValue}
              onChange={(e) => setExtendDeadlineValue(e.target.value)}
              required
            />
          </label>
          {extendDeadlineKind === 'gvpb' ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.governanceReasonLabel}
              <Input
                type="text"
                value={extendDeadlineReason}
                onChange={(e) => setExtendDeadlineReason(e.target.value)}
                maxLength={500}
                required
              />
            </label>
          ) : null}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setShowExtendDeadlineModal(false)}
              disabled={isSaving}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              onClick={() => void extendReportDeadline()}
              disabled={isSaving || !extendDeadlineValue}
            >
              {extendDeadlineKind === 'gvpb'
                ? messages.thesis.admin.gvpbDeadlineExtend
                : messages.thesis.admin.reportDeadlineExtend}
            </Button>
          </div>
        </div>
      </Modal>
      {/* Reason-bearing governance actions share one modal; each call writes
          an audit row server-side with the entered reason. */}
      <Modal
        isOpen={reasonAction !== null}
        onClose={() => {
          if (!isSaving) setReasonAction(null);
        }}
        title={reasonActionTitle}
      >
        <div className="space-y-4">
          {reasonActionError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {reasonActionError}
            </div>
          ) : null}
          {reasonAction?.kind === 'replaceMember' ? (
            (() => {
              const replaceCouncil = councils.find((c) => c.id === reasonAction.councilId);
              const replaceCouncilTopics = (replaceCouncil?.topicIds ?? [])
                .map((tid) => topics.find((t) => t.id === tid))
                .filter((t): t is ThesisTopic => Boolean(t));
              return (
                <Combobox
                  label={messages.thesis.councils.selectLecturer}
                  value={replacementLecturerId}
                  onChange={(next) => setReplacementLecturerId(next)}
                  placeholder={`-- ${messages.thesis.councils.selectLecturer} --`}
                  allowClear
                  emptyLabel={`-- ${messages.thesis.councils.selectLecturer} --`}
                  options={lecturers
                    .filter(
                      (l) =>
                        l.id !== reasonAction.lecturerId &&
                        !(replaceCouncil?.members ?? []).some((m) => m.lecturerId === l.id),
                    )
                    .map((l) => {
                      const conflict = councilMemberConflict({
                        lecturer: l,
                        assignedTopics: replaceCouncilTopics,
                        topicSupervisors,
                      });
                      return {
                        value: l.id,
                        label: l.user ? `${l.user.lastName} ${l.user.firstName}` : l.id,
                        description: [l.department?.name, l.employeeId]
                          .filter(Boolean)
                          .join(' · '),
                        keywords: [l.employeeId, l.user?.email].filter(Boolean).join(' '),
                        disabled: conflict != null,
                        disabledReason: conflict
                          ? messages.thesis.councils.conflicts[conflict]
                          : undefined,
                      };
                    })}
                />
              );
            })()
          ) : null}
          {reasonAction?.kind === 'lateTopic' ? (
            <>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lateTopicDepartment}
                <Input
                  value={lateTopicForm.departmentId}
                  onChange={(e) => setLateTopicForm({ ...lateTopicForm, departmentId: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lateTopicTitleField}
                <Input
                  value={lateTopicForm.title}
                  onChange={(e) => setLateTopicForm({ ...lateTopicForm, title: e.target.value })}
                  maxLength={240}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lateTopicDescription}
                <Input
                  value={lateTopicForm.description}
                  onChange={(e) => setLateTopicForm({ ...lateTopicForm, description: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lateTopicSupervisor}
                <Input
                  value={lateTopicForm.supervisorId}
                  onChange={(e) => setLateTopicForm({ ...lateTopicForm, supervisorId: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lateTopicMaxGroups}
                <Input
                  type="number"
                  min={1}
                  max={20}
                  value={lateTopicForm.maxGroups}
                  onChange={(e) => setLateTopicForm({ ...lateTopicForm, maxGroups: e.target.value })}
                  required
                />
              </label>
            </>
          ) : null}
          {reasonAction?.kind === 'rosterOverride' ? (
            <>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.rosterOverrideAction}
                <Select
                  value={overrideForm.action}
                  onChange={(e) =>
                    setOverrideForm({ ...overrideForm, action: e.target.value as 'ADD' | 'REMOVE' })
                  }
                  options={[
                    { value: 'ADD', label: messages.thesis.admin.rosterOverrideActionAdd },
                    { value: 'REMOVE', label: messages.thesis.admin.rosterOverrideActionRemove },
                  ]}
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.rosterOverrideGroup}
                <Input
                  value={overrideForm.groupId}
                  onChange={(e) => setOverrideForm({ ...overrideForm, groupId: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.rosterOverrideStudent}
                <Input
                  value={overrideForm.studentId}
                  onChange={(e) => setOverrideForm({ ...overrideForm, studentId: e.target.value })}
                  required
                />
              </label>
              {overrideForm.action === 'REMOVE' ? (
                <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                  {messages.thesis.admin.rosterOverrideSuccessor}
                  <Input
                    value={overrideForm.successorId}
                    onChange={(e) => setOverrideForm({ ...overrideForm, successorId: e.target.value })}
                  />
                </label>
              ) : null}
              <label className="flex items-center gap-2 text-sm font-medium text-foreground">
                <input
                  type="checkbox"
                  checked={overrideForm.allowSize}
                  onChange={(e) => setOverrideForm({ ...overrideForm, allowSize: e.target.checked })}
                />
                {messages.thesis.admin.rosterOverrideSizeException}
              </label>
            </>
          ) : null}
          {reasonAction?.kind === 'topicReassign' ? (
            <>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.topicReassignGroup}
                <Input
                  value={reassignForm.groupId}
                  onChange={(e) => setReassignForm({ ...reassignForm, groupId: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.topicReassignTopic}
                <Input
                  value={reassignForm.topicId}
                  onChange={(e) => setReassignForm({ ...reassignForm, topicId: e.target.value })}
                  required
                />
              </label>
            </>
          ) : null}
          {reasonAction?.kind === 'deleteRound' ? (
            <>
              <p className="text-sm text-muted-foreground">
                {messages.thesis.admin.deleteRoundHint}
              </p>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.deleteRoundConfirmLabel}
                <Input
                  value={retractConfirmation}
                  onChange={(e) => setRetractConfirmation(e.target.value)}
                  required
                />
              </label>
            </>
          ) : null}
          {reasonAction?.kind === 'retractResults' ? (
            <>
              <p className="text-sm text-muted-foreground">
                {messages.thesis.admin.retractResultsHint}
              </p>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.retractResultsConfirmLabel}
                <Input
                  value={retractConfirmation}
                  onChange={(e) => setRetractConfirmation(e.target.value)}
                  required
                />
              </label>
            </>
          ) : null}
          {reasonAction?.kind === 'closeRound' ? (
            <>
              <p className="text-sm text-muted-foreground">
                {messages.thesis.admin.closeRoundHint}
              </p>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.closeRoundConfirmLabel}
                <Input
                  value={retractConfirmation}
                  onChange={(e) => setRetractConfirmation(e.target.value)}
                  required
                />
              </label>
            </>
          ) : null}
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.governanceReasonLabel} <span className="text-destructive">*</span>
            <Input
              value={reasonText}
              onChange={(e) => setReasonText(e.target.value)}
              maxLength={500}
              required
            />
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setReasonAction(null)}
              disabled={isSaving}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              onClick={() => void submitReasonAction()}
              disabled={isSaving || !reasonText.trim()}
            >
              {messages.common.actions.save}
            </Button>
          </div>
        </div>
      </Modal>
      <Modal
        isOpen={restoreTargetRound !== null}
        onClose={() => {
          if (!isSaving) setRestoreTargetRound(null);
        }}
        title={messages.thesis.admin.restoreRoundTitle}
      >
        <div className="space-y-4">
          {modalError ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {modalError}
            </div>
          ) : null}
          <p className="text-sm text-muted-foreground">
            {restoreTargetRound?.cancelledFromStatus
              ? messages.thesis.admin.restoreRoundWindowPassed
              : messages.thesis.admin.restoreRoundPickTargetRequired}
          </p>
          <Select
            label={messages.thesis.admin.restoreTargetLabel}
            value={restoreStatusDraft}
            onChange={(e) => setRestoreStatusDraft(e.target.value as ThesisRoundStatus)}
            options={restoreTargets.map((status) => ({
              value: status,
              label: messages.thesis.status[status] ?? status,
            }))}
          />
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setRestoreTargetRound(null)}
              disabled={isSaving}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              onClick={() => {
                if (restoreTargetRound && restoreStatusDraft) {
                  void performRestore(restoreTargetRound.id, restoreStatusDraft);
                }
              }}
              disabled={isSaving || !restoreStatusDraft}
            >
              {messages.thesis.admin.restoreRound}
            </Button>
          </div>
        </div>
      </Modal>
      {confirmationDialog}
    </AdminFrame>
  );
}
