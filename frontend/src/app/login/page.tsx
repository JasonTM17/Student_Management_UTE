'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { AxiosError } from 'axios';
import { ArrowRight, CheckCircle2, Eye, EyeOff, Lock, Mail, ShieldCheck, X } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useI18n } from '@/i18n';
import {
  LOGIN_PORTALS,
  parseLoginPortal,
  portalMatchesUser,
  postLoginRoute,
  type LoginPortal,
} from '@/lib/login-portal';
import { cn } from '@/lib/utils';

const DEMO_CREDENTIALS: Record<LoginPortal, { email: string; password: string }> = {
  student: { email: 'student@campuscore.edu', password: 'password123' },
  lecturer: { email: 'lecturer@campuscore.edu', password: 'password123' },
  admin: { email: 'admin@campuscore.edu', password: 'admin123' },
};

export const dynamic = 'force-dynamic';
export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [isClientReady, setIsClientReady] = useState(false);
  const [noticeDismissed, setNoticeDismissed] = useState(false);
  const [formError, setFormError] = useState('');
  const formErrorRef = useRef<HTMLDivElement>(null);
  const { login, logout } = useAuth();
  const { href, messages, locale } = useI18n();
  const router = useRouter();
  const searchParams = useSearchParams();
  const portal = parseLoginPortal(searchParams.get('portal'));
  const portalCopy = messages.login.portals[portal];

  useEffect(() => {
    setIsClientReady(true);
  }, []);

  useEffect(() => {
    setFormError('');
    const creds = DEMO_CREDENTIALS[portal];
    setEmail(creds.email);
    setPassword(creds.password);
  }, [portal]);
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
      if (!portalMatchesUser(portal, user)) {
        await logout({ redirect: false });
        setFormError(portalCopy.mismatch);
        return;
      }

      router.push(href(postLoginRoute(portal)));
    } catch (error: unknown) {
      const message = getLoginErrorMessage(error);
      setFormError(message);
    } finally {
      setIsLoading(false);
    }
  };

  const portalHref = (next: LoginPortal) => {
    const params = new URLSearchParams();
    params.set('portal', next);
    if (reason) {
      params.set('reason', reason);
    }
    return `${href('/login')}?${params.toString()}`;
  };

  return (
    <AuthShell
      portal={portal}
      eyebrow={portalCopy.eyebrow}
      title={portalCopy.title}
      description={portalCopy.description}
      features={portalCopy.featureTitles.map((label, index) => ({
        label,
        description: portalCopy.featureDescriptions[index],
      }))}
    >
      <div className="space-y-6">
        <div className="space-y-2">
          <h2 className="text-2xl font-semibold leading-8 text-foreground">
            {portalCopy.heading}
          </h2>
          <p className="text-sm leading-6 text-muted-foreground">
            {portalCopy.subheading}
          </p>
        </div>

        <div
          className="grid grid-cols-3 gap-1 rounded-xl border border-border/80 bg-secondary/40 p-1"
          role="tablist"
          aria-label={messages.login.portals.groupLabel}
        >
          {LOGIN_PORTALS.map((item) => {
            const selected = item === portal;
            return (
              <LocalizedLink
                key={item}
                href={portalHref(item)}
                role="tab"
                aria-selected={selected}
                className={cn(
                  'inline-flex min-h-11 items-center justify-center rounded-lg px-2 text-center text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  selected
                    ? 'bg-card text-foreground shadow-sm'
                    : 'text-muted-foreground hover:text-foreground',
                )}
              >
                {messages.login.portals[item].tab}
              </LocalizedLink>
            );
          })}
        </div>
        <p className="text-xs leading-5 text-muted-foreground">{portalCopy.destination}</p>

        <div className="rounded-xl border border-primary/25 bg-primary/5 p-3.5 text-xs space-y-2 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-foreground">
              {locale === 'vi' ? 'Tài khoản demo sẵn có:' : 'Demo account for this role:'}
            </span>
            <button
              type="button"
              onClick={() => {
                const creds = DEMO_CREDENTIALS[portal];
                setEmail(creds.email);
                setPassword(creds.password);
              }}
              className="text-primary hover:underline font-semibold text-xs flex items-center gap-1"
            >
              <ArrowRight className="h-3.5 w-3.5" />
              {locale === 'vi' ? 'Điền nhanh' : 'Quick fill'}
            </button>
          </div>
          <div className="text-muted-foreground flex items-center justify-between">
            <code>{DEMO_CREDENTIALS[portal].email}</code>
            <span className="text-[11px] bg-secondary px-1.5 py-0.5 rounded text-foreground font-mono">
              {DEMO_CREDENTIALS[portal].password}
            </span>
          </div>
        </div>

        {notice && !noticeDismissed ? (
          <div
            role="status"
            className={cn(
              'relative overflow-hidden rounded-xl border p-4 shadow-sm transition-all duration-200',
              reason === 'signed-out'
                ? 'border-emerald-500/30 bg-emerald-500/[0.08] dark:border-emerald-500/30 dark:bg-emerald-950/30'
                : 'border-border/80 bg-secondary/50'
            )}
          >
            <div className="flex items-start gap-3.5">
              <div
                className={cn(
                  'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg shadow-xs',
                  reason === 'signed-out'
                    ? 'bg-emerald-500/15 text-emerald-600 dark:bg-emerald-500/25 dark:text-emerald-400'
                    : 'bg-primary/10 text-primary'
                )}
              >
                {reason === 'signed-out' ? (
                  <ShieldCheck className="h-5 w-5" />
                ) : (
                  <CheckCircle2 className="h-5 w-5" />
                )}
              </div>

              <div className="min-w-0 flex-1 pr-6">
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      'text-sm font-semibold',
                      reason === 'signed-out'
                        ? 'text-emerald-950 dark:text-emerald-200'
                        : 'text-foreground'
                    )}
                  >
                    {reason === 'signed-out'
                      ? (locale === 'vi' ? 'Đã đăng xuất an toàn' : 'Signed Out Securely')
                      : notice.title}
                  </span>
                  {reason === 'signed-out' && (
                    <span className="inline-flex items-center rounded-xs bg-emerald-500/15 px-2 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                      {locale === 'vi' ? 'Bảo mật hệ thống' : 'System Secured'}
                    </span>
                  )}
                </div>

                <p
                  className={cn(
                    'mt-1.5 text-xs leading-relaxed',
                    reason === 'signed-out'
                      ? 'text-emerald-900/90 dark:text-emerald-300/90'
                      : 'text-muted-foreground'
                  )}
                >
                  {reason === 'signed-out'
                    ? (locale === 'vi'
                        ? 'Bạn đã đăng xuất khỏi cổng học vụ thành công. Phiên làm việc đã kết thúc và dữ liệu bảo mật đã được xóa an toàn khỏi trình duyệt này.'
                        : 'You have signed out of the academic portal. The session has terminated and security tokens have been safely cleared.')
                    : notice.body}
                </p>
              </div>

              <button
                type="button"
                onClick={() => setNoticeDismissed(true)}
                className="absolute top-3.5 right-3.5 rounded-md p-1 text-muted-foreground hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10"
                aria-label={locale === 'vi' ? 'Đóng thông báo' : 'Dismiss notice'}
              >
                <X className="h-4 w-4" />
              </button>
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
            className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive shadow-xs"
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
            <div className="flex items-center justify-between gap-4">
              <label
                htmlFor="password"
                className="text-sm font-medium text-foreground"
              >
                {messages.login.passwordLabel}
              </label>
              <span className="text-xs text-muted-foreground">
                {locale === 'vi'
                  ? 'Quên mật khẩu? Liên hệ '
                  : 'Forgot your password? Contact '}
                {portalCopy.officeSupport}.
              </span>
            </div>
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={messages.login.passwordPlaceholder}
              autoComplete="current-password"
              icon={<Lock className="h-4 w-4" />}
              required
              endAction={
                <button
                  type="button"
                  onClick={() => setShowPassword((current) => !current)}
                  className="flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary/60 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
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
              }
            />
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

        {portal === 'student' ? (
          <p className="text-sm text-muted-foreground">
            {messages.signup.needAccount}{' '}
            <LocalizedLink href="/register" className="font-medium text-primary hover:underline">
              {messages.signup.submit}
            </LocalizedLink>
          </p>
        ) : null}
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
