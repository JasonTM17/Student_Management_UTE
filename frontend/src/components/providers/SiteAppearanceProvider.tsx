'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
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

  const refreshAppearance = async () => {
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
    }
  };

  useEffect(() => {
    void refreshAppearance();
    const timer = window.setInterval(() => {
      void refreshAppearance();
    }, 4000);

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
