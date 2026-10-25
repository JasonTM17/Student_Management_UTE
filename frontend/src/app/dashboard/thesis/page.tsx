'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useSearchParams } from 'next/navigation';
import {
  AlertCircle,
  Archive,
  ArrowUpRight,
  Award,
  BookOpen,
  Building2,
  CalendarDays,
  Check,
  CheckCircle2,
  ExternalLink,
  FileDown,
  FileStack,
  FileText,
  GraduationCap,
  Info,
  Lock,
  Plus,
  Search,
  Send,
  Trash2,
  UploadCloud,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { toast } from 'sonner';
import { classifyThesisScore } from '@/lib/grade-scale';
import { mergeThesisRounds, pickPreferredThesisRound, roundsForPicker } from '@/lib/thesis-round';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Combobox } from '@/components/ui/combobox';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/ui/modal';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useRovingTabs } from '@/components/ui/use-roving-tabs';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { ThesisWorkflowStepper } from '@/components/thesis/ThesisWorkflowStepper';
import { ThesisRegulationGuide } from '@/components/thesis/ThesisRegulationGuide';
import { RoundMilestoneCard } from '@/components/thesis/RoundMilestoneCard';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { formatThesisDateTime } from '@/lib/thesis-datetime';
import CouncilDefenseWorkspace from '@/features/thesis/components/CouncilDefenseWorkspace';
import GvpbReviewPanel from '@/features/thesis/components/GvpbReviewPanel';
import {
  fillCopy,
  formatReportFileSize,
  renderInlineBold,
} from '@/features/thesis/components/thesis-helpers';
import { WorkspaceMetricCard } from '@/components/dashboard/WorkspaceSurface';
import { metricToneClass } from '@/components/ui/status';
import StudentGroupCard, {
  type StudentGroupMemberRow,
} from '@/features/thesis/components/StudentGroupCard';
import SubmissionStatusTimeline from '@/features/thesis/components/SubmissionStatusTimeline';
import SupervisedGroupList from '@/features/thesis/components/SupervisedGroupList';
import ThesisRepositoryWorkspace from '@/features/thesis/components/ThesisRepositoryWorkspace';
import { departmentsApi, fetchAllPages, lecturersApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { plainTextThesisDescription } from '@/lib/thesis-text';
import type { Department, Lecturer } from '@/types/api';
import {
  getThesisTopicCapabilities,
  thesisApi,
  type ThesisLecturerWorkload,
  type ThesisCouncil,
  type ThesisCouncilScore,
  type ThesisGroup,
  type ThesisGroupMember,
  type ThesisGroupReport,
  type ThesisRepositoryReport,
  type ThesisRound,
  type ThesisRoundResult,
  type ThesisStudentResult,
  type ThesisTopic,
  type ThesisTopicSupervisor,
} from '@/lib/thesis-api';

/** Shape of the API error envelope (`{ code, message }`) used for domain conflicts. */
interface ThesisApiErrorShape {
  response?: { data?: { code?: string; message?: string } | null } | null;
}

function getThesisErrorCode(error: unknown): string {
  const data = (error as ThesisApiErrorShape | undefined)?.response?.data;
  return data?.code ?? '';
}

/** Stable classification keys; the display label comes from the i18n dictionary. */
type GradeBand =
  | 'EXCELLENT'
  | 'GOOD'
  | 'FAIR'
  | 'UPPER_AVERAGE'
  | 'AVERAGE'
  | 'BELOW_AVERAGE'
  | 'PASS'
  | 'RETAKE';

const THESIS_LECTURER_TABS = ['supervision', 'defense', 'repository'] as const;
const THESIS_SUPERVISION_TAB_ONLY = ['supervision'] as const;

// Feedback item 2: one classification source. The 10-point bands live in
// grade-scale.ts now, matching the official conversion table end to end.
const GRADE_BADGE_CLASS: Record<GradeBand, string> = {
  EXCELLENT: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
  GOOD: 'bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30',
  FAIR: 'bg-cyan-500/15 text-cyan-700 dark:text-cyan-300 border-cyan-500/30',
  UPPER_AVERAGE: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
  AVERAGE: 'bg-orange-500/15 text-orange-700 dark:text-orange-300 border-orange-500/30',
  BELOW_AVERAGE: 'bg-yellow-500/15 text-yellow-700 dark:text-yellow-300 border-yellow-500/30',
  PASS: 'bg-zinc-500/15 text-zinc-700 dark:text-zinc-300 border-zinc-500/30',
  RETAKE: 'bg-destructive/15 text-destructive border-destructive/30',
};

function getGradeClassification(score: number): {
  letter: string;
  gpa4: string;
  band: GradeBand;
  badgeClass: string;
} {
  const classified = classifyThesisScore(score);
  return { ...classified, badgeClass: GRADE_BADGE_CLASS[classified.band] };
}

export default function ThesisPage() {
  const { user, isStudent, isLecturer, isAdmin: isAdminRole, isSuperAdmin, isFacultyHead } = useAuth();
  const isAdmin = Boolean(isAdminRole || isSuperAdmin || isFacultyHead);
  const isDepartmentHead = Boolean(user?.roles?.includes('TRUONG_BO_MON'));
  const canLoadStaffRelations = Boolean(isLecturer || isDepartmentHead || isAdmin);
  const canProposeTopics = canLoadStaffRelations;
  const isTopicApprover = Boolean(isAdmin || isDepartmentHead);
  const isSupervisorOrAdmin = canLoadStaffRelations;
  const { locale, messages } = useI18n();
  // Every phase window and deadline on this workspace is a faculty clock, not
  // a viewer-local one — pin the shared dd/MM/yyyy HH:mm Asia/Bangkok display.
  const formatDateTime = formatThesisDateTime;
  const pageCopy = messages.thesisWorkflow.page;
  const searchParams = useSearchParams();
  const explicitRoundId = searchParams.get('roundId') || '';
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const topicRequestRef = useRef(0);
  const councilRequestRef = useRef(0);
  const repositoryRequestRef = useRef(0);
  const selectedRoundRef = useRef(selectedRoundId);
  selectedRoundRef.current = selectedRoundId;
  const [viewingTopic, setViewingTopic] = useState<ThesisTopic | null>(null);
  const [topicActionError, setTopicActionError] = useState('');
  const [isArchiveConfirmationOpen, setIsArchiveConfirmationOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [isActionPending, setIsActionPending] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  // Scoped load failure for the lecturer workload: without it the member
  // panel silently disappears.
  const [workloadError, setWorkloadError] = useState('');
  // Secondary panels (supervisor names, supervised report links) must surface
  // a failed fetch instead of silently rendering as empty.
  const [secondaryDataError, setSecondaryDataError] = useState(false);
  const [actionSuccess, setActionSuccess] = useState('');

  // Propose topic state
  const [isProposeModalOpen, setIsProposeModalOpen] = useState(false);
  const [proposeTitle, setProposeTitle] = useState('');
  const [proposeDescription, setProposeDescription] = useState('');
  const [proposeDepartmentId, setProposeDepartmentId] = useState('');
  const [proposeMaxGroups, setProposeMaxGroups] = useState(2);
  const [proposePublishImmediately, setProposePublishImmediately] = useState(false);
  const [proposeRoundId, setProposeRoundId] = useState('');
  const [proposeError, setProposeError] = useState('');

  // Edit topic modal state
  const [isEditModalOpen, setIsEditModalOpen] = useState(false);
  const [editingTopicId, setEditingTopicId] = useState('');
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editDepartmentId, setEditDepartmentId] = useState('');
  const [editMaxGroups, setEditMaxGroups] = useState(2);
  const [editError, setEditError] = useState('');

  // Reject group modal state
  const [isRejectModalOpen, setIsRejectModalOpen] = useState(false);
  const [isApproveExceptionModalOpen, setIsApproveExceptionModalOpen] = useState(false);
  const [approveExceptionGroupId, setApproveExceptionGroupId] = useState('');
  const [approveExceptionReason, setApproveExceptionReason] = useState('');
  const [approveExceptionError, setApproveExceptionError] = useState('');
  const [isApproveOverrideModalOpen, setIsApproveOverrideModalOpen] = useState(false);
  const [approveOverrideGroupId, setApproveOverrideGroupId] = useState('');
  const [approveOverrideReason, setApproveOverrideReason] = useState('');
  const [approveOverrideError, setApproveOverrideError] = useState('');
  const [isWithdrawModalOpen, setIsWithdrawModalOpen] = useState(false);
  const [withdrawGroupId, setWithdrawGroupId] = useState('');
  const [withdrawReason, setWithdrawReason] = useState('');
  const [withdrawError, setWithdrawError] = useState('');
  const [rejectGroupId, setRejectGroupId] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [rejectError, setRejectError] = useState('');

  // Add member modal state
  const [isAddMemberModalOpen, setIsAddMemberModalOpen] = useState(false);
  const [memberType, setMemberType] = useState<'internal' | 'external'>('internal');
  const [memberName, setMemberName] = useState('');
  const [memberContact, setMemberContact] = useState('');
  const [addMemberError, setAddMemberError] = useState('');
  const [studentQuery, setStudentQuery] = useState('');
  const [studentResults, setStudentResults] = useState<ThesisStudentResult[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [hasSearched, setHasSearched] = useState(false);

  const { confirm, confirmationDialog } = useConfirmationDialog();

  // Server-owned membership across every round: the round holding the
  // student's group anchors the workspace, and the persistent banner below can
  // always point back to it even after the reader switches rounds (F3).
  const [myGroups, setMyGroups] = useState<ThesisGroup[] | null>(null);

  // Group report state (approved groups only)
  const [groupReport, setGroupReport] = useState<ThesisGroupReport | null>(null);
  const [groupReportLoadError, setGroupReportLoadError] = useState<string | null>(null);
  const [groupReportReloadToken, setGroupReportReloadToken] = useState(0);
  const [isReportLoading, setIsReportLoading] = useState(false);
  const [isReportFormOpen, setIsReportFormOpen] = useState(false);
  const [reportTitle, setReportTitle] = useState('');
  const [reportUrl, setReportUrl] = useState('');
  const [reportNote, setReportNote] = useState('');
  const [reportError, setReportError] = useState('');
  // Feedback item 7: the report may be an attached Word/PDF document.
  const [reportFile, setReportFile] = useState<File | null>(null);
  // Live upload progress and the handle to abandon an in-flight document.
  const [reportUploadPercent, setReportUploadPercent] = useState<number | null>(null);
  const reportUploadAbortRef = useRef<AbortController | null>(null);

  // Round results state (shown once the round reaches RESULTS_PUBLISHED)
  const [roundResults, setRoundResults] = useState<ThesisRoundResult[]>([]);
  const [resultsState, setResultsState] = useState<
    'hidden' | 'loading' | 'ready' | 'notPublished'
  >('hidden');

  // Supervisor directory used by the propose-topic modal. A failed or empty
  // fetch stays failed or empty — the directory is never padded with invented
  // faculty, and `lecturersError` keeps an outage distinguishable from a
  // genuinely unpopulated directory.
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [lecturersError, setLecturersError] = useState('');

  // The propose/edit department pickers mirror the backend scope ladder: a
  // global admin targets any active department, a faculty head any active
  // department inside their own faculty, and a lecturer only their personal
  // department. Falling back to the full list keeps the picker usable while
  // the lecturer directory is still loading — the API still enforces scope.
  const ownDepartmentId = useMemo(
    () =>
      user?.lecturerId
        ? lecturers.find((lecturer) => lecturer.id === user.lecturerId)?.departmentId ?? ''
        : '',
    [lecturers, user?.lecturerId],
  );
  const scopedDepartments = useMemo(() => {
    const activeDepartments = departments.filter((department) => department.isActive !== false);
    if (isAdminRole || isSuperAdmin) {
      return activeDepartments;
    }
    if (isFacultyHead) {
      const ownFacultyId = departments.find((department) => department.id === ownDepartmentId)?.facultyId;
      const facultyScoped = activeDepartments.filter(
        (department) => ownFacultyId == null || department.facultyId === ownFacultyId,
      );
      return facultyScoped.length > 0 ? facultyScoped : activeDepartments;
    }
    if (ownDepartmentId) {
      const own = departments.filter((department) => department.id === ownDepartmentId);
      if (own.length > 0) return own;
    }
    return activeDepartments;
  }, [departments, isAdminRole, isSuperAdmin, isFacultyHead, ownDepartmentId]);

  // Default the proposal department to the signed-in lecturer's own
  // department; only fall back to the first in-scope option when that
  // profile link is missing or out of scope. Re-running on modal open also
  // re-seeds the field after a submit reset to the unscoped list head.
  useEffect(() => {
    if (scopedDepartments.length === 0) return;
    setProposeDepartmentId((current) => {
      if (ownDepartmentId && scopedDepartments.some((department) => department.id === ownDepartmentId)) {
        return ownDepartmentId;
      }
      if (current && scopedDepartments.some((department) => department.id === current)) return current;
      return scopedDepartments[0].id;
    });
  }, [scopedDepartments, ownDepartmentId, isProposeModalOpen]);
  const [proposeSupervisorId, setProposeSupervisorId] = useState('');
  const [proposeSecondSupervisorId, setProposeSecondSupervisorId] = useState('');

  // Defense council grading state (R6-R8)
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [lecturerWorkload, setLecturerWorkload] = useState<ThesisLecturerWorkload | null>(null);
  const [councilScores, setCouncilScores] = useState<Record<string, ThesisCouncilScore[]>>({});
  // Score-list load failures stay distinguishable from a genuinely empty list
  // ("0 of N graded" would otherwise mask an outage).
  const [councilScoreErrors, setCouncilScoreErrors] = useState<Record<string, boolean>>({});
  // A failed council listing must never read as "no councils yet": this flag
  // keeps the outage distinguishable and gives the defense tab a retry.
  const [councilLoadError, setCouncilLoadError] = useState('');
  const [draftScores, setDraftScores] = useState<Record<string, string>>({});
  const [draftComments, setDraftComments] = useState<Record<string, string>>({});
  const [topicSupervisors, setTopicSupervisors] = useState<Record<string, string[]>>({});
  // GVPB counter-review state keyed by topic id: the reviewer's own row.
  const [gvpbScores, setGvpbScores] = useState<Record<string, ThesisCouncilScore | null>>({});
  const [gvpbDraftScores, setGvpbDraftScores] = useState<Record<string, string>>({});
  const [gvpbDraftComments, setGvpbDraftComments] = useState<Record<string, string>>({});
  const [supervisedReports, setSupervisedReports] = useState<Record<string, ThesisGroupReport | null>>({});
  const [topicReports, setTopicReports] = useState<Record<string, ThesisGroupReport | null>>({});
  const [topicReportErrors, setTopicReportErrors] = useState<Record<string, boolean>>({});

  // Lecturer navigation tab (supervision vs council defense vs repository)
  const [lecturerTab, setLecturerTab] = useState<'supervision' | 'defense' | 'repository'>('supervision');
  const chooseLecturerTab = useCallback(
    (tab: 'supervision' | 'defense' | 'repository') => {
      userChoseTabRef.current = true;
      setLecturerTab(tab);
    },
    [],
  );

  // Thesis Repository State (Faculty archive of submitted theses)
  const [repositoryReports, setRepositoryReports] = useState<ThesisRepositoryReport[]>([]);
  const [isRepositoryLoading, setIsRepositoryLoading] = useState(false);
  // A failed archive load must never read as "no reports yet": this flag keeps
  // an outage distinguishable from a genuinely empty repository.
  const [repositoryError, setRepositoryError] = useState('');
  const [repositorySearch, setRepositorySearch] = useState('');

  // Supervisors for current student group's topic
  const [currentTopicSupervisors, setCurrentTopicSupervisors] = useState<ThesisTopicSupervisor[]>([]);

  // Council matching uses the Lecturer-profile id. Falling back to the auth
  // User id compared unrelated id spaces, so council actions were silently
  // disabled for lecturers without a profile claim — surface that state
  // instead of hiding it.
  const myLecturerId = user?.lecturerId || '';
  const visibleCouncils = useMemo(() => {
    if (!isSupervisorOrAdmin) return [];
    if (isAdmin) return councils;
    return councils.filter((c) =>
      c.members?.some((m) => m.lecturerId === myLecturerId)
    );
  }, [councils, isAdmin, isSupervisorOrAdmin, myLecturerId]);

  // `isCurrent` carries the caller's request identity: a round switch, an
  // unmount, or a newer retry invalidates the load so a late resolution can
  // neither issue more requests nor write stale round data into the maps.
  const loadCouncilDetails = useCallback(async (
    councilsList: ThesisCouncil[],
    isCurrent: () => boolean = () => true,
  ) => {
    for (const c of councilsList) {
      if (!isCurrent()) return;
      const isMemberOrAdmin = isAdmin || c.members?.some((m) => m.lecturerId === myLecturerId);
      if (!isMemberOrAdmin) continue;
      const tids = (c as unknown as { topicIds?: string[] }).topicIds || [];
      for (const tid of tids) {
        if (!isCurrent()) return;
        const key = `${c.id}:${tid}`;
        try {
          const [scoresResult, sups, reportResult] = await Promise.all([
            isMemberOrAdmin
              ? thesisApi.listScores(c.id, tid).then(
                  (scores) => ({ scores, failed: false }),
                  () => ({ scores: [] as ThesisCouncilScore[], failed: true }),
                )
              : Promise.resolve({ scores: [] as ThesisCouncilScore[], failed: false }),
            thesisApi.listSupervisors(tid).catch(() => []),
            thesisApi.getTopicReport(tid).then(
              (report) => ({ report, failed: false }),
              () => ({ report: null, failed: true }),
            ),
          ]);
          if (!isCurrent()) return;
          setCouncilScores((prev) => ({ ...prev, [key]: scoresResult.scores }));
          setCouncilScoreErrors((prev) => ({ ...prev, [key]: scoresResult.failed }));
          setTopicSupervisors((prev) => ({ ...prev, [tid]: sups.map((s) => s.lecturerId) }));
          setTopicReports((prev) => ({ ...prev, [tid]: reportResult.report }));
          setTopicReportErrors((prev) => ({ ...prev, [tid]: reportResult.failed }));
          const myScore = scoresResult.scores.find((s) => s.lecturerId === myLecturerId);
          if (myScore != null) {
            setDraftScores((prev) => ({ ...prev, [key]: String(myScore.score) }));
            if (myScore.comment) {
              setDraftComments((prev) => ({ ...prev, [key]: myScore.comment ?? '' }));
            }
          }
        } catch {
          // ignore
        }
      }
    }
  }, [isAdmin, myLecturerId]);

  const handleSubmitScore = async (councilId: string, topicId: string) => {
    const key = `${councilId}:${topicId}`;
    const scoreVal = parseFloat(draftScores[key] || '');
    if (isNaN(scoreVal) || scoreVal < 0 || scoreVal > 10) {
      setActionError(pageCopy.defenceScoreRange);
      return;
    }
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      const comment = (draftComments[key] || '').trim();
      await thesisApi.submitScore(
        councilId,
        topicId,
        scoreVal,
        'DEFENSE',
        comment || undefined,
      );
      setActionSuccess(messages.thesis.grading.scoreSaved);
      toast.success(messages.thesis.grading.scoreSaved);
      // A refresh outage must not mask the successful submit.
      const scores = await thesisApi.listScores(councilId, topicId).catch(() => null);
      if (scores) {
        setCouncilScores((prev) => ({ ...prev, [key]: scores }));
        setCouncilScoreErrors((prev) => ({ ...prev, [key]: false }));
      } else {
        setCouncilScoreErrors((prev) => ({ ...prev, [key]: true }));
      }
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      if (code === 'SUPERVISOR_CANNOT_GRADE') {
        setActionError(messages.thesis.grading.supervisorCannotGrade);
        toast.error(messages.thesis.grading.supervisorCannotGrade);
      } else {
        const msg = thesisActionError(err);
        setActionError(msg);
        toast.error(msg);
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const handleFinalizeScore = async (councilId: string, topicId: string) => {
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      const res = await thesisApi.finalizeScores(councilId, topicId);
      const msg = fillCopy(pageCopy.scoreFinalised, { score: res.finalScore ?? '' });
      setActionSuccess(msg);
      toast.success(msg);
      const key = `${councilId}:${topicId}`;
      const scores = await thesisApi.listScores(councilId, topicId).catch(() => null);
      if (scores) {
        setCouncilScores((prev) => ({ ...prev, [key]: scores }));
        setCouncilScoreErrors((prev) => ({ ...prev, [key]: false }));
      } else {
        setCouncilScoreErrors((prev) => ({ ...prev, [key]: true }));
      }
      await refreshTopics(selectedRoundId);
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      if (code === 'SCORES_INCOMPLETE') {
        const msg = pageCopy.councilScoresPending;
        setActionError(msg);
        toast.error(msg);
      } else if (code === 'SCORE_ALREADY_FINALIZED') {
        const msg = pageCopy.topicAlreadyFinalised;
        setActionError(msg);
        toast.info(msg);
      } else {
        const msg = thesisActionError(err);
        setActionError(msg);
        toast.error(msg);
      }
    } finally {
      setIsActionPending(false);
    }
  };

  // ---- GVPB counter-review (the assigned reviewer submits score + comment) ----

  const myReviewTopics = useMemo(
    () =>
      canLoadStaffRelations && myLecturerId
        ? topics.filter((t) => t.gvpbLecturerId === myLecturerId)
        : [],
    [canLoadStaffRelations, myLecturerId, topics],
  );

  // Topics whose reviewer score was already requested. topics refreshes mint a
  // new array on every mutation, so without this each refresh re-fired the
  // whole parallel fan-out — duplicate reads on the shared per-user thesis
  // rate limit that lose the reviewer's saved scores to swallowed 429s.
  const requestedReviewerScoresRef = useRef(new Set<string>());

  // Load the reviewer's own stored score for each assigned topic.
  const loadMyReviewerScores = useCallback(async (reviewTopics: ThesisTopic[]) => {
    const requested = requestedReviewerScoresRef.current;
    await Promise.all(
      reviewTopics
        .filter((t) => !requested.has(t.id))
        .map(async (t) => {
          requested.add(t.id);
          try {
            const row = await thesisApi.reviewerScore(t.id);
            setGvpbScores((prev) => ({ ...prev, [t.id]: row }));
            if (row) {
              setGvpbDraftScores((prev) => ({ ...prev, [t.id]: String(row.score) }));
              if (row.comment) {
                setGvpbDraftComments((prev) => ({ ...prev, [t.id]: row.comment ?? '' }));
              }
            }
          } catch {
            // A read failure leaves the form usable; the submit stays the
            // authoritative path. Drop the mark so the next refresh retries.
            requested.delete(t.id);
          }
        }),
    );
  }, []);

  useEffect(() => {
    if (myReviewTopics.length === 0) return;
    void loadMyReviewerScores(myReviewTopics);
  }, [myReviewTopics, loadMyReviewerScores]);

  const handleSubmitReviewerScore = async (topicId: string) => {
    const scoreVal = parseFloat(gvpbDraftScores[topicId] || '');
    if (isNaN(scoreVal) || scoreVal < 0 || scoreVal > 10) {
      setActionError(pageCopy.defenceScoreRange);
      return;
    }
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      const comment = (gvpbDraftComments[topicId] || '').trim();
      const saved = await thesisApi.submitReviewerScore(
        topicId,
        scoreVal,
        comment || undefined,
      );
      setGvpbScores((prev) => ({ ...prev, [topicId]: saved }));
      setActionSuccess(messages.thesis.grading.scoreSaved);
      toast.success(messages.thesis.grading.scoreSaved);
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      const msg =
        code === 'GVPB_DEADLINE_PASSED'
          ? messages.thesis.review.deadlinePassed
          : code === 'SCORE_ALREADY_FINALIZED'
            ? pageCopy.topicAlreadyFinalised
            : code === 'SUPERVISOR_CANNOT_GRADE'
              ? messages.thesis.grading.supervisorCannotGrade
              : thesisActionError(err);
      setActionError(msg);
      toast.error(msg);
    } finally {
      setIsActionPending(false);
    }
  };

  // Rounds loader is a callback so the error-state retry can re-fetch in
  // place instead of reloading the whole page (every other page re-fetches).
  const loadRounds = useCallback(async (signal: { cancelled: boolean }) => {
    setIsLoading(true);
    setError('');
    try {
      // Archived rounds stay resolvable so a closed membership or supervised
      // round still serves its read-only results; the picker filters them out.
      const [liveRounds, closedRounds] = await Promise.all([
        thesisApi.listRounds(),
        thesisApi.listRounds('CLOSED').catch(() => [] as ThesisRound[]),
      ]);
      const nextRounds = mergeThesisRounds(liveRounds, closedRounds);
      if (signal.cancelled) return;
      const sortedRounds = [...nextRounds].sort((a, b) => {
        const isOpenA = a.status === 'REGISTRATION_OPEN' ? 0 : 1;
        const isOpenB = b.status === 'REGISTRATION_OPEN' ? 0 : 1;
        if (isOpenA !== isOpenB) return isOpenA - isOpenB;
        const isKltnA = a.thesisType === 'KLTN' ? 0 : 1;
        const isKltnB = b.thesisType === 'KLTN' ? 0 : 1;
        if (isKltnA !== isKltnB) return isKltnA - isKltnB;
        return (b.registrationStart || '').localeCompare(a.registrationStart || '');
      });
      setRounds(sortedRounds);
      setSelectedRoundId((current) => {
        if (explicitRoundId && sortedRounds.some((r) => r.id === explicitRoundId)) {
          return explicitRoundId;
        }
        if (current) return current;
        const preferred =
          sortedRounds.find((r) => r.status === 'REGISTRATION_OPEN' && r.thesisType === 'KLTN') ||
          sortedRounds.find((r) => r.status === 'REGISTRATION_OPEN') ||
          sortedRounds.find((r) => r.status !== 'CLOSED');
        return preferred?.id || '';
      });
    } catch {
      if (!signal.cancelled) setError(messages.thesis.loadFailed);
    } finally {
      if (!signal.cancelled) setIsLoading(false);
    }
  }, [explicitRoundId, messages.thesis.loadFailed]);

  useEffect(() => {
    const signal = { cancelled: false };
    void loadRounds(signal);
    return () => {
      signal.cancelled = true;
    };
  }, [loadRounds]);

  // The round default above is a student-facing preference. A lecturer whose
  // supervision lives in another round would otherwise land on an empty
  // workspace, so once the workload is known, follow the round that actually
  // holds supervised groups.
  useEffect(() => {
    // The data-loading effect can run before auth resolves, so the workload is
    // fetched here where `isSupervisorOrAdmin` is reliable.
    // The workload endpoint is intentionally self-scoped to LECTURER. Admin
    // and faculty-head workspaces use the global group/council/repository
    // projections below and must not turn that expected 403 into a page-level
    // warning when opening the archive.
    if (!isLecturer || !selectedRoundId) return;
    let cancelled = false;
    thesisApi
      .myWorkload()
      .then((workload) => {
        if (!cancelled) {
          setLecturerWorkload(workload);
          setWorkloadError('');
        }
      })
      .catch(() => {
        // Losing the workload silently removes the member panel, so the
        // lecturer is told the data failed instead of seeing an empty page.
        if (!cancelled) setWorkloadError(messages.thesis.loadFailed);
      });
    return () => {
      cancelled = true;
    };
  }, [isLecturer, messages.thesis.loadFailed, selectedRoundId]);

  const hasAutoSelectedRoundRef = useRef(false);
  const hasAutoSelectedStudentRoundRef = useRef(false);

  // Students have no workload feed, so without this lookup the workspace opens
  // on the newest open round and reports "no group in this round" even though
  // their group sits in an older closed/published one. The membership list is
  // fetched once when the student role resolves; a failure leaves the default
  // selection in place and the round selector usable.
  useEffect(() => {
    if (!isStudent) return;
    let cancelled = false;
    thesisApi
      .myGroups()
      .then((mine) => {
        if (!cancelled) setMyGroups(mine);
      })
      .catch(() => {
        // Membership lookup failing must not break the workspace.
      });
    return () => {
      cancelled = true;
    };
  }, [isStudent]);

  // The round that actually holds the student's group wins over the "newest
  // open round" default; both the one-shot retarget below and the persistent
  // banner read this same server-owned answer.
  const membershipRoundId = useMemo(
    () => pickPreferredThesisRound(rounds, myGroups),
    [rounds, myGroups],
  );

  // One-shot retarget: an explicit ?roundId link or a manual choice still
  // wins; the banner keeps offering the way back regardless.
  useEffect(() => {
    if (!isStudent || explicitRoundId || !membershipRoundId || hasAutoSelectedStudentRoundRef.current) {
      return;
    }
    hasAutoSelectedStudentRoundRef.current = true;
    setSelectedRoundId((current) => (current === membershipRoundId ? current : membershipRoundId));
  }, [explicitRoundId, isStudent, membershipRoundId]);

  useEffect(() => {
    if (!isSupervisorOrAdmin || !lecturerWorkload || explicitRoundId || hasAutoSelectedRoundRef.current) return;
    // A cancelled or archived round must not hijack focus when active rounds exist.
    const withGroups = lecturerWorkload.topics.filter(
      (topic) => topic.groupCount > 0 && topic.roundStatus !== 'CANCELLED',
    );
    if (withGroups.length === 0) return;
    hasAutoSelectedRoundRef.current = true;
    if (withGroups.some((topic) => topic.roundId === selectedRoundId)) return;
    // Prefer live/active rounds (not CLOSED) before considering archived ones
    const liveWithGroups = withGroups.filter((topic) => topic.roundStatus !== 'CLOSED');
    const pool = liveWithGroups.length > 0 ? liveWithGroups : withGroups;
    const target = [...pool].sort((a, b) => b.groupCount - a.groupCount)[0].roundId;
    if (target && rounds.some((round) => round.id === target)) {
      setSelectedRoundId(target);
    }
  }, [explicitRoundId, isSupervisorOrAdmin, lecturerWorkload, rounds, selectedRoundId]);

  useEffect(() => {
    let cancelled = false;
    const loadDepartments = async () => {
      try {
        const response = await departmentsApi.getAll({ limit: 100 });
        const list = Array.isArray(response?.data)
          ? response.data
          : Array.isArray(response)
            ? response
            : [];
        if (!cancelled && list.length > 0) {
          setDepartments(list as Department[]);
          setProposeDepartmentId((current) => current || list[0].id);
        }
      } catch {
        // preserve fallback
      }
    };

    if (isSupervisorOrAdmin) {
      void loadDepartments();
    }
    return () => {
      cancelled = true;
    };
  }, [isSupervisorOrAdmin]);

  const loadLecturers = useCallback(async () => {
    setLecturersError('');
    try {
      // Fetch every page so a supervisor past row 100 can still be proposed.
      setLecturers(await fetchAllPages<Lecturer>((p, l) => lecturersApi.getAll({ page: p, limit: l })));
    } catch {
      // Never substitute a fabricated directory: the modal says the directory
      // failed to load so the reader is not shown invented supervisors.
      setLecturers([]);
      setLecturersError(messages.thesis.loadFailed);
    }
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (canLoadStaffRelations || (canProposeTopics && isProposeModalOpen)) {
      void loadLecturers();
    }
  }, [canLoadStaffRelations, canProposeTopics, isProposeModalOpen, loadLecturers]);

  const refreshTopics = useCallback(
    async (roundId: string) => {
      if (!roundId || roundId !== selectedRoundRef.current) return;
      const requestId = ++topicRequestRef.current;
      const isCurrent = () => requestId === topicRequestRef.current && roundId === selectedRoundRef.current;
      try {
        let next: ThesisTopic[];
        if (isSupervisorOrAdmin) {
          const [published, drafts] = await Promise.all([
            thesisApi.listTopics(roundId, 'PUBLISHED'),
            thesisApi.listTopics(roundId, 'DRAFT'),
          ]);
          const map = new Map<string, ThesisTopic>();
          for (const topic of [...published, ...drafts]) map.set(topic.id, topic);
          next = Array.from(map.values());
        } else {
          next = await thesisApi.listTopics(roundId, 'PUBLISHED');
        }
        if (isCurrent()) setTopics(next);
      } catch (caught) {
        if (isCurrent()) throw caught;
      }
    },
    [isSupervisorOrAdmin],
  );

  useEffect(() => {
    setTopics([]);
    setGroups([]);
    // Round-scoped UI state must not leak across rounds: the topic detail
    // modal and action banners belonged to the previous round's objects.
    setViewingTopic(null);
    setActionError('');
    setActionSuccess('');
    // Council defense state is equally round-scoped: score lists, score
    // drafts, supervisor and report maps from the previous round must not
    // render against the new selection (or linger when it is cleared).
    setCouncils([]);
    setCouncilScores({});
    setCouncilScoreErrors({});
    setDraftScores({});
    setDraftComments({});
    setTopicSupervisors({});
    setTopicReports({});
    setTopicReportErrors({});
    setCouncilLoadError('');
    if (!selectedRoundId) {
      return;
    }

    let cancelled = false;
    const councilRequestId = ++councilRequestRef.current;
    const repositoryRequestId = ++repositoryRequestRef.current;
    const roundId = selectedRoundId;
    const isCouncilCurrent = () =>
      !cancelled
      && councilRequestId === councilRequestRef.current
      && roundId === selectedRoundRef.current;
    const isRepositoryCurrent = () =>
      !cancelled
      && repositoryRequestId === repositoryRequestRef.current
      && roundId === selectedRoundRef.current;
    const loadWorkspace = async () => {
      setError('');
      try {
        const promises: Promise<unknown>[] = [refreshTopics(selectedRoundId)];
        if (isStudent || canLoadStaffRelations) {
          promises.push(thesisApi.listGroups(selectedRoundId).then((next) => {
            if (!cancelled) setGroups(next);
          }));
        } else {
          setGroups([]);
        }
        if (canLoadStaffRelations) {
          setIsRepositoryLoading(true);
          promises.push(
            thesisApi.listCouncils(selectedRoundId).then((cList) => {
              if (isCouncilCurrent()) {
                setCouncils(cList);
                setCouncilLoadError('');
                void loadCouncilDetails(cList, isCouncilCurrent);
              }
            }).catch(() => {
              if (isCouncilCurrent()) {
                // The defense tab reads an empty council list as "no councils
                // yet"; keep the failure distinct and retryable instead.
                setCouncils([]);
                setCouncilLoadError(messages.thesis.loadFailed);
              }
            }),
            thesisApi.listRoundRepository(selectedRoundId).then((rList) => {
              if (isRepositoryCurrent()) {
                setRepositoryReports(rList);
                setRepositoryError('');
                setIsRepositoryLoading(false);
              }
            }).catch(() => {
              if (isRepositoryCurrent()) {
                setRepositoryReports([]);
                setRepositoryError(messages.thesis.loadFailed);
                setIsRepositoryLoading(false);
              }
            }),
          );
          // Which topics this lecturer supervises drives the member-management
          // affordance; a dedicated effect below fetches it once the role is
          // actually known, because this data effect can run before auth does.
        }
        await Promise.all(promises);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };

    void loadWorkspace();
    return () => {
      cancelled = true;
      topicRequestRef.current += 1;
      councilRequestRef.current += 1;
    };
  }, [canLoadStaffRelations, isStudent, loadCouncilDetails, messages.thesis.loadFailed, refreshTopics, selectedRoundId]);

  // Retry path for a failed council listing: the defense tab gets the real
  // failure with a way back, instead of a council workspace that silently
  // reads as empty. The detail loader shares the same request identity, so a
  // round switch or a newer retry invalidates whatever it resolves late.
  const reloadCouncils = useCallback(async () => {
    if (!selectedRoundId || !canLoadStaffRelations) return;
    const requestId = ++councilRequestRef.current;
    const roundId = selectedRoundId;
    const isCurrent = () =>
      requestId === councilRequestRef.current && roundId === selectedRoundRef.current;
    setCouncilLoadError('');
    try {
      const cList = await thesisApi.listCouncils(roundId);
      if (!isCurrent()) return;
      setCouncils(cList);
      await loadCouncilDetails(cList, isCurrent);
    } catch {
      if (isCurrent()) {
        setCouncils([]);
        setCouncilLoadError(messages.thesis.loadFailed);
      }
    }
  }, [canLoadStaffRelations, loadCouncilDetails, messages.thesis.loadFailed, selectedRoundId]);

  // Retry path for a failed repository load: the reader gets the real failure
  // with a way back, instead of an archive that silently reads as empty.
  // The retry can resolve after the reader switched rounds, so — like the
  // council retry above — every state write is gated on the round still being
  // selected; a stale answer must not overwrite the new round's archive.
  const reloadRepository = useCallback(async () => {
    if (!selectedRoundId || !canLoadStaffRelations) return;
    const requestId = ++repositoryRequestRef.current;
    const roundId = selectedRoundId;
    const isCurrent = () =>
      requestId === repositoryRequestRef.current && roundId === selectedRoundRef.current;
    setIsRepositoryLoading(true);
    setRepositoryError('');
    try {
      const reports = await thesisApi.listRoundRepository(roundId);
      if (!isCurrent()) return;
      setRepositoryReports(reports);
    } catch {
      if (!isCurrent()) return;
      setRepositoryReports([]);
      setRepositoryError(messages.thesis.loadFailed);
    } finally {
      if (isCurrent()) setIsRepositoryLoading(false);
    }
  }, [canLoadStaffRelations, messages.thesis.loadFailed, selectedRoundId]);

  const selectedRound = rounds.find((round) => round.id === selectedRoundId);
  const roundGroups = useMemo(() => {
    const map = new Map<string, { value: string; label: string }[]>();
    for (const r of roundsForPicker(rounds, selectedRoundId)) {
      const match = r.name.match(/Ni\u00ean kh\u00f3a\s+\d{4}\s*[-–]\s*\d{4}/i);
      const cohort = match ? match[0] : (r.thesisType === 'KLTN' ? 'Khóa luận Tốt nghiệp' : 'Học phần Tốt nghiệp / Chuyên ngành');
      if (!map.has(cohort)) map.set(cohort, []);
      map.get(cohort)!.push({ value: r.id, label: r.name });
    }
    return Array.from(map.entries()).map(([label, opts]) => ({ label, options: opts }));
  }, [rounds, selectedRoundId]);
  const studentId = user?.studentId ?? '';
  const ownGroups = groups.filter(
    (group) =>
      group.leaderStudentId === studentId ||
      group.memberStudentIds.includes(studentId) ||
      Boolean(group.members?.some((m) => m.studentId === studentId)),
  );
  // Prefer an active group over a newer cancelled membership in the same round.
  const currentGroup = ownGroups.find((group) => group.status !== 'CANCELLED') ?? ownGroups[0];
  const isGroupLeader = Boolean(
    currentGroup && studentId && currentGroup.leaderStudentId === studentId,
  );

  const groupMemberList: ThesisGroupMember[] = useMemo(() => {
    if (!currentGroup) return [];
    if (currentGroup.members && currentGroup.members.length > 0) {
      return currentGroup.members;
    }
    return currentGroup.memberStudentIds.map((id) => ({
      studentId: id,
      isLeader: id === currentGroup.leaderStudentId,
      isExternal: false,
    }));
  }, [currentGroup]);

  // The backend also rejects group/topic actions outside the registration
  // window dates, so the CTA must not invite a doomed request after the end.
  const isRegistrationWindowOpen = useMemo(() => {
    if (selectedRound?.status !== 'REGISTRATION_OPEN') return false;
    const now = Date.now();
    const start = Date.parse(selectedRound.registrationStart);
    const end = Date.parse(selectedRound.registrationEnd);
    return (Number.isNaN(start) || now >= start) && (Number.isNaN(end) || now <= end);
  }, [selectedRound]);

  const canManageMembers = useMemo(() => {
    if (!currentGroup || !user?.studentId) return false;
    const isLeader = currentGroup.leaderStudentId === user.studentId;
    const isNotCancelled = currentGroup.status !== 'CANCELLED';
    const isNotApproved = currentGroup.approvalStatus !== 'APPROVED';
    return isLeader && isRegistrationWindowOpen && isNotCancelled && isNotApproved;
  }, [currentGroup, user, isRegistrationWindowOpen]);

  const reportGroupId = currentGroup?.id;
  const reportApprovalStatus = currentGroup?.approvalStatus;

  // The report row belongs to the group, not to the approval state: load it
  // whenever a group exists (404 → null stays "not submitted yet"), so a
  // reopened/pending group still shows what it already sent (F3/F6).
  useEffect(() => {
    if (!reportGroupId) {
      setGroupReport(null);
      setGroupReportLoadError(null);
      setIsReportFormOpen(false);
      return;
    }
    let cancelled = false;
    setGroupReport(null);
    setGroupReportLoadError(null);
    setIsReportFormOpen(false);
    setIsReportLoading(true);
    const loadReport = async () => {
      try {
        const report = await thesisApi.getReport(reportGroupId);
        if (!cancelled) {
          setGroupReport(report);
          setGroupReportLoadError(null);
        }
      } catch (caught) {
        if (cancelled) return;
        setGroupReport(null);
        // 404 REPORT_NOT_FOUND is expected before submission; anything else is
        // a real fetch failure and must not render as "no report submitted".
        setGroupReportLoadError(
          getThesisErrorCode(caught) === 'REPORT_NOT_FOUND'
            ? null
            : messages.thesis.report.loadFailed,
        );
      } finally {
        if (!cancelled) setIsReportLoading(false);
      }
    };
    void loadReport();
    return () => {
      cancelled = true;
    };
  }, [reportGroupId, reportApprovalStatus, groupReportReloadToken, messages.thesis.report.loadFailed]);

  useEffect(() => {
    // CLOSED still serves the published result set — closing archives the
    // round, it does not unpublish.
    const resultsPublished =
      selectedRound?.status === 'RESULTS_PUBLISHED' || selectedRound?.status === 'CLOSED';
    if (!isStudent || !selectedRoundId || !resultsPublished) {
      setRoundResults([]);
      setResultsState('hidden');
      return;
    }
    let cancelled = false;
    setResultsState('loading');
    const loadResults = async () => {
      try {
        const data = await thesisApi.myResults(selectedRoundId);
        if (!cancelled) {
          setRoundResults(data);
          setResultsState('ready');
        }
      } catch (caught) {
        if (!cancelled) {
          setRoundResults([]);
          setResultsState(
            getThesisErrorCode(caught) === 'RESULTS_NOT_PUBLISHED'
              ? 'notPublished'
              : 'hidden',
          );
        }
      }
    };
    void loadResults();
    return () => {
      cancelled = true;
    };
  }, [isStudent, selectedRound?.status, selectedRoundId]);

  const myTopics = useMemo(
    () =>
      isSupervisorOrAdmin
        ? topics.filter(
            (topic) =>
              isAdmin ||
              topic.createdBy === user?.id ||
              (user?.lecturerId && topic.createdBy === user.lecturerId),
          )
        : [],
    [isAdmin, isSupervisorOrAdmin, topics, user?.id, user?.lecturerId],
  );
  const myTopicIds = useMemo(() => new Set(myTopics.map((topic) => topic.id)), [myTopics]);
  const managedTopics = isTopicApprover ? topics : myTopics;
  const topicCapabilities = (topic: ThesisTopic) => getThesisTopicCapabilities(topic, {
    roles: user?.roles ?? [],
    userId: user?.id,
    lecturerId: user?.lecturerId,
    roundStatus: rounds.find((round) => round.id === topic.roundId)?.status,
    // Every status in this authenticated list is scoped by the server for a
    // department head without global governance, including composite roles.
    inDepartmentCatalog: topics.some((listed) =>
      listed.id === topic.id && listed.departmentId === topic.departmentId,
    ),
    hasHistory: topic.finalScore != null || groups.some((group) => group.topicId === topic.id) ||
      councils.some((council) => council.topicIds?.includes(topic.id)),
  });

  // "Supervised" is the R3 supervisor relationship (thesis_topic_supervisor),
  // served by /thesis/me/workload. Groups on topics the lecturer merely
  // proposed are a different set and must not be conflated with it.
  const supervisedTopicIds = useMemo(
    () => new Set((lecturerWorkload?.topics ?? []).map((topic) => topic.topicId)),
    [lecturerWorkload],
  );

  const supervisedGroups = useMemo(
    () =>
      isSupervisorOrAdmin
        ? groups.filter(
            (group) =>
              group.topicId &&
              (isAdmin || myTopicIds.has(group.topicId) || supervisedTopicIds.has(group.topicId)),
          )
        : [],
    [groups, isAdmin, isSupervisorOrAdmin, myTopicIds, supervisedTopicIds],
  );


  // Groups whose report was already requested. supervisedGroups is recomputed
  // as topics and workload load in, and a "not submitted" 404 stores nothing,
  // so without this every recompute re-fetched each unsubmitted report.
  const requestedReportGroupsRef = useRef(new Set<string>());

  useEffect(() => {
    if (!isSupervisorOrAdmin || supervisedGroups.length === 0) return;
    const requested = requestedReportGroupsRef.current;
    for (const g of supervisedGroups) {
      if (g.approvalStatus === 'APPROVED' && !requested.has(g.id)) {
        requested.add(g.id);
        void thesisApi.getReport(g.id)
          .then((rep) => setSupervisedReports((prev) => ({ ...prev, [g.id]: rep })))
          .catch((err: unknown) => {
            // 404 REPORT_NOT_FOUND means the group has not submitted yet —
            // an expected state, not a data error.
            const status = (err as { response?: { status?: number } })?.response?.status;
            if (status !== 404 && status !== 204) {
              // A real failure may be retried on the next recompute.
              requested.delete(g.id);
              setSecondaryDataError(true);
            }
          });
      }
    }
  }, [isSupervisorOrAdmin, supervisedGroups]);

  const filteredRepositoryReports = useMemo(() => {
    const raw = repositorySearch.trim();
    if (!raw) return repositoryReports;
    const fold = (value: string) =>
      value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/đ/g, 'd')
        .replace(/Đ/g, 'd')
        .toLowerCase();
    const foldedQuery = fold(raw);
    const queryTokens = foldedQuery.split(/\s+/).filter(Boolean);
    return repositoryReports.filter((report) => {
      const haystack = fold(
        [
          report.topicTitle || report.title || '',
          report.topicDescription || '',
          report.note || '',
          report.fileName || '',
          report.departmentName || '',
          report.submittedByDisplayName || '',
          report.submittedByStudentNumber || '',
          ...report.supervisors.map((s) => s.displayName || ''),
          ...report.members.map((m) => `${m.displayName || ''} ${m.studentNumber || ''}`),
        ].join(' '),
      );
      return queryTokens.every((token) => haystack.includes(token));
    });
  }, [repositoryReports, repositorySearch]);

  const handleDownloadRepositoryReport = async (report: ThesisRepositoryReport) => {
    try {
      await thesisApi.downloadRepositoryReportFile(report.reportId, report);
      toast.success(locale === 'vi' ? 'Đã tải tài liệu báo cáo' : 'Downloaded report artifact');
    } catch (caught) {
      toast.error(
        getThesisErrorCode(caught) === 'REPORT_STORAGE_UNAVAILABLE'
          ? messages.thesis.report.fileUnavailable
          : locale === 'vi'
            ? 'Không thể tải tài liệu báo cáo'
            : 'Failed to download report artifact',
      );
    }
  };

  const getTopicTitle = (topicId?: string | null) => {
    if (!topicId) return '—';
    const found = topics.find((topic) => topic.id === topicId);
    // The directory may be department-scoped while own supervision spans it.
    const supervised = lecturerWorkload?.topics.find((topic) => topic.topicId === topicId);
    return found ? found.title : supervised?.title || topicId;
  };

  const getLecturerLabel = (lecturer: Lecturer) => {
    const linkedUser = lecturer.user;
    const name = linkedUser
      ? `${linkedUser.lastName ?? ''} ${linkedUser.firstName ?? ''}`.trim()
      : '';
    return name || lecturer.employeeId || lecturer.id;
  };

  const statusLabel = (status: string) =>
    messages.thesis.status[status as keyof typeof messages.thesis.status] ??
    messages.common.statuses[status.toUpperCase() as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  useEffect(() => {
    if (!currentGroup?.topicId) {
      setCurrentTopicSupervisors([]);
      return;
    }
    let cancelled = false;
    void thesisApi
      .listSupervisors(currentGroup.topicId)
      .then((sups) => {
        if (!cancelled) {
          setCurrentTopicSupervisors(sups);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setSecondaryDataError(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [currentGroup?.topicId]);

  // Once the reader picks a tab by hand, background refreshes and round
  // changes must not snap them back to an auto-selected tab.
  const userChoseTabRef = useRef(false);
  const lecturerTabs = useRovingTabs(
    canLoadStaffRelations ? THESIS_LECTURER_TABS : THESIS_SUPERVISION_TAB_ONLY,
    lecturerTab,
    chooseLecturerTab,
  );

  useEffect(() => {
    if (!canLoadStaffRelations) {
      setLecturerTab('supervision');
      return;
    }
    if (userChoseTabRef.current) {
      return;
    }
    const tabParam = searchParams.get('tab');
    if (tabParam === 'repository') {
      setLecturerTab('repository');
      return;
    }
    if (tabParam === 'defense') {
      setLecturerTab('defense');
      return;
    }
    if (tabParam === 'supervision') {
      setLecturerTab('supervision');
      return;
    }
    if (supervisedGroups.length === 0 && visibleCouncils.length > 0) {
      setLecturerTab('defense');
    } else if (supervisedGroups.length > 0) {
      setLecturerTab('supervision');
    }
  }, [canLoadStaffRelations, searchParams, supervisedGroups.length, visibleCouncils.length]);

  useEffect(() => {
    const topicId = searchParams.get('topicId');
    if (topicId && topics.length > 0) {
      const found = topics.find((t) => t.id === topicId);
      if (found) {
        setViewingTopic(found);
      }
    }
  }, [searchParams, topics]);

  useEffect(() => {
    const actionParam = searchParams.get('action');
    const advisorIdParam = searchParams.get('advisorId');
    if (actionParam === 'propose' && canProposeTopics) {
      if (advisorIdParam) {
        setProposeSupervisorId(advisorIdParam);
      }
      setIsProposeModalOpen(true);
    }
  }, [canProposeTopics, searchParams]);

  const studentTopicSupervisors = useMemo(() => {
    return currentTopicSupervisors.map((supervisor) => {
      // The API now ships the name with the row; fall back to the lecturer
      // directory, and only then to the raw id.
      const fromRow = [supervisor.lastName, supervisor.firstName].filter(Boolean).join(' ').trim();
      if (fromRow) {
        return fromRow;
      }
      const lect = lecturers.find((l) => l.id === supervisor.lecturerId);
      if (lect?.user) {
        return `${lect.user.lastName} ${lect.user.firstName}`.trim();
      }
      return supervisor.lecturerId;
    });
  }, [currentTopicSupervisors, lecturers]);

  const currentTopic = topics.find((t) => t.id === currentGroup?.topicId);
  const myResultItem = currentGroup ? roundResults.find((r) => r.groupId === currentGroup.id) : null;
  const studentFinalScore = currentTopic?.finalScore ?? myResultItem?.finalScore ?? null;
  const studentGradeInfo = studentFinalScore != null ? getGradeClassification(studentFinalScore) : null;

  const currentLeaderMember = groupMemberList.find((m) => m.isLeader);
  const currentLeaderName = currentLeaderMember
    ? (currentLeaderMember.displayName || currentLeaderMember.studentNumber || currentLeaderMember.studentId)
    : (currentGroup?.leaderStudentId || pageCopy.groupLeaderFallback);

  const memberCopy =
    locale === 'vi'
      ? ({
          searchLabel: 'Tìm sinh viên trong trường',
          searchPlaceholder: 'Nhập tên, MSSV hoặc email...',
          searchButton: 'Tìm',
          searchHint: 'Nhập ít nhất 2 ký tự để tìm theo tên, MSSV hoặc email.',
          searching: 'Đang tìm...',
          searchNoResults: 'Không tìm thấy sinh viên phù hợp.',
          addShort: 'Thêm',
          addedShort: 'Đã trong nhóm',
          curriculumLabel: 'CTĐT',
          groupFull: messages.thesis.maxMembersReached,
          studentAlreadyInGroup: 'Sinh viên này đã thuộc một nhóm trong đợt này.',
        } as const)
      : ({
          searchLabel: 'Search internal students',
          searchPlaceholder: 'Enter name, student ID or email...',
          searchButton: 'Search',
          searchHint: 'Type at least 2 characters to search by name, student ID or email.',
          searching: 'Searching...',
          searchNoResults: 'No matching students found.',
          addShort: 'Add',
          addedShort: 'Already in group',
          curriculumLabel: 'Curriculum',
          groupFull: messages.thesis.maxMembersReached,
          studentAlreadyInGroup: 'This student already belongs to a group in this round.',
        } as const);

  const refreshGroups = async () => {
    if (!selectedRoundId) return;
    try {
      setGroups(await thesisApi.listGroups(selectedRoundId));
    } catch {
      // The preceding mutation already succeeded — a failed roster reload must
      // not be reported as "action failed" by the caller's catch block.
      toast.warning(messages.thesis.groupsRefreshFailed);
    }
  };

  /** Backend domain code → dictionary copy, falling back to the generic line. */
  const thesisActionError = (caught: unknown): string => {
    const codes = messages.common.campusErrors.codes as Record<string, string>;
    const code = getThesisErrorCode(caught);
    return (code ? codes[code] : undefined) ?? messages.thesis.actionFailed;
  };

  const createGroup = async () => {
    if (!selectedRoundId) return;
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.createGroup(selectedRoundId);
      await refreshGroups();
    } catch (caught) {
      setActionError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  const handleAddMember = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentGroup) return;

    if (!memberName.trim()) {
      setAddMemberError(messages.thesis.allFieldsRequired);
      return;
    }

    setIsActionPending(true);
    setAddMemberError('');
    setActionSuccess('');
    setActionError('');

    try {
      await thesisApi.addMember(currentGroup.id, {
        displayName: memberName.trim(),
        contact: memberContact.trim() || undefined,
      });
      await refreshGroups();
      setIsAddMemberModalOpen(false);
      setMemberName('');
      setMemberContact('');
      setActionSuccess(messages.thesis.memberAdded);
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'GROUP_FULL') {
        setAddMemberError(memberCopy.groupFull);
      } else if (code === 'STUDENT_ALREADY_IN_GROUP') {
        setAddMemberError(memberCopy.studentAlreadyInGroup);
      } else {
        setAddMemberError(thesisActionError(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const resetStudentSearch = () => {
    setStudentQuery('');
    setStudentResults([]);
    setHasSearched(false);
    setAddMemberError('');
  };

  const handleSearchStudents = async () => {
    const query = studentQuery.trim();
    if (query.length < 2) {
      setAddMemberError(memberCopy.searchHint);
      return;
    }
    setIsSearching(true);
    setHasSearched(true);
    setAddMemberError('');
    try {
      setStudentResults(await thesisApi.searchStudents(query));
    } catch {
      setStudentResults([]);
      setAddMemberError(messages.thesis.actionFailed);
    } finally {
      setIsSearching(false);
    }
  };

  const handleAddInternalMember = async (student: ThesisStudentResult) => {
    if (!currentGroup) return;
    setIsActionPending(true);
    setAddMemberError('');
    setActionSuccess('');
    setActionError('');

    try {
      await thesisApi.addMember(currentGroup.id, { studentId: student.studentId });
      await refreshGroups();
      setIsAddMemberModalOpen(false);
      resetStudentSearch();
      setActionSuccess(messages.thesis.memberAdded);
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'GROUP_FULL') {
        setAddMemberError(memberCopy.groupFull);
      } else if (code === 'STUDENT_ALREADY_IN_GROUP') {
        setAddMemberError(memberCopy.studentAlreadyInGroup);
      } else {
        setAddMemberError(thesisActionError(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const handleRemoveMember = async (targetStudentId: string) => {
    if (!currentGroup) return;
    // App-standard destructive-action dialog (matches admin/user management),
    // not a blocking native confirm.
    const shouldRemove = await confirm({
      title: messages.thesis.removeMemberTitle,
      message: messages.thesis.removeMemberConfirm,
      confirmText: messages.thesis.removeMember,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!shouldRemove) return;

    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');

    try {
      await thesisApi.removeMember(currentGroup.id, targetStudentId);
      await refreshGroups();
      setActionSuccess(messages.thesis.memberRemoved);
    } catch (caught) {
      setActionError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  // Topic selection lives only in the catalog (feedback item 6); the inline
  // chooser was removed so the catalog stays the single entry point.

  const approveGroup = async (groupId: string) => {
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.approveGroup(groupId);
      await refreshGroups();
      setActionSuccess(messages.thesis.approveSuccess);
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'GROUP_TOO_SMALL' && isAdmin) {
        // Governance may still approve an under-size roster — but only via
        // the documented size exception, not a dead-end error message.
        openApproveExceptionModal(groupId);
      } else if (code === 'GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED') {
        // A non-supervisor approval is a governance override: the documented
        // reason is collected in the same modal pattern and resubmitted.
        setApproveOverrideGroupId(groupId);
        setApproveOverrideReason('');
        setApproveOverrideError('');
        setIsApproveOverrideModalOpen(true);
      } else if (code === 'GROUP_TOO_SMALL') {
        setActionError(messages.thesis.groupTooSmall);
      } else if (code === 'GROUP_APPROVAL_STATE_CONFLICT') {
        setActionError(messages.thesis.groupStateConflict);
      } else {
        setActionError(thesisActionError(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  /** Opens the size-exception dialog directly (proactive governance action). */
  const openApproveExceptionModal = (groupId: string) => {
    setApproveExceptionGroupId(groupId);
    setApproveExceptionReason('');
    setApproveExceptionError('');
    setIsApproveExceptionModalOpen(true);
  };

  const confirmApproveException = async () => {
    const trimmed = approveExceptionReason.trim();
    if (!trimmed) {
      setApproveExceptionError(messages.thesis.sizeExceptionReasonPlaceholder);
      return;
    }
    if (trimmed.length > 500) {
      setApproveExceptionError(messages.thesis.reasonTooLong);
      return;
    }
    setIsActionPending(true);
    setApproveExceptionError('');
    setActionSuccess('');
    try {
      await thesisApi.approveGroup(approveExceptionGroupId, {
        allowSizeException: true,
        reason: trimmed,
      });
      await refreshGroups();
      setIsApproveExceptionModalOpen(false);
      setActionSuccess(messages.thesis.approveSuccess);
    } catch (caught) {
      setApproveExceptionError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  const confirmApproveOverride = async () => {
    const trimmed = approveOverrideReason.trim();
    if (!trimmed) {
      setApproveOverrideError(messages.thesis.rejectionReasonPlaceholder);
      return;
    }
    if (trimmed.length > 500) {
      setApproveOverrideError(messages.thesis.reasonTooLong);
      return;
    }
    setIsActionPending(true);
    setApproveOverrideError('');
    setActionSuccess('');
    try {
      await thesisApi.approveGroup(approveOverrideGroupId, { reason: trimmed });
      await refreshGroups();
      setIsApproveOverrideModalOpen(false);
      setActionSuccess(messages.thesis.approveSuccess);
    } catch (caught) {
      setApproveOverrideError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  const openWithdrawModal = (groupId: string) => {
    setWithdrawGroupId(groupId);
    setWithdrawReason('');
    setWithdrawError('');
    setIsWithdrawModalOpen(true);
  };

  const confirmWithdrawApproval = async () => {
    const trimmed = withdrawReason.trim();
    if (!trimmed) {
      setWithdrawError(messages.thesis.withdrawApprovalReasonPlaceholder);
      return;
    }
    if (trimmed.length > 500) {
      setWithdrawError(messages.thesis.reasonTooLong);
      return;
    }
    setIsActionPending(true);
    setWithdrawError('');
    setActionSuccess('');
    try {
      await thesisApi.withdrawGroupApproval(withdrawGroupId, trimmed);
      await refreshGroups();
      setIsWithdrawModalOpen(false);
      setActionSuccess(messages.thesis.withdrawApprovalSuccess);
    } catch (caught) {
      setWithdrawError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  const openRejectModal = (groupId: string) => {
    setRejectGroupId(groupId);
    setRejectReason('');
    setRejectError('');
    setIsRejectModalOpen(true);
  };

  const confirmRejectGroup = async () => {
    const trimmed = rejectReason.trim();
    if (!trimmed) {
      setRejectError(messages.thesis.rejectionReasonPlaceholder);
      return;
    }
    if (trimmed.length > 500) {
      setRejectError(messages.thesis.reasonTooLong);
      return;
    }
    setIsActionPending(true);
    setRejectError('');
    setActionSuccess('');
    try {
      await thesisApi.rejectGroup(rejectGroupId, trimmed);
      await refreshGroups();
      setIsRejectModalOpen(false);
      setActionSuccess(messages.thesis.rejectSuccess);
    } catch (caught) {
      setRejectError(thesisActionError(caught));
    } finally {
      setIsActionPending(false);
    }
  };

  const openReportForm = () => {
    if (reportWindowState !== 'open') return;
    setReportTitle(groupReport?.title ?? '');
    setReportUrl(groupReport?.url ?? '');
    setReportNote(groupReport?.note ?? '');
    setReportFile(null);
    setReportError('');
    setIsReportFormOpen(true);
  };

  const REPORT_FILE_EXTENSIONS = ['.pdf', '.doc', '.docx'];
  const REPORT_MAX_BYTES = 20 * 1024 * 1024;

  // In-flight downloads per group: a double-click (or a slow network invite
  // to click again) must not fire a second identical download.
  const downloadPendingRef = useRef(new Set<string>());

  /** Downloads an attached report document for any authorized viewer. */
  const downloadReportArtifact = async (
    groupId: string,
    report: ThesisGroupReport | null | undefined,
  ) => {
    if (!report?.fileName || downloadPendingRef.current.has(groupId)) return;
    downloadPendingRef.current.add(groupId);
    try {
      await thesisApi.downloadReportFile(groupId, report);
    } catch (caught) {
      // REPORT_STORAGE_UNAVAILABLE means the stored bytes are gone for good
      // (ephemeral container storage); the fix is a re-upload, so say so.
      toast.error(
        getThesisErrorCode(caught) === 'REPORT_STORAGE_UNAVAILABLE'
          ? messages.thesis.report.fileUnavailable
          : messages.thesis.report.submitFailed,
      );
    } finally {
      downloadPendingRef.current.delete(groupId);
    }
  };

  const handleDownloadReportFile = async () => {
    if (!currentGroup || !groupReport?.fileName) return;
    await downloadReportArtifact(currentGroup.id, groupReport);
  };

  /**
   * Shared error map for every report write path (create, PATCH edit, file
   * replace, file detach). The window can close between render and submit
   * because the panel gates on a snapshot, so the localized domain copies beat
   * the generic failure line.
   */
  const reportWriteErrorMessage = (caught: unknown): string => {
    const codes = messages.common.campusErrors.codes;
    const code = getThesisErrorCode(caught);
    if (code === 'FILE_TOO_LARGE') return messages.thesis.report.fileTooLarge;
    if (code === 'UNSUPPORTED_FILE_TYPE' || code === 'INVALID_FILE_CONTENT') {
      return messages.thesis.report.fileTypeUnsupported;
    }
    if (code === 'REPORT_WINDOW_CLOSED') return codes.REPORT_WINDOW_CLOSED;
    if (code === 'REPORT_DEADLINE_PASSED') return codes.REPORT_DEADLINE_PASSED;
    if (code === 'REPORT_ARTIFACT_REQUIRED') return codes.REPORT_ARTIFACT_REQUIRED;
    if (code === 'GROUP_STATE_CONFLICT') return codes.GROUP_STATE_CONFLICT;
    if (code === 'ROUND_STATE_CONFLICT') return codes.ROUND_STATE_CONFLICT;
    if (code === 'GROUP_OWNER_REQUIRED') return codes.GROUP_OWNER_REQUIRED;
    if (code === 'VALIDATION_ERROR') return codes.VALIDATION_ERROR;
    if (code === 'REPORT_STORAGE_UNAVAILABLE') return messages.thesis.report.fileUnavailable;
    return messages.thesis.report.submitFailed;
  };

  /** Detaches only the stored document; the link/metadata survive (F4). */
  const handleDetachReportFile = async () => {
    if (!currentGroup || !groupReport?.fileName) return;
    const approved = await confirm({
      title: messages.thesis.report.detachConfirmTitle,
      message: messages.thesis.report.detachConfirmDescription,
      confirmText: messages.thesis.report.detachConfirmAction,
      cancelText: messages.common.actions.cancel,
      variant: 'destructive',
    });
    if (!approved) return;
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      const saved = await thesisApi.deleteReportFile(currentGroup.id);
      setGroupReport(saved);
      setActionSuccess(messages.thesis.report.detachSuccess);
      toast.success(messages.thesis.report.detachSuccess);
    } catch (caught) {
      const message = reportWriteErrorMessage(caught);
      setActionError(message);
      toast.error(message);
    } finally {
      setIsActionPending(false);
    }
  };

  const handleSubmitReport = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentGroup) return;
    const url = reportUrl.trim();

    // Feedback item 7: a Word/PDF upload or a link — at least one artifact.
    // A stored file also counts, so a file-only report can still have its
    // title/note edited without inventing a link (F7).
    if (reportFile) {
      const lowerName = reportFile.name.toLowerCase();
      const extensionOk = REPORT_FILE_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
      if (!extensionOk) {
        setReportError(messages.thesis.report.fileTypeUnsupported);
        return;
      }
      if (reportFile.size > REPORT_MAX_BYTES) {
        setReportError(messages.thesis.report.fileTooLarge);
        return;
      }
    } else if (!url && !groupReport?.url && !groupReport?.fileName) {
      setReportError(messages.thesis.report.fileRequired);
      return;
    }

    setIsActionPending(true);
    setReportError('');
    setActionSuccess('');
    try {
      let saved: ThesisGroupReport;
      if (reportFile) {
        // Only a real artifact loss earns the destructive confirm: a freshly
        // chosen file replaces the stored document. Metadata edits and link
        // changes go through PATCH without any scary dialog.
        if (groupReport?.fileName) {
          const approved = await confirm({
            title: messages.thesis.report.replaceConfirmTitle,
            message: messages.thesis.report.replaceConfirmDescription,
            confirmText: messages.thesis.report.replaceConfirmAction,
            variant: 'destructive',
          });
          if (!approved) {
            setIsActionPending(false);
            return;
          }
        }
        const controller = new AbortController();
        reportUploadAbortRef.current = controller;
        setReportUploadPercent(0);
        try {
          saved = await thesisApi.submitReportFile(currentGroup.id, {
            file: reportFile,
            title: reportTitle.trim() || undefined,
            note: reportNote.trim() || undefined,
            onUploadProgress: setReportUploadPercent,
            signal: controller.signal,
          });
        } finally {
          reportUploadAbortRef.current = null;
          setReportUploadPercent(null);
        }
        // Reflect the landed file immediately: if the follow-up URL PATCH
        // fails, the UI still shows "submitted" instead of a stale empty
        // state while the server already holds the document.
        setGroupReport(saved);
        // The link field is independent of the upload (file + link coexist):
        // a URL typed or cleared alongside the file goes through PATCH right
        // after the document lands.
        const previousUrl = groupReport?.url ?? '';
        if (url !== previousUrl) {
          saved = await thesisApi.updateReport(
            currentGroup.id,
            url ? { url } : { clearUrl: true },
          );
        }
      } else if (groupReport) {
        // Non-destructive PATCH: the stored file and the submitted_* audit
        // stamp stay untouched. An emptied URL field means the leader cleared
        // the link on purpose — the server refuses when it is the last
        // artifact (409 REPORT_ARTIFACT_REQUIRED, mapped above).
        saved = await thesisApi.updateReport(currentGroup.id, {
          title: reportTitle.trim() || undefined,
          note: reportNote.trim() || undefined,
          ...(url ? { url } : groupReport.url ? { clearUrl: true } : {}),
        });
      } else {
        // First submission without a file: the link-only create path.
        if (!url) {
          setReportError(messages.thesis.report.fileRequired);
          setIsActionPending(false);
          return;
        }
        saved = await thesisApi.submitReport(currentGroup.id, {
          title: reportTitle.trim() || undefined,
          url,
          note: reportNote.trim() || undefined,
        });
      }
      setGroupReport(saved);
      setReportFile(null);
      setIsReportFormOpen(false);
      setActionSuccess(groupReport ? messages.thesis.report.updateSuccess : messages.thesis.report.submitSuccess);
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') {
        // The leader cancelled the upload on purpose: not an error to report.
        setReportError('');
      } else {
        setReportError(reportWriteErrorMessage(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const handleProposeTopic = async (event: React.FormEvent) => {
    event.preventDefault();
    if (isActionPending || !canProposeTopics) return;
    const targetRoundId = proposeRoundId || selectedRoundId;
    if (!targetRoundId) return;
    const title = proposeTitle.trim();
    const description = proposeDescription.trim();
    const departmentId = proposeDepartmentId.trim();
    if (!title || !description || !departmentId) {
      setProposeError(messages.thesis.allFieldsRequired);
      return;
    }
    setIsActionPending(true);
    setProposeError('');
    setActionError('');
    setActionSuccess('');
    try {
      const created = await thesisApi.createTopic({
        roundId: targetRoundId,
        departmentId,
        title,
        description,
        maxGroups: Math.min(20, Math.max(1, Number(proposeMaxGroups) || 1)),
      });
      const supervisorIds = [proposeSupervisorId, proposeSecondSupervisorId]
        .map((id) => id.trim())
        .filter(Boolean);
      let supervisorsAssigned = false;
      if (supervisorIds.length > 0) {
        try {
          await thesisApi.setSupervisors(created.id, supervisorIds);
          supervisorsAssigned = true;
        } catch (caught) {
          // The topic exists, but a silently dropped supervisor assignment
          // leaves the topic ungradeable — surface the failure with the
          // domain code so the lecturer can retry from the topic page.
          const code = getThesisErrorCode(caught);
          toast.error(
            code === 'LECTURER_NOT_FOUND' || code === 'INVALID_SUPERVISOR'
              ? messages.thesis.supervisorInvalid
              : messages.thesis.supervisorAssignFailed,
          );
        }
      }
      let publishCreated = false;
      if (isTopicApprover && proposePublishImmediately) {
        if (supervisorsAssigned) {
          try {
            await thesisApi.publishTopic(created.id);
            publishCreated = true;
          } catch (caught) {
            // A rejected approval leaves the saved draft available for review;
            // retrying the create form would otherwise create a second proposal.
            const message = topicActionErrorMessage(caught);
            setActionError(message);
            toast.error(message);
          }
        } else {
          // "Publish immediately" was checked but publishing requires an
          // assigned supervisor — the topic silently became a draft without
          // this notice.
          toast.warning(messages.thesis.createdAsDraftNotice);
        }
      }
      if (selectedRoundId !== targetRoundId) {
        setSelectedRoundId(targetRoundId);
      } else {
        try {
          await refreshTopics(targetRoundId);
        } catch {
          setError(messages.thesis.loadFailed);
        }
      }
      setIsProposeModalOpen(false);
      setProposeTitle('');
      setProposeDescription('');
      setProposeDepartmentId(departments[0]?.id || '');
      setProposeMaxGroups(2);
      setProposePublishImmediately(false);
      setProposeSupervisorId('');
      setProposeSecondSupervisorId('');
      setActionSuccess(
        publishCreated
          ? messages.thesis.publishedSuccess
          : messages.thesis.proposeSuccess,
      );
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'ROUND_NOT_ACCEPTING_PROPOSALS') {
        setProposeError(messages.thesis.roundNotAcceptingProposals);
      } else if (code === 'LECTURER_WINDOW_NOT_OPEN') {
        setProposeError(messages.thesis.lecturerWindowNotOpen);
      } else if (code === 'LECTURER_WINDOW_CLOSED') {
        setProposeError(messages.thesis.lecturerWindowClosed);
      } else if (code === 'TOPIC_DEPARTMENT_FORBIDDEN') {
        setProposeError(messages.thesis.topicDepartmentForbidden);
      } else {
        setProposeError(thesisActionError(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const openEditModal = (topic: ThesisTopic) => {
    if (isActionPending || !topicCapabilities(topic).canEdit) return;
    setEditingTopicId(topic.id);
    setEditTitle(topic.title);
    setEditDescription(topic.description);
    setEditDepartmentId(topic.departmentId || departments[0]?.id || '');
    setEditMaxGroups(topic.maxGroups || 2);
    setEditError('');
    setIsEditModalOpen(true);
  };

  const handleEditTopic = async (event: React.FormEvent) => {
    event.preventDefault();
    const topic = topics.find((item) => item.id === editingTopicId);
    if (isActionPending || !topic || !topicCapabilities(topic).canEdit) return;
    const title = editTitle.trim();
    const description = editDescription.trim();
    const departmentId = editDepartmentId.trim();
    if (!title || !description || !departmentId) {
      setEditError(messages.thesis.allFieldsRequired);
      return;
    }
    setIsActionPending(true);
    setEditError('');
    setActionSuccess('');
    try {
      await thesisApi.updateTopic(editingTopicId, {
        title,
        description,
        departmentId,
        maxGroups: Math.min(20, Math.max(1, Number(editMaxGroups) || 1)),
      });
      await refreshTopics(selectedRoundId);
      setIsEditModalOpen(false);
      setViewingTopic(null);
      setActionSuccess(messages.thesis.editTopicSuccess);
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'ROUND_NOT_ACCEPTING_PROPOSALS') {
        setEditError(messages.thesis.roundNotAcceptingProposals);
      } else if (code === 'LECTURER_WINDOW_NOT_OPEN') {
        setEditError(messages.thesis.lecturerWindowNotOpen);
      } else if (code === 'LECTURER_WINDOW_CLOSED') {
        setEditError(messages.thesis.lecturerWindowClosed);
      } else if (code === 'TOPIC_DEPARTMENT_FORBIDDEN') {
        setEditError(messages.thesis.topicDepartmentForbidden);
      } else {
        setEditError(thesisActionError(caught));
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const topicActionErrorMessage = (caught: unknown) => {
    const code = getThesisErrorCode(caught);
    const status = (caught as { response?: { status?: number } })?.response?.status;
    if (code === 'TOPIC_IN_USE') return messages.thesis.topicInUse;
    if (code === 'TOPIC_STATE_CONFLICT') return messages.thesis.topicStateConflict;
    if (code === 'ROUND_NOT_ACCEPTING_PROPOSALS') return messages.thesis.roundNotAcceptingProposals;
    if (code === 'LECTURER_WINDOW_NOT_OPEN') return messages.thesis.lecturerWindowNotOpen;
    if (code === 'LECTURER_WINDOW_CLOSED') return messages.thesis.lecturerWindowClosed;
    if (code === 'TOPIC_DEPARTMENT_FORBIDDEN') return messages.thesis.topicDepartmentForbidden;
    if (code === 'FORBIDDEN' || status === 403) return messages.thesis.topicActionForbidden;
    return thesisActionError(caught);
  };

  const handlePublishTopic = async (topicId: string) => {
    const topic = topics.find((item) => item.id === topicId);
    if (isActionPending || !topic || !topicCapabilities(topic).canPublish) return;
    setIsActionPending(true);
    setTopicActionError('');
    setActionError('');
    setActionSuccess('');
    try {
      const published = await thesisApi.publishTopic(topicId);
      setTopics((current) => current.map((item) => item.id === published.id ? published : item));
      setViewingTopic((current) => current?.id === published.id ? published : current);
      setActionSuccess(messages.thesis.publishedSuccess);
      toast.success(messages.thesis.publishedSuccess);
      try {
        await refreshTopics(topic.roundId);
      } catch {
        setError(messages.thesis.loadFailed);
      }
    } catch (caught) {
      const message = topicActionErrorMessage(caught);
      setTopicActionError(message);
      toast.error(message);
    } finally {
      setIsActionPending(false);
    }
  };

  const handleArchiveTopic = async (topic: ThesisTopic) => {
    if (isActionPending || !topicCapabilities(topic).canArchive) return;
    setIsActionPending(true);
    setTopicActionError('');
    setActionError('');
    setActionSuccess('');
    try {
      setIsArchiveConfirmationOpen(true);
      const approved = await confirm({
        title: messages.thesis.archiveTopicTitle,
        message: fillCopy(messages.thesis.archiveTopicConfirm, { title: topic.title }),
        confirmText: messages.thesis.archiveTopic,
        cancelText: messages.common.actions.cancel,
        variant: 'destructive',
      });
      setIsArchiveConfirmationOpen(false);
      if (!approved) return;
      await thesisApi.archiveTopic(topic.id);
      setTopics((current) => current.filter((item) => item.id !== topic.id));
      setViewingTopic((current) => current?.id === topic.id ? null : current);
      setActionSuccess(messages.thesis.archiveTopicSuccess);
      toast.success(messages.thesis.archiveTopicSuccess);
      try {
        await refreshTopics(topic.roundId);
      } catch {
        setError(messages.thesis.loadFailed);
      }
    } catch (caught) {
      const message = topicActionErrorMessage(caught);
      setTopicActionError(message);
      toast.error(message);
    } finally {
      setIsArchiveConfirmationOpen(false);
      setIsActionPending(false);
    }
  };

  if (isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (error && rounds.length === 0) {
    return (
      <ErrorState
        title={messages.thesis.loadFailed}
        description={error}
        retryLabel={messages.thesis.retry}
        onRetry={() => void loadRounds({ cancelled: false })}
      />
    );
  }

  
  // Live document validation: the same extension/size rules the submit handler
  // applies, surfaced on the selected-file row before the leader presses the CTA.
  const reportFileIssue = reportFile
    ? !REPORT_FILE_EXTENSIONS.some((ext) => reportFile.name.toLowerCase().endsWith(ext))
      ? messages.thesis.report.fileTypeUnsupported
      : reportFile.size > REPORT_MAX_BYTES
        ? messages.thesis.report.fileTooLarge
        : ''
    : '';

  // The round that owns the group carries the countdown the submission-status
  // timeline shows. `reportDeadline` is the audited override the escape-hatch
  // endpoint writes; when absent the window still falls back to
  // `gvpbDeadline`, matching the server's COALESCE.
  const reportDeadlineRound = currentGroup
    ? rounds.find((round) => round.id === currentGroup.roundId)
    : selectedRound;
  const reportDeadlineAt = reportDeadlineRound?.reportDeadline ?? reportDeadlineRound?.gvpbDeadline ?? null;
  const reportDeadlineMs = reportDeadlineAt ? Date.parse(reportDeadlineAt) : Number.NaN;
  const reportDaysRemaining = Number.isFinite(reportDeadlineMs)
    ? Math.ceil((reportDeadlineMs - Date.now()) / 86_400_000)
    : null;
  // The backend freezes a report once this deadline passes (409
  // REPORT_DEADLINE_PASSED); the panel now says so instead of offering a button
  // that can only fail. A round type without this date never freezes, matching
  // the server.
  const reportDeadlinePassed = Number.isFinite(reportDeadlineMs) && Date.now() >= reportDeadlineMs;
  // Four honest window states replace the old two-flag check, which read
  // RESULTS_PUBLISHED/CLOSED rounds as "opens after registration closes" —
  // backwards. Order matters: a finished round stays finished even when its
  // deadline date also passed, and an unknown owning round stays permissive
  // (the server remains the write gate, 409 REPORT_WINDOW_CLOSED).
  const reportWindowState: 'notYetOpen' | 'open' | 'deadlinePassed' | 'closedFinal' =
    reportDeadlineRound == null
      ? 'open'
      : reportDeadlineRound.status === 'RESULTS_PUBLISHED' ||
          reportDeadlineRound.status === 'CLOSED' ||
          reportDeadlineRound.status === 'CANCELLED'
        ? 'closedFinal'
        : reportDeadlinePassed
          ? 'deadlinePassed'
          : reportDeadlineRound.status === 'REGISTRATION_CLOSED'
            ? 'open'
            : 'notYetOpen';

  // The report panel stays here: it owns the submission form, its state, and
  // the leader-only rights it renders. A group that is not approved yet still
  // owns its report row — say so instead of rendering nothing and looking
  // like the data vanished.
  const studentReportSection = !currentGroup ? null : (
      <div className="rounded-xl border border-border/70 bg-card p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-primary" />
            <span className="text-sm font-semibold text-foreground">
              {messages.thesis.report.title} {pageCopy.articleR5Suffix}
            </span>
          </div>
          {currentGroup.approvalStatus !== 'APPROVED' ? (
            <p className="flex items-start gap-2 text-xs text-muted-foreground">
              <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              <span>{messages.thesis.report.pendingApproval}</span>
            </p>
          ) : isGroupLeader ? (
            reportWindowState === 'deadlinePassed' ? (
              <span className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                {messages.thesis.report.lockedTitle}
              </span>
            ) : reportWindowState === 'closedFinal' ? (
              // Terminal rounds stay terminal: never a button that can only
              // fail, and never the backwards "opens later" copy.
              <span className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-border/70 bg-muted/50 px-2.5 py-1 text-xs font-semibold text-muted-foreground">
                <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                {messages.thesis.report.closedFinalTitle}
              </span>
            ) : reportWindowState === 'notYetOpen' ? (
              // Locked until registration closes: show the real CTA disabled
              // with the same copy the error map carries, never a button that
              // can only fail with a generic toast.
              <div className="flex shrink-0 flex-col items-end gap-1">
                <Button type="button" size="sm" variant="outline" disabled>
                  {groupReport ? messages.thesis.report.update : messages.thesis.report.submit}
                </Button>
                <span className="inline-flex max-w-[16rem] items-start gap-1.5 text-xs text-muted-foreground">
                  <Lock className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                  {messages.common.campusErrors.codes.REPORT_WINDOW_CLOSED}
                </span>
              </div>
            ) : (
              <div className="flex shrink-0 flex-col items-end gap-1">
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={openReportForm}
                  disabled={isActionPending}
                  className="shrink-0"
                >
                  {groupReport
                    ? messages.thesis.report.update
                    : messages.thesis.report.submit}
                </Button>
                {reportDeadlineAt ? (
                  <span className="inline-flex max-w-[16rem] items-start gap-1.5 text-xs text-muted-foreground">
                    <CalendarDays className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                    {fillCopy(messages.thesis.report.openUntil, {
                      deadline: formatDateTime(reportDeadlineAt),
                    })}
                  </span>
                ) : null}
              </div>
            )
          ) : (
            // Read-only variant: a member must know the report is not theirs to
            // change, and who submits it, rather than seeing no control at all.
            <span className="inline-flex max-w-[16rem] shrink-0 items-start gap-1.5 rounded-md border border-border/70 bg-muted/40 px-2.5 py-1 text-xs text-muted-foreground">
              <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
              <span>
                {fillCopy(messages.thesis.report.leaderOnlyBanner, { leader: currentLeaderName })}
              </span>
            </span>
          )}
        </div>

        {/* Regulatory Notice R5 */}
        <div className="mt-3 rounded-lg border border-primary/20 bg-primary/[0.03] p-2.5 text-xs text-muted-foreground flex items-start gap-2">
          <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" />
          <span>
            {renderInlineBold(fillCopy(pageCopy.reportRightsNotice, { leader: currentLeaderName }))}
          </span>
        </div>

        {isReportLoading ? (
          <p className="mt-3 text-xs text-muted-foreground">
            {messages.common.states.loading}
          </p>
        ) : groupReport ? (
          <div className="mt-3 space-y-1">
            {groupReport.title ? (
              <p className="text-sm font-medium text-foreground">
                {groupReport.title}
              </p>
            ) : null}
            {groupReport.fileName ? (
              // Feedback item 7: the attached Word/PDF document.
              <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-primary/15 bg-primary/[0.035] p-3">
                <div className="flex min-w-[10rem] flex-1 items-start gap-2.5">
                  <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <FileStack className="h-4 w-4" aria-hidden="true" />
                  </span>
                  <div className="min-w-0">
                    <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                      {messages.thesis.report.documentAttached}
                    </p>
                    <p
                      className="truncate text-sm font-medium text-foreground"
                      title={groupReport.fileName}
                    >
                      {groupReport.fileName}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {[groupReport.fileType, formatReportFileSize(groupReport.fileSize, locale)]
                        .filter(Boolean)
                        .join(' · ')}
                    </p>
                  </div>
                </div>
                <div className="flex max-w-full flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => void handleDownloadReportFile()}
                    className="shrink-0"
                  >
                    {messages.thesis.report.downloadFile}
                    <FileDown className="ml-1.5 h-3.5 w-3.5" aria-hidden="true" />
                  </Button>
                  {isGroupLeader && currentGroup.approvalStatus === 'APPROVED' && reportWindowState === 'open' ? (
                    // The only destructive report action left: detach the
                    // stored document while the link/metadata survive.
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => void handleDetachReportFile()}
                      disabled={isActionPending}
                      className="shrink-0 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    >
                      {messages.thesis.report.detachFile}
                      <Trash2 className="ml-1.5 h-3.5 w-3.5" aria-hidden="true" />
                    </Button>
                  ) : null}
                </div>
              </div>
            ) : null}
            {groupReport.url ? (
              <a
                href={groupReport.url}
                target="_blank"
                rel="noreferrer"
                className="inline-flex min-h-8 items-center gap-1 text-sm text-primary underline-offset-2 hover:underline"
              >
                {messages.thesis.report.view}
                <ExternalLink className="h-3.5 w-3.5" />
              </a>
            ) : null}
            {groupReport.note ? (
              <p className="text-xs text-muted-foreground">{groupReport.note}</p>
            ) : null}
            <p className="text-xs text-muted-foreground">
              {messages.thesis.report.submittedAt}:{' '}
              {formatDateTime(groupReport.submittedAt)}
            </p>
          </div>
        ) : groupReportLoadError ? (
          <div className="mt-3 flex items-center justify-between gap-3 rounded-xl border border-destructive/30 bg-destructive/5 p-3">
            <p className="flex items-start gap-2 text-xs text-destructive">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
              <span>{groupReportLoadError}</span>
            </p>
            <Button
              type="button"
              size="sm"
              variant="outline"
              className="shrink-0"
              onClick={() => setGroupReportReloadToken((token) => token + 1)}
            >
              {messages.common.actions.retry}
            </Button>
          </div>
        ) : (
          <p className="mt-3 text-sm text-muted-foreground">
            {messages.thesis.report.notSubmitted}
          </p>
        )}

        {isGroupLeader && currentGroup.approvalStatus === 'APPROVED' && isReportFormOpen ? (
          <form
            onSubmit={(e) => void handleSubmitReport(e)}
            className="mt-4 space-y-3 border-t border-border/60 pt-4"
          >
            {reportError ? (
              <div className="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-2.5 text-xs text-destructive">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
                <span>{reportError}</span>
              </div>
            ) : null}
            <div>
              <div className="mb-1 flex items-baseline justify-between gap-2">
                <label
                  className="block text-xs font-medium text-foreground"
                  htmlFor="thesis-report-title"
                >
                  {messages.thesis.report.titleLabel}
                </label>
                <span className="text-[11px] tabular-nums text-muted-foreground">
                  {fillCopy(messages.thesis.report.titleCounter, {
                    count: reportTitle.length,
                  })}
                </span>
              </div>
              <Input
                id="thesis-report-title"
                value={reportTitle}
                onChange={(e) => setReportTitle(e.target.value.slice(0, 240))}
                maxLength={240}
                disabled={isActionPending}
              />
            </div>
            <div
              className={cn(
                'rounded-xl border-2 border-dashed p-4 text-center transition-colors',
                reportFileIssue
                  ? 'border-destructive/40 bg-destructive/[0.04]'
                  : 'border-primary/40 bg-primary/[0.025] hover:border-primary',
              )}
            >
              <span className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-primary/10 text-primary">
                <UploadCloud className="h-5 w-5" aria-hidden="true" />
              </span>
              <p className="mt-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {messages.thesis.report.fileLabel}
              </p>
              <label
                className="mt-0.5 block text-sm font-semibold text-foreground"
                htmlFor="thesis-report-file"
              >
                {pageCopy.chooseFileFromDevice}
              </label>
              <p className="mx-auto mt-1 max-w-md text-xs leading-5 text-muted-foreground">
                {messages.thesis.report.uploadPrimaryHint}
              </p>
              <p className="mx-auto mt-0.5 max-w-md text-[11px] font-medium text-muted-foreground">
                {pageCopy.uploadFormatsHint}
              </p>
              <input
                id="thesis-report-file"
                type="file"
                aria-label={messages.thesis.report.pickFile}
                accept=".pdf,.doc,.docx"
                onChange={(e) => setReportFile(e.target.files?.[0] ?? null)}
                disabled={isActionPending}
                className="mt-3 block w-full cursor-pointer rounded-lg border border-input bg-background px-3 py-2 text-xs text-foreground file:mr-3 file:cursor-pointer file:rounded-md file:border-0 file:bg-secondary file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-secondary-foreground hover:file:bg-secondary/80"
              />
            </div>
            {reportFile ? (
              <div
                className={cn(
                  'flex items-center justify-between gap-3 rounded-xl border p-3',
                  reportFileIssue
                    ? 'border-destructive/40 bg-destructive/[0.05]'
                    : 'border-border/70 bg-background/70',
                )}
              >
                <div className="flex min-w-0 items-start gap-2.5">
                  <span
                    className={cn(
                      'mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-lg',
                      reportFileIssue
                        ? 'bg-destructive/10 text-destructive'
                        : 'bg-status-success/15 text-status-success-foreground',
                    )}
                  >
                    {reportFileIssue ? (
                      <AlertCircle className="h-4 w-4" aria-hidden="true" />
                    ) : (
                      <CheckCircle2 className="h-4 w-4" aria-hidden="true" />
                    )}
                  </span>
                  <div className="min-w-0">
                    <p
                      className="truncate text-sm font-medium text-foreground"
                      title={reportFile.name}
                    >
                      {reportFile.name}
                    </p>
                    <p className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                      {formatReportFileSize(reportFile.size, locale) ? (
                        <span>{formatReportFileSize(reportFile.size, locale)}</span>
                      ) : null}
                      {reportFileIssue ? (
                        <span className="font-medium text-destructive">{reportFileIssue}</span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-md border border-status-success/30 bg-status-success/15 px-1.5 py-0.5 text-[11px] font-semibold text-status-success-foreground">
                          <Check className="h-3 w-3" aria-hidden="true" />
                          {pageCopy.fileReadyChip}
                        </span>
                      )}
                    </p>
                  </div>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="ghost"
                  onClick={() => setReportFile(null)}
                  disabled={isActionPending}
                  aria-label={pageCopy.removeFileLabel}
                  title={pageCopy.removeFileLabel}
                  className="h-8 w-8 shrink-0 rounded-lg p-0 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                </Button>
              </div>
            ) : null}
            <details
              className="group rounded-xl border border-border/70 bg-secondary/20 px-3"
              open={Boolean(reportUrl)}
            >
              <summary className="cursor-pointer list-none py-3 text-sm font-medium text-foreground marker:hidden">
                <span className="inline-flex items-center gap-2">
                  <ExternalLink className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  {messages.thesis.report.urlAlternative}
                </span>
              </summary>
              <div className="border-t border-border/60 pb-3 pt-3">
                <label
                  className="mb-1 block text-xs font-medium text-foreground"
                  htmlFor="thesis-report-url"
                >
                  {messages.thesis.report.urlLabel}
                </label>
                <Input
                  id="thesis-report-url"
                  value={reportUrl}
                  onChange={(e) => setReportUrl(e.target.value)}
                  placeholder="https://..."
                  disabled={isActionPending}
                />
                <p className="mt-1 text-[11px] text-muted-foreground">
                  {pageCopy.reportLinkHint}
                </p>
              </div>
            </details>
            <div>
              <div className="mb-1 flex items-baseline justify-between gap-2">
                <label
                  className="block text-xs font-medium text-foreground"
                  htmlFor="thesis-report-note"
                >
                  {messages.thesis.report.noteLabel}
                </label>
                <span className="text-[11px] tabular-nums text-muted-foreground">
                  {fillCopy(messages.thesis.report.noteCounter, {
                    count: reportNote.length,
                  })}
                </span>
              </div>
              <Textarea
                id="thesis-report-note"
                value={reportNote}
                onChange={(e) => setReportNote(e.target.value.slice(0, 500))}
                maxLength={500}
                rows={2}
                disabled={isActionPending}
              />
            </div>
            {reportUploadPercent !== null ? (
              <div
                className="rounded-lg border border-primary/25 bg-primary/[0.04] p-2.5"
                role="status"
                aria-live="polite"
              >
                <div className="flex items-center justify-between gap-2 text-xs text-foreground">
                  <span>
                    {fillCopy(messages.thesis.report.uploadProgress, {
                      percent: reportUploadPercent,
                    })}
                  </span>
                  <Button
                    type="button"
                    size="sm"
                    variant="ghost"
                    onClick={() => reportUploadAbortRef.current?.abort()}
                    className="h-7 px-2 text-xs"
                  >
                    {messages.thesis.report.uploadCancel}
                  </Button>
                </div>
                <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted">
                  <div
                    className="h-full rounded-full bg-primary transition-[width] duration-200"
                    style={{ width: `${reportUploadPercent}%` }}
                  />
                </div>
              </div>
            ) : null}
            <div className="flex justify-end gap-2">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setIsReportFormOpen(false)}
                disabled={isActionPending}
              >
                {messages.common.actions.cancel}
              </Button>
              <Button type="submit" size="sm" disabled={isActionPending} className="gap-1.5">
                {groupReport
                  ? messages.thesis.report.update
                  : messages.thesis.report.submit}
                <Send className="h-3.5 w-3.5" aria-hidden="true" />
              </Button>
            </div>
          </form>
        ) : null}
      </div>
    );

  // The Stitch board puts the submission history beside the form: first
  // submission, latest update, and the supervisor-feedback milestone, with the
  // round countdown as a chip. It reads the same report the panel above owns.
  const studentSubmissionStatus =
    currentGroup && currentGroup.approvalStatus === 'APPROVED' ? (
      <SubmissionStatusTimeline
        report={groupReport}
        reportWindowState={reportWindowState}
        deadlineAt={reportDeadlineAt}
        daysRemaining={reportDaysRemaining}
        formatDateTime={formatDateTime}
        locale={locale}
        copy={pageCopy.submissionStatus}
      />
    ) : null;

  // The group's own round gates its roster, not the round the page is
  // currently viewing.
  const isRegistrationOpenFor = (group: ThesisGroup) =>
    rounds.find((round) => round.id === group.roundId)?.status === 'REGISTRATION_OPEN';

  const groupStatusLabel = currentGroup
    ? messages.thesis.status[currentGroup.status] ?? messages.common.statuses.UNKNOWN
    : '';

  const groupMemberRows: StudentGroupMemberRow[] = groupMemberList.map((member) => {
    const memberDisplayName =
      member.displayName || member.studentNumber || member.studentId || 'SV';
    const memberIdentifier = member.studentNumber || member.studentId;
    return { ...member, displayName: memberDisplayName, identifier: memberIdentifier };
  });

  const renderTopicsCard = () => {
    if (currentGroup?.topicId) {
      const topicTitle = currentTopic?.title || getTopicTitle(currentGroup.topicId);
      const topicDescription = currentTopic?.description
        ? plainTextThesisDescription(currentTopic.description)
        : null;
      return (
        <Card className="h-full border-primary/30 bg-gradient-to-b from-card to-primary/[0.02]">
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <BookOpen className="h-4 w-4" />
                </span>
                <div>
                  <CardTitle className="text-base sm:text-lg">
                    {locale === 'vi' ? 'Đề tài Khóa luận đã đăng ký' : 'Registered Thesis Topic'}
                  </CardTitle>
                  <CardDescription>
                    {locale === 'vi' ? 'Đề tài nghiên cứu chính thức của nhóm' : 'Official research topic for your group'}
                  </CardDescription>
                </div>
              </div>
              <StatusBadge status={currentGroup.approvalStatus} variant="approval" />
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-xl border border-border/80 bg-background/80 p-4 space-y-3">
              <h3 className="text-base font-bold text-foreground leading-snug">
                {topicTitle}
              </h3>
              {topicDescription && (
                <p className="text-xs text-muted-foreground line-clamp-3 leading-relaxed">
                  {topicDescription}
                </p>
              )}

              <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-border/60 text-xs">
                <span className="font-semibold text-muted-foreground flex items-center gap-1">
                  <GraduationCap className="h-3.5 w-3.5 text-primary" />
                  {locale === 'vi' ? 'Giảng viên hướng dẫn (GVHD):' : 'Advisor:'}
                </span>
                <span className="font-bold text-foreground">
                  {studentTopicSupervisors.length > 0
                    ? studentTopicSupervisors.join(', ')
                    : (locale === 'vi' ? 'Đang cập nhật' : 'TBD')}
                </span>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-3 pt-1">
              <LinkButton
                href={`/dashboard/thesis/topics/${currentGroup.topicId}`}
                variant="default"
                size="sm"
                className="gap-1.5"
              >
                <BookOpen className="h-4 w-4" />
                {locale === 'vi' ? 'Xem chi tiết đề tài' : 'View Topic Details'}
              </LinkButton>
              {isGroupLeader && currentGroup.approvalStatus !== 'APPROVED' && isRegistrationWindowOpen && (
                <LinkButton
                  href="/dashboard/thesis/topics"
                  variant="outline"
                  size="sm"
                  className="gap-1.5"
                >
                  {locale === 'vi' ? 'Đổi đề tài khác' : 'Change Topic'}
                </LinkButton>
              )}
            </div>
          </CardContent>
        </Card>
      );
    }

    return (
      <Card className="h-full">
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
              <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
            </div>
            <span className="rounded-md bg-amber-500/10 px-2.5 py-1 text-xs font-semibold text-amber-700 dark:text-amber-400 border border-amber-500/20">
              {locale === 'vi' ? 'Chưa đăng ký đề tài' : 'No Topic Selected'}
            </span>
          </div>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col items-start gap-4 rounded-xl border border-dashed border-border/80 bg-muted/30 p-5">
            <div className="space-y-1.5">
              <h4 className="text-sm font-bold text-foreground">
                {locale === 'vi' ? 'Bước tiếp theo: Chọn GVHD & Đề tài' : 'Next Step: Choose Advisor & Topic'}
              </h4>
              <p className="text-xs leading-relaxed text-muted-foreground">
                {messages.thesis.openCatalogHint}
              </p>
            </div>
            <LinkButton href="/dashboard/thesis/topics" variant="default" className="gap-2">
              <GraduationCap className="h-4 w-4" />
              {locale === 'vi' ? 'Chọn GVHD & Đề tài khóa luận' : 'Select Advisor & Topic'}
            </LinkButton>
          </div>
        </CardContent>
      </Card>
    );
  };

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.eyebrow}</SectionEyebrow>}
        title={messages.thesis.title}
        tabLabel={messages.thesis.eyebrow}
        description={messages.thesis.description}
        actions={
          <div className="flex flex-wrap items-end gap-3">
            {canProposeTopics && selectedRound ? (
              <Button
                type="button"
                onClick={() => {
                  if (user?.lecturerId && !proposeSupervisorId) {
                    setProposeSupervisorId(user.lecturerId);
                  }
                  const currentIsProposalOpen = selectedRound?.status === 'PROPOSAL_OPEN';
                  const openProposalRound = rounds.find((r) => r.status === 'PROPOSAL_OPEN');
                  const targetRound = currentIsProposalOpen
                    ? selectedRoundId
                    : (openProposalRound?.id || selectedRoundId);
                  setProposeRoundId(targetRound);
                  setIsProposeModalOpen(true);
                }}
                disabled={isActionPending}
                className="h-11"
              >
                <Plus className="mr-2 h-4 w-4" />
                {messages.thesis.proposeTopic}
              </Button>
            ) : null}
            <div className="min-w-[15rem]">
              <Select
                label={messages.thesis.selectRound}
                value={selectedRoundId}
                onChange={(event) => {
                  hasAutoSelectedStudentRoundRef.current = true;
                  hasAutoSelectedRoundRef.current = true;
                  selectedRoundRef.current = event.target.value;
                  setSelectedRoundId(event.target.value);
                }}
                aria-label={messages.thesis.selectRound}
                groups={roundGroups}
                options={roundsForPicker(rounds, selectedRoundId).map((round) => ({
                  value: round.id,
                  label: round.name,
                }))}
              />
            </div>
          </div>
        }
      />

      {!selectedRound ? (
        <EmptyState
          icon={FileStack}
          title={messages.thesis.noRound}
          description={messages.thesis.noTopicsDescription}
        />
      ) : (
        <>
          <RoundMilestoneCard
            round={selectedRound}
            formatDateTime={formatDateTime}
            statusLabel={statusLabel}
          />

          <ThesisWorkflowStepper
            round={selectedRound}
            formatDateTime={formatDateTime}
          />

          <ThesisRegulationGuide />

          {error ? <ErrorState title={messages.thesis.loadFailed} description={error} /> : null}
          {workloadError ? (
            <div
              role="status"
              className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-700 dark:text-amber-400 shadow-xs"
            >
              {workloadError}
            </div>
          ) : null}
          {secondaryDataError ? (
            <div
              role="status"
              className="rounded-xl border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-700 dark:text-amber-400 shadow-xs"
            >
              {messages.thesis.secondaryLoadFailed}
            </div>
          ) : null}
          {actionError ? (
            <div className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive shadow-xs">
              {actionError}
            </div>
          ) : null}
          {actionSuccess ? (
            <div className="rounded-xl border border-status-success/30 bg-status-success/10 px-4 py-3 text-sm text-status-success-foreground shadow-xs">
              {actionSuccess}
            </div>
          ) : null}

          {/* F3 fallback: the membership round never silently disappears —
              a persistent banner points back to it whenever the student is
              viewing a different round. */}
          {membershipRoundId && membershipRoundId !== selectedRoundId && !currentGroup ? (
            <div
              role="status"
              className="flex flex-col gap-3 rounded-xl border border-primary/30 bg-primary/[0.06] px-4 py-3 text-sm text-foreground shadow-xs sm:flex-row sm:items-center sm:justify-between"
            >
              <span className="flex items-start gap-2">
                <Info className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                <span>
                  {fillCopy(messages.thesis.membershipRoundHint, {
                    round: rounds.find((round) => round.id === membershipRoundId)?.name ?? '',
                  })}
                </span>
              </span>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="shrink-0"
                onClick={() => setSelectedRoundId(membershipRoundId)}
              >
                {messages.thesis.membershipRoundOpen}
              </Button>
            </div>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <WorkspaceMetricCard label={messages.thesis.roundStatus} value={statusLabel(selectedRound.status)} icon={<CalendarDays className="h-5 w-5" />} toneClassName={metricToneClass('warning')} />
            <WorkspaceMetricCard label={messages.thesis.topics} value={topics.length} icon={<FileStack className="h-5 w-5" />} toneClassName={metricToneClass('info')} />
            {canLoadStaffRelations || isStudent ? (
              <WorkspaceMetricCard label={messages.thesis.groups} value={groups.length} icon={<UsersRound className="h-5 w-5" />} toneClassName={metricToneClass('success')} />
            ) : null}
            {isStudent ? (
              <WorkspaceMetricCard label={messages.thesis.groupMembersMetric} value={currentGroup ? currentGroup.memberStudentIds.length : 0} icon={<Check className="h-5 w-5" />} toneClassName={metricToneClass('neutral')} />
            ) : null}
          </div>

          {canLoadStaffRelations || isStudent ? (
          <Card variant="muted">
            <CardHeader>
              <CardTitle>{messages.thesis.lifecycleViewsTitle}</CardTitle>
              <CardDescription>{messages.thesis.lifecycleViewsDescription}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-3">
              {([
                ['catalog', '/dashboard/thesis/topics'],
                // The advisor directory reads the staff-only faculty directory
                // (STUDENT is pinned to 403 by PeopleRead boundary tests), so
                // students see the catalog and progress cards only.
                ...(canLoadStaffRelations ? ([['advisors', '/dashboard/thesis/advisors']] as const) : []),
                // The progress view is a student-only surface; staff roles
                // would hit its forbidden state.
                ...(isStudent ? ([['progress', '/dashboard/thesis/progress']] as const) : []),
              ] as const).map(([key, href]) => (
                <LocalizedLink
                  key={key}
                  href={href}
                  className="group rounded-lg border border-border/70 bg-card p-4 transition-colors hover:border-primary/50 hover:bg-primary/[0.025] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <div className="flex items-center justify-between gap-3">
                    <span className="font-semibold text-foreground">{messages.thesis.navigation[key]}</span>
                    <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                  </div>
                </LocalizedLink>
              ))}
            </CardContent>
          </Card>
          ) : null}

          {/* Lecturer & Supervisor Review Section */}
          {isSupervisorOrAdmin ? (
            <div className="space-y-6">
              {/* Tab Navigation for Lecturer */}
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-3">
                <div className="flex flex-wrap items-center gap-2" role="tablist" aria-label={pageCopy.tabTopicsAndSupervisors}>
                  <button
                    ref={lecturerTabs.tabRef('supervision')}
                    type="button"
                    role="tab"
                    aria-selected={lecturerTab === 'supervision'}
                    tabIndex={lecturerTabs.tabIndex('supervision')}
                    onKeyDown={(event) => lecturerTabs.onKeyDown(event, 0)}
                    onClick={() => chooseLecturerTab('supervision')}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'supervision'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <BookOpen className="h-4 w-4" />
                    <span>{canLoadStaffRelations ? pageCopy.tabTopicsAndSupervisors : messages.thesis.topicApprovalTitle}</span>
                    <span
                      className={cn(
                        'rounded-md px-2 py-0.5 text-[11px] font-mono font-bold',
                        lecturerTab === 'supervision'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {canLoadStaffRelations
                        ? fillCopy(pageCopy.groupsCount, { count: supervisedGroups.length })
                        : managedTopics.length}
                    </span>
                  </button>
                  {canLoadStaffRelations ? (
                  <>
                  <button
                    ref={lecturerTabs.tabRef('defense')}
                    type="button"
                    role="tab"
                    aria-selected={lecturerTab === 'defense'}
                    tabIndex={lecturerTabs.tabIndex('defense')}
                    onKeyDown={(event) => lecturerTabs.onKeyDown(event, 1)}
                    onClick={() => chooseLecturerTab('defense')}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'defense'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <GraduationCap className="h-4 w-4" />
                    <span>{pageCopy.tabCouncil}</span>
                    <span
                      className={cn(
                        'rounded-md px-2 py-0.5 text-[11px] font-mono font-bold',
                        lecturerTab === 'defense'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {fillCopy(pageCopy.councilsCount, { count: visibleCouncils.length })}
                    </span>
                  </button>
                  <button
                    ref={lecturerTabs.tabRef('repository')}
                    type="button"
                    role="tab"
                    aria-selected={lecturerTab === 'repository'}
                    tabIndex={lecturerTabs.tabIndex('repository')}
                    onKeyDown={(event) => lecturerTabs.onKeyDown(event, 2)}
                    onClick={() => chooseLecturerTab('repository')}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'repository'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <Archive className="h-4 w-4" />
                    <span>{pageCopy.tabRepository}</span>
                    <span
                      className={cn(
                        'rounded-md px-2 py-0.5 text-[11px] font-mono font-bold',
                        lecturerTab === 'repository'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {fillCopy(pageCopy.reportsCount, { count: repositoryReports.length })}
                    </span>
                  </button>
                  </>
                  ) : null}
                </div>
                <span className="text-xs text-muted-foreground font-medium">
                    {pageCopy.workspaceAreaLabel} <strong className="text-primary">{isAdmin ? pageCopy.roleAdmin : isDepartmentHead ? messages.thesis.departmentHeadRole : pageCopy.roleLecturer}</strong> {pageCopy.facultySuffix}
                </span>
              </div>

              {lecturerTab === 'supervision' || !canLoadStaffRelations ? (
                <div className="space-y-6">
                  <Card>
                    <CardHeader>
                      <CardTitle>{isTopicApprover ? messages.thesis.topicApprovalTitle : messages.thesis.myProposedTopics}</CardTitle>
                      <CardDescription>{isTopicApprover ? messages.thesis.topicApprovalDescription : messages.thesis.myProposedTopicsDescription}</CardDescription>
                    </CardHeader>
                    <CardContent>
                      {managedTopics.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {isTopicApprover ? messages.thesis.noTopicsForApproval : messages.thesis.noProposedTopics}
                        </p>
                      ) : (
                        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                          {managedTopics.map((topic) => (
                            <div key={topic.id} className="flex flex-col gap-3 rounded-lg border border-border/70 bg-card p-4">
                              <h3 className="line-clamp-2 text-sm font-semibold text-foreground" title={topic.title}>{topic.title}</h3>
                              <span className={cn(
                                'w-fit rounded-md border px-2 py-1 text-xs font-medium',
                                topic.status === 'DRAFT'
                                  ? 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300'
                                  : 'border-status-success/30 bg-status-success/10 text-status-success-foreground',
                              )}>
                                {topic.status === 'DRAFT' ? messages.thesis.pendingTopicApproval : statusLabel(topic.status)}
                              </span>
                              <p className="text-xs text-muted-foreground">
                                {getLocalizedName(locale, departments.find((department) => department.id === topic.departmentId), topic.departmentId)}
                                {' · '}{topic.maxGroups} {messages.thesis.groups.toLowerCase()}
                              </p>
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                className="mt-auto w-fit"
                                disabled={isActionPending}
                                onClick={() => {
                                  setTopicActionError('');
                                  setViewingTopic(topic);
                                }}
                                aria-label={`${messages.thesis.viewTopic}: ${topic.title}`}
                              >
                                {messages.thesis.viewTopic}
                              </Button>
                            </div>
                          ))}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                  {canLoadStaffRelations ? (
                  <SupervisedGroupList
                    messages={messages}
                    title={messages.thesis.supervisedGroupsTitle}
                    description={messages.thesis.supervisedGroupsDescription}
                    groups={supervisedGroups}
                    reportsByGroup={supervisedReports}
                    isActionPending={isActionPending}
                    getTopicTitle={getTopicTitle}
                    isRoundOpen={isRegistrationOpenFor}
                    canReviewGroup={(group) =>
                      isAdmin || (!!group.topicId && supervisedTopicIds.has(group.topicId))
                    }
                    onGroupUpdated={(next) =>
                      setGroups((current) =>
                        current.map((item) => (item.id === next.id ? next : item)),
                      )
                    }
                    onApprove={approveGroup}
                    onReject={openRejectModal}
                    minGroupSize={selectedRound?.minGroupSize ?? null}
                    canSizeException={isAdmin}
                    onApproveWithException={isAdmin ? openApproveExceptionModal : undefined}
                    onWithdrawApproval={openWithdrawModal}
                    onDownloadReport={downloadReportArtifact}
                  />
                  ) : null}
                </div>
              ) : lecturerTab === 'defense' ? (
                <div className="space-y-6">
                  {/* GVPB counter-review sits outside council membership: the
                      assigned reviewer is usually not on the council. */}
                  <GvpbReviewPanel
                    messages={messages}
                    reviewTopics={myReviewTopics}
                    round={selectedRound}
                    gvpbScores={gvpbScores}
                    draftScores={gvpbDraftScores}
                    draftComments={gvpbDraftComments}
                    isActionPending={isActionPending}
                    formatDateTime={formatDateTime}
                    onScoreDraftChange={(topicId, value) =>
                      setGvpbDraftScores((prev) => ({ ...prev, [topicId]: value }))
                    }
                    onCommentDraftChange={(topicId, value) =>
                      setGvpbDraftComments((prev) => ({ ...prev, [topicId]: value }))
                    }
                    onSubmit={handleSubmitReviewerScore}
                  />
                  <CouncilDefenseWorkspace
                    messages={messages}
                    round={selectedRound}
                    councils={visibleCouncils}
                    myLecturerId={myLecturerId}
                    topics={topics}
                    lecturers={lecturers}
                    councilLoadError={councilLoadError}
                    onRetryCouncils={() => void reloadCouncils()}
                    councilScores={councilScores}
                    councilScoreErrors={councilScoreErrors}
                    topicSupervisors={topicSupervisors}
                    topicReports={topicReports}
                    topicReportErrors={topicReportErrors}
                    onRetryTopicReport={async (topicId) => {
                      try {
                        const report = await thesisApi.getTopicReport(topicId);
                        setTopicReports((prev) => ({ ...prev, [topicId]: report }));
                        setTopicReportErrors((prev) => ({ ...prev, [topicId]: false }));
                      } catch {
                        setTopicReports((prev) => ({ ...prev, [topicId]: null }));
                        setTopicReportErrors((prev) => ({ ...prev, [topicId]: true }));
                      }
                    }}
                    draftScores={draftScores}
                    draftComments={draftComments}
                    isActionPending={isActionPending}
                    showProfileClaimNotice={isLecturer && !isAdmin && !user?.lecturerId}
                    profileClaimMissingLabel={messages.thesis.councils.profileClaimMissing}
                    onScoreDraftChange={(key, value) =>
                      setDraftScores((prev) => ({ ...prev, [key]: value }))
                    }
                    onCommentDraftChange={(key, value) =>
                      setDraftComments((prev) => ({ ...prev, [key]: value }))
                    }
                    onSubmitScore={handleSubmitScore}
                    onFinalizeScore={handleFinalizeScore}
                    onDownloadReport={downloadReportArtifact}
                  />
                </div>
              ) : (
                <ThesisRepositoryWorkspace
                  messages={messages}
                  locale={locale}
                  formatDateTime={formatDateTime}
                  reports={repositoryReports}
                  filteredReports={filteredRepositoryReports}
                  roundName={selectedRound?.name}
                  isLoading={isRepositoryLoading}
                  loadError={repositoryError}
                  onRetry={() => void reloadRepository()}
                  search={repositorySearch}
                  onSearchChange={setRepositorySearch}
                  onDownloadReport={handleDownloadRepositoryReport}
                />
              )}
        </div>
      ) : null}

                    {/* Student Guidance Header Alert & Student Workspace */}
          {(!isSupervisorOrAdmin || currentGroup) && (
            <div className="space-y-6">
              <div className="rounded-xl border border-primary/20 bg-primary/[0.03] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-2xs">
                <div className="flex items-start gap-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary mt-0.5">
                    <GraduationCap className="h-5 w-5" />
                  </div>
                  <div className="space-y-0.5">
                    <h4 className="text-sm font-bold text-foreground">
                      {pageCopy.workspaceTitle}
                    </h4>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {pageCopy.workspaceSubtitle}
                    </p>
                  </div>
                </div>
                {currentGroup && (
                  <div className="shrink-0 flex items-center gap-2">
                    <span className="rounded-md bg-background px-3 py-1 text-xs font-semibold text-foreground border border-border/80 shadow-2xs">
                        {pageCopy.yourRoleLabel} {isGroupLeader ? pageCopy.roleGroupLeader : pageCopy.roleMember}
                    </span>
                  </div>
                )}
              </div>

              {isStudent && selectedRound.eligibility ? (() => {
                const eligibility = selectedRound.eligibility;
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
              })() : null}

              {studentFinalScore != null && studentGradeInfo && (
            <div className="rounded-2xl border border-emerald-500/30 bg-gradient-to-r from-emerald-500/[0.06] via-card to-card p-5 sm:p-6 shadow-xs">
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 pb-4">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border border-emerald-500/30">
                    <Award className="h-5 w-5" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="rounded-md bg-emerald-500/15 px-2.5 py-0.5 text-xs font-bold text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
                        {pageCopy.defenceCompleted}
                      </span>
                      <span className="text-xs font-semibold text-muted-foreground">
                        {pageCopy.articlesR7R9}
                      </span>
                    </div>
                    <h3 className="mt-1 text-base font-bold text-foreground sm:text-lg">
                      {pageCopy.resultsHeading}
                    </h3>
                  </div>
                </div>
                <span className="text-xs font-medium text-muted-foreground">
                      {pageCopy.publishedByCouncil}
                </span>
              </div>

              <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-4">
                <div className="rounded-xl border border-emerald-500/30 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    {pageCopy.defenceScoreTenScale}
                  </p>
                  <p className="mt-1 font-mono text-3xl font-extrabold text-emerald-600 dark:text-emerald-400">
                    {studentFinalScore.toFixed(2)}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {pageCopy.arithmeticMeanR7}
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    {pageCopy.letterGradeLabel}
                  </p>
                  <p className="mt-1 font-mono text-3xl font-extrabold text-primary">
                    {studentGradeInfo.letter}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {fillCopy(pageCopy.gpaConversion, { gpa: studentGradeInfo.gpa4 })}
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    {pageCopy.graduationClassification}
                  </p>
                  <p className="mt-2 text-xl font-bold text-foreground">
                    {pageCopy.classification[studentGradeInfo.band]}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {pageCopy.creditScaleNote}
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    {pageCopy.evaluationCouncil}
                  </p>
                  <p className="mt-2 text-sm font-bold text-foreground truncate" title={myResultItem?.councilName || pageCopy.councilNameFallback}>
                    {myResultItem?.councilName || pageCopy.councilNameFallback}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    {pageCopy.universityName}
                  </p>
                </div>
              </div>
            </div>
          )}

              {/* 2-Column Grid for Student */}
              <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)]">
                <StudentGroupCard
                  messages={messages}
                  group={currentGroup ?? null}
                  roundCancelled={selectedRound.status === 'CANCELLED'}
                  groupStatusLabel={groupStatusLabel}
                  roleLabel={isGroupLeader ? pageCopy.roleGroupLeaderReport : pageCopy.roleMember}
                  topicTitle={currentGroup?.topicId ? getTopicTitle(currentGroup.topicId) : null}
                  supervisors={studentTopicSupervisors}
                  members={groupMemberRows}
                  canCreateGroup={isStudent && isRegistrationWindowOpen
                    && !(selectedRound.eligibility?.eligible === false
                      && selectedRound.eligibility?.enforced === true)}
                  canManageMembers={canManageMembers}
                  isActionPending={isActionPending}
                  onCreateGroup={() => void createGroup()}
                  onAddMember={() => {
                    resetStudentSearch();
                    setIsAddMemberModalOpen(true);
                  }}
                  onRemoveMember={handleRemoveMember}
                  reportSection={studentReportSection}
                  submissionStatusSection={studentSubmissionStatus}
                />
                {renderTopicsCard()}
              </div>
            </div>
          )}

          {/* Round results (published by the coordinator) */}
          {resultsState === 'notPublished' || resultsState === 'ready' ? (
            <Card variant="muted">
              <CardHeader>
                <CardTitle>{messages.thesis.results.title}</CardTitle>
                <CardDescription>{messages.thesis.results.description}</CardDescription>
              </CardHeader>
              <CardContent>
                {resultsState === 'notPublished' ? (
                  <p className="text-sm text-muted-foreground">
                    {messages.thesis.results.notPublished}
                  </p>
                ) : roundResults.length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {messages.thesis.results.noResults}
                  </p>
                ) : (
                  <div className="space-y-2">
                    {roundResults.map((result) => (
                      <div
                        key={result.groupId}
                        className="flex flex-col gap-2 rounded-xl border border-border/70 bg-card p-4 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm font-semibold text-foreground" title={result.topicTitle}>
                            {result.topicTitle}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {messages.thesis.results.council}: {result.councilName}
                          </p>
                          {result.resultStatus === 'REVIEW_FAILED' ? (
                            <p className="text-xs text-muted-foreground">
                              {messages.thesis.results.notEligible}
                            </p>
                          ) : result.resultStatus === 'WITHDRAWN' ? (
                            <p className="text-xs text-muted-foreground">
                              {messages.thesis.results.withdrawn}
                            </p>
                          ) : null}
                        </div>
                        <div className="text-left sm:text-right">
                          {result.resultStatus === 'REVIEW_FAILED' || result.resultStatus === 'WITHDRAWN' ? (
                            <p className="text-sm font-medium text-muted-foreground">
                              {result.resultStatus === 'REVIEW_FAILED'
                                ? messages.thesis.results.notEligible
                                : messages.thesis.results.withdrawn}
                            </p>
                          ) : (
                            <>
                              <p className="text-lg font-semibold text-foreground">
                                {result.finalScore}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {messages.thesis.results.finalScore}
                              </p>
                            </>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          ) : null}
        </>
      )}

      {/* Propose Topic Modal */}
      <Modal
        isOpen={isProposeModalOpen && canProposeTopics}
        onClose={() => {
          if (!isActionPending) setIsProposeModalOpen(false);
        }}
        title={messages.thesis.proposeTopicTitle}
      >
        <form onSubmit={(e) => void handleProposeTopic(e)} className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.proposeTopicDescription}
          </p>
          {rounds.length > 1 ? (
            <div>
              <Select
                label={messages.thesis.selectRound}
                value={proposeRoundId || selectedRoundId}
                onChange={(e) => setProposeRoundId(e.target.value)}
                disabled={isActionPending}
                options={rounds.map((round) => ({
                  value: round.id,
                  label: `${round.name} (${
                    messages.thesis.status[
                      round.status as keyof typeof messages.thesis.status
                    ] ?? round.status
                  })`,
                }))}
              />
            </div>
          ) : null}
          {(() => {
            const currentProposeRound =
              rounds.find((r) => r.id === (proposeRoundId || selectedRoundId)) || selectedRound;
            const isProposalOpen = currentProposeRound?.status === 'PROPOSAL_OPEN';
            if (isProposalOpen) {
              return (
                <div className="rounded-md border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700 dark:text-emerald-300">
                  <div className="font-semibold">{messages.thesis.proposeRoundOpenNotice}</div>
                  {currentProposeRound?.lecturerSubmitStart && currentProposeRound?.lecturerSubmitEnd ? (
                    <div className="mt-1 text-xs opacity-90">
                      {messages.thesis.proposalWindow}: {formatDateTime(currentProposeRound.lecturerSubmitStart)} → {formatDateTime(currentProposeRound.lecturerSubmitEnd)}
                    </div>
                  ) : null}
                </div>
              );
            }
            return (
              <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300">
                {messages.thesis.roundNotAcceptingProposals} ({currentProposeRound?.name}: {currentProposeRound?.status})
              </div>
            );
          })()}
          {proposeError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {proposeError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-propose-title" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicTitleLabel}
            </label>
            <Input
              id="thesis-propose-title"
              value={proposeTitle}
              onChange={(e) => setProposeTitle(e.target.value)}
              placeholder={messages.thesis.topicTitleLabel}
              required
              disabled={isActionPending}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
            <div>
              {scopedDepartments.length > 0 ? (
                <Select
                  label={messages.thesis.topicDepartmentLabel}
                  value={proposeDepartmentId}
                  onChange={(e) => setProposeDepartmentId(e.target.value)}
                  required
                  disabled={isActionPending}
                  options={scopedDepartments.map((dept) => ({
                    value: dept.id,
                    label: `${dept.code} - ${getLocalizedName(locale, dept, dept.name)}`,
                  }))}
                />
              ) : (
                <div>
                  <label htmlFor="thesis-propose-dept" className="mb-1 block text-sm font-medium text-foreground">
                    {messages.thesis.topicDepartmentLabel}
                  </label>
                  <Input
                    id="thesis-propose-dept"
                    value={proposeDepartmentId}
                    onChange={(e) => setProposeDepartmentId(e.target.value)}
                    placeholder={departments[0]?.id || 'FIT'}
                    required
                    disabled={isActionPending}
                  />
                </div>
              )}
            </div>
            <div>
              <label htmlFor="thesis-propose-max-groups" className="mb-1 block text-sm font-medium text-foreground">
                {messages.thesis.topicMaxGroupsLabel} (1-20)
              </label>
              <Input
                id="thesis-propose-max-groups"
                type="number"
                min={1}
                max={20}
                value={proposeMaxGroups}
                onChange={(e) => setProposeMaxGroups(Number(e.target.value) || 1)}
                required
                disabled={isActionPending}
              />
            </div>
          </div>
          {lecturersError ? (
            // An outage must not read as "no supervisors exist": the reader
            // gets the real failure and a retry.
            <ErrorState
              title={locale === 'vi' ? 'Không thể tải danh sách giảng viên' : 'Supervisor directory unavailable'}
              description={lecturersError}
              onRetry={() => void loadLecturers()}
            />
          ) : lecturers.length > 0 ? (
            <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
              <Combobox
                label={messages.thesis.supervisorPrimaryLabel}
                value={proposeSupervisorId}
                onChange={(next) => setProposeSupervisorId(next)}
                disabled={isActionPending}
                allowClear
                emptyLabel={messages.thesis.supervisorNoneOption}
                options={lecturers.map((lecturer) => {
                  const duplicated =
                    proposeSecondSupervisorId !== '' && lecturer.id === proposeSecondSupervisorId;
                  return {
                    value: lecturer.id,
                    label: getLecturerLabel(lecturer),
                    description: [lecturer.department?.name, lecturer.employeeId]
                      .filter(Boolean)
                      .join(' · '),
                    keywords: [lecturer.employeeId, lecturer.user?.email]
                      .filter(Boolean)
                      .join(' '),
                    disabled: duplicated,
                    disabledReason: duplicated
                      ? messages.thesis.supervisorAlreadySelected
                      : undefined,
                  };
                })}
              />
              <Combobox
                label={messages.thesis.supervisorSecondaryLabel}
                value={proposeSecondSupervisorId}
                onChange={(next) => setProposeSecondSupervisorId(next)}
                disabled={isActionPending}
                allowClear
                emptyLabel={messages.thesis.supervisorNoneOption}
                options={lecturers.map((lecturer) => {
                  const duplicated =
                    proposeSupervisorId !== '' && lecturer.id === proposeSupervisorId;
                  return {
                    value: lecturer.id,
                    label: getLecturerLabel(lecturer),
                    description: [lecturer.department?.name, lecturer.employeeId]
                      .filter(Boolean)
                      .join(' · '),
                    keywords: [lecturer.employeeId, lecturer.user?.email]
                      .filter(Boolean)
                      .join(' '),
                    disabled: duplicated,
                    disabledReason: duplicated
                      ? messages.thesis.supervisorAlreadySelected
                      : undefined,
                  };
                })}
              />
            </div>
          ) : (
            <EmptyState
              title={locale === 'vi' ? 'Chưa có giảng viên hướng dẫn' : 'No supervisors listed yet'}
              description={
                locale === 'vi'
                  ? 'Nhà trường chưa công bố giảng viên nào trong danh sách hướng dẫn. Vui lòng liên hệ Khoa để được hỗ trợ.'
                  : 'The faculty directory lists no supervisors yet. Contact your department office for assistance.'
              }
            />
          )}
          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicDescriptionLabel}
            </label>
            <RichTextEditor
              id="thesis-propose-desc"
              aria-label={messages.thesis.topicDescriptionLabel}
              value={proposeDescription}
              onChange={setProposeDescription}
              placeholder={messages.thesis.topicDescriptionLabel}
              minHeight="140px"
              disabled={isActionPending}
              locale={locale}
              showTemplates={true}
            />
          </div>
          {isTopicApprover ? (
          <div className="space-y-2">
          <label className="flex cursor-pointer items-center gap-2 text-sm text-foreground">
            <input
              type="checkbox"
              checked={proposePublishImmediately}
              onChange={(e) => setProposePublishImmediately(e.target.checked)}
              disabled={isActionPending || !proposeSupervisorId ||
                rounds.find((round) => round.id === (proposeRoundId || selectedRoundId))?.status !== 'PROPOSAL_OPEN'}
              className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
            />
            <span>{messages.thesis.publishImmediately}</span>
          </label>
          {!proposeSupervisorId ? (
            <p className="text-xs text-muted-foreground">{messages.thesis.publishAfterSupervisors}</p>
          ) : null}
          </div>
          ) : (
            <p className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300">
              {messages.thesis.pendingTopicApprovalNotice}
            </p>
          )}
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsProposeModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="submit"
              disabled={
                isActionPending ||
                (!isAdmin &&
                  (rounds.find((r) => r.id === (proposeRoundId || selectedRoundId)) || selectedRound)?.status !==
                    'PROPOSAL_OPEN')
              }
            >
              {isActionPending ? messages.common.states.loading : messages.thesis.proposeTopic}
            </Button>
          </div>
        </form>
      </Modal>

      {/* Reject Group Modal */}
      <Modal
        isOpen={isRejectModalOpen}
        onClose={() => {
          if (!isActionPending) setIsRejectModalOpen(false);
        }}
        title={messages.thesis.rejectModalTitle}
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.rejectModalDescription}
          </p>
          {rejectError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {rejectError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-reject-reason" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.rejectionReason}
            </label>
            <Textarea
              id="thesis-reject-reason"
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder={messages.thesis.rejectionReasonPlaceholder}
              maxLength={500}
              rows={4}
              disabled={isActionPending}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsRejectModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => void confirmRejectGroup()}
              disabled={isActionPending}
            >
              {isActionPending ? messages.common.states.loading : messages.thesis.reject}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Under-size Approval Exception Modal (governance only) */}
      <Modal
        isOpen={isApproveExceptionModalOpen}
        onClose={() => {
          if (!isActionPending) setIsApproveExceptionModalOpen(false);
        }}
        title={messages.thesis.approveExceptionModalTitle}
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.approveExceptionModalDescription}
          </p>
          {approveExceptionError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {approveExceptionError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-size-exception-reason" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.sizeExceptionReason}
            </label>
            <Textarea
              id="thesis-size-exception-reason"
              value={approveExceptionReason}
              onChange={(e) => setApproveExceptionReason(e.target.value)}
              placeholder={messages.thesis.sizeExceptionReasonPlaceholder}
              maxLength={500}
              rows={4}
              disabled={isActionPending}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsApproveExceptionModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              className="bg-emerald-700 text-white hover:bg-emerald-800"
              onClick={() => void confirmApproveException()}
              disabled={isActionPending}
            >
              {isActionPending ? messages.common.states.loading : messages.thesis.approveWithException}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Governance Override Approval Modal (non-supervisor approver) */}
      <Modal
        isOpen={isApproveOverrideModalOpen}
        onClose={() => {
          if (!isActionPending) setIsApproveOverrideModalOpen(false);
        }}
        title={messages.thesis.approveOverrideModalTitle}
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.approveOverrideModalDescription}
          </p>
          {approveOverrideError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {approveOverrideError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-override-reason" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.rejectionReason}
            </label>
            <Textarea
              id="thesis-override-reason"
              value={approveOverrideReason}
              onChange={(e) => setApproveOverrideReason(e.target.value)}
              placeholder={messages.thesis.rejectionReasonPlaceholder}
              maxLength={500}
              rows={4}
              disabled={isActionPending}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsApproveOverrideModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              className="bg-emerald-700 text-white hover:bg-emerald-800"
              onClick={() => void confirmApproveOverride()}
              disabled={isActionPending}
            >
              {isActionPending ? messages.common.states.loading : messages.thesis.approve}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Withdraw Approval Modal */}
      <Modal
        isOpen={isWithdrawModalOpen}
        onClose={() => {
          if (!isActionPending) setIsWithdrawModalOpen(false);
        }}
        title={messages.thesis.withdrawApprovalModalTitle}
      >
        <div className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.withdrawApprovalModalDescription}
          </p>
          {withdrawError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {withdrawError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-withdraw-reason" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.withdrawApprovalReason}
            </label>
            <Textarea
              id="thesis-withdraw-reason"
              value={withdrawReason}
              onChange={(e) => setWithdrawReason(e.target.value)}
              placeholder={messages.thesis.withdrawApprovalReasonPlaceholder}
              maxLength={500}
              rows={4}
              disabled={isActionPending}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsWithdrawModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button
              type="button"
              variant="destructive"
              onClick={() => void confirmWithdrawApproval()}
              disabled={isActionPending}
            >
              {isActionPending ? messages.common.states.loading : messages.thesis.withdrawApproval}
            </Button>
          </div>
        </div>
      </Modal>

      {/* Add Group Member Modal */}
      <Modal
        isOpen={isAddMemberModalOpen}
        onClose={() => {
          if (!isActionPending) setIsAddMemberModalOpen(false);
        }}
        title={messages.thesis.addMemberModalTitle}
        description={messages.thesis.addMemberModalDescription}
      >
        <div className="space-y-4">
          {addMemberError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {addMemberError}
            </div>
          ) : null}

          {/* Type switcher: Internal (search) vs External / Cross-major */}
          <div className="flex rounded-lg border border-border/80 bg-muted/40 p-1 text-xs font-medium">
            <button
              type="button"
              aria-pressed={memberType === 'internal'}
              onClick={() => {
                setMemberType('internal');
                resetStudentSearch();
              }}
              className={cn(
                'flex-1 rounded-md py-1.5 px-3 text-center transition-colors',
                memberType === 'internal'
                  ? 'bg-background text-foreground shadow-xs font-semibold'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {messages.thesis.memberTypeInternal}
            </button>
            <button
              type="button"
              aria-pressed={memberType === 'external'}
              onClick={() => {
                setMemberType('external');
                resetStudentSearch();
              }}
              className={cn(
                'flex-1 rounded-md py-1.5 px-3 text-center transition-colors',
                memberType === 'external'
                  ? 'bg-background text-foreground shadow-xs font-semibold'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {messages.thesis.memberTypeExternal}
            </button>
          </div>

          {memberType === 'internal' ? (
            <div className="space-y-3">
              <label className="block text-sm font-medium text-foreground" htmlFor="thesis-student-search">
                {memberCopy.searchLabel}
              </label>
              <div className="flex gap-2">
                <Input
                  id="thesis-student-search"
                  value={studentQuery}
                  onChange={(e) => setStudentQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      void handleSearchStudents();
                    }
                  }}
                  placeholder={memberCopy.searchPlaceholder}
                  disabled={isActionPending || isSearching}
                  className="h-10"
                />
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => void handleSearchStudents()}
                  disabled={isActionPending || isSearching}
                  className="h-10 shrink-0"
                >
                  {memberCopy.searchButton}
                </Button>
              </div>
              <p className="text-xs text-muted-foreground">{memberCopy.searchHint}</p>

              {isSearching ? (
                <p className="text-xs text-muted-foreground">{memberCopy.searching}</p>
              ) : hasSearched && studentResults.length === 0 ? (
                <p className="text-xs text-muted-foreground">{memberCopy.searchNoResults}</p>
              ) : null}

              {studentResults.length > 0 ? (
                <ul className="max-h-64 divide-y divide-border/60 overflow-y-auto rounded-lg border border-border/70 bg-card">
                  {studentResults.map((student) => {
                    const inGroup = groupMemberList.some(
                      (member) => member.studentId === student.studentId,
                    );
                    const fullName = `${student.lastName ?? ''} ${student.firstName ?? ''}`.trim();
                    const curriculumLabel = [student.curriculumCode, student.curriculumName]
                      .filter(Boolean)
                      .join(' - ');
                    return (
                      <li
                        key={student.studentId}
                        className="flex items-center justify-between gap-3 p-3"
                      >
                        <div className="min-w-0 space-y-0.5">
                          <p className="truncate text-sm font-semibold text-foreground" title={fullName}>{fullName}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            MSSV: <span className="font-mono">{student.studentNumber}</span>
                          </p>
                          {curriculumLabel ? (
                            <p className="truncate text-xs text-muted-foreground" title={curriculumLabel}>
                              {memberCopy.curriculumLabel}: {curriculumLabel}
                            </p>
                          ) : null}
                        </div>
                        <Button
                          type="button"
                          size="sm"
                          variant={inGroup ? 'ghost' : 'outline'}
                          onClick={() => void handleAddInternalMember(student)}
                          disabled={inGroup || isActionPending}
                          className="shrink-0"
                        >
                          {inGroup ? memberCopy.addedShort : memberCopy.addShort}
                        </Button>
                      </li>
                    );
                  })}
                </ul>
              ) : null}

              <div className="flex justify-end pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsAddMemberModalOpen(false)}
                  disabled={isActionPending}
                >
                  {messages.common.actions.cancel}
                </Button>
              </div>
            </div>
          ) : (
            <form onSubmit={handleAddMember} className="space-y-4">
              <div>
                <label
                  className="mb-1 block text-sm font-medium text-foreground"
                  htmlFor="thesis-external-member-name"
                >
                  {messages.thesis.memberNameLabel} <span className="text-destructive">*</span>
                </label>
                <Input
                  id="thesis-external-member-name"
                  value={memberName}
                  onChange={(e) => setMemberName(e.target.value)}
                  placeholder={messages.thesis.memberNamePlaceholder}
                  required
                  disabled={isActionPending}
                  className="h-10"
                />
              </div>
              <div>
                <label
                  className="mb-1 block text-sm font-medium text-foreground"
                  htmlFor="thesis-external-member-contact"
                >
                  {messages.thesis.memberContactLabel}
                </label>
                <Input
                  id="thesis-external-member-contact"
                  value={memberContact}
                  onChange={(e) => setMemberContact(e.target.value)}
                  placeholder={messages.thesis.memberContactPlaceholder}
                  disabled={isActionPending}
                  className="h-10"
                />
                <p className="mt-1 text-xs text-muted-foreground">
                  {locale === 'vi'
                    ? 'Áp dụng cho thành viên khác ngành hoặc đến từ trường đại học khác.'
                    : 'For members from another major or a different university.'}
                </p>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsAddMemberModalOpen(false)}
                  disabled={isActionPending}
                >
                  {messages.common.actions.cancel}
                </Button>
                <Button type="submit" disabled={isActionPending}>
                  {isActionPending ? messages.common.states.loading : messages.thesis.addMember}
                </Button>
              </div>
            </form>
          )}
        </div>
      </Modal>

      {/* App-standard confirmation dialog (member removal and other destructive actions). */}
      {confirmationDialog}

      {/* Topic Detail & Selection Modal */}
      {viewingTopic ? (
        <Modal
          isOpen={Boolean(viewingTopic) && !isArchiveConfirmationOpen}
          onClose={() => { if (!isActionPending) setViewingTopic(null); }}
          title={viewingTopic.title}
          description={`${viewingTopic.maxGroups} ${messages.thesis.groups.toLowerCase()} • ${viewingTopic.status === 'DRAFT' ? messages.thesis.pendingTopicApproval : statusLabel(viewingTopic.status)}`}
        >
          <div className="space-y-4">
            {topicActionError ? (
              <p role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
                {topicActionError}
              </p>
            ) : null}
            {viewingTopic.status === 'DRAFT' ? (
              <p className="rounded-md border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-300">
                {messages.thesis.pendingTopicApprovalNotice}
              </p>
            ) : null}
            {viewingTopic.departmentId ? (
              <div className="flex items-center gap-2 rounded-lg border border-border/60 bg-muted/40 p-2.5 text-xs text-muted-foreground">
                <Building2 className="h-4 w-4 shrink-0 text-primary" />
                <span>
                  <strong>{locale === 'vi' ? 'Đơn vị chuyên môn:' : 'Department:'}</strong>{' '}
                  {departments.find((d) => d.id === viewingTopic.departmentId)?.name || viewingTopic.departmentId}
                </span>
              </div>
            ) : null}

            <div className="rounded-lg border border-border/70 bg-card p-4 text-sm leading-relaxed text-foreground max-h-[50vh] overflow-y-auto">
              <h4 className="mb-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                {locale === 'vi' ? 'Mô tả & Yêu cầu học thuật' : 'Description & Academic Requirements'}
              </h4>
              <RichContentRenderer content={viewingTopic.description} />
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-3">
              {canLoadStaffRelations || isStudent ? (
              <LocalizedLink
                href={`/dashboard/thesis/topics/${viewingTopic.id}`}
                className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
              >
                <ExternalLink className="h-3.5 w-3.5" />
                {locale === 'vi' ? 'Mở trang chuyên sâu' : 'Open dedicated topic page'}
              </LocalizedLink>
              ) : null}

              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setViewingTopic(null)}
                  disabled={isActionPending}
                >
                  {locale === 'vi' ? 'Đóng' : 'Close'}
                </Button>

                {/* Governance approves a draft; ownership alone permits editing.
                    Student topic selection remains in the published catalog. */}
                {topicCapabilities(viewingTopic).canEdit ? (
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => openEditModal(viewingTopic)}
                      disabled={isActionPending}
                    >
                      {messages.thesis.editTopic}
                    </Button>
                ) : null}
                {topicCapabilities(viewingTopic).canPublish ? (
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => void handlePublishTopic(viewingTopic.id)}
                      disabled={isActionPending}
                    >
                      {isActionPending ? messages.common.states.loading : messages.thesis.approveTopic}
                    </Button>
                ) : null}
                {topicCapabilities(viewingTopic).canRequestArchive ? (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => void handleArchiveTopic(viewingTopic)}
                    disabled={isActionPending || !topicCapabilities(viewingTopic).canArchive}
                    title={!topicCapabilities(viewingTopic).canArchive ? messages.thesis.topicInUse : undefined}
                  >
                    <Archive className="mr-1.5 h-4 w-4" />
                    {isActionPending ? messages.common.states.loading : messages.thesis.archiveTopic}
                  </Button>
                ) : null}
              </div>
            </div>
            {topicCapabilities(viewingTopic).canRequestArchive && !topicCapabilities(viewingTopic).canArchive ? (
              <p className="text-xs text-muted-foreground">{messages.thesis.topicInUse}</p>
            ) : null}
          </div>
        </Modal>
      ) : null}

      {/* Edit Topic Modal */}
      <Modal
        isOpen={isEditModalOpen}
        onClose={() => {
          if (!isActionPending) setIsEditModalOpen(false);
        }}
        title={messages.thesis.editTopicTitle}
      >
        <form onSubmit={(e) => void handleEditTopic(e)} className="space-y-4">
          <p className="text-sm text-muted-foreground">
            {messages.thesis.editTopicDescription}
          </p>
          {editError ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              {editError}
            </div>
          ) : null}
          <div>
            <label htmlFor="thesis-edit-title" className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicTitleLabel}
            </label>
            <Input
              id="thesis-edit-title"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              placeholder={messages.thesis.topicTitleLabel}
              required
              disabled={isActionPending}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2 sm:items-end">
            <div>
              {scopedDepartments.length > 0 ? (
                <Select
                  label={messages.thesis.topicDepartmentLabel}
                  value={editDepartmentId}
                  onChange={(e) => setEditDepartmentId(e.target.value)}
                  required
                  disabled={isActionPending}
                  options={scopedDepartments.map((dept) => ({
                    value: dept.id,
                    label: `${dept.code} - ${getLocalizedName(locale, dept, dept.name)}`,
                  }))}
                />
              ) : (
                <div>
                  <label htmlFor="thesis-edit-dept" className="mb-1 block text-sm font-medium text-foreground">
                    {messages.thesis.topicDepartmentLabel}
                  </label>
                  <Input
                    id="thesis-edit-dept"
                    value={editDepartmentId}
                    onChange={(e) => setEditDepartmentId(e.target.value)}
                    placeholder={departments[0]?.id || 'FIT'}
                    required
                    disabled={isActionPending}
                  />
                </div>
              )}
            </div>
            <div>
              <label htmlFor="thesis-edit-max-groups" className="mb-1 block text-sm font-medium text-foreground">
                {messages.thesis.topicMaxGroupsLabel} (1-20)
              </label>
              <Input
                id="thesis-edit-max-groups"
                type="number"
                min={1}
                max={20}
                value={editMaxGroups}
                onChange={(e) => setEditMaxGroups(Number(e.target.value) || 1)}
                required
                disabled={isActionPending}
              />
            </div>
          </div>
          <div>
            <label className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicDescriptionLabel}
            </label>
            <RichTextEditor
              id="thesis-edit-desc"
              aria-label={messages.thesis.topicDescriptionLabel}
              value={editDescription}
              onChange={setEditDescription}
              placeholder={messages.thesis.topicDescriptionLabel}
              minHeight="140px"
              disabled={isActionPending}
              locale={locale}
              showTemplates={true}
            />
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsEditModalOpen(false)}
              disabled={isActionPending}
            >
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isActionPending}>
              {isActionPending ? messages.common.states.loading : messages.thesis.editTopic}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
