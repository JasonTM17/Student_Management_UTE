'use client';

import { useState } from 'react';
import { Calendar, KeyRound, Mail, MapPin, Phone, Save, User } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { authApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { WorkspacePanel } from '@/components/dashboard/WorkspaceSurface';
import { useI18n } from '@/i18n';
import { campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

function roleLabel(
  role: string,
  labels: { student: string; lecturer: string; admin: string },
) {
  const normalized = role.trim().toUpperCase();
  if (normalized === 'STUDENT') return labels.student;
  if (normalized === 'LECTURER') return labels.lecturer;
  if (normalized === 'ADMIN' || normalized === 'SUPER_ADMIN') return labels.admin;
  return labels.student;
}

/** Local-calendar date input value (toISOString shifts the day for UTC+7). */
function localDateInput(value: string | undefined): string {
  if (!value) return '';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return '';
  const month = `${parsed.getMonth() + 1}`.padStart(2, '0');
  const day = `${parsed.getDate()}`.padStart(2, '0');
  return `${parsed.getFullYear()}-${month}-${day}`;
}

function profileFormState(user: {
  firstName?: string;
  lastName?: string;
  phone?: string;
  dateOfBirth?: string;
  address?: string;
} | null | undefined) {
  return {
    firstName: user?.firstName || '',
    lastName: user?.lastName || '',
    phone: user?.phone || '',
    dateOfBirth: localDateInput(user?.dateOfBirth),
    address: user?.address || '',
  };
}

export default function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const { messages } = useI18n();
  const [isLoading, setIsLoading] = useState(false);
  const [isUpdatingPassword, setIsUpdatingPassword] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  // Key the form by user id so it initializes only after the profile has
  // loaded: seeding from a null user then saving would wipe every field.
  const [formData, setFormData] = useState(() => profileFormState(user));

  // Keep the form in sync if the signed-in user changes (re-login, refresh).
  const [formUserId, setFormUserId] = useState(user?.id);
  if (user && formUserId !== user.id) {
    setFormUserId(user.id);
    setFormData(profileFormState(user));
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError('');
    setIsLoading(true);

    try {
      await authApi.updateProfile({
        firstName: formData.firstName.trim(),
        lastName: formData.lastName.trim(),
        phone: formData.phone.trim(),
        dateOfBirth: formData.dateOfBirth || undefined,
        address: formData.address.trim(),
      });
      toast.success(messages.profile.profileUpdated);
      await refreshUser();
    } catch {
      const message = messages.profile.profileSaveFailed;
      setProfileError(message);
      toast.error(message);
    } finally {
      setIsLoading(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setPasswordError('');
    setIsUpdatingPassword(true);

    const form = e.currentTarget;
    const oldPassword = (form.elements.namedItem('oldPassword') as HTMLInputElement).value;
    const newPassword = (form.elements.namedItem('newPassword') as HTMLInputElement).value;
    const confirmPassword = (form.elements.namedItem('confirmPassword') as HTMLInputElement).value;

    if (newPassword !== confirmPassword) {
      setPasswordError(messages.profile.errors.mismatch);
      setIsUpdatingPassword(false);
      return;
    }

    if (newPassword.length < 8) {
      setPasswordError(messages.profile.errors.tooShort);
      setIsUpdatingPassword(false);
      return;
    }

    try {
      await authApi.changePassword(oldPassword, newPassword);
      toast.success(messages.profile.passwordUpdated);
      form.reset();
    } catch (error: any) {
      const message = campusErrorMessage(
        error,
        messages.common.campusErrors,
        messages.profile.passwordUpdateFailed,
      );
      setPasswordError(message);
      toast.error(message);
    } finally {
      setIsUpdatingPassword(false);
    }
  };

  // Never render (or submit) the profile form from an unloaded user: an
  // early Save before the profile fetch resolves would blank every field.
  if (!user) {
    return (
      <div className="space-y-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{messages.profile.eyebrow}</SectionEyebrow>}
          title={messages.profile.title}
          description={messages.profile.description}
        />
        <p className="text-sm text-muted-foreground" role="status">
          {messages.common.states.loadingContent}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{messages.profile.eyebrow}</SectionEyebrow>}
        title={messages.profile.title}
        description={messages.profile.description}
      />

      <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
        <WorkspacePanel
          title={messages.profile.profileTitle}
          description={messages.profile.profileDescription}
          contentClassName="space-y-6"
        >
            <div className="flex flex-col gap-4 rounded-lg border border-border/70 bg-secondary/35 p-5 sm:flex-row sm:items-center">
              <div className="flex h-16 w-16 items-center justify-center rounded-full bg-primary text-xl font-semibold text-primary-foreground">
                {user?.firstName?.[0]}
                {user?.lastName?.[0]}
              </div>
              <div className="space-y-1">
                <div className="text-lg font-semibold text-foreground">
                  {user?.firstName} {user?.lastName}
                </div>
                <div className="text-sm text-muted-foreground">{user?.email}</div>
                <div className="flex flex-wrap gap-2 pt-1">
                  {user?.roles?.map((role) => (
                    <span
                      key={role}
                      className="rounded-full bg-card px-2.5 py-1 text-xs font-medium text-foreground"
                    >
                      {roleLabel(role, messages.dashboardShell.roles)}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            {profileError ? (
              <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                {profileError}
              </div>
            ) : null}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <label htmlFor="profile-first-name" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.firstName}
                  </label>
                  <Input
                    id="profile-first-name"
                    type="text"
                    value={formData.firstName}
                    onChange={(e) =>
                      setFormData((current) => ({ ...current, firstName: e.target.value }))
                    }
                    icon={<User className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-last-name" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.lastName}
                  </label>
                  <Input
                    id="profile-last-name"
                    type="text"
                    value={formData.lastName}
                    onChange={(e) =>
                      setFormData((current) => ({ ...current, lastName: e.target.value }))
                    }
                    icon={<User className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-email" className="text-sm font-medium text-foreground">{messages.profile.fields.email}</label>
                  <Input
                    id="profile-email"
                    type="email"
                    value={user?.email || ''}
                    disabled
                    icon={<Mail className="h-4 w-4" />}
                    hint={messages.profile.fields.managedHint}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-phone" className="text-sm font-medium text-foreground">{messages.profile.fields.phone}</label>
                  <Input
                    id="profile-phone"
                    type="tel"
                    value={formData.phone}
                    onChange={(e) =>
                      setFormData((current) => ({ ...current, phone: e.target.value }))
                    }
                    placeholder={messages.profile.fields.phonePlaceholder}
                    icon={<Phone className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-date-of-birth" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.dateOfBirth}
                  </label>
                  <Input
                    id="profile-date-of-birth"
                    type="date"
                    value={formData.dateOfBirth}
                    onChange={(e) =>
                      setFormData((current) => ({ ...current, dateOfBirth: e.target.value }))
                    }
                    icon={<Calendar className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-address" className="text-sm font-medium text-foreground">{messages.profile.fields.address}</label>
                  <Input
                    id="profile-address"
                    type="text"
                    value={formData.address}
                    onChange={(e) =>
                      setFormData((current) => ({ ...current, address: e.target.value }))
                    }
                    placeholder={messages.profile.fields.addressPlaceholder}
                    icon={<MapPin className="h-4 w-4" />}
                  />
                </div>
              </div>
              <div className="flex justify-end">
                <Button type="submit" disabled={isLoading}>
                  <Save className="mr-2 h-4 w-4" />
                  {isLoading ? messages.profile.buttons.savingChanges : messages.common.actions.saveChanges}
                </Button>
              </div>
            </form>
        </WorkspacePanel>

        <div className="space-y-6">
          <WorkspacePanel
            title={messages.profile.passwordTitle}
            description={messages.profile.passwordDescription}
            variant="muted"
            contentClassName="space-y-4"
          >
              {passwordError ? (
                <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                  {passwordError}
                </div>
              ) : null}
              <form className="space-y-4" onSubmit={handlePasswordSubmit}>
                <div className="space-y-2">
                  <label htmlFor="profile-current-password" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.currentPassword}
                  </label>
                  <Input
                    id="profile-current-password"
                    name="oldPassword"
                    type="password"
                    placeholder={messages.profile.fields.currentPasswordPlaceholder}
                    required
                    icon={<KeyRound className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-new-password" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.newPassword}
                  </label>
                  <Input
                    id="profile-new-password"
                    name="newPassword"
                    type="password"
                    placeholder={messages.profile.fields.newPasswordPlaceholder}
                    required
                    minLength={8}
                    hint={messages.profile.fields.passwordHint}
                    icon={<KeyRound className="h-4 w-4" />}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-confirm-password" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.confirmNewPassword}
                  </label>
                  <Input
                    id="profile-confirm-password"
                    name="confirmPassword"
                    type="password"
                    placeholder={messages.profile.fields.confirmNewPasswordPlaceholder}
                    required
                    minLength={8}
                    icon={<KeyRound className="h-4 w-4" />}
                  />
                </div>
                <div className="flex justify-end">
                  <Button type="submit" disabled={isUpdatingPassword}>
                    {isUpdatingPassword
                      ? messages.profile.buttons.updatingPassword
                      : messages.common.actions.updatePassword}
                  </Button>
                </div>
              </form>
          </WorkspacePanel>

          <WorkspacePanel
            title={messages.profile.whatChangesTitle}
            variant="muted"
            contentClassName="space-y-3 text-sm leading-6 text-muted-foreground"
          >
              {messages.profile.whatChanges.map((item) => (
                <p key={item}>{item}</p>
              ))}
          </WorkspacePanel>
        </div>
      </div>
    </div>
  );
}
