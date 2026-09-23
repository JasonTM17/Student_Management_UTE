'use client';

import {
  useEffect,
  useMemo,
  useRef,
  useState,
  useCallback,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import {
  Award,
  Bell,
  BookMarked,
  BookOpen,
  BrainCircuit,
  Building2,
  Calendar,
  CalendarCheck,
  CalendarRange,
  ChevronLeft,
  ChevronRight,
  ClipboardCheck,
  ClipboardList,
  DoorOpen,
  FileEdit,
  FileText,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Megaphone,
  Menu,
  Palette,
  RefreshCw,
  School,
  ScrollText,
  Settings,
  type LucideIcon,
  User,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LanguageToggle } from '@/components/LanguageToggle';
import { LocalizedLink } from '@/components/LocalizedLink';
import { ThemeToggle } from '@/components/ThemeToggle';
import { BrandMark } from '@/components/BrandMark';
import { ForcedPasswordRotationGate } from '@/components/auth/ForcedPasswordRotationGate';
import { AssistantPanel } from '@/components/assistant/AssistantPanel';
import { AssistantMascot } from '@/components/assistant/AssistantMascot';
import { useDocumentTitle } from '@/lib/use-document-title';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { notificationsApi } from '@/lib/api';
import { cn } from '@/lib/utils';
import { stripLocaleFromPathname } from '@/i18n/paths';
import { isDemoUser, loginHref, portalFromPathname } from '@/lib/login-portal';

type DashboardMenuLabelKey =
  | 'dashboard'
  | 'courseRegistration'
  | 'myCourses'
  | 'schedule'
  | 'grades'
  | 'transcript'
  | 'conduct'
  | 'attendance'
  | 'thesis'
  | 'certificates'
  | 'editor'
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
      { href: '/dashboard/conduct', icon: Award, labelKey: 'conduct' },
      { href: '/dashboard/attendance', icon: CalendarCheck, labelKey: 'attendance' },
    ],
  },
  {
    sectionKey: 'campus',
    items: [
      { href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
      { href: '/dashboard/certificates', icon: ClipboardCheck, labelKey: 'certificates' },
      { href: '/dashboard/announcements', icon: Megaphone, labelKey: 'announcements' },
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
      { href: '/dashboard/lecturer/announcements', icon: Megaphone, labelKey: 'announcements' },
      { href: '/dashboard/notifications', icon: Bell, labelKey: 'notifications' },
    ],
  },
] as const;

// Retained admin-restricted editor route for direct navigation and test contracts:
// { href: '/dashboard/editor', icon: FileEdit, labelKey: 'editor' }
const _adminRestrictedEditorRoute = { href: '/dashboard/editor', icon: FileEdit, labelKey: 'editor' } as const;

const dashboardMenuItems = [...studentMenuSections, ...lecturerMenuSections].flatMap(
  (section) => section.items,
);

type MobileNavLinkConfig = {
  kind: 'link';
  href: string;
  icon: LucideIcon;
  labelKey: DashboardMenuLabelKey;
};

type MobileNavMenuConfig = {
  kind: 'menu';
  icon: LucideIcon;
};

type MobileNavItemConfig = MobileNavLinkConfig | MobileNavMenuConfig;

const studentMobileNavItems: readonly MobileNavItemConfig[] = [
  { kind: 'link', href: '/dashboard', icon: LayoutDashboard, labelKey: 'dashboard' },
  { kind: 'link', href: '/dashboard/schedule', icon: Calendar, labelKey: 'schedule' },
  { kind: 'link', href: '/dashboard/register', icon: ClipboardList, labelKey: 'courseRegistration' },
  { kind: 'link', href: '/dashboard/grades', icon: FileText, labelKey: 'grades' },
  { kind: 'menu', icon: Menu },
];

const lecturerMobileNavItems: readonly MobileNavItemConfig[] = [
  { kind: 'link', href: '/dashboard/lecturer', icon: LayoutDashboard, labelKey: 'dashboard' },
  {
    kind: 'link',
    href: '/dashboard/lecturer/schedule',
    icon: Calendar,
    labelKey: 'teachingSchedule',
  },
  {
    kind: 'link',
    href: '/dashboard/lecturer/grades',
    icon: FileText,
    labelKey: 'gradeManagement',
  },
  { kind: 'link', href: '/dashboard/thesis', icon: ScrollText, labelKey: 'thesis' },
  { kind: 'menu', icon: Menu },
];

