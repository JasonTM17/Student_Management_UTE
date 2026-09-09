'use client';

import React, { useState } from 'react';
import {
  AlertCircle,
  AlertTriangle,
  Check,
  CheckCircle2,
  Copy,
  ExternalLink,
  Info,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export interface RichContentRendererProps {
  content?: string | null;
  className?: string;
  fallbackText?: string;
}

// Sanitization: disallow dangerous protocols
function isSafeUrl(url: string): boolean {
  const trimmed = url.trim().toLowerCase();
  if (trimmed.startsWith('javascript:') || trimmed.startsWith('data:') || trimmed.startsWith('vbscript:')) {
    return false;
  }
  return true;
}

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
      if (isSafeUrl(url)) {
        parts.push(
          <span key={`img-${keyIndex++}`} className="my-2 inline-block max-w-full">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img
              src={url}
              alt={alt}
              className="max-h-96 rounded-md border border-border/70 object-cover shadow-sm"
              loading="lazy"
            />
            {alt ? (
              <span className="mt-1 block text-center text-xs text-muted-foreground">{alt}</span>
            ) : null}
          </span>
        );
      } else {
        parts.push(`[Blocked image: ${alt}]`);
      }
    } else if (fullMatch.startsWith('[')) {
      // Link
      const label = match[2] || '';
      const url = match[3] || '';
      if (isSafeUrl(url)) {
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
 * RichContentRenderer:
 * Renders Markdown-like formatted academic notices, guides, and articles
 * safely and cleanly with zero heavy external dependencies.
 */
export function RichContentRenderer({
  content,
  className,
  fallbackText,
}: RichContentRendererProps) {
  if (!content || !content.trim()) {
    if (fallbackText) {
      return <p className={cn('text-sm text-muted-foreground italic', className)}>{fallbackText}</p>;
    }
    return null;
  }

  const lines = content.replace(/\r\n/g, '\n').split('\n');
  const elements: React.ReactNode[] = [];
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

    // 4. Headings
    if (trimmed.startsWith('# ') || trimmed.startsWith('## ') || trimmed.startsWith('### ') || trimmed.startsWith('#### ')) {
      if (trimmed.startsWith('#### ')) {
        elements.push(
          <h4 key={`h4-${elementKey++}`} className="mt-4 mb-2 text-base font-semibold text-foreground">
            {renderInline(trimmed.slice(5))}
          </h4>
        );
      } else if (trimmed.startsWith('### ')) {
        elements.push(
          <h3 key={`h3-${elementKey++}`} className="mt-5 mb-2 text-lg font-semibold text-foreground">
            {renderInline(trimmed.slice(4))}
          </h3>
        );
      } else if (trimmed.startsWith('## ')) {
        elements.push(
          <h2 key={`h2-${elementKey++}`} className="mt-6 mb-3 text-xl font-bold tracking-tight text-foreground border-b border-border/60 pb-1.5">
            {renderInline(trimmed.slice(3))}
          </h2>
        );
      } else if (trimmed.startsWith('# ')) {
        elements.push(
          <h1 key={`h1-${elementKey++}`} className="mt-6 mb-4 text-2xl font-extrabold tracking-tight text-foreground border-b-2 border-primary/40 pb-2">
            {renderInline(trimmed.slice(2))}
          </h1>
        );
      }
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
      <p key={`p-${elementKey++}`} className="my-2.5 text-sm leading-7 text-foreground/90">
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
