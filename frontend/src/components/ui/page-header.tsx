import * as React from 'react';
import { cn } from '@/lib/utils';

interface SectionEyebrowProps {
  children: React.ReactNode;
  className?: string;
}

export function SectionEyebrow({
  children,
  className,
}: SectionEyebrowProps) {
  return (
    <div
      className={cn(
        'flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.24em] text-muted-foreground',
        className,
      )}
    >
      <span className="h-2 w-2 rounded-full bg-[hsl(var(--accent-warm))]" />
      <span>{children}</span>
    </div>
  );
}

interface PageHeaderProps {
  eyebrow?: React.ReactNode;
  title: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={cn(
        'portal-page-header flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between',
        className,
      )}
    >
      <div className="portal-page-header-copy min-w-0 space-y-2">
        {eyebrow ? <div className="portal-section-ribbon">{eyebrow}</div> : null}
        <div className="space-y-1">
          <h1 className="text-xl font-bold leading-tight tracking-tight sm:text-2xl">
            {title}
          </h1>
          {description ? (
            <p className="max-w-4xl text-xs leading-5 text-muted-foreground sm:text-sm">
              {description}
            </p>
          ) : null}
        </div>
      </div>
      {actions ? (
        <div className="portal-page-header-actions flex shrink-0 flex-wrap items-center gap-2">
          {actions}
        </div>
      ) : null}
    </header>
  );
}
