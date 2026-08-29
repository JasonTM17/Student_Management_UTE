'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Award, FileText, GraduationCap, TrendingUp } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { gradesApi, semestersApi } from '@/lib/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import {
  Semester,
  StudentGradeRecord,
  StudentTranscriptSemester,
} from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

function getGradeTone(letterGrade: string | null) {
  if (!letterGrade) {
    return metricToneClass('neutral');
  }

  if (letterGrade.startsWith('A')) {
    return metricToneClass('success');
  }

  if (letterGrade.startsWith('B')) {
    return metricToneClass('info');
  }

  if (letterGrade.startsWith('C') || letterGrade.startsWith('D')) {
    return metricToneClass('warning');
  }

  return metricToneClass('danger');
}

const gradePoints: Record<string, number> = {
  'A+': 4,
  A: 4,
  'A-': 3.7,
  'B+': 3.3,
  B: 3,
  'B-': 2.7,
  'C+': 2.3,
  C: 2,
  'C-': 1.7,
  'D+': 1.3,
  D: 1,
  'D-': 0.7,
  F: 0,
};

function getGradePoint(record: StudentGradeRecord) {
  if (typeof record.gradePoint === 'number') {
    return record.gradePoint;
  }

  if (record.letterGrade && gradePoints[record.letterGrade] !== undefined) {
    return gradePoints[record.letterGrade];
  }

  return null;
}

