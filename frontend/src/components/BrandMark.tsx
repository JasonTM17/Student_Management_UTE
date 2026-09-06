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
        'relative flex shrink-0 items-center justify-center overflow-hidden rounded-lg shadow-sm',
        className,
      )}
      style={{ width: size, height: size }}
    >
      <Image
        src="/icon.svg"
        alt="CampusUTE Logo"
        width={size}
        height={size}
        priority
        className="h-full w-full object-contain"
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
    <div className={cn('flex items-center gap-3', className)}>
      <CampusUteEmblem
        size={compact ? 36 : 44}
        className={cn('border border-white/10 ring-1 ring-black/5', markClassName)}
      />
      <div className="min-w-0">
        <div
          className={cn(
            'flex items-center gap-1.5 text-lg font-bold tracking-tight text-foreground',
            compact && 'text-base',
            titleClassName,
          )}
        >
          <span translate="no">{title}</span>
          <span className="rounded bg-[var(--portal-yellow,#F59E0B)]/15 px-1 py-0.5 text-[10px] font-extrabold uppercase tracking-wider text-[var(--portal-yellow,#D97706)]">
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

