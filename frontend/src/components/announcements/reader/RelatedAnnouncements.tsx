'use client';

import React from 'react';
import { ArrowRight, Clock, Sparkles } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AnnouncementRecord } from '@/lib/api';
import type { Locale } from '@/i18n/config';
import {
  calculateReadingTime,
  extractAnnouncementExcerpt,
  formatRelativeTime,
  resolveAnnouncementDomain,
} from '@/lib/announcement-presentation';

interface RelatedAnnouncementsProps {
  currentId: string;
  items?: AnnouncementRecord[];
  onSelectAnnouncement: (announcement: AnnouncementRecord) => void;
  className?: string;
  locale?: Locale;
}

export function RelatedAnnouncements({
  currentId,
  items = [],
  onSelectAnnouncement,
  className,
  locale = 'vi',
}: RelatedAnnouncementsProps) {
  const isVi = locale === 'vi';

  // Filter out current announcement and pick up to 3
  const related = items
    .filter((a) => a.id !== currentId)
    .slice(0, 3);

  if (related.length === 0) {
    return null;
  }

  return (
    <div className={cn('border-t border-border/70 pt-6 print:hidden', className)}>
      <div className="flex items-center gap-2 mb-4">
        <Sparkles className="h-4 w-4 text-primary" />
        <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">
          {isVi ? 'Bài viết liên quan & Đáng chú ý' : 'Related & Notable Articles'}
        </h4>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        {related.map((ann) => {
          const domain = resolveAnnouncementDomain(ann, locale);
          const readingTime = calculateReadingTime(ann.content);
          const excerpt = extractAnnouncementExcerpt(ann.content, 90);
          const relativeTime = formatRelativeTime(ann.publishAt || ann.createdAt, locale);

          return (
            <div
              key={ann.id}
              onClick={() => onSelectAnnouncement(ann)}
              className="group flex flex-col justify-between rounded-xl border border-border/70 bg-card p-4 transition-all hover:border-primary/50 hover:shadow-md cursor-pointer"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === 'Enter' || e.key === ' ') {
                  e.preventDefault();
                  onSelectAnnouncement(ann);
                }
              }}
            >
              <div>
                <div className="flex items-center justify-between gap-2 mb-2">
                  <span
                    className={cn(
                      'rounded-full px-2 py-0.5 text-[10px] font-bold uppercase tracking-tight',
                      domain.domain === 'EDITORIAL_ARTICLE'
                        ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                        : 'bg-red-500/10 text-red-600 dark:text-red-400',
                    )}
                  >
                    {domain.categoryLabel}
                  </span>
                  <span className="flex items-center gap-1 text-[11px] text-muted-foreground">
                    <Clock className="h-3 w-3" />
                    {readingTime.minutes}m
                  </span>
                </div>

                <h5 className="line-clamp-2 text-xs font-bold text-foreground group-hover:text-primary transition-colors leading-snug">
                  {ann.title}
                </h5>

                {excerpt && (
                  <p className="line-clamp-2 mt-1.5 text-[11.5px] text-muted-foreground leading-relaxed">
                    {excerpt}
                  </p>
                )}
              </div>

              <div className="mt-3 flex items-center justify-between border-t border-border/50 pt-2 text-[11px] text-muted-foreground">
                <span>{relativeTime}</span>
                <span className="inline-flex items-center gap-1 font-semibold text-primary group-hover:translate-x-0.5 transition-transform">
                  <span>{isVi ? 'Đọc tiếp' : 'Read'}</span>
                  <ArrowRight className="h-3 w-3" />
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
