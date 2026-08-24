'use client';

import * as React from 'react';
import { usePathname } from 'next/navigation';
import {
  ArrowLeft,
  Bell,
  BrainCircuit,
  BookMarked,
  BookOpen,
  Building2,
  CalendarRange,
  DoorOpen,
  FileText,
  GraduationCap,
  LayoutDashboard,
  LogOut,
  Menu,
  School,
  Users,
  X,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { BrandMark } from '@/components/BrandMark';
import { LanguageToggle } from '@/components/LanguageToggle';
import { ThemeToggle } from '@/components/ThemeToggle';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';
import { stripLocaleFromPathname } from '@/i18n/paths';
import { cn } from '@/lib/utils';

interface AdminFrameProps {
  title: string;
  description: string;
  eyebrow?: string;
  backHref?: string;
  backLabel?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}

export function AdminFrame({
  title,
  description,
  eyebrow,
  backHref = '/admin',
  backLabel,
  actions,
  children,
}: AdminFrameProps) {
  const { user, logout } = useAuth();
  const { messages, locale } = useI18n();
  const pathname = stripLocaleFromPathname(usePathname() ?? '/').pathname;
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const [isDesktopSidebar, setIsDesktopSidebar] = React.useState(false);
  const sidebarCloseRef = React.useRef<HTMLButtonElement>(null);
  const openSidebarButtonRef = React.useRef<HTMLButtonElement>(null);
  const mainRef = React.useRef<HTMLElement>(null);
  const previousPathnameRef = React.useRef(pathname);

  const resolvedEyebrow = eyebrow || messages.adminShell.eyebrow;
  const resolvedBackLabel = backLabel || messages.adminShell.backToDashboard;
  const roleLabel = user?.roles?.includes('SUPER_ADMIN')
    ? messages.adminShell.superAdminRole
    : messages.adminShell.adminRole;
  const adminMenuSections = [
    {
      key: 'overview',
      label: messages.adminShell.menuSections.overview,
      items: [
        { href: '/admin', icon: LayoutDashboard, label: messages.admin.title },
      ],
    },
    {
      key: 'people',
      label: messages.adminShell.menuSections.people,
      items: [
        { href: '/admin/users', icon: Users, label: messages.admin.menuItems[1]?.[0] },
        { href: '/admin/lecturers', icon: School, label: messages.admin.menuItems[2]?.[0] },
      ],
    },
    {
      key: 'academics',
      label: messages.adminShell.menuSections.academics,
      items: [
        { href: '/admin/courses', icon: BookOpen, label: messages.admin.menuItems[3]?.[0] },
        { href: '/admin/sections', icon: BookMarked, label: messages.admin.menuItems[4]?.[0] },
        { href: '/admin/enrollments', icon: FileText, label: messages.admin.menuItems[5]?.[0] },
        { href: '/admin/semesters', icon: GraduationCap, label: messages.admin.menuItems[6]?.[0] },
        { href: '/admin/registration-rounds', icon: CalendarRange, label: locale === 'vi' ? 'Đợt đăng ký' : 'Registration rounds' },
        { href: '/admin/academic-years', icon: CalendarRange, label: messages.adminShell.academicYears },
        { href: '/admin/departments', icon: Building2, label: messages.admin.menuItems[7]?.[0] },
        { href: '/admin/classrooms', icon: DoorOpen, label: messages.admin.menuItems[8]?.[0] },
      ],
    },
    {
      key: 'campus',
      label: messages.adminShell.menuSections.campus,
      items: [
        { href: '/admin/thesis', icon: GraduationCap, label: messages.admin.menuItems[0]?.[0] },
        { href: '/admin/announcements', icon: Bell, label: messages.admin.menuItems[9]?.[0] },
        { href: '/admin/assistant-knowledge', icon: BrainCircuit, label: locale === 'vi' ? 'Kiến thức trợ lý AI' : 'AI assistant knowledge' },
      ],
    },
  ];

  React.useEffect(() => {
    const media = window.matchMedia('(min-width: 1024px)');
    const syncDesktopState = () => setIsDesktopSidebar(media.matches);

    syncDesktopState();
    media.addEventListener('change', syncDesktopState);
    return () => media.removeEventListener('change', syncDesktopState);
  }, []);

  React.useEffect(() => {
    setSidebarOpen(false);
    if (previousPathnameRef.current !== pathname) {
      mainRef.current?.focus({ preventScroll: true });
      previousPathnameRef.current = pathname;
    }
  }, [pathname]);

  React.useEffect(() => {
    if (!sidebarOpen || isDesktopSidebar) {
      return;
    }

    const frame = window.requestAnimationFrame(() => sidebarCloseRef.current?.focus());
    return () => window.cancelAnimationFrame(frame);
  }, [isDesktopSidebar, sidebarOpen]);

  React.useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    if (sidebarOpen) {
      document.body.style.overflow = 'hidden';
    }

    const handleEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && sidebarOpen) {
        setSidebarOpen(false);
        window.requestAnimationFrame(() => openSidebarButtonRef.current?.focus());
      }
    };

    document.addEventListener('keydown', handleEscape);
    return () => {
      document.body.style.overflow = previousOverflow;
      document.removeEventListener('keydown', handleEscape);
    };
  }, [sidebarOpen]);

  return (
    <div className="portal-shell">
      <a
        href="#admin-main-content"
        className="portal-skip-link"
        tabIndex={!isDesktopSidebar && sidebarOpen ? -1 : undefined}
      >
        {messages.adminShell.skipToContent}
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
          aria-label={messages.adminShell.closeOverlay}
        />
      ) : null}

      <aside
        id="admin-sidebar"
        aria-label={messages.adminShell.sidebarNavigation}
        role={!isDesktopSidebar ? 'dialog' : undefined}
        aria-modal={!isDesktopSidebar && sidebarOpen ? true : undefined}
        aria-hidden={!isDesktopSidebar && !sidebarOpen}
        inert={!isDesktopSidebar && !sidebarOpen ? true : undefined}
        className={cn(
          'portal-sidebar fixed inset-y-0 left-0 z-50 flex w-[var(--portal-sidebar-width)] max-w-[calc(100vw-3rem)] flex-col border-r border-white/10 shadow-xl transition-transform duration-200 [transition-timing-function:var(--portal-ease)] lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full',
        )}
      >
        <div className="flex min-h-[4.25rem] items-center justify-between border-b border-white/10 px-5 py-3">
          <BrandMark
            href="/admin"
            compact
            markClassName="border-0 bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)] shadow-none"
            titleClassName="text-[var(--portal-sidebar-text)]"
            subtitle={messages.adminShell.portalTitle}
            subtitleClassName="text-[var(--portal-sidebar-muted)]"
          />
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
            aria-label={messages.adminShell.closeSidebar}
          >
            <X className="h-5 w-5" aria-hidden="true" />
          </Button>
        </div>

        <div className="border-b border-white/10 bg-[var(--portal-sidebar-strong)] px-5 py-3">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-white/10 text-xs font-bold text-[var(--portal-sidebar-text)]">
              {user?.firstName?.[0]}
              {user?.lastName?.[0]}
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold text-[var(--portal-sidebar-text)]">
                {user?.firstName} {user?.lastName}
              </div>
              <div className="truncate text-xs text-[var(--portal-sidebar-muted)]">
                {user?.email}
              </div>
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between gap-3">
            <span className="portal-menu-label">{messages.adminShell.identityLabel}</span>
            <span className="text-xs font-semibold text-[var(--portal-sidebar-text)]">
              {roleLabel}
            </span>
          </div>
        </div>

        <nav className="flex-1 space-y-4 overflow-y-auto overscroll-contain px-4 py-3">
          {adminMenuSections.map((section) => (
            <div key={section.key} className="space-y-2">
              <div className="portal-menu-label px-3">{section.label}</div>
              <div className="space-y-1">
                {section.items.map((item) => {
                  const isActive =
                    pathname === item.href ||
                    (item.href !== '/admin' && pathname.startsWith(`${item.href}/`));
                  const Icon = item.icon;

                  return (
                    <LocalizedLink
                      key={item.href}
                      href={item.href}
                      aria-current={isActive ? 'page' : undefined}
                      className={cn(
                        'relative flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-[var(--portal-sidebar-muted)] transition-[background-color,color] duration-150 hover:bg-white/10 hover:text-[var(--portal-sidebar-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]',
                        isActive &&
                          'bg-white/[0.12] font-semibold text-[var(--portal-sidebar-text)] before:absolute before:left-0 before:h-6 before:w-0.5 before:bg-[var(--portal-yellow)]',
                      )}
                    >
                      <Icon className="h-5 w-5 shrink-0" aria-hidden="true" />
                      <span>{item.label}</span>
                    </LocalizedLink>
                  );
                })}
              </div>
            </div>
          ))}
        </nav>

        <div className="border-t border-white/10 px-4 py-3">
          <div className="mb-3 flex items-center justify-between gap-2 border-b border-white/10 pb-3">
            <span className="portal-menu-label">{messages.adminShell.preferences}</span>
            <div className="flex items-center gap-1 rounded-md bg-white/10 p-1 text-[var(--portal-sidebar-text)]">
              <LanguageToggle inverse />
              <ThemeToggle className="text-[var(--portal-sidebar-text)] hover:bg-white/10 hover:text-[var(--portal-yellow)]" />
            </div>
          </div>
          <button
            type="button"
            className="flex min-h-11 w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm font-medium text-[var(--portal-sidebar-text)] transition-colors duration-150 hover:bg-red-500/20 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]"
            onClick={() => void logout()}
          >
            <LogOut className="h-5 w-5" aria-hidden="true" />
            {messages.common.actions.signOut}
          </button>
        </div>
      </aside>

      <div
        className="min-h-screen lg:pl-[var(--portal-sidebar-width)]"
        inert={!isDesktopSidebar && sidebarOpen ? true : undefined}
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
                aria-label={messages.adminShell.openSidebar}
                aria-expanded={sidebarOpen}
                aria-controls="admin-sidebar"
              >
                <Menu className="h-5 w-5" aria-hidden="true" />
              </Button>
              <div className="min-w-0">
                <div className="hidden text-sm font-medium text-muted-foreground sm:block">
                  {messages.adminShell.portalTitle}
                </div>
                <div className="truncate text-base font-semibold text-foreground sm:hidden">
                  {title}
                </div>
              </div>
            </div>

            <div className="flex shrink-0 items-center gap-2">
              <div className="hidden sm:block">
                <LanguageToggle />
              </div>
              <div className="hidden sm:block">
                <ThemeToggle />
              </div>
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={() => void logout()}
                aria-label={messages.common.actions.signOut}
                title={messages.common.actions.signOut}
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
          </div>
        </header>

        <main
          id="admin-main-content"
          ref={mainRef}
          tabIndex={-1}
          className="mx-auto min-w-0 max-w-[1440px] px-4 py-5 focus:outline-none sm:px-6 lg:px-8"
        >
          {pathname !== backHref ? (
            <LocalizedLink
              href={backHref}
              className="mb-3 inline-flex min-h-11 items-center gap-2 rounded-md px-2 text-sm font-semibold text-primary transition-colors duration-150 hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label={resolvedBackLabel}
            >
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              {resolvedBackLabel}
            </LocalizedLink>
          ) : null}
          <PageHeader
            eyebrow={<SectionEyebrow>{resolvedEyebrow}</SectionEyebrow>}
            title={title}
            description={description}
            actions={actions}
          />
          <div className="min-w-0 pt-6">{children}</div>
        </main>
      </div>
    </div>
  );
}
