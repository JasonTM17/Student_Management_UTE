'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bell, RefreshCw, Search } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { AnnouncementRecord, announcementsApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { LocalizedLink } from '@/components/LocalizedLink';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

type AnnouncementTab = 'general' | 'personal';

const priorityTone: Record<string, string> = {
  LOW: 'bg-secondary text-foreground',
  NORMAL: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
  HIGH: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
  URGENT: 'bg-rose-500/12 text-rose-600 dark:text-rose-400',
};

function getAnnouncementScope(item: AnnouncementRecord) {
  return item.isGlobal ?? false ? 'general' : 'personal';
}

function getAnnouncementSender(item: AnnouncementRecord, locale: 'en' | 'vi') {
  if (item.lecturerDisplayName) {
    return item.lecturerDisplayName;
  }

  if (item.publishedBy) {
    return item.publishedBy;
  }

  return locale === 'vi' ? 'Phòng đào tạo' : 'Academic office';
}

function getAnnouncementSearchText(item: AnnouncementRecord) {
  return [
    item.title,
    item.content,
    item.publishedBy,
    item.lecturerDisplayName,
    item.semesterName,
    item.sectionNumber,
    item.courseCode,
    item.courseName,
  ]
    .filter(Boolean)
    .join(' ')
    .toLowerCase();
}

