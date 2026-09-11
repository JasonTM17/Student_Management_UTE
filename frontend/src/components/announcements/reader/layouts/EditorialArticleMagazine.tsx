'use client';

import React from 'react';
import Image from 'next/image';
import {
  Calendar,
  Clock,
  Share2,
  Sparkles,
  Building2,
  Users,
  Award,
  FlaskConical,
  Briefcase,
  BookOpen,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AnnouncementRecord } from '@/lib/api';
import type { Locale } from '@/i18n/config';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import {
  calculateReadingTime,
  extractAnnouncementExcerpt,
  extractCoverImage,
  formatAnnouncementPublisher,
  formatRelativeTime,
  resolveAnnouncementDomain,
} from '@/lib/announcement-presentation';
import { DocumentAttachmentsList } from '../DocumentAttachmentsList';
import { TableOfContents } from '../TableOfContents';
import { RelatedAnnouncements } from '../RelatedAnnouncements';
import type { ReadingPreferences } from '../ReadingToolbar';

interface EditorialArticleMagazineProps {
  announcement: AnnouncementRecord;
  preferences: ReadingPreferences;
  relatedAnnouncements?: AnnouncementRecord[];
  onSelectAnnouncement?: (announcement: AnnouncementRecord) => void;
  locale?: Locale;
}

