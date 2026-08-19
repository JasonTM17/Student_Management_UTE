'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams } from 'next/navigation';
import { CheckCircle, FileText, Save, Send, Users } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
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
import { toast } from 'sonner';

type GradeUpdate = {
  enrollmentId: string;
  finalGrade: number;
  letterGrade: string;
};

const letterGrades = [
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

function calculateGrade(score: number) {
  if (score >= 97) return 'A+';
  if (score >= 93) return 'A';
  if (score >= 90) return 'A-';
  if (score >= 87) return 'B+';
  if (score >= 83) return 'B';
  if (score >= 80) return 'B-';
  if (score >= 77) return 'C+';
  if (score >= 73) return 'C';
  if (score >= 70) return 'C-';
  if (score >= 67) return 'D+';
  if (score >= 63) return 'D';
  if (score >= 60) return 'D-';
  return 'F';
}

export default function SectionGradingPage() {
  const params = useParams<{ id: string }>();
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber } = useI18n();
  const [sectionData, setSectionData] = useState<SectionGrades | null>(null);
  const [grades, setGrades] = useState<Map<string, GradeUpdate>>(new Map());
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
          eyebrow: 'Workspace giảng viên',
          title: 'Quản lý điểm',
          backToGrades: 'Quay lại quản lý điểm',
          missingSection: 'Không thể xác định section đã chọn.',
          loadFailed: 'Hiện chưa thể tải màn hình chấm điểm cho section này.',
          loading: 'Đang tải section chấm điểm',
          errorDescription:
            'Hãy xử lý lỗi ở cấp section trước khi thử mở lại màn hình chấm điểm.',
          pageDescription: (courseName: string) =>
            `Ghi nhận điểm cho ${courseName}, rà soát hồ sơ sinh viên và chỉ công bố khi section đã sẵn sàng.`,
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
          sectionStatus: 'Trạng thái section',
          publishWarning:
            'Vẫn còn ít nhất một bản ghi cần điểm công bố trước khi section này có thể đi sang bước publish.',
          emptyTitle: 'Chưa có sinh viên đăng ký',
          emptyDescription:
            'Section này hiện chưa có sinh viên nào để chấm điểm.',
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
          finalScoreLabel: (studentName: string) => `Điểm cuối kỳ cho ${studentName}`,
          letterGradeLabel: (studentName: string) => `Xếp loại cho ${studentName}`,
          publishedStatus: 'Đã công bố',
          draftStatus: 'Bản nháp',
          sectionPrefix: 'Section',
          unavailableTitle: 'Section chấm điểm chưa sẵn sàng',
        }
      : {
          eyebrow: 'Lecturer workspace',
          title: 'Grade management',
          backToGrades: 'Back to grade management',
          missingSection: 'The selected section could not be resolved.',
          loadFailed: 'The grading view could not be loaded.',
          loading: 'Loading grading section',
          errorDescription:
            'Resolve section-level grading issues before retrying the section view.',
          pageDescription: (courseName: string) =>
            `Capture grades for ${courseName}, review student records, and publish only when the section is ready.`,
          saveGrades: 'Save grades',
          savingGrades: 'Saving grades',
          publishGrades: 'Publish grades',
          publishingGrades: 'Publishing grades',
          saved: 'Grades saved',
          saveFailed: 'Grades could not be saved.',
          publishTitle: 'Publish grades',
          publishMessage:
            'Publish these grades now? Students will see the published results and this should be treated as a deliberate release step.',
          published: 'Grades published',
          publishFailed: 'Grades could not be published.',
          students: 'Students',
          gradedRecords: 'Graded records',
          sectionStatus: 'Section status',
          publishWarning:
            'At least one record still needs a published grade before this section can move into the publish step.',
          emptyTitle: 'No enrolled students',
          emptyDescription:
            'This section does not currently have any student enrollments to grade.',
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
          finalScoreLabel: (studentName: string) => `Final score for ${studentName}`,
          letterGradeLabel: (studentName: string) => `Letter grade for ${studentName}`,
          publishedStatus: 'Published',
          draftStatus: 'Draft',
          sectionPrefix: 'Section',
          unavailableTitle: 'Grading section unavailable',
        };

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
        const finalGrade = enrollment.finalGrade ?? 0;
        nextGrades.set(enrollment.id, {
          enrollmentId: enrollment.id,
          finalGrade,
          letterGrade: enrollment.letterGrade ?? calculateGrade(finalGrade),
        });
      });

      setGrades(nextGrades);
    } catch (requestError: any) {
      setError(requestError.response?.data?.message ?? copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed, copy.missingSection, sectionId]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSectionGrades();
    }
  }, [fetchSectionGrades, hasAccess]);

  const hasChanges = useMemo(() => {
    if (!sectionData) {
      return false;
    }

    return sectionData.enrollments.some((enrollment) => {
      const current = grades.get(enrollment.id);
      if (!current) {
        return false;
      }

      return (
        current.finalGrade !== (enrollment.finalGrade ?? 0) ||
        current.letterGrade !==
          (enrollment.letterGrade ?? calculateGrade(enrollment.finalGrade ?? 0))
      );
    });
  }, [grades, sectionData]);

  const allGraded = useMemo(() => {
    if (!sectionData) {
      return false;
    }

    return sectionData.enrollments.every(
      (enrollment) => enrollment.letterGrade !== null || grades.get(enrollment.id),
    );
  }, [grades, sectionData]);

  const handleGradeChange = (
    enrollmentId: string,
    field: 'finalGrade' | 'letterGrade',
    value: number | string,
  ) => {
    setGrades((previous) => {
      const next = new Map(previous);
      const existing = next.get(enrollmentId) ?? {
        enrollmentId,
        finalGrade: 0,
        letterGrade: 'F',
      };

      if (field === 'finalGrade') {
        existing.finalGrade = value as number;
        existing.letterGrade = calculateGrade(existing.finalGrade);
      } else {
        existing.letterGrade = value as string;
      }

      next.set(enrollmentId, existing);
      return next;
    });
  };

  const handleSave = async () => {
    if (!sectionId) {
      return;
    }

    setIsSaving(true);

    try {
      await sectionsApi.updateSectionGrades(sectionId, Array.from(grades.values()));
      toast.success(copy.saved);
      await fetchSectionGrades();
    } catch (requestError: any) {
      toast.error(requestError.response?.data?.message ?? copy.saveFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const handlePublish = async () => {
    if (!sectionId) {
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
      toast.error(requestError.response?.data?.message ?? copy.publishFailed);
    } finally {
      setIsPublishing(false);
    }
  };

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
  }

  if (error) {
    return (
      <div className="space-y-6">
        <PageHeader
          eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
          title={copy.title}
          description={copy.errorDescription}
          actions={
            <LocalizedLink href="/dashboard/lecturer/grades">
              <Button
                variant="outline"
                aria-label={copy.backToGrades}
                title={copy.backToGrades}
              >
                {copy.backToGrades}
              </Button>
            </LocalizedLink>
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
            <LocalizedLink href="/dashboard/lecturer/grades">
              <Button
                variant="outline"
                aria-label={copy.backToGrades}
                title={copy.backToGrades}
              >
                {copy.backToGrades}
              </Button>
            </LocalizedLink>
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
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-500/12 text-blue-600 dark:text-blue-400">
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
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-emerald-500/12 text-emerald-600 dark:text-emerald-400">
              <CheckCircle className="h-5 w-5" />
            </div>
          </CardContent>
        </Card>
        <Card variant="elevated">
          <CardContent className="flex items-center justify-between gap-4 pt-6">
            <div>
              <div className="text-sm text-muted-foreground">{copy.sectionStatus}</div>
              <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                {sectionData.status ?? 'OPEN'}
              </div>
            </div>
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-violet-500/12 text-violet-600 dark:text-violet-400">
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
                  finalGrade: enrollment.finalGrade ?? 0,
                  letterGrade:
                    enrollment.letterGrade ??
                    calculateGrade(enrollment.finalGrade ?? 0),
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
                          : enrollment.gradeStatus ?? copy.draftStatus}
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
                          max="100"
                          step="0.1"
                          value={current.finalGrade}
                          onChange={(event) =>
                            handleGradeChange(
                              enrollment.id,
                              'finalGrade',
                              Number(event.target.value) || 0,
                            )
                          }
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
                              'letterGrade',
                              event.target.value,
                            )
                          }
                          disabled={isPublished}
                          aria-label={copy.letterGradeLabel(enrollment.studentName)}
                        >
                          {letterGrades.map((grade) => (
                            <option key={grade} value={grade}>
                              {grade}
                            </option>
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
                      finalGrade: enrollment.finalGrade ?? 0,
                      letterGrade:
                        enrollment.letterGrade ??
                        calculateGrade(enrollment.finalGrade ?? 0),
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
                              max="100"
                              step="0.1"
                              value={current.finalGrade}
                              onChange={(event) =>
                                handleGradeChange(
                                  enrollment.id,
                                  'finalGrade',
                                  Number(event.target.value) || 0,
                                )
                              }
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
                                  'letterGrade',
                                  event.target.value,
                                )
                              }
                              disabled={isPublished}
                              aria-label={copy.letterGradeLabel(enrollment.studentName)}
                            >
                              {letterGrades.map((grade) => (
                                <option key={grade} value={grade}>
                                  {grade}
                                </option>
                              ))}
                            </select>
                          </div>
                        </td>
                        <td className="px-2 py-4 text-right">
                          <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {isPublished
                              ? copy.publishedStatus
                              : enrollment.gradeStatus ?? copy.draftStatus}
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
