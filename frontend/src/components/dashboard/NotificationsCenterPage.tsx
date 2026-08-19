'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bell,
  Check,
  CheckCheck,
  Inbox,
  RefreshCw,
} from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { notificationsApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { cn } from '@/lib/utils';

type NotificationItem = {
  id: string;
  title?: string;
  content?: string;
  message?: string;
  isRead: boolean;
  createdAt: string;
};

type Filter = 'all' | 'unread';

export default function NotificationsCenterPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth();
  const { formatDateTime, messages } = useI18n();
  const copy = messages.dashboardShell.notifications;
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [isLoading, setIsLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [isMarkingAll, setIsMarkingAll] = useState(false);
  const [error, setError] = useState('');
  const [status, setStatus] = useState('');

  const fetchNotifications = useCallback(async () => {
    setIsLoading(true);
    setError('');
    setStatus('');

    try {
      const response = await notificationsApi.getMy({ limit: 50 });
      setItems(response.data ?? []);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void fetchNotifications();
    }
  }, [fetchNotifications, hasAccess]);

  const unreadCount = useMemo(
    () => items.filter((item) => !item.isRead).length,
    [items],
  );
  const visibleItems = useMemo(
    () => (filter === 'unread' ? items.filter((item) => !item.isRead) : items),
    [filter, items],
  );

  async function markRead(id: string) {
    setBusyId(id);
    setError('');
    try {
      await notificationsApi.markRead(id);
      setItems((current) =>
        current.map((item) =>
          item.id === id ? { ...item, isRead: true } : item,
        ),
      );
    } catch {
      setError(copy.updateFailed);
    } finally {
      setBusyId(null);
    }
  }

  async function markAllRead() {
    if (unreadCount === 0) return;

    setIsMarkingAll(true);
    setError('');
    setStatus('');
    try {
      const response = await notificationsApi.markAllRead();
      const updated = response.updated ?? unreadCount;
      setItems((current) => current.map((item) => ({ ...item, isRead: true })));
      setStatus(copy.updatedCount.replace('{count}', String(updated)));
    } catch {
      setError(copy.updateFailed);
    } finally {
      setIsMarkingAll(false);
    }
  }

  if (authLoading || !hasAccess) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.title}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-wrap items-center gap-3">
            <Button
              type="button"
              variant="outline"
              onClick={() => void fetchNotifications()}
              disabled={isLoading || isMarkingAll}
            >
              <RefreshCw
                className={cn('mr-2 h-4 w-4', isLoading && 'animate-spin')}
              />
              {copy.refresh}
            </Button>
            <Button
              type="button"
              variant="default"
              onClick={() => void markAllRead()}
              disabled={unreadCount === 0 || isMarkingAll || isLoading}
            >
              <CheckCheck className="mr-2 h-4 w-4" />
              {copy.markAllRead}
            </Button>
          </div>
        }
      />

      <div className="flex flex-col gap-4 rounded-lg border border-border/70 bg-card/70 p-3 sm:flex-row sm:items-center sm:justify-between sm:p-4">
        <div className="flex flex-wrap gap-2" role="tablist" aria-label={copy.title}>
          {(['all', 'unread'] as const).map((tab) => {
            const selected = filter === tab;
            const label = tab === 'all' ? copy.all : copy.unread;
            const count = tab === 'all' ? items.length : unreadCount;
            return (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => setFilter(tab)}
                className={cn(
                  'inline-flex min-h-10 items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  selected
                    ? 'bg-primary text-primary-foreground'
                    : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
                )}
              >
                {label}
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-xs',
                    selected ? 'bg-white/15' : 'bg-secondary text-foreground',
                  )}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <Bell className="h-4 w-4" />
          <span>{unreadCount} {copy.unread.toLowerCase()}</span>
        </div>
      </div>

      {status ? (
        <div
          role="status"
          className="rounded-lg border border-emerald-500/25 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300"
        >
          {status}
        </div>
      ) : null}

      {error ? (
        <ErrorState
          title={copy.title}
          description={error}
          onRetry={() => void fetchNotifications()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : visibleItems.length === 0 ? (
        <EmptyState
          icon={Inbox}
          title={filter === 'unread' ? copy.noUnread : copy.noAll}
          description={
            filter === 'unread' ? copy.noUnread : copy.empty
          }
          action={
            <LocalizedLink href="/dashboard">
              <Button variant="outline">{messages.common.actions.openDashboard}</Button>
            </LocalizedLink>
          }
        />
      ) : (
        <Card variant="muted">
          <CardContent className="space-y-3 p-3 sm:p-5">
            {visibleItems.map((item) => {
              const title = item.title || copy.fallbackTitle;
              const content = item.content || item.message || copy.fallbackContent;
              const isBusy = busyId === item.id;

              return (
                <article
                  key={item.id}
                  className={cn(
                    'rounded-lg border bg-card px-4 py-4 transition-colors sm:px-5',
                    item.isRead
                      ? 'border-border/70'
                      : 'border-primary/35 bg-primary/[0.035] shadow-sm',
                  )}
                >
                  <div className="flex items-start gap-3 sm:gap-4">
                    <div
                      className={cn(
                        'mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                        item.isRead
                          ? 'bg-secondary text-muted-foreground'
                          : 'bg-primary/10 text-primary',
                      )}
                    >
                      {item.isRead ? (
                        <Check className="h-4 w-4" />
                      ) : (
                        <Bell className="h-4 w-4" />
                      )}
                    </div>
                    <div className="min-w-0 flex-1 space-y-2">
                      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div className="flex flex-wrap items-center gap-2">
                          <h2 className="text-base font-semibold text-foreground sm:text-lg">
                            {title}
                          </h2>
                          {!item.isRead ? (
                            <span className="rounded-full bg-primary/10 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.14em] text-primary">
                              {copy.unread}
                            </span>
                          ) : null}
                        </div>
                        <time
                          dateTime={item.createdAt}
                          className="shrink-0 text-xs text-muted-foreground"
                        >
                          {formatDateTime(item.createdAt)}
                        </time>
                      </div>
                      <p className="whitespace-pre-line text-sm leading-6 text-muted-foreground">
                        {content}
                      </p>
                      {!item.isRead ? (
                        <Button
                          type="button"
                          variant="ghost"
                          size="sm"
                          onClick={() => void markRead(item.id)}
                          disabled={isBusy}
                          className="px-0 text-primary hover:bg-transparent hover:text-primary/80"
                        >
                          <Check className="mr-2 h-4 w-4" />
                          {isBusy ? messages.common.states.loading : copy.markRead}
                        </Button>
                      ) : (
                        <span className="text-xs text-muted-foreground">{copy.read}</span>
                      )}
                    </div>
                  </div>
                </article>
              );
            })}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
