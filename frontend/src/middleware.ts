import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';
import { defaultLocale, isLocale } from '@/i18n/config';
import {
  isBypassedPath,
  stripLocaleFromPathname,
} from '@/i18n/paths';

const LOCALE_COOKIE = 'cc_locale';
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

// RT-P2-4 graduation: the report-only CSP is now enforced with a per-request
// nonce. Setting the policy on the *request* headers is what lets Next.js
// thread the nonce into its own scripts (flight data, webpack bootstrap);
// the response header is what the browser enforces. 'strict-dynamic' extends
// trust to scripts created by already-nonced code (TinyMCE's ScriptLoader);
// 'self' remains as the fallback for browsers that predate strict-dynamic.
function generateNonce(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  let binary = '';
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary);
}

function buildContentSecurityPolicy(nonce: string): string {
  return [
    "default-src 'self'",
    `script-src 'self' 'nonce-${nonce}' 'strict-dynamic'`,
    // Next injects inline styles; TinyMCE composes with inline content_style.
    "style-src 'self' 'unsafe-inline'",
    // Announcements may embed https images and the editor inlines data:/blob:.
    "img-src 'self' data: blob: https:",
    "font-src 'self' data:",
    "media-src 'self' data: blob: https:",
    "frame-src 'self' blob:",
    "worker-src 'self' blob:",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'self'",
  ].join('; ');
}

const DOCUMENT_FETCH_DESTINATIONS = new Set([
  'document',
  'frame',
  'iframe',
  'nested-document',
]);

function isNonDocumentFetch(secFetchDest: string | null) {
  return (
    secFetchDest !== null && !DOCUMENT_FETCH_DESTINATIONS.has(secFetchDest)
  );
}

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

  const nonce = generateNonce();
  const csp = buildContentSecurityPolicy(nonce);
  // x-nonce is consumed by layout.tsx for hand-written inline scripts; the CSP
  // request header is what Next.js parses to nonce its own framework scripts.
  requestHeaders.set('x-nonce', nonce);
  requestHeaders.set('Content-Security-Policy', csp);

  if (localeMatch.locale) {
    const response = NextResponse.next({
      request: {
        headers: requestHeaders,
      },
    });
    response.headers.set('Content-Security-Policy', csp);

    const isPrefetch =
      request.headers.has('rsc') ||
      request.headers.has('next-router-prefetch') ||
      request.headers.get('purpose')?.toLowerCase() === 'prefetch' ||
      /(?:^|[\s,;])prefetch(?:$|[\s,;])/.test(
        request.headers.get('sec-purpose')?.toLowerCase() ?? '',
      ) ||
      isNonDocumentFetch(request.headers.get('sec-fetch-dest'));

    if (!isPrefetch) {
      response.cookies.set(LOCALE_COOKIE, locale, {
        path: '/',
        sameSite: 'lax',
        maxAge: COOKIE_MAX_AGE,
      });
    }

    return response;
  }

  const response = NextResponse.next({
    request: {
      headers: requestHeaders,
    },
  });
  response.headers.set('Content-Security-Policy', csp);
  return response;
}

export const config = {
  matcher: ['/((?!api|_next|favicon.ico).*)'],
};
