import type { Metadata } from 'next';
import { unstable_noStore as noStore } from 'next/cache';
import { cookies, headers } from 'next/headers';
import {
  defaultLocale,
  isLocale,
  localeHtmlLang,
  localeOpenGraph,
  type Locale,
} from '@/i18n/config';
import { getMessages } from '@/i18n/messages';
import {
  buildCanonicalPath,
  getAlternateLanguagePaths,
  stripLocaleFromPathname,
} from '@/i18n/paths';
import { buildSiteUrl, getSiteUrl } from '@/lib/site';

async function getHeader(name: string) {
  noStore();
  const headerStore = await headers();
  return headerStore.get(name);
}

export async function getRequestLocale() {
  noStore();
  const raw = await getHeader('x-cc-locale');
  if (isLocale(raw)) {
    return raw;
  }

  const cookieStore = await cookies();
  const cookieLocale = cookieStore.get('cc_locale')?.value;
  return isLocale(cookieLocale) ? cookieLocale : defaultLocale;
}

export async function getRequestVisiblePathname() {
  return (await getHeader('x-cc-visible-pathname')) || '/';
}

export async function getRequestStrippedPathname() {
  return (
    (await getHeader('x-cc-stripped-pathname')) ||
    stripLocaleFromPathname('/').pathname
  );
}

export async function isPrefixedRequest() {
  return (await getHeader('x-cc-locale-prefixed')) === '1';
}

function getRouteMetadata(pathname: string, locale: Locale) {
  const messages = getMessages(locale);

  if (pathname === '/login') {
    return {
      title: messages.meta.login.title,
      description: messages.meta.login.description,
      robots: { index: false, follow: false },
    };
  }

  if (pathname === '/forgot-password') {
    return {
      title: messages.meta.forgotPassword.title,
      description: messages.meta.forgotPassword.description,
      robots: { index: false, follow: false },
    };
  }

  if (pathname === '/reset-password') {
    return {
      title: messages.meta.resetPassword.title,
      description: messages.meta.resetPassword.description,
      robots: { index: false, follow: false },
    };
  }

  if (pathname.startsWith('/dashboard')) {
    return {
      title: messages.meta.dashboard.title,
      description: messages.meta.dashboard.description,
      robots: { index: false, follow: false, nocache: true },
    };
  }

  if (pathname.startsWith('/admin')) {
    return {
      title: messages.meta.admin.title,
      description: messages.meta.admin.description,
      robots: { index: false, follow: false, nocache: true },
    };
  }

  return {
    title: messages.meta.home.title,
    description: messages.meta.home.description,
    robots: { index: true, follow: true },
  };
}

export async function getLocalizedMetadata(): Promise<Metadata> {
  noStore();
  const locale = await getRequestLocale();
  const pathname = await getRequestStrippedPathname();
  const canonicalPath = buildCanonicalPath(pathname, locale);
  const alternates = getAlternateLanguagePaths(pathname);
  const messages = getMessages(locale);
  const routeMetadata = getRouteMetadata(pathname, locale);
  const socialImagePath = `/social-image/${locale}`;

  return {
    metadataBase: new URL(getSiteUrl()),
    title: {
      default: messages.meta.defaults.siteName,
      template: `%s | ${messages.meta.defaults.siteName}`,
    },
    applicationName: messages.meta.defaults.siteName,
    description: routeMetadata.description,
    alternates: {
      canonical: buildSiteUrl(canonicalPath),
      languages: {
        'en-US': buildSiteUrl(alternates.en),
        'vi-VN': buildSiteUrl(alternates.vi),
        'x-default': buildSiteUrl(alternates['x-default']),
      },
    },
    openGraph: {
      title: routeMetadata.title,
      description: routeMetadata.description,
      url: buildSiteUrl(canonicalPath),
      siteName: messages.meta.defaults.siteName,
      type: 'website',
      locale: localeOpenGraph[locale],
      alternateLocale:
        locale === 'en'
          ? [localeOpenGraph.vi]
          : [localeOpenGraph.en],
      images: [
        {
          url: buildSiteUrl(socialImagePath),
          width: 1200,
          height: 630,
          alt: messages.meta.defaults.ogAlt,
        },
      ],
    },
    twitter: {
      card: 'summary_large_image',
      title: routeMetadata.title,
      description: routeMetadata.description,
      images: [buildSiteUrl(socialImagePath)],
    },
    robots: routeMetadata.robots,
    manifest: '/manifest.webmanifest',
  };
}

export async function getHtmlLang() {
  return localeHtmlLang[await getRequestLocale()];
}
