import type { Locale } from '@/i18n/config';

type LocalizedTextRecord = {
  code?: string | null;
  type?: string | null;
  name?: string | null;
  nameEn?: string | null;
  nameVi?: string | null;
  description?: string | null;
  descriptionEn?: string | null;
  descriptionVi?: string | null;
  academicYear?: { year?: number | null } | null;
};

type LocalizedCourseRecord = LocalizedTextRecord & {
  code?: string | null;
};

const LOCALIZED_NAME_FALLBACKS: Record<string, { en: string; vi: string }> = {
  FCS: {
    en: 'Faculty of Computer Science',
    vi: 'Khoa Khoa h\u1ecdc m\u00e1y t\u00ednh',
  },
  FE: {
    en: 'Faculty of Engineering',
    vi: 'Khoa K\u1ef9 thu\u1eadt',
  },
  ENG: {
    en: 'Faculty of Engineering',
    vi: 'Khoa K\u1ef9 thu\u1eadt',
  },
  FBA: {
    en: 'Faculty of Business Administration',
    vi: 'Khoa Qu\u1ea3n tr\u1ecb kinh doanh',
  },
  CS: {
    en: 'Computer Science',
    vi: 'Khoa h\u1ecdc m\u00e1y t\u00ednh',
  },
  CSE: {
    en: 'Computer Science',
    vi: 'Khoa h\u1ecdc m\u00e1y t\u00ednh',
  },
  SE: {
    en: 'Software Engineering',
    vi: 'K\u1ef9 thu\u1eadt ph\u1ea7n m\u1ec1m',
  },
  CE: {
    en: 'Computer Engineering',
    vi: 'K\u1ef9 thu\u1eadt m\u00e1y t\u00ednh',
  },
  BA: {
    en: 'Business Administration',
    vi: 'Qu\u1ea3n tr\u1ecb kinh doanh',
  },
  CS2025: {
    en: 'Computer Science 2025',
    vi: 'Ch\u01b0\u01a1ng tr\u00ecnh Khoa h\u1ecdc m\u00e1y t\u00ednh 2025',
  },
  SE2025: {
    en: 'Software Engineering 2025',
    vi: 'Ch\u01b0\u01a1ng tr\u00ecnh K\u1ef9 thu\u1eadt ph\u1ea7n m\u1ec1m 2025',
  },
  CS2026: {
    en: 'Computer Science 2026',
    vi: 'Ch\u01b0\u01a1ng tr\u00ecnh Khoa h\u1ecdc m\u00e1y t\u00ednh 2026',
  },
  CS101: {
    en: 'Introduction to Programming',
    vi: 'Nh\u1eadp m\u00f4n l\u1eadp tr\u00ecnh',
  },
  COMP101: {
    en: 'Introduction to Computer Science',
    vi: 'Nh\u1eadp m\u00f4n khoa h\u1ecdc m\u00e1y t\u00ednh',
  },
  COMP202: {
    en: 'Data Structures',
    vi: 'C\u1ea5u tr\u00fac d\u1eef li\u1ec7u',
  },
  CS201: {
    en: 'Data Structures',
    vi: 'C\u1ea5u tr\u00fac d\u1eef li\u1ec7u',
  },
  CS301: {
    en: 'Algorithms',
    vi: 'Gi\u1ea3i thu\u1eadt',
  },
  CS401: {
    en: 'Artificial Intelligence',
    vi: 'Tr\u00ed tu\u1ec7 nh\u00e2n t\u1ea1o',
  },
  SE201: {
    en: 'Software Engineering Principles',
    vi: 'Nguy\u00ean l\u00fd k\u1ef9 thu\u1eadt ph\u1ea7n m\u1ec1m',
  },
  SE301: {
    en: 'Database Systems',
    vi: 'H\u1ec7 qu\u1ea3n tr\u1ecb c\u01a1 s\u1edf d\u1eef li\u1ec7u',
  },
  SE401: {
    en: 'Web Development',
    vi: 'Ph\u00e1t tri\u1ec3n web',
  },
  CE201: {
    en: 'Computer Architecture',
    vi: 'Ki\u1ebfn tr\u00fac m\u00e1y t\u00ednh',
  },
  CE301: {
    en: 'Computer Networks',
    vi: 'M\u1ea1ng m\u00e1y t\u00ednh',
  },
  BA101: {
    en: 'Introduction to Business',
    vi: 'Nh\u1eadp m\u00f4n kinh doanh',
  },
  BA201: {
    en: 'Management Principles',
    vi: 'Nguy\u00ean l\u00fd qu\u1ea3n tr\u1ecb',
  },
};

