'use client';

import { useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useI18n } from '@/i18n';
import { locales, type Locale } from '@/i18n/config';
import { stripLocaleFromPathname } from '@/i18n/paths';
import { cn } from '@/lib/utils';

export function LanguageToggle({
  className,
}: {
  className?: string;
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const { locale, messages, switchLocalePath } = useI18n();

  const search = useMemo(() => {
    const query = searchParams.toString();
    return query ? `?${query}` : '';
  }, [searchParams]);
  const routeLocale = stripLocaleFromPathname(pathname).locale;

  const handleSwitch = (nextLocale: Locale) => {
    if (nextLocale === locale && pathname.startsWith(`/${locale}`)) {
      return;
    }

    const hash =
      typeof window !== 'undefined' && window.location.hash
        ? window.location.hash
        : '';

    router.push(switchLocalePath(pathname, nextLocale, search, hash));
  };

  return (
    <div
      className={cn(
        'inline-flex min-h-10 items-center rounded-md border border-border/70 bg-card p-0.5',
        className,
      )}
      role="group"
      aria-label={messages.common.locale.label}
    >
      {locales.map((item) => {
        const isActive =
          item === locale &&
          (pathname.startsWith(`/${item}`) ||
            (!routeLocale && item === locale));
        const label =
          item === 'en'
            ? messages.common.locale.english
            : messages.common.locale.vietnamese;

        return (
          <button
            key={item}
            type="button"
            onClick={() => handleSwitch(item)}
            className={cn(
              'min-h-9 rounded-sm px-2.5 py-1 text-xs font-semibold uppercase transition-colors',
              isActive
                ? 'bg-primary text-primary-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
            aria-pressed={isActive}
            aria-label={label}
            title={label}
          >
            {item}
          </button>
        );
      })}
    </div>
  );
}
