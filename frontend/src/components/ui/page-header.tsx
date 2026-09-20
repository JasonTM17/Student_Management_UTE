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
        // Symmetric padding around the label alone. A leading dot was tried and
        // removed: inside a chip that hugs its content there is no way to give
        // the dot clearance from the text AND keep the label on the frame
        // centre, so the dot either pushed the label off-centre or collided
        // with it. The border already carries the accent.
        'portal-menu-label inline-flex items-center justify-center rounded-sm bg-primary/10 px-3.5 py-0.5 text-xs font-bold text-primary border border-primary/20',
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
  /**
   * Short label for the mobile page tab. When the page title is a long
   * sentence (e.g. "Đồ án tốt nghiệp: từ đề tài đến kết quả."), the tab
   * would truncate to an unreadable fragment; tabLabel keeps the tab legible
   * while the full title still renders in the page body.
   */
  tabLabel?: React.ReactNode;
  description?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}

export function PageHeader({
  eyebrow: _eyebrow,
  title,
  tabLabel,
  description: _description,
  actions,
  className,
}: PageHeaderProps) {
  return (
    <header className={cn('min-w-0', className)}>
      <div className="relative flex flex-col gap-2 border-b border-border sm:flex-row sm:items-center sm:gap-x-4">
        <div className="portal-page-tab inline-flex max-w-full self-start rounded-t-lg bg-primary px-5 py-2.5 text-sm font-semibold text-primary-foreground">
          <h1 className="truncate">{tabLabel ?? title}</h1>
        </div>
        {actions ? (
          <div className="flex flex-wrap items-center gap-2 py-1.5 sm:ml-auto">
            {actions}
          </div>
        ) : null}
      </div>
    </header>
  );
}
