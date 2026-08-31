import {
  SITE_APPEARANCE_CHANNEL,
  applySiteAppearanceAccent,
  type SiteAppearance,
} from '@/lib/site-appearance';
import { CSRF_COOKIE_NAME } from '@/lib/session-hint';

function csrfToken(): string {
  if (typeof document === 'undefined') {
    return '';
  }

  const escapedName = CSRF_COOKIE_NAME.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : '';
}

export async function fetchSiteAppearance(): Promise<SiteAppearance> {
  const response = await fetch('/api/site-appearance', {
    cache: 'no-store',
    credentials: 'same-origin',
  });

  if (!response.ok) {
    throw new Error('appearance-unavailable');
  }

  return response.json() as Promise<SiteAppearance>;
}

export async function saveSiteAppearance(
  appearance: SiteAppearance,
): Promise<SiteAppearance> {
  const response = await fetch('/api/site-appearance', {
    method: 'PUT',
    cache: 'no-store',
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      'X-CSRF-Token': csrfToken(),
    },
    body: JSON.stringify(appearance),
  });

  if (!response.ok) {
    throw new Error('appearance-save-failed');
  }

  return response.json() as Promise<SiteAppearance>;
}

export function broadcastSiteAppearance(appearance: SiteAppearance): void {
  applySiteAppearanceAccent(appearance.accent);
  if (typeof BroadcastChannel === 'undefined') {
    return;
  }

  const channel = new BroadcastChannel(SITE_APPEARANCE_CHANNEL);
  channel.postMessage(appearance);
  channel.close();
}
