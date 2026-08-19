'use client';

import { GraduationCap } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { cn } from '@/lib/utils';

interface BrandMarkProps {
  href?: string;
  className?: string;
  markClassName?: string;
  titleClassName?: string;
  subtitleClassName?: string;
  subtitle?: string;
  compact?: boolean;
}

export function BrandMark({
  href,
  className,
  markClassName,
  titleClassName,
  subtitleClassName,
  subtitle,
  compact = false,
}: BrandMarkProps) {
  const content = (
    <div className={cn('flex items-center gap-3', className)}>
      <div
        className={cn(
          'flex h-11 w-11 items-center justify-center rounded-lg border border-white/10 bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))] shadow-sm',
          compact && 'h-9 w-9',
          markClassName,
        )}
      >
        <GraduationCap className={cn('h-5 w-5', compact && 'h-4 w-4')} />
      </div>
      <div className="min-w-0">
        <div
          className={cn(
            'text-lg font-semibold tracking-tight text-foreground',
            compact && 'text-base',
            titleClassName,
          )}
        >
          CampusCore
        </div>
        {subtitle ? (
          <div className={cn('truncate text-sm text-muted-foreground', subtitleClassName)}>
            {subtitle}
          </div>
        ) : null}
      </div>
    </div>
  );

  if (!href) {
    return content;
  }

  return (
    <LocalizedLink href={href} className="inline-flex min-w-0">
      {content}
    </LocalizedLink>
  );
}
