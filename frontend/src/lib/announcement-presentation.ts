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

export function formatAnnouncementSemester(
  announcement: Pick<AnnouncementRecord, 'semesterName' | 'semester'>,
  locale: Locale,
  prefix = locale === 'vi' ? 'Học kỳ' : 'Semester',
) {
  const raw = announcementSemesterName(announcement);
  if (!raw) return '';
  const trimmed = raw.trim();
  const lower = trimmed.toLowerCase();
  const lowerPrefix = prefix.toLowerCase();
  if (lower.startsWith(lowerPrefix)) {
    return trimmed;
  }
  return `${prefix}: ${trimmed}`;
}

export function announcementAudienceType(
  announcement: Pick<AnnouncementRecord, 'isGlobal' | 'targetRoles'>,
): 'GLOBAL' | 'STUDENT' | 'LECTURER' | 'OTHER' {
  if (announcement.isGlobal) {
    return 'GLOBAL';
  }
  const roles = announcementRoleValues(announcement.targetRoles);
  const hasStudent = roles.includes('STUDENT');
  const hasLecturer = roles.includes('LECTURER');

  if (hasStudent && hasLecturer) {
    return 'GLOBAL';
  }
  if (hasLecturer && !hasStudent) {
    return 'LECTURER';
  }
  if (hasStudent && !hasLecturer) {
    return 'STUDENT';
  }
  return 'OTHER';
}

export function announcementAudienceBadge(
  announcement: Pick<AnnouncementRecord, 'isGlobal' | 'targetRoles'>,
  locale: Locale,
): { label: string; tone: 'info' | 'primary' | 'neutral' } {
  const type = announcementAudienceType(announcement);
  switch (type) {
    case 'GLOBAL':
      return {
        label: locale === 'vi' ? '🌐 Thông báo toàn trường' : '🌐 Campus-wide Notice',
        tone: 'info',
      };
    case 'STUDENT':
      return {
        label: locale === 'vi' ? '🎓 Dành riêng cho Sinh viên' : '🎓 Student Dedicated',
        tone: 'primary',
      };
    case 'LECTURER':
      return {
        label: locale === 'vi' ? '👨‍🏫 Nghiệp vụ Giảng viên' : '👨‍🏫 Faculty & Lecturer',
        tone: 'primary',
      };
    default:
      return {
        label: locale === 'vi' ? 'Thông báo chuyên biệt' : 'Specialized Notice',
        tone: 'neutral',
      };
  }
}

export function announcementSalutation(
  announcement: Pick<AnnouncementRecord, 'isGlobal' | 'targetRoles'>,
  locale: Locale,
): string {
  const type = announcementAudienceType(announcement);
  switch (type) {
    case 'STUDENT':
      return locale === 'vi'
        ? 'Toàn thể sinh viên, học viên Nhà trường'
        : 'All students and trainees of the University';
    case 'LECTURER':
      return locale === 'vi'
        ? 'Quý Thầy/Cô, Cán bộ giảng dạy và Nghiên cứu viên Nhà trường'
        : 'All Faculty Members, Lecturers, and Researchers of the University';
    case 'GLOBAL':
    default:
      return locale === 'vi'
        ? 'Toàn thể cán bộ, giảng viên, học viên và sinh viên Nhà trường'
        : 'All faculty members, staff, researchers, and students of the University';
  }
}

export function announcementDistribution(
  announcement: Pick<AnnouncementRecord, 'isGlobal' | 'targetRoles'>,
  locale: Locale,
): string[] {
  const type = announcementAudienceType(announcement);
  if (type === 'STUDENT') {
    return locale === 'vi'
      ? [
          '- Ban Giám hiệu (để báo cáo);',
          '- Phòng Đào tạo, Phòng CTSV;',
          '- Văn phòng các Khoa/Bộ môn;',
          '- Toàn thể sinh viên, học viên (để thực hiện);',
          '- Lưu: Văn thư, CNTT.',
        ]
      : [
          '- Board of Rectors (for report);',
          '- Academic Affairs & Student Affairs Offices;',
          '- Faculty Offices & Departments;',
          '- All students and trainees (for action);',
          '- Archives: Secretariat, IT.',
        ];
  }

  if (type === 'LECTURER') {
    return locale === 'vi'
      ? [
          '- Ban Giám hiệu (để báo cáo);',
          '- Ban Chủ nhiệm các Khoa, Trưởng Bộ môn;',
          '- Quý Thầy/Cô cán bộ giảng dạy (để thực hiện);',
          '- Phòng Đào tạo, Phòng Khảo thí & ĐBCL;',
          '- Lưu: Văn thư, HCTH.',
        ]
      : [
          '- Board of Rectors (for report);',
          '- Deans of Faculties, Department Heads;',
          '- All faculty members & lecturers (for action);',
          '- Academic Affairs, Testing & QA Offices;',
          '- Archives: Secretariat, Administration.',
        ];
  }

  return locale === 'vi'
    ? [
        '- Ban Giám hiệu (để báo cáo);',
        '- Các Khoa, Phòng, Viện, Trung tâm;',
        '- Toàn thể giảng viên, sinh viên;',
        '- Lưu: Văn thư, CNTT.',
      ]
    : [
        '- Board of Rectors (for report);',
        '- Faculties, Departments, Centers;',
        '- All lecturers and students;',
        '- Archives: Secretariat, IT.',
      ];
}

