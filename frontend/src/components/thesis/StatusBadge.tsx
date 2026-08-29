import { cn } from '@/lib/utils';
import { useI18n } from '@/i18n';
import { statusToneClass, type StatusTone } from '@/components/ui/status';

type StatusVariant = 'default' | 'approval';

const STATUS_TONE_MAP: Record<StatusVariant, Record<string, StatusTone>> = {
  default: {
    APPROVED: 'success',
    PROPOSALS_PUBLISHED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'danger',
  },
  approval: {
    APPROVED: 'success',
    REJECTED: 'danger',
    PENDING: 'warning',
  },
};

function statusClass(status: string, variant: StatusVariant = 'default'): string {
  const tone = STATUS_TONE_MAP[variant]?.[status] ?? 'warning';
  return statusToneClass(tone);
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
