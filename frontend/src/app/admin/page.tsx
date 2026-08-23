'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowRight, Bell, BookMarked, BookOpen, Building2, DoorOpen, FileText, GraduationCap, School, TrendingUp, UserPlus, Users } from 'lucide-react';

import { useAuth } from '@/context/AuthContext';
import { coursesApi, enrollmentsApi, lecturersApi, usersApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminMetricCard, AdminTableCard } from '@/components/admin/AdminSurface';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { ErrorState, LoadingState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

interface QuickStats {
  totalStudents: number;
  totalLecturers: number;
  totalCourses: number;
  totalEnrollments: number;
}

const menuItems = [
  {
    href: '/admin/thesis',
    icon: GraduationCap,
    label: 'Thesis management',
    description: 'Create registration rounds and monitor topics, groups, and progress.',
    tone: 'bg-indigo-500/12 text-indigo-600 dark:text-indigo-400',
  },
  {
    href: '/admin/users',
    icon: Users,
    label: 'User management',
    description: 'Review campus accounts, statuses, and role assignments.',
    tone: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
  },
  {
    href: '/admin/lecturers',
    icon: School,
    label: 'Lecturers',
    description: 'Manage lecturer records and academic ownership data.',
    tone: 'bg-violet-500/12 text-violet-600 dark:text-violet-400',
  },
  {
    href: '/admin/courses',
    icon: BookOpen,
    label: 'Courses',
    description: 'Maintain catalog structure, codes, and course metadata.',
    tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400',
  },
  {
    href: '/admin/sections',
    icon: BookMarked,
    label: 'Sections',
    description: 'Watch capacity, section ownership, and classroom attachment.',
    tone: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
  },
  {
    href: '/admin/enrollments',
    icon: FileText,
    label: 'Enrollments',
    description: 'Inspect registration outcomes and enrollment-level actions.',
    tone: 'bg-cyan-500/12 text-cyan-600 dark:text-cyan-400',
  },
  {
    href: '/admin/semesters',
    icon: GraduationCap,
    label: 'Semesters',
    description: 'Control the academic timeline and current registration window.',
    tone: 'bg-pink-500/12 text-pink-600 dark:text-pink-400',
  },
  {
    href: '/admin/departments',
    icon: Building2,
    label: 'Departments',
    description: 'Manage departmental structure and faculty mappings.',
    tone: 'bg-teal-500/12 text-teal-600 dark:text-teal-400',
  },
  {
    href: '/admin/classrooms',
    icon: DoorOpen,
    label: 'Classrooms',
    description: 'Track rooms, buildings, and capacity readiness.',
    tone: 'bg-orange-500/12 text-orange-600 dark:text-orange-400',
  },
  {
    href: '/admin/announcements',
    icon: Bell,
    label: 'Announcements',
    description: 'Publish updates that flow out to the rest of the campus.',
    tone: 'bg-rose-500/12 text-rose-600 dark:text-rose-400',
  },
];

export default function AdminDashboardPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatNumber, href, messages } = useI18n();
  const router = useRouter();
  const [stats, setStats] = useState<QuickStats>({
    totalStudents: 0,
    totalLecturers: 0,
    totalCourses: 0,
    totalEnrollments: 0,
  });
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchStats = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const [users, lecturers, courses, enrollments] = await Promise.all([
        usersApi.getAll({ limit: 1 }),
        lecturersApi.getAll({ limit: 1 }),
        coursesApi.getAll({ limit: 1 }),
        enrollmentsApi.getAll({ limit: 1 }),
      ]);
      setStats({
        totalStudents: users.meta?.total ?? users.data.length,
        totalLecturers: lecturers.meta?.total ?? lecturers.data.length,
        totalCourses: courses.meta?.total ?? courses.data.length,
        totalEnrollments: enrollments.meta?.total ?? enrollments.data.length,
      });
    } catch {
      setError(messages.admin.unavailableDescription);
    } finally {
      setIsLoading(false);
    }
  }, [messages.admin.unavailableDescription]);

  useEffect(() => {
    if (!canAccess) {
      return;
    }

    void fetchStats();
  }, [canAccess, fetchStats]);

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={messages.admin.loading} className="m-8" />;
  }

  const statCards = [
    {
      label: messages.admin.stats[0],
      value: stats.totalStudents,
      icon: Users,
      detail: messages.admin.statDetails[0],
      tone: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
    },
    {
      label: messages.admin.stats[1],
      value: stats.totalLecturers,
      icon: School,
      detail: messages.admin.statDetails[1],
      tone: 'bg-violet-500/12 text-violet-600 dark:text-violet-400',
    },
    {
      label: messages.admin.stats[2],
      value: stats.totalCourses,
      icon: BookOpen,
      detail: messages.admin.statDetails[2],
      tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-400',
    },
    {
      label: messages.admin.stats[3],
      value: stats.totalEnrollments,
      icon: TrendingUp,
      detail: messages.admin.statDetails[3],
      tone: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
    },
  ];

  return (
    <AdminFrame
      title={messages.admin.title}
      description={messages.admin.description}
      actions={
        <>
          <Button asChild variant="outline">
            <LocalizedLink href="/admin/users">
              <UserPlus className="mr-2 h-4 w-4" />
              {messages.common.actions.addUser}
            </LocalizedLink>
          </Button>
          <Button asChild>
            <LocalizedLink href="/admin/courses">
              {messages.dashboardShell.menu.myCourses}
            </LocalizedLink>
          </Button>
        </>
      }
    >
      {error ? (
        <ErrorState
          title={messages.admin.unavailableTitle}
          description={error || messages.admin.unavailableDescription}
          onRetry={() => void fetchStats()}
        />
      ) : isLoading ? (
        <LoadingState label={messages.admin.loading} />
      ) : (
        <div className="space-y-8">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {statCards.map((stat) => (
              <AdminMetricCard
                key={stat.label}
                label={stat.label}
                value={formatNumber(stat.value)}
                icon={<stat.icon className="h-5 w-5" />}
                detail={stat.detail}
                toneClassName={stat.tone}
              />
            ))}
          </div>

              <AdminTableCard
            title={messages.admin.managementConsoleTitle}
            description={messages.admin.managementConsoleDescription}
            contentClassName="space-y-0"
          >
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {menuItems.map((item, index) => {
                const localizedItem = messages.admin.menuItems[index] ?? [
                  item.label,
                  item.description,
                ];

                return (
                  <LocalizedLink
                    key={item.href}
                    href={item.href}
                    className="group rounded-lg border border-border/70 bg-card px-5 py-5 transition-colors hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <div className="flex h-full flex-col gap-4">
                      <div
                        className={`flex h-11 w-11 items-center justify-center rounded-lg ${item.tone}`}
                      >
                        <item.icon className="h-5 w-5" />
                      </div>
                      <div className="space-y-3">
                        <h3 className="text-lg font-semibold text-foreground transition-colors group-hover:text-primary">
                          {localizedItem[0]}
                        </h3>
                        <p className="text-sm leading-6 text-muted-foreground">
                          {localizedItem[1]}
                        </p>
                      </div>
                      <div className="mt-auto flex items-center gap-2 text-sm font-medium text-primary">
                        <span>{messages.common.actions.openWorkspace}</span>
                        <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
                      </div>
                    </div>
                  </LocalizedLink>
                );
              })}
            </div>
          </AdminTableCard>
        </div>
      )}
    </AdminFrame>
  );
}
