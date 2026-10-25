'use client';

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { RefreshCw, Search } from 'lucide-react';
import { toast } from 'sonner';
import { AdminTableCard, AdminTableScroll, AdminToolbarCard } from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { statusToneClass } from '@/components/ui/status';
import { useI18n } from '@/i18n';
import { adminSectionsApi, gradeAppealsApi, sectionsApi } from '@/lib/api';
import { campusCodeMessage } from '@/lib/campus-error';
import type { Section, SectionGrades } from '@/types/api';

type Scope = 'ALL' | 'SELECTED';

function sectionLabel(section: Section, locale: string): string {
  const course = section.course;
  const term = section.semester;
  const name = locale === 'vi'
    ? course?.nameVi || course?.name || course?.code
    : course?.nameEn || course?.name || course?.code;
  const semester = locale === 'vi' ? term?.nameVi || term?.name : term?.nameEn || term?.name;
  return `${course?.code ?? '—'} · ${section.sectionNumber} · ${name ?? '—'}${semester ? ` · ${semester}` : ''}`;
}

export function AdminGradeUnpublishPanel() {
  const { locale, messages } = useI18n();
  const copy = messages.gradeAppeals.unpublish;
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [sections, setSections] = useState<Section[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [sectionLoading, setSectionLoading] = useState(true);
  const [sectionError, setSectionError] = useState('');
  const [sectionId, setSectionId] = useState('');
  const [grades, setGrades] = useState<SectionGrades | null>(null);
  const [gradesLoading, setGradesLoading] = useState(false);
  const [gradesError, setGradesError] = useState('');
  const [scope, setScope] = useState<Scope>('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [saving, setSaving] = useState(false);

  const loadSections = useCallback(async () => {
    setSectionLoading(true);
    setSectionError('');
    try {
      const response = await adminSectionsApi.getAll({ page, limit: 50, search: search.trim() || undefined });
      setSections(response.data ?? []);
      setTotalPages(response.meta?.totalPages ?? 1);
    } catch (cause) {
      setSectionError(campusCodeMessage(cause, messages.common.campusErrors, copy.loadFailed));
      setSections([]);
    } finally {
      setSectionLoading(false);
    }
  }, [copy.loadFailed, messages.common.campusErrors, page, search]);

  useEffect(() => { void loadSections(); }, [loadSections]);

  const loadGrades = useCallback(async (id: string) => {
    if (!id) {
      setGrades(null);
      return;
    }
    setGradesLoading(true);
    setGradesError('');
    try {
      const response = await sectionsApi.getSectionGrades(id) as SectionGrades;
      setGrades(response);
      setSelectedIds(new Set(response.enrollments
        .filter((row) => row.gradeStatus === 'PUBLISHED')
        .map((row) => row.id)));
    } catch (cause) {
      setGrades(null);
      setGradesError(campusCodeMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setGradesLoading(false);
    }
  }, [copy.loadFailed, messages.common.campusErrors]);

  const publishedRows = useMemo(() => (grades?.enrollments ?? [])
    .filter((row) => row.gradeStatus === 'PUBLISHED' || row.gradeStatus === 'APPEALED'), [grades]);
  const pendingAppeals = publishedRows.filter((row) => row.gradeStatus === 'APPEALED').length;
  const eligibleRows = publishedRows.filter((row) => row.gradeStatus === 'PUBLISHED');
  const selectedCount = selectedIds.size;

  const toggleAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(eligibleRows.map((row) => row.id)) : new Set());
  };

  const toggleOne = (id: string, checked: boolean) => {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  const openConfirm = () => {
    setReason('');
    setConfirmed(false);
    setSubmitError('');
    setConfirmOpen(true);
  };

  const closeConfirm = () => {
    if (saving) return;
    setConfirmOpen(false);
    setSubmitError('');
  };

  const submitUnpublish = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!grades || !sectionId) return;
    const cleanReason = reason.trim();
    if (cleanReason.length < 20 || cleanReason.length > 1000) {
      setSubmitError(messages.common.campusErrors.codes.GRADE_UNPUBLISH_REASON_INVALID);
      return;
    }
    if (!confirmed) {
      setSubmitError(copy.confirmationRequired);
      return;
    }
    if (scope === 'SELECTED' && selectedIds.size === 0) {
      setSubmitError(copy.noSelected);
      return;
    }
    setSaving(true);
    setSubmitError('');
    try {
      const result = await gradeAppealsApi.unpublishSection(sectionId, {
        enrollmentIds: scope === 'SELECTED' ? [...selectedIds].sort() : [],
        reason: cleanReason,
      });
      toast.success(copy.success.replace('{count}', String(result.unpublishedCount)));
      setConfirmOpen(false);
      await loadGrades(sectionId);
    } catch (cause) {
      setSubmitError(campusCodeMessage(cause, messages.common.campusErrors, copy.loadFailed));
    } finally {
      setSaving(false);
    }
  };

  const sectionOptions = [
    { value: '', label: copy.selectSection },
    ...sections.map((section) => ({ value: section.id, label: sectionLabel(section, locale) })),
  ];

  return (
    <section className="space-y-5" aria-labelledby="grade-unpublish-heading">
      <h2 id="grade-unpublish-heading" className="sr-only">{copy.title}</h2>
      <AdminTableCard title={copy.title} description={copy.description}>
        <div className="space-y-5">
          <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(16rem,1fr)_auto] lg:items-end">
            <div className="space-y-1.5">
              <label htmlFor="grade-unpublish-search" className="text-sm font-medium text-foreground">{copy.searchLabel}</label>
              <Input id="grade-unpublish-search" value={search} onChange={(event) => { setSearch(event.target.value); setPage(1); }} placeholder={copy.searchPlaceholder} icon={<Search className="h-4 w-4" aria-hidden="true" />} />
            </div>
            <div className="space-y-1.5">
              <label htmlFor="grade-unpublish-section" className="text-sm font-medium text-foreground">{copy.sectionLabel}</label>
              <Select id="grade-unpublish-section" value={sectionId} onChange={(event) => { const id = event.target.value; setSectionId(id); setScope('ALL'); void loadGrades(id); }} options={sectionOptions} />
            </div>
            <Button type="button" variant="outline" onClick={() => void loadSections()} disabled={sectionLoading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${sectionLoading ? 'animate-spin' : ''}`} aria-hidden="true" />
              {messages.gradeAppeals.review.refresh}
            </Button>
          </div>

          {sectionError ? <ErrorState title={copy.loadFailed} description={sectionError} onRetry={() => void loadSections()} /> : null}
          {!sectionError && sectionLoading ? <LoadingState label={copy.loadingSections} /> : null}
          {!sectionError && !sectionLoading && sections.length === 0 ? <EmptyState title={copy.noSections} description={copy.searchPlaceholder} /> : null}
          {!sectionLoading && !sectionError && totalPages > 1 ? (
            <div className="flex items-center justify-end gap-2">
              <Button type="button" size="sm" variant="outline" disabled={page <= 1} onClick={() => setPage((value) => Math.max(1, value - 1))}>{copy.previousPage}</Button>
              <span className="text-xs tabular-nums text-muted-foreground">{page} / {totalPages}</span>
              <Button type="button" size="sm" variant="outline" disabled={page >= totalPages} onClick={() => setPage((value) => Math.min(totalPages, value + 1))}>{copy.nextPage}</Button>
            </div>
          ) : null}

          {sectionId ? (
            gradesError ? <ErrorState title={copy.loadFailed} description={gradesError} onRetry={() => void loadGrades(sectionId)} />
              : gradesLoading ? <LoadingState label={copy.loadingSections} />
                : grades ? (
                  <div className="space-y-4 border-t border-border/70 pt-5">
                    <fieldset className="space-y-3">
                      <legend className="text-sm font-semibold text-foreground">{copy.scopeLabel}</legend>
                      <div className="grid gap-2 sm:grid-cols-2">
                        <label className="flex cursor-pointer items-start gap-2 rounded-lg border border-border/70 p-3 text-sm text-foreground">
                          <input type="radio" name="grade-unpublish-scope" className="mt-1 h-4 w-4 accent-primary" checked={scope === 'ALL'} onChange={() => setScope('ALL')} />
                          <span>{copy.allGrades} <span className="text-muted-foreground">({publishedRows.length})</span></span>
                        </label>
                        <label className="flex cursor-pointer items-start gap-2 rounded-lg border border-border/70 p-3 text-sm text-foreground">
                          <input type="radio" name="grade-unpublish-scope" className="mt-1 h-4 w-4 accent-primary" checked={scope === 'SELECTED'} onChange={() => setScope('SELECTED')} />
                          <span>{copy.selectedGrades} <span className="text-muted-foreground">({selectedCount})</span></span>
                        </label>
                      </div>
                    </fieldset>
                    {pendingAppeals > 0 ? (
                      <p role="status" className={`rounded-md px-3 py-2 text-sm ${statusToneClass('warning')}`}>
                        {copy.appealPending} ({pendingAppeals})
                      </p>
                    ) : null}
                    {publishedRows.length === 0 ? (
                      <EmptyState title={copy.noPublished} description={copy.noPublishedDescription} />
                    ) : (
                      <AdminTableScroll>
                        <table className="w-full min-w-[640px] text-sm">
                          <thead><tr className="border-b border-border/70 bg-secondary/50 text-left text-muted-foreground">
                            {scope === 'SELECTED' ? <th className="w-12 px-3 py-3"><span className="sr-only">{copy.selectAll}</span><input type="checkbox" aria-label={copy.selectAll} checked={eligibleRows.length > 0 && eligibleRows.every((row) => selectedIds.has(row.id))} onChange={(event) => toggleAll(event.target.checked)} className="h-4 w-4 accent-primary" /></th> : null}
                            <th className="px-4 py-3">{copy.student}</th><th className="px-4 py-3">{copy.grade}</th><th className="px-4 py-3">{copy.status}</th>
                          </tr></thead>
                          <tbody className="divide-y divide-border/60">
                            {publishedRows.map((row) => (
                              <tr key={row.id}>
                                {scope === 'SELECTED' ? <td className="px-3 py-3"><input type="checkbox" aria-label={`${copy.student}: ${row.studentName} ${row.studentCode}`} checked={selectedIds.has(row.id)} disabled={row.gradeStatus === 'APPEALED'} onChange={(event) => toggleOne(row.id, event.target.checked)} className="h-4 w-4 accent-primary disabled:opacity-40" /></td> : null}
                                <td className="px-4 py-3"><span className="font-medium text-foreground">{row.studentName}</span><span className="ml-2 text-xs text-muted-foreground">{row.studentCode}</span></td>
                                <td className="px-4 py-3 font-semibold text-foreground">{typeof row.finalGrade === 'number' ? row.finalGrade.toFixed(2) : '—'} · {row.letterGrade ?? '—'}</td>
                                <td className="px-4 py-3"><span className={`rounded-md px-2.5 py-1 text-xs font-medium ${statusToneClass(row.gradeStatus === 'APPEALED' ? 'warning' : 'success')}`}>{messages.common.statuses[row.gradeStatus ?? 'UNKNOWN']}</span></td>
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </AdminTableScroll>
                    )}
                    <div className="flex justify-end border-t border-border/70 pt-4">
                      <Button type="button" onClick={openConfirm} disabled={publishedRows.length === 0 || (scope === 'SELECTED' && selectedCount === 0)}>{copy.submit}</Button>
                    </div>
                  </div>
                ) : null
          ) : <p className="rounded-lg border border-dashed border-border/70 p-4 text-sm text-muted-foreground">{copy.chooseSection}</p>}
        </div>
      </AdminTableCard>

      <Modal isOpen={confirmOpen} onClose={closeConfirm} title={copy.title} description={copy.description} className="max-w-xl">
        <form className="space-y-5" onSubmit={(event) => void submitUnpublish(event)}>
          <p className="rounded-lg border border-amber-300/60 bg-amber-50 p-3 text-sm leading-5 text-amber-950 dark:border-amber-500/30 dark:bg-amber-950/30 dark:text-amber-100">
            {scope === 'ALL' ? copy.allGrades : copy.selectedCount.replace('{count}', String(selectedCount))}
          </p>
          <div className="space-y-1.5">
            <label htmlFor="grade-unpublish-reason" className="text-sm font-medium text-foreground">{copy.reasonLabel}</label>
            <Textarea id="grade-unpublish-reason" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} aria-describedby="grade-unpublish-reason-hint" placeholder={copy.reasonPlaceholder} required />
            <p id="grade-unpublish-reason-hint" className="text-xs text-muted-foreground">{copy.reasonHint} · {reason.length}/1000</p>
          </div>
          <label className="flex items-start gap-2 rounded-lg border border-border/70 p-3 text-sm leading-5 text-foreground">
            <input type="checkbox" className="mt-1 h-4 w-4 accent-primary" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
            <span>{copy.confirmLabel}</span>
          </label>
          {submitError ? <p role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive">{submitError}</p> : null}
          <div className="flex flex-col-reverse gap-2 border-t border-border/70 pt-4 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" onClick={closeConfirm} disabled={saving}>{messages.common.actions.cancel}</Button>
            <Button type="submit" disabled={saving || !confirmed || reason.trim().length < 20}>{saving ? copy.submitting : copy.submit}</Button>
          </div>
        </form>
      </Modal>
    </section>
  );
}
