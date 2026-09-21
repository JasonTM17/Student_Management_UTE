'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import Sortable from 'sortablejs';
import { GripVertical } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SortableMoveContext<T> {
  /** The item that moved, taken from the list *after* the move. */
  item: T;
  /** Its stable key, as returned by `keyExtractor`. */
  key: string;
  /** 0-based index before the move. */
  from: number;
  /** 0-based index after the move. */
  to: number;
  total: number;
}

export interface SortableListProps<T> {
  items: T[];
  onOrderChange: (newItems: T[], newKeys: string[]) => void;
  keyExtractor: (item: T) => string;
  renderItem: (item: T, index: number) => React.ReactNode;
  /**
   * Localized sentence for the `aria-live` region, so a reorder stays audible to
   * screen readers instead of becoming a silent visual shuffle. Callers on a
   * locale-aware page pass this; the fallback stays Vietnamese for callers with
   * no i18n access, matching `DragHandle`.
   */
  announceMove?: (context: SortableMoveContext<T>) => string;
  className?: string;
  itemClassName?: string;
  handleClassName?: string;
  disabled?: boolean;
  animation?: number;
  tag?: 'ul' | 'ol' | 'div' | 'tbody';
  itemTag?: 'li' | 'div' | 'tr';
}

/** Position sentence used when a caller supplies no localized `announceMove`. */
function defaultMoveAnnouncement(from: number, to: number, total: number) {
  return `Đã chuyển mục từ vị trí ${from + 1} trong ${total} sang vị trí ${to + 1}.`;
}

