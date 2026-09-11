'use client';

import React from 'react';
import {
  Download,
  FileSpreadsheet,
  FileText,
  FileArchive,
  Paperclip,
  ExternalLink,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { Locale } from '@/i18n/config';

export interface AttachmentItem {
  id?: string;
  name: string;
  url: string;
  size?: string;
  type?: 'pdf' | 'docx' | 'xlsx' | 'zip' | 'other';
}

interface DocumentAttachmentsListProps {
  attachments?: AttachmentItem[];
  content?: string | null;
  className?: string;
  locale?: Locale;
}

/**
 * Extracts links pointing to document files (.pdf, .docx, .xlsx, .zip) from markdown content.
 */
export function extractAttachmentsFromContent(content: string | null | undefined): AttachmentItem[] {
  if (!content) return [];
  const items: AttachmentItem[] = [];
  const regex = /\[([^\]]+)\]\((https?:\/\/[^)\s]+\.(pdf|docx|doc|xlsx|xls|zip|rar)[^)\s]*|\/[^)\s]+\.(pdf|docx|doc|xlsx|xls|zip|rar)[^)\s]*)\)/gi;
  let match: RegExpExecArray | null;

  while ((match = regex.exec(content)) !== null) {
    const name = match[1];
    const url = match[2];
    const ext = (match[3] || match[4] || '').toLowerCase();

    let type: AttachmentItem['type'] = 'other';
    if (ext === 'pdf') type = 'pdf';
    else if (ext.startsWith('doc')) type = 'docx';
    else if (ext.startsWith('xl')) type = 'xlsx';
    else if (ext === 'zip' || ext === 'rar') type = 'zip';

    items.push({
      name,
      url,
      type,
      size: 'Tài liệu số',
    });
  }

  return items;
}

export function DocumentAttachmentsList({
  attachments = [],
  content,
  className,
  locale = 'vi',
}: DocumentAttachmentsListProps) {
  const isVi = locale === 'vi';
  const extracted = extractAttachmentsFromContent(content);
  const allAttachments = [...attachments, ...extracted];

  if (allAttachments.length === 0) {
    return null;
  }

  const getIcon = (type?: string) => {
    switch (type) {
      case 'pdf':
        return <FileText className="h-5 w-5 text-red-600 dark:text-red-400" />;
      case 'docx':
        return <FileText className="h-5 w-5 text-blue-600 dark:text-blue-400" />;
      case 'xlsx':
        return <FileSpreadsheet className="h-5 w-5 text-emerald-600 dark:text-emerald-400" />;
      case 'zip':
        return <FileArchive className="h-5 w-5 text-purple-600 dark:text-purple-400" />;
      default:
        return <Paperclip className="h-5 w-5 text-muted-foreground" />;
    }
  };

  const getBadgeStyle = (type?: string) => {
    switch (type) {
      case 'pdf':
        return 'bg-red-500/10 text-red-600 border-red-500/20';
      case 'docx':
        return 'bg-blue-500/10 text-blue-600 border-blue-500/20';
      case 'xlsx':
        return 'bg-emerald-500/10 text-emerald-600 border-emerald-500/20';
      case 'zip':
        return 'bg-purple-500/10 text-purple-600 border-purple-500/20';
      default:
        return 'bg-secondary text-muted-foreground border-border';
    }
  };

  return (
    <div
      className={cn(
        'rounded-xl border border-border/70 bg-card p-4 sm:p-5 shadow-xs',
        className,
      )}
    >
      <div className="flex items-center gap-2 border-b border-border/60 pb-3">
        <Paperclip className="h-4 w-4 text-primary" />
        <h4 className="text-xs font-bold uppercase tracking-wider text-foreground">
          {isVi ? 'Tệp đính kèm chính thức' : 'Official Attached Documents'}
        </h4>
        <span className="ml-auto rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
          {allAttachments.length} {isVi ? 'tệp' : 'files'}
        </span>
      </div>

      <div className="mt-3 grid grid-cols-1 gap-2.5 sm:grid-cols-2">
        {allAttachments.map((file, idx) => (
          <a
            key={idx}
            href={file.url}
            target="_blank"
            rel="noopener noreferrer"
            download
            className="group flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-secondary/30 p-3 transition-all hover:bg-secondary/60 hover:border-primary/40 hover:shadow-xs focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <div className="flex items-center gap-3 overflow-hidden">
              <div className="rounded-md border border-border/60 bg-background p-2">
                {getIcon(file.type)}
              </div>
              <div className="overflow-hidden">
                <p className="line-clamp-1 text-xs font-semibold text-foreground group-hover:text-primary transition-colors">
                  {file.name}
                </p>
                <div className="flex items-center gap-2 pt-0.5 text-[11px] text-muted-foreground">
                  <span
                    className={cn(
                      'rounded px-1.5 py-0.2 uppercase font-mono font-bold text-[9px] border',
                      getBadgeStyle(file.type),
                    )}
                  >
                    {file.type || 'DOC'}
                  </span>
                  {file.size && <span>{file.size}</span>}
                </div>
              </div>
            </div>

            <div className="shrink-0 text-muted-foreground group-hover:text-primary transition-colors">
              <Download className="h-4 w-4" />
            </div>
          </a>
        ))}
      </div>
    </div>
  );
}
