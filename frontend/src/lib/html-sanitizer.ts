/**
 * Allowlist HTML sanitizer for announcement bodies.
 *
 * Announcements are authored by lecturers and administrators and rendered with
 * `dangerouslySetInnerHTML`, so this is a trust boundary. The previous
 * implementation stripped attributes with a blacklist, and a leading `\s` in its
 * `on\w+` pattern meant `<img src=x/onerror=…>` and `<svg/onload=…>` survived —
 * a stored-XSS path reachable by any lecturer account and executed in every
 * student's feed.
 *
 * This module inverts the approach: every tag is rebuilt from an allowlist, so
 * any attribute the allowlist does not name is dropped no matter how it is
 * spelled. It is deliberately DOM-free (no `DOMParser`) so the server render and
 * the client hydration produce byte-identical markup and cannot disagree about
 * what is safe.
 */

const ALLOWED_TAGS = new Set([
  'a', 'abbr', 'b', 'blockquote', 'br', 'caption', 'code', 'col', 'colgroup',
  'dd', 'div', 'dl', 'dt', 'em', 'figcaption', 'figure', 'h1', 'h2', 'h3', 'h4',
  'h5', 'h6', 'hr', 'i', 'img', 'li', 'mark', 'ol', 'p', 'pre', 's', 'small',
  'span', 'strong', 'sub', 'sup', 'table', 'tbody', 'td', 'tfoot', 'th',
  'thead', 'time', 'tr', 'u', 'ul',
]);

/**
 * Elements whose *content* is also discarded. Dropping only the tag would leave
 * script or stylesheet source visible as escaped text in the announcement.
 */
const DROP_WITH_CONTENT = new Set([
  'script', 'style', 'template', 'iframe', 'object', 'embed', 'noscript', 'svg',
  'math', 'form',
]);

const VOID_TAGS = new Set(['br', 'hr', 'col', 'img']);

const GLOBAL_ATTRS = new Set(['class', 'title', 'dir', 'lang', 'align']);

const TAG_ATTRS: Record<string, readonly string[]> = {
  a: ['href', 'target', 'rel'],
  img: ['src', 'alt', 'width', 'height', 'loading'],
  td: ['colspan', 'rowspan', 'scope', 'headers'],
  th: ['colspan', 'rowspan', 'scope', 'headers'],
  ol: ['start', 'type'],
  time: ['datetime'],
  col: ['span', 'width'],
  colgroup: ['span'],
};

/** Navigation targets only; `data:` and `javascript:` are absent by design. */
const SAFE_URL = /^(?:https?:|mailto:|tel:|#|\/(?!\/)|\.{1,2}\/)/i;
/** `src` additionally permits the base64 image payloads the editor inlines. */
const SAFE_IMAGE_URL =
  /^(?:https?:|data:image\/(?:png|jpe?g|gif|webp|bmp);base64,[a-z0-9+/=\s]+$|\/(?!\/)|\.{1,2}\/)/i;
const UNSAFE_STYLE =
  /(?:expression\s*\(|javascript:|vbscript:|@import|behavior\s*:|-moz-binding|url\s*\(\s*['"]?\s*(?:javascript|vbscript|data\s*:\s*text\/html))/i;

const TAG_RE = /<(\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:"[^"]*"|'[^']*'|[^>"'])*?)(\/?)>/g;
const ATTR_RE = /([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*("[^"]*"|'[^']*'|[^\s"'=<>`]+)|([a-zA-Z_:][-a-zA-Z0-9_:.]*)/g;

/**
 * Entity references and control characters are decoded before a scheme test so
 * `javas&#99;ript:` and `java\tscript:` cannot slip past, while the original
 * value is what gets re-serialized.
 */
function normalizeForSchemeCheck(value: string): string {
  return value
    .replace(/&#x([0-9a-f]+);?/gi, (_m, hex: string) => {
      const code = Number.parseInt(hex, 16);
      return Number.isFinite(code) ? String.fromCodePoint(code) : _m;
    })
    .replace(/&#(\d+);?/g, (_m, dec: string) => {
      const code = Number.parseInt(dec, 10);
      return Number.isFinite(code) ? String.fromCodePoint(code) : _m;
    })
    .replace(/&(?:amp|lt|gt|quot|apos|nbsp|#39);/gi, (m) => {
      const named: Record<string, string> = {
        '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"',
        '&apos;': "'", '&nbsp;': ' ', '&#39;': "'",
      };
      return named[m.toLowerCase()] ?? m;
    })
    .replace(/[\u0000-\u0020\u00a0\u2028\u2029]/g, '')
    .toLowerCase();
}

function escapeText(text: string): string {
  return text
    // Keep already-valid entities intact so "&amp;" is not double-escaped.
    .replace(/&(?!#?[a-zA-Z0-9]+;)/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function sanitizeAttributes(tag: string, rawAttributes: string): string {
  const allowed = new Set([...GLOBAL_ATTRS, ...(TAG_ATTRS[tag] ?? [])]);
  let out = '';
  let match: RegExpExecArray | null;
  ATTR_RE.lastIndex = 0;
  while ((match = ATTR_RE.exec(rawAttributes)) !== null) {
    const name = (match[1] ?? match[3] ?? '').toLowerCase();
    // A bare attribute without a value carries no meaning here and is dropped.
    if (!name || !allowed.has(name) || match[1] === undefined) continue;
    let value = match[2] ?? '';
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (name === 'style') {
      if (UNSAFE_STYLE.test(normalizeForSchemeCheck(value))) continue;
    } else if (name === 'href') {
      if (!SAFE_URL.test(normalizeForSchemeCheck(value))) continue;
    } else if (name === 'src') {
      if (!SAFE_IMAGE_URL.test(normalizeForSchemeCheck(value))) continue;
    } else if (name === 'target' && !/^_(?:blank|self|parent|top)$/i.test(value)) {
      continue;
    }
    out += ` ${name}="${value.replace(/"/g, '&quot;').replace(/[\r\n\u0000]/g, '')}"`;
  }
  if (tag === 'a' && /\btarget="_blank"/.test(out) && !/\brel=/.test(out)) {
    out += ' rel="noopener noreferrer"';
  }
  return out;
}

export function sanitizeAnnouncementHtml(html: string): string {
  if (!html) return '';
  let out = '';
  let cursor = 0;
  let match: RegExpExecArray | null;
  TAG_RE.lastIndex = 0;
  while ((match = TAG_RE.exec(html)) !== null) {
    const closing = match[1] === '/';
    const name = match[2].toLowerCase();

    if (DROP_WITH_CONTENT.has(name)) {
      const closeAt = html.toLowerCase().indexOf(`</${name}`, TAG_RE.lastIndex);
      if (closeAt !== -1) {
        const afterClose = html.indexOf('>', closeAt);
        TAG_RE.lastIndex = afterClose === -1 ? html.length : afterClose + 1;
      }
      out += escapeText(html.slice(cursor, match.index));
      cursor = TAG_RE.lastIndex;
      continue;
    }

    out += escapeText(html.slice(cursor, match.index));
    cursor = match.index + match[0].length;

    if (!ALLOWED_TAGS.has(name) || (closing && VOID_TAGS.has(name))) continue;
    if (closing) {
      out += `</${name}>`;
      continue;
    }
    out += `<${name}${sanitizeAttributes(name, match[3] ?? '')}${VOID_TAGS.has(name) ? ' /' : ''}>`;
  }
  out += escapeText(html.slice(cursor));
  return out;
}
