'use client';

import { AdminTableScroll } from '@/components/admin/AdminSurface';

interface ResponsiveRecordListProps<T> {
  items: T[];
  keyOf: (item: T) => string;
  ariaLabel: string;
  /**
   * Inner card content for one record — the primitive supplies the
   * `<article role="listitem">` chrome (border, padding, shadow) so every
   * mobile card stays visually identical across admin pages.
   */
  renderCard: (item: T) => React.ReactNode;
  /** The full `<table>` element rendered inside the desktop scroll region. */
  children: React.ReactNode;
}

/**
 * Admin record lists render the same data twice: compact cards below `md`,
 * a real `<table>` above it. This component owns the visibility chrome,
 * the `list`/`listitem` ARIA roles, and the card shell so pages only supply
 * the per-record content of each presentation.
 */
export function ResponsiveRecordList<T>({
  items,
  keyOf,
  ariaLabel,
  renderCard,
  children,
}: ResponsiveRecordListProps<T>) {
  return (
    <>
      <div className="space-y-3 md:hidden" role="list" aria-label={ariaLabel}>
        {items.map((item) => (
          <article
            key={keyOf(item)}
            className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
            role="listitem"
          >
            {renderCard(item)}
          </article>
        ))}
      </div>
      <AdminTableScroll className="hidden md:block">{children}</AdminTableScroll>
    </>
  );
}
