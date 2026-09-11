'use client';

import React from 'react';
import { cn } from '@/lib/utils';

interface ReadingProgressBarProps {
  progress: number; // 0 - 100
  className?: string;
  tone?: string;
}

export function ReadingProgressBar({
  progress,
  className,
  tone = 'from-blue-600 via-indigo-500 to-primary',
}: ReadingProgressBarProps) {
  const clamped = Math.min(100, Math.max(0, progress));

  return (
    <div
      role="progressbar"
      aria-valuenow={Math.round(clamped)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Tiến trình đọc bài viết"
      className={cn(
        'sticky top-0 z-30 h-1 w-full bg-border/40 overflow-hidden print:hidden',
        className,
      )}
    >
      <div
        className={cn('h-full bg-gradient-to-r transition-[width] duration-150 ease-out', tone)}
        style={{ width: `${clamped}%` }}
      />
    </div>
  );
}
