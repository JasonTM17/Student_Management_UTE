'use client';

import {
  ArrowRight,
  BarChart3,
  BookOpen,
  CalendarRange,
  CheckCircle2,
  GraduationCap,
  ShieldCheck,
  UsersRound,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { ThemeToggle } from '@/components/ThemeToggle';
import { BrandMark } from '@/components/BrandMark';
import { LanguageToggle } from '@/components/LanguageToggle';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { SectionEyebrow } from '@/components/ui/page-header';
import { buildSiteUrl } from '@/lib/site';
import { useI18n } from '@/i18n';
import { buildCanonicalPath } from '@/i18n/paths';

export const dynamic = 'force-dynamic';

export default function HomePage() {
  const { user, isLoading } = useAuth();
  const { locale, messages } = useI18n();
  const currentYear = new Date().getFullYear();
  const operationalPillars = [
    { icon: ShieldCheck, ...messages.home.pillars[0] },
    { icon: BookOpen, ...messages.home.pillars[1] },
    { icon: BarChart3, ...messages.home.pillars[2] },
    { icon: UsersRound, ...messages.home.pillars[3] },
    { icon: CalendarRange, ...messages.home.pillars[4] },
    { icon: GraduationCap, ...messages.home.pillars[5] },
  ];
  const homepageStructuredData = {
    '@context': 'https://schema.org',
    '@type': 'SoftwareApplication',
    name: 'CampusCore',
    applicationCategory: 'BusinessApplication',
    operatingSystem: 'Web',
    url: buildSiteUrl(buildCanonicalPath('/', locale)),
    description: messages.meta.home.description,
    offers: {
      '@type': 'Offer',
      price: '0',
      priceCurrency: 'USD',
    },
  };

  return (
    <main className="portal-content-canvas min-h-screen">
      <script
        type="application/ld+json"
        suppressHydrationWarning
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(homepageStructuredData),
        }}
      />
      <nav className="portal-sidebar-header border-b border-white/10 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <BrandMark
            href="/"
            subtitle={messages.home.navSubtitle}
            compact
            className="min-w-0"
            titleClassName="text-white"
            subtitleClassName="hidden text-white/70 sm:block"
          />
          <div className="flex shrink-0 items-center gap-2">
            <LanguageToggle />
            <ThemeToggle />
            {!isLoading &&
              (user ? (
                <LocalizedLink href="/dashboard" className="hidden sm:inline-flex">
                  <Button>
                    {messages.common.actions.openDashboard}
                    <ArrowRight className="ml-2 h-4 w-4" />
                  </Button>
                </LocalizedLink>
              ) : (
                <LocalizedLink href="/login" className="hidden sm:inline-flex">
                  <Button variant="outline">{messages.common.actions.signIn}</Button>
                </LocalizedLink>
              ))}
          </div>
        </div>
      </nav>

      <section className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8 lg:py-16">
        <div className="grid gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:items-start">
          <div className="space-y-8">
            <div className="space-y-4">
              <SectionEyebrow>{messages.home.eyebrow}</SectionEyebrow>
              <h1 className="max-w-3xl text-4xl font-semibold tracking-tight text-foreground sm:text-5xl lg:text-6xl">
                {messages.home.title}
              </h1>
              <p className="max-w-2xl text-base leading-7 text-muted-foreground sm:text-lg">
                {messages.home.description}
              </p>
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <LocalizedLink href={user ? '/dashboard' : '/login'}>
                <Button size="lg">
                  {user
                    ? messages.common.actions.continueToWorkspace
                    : messages.common.actions.signInToWorkspace}
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Button>
              </LocalizedLink>
              <LocalizedLink href={user ? '/admin' : '/login'}>
                <Button size="lg" variant="outline">
                  {messages.common.actions.reviewAdmin}
                </Button>
              </LocalizedLink>
            </div>

            <div className="grid gap-4 sm:grid-cols-3">
              {messages.home.metricCards.map((item) => (
                <Card key={item.title} variant="muted">
                  <CardHeader className="pb-3">
                    <CardTitle className="text-base">{item.title}</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <p className="text-sm leading-6 text-muted-foreground">
                      {item.description}
                    </p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </div>

          <Card variant="elevated" className="portal-section-card overflow-hidden">
            <div className="orbital-divider h-px w-full" />
            <CardHeader className="space-y-3">
              <SectionEyebrow>{messages.home.snapshotEyebrow}</SectionEyebrow>
              <CardTitle className="text-2xl">
                {messages.home.snapshotTitle}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid gap-3">
                {messages.home.snapshotChecks.map((item) => (
                  <div key={item} className="flex items-start gap-3">
                    <CheckCircle2 className="mt-0.5 h-5 w-5 text-[hsl(var(--success))]" />
                    <p className="text-sm leading-6 text-muted-foreground">{item}</p>
                  </div>
                ))}
              </div>
              <div className="rounded-lg border border-border/70 bg-secondary/45 p-4">
                <div className="grid gap-4 sm:grid-cols-2">
                  <div>
                    <div className="text-sm font-semibold text-foreground">
                      {messages.home.snapshotPrimaryAccessTitle}
                    </div>
                    <div className="mt-1 text-sm text-muted-foreground">
                      {messages.home.snapshotPrimaryAccessDescription}
                    </div>
                  </div>
                  <div>
                    <div className="text-sm font-semibold text-foreground">
                      {messages.home.snapshotReleaseTitle}
                    </div>
                    <div className="mt-1 text-sm text-muted-foreground">
                      {messages.home.snapshotReleaseDescription}
                    </div>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </section>

      <section className="border-y border-border/70 bg-canvas-subtle">
        <div className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:px-8">
          <div className="mb-10 max-w-3xl space-y-3">
            <SectionEyebrow>{messages.home.capabilitiesEyebrow}</SectionEyebrow>
            <h2 className="text-3xl font-semibold tracking-tight text-foreground">
              {messages.home.capabilitiesTitle}
            </h2>
            <p className="text-base leading-7 text-muted-foreground">
              {messages.home.capabilitiesDescription}
            </p>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {operationalPillars.map((pillar) => (
              <Card key={pillar.title} variant="default" className="h-full">
                <CardContent className="flex h-full flex-col gap-4 pt-6">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-[hsl(var(--foreground))] text-[hsl(var(--background))]">
                    <pillar.icon className="h-5 w-5" />
                  </div>
                  <div className="space-y-2">
                    <h3 className="text-lg font-semibold text-foreground">
                      {pillar.title}
                    </h3>
                    <p className="text-sm leading-6 text-muted-foreground">
                      {pillar.description}
                    </p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-16 sm:px-6 lg:px-8">
        <div className="space-y-10">
          <div className="max-w-3xl space-y-3">
            <SectionEyebrow>{messages.home.whyEyebrow}</SectionEyebrow>
            <h2 className="text-3xl font-semibold tracking-tight text-foreground">
              {messages.home.whyTitle}
            </h2>
            <p className="text-base leading-7 text-muted-foreground">
              {messages.home.whyDescription}
            </p>
          </div>

          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            {messages.home.whyPoints.map((point) => (
              <Card key={point.title} variant="default" className="h-full">
                <CardContent className="space-y-3 pt-6">
                  <h3 className="text-lg font-semibold text-foreground">
                    {point.title}
                  </h3>
                  <p className="text-sm leading-6 text-muted-foreground">
                    {point.description}
                  </p>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      <footer className="portal-sidebar text-[hsl(var(--nav-foreground))]">
        <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 lg:px-8">
          <div className="grid gap-8 md:grid-cols-[1.2fr_1fr_1fr]">
            <div className="space-y-3">
              <BrandMark
                href="/"
                compact
                markClassName="bg-[hsl(var(--background))] text-[hsl(var(--foreground))] border-transparent"
                titleClassName="text-[hsl(var(--background))]"
                subtitle={messages.home.footerSubtitle}
              />
              <p className="max-w-sm text-sm leading-6 text-[hsl(var(--background))/0.72]">
                {messages.home.footerDescription}
              </p>
            </div>
            <div className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-[hsl(var(--background))/0.65]">
                {messages.home.footerWorkspace}
              </h3>
              <div className="space-y-2 text-sm text-[hsl(var(--background))/0.8]">
                {messages.home.footerLinks.workspace.map((item) => (
                  <div key={item}>{item}</div>
                ))}
              </div>
            </div>
            <div className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.18em] text-[hsl(var(--background))/0.65]">
                {messages.home.footerDelivery}
              </h3>
              <div className="space-y-2 text-sm text-[hsl(var(--background))/0.8]">
                {messages.home.footerLinks.delivery.map((item) => (
                  <div key={item}>{item}</div>
                ))}
              </div>
            </div>
          </div>
          <div className="mt-8 border-t border-white/10 pt-6 text-sm text-[hsl(var(--background))/0.7]">
            &copy; {currentYear} CampusCore. {messages.home.footerCopyright}
          </div>
        </div>
      </footer>
    </main>
  );
}
