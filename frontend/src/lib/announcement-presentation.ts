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

export type ArticleCategoryCode =
  | 'RESEARCH_TECH'
  | 'AWARDS_HONORS'
  | 'STUDENT_LIFE'
  | 'CULTURE_ARTS'
  | 'ACADEMIC_AFFAIRS'
  | 'CAREER_OPPORTUNITIES';

export interface ArticleCategoryMeta {
  code: ArticleCategoryCode;
  nameVi: string;
  nameEn: string;
  colorTone: 'cyan' | 'emerald' | 'amber' | 'rose' | 'indigo' | 'purple';
  iconType: 'flask' | 'award' | 'users' | 'sparkles' | 'book' | 'briefcase';
  accentGradient: string;
}

export const INSTITUTIONAL_ARTICLE_CATEGORIES: Record<ArticleCategoryCode, ArticleCategoryMeta> = {
  RESEARCH_TECH: {
    code: 'RESEARCH_TECH',
    nameVi: 'Nghiên cứu & Công nghệ',
    nameEn: 'Research & Technology',
    colorTone: 'cyan',
    iconType: 'flask',
    accentGradient: 'from-cyan-600 via-teal-600 to-blue-700',
  },
  AWARDS_HONORS: {
    code: 'AWARDS_HONORS',
    nameVi: 'Học bổng & Khen thưởng',
    nameEn: 'Scholarships & Honors',
    colorTone: 'emerald',
    iconType: 'award',
    accentGradient: 'from-emerald-600 via-teal-600 to-green-700',
  },
  STUDENT_LIFE: {
    code: 'STUDENT_LIFE',
    nameVi: 'Đời sống Sinh viên',
    nameEn: 'Student Life & Youth',
    colorTone: 'amber',
    iconType: 'users',
    accentGradient: 'from-amber-600 via-orange-600 to-red-600',
  },
  CULTURE_ARTS: {
    code: 'CULTURE_ARTS',
    nameVi: 'Văn hóa & Nghệ thuật',
    nameEn: 'Culture & Arts',
    colorTone: 'rose',
    iconType: 'sparkles',
    accentGradient: 'from-rose-600 via-pink-600 to-purple-700',
  },
  ACADEMIC_AFFAIRS: {
    code: 'ACADEMIC_AFFAIRS',
    nameVi: 'Đào tạo & Học vụ',
    nameEn: 'Academic Affairs',
    colorTone: 'indigo',
    iconType: 'book',
    accentGradient: 'from-indigo-600 via-blue-600 to-slate-800',
  },
  CAREER_OPPORTUNITIES: {
    code: 'CAREER_OPPORTUNITIES',
    nameVi: 'Cơ hội Việc làm',
    nameEn: 'Career & Industry',
    colorTone: 'purple',
    iconType: 'briefcase',
    accentGradient: 'from-purple-600 via-violet-600 to-indigo-700',
  },
};

export type AnnouncementDomainType = 'OFFICIAL_DISPATCH' | 'EDITORIAL_ARTICLE';

export interface DomainResolution {
  domain: AnnouncementDomainType;
  categoryCode: ArticleCategoryCode;
  categoryLabel: string;
  categoryTone: 'primary' | 'info' | 'success' | 'warning' | 'danger';
  iconType: 'document' | 'newspaper' | 'award' | 'flask' | 'calendar' | 'briefcase' | 'users' | 'sparkles' | 'book';
  accentGradient: string;
}

/**
 * Intelligently classifies an announcement into institutional categories and domains
 */
