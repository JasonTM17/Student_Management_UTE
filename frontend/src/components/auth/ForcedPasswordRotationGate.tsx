'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { ShieldCheck } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { authApi } from '@/lib/api';
import { useI18n } from '@/i18n';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

/**
 * Office-issued accounts must rotate their temporary credential before the
 * portal is usable. Both the dashboard shell and the admin frame host this
 * gate so every role is covered; the server additionally rejects business
 * APIs with PASSWORD_CHANGE_REQUIRED, so the overlay is UX, not the gate.
 */
export function ForcedPasswordRotationGate() {
  const { user, logout } = useAuth();
  const { href, locale } = useI18n();
  const router = useRouter();
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  if (!user || !user.mustChangePassword) {
    return null;
  }

  const copy = locale === 'vi'
    ? {
        title: 'Đổi mật khẩu tạm thời',
        description:
          'Tài khoản của bạn do Phòng Đào tạo cấp bằng mật khẩu tạm thời. Hãy đặt mật khẩu mới để tiếp tục sử dụng cổng thông tin.',
        currentLabel: 'Mật khẩu tạm thời',
        newLabel: 'Mật khẩu mới',
        confirmLabel: 'Nhập lại mật khẩu mới',
        mismatch: 'Mật khẩu nhập lại chưa khớp.',
        short: 'Mật khẩu mới phải có ít nhất 8 ký tự.',
        submit: 'Đổi mật khẩu và đăng nhập lại',
        saving: 'Đang cập nhật...',
        success: 'Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.',
        failed: 'Không thể đổi mật khẩu. Hãy kiểm tra mật khẩu tạm thời và thử lại.',
      }
    : {
        title: 'Rotate your temporary credential',
        description:
          'Your account was issued by the Academic Office with a temporary password. Set a new one to continue using the portal.',
        currentLabel: 'Temporary password',
        newLabel: 'New password',
        confirmLabel: 'Confirm new password',
        mismatch: 'The confirmation does not match.',
        short: 'The new password must contain at least 8 characters.',
        submit: 'Rotate and sign in again',
        saving: 'Updating...',
        success: 'Password rotated. Please sign in again with the new one.',
        failed: 'Could not rotate the password. Check the temporary credential and retry.',
      };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError('');
    if (newPassword.length < 8) {
      setError(copy.short);
      return;
    }
    if (newPassword !== confirmPassword) {
      setError(copy.mismatch);
      return;
    }
    setIsSaving(true);
    try {
      await authApi.changePassword(oldPassword, newPassword);
      await logout({ redirect: false });
      router.replace(href('/login?reason=password-rotated'));
    } catch {
      setError(copy.failed);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    // A forced security gate: non-dismissible so Escape/backdrop cannot skip
    // the rotation, but still focus-trapped and screen-reader labelled via the
    // shared Modal (role="dialog" + aria-modal).
    <Modal
      isOpen
      onClose={() => {}}
      dismissible={false}
      title={copy.title}
      description={copy.description}
      className="max-w-md"
    >
      <div className="space-y-5">
        <p className="flex items-center gap-2 rounded-lg bg-secondary/40 px-3 py-2 text-xs text-muted-foreground">
          <ShieldCheck className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          {user.email}
        </p>

        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          {error ? (
            <p role="alert" className="text-sm font-medium text-destructive">
              {error}
            </p>
          ) : null}
          <div className="space-y-2">
            <label htmlFor="rotation-current" className="text-sm font-medium text-foreground">
              {copy.currentLabel}
            </label>
            <Input
              id="rotation-current"
              type="password"
              value={oldPassword}
              onChange={(e) => setOldPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="rotation-new" className="text-sm font-medium text-foreground">
              {copy.newLabel}
            </label>
            <Input
              id="rotation-new"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="rotation-confirm" className="text-sm font-medium text-foreground">
              {copy.confirmLabel}
            </label>
            <Input
              id="rotation-confirm"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
              minLength={8}
              required
            />
          </div>
          <Button type="submit" disabled={isSaving} className="w-full">
            {isSaving ? copy.saving : copy.submit}
          </Button>
        </form>
      </div>
    </Modal>
  );
}
