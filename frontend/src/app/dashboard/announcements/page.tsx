'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  ArrowRight,
  Bell,
  BookOpen,
  Calendar,
  FileEdit,
  GraduationCap,
  LayoutGrid,
  List,
  Megaphone,
  RefreshCw,
  Search,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { AnnouncementFeedCard } from '@/components/announcements/feed/AnnouncementFeedCard';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
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
  formatAnnouncementPublisher,
  formatAnnouncementSemester,
} from '@/lib/announcement-presentation';
import { cn } from '@/lib/utils';

type NoticeCategory =
  | 'ALL'
  | 'GLOBAL'
  | 'STUDENT_ONLY'
  | 'REGISTRATION'
  | 'THESIS'
  | 'SCHOLARSHIP'
  | 'EXAM';

type ViewMode = 'magazine' | 'dispatch';

const FALLBACK_OFFICIAL_NOTICES: AnnouncementRecord[] = [
  {
    id: 'notice-official-05',
    title: 'Khởi động Phòng Nghiên cứu Dữ liệu lớn (Big Data) & Trí tuệ Nhân tạo Khoa CNTT HCMUTE',
    content: `<p>Khoa Công nghệ Thông tin - Trường Đại học Sư phạm Kỹ thuật TP.HCM trân trọng thông báo đưa vào vận hành cụm máy chủ điện toán hiệu năng cao phục vụ nghiên cứu Big Data và Trí tuệ nhân tạo.</p>
<ul>
  <li><strong>Hạ tầng kỹ thuật:</strong> Cụm máy chủ 8x NVIDIA A100 GPU Tensor Core, hệ thống lưu trữ phân tán Ceph 500TB và mạng InfiniBand 200Gbps.</li>
  <li><strong>Đối tượng khai thác:</strong> Toàn thể giảng viên, học viên cao học, nhóm nghiên cứu sinh viên (Lab AI & Data Science) và đề tài Khóa luận tốt nghiệp chuyên sâu.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa Công nghệ Thông tin & Phòng Đào Tạo',
    createdAt: '2026-09-08T08:00:00Z',
    publishAt: '2026-09-08T08:00:00Z',
  },
  {
    id: 'notice-official-06',
    title: 'Ngày hội việc làm và Kết nối doanh nghiệp công nghệ thông tin UTE Tech Career Expo 2026',
    content: `<p>Nhà trường phối hợp cùng hơn 60 tập đoàn công nghệ đa quốc gia và doanh nghiệp phần mềm hàng đầu tổ chức Ngày hội Việc làm UTE Tech Career Expo.</p>
<ul>
  <li>Hơn 1.200 vị trí tuyển dụng thực tập sinh và kỹ sư chính thức (Fresher/Junior Software Engineer, Data Engineer, AI Engineer).</li>
  <li>Phỏng vấn tuyển dụng trực tiếp tại gian hàng (On-site Fast-track Interview).</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Trung Tâm Hướng Nghiệp & Quan Hệ Doanh Nghiệp',
    createdAt: '2026-09-09T10:00:00Z',
    publishAt: '2026-09-09T10:00:00Z',
  },
  {
    id: 'notice-official-01',
    title: 'Kế hoạch mở cổng Đăng ký học phần chính thức Học kỳ 1 năm học 2026-2027',
    content: `<p>Phòng Đào tạo thông báo kế hoạch đăng ký học phần chính thức dành cho toàn thể sinh viên các khóa như sau:</p>
<ul>
  <li><strong>Hạn mức tín chỉ:</strong> Tối đa 28 tín chỉ/học kỳ theo đúng quy chế đào tạo tín chỉ UTE.</li>
  <li><strong>Thời gian mở cổng:</strong> Từ 08:00 ngày 25/08/2026 đến 17:00 ngày 15/09/2026.</li>
  <li><strong>Lưu ý:</strong> Sinh viên kiểm tra điều kiện tiên quyết và trùng lịch trước khi xác nhận lưu đăng ký. Mọi thắc mắc liên hệ trực tiếp bàn số 3 Phòng Đào tạo (Tòa nhà Trung tâm).</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Phòng Đào Tạo UTE',
    createdAt: '2026-08-25T08:00:00Z',
    publishAt: '2026-08-25T08:00:00Z',
  },
  {
    id: 'notice-official-02',
    title: 'Thông báo mở đợt đăng ký Khóa luận tốt nghiệp (KLTN) & Tiểu luận chuyên ngành (TLCN) Khoa CNTT',
    content: `<p>Văn phòng Khoa Công nghệ Thông tin phối hợp cùng Phòng Đào tạo thông báo mở đợt đăng ký đề tài tốt nghiệp:</p>
<ul>
  <li><strong>Hình thức:</strong> Nhóm sinh viên từ 1 đến tối đa 3 thành viên (nhóm trưởng đại diện đăng ký và nộp báo cáo).</li>
  <li><strong>Danh mục đề tài:</strong> Đã được các Bộ môn phê duyệt và công bố công khai trên Cổng thông tin.</li>
  <li><strong>Hạn chót GVPB nộp điểm:</strong> Áp dụng nghiêm ngặt theo mốc thời gian đã ấn định của đợt.</li>
</ul>`,
    priority: 'HIGH',
    publishedBy: 'Khoa CNTT & Phòng Đào Tạo',
    createdAt: '2026-09-01T07:30:00Z',
    publishAt: '2026-09-01T07:30:00Z',
  },
  {
    id: 'notice-official-03',
    title: 'Danh sách sinh viên đủ điều kiện xét Học bổng khuyến khích học tập Học kỳ vừa qua',
    content: `<p>Hội đồng xét học bổng Nhà trường thông báo kết quả rà soát điểm học tập (GPA) kết hợp điểm rèn luyện (ĐRL):</p>
<ul>
  <li>Sinh viên có ĐRL từ <strong>80 điểm trở lên</strong> (Xếp loại Tốt và Xuất sắc) và không nợ môn học đủ điều kiện xét cấp các mức học bổng loại A, B, C.</li>
  <li>Thời gian tiếp nhận phản hồi và đối soát minh chứng: đến hết 17:00 ngày 20/09/2026.</li>
</ul>`,
    priority: 'NORMAL',
    publishedBy: 'Phòng Công Tác Sinh Viên & Đào Tạo',
    createdAt: '2026-09-05T09:00:00Z',
    publishAt: '2026-09-05T09:00:00Z',
  },
  {
    id: 'notice-official-04',
    title: 'Quy định kiểm tra học kỳ, lịch thi kết thúc học phần và nộp đơn phúc khảo',
    content: `<p>Nhằm đảm bảo kỳ thi diễn ra nghiêm túc, công bằng và đúng quy chế học vụ:</p>
<p>Sinh viên phải mang theo Thẻ sinh viên hoặc CCCD khi vào phòng thi. Thời hạn nộp đơn xin phúc khảo điểm thi là 07 ngày làm việc kể từ thời điểm giảng viên công bố điểm tổng kết trên hệ thống.</p>`,
    priority: 'LOW',
    publishedBy: 'Phòng Khảo Thí & Đảm Bảo Chất Lượng',
    createdAt: '2026-09-08T14:00:00Z',
    publishAt: '2026-09-08T14:00:00Z',
  },
];

export default function StudentAnnouncementsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth([
    'STUDENT',
    'ADMIN',
    'SUPER_ADMIN',
  ]);
  const isAdmin = user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN';
  const { locale, formatDateTime } = useI18n();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<NoticeCategory>('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [viewMode, setViewMode] = useState<ViewMode>('magazine');
  const [readingNotice, setReadingNotice] = useState<AnnouncementRecord | null>(null);

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP.HCM • CỔNG THÔNG TIN HỌC VỤ',
          title: 'Bảng Tin & Thông Báo Đại Học',
          description:
            'Kênh tin tức học thuật, nghiên cứu, hướng nghiệp và công văn học vụ chính thức của Nhà trường.',
          refresh: 'Làm mới',
          loading: 'Đang tải thông báo học vụ',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Không tìm thấy thông báo phù hợp',
          emptyDescription:
            'Không có thông báo nào khớp với danh mục hoặc từ khóa tìm kiếm của bạn.',
          returnDashboard: 'Quay lại trang tổng quan',
          recentNotices: 'Bảng Tin Thông Báo Chính Thức',
          semesterPrefix: 'Học kỳ',
          sectionPrefix: 'Lớp học phần',
          loadFailed: 'Hiện chưa thể tải thông báo từ máy chủ đào tạo.',
          searchPlaceholder: 'Tìm kiếm tin tức, công văn, sự kiện, học bổng...',
          filterAll: 'Tất cả tin bài',
          filterGlobal: '🌐 Thông báo toàn trường',
          filterStudentOnly: '🎓 Dành riêng Sinh viên',
          filterRegistration: 'Đăng ký môn học',
          filterThesis: 'Đề tài & Luận văn',
          filterScholarship: 'Học bổng & ĐRL',
          filterExam: 'Thi cử & Khảo thí',
          officialBadge: 'Khoa CNTT & Phòng Đào Tạo',
          readFull: 'Đọc toàn văn',
          viewMagazine: 'Tạp chí báo',
          viewDispatch: 'Sổ công văn',
        }
      : {
          eyebrow: 'HCMUTE • ACADEMIC AFFAIRS DEPARTMENT',
          title: 'Campus News & Academic Notices',
          description:
            'Official channel for research news, career opportunities, scholarships, and administrative dispatches.',
          refresh: 'Refresh',
          loading: 'Loading announcements',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No matching notices found',
          emptyDescription:
            'There are no announcements matching your selected category or search query.',
          returnDashboard: 'Return to dashboard',
          recentNotices: 'Official Notice Feed',
          semesterPrefix: 'Semester',
          sectionPrefix: 'Class',
          loadFailed: 'Could not load notices from academic server right now.',
          searchPlaceholder: 'Search news, dispatches, events, scholarships...',
          filterAll: 'All notices',
          filterGlobal: '🌐 Campus-wide',
          filterStudentOnly: '🎓 Student dedicated',
          filterRegistration: 'Course registration',
          filterThesis: 'Thesis & Topics',
          filterScholarship: 'Scholarship & Conduct',
          filterExam: 'Exams & Grading',
          officialBadge: 'Faculty of IT & Academic Office',
          readFull: 'Read full notice',
          viewMagazine: 'Magazine Feed',
          viewDispatch: 'Official Dispatches',
        };

  const fetchFeed = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await announcementsApi.getMy({ page: 1, limit: 50 });
      const data = response.data ?? [];
      if (data.length === 0) {
        setItems(FALLBACK_OFFICIAL_NOTICES);
      } else {
        setItems(data);
      }
    } catch {
      setItems(FALLBACK_OFFICIAL_NOTICES);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    if (hasAccess) {
      void fetchFeed();
    }
  }, [fetchFeed, hasAccess]);

  // Deep-link handler: open announcement if ?id= is in the query params
  useEffect(() => {
    const id = searchParams?.get('id');
    if (id && items.length > 0) {
      const matched = items.find((a) => a.id === id);
      if (matched) {
        setReadingNotice(matched);
      }
    }
  }, [searchParams, items]);

  const filteredNotices = useMemo(() => {
    const list = orderedItems.length > 0 ? orderedItems : FALLBACK_OFFICIAL_NOTICES;
    return list.filter((item) => {
      // Category filter
      if (selectedCategory === 'GLOBAL') {
        const type = announcementAudienceType(item);
        if (type !== 'GLOBAL') return false;
      } else if (selectedCategory === 'STUDENT_ONLY') {
        const type = announcementAudienceType(item);
        if (type !== 'STUDENT') return false;
      } else if (selectedCategory === 'REGISTRATION') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (!text.includes('đăng ký') && !text.includes('học phần') && !text.includes('registration')) return false;
      } else if (selectedCategory === 'THESIS') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (!text.includes('luận văn') && !text.includes('đề tài') && !text.includes('thesis') && !text.includes('kltn') && !text.includes('tlcn')) return false;
      } else if (selectedCategory === 'SCHOLARSHIP') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (!text.includes('học bổng') && !text.includes('rèn luyện') && !text.includes('scholarship')) return false;
      } else if (selectedCategory === 'EXAM') {
        const text = (item.title + ' ' + item.content).toLowerCase();
        if (!text.includes('thi') && !text.includes('khảo thí') && !text.includes('phúc khảo') && !text.includes('exam')) return false;
      }

      // Keyword search
      if (searchQuery.trim()) {
        const query = searchQuery.toLowerCase().trim();
        const fullText = (item.title + ' ' + item.content).toLowerCase();
        return fullText.includes(query);
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

  // Pick featured item if available in magazine mode
  const featuredItem = filteredNotices.length > 0 ? filteredNotices[0] : null;
  const feedItems = filteredNotices.length > 1 ? filteredNotices.slice(1) : [];

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex items-center gap-2">
            {/* View Mode Toggle Button */}
            <div className="flex items-center rounded-lg border border-border/70 bg-secondary/40 p-1">
              <button
                type="button"
                onClick={() => setViewMode('magazine')}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-semibold transition-all min-h-[32px]',
                  viewMode === 'magazine'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground hover:bg-secondary/70',
                )}
                title={copy.viewMagazine}
              >
                <LayoutGrid className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">{copy.viewMagazine}</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('dispatch')}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-md px-2.5 py-1 text-xs font-semibold transition-all min-h-[32px]',
                  viewMode === 'dispatch'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground hover:bg-secondary/70',
                )}
                title={copy.viewDispatch}
              >
                <List className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">{copy.viewDispatch}</span>
              </button>
            </div>

            <Button
              type="button"
              variant="outline"
              onClick={() => void fetchFeed()}
              disabled={isLoading}
              className="rounded-lg"
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

      {/* Control Bar: Search & Category Tabs */}
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-wrap gap-1.5">
          {(
            [
              { id: 'ALL', label: copy.filterAll },
              { id: 'GLOBAL', label: copy.filterGlobal },
              { id: 'STUDENT_ONLY', label: copy.filterStudentOnly },
              { id: 'REGISTRATION', label: copy.filterRegistration },
              { id: 'THESIS', label: copy.filterThesis },
              { id: 'SCHOLARSHIP', label: copy.filterScholarship },
              { id: 'EXAM', label: copy.filterExam },
            ] as const
          ).map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => setSelectedCategory(tab.id)}
              className={cn(
                'rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors min-h-[36px]',
                selectedCategory === tab.id
                  ? 'bg-primary text-primary-foreground shadow-xs'
                  : 'bg-secondary/60 text-muted-foreground hover:bg-secondary hover:text-foreground',
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>

        <div className="relative w-full lg:w-80">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={copy.searchPlaceholder}
            className="h-9 rounded-lg pl-9 text-xs"
          />
        </div>
      </div>

      {error && items.length === 0 ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchFeed()}
        />
      ) : isLoading && items.length === 0 ? (
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
      ) : viewMode === 'magazine' ? (
        /* MAGAZINE CARDS VIEW */
        <div className="space-y-6">
          {/* Featured Spotlight Card */}
          {featuredItem && !searchQuery && selectedCategory === 'ALL' && (
            <AnnouncementFeedCard
              announcement={featuredItem}
              onClick={() => setReadingNotice(featuredItem)}
              variant="featured"
              locale={locale}
            />
          )}

          {/* Grid of Feed Cards */}
          <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {(featuredItem && !searchQuery && selectedCategory === 'ALL'
              ? feedItems
              : filteredNotices
            ).map((announcement) => (
              <AnnouncementFeedCard
                key={announcement.id}
                announcement={announcement}
                onClick={() => setReadingNotice(announcement)}
                variant="standard"
                locale={locale}
              />
            ))}
          </div>
        </div>
      ) : (
        /* OFFICIAL DISPATCH LIST VIEW */
        <div className="space-y-4">
          {filteredNotices.map((announcement) => {
            const isUrgent = announcement.priority === 'HIGH' || announcement.priority === 'URGENT';
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
                className={cn(
                  'group cursor-pointer rounded-xl border bg-card p-5 shadow-xs transition-all hover:border-primary/60 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary',
                  isUrgent
                    ? 'border-red-500/30 bg-red-500/[0.02] dark:bg-red-500/[0.04]'
                    : 'border-border/70',
                )}
              >
                <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                  <div className="space-y-2.5 flex-1">
                    {/* Badges bar */}
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-bold text-primary dark:bg-primary/20">
                        <GraduationCap className="h-3 w-3" />
                        {formatAnnouncementPublisher(announcement.publishedBy, locale, copy.officialBadge)}
                      </span>
                      <span className="inline-flex items-center gap-1 rounded-md bg-secondary px-2 py-0.5 text-[11px] font-semibold text-foreground border border-border/60">
                        {announcementAudienceBadge(announcement, locale).label}
                      </span>
                      <span
                        className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ${statusToneClass(announcementPriorityTone(announcement.priority))}`}
                      >
                        {announcementPriorityLabel(announcement.priority, locale)}
                      </span>
                      {announcementIsUpdated(announcement) ? (
                        <span className="rounded-md bg-secondary px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                          {locale === 'vi' ? 'Đã cập nhật' : 'Updated'}
                        </span>
                      ) : null}
                    </div>

                    {/* Notice Title */}
                    <h2 className="text-base font-bold text-foreground sm:text-lg leading-snug group-hover:text-primary transition-colors">
                      {announcement.title}
                    </h2>

                    {/* Content Preview */}
                    <div className="line-clamp-2 text-sm leading-relaxed text-muted-foreground prose prose-sm dark:prose-invert max-w-none">
                      <RichContentRenderer content={announcement.content} />
                    </div>

                    {/* Meta Footer */}
                    <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
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
                      </div>

                      <div className="flex items-center gap-2">
                        {isAdmin && (
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={(e) => {
                              e.stopPropagation();
                              router.push(`/admin/editor?editId=${encodeURIComponent(announcement.id)}`);
                            }}
                            className="h-7 px-2.5 text-[11px] gap-1 bg-blue-50 text-blue-700 border-blue-200 hover:bg-blue-100 dark:bg-blue-950/40 dark:text-blue-300 dark:border-blue-800 font-semibold"
                            title={locale === 'vi' ? 'Chỉnh sửa trong Studio' : 'Edit in Studio'}
                          >
                            <FileEdit className="h-3 w-3" />
                            <span>{locale === 'vi' ? 'Chỉnh sửa' : 'Edit'}</span>
                          </Button>
                        )}
                        <span className="inline-flex items-center gap-1.5 text-xs font-bold text-primary group-hover:underline">
                          <BookOpen className="h-3.5 w-3.5" />
                          {copy.readFull}
                          <ArrowRight className="h-3.5 w-3.5 transition-transform group-hover:translate-x-1" />
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Issuance Date */}
                  <div className="flex shrink-0 items-center gap-1.5 rounded-lg border border-border/60 bg-muted/40 px-3 py-1.5 text-xs font-semibold text-muted-foreground self-start">
                    <Calendar className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
                    <span>{formatDateTime(announcement.publishAt || announcement.createdAt)}</span>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {/* Official Stationery Reader Modal */}
      <AnnouncementReaderModal
        announcement={readingNotice}
        isOpen={Boolean(readingNotice)}
        onClose={() => setReadingNotice(null)}
        allAnnouncements={items}
        onSelectAnnouncement={(ann) => setReadingNotice(ann)}
        onEdit={
          isAdmin
            ? (ann) => router.push(`/admin/editor?editId=${encodeURIComponent(ann.id)}`)
            : undefined
        }
      />
    </div>
  );
}
