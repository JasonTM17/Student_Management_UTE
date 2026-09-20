'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
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
import { isActiveEnrollment } from '@/lib/enrollment-status';
import { getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { LocalizedLink } from '@/components/LocalizedLink';
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
      tone: 'border-blue-500/25 bg-blue-500/10 text-blue-600 dark:text-blue-400',
    };
  }
  return {
    label: locale === 'vi' ? 'Ca Tối' : 'Evening',
    tone: 'border-purple-500/25 bg-purple-500/10 text-purple-600 dark:text-purple-400',
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
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [currentSemester, setCurrentSemester] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [termCreditCap, setTermCreditCap] = useState<number | null>(null);

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

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const semestersRes = await semestersApi.getAll();
      setSemesters(semestersRes.data);

      const preferredSemesterId = pickPreferredSemesterId(semestersRes.data);
      if (preferredSemesterId) {
        setCurrentSemester(preferredSemesterId);
        const [enrollmentData, summary] = await Promise.all([
          enrollmentsApi.getMyEnrollments(preferredSemesterId),
          registrationApi.summary(preferredSemesterId).catch(() => null),
        ]);
        setEnrollments(enrollmentData);
        setTermCreditCap(summary?.creditLimit ?? null);
      } else {
        setEnrollments([]);
      }
    } catch {
      setError(messages.studentDashboard.errors.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [messages.studentDashboard.errors.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void fetchData();
    }
  }, [fetchData, hasAccess]);

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
  const activeCourses = enrollments.filter((enrollment) =>
    isActiveEnrollment(enrollment.status),
  );
  const pendingCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'PENDING',
  );
  const highlightedCourses = activeCourses.slice(0, 3);

  const activeCredits = activeCourses.reduce(
    (sum, enrollment) => sum + (enrollment.section?.course?.credits ?? 0),
    0,
  );
  const creditCap = termCreditCap;
  const creditPercent = creditCap
    ? Math.min(100, Math.round((activeCredits / creditCap) * 100))
    : null;
  const creditRemaining = creditCap ? Math.max(0, creditCap - activeCredits) : null;

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

  const enrollmentStatusLabel = (status: string) =>
    messages.common.statuses[status as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  const statChips = [
    {
      label: copy.stats.courses,
      // Live registrations only: dropped/cancelled rows from add/drop
      // experiments must not inflate the term count (PENDING included —
      // activeCourses follows the seat-accounting definition).
      value: formatNumber(activeCourses.length),
      icon: BookOpen,
      tone: metricToneClass('info'),
      href: '/dashboard/enrollments',
      tooltip: locale === 'vi' ? 'Xem danh sách học phần' : 'View enrolled courses',
    },
    {
      label: copy.stats.active,
      value: formatNumber(activeCourses.length),
      icon: GraduationCap,
      tone: metricToneClass('success'),
      href: '/dashboard/enrollments',
      tooltip: locale === 'vi' ? 'Xem các môn đang học' : 'View active courses',
    },
    {
      label: copy.stats.pending,
      value: formatNumber(pendingCourses.length),
      icon: ClipboardList,
      tone: metricToneClass('warning'),
      href: '/dashboard/register',
      tooltip: locale === 'vi' ? 'Đến trang đăng ký học phần' : 'Go to course registration',
    },
    {
      label: copy.stats.term,
      value: currentSemesterName,
      icon: Calendar,
      tone: metricToneClass('neutral'),
      href: '/dashboard/schedule',
      tooltip: locale === 'vi' ? 'Xem thời khóa biểu học kỳ' : 'View semester schedule',
    },
  ];

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
          onRetry={() => void fetchData()}
        />
      ) : isLoading ? (
        <LoadingState label={messages.studentDashboard.errors.loading} />
      ) : (
        <>
          <div
            data-dashboard-metrics="student-overview"
            className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
          >
            {statChips.map((chip) => (
              <LocalizedLink
                key={chip.label}
                href={chip.href}
                title={chip.tooltip}
                className="group relative flex min-w-0 items-center justify-between gap-3 rounded-xl border border-border/70 bg-card p-3.5 shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/50 hover:bg-muted/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring cursor-pointer"
              >
                <div className="flex min-w-0 items-center gap-3">
                  <span
                    className={cn(
                      'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg transition-transform duration-200 group-hover:scale-105',
                      chip.tone,
                    )}
                  >
                    <chip.icon className="h-5 w-5" />
                  </span>
                  <div className="min-w-0">
                    <div
                      className="truncate text-xl font-bold leading-6 text-foreground group-hover:text-primary transition-colors"
                      title={chip.value}
                    >
                      {chip.value}
                    </div>
                    <div
                      className="truncate text-xs font-medium text-muted-foreground"
                      title={chip.label}
                    >
                      {chip.label}
                    </div>
                  </div>
                </div>
                <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground/40 opacity-0 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-primary group-hover:opacity-100" />
              </LocalizedLink>
            ))}
          </div>

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
                          <span className={cn('rounded-md border px-2 py-0.5 text-[10px] font-semibold', shift.tone)}>
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

            <WorkspacePanel title={copy.progressTitle} contentClassName="space-y-3.5">
              <div className="flex items-center justify-between gap-2 text-sm">
                <span className="font-medium text-foreground">{copy.creditLabel}</span>
                <span className="font-semibold tabular-nums text-foreground">
                  {formatNumber(activeCredits)}{creditCap ? ` / ${formatNumber(creditCap)}` : ''}
                </span>
              </div>
              <div className="h-2.5 w-full overflow-hidden rounded-full bg-secondary">
                <div
                  className="h-full rounded-full bg-primary transition-all duration-300"
                  style={{ width: `${creditPercent ?? 0}%` }}
                />
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground pt-0.5">
                <span>{creditPercent === null ? copy.creditsEarnedOnly : copy.progressStatus(creditPercent)}</span>
                {creditRemaining !== null ? (
                  <span className="font-medium text-foreground/80">
                    {creditRemaining > 0
                      ? copy.creditsAvailable(creditRemaining)
                      : copy.creditsCapped}
                  </span>
                ) : null}
              </div>
              {pendingCourses.length > 0 ? (
                <div className="pt-0.5">
                  <span className="inline-flex items-center gap-1.5 rounded-md border border-amber-500/20 bg-amber-500/10 px-2.5 py-1 text-xs font-semibold text-amber-600 dark:text-amber-400">
                    {copy.pendingBadge(formatNumber(pendingCourses.length))}
                  </span>
                </div>
              ) : null}
            </WorkspacePanel>
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
