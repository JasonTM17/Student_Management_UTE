'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bell, BookMarked, BookOpen, Calendar, ClipboardList, FileText, GraduationCap, TrendingUp } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { metricToneClass } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import {
  WorkspaceActionTile,
  WorkspaceMetricCard,
  WorkspacePanel,
} from '@/components/dashboard/WorkspaceSurface';
import { useI18n } from '@/i18n';

interface Enrollment {
  id: string;
  status: string;
  section?: {
    course?: {
      code: string;
      name: string;
    };
    sectionNumber: string;
  };
}

interface Semester {
  id: string;
  name: string;
  status: string;
}

const quickActions = [
  {
    href: '/dashboard/register',
    icon: ClipboardList,
    tone: metricToneClass('info'),
  },
  {
    href: '/dashboard/schedule',
    icon: Calendar,
    tone: metricToneClass('success'),
  },
  {
    href: '/dashboard/grades',
    icon: TrendingUp,
    tone: metricToneClass('neutral'),
  },
];

const portalLinks = [
  {
    href: '/dashboard/enrollments',
    icon: BookOpen,
    tone: metricToneClass('info'),
  },
  {
    href: '/dashboard/transcript',
    icon: FileText,
    tone: metricToneClass('neutral'),
  },
  {
    href: '/dashboard/announcements',
    icon: Bell,
    tone: metricToneClass('warning'),
  },
];

export default function DashboardPage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatDate, formatNumber, messages } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [currentSemester, setCurrentSemester] = useState<string>('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const semestersRes = await semestersApi.getAll();
      setSemesters(semestersRes.data);

      const preferredSemesterId = pickPreferredSemesterId(semestersRes.data);
      if (preferredSemesterId) {
        setCurrentSemester(preferredSemesterId);
        const enrollmentData = await enrollmentsApi.getMyEnrollments(preferredSemesterId);
        setEnrollments(enrollmentData);
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

  const confirmedCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'CONFIRMED',
  );
  const pendingCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'PENDING',
  );
  const highlightedCourses = confirmedCourses.slice(0, 3);

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
        description={messages.studentDashboard.description.replace('{semester}', currentSemesterName)}
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
          <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
            <WorkspaceMetricCard
              label={messages.studentDashboard.metrics.coursesInScope}
              value={formatNumber(enrollments.length)}
              icon={<BookOpen className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[0]}
              toneClassName={metricToneClass('info')}
            />
            <WorkspaceMetricCard
              label={messages.studentDashboard.metrics.confirmedEnrollments}
              value={formatNumber(confirmedCourses.length)}
              icon={<GraduationCap className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[1]}
              toneClassName={metricToneClass('success')}
            />
            <WorkspaceMetricCard
              label={messages.studentDashboard.metrics.pendingDecisions}
              value={formatNumber(pendingCourses.length)}
              icon={<ClipboardList className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[2]}
              toneClassName={metricToneClass('warning')}
            />
            <WorkspaceMetricCard
              label={messages.studentDashboard.metrics.currentSemester}
              value={currentSemesterName}
              icon={<Calendar className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[3]}
              valueClassName="text-xl sm:text-2xl"
              toneClassName={metricToneClass('neutral')}
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
            <WorkspacePanel
              title={messages.studentDashboard.panels.nextActions.title}
              description={messages.studentDashboard.panels.nextActions.description}
              variant="muted"
              contentClassName="grid gap-4 sm:grid-cols-2"
            >
                {quickActions.map((action, index) => (
                  <WorkspaceActionTile
                    key={action.href}
                    href={action.href}
                    icon={<action.icon className="h-5 w-5" />}
                    title={messages.studentDashboard.quickActions[index][0]}
                    description={messages.studentDashboard.quickActions[index][1]}
                    toneClassName={action.tone}
                    ctaLabel={messages.common.actions.openTool}
                  />
                ))}
            </WorkspacePanel>

            <WorkspacePanel
              title={messages.studentDashboard.panels.currentCourses.title}
              description={messages.studentDashboard.panels.currentCourses.description}
              variant="muted"
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
                    className="min-h-[280px] border-none bg-transparent px-0 py-0"
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
                      <div
                        key={enrollment.id}
                        className="flex items-center gap-3 rounded-md border border-border/70 bg-card px-4 py-3"
                      >
                        <div className="flex h-9 w-9 items-center justify-center rounded-md bg-secondary text-foreground">
                          <BookMarked className="h-5 w-5" />
                        </div>
                        <div className="min-w-0 flex-1">
                          <div className="truncate font-medium text-foreground">
                            {enrollment.section?.course?.code} - {localizedCourseName}
                          </div>
                          <div className="text-sm text-muted-foreground">
                            {messages.studentDashboard.panels.currentCourses.sectionLabel.replace('{section}', enrollment.section?.sectionNumber || '')}
                          </div>
                        </div>
                        <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                          {enrollment.status}
                        </span>
                      </div>
                      );
                    })}
                  </div>
                )}
            </WorkspacePanel>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
            <WorkspacePanel
              title={messages.studentDashboard.panels.referenceLinks.title}
              description={messages.studentDashboard.panels.referenceLinks.description}
              contentClassName="grid gap-4 md:grid-cols-3"
            >
                {portalLinks.map((item, index) => (
                  <WorkspaceActionTile
                    key={item.href}
                    href={item.href}
                    icon={<item.icon className="h-5 w-5" />}
                    title={messages.studentDashboard.portalLinks[index][0]}
                    description={messages.studentDashboard.portalLinks[index][1]}
                    toneClassName={item.tone}
                    ctaLabel={messages.common.actions.openView}
                    className="bg-secondary/35 hover:bg-secondary/60"
                  />
                ))}
            </WorkspacePanel>

            <WorkspacePanel
              title={messages.studentDashboard.panels.currentStatus.title}
              description={messages.studentDashboard.panels.currentStatus.description}
              contentClassName="space-y-4"
            >
                <div className="border-l-2 border-primary bg-secondary/30 px-4 py-3">
                  <div className="text-sm font-semibold text-foreground">
                    {messages.studentDashboard.panels.currentStatus.semesterSelectionTitle}
                  </div>
                  <p className="mt-1 text-sm leading-6 text-muted-foreground">
                    {currentSemester
                      ? messages.studentDashboard.panels.currentStatus.semesterSelectionActive.replace('{semester}', currentSemesterName)
                      : messages.studentDashboard.panels.currentStatus.semesterSelectionEmpty}
                  </p>
                </div>
                <div className="border-l-2 border-[hsl(var(--accent-warm))] bg-secondary/30 px-4 py-3">
                  <div className="text-sm font-semibold text-foreground">
                    {messages.studentDashboard.panels.currentStatus.enrollmentHealthTitle}
                  </div>
                  <p className="mt-1 text-sm leading-6 text-muted-foreground">
                    {pendingCourses.length > 0
                      ? messages.studentDashboard.panels.currentStatus.enrollmentHealthPending.replace('{count}', formatNumber(pendingCourses.length))
                      : messages.studentDashboard.panels.currentStatus.enrollmentHealthClear}
                  </p>
                </div>
                <LinkButton href="/dashboard/profile" variant="outline" className="w-full">
                  {messages.common.actions.reviewProfileSettings}
                </LinkButton>
            </WorkspacePanel>
          </div>
        </>
      )}
    </div>
  );
}
