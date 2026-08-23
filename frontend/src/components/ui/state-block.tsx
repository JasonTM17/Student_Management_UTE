import * as React from 'react';
import { AlertCircle, LucideIcon, RefreshCw, ShieldAlert } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface StateBlockProps {
  icon?: LucideIcon;
  title: string;
  description: string;
  action?: React.ReactNode;
  className?: string;
}

export function EmptyState({
  icon: Icon = AlertCircle,
  title,
  description,
  action,
  className,
}: StateBlockProps) {
  return (
    <div
      className={cn(
        'flex min-h-[220px] flex-col items-center justify-center border border-dashed border-border bg-[var(--portal-surface)] px-6 py-10 text-center',
        className,
      )}
    >
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-primary/10 text-primary">
        <Icon className="h-5 w-5" aria-hidden="true" />
      </div>
      <div className="space-y-2">
        <h3 className="text-lg font-semibold text-foreground">{title}</h3>
        <p className="max-w-md text-sm leading-6 text-muted-foreground">
          {description}
        </p>
      </div>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}

interface ErrorStateProps {
  title: string;
  description: string;
  onRetry?: () => void;
  retryLabel?: string;
  className?: string;
}

export function ErrorState({
  title,
  description,
  onRetry,
  retryLabel,
  className,
}: ErrorStateProps) {
  const { messages } = useI18n();

  return (
    <div
      role="alert"
      className={cn(
        'border border-destructive/35 bg-destructive/5 p-6',
        className,
      )}
    >
      <div className="flex items-start gap-4">
        <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-destructive/10 text-destructive">
          <AlertCircle className="h-5 w-5" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1 space-y-1">
          <h3 className="text-base font-semibold text-foreground">{title}</h3>
          <p className="text-sm leading-6 text-muted-foreground">
            {description}
          </p>
        </div>
        {onRetry ? (
          <Button
            type="button"
            variant="outline"
            onClick={onRetry}
            className="shrink-0"
          >
            <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
            {retryLabel || messages.common.actions.retry}
          </Button>
        ) : null}
      </div>
    </div>
  );
}

interface LoadingStateProps {
  label?: string;
  className?: string;
}

export function LoadingState({
  label,
  className,
}: LoadingStateProps) {
  const { messages } = useI18n();

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex min-h-[220px] flex-col items-center justify-center gap-4 border border-border bg-[var(--portal-surface)] px-6 py-10',
        className,
      )}
    >
      <div className="w-full max-w-sm space-y-3" aria-hidden="true">
        <div className="h-4 w-2/5 animate-pulse rounded bg-primary/20" />
        <div className="h-3 w-full animate-pulse rounded bg-secondary" />
        <div className="h-3 w-4/5 animate-pulse rounded bg-secondary" />
      </div>
      <p className="text-sm text-muted-foreground">
        {label || messages.common.states.loadingContent}
      </p>
    </div>
  );
}

interface ForbiddenStateProps {
  title: string;
  description: string;
  action?: React.ReactNode;
  className?: string;
}

export function ForbiddenState({
  title,
  description,
  action,
  className,
}: ForbiddenStateProps) {
  return (
    <div
      role="alert"
      className={cn(
        'flex min-h-[220px] flex-col items-center justify-center border border-[var(--portal-rule)] bg-[var(--portal-surface)] px-6 py-10 text-center',
        className,
      )}
    >
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-lg bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)]">
        <ShieldAlert className="h-5 w-5" aria-hidden="true" />
      </div>
      <h3 className="text-lg font-semibold text-foreground">{title}</h3>
      <p className="mt-2 max-w-md text-sm leading-6 text-muted-foreground">
        {description}
      </p>
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}
