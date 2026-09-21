'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  ArrowRight,
  Award,
  BookOpen,
  Briefcase,
  ChevronDown,
  ChevronUp,
  Clock,
  FlaskConical,
  Newspaper,
  Sparkles,
  Users,
  Loader2,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import { AnnouncementFeedCard } from '@/components/announcements/feed/AnnouncementFeedCard';
import { SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';
import { useArticleTaxonomy } from '@/lib/use-article-taxonomy';
import { cn } from '@/lib/utils';
import {
  resolveAnnouncementDomain,
  resolveArticleCover,
  calculateReadingTime,
  extractAnnouncementExcerpt,
  formatAnnouncementPublisher,
  formatRelativeTime,
  type ArticleCategoryCode,
} from '@/lib/announcement-presentation';

type CategoryFilter = 'ALL' | ArticleCategoryCode;

interface CategoryTab {
  key: CategoryFilter;
  labelVi: string;
  labelEn: string;
  icon: React.ReactNode;
}

const CATEGORY_TABS: CategoryTab[] = [
  {
    key: 'ALL',
    labelVi: 'Tất cả bài viết',
    labelEn: 'All Stories',
    icon: <Newspaper className="h-4 w-4" />,
  },
  {
    key: 'RESEARCH_TECH',
    labelVi: 'Nghiên cứu & Công nghệ',
    labelEn: 'Research & Tech',
    icon: <FlaskConical className="h-4 w-4" />,
  },
  {
    key: 'AWARDS_HONORS',
    labelVi: 'Học bổng & Khen thưởng',
    labelEn: 'Scholarships & Honors',
    icon: <Award className="h-4 w-4" />,
  },
  {
    key: 'STUDENT_LIFE',
    labelVi: 'Đời sống Sinh viên',
    labelEn: 'Student Life',
    icon: <Users className="h-4 w-4" />,
  },
  {
    key: 'CULTURE_ARTS',
    labelVi: 'Văn hóa & Nghệ thuật',
    labelEn: 'Culture & Arts',
    icon: <Sparkles className="h-4 w-4" />,
  },
  {
    key: 'ACADEMIC_AFFAIRS',
    labelVi: 'Đào tạo & Học vụ',
    labelEn: 'Academic Affairs',
    icon: <BookOpen className="h-4 w-4" />,
  },
  {
    key: 'CAREER_OPPORTUNITIES',
    labelVi: 'Việc làm & Doanh nghiệp',
    labelEn: 'Career & Industry',
    icon: <Briefcase className="h-4 w-4" />,
  },
];

export function HomeNewsSection() {
  const { user } = useAuth();
  const { locale } = useI18n();
  const { categories } = useArticleTaxonomy();
  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [selectedNotice, setSelectedNotice] = useState<AnnouncementRecord | null>(null);
  const [activeCategory, setActiveCategory] = useState<CategoryFilter>('ALL');
  const [isExpanded, setIsExpanded] = useState(false);

  const isVi = locale === 'vi';

  useEffect(() => {
    let isMounted = true;
    setIsLoading(true);
    const fetcher = user
      ? announcementsApi.getMy({ page: 1, limit: 18 })
      : announcementsApi.getPublic({ page: 1, limit: 18 });

    fetcher
      .then((res) => {
        if (isMounted) {
          if (res.data && Array.isArray(res.data)) {
            setItems(res.data);
          }
          setIsLoading(false);
        }
      })
      .catch(() => {
        if (isMounted) {
          setIsLoading(false);
        }
      });
    return () => {
      isMounted = false;
    };
  }, [user]);

  // Filter items by active category
  const filteredItems = useMemo(() => {
    if (activeCategory === 'ALL') {
      return items;
    }
    return items.filter((item) => {
      const domain = resolveAnnouncementDomain(item, locale, categories);
      return domain.categoryCode === activeCategory;
    });
  }, [items, activeCategory, locale, categories]);

  // Featured and secondary streams
  const featured = filteredItems[0] || null;
  const secondaryList = filteredItems.slice(1, 4);
  const galleryGrid = filteredItems.slice(4);
  const visibleGridItems = isExpanded ? galleryGrid : galleryGrid.slice(0, 6);

  return (
    <section className="border-t border-border/70 bg-secondary/15 py-12 sm:py-16">
      <div className="mx-auto max-w-[1280px] px-4 sm:px-6 lg:px-12 space-y-8">
        {/* Section Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div className="space-y-1.5">
            <SectionEyebrow>
              {isVi ? 'TẠP CHÍ & BẢN TIN ĐẠI HỌC' : 'CAMPUS PRESS & MAGAZINE'}
            </SectionEyebrow>
            <h2 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-foreground">
              {isVi ? 'Nhịp Sống Học Thuật & Sáng Tạo HCMUTE' : 'HCMUTE Academic Life & Innovation'}
            </h2>
            <p className="text-sm text-muted-foreground max-w-2xl">
              {isVi
                ? 'Thông báo học vụ, sự kiện học thuật, thành tựu nghiên cứu khoa học và chính sách đào tạo chính thức từ Trường Đại học Công nghệ Kỹ thuật TP.HCM.'
                : 'Official academic announcements, research breakthroughs, student initiatives, and university governance updates.'}
            </p>
          </div>

          <LocalizedLink
            href="/dashboard/announcements"
            className="group inline-flex min-h-8 items-center gap-1.5 font-bold text-sm text-primary hover:underline self-start sm:self-end"
          >
            <span>{isVi ? 'Xem tất cả thông báo' : 'View all announcements'}</span>
            <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
          </LocalizedLink>
        </div>

        {/* Category Navigation Pills */}
        <div className="flex flex-wrap items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
          {CATEGORY_TABS.map((tab) => {
            const count =
              tab.key === 'ALL'
                ? items.length
                : items.filter(
                    (item) => resolveAnnouncementDomain(item, locale, categories).categoryCode === tab.key,
                  ).length;
            const isActive = activeCategory === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => {
                  setActiveCategory(tab.key);
                  setIsExpanded(false);
                }}
                className={cn(
                  'inline-flex items-center gap-2 rounded-md px-4 py-2 text-xs font-semibold transition-all shadow-2xs cursor-pointer border',
                  isActive
                    ? 'bg-primary text-primary-foreground border-primary shadow-sm ring-2 ring-primary/20'
                    : 'bg-card text-muted-foreground border-border/80 hover:bg-accent hover:text-foreground',
                )}
              >
                {tab.icon}
                <span>{isVi ? tab.labelVi : tab.labelEn}</span>
                <span
                  className={cn(
                    'rounded-md px-1.5 py-0.2 text-[10px] font-bold',
                    isActive ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-muted text-muted-foreground',
                  )}
                >
                  {count}
                </span>
              </button>
            );
          })}
        </div>

        {/* Loading State */}
        {isLoading && (
          <div className="flex h-64 items-center justify-center rounded-2xl border border-dashed border-border/80 bg-card/40">
            <div className="flex flex-col items-center gap-3 text-muted-foreground">
              <Loader2 className="h-8 w-8 animate-spin text-primary" />
              <p className="text-sm font-medium">
                {isVi ? 'Đang tải bản tin từ hệ thống...' : 'Loading campus announcements...'}
              </p>
            </div>
          </div>
        )}

        {/* Empty State */}
        {!isLoading && items.length === 0 && (
          <div className="rounded-2xl border border-dashed border-border/80 bg-card/60 p-12 text-center">
            <Newspaper className="mx-auto h-12 w-12 text-muted-foreground/40 mb-3" />
            <h3 className="text-base font-semibold text-foreground">
              {isVi ? 'Chưa có bản tin nào được công bố' : 'No announcements published yet'}
            </h3>
            <p className="mt-1 text-sm text-muted-foreground max-w-md mx-auto">
              {isVi
                ? 'Các thông báo học thuật và tin tức chính thức của Nhà trường sẽ hiển thị tại đây ngay khi được phát hành.'
                : 'Official academic announcements and institutional news will appear here once published.'}
            </p>
          </div>
        )}

        {/* Primary News Stage (Featured + Sidebar) */}
        {!isLoading && featured && (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 items-stretch">
            {/* Featured Article (7 columns on desktop) */}
            <div className="lg:col-span-7 flex flex-col">
              <AnnouncementFeedCard
                announcement={featured}
                onClick={() => setSelectedNotice(featured)}
                variant="featured"
                layout="vertical"
                locale={locale}
                className="h-full"
              />
            </div>

            {/* Secondary News Stream (5 columns on desktop) */}
            <div className="lg:col-span-5 flex flex-col justify-between gap-4">
              {secondaryList.map((ann) => (
                <AnnouncementFeedCard
                  key={ann.id}
                  announcement={ann}
                  onClick={() => setSelectedNotice(ann)}
                  variant="standard"
                  locale={locale}
                />
              ))}
              {secondaryList.length === 0 && (
                <div className="flex h-full min-h-[280px] items-center justify-center rounded-2xl border border-dashed border-border/80 bg-card/50 p-6 text-center text-sm text-muted-foreground">
                  {isVi ? 'Không có thêm bài viết trong danh mục này' : 'No more stories in this category'}
                </div>
              )}
            </div>
          </div>
        )}

        {/* 3-Column Magazine Card Grid for Additional Stories */}
        {!isLoading && galleryGrid.length > 0 && (
          <div className="space-y-6 pt-4">
            <div className="flex items-center justify-between border-t border-border/60 pt-6">
              <h3 className="text-lg font-bold text-foreground flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-primary" />
                <span>{isVi ? 'Phóng sự & Chuyên đề Khác' : 'Special Feature Stories'}</span>
                <span className="text-xs font-normal text-muted-foreground">({galleryGrid.length} bài viết)</span>
              </h3>
            </div>

            <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {visibleGridItems.map((ann) => {
                const cover = resolveArticleCover(ann);
                const domain = resolveAnnouncementDomain(ann, locale, categories);
                const reading = calculateReadingTime(ann.content);
                const sapo = extractAnnouncementExcerpt(ann.content, 110);
                const pub = formatAnnouncementPublisher(ann.publishedBy, locale);
                const relTime = formatRelativeTime(ann.publishAt || ann.createdAt, locale);

                return (
                  <article
                    key={ann.id}
                    onClick={() => setSelectedNotice(ann)}
                    className="group flex flex-col overflow-hidden rounded-2xl border border-border/70 bg-card shadow-2xs transition-all duration-300 hover:-translate-y-1 hover:border-primary/50 hover:shadow-lg cursor-pointer"
                  >
                    {/* 16:9 Thumbnail */}
                    <div className="relative aspect-video w-full overflow-hidden bg-muted">
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        src={cover}
                        alt={ann.title}
                        loading="lazy"
                        className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                      />
                      <div className="absolute top-2.5 left-2.5 z-10">
                        <span className="inline-flex items-center gap-1 rounded-md bg-background/90 px-2 py-0.5 text-[11px] font-bold text-foreground backdrop-blur-md shadow-2xs">
                          {domain.categoryLabel}
                        </span>
                      </div>
                    </div>

                    {/* Card Content */}
                    <div className="flex flex-1 flex-col justify-between p-5 space-y-3">
                      <div className="space-y-2">
                        <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                          <span className="font-semibold text-primary">{pub}</span>
                          <span>{relTime}</span>
                        </div>
                        <h4 className="font-bold text-sm sm:text-base leading-snug line-clamp-2 text-foreground group-hover:text-primary transition-colors">
                          {ann.title}
                        </h4>
                        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
                          {sapo}
                        </p>
                      </div>

                      <div className="flex items-center justify-between border-t border-border/50 pt-3 text-[11px] text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3 text-muted-foreground" />
                          <span>{reading.displayText}</span>
                        </span>
                        <span className="font-bold text-primary inline-flex items-center gap-1 group-hover:translate-x-0.5 transition-transform">
                          <span>{isVi ? 'Đọc bài' : 'Read'}</span>
                          <ArrowRight className="h-3 w-3" />
                        </span>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>

            {/* Expand / Collapse Button */}
            {galleryGrid.length > 6 && (
              <div className="flex justify-center pt-2">
                <button
                  onClick={() => setIsExpanded(!isExpanded)}
                  className="inline-flex items-center gap-2 rounded-md border border-border/80 bg-card px-6 py-2.5 text-xs font-bold text-foreground shadow-xs hover:bg-accent transition-colors cursor-pointer"
                >
                  {isExpanded ? (
                    <>
                      <span>{isVi ? 'Thu gọn bài viết' : 'Show Less'}</span>
                      <ChevronUp className="h-4 w-4" />
                    </>
                  ) : (
                    <>
                      <span>{isVi ? `Xem thêm ${galleryGrid.length - 6} bài viết khác` : `View ${galleryGrid.length - 6} More Stories`}</span>
                      <ChevronDown className="h-4 w-4" />
                    </>
                  )}
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Embedded Announcement Reader Modal */}
      <AnnouncementReaderModal
        announcement={selectedNotice}
        isOpen={Boolean(selectedNotice)}
        onClose={() => setSelectedNotice(null)}
        allAnnouncements={items}
        onSelectAnnouncement={(ann) => setSelectedNotice(ann)}
      />
    </section>
  );
}
