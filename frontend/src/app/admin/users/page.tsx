'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  Building2,
  CheckCircle2,
  ChevronDown,
  Eye,
  EyeOff,
  GraduationCap,
  Pencil,
  Plus,
  School,
  Search,
  ShieldCheck,
  Trash2,
  User,
  UserCheck,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { departmentsApi, usersApi } from '@/lib/api';
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
import { cn } from '@/lib/utils';
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

function isRecordSuperAdmin(record: UserRecord): boolean {
  const values = Array.isArray(record.roles) ? record.roles : record.roles?.split(',') ?? [];
  return values.some((role) => role.trim().toUpperCase() === 'SUPER_ADMIN');
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
  const searchParams = useSearchParams();

  const [users, setUsers] = useState<UserRecord[]>([]);
  const [departments, setDepartments] = useState<{ id: string; name: string; code?: string }[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [roleFilter, setRoleFilter] = useState<'ALL' | 'STUDENT' | 'LECTURER' | 'ADMIN'>('ALL');

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showTemporaryPassword, setShowTemporaryPassword] = useState(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState('');

  // 2-Column form data covering both general account & role-specific academic profiles
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    role: defaultRole as ManagedRole,
    // Student-specific fields
    studentId: '',
    year: '1',
    curriculumId: 'curriculum-demo',
    // Lecturer-specific fields
    employeeId: '',
    departmentId: '',
    academicTitle: 'TS.',
    specialization: '',
  });

  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // Sync role filter from URL query param if present
  useEffect(() => {
    const roleParam = searchParams.get('role');
    if (roleParam && ['STUDENT', 'LECTURER', 'ADMIN'].includes(roleParam.toUpperCase())) {
      setRoleFilter(roleParam.toUpperCase() as 'STUDENT' | 'LECTURER' | 'ADMIN');
    }
  }, [searchParams]);

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

  const fetchDepartments = useCallback(async () => {
    try {
      const res = await departmentsApi.getAll({ limit: 100 });
      if (res.data) {
        setDepartments(res.data);
      }
    } catch {
      // Departments fallback list already available
    }
  }, []);

  useEffect(() => {
    if (canAccess) {
      void fetchUsers();
      void fetchDepartments();
    }
  }, [canAccess, fetchUsers, fetchDepartments]);

  const filteredUsers = useMemo(() => {
    if (roleFilter === 'ALL') return users;
    return users.filter((u) => {
      const roles = Array.isArray(u.roles) ? u.roles : u.roles?.split(',') ?? [];
      return roles.some((r) => r.trim().toUpperCase() === roleFilter);
    });
  }, [users, roleFilter]);

  const pageSummary = useMemo(() => {
    if (filteredUsers.length === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [locale, page, totalPages, filteredUsers.length]);

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải quản lý người dùng',
          title: 'Quản lý người dùng',
          description:
            'Rà soát tài khoản campus, tạo bản ghi mới và giữ các hành động nhạy cảm sau bước xác nhận rõ ràng.',
          createUser: 'Tạo người dùng',
          createStudent: 'Thêm sinh viên',
          createLecturer: 'Thêm giảng viên',
          searchUsers: 'Tìm người dùng',
          searchPlaceholder: 'Tìm theo email hoặc tên',
          unavailableTitle: 'Hồ sơ người dùng chưa sẵn sàng',
          emptyTitle: 'Không có người dùng phù hợp',
          emptyDescription:
            'Hãy thử từ khóa khác hoặc tạo tài khoản campus mới.',
          tableTitle: 'Tài khoản campus',
          headers: {
            name: 'Họ và tên',
            email: 'Email trường cấp',
            status: 'Trạng thái',
            role: 'Vai trò',
            created: 'Ngày tạo',
            actions: 'Tác vụ',
          },
          deleteTitle: 'Xóa người dùng',
          deleteMessage: (firstName: string, lastName: string) =>
            `Xóa ${lastName} ${firstName}? Hành động này sẽ gỡ bản ghi tài khoản khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa người dùng',
          deleted: 'Đã xóa người dùng',
          deleteFailed: 'Hiện chưa thể xóa người dùng này.',
          updated: 'Đã cập nhật người dùng',
          createdStudent: 'Đã tạo tài khoản sinh viên thành công',
          createdLecturer: 'Đã tạo tài khoản giảng viên thành công',
          createdUser: 'Đã tạo người dùng thành công',
          saveFailed: 'Hiện chưa thể lưu hồ sơ người dùng.',
          editTitle: 'Chỉnh sửa tài khoản người dùng',
          createStudentTitle: 'Thêm Mới Tài Khoản Sinh Viên',
          createLecturerTitle: 'Thêm Mới Tài Khoản Giảng Viên',
          createAdminTitle: 'Thêm Mới Tài Khoản Quản Trị Viên',
          emailLabel: 'Địa chỉ Email trường cấp',
          temporaryPassword: 'Mật khẩu tạm thời',
          temporaryPasswordHint:
            'Mật khẩu tạm thời cấp cho người dùng lần đầu đăng nhập. Yêu cầu đổi lại sau khi đăng nhập.',
          firstName: 'Tên',
          lastName: 'Họ và tên đệm',
          roleLabel: 'Vai trò hệ thống',
          roles: {
            STUDENT: 'Sinh viên',
            LECTURER: 'Giảng viên',
            ADMIN: 'Quản trị viên',
            SUPER_ADMIN: 'Siêu quản trị viên',
          },
          saving: 'Đang lưu dữ liệu...',
          editAction: messages.common.actions.saveChanges,
          closeDialog: 'Đóng biểu mẫu người dùng',
          editUserLabel: (firstName: string, lastName: string) =>
            `Chỉnh sửa người dùng ${lastName} ${firstName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Xóa người dùng ${lastName} ${firstName}`,
        }
      : {
          loading: 'Loading user management',
          title: 'User management',
          description:
            'Review campus accounts, create new records, and keep sensitive actions behind explicit confirmation.',
          createUser: 'Create user',
          createStudent: 'Add Student',
          createLecturer: 'Add Lecturer',
          searchUsers: 'Search users',
          searchPlaceholder: 'Search by email or name',
          unavailableTitle: 'User records unavailable',
          emptyTitle: 'No matching users',
          emptyDescription:
            'Try another search term or create a new campus account.',
          tableTitle: 'Campus accounts',
          headers: {
            name: 'Full Name',
            email: 'Campus Email',
            status: 'Status',
            role: 'Role',
            created: 'Created Date',
            actions: 'Actions',
          },
          deleteTitle: 'Delete user',
          deleteMessage: (firstName: string, lastName: string) =>
            `Delete ${lastName} ${firstName}? This action removes the account record from the current admin view.`,
          deleteConfirm: 'Delete user',
          deleted: 'User deleted',
          deleteFailed: 'We could not delete that user.',
          updated: 'User updated',
          createdStudent: 'Student account created successfully',
          createdLecturer: 'Lecturer account created successfully',
          createdUser: 'User created successfully',
          saveFailed: 'The user record could not be saved.',
          editTitle: 'Edit user account',
          createStudentTitle: 'Add New Student Account',
          createLecturerTitle: 'Add New Lecturer Account',
          createAdminTitle: 'Add New Administrator Account',
          emailLabel: 'Campus Email Address',
          temporaryPassword: 'Temporary password',
          temporaryPasswordHint:
            'Temporary password for first-time sign in. User should rotate it.',
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
            `Edit user ${lastName} ${firstName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Delete user ${lastName} ${firstName}`,
        };

  const statusLabel = (status: string | null | undefined) =>
    messages.common.statuses[
      (status ?? 'UNKNOWN').toUpperCase() as keyof typeof messages.common.statuses
    ] ?? messages.common.statuses.UNKNOWN;

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = (role: ManagedRole = defaultRole) => {
    setEditingUser(null);
    setShowTemporaryPassword(false);
    setFormError('');
    const randomSuffix = Math.floor(100 + Math.random() * 900);
    setFormData({
      email: '',
      password: '',
      firstName: '',
      lastName: '',
      role,
      studentId: role === 'STUDENT' ? `24110${randomSuffix}` : '',
      year: '1',
      curriculumId: 'curriculum-demo',
      employeeId: role === 'LECTURER' ? `GV2026${randomSuffix}` : '',
      departmentId: departments[0]?.id || '',
      academicTitle: 'TS.',
      specialization: '',
    });
  };

  const openCreate = (targetRole: ManagedRole = 'STUDENT') => {
    resetForm(targetRole);
    setShowCreateModal(true);
  };

  const openEdit = (userRecord: UserRecord) => {
    if (!isSuperAdmin && isRecordSuperAdmin(userRecord)) {
      toast.error(
        locale === 'vi'
          ? 'Chỉ siêu quản trị viên mới có thể chỉnh sửa tài khoản siêu quản trị viên.'
          : 'Only super administrators can edit super administrator accounts.',
      );
      return;
    }
    setEditingUser(userRecord);
    setShowTemporaryPassword(false);
    setFormError('');
    setFormData({
      email: userRecord.email,
      password: '',
      firstName: userRecord.firstName,
      lastName: userRecord.lastName,
      role: primaryRole(userRecord.roles),
      studentId: '',
      year: '1',
      curriculumId: 'curriculum-demo',
      employeeId: '',
      departmentId: departments[0]?.id || '',
      academicTitle: 'TS.',
      specialization: '',
    });
    setShowCreateModal(true);
  };

  const closeModal = () => {
    setShowCreateModal(false);
    resetForm();
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearch(searchInput);
    setPage(1);
  };

  const handleDelete = async (userRecord: UserRecord) => {
    if (user && (user.id === userRecord.id || user.email === userRecord.email)) {
      toast.error(
        locale === 'vi'
          ? 'Không thể xóa tài khoản của chính mình.'
          : 'You cannot delete your own account.',
      );
      return;
    }

    if (!isSuperAdmin && isRecordSuperAdmin(userRecord)) {
      toast.error(
        locale === 'vi'
          ? 'Chỉ siêu quản trị viên mới có thể xóa tài khoản siêu quản trị viên.'
          : 'Only super administrators can delete super administrator accounts.',
      );
      return;
    }

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
      if (users.length === 1 && page > 1) {
        setPage((prev) => prev - 1);
      } else {
        await fetchUsers();
      }
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
        const isSelf = Boolean(user && (user.id === editingUser.id || user.email === editingUser.email));
        if (isSelf && formData.role !== primaryRole(editingUser.roles)) {
          const selfRoleMsg =
            locale === 'vi'
              ? 'Không thể tự thay đổi vai trò của chính mình.'
              : 'You cannot change your own system role.';
          setFormError(selfRoleMsg);
          toast.error(selfRoleMsg);
          setIsSaving(false);
          return;
        }

        await usersApi.update(editingUser.id, {
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        });
        toast.success(copy.updated);
      } else {
        const payload: Record<string, any> = {
          email: formData.email.trim(),
          password: formData.password,
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        };

        if (formData.role === 'STUDENT') {
          payload.studentId = formData.studentId.trim() || undefined;
          payload.year = formData.year;
          payload.curriculumId = formData.curriculumId || 'curriculum-demo';
        } else if (formData.role === 'LECTURER') {
          payload.employeeId = formData.employeeId.trim() || undefined;
          payload.departmentId = formData.departmentId || departments[0]?.id || 'department-demo';
        }

        await usersApi.create(payload);

        if (formData.role === 'STUDENT') {
          toast.success(copy.createdStudent);
        } else if (formData.role === 'LECTURER') {
          toast.success(copy.createdLecturer);
        } else {
          toast.success(copy.createdUser);
        }
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

  const modalTitle = editingUser
    ? copy.editTitle
    : formData.role === 'STUDENT'
      ? copy.createStudentTitle
      : formData.role === 'LECTURER'
        ? copy.createLecturerTitle
        : copy.createAdminTitle;

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button
            onClick={() => openCreate('STUDENT')}
            className="bg-primary hover:bg-primary/90 text-primary-foreground shadow-xs"
          >
            <GraduationCap className="mr-2 h-4 w-4" />
            {copy.createStudent}
          </Button>
          <Button
            onClick={() => openCreate('LECTURER')}
            variant="outline"
            className="border-emerald-600/40 text-emerald-700 dark:text-emerald-400 hover:bg-emerald-500/10"
          >
            <School className="mr-2 h-4 w-4" />
            {copy.createLecturer}
          </Button>
          <Button
            onClick={() => openCreate('ADMIN')}
            variant="ghost"
            size="sm"
            className="text-xs text-muted-foreground hover:text-foreground"
          >
            <Plus className="mr-1 h-3.5 w-3.5" />
            {copy.roles.ADMIN}
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
          <div className="flex flex-col gap-4">
            {/* Filter Tabs by Role */}
            <div className="flex flex-wrap items-center gap-2 border-b border-border/70 pb-3">
              <span className="text-xs font-semibold text-muted-foreground mr-1">
                Lọc theo phân loại:
              </span>
              <button
                type="button"
                onClick={() => setRoleFilter('ALL')}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'ALL'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                Tất cả ({users.length})
              </button>
              <button
                type="button"
                onClick={() => setRoleFilter('STUDENT')}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'STUDENT'
                    ? 'bg-blue-600 text-white shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <GraduationCap className="h-3.5 w-3.5" />
                Sinh viên
              </button>
              <button
                type="button"
                onClick={() => setRoleFilter('LECTURER')}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'LECTURER'
                    ? 'bg-emerald-600 text-white shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <School className="h-3.5 w-3.5" />
                Giảng viên
              </button>
              <button
                type="button"
                onClick={() => setRoleFilter('ADMIN')}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'ADMIN'
                    ? 'bg-purple-600 text-white shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <ShieldCheck className="h-3.5 w-3.5" />
                Quản trị viên
              </button>
            </div>

            <form onSubmit={handleSearch} className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div className="w-full max-w-xl">
                <label className="mb-2 block text-sm font-medium text-foreground">
                  {copy.searchUsers}
                </label>
                <Input
                  type="text"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
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
          </div>
        </AdminToolbarCard>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchUsers()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : filteredUsers.length === 0 ? (
          <EmptyState
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            icon={Users}
            action={
              <div className="flex gap-2">
                <Button onClick={() => openCreate('STUDENT')}>
                  <GraduationCap className="mr-2 h-4 w-4" />
                  {copy.createStudent}
                </Button>
                <Button onClick={() => openCreate('LECTURER')} variant="outline">
                  <School className="mr-2 h-4 w-4" />
                  {copy.createLecturer}
                </Button>
              </div>
            }
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
                previousLabel={locale === 'vi' ? 'Trang trước' : 'Previous'}
                nextLabel={locale === 'vi' ? 'Trang sau' : 'Next'}
              />
            }
          >
            <div className="divide-y divide-border/60 md:hidden">
              {filteredUsers.map((record) => (
                <article key={record.id} className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="font-semibold text-foreground">
                        {record.lastName} {record.firstName}
                      </h3>
                      <p className="text-xs text-muted-foreground">{record.email}</p>
                    </div>
                    <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                      {statusLabel(record.status)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span className="font-medium text-foreground">{roleLabel(record.roles)}</span>
                    <span>{formatDate(record.createdAt)}</span>
                  </div>
                  <AdminRowActions>
                    <Button size="sm" variant="outline" onClick={() => openEdit(record)}>
                      <Pencil className="mr-1.5 h-3.5 w-3.5" />
                      Sửa
                    </Button>
                  </AdminRowActions>
                </article>
              ))}
            </div>

            <AdminTableScroll className="hidden md:block">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="bg-secondary text-left text-muted-foreground">
                    <th className="px-4 py-3 font-medium">{copy.headers.name}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.email}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.status}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.role}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.created}</th>
                    <th className="px-4 py-3 text-right font-medium">{copy.headers.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {filteredUsers.map((record) => {
                    const primary = primaryRole(record.roles);
                    return (
                      <tr key={record.id} className="hover:bg-secondary/20 transition-colors">
                        <td className="px-4 py-3.5">
                          <div className="font-medium text-foreground">
                            {record.lastName} {record.firstName}
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-muted-foreground font-mono text-xs">
                          {record.email}
                        </td>
                        <td className="px-4 py-3.5">
                          <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                            {statusLabel(record.status)}
                          </span>
                        </td>
                        <td className="px-4 py-3.5">
                          {primary === 'STUDENT' ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-600 dark:text-blue-400">
                              <GraduationCap className="h-3 w-3" />
                              Sinh viên
                            </span>
                          ) : primary === 'LECTURER' ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
                              <School className="h-3 w-3" />
                              Giảng viên
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-md bg-purple-500/10 px-2 py-0.5 text-xs font-medium text-purple-600 dark:text-purple-400">
                              <ShieldCheck className="h-3 w-3" />
                              Quản trị viên
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-muted-foreground text-xs">
                          {formatDate(record.createdAt)}
                        </td>
                        <td className="px-4 py-3.5 text-right">
                          <AdminRowActions>
                            {(() => {
                              const isSelf = Boolean(user && (user.id === record.id || user.email === record.email));
                              const isTargetSuperAdmin = isRecordSuperAdmin(record);
                              const canManageTarget = isSuperAdmin || !isTargetSuperAdmin;
                              return (
                                <>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    onClick={() => openEdit(record)}
                                    disabled={!canManageTarget}
                                    title={copy.editUserLabel(record.firstName, record.lastName)}
                                  >
                                    <Pencil className="h-4 w-4" />
                                  </Button>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    className="text-destructive hover:bg-destructive/10 hover:text-destructive disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => void handleDelete(record)}
                                    disabled={isSelf || !canManageTarget}
                                    title={copy.deleteUserLabel(record.firstName, record.lastName)}
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </Button>
                                </>
                              );
                            })()}
                          </AdminRowActions>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>

      {/* 2-COLUMN MODAL: TÁCH BIỆT THÊM SINH VIÊN VỚI GIẢNG VIÊN */}
      <Modal
        isOpen={showCreateModal}
        onClose={closeModal}
        title={modalTitle}
        closeLabel={copy.closeDialog}
        className="max-w-4xl"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          {formError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {formError}
            </div>
          ) : null}

          {/* Quick role switcher inside modal if creating new */}
          {!editingUser && (
            <div className="rounded-xl border border-border/80 bg-secondary/20 p-3">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs font-semibold text-foreground">Phân loại đối tượng tạo mới:</span>
                  <p className="text-xs text-muted-foreground">
                    Chọn loại tài khoản để hệ thống tải hồ sơ học vụ chuyên biệt tương ứng
                  </p>
                </div>
                <div className="flex items-center gap-1.5 rounded-lg border border-border bg-background p-1">
                  <button
                    type="button"
                    onClick={() => {
                      setFormData((c) => ({
                        ...c,
                        role: 'STUDENT',
                        studentId: c.studentId || `24110${Math.floor(100 + Math.random() * 900)}`,
                      }));
                    }}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'STUDENT'
                        ? 'bg-blue-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <GraduationCap className="h-3.5 w-3.5" />
                    Sinh viên
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setFormData((c) => ({
                        ...c,
                        role: 'LECTURER',
                        employeeId: c.employeeId || `GV2026${Math.floor(100 + Math.random() * 900)}`,
                      }));
                    }}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'LECTURER'
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <School className="h-3.5 w-3.5" />
                    Giảng viên
                  </button>
                  <button
                    type="button"
                    onClick={() => setFormData((c) => ({ ...c, role: 'ADMIN' }))}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'ADMIN'
                        ? 'bg-purple-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <ShieldCheck className="h-3.5 w-3.5" />
                    Quản trị
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* 2-COLUMN FORM GRID */}
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {/* CỘT 1: THÔNG TIN TÀI KHOẢN & ĐĂNG NHẬP */}
            <div className="space-y-4 rounded-xl border border-border/80 bg-card p-4">
              <div className="flex items-center gap-2 border-b border-border/70 pb-3">
                <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary/10 text-primary">
                  <User className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-foreground">
                    1. Thông Tin Tài Khoản & Đăng Nhập
                  </h4>
                  <p className="text-xs text-muted-foreground">
                    Định danh đăng nhập và bảo mật tài khoản
                  </p>
                </div>
              </div>

              <AdminFormField label={copy.emailLabel}>
                <Input
                  type="email"
                  value={formData.email}
                  placeholder={
                    formData.role === 'STUDENT'
                      ? 'sv.nguyenvana@campuscore.demo'
                      : formData.role === 'LECTURER'
                        ? 'gv.tranvanb@campuscore.demo'
                        : 'admin.hoangdung@campuscore.demo'
                  }
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
                  <div className="relative">
                    <Input
                      type={showTemporaryPassword ? 'text' : 'password'}
                      value={formData.password}
                      placeholder="Nhập mật khẩu ban đầu..."
                      onChange={(e) => setFormData((current) => ({ ...current, password: e.target.value }))}
                      className="pr-12"
                      required
                    />
                    <button
                      type="button"
                      className="absolute right-2 top-[22px] inline-flex h-9 w-9 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition hover:bg-secondary hover:text-foreground"
                      onClick={() => setShowTemporaryPassword((current) => !current)}
                      aria-label={showTemporaryPassword ? messages.login.hidePassword : messages.login.showPassword}
                    >
                      {showTemporaryPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </AdminFormField>
              ) : null}

              <div className="grid grid-cols-2 gap-3">
                <AdminFormField label={copy.lastName}>
                  <Input
                    type="text"
                    value={formData.lastName}
                    placeholder="Nguyễn Văn"
                    onChange={(e) => setFormData((current) => ({ ...current, lastName: e.target.value }))}
                    required
                  />
                </AdminFormField>
                <AdminFormField label={copy.firstName}>
                  <Input
                    type="text"
                    value={formData.firstName}
                    placeholder="An"
                    onChange={(e) => setFormData((current) => ({ ...current, firstName: e.target.value }))}
                    required
                  />
                </AdminFormField>
              </div>

              <div className="rounded-lg border border-border/60 bg-secondary/15 p-3 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Trạng thái tài khoản ban đầu:</span>
                  <span className="inline-flex items-center gap-1 rounded-full bg-emerald-500/10 px-2 py-0.5 font-semibold text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 className="h-3 w-3" />
                    Đang hoạt động (ACTIVE)
                  </span>
                </div>
              </div>
            </div>

            {/* CỘT 2: HỒ SƠ HỌC VỤ CHUYÊN BIỆT THEO VAI TRÒ */}
            <div className="space-y-4 rounded-xl border border-border/80 bg-card p-4">
              {/* Header Cột 2 */}
              <div className="flex items-center justify-between border-b border-border/70 pb-3">
                <div className="flex items-center gap-2">
                  <div className={cn(
                    'flex h-7 w-7 items-center justify-center rounded-md',
                    formData.role === 'STUDENT'
                      ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                      : formData.role === 'LECTURER'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                        : 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
                  )}>
                    {formData.role === 'STUDENT' ? (
                      <GraduationCap className="h-4 w-4" />
                    ) : formData.role === 'LECTURER' ? (
                      <School className="h-4 w-4" />
                    ) : (
                      <ShieldCheck className="h-4 w-4" />
                    )}
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-foreground">
                      2. Hồ Sơ Phân Công Học Vụ
                    </h4>
                    <p className="text-xs text-muted-foreground">
                      {formData.role === 'STUDENT'
                        ? 'Thông tin hồ sơ đào tạo sinh viên chính quy'
                        : formData.role === 'LECTURER'
                          ? 'Thông tin học hàm và bộ môn giảng dạy'
                          : 'Phân quyền quản trị hệ thống'}
                    </p>
                  </div>
                </div>

                <span className={cn(
                  'rounded-full px-2.5 py-0.5 text-xs font-semibold',
                  formData.role === 'STUDENT'
                    ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                    : formData.role === 'LECTURER'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                      : 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
                )}>
                  {formData.role === 'STUDENT'
                    ? 'Sinh viên'
                    : formData.role === 'LECTURER'
                      ? 'Giảng viên'
                      : 'Quản trị viên'}
                </span>
              </div>

              {/* TRƯỜNG HỢP: SINH VIÊN */}
              {formData.role === 'STUDENT' && (
                <div className="space-y-3.5">
                  <AdminFormField
                    label="Mã số sinh viên (MSSV) *"
                    description="Mã định danh sinh viên dùng tra cứu điểm, ĐRL và ĐKHP"
                  >
                    <Input
                      type="text"
                      value={formData.studentId}
                      placeholder="ví dụ: 24110054"
                      onChange={(e) => setFormData((c) => ({ ...c, studentId: e.target.value }))}
                      required
                    />
                  </AdminFormField>

                  <div className="grid grid-cols-2 gap-3">
                    <AdminFormField label="Khóa đào tạo *">
                      <select
                        value={formData.year}
                        onChange={(e) => setFormData((c) => ({ ...c, year: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="1">Năm 1 (Khóa K2026)</option>
                        <option value="2">Năm 2 (Khóa K2025)</option>
                        <option value="3">Năm 3 (Khóa K2024)</option>
                        <option value="4">Năm 4 (Khóa K2023)</option>
                      </select>
                    </AdminFormField>

                    <AdminFormField label="Khung chương trình *">
                      <select
                        value={formData.curriculumId}
                        onChange={(e) => setFormData((c) => ({ ...c, curriculumId: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="curriculum-demo">Kỹ sư Chuẩn (140 TC)</option>
                        <option value="curriculum-clc">Chất lượng cao (150 TC)</option>
                      </select>
                    </AdminFormField>
                  </div>

                  <AdminFormField label="Khoa / Viện đào tạo *">
                    <select
                      value={formData.departmentId}
                      onChange={(e) => setFormData((c) => ({ ...c, departmentId: e.target.value }))}
                      className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      <option value="">-- Chọn Khoa / Viện đào tạo --</option>
                      {departments.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name} {d.code ? `(${d.code})` : ''}
                        </option>
                      ))}
                    </select>
                  </AdminFormField>
                </div>
              )}

              {/* TRƯỜNG HỢP: GIẢNG VIÊN */}
              {formData.role === 'LECTURER' && (
                <div className="space-y-3.5">
                  <AdminFormField
                    label="Mã số Giảng viên (MSGV) *"
                    description="Mã định danh cán bộ giảng dạy và chấm thi luận văn"
                  >
                    <Input
                      type="text"
                      value={formData.employeeId}
                      placeholder="ví dụ: GV2026001 hoặc LEC-DEMO-001"
                      onChange={(e) => setFormData((c) => ({ ...c, employeeId: e.target.value }))}
                      required
                    />
                  </AdminFormField>

                  <div className="grid grid-cols-2 gap-3">
                    <AdminFormField label="Học hàm / Học vị *">
                      <select
                        value={formData.academicTitle}
                        onChange={(e) => setFormData((c) => ({ ...c, academicTitle: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="PGS.TS.">PGS.TS. (Phó Giáo sư - Tiến sĩ)</option>
                        <option value="GS.TS.">GS.TS. (Giáo sư - Tiến sĩ)</option>
                        <option value="TS.">TS. (Tiến sĩ)</option>
                        <option value="ThS.">ThS. (Thạc sĩ)</option>
                        <option value="KS.">KS. / Kỹ sư chính</option>
                      </select>
                    </AdminFormField>

                    <AdminFormField label="Khoa / Bộ môn công tác *">
                      <select
                        value={formData.departmentId}
                        onChange={(e) => setFormData((c) => ({ ...c, departmentId: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="">-- Chọn Khoa / Bộ môn --</option>
                        {departments.map((d) => (
                          <option key={d.id} value={d.id}>
                            {d.name} {d.code ? `(${d.code})` : ''}
                          </option>
                        ))}
                      </select>
                    </AdminFormField>
                  </div>

                  <AdminFormField
                    label="Lĩnh vực chuyên môn / Hướng nghiên cứu"
                    description="Ví dụ: Trí tuệ nhân tạo, Hệ thống nhúng, Kỹ thuật phần mềm"
                  >
                    <Input
                      type="text"
                      value={formData.specialization}
                      placeholder="ví dụ: Kỹ thuật phần mềm & AI"
                      onChange={(e) => setFormData((c) => ({ ...c, specialization: e.target.value }))}
                    />
                  </AdminFormField>
                </div>
              )}

              {/* TRƯỜNG HỢP: QUẢN TRỊ VIÊN */}
              {formData.role !== 'STUDENT' && formData.role !== 'LECTURER' && (
                <div className="space-y-4">
                  <div className="rounded-xl border border-purple-500/20 bg-purple-500/5 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-purple-700 dark:text-purple-400">
                      <ShieldCheck className="h-5 w-5" />
                      <span className="text-sm font-semibold">Phân quyền Quản Trị Hệ Thống</span>
                    </div>
                    <p className="text-xs text-muted-foreground leading-5">
                      Tài khoản có toàn quyền truy cập phân hệ Quản trị viện, quản lý danh mục người dùng, giảng viên, môn học, lớp học phần, bảng tin thông báo và cấu hình trường học.
                    </p>
                  </div>

                  {(() => {
                    const isSelfEditing = Boolean(editingUser && user && (user.id === editingUser.id || user.email === editingUser.email));
                    return (
                      <AdminFormField
                        label={copy.roleLabel}
                        description={
                          isSelfEditing
                            ? (locale === 'vi'
                                ? 'Không thể tự thay đổi vai trò tài khoản của chính mình.'
                                : 'You cannot change your own system role.')
                            : undefined
                        }
                      >
                        <select
                          value={formData.role}
                          disabled={isSelfEditing}
                          onChange={(e) => setFormData((c) => ({ ...c, role: e.target.value as ManagedRole }))}
                          className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
                        >
                          <option value="ADMIN">{copy.roles.ADMIN}</option>
                          {isSuperAdmin && <option value="SUPER_ADMIN">{copy.roles.SUPER_ADMIN}</option>}
                          <option value="STUDENT">{copy.roles.STUDENT}</option>
                          <option value="LECTURER">{copy.roles.LECTURER}</option>
                        </select>
                      </AdminFormField>
                    );
                  })()}
                </div>
              )}
            </div>
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingUser
                  ? copy.editAction
                  : formData.role === 'STUDENT'
                    ? '+ Tạo Tài Khoản Sinh Viên'
                    : formData.role === 'LECTURER'
                      ? '+ Tạo Tài Khoản Giảng Viên'
                      : copy.createUser}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
