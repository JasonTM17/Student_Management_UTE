export const locales = ['en', 'vi'] as const;

export type Locale = (typeof locales)[number];

export const defaultLocale: Locale = 'en';

export const localeLabels: Record<Locale, string> = {
  en: 'English',
  vi: 'Tiếng Việt',
};

export const localeCodes: Record<Locale, string> = {
  en: 'en-US',
  vi: 'vi-VN',
};

export const localeHtmlLang: Record<Locale, string> = {
  en: 'en',
  vi: 'vi',
};

export const localeOpenGraph: Record<Locale, string> = {
  en: 'en_US',
  vi: 'vi_VN',
};

export function isLocale(value: string | null | undefined): value is Locale {
  return locales.includes(value as Locale);
}
