'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Bell, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { announcementsApi, semestersApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminFormField,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { Textarea } from '@/components/ui/textarea';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { getLocalizedName } from '@/lib/academic-content';
import { useOrderedPosts } from '@/components/providers/SiteAppearanceProvider';

type Semester = {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
};

type Announcement = {
  id: string;
  title: string;
  content: string;
  priority: string;
  targetRoles?: string[];
  targetYears?: number[];
  isGlobal?: boolean;
  semesterId?: string | null;
  createdAt: string;
  semester?: { name: string; nameEn?: string; nameVi?: string } | null;
};

const priorities = ['LOW', 'NORMAL', 'HIGH', 'URGENT'] as const;
const roleOptions = ['STUDENT', 'LECTURER', 'ADMIN', 'SUPER_ADMIN'] as const;

function announcementPriorityTone(priority: string): StatusTone {
  switch (priority.toUpperCase()) {
    case 'URGENT':
      return 'danger';
    case 'HIGH':
      return 'warning';
    case 'NORMAL':
      return 'info';
    default:
      return 'neutral';
  }
}

export default function AdminAnnouncementsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, formatDateTime, messages } = useI18n();
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [items, setItems] = useState<Announcement[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [filters, setFilters] = useState({ semesterId: '', priority: '' });
  const [draft, setDraft] = useState({
    title: '',
    content: '',
    priority: 'NORMAL',
    isGlobal: true,
    semesterId: '',
    targetRoles: [] as string[],
    targetYears: '' as string,
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

  const fetchSemesters = useCallback(async () => {
    try {
      const response = await semestersApi.getAll();
      setSemesters(response.data || []);
    } catch {
      // Optional filter data.
    }
  }, []);

  const fetchAnnouncements = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await announcementsApi.getAll({
        page,
        limit: 20,
        semesterId: filters.semesterId || undefined,
        priority: filters.priority || undefined,
      });
      setItems(response.data || []);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải thông báo.'
          : 'Announcements could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [filters.priority, filters.semesterId, locale, page]);

  useEffect(() => {
    if (canAccess) {
      void fetchSemesters();
      void fetchAnnouncements();
    }
  }, [canAccess, fetchAnnouncements, fetchSemesters]);

  const semesterOptions = useMemo(
    () => [
      { value: '', label: locale === 'vi' ? 'Tất cả học kỳ' : 'All semesters' },
      ...semesters.map((semester) => ({
        value: semester.id,
        label: getLocalizedName(locale, semester, semester.name),
      })),
    ],
    [locale, semesters],
  );

  const createSemesterOptions = useMemo(
    () => [
      { value: '', label: locale === 'vi' ? 'Không có học kỳ' : 'No semester' },
      ...semesters.map((semester) => ({
        value: semester.id,
        label: getLocalizedName(locale, semester, semester.name),
      })),
    ],
    [locale, semesters],
  );

  const priorityOptions = useMemo(
    () => [
      { value: '', label: locale === 'vi' ? 'Tất cả mức ưu tiên' : 'All priorities' },
      ...priorities.map((priority) => ({
        value: priority,
        label: priority,
      })),
    ],
    [locale],
  );

  const pageSummary = useMemo(() => {
    if (items.length === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [items.length, locale, page, totalPages]);

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải thông báo',
          title: 'Thông báo',
          description:
            'Phát hành cập nhật rõ ràng mà không để thông điệp bị phân mảnh khắp nền tảng.',
          refresh: 'Làm mới',
          newAnnouncement: 'Thông báo mới',
          semester: 'Học kỳ',
          priority: 'Ưu tiên',
          clearFilters: 'Xóa bộ lọc',
          unavailableTitle: 'Thông báo chưa sẵn sàng',
          emptyTitle: 'Không có thông báo phù hợp',
          emptyDescription:
            'Hãy tạo thông báo để sinh viên, giảng viên và quản trị viên luôn nắm cùng một cập nhật campus.',
          feedTitle: 'Bảng tin thông báo',
          globalAudience: 'Toàn bộ đối tượng',
          targeted: 'Nhắm tới',
          selectedRoles: 'Vai trò đã chọn',
          semesterPrefix: 'Học kỳ',
          publishedAt: 'Phát hành',
          deleteTitle: 'Xóa thông báo',
          deleteMessage: (title: string) =>
            `Xóa ${title}? Hành động này sẽ gỡ thông báo khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa thông báo',
          deleted: 'Đã xóa thông báo',
          deleteFailed: 'Hiện chưa thể xóa thông báo này.',
          titleRequired: 'Tiêu đề và nội dung là bắt buộc.',
          created: 'Đã tạo thông báo',
          createFailed: 'Hiện chưa thể tạo thông báo.',
          modalTitle: 'Thông báo mới',
          closeModal: 'Đóng biểu mẫu thông báo mới',
          form: {
            title: 'Tiêu đề',
            content: 'Nội dung',
            priority: 'Ưu tiên',
            semester: 'Học kỳ',
            globalAudience: 'Toàn bộ đối tượng',
            targetRoles: 'Vai trò nhận',
            targetYears: 'Niên khóa nhận',
            titlePlaceholder: 'Tiêu đề thông báo',
            contentPlaceholder: 'Viết cập nhật một cách rõ ràng và trực tiếp.',
            yearsPlaceholder: 'Ví dụ: 1,2,3',
            yearsHint: 'Để trống nếu muốn nhắm tới tất cả niên khóa phù hợp.',
          },
          createAction: 'Tạo thông báo',
          deleteLabel: (title: string) => `Xóa thông báo ${title}`,
        }
      : {
          loading: 'Loading announcements',
          title: 'Announcements',
          description:
            'Publish clear updates without leaving fragmented messages scattered across the platform.',
          refresh: 'Refresh',
          newAnnouncement: 'New announcement',
          semester: 'Semester',
          priority: 'Priority',
          clearFilters: 'Clear filters',
          unavailableTitle: 'Announcements unavailable',
          emptyTitle: 'No matching announcements',
          emptyDescription:
            'Create an announcement to keep students, lecturers, and admins aligned on the latest campus updates.',
          feedTitle: 'Announcement feed',
          globalAudience: 'Global audience',
          targeted: 'Targeted',
          selectedRoles: 'Selected roles',
          semesterPrefix: 'Semester',
          publishedAt: 'Published',
          deleteTitle: 'Delete announcement',
          deleteMessage: (title: string) =>
            `Delete ${title}? This removes the announcement from the current admin view.`,
          deleteConfirm: 'Delete announcement',
          deleted: 'Announcement deleted',
          deleteFailed: 'We could not delete that announcement.',
          titleRequired: 'Title and content are required.',
          created: 'Announcement created',
          createFailed: 'The announcement could not be created.',
          modalTitle: 'New announcement',
          closeModal: 'Close new announcement form',
          form: {
            title: 'Title',
            content: 'Content',
            priority: 'Priority',
            semester: 'Semester',
            globalAudience: 'Global audience',
            targetRoles: 'Target roles',
            targetYears: 'Target years',
            titlePlaceholder: 'Announcement title',
            contentPlaceholder: 'Write the update clearly and directly.',
            yearsPlaceholder: 'e.g. 1,2,3',
            yearsHint: 'Leave empty to target every matching year.',
          },
          createAction: 'Create announcement',
          deleteLabel: (title: string) => `Delete announcement ${title}`,
        };

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetDraft = () => {
    setDraft({
      title: '',
      content: '',
      priority: 'NORMAL',
      isGlobal: true,
      semesterId: '',
      targetRoles: [],
      targetYears: '',
    });
  };

  const closeCreateModal = () => {
    setIsCreateOpen(false);
    resetDraft();
  };

  const handleCreate = async () => {
    if (!draft.title.trim() || !draft.content.trim()) {
      toast.error(copy.titleRequired);
      return;
    }

    const targetYears = draft.targetYears
      .split(',')
      .map((value) => value.trim())
      .filter(Boolean)
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value));

    try {
      await announcementsApi.create({
        title: draft.title,
        content: draft.content,
        priority: draft.priority,
        isGlobal: draft.isGlobal,
        semesterId: draft.semesterId || null,
        targetRoles: draft.isGlobal ? [] : draft.targetRoles,
        targetYears: draft.isGlobal ? [] : targetYears,
      });
      toast.success(copy.created);
      closeCreateModal();
      setPage(1);
      await fetchAnnouncements();
    } catch {
      toast.error(copy.createFailed);
    }
  };

  const handleDelete = async (announcement: Announcement) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(announcement.title),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await announcementsApi.delete(announcement.id);
      toast.success(copy.deleted);
      await fetchAnnouncements();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" onClick={() => void fetchAnnouncements()}>
            <RefreshCw className="mr-2 h-4 w-4" />
            {copy.refresh}
          </Button>
          <Button onClick={() => setIsCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            {copy.newAnnouncement}
          </Button>
        </div>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
            <div className="grid gap-4 md:grid-cols-3">
              <Select
                label={copy.semester}
                value={filters.semesterId}
                onChange={(event) => {
                  setFilters((current) => ({
                    ...current,
                    semesterId: event.target.value,
                  }));
                  setPage(1);
                }}
                options={semesterOptions}
              />
              <Select
                label={copy.priority}
                value={filters.priority}
                onChange={(event) => {
                  setFilters((current) => ({
                    ...current,
                    priority: event.target.value,
                  }));
                  setPage(1);
                }}
                options={priorityOptions}
              />
              <div className="flex items-end gap-2">
                <Button
                  variant="outline"
                  onClick={() => {
                    setFilters({ semesterId: '', priority: '' });
                    setPage(1);
                  }}
                >
                  {copy.clearFilters}
                </Button>
                <div className="text-sm text-muted-foreground">{pageSummary}</div>
              </div>
            </div>
        </AdminToolbarCard>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchAnnouncements()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : orderedItems.length === 0 ? (
          <EmptyState
            icon={Bell}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            action={<Button onClick={() => setIsCreateOpen(true)}>{copy.newAnnouncement}</Button>}
          />
        ) : (
          <AdminTableCard
            title={copy.feedTitle}
            contentClassName="space-y-4"
            footer={
              <AdminPaginationFooter
                summary={pageSummary}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
                className="mt-0"
              />
            }
          >
              {orderedItems.map((announcement) => (
                <div
                  key={announcement.id}
                  className="rounded-lg border border-border/70 bg-background/70 p-5"
                >
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0 space-y-3">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(announcementPriorityTone(announcement.priority))}`}>
                          {announcement.priority}
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {announcement.isGlobal
                            ? copy.globalAudience
                            : `${copy.targeted}: ${(announcement.targetRoles || []).join(', ') || copy.selectedRoles}`}
                        </span>
                        {announcement.semester?.name ? (
                          <span className="text-xs text-muted-foreground">
                            {copy.semesterPrefix}:{' '}
                            {getLocalizedName(
                              locale,
                              announcement.semester,
                              announcement.semester.name,
                            )}
                          </span>
                        ) : null}
                      </div>
                      <div>
                        <h3 className="text-base font-semibold text-foreground">
                          {announcement.title}
                        </h3>
                        <p className="mt-2 whitespace-pre-line text-sm leading-6 text-muted-foreground">
                          {announcement.content}
                        </p>
                      </div>
                      <p className="text-xs text-muted-foreground">
                        {copy.publishedAt} {formatDateTime(announcement.createdAt)}
                      </p>
                    </div>
                    <AdminRowActions className="shrink-0">
                      <Button
                        size="icon"
                        variant="ghost"
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                        onClick={() => void handleDelete(announcement)}
                        aria-label={copy.deleteLabel(announcement.title)}
                        title={copy.deleteLabel(announcement.title)}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </AdminRowActions>
                  </div>
                </div>
              ))}
          </AdminTableCard>
        )}
      </div>

      <Modal
        isOpen={isCreateOpen}
        onClose={closeCreateModal}
        title={copy.modalTitle}
        closeLabel={copy.closeModal}
        className="max-w-2xl"
      >
        <div className="space-y-4">
          <AdminFormField label={copy.form.title}>
            <Input
              value={draft.title}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  title: event.target.value,
                }))
              }
              placeholder={copy.form.titlePlaceholder}
            />
          </AdminFormField>

          <AdminFormField label={copy.form.content}>
            <Textarea
              value={draft.content}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  content: event.target.value,
                }))
              }
              className="min-h-[160px]"
              placeholder={copy.form.contentPlaceholder}
            />
          </AdminFormField>

          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label={copy.form.priority}
              value={draft.priority}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  priority: event.target.value,
                }))
              }
              options={priorities.map((priority) => ({
                value: priority,
                label: priority,
              }))}
            />
            <Select
              label={copy.form.semester}
              value={draft.semesterId}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  semesterId: event.target.value,
                }))
              }
              options={createSemesterOptions}
            />
          </div>

          <label className="flex items-center gap-2 text-sm text-foreground">
            <input
              id="isGlobal"
              type="checkbox"
              checked={draft.isGlobal}
              onChange={(event) =>
                setDraft((current) => ({
                  ...current,
                  isGlobal: event.target.checked,
                }))
              }
            />
            {copy.form.globalAudience}
          </label>

          {!draft.isGlobal ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">
                  {copy.form.targetRoles}
                </label>
                <div className="flex flex-wrap gap-3 rounded-lg border border-border/70 bg-background/70 p-3 text-sm">
                  {roleOptions.map((role) => (
                    <label key={role} className="flex items-center gap-2">
                      <input
                        type="checkbox"
                        checked={draft.targetRoles.includes(role)}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            targetRoles: event.target.checked
                              ? Array.from(new Set([...current.targetRoles, role]))
                              : current.targetRoles.filter((entry) => entry !== role),
                          }))
                        }
                      />
                      {role}
                    </label>
                  ))}
                </div>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-foreground">
                  {copy.form.targetYears}
                </label>
                <Input
                  value={draft.targetYears}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      targetYears: event.target.value,
                    }))
                  }
                  placeholder={copy.form.yearsPlaceholder}
                  hint={copy.form.yearsHint}
                />
              </div>
            </div>
          ) : null}

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeCreateModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button onClick={() => void handleCreate()}>{copy.createAction}</Button>
          </AdminDialogFooter>
        </div>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
