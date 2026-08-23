'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
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
  const [runtimeNotice, setRuntimeNotice] = useState<{
    tone: 'info' | 'warning';
    title: string;
    body: string;
  } | null>(null);
  const { login } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const searchParams = useSearchParams();

  useEffect(() => {
    setIsClientReady(true);
  }, []);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }

    const apiBaseUrl = (process.env.NEXT_PUBLIC_API_URL || '/api/v1').replace(/\/$/, '');
    const apiOrigin = new URL(apiBaseUrl, window.location.origin).origin;

    let cancelled = false;

    const checkRuntime = async () => {
      try {
        const response = await fetch(`${apiBaseUrl}/health/liveness`, {
          cache: 'no-store',
        });

        if (cancelled) {
          return;
        }

        if (response.ok) {
          setRuntimeNotice(null);
          return;
        }

        setRuntimeNotice({
          tone: 'warning',
          title: messages.login.runtimeNotice.warningTitle,
          body: messages.login.runtimeNotice.warningBody.replace('{origin}', apiOrigin),
        });
      } catch {
        if (!cancelled) {
          setRuntimeNotice({
            tone: 'warning',
            title: messages.login.runtimeNotice.warningTitle,
            body: messages.login.runtimeNotice.warningBody.replace('{origin}', apiOrigin),
          });
        }
      }
    };

    void checkRuntime();
    return () => {
      cancelled = true;
    };
  }, [
    messages.login.runtimeNotice.warningBody,
    messages.login.runtimeNotice.warningTitle,
  ]);

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
          <SectionEyebrow>{messages.login.sectionEyebrow}</SectionEyebrow>
          <div className="space-y-2">
            <h2 className="text-3xl font-semibold tracking-tight text-foreground">
              {messages.login.heading}
            </h2>
            <p className="text-sm leading-6 text-muted-foreground">
              {messages.login.subheading}
            </p>
          </div>
        </div>

        {notice ? (
          <div className="rounded-lg border border-border/80 bg-secondary/50 px-4 py-3">
            <div className="text-sm font-semibold text-foreground">
              {notice.title}
            </div>
            <div className="mt-1 text-sm text-muted-foreground">
              {notice.body}
            </div>
          </div>
        ) : null}

        {runtimeNotice ? (
          <div
            className={`rounded-lg px-4 py-3 ${
              runtimeNotice.tone === 'warning'
                ? 'border border-amber-500/30 bg-amber-500/10'
                : 'border border-border/80 bg-secondary/40'
            }`}
          >
            <div className="text-sm font-semibold text-foreground">
              {runtimeNotice.title}
            </div>
            <div className="mt-1 text-sm leading-6 text-muted-foreground">
              {runtimeNotice.body}
            </div>
          </div>
        ) : null}

        {formError ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
            {formError}
          </div>
        ) : null}

        <form onSubmit={handleSubmit} className="space-y-5">
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
            <div className="flex items-center justify-between gap-4">
              <label
                htmlFor="password"
                className="text-sm font-medium text-foreground"
              >
                {messages.login.passwordLabel}
              </label>
              <LocalizedLink
                href="/forgot-password"
                className="text-sm font-medium text-primary hover:underline"
              >
                {messages.login.forgotPassword}
              </LocalizedLink>
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
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword((current) => !current)}
                className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground transition-colors hover:text-foreground"
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

          <Button type="submit" className="w-full" disabled={isLoading || !isClientReady}>
            {isLoading ? (
              <span className="inline-flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-primary-foreground/40 border-t-primary-foreground" />
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

        <div className="rounded-lg border border-border/70 bg-card/70 px-4 py-4">
          <div className="flex items-start gap-3">
            <div className="mt-0.5 flex h-9 w-9 items-center justify-center rounded-lg bg-[hsl(var(--foreground))] text-[hsl(var(--background))]">
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
          <LocalizedLink href="/" className="font-medium text-primary hover:underline">
            {messages.common.actions.returnHome}
          </LocalizedLink>
          .
        </p>
      </div>
    </AuthShell>
  );
}
