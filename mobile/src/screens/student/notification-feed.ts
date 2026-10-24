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
