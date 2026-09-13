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

export type AssistantRenderBlock =
  | { type: 'table'; lines: string[] }
  | { type: 'code'; language: string; code: string; lines: string[] }
  | { type: 'text'; lines: string[] };

const SPECIAL_LINE =
  /^(?:#{1,4}\s|\d+\.\s|[*•\-]\s|>)/;

/**
 * Split assistant markdown into render blocks. Consecutive plain-text lines
 * are joined into one paragraph (a model that wraps mid-sentence must not
 * render as a stack of broken one-line paragraphs); headings, list items,
 * blockquotes, blank lines, pipe tables, and fenced code blocks stay properly scoped.
 */
export function splitAssistantBlocks(content: string): AssistantRenderBlock[] {
  if (!content) return [];
  const blocks: AssistantRenderBlock[] = [];
  let table: string[] = [];
  let paragraph: string[] = [];
  let inCode = false;
  let codeLang = '';
  let codeLines: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      blocks.push({ type: 'text', lines: [paragraph.join(' ')] });
      paragraph = [];
    }
  };
  const flushTable = () => {
    if (table.length > 0) {
      blocks.push({ type: 'table', lines: [...table] });
      table = [];
    }
  };

  for (const line of content.split('\n')) {
    const trimmed = line.trim();

    if (trimmed.startsWith('```')) {
      flushParagraph();
      flushTable();
      if (inCode) {
        const fullCode = codeLines.join('\n');
        blocks.push({
          type: 'code',
          language: codeLang,
          code: fullCode,
          lines: [fullCode],
        });
        inCode = false;
        codeLang = '';
        codeLines = [];
      } else {
        inCode = true;
        codeLang = trimmed.slice(3).trim();
        codeLines = [];
      }
      continue;
    }

    if (inCode) {
      codeLines.push(line);
      continue;
    }

    if (trimmed.startsWith('|') && trimmed.endsWith('|')) {
      flushParagraph();
      table.push(trimmed);
      continue;
    }
    flushTable();
    if (!trimmed) {
      flushParagraph();
      continue;
    }
    if (SPECIAL_LINE.test(trimmed)) {
      flushParagraph();
      blocks.push({ type: 'text', lines: [trimmed] });
      continue;
    }
    paragraph.push(trimmed);
  }

  if (inCode) {
    const fullCode = codeLines.join('\n');
    blocks.push({
      type: 'code',
      language: codeLang,
      code: fullCode,
      lines: [fullCode],
    });
  }

  flushParagraph();
  flushTable();
  return blocks;
}

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