export default function TranscriptPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber } = useI18n();
  const [transcriptData, setTranscriptData] = useState<{
    summary: {
      cumulativeGpa: number;
      totalCreditsEarned: number;
      totalCreditsAttempted: number;
    };
    semesters: StudentTranscriptSemester[];
  } | null>(null);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchTranscript = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await gradesApi.getMyTranscript(selectedSemester || undefined);
      setTranscriptData(data);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải dữ liệu bảng điểm.'
          : 'Transcript data could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, selectedSemester]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSemesters();
    }
  }, [fetchSemesters, hasAccess]);

  useEffect(() => {
    if (hasAccess) {
      void fetchTranscript();
    }
  }, [fetchTranscript, hasAccess]);

  const transcriptSemesters = useMemo(
    () => transcriptData?.semesters ?? [],
    [transcriptData],
  );

  const totalCourses = useMemo(
    () =>
      transcriptSemesters.reduce(
        (sum, semester) => sum + semester.records.length,
        0,
      ),
    [transcriptSemesters],
  );

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 't\u1ea5t c\u1ea3 h\u1ecdc k\u1ef3' : 'all semesters',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all semesters')
    );
  }, [locale, selectedSemester, semesters]);

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Workspace sinh viên',
          title: 'Bảng điểm',
          description: `Xem hồ sơ học tập dài hạn cho ${selectedSemesterName}, bao gồm GPA tích lũy và kết quả theo từng học kỳ.`,
          selectSemester: 'Chọn học kỳ cho bảng điểm',
          allSemesters: 'Tất cả học kỳ',
          openGrades: 'Mở điểm số',
          loading: 'Đang tải bảng điểm',
          unavailableTitle: 'Bảng điểm chưa sẵn sàng',
          emptyTitle: 'Chưa có hồ sơ bảng điểm',
          emptyDescription:
            'Các môn học hoàn tất và điểm đã công bố sẽ được tích lũy tại đây khi có kết quả học tập.',
          cumulativeGpa: 'GPA tích lũy',
          earnedCredits: 'Tín chỉ đạt',
          attemptedCredits: 'Tín chỉ đăng ký',
          courses: 'Môn học',
          courseWord: 'môn',
          coursesWord: 'môn',
          creditsAttempted: 'tín chỉ đã đăng ký',
          gradePointLabel: 'GPA',
          headers: {
            course: 'Môn học',
            section: 'Section',
            credits: 'Tín chỉ',
            score: 'Điểm',
            grade: 'Xếp loại',
            points: 'Điểm hệ',
            enrollment: 'Enrollment',
            gradeStatus: 'Trạng thái điểm',
          },
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Transcript',
          description: `Review the long-form academic record for ${selectedSemesterName}, including cumulative GPA and semester-by-semester outcomes.`,
          selectSemester: 'Select semester for transcript',
          allSemesters: 'All semesters',
          openGrades: 'Open grades',
          loading: 'Loading transcript',
          unavailableTitle: 'Transcript unavailable',
          emptyTitle: 'No transcript records yet',
          emptyDescription:
            'Completed courses and published grades will accumulate here once academic outcomes are available.',
          cumulativeGpa: 'Cumulative GPA',
          earnedCredits: 'Earned credits',
          attemptedCredits: 'Attempted credits',
          courses: 'Courses',
          courseWord: 'course',
          coursesWord: 'courses',
          creditsAttempted: 'credits attempted',
          gradePointLabel: 'GPA',
          headers: {
            course: 'Course',
            section: 'Section',
            credits: 'Credits',
            score: 'Score',
            grade: 'Grade',
            points: 'Points',
            enrollment: 'Enrollment',
            gradeStatus: 'Grade status',
          },
        };

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="min-w-[220px]">
              <Select
                aria-label={copy.selectSemester}
                value={selectedSemester}
                onChange={(event) => setSelectedSemester(event.target.value)}
                options={[
                  { value: '', label: copy.allSemesters },
                  ...semesters.map((semester) => ({
                    value: semester.id,
                    label: getLocalizedName(locale, semester, semester.name),
                  })),
                ]}
              />
            </div>
            <LinkButton href="/dashboard/grades" variant="outline">
              {copy.openGrades}
            </LinkButton>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchTranscript()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : !transcriptData || transcriptSemesters.length === 0 ? (
        <EmptyState
          icon={FileText}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard/grades">{copy.openGrades}</LinkButton>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-4">
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.cumulativeGpa}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {transcriptData.summary.cumulativeGpa.toFixed(2)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('info')}`}>
                  <TrendingUp className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.earnedCredits}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(transcriptData.summary.totalCreditsEarned)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('success')}`}>
                  <Award className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.attemptedCredits}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(transcriptData.summary.totalCreditsAttempted)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('neutral')}`}>
                  <GraduationCap className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.courses}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(totalCourses)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('warning')}`}>
                  <FileText className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            {transcriptSemesters.map((semester) => (
              <Card key={semester.semesterId} variant="muted">
                <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <CardTitle className="text-xl">
                    {getLocalizedFlatLabel(
                      locale,
                      semester.semesterName,
                      semester.semesterNameEn,
                      semester.semesterNameVi,
                      semester.semesterName,
                    )}
                  </CardTitle>
                  <div className="text-sm text-muted-foreground">
                    {formatNumber(semester.records.length)}{' '}
                    {semester.records.length === 1 ? copy.courseWord : copy.coursesWord}{' '}
                    - {formatNumber(semester.creditsAttempted)} {copy.creditsAttempted} -{' '}
                    {copy.gradePointLabel}{' '}
                    {semester.gpa.toFixed(2)}
                  </div>
                </CardHeader>
                <CardContent>
                  <div
                    className="space-y-3 md:hidden"
                    role="list"
                    aria-label={copy.headers.course}
                  >
                    {semester.records.map((record) => (
                      <article
                        key={`${record.id}-mobile`}
                        className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                        role="listitem"
                      >
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                              {record.courseCode}
                            </p>
                            <h3 className="mt-1 break-words font-semibold text-foreground">
                              {getLocalizedFlatLabel(
                                locale,
                                record.courseName,
                                record.courseNameEn,
                                record.courseNameVi,
                                record.courseName,
                              )}
                            </h3>
                            <p className="mt-1 text-sm text-muted-foreground">
                              {record.sectionCode}
                            </p>
                          </div>
                          <span
                            className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${getGradeTone(record.letterGrade)}`}
                          >
                            {record.letterGrade || '-'}
                          </span>
                        </div>
                        <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.credits}
                            </dt>
                            <dd className="mt-1 text-foreground">
                              {formatNumber(record.credits)}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.score}
                            </dt>
                            <dd className="mt-1 text-foreground">
                              {typeof record.finalGrade === 'number'
                                ? record.finalGrade.toFixed(1)
                                : '-'}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.points}
                            </dt>
                            <dd className="mt-1 text-foreground">
                              {typeof getGradePoint(record) === 'number'
                                ? getGradePoint(record)?.toFixed(1)
                                : '-'}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.enrollment}
                            </dt>
                            <dd className="mt-1 break-words text-foreground">
                              {record.enrollmentStatus}
                            </dd>
                          </div>
                          <div className="col-span-2 border-t border-border/60 pt-3">
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.gradeStatus}
                            </dt>
                            <dd className="mt-1 break-words text-foreground">
                              {record.gradeStatus}
                            </dd>
                          </div>
                        </dl>
                      </article>
                    ))}
                  </div>
                  <div className="hidden overflow-x-auto md:block">
                    <table className="w-full min-w-[840px] text-sm">
                      <thead>
                        <tr className="border-b border-border/70 text-left text-muted-foreground">
                          <th className="px-2 py-3 font-medium">{copy.headers.course}</th>
                          <th className="px-2 py-3 font-medium">{copy.headers.section}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.headers.credits}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.headers.score}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.headers.grade}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.headers.points}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.headers.enrollment}</th>
                          <th className="px-2 py-3 text-right font-medium">{copy.headers.gradeStatus}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {semester.records.map((record) => (
                          <tr key={record.id}>
                            <td className="px-2 py-4">
                              <div className="font-medium text-foreground">
                                {record.courseCode}
                              </div>
                              <div className="text-muted-foreground">
                                {getLocalizedFlatLabel(
                                  locale,
                                  record.courseName,
                                  record.courseNameEn,
                                  record.courseNameVi,
                                  record.courseName,
                                )}
                              </div>
                            </td>
                            <td className="px-2 py-4 text-muted-foreground">
                              {record.sectionCode}
                            </td>
                            <td className="px-2 py-4 text-center text-muted-foreground">
                              {formatNumber(record.credits)}
                            </td>
                            <td className="px-2 py-4 text-center text-foreground">
                              {typeof record.finalGrade === 'number'
                                ? record.finalGrade.toFixed(1)
                                : '-'}
                            </td>
                            <td className="px-2 py-4 text-center">
                              {record.letterGrade ? (
                                <span
                                  className={`inline-flex min-w-[2.75rem] items-center justify-center rounded-full px-2.5 py-1 text-xs font-semibold ${getGradeTone(
                                    record.letterGrade,
                                  )}`}
                                >
                                  {record.letterGrade}
                                </span>
                              ) : (
                                <span className="text-muted-foreground">-</span>
                              )}
                            </td>
                            <td className="px-2 py-4 text-center text-muted-foreground">
                              {typeof getGradePoint(record) === 'number'
                                ? getGradePoint(record)?.toFixed(1)
                                : '-'}
                            </td>
                            <td className="px-2 py-4 text-center">
                              <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                {record.enrollmentStatus}
                              </span>
                            </td>
                            <td className="px-2 py-4 text-right">
                              <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                {record.gradeStatus}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
