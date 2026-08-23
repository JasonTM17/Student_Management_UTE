'use client';

import Link, { type LinkProps } from 'next/link';
import * as React from 'react';
import { useI18n } from '@/i18n';
import { canonicalizeRouteTarget, stripLocaleFromPathname } from '@/i18n/paths';

type LocalizedLinkProps = LinkProps &
  Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, keyof LinkProps> & {
    preserveAlias?: boolean;
  };

export const LocalizedLink = React.forwardRef<HTMLAnchorElement, LocalizedLinkProps>(
  function LocalizedLink(
    { href, preserveAlias = true, ...props },
    ref,
  ) {
    const { href: localizeHref, isPrefixed } = useI18n();

    const hasExplicitLocale =
      typeof href === 'string' && Boolean(stripLocaleFromPathname(href).locale);
    const resolvedHref =
      typeof href === 'string'
        ? preserveAlias && !isPrefixed && !hasExplicitLocale
          ? canonicalizeRouteTarget(href)
          : localizeHref(href)
        : href;

    return <Link ref={ref} href={resolvedHref} {...props} />;
  },
);
