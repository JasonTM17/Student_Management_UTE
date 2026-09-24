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
  const [resolvedLocale, setResolvedLocale] = useState<Locale>(locale);
  const [resolvedIsPrefixed, setResolvedIsPrefixed] = useState(isPrefixed);

  useEffect(() => {
    const routeLocale = stripLocaleFromPathname(pathname).locale;
    const nextLocale = routeLocale ?? defaultLocale;
    const nextIsPrefixed = Boolean(routeLocale);

    setResolvedLocale(nextLocale);
    setResolvedIsPrefixed(nextIsPrefixed);
    document.documentElement.lang = localeHtmlLang[nextLocale];

    if (routeLocale) {
      document.cookie = `cc_locale=${nextLocale}; Path=/; Max-Age=${60 * 60 * 24 * 365}; SameSite=Lax`;
    }
  }, [pathname]);

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
      formatDate: (value, options) =>
        new Intl.DateTimeFormat(intlLocale, {
          year: 'numeric',
          month: resolvedLocale === 'vi' ? '2-digit' : 'short',
          day: resolvedLocale === 'vi' ? '2-digit' : 'numeric',
          ...options,
        }).format(new Date(value)),
      formatDateTime: (value, options) =>
        new Intl.DateTimeFormat(intlLocale, {
          year: 'numeric',
          month: resolvedLocale === 'vi' ? '2-digit' : 'short',
          day: resolvedLocale === 'vi' ? '2-digit' : 'numeric',
          hour: '2-digit',
          minute: '2-digit',
          ...options,
        }).format(new Date(value)),
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
