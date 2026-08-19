'use client';

import { Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { ArrowLeft, Eye, EyeOff, Lock } from 'lucide-react';
import { authApi } from '@/lib/api';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { EmptyState, LoadingState } from '@/components/ui/state-block';
import { SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

export const dynamic = 'force-dynamic';

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { href, messages } = useI18n();
  const token = searchParams.get('token');

  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [formError, setFormError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');

    if (password !== confirmPassword) {
      setFormError(messages.resetPassword.errors.mismatch);
      return;
    }

    if (password.length < 8) {
      setFormError(messages.resetPassword.errors.tooShort);
      return;
    }

    setIsLoading(true);
    try {
      await authApi.resetPassword(token!, password);
      toast.success(messages.resetPassword.successToast);
      router.push(href('/login'));
    } catch (error: any) {
      const message =
        error.response?.data?.message || messages.resetPassword.errors.fallback;
      setFormError(message);
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell
      eyebrow={messages.resetPassword.eyebrow}
      title={messages.resetPassword.title}
      description={messages.resetPassword.description}
      features={messages.resetPassword.featureTitles.map((label, index) => ({
        label,
        description: messages.resetPassword.featureDescriptions[index],
      }))}
      className="max-w-md"
    >
      {!token ? (
        <EmptyState
          title={messages.resetPassword.invalidTitle}
          description={messages.resetPassword.invalidDescription}
          action={
            <LocalizedLink href="/forgot-password">
              <Button>{messages.common.actions.requestNewResetLink}</Button>
            </LocalizedLink>
          }
        />
      ) : (
        <div className="space-y-6">
          <div className="space-y-3">
            <SectionEyebrow>{messages.resetPassword.sectionEyebrow}</SectionEyebrow>
            <div className="space-y-2">
              <h2 className="text-3xl font-semibold tracking-tight text-foreground">
                {messages.resetPassword.heading}
              </h2>
              <p className="text-sm leading-6 text-muted-foreground">
                {messages.resetPassword.subheading}
              </p>
            </div>
          </div>

          {formError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {formError}
            </div>
          ) : null}

          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="space-y-2">
              <label htmlFor="reset-password-new" className="text-sm font-medium text-foreground">
                {messages.resetPassword.newPassword}
              </label>
              <div className="relative">
                <Input
                  id="reset-password-new"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={messages.resetPassword.newPasswordPlaceholder}
                  autoComplete="new-password"
                  icon={<Lock className="h-4 w-4" />}
                  required
                  hint={messages.resetPassword.minimumHint}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((current) => !current)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground transition-colors hover:text-foreground"
                  aria-label={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                  title={showPassword ? messages.login.hidePassword : messages.login.showPassword}
                  aria-pressed={showPassword}
                >
                  {showPassword ? (
                    <EyeOff className="h-4 w-4" />
                  ) : (
                    <Eye className="h-4 w-4" />
                  )}
                </button>
              </div>
            </div>

            <div className="space-y-2">
              <label htmlFor="reset-password-confirm" className="text-sm font-medium text-foreground">
                {messages.resetPassword.confirmPassword}
              </label>
              <Input
                id="reset-password-confirm"
                type={showPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder={messages.resetPassword.confirmPasswordPlaceholder}
                autoComplete="new-password"
                icon={<Lock className="h-4 w-4" />}
                required
              />
            </div>

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading
                ? messages.resetPassword.savePassword
                : messages.resetPassword.resetPassword}
            </Button>
          </form>

          <p className="text-sm text-muted-foreground">
            <LocalizedLink href="/login" className="inline-flex items-center gap-2 font-medium text-primary hover:underline">
              <ArrowLeft className="h-4 w-4" />
              {messages.common.actions.backToSignIn}
            </LocalizedLink>
          </p>
        </div>
      )}
    </AuthShell>
  );
}

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<LoadingState label="Loading password reset flow" />}>
      <ResetPasswordForm />
    </Suspense>
  );
}
