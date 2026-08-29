'use client';

import { ArrowRight } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { ThemeToggle } from '@/components/ThemeToggle';
import { BrandMark } from '@/components/BrandMark';
import { LanguageToggle } from '@/components/LanguageToggle';
import { LocalizedLink } from '@/components/LocalizedLink';
import { LinkButton } from '@/components/ui/link-button';
import { SectionEyebrow } from '@/components/ui/page-header';
import { HomeIdentityBoard } from '@/components/home/HomeIdentityBoard';
import { buildSiteUrl } from '@/lib/site';
import { useI18n } from '@/i18n';
import { buildCanonicalPath } from '@/i18n/paths';
import { useSiteAppearance } from '@/components/providers/SiteAppearanceProvider';

export default function HomePage() {
  const {
    user,
    isAdmin,
    isLecturer,
    isSuperAdmin,
  } = useAuth();
  const { locale, messages } = useI18n();
  const { appearance } = useSiteAppearance();
  const hero = appearance.hero[locale];
  const currentYear = new Date().getFullYear();
  const workspaceHref = isAdmin || isSuperAdmin
    ? '/admin'
    : isLecturer
      ? '/dashboard/lecturer'
      : '/dashboard';
  const primaryHref = user ? workspaceHref : '/login?portal=student';
  const primaryLabel = user
    ? messages.common.actions.openDashboard
    : messages.common.actions.signIn;
  const proofRows = messages.home.publicProof;
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
    <div className="min-h-dvh bg-background">
      <script
        type="application/ld+json"
        suppressHydrationWarning
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(homepageStructuredData),
        }}
      />
      <a href="#main-content" className="portal-skip-link">
        {messages.home.skipToContent}
      </a>

      <header className="bg-[var(--portal-sidebar)] text-[var(--portal-sidebar-text)]">
        <nav className="mx-auto flex h-16 max-w-[1280px] items-center justify-between gap-2 px-4 sm:px-6 lg:px-12">
          <BrandMark
            href="/"
            subtitle={messages.home.navSubtitle}
            compact
            className="min-w-0"
            markClassName="border-0 bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)]"
            titleClassName="max-sm:sr-only text-[var(--portal-sidebar-text)]"
            subtitleClassName="hidden sm:block text-[var(--portal-sidebar-muted)]"
          />
          <div className="flex shrink-0 items-center gap-1 sm:gap-2">
            <LanguageToggle inverse />
            <ThemeToggle className="text-[var(--portal-sidebar-text)] hover:bg-white/10 hover:text-[var(--portal-sidebar-text)]" />
            {user ? (
              <LinkButton href={workspaceHref} variant="warm" className="inline-flex shrink-0 px-3 sm:px-4">
                {messages.common.actions.openDashboard}
              </LinkButton>
            ) : (
              <LinkButton
                href="/login?portal=student"
                variant="warm"
                className="inline-flex shrink-0 px-3 sm:px-4"
              >
                {messages.common.actions.signIn}
              </LinkButton>
            )}
          </div>
        </nav>
      </header>

      <main id="main-content" tabIndex={-1}>
        <section className="mx-auto grid max-w-[1280px] items-stretch gap-10 px-4 py-10 sm:px-6 lg:grid-cols-[1.15fr_0.85fr] lg:px-12 lg:py-14">
          <div className="flex flex-col justify-center space-y-6 border-l-4 border-[var(--portal-yellow)] pl-6">
            <SectionEyebrow>{hero.eyebrow || messages.home.eyebrow}</SectionEyebrow>
            <h1 className="max-w-xl text-4xl font-bold leading-[2.75rem] text-foreground lg:text-[2.75rem] lg:leading-[3.1rem]">
              {hero.title || messages.home.title}
            </h1>
            <p className="max-w-xl text-base leading-7 text-foreground/80 lg:text-lg">
              {hero.description || messages.home.description}
            </p>
            <div>
              <LinkButton
                href={primaryHref}
                size="lg"
                className="group min-h-12 bg-[var(--portal-sidebar)] px-8 text-base text-[var(--portal-sidebar-text)] hover:bg-[var(--portal-sidebar-hover)]"
              >
                {primaryLabel}
                <ArrowRight className="ml-2 h-4 w-4 transition-transform duration-150 motion-safe:group-hover:translate-x-0.5" />
              </LinkButton>
            </div>
          </div>

          <HomeIdentityBoard />
        </section>

        <section className="mx-auto max-w-[1280px] px-4 py-10 sm:px-6 lg:px-12 lg:py-14">
          <div className="grid gap-12 lg:grid-cols-[2fr_1fr_1fr] lg:gap-12">
            <article className="min-w-0">
              <h2 className="text-3xl font-semibold leading-9 text-foreground">
                {messages.home.roleLanes.student.title}
              </h2>
              <ul className="mt-6 divide-y divide-border border-y border-border">
                {messages.home.roleLanes.student.rows.map((row) => (
                  <li key={row} className="py-3 text-base leading-6 text-muted-foreground">
                    {row}
                  </li>
                ))}
              </ul>
              <LocalizedLink
                href={messages.home.roleLanes.student.href}
                className="mt-4 inline-flex min-h-11 items-center text-sm font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {messages.home.roleLanes.student.action}
              </LocalizedLink>
            </article>

            <article className="min-w-0 border-t border-border pt-8 lg:border-t-0 lg:pt-0">
              <h2 className="text-xl font-semibold leading-7 text-foreground">
                {messages.home.roleLanes.lecturer.title}
              </h2>
              <ul className="mt-6 space-y-3">
                {messages.home.roleLanes.lecturer.rows.map((row) => (
                  <li key={row} className="text-sm leading-6 text-muted-foreground">
                    {row}
                  </li>
                ))}
              </ul>
              <LocalizedLink
                href={messages.home.roleLanes.lecturer.href}
                className="mt-4 inline-flex min-h-11 items-center text-sm font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {messages.home.roleLanes.lecturer.action}
              </LocalizedLink>
            </article>

            <article className="min-w-0 border-t border-border pt-8 lg:border-t-0 lg:pt-0">
              <h2 className="text-xl font-semibold leading-7 text-foreground">
                {messages.home.roleLanes.admin.title}
              </h2>
              <ul className="mt-6 space-y-3">
                {messages.home.roleLanes.admin.rows.map((row) => (
                  <li key={row} className="text-sm leading-6 text-muted-foreground">
                    {row}
                  </li>
                ))}
              </ul>
              <LocalizedLink
                href={messages.home.roleLanes.admin.href}
                className="mt-4 inline-flex min-h-11 items-center text-sm font-semibold text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {messages.home.roleLanes.admin.action}
              </LocalizedLink>
            </article>
          </div>
        </section>

        <section className="bg-[var(--portal-sidebar)] text-[var(--portal-sidebar-text)]">
          <div className="mx-auto max-w-[1280px] px-4 py-10 sm:px-6 lg:px-12 lg:py-12">
            <SectionEyebrow className="text-[var(--portal-yellow)]">
              {messages.home.processKicker}
            </SectionEyebrow>
            <ol className="mt-8 flex flex-col gap-4 lg:flex-row lg:items-center lg:gap-0">
              {messages.home.processSteps.map((step, index) => (
                <li
                  key={step}
                  className="flex items-center gap-4 border-l-4 border-[var(--portal-yellow)] pl-4 lg:flex-1 lg:border-l-0 lg:pl-0"
                >
                  <span
                    className="hidden h-4 w-1 shrink-0 bg-[var(--portal-yellow)] lg:block"
                    aria-hidden="true"
                  />
                  <span className="text-xl font-semibold leading-7">{step}</span>
                  {index < messages.home.processSteps.length - 1 ? (
                    <span
                      className="mx-4 hidden h-px flex-1 bg-white/20 lg:block"
                      aria-hidden="true"
                    />
                  ) : null}
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="mx-auto max-w-[1280px] px-4 py-10 sm:px-6 lg:px-12 lg:py-14">
          <ul className="divide-y divide-border border-y border-border">
            {proofRows.map((row) => (
              <li key={row.title} className="grid gap-2 py-6 md:grid-cols-[1fr_2fr] md:gap-8">
                <h2 className="text-base font-semibold leading-6 text-foreground">
                  {row.title}
                </h2>
                <p className="text-sm leading-6 text-muted-foreground">{row.description}</p>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className="bg-[var(--portal-sidebar)] text-[var(--portal-sidebar-text)]">
        <div className="mx-auto max-w-[1280px] px-4 py-12 sm:px-6 lg:px-12">
          <div className="grid gap-8 md:grid-cols-[1.2fr_1fr_1fr]">
            <div className="space-y-3">
              <BrandMark
                href="/"
                compact
                markClassName="border-0 bg-[var(--portal-yellow)] text-[var(--portal-yellow-ink)]"
                titleClassName="text-[var(--portal-sidebar-text)]"
                subtitleClassName="text-[var(--portal-sidebar-muted)]"
                subtitle={messages.home.footerSubtitle}
              />
              <p className="max-w-sm text-sm leading-6 text-[var(--portal-sidebar-muted)]">
                {messages.home.footerDescription}
              </p>
            </div>
            <div className="space-y-2">
              <h2 className="text-sm font-semibold text-[var(--portal-sidebar-text)]">
                {messages.home.footerWorkspace}
              </h2>
              <ul>
                {messages.home.footerNav.workspace.map((item) => (
                  <li key={item.label}>
                    <LocalizedLink
                      href={item.href}
                      className="inline-flex min-h-11 items-center text-sm text-[var(--portal-sidebar-muted)] transition-colors duration-150 hover:text-[var(--portal-sidebar-text)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]"
                    >
                      {item.label}
                    </LocalizedLink>
                  </li>
                ))}
              </ul>
            </div>
            <div className="space-y-2">
              <h2 className="text-sm font-semibold text-[var(--portal-sidebar-text)]">
                {messages.home.footerDelivery}
              </h2>
              <ul>
                {messages.home.footerNav.delivery.map((item) => (
                  <li key={item.label}>
                    <LocalizedLink
                      href={item.href}
                      className="inline-flex min-h-11 items-center text-sm text-[var(--portal-sidebar-muted)] transition-colors duration-150 hover:text-[var(--portal-sidebar-text)] hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--portal-yellow)] focus-visible:ring-offset-2 focus-visible:ring-offset-[var(--portal-sidebar)]"
                    >
                      {item.label}
                    </LocalizedLink>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="mt-8 border-t border-white/10 pt-6 text-sm text-[var(--portal-sidebar-muted)]">
            &copy; {currentYear} CampusCore. {messages.home.footerCopyright}
          </div>
        </div>
      </footer>
    </div>
  );
}
