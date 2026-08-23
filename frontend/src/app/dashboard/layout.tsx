'use client';

import {
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import {
  Bell,
  BookOpen,
  Calendar,
  ChevronLeft,
  ChevronRight,
  ClipboardList,
  DoorOpen,
  FileText,
  LayoutDashboard,
  LogOut,
  Menu,
  School,
  ScrollText,
  Settings,
  type LucideIcon,
  User,
  X,
} from 'lucide-react';
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

type DashboardMenuLabelKey =
  | 'dashboard'
  | 'courseRegistration'
  | 'myCourses'
  | 'schedule'
  | 'grades'
  | 'transcript'
  | 'thesis'
  | 'announcements'
  | 'notifications'
  | 'teachingSchedule'
  | 'gradeManagement';

type DashboardMenuSectionKey = 'overview' | 'academic' | 'teaching' | 'campus';

interface DashboardMenuItemConfig {
  href: string;
  icon: LucideIcon;
  labelKey: DashboardMenuLabelKey;
}

interface DashboardMenuSectionConfig {
  sectionKey: DashboardMenuSectionKey;
  items: readonly DashboardMenuItemConfig[];
}

const studentMenuSections: readonly DashboardMenuSectionConfig[] = [
  {
    sectionKey: 'overview',
    items: [
      { href: '/dashboard', icon: LayoutDashboard, labelKey: 'dashboard' },
    ],
  },
  {
    sectionKey: 'academic',
    items: [
      { href: '/dashboard/register', icon: ClipboardList, labelKey: 'courseRegistration' },
      { href: '/dashboard/enrollments', icon: BookOpen, labelKey: 'myCourses' },
      { href: '/dashboard/schedule', icon: Calendar, labelKey: 'schedule' },
      { href: '/dashboard/grades', icon: FileText, labelKey: 'grades' },
      { href: '/dashboard/transcript', icon: School, labelKey: 'transcript' },
    ],
  },
  {
    sectionKey: 'campus',
    items: [
      { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
      { href: '/dashboard/announcements', icon: Bell, labelKey: 'announcements' },
      { href: '/dashboard/notifications', icon: Bell, labelKey: 'notifications' },
    ],
  },
] as const;

const lecturerMenuSections: readonly DashboardMenuSectionConfig[] = [
  {
    sectionKey: 'overview',
    items: [
      { href: '/dashboard/lecturer', icon: LayoutDashboard, labelKey: 'dashboard' },
    ],
  },
  {
    sectionKey: 'teaching',
    items: [
      { href: '/dashboard/lecturer/schedule', icon: Calendar, labelKey: 'teachingSchedule' },
      { href: '/dashboard/lecturer/grades', icon: FileText, labelKey: 'gradeManagement' },
    ],
  },
  {
    sectionKey: 'campus',
    items: [
      { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
      { href: '/dashboard/lecturer/announcements', icon: Bell, labelKey: 'announcements' },
      { href: '/dashboard/notifications', icon: Bell, labelKey: 'notifications' },
    ],
  },
] as const;

const dashboardMenuItems = [...studentMenuSections, ...lecturerMenuSections].flatMap(
  (section) => section.items,
);

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
  const [isDesktopSidebar, setIsDesktopSidebar] = useState(false);
  const [studentRailOpen, setStudentRailOpen] = useState(false);
  const [studentRailCollapsed, setStudentRailCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  const sidebarCloseRef = useRef<HTMLButtonElement>(null);
  const openSidebarButtonRef = useRef<HTMLButtonElement>(null);
  const studentRailRef = useRef<HTMLElement>(null);
  const studentRailTriggerRef = useRef<HTMLButtonElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const previousPathnameRef = useRef(pathname);
  const menuLabels = messages.dashboardShell.menu;
  const menuSectionLabels = messages.dashboardShell.menuSections;
  const showStudentRail = !isAdmin && !isLecturer;
  const closeStudentRail = () => {
    setStudentRailOpen(false);
    window.requestAnimationFrame(() => studentRailTriggerRef.current?.focus());
  };
  const menuSections = isAdmin
    ? []
    : (isLecturer ? lecturerMenuSections : studentMenuSections).map((section) => ({
        ...section,
        label: menuSectionLabels[section.sectionKey],
        items: section.items.map((item) => ({
          ...item,
          label: menuLabels[item.labelKey],
        })),
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
    const media = window.matchMedia('(min-width: 1024px)');
    const syncDesktopState = () => setIsDesktopSidebar(media.matches);

    syncDesktopState();
    media.addEventListener('change', syncDesktopState);
    return () => media.removeEventListener('change', syncDesktopState);
  }, []);

  useEffect(() => {
    setSidebarOpen(false);
    setStudentRailOpen(false);
    setProfileOpen(false);
    setNotificationsOpen(false);

    if (previousPathnameRef.current !== pathname) {
      mainRef.current?.focus({ preventScroll: true });
      previousPathnameRef.current = pathname;
    }
  }, [pathname]);

  useEffect(() => {
    if (!sidebarOpen || isDesktopSidebar) {
      return;
    }

    const frame = window.requestAnimationFrame(() => sidebarCloseRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [isDesktopSidebar, sidebarOpen]);

  useEffect(() => {
    if (!studentRailOpen || isDesktopSidebar) {
      return;
    }

    const focusableSelector = [
      'a[href]',
      'button:not([disabled])',
      'input:not([disabled])',
      'select:not([disabled])',
      'textarea:not([disabled])',
      '[tabindex]:not([tabindex="-1"])',
    ].join(',');
    const frame = window.requestAnimationFrame(() => {
      studentRailRef.current?.querySelector<HTMLElement>(focusableSelector)?.focus();
    });
    const handleTab = (event: KeyboardEvent) => {
      if (event.key !== 'Tab' || !studentRailRef.current) {
        return;
      }

      const focusable = Array.from(
        studentRailRef.current.querySelectorAll<HTMLElement>(focusableSelector),
      );
      if (focusable.length === 0) {
        event.preventDefault();
        studentRailRef.current.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === first || !studentRailRef.current.contains(active))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (active === last || !studentRailRef.current.contains(active))) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleTab);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handleTab);
    };
  }, [isDesktopSidebar, studentRailOpen]);

  useEffect(() => {
    const drawerOpen = sidebarOpen || studentRailOpen;
    const previousOverflow = document.body.style.overflow;

    if (drawerOpen) {
      document.body.style.overflow = 'hidden';
    }

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') {
        return;
      }

      if (profileOpen) {
        setProfileOpen(false);
      } else if (notificationsOpen) {
        setNotificationsOpen(false);
      } else if (studentRailOpen) {
        setStudentRailOpen(false);
        window.requestAnimationFrame(() => studentRailTriggerRef.current?.focus());
      } else if (sidebarOpen) {
        setSidebarOpen(false);
        window.requestAnimationFrame(() => openSidebarButtonRef.current?.focus());
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleEscape);
    };
  }, [notificationsOpen, profileOpen, sidebarOpen, studentRailOpen]);

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

    const matchingItem = dashboardMenuItems.find(
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
      <div
        className="portal-shell flex min-h-screen items-center justify-center px-6"
        role="status"
        aria-live="polite"
      >
        <div className="w-full max-w-sm space-y-3">
          <div className="h-4 w-2/5 animate-pulse rounded bg-primary/20" />
          <div className="h-3 w-full animate-pulse rounded bg-secondary" />
          <div className="h-3 w-4/5 animate-pulse rounded bg-secondary" />
          <p className="pt-2 text-sm text-muted-foreground">
            {messages.common.states.loadingContent}
          </p>
        </div>
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
    <div className="portal-shell">
      <a
        href="#dashboard-main-content"
        className="portal-skip-link"
        tabIndex={!isDesktopSidebar && sidebarOpen ? -1 : undefined}
      >
        {messages.dashboardShell.controls.skipToContent}
      </a>
      {sidebarOpen ? (
        <button
          type="button"
          tabIndex={-1}
          className="fixed inset-0 z-40 bg-[var(--portal-scrim)] lg:hidden"
          onClick={() => {
            setSidebarOpen(false);
            window.requestAnimationFrame(() => openSidebarButtonRef.current?.focus());
          }}
          aria-label={messages.dashboardShell.controls.closeOverlay}
        />
      ) : null}

      <aside
        id="dashboard-sidebar"
        ref={sidebarRef}
        aria-label={messages.dashboardShell.controls.sidebarNavigation}
        role={!isDesktopSidebar ? 'dialog' : undefined}
        aria-modal={!isDesktopSidebar && sidebarOpen ? true : undefined}
        aria-hidden={!isDesktopSidebar && !sidebarOpen}
        inert={!isDesktopSidebar && !sidebarOpen ? true : undefined}
        className={cn(
          'portal-sidebar fixed inset-y-0 left-0 z-50 flex w-[var(--portal-sidebar-width)] max-w-[calc(100vw-3rem)] flex-col border-r border-white/10 shadow-xl transition-[transform,width] duration-200 [transition-timing-function:var(--portal-ease)] lg:translate-x-0',
          sidebarCollapsed
            ? 'lg:w-[var(--portal-sidebar-collapsed)]'
            : 'lg:w-[var(--portal-sidebar-width)]',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div
          className={cn(
            'flex min-h-[4.25rem] items-center justify-between border-b border-white/10 py-3',
            sidebarCollapsed ? 'px-3' : 'px-5',
          )}
        >
          <BrandMark
            href={isLecturer ? '/dashboard/lecturer' : '/dashboard'}
            compact
            className={cn(sidebarCollapsed && 'justify-center gap-0')}
            markClassName="border-0 bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)] shadow-none"
            titleClassName={cn(
              'text-[var(--portal-sidebar-text)]',
              sidebarCollapsed && 'hidden',
            )}
            subtitle={messages.dashboardShell.portalTitle}
            subtitleClassName={cn(
              'text-[var(--portal-sidebar-muted)]',
              sidebarCollapsed && 'hidden',
            )}
          />
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="hidden text-[var(--portal-sidebar-muted)] hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] lg:inline-flex"
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
              <ChevronRight className="h-4 w-4" aria-hidden="true" />
            ) : (
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            )}
          </Button>
          <Button
            ref={sidebarCloseRef}
            type="button"
            variant="ghost"
            size="icon"
            className="text-[var(--portal-sidebar-text)] hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] lg:hidden"
            onClick={() => {
              setSidebarOpen(false);
              window.requestAnimationFrame(() => openSidebarButtonRef.current?.focus());
            }}
            aria-label={messages.dashboardShell.controls.closeSidebar}
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </Button>
        </div>

        <div
          className={cn(
            'border-b border-white/10 bg-[var(--portal-sidebar-strong)] py-3',
            sidebarCollapsed ? 'px-3' : 'px-5',
          )}
        >
          <div
            className={cn(
              'flex min-w-0 items-center gap-3',
              sidebarCollapsed && 'justify-center',
            )}
          >
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white/10 text-xs font-bold text-[var(--portal-sidebar-text)]">
              {user.firstName?.[0]}
              {user.lastName?.[0]}
            </div>
            {!sidebarCollapsed ? (
              <div className="min-w-0">
                <div className="truncate text-sm font-semibold text-[var(--portal-sidebar-text)]">
                  {user.firstName} {user.lastName}
                </div>
                <div className="truncate text-xs text-[var(--portal-sidebar-muted)]">
                  {user.email}
                </div>
              </div>
            ) : null}
          </div>
          {!sidebarCollapsed ? (
            <div className="mt-3 flex items-center justify-between gap-3">
              <span className="portal-menu-label">
                {messages.dashboardShell.identityLabel}
              </span>
              <span className="text-xs font-semibold text-[var(--portal-sidebar-text)]">
                {roleLabel}
              </span>
            </div>
          ) : null}
        </div>

        <nav
          className={cn(
            'flex-1 space-y-4 overflow-y-auto overscroll-contain py-3',
            sidebarCollapsed ? 'px-3' : 'px-4',
          )}
        >
          {menuSections.map((section) => (
            <div key={section.sectionKey} className="space-y-2">
              {!sidebarCollapsed ? (
                <div className="portal-menu-label px-3">{section.label}</div>
              ) : null}
              <div className="space-y-1">
                {section.items.map((item) => {
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
                        'relative flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-[var(--portal-sidebar-muted)] transition-[background-color,color] duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]',
                        sidebarCollapsed && 'justify-center px-0',
                        isActive &&
                          'bg-white/[0.12] font-semibold text-[var(--portal-sidebar-text)] before:absolute before:left-0 before:h-6 before:w-0.5 before:bg-[var(--portal-yellow)]',
                      )}
                    >
                      <item.icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                      {!sidebarCollapsed ? <span>{item.label}</span> : null}
                    </LocalizedLink>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        <div
          className={cn(
            'border-t border-white/10 py-3',
            sidebarCollapsed ? 'px-3' : 'px-4',
          )}
        >
          {!sidebarCollapsed ? (
            <div className="mb-3 flex items-center justify-between gap-2 border-b border-white/10 pb-3">
              <span className="portal-menu-label">
                {messages.dashboardShell.controls.preferences}
              </span>
              <div className="flex items-center gap-1 rounded-md bg-white/10 p-1 text-[var(--portal-sidebar-text)]">
                <LanguageToggle inverse />
                <ThemeToggle className="text-[var(--portal-sidebar-text)] hover:bg-white/10 hover:text-[var(--portal-yellow)]" />
              </div>
            </div>
          ) : null}
          <LocalizedLink
            href="/dashboard/profile"
            aria-label={messages.dashboardShell.menu.profileSettings}
            title={
              sidebarCollapsed
                ? messages.dashboardShell.menu.profileSettings
                : undefined
            }
            className={cn(
              'flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm text-[var(--portal-sidebar-muted)] transition-[background-color,color] duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]',
              sidebarCollapsed && 'justify-center px-0',
            )}
          >
            <Settings className="h-5 w-5" aria-hidden="true" />
            {!sidebarCollapsed ? messages.dashboardShell.menu.profileSettings : null}
          </LocalizedLink>
          <LocalizedLink
            href="/dashboard/sign-out"
            onClick={() => setSidebarOpen(false)}
            aria-label={messages.common.actions.signOut}
            title={sidebarCollapsed ? messages.common.actions.signOut : undefined}
            className={cn(
              'mt-1 flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm text-[var(--portal-sidebar-text)] transition-[background-color,color] duration-150 hover:bg-red-500/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]',
              sidebarCollapsed && 'justify-center px-0',
            )}
          >
            <LogOut className="h-5 w-5" aria-hidden="true" />
            {!sidebarCollapsed ? messages.common.actions.signOut : null}
          </LocalizedLink>
        </div>
      </aside>

      {showStudentRail && studentRailOpen ? (
        <>
          <button
            type="button"
            className="fixed inset-0 z-40 bg-[var(--portal-scrim)] 2xl:hidden"
            onClick={closeStudentRail}
            aria-label={messages.dashboardShell.controls.closeStudentRailOverlay}
          />
          <div className="fixed inset-y-0 right-0 z-50 w-full max-w-sm p-4 2xl:hidden">
            <StudentContextRail
              mobile
              containerRef={studentRailRef}
              currentPageTitle={currentPage.title}
              currentPageDescription={currentPage.description}
              unreadCount={unreadCount}
              collapsed={false}
              onToggleCollapsed={() => undefined}
              onCloseMobile={closeStudentRail}
            />
          </div>
        </>
      ) : null}

      <div
        inert={!isDesktopSidebar && (sidebarOpen || studentRailOpen) ? true : undefined}
        className={cn(
          'min-h-screen transition-[padding-left] duration-200 [transition-timing-function:var(--portal-ease)]',
          sidebarCollapsed
            ? 'lg:pl-[var(--portal-sidebar-collapsed)]'
            : 'lg:pl-[var(--portal-sidebar-width)]',
        )}
      >
        <header className="sticky top-0 z-30 border-b border-[var(--portal-rule)] bg-[var(--portal-surface)]/95 backdrop-blur supports-[backdrop-filter]:bg-[var(--portal-surface)]/90">
          <div className="flex min-h-[var(--portal-header-height)] items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-3">
              <Button
                ref={openSidebarButtonRef}
                type="button"
                variant="ghost"
                size="icon"
                className="lg:hidden"
                onClick={() => setSidebarOpen(true)}
                aria-label={messages.dashboardShell.controls.openSidebar}
                aria-expanded={sidebarOpen}
                aria-controls="dashboard-sidebar"
              >
                <Menu className="h-5 w-5" aria-hidden="true" />
              </Button>
              <div className="min-w-0">
                <div className="hidden text-sm font-medium text-muted-foreground sm:block">
                  {messages.dashboardShell.portalTitle}
                </div>
                <div className="truncate text-base font-semibold text-foreground sm:hidden">
                  {currentPage.title}
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {showStudentRail ? (
                <Button
                  ref={studentRailTriggerRef}
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="2xl:hidden"
                  onClick={() => setStudentRailOpen(true)}
                  aria-label={messages.dashboardShell.controls.openStudentRail}
                  aria-expanded={studentRailOpen}
                  aria-controls="student-context-rail"
                >
                  <DoorOpen className="h-5 w-5" aria-hidden="true" />
                </Button>
              ) : null}
              <div className="hidden sm:block">
                <LanguageToggle />
              </div>
              <div className="hidden sm:block">
                <ThemeToggle />
              </div>

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
                  <Bell className="h-5 w-5" aria-hidden="true" />
                  {unreadCount > 0 ? (
                    <span className="absolute right-2 top-2 h-2.5 w-2.5 rounded-full bg-[hsl(var(--accent-warm))]" />
                  ) : null}
                </Button>

                {notificationsOpen ? (
                  <div
                    id="dashboard-notifications-panel"
                    role="dialog"
                    aria-label={messages.dashboardShell.notifications.title}
                    className="absolute right-0 mt-2 w-[min(20rem,calc(100vw-2rem))] rounded-md border border-border/80 bg-card shadow-2xl"
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
                              className="rounded-md border border-border/60 bg-secondary/30 px-3 py-3"
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
                  className="flex min-h-11 items-center gap-3 rounded-md border border-border/70 bg-card px-1.5 py-1 transition-[background-color,border-color] duration-150 hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:px-3"
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
                    role="menu"
                    className="absolute right-0 mt-2 w-[min(16rem,calc(100vw-2rem))] rounded-md border border-border/80 bg-card shadow-2xl"
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
                        role="menuitem"
                        className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground"
                        onClick={() => setProfileOpen(false)}
                      >
                        <User className="h-4 w-4" aria-hidden="true" />
                        {messages.dashboardShell.menu.profile}
                      </LocalizedLink>
                      <LocalizedLink
                        href="/dashboard/profile"
                        role="menuitem"
                        className="flex items-center gap-3 rounded-lg px-3 py-2 text-sm text-muted-foreground transition-colors hover:bg-secondary/70 hover:text-foreground"
                        onClick={() => setProfileOpen(false)}
                      >
                        <Settings className="h-4 w-4" aria-hidden="true" />
                        {messages.dashboardShell.menu.settings}
                      </LocalizedLink>
                      <LocalizedLink
                        href="/dashboard/sign-out"
                        role="menuitem"
                        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-red-500 transition-colors hover:bg-red-500/10"
                        onClick={() => setProfileOpen(false)}
                      >
                        <LogOut className="h-4 w-4" aria-hidden="true" />
                        {messages.common.actions.signOut}
                      </LocalizedLink>
                    </div>
                  </div>
                ) : null}
              </div>
            </div>
          </div>
        </header>

        <div className="mx-auto w-full max-w-[1440px] px-4 py-5 sm:px-6 lg:px-8">
          {showStudentRail ? (
            <div
              className={cn(
                'grid items-start gap-6',
                studentRailCollapsed
                  ? '2xl:grid-cols-[minmax(0,1fr)_5.5rem]'
                  : '2xl:grid-cols-[minmax(0,1fr)_20rem]',
              )}
            >
              <main
                id="dashboard-main-content"
                ref={mainRef}
                tabIndex={-1}
                className="min-w-0 focus:outline-none"
              >
                {children}
              </main>
              <div className="hidden 2xl:block">
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
            <main
              id="dashboard-main-content"
              ref={mainRef}
              tabIndex={-1}
              className="min-w-0 focus:outline-none"
            >
              {children}
            </main>
          )}
        </div>
      </div>
      <AssistantPanel />
    </div>
  );
}
