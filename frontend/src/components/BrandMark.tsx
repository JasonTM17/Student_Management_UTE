'use client';

import Image from 'next/image';
import { LocalizedLink } from '@/components/LocalizedLink';
import { cn } from '@/lib/utils';

interface BrandMarkProps {
  href?: string;
  className?: string;
  markClassName?: string;
  titleClassName?: string;
  subtitleClassName?: string;
  title?: string;
  subtitle?: string;
  compact?: boolean;
}

export function CampusUteEmblem({ className, size = 44 }: { className?: string; size?: number }) {
  return (
    <div
      className={cn(
        'relative flex shrink-0 items-center justify-center overflow-hidden rounded-lg bg-white p-1 shadow-sm',
        className,
      )}
      style={{ width: size, height: size }}
    >
      <Image
        src="/hcmute-logo.png"
        alt="HCMUTE Official Logo"
        fill
        sizes={`${size}px`}
        priority
        className="object-contain"
      />
    </div>
  );
}

export function BrandMark({
  href,
  className,
  markClassName,
  titleClassName,
  subtitleClassName,
  title = 'CampusUTE',
  subtitle,
  compact = false,
}: BrandMarkProps) {
  const content = (
    <div className={cn('flex min-w-0 items-center gap-2.5', className)}>
      <CampusUteEmblem
        size={compact ? 36 : 44}
        className={cn('border border-white/10 ring-1 ring-black/5', markClassName)}
      />
      <div className="min-w-0">
        <div
          className={cn(
            'flex min-w-0 items-center gap-1.5 text-lg font-bold tracking-tight text-foreground',
            compact && 'text-base',
            titleClassName,
          )}
        >
          {/* The product name truncates so the fixed-width badge can never slide
              under the sidebar collapse control. */}
          <span translate="no" className="truncate">
            {title}
          </span>
          <span className="shrink-0 rounded-[4px] bg-[var(--portal-yellow)] px-1 py-0.5 text-[10px] font-extrabold uppercase tracking-wide text-[var(--portal-yellow-ink)] shadow-xs ring-1 ring-black/5">
            HCMUTE
          </span>
        </div>
        {subtitle ? (
          <div className={cn('truncate text-xs font-medium text-muted-foreground', subtitleClassName)}>
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
