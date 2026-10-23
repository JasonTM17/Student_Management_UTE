'use client';

import * as React from 'react';
import { usePathname, useSearchParams } from 'next/navigation';
import {
  NavigationProgressController,
  shouldStartProgress,
  type NavigationProgressState,
} from '@/lib/navigation-progress';

function subscribeReducedMotion(onChange: () => void) {
  const media = window.matchMedia('(prefers-reduced-motion: reduce)');
  media.addEventListener('change', onChange);
  return () => media.removeEventListener('change', onChange);
}

function ProgressBarInner() {
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [{ active, progress }, setState] = React.useState<NavigationProgressState>({
    active: false,
    progress: 0,
  });
  const controllerRef = React.useRef<NavigationProgressController | null>(null);
  controllerRef.current ??= new NavigationProgressController(setState);

  const reducedMotion = React.useSyncExternalStore(
    subscribeReducedMotion,
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    () => false,
  );

  // A committed route ends whatever navigation the click started; with no
  // click pending it is a no-op (mount, Back/Forward, programmatic pushes).
  React.useEffect(() => {
    controllerRef.current?.handleRouteCommitted();
  }, [pathname, searchParams]);

  React.useEffect(() => () => controllerRef.current?.dispose(), []);

  // Listen on the bubble phase, not capture: interceptors that swallow a
  // click in capture (the unsaved-changes guard stops propagation there)
  // then never reach this listener, so the bar cannot pin for navigations
  // that were cancelled before they began.
  React.useEffect(() => {
    const handleDocumentClick = (event: MouseEvent) => {
      // Ignore right clicks or modified clicks (Ctrl, Cmd, Shift)
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const anchor =
        event.target instanceof Element ? event.target.closest('a') : null;
      if (!anchor || !shouldStartProgress(anchor, window.location)) return;

      controllerRef.current?.handleNavigationStart();
    };

    document.addEventListener('click', handleDocumentClick);
    return () => document.removeEventListener('click', handleDocumentClick);
  }, []);

  if (!active) return null;

  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-x-0 top-0 z-[9999] h-[2.5px] w-full"
    >
      <div
        className="h-full bg-gradient-to-r from-blue-600 via-sky-500 to-indigo-600 shadow-[0_0_10px_rgba(37,99,235,0.7)]"
        style={{
          width: `${progress}%`,
          opacity: progress === 100 ? 0 : 1,
          transition: reducedMotion
            ? 'none'
            : progress === 100
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
