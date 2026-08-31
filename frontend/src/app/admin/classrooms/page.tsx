'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { DoorOpen, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { classroomsApi } from '@/lib/api';
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

interface Classroom {
  id: string;
  building: string;
  roomNumber: string;
  capacity: number;
  type: string;
  isActive?: boolean;
}

export default function AdminClassroomsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatNumber, href, locale, messages } = useI18n();
  const router = useRouter();
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingRoom, setEditingRoom] = useState<Classroom | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    building: '',
    roomNumber: '',
    capacity: 30,
    type: 'LECTURE',
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

  const fetchClassrooms = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await classroomsApi.getAll({ page, limit: 20 });
      const filteredRooms = search
        ? response.data.filter(
            (room: Classroom) =>
              room.building.toLowerCase().includes(search.toLowerCase()) ||
              room.roomNumber.toLowerCase().includes(search.toLowerCase()),
          )
        : response.data;

      setClassrooms(filteredRooms);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải danh sách phòng học.'
          : 'Classrooms could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search]);

  const classroomTypeOptions = useMemo(
    () => [
      { value: 'LECTURE', label: locale === 'vi' ? 'Lớp học lý thuyết' : 'Lecture' },
      { value: 'LAB', label: locale === 'vi' ? 'Phòng thí nghiệm' : 'Lab' },
      { value: 'SEMINAR', label: locale === 'vi' ? 'Phòng seminar' : 'Seminar' },
      { value: 'OTHER', label: locale === 'vi' ? 'Khác' : 'Other' },
    ],
    [locale],
  );

  const copy = useMemo(
    () =>
      locale === 'vi'
        ? {
          loading: 'Đang tải phòng học',
          title: 'Phòng học',
          description:
            'Giữ dữ liệu phòng học rõ ràng để section, lịch học và kế hoạch sức chứa luôn khớp nhau.',
          create: 'Tạo phòng học',
          searchLabel: 'Tìm phòng học',
          searchPlaceholder: 'Tìm theo tòa nhà hoặc số phòng',
          pageSummaryEmpty: 'Không có bản ghi phù hợp',
          pageSummary: (currentPage: number, pages: number) =>
            `Trang ${currentPage} / ${pages}`,
          unavailableTitle: 'Phòng học chưa sẵn sàng',
          emptyTitle: 'Không có phòng học phù hợp',
          emptyDescription:
            'Hãy tạo phòng học để phần xếp section có nguồn dữ liệu phòng ổn định.',
          tableTitle: 'Bản ghi phòng học',
          headers: {
            building: 'Tòa nhà',
            room: 'Phòng',
            capacity: 'Sức chứa',
            type: 'Loại phòng',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          active: 'Đang hoạt động',
          inactive: 'Ngừng hoạt động',
          deleteTitle: 'Xóa phòng học',
          deleteMessage: (building: string, roomNumber: string) =>
            `Xóa phòng ${building} ${roomNumber}? Hành động này sẽ gỡ phòng khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa phòng học',
          deleted: 'Đã xóa phòng học',
          deleteFailed: 'Hiện chưa thể xóa phòng học này.',
          updated: 'Đã cập nhật phòng học',
          created: 'Đã tạo phòng học',
          saveFailed: 'Hiện chưa thể lưu phòng học.',
          editTitle: 'Chỉnh sửa phòng học',
          createTitle: 'Tạo phòng học',
          fields: {
            building: 'Tòa nhà',
            roomNumber: 'Số phòng',
            capacity: 'Sức chứa',
            type: 'Loại phòng',
          },
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (building: string, roomNumber: string) =>
            `Chỉnh sửa phòng học ${building} ${roomNumber}`,
          deleteLabel: (building: string, roomNumber: string) =>
            `Xóa phòng học ${building} ${roomNumber}`,
        }
        : {
          loading: 'Loading classrooms',
          title: 'Classrooms',
          description:
            'Keep room inventory readable so sections, schedules, and capacity planning stay aligned.',
          create: 'Create classroom',
          searchLabel: 'Search classrooms',
          searchPlaceholder: 'Search by building or room number',
          pageSummaryEmpty: 'No matching records',
          pageSummary: (currentPage: number, pages: number) =>
            `Page ${currentPage} of ${pages}`,
          unavailableTitle: 'Classrooms unavailable',
          emptyTitle: 'No matching classrooms',
          emptyDescription:
            'Create a classroom so section scheduling has a reliable room inventory to work with.',
          tableTitle: 'Classroom records',
          headers: {
            building: 'Building',
            room: 'Room',
            capacity: 'Capacity',
            type: 'Type',
            status: 'Status',
            actions: 'Actions',
          },
          active: 'Active',
          inactive: 'Inactive',
          deleteTitle: 'Delete classroom',
          deleteMessage: (building: string, roomNumber: string) =>
            `Delete ${building} ${roomNumber}? This removes the classroom from the current admin view.`,
          deleteConfirm: 'Delete classroom',
          deleted: 'Classroom deleted',
          deleteFailed: 'We could not delete that classroom.',
          updated: 'Classroom updated',
          created: 'Classroom created',
          saveFailed: 'The classroom could not be saved.',
          editTitle: 'Edit classroom',
          createTitle: 'Create classroom',
          fields: {
            building: 'Building',
            roomNumber: 'Room number',
            capacity: 'Capacity',
            type: 'Type',
          },
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (building: string, roomNumber: string) =>
            `Edit classroom ${building} ${roomNumber}`,
          deleteLabel: (building: string, roomNumber: string) =>
            `Delete classroom ${building} ${roomNumber}`,
        },
    [locale, messages.common.actions.saveChanges],
  );

  useEffect(() => {
    if (canAccess) {
      void fetchClassrooms();
    }
  }, [canAccess, fetchClassrooms]);

  const pageSummary = useMemo(() => {
    if (classrooms.length === 0) {
      return copy.pageSummaryEmpty;
    }

    return copy.pageSummary(page, totalPages);
  }, [classrooms.length, copy, page, totalPages]);

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingRoom(null);
    setFormData({
      building: '',
      roomNumber: '',
      capacity: 30,
      type: 'LECTURE',
    });
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = (room: Classroom) => {
    setEditingRoom(room);
    setFormData({
      building: room.building,
      roomNumber: room.roomNumber,
      capacity: room.capacity,
      type: room.type,
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
    await fetchClassrooms();
  };

  const handleDelete = async (room: Classroom) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(room.building, room.roomNumber),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await classroomsApi.delete(room.id);
      toast.success(copy.deleted);
      await fetchClassrooms();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      if (editingRoom) {
        await classroomsApi.update(editingRoom.id, formData);
        toast.success(copy.updated);
      } else {
        await classroomsApi.create(formData);
        toast.success(copy.created);
      }

      closeModal();
      await fetchClassrooms();
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
            onRetry={() => void fetchClassrooms()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : classrooms.length === 0 ? (
          <EmptyState
            icon={DoorOpen}
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
                {classrooms.map((room) => (
                  <article
                    key={`${room.id}-mobile`}
                    className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                    role="listitem"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                          {room.building}
                        </p>
                        <h3 className="mt-1 font-semibold text-foreground">{room.roomNumber}</h3>
                      </div>
                      <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(room.isActive ? 'success' : 'neutral')}`}>
                        {room.isActive ? copy.active : copy.inactive}
                      </span>
                    </div>
                    <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.capacity}
                        </dt>
                        <dd className="mt-1 text-foreground">{formatNumber(room.capacity)}</dd>
                      </div>
                      <div>
                        <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.type}
                        </dt>
                        <dd className="mt-1 text-foreground">{room.type}</dd>
                      </div>
                    </dl>
                    <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                      <Button
                        size="icon"
                        variant="ghost"
                        onClick={() => openEdit(room)}
                        aria-label={copy.editLabel(room.building, room.roomNumber)}
                        title={copy.editLabel(room.building, room.roomNumber)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => void handleDelete(room)}
                        aria-label={copy.deleteLabel(room.building, room.roomNumber)}
                        title={copy.deleteLabel(room.building, room.roomNumber)}
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
                      <th className="px-2 py-3 font-medium">{copy.headers.building}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.room}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.capacity}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.type}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {classrooms.map((room) => (
                      <tr key={room.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {room.building}
                        </td>
                        <td className="px-2 py-4 text-foreground">
                          {room.roomNumber}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatNumber(room.capacity)}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {room.type}
                        </td>
                        <td className="px-2 py-4">
                          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(room.isActive ? 'success' : 'neutral')}`}>
                            {room.isActive ? copy.active : copy.inactive}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(room)}
                              aria-label={copy.editLabel(room.building, room.roomNumber)}
                              title={copy.editLabel(room.building, room.roomNumber)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(room)}
                              aria-label={copy.deleteLabel(room.building, room.roomNumber)}
                              title={copy.deleteLabel(room.building, room.roomNumber)}
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
        title={editingRoom ? copy.editTitle : copy.createTitle}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.fields.building}>
              <Input
                type="text"
                value={formData.building}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    building: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.fields.roomNumber}>
              <Input
                type="text"
                value={formData.roomNumber}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    roomNumber: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.fields.capacity}>
              <Input
                type="number"
                min="1"
                value={formData.capacity}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    capacity: Number(event.target.value) || 30,
                  }))
                }
                required
              />
            </AdminFormField>
            <Select
              label={copy.fields.type}
              value={formData.type}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  type: event.target.value,
                }))
              }
              options={classroomTypeOptions}
            />
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingRoom
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
