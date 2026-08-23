'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Building2, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { departmentsApi } from '@/lib/api';
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
import { Textarea } from '@/components/ui/textarea';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { toast } from 'sonner';
import { useI18n } from '@/i18n';
import {
  getLocalizedDescription,
  getLocalizedName,
} from '@/lib/academic-content';

interface Department {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  code: string;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  isActive: boolean;
}

export default function AdminDepartmentsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, messages } = useI18n();
  const router = useRouter();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDepartment, setEditingDepartment] = useState<Department | null>(
    null,
  );
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    nameEn: '',
    nameVi: '',
    code: '',
    descriptionEn: '',
    descriptionVi: '',
  });
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchDepartments = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await departmentsApi.getAll({ page, limit: 20 });
      const filteredDepartments = search
        ? response.data.filter(
            (department) =>
              department.name.toLowerCase().includes(search.toLowerCase()) ||
              department.nameEn?.toLowerCase().includes(search.toLowerCase()) ||
              department.nameVi?.toLowerCase().includes(search.toLowerCase()) ||
              department.code.toLowerCase().includes(search.toLowerCase()),
          )
        : response.data;

      setDepartments(filteredDepartments);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải danh sách khoa.'
          : 'Departments could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search]);

  const copy = useMemo(
    () =>
      locale === 'vi'
        ? {
          loading: 'Đang tải khoa',
          title: 'Khoa',
          description:
            'Giữ cấu trúc học thuật rõ ràng để môn học, giảng viên và chương trình cùng bám một ownership.',
          create: 'Tạo khoa',
          searchLabel: 'Tìm khoa',
          searchPlaceholder: 'Tìm theo tên hoặc mã khoa',
          pageSummaryEmpty: 'Không có bản ghi phù hợp',
          pageSummary: (currentPage: number, pages: number) =>
            `Trang ${currentPage} / ${pages}`,
          unavailableTitle: 'Khoa chưa sẵn sàng',
          emptyTitle: 'Không có khoa phù hợp',
          emptyDescription:
            'Hãy tạo khoa để môn học, giảng viên và catalog học thuật cùng bám vào một cấu trúc rõ ràng.',
          tableTitle: 'Bản ghi khoa',
          headers: {
            code: 'Mã',
            name: 'Tên khoa',
            description: 'Mô tả',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          noDescription: 'Chưa có mô tả',
          active: 'Đang hoạt động',
          inactive: 'Ngừng hoạt động',
          deleteTitle: 'Xóa khoa',
          deleteMessage: (name: string) =>
            `Xóa ${name}? Hành động này sẽ gỡ khoa khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa khoa',
          deleted: 'Đã xóa khoa',
          deleteFailed: 'Hiện chưa thể xóa khoa này.',
          updated: 'Đã cập nhật khoa',
          created: 'Đã tạo khoa',
          saveFailed: 'Hiện chưa thể lưu khoa.',
          editTitle: 'Chỉnh sửa khoa',
          createTitle: 'Tạo khoa',
          fields: {
            code: 'Mã khoa',
            name: 'Tên khoa',
            description: 'Mô tả',
          },
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (name: string) => `Chỉnh sửa khoa ${name}`,
          deleteLabel: (name: string) => `Xóa khoa ${name}`,
        }
        : {
          loading: 'Loading departments',
          title: 'Departments',
          description:
            'Keep the academic structure readable for courses, lecturers, and curriculum ownership.',
          create: 'Create department',
          searchLabel: 'Search departments',
          searchPlaceholder: 'Search by name or code',
          pageSummaryEmpty: 'No matching records',
          pageSummary: (currentPage: number, pages: number) =>
            `Page ${currentPage} of ${pages}`,
          unavailableTitle: 'Departments unavailable',
          emptyTitle: 'No matching departments',
          emptyDescription:
            'Create a department to anchor courses, lecturer assignments, and catalog ownership.',
          tableTitle: 'Department records',
          headers: {
            code: 'Code',
            name: 'Name',
            description: 'Description',
            status: 'Status',
            actions: 'Actions',
          },
          noDescription: 'No description',
          active: 'Active',
          inactive: 'Inactive',
          deleteTitle: 'Delete department',
          deleteMessage: (name: string) =>
            `Delete ${name}? This removes the department from the current admin view.`,
          deleteConfirm: 'Delete department',
          deleted: 'Department deleted',
          deleteFailed: 'We could not delete that department.',
          updated: 'Department updated',
          created: 'Department created',
          saveFailed: 'The department could not be saved.',
          editTitle: 'Edit department',
          createTitle: 'Create department',
          fields: {
            code: 'Code',
            name: 'Name',
            description: 'Description',
          },
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (name: string) => `Edit department ${name}`,
          deleteLabel: (name: string) => `Delete department ${name}`,
        },
    [locale, messages.common.actions.saveChanges],
  );

  useEffect(() => {
    if (canAccess) {
      void fetchDepartments();
    }
  }, [canAccess, fetchDepartments]);

  const pageSummary = useMemo(() => {
    if (departments.length === 0) {
      return copy.pageSummaryEmpty;
    }

    return copy.pageSummary(page, totalPages);
  }, [copy, departments.length, page, totalPages]);

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingDepartment(null);
    setFormData({
      nameEn: '',
      nameVi: '',
      code: '',
      descriptionEn: '',
      descriptionVi: '',
    });
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = (department: Department) => {
    setEditingDepartment(department);
    setFormData({
      nameEn: department.nameEn || department.name,
      nameVi: department.nameVi || '',
      code: department.code,
      descriptionEn: department.descriptionEn || department.description || '',
      descriptionVi: department.descriptionVi || '',
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
    await fetchDepartments();
  };

  const handleDelete = async (department: Department) => {
    const departmentLabel = getLocalizedName(
      locale,
      department,
      department.name,
    );
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(departmentLabel),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await departmentsApi.delete(department.id);
      toast.success(copy.deleted);
      await fetchDepartments();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      const payload = {
        ...formData,
        name: formData.nameEn || formData.nameVi,
        description: formData.descriptionEn || formData.descriptionVi,
        descriptionEn: formData.descriptionEn,
        descriptionVi: formData.descriptionVi,
      };
      if (editingDepartment) {
        await departmentsApi.update(editingDepartment.id, payload);
        toast.success(copy.updated);
      } else {
        await departmentsApi.create(payload);
        toast.success(copy.created);
      }

      closeModal();
      await fetchDepartments();
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

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchDepartments()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : departments.length === 0 ? (
          <EmptyState
            icon={Building2}
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
                {departments.map((department) => {
                  const departmentLabel = getLocalizedName(locale, department, department.name);
                  const departmentDescription = getLocalizedDescription(
                    locale,
                    department,
                    copy.noDescription,
                  );

                  return (
                    <article
                      key={`${department.id}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {department.code}
                          </p>
                          <h3 className="mt-1 font-semibold text-foreground">{departmentLabel}</h3>
                        </div>
                        <span className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                          {department.isActive ? copy.active : copy.inactive}
                        </span>
                      </div>
                      <p className="mt-4 border-t border-border/60 pt-3 text-sm leading-6 text-muted-foreground">
                        {departmentDescription}
                      </p>
                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => openEdit(department)}
                          aria-label={copy.editLabel(departmentLabel)}
                          title={copy.editLabel(departmentLabel)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => void handleDelete(department)}
                          aria-label={copy.deleteLabel(departmentLabel)}
                          title={copy.deleteLabel(departmentLabel)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </AdminRowActions>
                    </article>
                  );
                })}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[720px] text-sm">
                  <thead>
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.code}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.name}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.description}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {departments.map((department) => {
                      const departmentLabel = getLocalizedName(
                        locale,
                        department,
                        department.name,
                      );
                      const departmentDescription = getLocalizedDescription(
                        locale,
                        department,
                        copy.noDescription,
                      );

                      return (
                      <tr key={department.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {department.code}
                        </td>
                        <td className="px-2 py-4 text-foreground">
                          <div className="space-y-1">
                            <div>{departmentLabel}</div>
                            {department.nameEn && department.nameVi ? (
                              <div className="text-xs text-muted-foreground">
                                {locale === 'vi'
                                  ? department.nameEn
                                  : department.nameVi}
                              </div>
                            ) : null}
                          </div>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {departmentDescription}
                        </td>
                        <td className="px-2 py-4">
                          <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {department.isActive ? copy.active : copy.inactive}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(department)}
                              aria-label={copy.editLabel(departmentLabel)}
                              title={copy.editLabel(departmentLabel)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(department)}
                              aria-label={copy.deleteLabel(departmentLabel)}
                              title={copy.deleteLabel(departmentLabel)}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
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

      <Modal
        isOpen={isModalOpen}
        onClose={closeModal}
        title={editingDepartment ? copy.editTitle : copy.createTitle}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.fields.code}>
              <Input
                type="text"
                value={formData.code}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    code: event.target.value,
                  }))
                }
                disabled={Boolean(editingDepartment)}
                required
              />
            </AdminFormField>
            <AdminFormField label={locale === 'vi' ? 'Tên tiếng Anh' : 'English name'}>
              <Input
                type="text"
                value={formData.nameEn}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    nameEn: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
            <AdminFormField
              label={locale === 'vi' ? 'Tên tiếng Việt' : 'Vietnamese name'}
            >
              <Input
                type="text"
                value={formData.nameVi}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    nameVi: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField
              label={locale === 'vi' ? 'Mô tả tiếng Anh' : 'English description'}
            >
              <Textarea
                value={formData.descriptionEn}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    descriptionEn: event.target.value,
                  }))
                }
                rows={4}
              />
            </AdminFormField>
            <AdminFormField
              label={locale === 'vi' ? 'Mô tả tiếng Việt' : 'Vietnamese description'}
            >
              <Textarea
                value={formData.descriptionVi}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    descriptionVi: event.target.value,
                  }))
                }
                rows={4}
              />
            </AdminFormField>
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingDepartment
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
