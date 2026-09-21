'use client';

import React, { useMemo, useState } from 'react';

import {
  isSafeAnnouncementImageUrl,
  isSafeAnnouncementUrl,
  sanitizeAnnouncementHtml,
} from '@/lib/html-sanitizer';
import {
  AlertCircle,
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  ImageOff,
  Info,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export interface RichContentRendererProps {
  content?: string | null;
  className?: string;
  fallbackText?: string;
}

export function slugify(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)+/g, '');
}

/** One table-of-contents / anchor target. */
export interface ArticleHeading {
  id: string;
  text: string;
  level: number;
}

/**
 * RT-P3-a: TinyMCE headings arrive as bare `<h1>`..`<h6>` with no anchor, so a
 * `dangerouslySetInnerHTML` article had nothing for the table of contents to
 * link to. The TOC and the rendered markup are produced by the same walker
 * below on purpose: two independent slug implementations drift, and a drifting
 * anchor silently scrolls the reader to the top of the page.
 */
const HTML_HEADING_SOURCE = '<h([1-6])([^>]*)>([\\s\\S]*?)<\\/h\\1\\s*>';

function htmlHeadingPattern(): RegExp {
  // Fresh instance per walk: `lastIndex` on a shared /g regex leaks between
  // calls and would skip headings on the second body parsed.
  return new RegExp(HTML_HEADING_SOURCE, 'gi');
}

/** Heading text with markup and character references removed. */
function headingTextFromHtml(innerHtml: string): string {
  return innerHtml
    .replace(/<[^>]*>/g, ' ')
    // Character references are literal in authored HTML and re-escaped in
    // sanitizer output; decoding them here keeps both paths on one slug.
    .replace(/&(?:[a-z]+|#\d+);/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Repeated heading text must not produce colliding ids: the second occurrence
 * of the same title gets a `-2` suffix, the third `-3`, and so on.
 */
export function allocateHeadingId(used: Map<string, number>, base: string): string {
  if (!base) return '';
  const seen = (used.get(base) ?? 0) + 1;
  used.set(base, seen);
  return seen === 1 ? base : `${base}-${seen}`;
}

interface ResolvedHtmlHeading {
  level: number;
  text: string;
  /** Null when the heading has neither text nor anchor: nothing to link to. */
  id: string | null;
  /** The author supplied the anchor (editor `anchor` plugin), so leave it be. */
  authored: boolean;
}

function resolveHtmlHeadings(html: string): ResolvedHtmlHeading[] {
  const pattern = htmlHeadingPattern();
  const resolved: ResolvedHtmlHeading[] = [];
  const used = new Map<string, number>();
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(html)) !== null) {
    const level = Number(match[1]);
    const text = headingTextFromHtml(match[3] ?? '');
    const authoredMatch = /\bid\s*=\s*(?:"([^"]*)"|'([^']*)')/i.exec(match[2] ?? '');
    const authoredId = (authoredMatch?.[1] || authoredMatch?.[2] || '').trim();
    if (authoredId) used.set(authoredId, (used.get(authoredId) ?? 0) + 1);
    resolved.push({ level, text, id: authoredId || null, authored: Boolean(authoredId) });
  }

  // Generated slugs are allocated in a second pass so every authored anchor is
  // already in `used`. Reserving while generating would let an earlier
  // `<h2>Quy chế</h2>` claim `quy-che` before an authored `id="quy-che"` further
  // down is even seen, and the two anchors would then collide.
  return resolved.map((heading) => {
    if (heading.authored) {
      return { level: heading.level, text: heading.text, id: heading.id, authored: true };
    }
    const id = allocateHeadingId(used, slugify(heading.text));
    return { level: heading.level, text: heading.text, id: id || null, authored: false };
  });
}

/**
 * Anchors and labels for every heading in an HTML body, in document order.
 * Shares `resolveHtmlHeadings` with `annotateHtmlHeadingIds` so the contents and
 * the rendered ids cannot drift apart.
 */
