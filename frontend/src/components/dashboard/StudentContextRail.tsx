'use client';

import { Bell, BookOpen, Calendar, ChevronLeft, ChevronRight, ClipboardList, X } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface StudentContextRailProps {
  currentPageTitle: string;
  currentPageDescription: string;
  unreadCount: number;
  collapsed: boolean;
  onToggleCollapsed: () => void;
  mobile?: boolean;
  onCloseMobile?: () => void;
}

const quickLinkConfig = [
  {
    href: '/dashboard/register',
    icon: ClipboardList,
    tone: 'bg-blue-500/12 text-blue-600 dark:text-blue-300',
    labelKey: 'registration',
  },
  {
    href: '/dashboard/schedule',
    icon: Calendar,
    tone: 'bg-emerald-500/12 text-emerald-600 dark:text-emerald-300',
    labelKey: 'schedule',
  },
  {
    href: '/dashboard/announcements',
    icon: Bell,
    tone: 'bg-violet-500/12 text-violet-600 dark:text-violet-300',
    labelKey: 'announcements',
  },
] as const;

export function StudentContextRail({
  currentPageTitle,
  currentPageDescription,
  unreadCount,
  collapsed,
  onToggleCollapsed,
  mobile = false,
  onCloseMobile,
}: StudentContextRailProps) {
  const { locale, messages } = useI18n();
  const copy = messages.dashboardShell.studentRail;
  const localeLabel =
    locale === 'vi'
      ? messages.common.locale.vietnamese
      : messages.common.locale.english;

  return (
    <aside
      role={mobile ? 'dialog' : undefined}
      aria-modal={mobile || undefined}
      aria-label={copy.title}
      className={cn(
        'flex h-full min-h-[calc(100dvh-8rem)] flex-col overflow-y-auto overscroll-contain rounded-lg border border-border/70 bg-card panel-shadow',
        collapsed ? 'px-3 py-4' : 'px-4 py-4',
      )}
    >
      <div
        className={cn(
          'flex items-center gap-3 border-b border-border/70 pb-4',
          collapsed ? 'justify-center' : 'justify-between',
        )}
      >
        {collapsed ? null : (
          <div className="min-w-0">
            <div className="text-sm font-semibold text-foreground">
              {copy.title}
            </div>
            <p className="mt-1 text-sm leading-6 text-muted-foreground">
              {copy.subtitle}
            </p>
          </div>
        )}
        <div className="flex items-center gap-2">
          {mobile && onCloseMobile ? (
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={onCloseMobile}
              aria-label={copy.closeDrawer}
            >
              <X className="h-5 w-5" aria-hidden="true" />
            </Button>
          ) : null}
          <Button
            type="button"
            variant="ghost"
            size="icon"
            onClick={onToggleCollapsed}
            aria-label={collapsed ? copy.expand : copy.collapse}
            title={collapsed ? copy.expand : copy.collapse}
          >
            {collapsed ? (
              <ChevronLeft className="h-5 w-5" aria-hidden="true" />
            ) : (
              <ChevronRight className="h-5 w-5" aria-hidden="true" />
            )}
          </Button>
        </div>
      </div>

      <div className={cn('flex-1 space-y-4 pt-4', collapsed && 'space-y-3')}>
        <div className="rounded-lg border border-border/70 bg-secondary/35 px-3 py-3">
          <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {copy.currentViewLabel}
          </div>
          {collapsed ? (
            <div className="mt-2 flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <BookOpen className="h-5 w-5" aria-hidden="true" />
            </div>
          ) : (
            <>
              <div className="mt-2 text-sm font-semibold text-foreground">
                {currentPageTitle}
              </div>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                {currentPageDescription}
              </p>
            </>
          )}
        </div>

        <div className="rounded-lg border border-border/70 bg-secondary/20 px-3 py-3">
          <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
            {copy.signalsTitle}
          </div>
          <div className="mt-3 space-y-3">
            <div className="flex items-center justify-between gap-3">
              <span className="text-sm text-muted-foreground">
                {copy.notificationLabel}
              </span>
              <span className="rounded-full bg-card px-2.5 py-1 text-xs font-semibold text-foreground">
                {unreadCount}
              </span>
            </div>
            {!collapsed ? (
              <>
                <div className="flex items-center justify-between gap-3">
                  <span className="text-sm text-muted-foreground">
                    {copy.localeLabel}
                  </span>
                  <span className="text-sm font-medium text-foreground">
                    {localeLabel}
                  </span>
                </div>
                <p className="text-sm leading-6 text-muted-foreground">
                  {copy.sessionSummary}
                </p>
              </>
            ) : null}
          </div>
        </div>

        <div className="space-y-2">
          {!collapsed ? (
            <div className="px-1 text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
              {copy.quickActionsTitle}
            </div>
          ) : null}
          {quickLinkConfig.map((item) => {
            const Icon = item.icon;
            const linkCopy = copy.quickLinks[item.labelKey];

            return (
              <LocalizedLink
                key={item.href}
                href={item.href}
                onClick={mobile ? onCloseMobile : undefined}
                className={cn(
                  'group flex items-center gap-3 rounded-lg border border-border/70 bg-card px-3 py-3 transition-[background-color,transform] duration-150 hover:bg-secondary/50 motion-safe:active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
                  collapsed && 'justify-center px-0',
                )}
                aria-label={linkCopy.title}
                title={linkCopy.title}
              >
                <div
                  className={cn(
                    'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                    item.tone,
                  )}
                >
                  <Icon className="h-5 w-5" aria-hidden="true" />
                </div>
                {collapsed ? null : (
                  <div className="min-w-0">
                    <div className="text-sm font-semibold text-foreground">
                      {linkCopy.title}
                    </div>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">
                      {linkCopy.description}
                    </p>
                  </div>
                )}
              </LocalizedLink>
            );
          })}
        </div>
      </div>
    </aside>
  );
}
