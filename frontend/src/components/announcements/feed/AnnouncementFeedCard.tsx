'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import {
  ArrowRight,
  Award,
  BookOpen,
  Briefcase,
  Calendar,
  Check,
  Clock,
  FileText,
  Flame,
  FlaskConical,
  Share2,
  Sparkles,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AnnouncementRecord } from '@/lib/api';
import type { Locale } from '@/i18n/config';
import {
  calculateReadingTime,
  extractAnnouncementExcerpt,
  extractCoverImage,
  formatAnnouncementPublisher,
  formatRelativeTime,
  resolveAnnouncementDomain,
} from '@/lib/announcement-presentation';

interface AnnouncementFeedCardProps {
  announcement: AnnouncementRecord;
  onClick: () => void;
  locale?: Locale;
  className?: string;
  variant?: 'standard' | 'featured';
}

export function AnnouncementFeedCard({
  announcement,
  onClick,
  locale = 'vi',
  className,
  variant = 'standard',
}: AnnouncementFeedCardProps) {
  const isVi = locale === 'vi';
  const [copied, setCopied] = useState(false);

  const domain = resolveAnnouncementDomain(announcement, locale);
  const readingTime = calculateReadingTime(announcement.content);
  const coverImage = extractCoverImage(announcement.content);
  const sapo = extractAnnouncementExcerpt(
    announcement.content,
    variant === 'featured' ? 240 : 130,
  );
  const publisher = formatAnnouncementPublisher(announcement.publishedBy, locale);
  const relativeTime = formatRelativeTime(
    announcement.publishAt || announcement.createdAt,
    locale,
  );
  const isUrgent = announcement.priority === 'HIGH' || announcement.priority === 'URGENT';

  const handleShare = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      if (typeof window !== 'undefined') {
        const url = `${window.location.origin}/dashboard/announcements?id=${encodeURIComponent(announcement.id)}`;
        await navigator.clipboard.writeText(url);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const getCategoryIcon = () => {
    switch (domain.iconType) {
      case 'flask':
        return <FlaskConical className="h-3.5 w-3.5" />;
      case 'award':
        return <Award className="h-3.5 w-3.5" />;
      case 'briefcase':
        return <Briefcase className="h-3.5 w-3.5" />;
      default:
        return <BookOpen className="h-3.5 w-3.5" />;
    }
  };

  if (variant === 'featured') {
    return (
      <article
        onClick={onClick}
        className={cn(
          'group relative flex flex-col md:flex-row overflow-hidden rounded-2xl border border-border/70 bg-card shadow-sm transition-all hover:border-primary/50 hover:shadow-lg cursor-pointer focus-within:ring-2 focus-within:ring-primary',
          className,
        )}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onClick();
          }
        }}
      >
        {/* Featured 16:9 Left Banner */}
        <div className="relative aspect-video w-full md:w-1/2 overflow-hidden bg-muted">
          {coverImage ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={coverImage}
              alt={announcement.title}
              className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
              loading="lazy"
            />
          ) : (
            <div
              className={cn(
                'h-full w-full bg-gradient-to-br p-8 text-white flex flex-col justify-between relative overflow-hidden',
                domain.accentGradient,
              )}
            >
              <div className="absolute -right-8 -bottom-8 opacity-15">
                <Image
                  src="/hcmute-logo.png"
                  alt="HCMUTE Emblem Watermark"
                  width={220}
                  height={220}
                />
              </div>
              <div className="relative z-10 flex items-center gap-2">
                <span className="rounded-full bg-white/20 px-3 py-1 text-xs font-bold uppercase tracking-wider backdrop-blur-md">
                  {domain.categoryLabel}
                </span>
              </div>
              <div className="relative z-10">
                <p className="text-xl font-bold leading-snug line-clamp-3 text-white/95">
                  {announcement.title}
                </p>
              </div>
            </div>
          )}

          {/* Badge Overlay */}
          <div className="absolute top-3 left-3 z-10 flex items-center gap-1.5">
            <span className="inline-flex items-center gap-1 rounded-md bg-background/90 px-2.5 py-1 text-xs font-bold text-foreground backdrop-blur-md shadow-xs">
              <Sparkles className="h-3.5 w-3.5 text-amber-500" />
              <span>{isVi ? 'Bài viết tiêu điểm' : 'Featured'}</span>
            </span>
            {isUrgent && (
              <span className="inline-flex items-center gap-1 rounded-md bg-rose-600 px-2 py-1 text-xs font-bold text-white shadow-xs">
                <Flame className="h-3 w-3" />
                <span>{isVi ? 'Khẩn' : 'Urgent'}</span>
              </span>
            )}
          </div>
        </div>

        {/* Featured Right Content */}
        <div className="flex flex-1 flex-col justify-between p-6 sm:p-8">
          <div className="space-y-3">
            <div className="flex items-center gap-3 text-xs text-muted-foreground">
              <span className="inline-flex items-center gap-1 font-semibold text-primary">
                {getCategoryIcon()}
                <span>{domain.categoryLabel}</span>
              </span>
              <span>•</span>
              <span className="flex items-center gap-1">
                <Clock className="h-3.5 w-3.5" />
                <span>{readingTime.minutes} {isVi ? 'phút đọc' : 'min read'}</span>
              </span>
              <span>•</span>
              <span>{relativeTime}</span>
            </div>

            <h3 className="text-xl sm:text-2xl font-bold text-foreground group-hover:text-primary transition-colors leading-snug">
              {announcement.title}
            </h3>

            {sapo && (
              <p className="text-sm text-muted-foreground leading-relaxed line-clamp-3">
                {sapo}
              </p>
            )}
          </div>

          <div className="mt-6 flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-4">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <div className="relative h-6 w-6 overflow-hidden rounded-full border border-border/80 bg-background">
                <Image
                  src="/hcmute-logo.png"
                  alt="HCMUTE"
                  width={24}
                  height={24}
                  className="h-full w-full object-contain"
                />
              </div>
              <span className="font-medium line-clamp-1">{publisher}</span>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleShare}
                className="inline-flex h-8 w-8 items-center justify-center rounded-lg border border-border/70 text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
                title={isVi ? 'Sao chép liên kết' : 'Copy link'}
                aria-label="Share"
              >
                {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Share2 className="h-3.5 w-3.5" />}
              </button>

              <span className="inline-flex items-center gap-1 rounded-lg bg-primary/10 px-3.5 py-1.5 text-xs font-bold text-primary group-hover:bg-primary group-hover:text-primary-foreground transition-all">
                <span>{isVi ? 'Đọc toàn văn' : 'Read article'}</span>
                <ArrowRight className="h-3.5 w-3.5 group-hover:translate-x-0.5 transition-transform" />
              </span>
            </div>
          </div>
        </div>
      </article>
    );
  }

  // Standard Card
  return (
    <article
      onClick={onClick}
      className={cn(
        'group flex flex-col justify-between overflow-hidden rounded-xl border border-border/70 bg-card shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/50 hover:shadow-md cursor-pointer focus-within:ring-2 focus-within:ring-primary',
        className,
      )}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick();
        }
      }}
    >
      {/* 16:9 Card Thumbnail */}
      <div className="relative aspect-video w-full overflow-hidden bg-muted">
        {coverImage ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={coverImage}
            alt={announcement.title}
            className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
            loading="lazy"
          />
        ) : (
          <div
            className={cn(
              'h-full w-full bg-gradient-to-br p-5 text-white flex flex-col justify-between relative overflow-hidden',
              domain.accentGradient,
            )}
          >
            <div className="absolute -right-6 -bottom-6 opacity-15">
              <Image
                src="/hcmute-logo.png"
                alt="HCMUTE Emblem Watermark"
                width={140}
                height={140}
              />
            </div>
            <div className="relative z-10 flex items-center justify-between">
              <span className="rounded-full bg-white/20 px-2.5 py-0.5 text-[10.5px] font-bold uppercase tracking-wider backdrop-blur-md">
                {domain.categoryLabel}
              </span>
              {isUrgent && (
                <span className="rounded bg-red-600 px-2 py-0.5 text-[10px] font-bold text-white shadow-xs">
                  {isVi ? 'Ưu tiên cao' : 'Urgent'}
                </span>
              )}
            </div>
            <div className="relative z-10">
              <p className="text-sm font-bold leading-snug line-clamp-2 text-white/95">
                {announcement.title}
              </p>
            </div>
          </div>
        )}

        {/* Priority Badge on image */}
        {coverImage && isUrgent && (
          <div className="absolute top-2.5 right-2.5 z-10">
            <span className="inline-flex items-center gap-1 rounded bg-rose-600/90 px-2 py-0.5 text-[10.5px] font-bold text-white backdrop-blur-xs shadow-xs">
              <Flame className="h-3 w-3" />
              <span>{isVi ? 'Khẩn' : 'Urgent'}</span>
            </span>
          </div>
        )}
      </div>

      {/* Card Body */}
      <div className="flex flex-1 flex-col justify-between p-4 sm:p-5 space-y-3">
        <div className="space-y-2">
          <div className="flex items-center justify-between gap-2 text-[11.5px] text-muted-foreground">
            <span className="inline-flex items-center gap-1 font-semibold text-primary">
              {getCategoryIcon()}
              <span>{domain.categoryLabel}</span>
            </span>
            <span className="flex items-center gap-1">
              <Clock className="h-3 w-3" />
              <span>{readingTime.minutes} {isVi ? 'phút' : 'm'}</span>
            </span>
          </div>

          <h4 className="text-sm sm:text-base font-bold text-foreground group-hover:text-primary transition-colors leading-snug line-clamp-2">
            {announcement.title}
          </h4>

          {sapo && (
            <p className="text-xs text-muted-foreground leading-relaxed line-clamp-2">
              {sapo}
            </p>
          )}
        </div>

        {/* Card Footer */}
        <div className="flex items-center justify-between border-t border-border/50 pt-3 text-xs text-muted-foreground">
          <span className="line-clamp-1 text-[11px] font-medium">{relativeTime}</span>

          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={handleShare}
              className="inline-flex h-7 w-7 items-center justify-center rounded text-muted-foreground hover:text-foreground hover:bg-secondary transition-colors"
              title={isVi ? 'Sao chép liên kết' : 'Copy link'}
              aria-label="Share"
            >
              {copied ? <Check className="h-3 w-3 text-emerald-500" /> : <Share2 className="h-3 w-3" />}
            </button>

            <span className="inline-flex items-center gap-0.5 font-bold text-primary group-hover:translate-x-0.5 transition-transform text-xs">
              <span>{isVi ? 'Đọc bài' : 'Read'}</span>
              <ArrowRight className="h-3 w-3" />
            </span>
          </div>
        </div>
      </div>
    </article>
  );
}
