'use client';

import { useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowRight, Bell, BookMarked, BookOpen, BrainCircuit, Building2, DoorOpen, FileText, GraduationCap, School, TrendingUp, UserPlus, Users } from 'lucide-react';

import { useAuth } from '@/context/AuthContext';
import { coursesApi, enrollmentsApi, lecturersApi, usersApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import { AdminMetricCard } from '@/components/admin/AdminSurface';
import { LocalizedLink } from '@/components/LocalizedLink';
import { LinkButton } from '@/components/ui/link-button';
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
  {
    href: '/admin/assistant-knowledge',
    icon: BrainCircuit,
    label: 'AI assistant knowledge',
    description: 'Review public academic sources and the curated retrieval boundary.',
    tone: 'bg-sky-500/12 text-sky-600 dark:text-sky-400',
  },
];

export default function AdminDashboardPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatNumber, href, locale, messages } = useI18n();
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
          <LinkButton href="/admin/users" variant="outline">
            <UserPlus className="mr-2 h-4 w-4" />
            {messages.common.actions.addUser}
          </LinkButton>
          <LinkButton href="/admin/courses">
            {messages.dashboardShell.menu.myCourses}
          </LinkButton>
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
        <div className="space-y-6">
          <div className="grid grid-cols-2 gap-3 xl:grid-cols-4">
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

          <section aria-labelledby="admin-management-title" className="min-w-0">
            <div className="mb-3 border-b border-border pb-3">
              <h2 id="admin-management-title" className="text-lg font-semibold text-foreground">
                {messages.admin.managementConsoleTitle}
              </h2>
              <p className="mt-1 max-w-3xl text-sm leading-5 text-muted-foreground">
                {messages.admin.managementConsoleDescription}
              </p>
            </div>
            <div className="grid overflow-hidden border border-border/80 bg-card md:grid-cols-2">
              {menuItems.map((item, index) => {
                const localizedItem = messages.admin.menuItems[index] ??
                  (locale === 'vi'
                    ? ['Kiến thức trợ lý AI', 'Quản lý nguồn học thuật công khai và ranh giới truy xuất đã kiểm duyệt.']
                    : [item.label, item.description]);

                return (
                  <LocalizedLink
                    key={item.href}
                    href={item.href}
                    className="group flex min-h-[100px] min-w-0 items-start gap-3 border-b border-border/70 p-4 transition-colors hover:bg-secondary/35 focus-visible:z-10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring md:border-r md:[&:nth-child(2n)]:border-r-0"
                  >
                    <div
                      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-md ${item.tone}`}
                    >
                      <item.icon className="h-4 w-4" aria-hidden="true" />
                    </div>
                    <div className="min-w-0 flex-1 space-y-1">
                        <h3 className="text-base font-semibold text-foreground transition-colors group-hover:text-primary">
                          {localizedItem[0]}
                        </h3>
                        <p className="text-sm leading-5 text-muted-foreground">
                          {localizedItem[1]}
                        </p>
                    </div>
                    <ArrowRight className="mt-2 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                  </LocalizedLink>
                );
              })}
            </div>
          </section>
        </div>
      )}
    </AdminFrame>
  );
}
