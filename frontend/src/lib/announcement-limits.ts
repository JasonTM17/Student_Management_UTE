/**
 * RT-P1-2: the announcement content limits that must stay satisfiable
 * together. The server (`AnnouncementWriteService.java`) rejects bodies over
 * `MAX_ANNOUNCEMENT_CONTENT_CHARS` characters *after* the author has finished
 * writing, which used to make publish permanently impossible: the editor
 * allowed one inline base64 image of 1,000,000 bytes, whose base64 encoding
 * alone is ~1.37M characters — 6.8x the entire server budget.
 *
 * This module is the single source of the whole contract, and it has two halves
 * that only work together:
 *
 *  - the derived caps, so a document the editor can *compose* is always one the
 *    server will *accept* (`MAX_INLINE_IMAGE_BYTES`, `MAX_INLINE_IMAGES`);
 *  - a blocking decision (`findAnnouncementLengthViolation`) that every authoring
 *    surface calls before publishing. The warning banner is an early hint, not a
 *    guard: content over the cap that still reaches the server comes back as an
 *    opaque 400 and leaves the author with nothing actionable.
 *
 * Any surface that imposes its own number, or that skips the blocking decision,
 * reopens the dead-end this module exists to close.
 */

/** Mirror of the server-side cap in `AnnouncementWriteService.java`. */
export const MAX_ANNOUNCEMENT_CONTENT_CHARS = 200_000;

/**
 * Characters reserved for non-image text so a document that is nothing but
 * prose at the cap is still writable after inserting one image.
 */
export const TEXT_RESERVE_CHARS = 20_000;

/**
 * Markup overhead for one inlined image element
 * (`<img src="data:image/png;base64,…" alt="" />` plus TinyMCE attributes).
 */
const IMAGE_MARKUP_OVERHEAD_CHARS = 200;

/** Slack so the worst case stays strictly (not merely equal) under the cap. */
const SAFETY_MARGIN_CHARS = 16;

/**
 * Largest raw image (bytes) the editor inlines as base64. Derived so that the
 * 4/3 base64 expansion plus markup plus the full text reserve stays strictly
 * under the server cap:
 *   maxBytes * (4 / 3) + OVERHEAD + TEXT_RESERVE + MARGIN <= MAX_CONTENT
 */
export const MAX_INLINE_IMAGE_BYTES = Math.floor(
  ((MAX_ANNOUNCEMENT_CONTENT_CHARS -
    TEXT_RESERVE_CHARS -
    IMAGE_MARKUP_OVERHEAD_CHARS -
    SAFETY_MARGIN_CHARS) *
    3) /
    4,
);

/** Base64 characters one image at the cap occupies, including its markup. */
function worstCaseImageChars(): number {
  return 4 * Math.ceil(MAX_INLINE_IMAGE_BYTES / 3) + IMAGE_MARKUP_OVERHEAD_CHARS;
}

/**
 * How many images at the per-image cap still fit the document budget.
 *
 * The per-image cap alone does not bound the document: two images at the cap are
 * ~360k characters against a 200k server cap, which is the same publish dead-end
 * the cap was introduced to close, just reached with two files instead of one.
 * Deriving the count from the same budget keeps the two limits consistent.
 */
export const MAX_INLINE_IMAGES = Math.max(
  1,
  Math.floor(
    (MAX_ANNOUNCEMENT_CONTENT_CHARS - TEXT_RESERVE_CHARS - SAFETY_MARGIN_CHARS) /
      worstCaseImageChars(),
  ),
);

/**
 * The worst document the editor can compose: every permitted image at the
 * per-image cap plus the full text reserve. Exposed so tests can prove the
 * invariant against the server cap instead of re-deriving the arithmetic.
 */
export function worstCaseContentChars(): number {
  return MAX_INLINE_IMAGES * worstCaseImageChars() + TEXT_RESERVE_CHARS;
}

/** Content length at which the editor starts warning the author. */
export const CONTENT_LENGTH_WARN_CHARS = Math.floor(
  MAX_ANNOUNCEMENT_CONTENT_CHARS * 0.8,
);

export type AnnouncementContentLengthState = 'ok' | 'warning' | 'exceeded';

export function assessAnnouncementContentLength(
  content: string,
): AnnouncementContentLengthState {
  return classifyAnnouncementContentLength(content).state;
}

export interface AnnouncementContentLengthAssessment {
  state: AnnouncementContentLengthState;
  /** Characters in the composed content. */
  length: number;
  /** Characters the server would accept. */
  limit: number;
  /** How many characters must be removed; 0 unless `state` is 'exceeded'. */
  excessChars: number;
}

export function classifyAnnouncementContentLength(
  content: string,
): AnnouncementContentLengthAssessment {
  const length = content?.length ?? 0;
  if (length > MAX_ANNOUNCEMENT_CONTENT_CHARS) {
    return {
      state: 'exceeded',
      length,
      limit: MAX_ANNOUNCEMENT_CONTENT_CHARS,
      excessChars: length - MAX_ANNOUNCEMENT_CONTENT_CHARS,
    };
  }
  return {
    state: length >= CONTENT_LENGTH_WARN_CHARS ? 'warning' : 'ok',
    length,
    limit: MAX_ANNOUNCEMENT_CONTENT_CHARS,
    excessChars: 0,
  };
}

/**
 * The blocking decision for a publish attempt.
 *
 * A warning is not a gate. Publishing over the cap reaches the server, comes back
 * as an opaque 400, and leaves the author with a generic failure and no idea what
 * to change — so both authoring surfaces call this before the request and refuse
 * with a specific, actionable message instead. Returns null when the content may
 * be sent.
 */
export function findAnnouncementLengthViolation(
  content: string,
): AnnouncementContentLengthAssessment | null {
  const assessment = classifyAnnouncementContentLength(content);
  return assessment.state === 'exceeded' ? assessment : null;
}

/**
 * The author-facing message for a blocked publish.
 *
 * Lives here so every authoring surface says the same thing and gives the same two
 * numbers. The previous behaviour — send, receive an opaque 400, show "could not
 * publish" — told the author nothing about what to change, which is why the
 * blocking gate is paired with a concrete message rather than a bare refusal.
 */
export function announcementLengthViolationMessage(
  violation: AnnouncementContentLengthAssessment,
  locale: 'vi' | 'en',
): string {
  const tag = locale === 'vi' ? 'vi-VN' : 'en-US';
  const limit = violation.limit.toLocaleString(tag);
  const excess = violation.excessChars.toLocaleString(tag);
  return locale === 'vi'
    ? `Nội dung vượt quá giới hạn ${limit} ký tự (cần bỏ bớt ${excess} ký tự). Hãy rút gọn văn bản hoặc bỏ bớt hình ảnh.`
    : `The content exceeds the ${limit} character limit by ${excess} characters. Shorten the text or remove an image.`;
}
