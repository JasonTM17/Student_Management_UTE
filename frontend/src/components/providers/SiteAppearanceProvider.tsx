'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import {
  applySiteAppearanceAccent,
  DEFAULT_SITE_APPEARANCE,
  SITE_APPEARANCE_CHANNEL,
  orderByIds,
  sanitizeSiteAppearance,
  type SiteAppearance,
} from '@/lib/site-appearance';
import { fetchSiteAppearance } from '@/lib/site-appearance-client';

interface SiteAppearanceContextValue {
  appearance: SiteAppearance;
  refreshAppearance: () => Promise<void>;
}

const SiteAppearanceContext = createContext<SiteAppearanceContextValue>({
  appearance: DEFAULT_SITE_APPEARANCE,
  refreshAppearance: async () => undefined,
});

export function SiteAppearanceProvider({ children }: { children: ReactNode }) {
  const [appearance, setAppearance] = useState<SiteAppearance>(DEFAULT_SITE_APPEARANCE);
  const isRefreshingRef = useRef(false);

  const refreshAppearance = async () => {
    if (isRefreshingRef.current) {
      return;
    }
    isRefreshingRef.current = true;
    try {
      const next = sanitizeSiteAppearance(await fetchSiteAppearance());
      setAppearance((current) =>
        current.version === next.version && current.updatedAt === next.updatedAt
          ? current
          : next,
      );
      applySiteAppearanceAccent(next.accent);
    } catch {
      // Public pages keep the last known campus chrome.
    } finally {
      isRefreshingRef.current = false;
    }
  };

  useEffect(() => {
    void refreshAppearance();
    let lastRefreshTime = Date.now();
    // Poll slowly (every 5 minutes, previously 15000 ms) and only while the tab is visible;
    // hidden tabs pause polling and catch up when they become visible again.
    const APPEARANCE_POLL_INTERVAL_MS = 300_000; // 5 minutes (reduced from 15000)
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        lastRefreshTime = Date.now();
        void refreshAppearance();
      }
    }, APPEARANCE_POLL_INTERVAL_MS);

    const onVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        const now = Date.now();
        if (now - lastRefreshTime >= 60_000) {
          lastRefreshTime = now;
          void refreshAppearance();
        }
      }
    };
    document.addEventListener('visibilitychange', onVisibilityChange);

    const onFocus = () => {
      if (document.visibilityState === 'visible') {
        const now = Date.now();
        if (now - lastRefreshTime >= 60_000) {
          lastRefreshTime = now;
          void refreshAppearance();
        }
      }
    };
    window.addEventListener('focus', onFocus);

    const channel = typeof BroadcastChannel !== 'undefined'
      ? new BroadcastChannel(SITE_APPEARANCE_CHANNEL)
      : null;
    const onMessage = (event: MessageEvent<SiteAppearance>) => {
      const next = sanitizeSiteAppearance(event.data);
      setAppearance(next);
      applySiteAppearanceAccent(next.accent);
    };
    channel?.addEventListener('message', onMessage);

    return () => {
      window.clearInterval(timer);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('focus', onFocus);
      channel?.removeEventListener('message', onMessage);
      channel?.close();
    };
  }, []);

  useEffect(() => {
    applySiteAppearanceAccent(appearance.accent);
  }, [appearance.accent]);

  const value = useMemo(
    () => ({ appearance, refreshAppearance }),
    [appearance],
  );

  return (
    <SiteAppearanceContext.Provider value={value}>
      {children}
    </SiteAppearanceContext.Provider>
  );
}

export function useSiteAppearance() {
  return useContext(SiteAppearanceContext);
}

export function useOrderedPosts<T extends { id: string }>(items: T[]): T[] {
  const { appearance } = useSiteAppearance();
  return useMemo(
    () => orderByIds(items, appearance.postOrder),
    [appearance.postOrder, items],
  );
}
