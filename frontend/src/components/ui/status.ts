export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const STATUS_TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success/12 text-status-success-foreground',
  warning: 'bg-status-warning/12 text-status-warning-foreground',
  danger: 'bg-status-danger/12 text-status-danger-foreground',
  info: 'bg-status-info/12 text-status-info-foreground',
  neutral: 'bg-status-neutral/12 text-status-neutral-foreground',
};

const METRIC_TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success/12 text-status-success',
  warning: 'bg-status-warning/12 text-status-warning',
  danger: 'bg-status-danger/12 text-status-danger',
  info: 'bg-status-info/12 text-status-info',
  neutral: 'bg-status-neutral/12 text-status-neutral',
};

export function statusToneClass(kind: StatusTone): string {
  return STATUS_TONE_CLASS[kind] ?? STATUS_TONE_CLASS.neutral;
}

export function metricToneClass(kind: StatusTone): string {
  return METRIC_TONE_CLASS[kind] ?? METRIC_TONE_CLASS.neutral;
}
