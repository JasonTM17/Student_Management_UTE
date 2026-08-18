import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';

type StatusVariant = 'default' | 'approval' | 'council';

const STATUS_CLASS_MAP: Record<StatusVariant, Record<string, string>> = {
  default: {
    APPROVED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    RESULTS_PUBLISHED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    PROPOSALS_PUBLISHED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    FINALIZED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    SCHEDULED: 'bg-cyan-500/12 text-cyan-700 dark:text-cyan-300',
    REJECTED: 'bg-red-500/12 text-red-700 dark:text-red-300',
    CANCELLED: 'bg-red-500/12 text-red-700 dark:text-red-300',
  },
  approval: {
    APPROVED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    REJECTED: 'bg-red-500/12 text-red-700 dark:text-red-300',
    PENDING: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
  },
  council: {
    FINALIZED: 'bg-emerald-500/12 text-emerald-700 dark:text-emerald-300',
    SCHEDULED: 'bg-cyan-500/12 text-cyan-700 dark:text-cyan-300',
    SCORING_OPEN: 'bg-violet-500/12 text-violet-700 dark:text-violet-300',
    DRAFT: 'bg-amber-500/12 text-amber-700 dark:text-amber-300',
    CANCELLED: 'bg-red-500/12 text-red-700 dark:text-red-300',
  },
};

function statusClass(status: string, variant: StatusVariant = 'default'): string {
  return STATUS_CLASS_MAP[variant]?.[status] ?? 'bg-amber-500/12 text-amber-700 dark:text-amber-300';
}

interface StatusBadgeProps {
  status: string;
  variant?: StatusVariant;
  className?: string;
}

export function StatusBadge({ status, variant = 'default', className }: StatusBadgeProps) {
  const { messages } = useI18n();
  const label = messages.thesis.status[status as keyof typeof messages.thesis.status] ?? status;

  return (
    <span className={cn('inline-flex rounded-full px-2.5 py-1 text-xs font-semibold', statusClass(status, variant), className)}>
      {label}
    </span>
  );
}

export { statusClass };
