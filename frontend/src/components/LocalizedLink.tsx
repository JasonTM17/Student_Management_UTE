'use client';

import Link, { type LinkProps } from 'next/link';
import * as React from 'react';
import { useI18n } from '@/i18n';
import { isSafeInternalPath } from '@/lib/safe-path';

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

    if (typeof href === 'string' && !isSafeInternalPath(href)) {
      const { prefetch, replace, scroll, shallow, locale, onNavigate, ...anchorProps } = props;
      return <a ref={ref} {...anchorProps} />;
    }

    const resolvedHref =
      typeof href === 'string'
        ? preserveAlias && !isPrefixed
          ? href
          : localizeHref(href)
        : href;

    return <Link ref={ref} href={resolvedHref} {...props} />;
  },
);
