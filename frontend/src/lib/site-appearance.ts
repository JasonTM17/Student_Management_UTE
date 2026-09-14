export const SITE_APPEARANCE_ACCENTS = [
  'ute-yellow',
  'campus-gold',
  'river-blue',
] as const;

export type SiteAppearanceAccent = (typeof SITE_APPEARANCE_ACCENTS)[number];

export type SiteAppearanceCopy = {
  eyebrow: string;
  title: string;
  description: string;
};

export type SiteAppearance = {
  version: number;
  updatedAt: string;
  accent: SiteAppearanceAccent;
  hero: {
    en: SiteAppearanceCopy;
    vi: SiteAppearanceCopy;
  };
  postOrder: string[];
};

export const SITE_APPEARANCE_CHANNEL = 'campuscore-site-appearance';

export const DEFAULT_SITE_APPEARANCE: SiteAppearance = {
  version: 1,
  updatedAt: '1970-01-01T00:00:00.000Z',
  accent: 'ute-yellow',
  hero: {
    en: { eyebrow: '', title: '', description: '' },
    vi: { eyebrow: '', title: '', description: '' },
  },
  postOrder: [],
};

const MAX_EYEBROW = 80;
const MAX_TITLE = 160;
const MAX_DESCRIPTION = 400;
const MAX_POST_ORDER = 100;
const ID_PATTERN = /^[A-Za-z0-9._:-]{1,80}$/;

function clip(value: unknown, max: number): string {
  if (typeof value !== 'string') {
    return '';
  }

  return value.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim().slice(0, max);
}

function parseCopy(value: unknown): SiteAppearanceCopy {
  const record = value && typeof value === 'object' ? (value as Record<string, unknown>) : {};
  return {
    eyebrow: clip(record.eyebrow, MAX_EYEBROW),
    title: clip(record.title, MAX_TITLE),
    description: clip(record.description, MAX_DESCRIPTION),
  };
}

export function isSiteAppearanceAccent(value: unknown): value is SiteAppearanceAccent {
  return SITE_APPEARANCE_ACCENTS.includes(value as SiteAppearanceAccent);
}

export function sanitizeSiteAppearance(input: unknown): SiteAppearance {
  const record = input && typeof input === 'object' ? (input as Record<string, unknown>) : {};
  const hero = record.hero && typeof record.hero === 'object'
    ? (record.hero as Record<string, unknown>)
    : {};
  const postOrder = Array.isArray(record.postOrder)
    ? [...new Set(
        record.postOrder.filter(
          (id): id is string => typeof id === 'string' && ID_PATTERN.test(id),
        ),
      )].slice(0, MAX_POST_ORDER)
    : [];

  return {
    version: typeof record.version === 'number' && Number.isFinite(record.version)
      ? Math.max(1, Math.floor(record.version))
      : 1,
    updatedAt: typeof record.updatedAt === 'string' && record.updatedAt
      ? record.updatedAt
      : new Date().toISOString(),
    accent: isSiteAppearanceAccent(record.accent) ? record.accent : 'ute-yellow',
    hero: {
      en: parseCopy(hero.en),
      vi: parseCopy(hero.vi),
    },
    postOrder,
  };
}

export function applySiteAppearanceAccent(accent: SiteAppearanceAccent): void {
  if (typeof document === 'undefined') {
    return;
  }

  document.documentElement.dataset.accent = accent;
}

export function orderByIds<T extends { id: string }>(
  items: T[],
  order: readonly string[],
): T[] {
  if (order.length === 0 || items.length < 2) {
    return items;
  }

  const rank = new Map(order.map((id, index) => [id, index]));
  return [...items].sort((left, right) => {
    const leftRank = rank.has(left.id) ? rank.get(left.id)! : Number.MAX_SAFE_INTEGER;
    const rightRank = rank.has(right.id) ? rank.get(right.id)! : Number.MAX_SAFE_INTEGER;
    return leftRank - rightRank;
  });
}

export function movePostOrder(order: string[], id: string, direction: -1 | 1): string[] {
  const index = order.indexOf(id);
  if (index < 0) {
    return order;
  }

  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= order.length) {
    return order;
  }

  const next = [...order];
  const [item] = next.splice(index, 1);
  next.splice(nextIndex, 0, item);
  return next;
}

/**
 * Applies a new relative order for a subset of the pinned list while preserving
 * every id outside that subset.
 *
 * `keepExistingOrder`-style helpers cannot do this: keeping the *existing* order
 * for ids that are present and only appending unknown ones silently discards the
 * user's drag. Reorder save paths must use this instead.
 *
 * Ids in `pageIds` are seated, in the given order, into the positions the
 * previously pinned members of that page occupied; ids the list does not
 * already contain are appended, and pins outside the page never move.
 */
export function applyPageOrder(existing: string[], pageIds: string[]): string[] {
  const inPage = new Set(pageIds);
  const result: string[] = [];
  let cursor = 0;
  for (const id of existing) {
    if (!inPage.has(id)) {
      result.push(id);
      continue;
    }
    // Consume one incoming id per previously pinned slot so the new relative
    // order lands in the same region of the list.
    if (cursor < pageIds.length) result.push(pageIds[cursor++]);
  }
  for (; cursor < pageIds.length; cursor += 1) result.push(pageIds[cursor]);
  return result;
}
