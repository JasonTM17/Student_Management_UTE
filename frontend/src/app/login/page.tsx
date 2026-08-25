'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { AxiosError } from 'axios';
import { ArrowRight, Eye, EyeOff, KeyRound, Lock, Mail } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { User } from '@/types/api';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

export const dynamic = 'force-dynamic';

function getPostLoginRoute(user: User) {
  const roles = user.roles ?? (user.role ? [user.role] : []);

  if (roles.includes('SUPER_ADMIN') || roles.includes('ADMIN')) {
    return '/admin';
  }

  if (roles.includes('LECTURER')) {
    return '/dashboard/lecturer';
  }

  return '/dashboard';
}

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isClientReady, setIsClientReady] = useState(false);
  const [formError, setFormError] = useState('');
  const formErrorRef = useRef<HTMLDivElement>(null);
  const { login } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    setIsClientReady(true);
  }, []);

  useEffect(() => {
    if (formError) {
      formErrorRef.current?.focus();
    }
  }, [formError]);

  const reason = searchParams.get('reason') ?? '';
  const notice = useMemo(() => {
    if (reason === 'session-expired') {
      return messages.login.reasonMessages.sessionExpired;
    }

    if (reason === 'unauthorized') {
      return messages.login.reasonMessages.unauthorized;
    }

    if (reason === 'signed-out') {
      return messages.login.reasonMessages.signedOut;
    }

    return undefined;
  }, [messages.login.reasonMessages, reason]);

  const getLoginErrorMessage = (error: unknown) => {
    if (!(error instanceof AxiosError)) {
      return messages.login.errors.fallback;
    }

    if (!error.response) {
      return messages.login.errors.backendUnavailable;
    }

    const code = (error.response.data as { code?: string } | undefined)?.code;

    if (code === 'EMAIL_VERIFICATION_REQUIRED') {
      return messages.login.errors.emailVerificationRequired;
    }

    if (error.response.status === 401) {
      return messages.login.errors.invalidCredentials;
    }

    if (error.response.status === 403) {
      return messages.login.errors.blocked;
    }

    if (error.response.status === 404) {
      return messages.login.errors.backendUnavailable;
    }

    if (error.response.status >= 500) {
      return messages.login.errors.temporaryUnavailable;
    }

    return messages.login.errors.fallback;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    setIsLoading(true);

    try {
      const user = await login(email.trim(), password);
      toast.success(messages.login.heading);
      router.push(href(getPostLoginRoute(user)));
    } catch (error: unknown) {
      const message = getLoginErrorMessage(error);
      setFormError(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell
      eyebrow={messages.login.eyebrow}
      title={messages.login.title}
      description={messages.login.description}
      features={messages.login.featureTitles.map((label, index) => ({
        label,
        description: messages.login.featureDescriptions[index],
      }))}
    >
      <div className="space-y-6">
        <div className="space-y-3">
          <SectionEyebrow className="lg:hidden">{messages.login.sectionEyebrow}</SectionEyebrow>
          <div className="space-y-2">
            <h2 className="text-2xl font-semibold leading-8 text-foreground">
              {messages.login.heading}
            </h2>
            <p className="text-sm leading-6 text-muted-foreground">
              {messages.login.subheading}
            </p>
          </div>
        </div>

        {notice ? (
          <div role="status" className="rounded-md border border-border/80 bg-secondary/50 px-4 py-3">
            <div className="text-sm font-semibold text-foreground">
              {notice.title}
            </div>
            <div className="mt-1 text-sm text-muted-foreground">
              {notice.body}
            </div>
          </div>
        ) : null}

        {formError ? (
          <div
            id="login-error"
            ref={formErrorRef}
            role="alert"
            aria-live="assertive"
            tabIndex={-1}
            className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
          >
            {formError}
          </div>
        ) : null}

        <form
          onSubmit={handleSubmit}
          className="space-y-5"
          aria-describedby={formError ? 'login-error' : undefined}
        >
          <div className="space-y-2">
            <label
              htmlFor="email"
              className="text-sm font-medium text-foreground"
            >
              {messages.login.emailLabel}
            </label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder={messages.login.emailPlaceholder}
              autoComplete="email"
              icon={<Mail className="h-4 w-4" />}
              required
            />
          </div>

          <div className="space-y-2">
            <div>
              <label
                htmlFor="password"
                className="text-sm font-medium text-foreground"
              >
                {messages.login.passwordLabel}
              </label>
            </div>
            <div className="relative">
              <Input
                id="password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={messages.login.passwordPlaceholder}
                autoComplete="current-password"
                icon={<Lock className="h-4 w-4" />}
                className="pr-12"
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword((current) => !current)}
                className="absolute right-0 top-1/2 flex h-11 w-11 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary/60 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                aria-label={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                title={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                aria-pressed={showPassword}
                disabled={!isClientReady}
              >
                {showPassword ? (
                  <EyeOff className="h-4 w-4" />
                ) : (
                  <Eye className="h-4 w-4" />
                )}
              </button>
            </div>
          </div>

          <Button type="submit" className="w-full" disabled={isLoading || !isClientReady} aria-busy={isLoading}>
            {isLoading ? (
              <span className="inline-flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/40 border-t-primary-foreground motion-reduce:animate-none" aria-hidden="true" />
                {messages.login.signingIn}
              </span>
            ) : (
              <span className="inline-flex items-center gap-2">
                {messages.common.actions.signIn}
                <ArrowRight className="h-4 w-4" />
              </span>
            )}
          </Button>
        </form>

        <div className="flex flex-wrap items-center justify-between gap-3 text-sm">
          <LocalizedLink href="/forgot-password" className="inline-flex min-h-11 items-center rounded-sm font-medium text-primary underline-offset-4 transition-transform duration-150 hover:underline active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none">
            {messages.login.forgotPassword}
          </LocalizedLink>
          <LocalizedLink href="/register" className="inline-flex min-h-11 items-center rounded-sm font-medium text-primary underline-offset-4 transition-transform duration-150 hover:underline active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none">
            {messages.register.heading}
          </LocalizedLink>
        </div>

        <div className="border-l-2 border-primary bg-secondary/35 px-4 py-3">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 items-center justify-center rounded-md bg-primary text-primary-foreground">
              <KeyRound className="h-4 w-4" />
            </div>
            <div>
              <div className="text-sm font-semibold text-foreground">
                {messages.login.sessionBehaviorTitle}
              </div>
              <p className="mt-1 text-sm leading-6 text-muted-foreground">
                {messages.login.sessionBehaviorDescription}
              </p>
            </div>
          </div>
        </div>

        <p className="text-sm text-muted-foreground">
          {messages.login.returnHomeLead}{' '}
          <LocalizedLink href="/" className="inline-flex min-h-11 items-center rounded-sm font-medium text-primary underline-offset-4 transition-transform duration-150 hover:underline active:translate-y-px focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 motion-reduce:transform-none">
            {messages.common.actions.returnHome}
          </LocalizedLink>
          .
        </p>
      </div>
    </AuthShell>
  );
}
