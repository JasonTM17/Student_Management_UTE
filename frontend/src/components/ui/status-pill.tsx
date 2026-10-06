'use client';

import type { ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { statusToneClass, type StatusTone } from './status';

export interface StatusPillProps {
  tone: StatusTone;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}

/**
 * One shared pill for status/role badges: label + optional icon on the
 * `statusToneClass` contract, with the 11px+ typography floor baked in.
 */
export function StatusPill({ tone, icon, children, className }: StatusPillProps) {
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 rounded-md px-2.5 py-0.5 text-xs font-medium',
        statusToneClass(tone),
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}
