'use client';

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Calendar, Loader2, User } from 'lucide-react';
import { toast } from 'sonner';
import { Modal } from '@/components/ui/modal';
import { gradesApi } from '@/lib/api';
import { StudentGradeRecord, StudentGradesByEnrollmentResponse, GradeAppeal, GradeAppealEligibility } from '@/types/api';
import { useI18n } from '@/i18n';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { resolveGradePoint } from '@/lib/grade-scale';
import { shouldShowAppealDeadline } from '@/lib/grade-appeal-deadline';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { campusCodeMessage } from '@/lib/campus-error';

interface GradeDetailModalProps {
  isOpen: boolean;
  onClose: () => void;
  record: StudentGradeRecord | null;
  onAppealChanged?: () => void;
}

function isFiniteScore(value: number | null | undefined): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

/**
 * The grades API stores component names in Vietnamese only (there is no
 * `gradeItemNameEn`), so the English dialog used to show Vietnamese labels.
 * Translate the known component types and keep the raw name for anything else.
 */
const GRADE_ITEM_LABELS: Record<string, { vi: string; en: string }> = {
  PROCESS: { vi: 'Điểm quá trình (ĐQT - 50%)', en: 'Process score (50%)' },
  FINAL: { vi: 'Điểm cuối kỳ (ĐCK - 50%)', en: 'Final exam score (50%)' },
};

function gradeItemLabel(
  item: { gradeItemName: string; gradeItemType?: string | null },
  locale: string,
): string {
  const known = GRADE_ITEM_LABELS[(item.gradeItemType ?? '').toUpperCase()];
  if (!known) return item.gradeItemName;
  return locale === 'vi' ? known.vi : known.en;
}

