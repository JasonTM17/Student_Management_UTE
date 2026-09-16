'use client';

import { ArrowRight, Building2, ShieldCheck } from 'lucide-react';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { LinkButton } from '@/components/ui/link-button';
import { useI18n } from '@/i18n';

export const dynamic = 'force-dynamic';

/** Student identities are provisioned by the Academic Office, not self-created. */
export default function RegisterPage() {
  const { messages } = useI18n();
  const copy = messages.signup;

  return (
    <AuthShell
      portal="student"
      eyebrow={copy.eyebrow}
      title={copy.title}
      description={copy.description}
      features={[]}
    >
      <div className="space-y-6">
        <div className="space-y-2">
          <h2 className="text-2xl font-semibold leading-8 text-foreground">{copy.heading}</h2>
          <p className="text-sm leading-6 text-muted-foreground">{copy.subheading}</p>
        </div>

        <section
          aria-labelledby="student-account-policy-title"
          className="rounded-2xl border border-primary/20 bg-primary/5 p-5 shadow-xs"
        >
          <div className="flex items-start gap-3">
            <span className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
              <Building2 className="h-5 w-5" aria-hidden="true" />
            </span>
            <div className="min-w-0 space-y-2">
              <h3 id="student-account-policy-title" className="font-semibold text-foreground">
                {copy.issuedByOfficeTitle}
              </h3>
              <p className="text-sm leading-6 text-muted-foreground">
                {copy.issuedByOfficeDescription}
              </p>
            </div>
          </div>
          <ul className="mt-4 space-y-2 text-sm text-foreground">
            {copy.issuedByOfficeSteps.map((step) => (
              <li key={step} className="flex items-start gap-2">
                <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
                <span>{step}</span>
              </li>
            ))}
          </ul>
        </section>

        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <LinkButton href="/login?portal=student" className="w-full sm:flex-1">
            <span className="inline-flex items-center gap-2">
              {copy.signInLink}
              <ArrowRight className="h-4 w-4" aria-hidden="true" />
            </span>
          </LinkButton>
          <LocalizedLink
            href="/"
            className="inline-flex min-h-11 items-center justify-center rounded-md px-4 text-sm font-medium text-muted-foreground transition hover:bg-secondary hover:text-foreground"
          >
            {copy.returnHome}
          </LocalizedLink>
        </div>

        <p className="text-sm leading-6 text-muted-foreground">
          {copy.needAccount} {copy.officeContact}
        </p>
      </div>
    </AuthShell>
  );
}