export function resolveAnnouncementDomain(
  announcement: Pick<AnnouncementRecord, 'title' | 'content' | 'publishedBy'> & { documentType?: string; categoryId?: string | null },
  locale: Locale = 'vi',
): DomainResolution {
  const isVi = locale === 'vi';
  const title = (announcement.title || '').toLowerCase();
  const publisher = (announcement.publishedBy || '').toLowerCase();
  const content = (announcement.content || '').toLowerCase();

  // 1. Check categoryId explicit mapping if present
  if (announcement.categoryId) {
    const cat = announcement.categoryId.toLowerCase();
    if (cat.includes('research') || cat.includes('tech')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'RESEARCH_TECH',
        categoryLabel: isVi ? 'Nghiên cứu & Công nghệ' : 'Research & Tech',
        categoryTone: 'info',
        iconType: 'flask',
        accentGradient: 'from-cyan-600 via-blue-600 to-indigo-700',
      };
    }
    if (cat.includes('award') || cat.includes('honor')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'AWARDS_HONORS',
        categoryLabel: isVi ? 'Học bổng & Khen thưởng' : 'Scholarships & Honors',
        categoryTone: 'success',
        iconType: 'award',
        accentGradient: 'from-emerald-600 via-teal-600 to-green-700',
      };
    }
    if (cat.includes('student') || cat.includes('life')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'STUDENT_LIFE',
        categoryLabel: isVi ? 'Đời sống Sinh viên' : 'Student Life',
        categoryTone: 'warning',
        iconType: 'users',
        accentGradient: 'from-amber-600 via-orange-600 to-red-600',
      };
    }
    if (cat.includes('culture') || cat.includes('art')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'CULTURE_ARTS',
        categoryLabel: isVi ? 'Văn hóa & Nghệ thuật' : 'Culture & Arts',
        categoryTone: 'primary',
        iconType: 'sparkles',
        accentGradient: 'from-rose-600 via-pink-600 to-purple-700',
      };
    }
    if (cat.includes('career') || cat.includes('job')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'CAREER_OPPORTUNITIES',
        categoryLabel: isVi ? 'Cơ hội Việc làm' : 'Career & Industry',
        categoryTone: 'primary',
        iconType: 'briefcase',
        accentGradient: 'from-purple-600 via-violet-600 to-indigo-700',
      };
    }
    if (cat.includes('academic') || cat.includes('affair')) {
      return {
        domain: 'EDITORIAL_ARTICLE',
        categoryCode: 'ACADEMIC_AFFAIRS',
        categoryLabel: isVi ? 'Đào tạo & Học vụ' : 'Academic Affairs',
        categoryTone: 'primary',
        iconType: 'book',
        accentGradient: 'from-indigo-600 via-blue-700 to-slate-800',
      };
    }
  }

  // 2. Strict administrative indicators
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
      categoryCode: 'ACADEMIC_AFFAIRS',
      categoryLabel: isVi ? 'Công văn Đào tạo & Khảo thí' : 'Academic Dispatch',
      categoryTone: 'danger',
      iconType: 'document',
      accentGradient: 'from-red-600 to-amber-600',
    };
  }

  // 3. Culture & Arts
  if (title.includes('20/11') || title.includes('nhà giáo') || title.includes('nghệ thuật') || title.includes('nhạc hội') || title.includes('hoa sen')) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'CULTURE_ARTS',
      categoryLabel: isVi ? 'Văn hóa & Nghệ thuật' : 'Culture & Arts',
      categoryTone: 'primary',
      iconType: 'sparkles',
      accentGradient: 'from-rose-600 via-pink-600 to-purple-700',
    };
  }

  // 4. Student Life & Community
  if (
    title.includes('mùa hè xanh') ||
    title.includes('tình nguyện') ||
    title.includes('bóng đá') ||
    title.includes('champions cup') ||
    title.includes('hiến máu') ||
    title.includes('giọt hồng') ||
    title.includes('ký túc xá') ||
    title.includes('ktx')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'STUDENT_LIFE',
      categoryLabel: isVi ? 'Đời sống Sinh viên' : 'Student Life',
      categoryTone: 'warning',
      iconType: 'users',
      accentGradient: 'from-amber-600 via-orange-600 to-red-600',
    };
  }

  // 5. Awards & Honors
  if (title.includes('học bổng') || content.includes('học bổng khuyến khích') || title.includes('tôn vinh') || title.includes('giảng viên xuất sắc') || title.includes('tiêu biểu')) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'AWARDS_HONORS',
      categoryLabel: isVi ? 'Học bổng & Khen thưởng' : 'Scholarships & Honors',
      categoryTone: 'success',
      iconType: 'award',
      accentGradient: 'from-emerald-600 to-teal-700',
    };
  }

  // 6. Research & Tech
  if (
    title.includes('bán dẫn') ||
    title.includes('vi mạch') ||
    title.includes('cleanroom') ||
    title.includes('robotics') ||
    title.includes('tự động hóa') ||
    title.includes('iot') ||
    title.includes('ieee') ||
    title.includes('hackathon') ||
    title.includes('khởi động') ||
    title.includes('nghiên cứu') ||
    title.includes('big data') ||
    title.includes('trí tuệ nhân tạo') ||
    title.includes('ai') ||
    title.includes('lab')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'RESEARCH_TECH',
      categoryLabel: isVi ? 'Nghiên cứu & Công nghệ' : 'Research & Tech',
      categoryTone: 'info',
      iconType: 'flask',
      accentGradient: 'from-cyan-600 to-blue-700',
    };
  }

  // 7. Career
  if (
    title.includes('ngày hội') ||
    title.includes('tuyển dụng') ||
    title.includes('career') ||
    title.includes('việc làm') ||
    publisher.includes('hướng nghiệp')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'CAREER_OPPORTUNITIES',
      categoryLabel: isVi ? 'Sự kiện & Hướng nghiệp' : 'Career & Events',
      categoryTone: 'warning',
      iconType: 'briefcase',
      accentGradient: 'from-amber-600 to-orange-700',
    };
  }

  // 8. Academic Affairs / Library / Graduation
  if (
    title.includes('thư viện') ||
    title.includes('tốt nghiệp') ||
    title.includes('trao bằng') ||
    title.includes('khóa luận') ||
    title.includes('kltn') ||
    title.includes('học phần')
  ) {
    return {
      domain: 'EDITORIAL_ARTICLE',
      categoryCode: 'ACADEMIC_AFFAIRS',
      categoryLabel: isVi ? 'Đào tạo & Học vụ' : 'Academic Affairs',
      categoryTone: 'primary',
      iconType: 'book',
      accentGradient: 'from-indigo-600 to-blue-700',
    };
  }

  // Default to official dispatch
  return {
    domain: 'OFFICIAL_DISPATCH',
    categoryCode: 'ACADEMIC_AFFAIRS',
    categoryLabel: isVi ? 'Văn bản Hành chính' : 'Official Notice',
    categoryTone: 'danger',
    iconType: 'document',
    accentGradient: 'from-blue-700 to-slate-800',
  };
}

