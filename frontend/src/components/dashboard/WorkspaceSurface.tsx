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
    <Card variant="elevated" className={cn('portal-section-card portal-metric-card h-full min-w-0', className)}>
      <CardContent
        className={cn(
          'flex h-full flex-col gap-3 pt-4',
          compact ? 'pb-4' : 'pb-5',
        )}
      >
        <div className="flex min-w-0 items-center gap-3">
          <div
            className={cn(
              'flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
              toneClassName,
            )}
          >
            {icon}
          </div>
          <div className="min-w-0 flex-1">
            <div className="break-words text-xs font-semibold uppercase tracking-[0.08em] text-muted-foreground">
              {label}
            </div>
            <div
              className={cn(
                'mt-1 min-w-0 break-normal font-bold leading-tight tracking-tight text-foreground',
                compact ? 'text-xl' : 'text-2xl',
                valueClassName,
              )}
            >
              {value}
            </div>
          </div>
        </div>
        {detail ? (
          <p className="border-t border-border/60 pt-2 text-xs leading-5 text-muted-foreground">
            {detail}
          </p>
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
    <Card variant={variant} className={cn('portal-section-card min-w-0', className)}>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className={cn('min-w-0', contentClassName)}>{children}</CardContent>
      {footer ? <div className="px-4 pb-4 pt-0 sm:px-5 sm:pb-5">{footer}</div> : null}
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
        'group min-w-0 rounded-md border border-border/70 bg-card px-4 py-4 transition-colors hover:bg-secondary/50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 sm:px-5 sm:py-5',
        'portal-action-tile',
        className,
      )}
    >
      <div className="flex h-full min-w-0 flex-col gap-3">
        <div
          className={cn(
            'flex h-10 w-10 items-center justify-center rounded-md',
            toneClassName,
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 space-y-2">
          <h3 className="text-base font-semibold text-foreground transition-colors group-hover:text-primary">
            {title}
          </h3>
          <p className="text-sm leading-6 text-muted-foreground">
            {description}
          </p>
        </div>
        <div className="mt-auto flex items-center gap-2 text-sm font-medium text-primary">
          <span>{ctaLabel ?? messages.common.actions.openWorkspace}</span>
          <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-0.5" />
        </div>
      </div>
    </LocalizedLink>
  );
}
