import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';
import { defaultLocale, isLocale } from '@/i18n/config';
import {
  isBypassedPath,
  stripLocaleFromPathname,
} from '@/i18n/paths';

const LOCALE_COOKIE = 'cc_locale';
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

function withRequestLocaleCookie(request: NextRequest, locale: string) {
  const cookieHeader = request.headers.get('cookie') ?? '';
  const cookieParts = cookieHeader
    .split(';')
    .map((part) => part.trim())
    .filter(Boolean)
    .filter((part) => !part.startsWith(`${LOCALE_COOKIE}=`));

  return [...cookieParts, `${LOCALE_COOKIE}=${locale}`].join('; ');
}

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  if (isBypassedPath(pathname)) {
    return NextResponse.next();
  }

  const localeMatch = stripLocaleFromPathname(pathname);
  // Unprefixed URLs honor the saved preference: previously they forced the
  // default locale AND overwrote the request cookie with it, so a reload
  // silently reverted the visitor to Vietnamese.
  const cookieLocale = request.cookies.get(LOCALE_COOKIE)?.value;
  const locale =
    localeMatch.locale ?? (isLocale(cookieLocale) ? cookieLocale : defaultLocale);
  const strippedPath = localeMatch.pathname;
  const requestHeaders = new Headers(request.headers);

  requestHeaders.set('x-cc-locale', locale);
  requestHeaders.set('x-cc-visible-pathname', pathname);
  requestHeaders.set('x-cc-stripped-pathname', strippedPath);
  requestHeaders.set('x-cc-locale-prefixed', localeMatch.locale ? '1' : '0');
  requestHeaders.set(
    'cookie',
    withRequestLocaleCookie(request, locale),
  );

  if (localeMatch.locale) {
    const response = NextResponse.next({
      request: {
        headers: requestHeaders,
      },
    });

    response.cookies.set(LOCALE_COOKIE, locale, {
      path: '/',
      sameSite: 'lax',
      maxAge: COOKIE_MAX_AGE,
    });

    return response;
  }

  return NextResponse.next({
    request: {
      headers: requestHeaders,
    },
  });
}

export const config = {
  matcher: ['/((?!api|_next|favicon.ico).*)'],
};
