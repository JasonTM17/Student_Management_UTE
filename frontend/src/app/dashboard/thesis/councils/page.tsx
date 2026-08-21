'use client';

import { useEffect, useState } from 'react';
import {
  CalendarCheck,
  Check,
  GraduationCap,
  Plus,
  UserCog,
} from 'lucide-react';
import { useAuth, useRequireAuth } from '@/context/AuthContext';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Modal } from '@/components/ui/modal';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import {
  thesisApi,
  type ThesisCouncil,
  type ThesisRound,
} from '@/lib/thesis-api';

export default function ThesisCouncilsPage() {
  const { isLecturer, isAdmin } = useAuth();
  const { isLoading: authLoading, hasAccess } = useRequireAuth([
    'LECTURER',
    'ADMIN',
  ]);
  const { formatDateTime, messages } = useI18n();
  const [rounds, setRounds] = useState<ThesisRound[]>([]);
  const [councils, setCouncils] = useState<ThesisCouncil[]>([]);
  const [selectedRoundId, setSelectedRoundId] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  const [activeCouncil, setActiveCouncil] = useState<ThesisCouncil | null>(null);
  const [scheduleDate, setScheduleDate] = useState('');
  const [scheduleRoom, setScheduleRoom] = useState('');

  const canManage = isLecturer || isAdmin;

  useEffect(() => {
    let cancelled = false;
    const loadRounds = async () => {
      setIsLoading(true);
      setError('');
      try {
        const data = await thesisApi.listRounds();
        if (cancelled) return;
        setRounds(data);
        setSelectedRoundId((curr) => curr || data[0]?.id || '');
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

  useEffect(() => {
    if (!selectedRoundId) {
      setCouncils([]);
      return;
    }
    let cancelled = false;
    const loadCouncils = async () => {
      setError('');
      try {
        const data = await thesisApi.listCouncils(selectedRoundId);
        if (cancelled) return;
        setCouncils(data);
      } catch {
        if (!cancelled) setError(messages.thesis.loadFailed);
      }
    };
    void loadCouncils();
    return () => {
      cancelled = true;
    };
  }, [messages.thesis.loadFailed, selectedRoundId, success]);

  const openSchedule = (council: ThesisCouncil) => {
    setActiveCouncil(council);
    setScheduleDate('');
    setScheduleRoom('');
    setShowScheduleModal(true);
  };

  const submitSchedule = async () => {
    if (!activeCouncil || !scheduleDate || !scheduleRoom) return;
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.scheduleCouncil(activeCouncil.id, new Date(scheduleDate).toISOString(), scheduleRoom);
      setSuccess(messages.thesis.councils.scheduled);
      setShowScheduleModal(false);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const openScoring = async (councilId: string) => {
    setIsSaving(true);
    setError('');
    setSuccess('');
    try {
      await thesisApi.openScoring(councilId);
      setSuccess(messages.thesis.councils.scoringOpened);
    } catch {
      setError(messages.thesis.actionFailed);
    } finally {
      setIsSaving(false);
    }
  };

  if (authLoading || !hasAccess || isLoading) {
    return <LoadingState label={messages.thesis.loading} />;
  }

  if (!canManage) {
    return (
      <ErrorState
        title={messages.thesis.review.unauthorized}
        description={messages.thesis.review.unauthorizedDescription}
      />
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.thesis.councils.eyebrow}</SectionEyebrow>}
        title={messages.thesis.councils.title}
        description={messages.thesis.councils.description}
        actions={
          <label className="flex min-w-[15rem] flex-col gap-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {messages.thesis.selectRound}
            <select
              value={selectedRoundId}
              onChange={(e) => setSelectedRoundId(e.target.value)}
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
        }
      />

      {success ? (
        <div className="flex items-center gap-3 rounded-lg border border-emerald-500/30 bg-emerald-500/5 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">
          <Check className="h-4 w-4" />
          {success}
        </div>
      ) : null}
      {error ? (
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}
        </div>
      ) : null}

      {councils.length === 0 ? (
        <EmptyState
          icon={GraduationCap}
          title={messages.thesis.councils.empty}
          description={messages.thesis.councils.emptyDescription}
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {councils.map((council) => (
            <Card key={council.id} className="h-full">
              <CardHeader>
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-cyan-500/12 text-cyan-700 dark:text-cyan-300">
                      <GraduationCap className="h-5 w-5" />
                    </div>
                    <div>
                      <CardTitle className="text-base">
                        {council.room ?? council.id.slice(0, 8)}
                      </CardTitle>
                      <CardDescription className="mt-1 font-mono text-xs">
                        {council.id.slice(0, 8)}…
                      </CardDescription>
                    </div>
                  </div>
                  <span
                    className={cn(
                      'rounded-full px-2.5 py-1 text-xs font-semibold',
                      council.status === 'FINALIZED'
                        ? 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300'
                        : council.status === 'SCHEDULED'
                          ? 'bg-cyan-500/12 text-cyan-700 dark:text-cyan-300'
                          : 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
                    )}
                  >
                    {council.status}
                  </span>
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2 text-sm">
                  <div className="flex items-center justify-between">
                    <span className="text-muted-foreground">{messages.thesis.review.members}</span>
                    <span className="font-medium text-foreground">{council.members.length}</span>
                  </div>
                  {council.scheduledAt ? (
                    <div className="flex items-center justify-between">
                      <span className="text-muted-foreground">{messages.thesis.councils.scheduledAt}</span>
                      <span className="font-medium text-foreground">{formatDateTime(council.scheduledAt)}</span>
                    </div>
                  ) : null}
                </div>
                <div className="flex gap-2">
                  {council.status === 'DRAFT' ? (
                    <Button type="button" size="sm" variant="outline" className="flex-1" onClick={() => openSchedule(council)} disabled={isSaving}>
                      <CalendarCheck className="mr-2 h-4 w-4" />
                      {messages.thesis.councils.schedule}
                    </Button>
                  ) : null}
                  {council.status === 'SCHEDULED' ? (
                    <Button type="button" size="sm" className="flex-1" onClick={() => void openScoring(council.id)} disabled={isSaving}>
                      <UserCog className="mr-2 h-4 w-4" />
                      {messages.thesis.councils.openScoring}
                    </Button>
                  ) : null}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Modal
        isOpen={showScheduleModal}
        onClose={() => setShowScheduleModal(false)}
        title={messages.thesis.councils.scheduleTitle}
      >
        <div className="space-y-4">
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.councils.room}
            <Input
              placeholder={messages.thesis.councils.roomPlaceholder}
              value={scheduleRoom}
              onChange={(e) => setScheduleRoom(e.target.value)}
            />
          </label>
          <label className="flex flex-col gap-2 text-sm font-medium text-foreground">
            {messages.thesis.councils.date}
            <Input
              type="datetime-local"
              value={scheduleDate}
              onChange={(e) => setScheduleDate(e.target.value)}
            />
          </label>
          <div className="flex justify-end gap-2 pt-2">
            <Button type="button" variant="outline" onClick={() => setShowScheduleModal(false)}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="button" onClick={() => void submitSchedule()} disabled={isSaving || !scheduleDate || !scheduleRoom}>
              <CalendarCheck className="mr-2 h-4 w-4" />
              {messages.thesis.councils.schedule}
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
}
