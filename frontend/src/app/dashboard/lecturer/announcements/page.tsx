'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Bell,
  BookOpen,
  Calendar,
  GraduationCap,
  Plus,
  RefreshCw,
  Search,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementMutation, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { LecturerAnnouncementCreateModal } from '@/components/announcements/LecturerAnnouncementCreateModal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { LinkButton } from '@/components/ui/link-button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { statusToneClass } from '@/components/ui/status';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useI18n } from '@/i18n';
import { useOrderedPosts } from '@/components/providers/SiteAppearanceProvider';
import {
  announcementAudienceBadge,
  announcementAudienceType,
  announcementIsUpdated,
  announcementPriorityLabel,
  announcementPriorityTone,
  announcementSectionLabel,
  announcementSemesterName,
  formatAnnouncementPublisher,
  formatAnnouncementSemester,
} from '@/lib/announcement-presentation';
import { cn } from '@/lib/utils';

type LecturerNoticeCategory =
  | 'ALL'
  | 'GLOBAL'
  | 'LECTURER_ONLY'
  | 'EXAM_GRADES'
  | 'RESEARCH_SYLLABUS';

export const noticeTemplates = [
  { label: 'Nghỉ học & Học bù', title: 'Thông báo nghỉ học và lịch học bù', content: 'Lớp học phần nghỉ buổi học theo lịch. Buổi học bù được tổ chức vào: ' },
  { label: 'Nhắc nhở nộp bài tập lớn/đồ án', title: 'Nhắc thời hạn nộp bài tập lớn/đồ án', content: 'Sinh viên hoàn thành và nộp bài trước thời hạn: ' },
  { label: 'Lịch thi & Kiểm tra', title: 'Thông báo lịch thi/kiểm tra', content: 'Lịch thi/kiểm tra của lớp học phần: ' },
  { label: 'Thông báo lớp học phần', title: 'Thông báo lớp học phần', content: 'Nội dung thông báo: ' },
] as const;

