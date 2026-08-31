'use client';

import { useCallback, useEffect, useState } from 'react';
import { Bell, RefreshCw } from 'lucide-react';
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

export default function LecturerAnnouncementsPage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth(['LECTURER']);
  const { locale, formatDateTime } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu giảng viên',
          title: 'Thông báo',
          description:
            'Giữ các cập nhật mới nhất từ trường và lớp học phần sát với luồng giảng dạy của bạn.',
          backToDashboard: 'Quay lại trang tổng quan giảng viên',
          refresh: 'Làm mới',
          loading: 'Đang tải thông báo',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Chưa có thông báo',
          emptyDescription:
            'Các thông báo dùng chung cho việc giảng dạy sẽ xuất hiện ở đây sau khi được phát hành.',
          recentNotices: 'Thông báo gần đây',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'Lớp học phần',
          loadFailed: 'Hiện chưa thể tải thông báo.',
        }
      : {
          eyebrow: 'Lecturer area',
          title: 'Announcements',
          description:
            'Keep the latest campus and class notices close to your teaching workflow.',
          backToDashboard: 'Back to lecturer dashboard',
          refresh: 'Refresh',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No announcements yet',
          emptyDescription:
            'Shared teaching notices will appear here once they are published.',
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
          <div className="flex flex-wrap gap-3">
            <LinkButton
              href="/dashboard/lecturer"
              variant="outline"
              aria-label={copy.backToDashboard}
              title={copy.backToDashboard}
            >
              {copy.backToDashboard}
            </LinkButton>
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
      ) : orderedItems.length === 0 ? (
        <EmptyState
          icon={Bell}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
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
                    <span>{formatDateTime(announcement.publishAt || announcement.createdAt)}</span>
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
