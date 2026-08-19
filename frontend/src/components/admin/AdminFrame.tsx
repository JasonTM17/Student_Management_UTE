'use client';

import * as React from 'react';
import { usePathname } from 'next/navigation';
import {
  ArrowLeft,
  BarChart3,
  BookOpen,
  LayoutDashboard,
  LogOut,
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

  const resolvedEyebrow = eyebrow || messages.adminShell.eyebrow;
  const resolvedBackLabel = backLabel || messages.adminShell.backToDashboard;
  const mobileNavItems = [
    { href: '/admin', icon: LayoutDashboard, label: messages.admin.title },
    {
      href: '/admin/users',
      icon: Users,
      label: messages.admin.menuItems[0]?.[0] ?? 'Users',
    },
    {
      href: '/admin/courses',
      icon: BookOpen,
      label: messages.admin.menuItems[2]?.[0] ?? 'Courses',
    },
    {
      href: '/admin/analytics',
      icon: BarChart3,
      label: messages.admin.menuItems[8]?.[0] ?? 'Analytics',
    },
  ];

  return (
    <div className="min-h-screen bg-background">
      <nav className="border-b border-border/70 bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <BrandMark href="/admin" compact />
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
      </nav>

      <nav
        className="border-b border-border/60 bg-card/80 px-4 py-2 sm:hidden"
        aria-label={messages.adminShell.mobileNavigation}
      >
        <div className="mx-auto flex max-w-6xl gap-2 overflow-x-auto pb-0.5">
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
                  'inline-flex min-h-10 shrink-0 items-center gap-2 whitespace-nowrap rounded-lg px-3 text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  isActive
                    ? 'bg-primary/10 text-primary'
                    : 'text-muted-foreground hover:bg-secondary/70 hover:text-foreground',
                )}
              >
                <Icon className="h-4 w-4" aria-hidden="true" />
                {item.label}
              </LocalizedLink>
            );
          })}
        </div>
      </nav>

      <main className="mx-auto min-w-0 max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{resolvedEyebrow}</SectionEyebrow>}
          title={title}
          description={description}
          actions={actions}
        />
        <div className="min-w-0 pt-8">{children}</div>
      </main>
    </div>
  );
}