export function EditorialArticleMagazine({
  announcement,
  preferences,
  relatedAnnouncements = [],
  onSelectAnnouncement,
  locale = 'vi',
}: EditorialArticleMagazineProps) {
  const isVi = locale === 'vi';
  const domain = resolveAnnouncementDomain(announcement, locale);
  const readingTime = calculateReadingTime(announcement.content);
  const coverImage = extractCoverImage(announcement.content);
  const sapo = extractAnnouncementExcerpt(announcement.content, 220);
  const publisher = formatAnnouncementPublisher(announcement.publishedBy, locale);
  const relativeTime = formatRelativeTime(announcement.publishAt || announcement.createdAt, locale);

  const getCategoryIcon = () => {
    switch (domain.iconType) {
      case 'flask':
        return <FlaskConical className="h-4 w-4" />;
      case 'award':
        return <Award className="h-4 w-4" />;
      case 'briefcase':
        return <Briefcase className="h-4 w-4" />;
      default:
        return <BookOpen className="h-4 w-4" />;
    }
  };

  const getFontSizeClass = () => {
    switch (preferences.fontSize) {
      case 'sm':
        return 'text-sm';
      case 'lg':
        return 'text-lg';
      case 'xl':
        return 'text-xl';
      default:
        return 'text-base';
    }
  };

  return (
    <article
      className={cn(
        'mx-auto max-w-4xl space-y-8 p-4 sm:p-8 transition-colors duration-200',
        preferences.fontFamily === 'serif' ? 'font-serif' : 'font-sans',
        preferences.theme === 'sepia'
          ? 'bg-[#FBF0D9] text-[#2D2A26] rounded-2xl shadow-sm'
          : preferences.theme === 'dark'
            ? 'bg-slate-900 text-slate-100 rounded-2xl shadow-sm'
            : 'bg-card text-foreground rounded-2xl shadow-sm border border-border/70',
      )}
    >
      {/* 1. Category Pill & Reading Time Header */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border/50 pb-4">
        <div className="flex items-center gap-2">
          <span
            className={cn(
              'inline-flex items-center gap-1.5 rounded-full px-3.5 py-1 text-xs font-bold uppercase tracking-wider',
              domain.categoryTone === 'info' && 'bg-blue-500/10 text-blue-600 dark:text-blue-400 border border-blue-500/20',
              domain.categoryTone === 'success' && 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border border-emerald-500/20',
              domain.categoryTone === 'warning' && 'bg-amber-500/10 text-amber-600 dark:text-amber-400 border border-amber-500/20',
              domain.categoryTone === 'primary' && 'bg-indigo-500/10 text-indigo-600 dark:text-indigo-400 border border-indigo-500/20',
              domain.categoryTone === 'danger' && 'bg-rose-500/10 text-rose-600 dark:text-rose-400 border border-rose-500/20',
            )}
          >
            {getCategoryIcon()}
            <span>{domain.categoryLabel}</span>
          </span>

          {announcement.priority === 'HIGH' || announcement.priority === 'URGENT' ? (
            <span className="inline-flex items-center gap-1 rounded-full bg-rose-500/15 px-3 py-1 text-xs font-bold text-rose-600 dark:text-rose-400 border border-rose-500/30">
              <Sparkles className="h-3.5 w-3.5" />
              <span>{isVi ? 'Tiêu điểm' : 'Featured'}</span>
            </span>
          ) : null}
        </div>

        <div className="flex items-center gap-3 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1 font-medium">
            <Clock className="h-3.5 w-3.5 text-primary" />
            <span>{readingTime.displayText}</span>
          </span>
          <span>•</span>
          <span className="font-medium">{relativeTime}</span>
        </div>
      </div>

      {/* 2. Headline */}
      <h1 className="text-2xl sm:text-3xl lg:text-4xl font-extrabold tracking-tight text-foreground leading-[1.2]">
        {announcement.title}
      </h1>

      {/* 3. Byline Bar */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-y border-border/50 py-3.5">
        <div className="flex items-center gap-3">
          <div className="relative h-10 w-10 overflow-hidden rounded-full border border-border/80 bg-background p-0.5 shadow-xs">
            <Image
              src="/hcmute-logo.png"
              alt="HCMUTE Emblem"
              width={38}
              height={38}
              className="h-full w-full object-contain"
            />
          </div>
          <div>
            <p className="text-xs font-bold text-foreground">
              {publisher}
            </p>
            <p className="text-[11px] text-muted-foreground">
              {isVi ? 'Trường Đại học Công nghệ Kỹ thuật TP.HCM' : 'Ho Chi Minh City University of Technology and Engineering'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Calendar className="h-3.5 w-3.5 text-primary" />
          <span>
            {new Date(announcement.publishAt || announcement.createdAt).toLocaleDateString(
              isVi ? 'vi-VN' : 'en-US',
              {
                weekday: 'long',
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              },
            )}
          </span>
        </div>
      </div>

      {/* 4. Hero Banner (16:9 Cover Image or Editorial Graphic) */}
      {coverImage ? (
        <div className="relative aspect-video w-full overflow-hidden rounded-xl border border-border/60 shadow-md">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={coverImage}
            alt={announcement.title}
            className="h-full w-full object-cover"
          />
        </div>
      ) : (
        <div
          className={cn(
            'relative aspect-[21/9] w-full overflow-hidden rounded-xl bg-gradient-to-r p-6 sm:p-10 text-white shadow-md flex flex-col justify-end',
            domain.accentGradient,
          )}
        >
          <div className="absolute inset-0 bg-black/20 backdrop-blur-[2px]" />
          <div className="relative z-10 space-y-2">
            <span className="inline-block rounded-md bg-white/20 px-2.5 py-1 text-[11px] font-bold uppercase tracking-widest backdrop-blur-md">
              {domain.categoryLabel}
            </span>
            <h2 className="text-lg sm:text-2xl font-bold tracking-tight text-white/95 line-clamp-2">
              {announcement.title}
            </h2>
          </div>
        </div>
      )}

      {/* 5. Sapo Lead Excerpt */}
      {sapo && (
        <div className="rounded-xl border-l-4 border-primary bg-primary/[0.04] p-4 sm:p-5 text-base sm:text-lg font-medium italic leading-relaxed text-foreground/90">
          « {sapo} »
        </div>
      )}

      {/* 6. Layout Grid: Table of Contents + Article Body */}
      <div className="grid grid-cols-1 gap-8 lg:grid-cols-4 items-start">
        {/* Main Article Content (3 columns on large screens) */}
        <div className="lg:col-span-3 space-y-6">
          <div
            className={cn(
              'leading-relaxed text-foreground/90 transition-all',
              getFontSizeClass(),
            )}
          >
            <RichContentRenderer content={announcement.content} />
          </div>

          {/* Official Attached Documents */}
          <DocumentAttachmentsList content={announcement.content} locale={locale} />
        </div>

        {/* Floating Sidebar: Table of Contents (1 column on large screens) */}
        <aside className="lg:col-span-1 lg:sticky lg:top-16 space-y-4">
          <TableOfContents content={announcement.content} locale={locale} />
        </aside>
      </div>

      {/* 7. Related Articles Footer */}
      {relatedAnnouncements.length > 0 && onSelectAnnouncement && (
        <RelatedAnnouncements
          currentId={announcement.id}
          items={relatedAnnouncements}
          onSelectAnnouncement={onSelectAnnouncement}
          locale={locale}
        />
      )}
    </article>
  );
}
