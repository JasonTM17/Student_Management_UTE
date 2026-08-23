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
    <div className="portal-content-canvas min-h-screen">
      <div className="grid min-h-screen lg:grid-cols-[0.9fr_1.1fr]">
        <section className="portal-sidebar relative hidden lg:flex">
          <div className="portal-utility-bar absolute inset-x-0 top-0 flex items-center justify-between px-10 text-[11px] font-semibold uppercase tracking-[0.14em] xl:px-16">
            <span>{messages.authShell.desktopSubtitle}</span>
            <span className="opacity-75">CampusCore</span>
          </div>
          <div className="flex w-full items-center px-10 py-16 xl:px-16">
            <div className="mx-auto max-w-xl space-y-10">
              <BrandMark
                href="/"
                subtitle={messages.authShell.desktopSubtitle}
                titleClassName="text-white"
                subtitleClassName="text-white/70"
                markClassName="border-white/20 bg-[hsl(var(--nav-active))] text-[hsl(var(--nav-surface))]"
              />
              <div className="space-y-4">
                <SectionEyebrow className="text-white/70">{eyebrow}</SectionEyebrow>
                <h1 className="max-w-lg text-5xl font-semibold tracking-tight text-white">
                  {title}
                </h1>
                <p className="max-w-lg text-base leading-7 text-white/75">
                  {description}
                </p>
              </div>
              <div className="grid gap-4">
                {features.map((feature, index) => (
                  <div
                    key={feature.label}
                    className="grid grid-cols-[40px_1fr] gap-4 rounded-md border border-white/15 bg-white/[0.08] px-5 py-4"
                  >
                    <div className="flex h-10 w-10 items-center justify-center rounded-md bg-[hsl(var(--nav-active))] text-[hsl(var(--nav-surface))]">
                      <span className="text-sm font-semibold">
                        {String(index + 1).padStart(2, '0')}
                      </span>
                    </div>
                    <div className="space-y-1">
                      <h2 className="text-sm font-semibold text-white">
                        {feature.label}
                      </h2>
                      <p className="text-sm leading-6 text-white/70">
                        {feature.description}
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </section>

        <section className="relative flex items-center justify-center border-l border-border/70 bg-background px-5 py-10 sm:px-8 lg:px-10">
          <div className="absolute right-4 top-4 flex items-center gap-2 sm:right-6 sm:top-6">
            <LanguageToggle />
            <ThemeToggle />
          </div>
          <div className={cn('w-full max-w-md space-y-8', className)}>
            <div className="lg:hidden">
              <BrandMark
                href="/"
                subtitle={messages.authShell.mobileSubtitle}
                subtitleClassName="text-muted-foreground"
              />
            </div>
            {children}
            {footer ? <div>{footer}</div> : null}
          </div>
        </section>
      </div>
    </div>
  );
}
