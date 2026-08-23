'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { BookMarked, BookOpen, Calendar, ClipboardList, GraduationCap, TrendingUp } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
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
    tone: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
  },
  {
    href: '/dashboard/schedule',
    icon: Calendar,
    tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400',
  },
  {
    href: '/dashboard/grades',
    icon: TrendingUp,
    tone: 'bg-violet-500/12 text-violet-600 dark:text-violet-400',
  },
];

export default function DashboardPage() {
  const { user } = useAuth();
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
    void fetchData();
  }, [fetchData]);

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

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.studentDashboard.eyebrow}</SectionEyebrow>}
        title={messages.studentDashboard.title.replace('{name}', user?.firstName ?? 'student')}
        description={messages.studentDashboard.description.replace('{semester}', currentSemesterName)}
        actions={
          <div className="inline-flex rounded-full border border-border/70 bg-secondary/35 px-3.5 py-2 text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">
            {formatDate(new Date(), {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
              year: 'numeric',
            })}
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
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <WorkspaceMetricCard
              compact
              label={messages.studentDashboard.metrics.coursesInScope}
              value={formatNumber(enrollments.length)}
              icon={<BookOpen className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[0]}
              toneClassName="bg-blue-500/12 text-blue-600 dark:text-blue-400"
            />
            <WorkspaceMetricCard
              compact
              label={messages.studentDashboard.metrics.confirmedEnrollments}
              value={formatNumber(confirmedCourses.length)}
              icon={<GraduationCap className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[1]}
              toneClassName="bg-emerald-500/12 text-emerald-600 dark:text-emerald-400"
            />
            <WorkspaceMetricCard
              compact
              label={messages.studentDashboard.metrics.pendingDecisions}
              value={formatNumber(pendingCourses.length)}
              icon={<ClipboardList className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[2]}
              toneClassName="bg-amber-500/12 text-amber-600 dark:text-amber-400"
            />
            <WorkspaceMetricCard
              compact
              label={messages.studentDashboard.metrics.currentSemester}
              value={currentSemesterName}
              icon={<Calendar className="h-5 w-5" />}
              detail={messages.studentDashboard.metrics.details[3]}
              valueClassName="text-xl sm:text-2xl"
              toneClassName="bg-violet-500/12 text-violet-600 dark:text-violet-400"
            />
          </div>

          <WorkspacePanel
            title={messages.studentDashboard.panels.currentCourses.title}
            description={messages.studentDashboard.panels.currentCourses.description}
            className="overflow-hidden"
            contentClassName="p-0"
          >
            {highlightedCourses.length === 0 ? (
              <EmptyState
                icon={BookMarked}
                title={messages.studentDashboard.panels.currentCourses.emptyTitle}
                description={messages.studentDashboard.panels.currentCourses.emptyDescription}
                action={
                  <LocalizedLink href="/dashboard/register">
                    <Button>{messages.common.actions.browseSections}</Button>
                  </LocalizedLink>
                }
                className="min-h-[220px] border-none bg-transparent px-4 py-8"
              />
            ) : (
              <>
                <div className="overflow-x-auto">
                  <table className="portal-table w-full min-w-[640px] text-sm">
                    <thead>
                      <tr>
                        <th className="px-4 py-3 text-left">{locale === 'vi' ? 'Môn học' : 'Course'}</th>
                        <th className="px-4 py-3 text-left">{locale === 'vi' ? 'Lớp' : 'Section'}</th>
                        <th className="px-4 py-3 text-left">{locale === 'vi' ? 'Học kỳ' : 'Term'}</th>
                        <th className="px-4 py-3 text-right">{locale === 'vi' ? 'Trạng thái' : 'Status'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {highlightedCourses.map((enrollment) => {
                        const localizedCourseName = getLocalizedName(
                          locale,
                          enrollment.section?.course,
                          enrollment.section?.course?.name ?? '',
                        );

                        return (
                          <tr key={enrollment.id}>
                            <td className="px-4 py-3">
                              <div className="font-semibold text-foreground">
                                {enrollment.section?.course?.code}
                              </div>
                              <div className="text-muted-foreground">{localizedCourseName}</div>
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">
                              {enrollment.section?.sectionNumber || '-'}
                            </td>
                            <td className="px-4 py-3 text-muted-foreground">
                              {currentSemesterName}
                            </td>
                            <td className="px-4 py-3 text-right">
                              <span className="portal-status-badge">{enrollment.status}</span>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <div className="flex justify-end border-t border-border/70 px-4 py-3">
                  <LocalizedLink href="/dashboard/enrollments" className="text-sm font-semibold text-primary hover:underline">
                    {locale === 'vi' ? 'Xem toàn bộ môn học' : 'View all courses'}
                  </LocalizedLink>
                </div>
              </>
            )}
          </WorkspacePanel>

          <div className="grid gap-5 xl:grid-cols-[1.25fr_0.75fr]">
            <WorkspacePanel
              title={messages.studentDashboard.panels.nextActions.title}
              description={messages.studentDashboard.panels.nextActions.description}
              variant="muted"
              contentClassName="grid gap-3 sm:grid-cols-3"
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
                  className="portal-action-tile-compact"
                />
              ))}
            </WorkspacePanel>

            <WorkspacePanel
              title={messages.studentDashboard.panels.currentStatus.title}
              description={messages.studentDashboard.panels.currentStatus.description}
              contentClassName="space-y-3"
            >
              <div className="portal-status-row">
                <span>{messages.studentDashboard.panels.currentStatus.semesterSelectionTitle}</span>
                <strong>{currentSemesterName}</strong>
              </div>
              <div className="portal-status-row">
                <span>{messages.studentDashboard.panels.currentStatus.enrollmentHealthTitle}</span>
                <strong>{formatNumber(pendingCourses.length)}</strong>
              </div>
              <LocalizedLink href="/dashboard/profile">
                <Button variant="outline" className="w-full">
                  {messages.common.actions.reviewProfileSettings}
                </Button>
              </LocalizedLink>
            </WorkspacePanel>
          </div>
        </>
      )}
    </div>
  );
}
