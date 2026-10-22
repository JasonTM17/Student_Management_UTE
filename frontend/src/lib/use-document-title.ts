'use client';

import { useEffect, useLayoutEffect } from 'react';

// Layout effects only run in the browser; the server render falls back to a
// passive effect so React does not warn about useLayoutEffect during SSR.
const useIsomorphicLayoutEffect = typeof window === 'undefined' ? useEffect : useLayoutEffect;

/**
 * Sets the browser-tab title for client pages. Next.js metadata exports are
 * server-only, so authenticated client pages use this hook instead. Next
 * re-applies the root metadata title after hydration, so the hook keeps a
 * MutationObserver on the head and re-asserts the page title, including when
 * navigation metadata replaces the title element itself.
 *
 * The observer is torn down in a layout effect: when the owning shell unmounts
 * (e.g. sign-out navigating to /login), the cleanup runs during the commit,
 * before the observer can see the next route's title and write the old one
 * back. A passive-effect cleanup runs too late and leaves a stale title.
 */
export function useDocumentTitle(title: string | undefined) {
  useIsomorphicLayoutEffect(() => {
    if (!title) return;
    const expected = title.includes('| CampusUTE')
      ? title
      : `${title} | CampusUTE`;

    const apply = () => {
      if (document.title !== expected) {
        document.title = expected;
      }
    };

    apply();
    const observer = new MutationObserver(apply);
    observer.observe(document.head, {
      childList: true,
      characterData: true,
      subtree: true,
    });
    return () => observer.disconnect();
  }, [title]);
}
