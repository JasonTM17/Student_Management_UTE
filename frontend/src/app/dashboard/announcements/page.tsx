'use client';

import { useCallback, useEffect, useState } from 'react';
import { Bell, BookOpen, RefreshCw } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { statusToneClass } from '@/components/ui/status';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useI18n } from '@/i18n';
import { getLocalizedFlatLabel } from '@/lib/academic-content';
import { useOrderedPosts } from '@/components/providers/SiteAppearanceProvider';
import {
  announcementIsUpdated,
  announcementPriorityLabel,
  announcementPriorityTone,
  announcementSectionLabel,
  announcementSemesterName,
} from '@/lib/announcement-presentation';

export default function StudentAnnouncementsPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth([
    'STUDENT',
  ]);
  const { locale, formatDateTime } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu sinh viên',
          title: 'Thông báo',
          description: `Đọc các cập nhật quan trọng dành cho ${user?.firstName ?? 'bạn'} tại đây.`,
          refresh: 'Làm mới',
          loading: 'Đang tải thông báo',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Chưa có thông báo',
          emptyDescription:
            'Thông báo toàn trường và cập nhật từ học phần sẽ xuất hiện tại đây sau khi được phát hành.',
          returnDashboard: 'Quay lại trang tổng quan',
          recentNotices: 'Thông báo gần đây',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'Lớp học phần',
          loadFailed: 'Hiện chưa thể tải thông báo.',
        }
      : {
          eyebrow: 'Student area',
          title: 'Announcements',
          description: `Read the notices that matter to ${user?.firstName ?? 'you'} in one place.`,
          refresh: 'Refresh',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No announcements yet',
          emptyDescription:
            'Campus-wide notices and course updates will appear here once they are published.',
          returnDashboard: 'Return to dashboard',
          recentNotices: 'Recent notices',
          semesterPrefix: 'Semester',
          sectionPrefix: 'Class',
          loadFailed: 'Announcements could not be loaded right now.',
        };

  const fetchFeed = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await announcementsApi.getMy({ page: 1, limit: 50 });
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

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <Button
            type="button"
            variant="outline"
            onClick={() => void fetchFeed()}
            disabled={isLoading}
          >
            <RefreshCw
              className={`mr-2 h-4 w-4 motion-reduce:animate-none ${isLoading ? 'animate-spin' : ''}`}
              aria-hidden="true"
            />
            {copy.refresh}
          </Button>
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
      ) : orderedItems.length === 0 ? (
        <EmptyState
          icon={Bell}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard">{copy.returnDashboard}</LinkButton>
          }
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <CardTitle className="text-xl">{copy.recentNotices}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {orderedItems.map((announcement) => (
              <article
                key={announcement.id}
                className="rounded-lg border border-border/70 bg-card px-5 py-5"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(announcementPriorityTone(announcement.priority))}`}
                      >
                        {announcementPriorityLabel(announcement.priority, locale)}
                      </span>
                      <h2 className="text-lg font-semibold text-foreground">
                        {announcement.title}
                      </h2>
                      {announcementIsUpdated(announcement) ? (
                        <span className="rounded-full bg-secondary px-2 py-1 text-xs text-muted-foreground">
                          {locale === 'vi' ? 'Đã cập nhật' : 'Updated'}
                        </span>
                      ) : null}
                    </div>
                    <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-muted-foreground">
                      {announcement.content}
                    </p>
                    <div className="flex flex-wrap gap-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">
                      {announcementSemesterName(announcement) ? (
                        <span>
                          {copy.semesterPrefix}{' '}
                          {getLocalizedFlatLabel(locale, announcementSemesterName(announcement), announcement.semester?.nameEn, announcement.semester?.nameVi, announcementSemesterName(announcement))}
                        </span>
                      ) : null}
                      {announcementSectionLabel(announcement) ? (
                        <span>
                          {copy.sectionPrefix}: {announcementSectionLabel(announcement)}
                        </span>
                      ) : null}
                    </div>
                  </div>

                  <div className="flex shrink-0 items-start gap-2 rounded-full bg-secondary px-3 py-1.5 text-xs font-medium text-muted-foreground">
                    <BookOpen className="h-3.5 w-3.5" aria-hidden="true" />
                    {formatDateTime(announcement.publishAt || announcement.createdAt)}
                  </div>
                </div>
              </article>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
