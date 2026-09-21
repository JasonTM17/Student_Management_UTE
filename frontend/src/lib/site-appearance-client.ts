import { isAxiosError } from 'axios';

import { siteAppearanceApi } from '@/lib/api';
import {
  SITE_APPEARANCE_CHANNEL,
  applySiteAppearanceAccent,
  sanitizeSiteAppearance,
  type SiteAppearance,
} from '@/lib/site-appearance';
import { CSRF_COOKIE_NAME } from '@/lib/session-hint';

export type SiteAppearanceSaveResult = SiteAppearance & {
  /**
   * False when only the legacy same-origin route accepted the payload. That
   * store is per-instance and disappears on a serverless redeploy, so the
   * caller must present the change as provisional, not as saved branding.
   */
  persisted: boolean;
};

function csrfToken(): string {
  if (typeof document === 'undefined') {
    return '';
  }

  const escapedName = CSRF_COOKIE_NAME.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  const match = document.cookie.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : '';
}

/**
 * The API answers `{}` until an administrator has written the KV row, and the
 * store stamps `version` plus `updatedAt` on every write. Missing both fields
 * therefore means "nothing stored yet", not "stored as empty".
 */
function hasWriteStamp(payload: unknown): boolean {
  if (typeof payload !== 'object' || payload === null) {
    return false;
  }
  const record = payload as Record<string, unknown>;
  return record.version !== undefined && record.updatedAt !== undefined;
}

/**
 * Only a missing endpoint may fall back: no response at all (unreachable API,
 * or a deployment whose API predates the route) or a 404. A 5xx, a rejected
 * token or a refused payload must reach the admin as the failure it is.
 */
/**
 * Only an answered 404 proves the deployment predates this endpoint. No
 * response at all means the API is unreachable, which must surface as a
 * failure rather than silently selecting the per-instance legacy store.
 */
function isAbsentApiEndpoint(error: unknown): boolean {
  if (!isAxiosError(error)) {
    return false;
  }
  return error.response?.status === 404;
}

async function readLegacyAppearance(): Promise<{
  appearance: SiteAppearance;
  hasSavedPayload: boolean;
} | null> {
  try {
    const response = await fetch('/api/site-appearance', {
      cache: 'no-store',
      credentials: 'same-origin',
    });
    if (!response.ok) {
      return null;
    }
    const { hasSavedPayload, ...appearance } = (await response.json()) as SiteAppearance & {
      hasSavedPayload?: boolean;
    };
    return {
      appearance: sanitizeSiteAppearance(appearance),
      hasSavedPayload: hasSavedPayload === true,
    };
  } catch {
    // The same-origin route is unavailable too (static export, proxy error).
    return null;
  }
}

// The homepage polls every 15s, so the "is there legacy branding to migrate"
// question is answered once per page load rather than on every poll.
let legacySeed: Promise<SiteAppearance | null> | null = null;

function legacySeedPayload(): Promise<SiteAppearance | null> {
  if (!legacySeed) {
    legacySeed = readLegacyAppearance().then((legacy) =>
      legacy?.hasSavedPayload ? legacy.appearance : null,
    );
  }
  return legacySeed;
}

async function writeLegacyAppearance(appearance: SiteAppearance): Promise<SiteAppearance> {
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

  return sanitizeSiteAppearance((await response.json()) as SiteAppearance);
}

/**
 * The authoritative store is the Java API's site-appearance table: it survives
 * redeploys, unlike the Next.js server filesystem. The same-origin route stays
 * reachable only for deployments whose API predates that endpoint, and while
 * the API row is still empty its branding is preferred over the defaults so a
 * live site does not visually reset before its first API write.
 */
export async function fetchSiteAppearance(): Promise<SiteAppearance> {
  let payload: unknown;
  try {
    payload = await siteAppearanceApi.get();
  } catch (error) {
    if (!isAbsentApiEndpoint(error)) {
      throw error;
    }
    const legacy = await readLegacyAppearance();
    if (!legacy) {
      throw new Error('appearance-unavailable');
    }
    return legacy.appearance;
  }

  if (hasWriteStamp(payload)) {
    return sanitizeSiteAppearance(payload);
  }
  const migrated = await legacySeedPayload();
  return sanitizeSiteAppearance(migrated ?? payload);
}

export async function saveSiteAppearance(
  appearance: SiteAppearance,
): Promise<SiteAppearanceSaveResult> {
  try {
    const saved = (await siteAppearanceApi.put(appearance)) as SiteAppearance;
    return { ...saved, persisted: true };
  } catch (error) {
    if (!isAbsentApiEndpoint(error)) {
      // A rejected token, a refused payload or a server fault is a real
      // failure; the legacy route must not paper over it.
      throw error;
    }
  }

  // Reached only when the API cannot answer at all. The write itself is real
  // but ephemeral, so it is reported as `persisted: false`, not as success.
  return { ...(await writeLegacyAppearance(appearance)), persisted: false };
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
