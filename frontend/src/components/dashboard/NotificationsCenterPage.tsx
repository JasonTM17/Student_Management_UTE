'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Bell,
  Check,
  CheckCheck,
  Inbox,
  Search,
  RefreshCw,
} from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { notificationsApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
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
  const { locale, formatDateTime, messages } = useI18n();
  const copy = messages.dashboardShell.notifications;
  const [items, setItems] = useState<NotificationItem[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [search, setSearch] = useState('');
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
    () => {
      const narrowed =
        filter === 'unread' ? items.filter((item) => !item.isRead) : items;
      const normalizedSearch = search.trim().toLowerCase();

      if (!normalizedSearch) {
        return narrowed;
      }

      return narrowed.filter((item) =>
        [item.title, item.content, item.message]
          .filter(Boolean)
          .some((value) =>
            String(value).toLowerCase().includes(normalizedSearch),
          ),
      );
    },
    [filter, items, search],
  );
  const tabItems = [
    { key: 'all', label: copy.all, count: items.length },
    { key: 'unread', label: copy.unread, count: unreadCount },
  ] as const;
  const searchPlaceholder =
    messages.common.states.searchPlaceholder || (locale === 'vi'
      ? 'Tìm kiếm thông báo'
      : 'Search notifications');
  const timeSentLabel = locale === 'vi' ? 'Thời gian gửi' : 'Sent time';
  const hasSearch = search.trim().length > 0;

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

      <div className="space-y-4 rounded-lg border border-border/70 bg-card p-4 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div
            className="flex flex-wrap gap-2"
            role="tablist"
            aria-label={copy.title}
          >
            {tabItems.map((tab) => {
              const selected = filter === tab.key;
              return (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setFilter(tab.key)}
                  className={cn(
                    'inline-flex min-h-10 items-center gap-2 rounded-md border px-3 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                    selected
                      ? 'border-primary bg-primary text-primary-foreground'
                      : 'border-border/70 bg-secondary/20 text-muted-foreground hover:bg-secondary/60 hover:text-foreground',
                  )}
                >
                  {tab.label}
                  <span
                    className={cn(
                      'rounded-full px-2 py-0.5 text-xs font-semibold',
                      selected
                        ? 'bg-white/15 text-primary-foreground'
                        : 'bg-card text-foreground',
                    )}
                  >
                    {tab.count}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="flex w-full max-w-xl flex-col gap-3 sm:flex-row sm:items-center">
            <div className="flex-1">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={searchPlaceholder}
                icon={<Search className="h-4 w-4" />}
              />
            </div>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Bell className="h-4 w-4" />
              <span>
                {unreadCount} {copy.unread.toLowerCase()}
              </span>
            </div>
          </div>
        </div>

        {status ? (
          <div
            role="status"
            className="rounded-md border border-emerald-500/25 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300"
          >
            {status}
          </div>
        ) : null}

        {!error && !isLoading && visibleItems.length > 0 ? (
          <>
            <div className="md:hidden space-y-3">
          {visibleItems.map((item) => {
            const title = item.title || copy.fallbackTitle;
            const content = item.content || item.message || copy.fallbackContent;
            const isBusy = busyId === item.id;

            return (
              <article
                key={item.id}
                className={cn(
                  'rounded-md border px-4 py-4',
                  item.isRead
                    ? 'border-border/70 bg-card'
                    : 'border-primary/35 bg-primary/[0.035]',
                )}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={cn(
                      'mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-md',
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
                    <div className="flex items-start justify-between gap-3">
                      <h2 className="text-base font-semibold text-foreground">
                        {title}
                      </h2>
                      <time
                        dateTime={item.createdAt}
                        className="shrink-0 text-xs text-muted-foreground"
                      >
                        {formatDateTime(item.createdAt)}
                      </time>
                    </div>
                    <p className="text-sm leading-6 text-muted-foreground">
                      {content}
                    </p>
                    <div className="flex items-center justify-between gap-3">
                      <span
                        className={cn(
                          'inline-flex rounded-full px-2.5 py-1 text-xs font-semibold',
                          item.isRead
                            ? 'bg-secondary text-foreground'
                            : 'bg-primary/10 text-primary',
                        )}
                      >
                        {item.isRead ? copy.read : copy.unread}
                      </span>
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
                      ) : null}
                    </div>
                  </div>
                </div>
              </article>
            );
          })}
            </div>

            <div className="hidden overflow-hidden rounded-md border border-border/70 md:block">
              <table className="w-full border-collapse text-sm">
                <thead className="bg-primary text-primary-foreground">
                  <tr>
                    <th className="px-4 py-3 text-left font-semibold uppercase tracking-[0.14em]">
                      {locale === 'vi' ? 'Tiêu đề' : 'Title'}
                    </th>
                    <th className="px-4 py-3 text-left font-semibold uppercase tracking-[0.14em]">
                      {locale === 'vi' ? 'Trạng thái' : 'Status'}
                    </th>
                    <th className="px-4 py-3 text-left font-semibold uppercase tracking-[0.14em]">
                      {timeSentLabel}
                    </th>
                    <th className="px-4 py-3 text-right font-semibold uppercase tracking-[0.14em]">
                      {locale === 'vi' ? 'Xử lý' : 'Action'}
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60 bg-card">
                  {visibleItems.map((item) => {
                    const title = item.title || copy.fallbackTitle;
                    const content = item.content || item.message || copy.fallbackContent;
                    const isBusy = busyId === item.id;

                    return (
                      <tr
                        key={item.id}
                        className={cn(
                          'transition-colors',
                          item.isRead
                            ? 'hover:bg-secondary/40'
                            : 'bg-primary/[0.03] hover:bg-primary/[0.06]',
                        )}
                      >
                        <td className="px-4 py-3 align-top">
                          <div className="space-y-1">
                            <div className="font-medium text-foreground">{title}</div>
                            <div className="max-w-3xl text-sm leading-6 text-muted-foreground">
                              {content}
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-3 align-top">
                          <span
                            className={cn(
                              'inline-flex rounded-full px-2.5 py-1 text-xs font-semibold',
                              item.isRead
                                ? 'bg-secondary text-foreground'
                                : 'bg-primary/10 text-primary',
                            )}
                          >
                            {item.isRead ? copy.read : copy.unread}
                          </span>
                        </td>
                        <td className="px-4 py-3 align-top text-muted-foreground">
                          <time dateTime={item.createdAt}>
                            {formatDateTime(item.createdAt)}
                          </time>
                        </td>
                        <td className="px-4 py-3 align-top text-right">
                          {!item.isRead ? (
                            <Button
                              type="button"
                              variant="ghost"
                              size="sm"
                              onClick={() => void markRead(item.id)}
                              disabled={isBusy}
                              className="text-primary hover:bg-primary/10 hover:text-primary"
                            >
                              <Check className="mr-2 h-4 w-4" />
                              {isBusy
                                ? messages.common.states.loading
                                : copy.markRead}
                            </Button>
                          ) : (
                            <span className="text-xs text-muted-foreground">
                              {copy.read}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        ) : null}
      </div>

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
          title={
            hasSearch
              ? locale === 'vi'
                ? 'Không tìm thấy thông báo'
                : 'No matching notifications'
              : filter === 'unread'
                ? copy.noUnread
                : copy.noAll
          }
          description={
            hasSearch
              ? locale === 'vi'
                ? 'Hãy thử một từ khóa khác hoặc xóa bộ lọc hiện tại.'
                : 'Try a different keyword or clear the current filters.'
              : filter === 'unread'
                ? copy.noUnread
                : copy.empty
          }
          action={
            <LocalizedLink href="/dashboard">
              <Button variant="outline">
                {messages.common.actions.openDashboard}
              </Button>
            </LocalizedLink>
          }
        />
      ) : null}
    </div>
  );
}