export function extractArticleHeadingsFromHtml(html: string): ArticleHeading[] {
  if (!html) return [];
  const headings: ArticleHeading[] = [];
  for (const heading of resolveHtmlHeadings(html)) {
    if (heading.id) headings.push({ id: heading.id, text: heading.text, level: heading.level });
  }
  return headings;
}

/**
 * Adds the matching `id` to each heading element. Runs on sanitizer output —
 * the sanitizer rejects ids that do not start with a letter, which is common
 * for numbered headings ("2025 Quy chế"), and an injected slug is limited to
 * `[a-z0-9-]`, so this cannot introduce markup.
 */
export function annotateHtmlHeadingIds(html: string): string {
  if (!html || !/<h[1-6]/i.test(html)) return html;
  const resolved = resolveHtmlHeadings(html);
  if (resolved.length === 0) return html;
  let index = 0;
  return html.replace(
    htmlHeadingPattern(),
    (whole: string, tag: string, attrs: string, inner: string) => {
      const heading = resolved[index++];
      if (!heading || heading.authored || !heading.id) return whole;
      return `<h${tag}${attrs} id="${heading.id}">${inner}</h${tag}>`;
    },
  );
}

/**
 * Markdown headings resolved with the same de-duplication the TOC applies, so
 * `# Phụ lục` twice yields `phu-luc` and `phu-luc-2`.
 */
