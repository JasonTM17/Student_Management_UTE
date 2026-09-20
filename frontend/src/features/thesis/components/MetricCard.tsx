'use client';

import { Card, CardContent } from '@/components/ui/card';
import { metricToneClass, type StatusTone } from '@/components/ui/status';
import { cn } from '@/lib/utils';

/** Fills `{name}` placeholders in a copy template. */
export function fillCopy(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}

export function formatReportFileSize(bytes: number | null | undefined, locale: string): string | null {
  if (!Number.isFinite(bytes) || !bytes || bytes < 0) return null;
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const formatted = new Intl.NumberFormat(locale === 'vi' ? 'vi-VN' : 'en-US', {
    maximumFractionDigits: unitIndex === 0 ? 0 : 1,
  }).format(value);
  return `${formatted} ${units[unitIndex]}`;
}

/** Renders `**bold**` spans inside a copy string as inline emphasis. */
export function renderInlineBold(text: string): React.ReactNode[] {
  return text
    .split('**')
    .map((part, index) => (index % 2 === 1 ? <strong key={index}>{part}</strong> : <span key={index}>{part}</span>));
}

interface MetricCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  tone: StatusTone;
}

export default function MetricCard({ label, value, icon, tone }: MetricCardProps) {
  return (
    <Card variant="elevated">
      <CardContent className="flex items-start justify-between gap-4 pt-6">
        <div className={cn('flex h-11 w-11 shrink-0 items-center justify-center rounded-lg', metricToneClass(tone))}>{icon}</div>
        <div className="min-w-0 text-right">
          <div className="break-words text-2xl font-semibold tracking-tight text-foreground">{value}</div>
          <div className="mt-1 text-sm text-muted-foreground">{label}</div>
        </div>
      </CardContent>
    </Card>
  );
}
