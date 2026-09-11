'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowUpRight,
  Award,
  BookOpen,
  Building2,
  CalendarDays,
  Check,
  CircleDot,
  ExternalLink,
  FileStack,
  FileText,
  GraduationCap,
  Info,
  Layers,
  Plus,
  Scale,
  Shield,
  ShieldCheck,
  Sparkles,
  Trash2,
  UserPlus,
  Users,
  UsersRound,
  X,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Modal } from '@/components/ui/modal';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { metricToneClass, type StatusTone } from '@/components/ui/status';
import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { MemberAvatars } from '@/components/thesis/MemberAvatars';
import { ThesisWorkflowStepper } from '@/components/thesis/ThesisWorkflowStepper';
import { ThesisRegulationGuide } from '@/components/thesis/ThesisRegulationGuide';
import { RoundMilestoneCard } from '@/components/thesis/RoundMilestoneCard';
import { departmentsApi, lecturersApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import type { Department, Lecturer } from '@/types/api';
import {
  thesisApi,
  type ThesisCouncil,
  type ThesisCouncilScore,
  type ThesisGroup,
  type ThesisGroupMember,
  type ThesisGroupReport,
  type ThesisRound,
  type ThesisRoundResult,
  type ThesisStudentResult,
  type ThesisTopic,
} from '@/lib/thesis-api';

/** Shape of the API error envelope (`{ code, message }`) used for domain conflicts. */
interface ThesisApiErrorShape {
  response?: { data?: { code?: string; message?: string } | null } | null;
}

function getThesisErrorCode(error: unknown): string {
  const data = (error as ThesisApiErrorShape | undefined)?.response?.data;
  return data?.code ?? '';
}

export function getGradeClassification(score: number) {
  if (score >= 8.5) {
    return {
      letter: 'A',
      gpa4: '4.0',
      rank: 'Xuất sắc',
      badgeClass: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
    };
  }
  if (score >= 8.0) {
    return {
      letter: 'B+',
      gpa4: '3.5',
      rank: 'Giỏi',
      badgeClass: 'bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30',
    };
  }
  if (score >= 7.0) {
    return {
      letter: 'B',
      gpa4: '3.0',
      rank: 'Khá',
      badgeClass: 'bg-cyan-500/15 text-cyan-700 dark:text-cyan-300 border-cyan-500/30',
    };
  }
  if (score >= 6.5) {
    return {
      letter: 'C+',
      gpa4: '2.5',
      rank: 'Trung bình khá',
      badgeClass: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
    };
  }
  if (score >= 5.5) {
    return {
      letter: 'C',
      gpa4: '2.0',
      rank: 'Trung bình',
      badgeClass: 'bg-orange-500/15 text-orange-700 dark:text-orange-300 border-orange-500/30',
    };
  }
  if (score >= 5.0) {
    return {
      letter: 'D+',
      gpa4: '1.5',
      rank: 'Trung bình yếu',
      badgeClass: 'bg-yellow-500/15 text-yellow-700 dark:text-yellow-300 border-yellow-500/30',
    };
  }
  if (score >= 4.0) {
    return {
      letter: 'D',
      gpa4: '1.0',
      rank: 'Đạt',
      badgeClass: 'bg-zinc-500/15 text-zinc-700 dark:text-zinc-300 border-zinc-500/30',
    };
  }
  return {
    letter: 'F',
    gpa4: '0.0',
    rank: 'Không đạt (Bảo vệ lại)',
    badgeClass: 'bg-destructive/15 text-destructive border-destructive/30',
  };
}

export default function ThesisPage() {
  const { user, isStudent, isLecturer, isAdmin } = useAuth();
  const isSupervisorOrAdmin = Boolean(isLecturer || isAdmin);
  const { locale, formatDateTime, messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isActionPending, setIsActionPending] = useState(false);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [actionSuccess, setActionSuccess] = useState('');

  // Propose topic state
  const [isProposeModalOpen, setIsProposeModalOpen] = useState(false);
  const [proposeTitle, setProposeTitle] = useState('');
  const [proposeDescription, setProposeDescription] = useState('');
  const [proposeDepartmentId, setProposeDepartmentId] = useState('department-demo');
  const [proposeMaxGroups, setProposeMaxGroups] = useState(2);
  const [proposePublishImmediately, setProposePublishImmediately] = useState(true);
  const [proposeError, setProposeError] = useState('');

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

  // Group report state (approved groups only)
  const [groupReport, setGroupReport] = useState<ThesisGroupReport | null>(null);
  const [isReportLoading, setIsReportLoading] = useState(false);
  const [isReportFormOpen, setIsReportFormOpen] = useState(false);
  const [reportTitle, setReportTitle] = useState('');
  const [reportUrl, setReportUrl] = useState('');
  const [reportNote, setReportNote] = useState('');
  const [reportError, setReportError] = useState('');

  // Round results state (shown once the round reaches RESULTS_PUBLISHED)
  const [roundResults, setRoundResults] = useState<ThesisRoundResult[]>([]);
  const [resultsState, setResultsState] = useState<
    'hidden' | 'loading' | 'ready' | 'notPublished'
  >('hidden');

  // Supervisor directory used by the propose-topic modal
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [proposeSupervisorId, setProposeSupervisorId] = useState('');
  const [proposeSecondSupervisorId, setProposeSecondSupervisorId] = useState('');

  // Defense council grading state (R6-R8)
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [councilScores, setCouncilScores] = useState<Record<string, ThesisCouncilScore[]>>({});
  const [draftScores, setDraftScores] = useState<Record<string, string>>({});
  const [topicSupervisors, setTopicSupervisors] = useState<Record<string, string[]>>({});
  const [supervisedReports, setSupervisedReports] = useState<Record<string, ThesisGroupReport | null>>({});
  const [topicReports, setTopicReports] = useState<Record<string, ThesisGroupReport | null>>({});

  // Lecturer navigation tab (supervision vs council defense)
  const [lecturerTab, setLecturerTab] = useState<'supervision' | 'defense'>('supervision');

  // Supervisors for current student group's topic
  const [currentTopicSupervisors, setCurrentTopicSupervisors] = useState<string[]>([]);

  const myLecturerId = user?.lecturerId || user?.id || '';
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
      setActionError('Điểm bảo vệ phải là số từ 0.0 đến 10.0');
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
      const msg = `Đã chốt điểm thành công: ${res.finalScore} điểm.`;
      setActionSuccess(msg);
      toast.success(msg);
      const key = `${councilId}:${topicId}`;
      const scores = await thesisApi.listScores(councilId, topicId);
      setCouncilScores((prev) => ({ ...prev, [key]: scores }));
      await refreshTopics(selectedRoundId);
    } catch (err: unknown) {
      const code = getThesisErrorCode(err);
      if (code === 'SCORES_INCOMPLETE') {
        const msg = 'Cần tất cả thành viên hội đồng hợp lệ chấm điểm trước khi chốt.';
        setActionError(msg);
        toast.error(msg);
      } else if (code === 'SCORE_ALREADY_FINALIZED') {
        const msg = 'Điểm đề tài này đã được chốt trước đó.';
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
        setRounds(nextRounds);
        setSelectedRoundId((current) => {
          if (current) return current;
          const preferred = nextRounds.find((r) => r.id === '22222222-2222-2222-2222-222222222101') || nextRounds[0];
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
  }, [messages.thesis.loadFailed]);

  const [topicFilter, setTopicFilter] = useState<'all' | 'my'>('all');

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
          setProposeDepartmentId((current) => (current === 'department-demo' ? list[0].id : current));
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

  useEffect(() => {
    let cancelled = false;
    const loadLecturers = async () => {
      try {
        const response = await lecturersApi.getAll({ limit: 100 });
        const list = Array.isArray(response?.data) ? response.data : [];
        if (!cancelled && list.length > 0) {
          setLecturers(list);
        }
      } catch {
        // Supervisor selects stay hidden when the lecturer directory is unavailable.
      }
    };

    if (isSupervisorOrAdmin) {
      void loadLecturers();
    }
    return () => {
      cancelled = true;
    };
  }, [isSupervisorOrAdmin]);

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
          promises.push(
            thesisApi.listCouncils(selectedRoundId).then((cList) => {
              if (!cancelled) {
                setCouncils(cList);
                void loadCouncilDetails(cList);
              }
            }),
          );
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

  const selectedRound = rounds.find((round) => round.id === selectedRoundId);
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
    if (!selectedRoundId || selectedRound?.status !== 'RESULTS_PUBLISHED') {
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
  }, [selectedRound?.status, selectedRoundId]);

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

  const supervisedGroups = useMemo(
    () =>
      isSupervisorOrAdmin
        ? groups.filter((group) => group.topicId && (isAdmin || myTopicIds.has(group.topicId)))
        : [],
    [groups, isAdmin, isSupervisorOrAdmin, myTopicIds],
  );

  const displayedTopics = useMemo(
    () => (isSupervisorOrAdmin && topicFilter === 'my' ? myTopics : topics),
    [isSupervisorOrAdmin, myTopics, topicFilter, topics],
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
          setCurrentTopicSupervisors(sups.map((s) => s.lecturerId));
        }
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [currentGroup?.topicId]);

  useEffect(() => {
    if (supervisedGroups.length === 0 && visibleCouncils.length > 0) {
      setLecturerTab('defense');
    } else if (supervisedGroups.length > 0) {
      setLecturerTab('supervision');
    }
  }, [selectedRoundId, supervisedGroups.length, visibleCouncils.length]);

  const studentTopicSupervisors = useMemo(() => {
    return currentTopicSupervisors.map((lid) => {
      const lect = lecturers.find((l) => l.id === lid);
      return lect?.user
        ? `${lect.user.lastName} ${lect.user.firstName}`
        : lid;
    });
  }, [currentTopicSupervisors, lecturers]);

  const currentTopic = topics.find((t) => t.id === currentGroup?.topicId);
  const myResultItem = currentGroup ? roundResults.find((r) => r.groupId === currentGroup.id) : null;
  const studentFinalScore = currentTopic?.finalScore ?? myResultItem?.finalScore ?? null;
  const studentGradeInfo = studentFinalScore != null ? getGradeClassification(studentFinalScore) : null;

  const currentLeaderMember = groupMemberList.find((m) => m.isLeader);
  const currentLeaderName = currentLeaderMember
    ? (currentLeaderMember.displayName || currentLeaderMember.studentNumber || currentLeaderMember.studentId)
    : (currentGroup?.leaderStudentId || 'Nhóm trưởng');

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
          groupFull: 'Nhóm đã đủ 3 thành viên, không thể thêm mới.',
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
          groupFull: 'The group already has the maximum of 3 members.',
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
    if (!window.confirm(messages.thesis.removeMemberConfirm)) return;

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

  const chooseTopic = async (topicId: string) => {
    if (!currentGroup) return;
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.assignTopic(currentGroup.id, topicId);
      await refreshGroups();
    } catch {
      setActionError(messages.thesis.actionFailed);
    } finally {
      setIsActionPending(false);
    }
  };

  const approveGroup = async (groupId: string) => {
    setIsActionPending(true);
    setActionError('');
    setActionSuccess('');
    try {
      await thesisApi.approveGroup(groupId);
      await refreshGroups();
      setActionSuccess(messages.thesis.approveSuccess);
    } catch {
      setActionError(messages.thesis.actionFailed);
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
    setReportTitle(groupReport?.title ?? '');
    setReportUrl(groupReport?.url ?? '');
    setReportNote(groupReport?.note ?? '');
    setReportError('');
    setIsReportFormOpen(true);
  };

  const handleSubmitReport = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!currentGroup) return;
    const url = reportUrl.trim();
    if (!url) {
      setReportError(messages.thesis.report.urlRequired);
      return;
    }
    setIsActionPending(true);
    setReportError('');
    setActionSuccess('');
    try {
      const saved = await thesisApi.submitReport(currentGroup.id, {
        title: reportTitle.trim() || undefined,
        url,
        note: reportNote.trim() || undefined,
      });
      setGroupReport(saved);
      setIsReportFormOpen(false);
      setActionSuccess(messages.thesis.report.submitSuccess);
    } catch {
      setReportError(messages.thesis.report.submitFailed);
    } finally {
      setIsActionPending(false);
    }
  };

  const handleProposeTopic = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedRoundId) return;
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
        roundId: selectedRoundId,
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
      await refreshTopics(selectedRoundId);
      setIsProposeModalOpen(false);
      setProposeTitle('');
      setProposeDescription('');
      setProposeDepartmentId(departments[0]?.id || 'department-demo');
      setProposeMaxGroups(2);
      setProposePublishImmediately(true);
      setProposeSupervisorId('');
      setProposeSecondSupervisorId('');
      setActionSuccess(
        proposePublishImmediately
          ? messages.thesis.publishedSuccess
          : messages.thesis.proposeSuccess,
      );
    } catch {
      setProposeError(messages.thesis.actionFailed);
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
    } catch {
      setActionError(messages.thesis.actionFailed);
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

  
  const renderStudentGroupCard = () => (
<Card variant="muted" className="h-full">
              <CardHeader>
                <CardTitle>{messages.thesis.groupsTitle}</CardTitle>
                <CardDescription>{messages.thesis.groupsDescription}</CardDescription>
              </CardHeader>
              <CardContent>
                {!currentGroup ? (
                  <EmptyState
                    icon={UsersRound}
                    title={messages.thesis.noGroup}
                    description={messages.thesis.noGroupDescription}
                    action={
                      isStudent && selectedRound?.status === 'REGISTRATION_OPEN' ? (
                        <Button type="button" onClick={() => void createGroup()} disabled={isActionPending}>
                          {messages.thesis.createGroup}
                          <ArrowUpRight className="ml-2 h-4 w-4" />
                        </Button>
                      ) : undefined
                    }
                    className="min-h-[280px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  <div className="space-y-5">
                    <div className="flex flex-col gap-3 rounded-xl border border-border/70 bg-card p-4 sm:flex-row sm:items-center sm:justify-between">
                      <div className="min-w-0 space-y-1">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                            {messages.thesis.groupsTitle}
                          </span>
                          <span className="rounded-full bg-secondary px-2 py-0.5 text-xs font-medium text-foreground">
                            {messages.thesis.status[currentGroup.status] ?? messages.common.statuses.UNKNOWN}
                          </span>
                          <StatusBadge status={currentGroup.approvalStatus} variant="approval" />
                          <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[11px] font-semibold text-foreground">
                            Vai trò của bạn: {isGroupLeader ? 'Nhóm trưởng (Đại diện nộp báo cáo - R5)' : 'Thành viên'}
                          </span>
                        </div>
                        <h4 className="text-sm font-semibold text-foreground truncate">
                          {currentGroup.topicId ? getTopicTitle(currentGroup.topicId) : messages.thesis.chooseTopic}
                        </h4>
                      </div>

                      {canManageMembers && groupMemberList.length < 3 ? (
                        <Button
                          type="button"
                          size="sm"
                          onClick={() => {
                            resetStudentSearch();
                            setIsAddMemberModalOpen(true);
                          }}
                          disabled={isActionPending}
                          className="shrink-0 gap-1.5"
                        >
                          <UserPlus className="h-4 w-4" />
                          {messages.thesis.addMember}
                        </Button>
                      ) : null}
                    </div>

                    {/* Supervisor Info (Rule R3) */}
                    {studentTopicSupervisors.length > 0 && (
                      <div className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/20 bg-primary/[0.04] px-3 py-2 text-xs">
                        <span className="font-bold text-foreground">Giảng viên hướng dẫn (Điều R3):</span>
                        <span className="font-semibold text-primary">{studentTopicSupervisors.join(' · ')}</span>
                      </div>
                    )}

                    {/* Approval Status Guidance (Rule R4) */}
                    {currentGroup.approvalStatus === 'APPROVED' ? (
                      <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-800 dark:text-emerald-300 flex items-start gap-2">
                        <Check className="h-4 w-4 shrink-0 text-emerald-600 dark:text-emerald-400 mt-0.5" />
                        <div>
                          <strong>Đề tài đã được GVHD phê duyệt chính thức (Điều R4):</strong> Nhóm đủ điều kiện triển khai nghiên cứu. Nhóm trưởng chuẩn bị nộp báo cáo luận văn và slide theo Điều R5.
                        </div>
                      </div>
                    ) : currentGroup.approvalStatus === 'PENDING' ? (
                      <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-800 dark:text-amber-300 flex items-start gap-2">
                        <CircleDot className="h-4 w-4 shrink-0 text-amber-600 dark:text-amber-400 mt-0.5" />
                        <div>
                          <strong>Đang chờ GVHD xét duyệt (Điều R4):</strong> Nguyện vọng đề tài đang chờ Giảng viên hướng dẫn duyệt. Nhóm trưởng có thể quản lý thành viên trong thời gian mở đăng ký.
                        </div>
                      </div>
                    ) : (
                      <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-xs text-destructive flex items-start gap-2">
                        <Shield className="h-4 w-4 shrink-0 mt-0.5" />
                        <div>
                          <strong>Đề tài bị từ chối:</strong> {currentGroup.rejectionReason || 'Vui lòng liên hệ GVHD hoặc chọn đề tài khác phù hợp.'}
                        </div>
                      </div>
                    )}

                    {/* Member List Header & Count */}
                    <div className="space-y-3">
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-medium text-foreground">
                          {messages.thesis.memberCount.replace('{count}', String(groupMemberList.length))}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {groupMemberList.length >= 3 ? messages.thesis.maxMembersReached : ''}
                        </span>
                      </div>

                      {/* Detailed member items */}
                      <div className="space-y-2">
                        {groupMemberList.map((member) => {
                          const memberDisplayName =
                            member.displayName || member.studentNumber || member.studentId || 'SV';
                          const memberIdentifier = member.studentNumber || member.studentId;

                          return (
                          <div
                            key={member.studentId || member.displayName}
                            className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-card p-3 shadow-2xs transition-colors hover:border-primary/40"
                          >
                            <div className="flex min-w-0 items-center gap-3">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                                {memberDisplayName.slice(0, 2).toUpperCase()}
                              </div>
                              <div className="min-w-0 space-y-0.5">
                                <div className="flex flex-wrap items-center gap-1.5">
                                  <span className="text-sm font-semibold text-foreground">
                                    {memberDisplayName}
                                  </span>
                                  {member.isLeader ? (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2 py-0.5 text-[11px] font-semibold text-amber-700 dark:text-amber-300">
                                      <Shield className="h-3 w-3" />
                                      {messages.thesis.leaderBadge}
                                    </span>
                                  ) : (
                                    <span className="rounded-full bg-muted px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                                      {messages.thesis.memberBadge}
                                    </span>
                                  )}
                                  {member.isExternal ? (
                                    <span className="inline-flex items-center gap-1 rounded-full bg-indigo-500/15 px-2 py-0.5 text-[11px] font-semibold text-indigo-700 dark:text-indigo-300">
                                      <Building2 className="h-3 w-3" />
                                      {messages.thesis.externalBadge}
                                    </span>
                                  ) : null}
                                </div>
                                <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-foreground">
                                  {memberIdentifier && !member.isExternal ? (
                                    <span>MSSV: <strong className="font-mono text-foreground/80">{memberIdentifier}</strong></span>
                                  ) : null}
                                  {member.contact ? (
                                    <span>{member.contact}</span>
                                  ) : null}
                                </div>
                              </div>
                            </div>

                            {/* Remove button for leader during open registration */}
                            {canManageMembers && !member.isLeader ? (
                              <Button
                                type="button"
                                size="sm"
                                variant="ghost"
                                onClick={() => void handleRemoveMember(member.studentId)}
                                disabled={isActionPending}
                                className="h-8 w-8 p-0 text-muted-foreground hover:text-destructive hover:bg-destructive/10 shrink-0"
                                title={messages.thesis.removeMember}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            ) : null}
                          </div>
                          );
                        })}
                      </div>
                    </div>

                    {/* Group report (approved groups only) */}
                    {currentGroup.approvalStatus === 'APPROVED' ? (
                      <div className="rounded-xl border border-border/70 bg-card p-4">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                          <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 text-primary" />
                            <span className="text-sm font-semibold text-foreground">
                              {messages.thesis.report.title} (Điều R5)
                            </span>
                          </div>
                          {isGroupLeader ? (
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
                          ) : null}
                        </div>

                        {/* Regulatory Notice R5 */}
                        <div className="mt-3 rounded-lg border border-primary/20 bg-primary/[0.03] p-2.5 text-xs text-muted-foreground flex items-center gap-2">
                          <Info className="h-4 w-4 shrink-0 text-primary" />
                          <span>
                            <strong>Quy chế Điều R5:</strong> Quyền nộp hoặc cập nhật báo cáo luận văn thuộc về <strong>Nhóm trưởng</strong> ({currentLeaderName}). Các thành viên xem tài liệu nghiệm thu đã nộp.
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
                            <a
                              href={groupReport.url}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1 text-sm text-primary underline-offset-2 hover:underline"
                            >
                              {messages.thesis.report.view}
                              <ExternalLink className="h-3.5 w-3.5" />
                            </a>
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
                              <div className="rounded-md border border-destructive/30 bg-destructive/5 p-2 text-xs text-destructive">
                                {reportError}
                              </div>
                            ) : null}
                            <div>
                              <label
                                className="mb-1 block text-xs font-medium text-foreground"
                                htmlFor="thesis-report-title"
                              >
                                {messages.thesis.report.titleLabel}
                              </label>
                              <Input
                                id="thesis-report-title"
                                value={reportTitle}
                                onChange={(e) => setReportTitle(e.target.value)}
                                disabled={isActionPending}
                              />
                            </div>
                            <div>
                              <label
                                className="mb-1 block text-xs font-medium text-foreground"
                                htmlFor="thesis-report-url"
                              >
                                {messages.thesis.report.urlLabel}{' '}
                                <span className="text-destructive">*</span>
                              </label>
                              <Input
                                id="thesis-report-url"
                                value={reportUrl}
                                onChange={(e) => setReportUrl(e.target.value)}
                                placeholder="https://..."
                                required
                                disabled={isActionPending}
                              />
                              <p className="mt-1 text-[11px] text-muted-foreground">
                                Chấp nhận liên kết Google Drive (chế độ Anyone with the link), OneDrive, GitHub repo hoặc file PDF trực tiếp.
                              </p>
                            </div>
                            <div>
                              <label
                                className="mb-1 block text-xs font-medium text-foreground"
                                htmlFor="thesis-report-note"
                              >
                                {messages.thesis.report.noteLabel}
                              </label>
                              <Textarea
                                id="thesis-report-note"
                                value={reportNote}
                                onChange={(e) => setReportNote(e.target.value)}
                                rows={2}
                                disabled={isActionPending}
                              />
                            </div>
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
                              <Button type="submit" size="sm" disabled={isActionPending}>
                                {groupReport
                                  ? messages.thesis.report.update
                                  : messages.thesis.report.submit}
                              </Button>
                            </div>
                          </form>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                )}
              </CardContent>
            </Card>
  );

  const renderTopicsCard = () => (
<Card className="h-full">
              <CardHeader>
                <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <CardTitle>{messages.thesis.topicsTitle}</CardTitle>
                    <CardDescription>{messages.thesis.topicsDescription}</CardDescription>
                  </div>
                  {isSupervisorOrAdmin ? (
                    <div className="flex items-center gap-1.5 rounded-lg border border-border/80 bg-muted/40 p-1">
                      <button
                        type="button"
                        onClick={() => setTopicFilter('all')}
                        className={cn(
                          'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                          topicFilter === 'all'
                            ? 'bg-background text-foreground shadow-sm'
                            : 'text-muted-foreground hover:text-foreground',
                        )}
                      >
                        {messages.thesis.topics} ({topics.length})
                      </button>
                      <button
                        type="button"
                        onClick={() => setTopicFilter('my')}
                        className={cn(
                          'rounded-md px-2.5 py-1 text-xs font-medium transition-colors',
                          topicFilter === 'my'
                            ? 'bg-background text-foreground shadow-sm'
                            : 'text-muted-foreground hover:text-foreground',
                        )}
                      >
                        {messages.thesis.myProposedTopics} ({myTopics.length})
                      </button>
                    </div>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {displayedTopics.length === 0 ? (
                  <EmptyState
                    icon={FileStack}
                    title={
                      topicFilter === 'my'
                        ? messages.thesis.noProposedTopics
                        : messages.thesis.noTopics
                    }
                    description={
                      topicFilter === 'my'
                        ? messages.thesis.myProposedTopicsDescription
                        : messages.thesis.noTopicsDescription
                    }
                    className="min-h-[280px]"
                  />
                ) : (
                  <div className="grid gap-3 sm:grid-cols-2">
                    {displayedTopics.map((topic) => {
                      const selected = currentGroup?.topicId === topic.id;
                      const isMine =
                        isSupervisorOrAdmin &&
                        (topic.createdBy === user?.id ||
                          (user?.lecturerId && topic.createdBy === user.lecturerId));
                      return (
                        <article
                          key={topic.id}
                          className={cn(
                            'group flex min-h-[190px] flex-col rounded-xl border p-4 transition-colors',
                            selected
                              ? 'border-primary bg-primary/5'
                              : 'border-border/70 bg-card hover:border-primary/45 hover:bg-secondary/30',
                          )}
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="flex flex-wrap items-center gap-1.5">
                              <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                                {topic.maxGroups} {messages.thesis.groups.toLowerCase()}
                              </span>
                              {topic.status === 'DRAFT' ? (
                                <span className="inline-flex rounded-full bg-amber-500/10 px-2 py-0.5 text-xs font-medium text-amber-700 dark:text-amber-300">
                                  {messages.thesis.status.DRAFT}
                                </span>
                              ) : null}
                              {isMine ? (
                                <span className="inline-flex rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                                  {messages.thesis.myProposedTopics}
                                </span>
                              ) : null}
                            </div>
                            {selected ? (
                              <Check className="h-4 w-4 text-emerald-600" />
                            ) : (
                              <ArrowUpRight className="h-4 w-4 text-muted-foreground transition-transform group-hover:-translate-y-0.5 group-hover:translate-x-0.5" />
                            )}
                          </div>
                          <h3 className="mt-4 line-clamp-2 text-base font-semibold leading-6 text-foreground">
                            {topic.title}
                          </h3>
                          <div className="mt-2 line-clamp-3 text-sm leading-6 text-muted-foreground">
                            <RichContentRenderer content={topic.description} />
                          </div>
                          <div className="mt-auto pt-3 flex flex-wrap gap-2">
                            {currentGroup &&
                            !selected &&
                            currentGroup.approvalStatus !== 'APPROVED' &&
                            topic.status === 'PUBLISHED' ? (
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                className="w-full"
                                onClick={() => void chooseTopic(topic.id)}
                                disabled={isActionPending}
                              >
                                {messages.thesis.chooseTopic}
                              </Button>
                            ) : null}
                            {isSupervisorOrAdmin &&
                            isMine &&
                            topic.status === 'DRAFT' ? (
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                className="w-full text-xs"
                                onClick={() => void handlePublishTopic(topic.id)}
                                disabled={isActionPending}
                              >
                                {messages.thesis.publish}
                              </Button>
                            ) : null}
                          </div>
                        </article>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
  );

  const renderCouncilDefenseWorkspace = () => (
<Card className="rounded-xl border-border/80 shadow-xs">
              <CardHeader>
                <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <CardTitle className="flex items-center gap-2">
                      <Users className="h-5 w-5 text-primary" />
                      {messages.thesis.grading.title}
                    </CardTitle>
                    <CardDescription>{messages.thesis.grading.description}</CardDescription>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                {visibleCouncils.map((council) => {
                  const myMembership = council.members?.find(
                    (m) => m.lecturerId === myLecturerId,
                  );
                  const isChair = myMembership?.memberRole === 'CHAIR';
                  const topicIds =
                    (council as unknown as { topicIds?: string[] }).topicIds || [];

                  return (
                    <div
                      key={council.id}
                      className="rounded-xl border border-border/70 bg-card p-5 space-y-4"
                    >
                      <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between border-b border-border/50 pb-3">
                        <div>
                          <h4 className="font-semibold text-foreground text-base">
                            {council.name}
                          </h4>
                          <p className="text-xs text-muted-foreground mt-0.5">
                            Vai trò của bạn:{' '}
                            <span className="font-medium text-primary">
                              {myMembership?.memberRole === 'CHAIR'
                                ? messages.thesis.councils.roleChair
                                : myMembership?.memberRole === 'SECRETARY'
                                ? messages.thesis.councils.roleSecretary
                                : myMembership
                                ? messages.thesis.councils.roleMember
                                : 'Quản trị viên'}
                            </span>
                          </p>
                        </div>
                        <span className="text-xs font-medium text-muted-foreground">
                          {topicIds.length} đề tài phân công
                        </span>
                      </div>

                      {topicIds.length === 0 ? (
                        <p className="text-xs text-muted-foreground italic py-2">
                          {messages.thesis.councils.noAssignedTopics}
                        </p>
                      ) : (
                        <div className="space-y-4">
                          {topicIds.map((tid: string) => {
                            const topic = topics.find((t) => t.id === tid);
                            const key = `${council.id}:${tid}`;
                            const scores = councilScores[key] || [];
                            const sups = topicSupervisors[tid] || [];
                            const isSupervisor =
                              sups.includes(myLecturerId) ||
                              topic?.createdBy === myLecturerId;

                            const isFinalized = topic?.finalScore != null;
                            const eligibleGraderCount = (council.members || []).filter(
                              (m) =>
                                !sups.includes(m.lecturerId) &&
                                m.lecturerId !== topic?.createdBy,
                            ).length;
                            const submittedCount = scores.length;
                            const canFinalize =
                              isChair &&
                              !isFinalized &&
                              submittedCount >= eligibleGraderCount &&
                              eligibleGraderCount > 0;

                            return (
                              <div
                                key={tid}
                                className="rounded-xl border border-border/60 bg-muted/20 p-4 space-y-3"
                              >
                                <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between">
                                  <div className="space-y-1">
                                    <h5 className="font-semibold text-foreground text-sm">
                                      {topic?.title || tid}
                                    </h5>
                                    {topic?.description && (
                                      <p className="text-xs text-muted-foreground line-clamp-2">
                                        {topic.description}
                                      </p>
                                    )}
                                    {topicReports[tid] ? (
                                      <div className="pt-1.5 flex items-center gap-2">
                                        <a
                                          href={topicReports[tid]?.url}
                                          target="_blank"
                                          rel="noreferrer"
                                          className="inline-flex items-center gap-1.5 rounded-md border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline"
                                        >
                                          <FileText className="h-3.5 w-3.5" />
                                          <span>Tài liệu luận văn: {topicReports[tid]?.title || 'Báo cáo tốt nghiệp'}</span>
                                          <ExternalLink className="h-3 w-3" />
                                        </a>
                                        {topicReports[tid]?.note ? (
                                          <span className="text-[11px] text-muted-foreground italic truncate max-w-xs">
                                            ({topicReports[tid]?.note})
                                          </span>
                                        ) : null}
                                      </div>
                                    ) : null}
                                  </div>
                                  {isFinalized ? (
                                    <div className="flex items-center gap-1.5 rounded-full bg-status-success/15 px-3 py-1 text-xs font-semibold text-status-success-foreground border border-status-success/30 shrink-0">
                                      <Check className="h-3.5 w-3.5" />
                                      {messages.thesis.grading.finalizedBadge}:{' '}
                                      {topic.finalScore}
                                    </div>
                                  ) : null}
                                </div>

                                {/* Supervisor Constraint Warning (R8) */}
                                {isSupervisor ? (
                                  <div className="rounded-lg border border-status-warning/40 bg-status-warning/10 p-3 text-xs text-status-warning-foreground flex items-center gap-2">
                                    <Shield className="h-4 w-4 shrink-0 text-status-warning" />
                                    <span>
                                      {messages.thesis.grading.supervisorCannotGrade}
                                    </span>
                                  </div>
                                ) : !isFinalized ? (
                                  /* Grading Input for Eligible Council Member */
                                  <div className="flex flex-wrap items-center gap-3 pt-1">
                                    <label className="text-xs font-medium text-foreground flex items-center gap-2">
                                      {messages.thesis.grading.scoreInputLabel}:
                                      <Input
                                        type="number"
                                        min="0"
                                        max="10"
                                        step="0.1"
                                        placeholder="8.5"
                                        value={draftScores[key] ?? ''}
                                        onChange={(e) =>
                                          setDraftScores((prev) => ({
                                            ...prev,
                                            [key]: e.target.value,
                                          }))
                                        }
                                        className="h-8 w-24 text-xs font-semibold"
                                        disabled={isActionPending}
                                      />
                                    </label>
                                    <Button
                                      type="button"
                                      size="sm"
                                      className="h-8 text-xs"
                                      onClick={() => void handleSubmitScore(council.id, tid)}
                                      disabled={isActionPending || !draftScores[key]}
                                    >
                                      {messages.thesis.grading.saveScore}
                                    </Button>
                                  </div>
                                ) : null}

                                {/* Scores Breakdown */}
                                {scores.length > 0 && (
                                  <div className="rounded-lg bg-background/60 p-2.5 text-xs space-y-1.5 border border-border/40">
                                    <span className="font-semibold text-muted-foreground uppercase text-[10px] tracking-wider">
                                      {messages.thesis.grading.scoresListTitle}:
                                    </span>
                                    <div className="flex flex-wrap gap-2">
                                      {scores.map((s, sIdx) => {
                                        const lect = lecturers.find(
                                          (l) => l.id === s.lecturerId,
                                        );
                                        const name = lect?.user
                                          ? `${lect.user.lastName} ${lect.user.firstName}`
                                          : s.lecturerId;
                                        return (
                                          <span
                                            key={sIdx}
                                            className="inline-flex items-center rounded-md bg-muted px-2 py-1 text-[11px] font-medium text-foreground border border-border/50"
                                          >
                                            {name}:{' '}
                                            <strong className="ml-1 text-primary">
                                              {s.score}
                                            </strong>
                                          </span>
                                        );
                                      })}
                                    </div>
                                  </div>
                                )}

                                {/* Chair Finalize Action */}
                                {isChair && !isFinalized && (
                                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-t border-border/40 pt-3">
                                    <p className="text-xs text-muted-foreground">
                                      {submittedCount >= eligibleGraderCount
                                        ? messages.thesis.grading.readyToFinalize
                                        : messages.thesis.grading.waitingGrades
                                            .replace('{submitted}', String(submittedCount))
                                            .replace('{total}', String(eligibleGraderCount))}
                                    </p>
                                    <Button
                                      type="button"
                                      size="sm"
                                      variant={canFinalize ? 'default' : 'outline'}
                                      onClick={() => void handleFinalizeScore(council.id, tid)}
                                      disabled={isActionPending || !canFinalize}
                                    >
                                      {messages.thesis.grading.finalizeButton}
                                    </Button>
                                  </div>
                                )}
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  );
                })}
              </CardContent>
            </Card>
  );

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.eyebrow}</SectionEyebrow>}
        title={messages.thesis.title}
        description={messages.thesis.description}
        actions={
          <div className="flex flex-wrap items-end gap-3">
            {isSupervisorOrAdmin && selectedRound ? (
              <Button
                type="button"
                onClick={() => setIsProposeModalOpen(true)}
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
            <CardContent className="grid gap-3 sm:grid-cols-2">
              {([
                ['catalog', '/dashboard/thesis/topics'],
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
                    onClick={() => setLecturerTab('supervision')}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'supervision'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <BookOpen className="h-4 w-4" />
                    <span>1. Đề Tài & Nhóm Hướng Dẫn (GVHD)</span>
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-[10px] font-mono font-bold',
                        lecturerTab === 'supervision'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {supervisedGroups.length} nhóm
                    </span>
                  </button>
                  <button
                    type="button"
                    onClick={() => setLecturerTab('defense')}
                    className={cn(
                      'inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all border',
                      lecturerTab === 'defense'
                        ? 'border-primary bg-primary text-primary-foreground shadow-xs'
                        : 'border-border/70 bg-card text-muted-foreground hover:text-foreground hover:bg-muted/40',
                    )}
                  >
                    <GraduationCap className="h-4 w-4" />
                    <span>2. Hội Đồng Chấm Bảo Vệ (Điều R6 — R8)</span>
                    <span
                      className={cn(
                        'rounded-full px-2 py-0.5 text-[10px] font-mono font-bold',
                        lecturerTab === 'defense'
                          ? 'bg-primary-foreground/20 text-primary-foreground'
                          : 'bg-muted text-foreground',
                      )}
                    >
                      {visibleCouncils.length} hội đồng
                    </span>
                  </button>
                </div>
                <span className="text-xs text-muted-foreground font-medium">
                  Khu vực: <strong className="text-primary">{isAdmin ? 'Quản trị viên' : 'Giảng viên'}</strong> (Khoa CNTT - HCMUTE)
                </span>
              </div>

              {lecturerTab === 'supervision' ? (
                <div className="space-y-6">
                  <Card variant="muted" className="border-primary/20">
                  <CardHeader>
                    <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <CardTitle>{messages.thesis.supervisedGroupsTitle}</CardTitle>
                        <CardDescription>{messages.thesis.supervisedGroupsDescription}</CardDescription>
                      </div>
                      <span className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                        {supervisedGroups.length} {messages.thesis.groups.toLowerCase()}
                      </span>
                    </div>
                  </CardHeader>
                  <CardContent>
                {supervisedGroups.length === 0 ? (
                  <EmptyState
                    icon={UsersRound}
                    title={messages.thesis.noSupervisedGroups}
                    description={messages.thesis.supervisedGroupsDescription}
                    className="min-h-[160px]"
                  />
                ) : (
                  <div className="divide-y divide-border/60">
                    {supervisedGroups.map((group) => {
                      const topicTitle = getTopicTitle(group.topicId);
                      const isPending =
                        group.status === 'SUBMITTED' && group.approvalStatus === 'PENDING';
                      return (
                        <div
                          key={group.id}
                          className="flex flex-col gap-4 py-4 first:pt-0 last:pb-0 lg:flex-row lg:items-center lg:justify-between"
                        >
                          <div className="min-w-0 flex-1 space-y-1.5">
                            <div className="flex flex-wrap items-center gap-2">
                              <StatusBadge status={group.status} />
                              <StatusBadge status={group.approvalStatus} variant="approval" />
                            </div>
                            <h4 className="truncate text-sm font-semibold text-foreground">
                              {topicTitle}
                            </h4>
                            <div className="flex items-center gap-3 text-xs text-muted-foreground">
                              <span>
                                {messages.thesis.memberCount.replace(
                                  '{count}',
                                  String(group.memberStudentIds.length),
                                )}
                              </span>
                              <MemberAvatars memberIds={group.memberStudentIds} max={3} />
                            </div>
                            {group.rejectionReason ? (
                              <p className="text-xs italic text-destructive">
                                {messages.thesis.rejectionReason}: {group.rejectionReason}
                              </p>
                            ) : null}

                            {group.approvalStatus === 'APPROVED' ? (
                              <div className="mt-2 flex flex-wrap items-center gap-2">
                                {supervisedReports[group.id] ? (
                                  <a
                                    href={supervisedReports[group.id]?.url}
                                    target="_blank"
                                    rel="noreferrer"
                                    className="inline-flex items-center gap-1.5 rounded-lg border border-primary/40 bg-primary/10 px-2.5 py-1 text-xs font-semibold text-primary hover:underline transition-colors"
                                  >
                                    <FileText className="h-3.5 w-3.5" />
                                    <span>Báo cáo luận văn: {supervisedReports[group.id]?.title || 'Tài liệu đồ án'}</span>
                                    <ExternalLink className="h-3 w-3" />
                                  </a>
                                ) : (
                                  <span className="inline-flex items-center gap-1 rounded-md bg-muted px-2 py-0.5 text-[11px] text-muted-foreground">
                                    <FileText className="h-3 w-3" />
                                    Chưa nộp báo cáo luận văn
                                  </span>
                                )}
                              </div>
                            ) : null}
                          </div>

                          <div className="flex shrink-0 items-center gap-2">
                            {isPending ? (
                              <>
                                <Button
                                  type="button"
                                  size="sm"
                                  className="bg-emerald-600 text-white hover:bg-emerald-700"
                                  onClick={() => void approveGroup(group.id)}
                                  disabled={isActionPending}
                                >
                                  <Check className="mr-1.5 h-4 w-4" />
                                  {messages.thesis.approve}
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="destructive"
                                  onClick={() => openRejectModal(group.id)}
                                  disabled={isActionPending}
                                >
                                  <X className="mr-1.5 h-4 w-4" />
                                  {messages.thesis.reject}
                                </Button>
                              </>
                            ) : null}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
                  {renderTopicsCard()}
                </div>
              ) : (
                renderCouncilDefenseWorkspace()
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
                      Bàn Làm Việc Sinh Viên & Nhóm Nghiên Cứu (Điều R4, R5, R9)
                    </h4>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      Tuân thủ Quy chế Khoa CNTT: Tối đa 3 SV/nhóm (Điều R4), chỉ Nhóm trưởng nộp báo cáo luận văn (Điều R5), và tra cứu điểm số minh bạch sau khi Hội đồng chốt điểm (Điều R9).
                    </p>
                  </div>
                </div>
                {currentGroup && (
                  <div className="shrink-0 flex items-center gap-2">
                    <span className="rounded-full bg-background px-3 py-1 text-xs font-semibold text-foreground border border-border/80 shadow-2xs">
                      Vai trò của bạn: {isGroupLeader ? 'Nhóm trưởng' : 'Thành viên'}
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
                      <span className="rounded-full bg-emerald-500/15 px-2.5 py-0.5 text-xs font-bold text-emerald-700 dark:text-emerald-300 border border-emerald-500/30">
                        Đã Hoàn Thành Bảo Vệ
                      </span>
                      <span className="text-xs font-semibold text-muted-foreground">
                        Quy chế Điều R7 & Điều R9
                      </span>
                    </div>
                    <h3 className="mt-1 text-base font-bold text-foreground sm:text-lg">
                      Kết Quả Đánh Giá & Điểm Số Khóa Luận Tốt Nghiệp
                    </h3>
                  </div>
                </div>
                <span className="text-xs font-medium text-muted-foreground">
                  Công bố chính thức bởi Hội đồng Khoa CNTT
                </span>
              </div>

              <div className="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-4">
                <div className="rounded-xl border border-emerald-500/30 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Điểm Bảo Vệ (Thang 10)
                  </p>
                  <p className="mt-1 font-mono text-3xl font-extrabold text-emerald-600 dark:text-emerald-400">
                    {studentFinalScore.toFixed(2)}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    Trung bình cộng R7
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Điểm Chữ
                  </p>
                  <p className="mt-1 font-mono text-3xl font-extrabold text-primary">
                    {studentGradeInfo.letter}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    Quy đổi GPA: {studentGradeInfo.gpa4}
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Xếp Loại Tốt Nghiệp
                  </p>
                  <p className="mt-2 text-xl font-bold text-foreground">
                    {studentGradeInfo.rank}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    Theo thang điểm tín chỉ
                  </p>
                </div>

                <div className="rounded-xl border border-border/70 bg-background/80 p-4 text-center shadow-2xs">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    Hội Đồng Đánh Giá
                  </p>
                  <p className="mt-2 text-sm font-bold text-foreground truncate">
                    {myResultItem?.councilName || 'Hội đồng Khoa CNTT'}
                  </p>
                  <p className="mt-1 text-[11px] text-muted-foreground">
                    Trường ĐH SPKT TP.HCM
                  </p>
                </div>
              </div>
            </div>
          )}

              {/* 2-Column Grid for Student */}
              <div className="grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
                {renderStudentGroupCard()}
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
                    placeholder="department-demo"
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
          {lecturers.length > 0 ? (
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
          ) : null}
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
            <Button type="submit" disabled={isActionPending}>
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
                            MSSV: <span className="font-mono">{student.studentNumber}</span> · {student.email}
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
    </div>
  );
}

function MetricCard({
  label,
  value,
  icon,
  tone,
}: {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  tone: StatusTone;
}) {
  return (
    <Card variant="elevated">
      <CardContent className="flex items-start justify-between gap-4 pt-6">
        <div className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-lg', metricToneClass(tone))}>{icon}</div>
        <div className="min-w-0 text-right">
          <div className="break-words text-2xl font-semibold tracking-tight text-foreground">{value}</div>
          <div className="mt-1 text-sm text-muted-foreground">{label}</div>
        </div>
      </CardContent>
    </Card>
  );
}
