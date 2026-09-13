/**
 * Inline markdown pattern used by the assistant message renderer.
 * Groups: 1 link, 4 code, 6 bold-italic, 8 bold, 10 italic,
 * 12 direct internal route (/dashboard/... or /admin/...).
 *
 * The route branch must NOT use a leading \b: word boundaries never match
 * between two non-word characters, so "(/dashboard/register)" and
 * "mở /dashboard/schedule" stayed dead text with the old pattern. Explicit
 * lookaround boundaries keep the link detection working after spaces,
 * parentheses, and punctuation.
 */
export const ASSISTANT_INLINE_MARKDOWN_REGEX =
  /(!?\[([^\]]+)\]\(([^)]+)\))|(`([^`]+)`)|(\*\*\*([^*]+)\*\*\*)|(\*\*([^*]+)\*\*)|(\*([^*]+)\*)|(?<![\p{L}\p{N}_])(\/(?:dashboard|admin)(?:\/[\p{L}\p{N}_-]+)*)(?![\p{L}\p{N}_-])/gu;

const countMatches = (text: string, pattern: RegExp) =>
  (text.match(pattern) ?? []).length;

/**
 * While a message is still streaming, the newest chunk can end inside an
 * unclosed markdown construct ("**đang", "`code", "[label]("). Rendering the
 * raw markers flashes asterisks and brackets at the user. This trims only a
 * trailing incomplete construct; completed markdown is never touched.
 */
export function sanitizeStreamingMarkdown(text: string): string {
  if (!text) return text;
  let out = text;
  // Open link: [label](url… without a closing parenthesis.
  out = out.replace(/!?\[[^\]]*\]\([^)]*$/, '');
  // Bare open bracket: [label… or ![alt…
  out = out.replace(/!?\[[^\]]*$/, '');
  // Unbalanced inline code span.
  if (countMatches(out, /`/g) % 2 === 1) {
    out = out.replace(/`[^`\n]*$/, '');
  }
  // Unbalanced emphasis markers, longest first.
  if (countMatches(out, /\*\*\*/g) % 2 === 1) {
    out = out.replace(/\*\*\*[^*]*$/, '');
  } else if (countMatches(out, /\*\*(?!\*)/g) % 2 === 1) {
    out = out.replace(/\*\*(?!\*)[^*]*$/, '');
  } else if (countMatches(out, /(?<!\*)\*(?!\*)/g) % 2 === 1) {
    out = out.replace(/(?<!\*)\*(?!\*)[^*]*$/, '');
  }
  return out;
}
