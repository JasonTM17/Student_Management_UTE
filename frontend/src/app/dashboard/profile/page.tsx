'use client';

import { useEffect, useState } from 'react';
import { Calendar, Camera, Eye, EyeOff, KeyRound, Mail, MapPin, Phone, Save, User } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { authApi } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState } from '@/components/ui/state-block';
import { WorkspacePanel } from '@/components/dashboard/WorkspaceSurface';
import { useI18n } from '@/i18n';
import { campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

type PasswordFieldKey = 'oldPassword' | 'newPassword' | 'confirmPassword';

const MAX_AVATAR_DATA_URL_LENGTH = 200_000;
const MAX_AVATAR_DIMENSION = 320;
const ALLOWED_AVATAR_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp']);

function createProfileAvatarDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const objectUrl = URL.createObjectURL(file);

    image.onload = () => {
      try {
        const scale = Math.min(
          1,
          MAX_AVATAR_DIMENSION / Math.max(image.naturalWidth, 1),
          MAX_AVATAR_DIMENSION / Math.max(image.naturalHeight, 1),
        );
        const width = Math.max(1, Math.round(image.naturalWidth * scale));
        const height = Math.max(1, Math.round(image.naturalHeight * scale));
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const context = canvas.getContext('2d');
        if (!context) {
          reject(new Error('canvas-unavailable'));
          return;
        }

        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, width, height);
        context.drawImage(image, 0, 0, width, height);

        const qualities = [0.82, 0.72, 0.62];
        for (const quality of qualities) {
          const dataUrl = canvas.toDataURL('image/jpeg', quality);
          if (dataUrl.length <= MAX_AVATAR_DATA_URL_LENGTH) {
            resolve(dataUrl);
            return;
          }
        }
        reject(new Error('avatar-too-large'));
      } finally {
        URL.revokeObjectURL(objectUrl);
      }
    };

    image.onerror = () => {
      URL.revokeObjectURL(objectUrl);
      reject(new Error('image-load-failed'));
    };
    image.src = objectUrl;
  });
}

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

function PasswordField({
  id,
  name,
  placeholder,
  hint,
  minLength,
  visible,
  showLabel,
  hideLabel,
  onToggle,
}: {
  id: string;
  name: PasswordFieldKey;
  placeholder: string;
  hint?: string;
  minLength?: number;
  visible: boolean;
  showLabel: string;
  hideLabel: string;
  onToggle: () => void;
}) {
  const label = visible ? hideLabel : showLabel;
  return (
    <Input
      id={id}
      name={name}
      type={visible ? 'text' : 'password'}
      placeholder={placeholder}
      required
      minLength={minLength}
      hint={hint}
      icon={<KeyRound className="h-4 w-4" />}
      endAction={
        <button
          type="button"
          className="inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition hover:bg-secondary hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
          aria-label={label}
          title={label}
          aria-pressed={visible}
          onClick={onToggle}
        >
          {visible ? <EyeOff className="h-4 w-4" aria-hidden="true" /> : <Eye className="h-4 w-4" aria-hidden="true" />}
        </button>
      }
    />
  );
}

