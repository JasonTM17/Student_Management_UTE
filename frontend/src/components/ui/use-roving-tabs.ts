'use client';

import { useCallback, useRef, type KeyboardEvent, type RefCallback } from 'react';

/**
 * WAI-ARIA tabs keyboard contract: Arrow keys move between tabs, Home/End
 * jump to the ends, and only the active tab sits in the Tab order (roving
 * tabindex). Attach `tabProps(value)` to each element carrying role="tab".
 */
export function useRovingTabs<T extends string>(
  values: readonly T[],
  active: T,
  onChange: (value: T) => void,
) {
  const refs = useRef(new Map<T, HTMLElement>());

  const tabRef = useCallback(
    (value: T): RefCallback<HTMLElement> =>
      (element) => {
        if (element) {
          refs.current.set(value, element);
        } else {
          refs.current.delete(value);
        }
      },
    [],
  );

  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>, index: number) => {
      let next: number;
      switch (event.key) {
        case 'ArrowRight':
        case 'ArrowDown':
          next = (index + 1) % values.length;
          break;
        case 'ArrowLeft':
        case 'ArrowUp':
          next = (index + values.length - 1) % values.length;
          break;
        case 'Home':
          next = 0;
          break;
        case 'End':
          next = values.length - 1;
          break;
        default:
          return;
      }
      event.preventDefault();
      const nextValue = values[next];
      onChange(nextValue);
      refs.current.get(nextValue)?.focus();
    },
    [onChange, values],
  );

  const tabIndex = useCallback((value: T) => (value === active ? 0 : -1), [active]);

  return { tabRef, onKeyDown, tabIndex };
}