export function formatAnnouncementPublisher(
  publishedBy: string | null | undefined,
  locale: Locale,
  fallback = locale === 'vi' ? 'Khoa Công nghệ Thông tin & Phòng Đào Tạo' : 'Faculty of Information Technology & Academic Affairs Office',
): string {
  const trimmed = typeof publishedBy === 'string' ? publishedBy.trim() : '';
  if (!trimmed) {
    return fallback;
  }
  const lower = trimmed.toLowerCase();
  if (
    lower === 'admin' ||
    lower.startsWith('admin-user') ||
    lower.startsWith('admin_user') ||
    lower === 'super_admin' ||
    lower === 'system' ||
    lower === 'user' ||
    /^[0-9a-f]{8}-[0-9a-f]{4}/i.test(lower)
  ) {
    return fallback;
  }
  if (
    lower.includes('khoa học công nghệ') ||
    lower.includes('khcn') ||
    lower.includes('htqt') ||
    lower.includes('r&d') ||
    lower.includes('international cooperation')
  ) {
    return locale === 'vi'
      ? 'Khoa Công nghệ Thông tin & Phòng Đào Tạo'
      : 'Faculty of Information Technology & Academic Affairs Office';
  }
  return trimmed;
}

export type AnnouncementDomainType = 'OFFICIAL_DISPATCH' | 'EDITORIAL_ARTICLE';

export interface DomainResolution {
  domain: AnnouncementDomainType;
  categoryLabel: string;
  categoryTone: 'primary' | 'info' | 'success' | 'warning' | 'danger';
  iconType: 'document' | 'newspaper' | 'award' | 'flask' | 'calendar' | 'briefcase';
  accentGradient: string;
}

/**
 * Intelligently classifies an announcement into:
 * - OFFICIAL_DISPATCH: Formal institutional decree (NĐ 30/2020) for academic affairs, regulations, exams
 * - EDITORIAL_ARTICLE: Engaging academic magazine/news for tech talks, research, competitions, scholarships, jobs
 */
export function resolveAnnouncementDomain(
  announcement: Pick<AnnouncementRecord, 'title' | 'content' | 'publishedBy'> & { documentType?: string },
  locale: Locale = 'vi',
): DomainResolution {
  const isVi = locale === 'vi';

  // 1. Explicit flag if present
  if (announcement.documentType === 'EDITORIAL') {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryLabel: isVi ? 'Tin tức & Học thuật' : 'Academic News',
      categoryTone: 'primary',
      iconType: 'newspaper',
      accentGradient: 'from-blue-600 to-indigo-700',
    };
  }
  if (announcement.documentType === 'OFFICIAL') {
    return {
      domain: 'OFFICIAL_DISPATCH',
      categoryLabel: isVi ? 'Công văn e-Office' : 'Official Dispatch',
      categoryTone: 'danger',
      iconType: 'document',
      accentGradient: 'from-red-600 to-rose-700',
    };
  }

  const title = (announcement.title || '').toLowerCase();
  const publisher = (announcement.publishedBy || '').toLowerCase();
  const content = (announcement.content || '').toLowerCase();

  // Strict administrative indicators
  const isStrictAdmin =
    title.startsWith('quyết định') ||
    title.includes('kế hoạch mở cổng đăng ký') ||
    title.includes('lịch thi kết thúc') ||
    title.includes('phúc khảo') ||
    title.includes('xử lý học vụ') ||
    publisher.includes('phòng khảo thí');

  if (isStrictAdmin) {
    return {
      domain: 'OFFICIAL_DISPATCH',
      categoryLabel: isVi ? 'Công văn Đào tạo & Khảo thí' : 'Academic Dispatch',
      categoryTone: 'danger',
      iconType: 'document',
      accentGradient: 'from-red-600 to-amber-600',
    };
  }

  // Editorial domains
  if (title.includes('học bổng') || content.includes('học bổng khuyến khích')) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryLabel: isVi ? 'Học bổng & Khen thưởng' : 'Scholarships & Honors',
      categoryTone: 'success',
      iconType: 'award',
      accentGradient: 'from-emerald-600 to-teal-700',
    };
  }

  if (
    title.includes('khởi động') ||
    title.includes('nghiên cứu') ||
    title.includes('big data') ||
    title.includes('trí tuệ nhân tạo') ||
    title.includes('ai') ||
    title.includes('lab')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryLabel: isVi ? 'Nghiên cứu & Công nghệ' : 'Research & Tech',
      categoryTone: 'info',
      iconType: 'flask',
      accentGradient: 'from-cyan-600 to-blue-700',
    };
  }

  if (
    title.includes('ngày hội') ||
    title.includes('tuyển dụng') ||
    title.includes('career') ||
    title.includes('việc làm') ||
    publisher.includes('hướng nghiệp')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryLabel: isVi ? 'Sự kiện & Hướng nghiệp' : 'Career & Events',
      categoryTone: 'warning',
      iconType: 'briefcase',
      accentGradient: 'from-amber-600 to-orange-700',
    };
  }

  if (
    title.includes('hội thảo') ||
    title.includes('seminar') ||
    title.includes('hackathon') ||
    title.includes('cuộc thi')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryLabel: isVi ? 'Hội thảo & Sự kiện' : 'Seminars & Events',
      categoryTone: 'primary',
      iconType: 'newspaper',
      accentGradient: 'from-violet-600 to-purple-700',
    };
  }

  // Default to official dispatch
  return {
    domain: 'OFFICIAL_DISPATCH',
    categoryLabel: isVi ? 'Văn bản Hành chính' : 'Official Notice',
    categoryTone: 'danger',
    iconType: 'document',
    accentGradient: 'from-blue-700 to-slate-800',
  };
}