export default function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const { messages } = useI18n();
  const [isLoading, setIsLoading] = useState(false);
  const [isUpdatingPassword, setIsUpdatingPassword] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [avatarPreview, setAvatarPreview] = useState('');
  const [visiblePasswordFields, setVisiblePasswordFields] = useState<Record<PasswordFieldKey, boolean>>({
    oldPassword: false,
    newPassword: false,
    confirmPassword: false,
  });
  // Key the form by user id so it initializes only after the profile has
  // loaded: seeding from a null user then saving would wipe every field.
  const [formData, setFormData] = useState(() => profileFormState(user));

  // Keep the form in sync if the signed-in user changes (re-login, refresh).
  const [formUserId, setFormUserId] = useState(user?.id);
  if (user && formUserId !== user.id) {
    setFormUserId(user.id);
    setFormData(profileFormState(user));
  }

  useEffect(() => {
    setAvatarPreview(user?.avatar ?? '');
  }, [user?.avatar, user?.id]);

  const togglePasswordField = (field: PasswordFieldKey) => {
    setVisiblePasswordFields((current) => ({
      ...current,
      [field]: !current[field],
    }));
  };

  const handlePhotoChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !user?.id || !ALLOWED_AVATAR_TYPES.has(file.type)) {
      return;
    }

    try {
      const nextAvatar = await createProfileAvatarDataUrl(file);
      setProfileError('');
      setAvatarPreview(nextAvatar);
      window.dispatchEvent(
        new CustomEvent('campuscore:avatar-updated', {
          detail: { userId: user.id, photo: nextAvatar },
        }),
      );
    } catch {
      setProfileError(messages.profile.photoUploadFailed);
      toast.error(messages.profile.photoUploadFailed);
    }
  };

  const removePhoto = () => {
    setAvatarPreview('');
    window.dispatchEvent(
      new CustomEvent('campuscore:avatar-updated', {
        detail: { userId: user?.id, photo: '' },
      }),
    );
  };

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
        avatar: avatarPreview,
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
      setVisiblePasswordFields({
        oldPassword: false,
        newPassword: false,
        confirmPassword: false,
      });
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
        <LoadingState label={messages.common.states.loadingContent} />
      </div>
    );
  }

  const initials =
    `${user.firstName?.[0] ?? ''}${user.lastName?.[0] ?? ''}`.toUpperCase() ||
    user.email?.[0]?.toUpperCase() ||
    'U';

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
            <div className="flex flex-col gap-4 rounded-xl border border-border/70 bg-secondary/35 p-5 shadow-xs sm:flex-row sm:items-center">
              <div className="flex h-20 w-20 shrink-0 items-center justify-center overflow-hidden rounded-full bg-primary text-xl font-semibold text-primary-foreground">
                {avatarPreview ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={avatarPreview} alt="" className="h-full w-full object-cover" />
                ) : (
                  initials
                )}
              </div>
              <div className="min-w-0 flex-1 space-y-2">
                <div className="text-lg font-semibold text-foreground">
                  {user?.firstName} {user?.lastName}
                </div>
                <div className="text-sm text-muted-foreground">{user?.email}</div>
                <div className="flex flex-wrap items-center gap-2">
                  <input
                    id="profile-photo"
                    type="file"
                    accept="image/png,image/jpeg,image/webp"
                    aria-label={messages.profile.photoLabel}
                    className="sr-only"
                    onChange={handlePhotoChange}
                  />
                  <label
                    htmlFor="profile-photo"
                    className="inline-flex min-h-11 cursor-pointer items-center justify-center gap-2 rounded-md border border-input bg-background px-3 py-2 text-sm font-medium text-foreground transition hover:bg-secondary focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2"
                  >
                    <Camera className="h-4 w-4" />
                    {messages.profile.uploadPhoto}
                  </label>
                  {avatarPreview ? (
                    <Button type="button" variant="ghost" size="sm" onClick={removePhoto}>
                      {messages.profile.removePhoto}
                    </Button>
                  ) : null}
                </div>
                <p className="text-xs text-muted-foreground">{messages.profile.photoHint}</p>
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
                  <PasswordField
                    id="profile-current-password"
                    name="oldPassword"
                    placeholder={messages.profile.fields.currentPasswordPlaceholder}
                    visible={visiblePasswordFields.oldPassword}
                    showLabel={messages.login.showPassword}
                    hideLabel={messages.login.hidePassword}
                    onToggle={() => togglePasswordField('oldPassword')}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-new-password" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.newPassword}
                  </label>
                  <PasswordField
                    id="profile-new-password"
                    name="newPassword"
                    placeholder={messages.profile.fields.newPasswordPlaceholder}
                    minLength={8}
                    hint={messages.profile.fields.passwordHint}
                    visible={visiblePasswordFields.newPassword}
                    showLabel={messages.login.showPassword}
                    hideLabel={messages.login.hidePassword}
                    onToggle={() => togglePasswordField('newPassword')}
                  />
                </div>
                <div className="space-y-2">
                  <label htmlFor="profile-confirm-password" className="text-sm font-medium text-foreground">
                    {messages.profile.fields.confirmNewPassword}
                  </label>
                  <PasswordField
                    id="profile-confirm-password"
                    name="confirmPassword"
                    placeholder={messages.profile.fields.confirmNewPasswordPlaceholder}
                    minLength={8}
                    visible={visiblePasswordFields.confirmPassword}
                    showLabel={messages.login.showPassword}
                    hideLabel={messages.login.hidePassword}
                    onToggle={() => togglePasswordField('confirmPassword')}
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