const SEMESTER_TYPE_LABELS: Record<string, { en: string; vi: string }> = {
  FALL: {
    en: 'Fall',
    vi: 'H\u1ecdc k\u1ef3 Thu',
  },
  SPRING: {
    en: 'Spring',
    vi: 'H\u1ecdc k\u1ef3 Xu\u00e2n',
  },
  SUMMER: {
    en: 'Summer',
    vi: 'H\u1ecdc k\u1ef3 H\u00e8',
  },
};

function resolveSemesterFallback(
  locale: Locale,
  entity?: LocalizedTextRecord | null,
) {
  if (!entity) {
    return null;
  }

  const type = entity.type?.toUpperCase();
  const year = entity.academicYear?.year;
  if (type && SEMESTER_TYPE_LABELS[type]) {
    const label = SEMESTER_TYPE_LABELS[type][locale];
    return typeof year === 'number' ? `${label} ${year}` : label;
  }

  if (entity.name) {
    const match = entity.name.match(/^(Fall|Spring|Summer)\s+(\d{4})$/i);
    if (match) {
      const [, rawType, rawYear] = match;
      const resolved =
        SEMESTER_TYPE_LABELS[rawType.toUpperCase()]?.[locale] ?? rawType;
      return `${resolved} ${rawYear}`;
    }
  }

  return null;
}

const warnedNameFallbacks = new Set<string>();

function noteNameFallbackUse(key: string, locale: Locale) {
  // The dictionary rows should carry their own nameVi/nameEn; reaching this
  // map means the backend directory is missing them. Surface it in dev only.
  if (process.env.NODE_ENV !== 'production' && !warnedNameFallbacks.has(key)) {
    warnedNameFallbacks.add(key);
    console.warn(
      `[academic-content] localized name fallback used for "${key}" (${locale}) — directory row lacks nameVi/nameEn`,
    );
  }
}

function resolveLocalizedNameFallback(
  locale: Locale,
  entity?: LocalizedTextRecord | null,
) {
  if (!entity) {
    return null;
  }

  const byCode = entity.code ? LOCALIZED_NAME_FALLBACKS[entity.code] : null;
  if (byCode) {
    noteNameFallbackUse(entity.code as string, locale);
    return byCode[locale];
  }

  const byName = entity.name ? LOCALIZED_NAME_FALLBACKS[entity.name] : null;
  if (byName) {
    noteNameFallbackUse(entity.name as string, locale);
    return byName[locale];
  }

  const semesterFallback = resolveSemesterFallback(locale, entity);
  if (semesterFallback) {
    return semesterFallback;
  }

  return null;
}

export function getLocalizedName(
  locale: Locale,
  entity?: LocalizedTextRecord | null,
  fallback = '',
) {
  if (!entity) {
    return fallback;
  }

  if (locale === 'vi') {
    return (
      entity.nameVi ??
      resolveLocalizedNameFallback(locale, entity) ??
      entity.name ??
      entity.nameEn ??
      fallback
    );
  }

  return (
    entity.nameEn ??
    resolveLocalizedNameFallback(locale, entity) ??
    entity.name ??
    entity.nameVi ??
    fallback
  );
}

export function getLocalizedDescription(
  locale: Locale,
  entity?: LocalizedTextRecord | null,
  fallback = '',
) {
  if (!entity) {
    return fallback;
  }

  if (locale === 'vi') {
    return (
      entity.descriptionVi ??
      entity.description ??
      entity.descriptionEn ??
      fallback
    );
  }

  return (
    entity.descriptionEn ??
    entity.description ??
    entity.descriptionVi ??
    fallback
  );
}

export function getLocalizedFlatLabel(
  locale: Locale,
  value?: string | null,
  valueEn?: string | null,
  valueVi?: string | null,
  fallback = '',
) {
  return getLocalizedName(
    locale,
    {
      name: value,
      nameEn: valueEn,
      nameVi: valueVi,
    },
    fallback || value || valueEn || valueVi || '',
  );
}

export function getLocalizedCourseLabel(
  locale: Locale,
  course?: LocalizedCourseRecord | null,
  fallback = '',
) {
  if (!course) {
    return fallback;
  }

  const name = getLocalizedName(locale, course, fallback);
  if (!course.code) {
    return name;
  }

  return `${course.code} - ${name}`.trim();
}
