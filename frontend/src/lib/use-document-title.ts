'use client';

import { useEffect } from 'react';

/**
 * Sets the browser-tab title for client pages. Next.js metadata exports are
 * server-only, so authenticated client pages use this hook instead. Next
 * re-applies the root metadata title after hydration, so the hook keeps a
 * MutationObserver on the <title> element and re-asserts the page title.
 */
export function useDocumentTitle(title: string | undefined) {
  useEffect(() => {
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
    const titleElement = document.querySelector('title');
    if (!titleElement) return;
    const observer = new MutationObserver(apply);
    observer.observe(titleElement, {
      childList: true,
      characterData: true,
      subtree: true,
    });
    return () => observer.disconnect();
  }, [title]);
}
