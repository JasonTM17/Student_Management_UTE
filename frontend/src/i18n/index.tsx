'use client';

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { usePathname } from 'next/navigation';
import {
  defaultLocale,
  localeHtmlLang,
  localeCodes,
  type Locale,
} from '@/i18n/config';
import { type I18nMessages, getMessages } from '@/i18n/messages';
import {
  buildLocaleSwitchPath,
  localizePathname,
  stripLocaleFromPathname,
} from '@/i18n/paths';
import { readLocaleCookie, resolveClientLocale } from '@/i18n/resolve-locale';

interface I18nContextValue {
  locale: Locale;
  isPrefixed: boolean;
  messages: I18nMessages;
  href: (pathname: string) => string;
  switchLocalePath: (
    pathname: string,
    locale: Locale,
    search?: string,
    hash?: string,
  ) => string;
  formatDate: (
    value: string | number | Date,
    options?: Intl.DateTimeFormatOptions,
  ) => string;
  formatDateTime: (
    value: string | number | Date,
    options?: Intl.DateTimeFormatOptions,
  ) => string;
  formatNumber: (
    value: number,
    options?: Intl.NumberFormatOptions,
  ) => string;
  formatCurrency: (
    value: number,
    currency?: string,
    options?: Intl.NumberFormatOptions,
  ) => string;
}

const I18nContext = createContext<I18nContextValue | undefined>(undefined);

function toIntlLocale(locale: Locale) {
  return localeCodes[locale] ?? localeCodes[defaultLocale];
}

export function I18nProvider({
  locale,
  isPrefixed,
  children,
}: {
  locale: Locale;
  isPrefixed: boolean;
  children: ReactNode;
}) {
  const pathname = usePathname();
  // The initial state already runs the same resolution the middleware used:
  // URL prefix wins, then the cc_locale cookie, then the locale the server
  // rendered with. For an unprefixed URL with cc_locale=en the server and the
  // client therefore both pick 'en' — previously the client forced the
  // defaultLocale here and the page flipped to Vietnamese after hydration.
  const [resolvedLocale, setResolvedLocale] = useState<Locale>(() =>
    resolveClientLocale({
      routeLocale: stripLocaleFromPathname(pathname).locale,
      cookieLocale: readLocaleCookie(),
      serverLocale: locale,
    }),
  );
  const [resolvedIsPrefixed, setResolvedIsPrefixed] = useState(isPrefixed);

  useEffect(() => {
    const routeLocale = stripLocaleFromPathname(pathname).locale;
    const nextLocale = resolveClientLocale({
      routeLocale,
      cookieLocale: readLocaleCookie(),
      serverLocale: locale,
    });
    const nextIsPrefixed = Boolean(routeLocale);

    setResolvedLocale(nextLocale);
    setResolvedIsPrefixed(nextIsPrefixed);
    document.documentElement.lang = localeHtmlLang[nextLocale];

    if (routeLocale) {
      document.cookie = `cc_locale=${nextLocale}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
    }
  }, [pathname, locale]);

  const messages = useMemo(
    () => getMessages(resolvedLocale),
    [resolvedLocale],
  );

  const value = useMemo<I18nContextValue>(() => {
    const intlLocale = toIntlLocale(resolvedLocale);

    return {
      locale: resolvedLocale,
      isPrefixed: resolvedIsPrefixed,
      messages,
      href: (pathname: string) =>
        localizePathname(pathname, resolvedLocale, resolvedIsPrefixed),
      switchLocalePath: (pathname, nextLocale, search = '', hash = '') =>
        buildLocaleSwitchPath(pathname, nextLocale, search, hash),
      formatDate: (value, options) => {
        const date = new Date(value);
        // An unparseable API value used to throw RangeError out of Intl and
        // blank the whole render — print the same em-dash the UI uses for a
        // missing date instead.
        if (Number.isNaN(date.getTime())) return '—';
        return new Intl.DateTimeFormat(intlLocale, {
          year: 'numeric',
          month: resolvedLocale === 'vi' ? '2-digit' : 'short',
          day: resolvedLocale === 'vi' ? '2-digit' : 'numeric',
          ...options,
        }).format(date);
      },
      formatDateTime: (value, options) => {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) return '—';
        return new Intl.DateTimeFormat(intlLocale, {
          year: 'numeric',
          month: resolvedLocale === 'vi' ? '2-digit' : 'short',
          day: resolvedLocale === 'vi' ? '2-digit' : 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          ...options,
        }).format(date);
      },
      formatNumber: (value, options) =>
        new Intl.NumberFormat(intlLocale, options).format(value),
      formatCurrency: (value, currency = 'USD', options) =>
        new Intl.NumberFormat(intlLocale, {
          style: 'currency',
          currency,
          ...options,
        }).format(value),
    };
  }, [messages, resolvedIsPrefixed, resolvedLocale]);

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  const context = useContext(I18nContext);

  if (!context) {
    throw new Error('useI18n must be used within an I18nProvider');
  }

  return context;
}
