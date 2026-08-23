import { defaultLocale, isLocale, type Locale } from '@/i18n/config';

const PREFIX_PATTERN = /^\/(en|vi)(?=\/|$)/;

export function normalizePathname(pathname: string) {
  if (!pathname) {
    return '/';
  }

  const normalized = pathname.startsWith('/') ? pathname : `/${pathname}`;
  return normalized === '' ? '/' : normalized;
}

export function stripLocaleFromPathname(pathname: string) {
  const normalized = normalizePathname(pathname);
  const match = normalized.match(PREFIX_PATTERN);

  if (!match) {
    return {
      locale: null,
      pathname: normalized,
    };
  }

  const stripped = normalized.replace(PREFIX_PATTERN, '') || '/';

  return {
    locale: match[1] as Locale,
    pathname: stripped.startsWith('/') ? stripped : `/${stripped}`,
  };
}

export function addLocalePrefix(pathname: string, locale: Locale) {
  const normalized = stripLocaleFromPathname(pathname).pathname;

  if (normalized === '/') {
    return `/${locale}`;
  }

  return `/${locale}${normalized}`;
}

export function buildCanonicalPath(pathname: string, locale: Locale) {
  return addLocalePrefix(pathname, locale);
}

export function localizePathname(
  pathname: string,
  locale: Locale,
  isPrefixed: boolean,
) {
  const normalized = stripLocaleFromPathname(pathname).pathname;

  if (!isPrefixed && locale === defaultLocale) {
    return normalized;
  }

  return addLocalePrefix(normalized, locale);
}

export function buildLocaleSwitchPath(
  pathname: string,
  locale: Locale,
  search = '',
  hash = '',
) {
  return `${addLocalePrefix(pathname, locale)}${search}${hash}`;
}

export function isBypassedPath(pathname: string) {
  return (
    pathname.startsWith('/api') ||
    pathname.startsWith('/_next') ||
    pathname.startsWith('/health') ||
    /\.[a-z0-9]+$/i.test(pathname)
  );
}

export function getRouteLocale(pathname: string) {
  return stripLocaleFromPathname(pathname).locale ?? defaultLocale;
}

export function getAlternateLanguagePaths(pathname: string) {
  const stripped = stripLocaleFromPathname(pathname).pathname;

  return {
    en: addLocalePrefix(stripped, 'en'),
    vi: addLocalePrefix(stripped, 'vi'),
    'x-default': addLocalePrefix(stripped, defaultLocale),
  } as const;
}

export function getLocaleFromCookie(value: string | undefined) {
  return isLocale(value) ? value : defaultLocale;
}
