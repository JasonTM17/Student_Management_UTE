'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AxiosError } from 'axios';
import { ArrowRight, Eye, EyeOff, Lock, Mail, UserRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useI18n } from '@/i18n';

export const dynamic = 'force-dynamic';

/** Feedback item 10: validation problems surface under the field they belong
 *  to, not only as one form-level banner. The banner stays as the fallback for
 *  server failures that cannot be tied to a single field. */
interface SignupFieldErrors {
  firstName?: string;
  lastName?: string;
  email?: string;
  password?: string;
}

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<SignupFieldErrors>({});
  const formErrorRef = useRef<HTMLDivElement>(null);
  const { register } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const copy = messages.signup;

  useEffect(() => {
    if (formError) {
      formErrorRef.current?.focus();
    }
  }, [formError]);

  const serverError = (error: unknown): { form?: string; field?: SignupFieldErrors } => {
    if (!(error instanceof AxiosError) || !error.response) {
      return { form: copy.errors.fallback };
    }
    const payload = error.response.data as { code?: string; message?: string } | undefined;
    const detail = (payload?.message ?? '').toLowerCase();
    if (error.response.status === 409 || payload?.code === 'EMAIL_ALREADY_EXISTS') {
      return { field: { email: copy.errors.emailConflict } };
    }
    if (error.response.status === 400) {
      if (detail.includes('password')) {
        return { field: { password: copy.errors.passwordShort } };
      }
      if (detail.includes('email')) {
        return { field: { email: copy.errors.emailInvalid } };
      }
      if (detail.includes('first') || detail.includes('last') || detail.includes('name')) {
        return { field: { firstName: copy.errors.nameRequired } };
      }
      return { form: copy.errors.validation };
    }
    return { form: copy.errors.fallback };
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();

    // Mirror the server rules so a wrong field shows its message immediately
    // (feedback item 10) instead of failing after a round trip.
    const errors: SignupFieldErrors = {};
    if (!firstName.trim() || !lastName.trim()) {
      errors.firstName = copy.errors.nameRequired;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      errors.email = copy.errors.emailInvalid;
    }
    if (password.length < 8) {
      errors.password = copy.errors.passwordShort;
    }
    setFieldErrors(errors);
    setFormError('');
    if (Object.keys(errors).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      await register({
        email: email.trim(),
        password,
        firstName: firstName.trim(),
        lastName: lastName.trim(),
      });
      router.push(href('/dashboard'));
    } catch (error: unknown) {
      const mapped = serverError(error);
      setFieldErrors(mapped.field ?? {});
      setFormError(mapped.form ?? '');
    } finally {
      setIsSubmitting(false);
    }
  };

  const fieldError = (id: keyof SignupFieldErrors, describedBy: string) =>
    fieldErrors[id] ? (
      <p id={`${describedBy}-error`} role="alert" className="text-xs font-medium text-destructive">
        {fieldErrors[id]}
      </p>
    ) : null;

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
        {formError ? (
          <div
            id="signup-error"
            ref={formErrorRef}
            role="alert"
            tabIndex={-1}
            className="rounded-xl border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive shadow-xs"
          >
            {formError}
          </div>
        ) : null}
        <form onSubmit={handleSubmit} className="space-y-4" aria-describedby={formError ? 'signup-error' : undefined} noValidate>
          <div className="space-y-2">
            <label htmlFor="firstName" className="text-sm font-medium text-foreground">
              {copy.firstNameLabel}
            </label>
            <Input
              id="firstName"
              value={firstName}
              onChange={(event) => setFirstName(event.target.value)}
              autoComplete="given-name"
              icon={<UserRound className="h-4 w-4" />}
              aria-invalid={fieldErrors.firstName ? true : undefined}
              aria-describedby={fieldErrors.firstName ? 'firstName-error' : undefined}
              required
            />
            {fieldError('firstName', 'firstName')}
          </div>
          <div className="space-y-2">
            <label htmlFor="lastName" className="text-sm font-medium text-foreground">
              {copy.lastNameLabel}
            </label>
            <Input
              id="lastName"
              value={lastName}
              onChange={(event) => setLastName(event.target.value)}
              autoComplete="family-name"
              icon={<UserRound className="h-4 w-4" />}
              aria-invalid={fieldErrors.lastName ? true : undefined}
              aria-describedby={fieldErrors.lastName ? 'lastName-error' : undefined}
              required
            />
            {fieldError('lastName', 'lastName')}
          </div>
          <div className="space-y-2">
            <label htmlFor="email" className="text-sm font-medium text-foreground">
              {copy.emailLabel}
            </label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              autoComplete="email"
              icon={<Mail className="h-4 w-4" />}
              aria-invalid={fieldErrors.email ? true : undefined}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              required
            />
            {fieldError('email', 'email')}
          </div>
          <div className="space-y-2">
            <label htmlFor="password" className="text-sm font-medium text-foreground">
              {copy.passwordLabel}
            </label>
            <Input
              id="password"
              type={showPassword ? 'text' : 'password'}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
              icon={<Lock className="h-4 w-4" />}
              aria-invalid={fieldErrors.password ? true : undefined}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              required
              endAction={
                <button
                  type="button"
                  className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                  title={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                  aria-pressed={showPassword}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" aria-hidden="true" />
                  ) : (
                    <Eye className="h-4 w-4" aria-hidden="true" />
                  )}
                </button>
              }
            />
            {fieldError('password', 'password')}
          </div>
          <Button type="submit" className="w-full" disabled={isSubmitting}>
            <span className="inline-flex items-center gap-2">
              {isSubmitting ? copy.submitting : copy.submit}
              <ArrowRight className="h-4 w-4" />
            </span>
          </Button>
        </form>
        <p className="text-sm text-muted-foreground">
          {copy.hasAccount}{' '}
          <LocalizedLink href="/login?portal=student" className="font-medium text-primary hover:underline">
            {copy.signInLink}
          </LocalizedLink>
        </p>
      </div>
    </AuthShell>
  );
}
