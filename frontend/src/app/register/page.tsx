'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AxiosError } from 'axios';
import { ArrowRight, Lock, Mail, UserRound } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { useI18n } from '@/i18n';

export const dynamic = 'force-dynamic';

export default function RegisterPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
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

  const errorMessage = (error: unknown) => {
    if (!(error instanceof AxiosError) || !error.response) {
      return copy.errors.fallback;
    }
    if (error.response.status === 409) {
      return copy.errors.conflict;
    }
    if (error.response.status === 400) {
      return copy.errors.validation;
    }
    return copy.errors.fallback;
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setFormError('');
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
      setFormError(errorMessage(error));
    } finally {
      setIsSubmitting(false);
    }
  };

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
            className="rounded-md border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
          >
            {formError}
          </div>
        ) : null}
        <form onSubmit={handleSubmit} className="space-y-4" aria-describedby={formError ? 'signup-error' : undefined}>
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
              required
            />
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
              required
            />
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
              required
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="password" className="text-sm font-medium text-foreground">
              {copy.passwordLabel}
            </label>
            <Input
              id="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="new-password"
              minLength={8}
              icon={<Lock className="h-4 w-4" />}
              required
            />
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
