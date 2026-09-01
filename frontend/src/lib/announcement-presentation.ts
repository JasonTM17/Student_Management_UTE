import type { Locale } from '@/i18n/config';
import type { AnnouncementRecord } from '@/lib/api';

export const ANNOUNCEMENT_PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
export type AnnouncementPriority = (typeof ANNOUNCEMENT_PRIORITIES)[number];

export const ANNOUNCEMENT_ROLES = [
  'STUDENT',
  'LECTURER',
  'ADMIN',
  'SUPER_ADMIN',
] as const;
export type AnnouncementRole = (typeof ANNOUNCEMENT_ROLES)[number];

export type AnnouncementHistoryAction =
  | 'CREATED'
  | 'UPDATED'
  | 'ARCHIVED'
  | 'RESTORED';

const priorityLabels: Record<AnnouncementPriority, [string, string]> = {
  LOW: ['Thấp', 'Low'],
  NORMAL: ['Bình thường', 'Normal'],
  HIGH: ['Cao', 'High'],
  URGENT: ['Khẩn cấp', 'Urgent'],
};

const roleLabels: Record<AnnouncementRole, [string, string]> = {
  STUDENT: ['Sinh viên', 'Student'],
  LECTURER: ['Giảng viên', 'Lecturer'],
  ADMIN: ['Quản trị viên', 'Administrator'],
  SUPER_ADMIN: ['Quản trị viên cấp cao', 'Super administrator'],
};

const historyActionLabels: Record<AnnouncementHistoryAction, [string, string]> = {
  CREATED: ['Đã tạo', 'Created'],
  UPDATED: ['Đã chỉnh sửa', 'Edited'],
  ARCHIVED: ['Đã lưu trữ', 'Archived'],
  RESTORED: ['Đã khôi phục', 'Restored'],
};

function localizedPair(pair: [string, string], locale: Locale) {
  return pair[locale === 'vi' ? 0 : 1];
}

function normalized(value: unknown) {
  return typeof value === 'string' ? value.trim().toUpperCase() : '';
}

export function announcementPriorityLabel(value: unknown, locale: Locale) {
  const key = normalized(value) as AnnouncementPriority;
  return localizedPair(priorityLabels[key] ?? ['Khác', 'Other'], locale);
}

export function announcementPriorityTone(value: unknown) {
  switch (normalized(value)) {
    case 'URGENT':
      return 'danger' as const;
    case 'HIGH':
      return 'warning' as const;
    case 'NORMAL':
      return 'info' as const;
    default:
      return 'neutral' as const;
  }
}

export function announcementRoleLabel(value: unknown, locale: Locale) {
  const key = normalized(value) as AnnouncementRole;
  return localizedPair(roleLabels[key] ?? ['Đối tượng khác', 'Other audience'], locale);
}

export function announcementRoleValues(values: readonly unknown[] | null | undefined) {
  return (values ?? [])
    .map((value) => normalized(value))
    .filter((value): value is AnnouncementRole =>
      (ANNOUNCEMENT_ROLES as readonly string[]).includes(value),
    );
}

export function announcementAudienceLabel(
  announcement: Pick<AnnouncementRecord, 'isGlobal' | 'targetRoles'>,
  locale: Locale,
) {
  if (announcement.isGlobal) {
    return locale === 'vi' ? 'Toàn trường' : 'Campus-wide';
  }

  const labels = announcementRoleValues(announcement.targetRoles).map((role) =>
    announcementRoleLabel(role, locale),
  );
  return labels.length > 0
    ? labels.join(locale === 'vi' ? ', ' : ', ')
    : locale === 'vi'
      ? 'Chưa chọn đối tượng'
      : 'No audience selected';
}

export function announcementHistoryActionLabel(value: unknown, locale: Locale) {
  const key = normalized(value) as AnnouncementHistoryAction;
  return localizedPair(historyActionLabels[key] ?? ['Đã thay đổi', 'Changed'], locale);
}

/** Translate only compatibility-generated reasons; preserve administrator input verbatim. */
export function announcementHistoryReason(
  value: unknown,
  action: unknown,
  locale: Locale,
) {
  const reason = typeof value === 'string' ? value.trim() : '';
  if (!reason) return '—';
  if (reason === 'Announcement created' && normalized(action) === 'CREATED') {
    return locale === 'vi' ? 'Khởi tạo thông báo' : 'Announcement created';
  }
  if (reason === 'Archived from the legacy delete action' && normalized(action) === 'ARCHIVED') {
    return locale === 'vi' ? 'Lưu trữ thông báo' : 'Archived by a compatibility action';
  }
  return reason;
}

export function announcementIsUpdated(
  announcement: Pick<AnnouncementRecord, 'createdAt' | 'updatedAt'>,
) {
  if (!announcement.updatedAt || !announcement.createdAt) {
    return false;
  }
  const created = Date.parse(announcement.createdAt);
  const updated = Date.parse(announcement.updatedAt);
  return Number.isFinite(created) && Number.isFinite(updated) && updated > created;
}

export function announcementSemesterName(
  announcement: Pick<AnnouncementRecord, 'semesterName' | 'semester'>,
) {
  return announcement.semesterName || announcement.semester?.name || '';
}

export function announcementSectionLabel(
  announcement: Pick<AnnouncementRecord, 'sectionNumber' | 'courseCode' | 'section'>,
) {
  const courseCode = announcement.courseCode || announcement.section?.course?.code || '';
  const sectionNumber = announcement.sectionNumber || announcement.section?.sectionNumber || '';
  if (courseCode && sectionNumber) {
    return `${courseCode} · ${sectionNumber}`;
  }
  return courseCode || sectionNumber;
}

export function announcementLecturerName(
  announcement: Pick<AnnouncementRecord, 'lecturerDisplayName' | 'lecturer'>,
) {
  return announcement.lecturerDisplayName || announcement.lecturer?.displayName || '';
}

export function announcementPriorityValue(value: unknown): AnnouncementPriority {
  const key = normalized(value);
  return (ANNOUNCEMENT_PRIORITIES as readonly string[]).includes(key)
    ? (key as AnnouncementPriority)
    : 'NORMAL';
}
