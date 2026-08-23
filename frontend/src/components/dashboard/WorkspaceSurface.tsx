'use client';

import * as React from 'react';
import { ArrowRight } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface WorkspaceMetricCardProps {
  label: React.ReactNode;
  value: React.ReactNode;
  icon: React.ReactNode;
  detail?: React.ReactNode;
  toneClassName?: string;
  valueClassName?: string;
  compact?: boolean;
  className?: string;
}

export function WorkspaceMetricCard({
  label,
  value,
  icon,
  detail,
  toneClassName,
  valueClassName,
  compact = false,
  className,
}: WorkspaceMetricCardProps) {
  return (
    <Card variant="default" className={cn('h-full min-w-0', className)}>
      <CardContent
        className={cn(
          'flex h-full min-h-[126px] flex-col gap-3 p-4',
          compact && 'min-h-[112px]',
        )}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <div
              className={cn(
                'min-w-0 break-words text-2xl font-semibold leading-8 text-foreground',
                compact && 'text-xl',
                valueClassName,
              )}
            >
              {value}
            </div>
            <div className="mt-0.5 break-words text-sm font-medium leading-5 text-foreground">{label}</div>
          </div>
          <div
            className={cn(
              'flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
              toneClassName,
            )}
          >
            {icon}
          </div>
        </div>
        {detail ? (
          <p className="mt-auto border-t border-border/70 pt-2 text-xs leading-5 text-muted-foreground">{detail}</p>
        ) : null}
      </CardContent>
    </Card>
  );
}

interface WorkspacePanelProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  variant?: 'elevated' | 'muted' | 'default';
  className?: string;
  contentClassName?: string;
}

export function WorkspacePanel({
  title,
  description,
  children,
  footer,
  variant = 'elevated',
  className,
  contentClassName,
}: WorkspacePanelProps) {
  return (
    <Card variant={variant} className={cn('min-w-0', className)}>
      <CardHeader className="border-b border-border/70 pb-4">
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className={cn('min-w-0', contentClassName)}>{children}</CardContent>
      {footer ? <div className="px-5 pb-5 pt-0">{footer}</div> : null}
    </Card>
  );
}

interface WorkspaceActionTileProps {
  href: string;
  icon: React.ReactNode;
  title: React.ReactNode;
  description: React.ReactNode;
  toneClassName?: string;
  ctaLabel?: React.ReactNode;
  className?: string;
}

export function WorkspaceActionTile({
  href,
  icon,
  title,
  description,
  toneClassName,
  ctaLabel,
  className,
}: WorkspaceActionTileProps) {
  const { messages } = useI18n();

  return (
    <LocalizedLink
      href={href}
      className={cn(
        'group min-w-0 rounded-md border border-border/70 bg-card px-4 py-4 transition-colors hover:border-primary/35 hover:bg-secondary/35 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        className,
      )}
    >
      <div className="flex h-full min-w-0 items-start gap-3">
        <div
          className={cn(
            'flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
            toneClassName,
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <h3 className="text-base font-semibold text-foreground transition-colors group-hover:text-primary">
            {title}
          </h3>
          <p className="text-sm leading-5 text-muted-foreground">
            {description}
          </p>
        </div>
        <div className="flex h-9 w-9 shrink-0 items-center justify-center text-primary" title={String(ctaLabel ?? messages.common.actions.openWorkspace)}>
          <ArrowRight className="h-4 w-4" aria-hidden="true" />
        </div>
      </div>
    </LocalizedLink>
  );
}
