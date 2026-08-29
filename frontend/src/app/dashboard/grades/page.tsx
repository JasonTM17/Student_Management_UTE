'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Award, BookOpen, TrendingUp } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { gradesApi, semestersApi } from '@/lib/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import { StudentGradeRecord, Semester } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

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

export default function GradesPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber, messages } = useI18n();
  const [grades, setGrades] = useState<StudentGradeRecord[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchGrades = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await gradesApi.getMyGrades(selectedSemester || undefined);
      setGrades(data);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải dữ liệu điểm số.'
          : 'Grades could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, selectedSemester]);

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    void fetchSemesters();
  }, [fetchSemesters, hasAccess]);

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    void fetchGrades();
  }, [fetchGrades, hasAccess]);

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

  const summary = useMemo(() => {
    const gradedCourses = grades.filter(
      (grade) =>
        grade.letterGrade && gradePoints[grade.letterGrade] !== undefined,
    );
    const totalCredits = gradedCourses.reduce(
      (sum, grade) => sum + grade.credits,
      0,
    );
    const totalPoints = gradedCourses.reduce(
      (sum, grade) => sum + gradePoints[grade.letterGrade!] * grade.credits,
      0,
    );
    const gpa = totalCredits > 0 ? totalPoints / totalCredits : 0;

    return {
      gpa: gpa.toFixed(2),
      courseCount: grades.length,
      gradedCount: gradedCourses.length,
      completedCredits: grades
        .filter((grade) => grade.enrollmentStatus === 'COMPLETED')
        .reduce((sum, grade) => sum + grade.credits, 0),
    };
  }, [grades]);

  const groupedGrades = useMemo(() => {
    return grades.reduce<Record<string, StudentGradeRecord[]>>((groups, grade) => {
      const semesterLabel = getLocalizedFlatLabel(
        locale,
        grade.semester,
        grade.semesterNameEn,
        grade.semesterNameVi,
        grade.semester,
      );

      if (!groups[semesterLabel]) {
        groups[semesterLabel] = [];
      }

      groups[semesterLabel].push(grade);
      return groups;
    }, {});
  }, [grades, locale]);

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Workspace sinh viên',
          title: 'Điểm số',
          description: `Xem kết quả đã công bố cho ${selectedSemesterName}, rồi chuyển sang bảng điểm mà không rời khỏi student workspace.`,
          selectSemester: 'Chọn học kỳ cho điểm số',
          allSemesters: 'Tất cả học kỳ',
          openTranscript: 'Mở bảng điểm',
          loading: 'Đang tải điểm số',
          unavailableTitle: 'Điểm số chưa sẵn sàng',
          emptyTitle: 'Chưa có điểm được công bố',
          emptyDescription:
            'Khi môn học được chấm và công bố, kết quả sẽ xuất hiện tại đây.',
          currentGpa: 'GPA hiện tại',
          completedCredits: 'Tín chỉ hoàn tất',
          gradedCourses: 'Môn đã có điểm',
          courseWord: 'môn',
          coursesWord: 'môn',
          creditsWord: 'tín chỉ',
          tableHeaders: {
            course: 'Môn học',
            section: 'Section',
            lecturer: 'Giảng viên',
            credits: 'Tín chỉ',
            score: 'Điểm',
            grade: 'Xếp loại',
            status: 'Trạng thái',
          },
          pendingAssignment: 'Chờ phân công',
          notPublished: 'Chưa công bố',
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Grades',
          description: `Review published grading outcomes for ${selectedSemesterName} without losing access to the rest of the academic workspace.`,
          selectSemester: 'Select semester for grades',
          allSemesters: 'All semesters',
          openTranscript: 'Open transcript',
          loading: 'Loading grades',
          unavailableTitle: 'Grades unavailable',
          emptyTitle: 'No grades published yet',
          emptyDescription:
            'Once courses are graded and published, the results will appear here.',
          currentGpa: 'Current GPA',
          completedCredits: 'Completed credits',
          gradedCourses: 'Graded courses',
          courseWord: 'course',
          coursesWord: 'courses',
          creditsWord: 'credits',
          tableHeaders: {
            course: 'Course',
            section: 'Section',
            lecturer: 'Lecturer',
            credits: 'Credits',
            score: 'Score',
            grade: 'Grade',
            status: 'Status',
          },
          pendingAssignment: 'Pending assignment',
          notPublished: 'Not published',
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
            <LinkButton href="/dashboard/transcript" variant="outline">
              {copy.openTranscript}
            </LinkButton>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchGrades()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : grades.length === 0 ? (
        <EmptyState
          icon={BookOpen}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard/transcript">
              {copy.openTranscript}
            </LinkButton>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.currentGpa}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {summary.gpa}
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
                  <div className="text-sm text-muted-foreground">{copy.completedCredits}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(summary.completedCredits)}
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
                  <div className="text-sm text-muted-foreground">{copy.gradedCourses}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(summary.gradedCount)}/{formatNumber(summary.courseCount)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('neutral')}`}>
                  <BookOpen className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            {Object.entries(groupedGrades).map(([semesterName, records]) => (
              <Card key={semesterName} variant="muted">
                <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <CardTitle className="text-xl">{semesterName}</CardTitle>
                  <div className="text-sm text-muted-foreground">
                    {formatNumber(records.length)}{' '}
                    {records.length === 1 ? copy.courseWord : copy.coursesWord} -{' '}
                    {formatNumber(records.reduce((sum, record) => sum + record.credits, 0))}{' '}
                    {copy.creditsWord}
                  </div>
                </CardHeader>
                <CardContent>
                  <div
                    className="space-y-3 md:hidden"
                    role="list"
                    aria-label={copy.tableHeaders.course}
                  >
                    {records.map((record) => (
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
                          <div className="min-w-0">
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.tableHeaders.lecturer}
                            </dt>
                            <dd className="mt-1 break-words text-foreground">
                              {record.lecturerName ?? copy.pendingAssignment}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.tableHeaders.credits}
                            </dt>
                            <dd className="mt-1 text-foreground">
                              {formatNumber(record.credits)}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.tableHeaders.score}
                            </dt>
                            <dd className="mt-1 text-foreground">
                              {record.finalGrade !== null
                                ? record.finalGrade.toFixed(1)
                                : copy.notPublished}
                            </dd>
                          </div>
                          <div>
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.tableHeaders.status}
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
                    <table className="w-full min-w-[760px] text-sm">
                      <thead>
                        <tr className="border-b border-border/70 text-left text-muted-foreground">
                          <th className="px-2 py-3 font-medium">{copy.tableHeaders.course}</th>
                          <th className="px-2 py-3 font-medium">{copy.tableHeaders.section}</th>
                          <th className="px-2 py-3 font-medium">{copy.tableHeaders.lecturer}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.credits}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.score}</th>
                          <th className="px-2 py-3 text-center font-medium">{copy.tableHeaders.grade}</th>
                          <th className="px-2 py-3 text-right font-medium">{copy.tableHeaders.status}</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border/60">
                        {records.map((record) => (
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
                            <td className="px-2 py-4 text-muted-foreground">
                              {record.lecturerName ?? copy.pendingAssignment}
                            </td>
                            <td className="px-2 py-4 text-center text-muted-foreground">
                              {formatNumber(record.credits)}
                            </td>
                            <td className="px-2 py-4 text-center text-foreground">
                              {record.finalGrade !== null
                                ? record.finalGrade.toFixed(1)
                                : copy.notPublished}
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
