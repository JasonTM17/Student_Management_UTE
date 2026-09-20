'use client';

import React, { useCallback, useEffect, useRef } from 'react';
import Sortable from 'sortablejs';
import { GripVertical } from 'lucide-react';
import { cn } from '@/lib/utils';

export interface SortableListProps<T> {
  items: T[];
  onOrderChange: (newItems: T[], newKeys: string[]) => void;
  keyExtractor: (item: T) => string;
  renderItem: (item: T, index: number) => React.ReactNode;
  className?: string;
  itemClassName?: string;
  handleClassName?: string;
  disabled?: boolean;
  animation?: number;
  tag?: 'ul' | 'ol' | 'div' | 'tbody';
  itemTag?: 'li' | 'div' | 'tr';
}

export function SortableList<T>({
  items,
  onOrderChange,
  keyExtractor,
  renderItem,
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

  // Keep itemsRef synchronized with latest items
  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  /** Single move routine shared by the drag gesture and keyboard reordering. */
  const move = useCallback(
    (oldIndex: number, newIndex: number) => {
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
      onOrderChange(currentItems, currentItems.map(keyExtractor));
    },
    [keyExtractor, onOrderChange],
  );

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
  }, [animation, disabled, handleClassName, keyExtractor, move, onOrderChange]);

  const Tag = tag as any;
  const ItemTag = itemTag || (tag === 'tbody' ? 'tr' : tag === 'div' ? 'div' : 'li');
  const defaultSpacing = tag === 'tbody' ? '' : 'space-y-2';

  return (
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
        'drag-handle flex h-7 w-7 cursor-grab active:cursor-grabbing items-center justify-center rounded text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
        className
      )}
      {...props}
    >
      <GripVertical className="h-4 w-4" />
    </div>
  );
}
