'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ExternalLink } from 'lucide-react';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import {
  ASSISTANT_INLINE_MARKDOWN_REGEX,
  sanitizeStreamingMarkdown,
  splitAssistantBlocks,
} from '@/lib/assistant-inline-markdown-regex';
import { sanitizeAssistantOutput } from '@/lib/assistant-output-guard';

interface AssistantMarkdownContentProps {
  content: string;
  className?: string;
  /** True while the message is still streaming; trims unclosed markers. */
  streaming?: boolean;
  /** Only assistant output is sanitized; user messages remain verbatim. */
  guardOutput?: boolean;
}

export function AssistantMarkdownContent({
  content,
  className,
  streaming = false,
  guardOutput = true,
}: AssistantMarkdownContentProps) {
  const { href, messages } = useI18n();
  const router = useRouter();
  const renderedContent = streaming ? sanitizeStreamingMarkdown(content) : content;
  const safeContent = guardOutput
    ? sanitizeAssistantOutput(renderedContent, messages.assistant.technicalBlocked)
    : renderedContent;

  // Structural blocks: pipe tables stay table-scoped; consecutive plain-text
  // lines merge into one paragraph (see splitAssistantBlocks).
  const blocks = React.useMemo(
    () => splitAssistantBlocks(safeContent),
    [safeContent],
  );

  if (!safeContent) return null;

  const renderInline = (text: string): React.ReactNode[] => {
    // Regex matching:
    // 1. [Label](url)
    // 2. `code`
    // 3. ***bold-italic***
    // 4. **bold**
    // 5. *italic*
    // Fresh instance per call: the shared pattern carries lastIndex state.
    const regex = new RegExp(
      ASSISTANT_INLINE_MARKDOWN_REGEX.source,
      ASSISTANT_INLINE_MARKDOWN_REGEX.flags,
    );
    const elements: React.ReactNode[] = [];
    let lastIndex = 0;
    let match: RegExpExecArray | null;
    let key = 0;

    while ((match = regex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        elements.push(text.slice(lastIndex, match.index));
      }

      if (match[1]) {
        // [Label](url)
        const label = match[2];
        const rawUrl = match[3];
        const isInternal = rawUrl.startsWith('/') && !rawUrl.startsWith('//');
        const isSafeExternal = /^https?:\/\//i.test(rawUrl) || /^mailto:/i.test(rawUrl);

        if (!isInternal && !isSafeExternal) {
          // Guard against javascript:, data:, and other unsafe schemes
          elements.push(label);
        } else {
          elements.push(
            <a
              key={key++}
              href={isInternal ? href(rawUrl) : rawUrl}
              onClick={(e) => {
                if (isInternal) {
                  e.preventDefault();
                  router.push(href(rawUrl));
                }
              }}
              target={isInternal ? undefined : '_blank'}
              rel={isInternal ? undefined : 'noopener noreferrer'}
              className="inline-flex items-center gap-1 font-semibold text-primary underline underline-offset-4 transition-colors hover:text-primary/80"
            >
              {label}
              {!isInternal && (
                <ExternalLink className="inline h-3 w-3 opacity-70" />
              )}
            </a>,
          );
        }
      } else if (match[4]) {
        // `code`
        elements.push(
          <code
            key={key++}
            className="rounded-md border border-primary/20 bg-primary/10 px-1.5 py-0.5 font-mono text-[0.88em] font-medium text-primary"
          >
            {match[5]}
          </code>,
        );
      } else if (match[6]) {
        // ***bold-italic***
        elements.push(
          <strong key={key++} className="font-semibold italic text-foreground">
            {match[7]}
          </strong>,
        );
      } else if (match[8]) {
        // **bold**
        elements.push(
          <strong key={key++} className="font-semibold text-foreground">
            {match[9]}
          </strong>,
        );
      } else if (match[10]) {
        // *italic*
        elements.push(
          <em key={key++} className="italic text-foreground/90">
            {match[11]}
          </em>,
        );
      } else if (match[12]) {
        // Direct internal path: /dashboard/schedule, /dashboard/conduct, etc.
        const path = match[12];
        elements.push(
          <a
            key={key++}
            href={href(path)}
            onClick={(e) => {
              e.preventDefault();
              router.push(href(path));
            }}
            className="inline-flex items-center font-semibold text-primary underline underline-offset-2 hover:opacity-80"
          >
            {path}
          </a>,
        );
      }
      lastIndex = regex.lastIndex;
    }

    if (lastIndex < text.length) {
      elements.push(text.slice(lastIndex));
    }
    return elements;
  };

  return (
    <div
      className={cn(
        'space-y-2 text-sm leading-relaxed break-words [overflow-wrap:anywhere]',
        className,
      )}
    >
      {blocks.map((block, bIdx) => {
        if (block.type === 'table') {
          const rows = block.lines;
          if (rows.length < 2) return null;
          const parseRow = (line: string) =>
            line
              .replace(/^\|/, '')
              .replace(/\|$/, '')
              .split('|')
              .map((c) => c.trim());
          const headerCells = parseRow(rows[0]);
          const isSeparator = /^\|?[\s-:|]+\|?$/.test(rows[1]);
          const dataRows = (isSeparator ? rows.slice(2) : rows.slice(1)).map(
            parseRow,
          );

          return (
            <div
              key={bIdx}
              className="my-3 overflow-x-auto rounded-xl border border-primary/20 shadow-xs"
            >
              <table className="w-full border-collapse text-xs">
                <thead className="border-b border-primary/20 bg-primary/10 text-primary">
                  <tr>
                    {headerCells.map((cell, cIdx) => (
                      <th
                        key={cIdx}
                        className="px-3 py-2 text-left font-semibold"
                      >
                        {renderInline(cell)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60 bg-card">
                  {dataRows.map((row, rIdx) => (
                    <tr
                      key={rIdx}
                      className="transition-colors hover:bg-muted/40 odd:bg-card even:bg-muted/15"
                    >
                      {row.map((cell, cIdx) => (
                        <td key={cIdx} className="px-3 py-2 text-foreground/90">
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

        if (block.type === 'code') {
          return (
            <div
              key={bIdx}
              className="my-2.5 overflow-x-auto rounded-lg border border-border/80 bg-muted/60 p-3 font-mono text-xs"
            >
              {block.language && (
                <div className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                  {block.language}
                </div>
              )}
              <pre className="whitespace-pre overflow-x-auto font-mono text-[12px] text-foreground/90 leading-normal">
                <code>{block.code}</code>
              </pre>
            </div>
          );
        }

        const text = block.lines[0];
        const orderedMatch = text.match(/^(\d+)\.\s+(.*)$/);
        if (orderedMatch) {
          const num = orderedMatch[1];
          const itemText = orderedMatch[2];
          return (
            <div key={bIdx} className="flex items-start gap-2 pl-1 py-0.5">
              <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[10px] font-semibold text-primary">
                {num}
              </span>
              <div className="flex-1 leading-6">
                {renderInline(itemText)}
              </div>
            </div>
          );
        }

        if (text.startsWith('• ') || text.startsWith('- ') || text.startsWith('* ')) {
          return (
            <div key={bIdx} className="flex items-start gap-2 pl-1">
              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
              <div className="flex-1">
                {renderInline(text.replace(/^[*•\-]\s*/, ''))}
              </div>
            </div>
          );
        }

        if (text.startsWith('#### ') || text.startsWith('### ')) {
          const heading = text.replace(/^#{3,4}\s*/, '');
          return (
            <h4
              key={bIdx}
              className="mt-3 font-semibold text-foreground text-sm tracking-tight"
            >
              {renderInline(heading)}
            </h4>
          );
        }

        if (text.startsWith('## ') || text.startsWith('# ')) {
          const heading = text.replace(/^#{1,2}\s*/, '');
          return (
            <h3
              key={bIdx}
              className="mt-3 font-semibold text-foreground text-[15px] tracking-tight"
            >
              {renderInline(heading)}
            </h3>
          );
        }

        if (text.startsWith('> ') || text.startsWith('>')) {
          const quote = text.replace(/^>\s?/, '');
          return (
            <blockquote
              key={bIdx}
              className="border-l-2 border-primary/40 bg-primary/5 py-1 pl-2.5 pr-2 text-muted-foreground italic"
            >
              {renderInline(quote)}
            </blockquote>
          );
        }

        return (
          <p key={bIdx} className="whitespace-pre-wrap">
            {renderInline(text)}
          </p>
        );
      })}
    </div>
  );
}
