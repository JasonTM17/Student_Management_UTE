'use client';

import * as React from 'react';
import { usePathname } from 'next/navigation';
import {
  ArrowLeft,
  Bell,
  BookOpen,
  Building2,
  CalendarRange,
  ClipboardList,
  LayoutDashboard,
  LogOut,
  Menu,
  School,
  Settings,
  X,
  Users,
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

const adminSections = [
  {
    key: 'overview' as const,
    items: [
      { href: '/admin', icon: LayoutDashboard, labelIndex: null },
    ],
  },
  {
    key: 'academics' as const,
    items: [
      { href: '/admin/users', icon: Users, labelIndex: 0 },
      { href: '/admin/lecturers', icon: School, labelIndex: 1 },
      { href: '/admin/courses', icon: BookOpen, labelIndex: 2 },
      { href: '/admin/sections', icon: ClipboardList, labelIndex: 3 },
      { href: '/admin/enrollments', icon: Users, labelIndex: 4 },
      { href: '/admin/semesters', icon: CalendarRange, labelIndex: 5 },
    ],
  },
  {
    key: 'operations' as const,
    items: [
      { href: '/admin/departments', icon: Building2, labelIndex: 6 },
      { href: '/admin/classrooms', icon: Building2, labelIndex: 7 },
      { href: '/admin/announcements', icon: Bell, labelIndex: 8 },
    ],
  },
];

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
  const { messages } = useI18n();
  const pathname = stripLocaleFromPathname(usePathname() ?? '/').pathname;
  const [mobileMenuOpen, setMobileMenuOpen] = React.useState(false);
  const resolvedEyebrow = eyebrow || messages.adminShell.eyebrow;
  const resolvedBackLabel = backLabel || messages.adminShell.backToDashboard;
  const mobileNavItems = [
    { href: '/admin', icon: LayoutDashboard, label: messages.admin.title },
    {
      href: '/admin/users',
      icon: Users,
      label: messages.admin.menuItems[1]?.[0] ?? 'Users',
    },
    {
      href: '/admin/courses',
      icon: BookOpen,
      label: messages.admin.menuItems[3]?.[0] ?? 'Courses',
    },
    {
      href: '/admin/announcements',
      icon: Bell,
      label: messages.admin.menuItems[9]?.[0] ?? 'Announcements',
    },
  ];
  const completeMobileNavItems = adminSections.flatMap((section) =>
    section.items.map((item) => ({
      ...item,
      label:
        item.labelIndex === null
          ? messages.admin.title
          : messages.admin.menuItems[item.labelIndex]?.[0] ?? '',
    })),
  );

  return (
    <div className="portal-content-canvas min-h-screen">
      <aside className="portal-sidebar fixed inset-y-0 left-0 z-40 hidden w-64 flex-col border-r border-white/10 shadow-xl lg:flex">
        <div className="portal-sidebar-header border-b border-white/10 px-5 py-4">
          <BrandMark
            href="/admin"
            compact
            titleClassName="text-white"
            subtitle={messages.adminShell.eyebrow}
            subtitleClassName="text-white/65"
          />
        </div>
        <div className="portal-profile mx-3 my-3 rounded-md px-4 py-3">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-[hsl(var(--nav-active))] text-sm font-bold text-[hsl(var(--nav-surface))]">
              {user?.firstName?.[0]}
              {user?.lastName?.[0]}
            </div>
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold text-white">
                {user?.firstName} {user?.lastName}
              </div>
              <p className="truncate text-xs text-[hsl(var(--nav-muted))]">
                {messages.adminShell.eyebrow}
              </p>
            </div>
          </div>
        </div>

        <nav
          className="flex-1 overflow-y-auto px-4 py-3"
          aria-label={messages.adminShell.mobileNavigation}
        >
          {adminSections.map((section) => (
            <div key={section.key} className="mb-4 last:mb-0">
              <div className="mb-1 px-3 text-[10px] font-bold uppercase tracking-[0.16em] text-[hsl(var(--nav-muted))]">
                {messages.adminShell.sections[section.key]}
              </div>
              {section.items.map((item) => {
                const isActive =
                  pathname === item.href ||
                  (item.href !== '/admin' && pathname.startsWith(`${item.href}/`));
                const Icon = item.icon;
                const label =
                  item.labelIndex === null
                    ? messages.admin.title
                    : messages.admin.menuItems[item.labelIndex]?.[0] ?? '';

                return (
                  <LocalizedLink
                    key={item.href}
                    href={item.href}
                    aria-current={isActive ? 'page' : undefined}
                    className={cn(
                      'portal-nav-item flex items-center gap-3 px-3 py-2 text-sm font-medium transition-colors',
                      isActive && 'is-active',
                    )}
                  >
                    <Icon className="h-4 w-4" aria-hidden="true" />
                    <span>{label}</span>
                  </LocalizedLink>
                );
              })}
            </div>
          ))}
        </nav>

        <div className="border-t border-white/10 px-4 py-3">
          <LocalizedLink
            href="/dashboard/profile"
            className="flex items-center gap-3 rounded-md px-3 py-2 text-sm text-[hsl(var(--nav-muted))] transition-colors hover:bg-white/10 hover:text-white"
          >
            <Settings className="h-4 w-4" aria-hidden="true" />
            {messages.dashboardShell.menu.profileSettings}
          </LocalizedLink>
          <button
            type="button"
            onClick={() => void logout()}
            className="mt-1 flex w-full items-center gap-3 rounded-md px-3 py-2 text-left text-sm text-red-200 transition-colors hover:bg-red-500/15"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
            {messages.common.actions.signOut}
          </button>
        </div>
      </aside>

      <div className="lg:ml-64">
      <div className="portal-utility-bar hidden items-center justify-between px-4 text-[11px] font-semibold uppercase tracking-[0.14em] sm:flex lg:px-8">
        <span>{resolvedEyebrow}</span>
        <span className="max-w-[18rem] truncate opacity-80">{user?.email}</span>
      </div>
      <header className="border-b border-border/80 bg-card/95 backdrop-blur">
        <div className="mx-auto flex min-h-14 max-w-[1400px] items-center justify-between gap-4 px-4 py-2 sm:px-6 lg:px-8">
          <div className="flex min-w-0 items-center gap-3">
            <div className="lg:hidden">
              <div className="flex items-center gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  onClick={() => setMobileMenuOpen((current) => !current)}
                  aria-label={
                    mobileMenuOpen
                      ? messages.dashboardShell.controls.closeSidebar
                      : messages.dashboardShell.controls.openSidebar
                  }
                  aria-expanded={mobileMenuOpen}
                  aria-controls="admin-mobile-menu"
                >
                  {mobileMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
                </Button>
                <BrandMark href="/admin" compact />
              </div>
            </div>
            <LocalizedLink
              href={backHref}
              className="hidden items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground sm:inline-flex"
              aria-label={resolvedBackLabel}
              title={resolvedBackLabel}
            >
              <ArrowLeft className="h-4 w-4" />
              {resolvedBackLabel}
            </LocalizedLink>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <LanguageToggle />
            <ThemeToggle />
            <div className="hidden text-sm text-muted-foreground md:block">
              {user?.firstName}
            </div>
            <Button
              type="button"
              variant="outline"
              className="h-10 w-10 px-0 sm:w-auto sm:px-4"
              onClick={() => void logout()}
              aria-label={messages.common.actions.signOut}
              title={messages.common.actions.signOut}
            >
              <LogOut className="h-4 w-4 sm:mr-2" />
              <span className="sr-only sm:not-sr-only">
                {messages.common.actions.signOut}
              </span>
            </Button>
          </div>
        </div>
      </header>

      <nav
        className="border-b border-border/70 bg-card px-4 py-2 lg:hidden"
        aria-label={messages.adminShell.mobileNavigation}
      >
        <div className="mx-auto flex max-w-[1400px] gap-2 overflow-x-auto pb-0.5 sm:px-2">
          {mobileNavItems.map((item) => {
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
                  'inline-flex min-h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-md px-3 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  isActive
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                )}
              >
                <Icon className="h-4 w-4" aria-hidden="true" />
                {item.label}
              </LocalizedLink>
            );
          })}
          <button
            type="button"
            onClick={() => setMobileMenuOpen((current) => !current)}
            className="inline-flex min-h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-md border border-border/70 px-3 text-xs font-semibold text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-expanded={mobileMenuOpen}
            aria-controls="admin-mobile-menu"
          >
            {mobileMenuOpen ? <X className="h-4 w-4" /> : <Menu className="h-4 w-4" />}
            <span>{messages.dashboardShell.controls.workspaceNavigation}</span>
          </button>
        </div>
      </nav>

      {mobileMenuOpen ? (
        <>
          <button
            type="button"
            className="fixed inset-0 z-40 bg-black/45 lg:hidden"
            onClick={() => setMobileMenuOpen(false)}
            aria-label={messages.dashboardShell.controls.closeOverlay}
          />
          <div
            id="admin-mobile-menu"
            className="fixed inset-x-4 top-20 z-50 max-h-[calc(100vh-6rem)] overflow-y-auto rounded-md border border-border/80 bg-card p-3 shadow-2xl lg:hidden"
            role="dialog"
            aria-label={messages.adminShell.mobileNavigation}
          >
            <div className="mb-2 px-2 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {messages.adminShell.mobileNavigation}
            </div>
            <div className="grid gap-1 sm:grid-cols-2">
              {completeMobileNavItems.map((item) => {
                const Icon = item.icon;
                const isActive =
                  pathname === item.href ||
                  (item.href !== '/admin' && pathname.startsWith(`${item.href}/`));

                return (
                  <LocalizedLink
                    key={item.href}
                    href={item.href}
                    onClick={() => setMobileMenuOpen(false)}
                    aria-current={isActive ? 'page' : undefined}
                    className={cn(
                      'flex min-h-11 items-center gap-3 rounded-md px-3 text-sm font-medium transition-colors',
                      isActive
                        ? 'bg-primary text-primary-foreground'
                        : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                    )}
                  >
                    <Icon className="h-4 w-4" aria-hidden="true" />
                    <span>{item.label}</span>
                  </LocalizedLink>
                );
              })}
            </div>
          </div>
        </>
      ) : null}

      <main className="mx-auto min-w-0 max-w-[1400px] px-4 py-5 sm:px-6 lg:px-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{resolvedEyebrow}</SectionEyebrow>}
          title={title}
          description={description}
          actions={actions}
        />
        <div className="min-w-0 pt-5">{children}</div>
      </main>
      </div>
    </div>
  );
}
