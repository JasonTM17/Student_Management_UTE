export type StatusTone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

const STATUS_TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success/10 text-status-success-foreground',
  warning: 'bg-status-warning/10 text-status-warning-foreground',
  danger: 'bg-status-danger/10 text-status-danger-foreground',
  info: 'bg-status-info/10 text-status-info-foreground',
  neutral: 'bg-status-neutral/10 text-status-neutral-foreground',
};

const METRIC_TONE_CLASS: Record<StatusTone, string> = {
  success: 'bg-status-success/10 text-status-success-foreground',
  warning: 'bg-status-warning/10 text-status-warning-foreground',
  danger: 'bg-status-danger/10 text-status-danger-foreground',
  info: 'bg-status-info/10 text-status-info-foreground',
  neutral: 'bg-status-neutral/10 text-status-neutral',
};

export function statusToneClass(kind: StatusTone): string {
  return STATUS_TONE_CLASS[kind] ?? STATUS_TONE_CLASS.neutral;
}

export function metricToneClass(kind: StatusTone): string {
  return METRIC_TONE_CLASS[kind] ?? METRIC_TONE_CLASS.neutral;
}

export type PortalRole = 'STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN';

const ROLE_TONE_CLASS: Record<PortalRole, string> = {
  STUDENT: 'bg-role-student/10 text-role-student',
  LECTURER: 'bg-role-lecturer/10 text-role-lecturer',
  ADMIN: 'bg-role-admin/10 text-role-admin',
  SUPER_ADMIN: 'bg-role-super-admin/10 text-role-super-admin',
};

const ROLE_SOLID_CLASS: Record<PortalRole, string> = {
  STUDENT: 'bg-role-student-solid text-white shadow-xs hover:bg-role-student-solid/90',
  LECTURER: 'bg-role-lecturer-solid text-white shadow-xs hover:bg-role-lecturer-solid/90',
  ADMIN: 'bg-role-admin-solid text-white shadow-xs hover:bg-role-admin-solid/90',
  SUPER_ADMIN: 'bg-role-super-admin-solid text-white shadow-xs hover:bg-role-super-admin-solid/90',
};

export function roleToneClass(role: string): string {
  return ROLE_TONE_CLASS[role as PortalRole] ?? STATUS_TONE_CLASS.neutral;
}

export function roleSolidClass(role: string): string {
  return ROLE_SOLID_CLASS[role as PortalRole] ?? 'bg-primary text-primary-foreground';
}