/**
 * Calculates estimated reading time in minutes and total word count.
 */
export function calculateReadingTime(content: string | null | undefined): {
  minutes: number;
  wordCount: number;
  displayText: string;
} {
  if (!content) {
    return { minutes: 1, wordCount: 0, displayText: '1 phút đọc' };
  }
  // Strip html and markdown
  const clean = content
    .replace(/<[^>]+>/g, ' ')
    .replace(/[#*`_~[\]()|>-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  const words = clean ? clean.split(/\s+/).length : 0;
  const minutes = Math.max(1, Math.ceil(words / 220));
  return {
    minutes,
    wordCount: words,
    displayText: `${minutes} phút đọc (${words.toLocaleString('vi-VN')} từ)`,
  };
}

/**
 * Formats a timestamp into human-friendly relative time ("2 giờ trước", "Hôm qua")
 */
export function formatRelativeTime(
  dateString: string | null | undefined,
  locale: Locale = 'vi',
): string {
  if (!dateString) return '';
  const date = new Date(dateString);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffSec = Math.floor(diffMs / 1000);
  const diffMin = Math.floor(diffSec / 60);
  const diffHours = Math.floor(diffMin / 60);
  const diffDays = Math.floor(diffHours / 24);

  const isVi = locale === 'vi';

  if (diffMin < 1) {
    return isVi ? 'Vừa xong' : 'Just now';
  }
  if (diffMin < 60) {
    return isVi ? `${diffMin} phút trước` : `${diffMin}m ago`;
  }
  if (diffHours < 24) {
    return isVi ? `${diffHours} giờ trước` : `${diffHours}h ago`;
  }
  if (diffDays === 1) {
    return isVi ? 'Hôm qua' : 'Yesterday';
  }
  if (diffDays < 7) {
    return isVi ? `${diffDays} ngày trước` : `${diffDays}d ago`;
  }
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = date.getFullYear();
  return `${day}/${month}/${year}`;
}

/**
 * Extracts a clean textual excerpt (Sapo) from raw Markdown/HTML.
 */
export function extractAnnouncementExcerpt(
  content: string | null | undefined,
  maxLength = 160,
): string {
  if (!content) return '';
  const clean = content
    .replace(/<[^>]+>/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]+\)/g, '') // remove images
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // unwrap links
    .replace(/[#*`_~|>-]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (clean.length <= maxLength) return clean;
  return clean.slice(0, maxLength).trim() + '...';
}

/**
 * Extracts the first image URL from Markdown or HTML content if present.
 */
export function extractCoverImage(content: string | null | undefined): string | null {
  if (!content) return null;
  // Match markdown image ![alt](url)
  const mdMatch = content.match(/!\[[^\]]*\]\((https?:\/\/[^)\s]+|\/[^)\s]+)\)/i);
  if (mdMatch && mdMatch[1]) {
    return mdMatch[1];
  }
  // Match html img src
  const htmlMatch = content.match(/<img[^>]+src=["'](https?:\/\/[^"'\s]+|\/[^"'\s]+)["']/i);
  if (htmlMatch && htmlMatch[1]) {
    return htmlMatch[1];
  }
  return null;
}