/**
 * Resolves the primary 16:9 editorial cover image for an announcement:
 * 1. Checks explicit coverImageUrl
 * 2. Checks extracted image in content
 * 3. Intelligently falls back to our 18 authentic editorial photography assets
 */
export function resolveArticleCover(
  announcement: Pick<AnnouncementRecord, 'title' | 'content'> & { coverImageUrl?: string | null },
): string {
  if (announcement.coverImageUrl && typeof announcement.coverImageUrl === 'string' && announcement.coverImageUrl.trim()) {
    return announcement.coverImageUrl.trim();
  }

  const extracted = extractCoverImage(announcement.content);
  if (extracted) {
    return extracted;
  }

  const title = (announcement.title || '').toLowerCase();
  const content = (announcement.content || '').toLowerCase();

  // Keyword-to-photo matching
  if (title.includes('bán dẫn') || title.includes('vi mạch') || title.includes('cleanroom') || title.includes('semiconductor')) {
    return '/images/news/semiconductor-cleanroom.jpg';
  }
  if (title.includes('robotics') || title.includes('robot') || title.includes('tự động hóa') || title.includes('iot')) {
    return '/images/news/robotics-iot-lab.jpg';
  }
  if (title.includes('mùa hè xanh') || title.includes('tình nguyện') || title.includes('nông thôn')) {
    return '/images/news/green-summer-volunteer.jpg';
  }
  if (title.includes('bóng đá') || title.includes('champions cup') || title.includes('thể thao')) {
    return '/images/news/campus-sports-cup.jpg';
  }
  if (title.includes('20/11') || title.includes('nhà giáo') || title.includes('tri ân') || title.includes('nghệ thuật') || title.includes('nhạc hội')) {
    return '/images/news/cultural-arts-gala.jpg';
  }
  if (title.includes('ieee') || title.includes('stem') || title.includes('hội nghị quốc tế') || title.includes('keynote')) {
    return '/images/news/stem-conference-keynote.jpg';
  }
  if (title.includes('hackathon') || title.includes('đổi mới sáng tạo') || title.includes('48 giờ') || title.includes('48h')) {
    return '/images/news/ai-hackathon-arena.jpg';
  }
  if (title.includes('thư viện') || title.includes('learning commons') || title.includes('học liệu')) {
    return '/images/news/campus-digital-library.jpg';
  }
  if (title.includes('tốt nghiệp') || title.includes('trao bằng') || title.includes('tân khoa') || title.includes('commencement')) {
    return '/images/news/commencement-graduation.jpg';
  }
  if (title.includes('hiến máu') || title.includes('giọt hồng') || title.includes('chữ thập đỏ') || title.includes('nhân đạo')) {
    return '/images/news/blood-donation-day.jpg';
  }
  if (title.includes('ktx') || title.includes('ký túc xá') || title.includes('nội trú') || title.includes('dormitory')) {
    return '/images/news/student-dormitory-campus.jpg';
  }
  if (title.includes('tôn vinh') || title.includes('giảng viên xuất sắc') || title.includes('nhà khoa học tiêu biểu') || title.includes('excellence')) {
    return '/images/news/faculty-excellence-awards.jpg';
  }
  if (title.includes('big data') || title.includes('a100') || title.includes('hpc') || (title.includes('ai') && content.includes('gpu'))) {
    return '/images/news/bigdata-ai-lab.jpg';
  }
  if (title.includes('việc làm') || title.includes('tuyển dụng') || title.includes('career') || title.includes('job fair')) {
    return '/images/news/tech-career-expo.jpg';
  }
  if (title.includes('học bổng') || content.includes('học bổng khuyến khích') || title.includes('khen thưởng')) {
    return '/images/news/scholarship-ceremony.jpg';
  }
  if (title.includes('đăng ký học phần') || title.includes('tín chỉ') || title.includes('thời khóa biểu')) {
    return '/images/news/course-registration.jpg';
  }
  if (title.includes('khóa luận') || title.includes('kltn') || title.includes('bảo vệ')) {
    return '/images/news/thesis-defense.jpg';
  }
  if (title.includes('nghiên cứu khoa học') || title.includes('nckh') || title.includes('sáng tạo trẻ')) {
    return '/images/news/scientific-research.jpg';
  }

  return '/images/news/bigdata-ai-lab.jpg';
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
    .replace(/<figure[^>]*>[\s\S]*?<\/figure>/gi, '') // drop figures and figcaptions from sapo excerpt
    .replace(/<[^>]+>/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]+\)/g, '') // remove images
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1') // unwrap links
    .replace(/[#*`_~|>-]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (clean.length <= maxLength) return clean;
  return clean.slice(0, maxLength).trim() + '...';
}

export interface CoverImageDetails {
  url: string;
  alt: string;
  caption?: string;
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

/**
 * Extracts detailed cover image metadata including alt text and optional figcaption caption.
 */
export function extractCoverImageDetails(content: string | null | undefined): CoverImageDetails | null {
  if (!content) return null;

  // 1. Check for <figure> with <img> and <figcaption>
  const figureRegex = /<figure[^>]*>[\s\S]*?<img[^>]+src=["'](https?:\/\/[^"'\s]+|\/[^"'\s]+)["'][^>]*(?:alt=["']([^"']*)["'])?[^>]*>[\s\S]*?(?:<figcaption[^>]*>([\s\S]*?)<\/figcaption>)?[\s\S]*?<\/figure>/i;
  const figMatch = content.match(figureRegex);
  if (figMatch && figMatch[1]) {
    const rawCaption = figMatch[3] ? figMatch[3].replace(/<[^>]+>/g, '').trim() : undefined;
    return {
      url: figMatch[1],
      alt: figMatch[2] || '',
      caption: rawCaption || undefined,
    };
  }

  // 2. Check for bare <img ...>
  const htmlMatch = content.match(/<img[^>]+src=["'](https?:\/\/[^"'\s]+|\/[^"'\s]+)["'][^>]*(?:alt=["']([^"']*)["'])?[^>]*>/i);
  if (htmlMatch && htmlMatch[1]) {
    return {
      url: htmlMatch[1],
      alt: htmlMatch[2] || '',
    };
  }

  // 3. Check for Markdown image ![alt](url)
  const mdMatch = content.match(/!\[([^\]]*)\]\((https?:\/\/[^)\s]+|\/[^)\s]+)\)/i);
  if (mdMatch && mdMatch[2]) {
    return {
      url: mdMatch[2],
      alt: mdMatch[1] || '',
    };
  }

  return null;
}

/**
 * Strips the leading cover image or figure from the body content
 * so it is not duplicated right below the reader's 16:9 Hero Banner.
 */
export function stripFirstCoverImage(content: string | null | undefined): string {
  if (!content) return '';
  const figureRegex = /<figure[^>]*>[\s\S]*?<img[^>]+src=["'][^"']+["'][^>]*>[\s\S]*?<\/figure>/i;
  if (figureRegex.test(content)) {
    return content.replace(figureRegex, '').trim();
  }
  const imgRegex = /<img[^>]+src=["'][^"']+["'][^>]*\/?>/i;
  if (imgRegex.test(content)) {
    return content.replace(imgRegex, '').trim();
  }
  const mdRegex = /!\[[^\]]*\]\([^)]+\)/;
  if (mdRegex.test(content)) {
    return content.replace(mdRegex, '').trim();
  }
  return content;
}
