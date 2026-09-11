'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CalendarDays,
  Check,
  ChevronDown,
  ChevronUp,
  CircleDot,
  FileStack,
  Plus,
  Trash2,
  UserPlus,
  Users,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminMetricCard } from '@/components/admin/AdminSurface';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import { cn } from '@/lib/utils';
import {
  EmptyState,
  ForbiddenState,
  LoadingState,
} from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { useI18n } from '@/i18n';
import { lecturersApi } from '@/lib/api';
import type { Lecturer } from '@/types/api';
import {
  thesisApi,
  type ThesisCouncil,
  type ThesisCouncilMemberRole,
  type ThesisGroup,
  type ThesisRound,
  type ThesisTopic,
} from '@/lib/thesis-api';

export default function AdminThesisPage() {
  const {
    user,
    isAdmin,
    isLecturer,
    isLoading: isAuthLoading,
    isLoggingOut,
    isSuperAdmin,
  } = useAuth();
  const { messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [topics, setTopics] = useState<ThesisTopic[]>([]);
  const [groups, setGroups] = useState<ThesisGroup[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [expandedRoundId, setExpandedRoundId] = useState<string | null>(null);

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

  // Council management state
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [showCreateCouncilModal, setShowCreateCouncilModal] = useState(false);
  const [councilNameInput, setCouncilNameInput] = useState('');
  const [targetRoundIdForCouncil, setTargetRoundIdForCouncil] = useState('');
  const [showAddMemberModal, setShowAddMemberModal] = useState(false);
  const [targetCouncilIdForMember, setTargetCouncilIdForMember] = useState('');
  const [selectedLecturerId, setSelectedLecturerId] = useState('');
  const [showAssignTopicModal, setShowAssignTopicModal] = useState(false);
  const [targetCouncilIdForTopic, setTargetCouncilIdForTopic] = useState('');
  const [selectedTopicIdToAssign, setSelectedTopicIdToAssign] = useState('');

  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const data = await thesisApi.listRounds();
      setRounds(data);
    } catch {
      setError(messages.thesis.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [messages.thesis.loadFailed]);

  useEffect(() => {
    if (canAccess) void loadData();
  }, [canAccess, loadData]);

  const loadRoundDetail = async (roundId: string) => {
    try {
      const [t, g, c] = await Promise.all([
        thesisApi.listTopics(roundId),
        thesisApi.listGroups(roundId),
        thesisApi.listCouncils(roundId),
      ]);
      setTopics(t);
      setGroups(g);
      setCouncils(c);
      if (lecturers.length === 0) {
        try {
          const res = await lecturersApi.getAll({ limit: 100 });
          if (Array.isArray(res?.data)) setLecturers(res.data);
        } catch {
          // ignore lecturer list failure
        }
      }
    } catch {
      setError(messages.thesis.loadFailed);
    }
  };

  const handleCreateCouncil = async (roundId: string) => {
    if (!councilNameInput.trim()) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.createCouncil(roundId, councilNameInput.trim());
      setSuccess(messages.thesis.councils.councilCreatedSuccess);
      setCouncilNameInput('');
      setShowCreateCouncilModal(false);
      const c = await thesisApi.listCouncils(roundId);
      setCouncils(c);
    } catch {
      setError(messages.thesis.actionFailed);
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
    try {
      await thesisApi.addCouncilMember(councilId, selectedLecturerId, nextRole);
      setSuccess(messages.thesis.councils.memberAddedSuccess);
      setSelectedLecturerId('');
      setShowAddMemberModal(false);
      const c = await thesisApi.listCouncils(roundId);
      setCouncils(c);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const handleRemoveMember = async (councilId: string, lecturerId: string, roundId: string) => {
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.removeCouncilMember(councilId, lecturerId);
      setSuccess(messages.thesis.councils.memberRemovedSuccess);
      const c = await thesisApi.listCouncils(roundId);
      setCouncils(c);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const handleAssignTopic = async (councilId: string, roundId: string) => {
    if (!selectedTopicIdToAssign) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.assignTopicToCouncil(councilId, selectedTopicIdToAssign);
      setSuccess(messages.thesis.councils.assignSuccess);
      setSelectedTopicIdToAssign('');
      setShowAssignTopicModal(false);
      const c = await thesisApi.listCouncils(roundId);
      setCouncils(c);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const toggleExpand = (roundId: string) => {
    if (expandedRoundId === roundId) {
      setExpandedRoundId(null);
    } else {
      setExpandedRoundId(roundId);
      void loadRoundDetail(roundId);
    }
  };

  const createRound = async () => {
    if (!formName || !formStart || !formEnd || !formLecturerStart || !formLecturerEnd) {
      setError(messages.thesis.admin.createIncomplete);
      return;
    }
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.createRound({
        name: formName,
        thesisType: formType,
        registrationStart: new Date(formStart).toISOString(),
        registrationEnd: new Date(formEnd).toISOString(),
        lecturerSubmitStart: new Date(formLecturerStart).toISOString(),
        lecturerSubmitEnd: new Date(formLecturerEnd).toISOString(),
        proposalPublishAt: formProposal ? new Date(formProposal).toISOString() : undefined,
        gvpbDeadline: formGvpb ? new Date(formGvpb).toISOString() : undefined,
        reportDate:
          formType === 'KLTN' && formReportDate
            ? new Date(formReportDate).toISOString()
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
      await loadData();
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const transitionRound = async (
    roundId: string,
    action: 'open' | 'close' | 'publish' | 'open-proposals' | 'publish-results',
  ) => {
    setIsSaving(true);
    setError('');
    setSuccess('');
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
        await thesisApi.publishResults(roundId);
        setSuccess(messages.thesis.admin.resultsPublished);
      }
      await loadData();
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

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
  const totalTopics = topics.length;
  const totalGroups = groups.length;

  return (
    <AdminFrame
      title={messages.thesis.admin.title}
      description={messages.thesis.admin.description}
      actions={
        <Button type="button" onClick={() => setShowCreateModal(true)} disabled={!canAccess}>
          <Plus className="mr-2 h-4 w-4" />
          {messages.thesis.admin.createRound}
        </Button>
      }
    >
      {error ? (
        <div className="mb-6 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}
      {success ? (
        <div className="mb-6 flex items-center gap-3 rounded-lg border border-status-success/30 bg-status-success/12 px-4 py-3 text-sm text-status-success-foreground">
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
            label={messages.thesis.topics}
            value={totalTopics}
            icon={<FileStack className="h-5 w-5" />}
            toneClassName={metricToneClass('warning')}
          />
          <AdminMetricCard
            label={messages.thesis.groups}
            value={totalGroups}
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
              return (
                <Card key={round.id} className="overflow-hidden">
                  <CardHeader
                    className="cursor-pointer transition-colors hover:bg-secondary/30"
                    onClick={() => toggleExpand(round.id)}
                  >
                    <div className="flex items-center justify-between gap-4">
                      <div className="flex items-center gap-4">
                        <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary/10 text-primary">
                          <CalendarDays className="h-5 w-5" />
                        </div>
                        <div>
                          <CardTitle className="text-lg">{round.name}</CardTitle>
                          <CardDescription className="mt-1">
                            {messages.thesis.admin.thesisTypeOptions[round.thesisType as keyof typeof messages.thesis.admin.thesisTypeOptions] ?? round.thesisType} · {new Date(round.registrationStart).toLocaleString()} → {new Date(round.registrationEnd).toLocaleString()}
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
                      <div className="flex flex-wrap gap-2 pt-2">
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
                          <Button type="button" size="sm" variant="outline" onClick={() => void transitionRound(round.id, 'open')} disabled={isSaving}>
                            {messages.thesis.admin.openRegistration}
                          </Button>
                        ) : null}
                        {round.status === 'REGISTRATION_OPEN' ? (
                          <Button type="button" size="sm" variant="outline" onClick={() => void transitionRound(round.id, 'close')} disabled={isSaving}>
                            {messages.thesis.admin.closeRegistration}
                          </Button>
                        ) : null}
                        {round.status === 'REGISTRATION_CLOSED' ? (
                          <Button type="button" size="sm" onClick={() => void transitionRound(round.id, 'publish-results')} disabled={isSaving}>
                            {messages.thesis.admin.publishResults}
                          </Button>
                        ) : null}
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
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            onClick={() => {
                              setTargetRoundIdForCouncil(round.id);
                              setShowCreateCouncilModal(true);
                            }}
                          >
                            <Plus className="mr-1.5 h-3.5 w-3.5" />
                            {messages.thesis.councils.createCouncil}
                          </Button>
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
                                        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium',
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
                                      {members.length < 5 && (
                                        <Button
                                          type="button"
                                          variant="ghost"
                                          size="sm"
                                          className="h-7 text-xs px-2 text-primary"
                                          onClick={() => {
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
                                                    'rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wider',
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
                                              <button
                                                type="button"
                                                className="text-muted-foreground hover:text-destructive p-1 transition-colors"
                                                title={messages.thesis.councils.removeMember}
                                                onClick={() => void handleRemoveMember(council.id, m.lecturerId, round.id)}
                                                disabled={isSaving}
                                              >
                                                <Trash2 className="h-3.5 w-3.5" />
                                              </button>
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
                                      {isComplete && (
                                        <Button
                                          type="button"
                                          variant="ghost"
                                          size="sm"
                                          className="h-7 text-xs px-2 text-primary"
                                          onClick={() => {
                                            setTargetCouncilIdForTopic(council.id);
                                            setTargetRoundIdForCouncil(round.id);
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
                                          return (
                                            <div
                                              key={tid}
                                              className="flex items-center justify-between rounded-lg border border-border/50 bg-background/50 px-2.5 py-1.5 text-xs"
                                            >
                                              <span className="font-medium text-foreground line-clamp-1">
                                                {top?.title || tid}
                                              </span>
                                              {top?.finalScore != null && (
                                                <span className="shrink-0 rounded bg-status-success/20 px-1.5 py-0.5 text-[10px] font-semibold text-status-success-foreground">
                                                  {top.finalScore} điểm
                                                </span>
                                              )}
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
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.roundName}
            <Input placeholder={messages.thesis.admin.roundNamePlaceholder} value={formName} onChange={(e) => setFormName(e.target.value)} />
          </label>
          <Select
            label={messages.thesis.admin.thesisType}
            value={formType}
            onChange={(e) => setFormType(e.target.value)}
            options={[
              { value: 'MON_HOC', label: messages.thesis.admin.thesisTypeOptions.MON_HOC },
              { value: 'NCKH', label: messages.thesis.admin.thesisTypeOptions.NCKH },
              { value: 'TLCN', label: messages.thesis.admin.thesisTypeOptions.TLCN },
              { value: 'KLTN', label: messages.thesis.admin.thesisTypeOptions.KLTN },
            ]}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationStart}
              <Input type="datetime-local" value={formStart} onChange={(e) => setFormStart(e.target.value)} />
            </label>
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.registrationEnd}
              <Input type="datetime-local" value={formEnd} onChange={(e) => setFormEnd(e.target.value)} />
            </label>
          </div>
          <div>
            <p className="text-sm font-medium text-foreground">{messages.thesis.admin.lecturerWindow}</p>
            <div className="mt-2 grid gap-4 sm:grid-cols-2">
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitStart}
                <Input type="datetime-local" value={formLecturerStart} onChange={(e) => setFormLecturerStart(e.target.value)} />
              </label>
              <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                {messages.thesis.admin.lecturerSubmitEnd}
                <Input type="datetime-local" value={formLecturerEnd} onChange={(e) => setFormLecturerEnd(e.target.value)} />
              </label>
            </div>
          </div>
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.proposalPublishAt}
            <Input type="datetime-local" value={formProposal} onChange={(e) => setFormProposal(e.target.value)} />
          </label>
          {formType === 'TLCN' || formType === 'KLTN' ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.gvpbDeadline}
              <Input type="datetime-local" value={formGvpb} onChange={(e) => setFormGvpb(e.target.value)} />
            </label>
          ) : null}
          {formType === 'KLTN' ? (
            <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
              {messages.thesis.admin.reportDate}
              <Input type="datetime-local" value={formReportDate} onChange={(e) => setFormReportDate(e.target.value)} />
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

            return (
              <>
                <div className="rounded-lg border border-border/60 bg-muted/30 p-3 text-xs flex items-center justify-between">
                  <span className="text-muted-foreground">Ghế tiếp theo (Bắt buộc):</span>
                  <span className="font-semibold text-primary">{nextRoleLabel}</span>
                </div>
                <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                  {messages.thesis.councils.selectLecturer}
                  <select
                    className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                    value={selectedLecturerId}
                    onChange={(e) => setSelectedLecturerId(e.target.value)}
                  >
                    <option value="">-- {messages.thesis.councils.selectLecturer} --</option>
                    {availableLecturers.map((l) => {
                      const name = l.user
                        ? `${l.user.lastName} ${l.user.firstName} (${l.employeeId || l.user.email})`
                        : l.id;
                      return (
                        <option key={l.id} value={l.id}>
                          {name}
                        </option>
                      );
                    })}
                  </select>
                </label>
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

      {/* Assign Topic Modal */}
      <Modal
        isOpen={showAssignTopicModal}
        onClose={() => setShowAssignTopicModal(false)}
        title={messages.thesis.councils.assignTopic}
      >
        <div className="space-y-4">
          {(() => {
            const allAssigned = new Set<string>();
            for (const c of councils) {
              for (const tid of (c as unknown as { topicIds?: string[] }).topicIds || []) {
                allAssigned.add(tid);
              }
            }
            const assignableTopics = topics.filter(
              (t) => !allAssigned.has(t.id) && t.finalScore == null
            );

            return (
              <>
                <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
                  {messages.thesis.councils.selectTopic}
                  {assignableTopics.length === 0 ? (
                    <p className="text-xs text-muted-foreground italic py-2">
                      {messages.thesis.councils.noAssignedTopics}
                    </p>
                  ) : (
                    <select
                      className="flex h-10 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      value={selectedTopicIdToAssign}
                      onChange={(e) => setSelectedTopicIdToAssign(e.target.value)}
                    >
                      <option value="">-- {messages.thesis.councils.selectTopic} --</option>
                      {assignableTopics.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.title}
                        </option>
                      ))}
                    </select>
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
    </AdminFrame>
  );
}
