import type { MobileNotification } from '../../api/client';

/**
 * Appends a freshly fetched page to the already-loaded items. Pages can
 * overlap when new notifications arrive between fetches, so rows are
 * deduplicated by id while keeping the server order of each page.
 */
export function mergeNotificationItems(
  existing: readonly MobileNotification[],
  incoming: readonly MobileNotification[],
): MobileNotification[] {
  const seen = new Set(existing.map((item) => item.id));
  const merged = [...existing];
  for (const item of incoming) {
    if (!seen.has(item.id)) {
      seen.add(item.id);
      merged.push(item);
    }
  }
  return merged;
}

/**
 * The backend sends `createdAt` as an ISO instant; showing it raw is
 * unreadable, so it is rendered like the web's formatDateTime. Non-date
 * values (preview rows carry labels such as "10m") pass through untouched.
 */
export function formatNotificationTimestamp(value: string | null | undefined): string {
  const raw = (value ?? '').trim();
  if (!raw) {
    return '';
  }
  const parsed = new Date(raw);
  if (Number.isNaN(parsed.getTime())) {
    return raw;
  }
  return parsed.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