export function GradeDetailModal({ isOpen, onClose, record, onAppealChanged }: GradeDetailModalProps) {
  const { locale, messages, formatDateTime } = useI18n();
  const [data, setData] = useState<StudentGradesByEnrollmentResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [detailFailed, setDetailFailed] = useState(false);
  const [appeals, setAppeals] = useState<GradeAppeal[]>([]);
  const [eligibility, setEligibility] = useState<GradeAppealEligibility | null>(null);
  const [appealLoading, setAppealLoading] = useState(false);
  const [appealLoadError, setAppealLoadError] = useState('');
  const [appealFormOpen, setAppealFormOpen] = useState(false);
  const [appealReason, setAppealReason] = useState('');
  const [appealConfirmed, setAppealConfirmed] = useState(false);
  const [appealError, setAppealError] = useState('');
  const [appealSaving, setAppealSaving] = useState(false);
  const [cancelArmed, setCancelArmed] = useState(false);
  const [cancelConfirmed, setCancelConfirmed] = useState(false);
  const [cancelError, setCancelError] = useState('');
  const [cancelling, setCancelling] = useState(false);
  const studentCopy = messages.gradeAppeals.student;

  useEffect(() => {
    if (!isOpen || !record?.id) {
      setData(null);
      setDetailFailed(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setDetailFailed(false);

    gradesApi
      .getStudentGradesByEnrollment(record.id)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch(() => {
        if (!cancelled) {
          setData(null);
          setDetailFailed(true);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [isOpen, record?.id]);

  useEffect(() => {
    if (!isOpen || !record?.id || !['PUBLISHED', 'APPEALED'].includes(record.gradeStatus)) {
      setAppeals([]);
      setEligibility(null);
      setAppealLoadError('');
      setAppealLoading(false);
      setAppealFormOpen(false);
      return;
    }
    let cancelled = false;
    setAppealLoading(true);
    setAppealLoadError('');
    setAppealFormOpen(false);
    setAppealError('');
    setCancelArmed(false);
    setCancelConfirmed(false);
    setCancelError('');
    Promise.all([gradesApi.getMyGradeAppeals(), gradesApi.getGradeAppealEligibility()])
      .then(([history, rows]) => {
        if (cancelled) return;
        setAppeals(history.filter((appeal) => appeal.enrollmentId === record.id));
        setEligibility(rows.find((item) => item.enrollmentId === record.id) ?? null);
      })
      .catch((cause) => {
        if (!cancelled) {
          setAppealLoadError(campusCodeMessage(cause, messages.common.campusErrors, studentCopy.loadFailed));
        }
      })
      .finally(() => {
        if (!cancelled) setAppealLoading(false);
      });
    return () => { cancelled = true; };
  }, [isOpen, record?.id, record?.gradeStatus, messages.common.campusErrors, studentCopy.loadFailed]);

  const currentAppeal = useMemo(
    () => appeals.find((appeal) => appeal.status === 'PENDING') ?? appeals[0] ?? null,
    [appeals],
  );

  const submitAppeal = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!record) return;
    const reason = appealReason.trim();
    if (reason.length < 20 || reason.length > 1000) {
      setAppealError(studentCopy.reasonLengthError);
      return;
    }
    if (!appealConfirmed) {
      setAppealError(studentCopy.confirmationRequired);
      return;
    }
    setAppealSaving(true);
    setAppealError('');
    try {
      const created = await gradesApi.submitGradeAppeal(record.id, reason);
      setAppeals((current) => [created, ...current]);
      setAppealFormOpen(false);
      setAppealReason('');
      setAppealConfirmed(false);
      setEligibility((current) => current ? { ...current, gradeStatus: 'APPEALED', appealAllowed: false } : current);
      toast.success(studentCopy.submitted);
      onAppealChanged?.();
    } catch (cause) {
      setAppealError(campusCodeMessage(cause, messages.common.campusErrors, studentCopy.loadFailed));
    } finally {
      setAppealSaving(false);
    }
  };

  const cancelAppeal = async () => {
    if (!currentAppeal || !cancelConfirmed) {
      setCancelError(studentCopy.cancelConfirmationRequired);
      return;
    }
    setCancelling(true);
    setCancelError('');
    try {
      const cancelledAppeal = await gradesApi.cancelGradeAppeal(currentAppeal.id);
      setAppeals((current) => current.map((item) => item.id === cancelledAppeal.id ? cancelledAppeal : item));
      setEligibility((current) => current ? { ...current, gradeStatus: 'PUBLISHED', appealAllowed: false } : current);
      setCancelArmed(false);
      setCancelConfirmed(false);
      toast.success(studentCopy.cancelledMessage);
      onAppealChanged?.();
    } catch (cause) {
      setCancelError(campusCodeMessage(cause, messages.common.campusErrors, studentCopy.loadFailed));
    } finally {
      setCancelling(false);
    }
  };

  if (!record) return null;

  const title = getLocalizedFlatLabel(
    locale,
    record.courseName,
    record.courseNameEn,
    record.courseNameVi,
    record.courseName,
  );
  const emptyValue = locale === 'vi' ? 'Chưa có' : 'N/A';
  const appealStatusLabel = (status: GradeAppeal['status']) => status === 'PENDING'
    ? studentCopy.pending
    : status === 'ACCEPTED' ? studentCopy.accepted
      : status === 'REJECTED' ? studentCopy.rejected : studentCopy.cancelled;
  const appealDeadline = currentAppeal?.appealUntil ?? eligibility?.appealUntil ?? null;

  // Canonical 50/50 rows come from persisted component scores only.
  const items = data?.grades?.length
    ? data.grades
    : isFiniteScore(record.processScore) && isFiniteScore(record.finalExamScore)
      ? [
            {
              id: 'process-grade',
              gradeItemId: 'process',
              gradeItemName: GRADE_ITEM_LABELS.PROCESS.vi,
              gradeItemType: 'PROCESS',
              score: record.processScore,
              maxScore: 10,
              weight: 50,
            },
            {
              id: 'final-exam-grade',
              gradeItemId: 'final_exam',
              gradeItemName: GRADE_ITEM_LABELS.FINAL.vi,
              gradeItemType: 'FINAL',
              score: record.finalExamScore,
              maxScore: 10,
              weight: 50,
            },
          ]
      : [];

  return (
    <Modal
      isOpen={isOpen}
      onClose={() => {
        if (!appealSaving && !cancelling) onClose();
      }}
      title={locale === 'vi' ? 'Chi tiết điểm học phần' : 'Course Grade Breakdown'}
      className="max-w-xl"
    >
      <div className="space-y-6">
        {/* Header Course Info */}
        <div className="rounded-xl border border-border/70 bg-card p-4">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-mono text-xs font-bold uppercase text-primary">
              {record.courseCode}
            </span>
            <span className="rounded-md bg-secondary px-2.5 py-0.5 text-xs font-medium text-foreground">
              {record.sectionCode}
            </span>
            <span className="text-xs text-muted-foreground">
              {record.credits} {locale === 'vi' ? 'tín chỉ' : 'credits'}
            </span>
          </div>
          <h3 className="mt-1.5 text-base font-bold text-foreground sm:text-lg">
            {title}
          </h3>
          <div className="mt-2 flex flex-wrap gap-4 text-xs text-muted-foreground">
            {record.lecturerName ? (
              <span className="inline-flex items-center gap-1.5">
                <User className="h-3.5 w-3.5 text-primary" />
                {record.lecturerName}
              </span>
            ) : null}
            <span className="inline-flex items-center gap-1.5">
              <Calendar className="h-3.5 w-3.5 text-primary" />
              {getLocalizedFlatLabel(
                locale,
                record.semester,
                record.semesterNameEn,
                record.semesterNameVi,
                record.semester,
              )}
            </span>
          </div>
        </div>

        {/* Grade Breakdown Table */}
        <div className="space-y-3">
          <div className="flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
            <h4 className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
              {locale === 'vi' ? 'Thành phần điểm học phần (50% Quá trình + 50% Cuối kỳ)' : 'Course Grade Components (50% In-Course + 50% Final)'}
            </h4>
            <span className="text-[11px] font-mono text-muted-foreground">
              {locale === 'vi' ? 'ĐHP = ĐQT × 50% + ĐCK × 50%' : 'Final = Process × 50% + Exam × 50%'}
            </span>
          </div>

          {detailFailed ? (
            <div className="rounded-md border border-amber-300/60 bg-amber-50 px-3 py-2 text-xs font-medium text-amber-900 dark:border-amber-500/40 dark:bg-amber-950/40 dark:text-amber-200">
              {locale === 'vi'
                ? 'Không tải được điểm thành phần mới nhất — bảng dưới hiển thị điểm tóm tắt đã lưu, có thể chưa đầy đủ.'
                : 'Could not load the latest component scores — the table below shows saved summary scores and may be incomplete.'}
            </div>
          ) : null}

          {loading ? (
            <div className="flex items-center justify-center py-8 text-sm text-muted-foreground">
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {locale === 'vi' ? 'Đang tải điểm chi tiết...' : 'Loading grade breakdown...'}
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-dashed border-border/80 p-6 text-center text-sm text-muted-foreground">
              {locale === 'vi'
                ? 'Chưa có dữ liệu điểm thành phần cho học phần này.'
                : 'No grade breakdown available for this course yet.'}
            </div>
          ) : (
            <div className="overflow-hidden rounded-lg border border-border/70">
              <table className="w-full text-left text-sm">
                <thead className="bg-secondary/40 text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 font-semibold">
                      {locale === 'vi' ? 'Thành phần' : 'Component'}
                    </th>
                    <th className="px-4 py-3 text-center font-semibold">
                      {locale === 'vi' ? 'Trọng số' : 'Weight'}
                    </th>
                    <th className="px-4 py-3 text-right font-semibold">
                      {locale === 'vi' ? 'Điểm số' : 'Score'}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60 bg-card">
                  {items.map((item) => {
                    const weightPct =
                      isFiniteScore(item.weight)
                        ? item.weight <= 1
                          ? `${Math.round(item.weight * 100)}%`
                          : `${item.weight}%`
                        : emptyValue;

                    return (
                      <tr key={item.id} className="transition-colors hover:bg-secondary/15">
                        <td className="px-4 py-3 font-medium text-foreground">
                          {gradeItemLabel(item, locale)}
                        </td>
                        <td className="px-4 py-3 text-center text-muted-foreground font-mono">
                          {weightPct}
                        </td>
                        <td className="px-4 py-3 text-right font-bold text-foreground text-base">
                          {isFiniteScore(item.score)
                            ? item.score.toFixed(1)
                            : emptyValue}
                          <span className="text-xs font-normal text-muted-foreground">
                            {' '}/ {item.maxScore ?? 10}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Overall Summary */}
        <div className="grid grid-cols-3 gap-3 rounded-xl border border-border/70 bg-secondary/20 p-4 text-center">
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Điểm tổng kết (10)' : 'Final Score (10)'}
            </div>
            <div className="mt-1 text-2xl font-bold text-foreground">
              {isFiniteScore(record.finalGrade)
                ? record.finalGrade.toFixed(1)
                : emptyValue}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Điểm chữ' : 'Letter Grade'}
            </div>
            <div className="mt-1 text-2xl font-bold text-primary">
              {record.letterGrade || emptyValue}
            </div>
          </div>
          <div>
            <div className="text-xs text-muted-foreground">
              {locale === 'vi' ? 'Quy đổi (4.0)' : 'Grade Point'}
            </div>
            <div className="mt-1 text-2xl font-bold text-foreground">
              {resolveGradePoint(record) !== null
                ? resolveGradePoint(record)!.toFixed(1)
                : emptyValue}
            </div>
          </div>
        </div>

        {['PUBLISHED', 'APPEALED'].includes(record.gradeStatus) ? (
          <section className="space-y-4 rounded-xl border border-border/70 bg-card p-4" aria-labelledby="grade-appeal-title">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                <h4 id="grade-appeal-title" className="font-semibold text-foreground">{studentCopy.historyTitle}</h4>
                {shouldShowAppealDeadline(eligibility?.appealAllowed === true, appealDeadline) ? (
                  <p className="mt-1 text-sm text-muted-foreground">
                    {studentCopy.appealUntil.replace('{date}', formatDateTime(appealDeadline, { timeZone: 'Asia/Bangkok' }))}
                  </p>
                ) : null}
              </div>
              {record.gradeStatus === 'APPEALED' || currentAppeal?.status === 'PENDING' ? (
                <span className="rounded-md bg-amber-100 px-2.5 py-1 text-xs font-semibold text-amber-900 dark:bg-amber-950/50 dark:text-amber-100">{studentCopy.pending}</span>
              ) : null}
            </div>

            {appealLoading ? <p role="status" className="text-sm text-muted-foreground">{studentCopy.loading}</p> : null}
            {appealLoadError ? <p role="alert" className="text-sm text-destructive">{appealLoadError}</p> : null}
            {!appealLoading && !appealLoadError && appeals.length > 0 ? (
              <ol className="space-y-3">
                {appeals.map((appeal) => (
                  <li key={appeal.id} className="rounded-lg border border-border/70 bg-secondary/20 p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="text-sm font-semibold text-foreground">{appealStatusLabel(appeal.status)}</span>
                      <time className="text-xs text-muted-foreground" dateTime={appeal.createdAt}>{formatDateTime(appeal.createdAt, { timeZone: 'Asia/Bangkok' })}</time>
                    </div>
                    <p className="mt-2 whitespace-pre-wrap text-sm leading-5 text-foreground">{appeal.reason}</p>
                    {appeal.resolutionReason ? <p className="mt-2 border-t border-border/60 pt-2 text-sm text-muted-foreground">{appeal.resolutionReason}</p> : null}
                  </li>
                ))}
              </ol>
            ) : null}

            {!appealLoading && !appealLoadError && !currentAppeal?.status && eligibility?.appealAllowed && !appealFormOpen ? (
              <Button type="button" size="sm" onClick={() => { setAppealFormOpen(true); setAppealError(''); }}>{studentCopy.appeal}</Button>
            ) : null}
            {!appealLoading && !appealLoadError && !currentAppeal && !eligibility?.appealAllowed ? (
              <p className="text-sm text-muted-foreground">{studentCopy.noDeadline}</p>
            ) : null}

            {currentAppeal?.status === 'PENDING' ? (
              <div className="space-y-3 border-t border-border/70 pt-3">
                <p role="status" className="text-sm text-muted-foreground">{studentCopy.pendingNotice}</p>
                {!cancelArmed ? (
                  <Button type="button" size="sm" variant="outline" onClick={() => setCancelArmed(true)}>{studentCopy.cancelPending}</Button>
                ) : (
                  <div className="space-y-3 rounded-lg border border-amber-300/60 bg-amber-50/70 p-3 dark:border-amber-500/30 dark:bg-amber-950/20">
                    <label className="flex items-start gap-2 text-sm leading-5 text-foreground">
                      <input type="checkbox" className="mt-1 h-4 w-4 accent-primary" checked={cancelConfirmed} onChange={(event) => setCancelConfirmed(event.target.checked)} />
                      <span>{studentCopy.cancelPendingConfirm}</span>
                    </label>
                    {cancelError ? <p role="alert" className="text-sm text-destructive">{cancelError}</p> : null}
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" size="sm" onClick={() => void cancelAppeal()} disabled={!cancelConfirmed || cancelling}>{cancelling ? studentCopy.cancelling : studentCopy.cancel}</Button>
                      <Button type="button" size="sm" variant="outline" onClick={() => { setCancelArmed(false); setCancelConfirmed(false); setCancelError(''); }} disabled={cancelling}>{messages.common.actions.cancel}</Button>
                    </div>
                  </div>
                )}
              </div>
            ) : null}

            {appealFormOpen ? (
              <form className="space-y-3 border-t border-border/70 pt-3" onSubmit={(event) => void submitAppeal(event)}>
                <div className="space-y-1.5">
                  <label htmlFor="student-grade-appeal-reason" className="text-sm font-medium text-foreground">{studentCopy.reasonLabel}</label>
                  <Textarea id="student-grade-appeal-reason" value={appealReason} onChange={(event) => setAppealReason(event.target.value)} maxLength={1000} aria-describedby="student-grade-appeal-reason-hint" placeholder={studentCopy.reasonPlaceholder} required />
                  <p id="student-grade-appeal-reason-hint" className="text-xs text-muted-foreground">{studentCopy.reasonHint} · {appealReason.length}/1000</p>
                </div>
                <label className="flex items-start gap-2 rounded-lg border border-border/70 p-3 text-sm leading-5 text-foreground">
                  <input type="checkbox" className="mt-1 h-4 w-4 accent-primary" checked={appealConfirmed} onChange={(event) => setAppealConfirmed(event.target.checked)} />
                  <span>{studentCopy.confirmLabel}</span>
                </label>
                {appealError ? <p role="alert" className="text-sm text-destructive">{appealError}</p> : null}
                <div className="flex flex-wrap gap-2">
                  <Button type="submit" size="sm" disabled={appealSaving || !appealConfirmed || appealReason.trim().length < 20}>{appealSaving ? studentCopy.submitting : studentCopy.submit}</Button>
                  <Button type="button" size="sm" variant="outline" onClick={() => { setAppealFormOpen(false); setAppealError(''); }} disabled={appealSaving}>{messages.common.actions.cancel}</Button>
                </div>
              </form>
            ) : null}
          </section>
        ) : null}
      </div>
    </Modal>
  );
}
