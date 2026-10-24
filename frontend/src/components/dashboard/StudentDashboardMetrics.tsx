import { ArrowRight } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { WorkspacePanel } from '@/components/dashboard/WorkspaceSurface';
import { cn } from '@/lib/utils';
import type { StudentDashboardEnrollment } from '@/lib/student-dashboard-metrics';
import type { ElementType } from 'react';

interface DashboardMetricTotals {
  activeCourses: StudentDashboardEnrollment[];
  pendingCourses: StudentDashboardEnrollment[];
  enrolledCourseCount: number;
  currentCredits: number;
  creditCap: number | null;
  creditPercent: number | null;
  creditRemaining: number | null;
}

interface StudentDashboardMetricCardsProps {
  chips: Array<{
    label: string;
    value: string;
    icon: ElementType<{ className?: string }>;
    tone: string;
    href: string;
    tooltip: string;
    wraps: boolean;
  }>;
}

export function StudentDashboardMetricCards({ chips }: StudentDashboardMetricCardsProps) {
  return (
    <div
      data-dashboard-metrics="student-overview"
      className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
    >
      {chips.map((chip) => (
        <LocalizedLink
          key={chip.label}
          href={chip.href}
          title={chip.tooltip}
          className="group relative flex min-w-0 items-center justify-between gap-3 rounded-xl border border-border/70 bg-card p-3.5 shadow-xs transition-all duration-200 hover:-translate-y-0.5 hover:border-primary/50 hover:bg-muted/30 hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring cursor-pointer"
        >
          <div className="flex min-w-0 items-center gap-3">
            <span
              className={cn(
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-lg transition-transform duration-200 group-hover:scale-105',
                chip.tone,
              )}
            >
              <chip.icon className="h-5 w-5" />
            </span>
            <div className="min-w-0">
              <div
                className={cn(
                  'font-bold leading-6 text-foreground group-hover:text-primary transition-colors',
                  chip.wraps
                    ? 'line-clamp-3 break-words text-lg'
                    : 'truncate text-xl',
                )}
                title={chip.value}
              >
                {chip.value}
              </div>
              <div
                className="truncate text-xs font-medium text-muted-foreground"
                title={chip.label}
              >
                {chip.label}
              </div>
            </div>
          </div>
          <ArrowRight className="h-4 w-4 shrink-0 text-muted-foreground/40 opacity-0 transition-all duration-200 group-hover:translate-x-0.5 group-hover:text-primary group-hover:opacity-100" />
        </LocalizedLink>
      ))}
    </div>
  );
}

interface StudentDashboardCreditProgressProps {
  metrics: DashboardMetricTotals;
  formatNumber: (value: number) => string;
  copy: {
    title: string;
    creditLabel: string;
    creditsEarnedOnly: string;
    progressStatus: (percent: number) => string;
    creditsAvailable: (credits: number) => string;
    creditsCapped: string;
    pendingBadge: (count: string) => string;
  };
}

export function StudentDashboardCreditProgress({
  metrics,
  formatNumber,
  copy,
}: StudentDashboardCreditProgressProps) {
  return (
    <WorkspacePanel title={copy.title} contentClassName="space-y-3.5">
      <div className="flex items-center justify-between gap-2 text-sm">
        <span className="font-medium text-foreground">{copy.creditLabel}</span>
        <span className="font-semibold tabular-nums text-foreground">
          {formatNumber(metrics.currentCredits)}{metrics.creditCap ? ` / ${formatNumber(metrics.creditCap)}` : ''}
        </span>
      </div>
      <div className="h-2.5 w-full overflow-hidden rounded-full bg-secondary">
        <div
          className="h-full rounded-full bg-primary transition-all duration-300"
          style={{ width: `${metrics.creditPercent ?? 0}%` }}
        />
      </div>
      <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground pt-0.5">
        <span>
          {metrics.creditPercent === null
            ? copy.creditsEarnedOnly
            : copy.progressStatus(metrics.creditPercent)}
        </span>
        {metrics.creditRemaining !== null ? (
          <span className="font-medium text-foreground/80">
            {metrics.creditRemaining > 0
              ? copy.creditsAvailable(metrics.creditRemaining)
              : copy.creditsCapped}
          </span>
        ) : null}
      </div>
      {metrics.pendingCourses.length > 0 ? (
        <div className="pt-0.5">
          <span className="inline-flex items-center gap-1.5 rounded-md border border-status-warning/30 bg-status-warning/10 px-2.5 py-1 text-xs font-semibold text-status-warning-foreground">
            {copy.pendingBadge(formatNumber(metrics.pendingCourses.length))}
          </span>
        </div>
      ) : null}
    </WorkspacePanel>
  );
}
