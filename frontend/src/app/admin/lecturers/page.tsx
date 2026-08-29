'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { GraduationCap, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { departmentsApi, lecturersApi } from '@/lib/api';
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
import { Select } from '@/components/ui/select';
import { statusToneClass } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { getLocalizedName } from '@/lib/academic-content';
import { ACADEMIC_REFERENCE_LIMIT } from '@/lib/reference-data';

interface Lecturer {
  id: string;
  employeeId: string;
  userId: string;
  departmentId: string;
  specialization?: string;
  isActive: boolean;
  user?: { firstName: string; lastName: string; email: string };
  department?: { name: string; nameEn?: string; nameVi?: string };
}

interface Department {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
}

export default function AdminLecturersPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, messages } = useI18n();
  const router = useRouter();
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [referenceError, setReferenceError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingLecturer, setEditingLecturer] = useState<Lecturer | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    employeeId: '',
    userId: '',
    departmentId: '',
    specialization: '',
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

  const fetchDepartments = useCallback(async () => {
    setReferenceError('');
    try {
      const response = await departmentsApi.getAll({ limit: ACADEMIC_REFERENCE_LIMIT });
      setDepartments(response.data || []);
    } catch {
      setReferenceError(
        locale === 'vi'
          ? 'Hiện chưa thể tải dữ liệu khoa cho biểu mẫu giảng viên.'
          : 'Department options could not be loaded.',
      );
    }
  }, [locale]);

  const fetchLecturers = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await lecturersApi.getAll({ page, limit: 20 });
      const filteredLecturers = search
        ? response.data.filter((lecturer: Lecturer) => {
            const fullName = `${lecturer.user?.firstName || ''} ${lecturer.user?.lastName || ''}`
              .trim()
              .toLowerCase();
            const query = search.toLowerCase();

            return (
              lecturer.employeeId.toLowerCase().includes(query) ||
              lecturer.user?.email?.toLowerCase().includes(query) ||
              fullName.includes(query)
            );
          })
        : response.data;

      setLecturers(filteredLecturers);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải danh sách giảng viên.'
          : 'Lecturers could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search]);

  const copy = useMemo(
    () =>
      locale === 'vi'
        ? {
          loading: 'Đang tải giảng viên',
          title: 'Giảng viên',
          description:
            'Giữ hồ sơ giảng dạy gắn đúng người, đúng khoa và đúng metadata học thuật.',
          create: 'Tạo giảng viên',
          searchLabel: 'Tìm giảng viên',
          searchPlaceholder: 'Tìm theo tên, email hoặc mã nhân sự',
          pageSummaryEmpty: 'Không có bản ghi phù hợp',
          pageSummary: (currentPage: number, pages: number) =>
            `Trang ${currentPage} / ${pages}`,
          selectDepartment: 'Chọn khoa',
          unavailableTitle: 'Giảng viên chưa sẵn sàng',
          emptyTitle: 'Không có giảng viên phù hợp',
          emptyDescription:
            'Hãy tạo hồ sơ giảng viên để section, lịch dạy và ownership chấm điểm luôn khớp nhau.',
          tableTitle: 'Bản ghi giảng viên',
          headers: {
            employeeId: 'Mã nhân sự',
            lecturer: 'Giảng viên',
            email: 'Email',
            department: 'Khoa',
            specialization: 'Chuyên môn',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          unlinkedAccount: 'Chưa liên kết tài khoản',
          noEmail: 'Chưa có email',
          unassigned: 'Chưa gán',
          notProvided: 'Chưa cập nhật',
          active: 'Đang hoạt động',
          inactive: 'Ngừng hoạt động',
          deleteTitle: 'Xóa giảng viên',
          deleteMessage: (employeeId: string) =>
            `Xóa ${employeeId}? Hành động này sẽ gỡ giảng viên khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa giảng viên',
          deleted: 'Đã xóa giảng viên',
          deleteFailed: 'Hiện chưa thể xóa giảng viên này.',
          updated: 'Đã cập nhật giảng viên',
          created: 'Đã tạo giảng viên',
          saveFailed: 'Hiện chưa thể lưu hồ sơ giảng viên.',
          editTitle: 'Chỉnh sửa giảng viên',
          createTitle: 'Tạo giảng viên',
          fields: {
            employeeId: 'Mã nhân sự',
            userId: 'ID tài khoản liên kết',
            userIdPlaceholder: 'ID tài khoản người dùng hiện có',
            department: 'Khoa',
            specialization: 'Chuyên môn',
            specializationPlaceholder: 'Tùy chọn: môn hoặc lĩnh vực phụ trách',
          },
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (employeeId: string) => `Chỉnh sửa giảng viên ${employeeId}`,
          deleteLabel: (employeeId: string) => `Xóa giảng viên ${employeeId}`,
        }
        : {
          loading: 'Loading lecturers',
          title: 'Lecturers',
          description:
            'Keep teaching assignments tied to the right people, departments, and profile metadata.',
          create: 'Create lecturer',
          searchLabel: 'Search lecturers',
          searchPlaceholder: 'Search by name, email, or employee ID',
          pageSummaryEmpty: 'No matching records',
          pageSummary: (currentPage: number, pages: number) =>
            `Page ${currentPage} of ${pages}`,
          selectDepartment: 'Select department',
          unavailableTitle: 'Lecturers unavailable',
          emptyTitle: 'No matching lecturers',
          emptyDescription:
            'Create a lecturer profile to keep schedules, sections, and grading ownership aligned.',
          tableTitle: 'Lecturer records',
          headers: {
            employeeId: 'Employee ID',
            lecturer: 'Lecturer',
            email: 'Email',
            department: 'Department',
            specialization: 'Specialization',
            status: 'Status',
            actions: 'Actions',
          },
          unlinkedAccount: 'Unlinked account',
          noEmail: 'No email',
          unassigned: 'Unassigned',
          notProvided: 'Not provided',
          active: 'Active',
          inactive: 'Inactive',
          deleteTitle: 'Delete lecturer',
          deleteMessage: (employeeId: string) =>
            `Delete ${employeeId}? This removes the lecturer from the current admin view.`,
          deleteConfirm: 'Delete lecturer',
          deleted: 'Lecturer deleted',
          deleteFailed: 'We could not delete that lecturer.',
          updated: 'Lecturer updated',
          created: 'Lecturer created',
          saveFailed: 'The lecturer profile could not be saved.',
          editTitle: 'Edit lecturer',
          createTitle: 'Create lecturer',
          fields: {
            employeeId: 'Employee ID',
            userId: 'Linked user ID',
            userIdPlaceholder: 'Existing user account ID',
            department: 'Department',
            specialization: 'Specialization',
            specializationPlaceholder: 'Optional subject or field focus',
          },
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (employeeId: string) => `Edit lecturer ${employeeId}`,
          deleteLabel: (employeeId: string) => `Delete lecturer ${employeeId}`,
        },
    [locale, messages.common.actions.saveChanges],
  );

  useEffect(() => {
    if (canAccess) {
      void fetchDepartments();
      void fetchLecturers();
    }
  }, [canAccess, fetchDepartments, fetchLecturers]);

  const pageSummary = useMemo(() => {
    if (lecturers.length === 0) {
      return copy.pageSummaryEmpty;
    }

    return copy.pageSummary(page, totalPages);
  }, [copy, lecturers.length, page, totalPages]);

  const departmentOptions = useMemo(
    () => [
      { value: '', label: copy.selectDepartment },
      ...departments.map((department) => ({
        value: department.id,
        label: getLocalizedName(locale, department, department.name),
      })),
    ],
    [copy.selectDepartment, departments, locale],
  );

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingLecturer(null);
    setFormData({
      employeeId: '',
      userId: '',
      departmentId: '',
      specialization: '',
    });
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = (lecturer: Lecturer) => {
    setEditingLecturer(lecturer);
    setFormData({
      employeeId: lecturer.employeeId,
      userId: lecturer.userId,
      departmentId: lecturer.departmentId,
      specialization: lecturer.specialization || '',
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    resetForm();
  };

  const handleSearch = async (event: React.FormEvent) => {
    event.preventDefault();
    setPage(1);
    await fetchLecturers();
  };

  const handleDelete = async (lecturer: Lecturer) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(lecturer.employeeId),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await lecturersApi.delete(lecturer.id);
      toast.success(copy.deleted);
      await fetchLecturers();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      if (editingLecturer) {
        await lecturersApi.update(editingLecturer.id, formData);
        toast.success(copy.updated);
      } else {
        await lecturersApi.create(formData);
        toast.success(copy.created);
      }

      closeModal();
      await fetchLecturers();
    } catch (requestError: any) {
      toast.error(
        requestError.response?.data?.message ?? copy.saveFailed,
      );
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
          {copy.create}
        </Button>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
            <form
              onSubmit={handleSearch}
              className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between"
            >
              <div className="w-full max-w-xl">
                <label className="mb-2 block text-sm font-medium text-foreground">
                  {copy.searchLabel}
                </label>
                <Input
                  type="text"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
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

        {referenceError ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={referenceError}
            onRetry={() => void fetchDepartments()}
          />
        ) : null}

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchLecturers()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : lecturers.length === 0 ? (
          <EmptyState
            icon={GraduationCap}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            action={<Button onClick={openCreate}>{copy.create}</Button>}
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
                {lecturers.map((lecturer) => {
                  const lecturerLabel = lecturer.user
                    ? `${lecturer.user.firstName} ${lecturer.user.lastName}`
                    : copy.unlinkedAccount;
                  const departmentLabel = getLocalizedName(
                    locale,
                    lecturer.department,
                    lecturer.department?.name || copy.unassigned,
                  );

                  return (
                    <article
                      key={`${lecturer.id}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {lecturer.employeeId}
                          </p>
                          <h3 className="mt-1 font-semibold text-foreground">{lecturerLabel}</h3>
                          <p className="mt-1 break-all text-sm text-muted-foreground">
                            {lecturer.user?.email || copy.noEmail}
                          </p>
                        </div>
                        <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(lecturer.isActive ? 'success' : 'neutral')}`}>
                          {lecturer.isActive ? copy.active : copy.inactive}
                        </span>
                      </div>
                      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.department}
                          </dt>
                          <dd className="mt-1 text-foreground">{departmentLabel}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.specialization}
                          </dt>
                          <dd className="mt-1 text-foreground">
                            {lecturer.specialization || copy.notProvided}
                          </dd>
                        </div>
                      </dl>
                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => openEdit(lecturer)}
                          aria-label={copy.editLabel(lecturer.employeeId)}
                          title={copy.editLabel(lecturer.employeeId)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => void handleDelete(lecturer)}
                          aria-label={copy.deleteLabel(lecturer.employeeId)}
                          title={copy.deleteLabel(lecturer.employeeId)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </AdminRowActions>
                    </article>
                  );
                })}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[860px] text-sm">
                  <thead>
                    <tr className="bg-secondary text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.employeeId}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.lecturer}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.email}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.department}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.specialization}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {lecturers.map((lecturer) => (
                      <tr key={lecturer.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {lecturer.employeeId}
                        </td>
                        <td className="px-2 py-4 text-foreground">
                          {lecturer.user
                            ? `${lecturer.user.firstName} ${lecturer.user.lastName}`
                            : copy.unlinkedAccount}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {lecturer.user?.email || copy.noEmail}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {getLocalizedName(
                            locale,
                            lecturer.department,
                            lecturer.department?.name || copy.unassigned,
                          )}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {lecturer.specialization || copy.notProvided}
                        </td>
                        <td className="px-2 py-4">
                          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(lecturer.isActive ? 'success' : 'neutral')}`}>
                            {lecturer.isActive ? copy.active : copy.inactive}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(lecturer)}
                              aria-label={copy.editLabel(lecturer.employeeId)}
                              title={copy.editLabel(lecturer.employeeId)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(lecturer)}
                              aria-label={copy.deleteLabel(lecturer.employeeId)}
                              title={copy.deleteLabel(lecturer.employeeId)}
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
        isOpen={isModalOpen}
        onClose={closeModal}
        title={editingLecturer ? copy.editTitle : copy.createTitle}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.fields.employeeId}>
              <Input
                type="text"
                value={formData.employeeId}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    employeeId: event.target.value,
                  }))
                }
                disabled={Boolean(editingLecturer)}
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.fields.userId}>
              <Input
                type="text"
                value={formData.userId}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    userId: event.target.value,
                  }))
                }
                disabled={Boolean(editingLecturer)}
                placeholder={copy.fields.userIdPlaceholder}
                required
              />
            </AdminFormField>
          </div>

          <Select
            label={copy.fields.department}
            value={formData.departmentId}
            onChange={(event) =>
              setFormData((current) => ({
                ...current,
                departmentId: event.target.value,
              }))
            }
            options={departmentOptions}
            required
          />

          <AdminFormField label={copy.fields.specialization}>
            <Input
              type="text"
              value={formData.specialization}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  specialization: event.target.value,
                }))
              }
              placeholder={copy.fields.specializationPlaceholder}
            />
          </AdminFormField>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingLecturer
                  ? copy.editAction
                  : copy.create}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
