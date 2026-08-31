'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  CalendarDays,
  Check,
  ChevronDown,
  ChevronUp,
  CircleDot,
  FileStack,
  Plus,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminMetricCard } from '@/components/admin/AdminSurface';
import { metricToneClass } from '@/components/ui/status';
import {
  EmptyState,
  ForbiddenState,
  LoadingState,
} from '@/components/ui/state-block';
import { StatusBadge } from '@/components/thesis/StatusBadge';
import { useI18n } from '@/i18n';
import {
  thesisApi,
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
  const [formType, setFormType] = useState('KHOA_LUAN');
  const [formStart, setFormStart] = useState('');
  const [formEnd, setFormEnd] = useState('');
  const [formProposal, setFormProposal] = useState('');

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
      const [t, g] = await Promise.all([
        thesisApi.listTopics(roundId),
        thesisApi.listGroups(roundId),
      ]);
      setTopics(t);
      setGroups(g);
    } catch {
      setError(messages.thesis.loadFailed);
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
    if (!formName || !formStart || !formEnd) {
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
        proposalPublishAt: formProposal ? new Date(formProposal).toISOString() : undefined,
      });
      setSuccess(messages.thesis.admin.created);
      setShowCreateModal(false);
      setFormName('');
      setFormStart('');
      setFormEnd('');
      setFormProposal('');
      await loadData();
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const transitionRound = async (roundId: string, action: 'open' | 'close' | 'publish') => {
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      if (action === 'open') {
        await thesisApi.openRegistration(roundId);
        setSuccess(messages.thesis.admin.registrationOpened);
      } else if (action === 'close') {
        await thesisApi.closeRegistration(roundId);
        setSuccess(messages.thesis.admin.registrationClosed);
      } else {
        await thesisApi.publishProposals(roundId);
        setSuccess(messages.thesis.admin.proposalsPublished);
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
                            {round.thesisType} · {new Date(round.registrationStart).toLocaleString()} → {new Date(round.registrationEnd).toLocaleString()}
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
                          <Button type="button" size="sm" onClick={() => void transitionRound(round.id, 'publish')} disabled={isSaving}>
                            {messages.thesis.admin.publishProposals}
                          </Button>
                        ) : null}
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
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.thesisType}
            <Input placeholder="KHOA_LUAN" value={formType} onChange={(e) => setFormType(e.target.value)} />
          </label>
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
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.admin.proposalPublishAt}
            <Input type="datetime-local" value={formProposal} onChange={(e) => setFormProposal(e.target.value)} />
          </label>
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
    </AdminFrame>
  );
}
