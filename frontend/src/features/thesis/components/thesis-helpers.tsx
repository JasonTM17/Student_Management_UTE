'use client';

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

// The dashboard metric tile itself was consolidated into
// `WorkspaceMetricCard` (components/dashboard/WorkspaceSurface) in Phase 6 —
// this module now holds only the shared thesis copy/file-size helpers.
