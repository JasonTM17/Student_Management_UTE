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
        'portal-menu-label inline-flex items-center gap-1.5 rounded-sm bg-primary/10 px-2.5 py-0.5 text-xs font-bold tracking-wider text-primary border border-primary/20',
        className,
      )}
    >
      <span className="h-1.5 w-1.5 rounded-full bg-[var(--portal-yellow)]" aria-hidden="true" />
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
  eyebrow: _eyebrow,
  title,
  description: _description,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header className={cn('min-w-0', className)}>
      <div className="relative flex min-w-0 items-center gap-x-4 border-b border-border">
        <div className="portal-page-tab inline-flex min-w-0 items-center rounded-t-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground">
          <h1 className="truncate">{title}</h1>
        </div>
        {actions ? (
          <div className="ml-auto flex flex-wrap items-center gap-2 py-1.5">
            {actions}
          </div>
        ) : null}
      </div>
    </header>
  );
}
