'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Pencil, Plus, Search, Trash2, Users } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { usersApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminFormField,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
  AdminToolbarMeta,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

interface UserRecord {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  roles?: string | string[];
  createdAt: string;
}

type ManagedRole = 'STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN';
const defaultRole: ManagedRole = 'STUDENT';

function primaryRole(roles?: string | string[]): ManagedRole {
  const values = Array.isArray(roles) ? roles : roles?.split(',') ?? [];
  return (
    values.find((role): role is ManagedRole =>
      ['STUDENT', 'LECTURER', 'ADMIN', 'SUPER_ADMIN'].includes(role.trim().toUpperCase()),
    )?.trim().toUpperCase() as ManagedRole | undefined
  ) ?? defaultRole;
}

function roleLabel(roles?: string | string[]) {
  const values = Array.isArray(roles) ? roles : roles?.split(',') ?? [];
  return values.filter(Boolean).join(', ') || defaultRole;
}

function userStatusTone(status: string): StatusTone {
  switch (status.toUpperCase()) {
    case 'ACTIVE':
      return 'success';
    case 'PENDING':
      return 'warning';
    case 'LOCKED':
    case 'SUSPENDED':
    case 'DISABLED':
      return 'danger';
    default:
      return 'neutral';
  }
}

export default function AdminUsersPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, formatDate, messages } = useI18n();
  const router = useRouter();
  const [users, setUsers] = useState<UserRecord[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState('');
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    role: defaultRole as ManagedRole,
  });
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const response = await usersApi.getAll({
        page,
        limit: 20,
        search: search || undefined,
      });
      const rows = Array.isArray(response.data)
        ? response.data
        : Array.isArray(response)
          ? response
          : [];
      setUsers(rows as UserRecord[]);
      setTotalPages(response.meta?.totalPages || 1);
    } catch (loadError) {
      const status = (loadError as { response?: { status?: number } }).response?.status;
      setError(
        status === 403
          ? locale === 'vi'
            ? 'Bạn không có quyền xem danh sách người dùng.'
            : 'You do not have permission to view user records.'
          : locale === 'vi'
            ? 'Hiện chưa thể tải hồ sơ người dùng.'
            : 'User records could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search]);

  useEffect(() => {
    if (canAccess) {
      void fetchUsers();
    }
  }, [canAccess, fetchUsers]);

  const pageSummary = useMemo(() => {
    if (users.length === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [locale, page, totalPages, users.length]);

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải quản lý người dùng',
          title: 'Quản lý người dùng',
          description:
            'Rà soát tài khoản campus, tạo bản ghi mới và giữ các hành động nhạy cảm sau bước xác nhận rõ ràng.',
          createUser: 'Tạo người dùng',
          searchUsers: 'Tìm người dùng',
          searchPlaceholder: 'Tìm theo email hoặc tên',
          unavailableTitle: 'Hồ sơ người dùng chưa sẵn sàng',
          emptyTitle: 'Không có người dùng phù hợp',
          emptyDescription:
            'Hãy thử từ khóa khác hoặc tạo tài khoản campus mới.',
          tableTitle: 'Tài khoản campus',
          headers: {
            name: 'Tên',
            email: 'Email',
            status: 'Trạng thái',
            role: 'Vai trò',
            created: 'Tạo ngày',
            actions: 'Tác vụ',
          },
          deleteTitle: 'Xóa người dùng',
          deleteMessage: (firstName: string, lastName: string) =>
            `Xóa ${firstName} ${lastName}? Hành động này sẽ gỡ bản ghi tài khoản khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa người dùng',
          deleted: 'Đã xóa người dùng',
          deleteFailed: 'Hiện chưa thể xóa người dùng này.',
          updated: 'Đã cập nhật người dùng',
          created: 'Đã tạo người dùng',
          saveFailed: 'Hiện chưa thể lưu hồ sơ người dùng.',
          editTitle: 'Chỉnh sửa người dùng',
          createTitle: 'Tạo người dùng',
          emailLabel: 'Địa chỉ email',
          temporaryPassword: 'Mật khẩu tạm thời',
          temporaryPasswordHint:
            'Hãy dùng mật khẩu tạm thời và yêu cầu người dùng đổi lại sau lần đăng nhập đầu tiên.',
          firstName: 'Tên',
          lastName: 'Họ',
          roleLabel: 'Vai trò hệ thống',
          roles: {
            STUDENT: 'Sinh viên',
            LECTURER: 'Giảng viên',
            ADMIN: 'Quản trị viên',
            SUPER_ADMIN: 'Siêu quản trị viên',
          },
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          closeDialog: 'Đóng biểu mẫu người dùng',
          editUserLabel: (firstName: string, lastName: string) =>
            `Chỉnh sửa người dùng ${firstName} ${lastName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Xóa người dùng ${firstName} ${lastName}`,
        }
      : {
          loading: 'Loading user management',
          title: 'User management',
          description:
            'Review campus accounts, create new records, and keep sensitive actions behind explicit confirmation.',
          createUser: 'Create user',
          searchUsers: 'Search users',
          searchPlaceholder: 'Search by email or name',
          unavailableTitle: 'User records unavailable',
          emptyTitle: 'No matching users',
          emptyDescription:
            'Try another search term or create a new campus account.',
          tableTitle: 'Campus accounts',
          headers: {
            name: 'Name',
            email: 'Email',
            status: 'Status',
            role: 'Role',
            created: 'Created',
            actions: 'Actions',
          },
          deleteTitle: 'Delete user',
          deleteMessage: (firstName: string, lastName: string) =>
            `Delete ${firstName} ${lastName}? This action removes the account record from the current admin view.`,
          deleteConfirm: 'Delete user',
          deleted: 'User deleted',
          deleteFailed: 'We could not delete that user.',
          updated: 'User updated',
          created: 'User created',
          saveFailed: 'The user record could not be saved.',
          editTitle: 'Edit user',
          createTitle: 'Create user',
          emailLabel: 'Email address',
          temporaryPassword: 'Temporary password',
          temporaryPasswordHint:
            'Use a temporary password and ask the user to rotate it after first sign-in.',
          firstName: 'First name',
          lastName: 'Last name',
          roleLabel: 'System role',
          roles: {
            STUDENT: 'Student',
            LECTURER: 'Lecturer',
            ADMIN: 'Administrator',
            SUPER_ADMIN: 'Super administrator',
          },
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          closeDialog: 'Close user form',
          editUserLabel: (firstName: string, lastName: string) =>
            `Edit user ${firstName} ${lastName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Delete user ${firstName} ${lastName}`,
        };

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingUser(null);
    setFormError('');
    setFormData({ email: '', password: '', firstName: '', lastName: '', role: defaultRole });
  };

  const openCreate = () => {
    resetForm();
    setShowCreateModal(true);
  };

  const openEdit = (userRecord: UserRecord) => {
    setEditingUser(userRecord);
    setFormError('');
    setFormData({
      email: userRecord.email,
      password: '',
      firstName: userRecord.firstName,
      lastName: userRecord.lastName,
      role: primaryRole(userRecord.roles),
    });
    setShowCreateModal(true);
  };

  const closeModal = () => {
    setShowCreateModal(false);
    resetForm();
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    await fetchUsers();
  };

  const handleDelete = async (userRecord: UserRecord) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(userRecord.firstName, userRecord.lastName),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await usersApi.delete(userRecord.id);
      toast.success(copy.deleted);
      await fetchUsers();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    setIsSaving(true);

    try {
      if (editingUser) {
        await usersApi.update(editingUser.id, {
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        });
        toast.success(copy.updated);
      } else {
        await usersApi.create({
          email: formData.email.trim(),
          password: formData.password,
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        });
        toast.success(copy.created);
      }

      closeModal();
      await fetchUsers();
    } catch (err: any) {
      const message = err.response?.data?.message || copy.saveFailed;
      setFormError(message);
      toast.error(message);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          {copy.createUser}
        </Button>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
            <form onSubmit={handleSearch} className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div className="w-full max-w-xl">
                <label className="mb-2 block text-sm font-medium text-foreground">
                  {copy.searchUsers}
                </label>
                <Input
                  type="text"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder={copy.searchPlaceholder}
                  icon={<Search className="h-4 w-4" />}
                />
              </div>
              <AdminToolbarMeta
                summary={pageSummary}
                actions={
                  <Button type="submit" variant="outline">
                    {messages.common.actions.search}
                  </Button>
                }
              />
            </form>
        </AdminToolbarCard>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchUsers()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : users.length === 0 ? (
          <EmptyState
            icon={Users}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            action={<Button onClick={openCreate}>{copy.createUser}</Button>}
          />
        ) : (
          <AdminTableCard
            title={copy.tableTitle}
            footer={
              <AdminPaginationFooter
                summary={pageSummary}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
              />
            }
          >
              <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
                {users.map((record) => (
                  <article
                    key={`${record.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <h3 className="font-semibold text-foreground">
                          {record.firstName} {record.lastName}
                        </h3>
                        <p className="mt-1 break-all text-sm text-muted-foreground">
                          {record.email}
                        </p>
                      </div>
                      <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                        {record.status}
                      </span>
                    </div>
                    <dl className="mt-4 border-t border-border/60 pt-3">
                      <div className="flex items-center justify-between gap-4 text-sm">
                        <dt className="text-muted-foreground">{copy.headers.created}</dt>
                        <dd className="text-right text-foreground">{formatDate(record.createdAt)}</dd>
                      </div>
                      <div className="mt-2 flex items-center justify-between gap-4 text-sm">
                        <dt className="text-muted-foreground">{copy.headers.role}</dt>
                        <dd className="text-right text-foreground">{roleLabel(record.roles)}</dd>
                      </div>
                    </dl>
                    <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => openEdit(record)}
                        aria-label={copy.editUserLabel(record.firstName, record.lastName)}
                        title={copy.editUserLabel(record.firstName, record.lastName)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => void handleDelete(record)}
                        aria-label={copy.deleteUserLabel(record.firstName, record.lastName)}
                        title={copy.deleteUserLabel(record.firstName, record.lastName)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AdminRowActions>
                  </article>
                ))}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[720px] text-sm">
                  <thead>
                    <tr className="bg-secondary text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.name}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.email}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.role}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.created}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {users.map((record) => (
                      <tr key={record.id}>
                        <td className="px-2 py-4">
                          <div className="font-medium text-foreground">
                            {record.firstName} {record.lastName}
                          </div>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {record.email}
                        </td>
                        <td className="px-2 py-4">
                          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                            {record.status}
                          </span>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {roleLabel(record.roles)}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatDate(record.createdAt)}
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(record)}
                              aria-label={copy.editUserLabel(record.firstName, record.lastName)}
                              title={copy.editUserLabel(record.firstName, record.lastName)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(record)}
                              aria-label={copy.deleteUserLabel(record.firstName, record.lastName)}
                              title={copy.deleteUserLabel(record.firstName, record.lastName)}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </AdminRowActions>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>

      <Modal
        isOpen={showCreateModal}
        onClose={closeModal}
        title={editingUser ? copy.editTitle : copy.createTitle}
        closeLabel={copy.closeDialog}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          {formError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {formError}
            </div>
          ) : null}

          <AdminFormField label={copy.emailLabel}>
            <Input
              type="email"
              value={formData.email}
              onChange={(e) => setFormData((current) => ({ ...current, email: e.target.value }))}
              disabled={Boolean(editingUser)}
              required
            />
          </AdminFormField>

          {!editingUser ? (
            <AdminFormField
              label={copy.temporaryPassword}
              description={copy.temporaryPasswordHint}
            >
              <Input
                type="password"
                value={formData.password}
                onChange={(e) => setFormData((current) => ({ ...current, password: e.target.value }))}
                required
              />
            </AdminFormField>
          ) : null}

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.firstName}>
              <Input
                type="text"
                value={formData.firstName}
                onChange={(e) => setFormData((current) => ({ ...current, firstName: e.target.value }))}
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.lastName}>
              <Input
                type="text"
                value={formData.lastName}
                onChange={(e) => setFormData((current) => ({ ...current, lastName: e.target.value }))}
                required
              />
            </AdminFormField>
          </div>

          <AdminFormField label={copy.roleLabel}>
            <select
              value={formData.role}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  role: event.target.value as ManagedRole,
                }))
              }
              className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
            >
              {(isSuperAdmin
                ? (['STUDENT', 'LECTURER', 'ADMIN', 'SUPER_ADMIN'] as ManagedRole[])
                : (['STUDENT', 'LECTURER', 'ADMIN'] as ManagedRole[])
              ).map((role) => (
                <option key={role} value={role}>
                  {copy.roles[role]}
                </option>
              ))}
            </select>
          </AdminFormField>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingUser
                  ? copy.editAction
                  : copy.createUser}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
