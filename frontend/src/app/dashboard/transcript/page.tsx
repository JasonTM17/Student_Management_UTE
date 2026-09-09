'use client';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Award, BookOpen, FileText, GraduationCap, Info, Printer, TrendingUp } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import { GpaTrendChart, GradeDistributionChart } from '@/components/dashboard/TranscriptCharts';
import {
  buildCumulativeGpaTrendPoints,
  buildGpaTrendPoints,
  buildGradeDistribution,
  buildTenScaleTrendPoints,
} from '@/lib/transcript-charts';
import { GradeDetailModal } from '@/components/dashboard/GradeDetailModal';
import { cn } from '@/lib/utils';
import { useRequireAuth } from '@/context/AuthContext';
import { curriculumApi, gradesApi, semestersApi } from '@/lib/api';
import { getLocalizedFlatLabel, getLocalizedName } from '@/lib/academic-content';
import {
  type MyCurriculumCourse,
  type MyCurriculumResponse,
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
  const { locale, formatNumber, messages } = useI18n();
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
  const [selectedRecord, setSelectedRecord] = useState<StudentGradeRecord | null>(null);
  const [gpaMode, setGpaMode] = useState<'cumulative' | 'semester' | 'both'>('both');
  const [curriculumData, setCurriculumData] = useState<MyCurriculumResponse | null>(null);
  const [curriculumState, setCurriculumState] = useState<'loading' | 'ready' | 'unavailable'>(
    'loading',
  );
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const loadGeneration = useRef(0);

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchTranscript = useCallback(async () => {
    const generation = ++loadGeneration.current;
    setIsLoading(true);
    setError('');

    try {
      const data = await gradesApi.getMyTranscript();
      if (generation !== loadGeneration.current) return;
      setTranscriptData(data);
    } catch {
      if (generation !== loadGeneration.current) return;
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải dữ liệu bảng điểm.'
          : 'Transcript data could not be loaded.',
      );
    } finally {
      if (generation === loadGeneration.current) {
        setIsLoading(false);
      }
    }
  }, [locale]);

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

  useEffect(() => {
    if (!hasAccess) return;
    let cancelled = false;
    setCurriculumState('loading');
    curriculumApi
      .getMyCurriculum()
      .then((data) => {
        if (cancelled) return;
        setCurriculumData(data);
        setCurriculumState('ready');
      })
      .catch(() => {
        if (!cancelled) setCurriculumState('unavailable');
      });
    return () => {
      cancelled = true;
    };
  }, [hasAccess]);

  const transcriptSemesters = useMemo(() => {
    const all = transcriptData?.semesters ?? [];
    if (!selectedSemester) return all;
    return all.filter((semester) => semester.semesterId === selectedSemester);
  }, [transcriptData, selectedSemester]);

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

  const gpaTrendPoints = useMemo(
    () => buildGpaTrendPoints(transcriptData?.semesters ?? [], locale),
    [locale, transcriptData],
  );

  const cumulativeGpaTrendPoints = useMemo(
    () => buildCumulativeGpaTrendPoints(transcriptData?.semesters ?? [], locale),
    [locale, transcriptData],
  );

  const gradeDistribution = useMemo(
    () => buildGradeDistribution(transcriptSemesters),
    [transcriptSemesters],
  );

  const tenScaleTrendPoints = useMemo(
    () => buildTenScaleTrendPoints(transcriptData?.semesters ?? [], locale),
    [locale, transcriptData],
  );

  const curriculumCourses = curriculumData?.courses ?? [];

  const curriculumGroups = useMemo(() => {
    const courses = curriculumData?.courses ?? [];
    const groups = new Map<string, { year: number; semester: number; courses: MyCurriculumCourse[] }>();
    courses.forEach((course) => {
      const key = `${course.year}-${course.semester}`;
      const group = groups.get(key) ?? { year: course.year, semester: course.semester, courses: [] };
      group.courses.push(course);
      groups.set(key, group);
    });
    return [...groups.values()].sort((a, b) => a.year - b.year || a.semester - b.semester);
  }, [curriculumData]);

  const completedCourseCount = curriculumData
    ? curriculumCourses.filter((course) => course.status === 'COMPLETED').length
    : (transcriptData?.semesters ?? []).reduce(
        (sum, semester) =>
          sum + semester.records.filter((record) => Boolean(record.letterGrade)).length,
        0,
      );

  const inProgressCourseCount = curriculumData
    ? curriculumCourses.filter((course) => course.status === 'IN_PROGRESS').length
    : (transcriptData?.semesters ?? []).reduce(
        (sum, semester) =>
          sum + semester.records.filter((record) => !record.letterGrade).length,
        0,
      );

  const curriculumCompletedCredits = curriculumCourses
    .filter((course) => course.status === 'COMPLETED')
    .reduce((sum, course) => sum + course.credits, 0);

  const curriculumCreditPercent = useMemo(() => {
    const total = curriculumData?.curriculum.totalCredits ?? 0;
    if (total <= 0) return 0;
    return Math.min(100, Math.round((curriculumCompletedCredits / total) * 100));
  }, [curriculumCompletedCredits, curriculumData]);

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu sinh viên',
          title: 'Bảng điểm',
          description: `Xem hồ sơ học tập dài hạn cho ${selectedSemesterName}, bao gồm GPA tích lũy và kết quả theo từng học kỳ.`,
          selectSemester: 'Chọn học kỳ cho bảng điểm',
          allSemesters: 'Tất cả học kỳ',
          openGrades: 'Mở điểm số',
          printTranscript: 'In bảng điểm',
          loading: 'Đang tải bảng điểm',
          unavailableTitle: 'Bảng điểm chưa sẵn sàng',
          emptyTitle: 'Chưa có hồ sơ bảng điểm',
          emptyDescription:
            'Các môn học hoàn tất và điểm đã công bố sẽ được tích lũy tại đây khi có kết quả học tập.',
          cumulativeGpa: 'GPA hệ 4 tích lũy',
          earnedCredits: 'Tín chỉ đã đạt',
          completedCourses: 'Môn đã hoàn tất',
          inProgressCourses: 'Môn đang học',
          courseWord: 'môn',
          coursesWord: 'môn',
          creditsAttempted: 'tín chỉ đã đăng ký',
          gradePointLabel: 'GPA',
          gpaTrend: 'Xu hướng GPA',
          gpaAllTrend: 'Tổng hợp tất cả',
          gpaSemesterTrend: 'Theo học kỳ',
          gpaBothTrend: 'Song song',
          avgTenScale: 'ĐTB hệ 10',
          clickToViewDetail: 'Nhấn vào môn học để xem chi tiết điểm Quá trình (GK) và Cuối kỳ (CK)',
          distribution: 'Phân bố xếp loại',
          programTitle: 'Chương trình đào tạo',
          programLoading: 'Đang tải chương trình đào tạo',
          programEmpty: 'Chưa có chương trình đào tạo',
          programCreditsUnit: 'tín chỉ',
          mandatory: 'Bắt buộc',
          statusCompleted: 'Đã hoàn tất',
          statusInProgress: 'Đang học',
          statusNotStarted: 'Chưa hoàn tất',
          yearSemesterLabel: (year: number, semester: number) => `Năm ${year} · HK ${semester}`,
          programHeaders: {
            course: 'Môn học',
            credits: 'Tín chỉ',
            status: 'Trạng thái',
          },
          headers: {
            course: 'Môn học',
            section: 'Lớp học phần',
            credits: 'Tín chỉ',
            score: 'Điểm',
            grade: 'Xếp loại',
            points: 'Điểm hệ',
            enrollment: 'Đăng ký',
            gradeStatus: 'Trạng thái điểm',
          },
        }
      : {
          eyebrow: 'Student area',
          title: 'Transcript',
          description: `Review the long-form academic record for ${selectedSemesterName}, including cumulative GPA and semester-by-semester outcomes.`,
          selectSemester: 'Select semester for transcript',
          allSemesters: 'All semesters',
          openGrades: 'Open grades',
          printTranscript: 'Print transcript',
          loading: 'Loading transcript',
          unavailableTitle: 'Transcript unavailable',
          emptyTitle: 'No transcript records yet',
          emptyDescription:
            'Completed courses and published grades will accumulate here once academic outcomes are available.',
          cumulativeGpa: 'Cumulative GPA (4.0 scale)',
          earnedCredits: 'Credits earned',
          completedCourses: 'Completed courses',
          inProgressCourses: 'In progress',
          courseWord: 'course',
          coursesWord: 'courses',
          creditsAttempted: 'credits attempted',
          gradePointLabel: 'GPA',
          gpaTrend: 'GPA trend',
          gpaAllTrend: 'Overall cumulative',
          gpaSemesterTrend: 'By semester',
          gpaBothTrend: 'Both',
          avgTenScale: '10-scale average',
          clickToViewDetail: 'Click on a course to view Midterm & Final score breakdown',
          distribution: 'Grade distribution',
          programTitle: 'Study program',
          programLoading: 'Loading study program',
          programEmpty: 'No study program available',
          programCreditsUnit: 'credits',
          mandatory: 'Mandatory',
          statusCompleted: 'Completed',
          statusInProgress: 'In progress',
          statusNotStarted: 'Not completed',
          yearSemesterLabel: (year: number, semester: number) => `Year ${year} · Semester ${semester}`,
          programHeaders: {
            course: 'Course',
            credits: 'Credits',
            status: 'Status',
          },
          headers: {
            course: 'Course',
            section: 'Class',
            credits: 'Credits',
            score: 'Score',
            grade: 'Grade',
            points: 'Points',
            enrollment: 'Registration',
            gradeStatus: 'Grade status',
          },
        };

  const statusLabel = (status: string | null | undefined) =>
    messages.common.statuses[
      (status ?? 'UNKNOWN').toUpperCase() as keyof typeof messages.common.statuses
    ] ?? messages.common.statuses.UNKNOWN;

  const renderCurriculumStatus = (course: MyCurriculumCourse) => {
    if (course.status === 'COMPLETED') {
      return (
        <span className="inline-flex flex-wrap items-center justify-end gap-1.5">
          <span
            className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${statusToneClass('success')}`}
          >
            {copy.statusCompleted}
          </span>
          {course.letterGrade ? (
            <span className="text-xs font-semibold text-foreground">
              {course.letterGrade}
              {typeof course.finalGrade === 'number'
                ? ` · ${course.finalGrade.toFixed(1)}`
                : ''}
            </span>
          ) : null}
        </span>
      );
    }
    if (course.status === 'IN_PROGRESS') {
      return (
        <span
          className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${statusToneClass('info')}`}
        >
          {copy.statusInProgress}
        </span>
      );
    }
    return (
      <span
        className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass('neutral')}`}
      >
        {copy.statusNotStarted}
      </span>
    );
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
            <Button
              variant="outline"
              onClick={() => window.print()}
              className="print:hidden"
            >
              <Printer className="mr-1.5 h-4 w-4" />
              {copy.printTranscript}
            </Button>
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
                    {curriculumData ? (
                      <span className="text-lg font-medium text-muted-foreground">
                        {' '}/ {formatNumber(curriculumData.curriculum.totalCredits)}
                      </span>
                    ) : null}
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
                  <div className="text-sm text-muted-foreground">{copy.completedCourses}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(completedCourseCount)}
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
                  <div className="text-sm text-muted-foreground">{copy.inProgressCourses}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(inProgressCourseCount)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('warning')}`}>
                  <FileText className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card variant="elevated">
              <CardHeader className="flex flex-col gap-3 pb-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <CardTitle className="text-base">{copy.gpaTrend}</CardTitle>
                  <p className="mt-0.5 text-xs text-muted-foreground">
                    {gpaMode === 'cumulative'
                      ? copy.gpaAllTrend
                      : gpaMode === 'semester'
                        ? copy.gpaSemesterTrend
                        : copy.gpaBothTrend}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <div className="flex items-center rounded-lg border border-border/80 bg-muted/40 p-0.5 text-xs font-medium">
                    <button
                      type="button"
                      onClick={() => setGpaMode('cumulative')}
                      className={cn(
                        'rounded-md px-2 py-1 transition-colors',
                        gpaMode === 'cumulative'
                          ? 'bg-background text-foreground shadow-sm'
                          : 'text-muted-foreground hover:text-foreground',
                      )}
                    >
                      {copy.gpaAllTrend}
                    </button>
                    <button
                      type="button"
                      onClick={() => setGpaMode('semester')}
                      className={cn(
                        'rounded-md px-2 py-1 transition-colors',
                        gpaMode === 'semester'
                          ? 'bg-background text-foreground shadow-sm'
                          : 'text-muted-foreground hover:text-foreground',
                      )}
                    >
                      {copy.gpaSemesterTrend}
                    </button>
                    <button
                      type="button"
                      onClick={() => setGpaMode('both')}
                      className={cn(
                        'rounded-md px-2 py-1 transition-colors',
                        gpaMode === 'both'
                          ? 'bg-background text-foreground shadow-sm'
                          : 'text-muted-foreground hover:text-foreground',
                      )}
                    >
                      {copy.gpaBothTrend}
                    </button>
                  </div>
                  {(gpaMode === 'semester' || gpaMode === 'both') && (
                    <div className="min-w-[140px]">
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
                        className="h-8 text-xs"
                      />
                    </div>
                  )}
                </div>
              </CardHeader>
              <CardContent>
                <GpaTrendChart
                  points={gpaTrendPoints}
                  cumulativePoints={cumulativeGpaTrendPoints}
                  tenScalePoints={tenScaleTrendPoints}
                  tenScaleLegendLabel={copy.avgTenScale}
                  mode={gpaMode}
                  selectedLabel={selectedSemester ? selectedSemesterName : undefined}
                  ariaLabel={copy.gpaTrend}
                />
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardHeader>
                <CardTitle className="text-base">{copy.distribution}</CardTitle>
              </CardHeader>
              <CardContent>
                <GradeDistributionChart
                  buckets={gradeDistribution}
                  ariaLabel={copy.distribution}
                />
              </CardContent>
            </Card>
          </div>

          <div className="space-y-6">
            {transcriptSemesters.map((semester) => (
              <Card key={semester.semesterId} variant="muted">
                <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <CardTitle className="text-xl">
                      {getLocalizedFlatLabel(
                        locale,
                        semester.semesterName,
                        semester.semesterNameEn,
                        semester.semesterNameVi,
                        semester.semesterName,
                      )}
                    </CardTitle>
                    <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
                      <Info className="h-3.5 w-3.5 text-primary shrink-0" />
                      <span>{copy.clickToViewDetail}</span>
                    </p>
                  </div>
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
                        role="button"
                        tabIndex={0}
                        onClick={() => setSelectedRecord(record)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setSelectedRecord(record);
                          }
                        }}
                        className="cursor-pointer rounded-lg border border-border/70 bg-card p-4 shadow-sm transition hover:border-primary/50 hover:bg-muted/30"
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
                              {statusLabel(record.enrollmentStatus)}
                            </dd>
                          </div>
                          <div className="col-span-2 border-t border-border/60 pt-3">
                            <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                              {copy.headers.gradeStatus}
                            </dt>
                            <dd className="mt-1 break-words text-foreground">
                              {statusLabel(record.gradeStatus)}
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
                          <tr
                            key={record.id}
                            role="button"
                            tabIndex={0}
                            onClick={() => setSelectedRecord(record)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                setSelectedRecord(record);
                              }
                            }}
                            className="group cursor-pointer transition-colors hover:bg-muted/50"
                            title={copy.clickToViewDetail}
                          >
                            <td className="px-2 py-4">
                              <div className="font-medium text-foreground group-hover:text-primary transition-colors">
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
                            <td className="px-2 py-4 text-center text-foreground font-semibold">
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
                                {statusLabel(record.enrollmentStatus)}
                              </span>
                            </td>
                            <td className="px-2 py-4 text-right">
                              <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                {statusLabel(record.gradeStatus)}
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

          {/* Study program: per-course status grouped by year and semester */}
          {curriculumState === 'loading' ? (
            <Card variant="muted">
              <CardContent className="py-6 text-center text-sm text-muted-foreground">
                {copy.programLoading}
              </CardContent>
            </Card>
          ) : curriculumData ? (
            <Card variant="muted">
              <CardHeader className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <CardTitle className="text-xl">{copy.programTitle}</CardTitle>
                  <p className="mt-1 break-words text-sm text-muted-foreground">
                    {getLocalizedFlatLabel(
                      locale,
                      curriculumData.curriculum.name,
                      curriculumData.curriculum.nameEn,
                      curriculumData.curriculum.nameVi,
                      curriculumData.curriculum.name,
                    )}
                    {' · '}
                    {curriculumData.curriculum.code}
                  </p>
                </div>
                <div className="w-full max-w-xs shrink-0 space-y-1.5">
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span>
                      {formatNumber(curriculumCompletedCredits)} /{' '}
                      {formatNumber(curriculumData.curriculum.totalCredits)}{' '}
                      {copy.programCreditsUnit}
                    </span>
                    <span className="font-semibold tabular-nums text-foreground">
                      {curriculumCreditPercent}%
                    </span>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                    <div
                      className="h-full rounded-full bg-primary transition-all duration-300"
                      style={{ width: `${curriculumCreditPercent}%` }}
                    />
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-6">
                {curriculumGroups.map((group) => (
                  <section
                    key={`${group.year}-${group.semester}`}
                    aria-label={copy.yearSemesterLabel(group.year, group.semester)}
                  >
                    <h3 className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                      {copy.yearSemesterLabel(group.year, group.semester)}
                    </h3>
                    <div className="space-y-2 md:hidden">
                      {group.courses.map((course) => (
                        <div
                          key={course.courseId}
                          className="rounded-lg border border-border/70 bg-card p-3 shadow-sm"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                                {course.code}
                              </p>
                              <h4 className="mt-0.5 break-words text-sm font-semibold text-foreground">
                                {getLocalizedFlatLabel(
                                  locale,
                                  course.name,
                                  course.nameEn,
                                  course.nameVi,
                                  course.name,
                                )}
                              </h4>
                            </div>
                            {renderCurriculumStatus(course)}
                          </div>
                          <div className="mt-2 flex flex-wrap items-center gap-2 border-t border-border/60 pt-2 text-xs text-muted-foreground">
                            <span>
                              {formatNumber(course.credits)} {copy.programHeaders.credits}
                            </span>
                            {course.isMandatory ? (
                              <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-foreground">
                                {copy.mandatory}
                              </span>
                            ) : null}
                          </div>
                        </div>
                      ))}
                    </div>
                    <div className="hidden overflow-x-auto md:block">
                      <table className="w-full text-sm">
                        <thead>
                          <tr className="border-b border-border/70 text-left text-muted-foreground">
                            <th className="px-2 py-2.5 font-medium">{copy.programHeaders.course}</th>
                            <th className="px-2 py-2.5 text-center font-medium">{copy.programHeaders.credits}</th>
                            <th className="px-2 py-2.5 text-center font-medium">{copy.mandatory}</th>
                            <th className="px-2 py-2.5 text-right font-medium">{copy.programHeaders.status}</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border/60">
                          {group.courses.map((course) => (
                            <tr key={course.courseId} className="transition-colors hover:bg-muted/50">
                              <td className="px-2 py-3">
                                <div className="font-medium text-foreground">{course.code}</div>
                                <div className="text-muted-foreground">
                                  {getLocalizedFlatLabel(
                                    locale,
                                    course.name,
                                    course.nameEn,
                                    course.nameVi,
                                    course.name,
                                  )}
                                </div>
                              </td>
                              <td className="px-2 py-3 text-center text-muted-foreground">
                                {formatNumber(course.credits)}
                              </td>
                              <td className="px-2 py-3 text-center">
                                {course.isMandatory ? (
                                  <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                    {copy.mandatory}
                                  </span>
                                ) : (
                                  <span className="text-muted-foreground">-</span>
                                )}
                              </td>
                              <td className="px-2 py-3 text-right">
                                {renderCurriculumStatus(course)}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </section>
                ))}
              </CardContent>
            </Card>
          ) : (
            <EmptyState
              icon={BookOpen}
              title={copy.programEmpty}
              description={
                locale === 'vi'
                  ? 'Danh sách môn học theo chương trình đào tạo sẽ hiển thị tại đây.'
                  : 'Courses from your study program will appear here.'
              }
              className="min-h-[120px] border-none bg-transparent px-0 py-0"
            />
          )}

          {/* Grade Detail Modal for GK / CK Breakdown */}
          <GradeDetailModal
            record={selectedRecord}
            isOpen={Boolean(selectedRecord)}
            onClose={() => setSelectedRecord(null)}
          />
        </>
      )}
    </div>
  );
}
