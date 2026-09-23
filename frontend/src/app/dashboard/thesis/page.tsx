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
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/ui/modal';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { ThesisWorkflowStepper } from '@/components/thesis/ThesisWorkflowStepper';
import { ThesisRegulationGuide } from '@/components/thesis/ThesisRegulationGuide';
import { RoundMilestoneCard } from '@/components/thesis/RoundMilestoneCard';
import CouncilDefenseWorkspace from '@/features/thesis/components/CouncilDefenseWorkspace';
import MetricCard, {
  fillCopy,
  formatReportFileSize,
  renderInlineBold,
} from '@/features/thesis/components/MetricCard';
import StudentGroupCard, {
  type StudentGroupMemberRow,
} from '@/features/thesis/components/StudentGroupCard';
import SubmissionStatusTimeline from '@/features/thesis/components/SubmissionStatusTimeline';
import SupervisedGroupList from '@/features/thesis/components/SupervisedGroupList';
import ThesisRepositoryWorkspace from '@/features/thesis/components/ThesisRepositoryWorkspace';
import { departmentsApi, lecturersApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import type { Department, Lecturer } from '@/types/api';
import {
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
  const { user, isStudent, isLecturer, isAdmin: isAdminRole, isFacultyHead } = useAuth();
  const isAdmin = Boolean(isAdminRole || isFacultyHead);
  const isSupervisorOrAdmin = Boolean(isLecturer || isAdmin);
  const { locale, formatDateTime, messages } = useI18n();
  const pageCopy = messages.thesisWorkflow.page;
  const searchParams = useSearchParams();
  const explicitRoundId = searchParams.get('roundId') || '';
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [viewingTopic, setViewingTopic] = useState<ThesisTopic | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isActionPending, setIsActionPending] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  // Scoped load failure for the lecturer workload: without it the member
  // panel silently disappears.
  const [workloadError, setWorkloadError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

  // Propose topic state
  const [isProposeModalOpen, setIsProposeModalOpen] = useState(false);
  const [proposeTitle, setProposeTitle] = useState('');
  const [proposeDescription, setProposeDescription] = useState('');
  const [proposeDepartmentId, setProposeDepartmentId] = useState('');
  const [proposeMaxGroups, setProposeMaxGroups] = useState(2);
  const [proposePublishImmediately, setProposePublishImmediately] = useState(true);
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

  // Group report state (approved groups only)
  const [groupReport, setGroupReport] = useState<ThesisGroupReport | null>(null);
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
  const [proposeSupervisorId, setProposeSupervisorId] = useState('');
  const [proposeSecondSupervisorId, setProposeSecondSupervisorId] = useState('');

  // Defense council grading state (R6-R8)
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [lecturerWorkload, setLecturerWorkload] = useState<ThesisLecturerWorkload | null>(null);
  const [councilScores, setCouncilScores] = useState<Record<string, ThesisCouncilScore[]>>({});
  const [draftScores, setDraftScores] = useState<Record<string, string>>({});
  const [topicSupervisors, setTopicSupervisors] = useState<Record<string, string[]>>({});
  const [supervisedReports, setSupervisedReports] = useState<Record<string, ThesisGroupReport | null>>({});
  const [topicReports, setTopicReports] = useState<Record<string, ThesisGroupReport | null>>({});

  // Lecturer navigation tab (supervision vs council defense vs repository)
  const [lecturerTab, setLecturerTab] = useState<'supervision' | 'defense' | 'repository'>('supervision');

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

  const loadCouncilDetails = useCallback(async (councilsList: ThesisCouncil[]) => {
    for (const c of councilsList) {
      const isMemberOrAdmin = isAdmin || c.members?.some((m) => m.lecturerId === myLecturerId);
      const tids = (c as unknown as { topicIds?: string[] }).topicIds || [];
      for (const tid of tids) {
        try {
          const [scores, sups, report] = await Promise.all([
            isMemberOrAdmin ? thesisApi.listScores(c.id, tid).catch(() => []) : Promise.resolve([]),
            thesisApi.listSupervisors(tid).catch(() => []),
            thesisApi.getTopicReport(tid).catch(() => null),
          ]);
          const key = `${c.id}:${tid}`;
          setCouncilScores((prev) => ({ ...prev, [key]: scores }));
          setTopicSupervisors((prev) => ({ ...prev, [tid]: sups.map((s) => s.lecturerId) }));
          if (report) {
            setTopicReports((prev) => ({ ...prev, [tid]: report }));
          }
          const myScore = scores.find((s) => s.lecturerId === myLecturerId);
          if (myScore != null) {
            setDraftScores((prev) => ({ ...prev, [key]: String(myScore.score) }));
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
      await thesisApi.submitScore(councilId, topicId, scoreVal, 'DEFENSE');
      setActionSuccess(messages.thesis.grading.scoreSaved);
      toast.success(messages.thesis.grading.scoreSaved);
      const scores = await thesisApi.listScores(councilId, topicId);
      setCouncilScores((prev) => ({ ...prev, [key]: scores }));
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      if (code === 'SUPERVISOR_CANNOT_GRADE') {
        setActionError(messages.thesis.grading.supervisorCannotGrade);
        toast.error(messages.thesis.grading.supervisorCannotGrade);
      } else {
        setActionError(messages.thesis.actionFailed);
        toast.error(messages.thesis.actionFailed);
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
      const scores = await thesisApi.listScores(councilId, topicId);
      setCouncilScores((prev) => ({ ...prev, [key]: scores }));
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
        setActionError(messages.thesis.actionFailed);
        toast.error(messages.thesis.actionFailed);
      }
    } finally {
      setIsActionPending(false);
    }
  };

  useEffect(() => {
    let cancelled = false;

    const loadRounds = async () => {
      setIsLoading(true);
      setError('');
      try {
        const nextRounds = await thesisApi.listRounds();
        if (cancelled) return;
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
            sortedRounds[0];
          return preferred?.id || '';
        });
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    };

    void loadRounds();
    return () => {
      cancelled = true;
    };
  }, [explicitRoundId, messages.thesis.loadFailed]);

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

  useEffect(() => {
    if (!isSupervisorOrAdmin || !lecturerWorkload || explicitRoundId || hasAutoSelectedRoundRef.current) return;
    const withGroups = lecturerWorkload.topics.filter((topic) => topic.groupCount > 0);
    if (withGroups.length === 0) return;
    hasAutoSelectedRoundRef.current = true;
    if (withGroups.some((topic) => topic.roundId === selectedRoundId)) return;
    const target = [...withGroups].sort((a, b) => b.groupCount - a.groupCount)[0].roundId;
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
      const response = await lecturersApi.getAll({ limit: 100 });
      const list = Array.isArray(response?.data) ? response.data : [];
      setLecturers(list);
    } catch {
      // Never substitute a fabricated directory: the modal says the directory
      // failed to load so the reader is not shown invented supervisors.
      setLecturers([]);
      setLecturersError(messages.thesis.loadFailed);
    }
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (isSupervisorOrAdmin || isProposeModalOpen) {
      void loadLecturers();
    }
  }, [isSupervisorOrAdmin, isProposeModalOpen, loadLecturers]);

  const refreshTopics = useCallback(
    async (roundId: string) => {
      if (!roundId) return;
      if (isSupervisorOrAdmin) {
        const [published, drafts] = await Promise.all([
          thesisApi.listTopics(roundId, 'PUBLISHED'),
          thesisApi.listTopics(roundId, 'DRAFT'),
        ]);
        const map = new Map<string, ThesisTopic>();
        for (const t of [...published, ...drafts]) {
          map.set(t.id, t);
        }
        setTopics(Array.from(map.values()));
      } else {
        setTopics(await thesisApi.listTopics(roundId, 'PUBLISHED'));
      }
    },
    [isSupervisorOrAdmin],
  );

  useEffect(() => {
    if (!selectedRoundId) {
      setTopics([]);
      setGroups([]);
      return;
    }

    let cancelled = false;
    const loadWorkspace = async () => {
      setError('');
      try {
        const promises: Promise<unknown>[] = [
          refreshTopics(selectedRoundId),
          thesisApi.listGroups(selectedRoundId).then((next) => {
            if (!cancelled) setGroups(next);
          }),
        ];
        if (isSupervisorOrAdmin) {
          setIsRepositoryLoading(true);
          promises.push(
            thesisApi.listCouncils(selectedRoundId).then((cList) => {
              if (!cancelled) {
                setCouncils(cList);
                void loadCouncilDetails(cList);
              }
            }),
            thesisApi.listRoundRepository(selectedRoundId).then((rList) => {
              if (!cancelled) {
                setRepositoryReports(rList);
                setRepositoryError('');
                setIsRepositoryLoading(false);
              }
            }).catch(() => {
              if (!cancelled) {
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
    };
  }, [isSupervisorOrAdmin, loadCouncilDetails, messages.thesis.loadFailed, refreshTopics, selectedRoundId]);

  // Retry path for a failed repository load: the reader gets the real failure
  // with a way back, instead of an archive that silently reads as empty.
  const reloadRepository = useCallback(async () => {
    if (!selectedRoundId || !isSupervisorOrAdmin) return;
    setIsRepositoryLoading(true);
    setRepositoryError('');
    try {
      setRepositoryReports(await thesisApi.listRoundRepository(selectedRoundId));
    } catch {
      setRepositoryReports([]);
      setRepositoryError(messages.thesis.loadFailed);
    } finally {
      setIsRepositoryLoading(false);
    }
  }, [isSupervisorOrAdmin, messages.thesis.loadFailed, selectedRoundId]);

  const selectedRound = rounds.find((round) => round.id === selectedRoundId);
  const roundGroups = useMemo(() => {
    const map = new Map<string, { value: string; label: string }[]>();
    for (const r of rounds) {
      const match = r.name.match(/Ni\u00ean kh\u00f3a\s+\d{4}\s*[-–]\s*\d{4}/i);
      const cohort = match ? match[0] : (r.thesisType === 'KLTN' ? 'Khóa luận Tốt nghiệp' : 'Học phần Tốt nghiệp / Chuyên ngành');
      if (!map.has(cohort)) map.set(cohort, []);
      map.get(cohort)!.push({ value: r.id, label: r.name });
    }
    return Array.from(map.entries()).map(([label, opts]) => ({ label, options: opts }));
  }, [rounds]);
  const studentId = user?.studentId ?? '';
  const currentGroup = groups.find(
    (group) =>
      group.leaderStudentId === studentId ||
      group.memberStudentIds.includes(studentId) ||
      Boolean(group.members?.some((m) => m.studentId === studentId)),
  );
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

  const canManageMembers = useMemo(() => {
    if (!currentGroup || !user?.studentId) return false;
    const isLeader = currentGroup.leaderStudentId === user.studentId;
    const isRoundOpen = selectedRound?.status === 'REGISTRATION_OPEN';
    const isNotApproved = currentGroup.approvalStatus !== 'APPROVED';
    return isLeader && isRoundOpen && isNotApproved;
  }, [currentGroup, user, selectedRound]);

  // The backend also rejects group/topic actions outside the registration
  // window dates, so the CTA must not invite a doomed request after the end.
  const isRegistrationWindowOpen = useMemo(() => {
    if (selectedRound?.status !== 'REGISTRATION_OPEN') return false;
    const now = Date.now();
    const start = Date.parse(selectedRound.registrationStart);
    const end = Date.parse(selectedRound.registrationEnd);
    return (Number.isNaN(start) || now >= start) && (Number.isNaN(end) || now <= end);
  }, [selectedRound]);

  const reportGroupId = currentGroup?.id;
  const reportApprovalStatus = currentGroup?.approvalStatus;

  useEffect(() => {
    if (!reportGroupId || reportApprovalStatus !== 'APPROVED') {
      setGroupReport(null);
      setIsReportFormOpen(false);
      return;
    }
    let cancelled = false;
    setIsReportLoading(true);
    const loadReport = async () => {
      try {
        const report = await thesisApi.getReport(reportGroupId);
        if (!cancelled) setGroupReport(report);
      } catch {
        // A missing report (404 REPORT_NOT_FOUND) is expected before submission.
        if (!cancelled) setGroupReport(null);
      } finally {
        if (!cancelled) setIsReportLoading(false);
      }
    };
    void loadReport();
    return () => {
      cancelled = true;
    };
  }, [reportGroupId, reportApprovalStatus]);

  useEffect(() => {
    if (!isStudent || !selectedRoundId || selectedRound?.status !== 'RESULTS_PUBLISHED') {
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


  useEffect(() => {
    if (!isSupervisorOrAdmin || supervisedGroups.length === 0) return;
    for (const g of supervisedGroups) {
      if (g.approvalStatus === 'APPROVED' && !supervisedReports[g.id]) {
        void thesisApi.getReport(g.id)
          .then((rep) => setSupervisedReports((prev) => ({ ...prev, [g.id]: rep })))
          .catch(() => {});
      }
    }
  }, [isSupervisorOrAdmin, supervisedGroups, supervisedReports]);

  const filteredRepositoryReports = useMemo(() => {
    if (!repositorySearch.trim()) return repositoryReports;
    const q = repositorySearch.trim().toLowerCase();
    return repositoryReports.filter((report) => {
      const topicTitle = (report.topicTitle || report.title || '').toLowerCase();
      const topicDescription = (report.topicDescription || '').toLowerCase();
      const reportNote = (report.note || '').toLowerCase();
      const fileName = (report.fileName || '').toLowerCase();
      const submitter = `${report.submittedByDisplayName || ''} ${report.submittedByStudentNumber || ''}`.toLowerCase();
      const supervisorMatch = report.supervisors.some((s) =>
        s.displayName.toLowerCase().includes(q),
      );
      const memberMatch = report.members.some((m) =>
        `${m.displayName} ${m.studentNumber || ''}`.toLowerCase().includes(q),
      );

      return (
        topicTitle.includes(q) ||
        topicDescription.includes(q) ||
        reportNote.includes(q) ||
        fileName.includes(q) ||
        (report.departmentName || '').toLowerCase().includes(q) ||
        submitter.includes(q) ||
        Boolean(supervisorMatch) ||
        Boolean(memberMatch)
      );
    });
  }, [repositoryReports, repositorySearch]);

  const handleDownloadRepositoryReport = async (report: ThesisRepositoryReport) => {
    try {
      await thesisApi.downloadRepositoryReportFile(report.reportId, report);
      toast.success(locale === 'vi' ? 'Đã tải tài liệu báo cáo' : 'Downloaded report artifact');
    } catch {
      toast.error(locale === 'vi' ? 'Không thể tải tài liệu báo cáo' : 'Failed to download report artifact');
    }
  };

  const getTopicTitle = (topicId?: string | null) => {
    if (!topicId) return '—';
    const found = topics.find((topic) => topic.id === topicId);
    return found ? found.title : topicId;
  };

  const getLecturerLabel = (lecturer: Lecturer) => {
    const linkedUser = lecturer.user;
    const name = linkedUser
      ? locale === 'vi'
        ? `${linkedUser.lastName} ${linkedUser.firstName}`.trim()
        : `${linkedUser.firstName} ${linkedUser.lastName}`.trim()
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
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [currentGroup?.topicId]);

  // Once the reader picks a tab by hand, background refreshes and round
  // changes must not snap them back to an auto-selected tab.
  const userChoseTabRef = useRef(false);

  useEffect(() => {
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
  }, [searchParams, supervisedGroups.length, visibleCouncils.length]);

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
    if (actionParam === 'propose') {
      if (advisorIdParam) {
        setProposeSupervisorId(advisorIdParam);
      }
      setIsProposeModalOpen(true);
    }
  }, [searchParams]);

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
          groupFull: 'Nhóm đã đủ 4 thành viên, không thể thêm mới.',
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
          groupFull: 'The group already has the maximum of 4 members.',
          studentAlreadyInGroup: 'This student already belongs to a group in this round.',
        } as const);

  const refreshGroups = async () => {
    if (!selectedRoundId) return;
    setGroups(await thesisApi.listGroups(selectedRoundId));
  };

  const createGroup = async () => {
    if (!selectedRoundId) return;
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.createGroup(selectedRoundId);
      await refreshGroups();
    } catch {
      setActionError(messages.thesis.actionFailed);
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
        setAddMemberError(messages.thesis.actionFailed);
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
        setAddMemberError(messages.thesis.actionFailed);
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
    } catch {
      setActionError(messages.thesis.actionFailed);
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
      if (code === 'GROUP_TOO_SMALL') {
        setActionError(messages.thesis.groupTooSmall);
      } else if (code === 'GROUP_APPROVAL_STATE_CONFLICT') {
        setActionError(messages.thesis.groupStateConflict);
      } else {
        setActionError(messages.thesis.actionFailed);
      }
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
    } catch {
      setRejectError(messages.thesis.actionFailed);
    } finally {
      setIsActionPending(false);
    }
  };

  const openReportForm = () => {
    if (reportDeadlinePassed) return;
    setReportTitle(groupReport?.title ?? '');
    setReportUrl(groupReport?.url ?? '');
    setReportNote(groupReport?.note ?? '');
    setReportFile(null);
    setReportError('');
    setIsReportFormOpen(true);
  };

  const REPORT_FILE_EXTENSIONS = ['.pdf', '.doc', '.docx'];
  const REPORT_MAX_BYTES = 20 * 1024 * 1024;

  /** Downloads an attached report document for any authorized viewer. */
  const downloadReportArtifact = async (
    groupId: string,
    report: ThesisGroupReport | null | undefined,
  ) => {
    if (!report?.fileName) return;
    try {
      await thesisApi.downloadReportFile(groupId, report);
    } catch {
      toast.error(messages.thesis.report.submitFailed);
    }
  };

  const handleDownloadReportFile = async () => {
    if (!currentGroup || !groupReport?.fileName) return;
    await downloadReportArtifact(currentGroup.id, groupReport);
  };

  const handleSubmitReport = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentGroup) return;
    const url = reportUrl.trim();

    // Feedback item 7: a Word/PDF upload or a link — at least one artifact.
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
    } else if (!url && !groupReport?.url) {
      setReportError(messages.thesis.report.fileRequired);
      return;
    }

    setIsActionPending(true);
    setReportError('');
    setActionSuccess('');
    try {
      let saved: ThesisGroupReport;
      if (reportFile) {
        // Replacing an existing report discards the stored document and the
        // attached link, and the previous version cannot be recovered, so the
        // leader confirms before the destructive call is made.
        if (groupReport) {
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
      } else {
        // Keep the previous link when the leader only refreshes the metadata
        // of a link-based report without re-entering the URL.
        const effectiveUrl = url || groupReport?.url || '';
        if (!effectiveUrl) {
          setReportError(messages.thesis.report.fileRequired);
          setIsActionPending(false);
          return;
        }
        saved = await thesisApi.submitReport(currentGroup.id, {
          title: reportTitle.trim() || undefined,
          url: effectiveUrl,
          note: reportNote.trim() || undefined,
        });
      }
      setGroupReport(saved);
      setReportFile(null);
      setIsReportFormOpen(false);
      setActionSuccess(messages.thesis.report.submitSuccess);
    } catch (caught) {
      if (caught instanceof DOMException && caught.name === 'AbortError') {
        // The leader cancelled the upload on purpose: not an error to report.
        setReportError('');
      } else {
        const code = getThesisErrorCode(caught);
        if (code === 'FILE_TOO_LARGE') {
          setReportError(messages.thesis.report.fileTooLarge);
        } else if (code === 'UNSUPPORTED_FILE_TYPE' || code === 'INVALID_FILE_CONTENT') {
          setReportError(messages.thesis.report.fileTypeUnsupported);
        } else if (code === 'REPORT_WINDOW_CLOSED') {
          // The round status can change between render and submit (the panel
          // gates on it), so surface the localized window copy, not the
          // generic failure, when the server rejects the race.
          setReportError(messages.common.campusErrors.codes.REPORT_WINDOW_CLOSED);
        } else {
          setReportError(messages.thesis.report.submitFailed);
        }
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const handleProposeTopic = async (event: React.FormEvent) => {
    event.preventDefault();
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
    setActionSuccess('');
    try {
      const created = await thesisApi.createTopic({
        roundId: targetRoundId,
        departmentId,
        title,
        description,
        maxGroups: Math.min(20, Math.max(1, Number(proposeMaxGroups) || 1)),
      });
      if (proposePublishImmediately) {
        await thesisApi.publishTopic(created.id);
      }
      const supervisorIds = [proposeSupervisorId, proposeSecondSupervisorId]
        .map((id) => id.trim())
        .filter(Boolean);
      if (supervisorIds.length > 0) {
        try {
          await thesisApi.setSupervisors(created.id, supervisorIds);
        } catch {
          // The topic itself was created; supervisor assignment can be retried.
        }
      }
      if (selectedRoundId !== targetRoundId) {
        setSelectedRoundId(targetRoundId);
      } else {
        await refreshTopics(targetRoundId);
      }
      setIsProposeModalOpen(false);
      setProposeTitle('');
      setProposeDescription('');
      setProposeDepartmentId(departments[0]?.id || '');
      setProposeMaxGroups(2);
      setProposePublishImmediately(true);
      setProposeSupervisorId('');
      setProposeSecondSupervisorId('');
      setActionSuccess(
        proposePublishImmediately
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
      } else {
        setProposeError(messages.thesis.actionFailed);
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const openEditModal = (topic: ThesisTopic) => {
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
    if (!editingTopicId) return;
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
      } else {
        setEditError(messages.thesis.actionFailed);
      }
    } finally {
      setIsActionPending(false);
    }
  };

  const handlePublishTopic = async (topicId: string) => {
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.publishTopic(topicId);
      await refreshTopics(selectedRoundId);
      setActionSuccess(messages.thesis.publishedSuccess);
    } catch (caught) {
      const code = getThesisErrorCode(caught);
      if (code === 'TOPIC_STATE_CONFLICT') {
        setActionError(messages.thesis.topicStateConflict);
      } else if (code === 'ROUND_NOT_ACCEPTING_PROPOSALS') {
        setActionError(messages.thesis.roundNotAcceptingProposals);
      } else if (code === 'LECTURER_WINDOW_NOT_OPEN') {
        setActionError(messages.thesis.lecturerWindowNotOpen);
      } else if (code === 'LECTURER_WINDOW_CLOSED') {
        setActionError(messages.thesis.lecturerWindowClosed);
      } else {
        setActionError(messages.thesis.actionFailed);
      }
    } finally {
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
        onRetry={() => window.location.reload()}
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
  // timeline shows. `gvpbDeadline` is the field the workflow stepper already
  // labels as the report deadline, so the two never disagree.
  const reportDeadlineRound = currentGroup
    ? rounds.find((round) => round.id === currentGroup.roundId)
    : selectedRound;
  const reportDeadlineAt = reportDeadlineRound?.gvpbDeadline ?? null;
  const reportDeadlineMs = reportDeadlineAt ? Date.parse(reportDeadlineAt) : Number.NaN;
  const reportDaysRemaining = Number.isFinite(reportDeadlineMs)
    ? Math.ceil((reportDeadlineMs - Date.now()) / 86_400_000)
    : null;
  // The backend freezes a report once this deadline passes (409
  // REPORT_DEADLINE_PASSED); the panel now says so instead of offering a button
  // that can only fail. A round type without this date never freezes, matching
  // the server.
  const reportDeadlinePassed = Number.isFinite(reportDeadlineMs) && Date.now() >= reportDeadlineMs;
  // The backend also freezes reports until the owning round reaches
  // REGISTRATION_CLOSED (409 REPORT_WINDOW_CLOSED, checked right after the
  // deadline): gate the control on the round status the panel already loads,
  // and keep the current behaviour while the owning round is unknown.
  const reportWindowClosed =
    reportDeadlineRound != null && reportDeadlineRound.status !== 'REGISTRATION_CLOSED';

  // The report panel stays here: it owns the submission form, its state, and
  // the leader-only rights it renders.
  const studentReportSection =
    currentGroup && currentGroup.approvalStatus === 'APPROVED' ? (
      <div className="rounded-xl border border-border/70 bg-card p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-2">
            <FileText className="h-4 w-4 text-primary" />
            <span className="text-sm font-semibold text-foreground">
              {messages.thesis.report.title} {pageCopy.articleR5Suffix}
            </span>
          </div>
          {isGroupLeader ? (
            reportDeadlinePassed ? (
              <span className="inline-flex shrink-0 items-center gap-1.5 rounded-md border border-amber-300 bg-amber-50 px-2.5 py-1 text-xs font-semibold text-amber-800 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                <Lock className="h-3.5 w-3.5" aria-hidden="true" />
                {messages.thesis.report.lockedTitle}
              </span>
            ) : reportWindowClosed ? (
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
        <div className="mt-3 rounded-lg border border-primary/20 bg-primary/[0.03] p-2.5 text-xs text-muted-foreground flex items-center gap-2">
          <Info className="h-4 w-4 shrink-0 text-primary" />
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
              <div className="flex flex-col gap-3 rounded-xl border border-primary/15 bg-primary/[0.035] p-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex min-w-0 items-start gap-2.5">
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
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => void handleDownloadReportFile()}
                  className="shrink-0 self-start sm:self-auto"
                >
                  {messages.thesis.report.downloadFile}
                  <FileDown className="ml-1.5 h-3.5 w-3.5" aria-hidden="true" />
                </Button>
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
        ) : (
          <p className="mt-3 text-sm text-muted-foreground">
            {messages.thesis.report.notSubmitted}
          </p>
        )}

        {isGroupLeader && isReportFormOpen ? (
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
    ) : null;

  // The Stitch board puts the submission history beside the form: first
  // submission, latest update, and the supervisor-feedback milestone, with the
  // round countdown as a chip. It reads the same report the panel above owns.
  const studentSubmissionStatus =
    currentGroup && currentGroup.approvalStatus === 'APPROVED' ? (
      <SubmissionStatusTimeline
        report={groupReport}
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

  const renderTopicsCard = () => (
<Card className="h-full">
              <CardHeader>
                <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {/* Topic selection moved to the catalog: the inline grid could
                    not show every topic, and lecturers were being offered a
                    chooser for a topic they do not pick. */}
                <div className="flex flex-col items-start gap-3 rounded-lg border border-border/70 bg-card p-5">
                  <p className="text-sm leading-6 text-muted-foreground">
                    {messages.thesis.openCatalogHint}
                  </p>
                  <LinkButton href="/dashboard/thesis/topics" variant="outline">
                    {messages.thesis.navigation.catalog}
                  </LinkButton>
                </div>
              </CardContent>
            </Card>
  );

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.eyebrow}</SectionEyebrow>}
        title={messages.thesis.title}
        tabLabel={messages.thesis.eyebrow}
        description={messages.thesis.description}
        actions={
          <div className="flex flex-wrap items-end gap-3">
            {isSupervisorOrAdmin && selectedRound ? (
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
                onChange={(event) => setSelectedRoundId(event.target.value)}
                aria-label={messages.thesis.selectRound}
                groups={roundGroups}
                options={rounds.map((round) => ({
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

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <MetricCard label={messages.thesis.roundStatus} value={statusLabel(selectedRound.status)} icon={<CalendarDays className="h-5 w-5" />} tone="warning" />
            <MetricCard label={messages.thesis.topics} value={topics.length} icon={<FileStack className="h-5 w-5" />} tone="info" />
            <MetricCard label={messages.thesis.groups} value={groups.length} icon={<UsersRound className="h-5 w-5" />} tone="success" />
            <MetricCard label={messages.thesis.groupsTitle} value={currentGroup ? currentGroup.memberStudentIds.length : 0} icon={<Check className="h-5 w-5" />} tone="neutral" />
          </div>

          <Card variant="muted">
            <CardHeader>
              <CardTitle>{messages.thesis.lifecycleViewsTitle}</CardTitle>
              <CardDescription>{messages.thesis.lifecycleViewsDescription}</CardDescription>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-3">
              {([
                ['catalog', '/dashboard/thesis/topics'],
                ['advisors', '/dashboard/thesis/advisors'],
                ['progress', '/dashboard/thesis/progress'],
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

          {/* Lecturer & Supervisor Review Section */}
          {isSupervisorOrAdmin ? (
            <div className="space-y-6">
              {/* Tab Navigation for Lecturer */}
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-3">
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    type="button"
                    onClick={() => {
                      userChoseTabRef.current = true;
                      setLecturerTab('supervision');
                    }}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'supervision'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <BookOpen className="h-4 w-4" />
                    <span>{pageCopy.tabTopicsAndSupervisors}</span>
                    <span
                      className={cn(
                        'rounded-md px-2 py-0.5 text-[10px] font-mono font-bold',
                        lecturerTab === 'supervision'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {fillCopy(pageCopy.groupsCount, { count: supervisedGroups.length })}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      userChoseTabRef.current = true;
                      setLecturerTab('defense');
                    }}
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
                        'rounded-md px-2 py-0.5 text-[10px] font-mono font-bold',
                        lecturerTab === 'defense'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {fillCopy(pageCopy.councilsCount, { count: visibleCouncils.length })}
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      userChoseTabRef.current = true;
                      setLecturerTab('repository');
                    }}
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
                        'rounded-md px-2 py-0.5 text-[10px] font-mono font-bold',
                        lecturerTab === 'repository'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {fillCopy(pageCopy.reportsCount, { count: repositoryReports.length })}
                    </span>
                  </button>
                </div>
                <span className="text-xs text-muted-foreground font-medium">
                    {pageCopy.workspaceAreaLabel} <strong className="text-primary">{isAdmin ? pageCopy.roleAdmin : pageCopy.roleLecturer}</strong> {pageCopy.facultySuffix}
                </span>
              </div>

              {lecturerTab === 'supervision' ? (
                <div className="space-y-6">
                  <SupervisedGroupList
                    messages={messages}
                    title={messages.thesis.supervisedGroupsTitle}
                    description={messages.thesis.supervisedGroupsDescription}
                    groups={supervisedGroups}
                    reportsByGroup={supervisedReports}
                    isActionPending={isActionPending}
                    getTopicTitle={getTopicTitle}
                    isRoundOpen={isRegistrationOpenFor}
                    onGroupUpdated={(next) =>
                      setGroups((current) =>
                        current.map((item) => (item.id === next.id ? next : item)),
                      )
                    }
                    onApprove={approveGroup}
                    onReject={openRejectModal}
                    onDownloadReport={downloadReportArtifact}
                  />
                </div>
              ) : lecturerTab === 'defense' ? (
                <CouncilDefenseWorkspace
                  messages={messages}
                  councils={visibleCouncils}
                  myLecturerId={myLecturerId}
                  topics={topics}
                  lecturers={lecturers}
                  councilScores={councilScores}
                  topicSupervisors={topicSupervisors}
                  topicReports={topicReports}
                  draftScores={draftScores}
                  isActionPending={isActionPending}
                  showProfileClaimNotice={isLecturer && !isAdmin && !user?.lecturerId}
                  profileClaimMissingLabel={messages.thesis.councils.profileClaimMissing}
                  onScoreDraftChange={(key, value) =>
                    setDraftScores((prev) => ({ ...prev, [key]: value }))
                  }
                  onSubmitScore={handleSubmitScore}
                  onFinalizeScore={handleFinalizeScore}
                  onDownloadReport={downloadReportArtifact}
                />
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
                  <p className="mt-2 text-sm font-bold text-foreground truncate">
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
              <div className="grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
                <StudentGroupCard
                  messages={messages}
                  group={currentGroup ?? null}
                  groupStatusLabel={groupStatusLabel}
                  roleLabel={isGroupLeader ? pageCopy.roleGroupLeaderReport : pageCopy.roleMember}
                  topicTitle={currentGroup?.topicId ? getTopicTitle(currentGroup.topicId) : null}
                  supervisors={studentTopicSupervisors}
                  members={groupMemberRows}
                  canCreateGroup={isStudent && isRegistrationWindowOpen}
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
                          <p className="truncate text-sm font-semibold text-foreground">
                            {result.topicTitle}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {messages.thesis.results.council}: {result.councilName}
                          </p>
                        </div>
                        <div className="text-left sm:text-right">
                          <p className="text-lg font-semibold text-foreground">
                            {result.finalScore}
                          </p>
                          <p className="text-xs text-muted-foreground">
                            {messages.thesis.results.finalScore}
                          </p>
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
        isOpen={isProposeModalOpen}
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
                  label: `${round.name} (${round.status})`,
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
                      {messages.thesis.registrationWindow}: {currentProposeRound.lecturerSubmitStart.slice(0, 10)} → {currentProposeRound.lecturerSubmitEnd.slice(0, 10)}
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
            <label className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicTitleLabel}
            </label>
            <Input
              value={proposeTitle}
              onChange={(e) => setProposeTitle(e.target.value)}
              placeholder={messages.thesis.topicTitleLabel}
              required
              disabled={isActionPending}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              {departments.length > 0 ? (
                <Select
                  label={messages.thesis.topicDepartmentLabel}
                  value={proposeDepartmentId}
                  onChange={(e) => setProposeDepartmentId(e.target.value)}
                  required
                  disabled={isActionPending}
                  options={departments.map((dept) => ({
                    value: dept.id,
                    label: `${dept.code} - ${getLocalizedName(locale, dept, dept.name)}`,
                  }))}
                />
              ) : (
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">
                    {messages.thesis.topicDepartmentLabel}
                  </label>
                  <Input
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
              <label className="mb-1 block text-sm font-medium text-foreground">
                {messages.thesis.topicMaxGroupsLabel} (1-20)
              </label>
              <Input
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
            <div className="grid gap-4 sm:grid-cols-2">
              <Select
                label={messages.thesis.supervisorPrimaryLabel}
                value={proposeSupervisorId}
                onChange={(e) => setProposeSupervisorId(e.target.value)}
                disabled={isActionPending}
                options={[
                  { value: '', label: messages.thesis.supervisorNoneOption },
                  ...lecturers.map((lecturer) => ({
                    value: lecturer.id,
                    label: getLecturerLabel(lecturer),
                  })),
                ]}
              />
              <Select
                label={messages.thesis.supervisorSecondaryLabel}
                value={proposeSecondSupervisorId}
                onChange={(e) => setProposeSecondSupervisorId(e.target.value)}
                disabled={isActionPending}
                options={[
                  { value: '', label: messages.thesis.supervisorNoneOption },
                  ...lecturers.map((lecturer) => ({
                    value: lecturer.id,
                    label: getLecturerLabel(lecturer),
                  })),
                ]}
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
              value={proposeDescription}
              onChange={setProposeDescription}
              placeholder={messages.thesis.topicDescriptionLabel}
              minHeight="140px"
              disabled={isActionPending}
              locale={locale}
              showTemplates={true}
            />
          </div>
          <label className="flex cursor-pointer items-center gap-2 text-sm text-foreground">
            <input
              type="checkbox"
              checked={proposePublishImmediately}
              onChange={(e) => setProposePublishImmediately(e.target.checked)}
              disabled={isActionPending}
              className="h-4 w-4 rounded border-border text-primary focus:ring-primary"
            />
            <span>{messages.thesis.publishImmediately}</span>
          </label>
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
            <label className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.rejectionReason}
            </label>
            <Textarea
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
                    const fullName =
                      locale === 'vi'
                        ? `${student.lastName} ${student.firstName}`.trim()
                        : `${student.firstName} ${student.lastName}`.trim();
                    const curriculumLabel = [student.curriculumCode, student.curriculumName]
                      .filter(Boolean)
                      .join(' - ');
                    return (
                      <li
                        key={student.studentId}
                        className="flex items-center justify-between gap-3 p-3"
                      >
                        <div className="min-w-0 space-y-0.5">
                          <p className="truncate text-sm font-semibold text-foreground">{fullName}</p>
                          <p className="truncate text-xs text-muted-foreground">
                            MSSV: <span className="font-mono">{student.studentNumber}</span>
                          </p>
                          {curriculumLabel ? (
                            <p className="truncate text-xs text-muted-foreground">
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
          isOpen={Boolean(viewingTopic)}
          onClose={() => setViewingTopic(null)}
          title={viewingTopic.title}
          description={`${viewingTopic.maxGroups} ${messages.thesis.groups.toLowerCase()} • ${statusLabel(viewingTopic.status)}`}
        >
          <div className="space-y-4">
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
              <LocalizedLink
                href={`/dashboard/thesis/topics/${viewingTopic.id}`}
                className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
              >
                <ExternalLink className="h-3.5 w-3.5" />
                {locale === 'vi' ? 'Mở trang chuyên sâu' : 'Open dedicated topic page'}
              </LocalizedLink>

              <div className="flex flex-wrap items-center gap-2">
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() => setViewingTopic(null)}
                >
                  {locale === 'vi' ? 'Đóng' : 'Close'}
                </Button>

                {/* Feedback item 6: the topic catalog is the only place a topic
                    is chosen, so the modal offers no student chooser. The
                    lecturer branch below stays because publishing a draft is
                    not a topic selection (item 12 keeps lecturers off the
                    chooser). */}
                {isSupervisorOrAdmin &&
                  (isAdmin || viewingTopic.createdBy === user?.id || (user?.lecturerId && viewingTopic.createdBy === user.lecturerId)) &&
                  viewingTopic.status === 'DRAFT' ? (
                  <>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => openEditModal(viewingTopic)}
                      disabled={isActionPending}
                    >
                      {messages.thesis.editTopic}
                    </Button>
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => {
                        void handlePublishTopic(viewingTopic.id);
                        setViewingTopic(null);
                      }}
                      disabled={isActionPending}
                    >
                      {messages.thesis.publish}
                    </Button>
                  </>
                ) : null}
              </div>
            </div>
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
            <label className="mb-1 block text-sm font-medium text-foreground">
              {messages.thesis.topicTitleLabel}
            </label>
            <Input
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              placeholder={messages.thesis.topicTitleLabel}
              required
              disabled={isActionPending}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              {departments.length > 0 ? (
                <Select
                  label={messages.thesis.topicDepartmentLabel}
                  value={editDepartmentId}
                  onChange={(e) => setEditDepartmentId(e.target.value)}
                  required
                  disabled={isActionPending}
                  options={departments.map((dept) => ({
                    value: dept.id,
                    label: `${dept.code} - ${getLocalizedName(locale, dept, dept.name)}`,
                  }))}
                />
              ) : (
                <div>
                  <label className="mb-1 block text-sm font-medium text-foreground">
                    {messages.thesis.topicDepartmentLabel}
                  </label>
                  <Input
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
              <label className="mb-1 block text-sm font-medium text-foreground">
                {messages.thesis.topicMaxGroupsLabel} (1-20)
              </label>
              <Input
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