export function resolveMarkdownHeading(
  rawText: string,
  level: number,
  used: Map<string, number>,
): ArticleHeading | null {
  const text = rawText.replace(/[*_`]/g, '').trim();
  if (!text) return null;
  const id = allocateHeadingId(used, slugify(text));
  // A heading whose text has no slug characters at all (`### ***`) cannot carry
  // a valid id, so it stays out of the contents instead of shipping a dead link.
  if (!id) return null;
  return { id, text, level };
}

/** Per-level markdown heading styling, kept in one table so h1-h6 all render. */
const MARKDOWN_HEADING_CLASS_NAMES: Record<number, string> = {
  1: 'mt-6 mb-4 text-2xl font-extrabold tracking-tight text-foreground border-b-2 border-primary/40 pb-2',
  2: 'mt-6 mb-3 text-xl font-bold tracking-tight text-foreground border-b border-border/60 pb-1.5',
  3: 'mt-5 mb-2 text-lg font-semibold text-foreground',
  4: 'mt-4 mb-2 text-base font-semibold text-foreground',
  5: 'mt-4 mb-2 text-sm font-semibold text-foreground',
  6: 'mt-3 mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground',
};

function SafeImage({ src, alt }: { src: string; alt: string }) {
  const [hasError, setHasError] = useState(false);

  if (hasError) {
    return (
      <span className="my-3 flex flex-col items-center justify-center rounded-lg border border-dashed border-border/80 bg-secondary/30 p-6 text-center text-xs text-muted-foreground">
        <ImageOff className="mb-2 h-6 w-6 text-muted-foreground/60" />
        <span>{alt ? `[Hình ảnh không tải được: ${alt}]` : '[Hình ảnh minh họa không thể hiển thị]'}</span>
      </span>
    );
  }

  return (
    <span className="my-2 inline-block max-w-full">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img
        src={src}
        alt={alt}
        className="max-h-96 rounded-md border border-border/70 object-cover shadow-sm"
        loading="lazy"
        onError={() => setHasError(true)}
      />
      {alt ? (
        <span className="mt-1 block text-center text-xs text-muted-foreground">{alt}</span>
      ) : null}
    </span>
  );
}

// RT-P1-1: the markdown branch once carried its own weaker `isSafeUrl` that
// only looked for a `javascript:`/`data:`/`vbscript:` prefix after a trim.
// Browsers strip `\t\n\r` from URLs before resolving them, so
// `[x](java\tscript:alert(1))` slipped past and fired in every reader's
// browser — stored XSS authored by anyone with announcement rights. The
// markdown branch now delegates to the same scheme policy the HTML branch
// uses, exported from `lib/html-sanitizer.ts`; there is deliberately no
// second regex here.

// Inline token rendering
function renderInline(text: string): React.ReactNode[] {
  // Regex to match inline patterns:
  // 1. Links: [text](url)
  // 2. Images: ![alt](url)
  // 3. Bold/Italic: ***text***
  // 4. Bold: **text**
  // 5. Italic: *text*
  // 6. Strikethrough: ~~text~~
  // 7. Inline code: `code`
  const regex = /(!?\[([^\]]*)\]\(([^)]+)\))|(\*\*\*([^*]+)\*\*\*)|(\*\*([^*]+)\*\*)|(\*([^*]+)\*)|(~~([^~]+)~~)|(`([^`]+)`)/g;

  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let keyIndex = 0;

  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }

    const fullMatch = match[0];

    if (fullMatch.startsWith('![')) {
      // Image
      const alt = match[2] || '';
      const url = match[3] || '';
      if (isSafeAnnouncementImageUrl(url)) {
        parts.push(
          <SafeImage key={`img-${keyIndex++}`} src={url} alt={alt} />
        );
      } else {
        parts.push(`[Blocked image: ${alt}]`);
      }
    } else if (fullMatch.startsWith('[')) {
      // Link
      const label = match[2] || '';
      const url = match[3] || '';
      if (isSafeAnnouncementUrl(url)) {
        const isExternal = url.startsWith('http://') || url.startsWith('https://');
        parts.push(
          <a
            key={`link-${keyIndex++}`}
            href={url}
            target={isExternal ? '_blank' : undefined}
            rel={isExternal ? 'noopener noreferrer' : undefined}
            className="inline-flex items-center gap-1 font-medium text-primary underline underline-offset-4 transition-colors hover:text-primary/80"
          >
            <span>{label || url}</span>
            {isExternal ? <ExternalLink className="inline h-3 w-3 shrink-0 opacity-70" aria-hidden="true" /> : null}
          </a>
        );
      } else {
        parts.push(label || fullMatch);
      }
    } else if (match[4]) {
      // Bold + Italic (***text***)
      parts.push(
        <strong key={`bi-${keyIndex++}`} className="font-semibold italic text-foreground">
          {match[5]}
        </strong>
      );
    } else if (match[6]) {
      // Bold (**text**)
      parts.push(
        <strong key={`b-${keyIndex++}`} className="font-semibold text-foreground">
          {match[7]}
        </strong>
      );
    } else if (match[8]) {
      // Italic (*text*)
      parts.push(
        <em key={`i-${keyIndex++}`} className="italic text-foreground/90">
          {match[9]}
        </em>
      );
    } else if (match[10]) {
      // Strikethrough (~~text~~)
      parts.push(
        <del key={`del-${keyIndex++}`} className="text-muted-foreground line-through">
          {match[11]}
        </del>
      );
    } else if (match[12]) {
      // Inline Code (`code`)
      parts.push(
        <code
          key={`code-${keyIndex++}`}
          className="rounded border border-border/80 bg-secondary/80 px-1.5 py-0.5 font-mono text-[0.88em] font-medium text-foreground"
        >
          {match[13]}
        </code>
      );
    }

    lastIndex = regex.lastIndex;
  }

  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }

  return parts;
}

// Code Block with Copy
function CodeBlock({ code, language }: { code: string; language?: string }) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(code).then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }).catch(() => {});
    }
  };

  return (
    <div className="relative my-4 overflow-hidden rounded-lg border border-border/80 bg-secondary/40 text-foreground">
      <div className="flex items-center justify-between border-b border-border/60 bg-secondary/60 px-4 py-1.5 text-xs font-mono text-muted-foreground">
        <span>{language || 'code'}</span>
        <button
          type="button"
          onClick={handleCopy}
          className="inline-flex items-center gap-1 rounded px-2 py-0.5 text-xs font-medium text-muted-foreground transition-colors hover:bg-background hover:text-foreground"
          aria-label="Sao chép mã nguồn"
        >
          {copied ? (
            <>
              <Check className="h-3.5 w-3.5 text-status-success-foreground" />
              <span className="text-status-success-foreground">Đã chép</span>
            </>
          ) : (
            <>
              <Copy className="h-3.5 w-3.5" />
              <span>Sao chép</span>
            </>
          )}
        </button>
      </div>
      <pre className="overflow-x-auto p-4 font-mono text-xs leading-relaxed">
        <code>{code}</code>
      </pre>
    </div>
  );
}

// Callout Alert Box
type AlertType = 'note' | 'tip' | 'important' | 'warning' | 'caution';

function AlertCallout({ type, children }: { type: AlertType; children: React.ReactNode }) {
  const styles: Record<AlertType, { border: string; bg: string; icon: React.ReactNode; label: string; text: string }> = {
    note: {
      border: 'border-blue-500/40 dark:border-blue-400/30',
      bg: 'bg-blue-50/50 dark:bg-blue-950/20',
      icon: <Info className="h-4 w-4 text-blue-600 dark:text-blue-400 shrink-0" />,
      label: 'LƯU Ý (NOTE)',
      text: 'text-blue-900 dark:text-blue-200',
    },
    tip: {
      border: 'border-emerald-500/40 dark:border-emerald-400/30',
      bg: 'bg-emerald-50/50 dark:bg-emerald-950/20',
      icon: <CheckCircle2 className="h-4 w-4 text-emerald-600 dark:text-emerald-400 shrink-0" />,
      label: 'MẸO (TIP)',
      text: 'text-emerald-900 dark:text-emerald-200',
    },
    important: {
      border: 'border-purple-500/40 dark:border-purple-400/30',
      bg: 'bg-purple-50/50 dark:bg-purple-950/20',
      icon: <AlertCircle className="h-4 w-4 text-purple-600 dark:text-purple-400 shrink-0" />,
      label: 'QUAN TRỌNG (IMPORTANT)',
      text: 'text-purple-900 dark:text-purple-200',
    },
    warning: {
      border: 'border-amber-500/40 dark:border-amber-400/30',
      bg: 'bg-amber-50/50 dark:bg-amber-950/20',
      icon: <AlertTriangle className="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" />,
      label: 'CẢNH BÁO (WARNING)',
      text: 'text-amber-900 dark:text-amber-200',
    },
    caution: {
      border: 'border-rose-500/40 dark:border-rose-400/30',
      bg: 'bg-rose-50/50 dark:bg-rose-950/20',
      icon: <AlertTriangle className="h-4 w-4 text-rose-600 dark:text-rose-400 shrink-0" />,
      label: 'CHÚ Ý NGUY CƠ (CAUTION)',
      text: 'text-rose-900 dark:text-rose-200',
    },
  };

  const style = styles[type] || styles.note;

  return (
    <div className={cn('my-4 rounded-lg border-l-4 p-4 shadow-sm', style.border, style.bg)}>
      <div className="flex items-center gap-2 font-semibold text-xs tracking-wider uppercase">
        {style.icon}
        <span className={style.text}>{style.label}</span>
      </div>
      <div className="mt-2 text-sm leading-relaxed text-foreground/90">
        {children}
      </div>
    </div>
  );
}

// Table renderer
function MarkdownTable({ rows }: { rows: string[] }) {
  if (rows.length < 2) return null;

  // Split cell by pipe '|'
  const parseRow = (line: string) => {
    const trimmed = line.trim();
    const withoutEdges = trimmed.replace(/^\|/, '').replace(/\|$/, '');
    return withoutEdges.split('|').map((c) => c.trim());
  };

  const headerCells = parseRow(rows[0]);
  const isSeparator = /^\|?[\s-:|]+\|?$/.test(rows[1]);
  const dataRows = (isSeparator ? rows.slice(2) : rows.slice(1)).map(parseRow);

  return (
    <div className="my-4 overflow-x-auto rounded-lg border border-border/70 shadow-sm">
      <table className="w-full border-collapse text-left text-sm">
        <thead className="border-b border-border/80 bg-secondary/70">
          <tr>
            {headerCells.map((cell, idx) => (
              <th
                key={idx}
                className="px-4 py-2.5 font-semibold text-foreground tracking-wide"
              >
                {renderInline(cell)}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border/50 bg-card">
          {dataRows.map((rowCells, rowIdx) => (
            <tr
              key={rowIdx}
              className="transition-colors hover:bg-secondary/30 odd:bg-card even:bg-secondary/15"
            >
              {rowCells.map((cell, cellIdx) => (
                <td key={cellIdx} className="px-4 py-2 text-foreground/90 leading-6">
                  {renderInline(cell)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/**
 * HTML bodies are rebuilt from an allowlist. The previous attribute blacklist
 * let `<img src=x/onerror=…>` through because its `on\w+` pattern required a
 * leading whitespace, which made every lecturer-authored announcement a
 * stored-XSS vector for student feeds.
 */
function sanitizeHtml(html: string): string {
  return sanitizeAnnouncementHtml(html);
}

/**
 * RT-P2-3: TinyMCE emits bare fragments from ordinary authoring — a bulleted
 * list is `<ul><li>…</li></ul>`, a quote is `<blockquote>…</blockquote>` — and
 * the old leading-tag allowlist missed all of them, so those announcements
 * fell into the markdown converter, which does not understand those constructs
 * and printed the raw tags as literal text to students. Detection is now
 * structural: any element-shaped markup outside a fenced code block means the
 * body is HTML and must go through the sanitizer/render branch.
 */
export function isHtmlDocument(raw: string): boolean {
  if (!raw) return false;
  const withoutFences = raw.replace(/```[\s\S]*?```/g, '');
  return /<\/?[a-zA-Z][a-zA-Z0-9-]*(?:\s[^<>]*)?>/.test(withoutFences);
}

/**
 * The whole HTML render computation, as one pure function of the body: sanitize,
 * then anchor the headings. Keeping it in a single callable is what lets the
 * component cache it (see `RichContentRenderer`) instead of rebuilding a
 * 200k-character document on every scroll frame, and lets a test assert the
 * output is byte-stable for a given input.
 */
export function renderHtmlDocument(content: string): string {
  return annotateHtmlHeadingIds(sanitizeHtml(content));
}

function normalizeHtmlToMarkdown(raw: string): string {
  if (!raw || !raw.includes('<')) return raw;
  return raw
    .replace(/<p[^>]*>/gi, '')
    .replace(/<\/p>/gi, '\n\n')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<strong[^>]*>(.*?)<\/strong>/gi, '**$1**')
    .replace(/<b[^>]*>(.*?)<\/b>/gi, '**$1**')
    .replace(/<em[^>]*>(.*?)<\/em>/gi, '*$1*')
    .replace(/<i[^>]*>(.*?)<\/i>/gi, '*$1*')
    .replace(/<li[^>]*>(.*?)<\/li>/gi, '- $1\n')
    .replace(/<ul[^>]*>/gi, '')
    .replace(/<\/ul>/gi, '\n')
    .replace(/<ol[^>]*>/gi, '')
    .replace(/<\/ol>/gi, '\n')
    .replace(/<h1[^>]*>(.*?)<\/h1>/gi, '# $1\n\n')
    .replace(/<h2[^>]*>(.*?)<\/h2>/gi, '## $1\n\n')
    .replace(/<h3[^>]*>(.*?)<\/h3>/gi, '### $1\n\n')
    .replace(/<div[^>]*>/gi, '')
    .replace(/<\/div>/gi, '\n');
}

/**
 * RichContentRenderer:
 * Renders Markdown-like formatted academic notices, guides, and articles
 * or rich HTML outputs generated by TinyMCE safely and cleanly.
 */
export function RichContentRenderer({
  content,
  className,
  fallbackText,
}: RichContentRendererProps) {
  // RT-P3-f: the reader re-renders on every scroll frame, and the allowlist
  // rebuild walks the whole body (up to the server's 200k-character cap). The
  // result depends only on `content`, so it is cached: same bytes in, same bytes
  // out, sanitized once per document instead of once per frame.
  const htmlBody = useMemo(() => {
    if (!content || !content.trim()) return null;
    if (!isHtmlDocument(content)) return null;
    return renderHtmlDocument(content);
  }, [content]);

  if (htmlBody !== null) {
    return (
      <div
        className={cn('space-y-2 text-foreground break-words rich-html-content', className)}
        dangerouslySetInnerHTML={{ __html: htmlBody }}
      />
    );
  }

  if (!content || !content.trim()) {
    if (fallbackText) {
      return <p className={cn('text-sm text-muted-foreground italic', className)}>{fallbackText}</p>;
    }
    return null;
  }

  const normalized = normalizeHtmlToMarkdown(content);
  const lines = normalized.replace(/\r\n/g, '\n').split('\n');
  const elements: React.ReactNode[] = [];
  const usedHeadingIds = new Map<string, number>();
  let i = 0;
  let elementKey = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    // Empty line
    if (!trimmed) {
      i += 1;
      continue;
    }

    // 1. Code Block (```lang)
    if (trimmed.startsWith('```')) {
      const language = trimmed.slice(3).trim();
      const codeLines: string[] = [];
      i += 1;
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(lines[i]);
        i += 1;
      }
      if (i < lines.length && lines[i].trim().startsWith('```')) {
        i += 1; // skip closing ```
      }
      elements.push(
        <CodeBlock
          key={`codeblock-${elementKey++}`}
          code={codeLines.join('\n')}
          language={language}
        />
      );
      continue;
    }

    // 2. Tables (| col | col |)
    if (trimmed.startsWith('|') && trimmed.includes('|')) {
      const tableLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith('|') && lines[i].trim().includes('|')) {
        tableLines.push(lines[i]);
        i += 1;
      }
      elements.push(
        <MarkdownTable key={`table-${elementKey++}`} rows={tableLines} />
      );
      continue;
    }

    // 3. Blockquotes & Alert Callouts
    if (trimmed.startsWith('>')) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith('>')) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ''));
        i += 1;
      }

      const firstLine = quoteLines[0] || '';
      const alertMatch = firstLine.match(/^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]/i);

      if (alertMatch) {
        const type = alertMatch[1].toLowerCase() as AlertType;
        const restLines = quoteLines.slice(1);
        elements.push(
          <AlertCallout key={`alert-${elementKey++}`} type={type}>
            {restLines.map((ql, qIdx) => (
              <p key={qIdx} className={qIdx > 0 ? 'mt-1' : ''}>
                {renderInline(ql)}
              </p>
            ))}
          </AlertCallout>
        );
      } else {
        elements.push(
          <blockquote
            key={`quote-${elementKey++}`}
            className="my-3 border-l-4 border-primary/50 bg-secondary/20 py-2 pl-4 pr-3 italic text-foreground/85 rounded-r"
          >
            {quoteLines.map((ql, qIdx) => (
              <p key={qIdx} className={qIdx > 0 ? 'mt-1' : ''}>
                {renderInline(ql)}
              </p>
            ))}
          </blockquote>
        );
      }
      continue;
    }

    // 4. Headings (`#` through `######`), anchored with the same ids the table
    //    of contents derives, so a contents row always lands on its section.
    const headingMatch = /^(#{1,6})\s+(.+)$/.exec(trimmed);
    if (headingMatch) {
      const level = headingMatch[1].length;
      const headingText = headingMatch[2].trim();
      const heading = resolveMarkdownHeading(headingText, level, usedHeadingIds);
      // `createElement` rather than a JSX tag: the element name comes from the
      // hash count, and h1-h6 all take these same three props.
      elements.push(
        React.createElement(
          `h${level}`,
          {
            key: `h${level}-${elementKey++}`,
            id: heading?.id,
            className: cn('scroll-mt-20', MARKDOWN_HEADING_CLASS_NAMES[level]),
          },
          renderInline(headingText),
        ),
      );
      i += 1;
      continue;
    }

    // 5. Horizontal Divider
    if (/^(\*{3,}|-{3,}|_{3,})$/.test(trimmed)) {
      elements.push(<hr key={`hr-${elementKey++}`} className="my-6 border-border/70" />);
      i += 1;
      continue;
    }

    // 6. Lists (Unordered, Ordered, Task Lists)
    const isUnordered = /^[-*+]\s/.test(trimmed);
    const isOrdered = /^\d+\.\s/.test(trimmed);

    if (isUnordered || isOrdered) {
      const listItems: Array<{ content: string; checked?: boolean; isTask?: boolean }> = [];
      const isCurrentOrdered = isOrdered;

      while (
        i < lines.length &&
        ((isCurrentOrdered && /^\d+\.\s/.test(lines[i].trim())) ||
          (!isCurrentOrdered && /^[-*+]\s/.test(lines[i].trim())))
      ) {
        const itemLine = lines[i].trim();
        const rawContent = isCurrentOrdered
          ? itemLine.replace(/^\d+\.\s/, '')
          : itemLine.replace(/^[-*+]\s/, '');

        // Check if task list item: [ ] or [x]
        const taskMatch = rawContent.match(/^\[([ xX])\]\s+(.*)$/);
        if (taskMatch) {
          listItems.push({
            isTask: true,
            checked: taskMatch[1].toLowerCase() === 'x',
            content: taskMatch[2],
          });
        } else {
          listItems.push({ content: rawContent });
        }
        i += 1;
      }

      if (isCurrentOrdered) {
        elements.push(
          <ol key={`ol-${elementKey++}`} className="my-3 ml-6 list-decimal space-y-1 text-sm leading-6 text-foreground/90">
            {listItems.map((item, idx) => (
              <li key={idx} className="pl-1">
                {renderInline(item.content)}
              </li>
            ))}
          </ol>
        );
      } else {
        const hasTasks = listItems.some((it) => it.isTask);
        if (hasTasks) {
          elements.push(
            <ul key={`tasklist-${elementKey++}`} className="my-3 space-y-1.5 text-sm leading-6 text-foreground/90">
              {listItems.map((item, idx) => (
                <li key={idx} className="flex items-start gap-2.5">
                  <input
                    type="checkbox"
                    readOnly
                    checked={item.checked}
                    className="mt-1 h-4 w-4 rounded border-border/80 text-primary accent-primary"
                  />
                  <span className={cn(item.checked && 'line-through text-muted-foreground')}>
                    {renderInline(item.content)}
                  </span>
                </li>
              ))}
            </ul>
          );
        } else {
          elements.push(
            <ul key={`ul-${elementKey++}`} className="my-3 ml-6 list-disc space-y-1 text-sm leading-6 text-foreground/90">
              {listItems.map((item, idx) => (
                <li key={idx} className="pl-1">
                  {renderInline(item.content)}
                </li>
              ))}
            </ul>
          );
        }
      }
      continue;
    }

    // 7. Regular Paragraph
    // Group consecutive normal lines into a single paragraph with line breaks
    const paraLines: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() &&
      !lines[i].trim().startsWith('```') &&
      !lines[i].trim().startsWith('#') &&
      !lines[i].trim().startsWith('>') &&
      !lines[i].trim().startsWith('|') &&
      !/^[-*+]\s/.test(lines[i].trim()) &&
      !/^\d+\.\s/.test(lines[i].trim()) &&
      !/^(\*{3,}|-{3,}|_{3,})$/.test(lines[i].trim())
    ) {
      paraLines.push(lines[i]);
      i += 1;
    }

    elements.push(
      <p key={`p-${elementKey++}`} className="my-2.5 text-sm sm:text-base leading-7 text-foreground/90 text-left sm:text-justify hyphens-auto">
        {paraLines.map((pl, plIdx) => (
          <React.Fragment key={plIdx}>
            {renderInline(pl)}
            {plIdx < paraLines.length - 1 ? <br /> : null}
          </React.Fragment>
        ))}
      </p>
    );
  }

  return <div className={cn('space-y-1 text-foreground break-words', className)}>{elements}</div>;
}
