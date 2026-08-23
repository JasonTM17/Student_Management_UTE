'use client';

import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { Bell, Calendar, ChevronLeft, ChevronRight, ClipboardList, FileText, LayoutDashboard, LogOut, Menu, School, Settings, User, X, BookOpen, ScrollText } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { LanguageToggle } from '@/components/LanguageToggle';
import { LocalizedLink } from '@/components/LocalizedLink';
import { ThemeToggle } from '@/components/ThemeToggle';
import { BrandMark } from '@/components/BrandMark';
import { Button } from '@/components/ui/button';
import { ErrorState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import { notificationsApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import { stripLocaleFromPathname } from '@/i18n/paths';

const studentMenuItems = [
  { href: '/dashboard', icon: LayoutDashboard, labelKey: 'dashboard', sectionKey: 'overview' as const },
  { href: '/dashboard/register', icon: ClipboardList, labelKey: 'courseRegistration', sectionKey: 'academics' as const },
  { href: '/dashboard/enrollments', icon: BookOpen, labelKey: 'myCourses', sectionKey: 'academics' as const },
  { href: '/dashboard/schedule', icon: Calendar, labelKey: 'schedule', sectionKey: 'academics' as const },
  { href: '/dashboard/grades', icon: FileText, labelKey: 'grades', sectionKey: 'academics' as const },
  { href: '/dashboard/transcript', icon: School, labelKey: 'transcript', sectionKey: 'academics' as const },
  { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis', sectionKey: 'academics' as const },
  { href: '/dashboard/announcements', icon: Bell, labelKey: 'announcements', sectionKey: 'communication' as const },
  { href: '/dashboard/notifications', icon: Bell, labelKey: 'notifications', sectionKey: 'communication' as const },
];

const lecturerMenuItems = [
  { href: '/dashboard/lecturer', icon: LayoutDashboard, labelKey: 'dashboard', sectionKey: 'overview' as const },
  { href: '/dashboard/lecturer/schedule', icon: Calendar, labelKey: 'teachingSchedule', sectionKey: 'academics' as const },
  { href: '/dashboard/lecturer/grades', icon: FileText, labelKey: 'gradeManagement', sectionKey: 'academics' as const },
  { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis', sectionKey: 'academics' as const },
  { href: '/dashboard/lecturer/announcements', icon: Bell, labelKey: 'announcements', sectionKey: 'communication' as const },
  { href: '/dashboard/notifications', icon: Bell, labelKey: 'notifications', sectionKey: 'communication' as const },
];

const menuSections = [
  { key: 'overview' as const },
  { key: 'academics' as const },
  { key: 'communication' as const },
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
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);
  const menuLabels = messages.dashboardShell.menu;
  const menuItems = isAdmin
    ? []
    : (isLecturer ? lecturerMenuItems : studentMenuItems).map((item) => ({
        ...item,
        label: menuLabels[item.labelKey as keyof typeof menuLabels],
      }));
  const mobileMenuItems = isLecturer
    ? [
        { href: '/dashboard/lecturer', icon: LayoutDashboard, label: menuLabels.dashboard },
        { href: '/dashboard/lecturer/schedule', icon: Calendar, label: menuLabels.teachingSchedule },
        { href: '/dashboard/lecturer/grades', icon: FileText, label: menuLabels.gradeManagement },
        { href: '/dashboard/notifications', icon: Bell, label: menuLabels.notifications },
        { href: '/dashboard/profile', icon: User, label: menuLabels.profile },
      ]
    : [
        { href: '/dashboard', icon: LayoutDashboard, label: menuLabels.dashboard },
        { href: '/dashboard/register', icon: ClipboardList, label: menuLabels.courseRegistration },
        { href: '/dashboard/schedule', icon: Calendar, label: menuLabels.schedule },
        { href: '/dashboard/notifications', icon: Bell, label: menuLabels.notifications },
        { href: '/dashboard/profile', icon: User, label: menuLabels.profile },
      ];

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
      '/dashboard/thesis/topics': {
        title: messages.thesis.catalogTitle,
        description: messages.thesis.catalogDescription,
      },
      '/dashboard/thesis/progress': {
        title: messages.thesis.progressTitle,
        description: messages.thesis.progressDescription,
      },
      '/dashboard/sign-out': {
        title: messages.dashboardShell.signOutPage.title,
        description: messages.dashboardShell.signOutPage.description,
      },
      '/dashboard/announcements': {
        title: messages.dashboardShell.menu.announcements,
        description: messages.dashboardShell.routeDescriptions.announcements,
      },
      '/dashboard/notifications': {
        title: messages.dashboardShell.menu.notifications,
        description: messages.dashboardShell.routeDescriptions.notifications,
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
      return;
    }
    if (isAdmin) {
      router.replace(href('/admin'));
    }
  }, [href, user, isLoading, isLoggingOut, isAdmin, router]);

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
          setNotificationsError(false);
        }
      } catch {
        if (!cancelled) {
          setNotifications([]);
          setNotificationsError(true);
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
    <div className="portal-content-canvas min-h-screen dark:bg-[hsl(var(--background))]">
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
          'portal-sidebar fixed inset-y-0 left-0 z-50 flex w-64 flex-col border-r border-white/10 shadow-xl transition-[transform,width] duration-200 lg:translate-x-0',
          sidebarCollapsed ? 'lg:w-20' : 'lg:w-64',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div
          className={cn(
            'portal-sidebar-header flex items-center justify-between border-b border-white/10 py-4',
            sidebarCollapsed ? 'px-3' : 'px-5',
          )}
        >
            <BrandMark
              href={isLecturer ? '/dashboard/lecturer' : '/dashboard'}
              compact
              className={cn(sidebarCollapsed && 'justify-center gap-0')}
              titleClassName={cn('text-white', sidebarCollapsed && 'hidden')}
              subtitle={messages.home.navSubtitle}
              subtitleClassName={cn('text-white/65', sidebarCollapsed && 'hidden')}
            />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="portal-sidebar-toggle hidden lg:inline-flex"
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
            'portal-profile mx-3 my-3 rounded-md px-3 py-3',
            sidebarCollapsed ? 'px-3 text-center' : 'px-5',
          )}
        >
          <div className={cn('flex items-center gap-3', sidebarCollapsed && 'justify-center')}>
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[hsl(var(--nav-active))] text-sm font-bold text-[hsl(var(--nav-surface))]">
              {user.firstName?.[0]}
              {user.lastName?.[0]}
            </div>
            {!sidebarCollapsed ? (
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-white">
                  {user.firstName} {user.lastName}
                </div>
                <p className="mt-0.5 truncate text-xs text-[hsl(var(--nav-muted))]">
                  {roleLabel}
                </p>
              </div>
            ) : null}
          </div>
        </div>

        <nav
          className={cn(
            'flex-1 overflow-y-auto py-2',
            sidebarCollapsed ? 'px-3' : 'px-4',
          )}
          aria-label={messages.dashboardShell.controls.workspaceNavigation}
        >
          {menuSections.map((section) => {
            const sectionItems = menuItems.filter((item) => item.sectionKey === section.key);

            return (
              <div key={section.key} className="mb-4 last:mb-0">
                {!sidebarCollapsed ? (
                  <div className="mb-1 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-[hsl(var(--nav-muted))]">
                    {messages.dashboardShell.sections[section.key]}
                  </div>
                ) : null}
                {sectionItems.map((item) => {
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
                      aria-current={isActive ? 'page' : undefined}
                      title={sidebarCollapsed ? item.label : undefined}
                      className={cn(
                        'portal-nav-item flex items-center gap-3 px-3 py-2 text-sm font-medium transition-colors',
                        sidebarCollapsed && 'justify-center px-0',
                        isActive && 'is-active',
                      )}
                    >
                      <item.icon className="h-4.5 w-4.5" />
                      {!sidebarCollapsed ? <span>{item.label}</span> : null}
                    </LocalizedLink>
                  );
                })}
              </div>
            );
          })}
        </nav>

        <div
          className={cn(
            'border-t border-white/10 py-3',
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
              'flex items-center gap-3 rounded-md px-3 py-2.5 text-sm text-[hsl(var(--nav-muted))] transition-colors hover:bg-white/10 hover:text-white',
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
              'mt-1 flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm text-red-200 transition-colors hover:bg-red-500/15',
              sidebarCollapsed && 'justify-center px-0',
            )}
          >
            <LogOut className="h-4.5 w-4.5" />
            {!sidebarCollapsed ? messages.common.actions.signOut : null}
          </LocalizedLink>
        </div>
      </aside>

      <div className={cn(sidebarCollapsed ? 'lg:pl-20' : 'lg:pl-64')}>
        <div className="portal-utility-bar hidden items-center justify-between px-4 text-[11px] font-semibold uppercase tracking-[0.14em] sm:flex lg:px-8">
          <span>{roleLabel}</span>
          <span className="opacity-80">{messages.dashboardShell.pageDefaults.title}</span>
        </div>
        <header className="portal-topbar sticky top-0 z-30 border-b border-border/80 bg-card/95 backdrop-blur">
          <div className="portal-topbar-inner flex min-h-14 items-center justify-between gap-4 px-4 py-2 sm:px-6 lg:px-8">
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
                  <p className="portal-breadcrumb hidden text-[10px] font-bold uppercase tracking-[0.16em] text-muted-foreground sm:block">
                    {messages.dashboardShell.pageDefaults.title}
                  </p>
                  <h1 className="truncate text-base font-semibold tracking-tight text-foreground">
                  {currentPage.title}
                </h1>
                <p className="hidden text-xs leading-5 text-muted-foreground sm:block">
                  {currentPage.description}
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2">
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
                    className="portal-popover absolute right-0 mt-2 w-80 rounded-md border border-border/80 bg-card shadow-xl"
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
                      ) : notificationsError ? (
                        <ErrorState
                          title={messages.dashboardShell.notifications.title}
                          description={messages.dashboardShell.notifications.empty}
                          className="border-0 bg-transparent p-0"
                        />
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
                        href="/dashboard/notifications"
                        className="text-sm font-medium text-primary hover:underline"
                        onClick={() => setNotificationsOpen(false)}
                      >
                        {messages.dashboardShell.notifications.openNotifications}
                      </LocalizedLink>
                    </div>
                  </div>
                ) : null}
              </div>

              <div className="relative" ref={profileRef}>
                <button
                  type="button"
                  onClick={() => setProfileOpen((current) => !current)}
                  className="portal-user-button flex items-center gap-3 rounded-md border border-border/70 bg-card px-3 py-2 transition-colors hover:bg-secondary/50"
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
                    className="portal-popover absolute right-0 mt-2 w-64 rounded-md border border-border/80 bg-card shadow-xl"
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

        <div className="portal-main-container mx-auto w-full max-w-[1560px] px-4 py-5 pb-24 sm:px-6 md:pb-8 lg:px-8">
          <main className="min-w-0">{children}</main>
        </div>

        {!isAdmin ? (
          <nav
            className="portal-mobile-nav fixed inset-x-0 bottom-0 z-40 border-t border-border/80 bg-card/95 px-2 pt-2 shadow-[0_-8px_24px_-20px_rgba(15,23,42,0.45)] backdrop-blur md:hidden"
            aria-label={messages.dashboardShell.controls.mobileNavigation}
          >
            <div className="mx-auto grid max-w-lg grid-cols-5 gap-1">
              {mobileMenuItems.map((item) => {
                const isActive =
                  pathname === item.href ||
                  (item.href !== '/dashboard' &&
                    item.href !== '/dashboard/lecturer' &&
                    pathname.startsWith(item.href));
                const Icon = item.icon;

                return (
                  <LocalizedLink
                    key={item.href}
                    href={item.href}
                    onClick={() => setSidebarOpen(false)}
                    aria-label={item.label}
                    aria-current={isActive ? 'page' : undefined}
                    className={cn(
                      'relative flex min-h-12 flex-col items-center justify-center gap-1 rounded-md px-1 py-1 text-[11px] font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                      isActive
                        ? 'bg-primary/10 text-primary'
                        : 'text-muted-foreground hover:bg-secondary/70 hover:text-foreground',
                    )}
                  >
                    <span className="relative">
                      <Icon className="h-5 w-5" />
                      {item.href === '/dashboard/notifications' && unreadCount > 0 ? (
                        <span className="absolute -right-2 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-[hsl(var(--accent-warm))] px-1 text-[9px] font-bold text-white">
                          {unreadCount > 9 ? '9+' : unreadCount}
                        </span>
                      ) : null}
                    </span>
                    <span className="whitespace-nowrap text-center text-[10px] leading-tight">
                      {item.label}
                    </span>
                  </LocalizedLink>
                );
              })}
            </div>
          </nav>
        ) : null}
      </div>
    </div>
  );
}
