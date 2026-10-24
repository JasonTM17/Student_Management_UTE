'use client';

import { useMemo } from 'react';
import {
  ArrowRight,
  Bell,
  BookMarked,
  BookOpen,
  Calendar,
  ClipboardList,
  Clock,
  FileText,
  GraduationCap,
  MapPin,
  TrendingUp,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { enrollmentsApi, registrationApi, semestersApi } from '@/lib/api';
import { STATIC_STALE_TIME_MS, useApiQuery } from '@/lib/query';
import { hasCsrfSessionHint } from '@/lib/session-hint';
import { getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { deriveStudentDashboardMetrics } from '@/lib/student-dashboard-metrics';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { LocalizedLink } from '@/components/LocalizedLink';
import {
  StudentDashboardCreditProgress,
  StudentDashboardMetricCards,
} from '@/components/dashboard/StudentDashboardMetrics';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { metricToneClass } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { WorkspacePanel } from '@/components/dashboard/WorkspaceSurface';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface Enrollment {
  id: string;
  status: string;
  section?: {
    course?: {
      code: string;
      name: string;
      credits?: number;
    };
    sectionNumber: string;
    status?: string;
    schedules?: {
      dayOfWeek: number;
      startTime: string;
      endTime: string;
      classroom?: {
        building?: string;
        roomNumber?: string;
      };
    }[];
  };
}

interface Semester {
  id: string;
  name: string;
  status: string;
}

interface UpcomingMeeting {
  id: string;
  dayOfWeek: number; // 1=Monday .. 7=Sunday
  daysUntil: number; // 0 = today
  startTime: string;
  endTime: string;
  courseCode: string;
  courseName: string;
  building: string;
  roomNumber: string;
}

function getMeetingShift(startTime: string, locale: string) {
  const hour = parseInt(startTime.split(':')[0], 10);
  if (hour < 12) {
    return {
      label: locale === 'vi' ? 'Ca Sáng' : 'Morning',
      tone: 'border-[hsl(var(--status-warning)/0.35)] bg-[hsl(var(--status-warning)/0.12)] text-[hsl(var(--status-warning-foreground))]',
    };
  }
  if (hour < 17) {
    return {
      label: locale === 'vi' ? 'Ca Chiều' : 'Afternoon',
      tone: 'border-status-info/30 bg-status-info/10 text-status-info-foreground',
    };
  }
  return {
    label: locale === 'vi' ? 'Ca Tối' : 'Evening',
    tone: 'border-status-neutral/30 bg-status-neutral/10 text-status-neutral-foreground',
  };
}

// The live cap comes from the registration round (registrationApi.summary);
// without it the widget shows the earned credits only instead of inventing a
// limit that would contradict the registration page.

const quickAccess = [
  {
    href: '/dashboard/register',
    icon: ClipboardList,
    tone: metricToneClass('info'),
    labelKey: 'courseRegistration',
  },
  {
    href: '/dashboard/schedule',
    icon: Calendar,
    tone: metricToneClass('success'),
    labelKey: 'schedule',
  },
  {
    href: '/dashboard/grades',
    icon: TrendingUp,
    tone: metricToneClass('neutral'),
    labelKey: 'grades',
  },
  {
    href: '/dashboard/transcript',
    icon: FileText,
    tone: metricToneClass('info'),
    labelKey: 'transcript',
  },
  {
    href: '/dashboard/thesis',
    icon: GraduationCap,
    tone: metricToneClass('warning'),
    labelKey: 'thesis',
  },
  {
    href: '/dashboard/announcements',
    icon: Bell,
    tone: metricToneClass('danger'),
    labelKey: 'announcements',
  },
] as const;

export default function DashboardPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatDate, formatNumber, messages } = useI18n();
  // The csrf cookie is written by the login response before this page mounts,
  // so its presence means a session most likely exists: the data waterfall may
  // start while /auth/me is still resolving instead of queueing behind it. If
  // the session turns out dead, the API 401 walks the same refresh/redirect
  // path a failed /auth/me would.
  const canLoad = hasAccess || hasCsrfSessionHint();

  const semestersQuery = useApiQuery(
    ['dashboard', 'semesters'],
    async () => (await semestersApi.getAll()).data,
    // Semester reference data changes rarely; caching it across navigations
    // removes a full waterfall level on repeat visits.
    { enabled: canLoad, staleTime: STATIC_STALE_TIME_MS },
  );
  const semesters = useMemo(() => semestersQuery.data ?? [], [semestersQuery.data]);
  const currentSemester = useMemo(() => pickPreferredSemesterId(semesters), [semesters]);

  // Same ['registration','enrollments'|'summary'] keys the register page uses,
  // so its mutations invalidate this data too. Enrollments stay gated on the
  // resolved user: the key carries user.id, and firing before /auth/me would
  // only refetch the same rows under a second key.
  const enrollmentsQuery = useApiQuery(
    ['registration', 'enrollments', currentSemester, user?.id ?? ''],
    () => enrollmentsApi.getMyEnrollments(currentSemester || undefined),
    { enabled: hasAccess && Boolean(currentSemester), staleTime: 5 * 60_000 },
  );
  const summaryQuery = useApiQuery(
    ['registration', 'summary', currentSemester],
    () => registrationApi.summary(currentSemester).catch(() => null),
    { enabled: canLoad && Boolean(currentSemester), staleTime: 5 * 60_000 },
  );

  const enrollments = (enrollmentsQuery.data ?? []) as Enrollment[];
  const termCreditCap = summaryQuery.data?.creditLimit ?? null;
  const isLoading =
    semestersQuery.isPending ||
    (Boolean(currentSemester) && (enrollmentsQuery.isPending || summaryQuery.isPending));
  const error =
    semestersQuery.isError || enrollmentsQuery.isError
      ? messages.studentDashboard.errors.loadFailed
      : '';
  const refetchData = () => {
    void semestersQuery.refetch();
    void enrollmentsQuery.refetch();
    void summaryQuery.refetch();
  };

  const copy =
    locale === 'vi'
      ? {
          description: 'Tổng quan thông tin học vụ và tiến độ học kỳ hiện tại.',
          stats: {
            courses: 'Môn học kỳ này',
            active: 'Đang học',
            pending: 'Chờ xử lý',
            term: 'Học kỳ hiện tại',
          },
          upcomingTitle: 'Lịch học hôm nay & sắp tới',
          today: 'Hôm nay',
          noMeetings: 'Chưa có buổi học nào được xếp lịch.',
          openSchedule: 'Xem thời khóa biểu',
          progressTitle: 'Tiến độ học tập',
          creditLabel: 'Tín chỉ đăng ký kỳ này',
          progressStatus: (pct: number) => `Đã đạt ${pct}% chỉ tiêu học kỳ`,
          creditsAvailable: (rem: number) => `Còn có thể đăng ký thêm ${rem} tín chỉ`,
          creditsCapped: 'Đã đạt hạn mức tín chỉ tối đa',
          creditsEarnedOnly: 'Tín chỉ đã đăng ký kỳ này',
          pendingBadge: (count: string) => `${count} đăng ký chờ xử lý`,
          quickAccessTitle: 'Truy cập nhanh',
          coursesUnit: 'môn',
        } as const
      : {
          description: 'A quick snapshot of your active term.',
          stats: {
            courses: 'Courses this term',
            active: 'Active',
            pending: 'Pending',
            term: 'Current term',
          },
          upcomingTitle: 'Today & upcoming classes',
          today: 'Today',
          noMeetings: 'No class meetings scheduled yet.',
          openSchedule: 'Open schedule',
          progressTitle: 'Study progress',
          creditLabel: 'Credits this term',
          progressStatus: (pct: number) => `${pct}% of term credit capacity`,
          creditsAvailable: (rem: number) => `Can register up to ${rem} more credits`,
          creditsCapped: 'Maximum credit limit reached',
          creditsEarnedOnly: 'Credits registered this term',
          pendingBadge: (count: string) => `${count} registrations pending`,
          quickAccessTitle: 'Quick access',
          coursesUnit: 'courses',
        } as const;

  const currentSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === currentSemester),
        messages.studentDashboard.currentTermFallback,
      ) || messages.studentDashboard.currentTermFallback
    );
  }, [
    currentSemester,
    locale,
    messages.studentDashboard.currentTermFallback,
    semesters,
  ]);

  // Seat accounting mirrors the backend: PENDING rows occupy a seat and count
  // toward the credit cap exactly like ENROLLED ones (lib/enrollment-status).
  const dashboardMetrics = deriveStudentDashboardMetrics(enrollments, termCreditCap);
  const activeCourses = dashboardMetrics.activeCourses;
  const pendingCourses = dashboardMetrics.pendingCourses;
  const highlightedCourses = activeCourses.slice(0, 3);

  // Next class meetings derived from active sections.
  // DB schedule.dayOfWeek: 1=Sunday, 2=Monday, ..., 7=Saturday (0=Sunday).
  // Normalize to ISO standard: 1=Monday .. 7=Sunday so daysUntil sorts from today.
  const jsDay = new Date().getDay(); // 0 = Sun, 1 = Mon ... 6 = Sat
  const todayDow = jsDay === 0 ? 7 : jsDay; // 1 = Monday ... 7 = Sunday
  const upcomingMeetings: UpcomingMeeting[] = [];
  activeCourses.forEach((enrollment) => {
    const section = enrollment.section;
    section?.schedules?.forEach((schedule, index) => {
      const rawDay = schedule.dayOfWeek;
      const isoDay = rawDay === 0 || rawDay === 1 ? 7 : rawDay - 1;
      upcomingMeetings.push({
        id: `${enrollment.id}-${index}`,
        dayOfWeek: isoDay,
        daysUntil: (isoDay - todayDow + 7) % 7,
        startTime: schedule.startTime,
        endTime: schedule.endTime,
        courseCode: section.course?.code ?? '',
        courseName: getLocalizedName(
          locale,
          section.course,
          section.course?.name ?? '',
        ),
        building: schedule.classroom?.building ?? '',
        roomNumber: schedule.classroom?.roomNumber ?? '',
      });
    });
  });
  upcomingMeetings.sort(
    (left, right) =>
      left.daysUntil - right.daysUntil ||
      left.startTime.localeCompare(right.startTime),
  );
  const nextMeetings = upcomingMeetings.slice(0, 3);

  const dayBadgeLabels: Record<number, string> =
    locale === 'vi'
      ? { 1: 'T2', 2: 'T3', 3: 'T4', 4: 'T5', 5: 'T6', 6: 'T7', 7: 'CN' }
      : { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' };

  const statChips = [
    {
      label: copy.stats.courses,
      // Count live seat requests (including PENDING), but not dropped rows.
      value: formatNumber(activeCourses.length),
      icon: BookOpen,
      tone: metricToneClass('info'),
      href: '/dashboard/enrollments',
      tooltip: locale === 'vi' ? 'Xem danh sách học phần' : 'View enrolled courses',
      wraps: false,
    },
    {
      label: copy.stats.active,
      // Pending seats count toward the term total, not confirmed enrollment.
      value: formatNumber(dashboardMetrics.enrolledCourseCount),
      icon: GraduationCap,
      tone: metricToneClass('success'),
      href: '/dashboard/enrollments',
      tooltip: locale === 'vi' ? 'Xem các môn đang học' : 'View active courses',
      wraps: false,
    },
    {
      label: copy.stats.pending,
      value: formatNumber(pendingCourses.length),
      icon: ClipboardList,
      tone: metricToneClass('warning'),
      href: '/dashboard/register',
      tooltip: locale === 'vi' ? 'Đến trang đăng ký học phần' : 'Go to course registration',
      wraps: false,
    },
    {
      label: copy.stats.term,
      // Semester names are authored data and wrap in the four-card grid.
      value: currentSemesterName,
      icon: Calendar,
      tone: metricToneClass('neutral'),
      href: '/dashboard/schedule',
      tooltip: locale === 'vi' ? 'Xem thời khóa biểu học kỳ' : 'View semester schedule',
      wraps: true,
    },
  ];

  const enrollmentStatusLabel = (status: string) =>
    messages.common.statuses[status as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  if (authLoading) {
    return <LoadingState label={messages.studentDashboard.errors.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.studentDashboard.eyebrow}</SectionEyebrow>}
        title={messages.studentDashboard.title.replace('{name}', user?.firstName ?? 'student')}
        description={copy.description}
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex rounded-md border border-border/70 bg-card px-3 py-2 text-xs font-medium text-muted-foreground">
              {formatDate(new Date(), {
                weekday: 'long',
                month: 'long',
                day: 'numeric',
                year: 'numeric',
              })}
            </div>
            <LinkButton href="/dashboard/register" size="sm">
              {messages.studentDashboard.quickActions[0][0]}
            </LinkButton>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={messages.studentDashboard.errors.unavailableTitle}
          description={error}
          onRetry={refetchData}
        />
      ) : isLoading ? (
        <LoadingState label={messages.studentDashboard.errors.loading} />
      ) : (
        <>
          <StudentDashboardMetricCards
            chips={statChips}
          />

          <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
            <WorkspacePanel
              title={copy.upcomingTitle}
              variant="muted"
              contentClassName="space-y-2.5"
            >
              {nextMeetings.length === 0 ? (
                <div className="flex flex-col items-center gap-2.5 py-6 text-center">
                  <Calendar className="h-8 w-8 text-muted-foreground" />
                  <p className="text-sm text-muted-foreground">{copy.noMeetings}</p>
                  <LinkButton href="/dashboard/schedule" variant="outline" size="sm">
                    {copy.openSchedule}
                  </LinkButton>
                </div>
              ) : (
                nextMeetings.map((meeting) => {
                  const room =
                    meeting.building && meeting.roomNumber
                      ? (locale === 'vi'
                          ? `Phòng ${meeting.building}-${meeting.roomNumber}`
                          : `Room ${meeting.building}-${meeting.roomNumber}`)
                      : meeting.roomNumber
                        ? (locale === 'vi'
                            ? `Phòng ${meeting.roomNumber}`
                            : `Room ${meeting.roomNumber}`)
                        : meeting.building;
                  const shift = getMeetingShift(meeting.startTime, locale);

                  const badgeText =
                    meeting.daysUntil === 0
                      ? copy.today
                      : meeting.daysUntil === 1
                        ? locale === 'vi'
                          ? 'Ngày mai'
                          : 'Tomorrow'
                        : undefined;

                  return (
                    <LocalizedLink
                      key={meeting.id}
                      href="/dashboard/schedule"
                      className="group flex items-center gap-3 rounded-lg border border-border/70 bg-card p-3 transition-all hover:border-primary/40 hover:shadow-xs"
                    >
                      <span
                        className={cn(
                          'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-xs font-bold transition-colors',
                          meeting.daysUntil === 0
                            ? metricToneClass('info')
                            : 'bg-secondary/60 text-foreground group-hover:bg-primary/10 group-hover:text-primary',
                        )}
                      >
                        {dayBadgeLabels[meeting.dayOfWeek]}
                      </span>
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-sm font-semibold text-foreground group-hover:text-primary">
                            {meeting.courseCode} — {meeting.courseName}
                          </span>
                          {badgeText ? (
                            <span
                              className={cn(
                                'shrink-0 rounded-md px-2 py-0.5 text-[11px] font-semibold',
                                meeting.daysUntil === 0
                                  ? 'bg-primary/15 text-primary'
                                  : 'bg-secondary text-muted-foreground',
                              )}
                            >
                              {badgeText}
                            </span>
                          ) : null}
                        </div>
                        <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                          <span className={cn('rounded-md border px-2 py-0.5 text-[11px] font-semibold', shift.tone)}>
                            {shift.label}
                          </span>
                          <span className="inline-flex items-center gap-1 tabular-nums">
                            <Clock className="h-3 w-3" />
                            {meeting.startTime}–{meeting.endTime}
                          </span>
                          {room ? (
                            <span className="inline-flex items-center gap-1 truncate">
                              <MapPin className="h-3 w-3" />
                              {room}
                            </span>
                          ) : null}
                        </div>
                      </div>
                      <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground/50 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                    </LocalizedLink>
                  );
                })
              )}
            </WorkspacePanel>

            <StudentDashboardCreditProgress
              metrics={dashboardMetrics}
              formatNumber={formatNumber}
              copy={{
                title: copy.progressTitle,
                creditLabel: copy.creditLabel,
                creditsEarnedOnly: copy.creditsEarnedOnly,
                progressStatus: copy.progressStatus,
                creditsAvailable: copy.creditsAvailable,
                creditsCapped: copy.creditsCapped,
                pendingBadge: copy.pendingBadge,
              }}
            />
          </div>

          <WorkspacePanel
            title={
              <span className="flex items-center gap-2.5">
                <span>{messages.studentDashboard.panels.currentCourses.title}</span>
                <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
                  {activeCourses.length} {copy.coursesUnit}
                </span>
              </span>
            }
            variant="muted"
            footer={
              activeCourses.length > 0 ? (
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/60 pt-3">
                  <LinkButton href="/dashboard/schedule" variant="outline" size="sm">
                    <Calendar className="mr-1.5 h-4 w-4" />
                    {messages.dashboardShell.menu.schedule}
                  </LinkButton>
                  <LinkButton href="/dashboard/enrollments" variant="ghost" size="sm">
                    {messages.dashboardShell.menu.myCourses}
                    <ArrowRight className="ml-1.5 h-4 w-4" />
                  </LinkButton>
                </div>
              ) : null
            }
          >
            {highlightedCourses.length === 0 ? (
              <EmptyState
                icon={BookMarked}
                title={messages.studentDashboard.panels.currentCourses.emptyTitle}
                description={messages.studentDashboard.panels.currentCourses.emptyDescription}
                action={
                  <LinkButton href="/dashboard/register">
                    {messages.common.actions.browseSections}
                  </LinkButton>
                }
                className="min-h-[200px] border-none bg-transparent px-0 py-0"
              />
            ) : (
              <div className="space-y-3">
                {highlightedCourses.map((enrollment) => {
                  const localizedCourseName = getLocalizedName(
                    locale,
                    enrollment.section?.course,
                    enrollment.section?.course?.name ?? '',
                  );

                  return (
                    <LocalizedLink
                      key={enrollment.id}
                      href="/dashboard/enrollments"
                      className="group flex items-center gap-3.5 rounded-lg border border-border/70 bg-card p-3.5 transition-all hover:border-primary/40 hover:bg-secondary/20 hover:shadow-xs"
                    >
                      <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary transition-transform group-hover:scale-105">
                        <BookMarked className="h-5 w-5" />
                      </div>
                      <div className="min-w-0 flex-1 truncate font-semibold text-foreground transition-colors group-hover:text-primary">
                        {enrollment.section?.course?.code} - {localizedCourseName}
                      </div>
                      <span
                        className="shrink-0 rounded-md bg-secondary px-2.5 py-1 text-xs font-medium text-foreground"
                        aria-label={enrollmentStatusLabel(enrollment.status)}
                      >
                        {enrollmentStatusLabel(enrollment.status)}
                      </span>
                      <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground/40 transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />
                    </LocalizedLink>
                  );
                })}
              </div>
            )}
          </WorkspacePanel>

          <WorkspacePanel
            title={copy.quickAccessTitle}
            variant="muted"
            contentClassName="grid grid-cols-2 gap-3 sm:grid-cols-3"
          >
            {quickAccess.map((tile) => (
              <LocalizedLink
                key={tile.href}
                href={tile.href}
                className="group flex flex-col items-center gap-2 rounded-lg border border-border/70 bg-card p-4 text-center transition-all duration-150 hover:border-primary/40 hover:bg-secondary/25 hover:shadow-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <span
                  className={cn(
                    'flex h-10 w-10 items-center justify-center rounded-lg transition-transform duration-150 group-hover:scale-105',
                    tile.tone,
                  )}
                >
                  <tile.icon className="h-5 w-5" />
                </span>
                <span className="text-sm font-semibold leading-5 text-foreground transition-colors group-hover:text-primary">
                  {messages.dashboardShell.menu[tile.labelKey]}
                </span>
              </LocalizedLink>
            ))}
          </WorkspacePanel>
        </>
      )}
    </div>
  );
}
