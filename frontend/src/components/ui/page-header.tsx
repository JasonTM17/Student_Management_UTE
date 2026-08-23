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
        'portal-menu-label',
        className,
      )}
    >
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
        'min-w-0',
        className,
      )}
    >
      <div className="portal-page-ribbon min-w-0 px-4 py-5 sm:px-6">
        <div className="min-w-0 space-y-1">
          {eyebrow ? eyebrow : null}
          <h1 className="break-words text-xl font-semibold leading-7 text-foreground sm:text-2xl">
            {title}
          </h1>
          {description ? (
            <p className="max-w-4xl text-sm leading-6 text-muted-foreground">
              {description}
            </p>
          ) : null}
        </div>
      </div>
      {actions ? (
        <div className="portal-page-actions flex flex-wrap items-center justify-start gap-2 px-4 py-3 sm:justify-end sm:px-6">
          {actions}
        </div>
      ) : null}
    </header>
  );
}
