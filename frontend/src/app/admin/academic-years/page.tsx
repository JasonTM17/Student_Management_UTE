'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { CalendarRange, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { academicYearsApi } from '@/lib/api';
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
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { toast } from 'sonner';

interface AcademicYear {
  id: string;
  year: number;
  startDate: string;
  endDate: string;
  isActive?: boolean;
  isCurrent?: boolean;
}

export default function AdminAcademicYearsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, formatDate, formatNumber, messages } = useI18n();
  const router = useRouter();
  const [academicYears, setAcademicYears] = useState<AcademicYear[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingYear, setEditingYear] = useState<AcademicYear | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    year: new Date().getFullYear(),
    startDate: '',
    endDate: '',
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

  const fetchAcademicYears = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await academicYearsApi.getAll({ page, limit: 20 });
      const filteredYears = search
        ? response.data.filter((entry) =>
            entry.year.toString().includes(search.trim()),
          )
        : response.data;

      setAcademicYears(filteredYears);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải niên khóa.'
          : 'Academic years could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search]);

  useEffect(() => {
    if (canAccess) {
      void fetchAcademicYears();
    }
  }, [canAccess, fetchAcademicYears]);

  const pageSummary = useMemo(() => {
    if (academicYears.length === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [academicYears.length, locale, page, totalPages]);

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải niên khóa',
          title: 'Niên khóa',
          description:
            'Giữ lịch học năm học rõ ràng, dễ tìm và có chủ đích trước khi học kỳ và đợt đăng ký dựa vào đó.',
          create: 'Tạo niên khóa',
          searchLabel: 'Tìm niên khóa',
          searchPlaceholder: 'Tìm theo năm',
          unavailableTitle: 'Niên khóa chưa sẵn sàng',
          emptyTitle: 'Không có niên khóa phù hợp',
          emptyDescription:
            'Hãy tạo niên khóa mới để kế hoạch học kỳ có một mốc chuẩn rõ ràng.',
          tableTitle: 'Bản ghi niên khóa',
          headers: {
            year: 'Năm',
            startDate: 'Ngày bắt đầu',
            endDate: 'Ngày kết thúc',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          active: 'Đang hoạt động',
          inactive: 'Ngừng hoạt động',
          deleteTitle: 'Xóa niên khóa',
          deleteMessage: (year: number) =>
            `Xóa niên khóa ${year}? Hành động này sẽ gỡ niên khóa khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa niên khóa',
          deleted: 'Đã xóa niên khóa',
          deleteFailed: 'Hiện chưa thể xóa niên khóa này.',
          updated: 'Đã cập nhật niên khóa',
          created: 'Đã tạo niên khóa',
          saveFailed: 'Hiện chưa thể lưu niên khóa.',
          editTitle: 'Chỉnh sửa niên khóa',
          createTitle: 'Tạo niên khóa',
          yearLabel: 'Năm',
          startDateLabel: 'Ngày bắt đầu',
          endDateLabel: 'Ngày kết thúc',
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (year: number) => `Chỉnh sửa niên khóa ${year}`,
          deleteLabel: (year: number) => `Xóa niên khóa ${year}`,
        }
      : {
          loading: 'Loading academic years',
          title: 'Academic years',
          description:
            'Keep the academic calendar clean, searchable, and deliberate before semesters and registration windows build on top of it.',
          create: 'Create academic year',
          searchLabel: 'Search academic years',
          searchPlaceholder: 'Search by year',
          unavailableTitle: 'Academic years unavailable',
          emptyTitle: 'No matching academic years',
          emptyDescription:
            'Create a new academic year to give semester planning a clean anchor.',
          tableTitle: 'Academic year records',
          headers: {
            year: 'Year',
            startDate: 'Start date',
            endDate: 'End date',
            status: 'Status',
            actions: 'Actions',
          },
          active: 'Active',
          inactive: 'Inactive',
          deleteTitle: 'Delete academic year',
          deleteMessage: (year: number) =>
            `Delete ${year}? This removes the academic year from the current admin view.`,
          deleteConfirm: 'Delete academic year',
          deleted: 'Academic year deleted',
          deleteFailed: 'We could not delete that academic year.',
          updated: 'Academic year updated',
          created: 'Academic year created',
          saveFailed: 'The academic year could not be saved.',
          editTitle: 'Edit academic year',
          createTitle: 'Create academic year',
          yearLabel: 'Year',
          startDateLabel: 'Start date',
          endDateLabel: 'End date',
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (year: number) => `Edit academic year ${year}`,
          deleteLabel: (year: number) => `Delete academic year ${year}`,
        };

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingYear(null);
    setFormData({
      year: new Date().getFullYear(),
      startDate: '',
      endDate: '',
    });
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = (year: AcademicYear) => {
    setEditingYear(year);
    setFormData({
      year: year.year,
      startDate: year.startDate.split('T')[0],
      endDate: year.endDate.split('T')[0],
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
    await fetchAcademicYears();
  };

  const handleDelete = async (record: AcademicYear) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(record.year),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await academicYearsApi.delete(record.id);
      toast.success(copy.deleted);
      await fetchAcademicYears();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      if (editingYear) {
        await academicYearsApi.update(editingYear.id, formData);
        toast.success(copy.updated);
      } else {
        await academicYearsApi.create(formData);
        toast.success(copy.created);
      }

      closeModal();
      await fetchAcademicYears();
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
            onRetry={() => void fetchAcademicYears()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : academicYears.length === 0 ? (
          <EmptyState
            icon={CalendarRange}
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
                {academicYears.map((record) => (
                  <article
                    key={`${record.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <h3 className="font-semibold text-foreground">{record.year}</h3>
                        <p className="mt-1 text-sm text-muted-foreground">{copy.headers.year}</p>
                      </div>
                      <span className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                        {record.isActive || record.isCurrent ? copy.active : copy.inactive}
                      </span>
                    </div>
                    <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.startDate}
                        </dt>
                        <dd className="mt-1 text-foreground">{formatDate(record.startDate)}</dd>
                      </div>
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.endDate}
                        </dt>
                        <dd className="mt-1 text-foreground">{formatDate(record.endDate)}</dd>
                      </div>
                    </dl>
                    <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => openEdit(record)}
                        aria-label={copy.editLabel(record.year)}
                        title={copy.editLabel(record.year)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => void handleDelete(record)}
                        aria-label={copy.deleteLabel(record.year)}
                        title={copy.deleteLabel(record.year)}
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
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.year}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.startDate}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.endDate}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {academicYears.map((record) => (
                      <tr key={record.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {record.year}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatDate(record.startDate)}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatDate(record.endDate)}
                        </td>
                        <td className="px-2 py-4">
                          <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {record.isActive || record.isCurrent ? copy.active : copy.inactive}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(record)}
                              aria-label={copy.editLabel(record.year)}
                              title={copy.editLabel(record.year)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(record)}
                              aria-label={copy.deleteLabel(record.year)}
                              title={copy.deleteLabel(record.year)}
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
        title={editingYear ? copy.editTitle : copy.createTitle}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <AdminFormField label={copy.yearLabel}>
            <Input
              type="number"
              min="2000"
              max="2100"
              value={formData.year}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  year: Number(event.target.value) || new Date().getFullYear(),
                }))
              }
              required
              />
          </AdminFormField>

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.startDateLabel}>
              <Input
                type="date"
                value={formData.startDate}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    startDate: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.endDateLabel}>
              <Input
                type="date"
                value={formData.endDate}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    endDate: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? copy.saving : editingYear ? copy.editAction : copy.create}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