export default function LecturerAnnouncementsPage() {
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatDateTime, formatNumber } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const [activeTab, setActiveTab] = useState<AnnouncementTab>('general');
  const [search, setSearch] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Workspace giảng viên',
          title: 'Thông báo',
          description:
            'Giữ các cập nhật của campus và lớp học phần trong cùng một portal gọn, dễ rà soát.',
          refresh: 'Làm mới',
          backToDashboard: 'Quay lại dashboard giảng viên',
          banner: 'THÔNG BÁO',
          loading: 'Đang tải thông báo',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Chưa có thông báo phù hợp',
          emptyDescription:
            'Thông báo chung và thông báo cá nhân sẽ xuất hiện ở đây khi được phát hành.',
          returnDashboard: 'Quay lại dashboard',
          searchPlaceholder: 'Tìm kiếm thông báo',
          tabGeneral: 'Thông báo chung',
          tabPersonal: 'Thông báo cá nhân',
          tableTitle: 'Tiêu đề',
          tableSender: 'Người gửi',
          tableTime: 'Thời gian gửi',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'Lớp học phần',
          scopeGeneral: 'Chung',
          scopePersonal: 'Cá nhân',
          loadFailed: 'Hiện chưa thể tải thông báo.',
        }
      : {
          eyebrow: 'Lecturer workspace',
          title: 'Announcements',
          description:
            'Keep campus and section notices in one clean teaching portal.',
          refresh: 'Refresh',
          backToDashboard: 'Back to lecturer dashboard',
          banner: 'ANNOUNCEMENTS',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No matching announcements',
          emptyDescription:
            'General notices and personal notices will appear here once they are published.',
          returnDashboard: 'Return to dashboard',
          searchPlaceholder: 'Search announcements',
          tabGeneral: 'General notices',
          tabPersonal: 'Personal notices',
          tableTitle: 'Title',
          tableSender: 'Sender',
          tableTime: 'Sent time',
          semesterPrefix: 'Semester',
          sectionPrefix: 'Section',
          scopeGeneral: 'General',
          scopePersonal: 'Personal',
          loadFailed: 'Announcements could not be loaded right now.',
        };

  const fetchFeed = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await announcementsApi.getMy({ page: 1, limit: 100 });
      setItems(response.data ?? []);
    } catch {
      setError(copy.loadFailed);
    } finally {
      setIsLoading(false);
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void fetchFeed();
    }
  }, [fetchFeed, hasAccess]);

  const tabCounts = useMemo(
    () => ({
      general: items.filter((item) => getAnnouncementScope(item) === 'general')
        .length,
      personal: items.filter((item) => getAnnouncementScope(item) === 'personal')
        .length,
    }),
    [items],
  );

  const visibleItems = useMemo(() => {
    const narrowed = items.filter(
      (item) => getAnnouncementScope(item) === activeTab,
    );
    const normalizedSearch = search.trim().toLowerCase();

    const sorted = [...narrowed].sort((left, right) => {
      const leftTime = new Date(left.publishAt ?? left.createdAt).getTime();
      const rightTime = new Date(right.publishAt ?? right.createdAt).getTime();
      return rightTime - leftTime;
    });

    if (!normalizedSearch) {
      return sorted;
    }

    return sorted.filter((item) =>
      getAnnouncementSearchText(item).includes(normalizedSearch),
    );
  }, [activeTab, items, search]);

  const tabs = [
    { key: 'general' as const, label: copy.tabGeneral, count: tabCounts.general },
    {
      key: 'personal' as const,
      label: copy.tabPersonal,
      count: tabCounts.personal,
    },
  ];

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-wrap gap-3">
            <LocalizedLink href="/dashboard/lecturer">
              <Button
                type="button"
                variant="outline"
                aria-label={copy.backToDashboard}
                title={copy.backToDashboard}
              >
                {copy.backToDashboard}
              </Button>
            </LocalizedLink>
            <Button
              type="button"
              variant="outline"
              onClick={() => void fetchFeed()}
              disabled={isLoading}
            >
              <RefreshCw
                className={cn('mr-2 h-4 w-4', isLoading && 'animate-spin')}
              />
              {copy.refresh}
            </Button>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchFeed()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : (
        <section className="space-y-4 rounded-md border border-border/70 bg-card p-4 shadow-sm">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="min-w-0 space-y-3">
              <div className="inline-flex items-center bg-primary px-4 py-2 text-[15px] font-semibold uppercase tracking-[0.16em] text-primary-foreground">
                {copy.banner}
              </div>
              <div className="flex flex-wrap items-center gap-3 text-xs font-medium uppercase tracking-[0.16em] text-muted-foreground">
                <span>
                  {copy.tabGeneral}: {formatNumber(tabCounts.general)}
                </span>
                <span>
                  {copy.tabPersonal}: {formatNumber(tabCounts.personal)}
                </span>
                <span>
                  {locale === 'vi' ? 'Đang hiển thị' : 'Showing'}{' '}
                  {formatNumber(visibleItems.length)}
                </span>
              </div>
            </div>

            <div className="w-full max-w-xl">
              <Input
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={copy.searchPlaceholder}
                icon={<Search className="h-4 w-4" />}
              />
            </div>
          </div>

          <div
            className="flex flex-wrap gap-2 border-b border-border/70 pb-4"
            role="tablist"
            aria-label={copy.title}
          >
            {tabs.map((tab) => {
              const selected = activeTab === tab.key;

              return (
                <button
                  key={tab.key}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  onClick={() => setActiveTab(tab.key)}
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
                    {formatNumber(tab.count)}
                  </span>
                </button>
              );
            })}
          </div>

          {visibleItems.length === 0 ? (
            <EmptyState
              icon={Bell}
              title={copy.emptyTitle}
              description={copy.emptyDescription}
              action={
                <LocalizedLink href="/dashboard/lecturer">
                  <Button variant="outline">{copy.returnDashboard}</Button>
                </LocalizedLink>
              }
              className="min-h-[320px] border-border/70 bg-background/40"
            />
          ) : (
            <>
              <div className="hidden overflow-hidden rounded-md border border-border/70 md:block">
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse">
                    <thead className="bg-primary text-primary-foreground">
                      <tr>
                        <th className="px-4 py-3 text-left text-sm font-semibold uppercase tracking-[0.14em]">
                          {copy.tableTitle}
                        </th>
                        <th className="px-4 py-3 text-left text-sm font-semibold uppercase tracking-[0.14em]">
                          {copy.tableSender}
                        </th>
                        <th className="px-4 py-3 text-right text-sm font-semibold uppercase tracking-[0.14em]">
                          {copy.tableTime}
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60 bg-card">
                      {visibleItems.map((announcement) => {
                        const sender = getAnnouncementSender(announcement, locale);
                        const timeValue =
                          announcement.publishAt ?? announcement.createdAt;
                        const semesterLabel =
                          announcement.semesterName ||
                          announcement.semester?.name ||
                          '';
                        const sectionLabel =
                          announcement.courseCode && announcement.sectionNumber
                            ? `${announcement.courseCode} - ${announcement.sectionNumber}`
                            : announcement.sectionNumber
                              ? `${copy.sectionPrefix} ${announcement.sectionNumber}`
                              : '';

                        return (
                          <tr
                            key={announcement.id}
                            className="transition-colors hover:bg-secondary/35"
                          >
                            <td className="px-4 py-4 align-top">
                              <div className="space-y-2">
                                <div className="flex flex-wrap items-center gap-2">
                                  <span
                                    className={cn(
                                      'rounded-full px-2.5 py-1 text-xs font-medium',
                                      priorityTone[announcement.priority] ??
                                        'bg-secondary text-foreground',
                                    )}
                                  >
                                    {announcement.priority}
                                  </span>
                                  <div className="min-w-0 break-words text-sm font-semibold text-foreground">
                                    {announcement.title}
                                  </div>
                                </div>
                                {semesterLabel || sectionLabel ? (
                                  <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                                    {semesterLabel ? (
                                      <span>{copy.semesterPrefix} {semesterLabel}</span>
                                    ) : null}
                                    {sectionLabel ? <span>{sectionLabel}</span> : null}
                                    <span>
                                      {announcement.isGlobal
                                        ? copy.scopeGeneral
                                        : copy.scopePersonal}
                                    </span>
                                  </div>
                                ) : null}
                              </div>
                            </td>
                            <td className="px-4 py-4 align-top">
                              <div className="text-sm font-medium text-foreground">
                                {sender}
                              </div>
                              <div className="mt-1 text-xs text-muted-foreground">
                                {announcement.lecturerDisplayName
                                  ? announcement.publishedBy || sender
                                  : announcement.isGlobal
                                    ? copy.scopeGeneral
                                    : copy.scopePersonal}
                              </div>
                            </td>
                            <td className="px-4 py-4 align-top text-right text-sm text-muted-foreground">
                              <time dateTime={timeValue}>
                                {formatDateTime(timeValue)}
                              </time>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              <div className="space-y-3 md:hidden">
                {visibleItems.map((announcement) => {
                  const sender = getAnnouncementSender(announcement, locale);
                  const timeValue =
                    announcement.publishAt ?? announcement.createdAt;
                  const semesterLabel =
                    announcement.semesterName || announcement.semester?.name || '';
                  const sectionLabel =
                    announcement.courseCode && announcement.sectionNumber
                      ? `${announcement.courseCode} - ${announcement.sectionNumber}`
                      : announcement.sectionNumber
                        ? `${copy.sectionPrefix} ${announcement.sectionNumber}`
                        : '';

                  return (
                    <article
                      key={announcement.id}
                      className="rounded-md border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0 space-y-2">
                          <div className="flex flex-wrap items-center gap-2">
                            <span
                              className={cn(
                                'rounded-full px-2.5 py-1 text-xs font-medium',
                                priorityTone[announcement.priority] ??
                                  'bg-secondary text-foreground',
                              )}
                            >
                              {announcement.priority}
                            </span>
                            <h2 className="min-w-0 break-words text-base font-semibold text-foreground">
                              {announcement.title}
                            </h2>
                          </div>
                          <p className="break-words whitespace-pre-line text-sm leading-6 text-muted-foreground">
                            {announcement.content}
                          </p>
                          {(semesterLabel || sectionLabel) && (
                            <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                              {semesterLabel ? (
                                <span>{copy.semesterPrefix} {semesterLabel}</span>
                              ) : null}
                              {sectionLabel ? <span>{sectionLabel}</span> : null}
                            </div>
                          )}
                        </div>
                        <div className="shrink-0 rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-foreground">
                          {announcement.isGlobal ? copy.scopeGeneral : copy.scopePersonal}
                        </div>
                      </div>
                      <div className="mt-4 flex items-center justify-between gap-3 border-t border-border/70 pt-3 text-xs text-muted-foreground">
                        <span className="truncate">{sender}</span>
                        <time dateTime={timeValue}>{formatDateTime(timeValue)}</time>
                      </div>
                    </article>
                  );
                })}
              </div>
            </>
          )}
        </section>
      )}
    </div>
  );
}
