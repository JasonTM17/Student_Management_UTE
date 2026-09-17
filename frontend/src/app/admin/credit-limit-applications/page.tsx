'use client';

import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { CheckCircle2, ClipboardCheck, Clock3, RefreshCw, XCircle } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  adminRegistrationApi,
  type CreditLimitApplication,
} from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Modal } from '@/components/ui/modal';
import { Textarea } from '@/components/ui/textarea';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { campusErrorMessage } from '@/lib/campus-error';

type StatusFilter = 'PENDING' | 'APPROVED' | 'REJECTED' | 'ALL';
type ReviewDecision = 'APPROVED' | 'REJECTED';

function applicationTone(status: CreditLimitApplication['status']): StatusTone {
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  return 'warning';
}

function applicationIcon(status: CreditLimitApplication['status']) {
  if (status === 'APPROVED') return CheckCircle2;
  if (status === 'REJECTED') return XCircle;
  return Clock3;
}

export default function AdminCreditLimitApplicationsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { href, messages, formatDateTime } = useI18n();
  const router = useRouter();
  const copy = messages.admin.creditLimitApplications;
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const [filter, setFilter] = useState<StatusFilter>('PENDING');
  const [applications, setApplications] = useState<CreditLimitApplication[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState<CreditLimitApplication | null>(null);
  const [decision, setDecision] = useState<ReviewDecision>('APPROVED');
  const [note, setNote] = useState('');
  const [reviewError, setReviewError] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (authLoading || isLoggingOut) return;
    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }
    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [authLoading, href, isAdmin, isLoggingOut, isSuperAdmin, router, user]);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setApplications(await adminRegistrationApi.creditLimitApplications(filter));
    } catch (cause) {
      setError(campusErrorMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setLoading(false);
    }
  }, [copy.loadFailed, filter, messages.common.campusErrors]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const openReview = (application: CreditLimitApplication) => {
    setSelected(application);
    setDecision('APPROVED');
    setNote('');
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
    const cleanNote = note.trim();
    if (decision === 'REJECTED' && !cleanNote) {
      setReviewError(copy.rejectionNoteRequired);
      return;
    }

    setSaving(true);
    setReviewError('');
    try {
      await adminRegistrationApi.reviewCreditLimitApplication(
        selected.id,
        decision,
        cleanNote || undefined,
      );
      setSelected(null);
      toast.success(copy.reviewed);
      await load();
    } catch (cause) {
      setReviewError(campusErrorMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setSaving(false);
    }
  };

  const statusLabel = (status: CreditLimitApplication['status']) => {
    if (status === 'APPROVED') return copy.statusApproved;
    if (status === 'REJECTED') return copy.statusRejected;
    return copy.statusPending;
  };

  if (authLoading || isLoggingOut) {
    return <LoadingState label={copy.loading} />;
  }
  if (!user || !canAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const filters: Array<{ value: StatusFilter; label: string }> = [
    { value: 'PENDING', label: copy.pending },
    { value: 'APPROVED', label: copy.approved },
    { value: 'REJECTED', label: copy.rejected },
    { value: 'ALL', label: copy.all },
  ];

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <Button type="button" variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`mr-2 h-4 w-4 ${loading ? 'animate-spin' : ''}`} aria-hidden="true" />
          {copy.refresh}
        </Button>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-semibold text-foreground">{copy.filter}</p>
              <p className="mt-1 text-sm text-muted-foreground">{copy.tableTitle}</p>
            </div>
            <div className="flex flex-wrap gap-2" role="group" aria-label={copy.filter}>
              {filters.map((item) => (
                <Button
                  key={item.value}
                  type="button"
                  size="sm"
                  variant={filter === item.value ? 'default' : 'outline'}
                  aria-pressed={filter === item.value}
                  onClick={() => setFilter(item.value)}
                >
                  {item.label}
                </Button>
              ))}
            </div>
          </div>
        </AdminToolbarCard>

        <Card className="border-primary/20 bg-primary/[0.04]">
          <CardContent className="grid gap-4 p-4 text-sm md:grid-cols-[auto_minmax(0,1fr)] md:items-center">
            <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <ClipboardCheck className="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <p className="font-semibold text-foreground">28 {messages.courseRegistration.creditsUnit} · {copy.approve} = 30</p>
              <p className="mt-1 leading-6 text-muted-foreground">{copy.description}</p>
            </div>
          </CardContent>
        </Card>

        {error ? (
          <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />
        ) : loading ? (
          <LoadingState label={copy.loading} />
        ) : applications.length === 0 ? (
          <EmptyState
            icon={ClipboardCheck}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
          />
        ) : (
          <AdminTableCard title={copy.tableTitle} description={copy.pageSummary.replace('{count}', String(applications.length))}>
            <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
              {applications.map((application) => {
                const Icon = applicationIcon(application.status);
                const pending = application.status === 'PENDING';
                return (
                  <article key={application.id} className="rounded-xl border border-border/80 bg-card p-4 shadow-xs" role="listitem">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-semibold text-foreground">{application.studentName || application.studentCode}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{application.studentCode} · {application.studentEmail}</p>
                      </div>
                      <span className={`inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(applicationTone(application.status))}`}>
                        <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                        {statusLabel(application.status)}
                      </span>
                    </div>
                    <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{copy.term}</dt>
                        <dd className="mt-1 text-foreground">{application.semesterName}</dd>
                      </div>
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{copy.requestedLimit}</dt>
                        <dd className="mt-1 font-semibold text-foreground">{application.standardLimit} → {application.requestedLimit}</dd>
                      </div>
                    </dl>
                    <div className="mt-4 rounded-lg bg-secondary/40 p-3 text-sm leading-6 text-foreground">
                      <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{copy.reason}</p>
                      <p className="mt-1 whitespace-pre-wrap">{application.reason}</p>
                    </div>
                    <p className="mt-3 text-xs text-muted-foreground">{copy.submitted}: {formatDateTime(application.createdAt)}</p>
                    {pending ? (
                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button type="button" size="sm" onClick={() => openReview(application)}>
                          <ClipboardCheck className="mr-2 h-4 w-4" aria-hidden="true" />
                          {copy.review}
                        </Button>
                      </AdminRowActions>
                    ) : null}
                  </article>
                );
              })}
            </div>

            <AdminTableScroll className="hidden md:block">
              <table className="w-full min-w-[980px] text-sm">
                <thead>
                  <tr className="border-b border-border/70 bg-secondary/50 text-left text-muted-foreground">
                    <th className="px-4 py-3.5 font-medium">{copy.student}</th>
                    <th className="px-4 py-3.5 font-medium">{copy.term}</th>
                    <th className="px-4 py-3.5 font-medium">{copy.requestedLimit}</th>
                    <th className="px-4 py-3.5 font-medium">{copy.reason}</th>
                    <th className="px-4 py-3.5 font-medium">{copy.status}</th>
                    <th className="px-4 py-3.5 font-medium">{copy.submitted}</th>
                    <th className="px-4 py-3.5 text-right font-medium">{copy.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {applications.map((application) => {
                    const Icon = applicationIcon(application.status);
                    const pending = application.status === 'PENDING';
                    return (
                      <tr key={application.id} className="align-top transition-colors hover:bg-muted/40">
                        <td className="px-4 py-4">
                          <p className="font-semibold text-foreground">{application.studentName || application.studentCode}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{application.studentCode}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{application.studentEmail}</p>
                        </td>
                        <td className="px-4 py-4 text-foreground">{application.semesterName}<p className="mt-1 text-xs text-muted-foreground">{application.roundName}</p></td>
                        <td className="px-4 py-4 font-semibold text-foreground">{application.standardLimit} → {application.requestedLimit}</td>
                        <td className="max-w-[320px] px-4 py-4 leading-6 text-muted-foreground">{application.reason}</td>
                        <td className="px-4 py-4">
                          <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(applicationTone(application.status))}`}>
                            <Icon className="h-3.5 w-3.5" aria-hidden="true" />
                            {statusLabel(application.status)}
                          </span>
                        </td>
                        <td className="whitespace-nowrap px-4 py-4 text-muted-foreground">{formatDateTime(application.createdAt)}</td>
                        <td className="px-4 py-4">
                          <AdminRowActions>
                            {pending ? (
                              <Button type="button" size="sm" onClick={() => openReview(application)}>
                                {copy.review}
                              </Button>
                            ) : <span className="text-xs text-muted-foreground">{copy.reviewedAt}: {application.reviewedAt ? formatDateTime(application.reviewedAt) : '—'}</span>}
                          </AdminRowActions>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>

      <Modal
        isOpen={Boolean(selected)}
        onClose={closeReview}
        title={copy.review}
        description={selected ? `${selected.studentCode} · ${selected.semesterName}` : undefined}
        closeLabel={copy.cancel}
        className="max-w-2xl"
      >
        {selected ? (
          <form className="space-y-5" onSubmit={submitReview}>
            <div className="rounded-xl border border-border/70 bg-secondary/30 p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{copy.reason}</p>
              <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-foreground">{selected.reason}</p>
            </div>
            <fieldset className="grid gap-3 sm:grid-cols-2">
              <legend className="mb-2 text-sm font-medium text-foreground">{copy.status}</legend>
              <label className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${decision === 'APPROVED' ? 'border-primary bg-primary/[0.06]' : 'border-border/70'}`}>
                <input type="radio" name="credit-limit-decision" value="APPROVED" checked={decision === 'APPROVED'} onChange={() => setDecision('APPROVED')} className="mt-1 accent-primary" />
                <span><span className="block font-semibold text-foreground">{copy.approve}</span><span className="mt-1 block text-xs leading-5 text-muted-foreground">{selected.standardLimit} → {selected.requestedLimit} {messages.courseRegistration.creditsUnit}</span></span>
              </label>
              <label className={`flex cursor-pointer items-start gap-3 rounded-xl border p-3 transition ${decision === 'REJECTED' ? 'border-destructive bg-destructive/[0.05]' : 'border-border/70'}`}>
                <input type="radio" name="credit-limit-decision" value="REJECTED" checked={decision === 'REJECTED'} onChange={() => setDecision('REJECTED')} className="mt-1 accent-destructive" />
                <span><span className="block font-semibold text-foreground">{copy.reject}</span><span className="mt-1 block text-xs leading-5 text-muted-foreground">{copy.reviewerNoteHint}</span></span>
              </label>
            </fieldset>
            <div>
              <label htmlFor="credit-limit-review-note" className="mb-2 block text-sm font-medium text-foreground">{copy.reviewerNote}</label>
              <Textarea
                id="credit-limit-review-note"
                value={note}
                onChange={(event) => setNote(event.target.value)}
                placeholder={copy.reviewerNotePlaceholder}
                maxLength={1000}
                hint={`${copy.reviewerNoteHint} ${note.length}/1000`}
                error={reviewError || undefined}
              />
            </div>
            <AdminDialogFooter className="border-t border-border/70 pt-4">
              <Button type="button" variant="outline" onClick={closeReview} disabled={saving}>{copy.cancel}</Button>
              <Button type="submit" variant={decision === 'REJECTED' ? 'destructive' : 'default'} disabled={saving}>
                {saving ? copy.saving : copy.save}
              </Button>
            </AdminDialogFooter>
          </form>
        ) : null}
      </Modal>
    </AdminFrame>
  );
}
