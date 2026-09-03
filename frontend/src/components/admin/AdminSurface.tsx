import * as React from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

interface AdminMetricCardProps {
  label: React.ReactNode;
  value: React.ReactNode;
  icon: React.ReactNode;
  detail?: React.ReactNode;
  toneClassName?: string;
  compact?: boolean;
  className?: string;
}

export function AdminMetricCard({
  label,
  value,
  icon,
  detail,
  toneClassName,
  compact = false,
  className,
}: AdminMetricCardProps) {
  return (
    <Card variant="default" className={cn('h-full', className)}>
      <CardContent
        className={cn(
          'flex h-full min-h-[126px] flex-col gap-3 p-4',
          compact && 'min-h-[112px]',
        )}
      >
        <div
          className={cn(
            'flex items-start justify-between gap-3',
          )}
        >
          <div
            className={cn(
              'order-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-md',
              toneClassName,
            )}
          >
            {icon}
          </div>
          <div className="min-w-0">
            <div
              className={cn(
                'text-2xl font-semibold leading-8 text-foreground',
                compact && 'text-xl',
              )}
            >
              {value}
            </div>
            <div className="mt-0.5 text-sm font-medium text-foreground">{label}</div>
          </div>
        </div>
        {detail ? (
          <p className="mt-auto border-t border-border/70 pt-2 text-xs leading-5 text-muted-foreground">{detail}</p>
        ) : null}
      </CardContent>
    </Card>
  );
}

interface AdminToolbarCardProps {
  children: React.ReactNode;
  className?: string;
  contentClassName?: string;
}

export function AdminToolbarCard({
  children,
  className,
  contentClassName,
}: AdminToolbarCardProps) {
  return (
    <Card variant="muted" className={className}>
      <CardContent className={cn('pt-6', contentClassName)}>{children}</CardContent>
    </Card>
  );
}

interface AdminToolbarMetaProps {
  summary?: React.ReactNode;
  actions?: React.ReactNode;
  className?: string;
}

export function AdminToolbarMeta({
  summary,
  actions,
  className,
}: AdminToolbarMetaProps) {
  return (
    <div
      className={cn(
        'flex flex-wrap items-center justify-end gap-3',
        className,
      )}
    >
      {summary ? (
        <div className="text-sm text-muted-foreground">{summary}</div>
      ) : null}
      {actions}
    </div>
  );
}

interface AdminTableCardProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  children: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
  contentClassName?: string;
}

export function AdminTableCard({
  title,
  description,
  children,
  footer,
  className,
  contentClassName,
}: AdminTableCardProps) {
  return (
    <Card variant="elevated" className={cn('min-w-0', className)}>
      <CardHeader className="min-w-0 border-b border-border/70 pb-4">
        <CardTitle>{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className={cn('min-w-0 space-y-0', contentClassName)}>
        {children}
        {footer ? footer : null}
      </CardContent>
    </Card>
  );
}

interface AdminTableScrollProps {
  children: React.ReactNode;
  className?: string;
}

export function AdminTableScroll({
  children,
  className,
}: AdminTableScrollProps) {
  return (
    <div
      tabIndex={0}
      role="region"
      aria-label="Data table"
      className={cn(
        'min-w-0 max-w-full overflow-x-auto overscroll-x-contain focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring rounded-md',
        className,
      )}
    >
      {children}
    </div>
  );
}

interface AdminPaginationFooterProps {
  summary: React.ReactNode;
  page: number;
  totalPages: number;
  onPrevious: () => void;
  onNext: () => void;
  previousLabel?: string;
  nextLabel?: string;
  className?: string;
}

export function AdminPaginationFooter({
  summary,
  page,
  totalPages,
  onPrevious,
  onNext,
  previousLabel = 'Previous',
  nextLabel = 'Next',
  className,
}: AdminPaginationFooterProps) {
  if (totalPages <= 1) {
    return null;
  }

  return (
    <div
      className={cn(
        'mt-6 flex flex-col gap-3 border-t border-border/70 pt-4 sm:flex-row sm:items-center sm:justify-between',
        className,
      )}
    >
      <div className="text-sm text-muted-foreground">{summary}</div>
      <div className="flex items-center gap-2">
        <Button
          size="sm"
          variant="outline"
          disabled={page === 1}
          onClick={onPrevious}
        >
          {previousLabel}
        </Button>
        <Button
          size="sm"
          variant="outline"
          disabled={page === totalPages}
          onClick={onNext}
        >
          {nextLabel}
        </Button>
      </div>
    </div>
  );
}

interface AdminRowActionsProps {
  children: React.ReactNode;
  className?: string;
}

export function AdminRowActions({
  children,
  className,
}: AdminRowActionsProps) {
  return (
    <div className={cn('flex items-center justify-end gap-2', className)}>
      {children}
    </div>
  );
}

interface AdminFormFieldProps {
  label: React.ReactNode;
  description?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export function AdminFormField({
  label,
  description,
  children,
  className,
}: AdminFormFieldProps) {
  const generatedId = `admin-field-${React.useId().replace(/:/g, '')}`;
  const childNodes = React.Children.toArray(children);
  const child = childNodes.length === 1 ? childNodes[0] : null;
  const control = React.isValidElement(child)
    ? (() => {
        const element = child as React.ReactElement<{ id?: string }>;
        const controlId = element.props.id ?? generatedId;
        return {
          id: controlId,
          node: React.cloneElement(element, { id: controlId }),
        };
      })()
    : null;

  return (
    <div className={cn('space-y-2', className)}>
      <label
        htmlFor={control?.id}
        className="text-sm font-medium text-foreground"
      >
        {label}
      </label>
      {control?.node ?? children}
      {description ? (
        <p className="text-sm text-muted-foreground">{description}</p>
      ) : null}
    </div>
  );
}

interface AdminFormSectionProps {
  title?: React.ReactNode;
  description?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}

export function AdminFormSection({
  title,
  description,
  children,
  className,
}: AdminFormSectionProps) {
  return (
    <div
      className={cn(
        'space-y-3 rounded-lg border border-border/70 bg-secondary/20 p-4',
        className,
      )}
    >
      {title || description ? (
        <div className="space-y-1">
          {title ? (
            <h3 className="text-sm font-semibold text-foreground">{title}</h3>
          ) : null}
          {description ? (
            <p className="text-sm text-muted-foreground">{description}</p>
          ) : null}
        </div>
      ) : null}
      {children}
    </div>
  );
}

interface AdminDialogFooterProps {
  children: React.ReactNode;
  className?: string;
}

export function AdminDialogFooter({
  children,
  className,
}: AdminDialogFooterProps) {
  return (
    <div className={cn('flex justify-end gap-2 pt-2', className)}>
      {children}
    </div>
  );
}
