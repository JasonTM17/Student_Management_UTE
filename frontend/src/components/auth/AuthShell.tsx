'use client';

import * as React from 'react';
import { BrandMark } from '@/components/BrandMark';
import { LanguageToggle } from '@/components/LanguageToggle';
import { ThemeToggle } from '@/components/ThemeToggle';
import { SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

interface AuthShellFeature {
  label: string;
  description: string;
}

interface AuthShellProps {
  eyebrow: string;
  title: string;
  description: string;
  features: AuthShellFeature[];
  children: React.ReactNode;
  footer?: React.ReactNode;
  className?: string;
}

export function AuthShell({
  eyebrow,
  title,
  description,
  features,
  children,
  footer,
  className,
}: AuthShellProps) {
  const { messages } = useI18n();

  return (
    <div className="min-h-screen bg-background">
      <div className="grid min-h-screen lg:grid-cols-[0.9fr_1.1fr]">
        <section className="portal-sidebar relative hidden overflow-hidden border-r border-white/10 lg:flex">
          <div className="flex w-full items-center px-10 py-12 xl:px-16">
            <div className="mx-auto w-full max-w-xl space-y-10">
              <BrandMark
                href="/"
                subtitle={messages.authShell.desktopSubtitle}
                markClassName="border-0 bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)]"
                titleClassName="text-[var(--portal-sidebar-text)]"
                subtitleClassName="text-[var(--portal-sidebar-muted)]"
              />
              <div className="space-y-4 border-l-4 border-[var(--portal-yellow)] pl-6">
                <SectionEyebrow className="text-[var(--portal-yellow)]">{eyebrow}</SectionEyebrow>
                <h1 className="max-w-lg text-4xl font-semibold leading-[1.18] text-[var(--portal-sidebar-text)] xl:text-[2.75rem]">
                  {title}
                </h1>
                <p className="max-w-lg text-base leading-7 text-[var(--portal-sidebar-muted)]">
                  {description}
                </p>
              </div>
              <div className="divide-y divide-white/10 border-y border-white/10">
                {features.map((feature, index) => (
                  <div
                    key={feature.label}
                    className="grid grid-cols-[2rem_1fr] gap-4 py-4"
                  >
                    <div className="flex h-8 w-8 items-center justify-center border border-white/15 text-[var(--portal-yellow)]">
                      <span className="text-xs font-semibold">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                    </div>
                    <div className="space-y-1">
                      <h2 className="text-sm font-semibold text-[var(--portal-sidebar-text)]">
                        {feature.label}
                      </h2>
                      <p className="text-sm leading-6 text-[var(--portal-sidebar-muted)]">
                        {feature.description}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section className="relative flex items-center justify-center px-5 py-20 sm:px-8 lg:px-12">
          <div className="absolute right-4 top-4 flex items-center gap-2 sm:right-6 sm:top-6">
            <LanguageToggle />
            <ThemeToggle />
          </div>
          <div className={cn('w-full max-w-md space-y-8 border-0 bg-transparent p-0 sm:rounded-md sm:border sm:border-border/80 sm:bg-card sm:p-8 sm:panel-shadow', className)}>
            <div className="border-b border-border/70 pb-6 lg:hidden">
              <BrandMark href="/" subtitle={messages.authShell.mobileSubtitle} />
            </div>
            {children}
            {footer ? <div>{footer}</div> : null}
          </div>
        </section>
      </div>
    </div>
  );
}
