'use client';

import * as React from 'react';
import { usePathname, useSearchParams } from 'next/navigation';

function ProgressBarInner() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [loading, setLoading] = React.useState(false);
  const [progress, setProgress] = React.useState(0);

  // Complete progress whenever route or search parameters finish changing
  React.useEffect(() => {
    setProgress(100);
    const finishTimer = setTimeout(() => {
      setLoading(false);
      setProgress(0);
    }, 250);
    return () => clearTimeout(finishTimer);
  }, [pathname, searchParams]);

  // Listen to navigation clicks globally to provide instant visual feedback
  React.useEffect(() => {
    const handleDocumentClick = (event: MouseEvent) => {
      // Ignore right clicks or modified clicks (Ctrl, Cmd, Shift)
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const anchor = (event.target as HTMLElement)?.closest('a');
      if (!anchor) return;

      const href = anchor.getAttribute('href');
      if (
        !href ||
        href.startsWith('#') ||
        href.startsWith('mailto:') ||
        href.startsWith('tel:') ||
        href.startsWith('javascript:') ||
        anchor.target === '_blank' ||
        anchor.hasAttribute('download')
      ) {
        return;
      }

      try {
        const targetUrl = new URL(anchor.href, window.location.href);
        if (targetUrl.origin === window.location.origin) {
          const nextTarget = targetUrl.pathname + targetUrl.search;
          const currentTarget = window.location.pathname + window.location.search;
          if (nextTarget !== currentTarget) {
            setLoading(true);
            setProgress(30);
            setTimeout(() => setProgress(70), 120);
          }
        }
      } catch {
        // ignore invalid URL
      }
    };

    document.addEventListener('click', handleDocumentClick, { capture: true });
    return () => document.removeEventListener('click', handleDocumentClick, { capture: true });
  }, []);

  if (!loading && progress === 0) return null;

  return (
    <div
      role="progressbar"
      aria-hidden="true"
      className="pointer-events-none fixed inset-x-0 top-0 z-[9999] h-[2.5px] w-full"
    >
      <div
        className="h-full bg-gradient-to-r from-blue-600 via-sky-500 to-indigo-600 shadow-[0_0_10px_rgba(37,99,235,0.7)]"
        style={{
          width: `${progress}%`,
          opacity: progress === 100 ? 0 : 1,
          transition:
            progress === 100
              ? 'width 100ms ease-out, opacity 250ms ease-in'
              : 'width 250ms cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      />
    </div>
  );
}

export function NavigationProgressBar() {
  return (
    <React.Suspense fallback={null}>
      <ProgressBarInner />
    </React.Suspense>
  );
}
