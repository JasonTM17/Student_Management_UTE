import * as React from 'react';
import { cn } from '@/lib/utils';

export interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
}

/**
 * Base atomic skeleton primitive with pulse animation and neutral muted styling.
 */
export function Skeleton({ className, ...props }: SkeletonProps) {
  return (
    <div
      aria-hidden="true"
      className={cn('animate-pulse rounded-md bg-muted/60', className)}
      {...props}
    />
  );
}

export interface TableSkeletonProps {
  rows?: number;
  cols?: number;
  showHeader?: boolean;
  className?: string;
}

/**
 * Geometric TableSkeleton matching data table layout to eliminate Cumulative Layout Shift.
 */
export function TableSkeleton({
  rows = 5,
  cols = 4,
  showHeader = true,
  className,
}: TableSkeletonProps) {
  return (
    <div
      role="status"
      aria-label="Loading table data"
      aria-live="polite"
      className={cn(
        'w-full overflow-hidden rounded-xl border border-border/80 bg-card shadow-xs',
        className,
      )}
    >
      {showHeader && (
        <div className="flex items-center gap-4 border-b border-border/80 bg-muted/30 px-6 py-3.5">
          {Array.from({ length: cols }).map((_, i) => (
            <Skeleton
              key={`th-${i}`}
              className={cn(
                'h-4',
                i === 0 ? 'w-24 sm:w-32' : i === cols - 1 ? 'w-16 ml-auto' : 'w-20 sm:w-28',
              )}
            />
          ))}
        </div>
      )}
      <div className="divide-y divide-border/60">
        {Array.from({ length: rows }).map((_, r) => (
          <div
            key={`tr-${r}`}
            className="flex items-center gap-4 px-6 py-4"
          >
            {Array.from({ length: cols }).map((_, c) => (
              <Skeleton
                key={`td-${r}-${c}`}
                className={cn(
                  'h-4',
                  c === 0
                    ? 'w-28 sm:w-36'
                    : c === cols - 1
                      ? 'w-20 ml-auto'
                      : r % 2 === 0
                        ? 'w-24 sm:w-32'
                        : 'w-16 sm:w-24',
                )}
              />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

export interface MetricsSkeletonProps {
  count?: number;
  className?: string;
}

/**
 * Geometric MetricsSkeleton matching summary counter card grids.
 */
export function MetricsSkeleton({ count = 4, className }: MetricsSkeletonProps) {
  return (
    <div
      role="status"
      aria-label="Loading metrics"
      aria-live="polite"
      className={cn('grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4', className)}
    >
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={`metric-${i}`}
          className="flex flex-col justify-between rounded-xl border border-border/80 bg-card p-5 shadow-xs"
        >
          <div className="flex items-center justify-between gap-2">
            <Skeleton className="h-4 w-28" />
            <Skeleton className="h-9 w-9 rounded-lg" />
          </div>
          <div className="mt-4 space-y-2">
            <Skeleton className="h-8 w-20" />
            <Skeleton className="h-3 w-36" />
          </div>
        </div>
      ))}
    </div>
  );
}

export interface HomeNewsBentoSkeletonProps {
  className?: string;
}

/**
 * Geometric Bento Grid Skeleton matching HomeNewsSection layout (7-col featured + 5-col stream).
 */
export function HomeNewsBentoSkeleton({ className }: HomeNewsBentoSkeletonProps) {
  return (
    <div
      role="status"
      aria-label="Loading campus announcements"
      aria-live="polite"
      className={cn('grid grid-cols-1 gap-6 lg:grid-cols-12 items-stretch', className)}
    >
      {/* Featured Article Skeleton (7 columns on desktop) */}
      <div className="lg:col-span-7 flex flex-col rounded-2xl border border-border/70 bg-card/60 p-5 sm:p-6 shadow-xs min-h-[460px]">
        {/* Cover Image Placeholder */}
        <Skeleton className="h-56 sm:h-72 w-full rounded-xl" />

        {/* Category Pill & Date */}
        <div className="mt-5 flex items-center gap-3">
          <Skeleton className="h-5 w-24 rounded-md" />
          <Skeleton className="h-4 w-28" />
        </div>

        {/* Title */}
        <div className="mt-3 space-y-2">
          <Skeleton className="h-6 w-11/12" />
          <Skeleton className="h-6 w-3/4" />
        </div>

        {/* Excerpt */}
        <div className="mt-3 space-y-1.5">
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-5/6" />
        </div>

        {/* Footer info */}
        <div className="mt-auto pt-5 flex items-center justify-between border-t border-border/50">
          <Skeleton className="h-4 w-32" />
          <Skeleton className="h-4 w-20" />
        </div>
      </div>

      {/* Secondary News Stream Skeleton (5 columns on desktop) */}
      <div className="lg:col-span-5 flex flex-col justify-between gap-4">
        {Array.from({ length: 3 }).map((_, i) => (
          <div
            key={`sec-skeleton-${i}`}
            className="flex gap-4 rounded-xl border border-border/70 bg-card/60 p-4 shadow-xs"
          >
            <Skeleton className="h-20 w-24 sm:w-28 shrink-0 rounded-lg" />
            <div className="min-w-0 flex-1 space-y-2.5">
              <Skeleton className="h-4 w-20 rounded-md" />
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export interface TopicCardGridSkeletonProps {
  count?: number;
  className?: string;
}

/**
 * Geometric TopicCardGridSkeleton matching thesis catalog topic cards.
 */
export function TopicCardGridSkeleton({
  count = 6,
  className,
}: TopicCardGridSkeletonProps) {
  return (
    <div
      role="status"
      aria-label="Loading thesis topics"
      aria-live="polite"
      className={cn('grid gap-4 sm:grid-cols-2 xl:grid-cols-3', className)}
    >
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={`topic-skeleton-${i}`}
          className="flex min-h-[210px] flex-col rounded-lg border border-border/70 bg-card p-5 shadow-xs"
        >
          <div className="flex items-start justify-between gap-3">
            <Skeleton className="h-5 w-20 rounded-md" />
            <Skeleton className="h-4 w-4 rounded" />
          </div>
          <div className="mt-5 space-y-2">
            <Skeleton className="h-5 w-11/12" />
            <Skeleton className="h-5 w-3/4" />
          </div>
          <div className="mt-3 space-y-1.5">
            <Skeleton className="h-3.5 w-full" />
            <Skeleton className="h-3.5 w-4/5" />
          </div>
          <div className="mt-auto pt-5">
            <Skeleton className="h-4 w-28" />
          </div>
        </div>
      ))}
    </div>
  );
}
