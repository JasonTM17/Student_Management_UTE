'use client';

import { useState } from 'react';
import { ArrowLeft, Mail, RotateCcw } from 'lucide-react';
import { authApi } from '@/lib/api';
import { AuthShell } from '@/components/auth/AuthShell';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { SectionEyebrow } from '@/components/ui/page-header';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

export const dynamic = 'force-dynamic';

export default function ForgotPasswordPage() {
  const { messages } = useI18n();
  const [email, setEmail] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [emailSent, setEmailSent] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      await authApi.forgotPassword(email.trim());
      setEmailSent(true);
      toast.success(messages.forgotPassword.sentToast);
    } catch {
      toast.error(messages.forgotPassword.failedToast);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <AuthShell
      eyebrow={messages.forgotPassword.eyebrow}
      title={messages.forgotPassword.title}
      description={messages.forgotPassword.description}
      features={messages.forgotPassword.featureTitles.map((label, index) => ({
        label,
        description: messages.forgotPassword.featureDescriptions[index],
      }))}
      className="max-w-md"
    >
      <div className="space-y-6">
        <div className="space-y-3">
          <SectionEyebrow>{messages.forgotPassword.sectionEyebrow}</SectionEyebrow>
          <div className="space-y-2">
            <h2 className="text-3xl font-semibold tracking-tight text-foreground">
              {messages.forgotPassword.heading}
            </h2>
            <p className="text-sm leading-6 text-muted-foreground">
              {emailSent
                ? messages.forgotPassword.afterSend
                : messages.forgotPassword.beforeSend}
            </p>
          </div>
        </div>

        {emailSent ? (
          <div className="space-y-4 rounded-lg border border-border/70 bg-card/80 p-5">
            <div className="rounded-lg border border-[hsl(var(--success))/0.3] bg-[hsl(var(--success))/0.08] px-4 py-3 text-sm text-foreground">
              {messages.forgotPassword.sentBanner.replace('{email}', email)}
            </div>
            <p className="text-sm leading-6 text-muted-foreground">
              {messages.forgotPassword.sentDescription}
            </p>
            <div className="flex flex-col gap-3 sm:flex-row">
              <Button
                type="button"
                variant="outline"
                onClick={() => setEmailSent(false)}
                className="sm:flex-1"
              >
                <RotateCcw className="mr-2 h-4 w-4" />
                {messages.common.actions.tryAnotherEmail}
              </Button>
              <LocalizedLink href="/login" className="sm:flex-1">
                <Button variant="default" className="w-full">
                  {messages.common.actions.backToSignIn}
                </Button>
              </LocalizedLink>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="space-y-2">
              <label htmlFor="forgot-password-email" className="text-sm font-medium text-foreground">
                {messages.forgotPassword.emailLabel}
              </label>
              <Input
                id="forgot-password-email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder={messages.forgotPassword.emailPlaceholder}
                autoComplete="email"
                required
                icon={<Mail className="h-4 w-4" />}
                hint={messages.forgotPassword.emailHint}
              />
            </div>

            <Button type="submit" className="w-full" disabled={isLoading}>
              {isLoading
                ? messages.forgotPassword.sendingResetInstructions
                : messages.forgotPassword.sendResetLink}
            </Button>
          </form>
        )}

        <p className="text-sm text-muted-foreground">
          <LocalizedLink href="/login" className="inline-flex items-center gap-2 font-medium text-primary hover:underline">
            <ArrowLeft className="h-4 w-4" />
            {messages.common.actions.backToSignIn}
          </LocalizedLink>
        </p>
      </div>
    </AuthShell>
  );
}