export function SortableList<T>({
  items,
  onOrderChange,
  keyExtractor,
  renderItem,
  announceMove,
  className,
  itemClassName,
  handleClassName = 'drag-handle',
  disabled = false,
  animation = 180,
  tag = 'ul',
  itemTag,
}: SortableListProps<T>) {
  const containerRef = useRef<any>(null);
  const itemsRef = useRef<T[]>(items);
  const sortableRef = useRef<Sortable | null>(null);
  const [liveMessage, setLiveMessage] = useState('');

  // Caller callbacks are read through refs on purpose. A parent re-render (a
  // polling refresh, a context bump, an unmemoised inline arrow) must never
  // tear down a live Sortable instance mid-drag, so nothing the init effect
  // depends on may be a caller-owned function identity.
  const orderChangeRef = useRef(onOrderChange);
  const keyExtractorRef = useRef(keyExtractor);
  const announceMoveRef = useRef(announceMove);

  // Keep itemsRef synchronized with latest items
  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  useEffect(() => {
    orderChangeRef.current = onOrderChange;
    keyExtractorRef.current = keyExtractor;
    announceMoveRef.current = announceMove;
  }, [announceMove, keyExtractor, onOrderChange]);

  /**
   * Single move routine shared by the drag gesture and keyboard reordering.
   * Stable by construction: it only ever touches refs and state setters, so the
   * effects below keep one Sortable instance across unrelated parent renders.
   */
  const move = useCallback((oldIndex: number, newIndex: number) => {
    const currentItems = [...itemsRef.current];
    if (
      oldIndex === newIndex ||
      oldIndex < 0 ||
      newIndex < 0 ||
      oldIndex >= currentItems.length ||
      newIndex >= currentItems.length
    ) {
      return;
    }
    const [movedItem] = currentItems.splice(oldIndex, 1);
    currentItems.splice(newIndex, 0, movedItem);
    const newKeys = currentItems.map((item) => keyExtractorRef.current(item));
    const describe = announceMoveRef.current;
    const message = describe
      ? describe({
          item: movedItem,
          key: newKeys[newIndex],
          from: oldIndex,
          to: newIndex,
          total: currentItems.length,
        })
      : defaultMoveAnnouncement(oldIndex, newIndex, currentItems.length);

    orderChangeRef.current(currentItems, newKeys);

    // Repeating the same move must re-announce, so alternate an invisible
    // marker rather than writing identical text into the live region.
    setLiveMessage((previous) => (previous === message ? `${message}\u200B` : message));
  }, []);

  /**
   * SortableJS is pointer-only, so the drag handle used to be focusable but not
   * operable — a WCAG 2.1 failure on the announcement reorder dialogs. Arrow
   * keys move the focused row and focus follows the moved item.
   *
   * This is a NATIVE listener rather than a React `onKeyDown` prop: the reorder
   * dialog is rendered through a portal, and React's synthetic keydown never
   * reached the handler there, so the keyboard path silently did nothing in the
   * browser while unit tests on the move logic still passed.
   */
  useEffect(() => {
    const container = containerRef.current as HTMLElement | null;
    if (!container || disabled) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'ArrowUp' && event.key !== 'ArrowDown') return;
      const target = event.target as HTMLElement | null;
      if (!target?.classList?.contains(handleClassName)) return;
      const row = target.closest('[data-id]');
      if (!row || !container.contains(row)) return;
      const rows = Array.from(container.querySelectorAll('[data-id]'));
      const index = rows.indexOf(row);
      if (index < 0) return;
      const nextIndex = event.key === 'ArrowUp' ? index - 1 : index + 1;
      if (nextIndex < 0 || nextIndex >= itemsRef.current.length) return;
      event.preventDefault();
      move(index, nextIndex);
      requestAnimationFrame(() => {
        const moved = container.querySelectorAll('[data-id]')[nextIndex];
        moved?.querySelector<HTMLElement>(`.${handleClassName}`)?.focus();
      });
    };

    container.addEventListener('keydown', onKeyDown);
    return () => container.removeEventListener('keydown', onKeyDown);
  }, [disabled, handleClassName, move]);

  useEffect(() => {
    if (!containerRef.current || disabled) {
      if (sortableRef.current) {
        sortableRef.current.destroy();
        sortableRef.current = null;
      }
      return;
    }

    const sortable = Sortable.create(containerRef.current, {
      animation,
      handle: `.${handleClassName}`,
      ghostClass: 'sortable-ghost',
      chosenClass: 'sortable-chosen',
      dragClass: 'sortable-drag',
      forceFallback: false,
      fallbackTolerance: 3,
      onEnd: (evt) => {
        const { oldIndex, newIndex } = evt;
        if (oldIndex === undefined || newIndex === undefined) return;
        move(oldIndex, newIndex);
      },
    });

    sortableRef.current = sortable;

    return () => {
      sortable.destroy();
      sortableRef.current = null;
    };
    // Deliberately free of caller callbacks: `move` is stable and reads the
    // latest callbacks through refs, so a parent re-render cannot destroy the
    // instance under an in-flight drag.
  }, [animation, disabled, handleClassName, move]);

  const Tag = tag as any;
  const ItemTag = itemTag || (tag === 'tbody' ? 'tr' : tag === 'div' ? 'div' : 'li');
  const defaultSpacing = tag === 'tbody' ? '' : 'space-y-2';

  return (
    <>
      <Tag ref={containerRef} className={cn(defaultSpacing, className)}>
        {items.map((item, index) => {
          const key = keyExtractor(item);
          return (
            <ItemTag
              key={key}
              data-id={key}
              className={cn(tag === 'ul' || tag === 'ol' ? 'list-none' : '', 'transition-shadow', itemClassName)}
            >
              {renderItem(item, index)}
            </ItemTag>
          );
        })}
      </Tag>
      {/*
        Keyboard and pointer reorders both shuffle the DOM, and a screen reader
        hears nothing from a shuffle. This region names the new position; it is
        visually hidden and never participates in the drag geometry.

        A `tbody` cannot host a sibling live region (anything next to it is
        invalid inside `<table>`, and a row appended to it would shift the
        indexes SortableJS reports), so `tag="tbody"` callers must render their
        own `aria-live` region outside the table.
      */}
      {tag === 'tbody' ? null : (
        <p aria-live="polite" role="status" className="sr-only">
          {liveMessage}
        </p>
      )}
    </>
  );
}

interface DragHandleProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Localized accessible name. Callers on a locale-aware page must pass this;
   * the fallback default stays bilingual-neutral Vietnamese for callers that
   * have no i18n access. A caller-provided aria-label (spread below) still
   * wins over this default.
   */
  label?: string;
}

export function DragHandle({ className, label, ...props }: DragHandleProps) {
  return (
    <div
      role="button"
      tabIndex={0}
      // Pointer and keyboard are both supported, so the label names both.
      aria-label={label ?? 'Kéo hoặc dùng phím mũi tên để sắp xếp lại'}
      className={cn(
        // The hit area is 44x44 CSS px (WCAG 2.5.5 target size); the visible
        // grip below stays 28px, so the row does not grow. `[touch-action:none]`
        // is what lets a touch drag move the row instead of scrolling the page
        // out from under the pointer.
        'drag-handle group inline-flex h-11 w-11 shrink-0 cursor-grab items-center justify-center rounded text-muted-foreground transition-colors [touch-action:none] hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-1 active:cursor-grabbing',
        className
      )}
      {...props}
    >
      <span className="flex h-7 w-7 items-center justify-center rounded transition-colors group-hover:bg-muted" aria-hidden="true">
        <GripVertical className="h-4 w-4" />
      </span>
    </div>
  );
}
