'use client';

import { useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { useI18n } from '@/i18n';
import { locales, type Locale } from '@/i18n/config';
import { stripLocaleFromPathname } from '@/i18n/paths';
import { cn } from '@/lib/utils';

export function LanguageToggle({
  className,
  inverse = false,
}: {
  className?: string;
  inverse?: boolean;
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
        'inline-flex items-center rounded-full border border-border/70 bg-card/80 p-1 shadow-sm',
        inverse && 'border-white/20 bg-white/10 shadow-none',
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
              'min-h-11 rounded-full px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.08em] transition-[background-color,color,transform] duration-150 motion-safe:active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
              inverse &&
                'min-h-9 px-2.5 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-[var(--portal-sidebar)]',
              isActive
                ? inverse
                  ? 'bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)]'
                  : 'bg-primary text-primary-foreground'
                : inverse
                  ? 'text-[var(--portal-sidebar-text)] hover:text-white'
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
