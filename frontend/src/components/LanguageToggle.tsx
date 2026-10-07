'use client';

import { useMemo } from 'react';
import Link from 'next/link';
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

  const targetPath = (nextLocale: Locale) =>
    switchLocalePath(pathname, nextLocale, search);

  // The link href carries the target route so the toggle works before React
  // hydrates (plain <a> navigation) — the pre-hydration window on cold prod
  // loads was eating the first click because the button had no handler yet.
  // Hash fragments are client-only, so they are appended on click instead.
  const preserveHashOnClick = (
    event: React.MouseEvent<HTMLAnchorElement>,
    nextLocale: Locale,
  ) => {
    const hash = window.location.hash;
    if (hash) {
      event.preventDefault();
      router.push(`${targetPath(nextLocale)}${hash}`);
    }
  };

  return (
    <div
      className={cn(
        // Container and its active chip share the same 6px radius family; a pill
        // container with a rounded-rect chip left the chip corners poking out.
        'inline-flex items-center gap-0.5 rounded-md border border-border/70 bg-card/80 p-0.5 shadow-sm',
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

        const chipClass = cn(
          'inline-flex min-h-11 items-center rounded-[4px] px-3 py-1.5 text-xs font-semibold uppercase tracking-[0.08em] transition-[background-color,color,transform] duration-150 motion-safe:active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
          inverse &&
            'min-h-9 px-2.5 focus-visible:ring-portal-chrome-accent focus-visible:ring-offset-portal-sidebar',
          isActive
            ? inverse
              ? 'bg-white text-slate-950 font-bold shadow-xs'
              : // bg-primary in BOTH themes: bg-white on the near-white
                // card container left the active locale invisible.
                'bg-primary text-primary-foreground font-bold shadow-xs'
            : inverse
              ? 'text-white/80 hover:text-white hover:bg-white/10'
              : 'text-muted-foreground hover:text-foreground hover:bg-muted/50',
        );

        if (isActive) {
          return (
            <span
              key={item}
              className={chipClass}
              aria-current="true"
              aria-label={label}
              title={label}
            >
              {item}
            </span>
          );
        }

        return (
          <Link
            key={item}
            href={targetPath(item)}
            onClick={(event) => preserveHashOnClick(event, item)}
            className={chipClass}
            aria-label={label}
            title={label}
          >
            {item}
          </Link>
        );
      })}
    </div>
  );
}
