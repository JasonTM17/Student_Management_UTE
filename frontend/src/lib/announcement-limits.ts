/**
 * RT-P1-2: the announcement content limits that must stay satisfiable
 * together. The server (`AnnouncementWriteService.java`) rejects bodies over
 * `MAX_ANNOUNCEMENT_CONTENT_CHARS` characters *after* the author has finished
 * writing, which used to make publish permanently impossible: the editor
 * allowed one inline base64 image of 1,000,000 bytes, whose base64 encoding
 * alone is ~1.37M characters — 6.8x the entire server budget.
 *
 * The client image cap below is DERIVED from the server cap, not an
 * independent magic number: one image at the cap plus a full text reserve
 * can never exceed the server limit (see `MAX_INLINE_IMAGE_BYTES`). A second
 * guard, `assessAnnouncementContentLength`, watches the whole document so
 * several smaller images or a long text cannot creep past the budget
 * unannounced — the warning shows while composing, before any publish attempt.
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

/**
 * The worst document the editor can compose: one image at the client cap plus
 * the full text reserve. Exposed so tests can prove the invariant against the
 * server cap instead of re-deriving the arithmetic.
 */
export function worstCaseContentChars(): number {
  const base64Chars = 4 * Math.ceil(MAX_INLINE_IMAGE_BYTES / 3);
  return base64Chars + IMAGE_MARKUP_OVERHEAD_CHARS + TEXT_RESERVE_CHARS;
}

/** Content length at which the editor starts warning the author. */
export const CONTENT_LENGTH_WARN_CHARS = Math.floor(
  MAX_ANNOUNCEMENT_CONTENT_CHARS * 0.8,
);

export type AnnouncementContentLengthState = 'ok' | 'warning' | 'exceeded';

export function assessAnnouncementContentLength(
  content: string,
): AnnouncementContentLengthState {
  const length = content?.length ?? 0;
  if (length > MAX_ANNOUNCEMENT_CONTENT_CHARS) return 'exceeded';
  if (length >= CONTENT_LENGTH_WARN_CHARS) return 'warning';
  return 'ok';
}
