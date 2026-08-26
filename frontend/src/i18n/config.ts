export const locales = ['en', 'vi'] as const;

export type Locale = (typeof locales)[number];

// Vietnamese is the product default; English remains available through the
// explicit /en locale alias and the language switcher.
export const defaultLocale: Locale = 'vi';

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
