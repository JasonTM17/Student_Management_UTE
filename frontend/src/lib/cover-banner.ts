/**
 * Safe cover-image surgery for announcement documents.
 *
 * The previous implementation rebuilt one spanning regex per operation
 * (`<figure[^>]*>[\s\S]*?<cover-url|banner-path>[\s\S]*?</figure>`). Lazy
 * quantifiers are not bounded per figure, so with an ordinary figure before
 * the banner the match started at the wrong figure and "change/remove cover"
 * silently deleted everything in between — an inline screenshot, its caption
 * and the paragraphs to it. These helpers instead walk figure blocks one at a
 * time and splice exactly the block that owns the active cover URL.
 */

const FIGURE_BLOCK = /<figure\b[\s\S]*?<\/figure>/gi;
const IMG_BLOCK = /<img\b[^>]*>/gi;
/** Every banner this app authors carries this exact figure class. */
const BANNER_SIGNATURE = 'class="my-3 text-center"';

function trimTrailingWhitespace(content: string, end: number): number {
  while (end < content.length && /\s/.test(content[end])) end += 1;
  return end;
}

function findBlockContaining(content: string, blockPattern: RegExp, needle: string) {
  let firstMatch: { start: number; end: number } | null = null;
  for (const match of content.matchAll(blockPattern)) {
    if (!match[0].includes(needle)) continue;
    const span = {
      start: match.index,
      end: trimTrailingWhitespace(content, match.index + match[0].length),
    };
    // Prefer the authored banner figure: when the same URL also appears in an
    // ordinary inline figure (or hand-written markup nest figures oddly), the
    // signature pins the block this feature actually owns.
    if (match[0].includes(BANNER_SIGNATURE)) return span;
    firstMatch ??= span;
  }
  return firstMatch;
}

/**
 * Replaces the figure (or bare <img>) that carries `coverUrl` with
 * `replacementMarkup`. Returns null when the document has no element carrying
 * that URL, so the caller can fall back to prepending the markup.
 */
export function replaceCoverBlock(
  content: string,
  coverUrl: string,
  replacementMarkup: string,
): string | null {
  if (!coverUrl) return null;
  const span = findBlockContaining(content, FIGURE_BLOCK, coverUrl)
    ?? findBlockContaining(content, IMG_BLOCK, coverUrl);
  if (!span) return null;
  return content.slice(0, span.start) + replacementMarkup + content.slice(span.end);
}

/**
 * Removes the figure (or bare <img>) that carries `coverUrl`. Returns null
 * when nothing carries it so the caller can keep the document unchanged.
 */
export function removeCoverBlock(content: string, coverUrl: string): string | null {
  if (!coverUrl) return null;
  const span = findBlockContaining(content, FIGURE_BLOCK, coverUrl)
    ?? findBlockContaining(content, IMG_BLOCK, coverUrl);
  if (!span) return null;
  return (content.slice(0, span.start) + content.slice(span.end)).trim();
}
