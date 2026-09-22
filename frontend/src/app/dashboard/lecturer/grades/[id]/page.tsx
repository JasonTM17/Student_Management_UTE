'use client';

import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { CheckCircle, FileText, Save, Send, Users } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi } from '@/lib/api';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { SectionGrades } from '@/types/api';
import { GradeImportPanel } from '@/components/lecturer/GradeImportPanel';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { UnsavedChangesConfirmDialog } from '@/components/ui/unsaved-changes-confirm';
import { useUnsavedChangesGuard } from '@/lib/use-unsaved-changes-guard';
import { useI18n } from '@/i18n';
import { campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

type GradeUpdate = {
  enrollmentId: string;
  processScore: number | null;
  finalExamScore: number | null;
};

function hasCompletedGrade(update: GradeUpdate | undefined) {
  return Boolean(update && update.processScore !== null && update.finalExamScore !== null);
}

// Standard Vietnamese university credit grading scale (Thang điểm 10 -> Chữ theo quy chế Bộ GD&ĐT & UTE)
function calculateGrade(score: number) {
  if (score >= 9.0) return 'A+';
  if (score >= 8.5) return 'A';
  if (score >= 8.0) return 'B+';
  if (score >= 7.0) return 'B';
  if (score >= 6.5) return 'C+';
  if (score >= 5.5) return 'C';
  if (score >= 5.0) return 'D+';
  if (score >= 4.0) return 'D';
  return 'F';
}

// Score drafts stay strings until commit so "9." and empty inputs survive
// keystrokes instead of snapping to 0 and stamping an F per character.
function isGradeComplete(update: GradeUpdate | undefined) {
  return hasCompletedGrade(update);
}

function totalScore(update: GradeUpdate | undefined) {
  return hasCompletedGrade(update)
    ? Math.round(((update!.processScore! + update!.finalExamScore!) / 2) * 100) / 100
    : null;
}

function formatVietnameseName(name: string): string {
  if (!name) return '';
  const parts = name.trim().split(/\s+/);
  if (parts.length < 2) return name;
  const vietnameseSurnames = new Set([
    'Nguyễn', 'Trần', 'Lê', 'Phạm', 'Hoàng', 'Huỳnh', 'Phan',
    'Vũ', 'Võ', 'Đặng', 'Bùi', 'Đỗ', 'Hồ', 'Ngô', 'Dương', 'Lý', 'Đinh', 'Đoàn', 'Trịnh',
  ]);
  const lastToken = parts[parts.length - 1];
  if (vietnameseSurnames.has(lastToken)) {
    return [lastToken, ...parts.slice(0, parts.length - 1)].join(' ');
  }
  return name;
}

export default function SectionGradingPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber, messages } = useI18n();
  const [sectionData, setSectionData] = useState<SectionGrades | null>(null);
  const [grades, setGrades] = useState<Map<string, GradeUpdate>>(new Map());
  const [editedIds, setEditedIds] = useState<Set<string>>(new Set());
  const [isLoading, setIsLoading] = useState(true);
  // A background refetch (after save) must not unmount the matrix: the old
  // full LoadingState swap scrolled the lecturer back to the top mid-session.
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [error, setError] = useState('');
  const [scoreErrors, setScoreErrors] = useState<Map<string, string>>(new Map());
  const { confirm, confirmationDialog } = useConfirmationDialog();

  const sectionId = params?.id;
  const localizedCourseName = useMemo(
    () =>
      sectionData
        ? getLocalizedFlatLabel(
            locale,
            sectionData.courseName,
            sectionData.courseNameEn,
            sectionData.courseNameVi,
            sectionData.courseName,
          )
        : '',
    [locale, sectionData],
  );

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Cổng Giảng viên',
          title: 'Quản lý điểm',
          backToGrades: 'Quay lại quản lý điểm',
          missingSection: 'Không thể xác định lớp học phần đã chọn.',
          loadFailed: 'Hiện chưa thể tải màn hình chấm điểm cho lớp học phần này.',
          loading: 'Đang tải lớp học phần',
          errorDescription:
            'Hãy xử lý lỗi của lớp học phần trước khi thử mở lại màn hình chấm điểm.',
          pageDescription: (courseName: string) =>
            `Ghi nhận điểm cho ${courseName}, rà soát hồ sơ sinh viên và chỉ công bố khi lớp học phần đã sẵn sàng.`,
          saveGrades: 'Lưu điểm',
          savingGrades: 'Đang lưu điểm',
          publishGrades: 'Công bố điểm',
          publishingGrades: 'Đang công bố điểm',
          saved: 'Đã lưu điểm',
          saveFailed: 'Hiện chưa thể lưu điểm.',
          invalidScore: 'Điểm phải là số từ 0 đến 10.',
          partialRowsOnly:
            '{count} dòng mới nhập một nửa điểm — hãy điền đủ cả hai cột để lưu.',
          partialRowsSkipped:
            '{count} dòng chỉ mới nhập một nửa điểm nên chưa được lưu.',
          unsavedTitle: 'Điểm chưa được lưu',
          unsavedBody:
            'Bạn còn ô điểm đã nhập chưa lưu. Rời khỏi màn hình sẽ mất các điểm đó. Tiếp tục?',
          publishTitle: 'Công bố điểm',
          publishMessage:
            'Công bố điểm ngay bây giờ? Sinh viên sẽ nhìn thấy kết quả đã công bố, nên đây cần là một bước phát hành có chủ đích.',
          published: 'Đã công bố điểm',
          publishFailed: 'Hiện chưa thể công bố điểm.',
          students: 'Sinh viên',
          gradedRecords: 'Bản ghi đã chấm',
          sectionStatus: 'Trạng thái lớp học phần',
          publishWarning:
            'Vẫn còn ít nhất một sinh viên cần điểm trước khi lớp học phần này có thể công bố kết quả.',
          emptyTitle: 'Chưa có sinh viên đăng ký',
          emptyDescription:
            'Lớp học phần này hiện chưa có sinh viên nào để chấm điểm.',
          tableTitle: 'Điểm sinh viên',
          headers: {
            student: 'Sinh viên',
            studentId: 'Mã sinh viên',
            email: 'Email',
            processScore: 'ĐQT (50%)',
            finalExamScore: 'ĐCK (50%)',
            total: 'Tổng kết',
            letter: 'Điểm chữ',
            status: 'Trạng thái',
          },
          unavailableEmail: 'Chưa có',
          noLetterGrade: 'Chưa chọn',
          processScoreLabel: (studentName: string) => `ĐQT (50%) cho ${studentName}`,
          finalExamScoreLabel: (studentName: string) => `ĐCK (50%) cho ${studentName}`,
          letterGradeLabel: (studentName: string) => `Xếp loại cho ${studentName}`,
          publishedStatus: 'Đã công bố',
          draftStatus: 'Bản nháp',
          sectionPrefix: 'Lớp',
          unavailableTitle: 'Lớp học phần chưa sẵn sàng',
        }
      : {
          eyebrow: 'Lecturer area',
          title: 'Grade management',
          backToGrades: 'Back to grade management',
          missingSection: 'The selected class could not be found.',
          loadFailed: 'The grading view could not be loaded.',
          loading: 'Loading class grades',
          errorDescription:
            'Resolve class-level grading issues before retrying the class view.',
          pageDescription: (courseName: string) =>
            `Capture grades for ${courseName}, review student records, and release results only when the class is ready.`,
          saveGrades: 'Save grades',
          savingGrades: 'Saving grades',
          publishGrades: 'Release grades',
          publishingGrades: 'Releasing grades',
          saved: 'Grades saved',
          saveFailed: 'Grades could not be saved.',
          invalidScore: 'Score must be a number between 0 and 10.',
          partialRowsOnly:
            '{count} row(s) have only one score filled — complete both columns to save.',
          partialRowsSkipped:
            '{count} row(s) with only one score filled were not saved.',
          unsavedTitle: 'Unsaved grades',
          unsavedBody:
            'Some score cells have not been saved yet. Leaving this screen discards them. Continue?',
          publishTitle: 'Release grades',
          publishMessage:
            'Release these grades now? Students will see the results immediately, so confirm that everything is ready.',
          published: 'Grades released',
          publishFailed: 'Grades could not be released.',
          students: 'Students',
          gradedRecords: 'Graded records',
          sectionStatus: 'Class status',
          publishWarning:
            'At least one student still needs a grade before this class can release results.',
          emptyTitle: 'No enrolled students',
          emptyDescription:
            'This class does not currently have any enrolled students to grade.',
          tableTitle: 'Student grades',
          headers: {
            student: 'Student',
            studentId: 'Student ID',
            email: 'Email',
            processScore: 'Process (50%)',
            finalExamScore: 'Final (50%)',
            total: 'Total',
            letter: 'Letter',
            status: 'Status',
          },
          unavailableEmail: 'Unavailable',
          noLetterGrade: 'Not selected',
          processScoreLabel: (studentName: string) => `Process score (50%) for ${studentName}`,
          finalExamScoreLabel: (studentName: string) => `Final exam score (50%) for ${studentName}`,
          letterGradeLabel: (studentName: string) => `Letter grade for ${studentName}`,
          publishedStatus: 'Published',
          draftStatus: 'Draft',
          sectionPrefix: 'Class',
          unavailableTitle: 'Class grades unavailable',
        };

  const statusLabel = (status: string | null | undefined) =>
    messages.common.statuses[
      (status ?? 'UNKNOWN').toUpperCase() as keyof typeof messages.common.statuses
    ] ?? messages.common.statuses.UNKNOWN;

  const fetchSectionGrades = useCallback(async (options?: { silent?: boolean }) => {
    if (!sectionId) {
      setError(copy.missingSection);
      setIsLoading(false);
      return;
    }

    if (options?.silent) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError('');

    try {
      const data = (await sectionsApi.getSectionGrades(sectionId)) as SectionGrades;
      setSectionData(data);

      const nextGrades = new Map<string, GradeUpdate>();
      data.enrollments.forEach((enrollment) => {
        nextGrades.set(enrollment.id, {
          enrollmentId: enrollment.id,
          processScore: enrollment.processScore ?? null,
          finalExamScore: enrollment.finalExamScore ?? null,
        });
      });

      setGrades(nextGrades);
      setEditedIds(new Set());
    } catch (requestError: any) {
      setError(
        campusErrorMessage(requestError, messages.common.campusErrors, copy.loadFailed),
      );
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [copy.loadFailed, copy.missingSection, messages.common.campusErrors, sectionId]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSectionGrades();
    }
  }, [fetchSectionGrades, hasAccess]);

  const hasChanges = editedIds.size > 0;

  // Leaving the sheet with typed scores discards them; the shared guard adds
  // the beforeunload dialog and an in-app confirm for the back link.
  const unsaved = useUnsavedChangesGuard({
    isDirty: useCallback(() => editedIds.size > 0, [editedIds]),
    enabled: !isSaving,
  });
  const { requestLeave, confirmLeave, cancelLeave, confirmOpen } = unsaved;

  const allGraded = useMemo(() => {
    if (!sectionData) {
      return false;
    }

    return sectionData.enrollments.every((enrollment) =>
      hasCompletedGrade(grades.get(enrollment.id)),
    );
  }, [grades, sectionData]);

  const markEdited = (enrollmentId: string) => {
    setEditedIds((previous) => {
      if (previous.has(enrollmentId)) {
        return previous;
      }

      const next = new Set(previous);
      next.add(enrollmentId);
      return next;
    });
  };

  /** Focus the first matching score cell that is actually on screen (the
   *  mobile cards and the desktop table are both mounted, CSS-hidden). */
  const focusCell = (dataCell: string) => {
    if (typeof document === 'undefined') return;
    const candidates = Array.from(
      document.querySelectorAll<HTMLInputElement>(`[data-cell="${dataCell}"]`),
    );
    const visible = candidates.find((el) => el.offsetParent !== null) ?? candidates[0];
    visible?.focus();
    visible?.select();
  };

  const focusFirstInvalidCell = () => {
    const firstErrorKey = scoreErrors.keys().next().value;
    if (firstErrorKey) focusCell(`grade-${firstErrorKey}`);
  };

  const focusFirstPartialCell = () => {
    const partial = sectionData?.enrollments.find(
      (enrollment) => editedIds.has(enrollment.id) && !hasCompletedGrade(grades.get(enrollment.id)),
    );
    if (!partial) return;
    const current = grades.get(partial.id);
    focusCell(`grade-${partial.id}:${current?.processScore == null ? 'processScore' : 'finalExamScore'}`);
  };

  /** Enter / ArrowDown moves to the next student's same column, ArrowUp the
   *  previous one — a 60-row matrix is unusable if every step is two Tabs. */
  const handleCellKeyDown = (
    event: KeyboardEvent<HTMLInputElement>,
    enrollmentIndex: number,
    field: 'processScore' | 'finalExamScore',
  ) => {
    if (!sectionData || (event.key !== 'Enter' && event.key !== 'ArrowDown' && event.key !== 'ArrowUp')) {
      return;
    }
    event.preventDefault();
    const delta = event.key === 'ArrowUp' ? -1 : 1;
    const next = sectionData.enrollments[enrollmentIndex + delta];
    if (!next) return;
    focusCell(`grade-${next.id}:${field}`);
  };

  /**
   * A `type="number"` input shows characters it rejects while reporting an empty
   * `value`, so an out-of-range or non-numeric entry used to vanish silently:
   * the lecturer saw the text, no message, and a save that quietly dropped it.
   * `badInput` is the reliable signal that the browser rejected the entry.
   */
  const handleScoreChange = (
    enrollmentId: string,
    field: 'processScore' | 'finalExamScore',
    draft: string,
    badInput = false,
  ) => {
    markEdited(enrollmentId);
    const errorKey = `${enrollmentId}:${field}`;
    const normalizedDraft = draft.replace(',', '.');
    const trimmed = normalizedDraft.trim();
    const parsed = trimmed === '' ? null : Number(normalizedDraft);
    const invalid =
      badInput || (trimmed !== '' && (parsed === null || !Number.isFinite(parsed) || parsed < 0 || parsed > 10));

    setScoreErrors((previous) => {
      const next = new Map(previous);
      if (invalid) next.set(errorKey, copy.invalidScore);
      else next.delete(errorKey);
      return next;
    });
    if (invalid) {
      // Keep the last valid value in state; the inline message explains why.
      return;
    }

    setGrades((previous) => {
      const next = new Map(previous);
      const current = next.get(enrollmentId) ?? { enrollmentId, processScore: null, finalExamScore: null };
      next.set(enrollmentId, { ...current, [field]: parsed });
      return next;
    });
  };

  /** Feedback item 9: an imported sheet lands in the same table the lecturer
   *  reviews, so the existing save path and its validation stay authoritative. */
  const applyImportedRows = (rows: { enrollmentId: string; processScore: number; finalExamScore: number }[]) => {
    setGrades((previous) => {
      const next = new Map(previous);
      for (const row of rows) {
        const current = next.get(row.enrollmentId) ?? {
          enrollmentId: row.enrollmentId,
          processScore: null,
          finalExamScore: null,
        };
        next.set(row.enrollmentId, { ...current, processScore: row.processScore, finalExamScore: row.finalExamScore });
      }
      return next;
    });
    setScoreErrors((previous) => {
      const next = new Map(previous);
      for (const row of rows) {
        next.delete(`${row.enrollmentId}:processScore`);
        next.delete(`${row.enrollmentId}:finalExamScore`);
      }
      return next;
    });
    for (const row of rows) markEdited(row.enrollmentId);
  };

  const handleSave = async () => {
    if (!sectionId || !sectionData) {
      return;
    }

    // The backend upserts only the rows it receives, so submit just the
    // enrollments the lecturer actually edited instead of the whole roster.
    const editedRows = sectionData.enrollments.filter((enrollment) => editedIds.has(enrollment.id));
    const partialRows = editedRows.filter((enrollment) => !hasCompletedGrade(grades.get(enrollment.id)));
    const updates = editedRows
      .filter((enrollment) => hasCompletedGrade(grades.get(enrollment.id)))
      .map((enrollment) => grades.get(enrollment.id))
      .filter((update): update is GradeUpdate & { processScore: number; finalExamScore: number } => hasCompletedGrade(update));

    // An out-of-range entry keeps the previous value in state, so saving now
    // would persist something other than what the lecturer sees on screen.
    if (scoreErrors.size > 0) {
      toast.error(copy.invalidScore);
      focusFirstInvalidCell();
      return;
    }

    // Partial rows cannot be submitted (the backend averages both columns),
    // but dropping them silently made Save look broken. Name them instead.
    if (editedRows.length > 0 && updates.length === 0) {
      toast.error(copy.partialRowsOnly.replace('{count}', formatNumber(partialRows.length)));
      focusFirstPartialCell();
      return;
    }

    setIsSaving(true);

    try {
      await sectionsApi.updateSectionGrades(sectionId, updates);
      if (partialRows.length > 0) {
        toast.warning(copy.partialRowsSkipped.replace('{count}', formatNumber(partialRows.length)));
      }
      toast.success(copy.saved);
      await fetchSectionGrades({ silent: true });
    } catch (requestError: any) {
      toast.error(
        campusErrorMessage(requestError, messages.common.campusErrors, copy.saveFailed),
      );
    } finally {
      setIsSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!sectionId || !sectionData || !allGraded) {
      return;
    }

    const shouldPublish = await confirm({
      title: copy.publishTitle,
      message: copy.publishMessage,
      confirmText: copy.publishGrades,
    });

    if (!shouldPublish) {
      return;
    }

    setIsPublishing(true);

    try {
      await sectionsApi.publishSectionGrades(sectionId);
      toast.success(copy.published);
      await fetchSectionGrades();
    } catch (requestError: any) {
      toast.error(
        campusErrorMessage(requestError, messages.common.campusErrors, copy.publishFailed),
      );
    } finally {
      setIsPublishing(false);
    }
  };

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (error) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
          title={copy.title}
          description={copy.errorDescription}
          actions={
            <LinkButton
              href="/dashboard/lecturer/grades"
              variant="outline"
              aria-label={copy.backToGrades}
              title={copy.backToGrades}
            >
              {copy.backToGrades}
            </LinkButton>
          }
        />
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchSectionGrades()}
        />
      </div>
    );
  }

  if (isLoading || !sectionData) {
    return <LoadingState label={copy.loading} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={`${sectionData.courseCode} · ${copy.sectionPrefix} ${sectionData.sectionNumber}`}
        description={copy.pageDescription(localizedCourseName)}
        actions={
          <div className="flex flex-wrap gap-3">
            <LinkButton
              href="/dashboard/lecturer/grades"
              variant="outline"
              aria-label={copy.backToGrades}
              title={copy.backToGrades}
              onClick={(event) => {
                if (!hasChanges) return;
                event.preventDefault();
                requestLeave(() => router.push('/dashboard/lecturer/grades'));
              }}
            >
              {copy.backToGrades}
            </LinkButton>
            <Button
              type="button"
              variant="outline"
              disabled={!hasChanges || isSaving}
              onClick={() => void handleSave()}
            >
              <Save className="mr-2 h-4 w-4" />
              {isSaving
                ? copy.savingGrades
                : hasChanges
                  ? `${copy.saveGrades} (${formatNumber(editedIds.size)})`
                  : copy.saveGrades}
            </Button>
            <Button
              type="button"
              disabled={!allGraded || isPublishing}
              onClick={() => void handlePublish()}
            >
              <Send className="mr-2 h-4 w-4" />
              {isPublishing ? copy.publishingGrades : copy.publishGrades}
            </Button>
          </div>
        }
      />

      <div className="grid gap-4 md:grid-cols-3">
        <Card variant="elevated">
          <CardContent className="flex items-center justify-between gap-4 pt-6">
            <div>
              <div className="text-sm text-muted-foreground">{copy.students}</div>
              <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                {formatNumber(sectionData.enrollments.length)}
              </div>
            </div>
            <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('info')}`}>
              <Users className="h-5 w-5" />
            </div>
          </CardContent>
        </Card>
        <Card variant="elevated">
          <CardContent className="flex items-center justify-between gap-4 pt-6">
            <div>
              <div className="text-sm text-muted-foreground">{copy.gradedRecords}</div>
              <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                {formatNumber(
                  sectionData.enrollments.filter((enrollment) => enrollment.letterGrade).length,
                )}
              </div>
            </div>
            <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('success')}`}>
              <CheckCircle className="h-5 w-5" />
            </div>
          </CardContent>
        </Card>
        <Card variant="elevated">
          <CardContent className="flex items-center justify-between gap-4 pt-6">
            <div>
              <div className="text-sm text-muted-foreground">{copy.sectionStatus}</div>
              <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                {statusLabel(sectionData.status)}
              </div>
            </div>
            <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('neutral')}`}>
              <FileText className="h-5 w-5" />
            </div>
          </CardContent>
        </Card>
      </div>

      {!allGraded ? (
        <Card variant="muted">
          <CardContent className="pt-6 text-sm leading-6 text-muted-foreground">
            {copy.publishWarning}
          </CardContent>
        </Card>
      ) : null}

      {sectionData.enrollments.length === 0 ? (
        <EmptyState
          icon={Users}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <CardTitle className="text-xl">{copy.tableTitle}</CardTitle>
              {isSaving ? null : (
                <GradeImportPanel
                  students={sectionData.enrollments.map((enrollment) => ({
                    enrollmentId: enrollment.id,
                    studentCode: enrollment.studentCode,
                    email: enrollment.email,
                  }))}
                  disabled={sectionData.enrollments.some((enrollment) => enrollment.gradeStatus === 'PUBLISHED')}
                  onApply={applyImportedRows}
                />
              )}
            </div>
          </CardHeader>
          <CardContent>
            <div
              className="space-y-3 md:hidden"
              role="list"
              aria-label={copy.tableTitle}
            >
              {sectionData.enrollments.map((enrollment) => {
                const current = grades.get(enrollment.id) ?? {
                  enrollmentId: enrollment.id,
                  processScore: enrollment.processScore ?? null,
                  finalExamScore: enrollment.finalExamScore ?? null,
                };
                const isPublished = enrollment.gradeStatus === 'PUBLISHED';

                return (
                  <article
                    key={`${enrollment.id}-mobile`}
                    className="rounded-lg border border-border/80 bg-card p-5 shadow-xs transition hover:border-primary/40"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <h3 className="break-words font-semibold text-foreground">
                          {formatVietnameseName(enrollment.studentName)}
                        </h3>
                        <p className="mt-1 break-words text-sm text-muted-foreground">
                          {enrollment.studentCode}
                        </p>
                        <p className="mt-1 break-words text-sm text-muted-foreground">
                          {enrollment.email ?? copy.unavailableEmail}
                        </p>
                      </div>
                      <span className="shrink-0 rounded-md bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                        {isPublished
                          ? copy.publishedStatus
                          : statusLabel(enrollment.gradeStatus ?? 'DRAFT')}
                      </span>
                    </div>
                    <div className="mt-4 grid gap-4 border-t border-border/60 pt-3 sm:grid-cols-2">
                      <label className="space-y-1.5 text-sm">
                        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.processScore}
                        </span>
                        <Input
                          type="number"
                          min="0"
                          max="10"
                          step="0.1"
                          data-cell={`grade-${enrollment.id}:processScore`}
                          value={current.processScore ?? ''}
                          onChange={(event) =>
                            handleScoreChange(enrollment.id, 'processScore', event.target.value, event.target.validity.badInput)
                          }
                          error={scoreErrors.get(`${enrollment.id}:processScore`)}
                          disabled={isPublished || isSaving}
                          aria-label={copy.processScoreLabel(formatVietnameseName(enrollment.studentName))}
                        />
                      </label>
                      <label className="space-y-1.5 text-sm">
                        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.finalExamScore}
                        </span>
                        <Input type="number" min="0" max="10" step="0.1"
                          data-cell={`grade-${enrollment.id}:finalExamScore`}
                          value={current.finalExamScore ?? ''}
                          onChange={(event) =>
                            handleScoreChange(enrollment.id, 'finalExamScore', event.target.value, event.target.validity.badInput)
                          }
                          error={scoreErrors.get(`${enrollment.id}:finalExamScore`)}
                          disabled={isPublished || isSaving}
                          aria-label={copy.finalExamScoreLabel(formatVietnameseName(enrollment.studentName))} />
                      </label>
                    </div>
                    <p className="mt-3 text-sm font-medium text-foreground">
                      {copy.headers.total}: {totalScore(current) ?? '—'} · {copy.headers.letter}: {totalScore(current) === null ? '—' : calculateGrade(totalScore(current)!)}
                    </p>
                  </article>
                );
              })}
            </div>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[880px] table-fixed text-sm">
                <thead>
                  <tr className="border-b border-border/70 text-left text-muted-foreground">
                    <th className="w-[21%] px-3 py-3.5 font-medium">{copy.headers.student}</th>
                    <th className="w-[13%] px-3 py-3.5 font-medium">{copy.headers.studentId}</th>
                    <th className="w-[13%] px-3 py-3.5 text-center font-medium">{copy.headers.processScore}</th>
                    <th className="w-[13%] px-3 py-3.5 text-center font-medium">{copy.headers.finalExamScore}</th>
                    <th className="w-[12%] px-3 py-3.5 text-center font-medium">{copy.headers.total}</th>
                    <th className="w-[10%] px-3 py-3.5 text-center font-medium">{copy.headers.letter}</th>
                    <th className="w-[18%] px-3 py-3.5 text-right font-medium">{copy.headers.status}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {sectionData.enrollments.map((enrollment, enrollmentIndex) => {
                    const current = grades.get(enrollment.id) ?? {
                      enrollmentId: enrollment.id,
                      processScore: enrollment.processScore ?? null,
                      finalExamScore: enrollment.finalExamScore ?? null,
                    };
                    const isPublished = enrollment.gradeStatus === 'PUBLISHED';
                    const isDirtyRow = editedIds.has(enrollment.id);

                    return (
                      <tr
                        key={enrollment.id}
                        className={
                          isDirtyRow
                            ? 'border-l-2 border-l-[var(--portal-chrome-accent)] bg-status-warning/5 transition-colors hover:bg-muted/40'
                            : 'transition-colors hover:bg-muted/40'
                        }
                      >
                        <td className="px-3 py-3.5">
                          <div className="flex items-center gap-1.5 font-medium text-foreground">
                            {isDirtyRow && (
                              <span
                                aria-hidden="true"
                                className="h-1.5 w-1.5 shrink-0 rounded-full bg-status-warning"
                              />
                            )}
                            {formatVietnameseName(enrollment.studentName)}
                          </div>
                          <div className="mt-1 truncate text-xs text-muted-foreground" title={enrollment.email ?? copy.unavailableEmail}>
                            {enrollment.email ?? copy.unavailableEmail}
                          </div>
                        </td>
                        <td className="px-3 py-3.5 text-muted-foreground">
                          {enrollment.studentCode}
                        </td>
                        <td className="px-3 py-3.5 text-center">
                          <div className="mx-auto max-w-[96px]">
                            <Input
                              type="number"
                              min="0"
                              max="10"
                              step="0.1"
                              data-cell={`grade-${enrollment.id}:processScore`}
                              value={current.processScore ?? ''}
                              onChange={(event) =>
                                handleScoreChange(enrollment.id, 'processScore', event.target.value, event.target.validity.badInput)
                              }
                              onKeyDown={(event) => handleCellKeyDown(event, enrollmentIndex, 'processScore')}
                              error={scoreErrors.get(`${enrollment.id}:processScore`)}
                              disabled={isPublished || isSaving}
                              aria-label={copy.processScoreLabel(formatVietnameseName(enrollment.studentName))}
                            />
                          </div>
                        </td>
                        <td className="px-3 py-3.5 text-center">
                          <div className="mx-auto max-w-[96px]">
                            <Input type="number" min="0" max="10" step="0.1"
                              data-cell={`grade-${enrollment.id}:finalExamScore`}
                              value={current.finalExamScore ?? ''}
                              onChange={(event) =>
                                handleScoreChange(enrollment.id, 'finalExamScore', event.target.value, event.target.validity.badInput)
                              }
                              onKeyDown={(event) => handleCellKeyDown(event, enrollmentIndex, 'finalExamScore')}
                              error={scoreErrors.get(`${enrollment.id}:finalExamScore`)}
                              disabled={isPublished || isSaving}
                              aria-label={copy.finalExamScoreLabel(formatVietnameseName(enrollment.studentName))} />
                          </div>
                        </td>
                        <td className="px-3 py-3.5 text-center font-semibold text-foreground">
                          {totalScore(current) ?? '—'}
                        </td>
                        <td className="px-3 py-3.5 text-center font-semibold text-foreground">
                          {totalScore(current) === null ? '—' : calculateGrade(totalScore(current)!)}
                        </td>
                        <td className="px-3 py-3.5 text-right">
                          <span className="rounded-md bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {isPublished
                              ? copy.publishedStatus
                              : statusLabel(enrollment.gradeStatus ?? 'DRAFT')}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      )}

      {confirmationDialog}
      <UnsavedChangesConfirmDialog
        open={unsaved.confirmOpen}
        title={copy.unsavedTitle}
        description={copy.unsavedBody}
        confirmLabel={locale === 'vi' ? 'Tiếp tục' : 'Continue'}
        cancelLabel={locale === 'vi' ? 'Ở lại nhập điểm' : 'Keep editing'}
        onConfirm={unsaved.confirmLeave}
        onCancel={unsaved.cancelLeave}
      />
    </div>
  );
}
