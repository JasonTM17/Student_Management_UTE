'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { List, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  extractArticleHeadingsFromHtml,
  isHtmlDocument,
  resolveMarkdownHeading,
} from '@/components/ui/rich-content-renderer';
import type { Locale } from '@/i18n/config';

interface TocItem {
  id: string;
  text: string;
  level: number;
}

interface TableOfContentsProps {
  content: string | null | undefined;
  className?: string;
  locale?: Locale;
}

/**
 * RT-P3-a: TinyMCE bodies are HTML, so an author's `<h2>` produced no markdown
 * `##` line and the contents stayed empty. Headings are read the same way the
 * renderer anchors them (one shared slug/dedupe implementation in
 * `rich-content-renderer.tsx`), otherwise a row points at an id that was never
 * written to the DOM.
 */
export function extractTocHeadings(content: string | null | undefined): TocItem[] {
  if (!content || !content.trim()) return [];

  // The renderer sends an HTML body through the sanitizer (with injected
  // heading ids) and a plain body through the markdown converter; follow that
  // same fork here or the two disagree on what is a heading.
  if (isHtmlDocument(content)) return extractArticleHeadingsFromHtml(content);

  const items: TocItem[] = [];
  const usedHeadingIds = new Map<string, number>();
  let insideCodeFence = false;
  const lines = content.replace(/\r\n/g, '\n').split('\n');

  for (const line of lines) {
    const trimmed = line.trim();
    // The markdown renderer swallows fenced blocks, so their `#` comments are
    // source text, not sections.
    if (trimmed.startsWith('```')) {
      insideCodeFence = !insideCodeFence;
      continue;
    }
    if (insideCodeFence) continue;

    const match = /^(#{1,6})\s+(.+)$/.exec(trimmed);
    if (!match) continue;

    const heading = resolveMarkdownHeading(match[2].trim(), match[1].length, usedHeadingIds);
    if (heading) items.push(heading);
  }

  return items;
}

export function TableOfContents({
  content,
  className,
  locale = 'vi',
}: TableOfContentsProps) {
  // Recomputed only when the body changes: the reader re-renders on every
  // scroll frame, and a fresh array here would re-observe the whole document.
  const headings = useMemo(() => extractTocHeadings(content), [content]);
  const [activeId, setActiveId] = useState<string>('');
  const isVi = locale === 'vi';

  useEffect(() => {
    if (headings.length === 0) return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveId(entry.target.id);
            break;
          }
        }
      },
      {
        rootMargin: '-80px 0% -60% 0%',
        threshold: 0.1,
      },
    );

    for (const heading of headings) {
      const el = document.getElementById(heading.id);
      if (el) observer.observe(el);
    }

    return () => observer.disconnect();
  }, [headings]);

  if (headings.length < 2) {
    return null;
  }

  const handleScrollTo = (id: string) => {
    const element = document.getElementById(id);
    if (element) {
      // Carry focus to the section as well as the scroll: otherwise a keyboard or
      // screen-reader user who follows a contents link is left back on the link,
      // with the reading position moved out from under them.
      element.tabIndex = -1;
      element.focus({ preventScroll: true });
      element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      setActiveId(id);
    }
  };

  return (
    <nav
      aria-label={isVi ? 'Mục lục bài viết' : 'Table of contents'}
      className={cn(
        'rounded-xl border border-border/70 bg-secondary/30 p-4 print:hidden transition-all',
        className,
      )}
    >
      <div className="flex items-center gap-2 border-b border-border/60 pb-2.5 text-xs font-bold uppercase tracking-wider text-foreground">
        <List className="h-4 w-4 text-primary" />
        <span>{isVi ? 'Mục lục bài viết' : 'Table of Contents'}</span>
        <span className="ml-auto rounded-md bg-primary/10 px-2 py-0.5 text-[10px] font-semibold text-primary">
          {headings.length} {isVi ? 'phần' : 'sections'}
        </span>
      </div>

      <ul className="mt-3 space-y-1 text-xs">
        {headings.map((item) => {
          const isActive = activeId === item.id;
          return (
            <li
              key={item.id}
              style={{ paddingLeft: `${(item.level - 1) * 0.75}rem` }}
            >
              <button
                type="button"
                onClick={() => handleScrollTo(item.id)}
                className={cn(
                  'group flex w-full items-center gap-1.5 py-1 text-left transition-colors focus:outline-none focus:ring-1 focus:ring-primary rounded px-1.5',
                  isActive
                    ? 'font-bold text-primary bg-primary/10'
                    : 'text-muted-foreground hover:text-foreground hover:bg-secondary/60',
                )}
              >
                <ChevronRight
                  className={cn(
                    'h-3 w-3 shrink-0 transition-transform',
                    isActive ? 'text-primary translate-x-0.5' : 'text-muted-foreground/40 group-hover:text-muted-foreground',
                  )}
                />
                <span className="line-clamp-1">{item.text}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
