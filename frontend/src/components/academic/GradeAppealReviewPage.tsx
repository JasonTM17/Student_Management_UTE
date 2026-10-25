'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, ClipboardCheck, Clock3, RefreshCw, XCircle } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { gradeAppealsApi } from '@/lib/api';
import type { GradeAppeal, GradeAppealReviewRequest, GradeAppealStatus } from '@/types/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { AdminGradeUnpublishPanel } from '@/components/academic/AdminGradeUnpublishPanel';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Textarea } from '@/components/ui/textarea';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { campusCodeMessage } from '@/lib/campus-error';

type Audience = 'lecturer' | 'admin';
type Filter = GradeAppealStatus | 'ALL';
type Decision = 'ACCEPTED' | 'REJECTED';

function tone(status: GradeAppealStatus): StatusTone {
  if (status === 'ACCEPTED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'CANCELLED') return 'neutral';
  return 'warning';
}

function AppealStatus({ status }: { status: GradeAppealStatus }) {
  const { messages } = useI18n();
  const copy = messages.gradeAppeals.review;
  const label = status === 'PENDING' ? copy.pending
    : status === 'ACCEPTED' ? copy.accepted
      : status === 'REJECTED' ? copy.rejected : copy.cancelled;
  const Icon = status === 'ACCEPTED' ? CheckCircle2
    : status === 'REJECTED' ? XCircle : Clock3;
  return (
    <span className={`inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-medium ${statusToneClass(tone(status))}`}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {label}
    </span>
  );
}

export default function GradeAppealReviewPage({ audience }: { audience: Audience }) {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { messages, formatDateTime } = useI18n();
  const copy = messages.gradeAppeals.review;
  const isLecturer = Boolean(user && (user.roles?.includes('LECTURER') || user.role === 'LECTURER'));
  const canAccess = audience === 'admin'
    ? Boolean(user && (isAdmin || isSuperAdmin))
    : isLecturer;
  const [filter, setFilter] = useState<Filter>('PENDING');
  const [appeals, setAppeals] = useState<GradeAppeal[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<GradeAppeal | null>(null);
  const [decision, setDecision] = useState<Decision>('ACCEPTED');
  const [correctedGrade, setCorrectedGrade] = useState('');
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [reviewError, setReviewError] = useState('');
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const rows = audience === 'admin'
        ? await gradeAppealsApi.adminQueue(filter)
        : await gradeAppealsApi.lecturerQueue(filter);
      setAppeals(rows);
    } catch (cause) {
      setError(campusCodeMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setLoading(false);
    }
  }, [audience, copy.loadFailed, filter, messages.common.campusErrors]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const openReview = (appeal: GradeAppeal) => {
    setSelected(appeal);
    setDecision('ACCEPTED');
    setCorrectedGrade('');
    setReason('');
    setConfirmed(false);
    setReviewError('');
  };

  const closeReview = () => {
    if (saving) return;
    setSelected(null);
    setReviewError('');
  };

  const submitReview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!selected) return;
    const cleanReason = reason.trim();
    if (cleanReason.length < 20 || cleanReason.length > 1000) {
      setReviewError(copy.reasonLengthError);
      return;
    }
    if (!confirmed) {
      setReviewError(copy.confirmationRequired);
      return;
    }
    let score: number | undefined;
    if (decision === 'ACCEPTED') {
      if (!/^\d{1,2}(?:\.\d{1,2})?$/.test(correctedGrade.trim())) {
        setReviewError(copy.invalidScore);
        return;
      }
      score = Number(correctedGrade);
      if (!Number.isFinite(score) || score < 0 || score > 10) {
        setReviewError(copy.invalidScore);
        return;
      }
    }

    const request: GradeAppealReviewRequest = {
      decision,
      ...(score === undefined ? {} : { correctedFinalGrade: score }),
      reason: cleanReason,
    };
    setSaving(true);
    setReviewError('');
    try {
      if (audience === 'admin') await gradeAppealsApi.reviewAsAdmin(selected.id, request);
      else await gradeAppealsApi.reviewAsLecturer(selected.id, request);
      setSelected(null);
      toast.success(copy.reviewed);
      await load();
    } catch (cause) {
      setReviewError(campusCodeMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setSaving(false);
    }
  };

  const filters: Array<{ value: Filter; label: string }> = [
    { value: 'PENDING', label: copy.pending },
    { value: 'ACCEPTED', label: copy.accepted },
    { value: 'REJECTED', label: copy.rejected },
    { value: 'CANCELLED', label: copy.cancelled },
    { value: 'ALL', label: copy.all },
  ];

  if (authLoading || isLoggingOut) return <LoadingState label={copy.loading} />;
  if (!user || !canAccess) return <WorkspaceForbiddenState signedIn={Boolean(user)} />;

  const toolbar = (
    <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
      <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
      {copy.refresh}
    </Button>
  );

  const queue = (
    <div className="space-y-6">
      <AdminToolbarCard>
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-sm font-semibold text-foreground">{copy.filter}</p>
            <p className="mt-1 text-sm text-muted-foreground">{copy.tableTitle}</p>
          </div>
          <div className="flex flex-wrap gap-2" role="group" aria-label={copy.filter}>
            {filters.map((item) => (
              <Button key={item.value} type="button" size="sm"
                variant={filter === item.value ? 'default' : 'outline'}
                aria-pressed={filter === item.value} onClick={() => setFilter(item.value)}>
                {item.label}
              </Button>
            ))}
          </div>
        </div>
      </AdminToolbarCard>

      {error ? (
        <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />
      ) : loading ? (
        <LoadingState label={copy.loading} />
      ) : appeals.length === 0 ? (
        <EmptyState icon={ClipboardCheck} title={copy.emptyTitle} description={copy.emptyDescription} />
      ) : (
        <AdminTableCard title={copy.tableTitle} description={copy.pageSummary.replace('{count}', String(appeals.length))}>
          <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
            {appeals.map((appeal) => (
              <article key={appeal.id} className="rounded-xl border border-border/80 bg-card p-4 shadow-xs" role="listitem">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="font-semibold text-foreground">{appeal.studentName || appeal.studentCode}</p>
                    <p className="mt-1 text-sm text-muted-foreground">{appeal.studentCode} · {appeal.courseCode} · {copy.section} {appeal.sectionNumber}</p>
                  </div>
                  <AppealStatus status={appeal.status} />
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                  <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{copy.semester}</dt><dd className="mt-1 text-foreground">{appeal.semesterName}</dd></div>
                  <div><dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{copy.grade}</dt><dd className="mt-1 font-semibold text-foreground">{appeal.finalGrade?.toFixed(2) ?? '—'} · {appeal.letterGrade ?? '—'}</dd></div>
                </dl>
                <div className="mt-4 rounded-lg bg-secondary/40 p-3 text-sm leading-6 text-foreground">
                  <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{copy.reason}</p>
                  <p className="mt-1 whitespace-pre-wrap">{appeal.reason}</p>
                  {appeal.resolutionReason ? <p className="mt-3 border-t border-border/60 pt-2"><span className="font-semibold">{copy.resolutionReason}: </span>{appeal.resolutionReason}</p> : null}
                </div>
                <p className="mt-3 text-xs text-muted-foreground">{copy.submitted}: {formatDateTime(appeal.createdAt)}</p>
                {appeal.status === 'PENDING' ? (
                  <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                    <Button type="button" size="sm" onClick={() => openReview(appeal)}>{copy.review}</Button>
                  </AdminRowActions>
                ) : null}
              </article>
            ))}
          </div>
          <AdminTableScroll className="hidden md:block">
            <table className="w-full min-w-[1000px] text-sm">
              <thead><tr className="border-b border-border/70 bg-secondary/50 text-left text-muted-foreground">
                <th className="px-4 py-3.5 font-medium">{copy.student}</th><th className="px-4 py-3.5 font-medium">{copy.course}</th>
                <th className="px-4 py-3.5 font-medium">{copy.grade}</th><th className="px-4 py-3.5 font-medium">{copy.reason}</th>
                <th className="px-4 py-3.5 font-medium">{copy.status}</th><th className="px-4 py-3.5 font-medium">{copy.submitted}</th>
                <th className="px-4 py-3.5 text-right font-medium">{copy.actions}</th>
              </tr></thead>
              <tbody className="divide-y divide-border/60">
                {appeals.map((appeal) => (
                  <tr key={appeal.id} className="align-top transition-colors hover:bg-muted/40">
                    <td className="px-4 py-4"><p className="font-semibold text-foreground">{appeal.studentName || appeal.studentCode}</p><p className="text-xs text-muted-foreground">{appeal.studentCode}</p></td>
                    <td className="px-4 py-4"><p className="font-medium text-foreground">{appeal.courseCode} · {appeal.sectionNumber}</p><p className="text-xs text-muted-foreground">{appeal.courseName} · {appeal.semesterName}</p></td>
                    <td className="px-4 py-4 font-semibold text-foreground">{appeal.finalGrade?.toFixed(2) ?? '—'} · {appeal.letterGrade ?? '—'}</td>
                    <td className="max-w-sm px-4 py-4"><p className="whitespace-pre-wrap text-foreground">{appeal.reason}</p>{appeal.resolutionReason ? <p className="mt-2 border-t border-border/60 pt-2 text-muted-foreground"><span className="font-semibold">{copy.resolutionReason}: </span>{appeal.resolutionReason}</p> : null}</td>
                    <td className="px-4 py-4"><AppealStatus status={appeal.status} /></td>
                    <td className="px-4 py-4 text-muted-foreground">{formatDateTime(appeal.createdAt)}</td>
                    <td className="px-4 py-4 text-right">{appeal.status === 'PENDING' ? <Button type="button" size="sm" onClick={() => openReview(appeal)}>{copy.review}</Button> : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </AdminTableScroll>
        </AdminTableCard>
      )}
    </div>
  );

  return (
    <>
      {audience === 'admin' ? (
        <AdminFrame title={copy.adminTitle} description={copy.adminDescription} actions={toolbar}>
          <div className="space-y-8">{queue}<AdminGradeUnpublishPanel /></div>
        </AdminFrame>
      ) : (
        <div className="space-y-6">
          <PageHeader eyebrow={<SectionEyebrow>{messages.lecturerGrades.eyebrow}</SectionEyebrow>}
            title={copy.lecturerTitle} description={copy.lecturerDescription} actions={toolbar} />
          {queue}
        </div>
      )}

      <Modal isOpen={Boolean(selected)} onClose={closeReview} title={copy.reviewTitle} className="max-w-xl">
        {selected ? (
          <form className="space-y-5" onSubmit={(event) => void submitReview(event)}>
            <section className="rounded-lg border border-border/70 bg-secondary/20 p-4" aria-label={copy.grade}>
              <p className="font-semibold text-foreground">{selected.studentName} · {selected.studentCode}</p>
              <p className="mt-1 text-sm text-muted-foreground">{selected.courseCode} · {selected.courseName} · {copy.section} {selected.sectionNumber}</p>
              <p className="mt-2 text-sm font-semibold text-foreground">{copy.grade}: {selected.finalGrade?.toFixed(2) ?? '—'} · {selected.letterGrade ?? '—'}</p>
              <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-foreground">{selected.reason}</p>
            </section>

            <fieldset className="space-y-2">
              <legend className="text-sm font-semibold text-foreground">{copy.decision}</legend>
              <div className="flex flex-wrap gap-2">
                <Button type="button" variant={decision === 'ACCEPTED' ? 'default' : 'outline'} aria-pressed={decision === 'ACCEPTED'} onClick={() => setDecision('ACCEPTED')}>{copy.accept}</Button>
                <Button type="button" variant={decision === 'REJECTED' ? 'default' : 'outline'} aria-pressed={decision === 'REJECTED'} onClick={() => setDecision('REJECTED')}>{copy.reject}</Button>
              </div>
            </fieldset>

            {decision === 'ACCEPTED' ? (
              <div className="space-y-1.5">
                <label htmlFor="grade-appeal-corrected-grade" className="text-sm font-medium text-foreground">{copy.correctedGrade}</label>
                <Input id="grade-appeal-corrected-grade" type="number" min="0" max="10" step="0.01" inputMode="decimal" value={correctedGrade} onChange={(event) => setCorrectedGrade(event.target.value)} hint={copy.correctedGradeHint} required />
              </div>
            ) : null}

            <div className="space-y-1.5">
              <label htmlFor="grade-appeal-review-reason" className="text-sm font-medium text-foreground">{copy.reviewReason}</label>
              <Textarea id="grade-appeal-review-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} aria-describedby="grade-appeal-review-reason-hint" placeholder={copy.reviewReasonPlaceholder} required />
              <p id="grade-appeal-review-reason-hint" className="text-xs text-muted-foreground">{copy.reviewReasonHint} · {reason.length}/1000</p>
            </div>
            <label className="flex items-start gap-2 rounded-lg border border-border/70 p-3 text-sm leading-5 text-foreground">
              <input type="checkbox" className="mt-1 h-4 w-4 accent-primary" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
              <span>{copy.confirm}</span>
            </label>
            {reviewError ? <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">{reviewError}</p> : null}
            <div className="flex flex-col-reverse gap-2 border-t border-border/70 pt-4 sm:flex-row sm:justify-end">
              <Button type="button" variant="outline" onClick={closeReview} disabled={saving}>{messages.common.actions.cancel}</Button>
              <Button type="submit" disabled={saving || !confirmed || reason.trim().length < 20 || (decision === 'ACCEPTED' && !correctedGrade)}>
                {saving ? copy.saving : copy.save}
              </Button>
            </div>
          </form>
        ) : null}
      </Modal>
    </>
  );
}
