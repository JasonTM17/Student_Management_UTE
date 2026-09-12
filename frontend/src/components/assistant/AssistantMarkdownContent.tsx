'use client';

import React from 'react';
import { useRouter } from 'next/navigation';
import { ExternalLink } from 'lucide-react';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface AssistantMarkdownContentProps {
  content: string;
  className?: string;
}

export function AssistantMarkdownContent({
  content,
  className,
}: AssistantMarkdownContentProps) {
  const { href } = useI18n();
  const router = useRouter();

  // Split into structural blocks: tables vs text lines
  const blocks = React.useMemo(() => {
    if (!content) return [];
    const lines = content.split('\n');
    const parsedBlocks: { type: 'table' | 'text'; lines: string[] }[] = [];
    let currentTable: string[] = [];

    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim();
      const isPipeLine = line.startsWith('|') && line.endsWith('|');

      if (isPipeLine) {
        currentTable.push(line);
      } else {
        if (currentTable.length > 0) {
          parsedBlocks.push({ type: 'table', lines: [...currentTable] });
          currentTable = [];
        }
        parsedBlocks.push({ type: 'text', lines: [lines[i]] });
      }
    }
    if (currentTable.length > 0) {
      parsedBlocks.push({ type: 'table', lines: [...currentTable] });
    }
    return parsedBlocks;
  }, [content]);

  if (!content) return null;

  const renderInline = (text: string): React.ReactNode[] => {
    // Regex matching:
    // 1. [Label](url)
    // 2. `code`
    // 3. ***bold-italic***
    // 4. **bold**
    // 5. *italic*
    // 6. Direct internal routes: /dashboard/... or /admin/...
    const regex =
      /(!?\[([^\]]+)\]\(([^)]+)\))|(`([^`]+)`)|(\*\*\*([^*]+)\*\*\*)|(\*\*([^*]+)\*\*)|(\*([^*]+)\*)|(\b\/(?:dashboard|admin)(?:\/[a-z0-9\-_]+)*\b)/g;
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
        const isInternal = rawUrl.startsWith('/');
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

        const text = block.lines[0];
        if (text.startsWith('• ') || text.startsWith('- ')) {
          return (
            <div key={bIdx} className="flex items-start gap-2 pl-1">
              <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-primary" />
              <div className="flex-1">
                {renderInline(text.replace(/^[•\-]\s*/, ''))}
              </div>
            </div>
          );
        }

        if (text.startsWith('### ')) {
          return (
            <h4
              key={bIdx}
              className="mt-3 font-semibold text-foreground text-sm tracking-tight"
            >
              {renderInline(text.replace(/^###\s*/, ''))}
            </h4>
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
