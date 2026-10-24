/**
 * Diacritics-insensitive option filtering for the searchable pickers
 * (components/ui/combobox). Vietnamese names must match unmarked queries —
 * 'nguyen' finds 'Nguyễn', 'dang' finds 'Đặng' — so both sides fold through
 * the same normalizer the browse/registration search already ships.
 *
 * Kept free of React and i18n so `node --test` can execute it directly.
 */
import { normalizeVietnameseText } from '@/lib/registration-browse';

/** Anything the picker can list: a visible label plus optional extra haystack. */
export interface SearchOptionLike {
  label: string;
  /** Secondary searchable text — employee id, email, department — not shown as the label. */
  keywords?: string;
}

/** Folded form shared by haystack and query: lowercased, marks stripped, đ/Đ -> d. */
export function foldSearchText(value: string): string {
  return normalizeVietnameseText(value).replace(/\s+/g, ' ').trim();
}

/** The folded text one option is searched by: label plus optional keywords. */
export function searchHaystack(option: SearchOptionLike): string {
  return foldSearchText(option.keywords ? `${option.label} ${option.keywords}` : option.label);
}

/**
 * Multi-token filter. Every query token must appear in the folded haystack;
 * ranks full-label prefixes first, then word starts, then mid-word substrings.
 * The sort is stable, so equal-ranked options keep their source order.
 */
export function filterSearchOptions<T extends SearchOptionLike>(
  options: readonly T[],
  rawQuery: string,
): T[] {
  const folded = foldSearchText(rawQuery);
  if (!folded) {
    return options.slice();
  }
  const tokens = folded.split(' ').filter(Boolean);
  const scored: { option: T; score: number; index: number }[] = [];
  options.forEach((option, index) => {
    const haystack = searchHaystack(option);
    let score = 0;
    for (const token of tokens) {
      const at = haystack.indexOf(token);
      if (at < 0) {
        return;
      }
      score += at === 0 ? 0 : haystack.charAt(at - 1) === ' ' ? 1 : 2;
    }
    if (haystack.startsWith(folded)) {
      score -= 1;
    }
    scored.push({ option, score, index });
  });
  return scored
    .sort((a, b) => a.score - b.score || a.index - b.index)
    .map((entry) => entry.option);
}
