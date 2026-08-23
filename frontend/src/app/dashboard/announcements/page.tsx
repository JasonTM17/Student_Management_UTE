'use client';

import { useCallback, useEffect, useState } from 'react';
import { Bell, BookOpen, RefreshCw } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { announcementsApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { LinkButton } from '@/components/ui/link-button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import { getLocalizedFlatLabel } from '@/lib/academic-content';

type Announcement = {
  id: string;
  title: string;
  content: string;
  priority: string;
  createdAt: string;
  semester?: { name: string; nameEn?: string; nameVi?: string } | null;
  section?: {
    sectionNumber: string;
    course?: { code?: string; name?: string; nameEn?: string; nameVi?: string };
  } | null;
};

const priorityTone: Record<string, string> = {
  LOW: 'bg-secondary text-foreground',
  NORMAL: 'bg-blue-500/12 text-blue-600 dark:text-blue-400',
  HIGH: 'bg-amber-500/12 text-amber-600 dark:text-amber-400',
  URGENT: 'bg-rose-500/12 text-rose-600 dark:text-rose-400',
};

export default function StudentAnnouncementsPage() {
  const { user, isLoading: authLoading, hasAccess } = useRequireAuth([
    'STUDENT',
  ]);
  const { locale, formatDateTime } = useI18n();
  const [items, setItems] = useState<Announcement[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Không gian sinh viên',
          title: 'Thông báo',
          description: `Đọc các cập nhật quan trọng dành cho ${user?.firstName ?? 'bạn'} mà không rời khỏi không gian sinh viên.`,
          refresh: 'Làm mới',
          loading: 'Đang tải thông báo',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Chưa có thông báo',
          emptyDescription:
            'Thông báo toàn trường và cập nhật từ học phần sẽ xuất hiện tại đây sau khi được phát hành.',
          returnDashboard: 'Quay lại dashboard',
          recentNotices: 'Thông báo gần đây',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'lớp học phần',
          loadFailed: 'Hiện chưa thể tải thông báo.',
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Announcements',
          description: `Read the notices that matter to ${user?.firstName ?? 'you'} without leaving the protected student workspace.`,
          refresh: 'Refresh',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No announcements yet',
          emptyDescription:
            'Campus-wide notices and course updates will appear here once they are published.',
          returnDashboard: 'Return to dashboard',
          recentNotices: 'Recent notices',
          semesterPrefix: 'Semester',
          sectionPrefix: 'section',
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

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
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
              className={`mr-2 h-4 w-4 ${isLoading ? 'animate-spin' : ''}`}
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
      ) : items.length === 0 ? (
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
            {items.map((announcement) => (
              <article
                key={announcement.id}
                className="rounded-lg border border-border/70 bg-card px-5 py-5"
              >
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span
                        className={`rounded-full px-2.5 py-1 text-xs font-medium ${
                          priorityTone[announcement.priority] ??
                          'bg-secondary text-foreground'
                        }`}
                      >
                        {announcement.priority}
                      </span>
                      <h2 className="text-lg font-semibold text-foreground">
                        {announcement.title}
                      </h2>
                    </div>
                    <p className="max-w-3xl whitespace-pre-line text-sm leading-7 text-muted-foreground">
                      {announcement.content}
                    </p>
                    <div className="flex flex-wrap gap-3 text-xs uppercase tracking-[0.16em] text-muted-foreground">
                      {announcement.semester?.name ? (
                        <span>
                          {copy.semesterPrefix}{' '}
                          {getLocalizedFlatLabel(
                            locale,
                            announcement.semester.name,
                            announcement.semester.nameEn,
                            announcement.semester.nameVi,
                            announcement.semester.name,
                          )}
                        </span>
                      ) : null}
                      {announcement.section?.course?.code ? (
                        <span>
                          {announcement.section.course.code} {copy.sectionPrefix}{' '}
                          {announcement.section.sectionNumber}
                        </span>
                      ) : null}
                    </div>
                  </div>

                  <div className="flex shrink-0 items-start gap-2 rounded-full bg-secondary px-3 py-1.5 text-xs font-medium text-muted-foreground">
                    <BookOpen className="h-3.5 w-3.5" />
                    {formatDateTime(announcement.createdAt)}
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
