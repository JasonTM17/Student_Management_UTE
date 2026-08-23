import { defaultLocale, isLocale, type Locale } from '@/i18n/config';

const PREFIX_PATTERN = /^\/(en|vi)(?=\/|$)/;

export interface RouteTargetParts {
  pathname: string;
  search: string;
  hash: string;
}

export function splitRouteTarget(target: string): RouteTargetParts {
  const normalized = target.startsWith('/') ? target : `/${target}`;
  const hashIndex = normalized.indexOf('#');
  const withoutHash = hashIndex >= 0 ? normalized.slice(0, hashIndex) : normalized;
  const hash = hashIndex >= 0 ? normalized.slice(hashIndex) : '';
  const searchIndex = withoutHash.indexOf('?');

  return {
    pathname: searchIndex >= 0 ? withoutHash.slice(0, searchIndex) : withoutHash,
    search: searchIndex >= 0 ? withoutHash.slice(searchIndex) : '',
    hash,
  };
}

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

export function canonicalizeRouteTarget(target: string) {
  const parts = splitRouteTarget(target);
  const pathname = stripLocaleFromPathname(parts.pathname).pathname;
  return `${pathname}${parts.search}${parts.hash}`;
}

export function localizePathname(
  pathname: string,
  locale: Locale,
  isPrefixed: boolean,
) {
  const parts = splitRouteTarget(pathname);
  const normalized = stripLocaleFromPathname(parts.pathname).pathname;
  const suffix = `${parts.search}${parts.hash}`;

  if (!isPrefixed && locale === defaultLocale) {
    return `${normalized}${suffix}`;
  }

  return `${addLocalePrefix(normalized, locale)}${suffix}`;
}

export function buildLocaleSwitchPath(
  pathname: string,
  locale: Locale,
  search = '',
  hash = '',
) {
  const parts = splitRouteTarget(pathname);
  return `${addLocalePrefix(parts.pathname, locale)}${search || parts.search}${hash || parts.hash}`;
}

export function isBypassedPath(pathname: string) {
  const routePath = splitRouteTarget(pathname).pathname;
  return (
    routePath.startsWith('/api') ||
    routePath.startsWith('/_next') ||
    routePath.startsWith('/health') ||
    /\.[a-z0-9]+$/i.test(routePath)
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
