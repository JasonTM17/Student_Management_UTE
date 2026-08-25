'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { AxiosError } from 'axios';
import { ArrowRight, CheckCircle2, Lock, Mail, ShieldCheck } from 'lucide-react';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { authApi } from '@/lib/api';

type ErrorBody = { code?: string; message?: string };

function errorCode(error: unknown): string | undefined {
  if (!(error instanceof AxiosError)) return undefined;
  const data = error.response?.data as ErrorBody | undefined;
  return data?.code;
}

function scrubToken(pathname: string, searchParams: URLSearchParams) {
  searchParams.delete('token');
  const query = searchParams.toString();
  window.history.replaceState({}, document.title, `${pathname}${query ? `?${query}` : ''}`);
}

function lifecycleToken(searchParams: URLSearchParams) {
  const fragment = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  return fragment.get('token') ?? searchParams.get('token');
}

function LifecycleFooter({ children }: { children: React.ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>;
}

export function RegisterPage() {
  const { href, messages } = useI18n();
  const router = useRouter();
  const copy = messages.register;
  const [form, setForm] = useState({ email: '', password: '', confirmPassword: '', firstName: '', lastName: '' });
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [success, setSuccess] = useState(false);

  const update = (field: keyof typeof form) => (event: React.ChangeEvent<HTMLInputElement>) => {
    setForm((current) => ({ ...current, [field]: event.target.value }));
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    if (form.password !== form.confirmPassword) {
      setError(copy.errors.mismatch);
      return;
    }
    setPending(true);
    try {
      await authApi.register({
        email: form.email.trim(),
        password: form.password,
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
      });
      setSuccess(true);
    } catch (requestError) {
      setError(errorCode(requestError) === 'EMAIL_ALREADY_EXISTS'
        ? copy.errors.emailExists
        : copy.errors.fallback);
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell
      eyebrow={copy.eyebrow}
      title={copy.title}
      description={copy.description}
      features={copy.featureTitles.map((label, index) => ({ label, description: copy.featureDescriptions[index] }))}
    >
      <div className="space-y-6">
        <div className="space-y-2">
          <SectionEyebrow>{copy.sectionEyebrow}</SectionEyebrow>
          <h2 className="text-2xl font-semibold leading-8 text-foreground">{success ? copy.successTitle : copy.heading}</h2>
          <p className="text-sm leading-6 text-muted-foreground">{success ? copy.successDescription : copy.subheading}</p>
        </div>
        {success ? (
          <div role="status" className="space-y-4 rounded-md border border-emerald-500/30 bg-emerald-500/5 px-4 py-4 text-sm text-foreground">
            <div className="flex items-start gap-3"><CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" /><span>{copy.successDescription}</span></div>
            <Button type="button" className="w-full" onClick={() => router.push(href(`/verify-email?email=${encodeURIComponent(form.email.trim())}`))}>
              {copy.continueVerification}<ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </div>
        ) : (
          <form onSubmit={submit} className="space-y-4" noValidate>
            {error ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{error}</div> : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2 text-sm font-medium"><label htmlFor="register-first-name">{copy.firstName}</label><Input id="register-first-name" value={form.firstName} onChange={update('firstName')} autoComplete="given-name" required /></div>
              <div className="space-y-2 text-sm font-medium"><label htmlFor="register-last-name">{copy.lastName}</label><Input id="register-last-name" value={form.lastName} onChange={update('lastName')} autoComplete="family-name" required /></div>
            </div>
            <div className="space-y-2 text-sm font-medium"><label htmlFor="register-email">{copy.emailLabel}</label><Input id="register-email" type="email" icon={<Mail className="h-4 w-4" />} value={form.email} onChange={update('email')} autoComplete="email" required /></div>
            <div className="space-y-2 text-sm font-medium"><label htmlFor="register-password">{copy.passwordLabel}</label><Input id="register-password" type="password" icon={<Lock className="h-4 w-4" />} value={form.password} onChange={update('password')} autoComplete="new-password" minLength={8} hint={copy.passwordHint} required /></div>
            <div className="space-y-2 text-sm font-medium"><label htmlFor="register-confirm-password">{copy.confirmPasswordLabel}</label><Input id="register-confirm-password" type="password" icon={<Lock className="h-4 w-4" />} value={form.confirmPassword} onChange={update('confirmPassword')} autoComplete="new-password" minLength={8} required /></div>
            <Button type="submit" className="w-full" disabled={pending}>
              {pending ? copy.submitting : copy.submit}<ArrowRight className="ml-2 h-4 w-4" />
            </Button>
          </form>
        )}
        <LifecycleFooter>{copy.alreadyHaveAccount}{' '}<LocalizedLink href="/login" className="font-medium text-primary hover:underline">{copy.signIn}</LocalizedLink></LifecycleFooter>
      </div>
    </AuthShell>
  );
}

export function VerifyEmailPage() {
  const { href, messages } = useI18n();
  const copy = messages.verifyEmail;
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [token, setToken] = useState('');
  const [email, setEmail] = useState(searchParams.get('email') ?? '');
  const [state, setState] = useState<'idle' | 'checking' | 'success' | 'invalid' | 'expired' | 'attemptsExceeded'>('idle');
  const [resendState, setResendState] = useState<'idle' | 'resending' | 'success' | 'throttled' | 'unavailable'>('idle');
  const autoSubmitted = useRef(false);

  useEffect(() => {
    const rawToken = lifecycleToken(searchParams);
    if (!rawToken || autoSubmitted.current) return;
    autoSubmitted.current = true;
    setToken(rawToken);
    scrubToken(pathname, new URLSearchParams(searchParams.toString()));
    setState('checking');
    void authApi.confirmEmail(rawToken.trim())
      .then(() => setState('success'))
      .catch((requestError) => {
        const code = errorCode(requestError);
        setState(code === 'AUTH_CHALLENGE_EXPIRED'
          ? 'expired'
          : code === 'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED'
            ? 'attemptsExceeded'
            : 'invalid');
      });
  }, [pathname, searchParams]);

  const confirm = async (candidate = token) => {
    if (!candidate.trim()) return;
    setState('checking');
    try {
      await authApi.confirmEmail(candidate.trim());
      setState('success');
    } catch (requestError) {
      const code = errorCode(requestError);
      setState(code === 'AUTH_CHALLENGE_EXPIRED'
        ? 'expired'
        : code === 'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED'
          ? 'attemptsExceeded'
          : 'invalid');
    }
  };

  const resend = async (event: React.FormEvent) => {
    event.preventDefault();
    setResendState('resending');
    try {
      await authApi.resendVerification(email.trim());
      setResendState('success');
    } catch (requestError) {
      const code = errorCode(requestError);
      setResendState(code === 'AUTH_RESEND_THROTTLED' || code === 'AUTH_RATE_LIMITED'
        ? 'throttled'
        : 'unavailable');
    }
  };

  const verificationMessage = state === 'success'
    ? copy.success
    : state === 'expired'
      ? copy.expired
      : state === 'attemptsExceeded'
        ? copy.attemptsExceeded
        : state === 'invalid'
          ? copy.invalid
          : copy.pending;

  return (
    <AuthShell eyebrow={copy.eyebrow} title={copy.title} description={copy.description} features={copy.featureTitles.map((label, index) => ({ label, description: copy.featureDescriptions[index] }))}>
      <div className="space-y-6">
        <div className="space-y-2"><SectionEyebrow>{copy.sectionEyebrow}</SectionEyebrow><h2 className="text-2xl font-semibold leading-8 text-foreground">{copy.heading}</h2><p className="text-sm leading-6 text-muted-foreground">{verificationMessage}</p></div>
        {state === 'success' ? <div role="status" className="flex items-start gap-3 rounded-md border border-emerald-500/30 bg-emerald-500/5 px-4 py-4 text-sm"><ShieldCheck className="mt-0.5 h-5 w-5 text-emerald-600" /><span>{copy.success}</span></div> : null}
        {state !== 'success' ? <form className="space-y-4" onSubmit={(event) => { event.preventDefault(); void confirm(); }}>
          {state === 'invalid' || state === 'expired' || state === 'attemptsExceeded' ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{verificationMessage}</div> : null}
          <div className="space-y-2 text-sm font-medium"><label htmlFor="verification-token">{copy.tokenLabel}</label><Input id="verification-token" type="password" value={token} onChange={(event) => setToken(event.target.value)} placeholder={copy.tokenPlaceholder} autoComplete="off" /></div>
          <Button type="submit" className="w-full" disabled={state === 'checking' || !token.trim()}>{state === 'checking' ? copy.verifying : copy.verify}</Button>
        </form> : null}
        {state !== 'success' ? <div className="space-y-3 border-t border-border/70 pt-5"><h3 className="text-sm font-semibold">{copy.resendTitle}</h3><p className="text-sm text-muted-foreground">{copy.resendDescription}</p><form className="space-y-3" onSubmit={resend}><label htmlFor="verification-email" className="sr-only">{copy.emailLabel}</label><Input id="verification-email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} placeholder={copy.emailPlaceholder} required /><Button type="submit" variant="outline" disabled={resendState === 'resending'}>{resendState === 'resending' ? copy.resending : copy.resend}</Button></form>{resendState === 'success' ? <div role="status" className="rounded-md border border-emerald-500/30 bg-emerald-500/5 px-3 py-2 text-sm">{copy.genericResendSuccess}</div> : null}{resendState === 'throttled' || resendState === 'unavailable' ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">{resendState === 'throttled' ? copy.resendThrottled : copy.resendUnavailable}</div> : null}</div> : null}
        {state === 'success' ? <Button type="button" className="w-full" onClick={() => router.push(href('/login'))}>{copy.backToLogin}<ArrowRight className="ml-2 h-4 w-4" /></Button> : null}
        {state !== 'success' ? <LifecycleFooter><LocalizedLink href="/login" className="font-medium text-primary hover:underline">{copy.backToLogin}</LocalizedLink></LifecycleFooter> : null}
      </div>
    </AuthShell>
  );
}

export function ForgotPasswordPage() {
  const { messages } = useI18n();
  const copy = messages.forgotPassword;
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setPending(true); setError('');
    try { await authApi.requestPasswordReset(email.trim()); setSent(true); }
    catch (requestError) { setError(errorCode(requestError) === 'AUTH_RATE_LIMITED' ? copy.rateLimited : copy.failedToast); }
    finally { setPending(false); }
  };
  return <AuthShell eyebrow={copy.eyebrow} title={copy.title} description={copy.description} features={copy.featureTitles.map((label, index) => ({ label, description: copy.featureDescriptions[index] }))}>
    <div className="space-y-6"><div className="space-y-2"><SectionEyebrow>{copy.sectionEyebrow}</SectionEyebrow><h2 className="text-2xl font-semibold leading-8 text-foreground">{copy.heading}</h2><p className="text-sm leading-6 text-muted-foreground">{sent ? copy.afterSend : copy.beforeSend}</p></div>
      {error ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{error}</div> : null}
      {sent ? <div role="status" className="rounded-md border border-emerald-500/30 bg-emerald-500/5 px-4 py-4 text-sm"><p>{copy.sentBanner.replace('{email}', email.trim())}</p><p className="mt-2 text-muted-foreground">{copy.sentDescription}</p></div> : <form onSubmit={submit} className="space-y-4"><div className="space-y-2 text-sm font-medium"><label htmlFor="forgot-email">{copy.emailLabel}</label><Input id="forgot-email" type="email" icon={<Mail className="h-4 w-4" />} value={email} onChange={(event) => setEmail(event.target.value)} placeholder={copy.emailPlaceholder} autoComplete="email" hint={copy.emailHint} required /></div><Button type="submit" className="w-full" disabled={pending}>{pending ? copy.sendingResetInstructions : copy.sendResetLink}</Button></form>}
      <LifecycleFooter><LocalizedLink href="/login" className="font-medium text-primary hover:underline">{messages.login.heading}</LocalizedLink></LifecycleFooter></div>
  </AuthShell>;
}

export function ResetPasswordPage() {
  const { href, messages } = useI18n();
  const copy = messages.resetPassword;
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [token, setToken] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [state, setState] = useState<'ready' | 'invalid' | 'expired' | 'attemptsExceeded' | 'saving' | 'success'>('ready');
  const [formError, setFormError] = useState('');
  const tokenRead = useRef(false);

  useEffect(() => {
    if (tokenRead.current) return;
    tokenRead.current = true;
    const rawToken = lifecycleToken(searchParams);
    if (rawToken) {
      setToken(rawToken);
      scrubToken(pathname, new URLSearchParams(searchParams.toString()));
    } else {
      setState('invalid');
    }
  }, [pathname, searchParams]);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError('');
    if (!token.trim()) { setFormError(copy.invalidDescription); return; }
    if (password !== confirmPassword) { setFormError(copy.errors.mismatch); return; }
    if (password.length < 8) { setFormError(copy.errors.tooShort); return; }
    setState('saving');
    try { await authApi.confirmPasswordReset(token.trim(), password); setState('success'); }
    catch (requestError) {
      const code = errorCode(requestError);
      setState(code === 'AUTH_CHALLENGE_EXPIRED'
        ? 'expired'
        : code === 'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED'
          ? 'attemptsExceeded'
          : 'invalid');
    }
  };

  const errorText = useMemo(() => {
    if (state === 'expired') return copy.expiredDescription;
    if (state === 'attemptsExceeded') return copy.attemptsExceededDescription;
    return copy.invalidDescription;
  }, [copy.attemptsExceededDescription, copy.expiredDescription, copy.invalidDescription, state]);

  const errorTitle = state === 'expired'
    ? copy.expiredTitle
    : state === 'attemptsExceeded'
      ? copy.attemptsExceededTitle
      : copy.invalidTitle;

  return <AuthShell eyebrow={copy.eyebrow} title={copy.title} description={copy.description} features={copy.featureTitles.map((label, index) => ({ label, description: copy.featureDescriptions[index] }))}>
    <div className="space-y-6"><div className="space-y-2"><SectionEyebrow>{copy.sectionEyebrow}</SectionEyebrow><h2 className="text-2xl font-semibold leading-8 text-foreground">{copy.heading}</h2><p className="text-sm leading-6 text-muted-foreground">{state === 'success' ? copy.successToast : state === 'invalid' || state === 'expired' || state === 'attemptsExceeded' ? errorText : copy.subheading}</p></div>
      {state === 'success' ? <div role="status" className="flex items-start gap-3 rounded-md border border-emerald-500/30 bg-emerald-500/5 px-4 py-4 text-sm"><CheckCircle2 className="mt-0.5 h-5 w-5 text-emerald-600" /><span>{copy.successToast}</span></div> : null}
      {state !== 'success' ? <form onSubmit={submit} className="space-y-4">{state === 'invalid' || state === 'expired' || state === 'attemptsExceeded' ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-4 text-sm text-destructive"><strong>{errorTitle}</strong><p className="mt-1">{errorText}</p></div> : null}{formError ? <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">{formError}</div> : null}<div className="space-y-2 text-sm font-medium"><label htmlFor="reset-token">{copy.tokenLabel}</label><Input id="reset-token" type="password" value={token} onChange={(event) => { setToken(event.target.value); setState('ready'); }} placeholder={copy.tokenPlaceholder} autoComplete="off" required /></div><div className="space-y-2 text-sm font-medium"><label htmlFor="reset-password">{copy.newPassword}</label><Input id="reset-password" type="password" icon={<Lock className="h-4 w-4" />} value={password} onChange={(event) => setPassword(event.target.value)} placeholder={copy.newPasswordPlaceholder} autoComplete="new-password" minLength={8} hint={copy.minimumHint} required /></div><div className="space-y-2 text-sm font-medium"><label htmlFor="reset-confirm-password">{copy.confirmPassword}</label><Input id="reset-confirm-password" type="password" icon={<Lock className="h-4 w-4" />} value={confirmPassword} onChange={(event) => setConfirmPassword(event.target.value)} placeholder={copy.confirmPasswordPlaceholder} autoComplete="new-password" minLength={8} required /></div><Button type="submit" className="w-full" disabled={state === 'saving'}>{state === 'saving' ? copy.savePassword : copy.resetPassword}</Button></form> : null}
      {state === 'success' ? <Button type="button" className="w-full" onClick={() => router.push(href('/login'))}>{messages.login.heading}<ArrowRight className="ml-2 h-4 w-4" /></Button> : null}
      {state !== 'success' ? <LifecycleFooter><LocalizedLink href="/forgot-password" className="font-medium text-primary hover:underline">{messages.forgotPassword.heading}</LocalizedLink></LifecycleFooter> : null}
    </div>
  </AuthShell>;
}