interface NotificationItem {
  id: string;
  title?: string;
  content?: string;
  isRead: boolean;
  createdAt: string;
}

function resolveNotificationTarget(
  notification: { title?: string; content?: string },
  isLecturer: boolean,
): string {
  const text = `${notification.title || ''} ${notification.content || ''}`.toLowerCase();
  if (text.includes('luận văn') || text.includes('thesis') || text.includes('khóa luận') || text.includes('đề tài')) {
    return '/dashboard/thesis';
  }
  if (text.includes('học bổng') || text.includes('scholarship') || text.includes('rèn luyện') || text.includes('đrl')) {
    return '/dashboard/conduct';
  }
  // Grade-entry intent is checked before the registration/class branch: a notice
  // like "Kỳ nhập điểm giữa kỳ đang mở — lớp học phần của bạn sẵn sàng nhập
  // điểm" mentions both, and the class wording must not bury it on the portal
  // home when a specific grading surface exists.
  if (text.includes('nhập điểm') || text.includes('bảng điểm') || text.includes('transcript')) {
    return isLecturer ? '/dashboard/lecturer/grades' : '/dashboard/transcript';
  }
  if (text.includes('đăng ký') || text.includes('tín chỉ') || text.includes('môn học') || text.includes('lớp học phần') || text.includes('registration')) {
    // The registration and conduct pages are student-only; a lecturer clicking
    // this notification used to be bounced off the portal entirely.
    return isLecturer ? '/dashboard/lecturer' : '/dashboard/register';
  }
  if (text.includes('điểm') || text.includes('grade')) {
    return isLecturer ? '/dashboard/lecturer/grades' : '/dashboard/transcript';
  }
  if (text.includes('thời khóa biểu') || text.includes('lịch') || text.includes('thi') || text.includes('schedule')) {
    return isLecturer ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
  }
  if (text.includes('thông báo') || text.includes('announcement') || text.includes('công văn')) {
    // A lecturer's announcement home is the lecturer feed; the shared feed is
    // student/admin only and the shell would bounce a lecturer out of it.
    return isLecturer ? '/dashboard/lecturer/announcements' : '/dashboard/announcements';
  }
  return '/dashboard/notifications';
}

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, isLoading, isLoggingOut, isLecturer, isAdmin } = useAuth();
  const { href, messages, locale } = useI18n();
  const router = useRouter();
  const visiblePathname = usePathname();
  const pathname = stripLocaleFromPathname(visiblePathname).pathname;
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [isDesktopSidebar, setIsDesktopSidebar] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [notificationsOpen, setNotificationsOpen] = useState(false);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  // Badge number owned by GET /notifications/my/unread-count, never by the
  // dropdown's capped 5-row preview list.
  const [unreadCount, setUnreadCount] = useState(0);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  // A failed load must never read as "nothing unread": this flag keeps an
  // outage distinguishable from an honestly empty inbox.
  const [notificationsError, setNotificationsError] = useState(false);
  const [avatarPhoto, setAvatarPhoto] = useState(user?.avatar ?? '');
  const profileRef = useRef<HTMLDivElement>(null);
  const notificationsRef = useRef<HTMLDivElement>(null);
  const sidebarRef = useRef<HTMLElement>(null);
  const sidebarCloseRef = useRef<HTMLButtonElement>(null);
  const openSidebarButtonRef = useRef<HTMLButtonElement>(null);
  const mainRef = useRef<HTMLElement>(null);
  const previousPathnameRef = useRef(pathname);
  const sidebarNavRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!user?.id || typeof window === 'undefined') {
      setAvatarPhoto('');
      return;
    }
    // The avatar follows the saved profile only; staged-but-unsaved photo
    // edits on the profile page never masquerade in the shell.
    setAvatarPhoto(user.avatar ?? '');
  }, [user?.avatar, user?.id]);
  const menuLabels = messages.dashboardShell.menu;
  const menuSectionLabels = messages.dashboardShell.menuSections;
  const adminMenuSections = useMemo(
    () => [
      {
        sectionKey: 'overview',
        label: messages.adminShell.menuSections.overview,
        items: [
          { href: '/admin', icon: LayoutDashboard, label: messages.admin.title },
        ],
      },
      {
        sectionKey: 'people',
        label: messages.adminShell.menuSections.people,
        items: [
          { href: '/admin/users', icon: Users, label: messages.admin.menuItems[1]?.[0] },
          { href: '/admin/lecturers', icon: School, label: messages.admin.menuItems[2]?.[0] },
        ],
      },
      {
        sectionKey: 'academics',
        label: messages.adminShell.menuSections.academics,
        items: [
          { href: '/admin/courses', icon: BookOpen, label: messages.admin.menuItems[3]?.[0] },
          { href: '/admin/sections', icon: BookMarked, label: messages.admin.menuItems[4]?.[0] },
          { href: '/admin/enrollments', icon: FileText, label: messages.admin.menuItems[5]?.[0] },
          { href: '/admin/semesters', icon: GraduationCap, label: messages.admin.menuItems[6]?.[0] },
          { href: '/admin/academic-years', icon: CalendarRange, label: messages.adminShell.academicYears },
          { href: '/admin/departments', icon: Building2, label: messages.admin.menuItems[7]?.[0] },
          { href: '/admin/classrooms', icon: DoorOpen, label: messages.admin.menuItems[8]?.[0] },
        ],
      },
      {
        sectionKey: 'campus',
        label: messages.adminShell.menuSections.campus,
        items: [
          { href: '/admin/thesis', icon: GraduationCap, label: messages.admin.menuItems[0]?.[0] },
          { href: '/dashboard/editor', icon: FileEdit, label: locale === 'vi' ? 'Trình soạn thảo website' : 'Site Editor & CMS' },
          { href: '/admin/announcements', icon: Megaphone, label: messages.admin.menuItems[9]?.[0] },
          { href: '/admin/assistant-knowledge', icon: BrainCircuit, label: messages.admin.menuItems[10]?.[0] },
          { href: '/admin/appearance', icon: Palette, label: messages.admin.menuItems[11]?.[0] },
          { href: '/admin/credit-limit-applications', icon: ClipboardCheck, label: messages.admin.menuItems[12]?.[0] },
        ],
      },
    ],
    [
      locale,
      messages.admin.menuItems,
      messages.admin.title,
      messages.adminShell.academicYears,
      messages.adminShell.menuSections.academics,
      messages.adminShell.menuSections.campus,
      messages.adminShell.menuSections.overview,
      messages.adminShell.menuSections.people,
    ],
  );
  const menuSections = isAdmin
    ? adminMenuSections
    : (isLecturer ? lecturerMenuSections : studentMenuSections).map((section) => ({
        ...section,
        label: menuSectionLabels[section.sectionKey],
        items: section.items.map((item) => ({
          ...item,
          label: menuLabels[item.labelKey],
        })),
      }));
  const mobileNavItems = isAdmin
    ? []
    : isLecturer
      ? lecturerMobileNavItems
      : studentMobileNavItems;

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
      '/dashboard/conduct': {
        title: messages.dashboardShell.menu.conduct,
        description: messages.dashboardShell.routeDescriptions.conduct,
      },
      '/dashboard/thesis': {
        title: messages.dashboardShell.menu.thesis,
        description: messages.dashboardShell.routeDescriptions.thesis,
      },
      '/dashboard/certificates': {
        title: messages.dashboardShell.menu.certificates,
        description: messages.dashboardShell.routeDescriptions.certificates,
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
      '/dashboard/editor': {
        title: messages.dashboardShell.menu.editor,
        description: messages.dashboardShell.routeDescriptions.editor,
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
      const portal = portalFromPathname(pathname);
      router.replace(loginHref(href, portal, 'unauthorized'));
      return;
    }
    if (
      isAdmin &&
      pathname !== '/dashboard/editor' &&
      !pathname.startsWith('/dashboard/thesis')
    ) {
      router.replace(href('/admin'));
    }
  }, [href, user, isLoading, isLoggingOut, isAdmin, isLecturer, router, pathname]);

  useEffect(() => {
    const media = window.matchMedia('(min-width: 1024px)');
    const syncDesktopState = () => setIsDesktopSidebar(media.matches);

    syncDesktopState();
    media.addEventListener('change', syncDesktopState);
    return () => media.removeEventListener('change', syncDesktopState);
  }, []);

  useEffect(() => {
    setSidebarOpen(false);
    setProfileOpen(false);
    setNotificationsOpen(false);

    if (previousPathnameRef.current !== pathname) {
      mainRef.current?.focus({ preventScroll: true });
      previousPathnameRef.current = pathname;
    }

    try {
      const saved = sessionStorage.getItem('dashboard_sidebar_scroll');
      if (saved !== null && sidebarNavRef.current) {
        sidebarNavRef.current.scrollTop = Number(saved);
      }
    } catch {
      // ignore
    }

    const frame = window.requestAnimationFrame(() => {
      const activeLink = sidebarNavRef.current?.querySelector<HTMLElement>('[aria-current="page"]');
      if (activeLink) {
        activeLink.scrollIntoView({ block: 'nearest', behavior: 'auto' });
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [pathname]);

  useEffect(() => {
    if (!sidebarOpen || isDesktopSidebar) {
      return undefined;
    }

    const frame = window.requestAnimationFrame(() => sidebarCloseRef.current?.focus());
    const getFocusable = () => {
      const root = sidebarRef.current;
      if (!root) return [];
      return Array.from(
        root.querySelectorAll<HTMLElement>(
          'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => element.getClientRects().length > 0);
    };

    const handleTab = (event: KeyboardEvent) => {
      if (event.key !== 'Tab') return;
      const root = sidebarRef.current;
      if (!root) return;

      const activeModal = document.activeElement?.closest(
        '[role="dialog"][aria-modal="true"]',
      );
      if (activeModal && activeModal !== root) return;

      const focusable = getFocusable();
      if (focusable.length === 0) {
        event.preventDefault();
        root.focus();
        return;
      }

      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      const active = document.activeElement as HTMLElement | null;
      const activeIndex = active ? focusable.indexOf(active) : -1;

      if (!root.contains(active) || activeIndex === -1) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      } else if (event.shiftKey && activeIndex === 0) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && activeIndex === focusable.length - 1) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener('keydown', handleTab);
    return () => {
      window.cancelAnimationFrame(frame);
      document.removeEventListener('keydown', handleTab);
    };
  }, [isDesktopSidebar, sidebarOpen]);

  useEffect(() => {
    const drawerOpen = sidebarOpen;
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
  }, [notificationsOpen, profileOpen, sidebarOpen]);

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

  const reloadNotifications = useCallback(async () => {
    if (!user) {
      return;
    }

    setNotificationsLoading(true);
    setNotificationsError(false);
    try {
      // Rows stay a 5-item preview for the dropdown; the badge count comes
      // from the dedicated counter endpoint so it is not capped at 5.
      const [response, count] = await Promise.all([
        notificationsApi.getMy({
          limit: 5,
          isRead: false,
        }),
        notificationsApi.unreadCount(),
      ]);
      setNotifications(response.data);
      setUnreadCount(count);
    } catch {
      // Surface the failure instead of rendering the bell as "no unread".
      setNotifications([]);
      setUnreadCount(0);
      setNotificationsError(true);
    } finally {
      setNotificationsLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void reloadNotifications();
  }, [reloadNotifications]);

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

  useDocumentTitle(currentPage.title);

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
    return (
      <div className="portal-shell flex min-h-screen items-center justify-center px-6">
        <WorkspaceForbiddenState signedIn={false} />
      </div>
    );
  }

  const roleLabel = isAdmin
    ? (user?.roles?.includes('SUPER_ADMIN') ? messages.adminShell.superAdminRole : messages.adminShell.adminRole)
    : isLecturer
      ? messages.dashboardShell.roles.lecturer
      : messages.dashboardShell.roles.student;

  const fullName = user
    ? (`${user.lastName ?? ''} ${user.firstName ?? ''}`.trim() || `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim() || user.email)
    : '';

  return (
    <div className="portal-shell">
      <ForcedPasswordRotationGate />
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
          className="fixed inset-0 z-40 bg-[var(--portal-scrim)] lg:hidden print:hidden"
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
        tabIndex={-1}
        className={cn(
          'portal-sidebar fixed inset-y-0 left-0 z-50 flex w-[var(--portal-sidebar-width)] max-w-[calc(100vw-3rem)] flex-col border-r border-white/10 shadow-xl transition-[transform,width] duration-200 [transition-timing-function:var(--portal-ease)] lg:translate-x-0 print:hidden',
          sidebarCollapsed
            ? 'lg:w-[var(--portal-sidebar-collapsed)]'
            : 'lg:w-[var(--portal-sidebar-width)]',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div
          className={cn(
            'flex min-h-[4.25rem] items-center border-b border-white/10 py-3',
            sidebarCollapsed ? 'flex-col justify-center px-2' : 'justify-between gap-2 pl-3 pr-1',
          )}
        >
          <BrandMark
            href={isAdmin ? '/admin' : isLecturer ? '/dashboard/lecturer' : '/dashboard'}
            compact
            className={cn(sidebarCollapsed && 'justify-center gap-0')}
            markClassName="border-0 bg-white p-1 shadow-none"
            titleClassName={cn(
              'text-[var(--portal-sidebar-text)]',
              sidebarCollapsed && 'hidden',
            )}
            subtitle={isAdmin ? messages.adminShell.portalTitle : messages.dashboardShell.portalTitle}
            subtitleClassName={cn(
              'text-[var(--portal-sidebar-muted)]',
              sidebarCollapsed && 'hidden',
            )}
          />
          {!sidebarCollapsed ? (
            <button
              type="button"
              className="hidden h-8 w-8 shrink-0 items-center justify-center rounded-md text-[var(--portal-sidebar-muted)] transition-colors duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] lg:inline-flex"
              onClick={() => setSidebarCollapsed(true)}
              aria-label={messages.dashboardShell.controls.collapseSidebar}
              title={messages.dashboardShell.controls.collapseSidebar}
              aria-expanded={true}
            >
              <ChevronLeft className="h-4 w-4" aria-hidden="true" />
            </button>
          ) : (
            <button
              type="button"
              className="mt-1.5 hidden h-7 w-7 shrink-0 items-center justify-center rounded-md text-[var(--portal-sidebar-muted)] transition-colors duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] lg:inline-flex"
              onClick={() => setSidebarCollapsed(false)}
              aria-label={messages.dashboardShell.controls.expandSidebar}
              title={messages.dashboardShell.controls.expandSidebar}
              aria-expanded={false}
            >
              <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />
            </button>
          )}
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
            <LocalizedLink
              href={isAdmin ? '/admin' : '/dashboard/profile'}
              aria-label={isAdmin ? messages.admin.title : messages.dashboardShell.menu.profile}
              title={isAdmin ? messages.admin.title : messages.dashboardShell.menu.profile}
              className={cn(
                'flex min-w-0 items-center gap-3 rounded-md p-1.5 text-left transition-[background-color,color] duration-150 hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]',
                sidebarCollapsed && 'justify-center',
              )}
            >
              <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-md bg-white/10 text-xs font-bold text-[var(--portal-sidebar-text)]">
                {avatarPhoto ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={avatarPhoto} alt="" className="h-full w-full object-cover" />
                ) : (
                  <>
                    {user.firstName?.[0]}
                    {user.lastName?.[0]}
                  </>
                )}
              </div>
              {!sidebarCollapsed ? (
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-semibold text-[var(--portal-sidebar-text)]">
                    {fullName}
                  </div>
                  <div className="truncate text-xs text-[var(--portal-sidebar-muted)]">
                    {user.email}
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    {/* Institutional brand gold (accent-independent): the role chip
                        and demo marker keep the warm gold identity on the navy
                        chrome even when the site accent is a cool tone. */}
                    <span className="inline-flex items-center rounded-md border border-[var(--portal-brand-gold)]/40 bg-[var(--portal-brand-gold)]/15 px-2.5 py-0.5 text-[11px] font-semibold uppercase tracking-wider text-[var(--portal-sidebar-text)] shadow-xs">
                      {roleLabel}
                    </span>
                    {isDemoUser(user) && (
                      <span className="inline-flex items-center gap-1 rounded-md border border-[var(--portal-brand-gold)]/35 bg-[var(--portal-brand-gold)]/12 px-2 py-0.5 text-[10px] font-medium text-[var(--portal-sidebar-text)]">
                        <span className="h-1.5 w-1.5 rounded-full bg-[var(--portal-brand-gold)] shadow-xs" />
                        {locale === 'vi' ? 'TK Demo trải nghiệm' : 'Demo account'}
                      </span>
                    )}
                  </div>
                </div>
              ) : null}
            </LocalizedLink>
          </div>
        </div>

        <nav
          ref={sidebarNavRef}
          onScroll={(e) => {
            try {
              sessionStorage.setItem('dashboard_sidebar_scroll', String(e.currentTarget.scrollTop));
            } catch {
              // ignore
            }
          }}
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
                      item.href !== '/admin' &&
                      pathname.startsWith(`${item.href}/`));

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
          <div className={cn('pt-2 border-t border-white/10 mt-2', sidebarCollapsed ? 'px-1' : 'px-2')}>
            {/* The academic assistant stays reachable from the page header pill;
                the sidebar keeps only the specialized launcher so the menu does
                not carry two entries for the same panel. */}
            <button
              onClick={() => {
                if (typeof window !== 'undefined') {
                  window.dispatchEvent(
                    new CustomEvent('open-campus-assistant', { detail: { mode: 'specialized' } }),
                  );
                }
              }}
              className={cn(
                'group flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-[var(--portal-sidebar-muted)] transition-colors duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)]',
                sidebarCollapsed && 'justify-center px-0',
              )}
              aria-label={messages.assistant.specializedLabel}
              title={sidebarCollapsed ? messages.assistant.specializedLabel : undefined}
            >
              <AssistantMascot className="h-5 w-5 shrink-0 text-[var(--portal-brand-gold)] transition-transform duration-200 group-hover:scale-110" />
              {!sidebarCollapsed ? (
                <span>{messages.assistant.specializedLabel}</span>
              ) : null}
            </button>
          </div>

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

      <div
        inert={!isDesktopSidebar && sidebarOpen ? true : undefined}
        className={cn(
          'min-h-screen transition-[padding-left] duration-200 [transition-timing-function:var(--portal-ease)] print:pl-0',
          sidebarCollapsed
            ? 'lg:pl-[var(--portal-sidebar-collapsed)]'
            : 'lg:pl-[var(--portal-sidebar-width)]',
        )}
      >
        <header className="sticky top-0 z-30 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/90 shadow-sm print:hidden">
          <div className="flex min-h-[var(--portal-header-height)] items-center justify-between gap-3 px-4 sm:px-6 lg:px-8">
            <div className="flex min-w-0 items-center gap-3">
              <Button
                ref={openSidebarButtonRef}
                type="button"
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11 lg:hidden"
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
                <div className="text-sm font-semibold leading-snug text-foreground sm:hidden">
                  {currentPage.title}
                </div>
              </div>
            </div>

            <div className="flex items-center gap-2">
              {/* One small assistant launcher in the right corner (all breakpoints):
                  the old labelled pill and its mobile twin were redundant. */}
              <Button
                type="button"
                variant="ghost"
                size="icon"
                className="min-h-11 min-w-11 text-primary hover:bg-secondary/60"
                onClick={() => {
                  if (typeof window !== 'undefined') {
                    window.dispatchEvent(new CustomEvent('open-campus-assistant'));
                  }
                }}
                aria-label={messages.assistant.title}
                title={messages.assistant.title}
              >
                <AssistantMascot className="h-5 w-5" />
              </Button>
              <div className="hidden sm:flex items-center">
                <LanguageToggle />
              </div>
              <div className="flex items-center">
                <ThemeToggle className="min-h-11 min-w-11 text-muted-foreground hover:bg-secondary/60 hover:text-foreground" />
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
                      ) : notificationsError ? (
                        // An outage is not an empty inbox: say so and let the
                        // reader retry the load.
                        <div className="py-4" role="alert">
                          <p className="text-sm leading-6 text-destructive">
                            {messages.dashboardShell.notifications.loadFailed}
                          </p>
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            className="mt-2"
                            onClick={() => void reloadNotifications()}
                          >
                            <RefreshCw className="mr-2 h-3.5 w-3.5" aria-hidden="true" />
                            {messages.common.actions.retry}
                          </Button>
                        </div>
                      ) : notifications.length === 0 ? (
                        <div className="py-6 text-sm leading-6 text-muted-foreground">
                          {messages.dashboardShell.notifications.empty}
                        </div>
                      ) : (
                        <div className="space-y-2">
                          {notifications.map((notification) => {
                            const targetUrl = resolveNotificationTarget(notification, isLecturer);
                            return (
                              <button
                                key={notification.id}
                                type="button"
                                onClick={() => {
                                  setNotificationsOpen(false);
                                  try {
                                    void notificationsApi.markRead(notification.id);
                                    setNotifications((prev) =>
                                      prev.filter((n) => n.id !== notification.id)
                                    );
                                    // The row was unread; keep the server-sourced
                                    // badge in step with the local removal.
                                    setUnreadCount((count) => Math.max(0, count - 1));
                                  } catch {
                                    // ignore
                                  }
                                  router.push(href(targetUrl));
                                }}
                                className="group flex w-full flex-col gap-1 rounded-lg border border-border/70 bg-secondary/40 p-2.5 text-left transition-all duration-150 hover:border-primary/50 hover:bg-secondary/80 hover:shadow-xs active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring cursor-pointer"
                              >
                                <div className="flex items-start justify-between gap-2">
                                  <span className="text-xs font-semibold text-foreground group-hover:text-primary transition-colors">
                                    {notification.title || messages.dashboardShell.notifications.fallbackTitle}
                                  </span>
                                  <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground transition-transform duration-150 group-hover:translate-x-0.5 group-hover:text-primary" />
                                </div>
                                <p className="line-clamp-2 text-[11.5px] leading-relaxed text-muted-foreground">
                                  {notification.content || messages.dashboardShell.notifications.fallbackContent}
                                </p>
                              </button>
                            );
                          })}
                        </div>
                      )}
                    </div>
                    <div className="border-t border-border/70 px-4 py-3 flex flex-col gap-2">
                      <LocalizedLink
                        href="/dashboard/announcements"
                        className="text-xs font-semibold text-primary hover:underline flex items-center justify-between"
                        onClick={() => setNotificationsOpen(false)}
                      >
                        <span className="flex items-center gap-1.5">
                          <Megaphone className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                          <span>{locale === 'vi' ? 'Bảng tin Phòng Đào tạo' : 'Academic Affairs Notice Board'}</span>
                        </span>
                        <ChevronRight className="h-3.5 w-3.5 text-muted-foreground" />
                      </LocalizedLink>
                      <LocalizedLink
                        href="/dashboard/notifications"
                        className="text-xs text-muted-foreground hover:underline"
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
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary text-sm font-semibold text-primary-foreground">
                    {avatarPhoto ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img src={avatarPhoto} alt="" className="h-full w-full object-cover" />
                    ) : (
                      <>
                        {user.firstName?.[0]}
                        {user.lastName?.[0]}
                      </>
                    )}
                  </div>
                  <div className="hidden min-w-0 text-left md:block">
                    <div className="truncate text-sm font-semibold text-foreground">
                      {fullName}
                    </div>
                    <div className="truncate text-xs text-muted-foreground flex items-center gap-1">
                      <span>{user.email}</span>
                      {isDemoUser(user) && (
                        <span className="inline-flex items-center gap-1 text-[10px] font-medium text-muted-foreground">
                          <span
                            className="h-1.5 w-1.5 rounded-full bg-[var(--portal-brand-gold)]"
                            aria-hidden="true"
                          />
                          {locale === 'vi' ? 'Demo trải nghiệm' : 'Demo'}
                        </span>
                      )}
                    </div>
                  </div>
                </button>

                {profileOpen ? (
                  <div
                    id="dashboard-profile-menu"
                    role="menu"
                    className="absolute right-0 mt-2 w-[min(18rem,calc(100vw-2rem))] rounded-md border border-border/80 bg-card shadow-2xl"
                  >
                    <div className="flex items-center gap-3 border-b border-border/70 px-4 py-4">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary text-base font-semibold text-primary-foreground">
                        {avatarPhoto ? (
                          // eslint-disable-next-line @next/next/no-img-element
                          <img src={avatarPhoto} alt="" className="h-full w-full object-cover" />
                        ) : (
                          <>
                            {user.firstName?.[0]}
                            {user.lastName?.[0]}
                          </>
                        )}
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold text-foreground">
                          {fullName}
                        </p>
                        <p className="truncate text-sm text-muted-foreground">
                          {user.email}
                        </p>
                        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                          <div className="inline-flex rounded-md bg-secondary px-2.5 py-0.5 text-xs font-medium text-foreground">
                            {user.roles?.[0] || 'USER'}
                          </div>
                          {isDemoUser(user) && (
                            <div className="inline-flex items-center gap-1 rounded-md bg-amber-500/15 border border-amber-500/30 px-2.5 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">
                              <span className="h-1.5 w-1.5 rounded-full bg-amber-500" />
                              {locale === 'vi' ? 'Tài khoản demo để trải nghiệm' : 'Demo experience account'}
                            </div>
                          )}
                        </div>
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
                      <div className="my-1 border-t border-border/70 pt-1 sm:hidden">
                        <div className="flex items-center justify-between px-3 py-1.5">
                          <span className="text-xs text-muted-foreground">{messages.dashboardShell.controls.preferences}</span>
                          <div className="flex items-center gap-1">
                            <LanguageToggle />
                            <ThemeToggle className="h-8 w-8" />
                          </div>
                        </div>
                      </div>
                      <LocalizedLink
                        href="/dashboard/sign-out"
                        role="menuitem"
                        className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left text-sm text-destructive transition-colors hover:bg-destructive/10"
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

        <div className="mx-auto w-full max-w-[1440px] px-4 py-5 pb-28 sm:px-6 lg:px-8 lg:pb-28 print:p-0 print:m-0 print:max-w-none">
          <main
            id="dashboard-main-content"
            ref={mainRef}
            tabIndex={-1}
            className="min-w-0 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-4"
          >
            {children}
          </main>
        </div>
      </div>
      {mobileNavItems.length > 0 ? (
        <nav
          aria-label={messages.dashboardShell.controls.mobileNavigation}
          aria-hidden={sidebarOpen ? true : undefined}
          inert={sidebarOpen ? true : undefined}
          className="fixed inset-x-0 bottom-0 z-30 border-t border-[var(--portal-rule)] bg-[var(--portal-surface)]/95 px-2 pb-[env(safe-area-inset-bottom)] shadow-[0_-8px_24px_rgba(25,28,33,0.08)] backdrop-blur md:hidden print:hidden"
        >
          <div className="mx-auto grid max-w-md grid-cols-6 gap-1 py-2">
            {mobileNavItems.map((item) => {
              if (item.kind === 'menu') {
                return (
                  <button
                    key="mobile-menu"
                    type="button"
                    onClick={() => setSidebarOpen(true)}
                    aria-label={messages.dashboardShell.controls.openSidebar}
                    aria-expanded={sidebarOpen}
                    aria-controls="dashboard-sidebar"
                    className={cn(
                      'relative flex min-h-11 min-w-0 flex-col items-center justify-center gap-1 rounded-md px-1 py-1 text-xs font-medium transition-[background-color,color] duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                      sidebarOpen
                        ? 'bg-secondary text-primary'
                        : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                    )}
                  >
                    <item.icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                    <span
                      title={messages.dashboardShell.controls.bottomNav.menu}
                      className="w-full max-w-full truncate text-center text-[10px] leading-4"
                    >
                      {messages.dashboardShell.controls.bottomNav.menu}
                    </span>
                    {unreadCount > 0 ? (
                      <span className="absolute right-1 top-1 min-w-4 rounded-md bg-primary px-1 text-center text-[10px] font-semibold leading-4 text-primary-foreground">
                        {unreadCount > 99 ? '99+' : unreadCount}
                      </span>
                    ) : null}
                  </button>
                );
              }

              const isActive =
                pathname === item.href ||
                (item.href !== '/dashboard' &&
                  item.href !== '/dashboard/lecturer' &&
                  pathname.startsWith(item.href));

              const itemLabel =
                messages.dashboardShell.controls.bottomNav[
                  item.labelKey as keyof typeof messages.dashboardShell.controls.bottomNav
                ] ?? menuLabels[item.labelKey];

              return (
                <LocalizedLink
                  key={item.href}
                  href={item.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={cn(
                    'flex min-h-11 min-w-0 flex-col items-center justify-center gap-1 rounded-md px-1 py-1 text-xs font-medium transition-[background-color,color] duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    isActive
                      ? 'bg-secondary text-primary'
                      : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                  )}
                >
                  <item.icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                  <span
                    title={itemLabel}
                    className="w-full max-w-full truncate text-center text-[10px] leading-4"
                  >
                    {itemLabel}
                  </span>
                </LocalizedLink>
              );
            })}
            <button
              type="button"
              data-mobile-assistant-slot="true"
              onClick={() => {
                if (typeof window !== 'undefined') {
                  window.dispatchEvent(new CustomEvent('open-campus-assistant'));
                }
              }}
              aria-label={messages.assistant.open}
              title={messages.assistant.open}
              className="flex min-h-11 min-w-0 w-full flex-col items-center justify-center gap-1 rounded-md px-1 py-1 text-xs font-medium text-muted-foreground transition-[background-color,color] duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring hover:bg-secondary hover:text-foreground"
            >
              <AssistantMascot className="h-5 w-5 shrink-0" />
              <span
                title={messages.assistant.slotLabel}
                className="w-full max-w-full truncate text-center text-[10px] leading-4"
              >
                {messages.assistant.slotLabel}
              </span>
            </button>
          </div>
        </nav>
      ) : null}
      <div className="print:hidden">
        <AssistantPanel />
      </div>
    </div>
  );
}
