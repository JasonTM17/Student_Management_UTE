'use client';

import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Bell, Calendar, ChevronLeft, ChevronRight, ClipboardList, CreditCard, FileText, LayoutDashboard, LogOut, Menu, School, Settings, User, Users, X, BookOpen, DoorOpen, BarChart3, ScrollText } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { LanguageToggle } from '@/components/LanguageToggle';
import { LocalizedLink } from '@/components/LocalizedLink';
import { ThemeToggle } from '@/components/ThemeToggle';
import { BrandMark } from '@/components/BrandMark';
import { StudentContextRail } from '@/components/dashboard/StudentContextRail';
import { AssistantPanel } from '@/components/assistant/AssistantPanel';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { notificationsApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import { stripLocaleFromPathname } from '@/i18n/paths';

const studentMenuItems = [
  { href: '/dashboard', icon: LayoutDashboard, labelKey: 'dashboard' },
  { href: '/dashboard/register', icon: ClipboardList, labelKey: 'courseRegistration' },
  { href: '/dashboard/enrollments', icon: BookOpen, labelKey: 'myCourses' },
  { href: '/dashboard/schedule', icon: Calendar, labelKey: 'schedule' },
  { href: '/dashboard/grades', icon: FileText, labelKey: 'grades' },
  { href: '/dashboard/transcript', icon: School, labelKey: 'transcript' },
  { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
  { href: '/dashboard/invoices', icon: CreditCard, labelKey: 'invoices' },
  { href: '/dashboard/announcements', icon: Bell, labelKey: 'announcements' },
];

const lecturerMenuItems = [
  { href: '/dashboard/lecturer', icon: LayoutDashboard, labelKey: 'dashboard' },
  { href: '/dashboard/lecturer/schedule', icon: Calendar, labelKey: 'teachingSchedule' },
  { href: '/dashboard/lecturer/grades', icon: FileText, labelKey: 'gradeManagement' },
  { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
  { href: '/dashboard/lecturer/announcements', icon: Bell, labelKey: 'announcements' },
];

interface NotificationItem {
  id: string;
  title?: string;
  content?: string;
  isRead: boolean;
  createdAt: string;
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading, isLoggingOut, isLecturer, isAdmin } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const visiblePathname = usePathname();
  const pathname = stripLocaleFromPathname(visiblePathname).pathname;
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [studentRailOpen, setStudentRailOpen] = useState(false);
  const [studentRailCollapsed, setStudentRailCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);
  const menuLabels = messages.dashboardShell.menu;
  const showStudentRail = !isAdmin && !isLecturer;
  const menuItems = isAdmin
    ? []
    : (isLecturer ? lecturerMenuItems : studentMenuItems).map((item) => ({
        ...item,
        label: menuLabels[item.labelKey as keyof typeof menuLabels],
      }));

  const pageMetadata = useMemo<Record<string, { title: string; description: string }>>(
    () => ({
      '/dashboard': {
        title: messages.studentDashboard.eyebrow,
        description: messages.dashboardShell.routeDescriptions.dashboard,
      },
      '/dashboard/profile': {
        title: messages.profile.title,
        description: messages.dashboardShell.routeDescriptions.profile,
      },
      '/dashboard/register': {
        title: messages.dashboardShell.menu.courseRegistration,
        description: messages.dashboardShell.routeDescriptions.register,
      },
      '/dashboard/enrollments': {
        title: messages.dashboardShell.menu.myCourses,
        description: messages.dashboardShell.routeDescriptions.enrollments,
      },
      '/dashboard/schedule': {
        title: messages.dashboardShell.menu.schedule,
        description: messages.dashboardShell.routeDescriptions.schedule,
      },
      '/dashboard/grades': {
        title: messages.dashboardShell.menu.grades,
        description: messages.dashboardShell.routeDescriptions.grades,
      },
      '/dashboard/transcript': {
        title: messages.dashboardShell.menu.transcript,
        description: messages.dashboardShell.routeDescriptions.transcript,
      },
      '/dashboard/thesis': {
        title: messages.dashboardShell.menu.thesis,
        description: messages.dashboardShell.routeDescriptions.thesis,
      },
      '/dashboard/invoices': {
        title: messages.dashboardShell.menu.invoices,
        description: messages.dashboardShell.routeDescriptions.invoices,
      },
      '/dashboard/sign-out': {
        title: messages.dashboardShell.signOutPage.title,
        description: messages.dashboardShell.signOutPage.description,
      },
      '/dashboard/announcements': {
        title: messages.dashboardShell.menu.announcements,
        description: messages.dashboardShell.routeDescriptions.announcements,
      },
      '/dashboard/lecturer': {
        title: messages.lecturerDashboard.eyebrow,
        description: messages.dashboardShell.routeDescriptions.lecturer,
      },
      '/dashboard/lecturer/schedule': {
        title: messages.dashboardShell.menu.teachingSchedule,
        description: messages.dashboardShell.routeDescriptions.lecturerSchedule,
      },
      '/dashboard/lecturer/grades': {
        title: messages.dashboardShell.menu.gradeManagement,
        description: messages.dashboardShell.routeDescriptions.lecturerGrades,
      },
      '/dashboard/lecturer/announcements': {
        title: messages.dashboardShell.menu.announcements,
        description: messages.dashboardShell.routeDescriptions.lecturerAnnouncements,
      },
    }),
    [messages],
  );

  useEffect(() => {
    if (isLoading || isLoggingOut) return;
    if (!user) {
      router.replace(`${href('/login')}?reason=unauthorized`);
    }
  }, [href, user, isLoading, isLoggingOut, router]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const persisted = window.localStorage.getItem('campuscore.dashboard-sidebar');
    if (persisted === 'collapsed') {
      setSidebarCollapsed(true);
    }
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem(
      'campuscore.dashboard-sidebar',
      sidebarCollapsed ? 'collapsed' : 'expanded',
    );
  }, [sidebarCollapsed]);

  useEffect(() => {
    if (typeof window === 'undefined' || !showStudentRail) {
      return;
    }

    const persisted = window.localStorage.getItem('campuscore.student-rail');
    if (persisted === 'collapsed') {
      setStudentRailCollapsed(true);
    }
  }, [showStudentRail]);

  useEffect(() => {
    if (typeof window === 'undefined' || !showStudentRail) {
      return;
    }

    window.localStorage.setItem(
      'campuscore.student-rail',
      studentRailCollapsed ? 'collapsed' : 'expanded',
    );
  }, [showStudentRail, studentRailCollapsed]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (profileRef.current && !profileRef.current.contains(event.target as Node)) {
        setProfileOpen(false);
      }
      if (
        notificationsRef.current &&
        !notificationsRef.current.contains(event.target as Node)
      ) {
        setNotificationsOpen(false);
      }
    }

    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  useEffect(() => {
    if (!user) {
      return;
    }

    let cancelled = false;

    const loadNotifications = async () => {
      setNotificationsLoading(true);
      try {
        const response = await notificationsApi.getMy({
          limit: 5,
          isRead: false,
        });
        if (!cancelled) {
          setNotifications(response.data);
        }
      } catch {
        if (!cancelled) {
          setNotifications([]);
        }
      } finally {
        if (!cancelled) {
          setNotificationsLoading(false);
        }
      }
    };

    void loadNotifications();
    return () => {
      cancelled = true;
    };
  }, [user]);

  const currentPage = useMemo(() => {
    if (pageMetadata[pathname]) {
      return pageMetadata[pathname];
    }

    const matchingItem = [...studentMenuItems, ...lecturerMenuItems].find(
      (item) => pathname === item.href || pathname.startsWith(`${item.href}/`),
    );

    if (matchingItem) {
      return {
        title: menuLabels[matchingItem.labelKey as keyof typeof menuLabels],
        description: messages.dashboardShell.pageDefaults.description,
      };
    }

    return {
      title: messages.dashboardShell.pageDefaults.title,
      description: messages.dashboardShell.pageDefaults.fallbackDescription,
    };
  }, [menuLabels, messages, pageMetadata, pathname]);

  if (isLoading) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="h-10 w-10 animate-spin rounded-full border-2 border-primary/20 border-t-primary" />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const unreadCount = notifications.filter((notification) => !notification.isRead).length;
  const roleLabel = isAdmin
    ? messages.dashboardShell.roles.admin
    : isLecturer
      ? messages.dashboardShell.roles.lecturer
      : messages.dashboardShell.roles.student;

  return (
    <div className="min-h-screen bg-background dark:bg-[hsl(var(--background))]">
      {sidebarOpen ? (
        <button
          type="button"
          className="fixed inset-0 z-40 bg-black/50 lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-label={messages.dashboardShell.controls.closeOverlay}
        />
      ) : null}

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-72 flex-col border-r border-border/70 bg-[hsl(var(--surface-alt))] transition-[transform,width] duration-200 lg:translate-x-0',
          sidebarCollapsed ? 'lg:w-20' : 'lg:w-72',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div
          className={cn(
            'flex items-center justify-between border-b border-border/70 py-4',
            sidebarCollapsed ? 'px-3' : 'px-5',
          )}
        >
          <BrandMark
            href={isLecturer ? '/dashboard/lecturer' : '/dashboard'}
            compact
            className={cn(sidebarCollapsed && 'justify-center gap-0')}
            titleClassName={cn(sidebarCollapsed && 'hidden')}
          />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="hidden lg:inline-flex"
            onClick={() => setSidebarCollapsed((current) => !current)}
            aria-label={
              sidebarCollapsed
                ? messages.dashboardShell.controls.expandSidebar
                : messages.dashboardShell.controls.collapseSidebar
            }
            title={
              sidebarCollapsed
                ? messages.dashboardShell.controls.expandSidebar
                : messages.dashboardShell.controls.collapseSidebar
            }
            aria-expanded={!sidebarCollapsed}
          >
            {sidebarCollapsed ? (
              <ChevronRight className="h-4 w-4" />
            ) : (
              <ChevronLeft className="h-4 w-4" />
            )}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label={messages.dashboardShell.controls.closeSidebar}
          >
            <X className="h-5 w-5" />
          </Button>
        </div>

        <div
          className={cn(
            'border-b border-border/70 py-4',
            sidebarCollapsed ? 'px-3 text-center' : 'px-5',
          )}
        >
          <div className="text-sm font-semibold text-foreground">
            {sidebarCollapsed ? roleLabel.slice(0, 2).toUpperCase() : roleLabel}
          </div>
          {!sidebarCollapsed ? (
            <p className="mt-1 text-sm leading-6 text-muted-foreground">
              {messages.dashboardShell.roleDescription}
            </p>
          ) : null}
        </div>

        <nav
          className={cn(
            'flex-1 space-y-1 overflow-y-auto py-4',
            sidebarCollapsed ? 'px-3' : 'px-4',
          )}
        >
          {menuItems.map((item) => {
            const isActive =
              pathname === item.href ||
              (item.href !== '/dashboard' &&
                item.href !== '/dashboard/lecturer' &&
                pathname.startsWith(item.href));

            return (
              <LocalizedLink
                key={item.href}
                href={item.href}
                onClick={() => setSidebarOpen(false)}
                aria-label={item.label}
                title={sidebarCollapsed ? item.label : undefined}
                className={cn(
                  'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                  sidebarCollapsed && 'justify-center px-0',
                  isActive
                    ? 'bg-primary text-primary-foreground shadow-sm'
                    : 'text-muted-foreground hover:bg-secondary/70 hover:text-foreground',
                )}
              >
                <item.icon className="h-4.5 w-4.5" />
                {!sidebarCollapsed ? <span>{item.label}</span> : null}
              </LocalizedLink>
            );
          })}
        </nav>

        <div
          className={cn(
            'border-t border-border/70 py-4',
            sidebarCollapsed ? 'px-3' : 'px-4',
          )}
        >
          <LocalizedLink
            href="/dashboard/profile"
            aria-label={messages.dashboardShell.menu.profileSettings}
            title={
              sidebarCollapsed
                ? messages.dashboardShell.menu.profileSettings
                : undefined
            }
            className={cn(
              'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground',
              sidebarCollapsed && 'justify-center px-0',
            )}
          >
            <Settings className="h-4.5 w-4.5" />
            {!sidebarCollapsed ? messages.dashboardShell.menu.profileSettings : null}
          </LocalizedLink>
          <LocalizedLink
            href="/dashboard/sign-out"
            onClick={() => setSidebarOpen(false)}
            aria-label={messages.common.actions.signOut}
            title={sidebarCollapsed ? messages.common.actions.signOut : undefined}
            className={cn(
              'mt-1 flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm text-red-500 transition-colors hover:bg-red-500/10',
              sidebarCollapsed && 'justify-center px-0',
            )}
          >
            <LogOut className="h-4.5 w-4.5" />
            {!sidebarCollapsed ? messages.common.actions.signOut : null}
          </LocalizedLink>
        </div>
      </aside>

      <div className={cn(sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-72')}>
        <header className="sticky top-0 z-30 border-b border-border/70 bg-background/95 backdrop-blur">
          <div className="flex items-center justify-between gap-4 px-4 py-4 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-start gap-3">
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="lg:hidden"
                onClick={() => setSidebarOpen(true)}
                aria-label={messages.dashboardShell.controls.openSidebar}
                aria-expanded={sidebarOpen}
              >
                <Menu className="h-5 w-5" />
              </Button>
              <div className="min-w-0">
                <h1 className="truncate text-xl font-semibold tracking-tight text-foreground">
                  {currentPage.title}
                </h1>
                <p className="hidden text-sm leading-6 text-muted-foreground sm:block">
                  {currentPage.description}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {showStudentRail ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="xl:hidden"
                  onClick={() => setStudentRailOpen(true)}
                  aria-label={messages.dashboardShell.controls.openStudentRail}
                  aria-expanded={studentRailOpen}
                >
                  <DoorOpen className="h-5 w-5" />
                </Button>
              ) : null}
              <LanguageToggle />
              <ThemeToggle />

              <div className="relative" ref={notificationsRef}>
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => setNotificationsOpen((current) => !current)}
                  aria-label={messages.dashboardShell.controls.toggleNotifications}
                  aria-expanded={notificationsOpen}
                  aria-controls="dashboard-notifications-panel"
                >
                  <Bell className="h-5 w-5" />
                  {unreadCount > 0 ? (
                    <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-[hsl(var(--accent-warm))]" />
                  ) : null}
                </Button>

                {notificationsOpen ? (
                  <div
                    id="dashboard-notifications-panel"
                    className="absolute right-0 mt-2 w-80 rounded-lg border border-border/80 bg-card shadow-2xl"
                  >
                    <div className="border-b border-border/70 px-4 py-3">
                      <h3 className="text-sm font-semibold text-foreground">
                        {messages.dashboardShell.notifications.title}
                      </h3>
                    </div>
                    <div className="max-h-72 overflow-y-auto px-4 py-3">
                      {notificationsLoading ? (
                        <div className="py-6 text-sm text-muted-foreground">
                          {messages.dashboardShell.notifications.loading}
                        </div>
                      ) : notifications.length === 0 ? (
                        <div className="py-6 text-sm leading-6 text-muted-foreground">
                          {messages.dashboardShell.notifications.empty}
                        </div>
                      ) : (
                        <div className="space-y-3">
                          {notifications.map((notification) => (
                            <div
                              key={notification.id}
                              className="rounded-lg border border-border/60 bg-secondary/30 px-3 py-3"
                            >
                              <div className="text-sm font-medium text-foreground">
                                {notification.title || messages.dashboardShell.notifications.fallbackTitle}
                              </div>
                              <div className="mt-1 text-sm leading-6 text-muted-foreground">
                                {notification.content || messages.dashboardShell.notifications.fallbackContent}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                    <div className="border-t border-border/70 px-4 py-3">
                      <LocalizedLink
                        href={isLecturer ? '/dashboard/lecturer/announcements' : '/dashboard/announcements'}
                        className="text-sm font-medium text-primary hover:underline"
                        onClick={() => setNotificationsOpen(false)}
                      >
                        {messages.dashboardShell.notifications.openAnnouncements}
                      </LocalizedLink>
                    </div>
                  </div>
                ) : null}
              </div>

              <div className="relative" ref={profileRef}>
                <button
                  type="button"
                  onClick={() => setProfileOpen((current) => !current)}
                  className="flex items-center gap-3 rounded-lg border border-border/70 bg-card px-3 py-2 transition-colors hover:bg-secondary/50"
                  aria-label={messages.dashboardShell.controls.toggleProfile}
                  aria-expanded={profileOpen}
                  aria-controls="dashboard-profile-menu"
                  aria-haspopup="menu"
                >
                  <div className="flex h-9 w-9 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground">
                    {user.firstName?.[0]}
                    {user.lastName?.[0]}
                  </div>
                  <div className="hidden min-w-0 text-left md:block">
                    <div className="truncate text-sm font-semibold text-foreground">
                      {user.firstName} {user.lastName}
                    </div>
                    <div className="truncate text-xs text-muted-foreground">
                      {user.email}
                    </div>
                  </div>
                </button>

                {profileOpen ? (
                  <div
                    id="dashboard-profile-menu"
                    className="absolute right-0 mt-2 w-64 rounded-lg border border-border/80 bg-card shadow-2xl"
                  >
                    <div className="border-b border-border/70 px-4 py-4">
                      <p className="font-semibold text-foreground">
                        {user.firstName} {user.lastName}
                      </p>
                      <p className="mt-1 text-sm text-muted-foreground">
                        {user.email}
                      </p>
                      <div className="mt-3 inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                        {user.roles?.[0] || 'USER'}
                      </div>
                    </div>
                    <div className="px-2 py-2">
                      <LocalizedLink
                        href="/dashboard/profile"
                        className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground"
                        onClick={() => setProfileOpen(false)}
                      >
                        <User className="h-4 w-4" />
                        {messages.dashboardShell.menu.profile}
                      </LocalizedLink>
                      <LocalizedLink
                        href="/dashboard/profile"
                        className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground"
                        onClick={() => setProfileOpen(false)}
                      >
                        <Settings className="h-4 w-4" />
                        {messages.dashboardShell.menu.settings}
                      </LocalizedLink>
                      <LocalizedLink
                        href="/dashboard/sign-out"
                        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-red-500 transition-colors hover:bg-red-500/10"
                        onClick={() => setProfileOpen(false)}
                      >
                        <LogOut className="h-4 w-4" />
                        {messages.common.actions.signOut}
                      </LocalizedLink>
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </header>

        {showStudentRail && studentRailOpen ? (
          <>
            <button
              type="button"
              className="fixed inset-0 z-40 bg-black/45 xl:hidden"
              onClick={() => setStudentRailOpen(false)}
              aria-label={messages.dashboardShell.controls.closeStudentRailOverlay}
            />
            <div className="fixed inset-y-0 right-0 z-50 w-full max-w-sm p-4 xl:hidden">
              <StudentContextRail
                mobile
                currentPageTitle={currentPage.title}
                currentPageDescription={currentPage.description}
                unreadCount={unreadCount}
                collapsed={false}
                onToggleCollapsed={() => undefined}
                onCloseMobile={() => setStudentRailOpen(false)}
              />
            </div>
          </>
        ) : null}

        <div className="px-4 py-6 sm:px-6 lg:px-8">
          {showStudentRail ? (
            <div
              className={cn(
                'grid items-start gap-6',
                studentRailCollapsed
                  ? 'xl:grid-cols-[minmax(0,1fr)_5.5rem]'
                  : 'xl:grid-cols-[minmax(0,1fr)_22rem]',
              )}
            >
              <main className="min-w-0">{children}</main>
              <div className="hidden xl:block">
                <StudentContextRail
                  currentPageTitle={currentPage.title}
                  currentPageDescription={currentPage.description}
                  unreadCount={unreadCount}
                  collapsed={studentRailCollapsed}
                  onToggleCollapsed={() =>
                    setStudentRailCollapsed((current) => !current)
                  }
                />
              </div>
            </div>
          ) : (
            <main>{children}</main>
          )}
        </div>
      </div>
      <AssistantPanel />
    </div>
  );
}
