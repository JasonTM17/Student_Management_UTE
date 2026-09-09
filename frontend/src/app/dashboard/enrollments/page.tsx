'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  Calendar,
  CheckCircle2,
  Clock,
  GraduationCap,
  Layers,
  MapPin,
  Trash2,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { curriculumApi, enrollmentsApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { Enrollment, MyCurriculumCourse, MyCurriculumResponse } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

const statusTone: Record<string, string> = {
  CONFIRMED: metricToneClass('success'),
  ENROLLED: metricToneClass('success'),
  PENDING: metricToneClass('warning'),
  DROPPED: metricToneClass('danger'),
  COMPLETED: metricToneClass('info'),
};

function getDayName(day: number, locale: 'en' | 'vi') {
  // DB convention: 1 = Sunday/Chủ nhật, 2 = Monday/Thứ hai, ..., 7 = Saturday/Thứ bảy (0 = Sunday)
  const normalizedDay = day === 0 ? 1 : day;
  const viMap: Record<number, string> = {
    1: 'Chủ nhật',
    2: 'Thứ hai',
    3: 'Thứ ba',
    4: 'Thứ tư',
    5: 'Thứ năm',
    6: 'Thứ sáu',
    7: 'Thứ bảy',
  };
  const enMap: Record<number, string> = {
    1: 'Sunday',
    2: 'Monday',
    3: 'Tuesday',
    4: 'Wednesday',
    5: 'Thursday',
    6: 'Friday',
    7: 'Saturday',
  };
  return (locale === 'vi' ? viMap[normalizedDay] : enMap[normalizedDay]) ?? '';
}

export default function EnrollmentsPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatDate, formatNumber, messages } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [curriculumData, setCurriculumData] = useState<MyCurriculumResponse | null>(null);
  const [activeTab, setActiveTab] = useState<'curriculum' | 'enrollments'>('curriculum');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'COMPLETED' | 'IN_PROGRESS' | 'NOT_STARTED'>('ALL');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [isDropping, setIsDropping] = useState<string | null>(null);
  const { confirm, confirmationDialog } = useConfirmationDialog();

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu sinh viên',
          title: 'Môn học của tôi',
          description:
            'Theo dõi lộ trình toàn khóa theo chương trình đào tạo và cập nhật lớp học phần đã đăng ký.',
          browseSections: 'Xem lớp học phần',
          loading: 'Đang tải thông tin môn học & CTĐT',
          unavailableTitle: 'Thông tin môn học chưa sẵn sàng',
          emptyTitle: 'Chưa có môn học',
          emptyDescription:
            'Khi bạn đăng ký một lớp học phần, lịch học và thông tin lớp sẽ xuất hiện ở đây.',
          tabCurriculum: 'Chương trình đào tạo (CTĐT)',
          tabEnrollments: 'Lớp học phần đang đăng ký',
          statCurriculumTotal: 'Tổng môn học CTĐT',
          statCompleted: 'Đã hoàn tất',
          statInProgress: 'Đang học',
          statRemaining: 'Chưa hoàn tất',
          filterAll: 'Tất cả môn',
          filterCompleted: 'Đã hoàn tất',
          filterInProgress: 'Đang học',
          filterNotStarted: 'Chưa hoàn tất',
          yearPrefix: 'Năm học',
          semesterPrefix: 'Học kỳ',
          mandatory: 'Bắt buộc',
          elective: 'Tự chọn',
          creditsLabel: 'tín chỉ',
          gradeScore: 'Điểm',
          noCurriculumCourses: 'Không có môn học nào thuộc nhóm trạng thái này.',
          recordTitle: 'Hồ sơ đăng ký lớp học phần',
          sectionPrefix: 'Lớp học phần',
          enrolledOn: 'Đăng ký ngày',
          unknownCourse: 'Môn học',
          unavailableCourseName: 'Chưa có tên môn',
          unknownSection: 'Chưa rõ lớp học phần',
          dropCourse: 'Hủy môn học',
          droppingCourse: 'Đang hủy môn',
          confirmTitle: 'Hủy môn học',
          confirmMessage: (courseLabel: string) =>
            `Hủy ${courseLabel}? Hành động này giữ cho việc đổi lịch học luôn rõ ràng và có chủ đích.`,
          dropped: 'Đã hủy môn học',
          dropFailed: 'Hiện chưa thể hủy môn học này.',
          studentProfileMissing:
            'Không tìm thấy hồ sơ sinh viên trong phiên hiện tại.',
          loadFailed: 'Hiện chưa thể tải danh sách môn học của bạn.',
        }
      : {
          eyebrow: 'Student area',
          title: 'My courses',
          description:
            'Track your overall study roadmap against the degree curriculum and manage your enrolled class sections.',
          browseSections: 'Browse classes',
          loading: 'Loading courses & study program',
          unavailableTitle: 'Course information unavailable',
          emptyTitle: 'No courses yet',
          emptyDescription:
            'Once you enroll in a class, its schedule and details will appear here.',
          tabCurriculum: 'Curriculum Roadmap',
          tabEnrollments: 'Enrolled Classes',
          statCurriculumTotal: 'Total Curriculum Courses',
          statCompleted: 'Completed',
          statInProgress: 'In Progress',
          statRemaining: 'Not Started',
          filterAll: 'All courses',
          filterCompleted: 'Completed',
          filterInProgress: 'In Progress',
          filterNotStarted: 'Not Started',
          yearPrefix: 'Year',
          semesterPrefix: 'Semester',
          mandatory: 'Mandatory',
          elective: 'Elective',
          creditsLabel: 'credits',
          gradeScore: 'Grade',
          noCurriculumCourses: 'No courses found for the selected status filter.',
          recordTitle: 'Enrolled Class Sections',
          sectionPrefix: 'Class',
          enrolledOn: 'Enrolled',
          unknownCourse: 'Course',
          unavailableCourseName: 'Unavailable',
          unknownSection: 'Unknown class',
          dropCourse: 'Drop course',
          droppingCourse: 'Dropping course',
          confirmTitle: 'Drop course',
          confirmMessage: (courseLabel: string) =>
            `Drop ${courseLabel}? This keeps the action explicit and prevents accidental schedule changes.`,
          dropped: 'Course dropped',
          dropFailed: 'We could not drop this course.',
          studentProfileMissing:
            'Your student profile is not available in this session.',
          loadFailed: 'Your current enrollments could not be loaded.',
        };

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [enrollmentsData, curriculumResult] = await Promise.allSettled([
        enrollmentsApi.getMyEnrollments(),
        curriculumApi.getMyCurriculum(),
      ]);

      if (enrollmentsData.status === 'fulfilled') {
        setEnrollments(enrollmentsData.value);
      } else {
        setError(copy.loadFailed);
      }

      if (curriculumResult.status === 'fulfilled') {
        setCurriculumData(curriculumResult.value);
      }
    } catch {
      setError(copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void fetchData();
    }
  }, [fetchData, hasAccess]);

  const handleDrop = async (enrollmentId: string, courseLabel: string) => {
    if (!user?.studentId) {
      toast.error(copy.studentProfileMissing);
      return;
    }

    const shouldContinue = await confirm({
      title: copy.confirmTitle,
      message: copy.confirmMessage(courseLabel),
      confirmText: copy.dropCourse,
      variant: 'destructive',
    });

    if (!shouldContinue) {
      return;
    }

    setIsDropping(enrollmentId);
    try {
      await enrollmentsApi.drop(enrollmentId);
      toast.success(copy.dropped);
      await fetchData();
    } catch (error: any) {
      toast.error(campusErrorMessage(error, messages.common.campusErrors, copy.dropFailed));
    } finally {
      setIsDropping(null);
    }
  };

  const curriculumCourses = useMemo(
    () => curriculumData?.courses ?? [],
    [curriculumData],
  );

  const completedCurriculum = useMemo(
    () => curriculumCourses.filter((c) => c.status === 'COMPLETED'),
    [curriculumCourses],
  );

  const inProgressCurriculum = useMemo(
    () => curriculumCourses.filter((c) => c.status === 'IN_PROGRESS'),
    [curriculumCourses],
  );

  const notStartedCurriculum = useMemo(
    () => curriculumCourses.filter((c) => c.status === 'NOT_STARTED'),
    [curriculumCourses],
  );

  const activeEnrollments = useMemo(
    () =>
      enrollments.filter(
        (e) => e.status === 'CONFIRMED' || e.status === 'ENROLLED',
      ),
    [enrollments],
  );

  const groupedCurriculum = useMemo(() => {
    const filtered = curriculumCourses.filter((course) => {
      if (statusFilter === 'ALL') return true;
      return course.status === statusFilter;
    });

    const groups: Record<number, Record<number, MyCurriculumCourse[]>> = {};
    for (const course of filtered) {
      const year = course.year || 1;
      const sem = course.semester || 1;
      if (!groups[year]) groups[year] = {};
      if (!groups[year][sem]) groups[year][sem] = [];
      groups[year][sem].push(course);
    }
    return groups;
  }, [curriculumCourses, statusFilter]);

  const summaryCards = useMemo(() => {
    if (curriculumCourses.length > 0) {
      const totalCredits = curriculumCourses.reduce((sum, c) => sum + (c.credits || 0), 0);
      const completedCredits = completedCurriculum.reduce((sum, c) => sum + (c.credits || 0), 0);

      return [
        {
          label: copy.statCurriculumTotal,
          value: `${formatNumber(curriculumCourses.length)} môn`,
          subvalue: `${formatNumber(totalCredits)} ${copy.creditsLabel}`,
          tone: metricToneClass('info'),
          icon: GraduationCap,
        },
        {
          label: copy.statCompleted,
          value: `${formatNumber(completedCurriculum.length)} môn`,
          subvalue: `${formatNumber(completedCredits)} / ${formatNumber(totalCredits)} ${copy.creditsLabel}`,
          tone: metricToneClass('success'),
          icon: CheckCircle2,
        },
        {
          label: copy.statInProgress,
          value: `${formatNumber(inProgressCurriculum.length)} môn`,
          subvalue: `${formatNumber(notStartedCurriculum.length)} ${copy.statRemaining.toLowerCase()}`,
          tone: metricToneClass('warning'),
          icon: Clock,
        },
      ];
    }

    return [
      {
        label: copy.statCurriculumTotal,
        value: formatNumber(enrollments.length),
        subvalue: '',
        tone: metricToneClass('info'),
        icon: BookOpen,
      },
      {
        label: copy.statCompleted,
        value: formatNumber(
          enrollments.filter((e) => e.status === 'COMPLETED').length,
        ),
        subvalue: '',
        tone: metricToneClass('success'),
        icon: CheckCircle2,
      },
      {
        label: copy.statInProgress,
        value: formatNumber(activeEnrollments.length),
        subvalue: '',
        tone: metricToneClass('warning'),
        icon: Clock,
      },
    ];
  }, [
    activeEnrollments.length,
    completedCurriculum,
    copy.creditsLabel,
    copy.statCompleted,
    copy.statCurriculumTotal,
    copy.statInProgress,
    copy.statRemaining,
    curriculumCourses,
    enrollments,
    formatNumber,
    inProgressCurriculum.length,
    notStartedCurriculum.length,
  ]);

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
          <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchData()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            {summaryCards.map((item) => {
              const Icon = item.icon;
              return (
                <Card key={item.label} variant="elevated">
                  <CardContent className="flex items-center justify-between gap-4 pt-6">
                    <div>
                      <div className="text-sm font-medium text-muted-foreground">
                        {item.label}
                      </div>
                      <div className="mt-1 text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
                        {item.value}
                      </div>
                      {item.subvalue ? (
                        <div className="mt-1 text-xs text-muted-foreground">
                          {item.subvalue}
                        </div>
                      ) : null}
                    </div>
                    <div
                      className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-lg ${item.tone}`}
                    >
                      <Icon className="h-5 w-5" />
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-3">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setActiveTab('curriculum')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition ${
                  activeTab === 'curriculum'
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'bg-secondary/40 text-muted-foreground hover:bg-secondary hover:text-foreground'
                }`}
              >
                <Layers className="h-4 w-4" />
                {copy.tabCurriculum}
                {curriculumCourses.length > 0 && (
                  <span className="ml-1 rounded-full bg-primary-foreground/20 px-2 py-0.5 text-xs">
                    {curriculumCourses.length}
                  </span>
                )}
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('enrollments')}
                className={`flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-semibold transition ${
                  activeTab === 'enrollments'
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'bg-secondary/40 text-muted-foreground hover:bg-secondary hover:text-foreground'
                }`}
              >
                <BookOpen className="h-4 w-4" />
                {copy.tabEnrollments}
                <span className="ml-1 rounded-full bg-muted px-2 py-0.5 text-xs text-foreground">
                  {enrollments.length}
                </span>
              </button>
            </div>

            {activeTab === 'curriculum' && (
              <div className="flex flex-wrap items-center gap-1.5 text-xs">
                {(
                  [
                    { key: 'ALL', label: copy.filterAll, count: curriculumCourses.length },
                    { key: 'COMPLETED', label: copy.filterCompleted, count: completedCurriculum.length },
                    { key: 'IN_PROGRESS', label: copy.filterInProgress, count: inProgressCurriculum.length },
                    { key: 'NOT_STARTED', label: copy.filterNotStarted, count: notStartedCurriculum.length },
                  ] as const
                ).map((btn) => (
                  <button
                    key={btn.key}
                    type="button"
                    onClick={() => setStatusFilter(btn.key)}
                    className={`rounded-full px-3 py-1 font-medium transition ${
                      statusFilter === btn.key
                        ? 'bg-foreground text-background font-semibold'
                        : 'border border-border/80 bg-card text-muted-foreground hover:text-foreground'
                    }`}
                  >
                    {btn.label} ({btn.count})
                  </button>
                ))}
              </div>
            )}
          </div>

          {activeTab === 'curriculum' && (
            <div className="space-y-8">
              {curriculumData?.curriculum && (
                <div className="rounded-xl border border-border/70 bg-card/60 p-4 sm:p-5">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                        {curriculumData.curriculum.code}
                      </p>
                      <h3 className="text-lg font-bold text-foreground sm:text-xl">
                        {getLocalizedName(locale, curriculumData.curriculum, curriculumData.curriculum.name)}
                      </h3>
                    </div>
                    <div className="flex items-center gap-3">
                      <span className="rounded-xl border border-border/80 bg-secondary/40 px-3.5 py-1.5 text-xs font-medium text-foreground">
                        {copy.statCompleted}: <strong className="font-semibold text-primary">{completedCurriculum.length}</strong> / {curriculumCourses.length} môn
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {Object.keys(groupedCurriculum).length === 0 ? (
                <EmptyState
                  icon={BookOpen}
                  title={copy.emptyTitle}
                  description={copy.noCurriculumCourses}
                />
              ) : (
                Object.entries(groupedCurriculum).map(([year, semestersMap]) => (
                  <div key={`year-${year}`} className="space-y-5">
                    <div className="flex items-center gap-2 border-b border-border/60 pb-2">
                      <GraduationCap className="h-5 w-5 text-primary" />
                      <h3 className="text-lg font-bold text-foreground">
                        {copy.yearPrefix} {year}
                      </h3>
                    </div>

                    <div className="grid gap-6 lg:grid-cols-2">
                      {Object.entries(semestersMap).map(([sem, courses]) => (
                        <Card key={`year-${year}-sem-${sem}`} variant="muted" className="overflow-hidden">
                          <CardHeader className="border-b border-border/60 bg-secondary/20 py-3">
                            <CardTitle className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                              {copy.semesterPrefix} {sem} - {courses.length} {copy.creditsLabel === 'credits' ? 'courses' : 'môn'}
                            </CardTitle>
                          </CardHeader>
                          <CardContent className="divide-y divide-border/60 p-0">
                            {courses.map((course) => {
                              const courseTitle = getLocalizedName(locale, course, course.name);
                              const isCompleted = course.status === 'COMPLETED';
                              const isInProgress = course.status === 'IN_PROGRESS';

                              return (
                                <div
                                  key={course.courseId}
                                  className="flex flex-col gap-2 p-4 transition-colors hover:bg-secondary/15 sm:flex-row sm:items-center sm:justify-between"
                                >
                                  <div className="min-w-0 flex-1">
                                    <div className="flex flex-wrap items-center gap-2">
                                      <span className="font-mono text-xs font-semibold text-primary">
                                        {course.code}
                                      </span>
                                      <span className="text-xs text-muted-foreground">
                                        {course.credits} {copy.creditsLabel}
                                      </span>
                                      <span
                                        className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                                          course.isMandatory
                                            ? 'bg-primary/10 text-primary'
                                            : 'bg-secondary text-muted-foreground'
                                        }`}
                                      >
                                        {course.isMandatory ? copy.mandatory : copy.elective}
                                      </span>
                                    </div>
                                    <h4 className="mt-1 font-medium text-foreground">
                                      {courseTitle}
                                    </h4>
                                  </div>

                                  <div className="flex shrink-0 items-center gap-2">
                                    {isCompleted ? (
                                      <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${statusToneClass('success')}`}>
                                        <CheckCircle2 className="h-3.5 w-3.5" />
                                        {copy.filterCompleted}
                                        {course.finalGrade !== null && course.finalGrade !== undefined ? (
                                          <span className="ml-1 border-l border-border/80 pl-1 font-bold">
                                            {course.finalGrade.toFixed(1)} {course.letterGrade ? `(${course.letterGrade})` : ''}
                                          </span>
                                        ) : null}
                                      </span>
                                    ) : isInProgress ? (
                                      <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${statusToneClass('info')}`}>
                                        <Clock className="h-3.5 w-3.5" />
                                        {copy.filterInProgress}
                                      </span>
                                    ) : (
                                      <span className={`inline-flex items-center rounded-full px-3 py-1 text-xs font-medium ${statusToneClass('neutral')}`}>
                                        {copy.filterNotStarted}
                                      </span>
                                    )}
                                  </div>
                                </div>
                              );
                            })}
                          </CardContent>
                        </Card>
                      ))}
                    </div>
                  </div>
                ))
              )}
            </div>
          )}

          {activeTab === 'enrollments' && (
            <div>
              {enrollments.length === 0 ? (
                <EmptyState
                  icon={BookOpen}
                  title={copy.emptyTitle}
                  description={copy.emptyDescription}
                  action={
                    <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
                  }
                />
              ) : (
                <Card variant="muted">
                  <CardHeader>
                    <CardTitle className="text-xl">{copy.recordTitle}</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    {enrollments.map((enrollment) => {
                      const courseCode = enrollment.section?.course?.code ?? copy.unknownCourse;
                      const courseName = getLocalizedName(
                        locale,
                        enrollment.section?.course,
                        enrollment.section?.course?.name ?? copy.unavailableCourseName,
                      );
                      const courseLabel = `${courseCode} ${courseName}`.trim();

                      return (
                        <div
                          key={enrollment.id}
                          className="rounded-xl border border-border/80 bg-card px-5 py-5 shadow-xs transition hover:border-primary/40"
                        >
                          <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                            <div className="space-y-3">
                              <div className="flex flex-wrap items-center gap-2">
                                <h2 className="text-lg font-semibold text-foreground">
                                  {courseCode} - {courseName}
                                </h2>
                                <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                  {copy.sectionPrefix}{' '}
                                  {enrollment.section?.sectionNumber || copy.unknownSection}
                                </span>
                                <span
                                  className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                                    statusTone[enrollment.status] ??
                                      'bg-secondary text-foreground'
                                  }`}
                                >
                                  {messages.common.statuses[enrollment.status as keyof typeof messages.common.statuses] ?? messages.common.statuses.UNKNOWN}
                                </span>
                              </div>

                              <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                                <span className="inline-flex items-center gap-2">
                                  <Calendar className="h-4 w-4" />
                                  {copy.enrolledOn} {formatDate(enrollment.enrolledAt)}
                                </span>
                                <span>
                                  {enrollment.section?.course?.credits} {copy.creditsLabel}
                                </span>
                                {enrollment.section?.lecturer ? (
                                  <span>
                                    {enrollment.section.lecturer.user?.firstName}{' '}
                                    {enrollment.section.lecturer.user?.lastName}
                                  </span>
                                ) : null}
                                {enrollment.section?.classroom ? (
                                  <span className="inline-flex items-center gap-2">
                                    <MapPin className="h-4 w-4" />
                                    {enrollment.section.classroom.building}{' '}
                                    {enrollment.section.classroom.roomNumber}
                                  </span>
                                ) : null}
                              </div>

                              {enrollment.section?.schedules?.length ? (
                                <div className="flex flex-wrap gap-2">
                                  {enrollment.section.schedules.map((schedule, index) => (
                                    <span
                                      key={`${enrollment.id}-${index}`}
                                      className="inline-flex items-center gap-2 rounded-full bg-secondary px-3 py-1 text-xs text-foreground"
                                    >
                                      <Clock className="h-3.5 w-3.5" />
                                      {getDayName(schedule.dayOfWeek, locale)}{' '}
                                      {schedule.startTime}-
                                      {schedule.endTime}
                                    </span>
                                  ))}
                                </div>
                              ) : null}
                            </div>

                            {enrollment.status !== 'DROPPED' &&
                            enrollment.status !== 'COMPLETED' ? (
                              <Button
                                type="button"
                                variant="destructive"
                                onClick={() => void handleDrop(enrollment.id, courseLabel)}
                                disabled={isDropping === enrollment.id}
                              >
                                <Trash2 className="mr-2 h-4 w-4" />
                                {isDropping === enrollment.id
                                  ? copy.droppingCourse
                                  : copy.dropCourse}
                              </Button>
                            ) : null}
                          </div>
                        </div>
                      );
                    })}
                  </CardContent>
                </Card>
              )}
            </div>
          )}
        </>
      )}

      {confirmationDialog}
    </div>
  );
}
