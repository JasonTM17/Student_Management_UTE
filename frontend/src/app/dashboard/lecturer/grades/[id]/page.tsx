'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { CheckCircle, FileText, Save, Send, Users } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi } from '@/lib/api';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { SectionGrades } from '@/types/api';
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
import { useI18n } from '@/i18n';
import { campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

type GradeUpdate = {
  enrollmentId: string;
  finalGrade: number | null;
  letterGrade: string;
};

const letterGrades = [
  '',
  'A+',
  'A',
  'A-',
  'B+',
  'B',
  'B-',
  'C+',
  'C',
  'C-',
  'D+',
  'D',
  'D-',
  'F',
];

function hasCompletedGrade(update: GradeUpdate | undefined) {
  return Boolean(update && (update.finalGrade !== null || update.letterGrade !== ''));
}

// Grades are capped at 10.0 by the backend validation contract, so letter
// bands follow the same 10-point scale.
function calculateGrade(score: number) {
  if (score >= 9.7) return 'A+';
  if (score >= 9.3) return 'A';
  if (score >= 9.0) return 'A-';
  if (score >= 8.7) return 'B+';
  if (score >= 8.3) return 'B';
  if (score >= 8.0) return 'B-';
  if (score >= 7.7) return 'C+';
  if (score >= 7.3) return 'C';
  if (score >= 7.0) return 'C-';
  if (score >= 6.7) return 'D+';
  if (score >= 6.3) return 'D';
  if (score >= 6.0) return 'D-';
  return 'F';
}

// Score drafts stay strings until commit so "9." and empty inputs survive
// keystrokes instead of snapping to 0 and stamping an F per character.
function applyScoreDraft(
  update: GradeUpdate | undefined,
  draft: string | undefined,
): GradeUpdate | undefined {
  if (!update || draft === undefined) {
    return update;
  }

  const trimmed = draft.trim();
  if (trimmed === '') {
    return { ...update, finalGrade: null };
  }

  const parsed = Number(trimmed);
  if (!Number.isFinite(parsed) || parsed < 0 || parsed > 10) {
    return update;
  }

  return { ...update, finalGrade: parsed, letterGrade: calculateGrade(parsed) };
}

function isGradeComplete(update: GradeUpdate | undefined) {
  return Boolean(update?.letterGrade && update.finalGrade !== null);
}

export default function SectionGradingPage() {
  const params = useParams<{ id: string }>();
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber, messages } = useI18n();
  const [sectionData, setSectionData] = useState<SectionGrades | null>(null);
  const [grades, setGrades] = useState<Map<string, GradeUpdate>>(new Map());
  const [editedIds, setEditedIds] = useState<Set<string>>(new Set());
  const [scoreDrafts, setScoreDrafts] = useState<Record<string, string>>({});
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [error, setError] = useState('');
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
          eyebrow: 'Khu giảng viên',
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
            score: 'Điểm',
            grade: 'Xếp loại',
            status: 'Trạng thái',
          },
          unavailableEmail: 'Chưa có',
          noLetterGrade: 'Chưa chọn',
          finalScoreLabel: (studentName: string) => `Điểm cuối kỳ cho ${studentName}`,
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
            score: 'Score',
            grade: 'Grade',
            status: 'Status',
          },
          unavailableEmail: 'Unavailable',
          noLetterGrade: 'Not selected',
          finalScoreLabel: (studentName: string) => `Final score for ${studentName}`,
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

  const fetchSectionGrades = useCallback(async () => {
    if (!sectionId) {
      setError(copy.missingSection);
      setIsLoading(false);
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const data = (await sectionsApi.getSectionGrades(sectionId)) as SectionGrades;
      setSectionData(data);

      const nextGrades = new Map<string, GradeUpdate>();
      data.enrollments.forEach((enrollment) => {
        nextGrades.set(enrollment.id, {
          enrollmentId: enrollment.id,
          finalGrade: enrollment.finalGrade ?? null,
          letterGrade: enrollment.letterGrade ?? '',
        });
      });

      setGrades(nextGrades);
      setEditedIds(new Set());
      setScoreDrafts({});
    } catch (requestError: any) {
      setError(
        campusErrorMessage(requestError, messages.common.campusErrors, copy.loadFailed),
      );
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed, copy.missingSection, messages.common.campusErrors, sectionId]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSectionGrades();
    }
  }, [fetchSectionGrades, hasAccess]);

  const hasChanges = editedIds.size > 0;

  const allGraded = useMemo(() => {
    if (!sectionData) {
      return false;
    }

    return sectionData.enrollments.every((enrollment) =>
      hasCompletedGrade(
        applyScoreDraft(grades.get(enrollment.id), scoreDrafts[enrollment.id]),
      ),
    );
  }, [grades, scoreDrafts, sectionData]);

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

  const handleScoreDraftChange = (enrollmentId: string, draft: string) => {
    markEdited(enrollmentId);
    setScoreDrafts((previous) => ({ ...previous, [enrollmentId]: draft }));
  };

  const commitScoreDraft = (enrollmentId: string) => {
    const draft = scoreDrafts[enrollmentId];
    if (draft === undefined) {
      return;
    }

    setGrades((previous) => {
      const current = previous.get(enrollmentId);
      if (!current) {
        return previous;
      }

      const committed = applyScoreDraft(current, draft);
      if (!committed) {
        return previous;
      }

      const next = new Map(previous);
      next.set(enrollmentId, committed);
      return next;
    });
    setScoreDrafts((previous) => {
      if (!(enrollmentId in previous)) {
        return previous;
      }

      const next = { ...previous };
      delete next[enrollmentId];
      return next;
    });
  };

  const handleGradeChange = (enrollmentId: string, letterGrade: string) => {
    markEdited(enrollmentId);
    setGrades((previous) => {
      const next = new Map(previous);
      const existing = next.get(enrollmentId) ?? {
        enrollmentId,
        finalGrade: null,
        letterGrade: '',
      };

      next.set(enrollmentId, { ...existing, letterGrade });
      return next;
    });
  };

  const handleSave = async () => {
    if (!sectionId || !sectionData) {
      return;
    }

    // The backend upserts only the rows it receives, so submit just the
    // enrollments the lecturer actually edited instead of the whole roster.
    const updates = sectionData.enrollments
      .filter((enrollment) => editedIds.has(enrollment.id))
      .map((enrollment) =>
        applyScoreDraft(grades.get(enrollment.id), scoreDrafts[enrollment.id]),
      )
      .filter((update): update is GradeUpdate => hasCompletedGrade(update));

    if (updates.length === 0) {
      return;
    }

    setIsSaving(true);

    try {
      await sectionsApi.updateSectionGrades(sectionId, updates);
      toast.success(copy.saved);
      await fetchSectionGrades();
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
              {isSaving ? copy.savingGrades : copy.saveGrades}
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
            <CardTitle className="text-xl">{copy.tableTitle}</CardTitle>
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
                  finalGrade: enrollment.finalGrade ?? null,
                  letterGrade: enrollment.letterGrade ?? '',
                };
                const isPublished = enrollment.gradeStatus === 'PUBLISHED';

                return (
                  <article
                    key={`${enrollment.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <h3 className="break-words font-semibold text-foreground">
                          {enrollment.studentName}
                        </h3>
                        <p className="mt-1 break-words text-sm text-muted-foreground">
                          {enrollment.studentCode}
                        </p>
                        <p className="mt-1 break-words text-sm text-muted-foreground">
                          {enrollment.email ?? copy.unavailableEmail}
                        </p>
                      </div>
                      <span className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                        {isPublished
                          ? copy.publishedStatus
                          : statusLabel(enrollment.gradeStatus ?? 'DRAFT')}
                      </span>
                    </div>
                    <div className="mt-4 grid gap-4 border-t border-border/60 pt-3 sm:grid-cols-2">
                      <label className="space-y-1.5 text-sm">
                        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.score}
                        </span>
                        <Input
                          type="number"
                          min="0"
                          max="10"
                          step="0.1"
                          value={
                            scoreDrafts[enrollment.id] ??
                            (current.finalGrade === null
                              ? ''
                              : String(current.finalGrade))
                          }
                          onChange={(event) =>
                            handleScoreDraftChange(
                              enrollment.id,
                              event.target.value,
                            )
                          }
                          onBlur={() => commitScoreDraft(enrollment.id)}
                          disabled={isPublished}
                          aria-label={copy.finalScoreLabel(enrollment.studentName)}
                        />
                      </label>
                      <label className="space-y-1.5 text-sm">
                        <span className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.grade}
                        </span>
                        <select
                          className="flex h-11 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground ring-offset-background transition-[border-color,box-shadow] focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
                          value={current.letterGrade}
                          onChange={(event) =>
                            handleGradeChange(
                              enrollment.id,
                              event.target.value,
                            )
                          }
                          disabled={isPublished}
                          aria-label={copy.letterGradeLabel(enrollment.studentName)}
                        >
                          <option value="">{copy.noLetterGrade}</option>
                          {letterGrades.map((grade) => (
                            grade === '' ? null : (
                            <option key={grade} value={grade}>
                              {grade}
                            </option>
                            )
                          ))}
                        </select>
                      </label>
                    </div>
                  </article>
                );
              })}
            </div>
            <div className="hidden overflow-x-auto md:block">
              <table className="w-full min-w-[960px] text-sm">
                <thead>
                  <tr className="border-b border-border/70 text-left text-muted-foreground">
                    <th className="px-2 py-3 font-medium">{copy.headers.student}</th>
                    <th className="px-2 py-3 font-medium">{copy.headers.studentId}</th>
                    <th className="px-2 py-3 font-medium">{copy.headers.email}</th>
                    <th className="px-2 py-3 text-center font-medium">{copy.headers.score}</th>
                    <th className="px-2 py-3 text-center font-medium">{copy.headers.grade}</th>
                    <th className="px-2 py-3 text-right font-medium">{copy.headers.status}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {sectionData.enrollments.map((enrollment) => {
                    const current = grades.get(enrollment.id) ?? {
                      enrollmentId: enrollment.id,
                      finalGrade: enrollment.finalGrade ?? null,
                      letterGrade: enrollment.letterGrade ?? '',
                    };
                    const isPublished = enrollment.gradeStatus === 'PUBLISHED';

                    return (
                      <tr key={enrollment.id}>
                        <td className="px-2 py-4">
                          <div className="font-medium text-foreground">
                            {enrollment.studentName}
                          </div>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {enrollment.studentCode}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {enrollment.email ?? copy.unavailableEmail}
                        </td>
                        <td className="px-2 py-4 text-center">
                          <div className="mx-auto max-w-[120px]">
                            <Input
                              type="number"
                              min="0"
                              max="10"
                              step="0.1"
                              value={
                                scoreDrafts[enrollment.id] ??
                                (current.finalGrade === null
                                  ? ''
                                  : String(current.finalGrade))
                              }
                              onChange={(event) =>
                                handleScoreDraftChange(
                                  enrollment.id,
                                  event.target.value,
                                )
                              }
                              onBlur={() => commitScoreDraft(enrollment.id)}
                              disabled={isPublished}
                              aria-label={copy.finalScoreLabel(enrollment.studentName)}
                            />
                          </div>
                        </td>
                        <td className="px-2 py-4 text-center">
                          <div className="mx-auto max-w-[120px]">
                            <select
                              className="flex h-11 w-full rounded-lg border border-input bg-background px-3 py-2 text-sm text-foreground ring-offset-background transition-[border-color,box-shadow] focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-60"
                              value={current.letterGrade}
                              onChange={(event) =>
                                handleGradeChange(
                                  enrollment.id,
                                  event.target.value,
                                )
                              }
                              disabled={isPublished}
                              aria-label={copy.letterGradeLabel(enrollment.studentName)}
                            >
                              <option value="">{copy.noLetterGrade}</option>
                              {letterGrades.map((grade) => (
                                grade === '' ? null : (
                                <option key={grade} value={grade}>
                                  {grade}
                                </option>
                                )
                              ))}
                            </select>
                          </div>
                        </td>
                        <td className="px-2 py-4 text-right">
                          <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
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
    </div>
  );
}