export default function LecturerAnnouncementsPage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth(['LECTURER']);
  const { locale, formatDateTime } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<LecturerNoticeCategory>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [readingNotice, setReadingNotice] = useState<AnnouncementRecord | null>(null);
  const [isComposerOpen, setIsComposerOpen] = useState(false);

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu giảng viên',
          title: 'Thông báo Giảng dạy & Nghiên cứu',
          description:
            'Cổng thông báo chuyên trách dành cho Cán bộ giảng dạy về kế hoạch đào tạo, lịch coi thi, nhập điểm và nghiên cứu khoa học.',
          backToDashboard: 'Quay lại trang tổng quan giảng viên',
          refresh: 'Làm mới',
          loading: 'Đang tải thông báo',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Không tìm thấy thông báo phù hợp',
          emptyDescription:
            'Không có thông báo nào khớp với bộ lọc hoặc từ khóa tìm kiếm của Thầy/Cô.',
          recentNotices: 'Bảng Tin Thông Báo Chính Thức',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'Lớp học phần',
          loadFailed: 'Hiện chưa thể tải thông báo.',
          readFull: 'Đọc toàn văn',
          officialBadge: 'Khoa CNTT & Phòng Đào Tạo',
          searchPlaceholder: 'Tìm kiếm thông báo nghiệp vụ, khảo thí, đề tài...',
          filterAll: 'Tất cả thông báo',
          filterGlobal: '🌐 Thông báo toàn trường',
          filterLecturerOnly: '👨‍🏫 Nghiệp vụ Giảng viên',
          filterExamGrades: 'Khảo thí & Sổ điểm',
          filterResearch: 'NCKH & Đề cương',
        }
      : {
          eyebrow: 'Lecturer area',
          title: 'Teaching & Research Notices',
          description:
            'Dedicated announcement board for faculty members regarding academic planning, exams, grading, and scientific research.',
          backToDashboard: 'Back to lecturer dashboard',
          refresh: 'Refresh',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No matching notices found',
          emptyDescription:
            'There are no announcements matching your selected category or search keywords.',
          recentNotices: 'Official Notice Feed',
          semesterPrefix: 'Semester',
          sectionPrefix: 'Class',
          loadFailed: 'Announcements could not be loaded right now.',
          readFull: 'Read full notice',
          officialBadge: 'Faculty of IT & Academic Office',
          searchPlaceholder: 'Search teaching notices, exams, research...',
          filterAll: 'All notices',
          filterGlobal: '🌐 Campus-wide',
          filterLecturerOnly: '👨‍🏫 Faculty dedicated',
          filterExamGrades: 'Exams & Gradebook',
          filterResearch: 'Research & Syllabus',
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

  const filteredNotices = useMemo(() => {
    return orderedItems.filter((item) => {
      if (selectedCategory === 'GLOBAL') {
        const type = announcementAudienceType(item);
        if (type !== 'GLOBAL') return false;
      } else if (selectedCategory === 'LECTURER_ONLY') {
        const type = announcementAudienceType(item);
        if (type !== 'LECTURER') return false;
      } else if (selectedCategory === 'EXAM_GRADES') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (
          !text.includes('coi thi') &&
          !text.includes('đề thi') &&
          !text.includes('điểm') &&
          !text.includes('khảo thí') &&
          !text.includes('exam')
        )
          return false;
      } else if (selectedCategory === 'RESEARCH_SYLLABUS') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (
          !text.includes('nckh') &&
          !text.includes('nghiên cứu') &&
          !text.includes('đề tài') &&
          !text.includes('đề cương') &&
          !text.includes('abet') &&
          !text.includes('syllabus')
        )
          return false;
      }

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const fullText = (item.title + ' ' + item.content).toLowerCase();
        return fullText.includes(q);
      }

      return true;
    });
  }, [orderedItems, selectedCategory, searchQuery]);

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
            <Button type="button" onClick={() => setIsComposerOpen(true)}>
              <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
              {locale === 'vi' ? 'Đăng thông báo cho sinh viên' : 'Post student announcement'}
            </Button>
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

      <LecturerAnnouncementCreateModal
        isOpen={isComposerOpen}
        onClose={() => setIsComposerOpen(false)}
        onSuccess={() => void fetchFeed()}
        lecturerName={user ? `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim() : undefined}
      />

      {/* Control Bar: Search & Category Tabs */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              { id: 'ALL', label: copy.filterAll },
              { id: 'GLOBAL', label: copy.filterGlobal },
              { id: 'LECTURER_ONLY', label: copy.filterLecturerOnly },
              { id: 'EXAM_GRADES', label: copy.filterExamGrades },
              { id: 'RESEARCH_SYLLABUS', label: copy.filterResearch },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setSelectedCategory(tab.id)}
              className={cn(
                'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors',
                selectedCategory === tab.id
                  ? 'bg-primary text-primary-foreground shadow-xs'
                  : 'bg-secondary/60 text-muted-foreground hover:bg-secondary hover:text-foreground',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={copy.searchPlaceholder}
            className="h-9 rounded-lg pl-9 text-xs"
          />
        </div>
      </div>

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchFeed()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : filteredNotices.length === 0 ? (
        <EmptyState
          icon={Bell}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <Button
              variant="outline"
              onClick={() => {
                setSelectedCategory('ALL');
                setSearchQuery('');
              }}
              className="rounded-lg"
            >
              {copy.filterAll}
            </Button>
          }
        />
      ) : (
        <Card variant="muted">
          <CardHeader>
            <CardTitle className="text-xl">{copy.recentNotices}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {filteredNotices.map((announcement) => {
              const semesterLabel = formatAnnouncementSemester(announcement, locale, copy.semesterPrefix);
              return (
                <article
                  key={announcement.id}
                  role="button"
                  tabIndex={0}
                  onClick={() => setReadingNotice(announcement)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      setReadingNotice(announcement);
                    }
                  }}
                  className="group cursor-pointer rounded-lg border border-border/70 bg-card p-5 shadow-xs transition-all hover:border-primary/60 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                >
                  <div className="space-y-3">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary border border-primary/20">
                        <GraduationCap className="h-3.5 w-3.5" />
                        {formatAnnouncementPublisher(announcement.publishedBy, locale, copy.officialBadge)}
                      </span>
                      <span className="inline-flex items-center gap-1 rounded-md bg-secondary px-2.5 py-0.5 text-xs font-semibold text-foreground border border-border/60">
                        {announcementAudienceBadge(announcement, locale).label}
                      </span>
                      <span
                        className={`rounded-md px-2.5 py-0.5 text-xs font-semibold ${statusToneClass(announcementPriorityTone(announcement.priority))}`}
                      >
                        {announcementPriorityLabel(announcement.priority, locale)}
                      </span>
                      {announcementIsUpdated(announcement) ? (
                        <span className="rounded-md bg-secondary px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
                          {locale === 'vi' ? 'Đã cập nhật' : 'Updated'}
                        </span>
                      ) : null}
                    </div>
                    <h2 className="text-lg font-semibold text-foreground group-hover:text-primary transition-colors">
                      {announcement.title}
                    </h2>
                    <div className="line-clamp-3 max-w-3xl text-sm leading-7 text-muted-foreground prose prose-sm dark:prose-invert">
                      <RichContentRenderer content={announcement.content} />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-3 pt-1 border-t border-border/50">
                      <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                        {semesterLabel ? (
                          <span className="rounded bg-secondary/70 px-2 py-0.5 font-medium">
                            {semesterLabel}
                          </span>
                        ) : null}
                        {announcementSectionLabel(announcement) ? (
                          <span className="rounded bg-secondary/70 px-2 py-0.5 font-medium">
                            {copy.sectionPrefix}: {announcementSectionLabel(announcement)}
                          </span>
                        ) : null}
                        <span className="inline-flex items-center gap-1">
                          <Calendar className="h-3 w-3 text-primary" />
                          {formatDateTime(announcement.publishAt || announcement.createdAt)}
                        </span>
                      </div>

                      <span className="inline-flex items-center gap-1.5 text-xs font-bold text-primary group-hover:underline">
                        <BookOpen className="h-3.5 w-3.5" />
                        {copy.readFull}
                        <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" />
                      </span>
                    </div>
                  </div>
                </article>
              );
            })}
          </CardContent>
        </Card>
      )}

      {/* Official Stationery Reader Modal */}
      <AnnouncementReaderModal
        announcement={readingNotice}
        isOpen={Boolean(readingNotice)}
        onClose={() => setReadingNotice(null)}
        allAnnouncements={items}
        onSelectAnnouncement={(ann) => setReadingNotice(ann)}
      />
    </div>
  );
}
