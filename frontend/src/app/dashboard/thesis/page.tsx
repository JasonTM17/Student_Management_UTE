'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowUpRight,
  Building2,
  CalendarDays,
  Check,
  CircleDot,
  FileStack,
  Plus,
  Shield,
  Trash2,
  UserPlus,
  UsersRound,
  X,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
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
import { departmentsApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import type { Department } from '@/types/api';
import {
  thesisApi,
  type ThesisGroup,
  type ThesisGroupMember,
  type ThesisRound,
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

export default function ThesisPage() {
  const { user, isStudent, isLecturer, isAdmin } = useAuth();
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

  useEffect(() => {
    let cancelled = false;

    const loadRounds = async () => {
      setIsLoading(true);
      setError('');
      try {
        const nextRounds = await thesisApi.listRounds();
        if (cancelled) return;
        setRounds(nextRounds);
        setSelectedRoundId((current) => current || nextRounds[0]?.id || '');
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

  const isSupervisorOrAdmin = Boolean(isLecturer || isAdmin);
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
        await Promise.all([
          refreshTopics(selectedRoundId),
          thesisApi.listGroups(selectedRoundId).then((next) => {
            if (!cancelled) setGroups(next);
          }),
        ]);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };

    void loadWorkspace();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, refreshTopics, selectedRoundId]);

  const selectedRound = rounds.find((round) => round.id === selectedRoundId);
  const studentId = user?.studentId ?? '';
  const currentGroup = groups.find(
    (group) =>
      group.leaderStudentId === studentId ||
      group.memberStudentIds.includes(studentId) ||
      Boolean(group.members?.some((m) => m.studentId === studentId)),
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

  const myTopics = isSupervisorOrAdmin
    ? topics.filter(
        (topic) =>
          isAdmin ||
          topic.createdBy === user?.id ||
          (user?.lecturerId && topic.createdBy === user.lecturerId),
      )
    : [];
  const myTopicIds = new Set(myTopics.map((topic) => topic.id));

  const supervisedGroups = isSupervisorOrAdmin
    ? groups.filter((group) => group.topicId && (isAdmin || myTopicIds.has(group.topicId)))
    : [];

  const displayedTopics =
    isSupervisorOrAdmin && topicFilter === 'my' ? myTopics : topics;

  const getTopicTitle = (topicId?: string | null) => {
    if (!topicId) return '—';
    const found = topics.find((topic) => topic.id === topicId);
    return found ? found.title : topicId;
  };

  const statusLabel = (status: string) =>
    messages.thesis.status[status as keyof typeof messages.thesis.status] ??
    messages.common.statuses[status.toUpperCase() as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

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
      await refreshTopics(selectedRoundId);
      setIsProposeModalOpen(false);
      setProposeTitle('');
      setProposeDescription('');
      setProposeDepartmentId(departments[0]?.id || 'department-demo');
      setProposeMaxGroups(2);
      setProposePublishImmediately(true);
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
            <label className="flex min-w-[15rem] flex-col gap-2 text-left text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {messages.thesis.selectRound}
              <select
                value={selectedRoundId}
                onChange={(event) => setSelectedRoundId(event.target.value)}
                className="h-11 rounded-lg border border-border/80 bg-card px-3 text-sm font-medium normal-case tracking-normal text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                aria-label={messages.thesis.selectRound}
              >
                {rounds.map((round) => (
                  <option key={round.id} value={round.id}>
                    {round.name}
                  </option>
                ))}
              </select>
            </label>
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
          <Card className="overflow-hidden border border-border/80 bg-card text-card-foreground shadow-sm">
            <CardContent className="relative grid gap-8 p-6 sm:p-8 lg:grid-cols-[1.2fr_0.8fr] lg:items-end">
              <div className="space-y-4">
                <div className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                  <CircleDot className="h-3.5 w-3.5" />
                  {selectedRound.thesisType}
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">{messages.thesis.roundStatus}</p>
                  <h2 className="mt-2 text-3xl font-semibold tracking-tight text-foreground sm:text-4xl">
                    {selectedRound.name}
                  </h2>
                </div>
              </div>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
                <div className="border-l-2 border-primary/30 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {messages.thesis.roundStatus}
                  </p>
                  <p className="mt-2 text-lg font-semibold text-foreground">{statusLabel(selectedRound.status)}</p>
                </div>
                <div className="border-l-2 border-primary/30 pl-4">
                  <p className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {messages.thesis.registrationWindow}
                  </p>
                  <p className="mt-2 text-sm leading-6 text-foreground/90">
                    {formatDateTime(selectedRound.registrationStart)}
                    <span className="mx-1 text-muted-foreground">→</span>
                    {formatDateTime(selectedRound.registrationEnd)}
                  </p>
                </div>
              </div>
              <div className="pointer-events-none absolute -bottom-16 right-8 h-48 w-48 rounded-full border border-primary/10 sm:right-24" />
            </CardContent>
          </Card>

          {error ? <ErrorState title={messages.thesis.loadFailed} description={error} /> : null}
          {actionError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {actionError}
            </div>
          ) : null}
          {actionSuccess ? (
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
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
                              <span className="font-mono text-xs text-muted-foreground">
                                {group.id.slice(0, 8)}…
                              </span>
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
          ) : null}

          <div className="grid gap-6 xl:grid-cols-[0.92fr_1.08fr]">
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
                      isStudent && selectedRound.status === 'REGISTRATION_OPEN' ? (
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
                            {messages.thesis.status[currentGroup.status] ?? currentGroup.status}
                          </span>
                          <StatusBadge status={currentGroup.approvalStatus} variant="approval" />
                        </div>
                        <h4 className="text-sm font-semibold text-foreground truncate">
                          {currentGroup.topicId ? getTopicTitle(currentGroup.topicId) : messages.thesis.chooseTopic}
                        </h4>
                        <p className="font-mono text-xs text-muted-foreground">
                          ID: {currentGroup.id.slice(0, 8)}…{currentGroup.id.slice(-4)}
                        </p>
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
                        {groupMemberList.map((member) => (
                          <div
                            key={member.studentId || member.displayName}
                            className="flex items-center justify-between gap-3 rounded-xl border border-border/70 bg-card p-3 shadow-2xs transition-colors hover:border-primary/40"
                          >
                            <div className="flex items-center gap-3 min-w-0">
                              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                                {(member.displayName || member.studentId || 'SV').slice(0, 2).toUpperCase()}
                              </div>
                              <div className="min-w-0 space-y-0.5">
                                <div className="flex flex-wrap items-center gap-1.5">
                                  <span className="font-semibold text-sm text-foreground truncate">
                                    {member.displayName || member.studentId}
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
                                  {member.studentId && !member.isExternal ? (
                                    <span>MSSV: <strong className="font-mono text-foreground/80">{member.studentId}</strong></span>
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
                        ))}
                      </div>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

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
          </div>
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
              <label className="mb-1 block text-sm font-medium text-foreground">
                {messages.thesis.topicDepartmentLabel}
              </label>
              {departments.length > 0 ? (
                <select
                  value={proposeDepartmentId}
                  onChange={(e) => setProposeDepartmentId(e.target.value)}
                  required
                  disabled={isActionPending}
                  className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {departments.map((dept) => (
                    <option key={dept.id} value={dept.id}>
                      {dept.code} - {getLocalizedName(locale, dept, dept.name)}
                    </option>
                  ))}
                </select>
              ) : (
                <Input
                  value={proposeDepartmentId}
                  onChange={(e) => setProposeDepartmentId(e.target.value)}
                  placeholder="department-demo"
                  required
                  disabled={isActionPending}
                />
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
                <label className="mb-1 block text-sm font-medium text-foreground">
                  {messages.thesis.memberNameLabel} <span className="text-destructive">*</span>
                </label>
                <Input
                  value={memberName}
                  onChange={(e) => setMemberName(e.target.value)}
                  placeholder={messages.thesis.memberNamePlaceholder}
                  required
                  disabled={isActionPending}
                  className="h-10"
                />
              </div>
              <div>
                <label className="mb-1 block text-sm font-medium text-foreground">
                  {messages.thesis.memberContactLabel}
                </label>
                <Input
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
