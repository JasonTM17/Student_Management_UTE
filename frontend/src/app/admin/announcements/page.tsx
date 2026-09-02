'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Archive,
  Bell,
  CalendarClock,
  History,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
} from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import {
  announcementsApi,
  lecturersApi,
  sectionsApi,
  semestersApi,
  type AnnouncementHistoryRecord,
  type AnnouncementMutation,
  type AnnouncementRecord,
} from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminFormField,
  AdminFormSection,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { statusToneClass } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { Textarea } from '@/components/ui/textarea';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { getLocalizedName } from '@/lib/academic-content';
import { campusErrorCode, campusErrorMessage } from '@/lib/campus-error';
import {
  ANNOUNCEMENT_PRIORITIES,
  ANNOUNCEMENT_ROLES,
  announcementAudienceLabel,
  announcementHistoryActionLabel,
  announcementHistoryReason,
  announcementIsUpdated,
  announcementLecturerName,
  announcementPriorityLabel,
  announcementPriorityTone,
  announcementPriorityValue,
  announcementRoleLabel,
  announcementRoleValues,
  announcementSectionLabel,
  announcementSemesterName,
  type AnnouncementPriority,
  type AnnouncementRole,
} from '@/lib/announcement-presentation';
import { useOrderedPosts } from '@/components/providers/SiteAppearanceProvider';
import type { Lecturer, Section, Semester } from '@/types/api';

type StatusFilter = 'ACTIVE' | 'ARCHIVED' | 'ALL';
type ModalKind = 'editor' | 'history' | 'lifecycle' | null;
type LifecycleAction = 'archive' | 'restore';

type AnnouncementDraft = {
  title: string;
  content: string;
  priority: AnnouncementPriority;
  isGlobal: boolean;
  targetRoles: AnnouncementRole[];
  targetYears: string;
  semesterId: string;
  sectionId: string;
  lecturerId: string;
  publishAt: string;
  expiresAt: string;
  reason: string;
};

const emptyDraft: AnnouncementDraft = {
  title: '',
  content: '',
  priority: 'NORMAL',
  isGlobal: true,
  targetRoles: [],
  targetYears: '',
  semesterId: '',
  sectionId: '',
  lecturerId: '',
  publishAt: '',
  expiresAt: '',
  reason: '',
};

const historyFields = [
  'title',
  'content',
  'priority',
  'isGlobal',
  'targetRoles',
  'targetYears',
  'publishAt',
  'expiresAt',
  'semesterId',
  'sectionId',
  'lecturerId',
  'archivedAt',
] as const;

type HistoryField = (typeof historyFields)[number];

function localDateTime(value: string | null | undefined) {
  if (!value) return '';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '';
  const pad = (part: number) => String(part).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
function isoDateTime(value: string) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toISOString() : null;
}

function parseYears(value: string) {
  const tokens = value.split(',').map((token) => token.trim()).filter(Boolean);
  const numbers = tokens.map((token) => Number(token));
  const invalid = tokens.some(
    (token, index) => !/^\d+$/.test(token) || !Number.isInteger(numbers[index]) || numbers[index] < 1,
  );
  return {
    values: [...new Set(numbers.filter((year) => Number.isInteger(year) && year > 0))],
    invalid,
  };
}

function draftFromAnnouncement(item: AnnouncementRecord): AnnouncementDraft {
  return {
    title: item.title,
    content: item.content,
    priority: announcementPriorityValue(item.priority),
    isGlobal: item.isGlobal ?? false,
    targetRoles: announcementRoleValues(item.targetRoles),
    targetYears: (item.targetYears ?? []).join(', '),
    semesterId: item.semesterId ?? '',
    sectionId: item.sectionId ?? '',
    lecturerId: item.lecturerId ?? '',
    publishAt: localDateTime(item.publishAt),
    expiresAt: localDateTime(item.expiresAt),
    reason: '',
  };
}

function serializeDraft(draft: AnnouncementDraft) {
  return JSON.stringify(draft);
}

function snapshotValue(snapshot: Record<string, unknown> | null | undefined, field: string) {
  if (!snapshot || typeof snapshot !== 'object') return undefined;
  return snapshot[field];
}

function sameValue(left: unknown, right: unknown) {
  return JSON.stringify(left ?? null) === JSON.stringify(right ?? null);
}

function actorDescription(locale: 'vi' | 'en') {
  return locale === 'vi' ? 'Tài khoản quản trị đã ghi nhận' : 'Recorded by an administrator';
}

export default function AdminAnnouncementsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { href, locale, formatDateTime } = useI18n();
  const router = useRouter();
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const vi = locale === 'vi';

  const [items, setItems] = useState<AnnouncementRecord[]>([]);
  const orderedItems = useOrderedPosts(items);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [sections, setSections] = useState<Section[]>([]);
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [referenceError, setReferenceError] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [filters, setFilters] = useState<{ semesterId: string; priority: string; status: StatusFilter }>({
    semesterId: '',
    priority: '',
    status: 'ACTIVE',
  });
  const [modal, setModal] = useState<ModalKind>(null);
  const [editorMode, setEditorMode] = useState<'create' | 'edit'>('create');
  const [selected, setSelected] = useState<AnnouncementRecord | null>(null);
  const [draft, setDraft] = useState<AnnouncementDraft>(emptyDraft);
  const [history, setHistory] = useState<AnnouncementHistoryRecord[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState('');
  const [lifecycle, setLifecycle] = useState<{ item: AnnouncementRecord; action: LifecycleAction } | null>(null);
  const [lifecycleReason, setLifecycleReason] = useState('');
  const [previewRole, setPreviewRole] = useState<AnnouncementRole>('STUDENT');
  const [previewYear, setPreviewYear] = useState('1');
  const [busy, setBusy] = useState(false);
  const editorSnapshot = useRef('');

  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const copy = vi
    ? {
        title: 'Quản trị thông báo',
        description: 'Chỉnh sửa, lập lịch và lưu trữ thông báo với lịch sử rõ ràng.',
        refresh: 'Làm mới',
        create: 'Thông báo mới',
        active: 'Đang hiển thị',
        archived: 'Đã lưu trữ',
        all: 'Tất cả',
        semester: 'Học kỳ',
        priority: 'Mức ưu tiên',
        status: 'Trạng thái',
        feed: 'Danh sách thông báo',
        empty: 'Chưa có thông báo phù hợp',
        emptyDescription: 'Tạo thông báo đầu tiên hoặc đổi bộ lọc để xem bản ghi khác.',
        edit: 'Chỉnh sửa',
        archive: 'Lưu trữ',
        restore: 'Khôi phục',
        history: 'Lịch sử thay đổi',
        reason: 'Lý do thay đổi',
        reasonHint: 'Lý do được lưu cùng lịch sử để các quản trị viên khác dễ theo dõi.',
        save: 'Lưu thay đổi',
        createSave: 'Phát hành thông báo',
        cancel: 'Hủy',
        close: 'Đóng',
        preview: 'Xem trước theo vai trò',
        previewStudent: 'Sinh viên',
        previewLecturer: 'Giảng viên',
        updated: 'Đã cập nhật',
        schedule: 'Lịch hiển thị',
        audience: 'Đối tượng nhận',
        global: 'Toàn trường',
        targeted: 'Chọn nhóm cụ thể',
        targetYears: 'Năm học sinh viên',
        targetYearsHint: 'Nhập các năm, cách nhau bằng dấu phẩy. Để trống nếu áp dụng cho mọi năm.',
        section: 'Lớp học phần',
        lecturer: 'Giảng viên phụ trách',
        noSelection: 'Không gán',
        selectionUnavailable: 'Mục học vụ không còn trong danh mục',
        sectionHint: 'Chọn lớp học phần để giới hạn thông báo theo lớp.',
        lecturerHint: 'Có thể giới hạn thông báo cho lớp do giảng viên phụ trách.',
        originalAuthor: 'Tác giả gốc được giữ nguyên',
        revision: 'Lần cập nhật',
        publishAt: 'Bắt đầu hiển thị',
        expiresAt: 'Ngừng hiển thị',
        noEnd: 'Không đặt ngày kết thúc',
        required: 'Bắt buộc',
        unsavedTitle: 'Bản nháp chưa được lưu',
        unsavedMessage: 'Bạn có muốn đóng và bỏ các thay đổi đang nhập không?',
        archiveTitle: 'Lưu trữ thông báo?',
        restoreTitle: 'Khôi phục thông báo?',
        archiveMessage: 'Thông báo sẽ rời khỏi bảng tin sinh viên và giảng viên cho đến khi được khôi phục.',
        restoreMessage: 'Thông báo sẽ xuất hiện lại theo đối tượng và lịch hiển thị hiện tại.',
        confirmArchive: 'Lưu trữ',
        confirmRestore: 'Khôi phục',
        changeReason: 'Nhập lý do để tiếp tục.',
        missingRequired: 'Hãy điền tiêu đề và nội dung.',
        missingAudience: 'Hãy chọn ít nhất một nhóm nhận hoặc bật Toàn trường.',
        invalidYears: 'Năm học phải là các số nguyên dương, cách nhau bằng dấu phẩy.',
        invalidDates: 'Ngày kết thúc phải sau ngày bắt đầu.',
        conflict: 'Bản ghi đã thay đổi. Hãy tải lại và thử lại để không ghi đè dữ liệu mới.',
        archivedEdit: 'Thông báo đã lưu trữ. Hãy khôi phục trước khi chỉnh sửa.',
        genericError: 'Hiện chưa thể hoàn tất thao tác. Hãy thử lại sau.',
        created: 'Đã phát hành thông báo.',
        saved: 'Đã lưu thay đổi.',
        transitioned: 'Đã cập nhật trạng thái thông báo.',
        historyUnavailable: 'Hiện chưa thể tải lịch sử thay đổi.',
        historyEmpty: 'Chưa có lịch sử thay đổi.',
        noFieldChange: 'Không có nội dung hiển thị nào thay đổi trong lần này.',
        changedFields: 'Nội dung đã thay đổi',
        before: 'Trước',
        after: 'Sau',
        actor: 'Người thực hiện',
        version: 'Lần thay đổi',
        statusArchived: 'Đã lưu trữ',
        statusVisible: 'Đang hiển thị',
        notVisible: 'Không hiển thị với vai trò này',
        visible: 'Sẽ hiển thị với vai trò này',
        studentYear: 'Năm học xem trước',
        loading: 'Đang tải thông báo',
        referenceUnavailable: 'Một số danh sách học vụ chưa sẵn sàng; bạn vẫn có thể lưu nội dung và đối tượng.',
        page: (current: number, total: number) => `Trang ${current} / ${total}`,
      }
    : {
        title: 'Manage announcements',
        description: 'Edit, schedule, and store announcements with a clear change history.',
        refresh: 'Refresh',
        create: 'New announcement',
        active: 'Visible',
        archived: 'Archived',
        all: 'All',
        semester: 'Semester',
        priority: 'Priority',
        status: 'Status',
        feed: 'Announcements',
        empty: 'No matching announcements',
        emptyDescription: 'Create the first notice or change the filters to see another record.',
        edit: 'Edit',
        archive: 'Archive',
        restore: 'Restore',
        history: 'Change history',
        reason: 'Reason for change',
        reasonHint: 'The reason is stored with the history so other administrators can follow the decision.',
        save: 'Save changes',
        createSave: 'Publish announcement',
        cancel: 'Cancel',
        close: 'Close',
        preview: 'Role preview',
        previewStudent: 'Student',
        previewLecturer: 'Lecturer',
        updated: 'Updated',
        schedule: 'Visibility schedule',
        audience: 'Audience',
        global: 'Campus-wide',
        targeted: 'Choose specific groups',
        targetYears: 'Student years',
        targetYearsHint: 'Enter years separated by commas. Leave empty for every year.',
        section: 'Class section',
        lecturer: 'Responsible lecturer',
        noSelection: 'Not assigned',
        selectionUnavailable: 'Academic item is no longer in the catalogue',
        sectionHint: 'Choose a class section to limit the notice to that class.',
        lecturerHint: 'Optionally limit the notice to a lecturer’s assigned class.',
        originalAuthor: 'Original author is preserved',
        revision: 'Update',
        publishAt: 'Starts showing',
        expiresAt: 'Stops showing',
        noEnd: 'No end date',
        required: 'Required',
        unsavedTitle: 'Unsaved draft',
        unsavedMessage: 'Close the editor and discard the changes you are entering?',
        archiveTitle: 'Archive announcement?',
        restoreTitle: 'Restore announcement?',
        archiveMessage: 'The notice will leave student and lecturer feeds until it is restored.',
        restoreMessage: 'The notice will appear again for its audience and schedule.',
        confirmArchive: 'Archive',
        confirmRestore: 'Restore',
        changeReason: 'Enter a reason to continue.',
        missingRequired: 'Add a title and content.',
        missingAudience: 'Choose at least one audience group or enable Campus-wide.',
        invalidYears: 'Student years must be positive integers separated by commas.',
        invalidDates: 'The end date must be after the start date.',
        conflict: 'This record changed. Refresh and retry so newer work is not overwritten.',
        archivedEdit: 'This notice is archived. Restore it before editing.',
        genericError: 'The action could not be completed. Try again in a moment.',
        created: 'Announcement published.',
        saved: 'Changes saved.',
        transitioned: 'Announcement status updated.',
        historyUnavailable: 'Change history is not available right now.',
        historyEmpty: 'No changes have been recorded yet.',
        noFieldChange: 'No visible details changed in this entry.',
        changedFields: 'What changed',
        before: 'Before',
        after: 'After',
        actor: 'Changed by',
        version: 'Change',
        statusArchived: 'Archived',
        statusVisible: 'Visible',
        notVisible: 'Not visible for this role',
        visible: 'Will be visible for this role',
        studentYear: 'Preview student year',
        loading: 'Loading announcements',
        referenceUnavailable: 'Some academic lists are not ready; content and audience can still be saved.',
        page: (current: number, total: number) => `Page ${current} of ${total}`,
      };

  useEffect(() => {
    if (authLoading || isLoggingOut) return;
    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }
    if (!isAdmin && !isSuperAdmin) router.replace(href('/dashboard'));
  }, [authLoading, href, isAdmin, isLoggingOut, isSuperAdmin, router, user]);

  const friendlyError = useCallback(
    (cause: unknown, fallback = copy.genericError) => {
      const code = (campusErrorCode(cause) ?? '').toUpperCase();
      if (code.includes('VERSION')) return copy.conflict;
      if (code.includes('ARCHIVED')) return copy.archivedEdit;
      if (code.includes('REASON')) return copy.changeReason;
      return campusErrorMessage(cause, {
        network: fallback,
        validation: fallback,
        conflict: copy.conflict,
        unauthorized: fallback,
        forbidden: fallback,
        notFound: fallback,
        server: fallback,
        unknown: fallback,
      }, fallback);
    },
    [copy.archivedEdit, copy.changeReason, copy.conflict, copy.genericError],
  );

  const fetchAnnouncements = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      const response = await announcementsApi.getAll({
        page,
        limit: 20,
        semesterId: filters.semesterId || undefined,
        priority: filters.priority || undefined,
        status: filters.status,
      });
      setItems(response.data ?? []);
      setTotalPages(Math.max(response.meta?.totalPages ?? 1, 1));
    } catch (cause) {
      setError(friendlyError(cause));
    } finally {
      setIsLoading(false);
    }
  }, [filters.priority, filters.semesterId, filters.status, friendlyError, page]);

  useEffect(() => {
    if (canAccess) void fetchAnnouncements();
  }, [canAccess, fetchAnnouncements]);

  useEffect(() => {
    if (!canAccess) return;
    let cancelled = false;
    setReferenceError('');
    void Promise.allSettled([
      semestersApi.getAll(),
      // The section read contract caps a page at 100 rows. Keep the
      // reference lookup inside that contract so the editor remains usable
      // on the admin announcements route instead of surfacing a 400.
      sectionsApi.getAll({ page: 1, limit: 100 }),
      lecturersApi.getAll({ page: 1, limit: 100 }),
    ]).then(([semesterResult, sectionResult, lecturerResult]) => {
      if (cancelled) return;
      let failed = false;
      if (semesterResult.status === 'fulfilled') setSemesters(semesterResult.value.data ?? []);
      else failed = true;
      if (sectionResult.status === 'fulfilled') setSections(sectionResult.value.data ?? []);
      else failed = true;
      if (lecturerResult.status === 'fulfilled') setLecturers(lecturerResult.value.data ?? []);
      else failed = true;
      if (failed) setReferenceError(copy.referenceUnavailable);
    });
    return () => {
      cancelled = true;
    };
  }, [canAccess, copy.referenceUnavailable]);

  const openCreate = () => {
    const nextDraft = { ...emptyDraft, targetRoles: [] as AnnouncementRole[] };
    setEditorMode('create');
    setSelected(null);
    setDraft(nextDraft);
    editorSnapshot.current = serializeDraft(nextDraft);
    setPreviewRole('STUDENT');
    setPreviewYear('1');
    setModal('editor');
  };

  const openEdit = (item: AnnouncementRecord) => {
    const nextDraft = draftFromAnnouncement(item);
    setEditorMode('edit');
    setSelected(item);
    setDraft(nextDraft);
    editorSnapshot.current = serializeDraft(nextDraft);
    setPreviewRole('STUDENT');
    setPreviewYear(String(item.targetYears?.[0] ?? 1));
    setModal('editor');
  };

  const editorDirty = modal === 'editor' && serializeDraft(draft) !== editorSnapshot.current;

  const requestCloseEditor = useCallback(async () => {
    if (busy) return;
    if (editorDirty) {
      const confirmed = await confirm({
        title: copy.unsavedTitle,
        message: copy.unsavedMessage,
        confirmText: copy.close,
        cancelText: copy.cancel,
        variant: 'destructive',
      });
      if (!confirmed) return;
    }
    setModal(null);
    setSelected(null);
  }, [busy, confirm, copy.cancel, copy.close, copy.unsavedMessage, copy.unsavedTitle, editorDirty]);

  const closeModal = useCallback(() => {
    if (modal === 'editor') {
      void requestCloseEditor();
      return;
    }
    setModal(null);
    setSelected(null);
    setLifecycle(null);
  }, [modal, requestCloseEditor]);

  const payload = useCallback((): AnnouncementMutation => ({
    title: draft.title.trim(),
    content: draft.content.trim(),
    priority: draft.priority,
    isGlobal: draft.isGlobal,
    targetRoles: draft.isGlobal ? [] : [...draft.targetRoles],
    targetYears: draft.isGlobal ? [] : parseYears(draft.targetYears).values,
    publishAt: isoDateTime(draft.publishAt),
    expiresAt: isoDateTime(draft.expiresAt),
    semesterId: draft.semesterId || null,
    sectionId: draft.sectionId || null,
    lecturerId: draft.lecturerId || null,
    ...(editorMode === 'edit' && selected
      ? { reason: draft.reason.trim(), expectedVersion: selected.version ?? 0 }
      : {}),
  }), [draft, editorMode, selected]);

  const validateDraft = () => {
    if (!draft.title.trim() || !draft.content.trim()) return copy.missingRequired;
    if (!draft.isGlobal && draft.targetRoles.length === 0) return copy.missingAudience;
    if (parseYears(draft.targetYears).invalid) return copy.invalidYears;
    if (draft.publishAt && draft.expiresAt) {
      const start = new Date(draft.publishAt).getTime();
      const end = new Date(draft.expiresAt).getTime();
      if (Number.isFinite(start) && Number.isFinite(end) && end <= start) return copy.invalidDates;
    }
    if (editorMode === 'edit') {
      const reasonLength = Array.from(draft.reason.trim()).length;
      if (reasonLength === 0 || reasonLength > 500) return copy.changeReason;
    }
    return null;
  };

  const save = async () => {
    const validationError = validateDraft();
    if (validationError) {
      toast.error(validationError);
      return;
    }
    setBusy(true);
    try {
      if (editorMode === 'edit' && selected) {
        await announcementsApi.update(selected.id, payload());
        toast.success(copy.saved);
      } else {
        await announcementsApi.create(payload());
        toast.success(copy.created);
      }
      editorSnapshot.current = '';
      setModal(null);
      setSelected(null);
      await fetchAnnouncements();
    } catch (cause) {
      toast.error(friendlyError(cause));
    } finally {
      setBusy(false);
    }
  };

  const openLifecycle = (item: AnnouncementRecord, action: LifecycleAction) => {
    setLifecycle({ item, action });
    setLifecycleReason('');
    setModal('lifecycle');
  };

  const submitLifecycle = async () => {
    if (!lifecycle || !lifecycleReason.trim() || Array.from(lifecycleReason.trim()).length > 500) {
      toast.error(copy.changeReason);
      return;
    }
    setBusy(true);
    try {
      const request = { reason: lifecycleReason.trim(), expectedVersion: lifecycle.item.version ?? 0 };
      if (lifecycle.action === 'archive') await announcementsApi.archive(lifecycle.item.id, request);
      else await announcementsApi.restore(lifecycle.item.id, request);
      toast.success(copy.transitioned);
      setModal(null);
      setLifecycle(null);
      await fetchAnnouncements();
    } catch (cause) {
      toast.error(friendlyError(cause));
    } finally {
      setBusy(false);
    }
  };

  const openHistory = async (item: AnnouncementRecord) => {
    setSelected(item);
    setHistory([]);
    setHistoryError('');
    setHistoryLoading(true);
    setModal('history');
    try {
      const response = await announcementsApi.history(item.id, { page: 1, limit: 50 });
      setHistory(response.data ?? []);
    } catch (cause) {
      setHistoryError(friendlyError(cause, copy.historyUnavailable));
    } finally {
      setHistoryLoading(false);
    }
  };

  const semesterOptions = useMemo(() => [
    { value: '', label: copy.all },
    ...semesters.map((semester) => ({ value: semester.id, label: getLocalizedName(locale, semester, semester.name) })),
  ], [copy.all, locale, semesters]);

  const editorSemesterOptions = useMemo(() => [
    { value: '', label: copy.noSelection },
    ...semesters.map((semester) => ({ value: semester.id, label: getLocalizedName(locale, semester, semester.name) })),
  ], [copy.noSelection, locale, semesters]);

  const editorSectionOptions = useMemo(() => {
    const filtered = draft.semesterId ? sections.filter((section) => section.semesterId === draft.semesterId) : sections;
    const current = draft.sectionId && !filtered.some((section) => section.id === draft.sectionId)
      ? sections.find((section) => section.id === draft.sectionId)
      : undefined;
    const rows = current ? [current, ...filtered] : filtered;
    return [
      { value: '', label: copy.noSelection },
      ...rows.filter((section, index, all) => all.findIndex((row) => row.id === section.id) === index).map((section) => ({
        value: section.id,
        label: `${section.course?.code ?? ''} · ${section.sectionNumber}`.trim(),
      })),
    ];
  }, [copy.noSelection, draft.sectionId, draft.semesterId, sections]);

  const editorLecturerOptions = useMemo(() => [
    { value: '', label: copy.noSelection },
    ...lecturers.map((lecturer) => ({
      value: lecturer.id,
      label: lecturer.user ? `${lecturer.user.firstName} ${lecturer.user.lastName}`.trim() : lecturer.employeeId,
    })),
  ], [copy.noSelection, lecturers]);

  const audienceMatches = (role: AnnouncementRole) => {
    if (draft.isGlobal) return true;
    if (!draft.targetRoles.includes(role)) return false;
    if (role !== 'STUDENT' || !draft.targetYears.trim()) return true;
    const year = Number(previewYear);
    return Number.isInteger(year) && parseYears(draft.targetYears).values.includes(year);
  };

  const previewVisible = audienceMatches(previewRole);
  const previewSemester = semesters.find((semester) => semester.id === draft.semesterId);
  const previewSection = sections.find((section) => section.id === draft.sectionId);
  const previewLecturer = lecturers.find((lecturer) => lecturer.id === draft.lecturerId);
  const previewMeta = [
    previewSemester ? getLocalizedName(locale, previewSemester, previewSemester.name) : '',
    previewSection ? `${previewSection.course?.code ?? ''} · ${previewSection.sectionNumber}`.trim() : '',
    previewLecturer?.user ? `${previewLecturer.user.firstName} ${previewLecturer.user.lastName}`.trim() : '',
  ].filter(Boolean);

  const historyFieldLabel = (field: HistoryField) => {
    const labels: Record<HistoryField, [string, string]> = {
      title: ['Tiêu đề', 'Title'],
      content: ['Nội dung', 'Content'],
      priority: ['Mức ưu tiên', 'Priority'],
      isGlobal: ['Đối tượng', 'Audience'],
      targetRoles: ['Nhóm nhận', 'Audience groups'],
      targetYears: ['Năm học', 'Student years'],
      publishAt: ['Bắt đầu hiển thị', 'Starts showing'],
      expiresAt: ['Ngừng hiển thị', 'Stops showing'],
      semesterId: ['Học kỳ', 'Semester'],
      sectionId: ['Lớp học phần', 'Class section'],
      lecturerId: ['Giảng viên', 'Lecturer'],
      archivedAt: ['Trạng thái', 'Status'],
    };
    return labels[field][vi ? 0 : 1];
  };

  const historyValue = (
    field: HistoryField,
    value: unknown,
    snapshot: Record<string, unknown> | null,
  ) => {
    if (value === undefined || value === null || value === '') return '—';
    if (field === 'priority') return announcementPriorityLabel(value, locale);
    if (field === 'targetRoles') {
      const roles = announcementRoleValues(Array.isArray(value) ? value : []);
      return roles.length > 0 ? roles.map((role) => announcementRoleLabel(role, locale)).join(', ') : copy.noSelection;
    }
    if (field === 'targetYears') return Array.isArray(value) && value.length > 0 ? value.join(', ') : copy.noSelection;
    if (field === 'isGlobal') return value === true ? copy.global : copy.targeted;
    if (field === 'publishAt' || field === 'expiresAt') return typeof value === 'string' ? formatDateTime(value) : '—';
    if (field === 'archivedAt') return value ? copy.statusArchived : copy.statusVisible;
    if (field === 'semesterId') {
      const semester = semesters.find((row) => row.id === value);
      return semester
        ? getLocalizedName(locale, semester, semester.name)
        : typeof snapshot?.semesterName === 'string' && snapshot.semesterName
          ? snapshot.semesterName
          : copy.selectionUnavailable;
    }
    if (field === 'sectionId') {
      const section = sections.find((row) => row.id === value);
      if (section) return `${section.course?.code ?? ''} · ${section.sectionNumber}`.trim();
      const legacyLabel = [snapshot?.courseCode, snapshot?.sectionNumber]
        .filter((part): part is string => typeof part === 'string' && part.length > 0)
        .join(' · ');
      return legacyLabel || copy.selectionUnavailable;
    }
    if (field === 'lecturerId') {
      const lecturer = lecturers.find((row) => row.id === value);
      if (lecturer) return lecturer.user ? `${lecturer.user.firstName} ${lecturer.user.lastName}`.trim() : lecturer.employeeId;
      return typeof snapshot?.lecturerDisplayName === 'string' && snapshot.lecturerDisplayName
        ? snapshot.lecturerDisplayName
        : copy.selectionUnavailable;
    }
    return String(value);
  };

  const historyChanges = (entry: AnnouncementHistoryRecord) => {
    const before = entry.before ?? null;
    const after = entry.after ?? null;
    return historyFields
      .filter((field) => !sameValue(snapshotValue(before, field), snapshotValue(after, field)))
      .map((field) => ({
        field,
        before: historyValue(field, snapshotValue(before, field), before),
        after: historyValue(field, snapshotValue(after, field), after),
      }));
  };

  if (authLoading || isLoggingOut || !canAccess) return <LoadingState label={copy.loading} className="m-8" />;

  return (
    <>
      <AdminFrame
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => void fetchAnnouncements()} disabled={isLoading}>
              <RefreshCw className={`mr-2 h-4 w-4 motion-reduce:animate-none ${isLoading ? 'animate-spin' : ''}`} aria-hidden="true" />
              {copy.refresh}
            </Button>
            <Button type="button" onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
              {copy.create}
            </Button>
          </div>
        }
      >
        <div className="space-y-6">
          <AdminToolbarCard>
            <div className="space-y-5">
              <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <p className="text-sm font-semibold text-foreground">{copy.status}</p>
                  <div className="mt-2 flex flex-wrap gap-2" role="tablist" aria-label={copy.status}>
                    {(['ACTIVE', 'ARCHIVED', 'ALL'] as const).map((status) => {
                      const label = status === 'ACTIVE' ? copy.active : status === 'ARCHIVED' ? copy.archived : copy.all;
                      return (
                        <button
                          key={status}
                          type="button"
                          role="tab"
                          aria-selected={filters.status === status}
                          className={`min-h-11 rounded-md border px-4 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${filters.status === status ? 'border-primary bg-primary text-primary-foreground' : 'border-input bg-background text-foreground hover:bg-secondary'}`}
                          onClick={() => {
                            setFilters((current) => ({ ...current, status }));
                            setPage(1);
                          }}
                        >
                          {label}
                        </button>
                      );
                    })}
                  </div>
                </div>
                <p className="text-sm text-muted-foreground" aria-live="polite">{copy.page(page, totalPages)}</p>
              </div>
              <div className="grid gap-4 md:grid-cols-2">
                <div>
                  <label htmlFor="announcement-filter-semester" className="mb-1 block text-sm font-medium text-foreground">{copy.semester}</label>
                  <Select id="announcement-filter-semester" aria-label={copy.semester} value={filters.semesterId} onChange={(event) => { setFilters((current) => ({ ...current, semesterId: event.target.value })); setPage(1); }} options={semesterOptions} />
                </div>
                <div>
                  <label htmlFor="announcement-filter-priority" className="mb-1 block text-sm font-medium text-foreground">{copy.priority}</label>
                  <Select id="announcement-filter-priority" aria-label={copy.priority} value={filters.priority} onChange={(event) => { setFilters((current) => ({ ...current, priority: event.target.value })); setPage(1); }} options={[{ value: '', label: copy.all }, ...ANNOUNCEMENT_PRIORITIES.map((priority) => ({ value: priority, label: announcementPriorityLabel(priority, locale) }))]} />
                </div>
              </div>
            </div>
          </AdminToolbarCard>

          {referenceError ? (
            <div className="flex items-start gap-3 rounded-md border border-border/70 bg-secondary/35 p-4 text-sm text-muted-foreground" role="status">
              <CalendarClock className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              <span>{referenceError}</span>
            </div>
          ) : null}

          {error ? (
            <ErrorState title={vi ? 'Không thể tải thông báo' : 'Announcements unavailable'} description={error} onRetry={() => void fetchAnnouncements()} />
          ) : isLoading ? (
            <LoadingState label={copy.loading} />
          ) : orderedItems.length === 0 ? (
            <EmptyState icon={Bell} title={copy.empty} description={copy.emptyDescription} action={<Button onClick={openCreate}>{copy.create}</Button>} />
          ) : (
            <AdminTableCard title={copy.feed} contentClassName="space-y-4" footer={<AdminPaginationFooter summary={copy.page(page, totalPages)} page={page} totalPages={totalPages} previousLabel={vi ? 'Trước' : 'Previous'} nextLabel={vi ? 'Sau' : 'Next'} onPrevious={() => setPage((current) => Math.max(1, current - 1))} onNext={() => setPage((current) => Math.min(totalPages, current + 1))} className="mt-0" />}>
              {orderedItems.map((item) => {
                const updated = announcementIsUpdated(item);
                const tone = statusToneClass(announcementPriorityTone(item.priority));
                const audience = announcementAudienceLabel(item, locale);
                const semesterName = announcementSemesterName(item);
                const sectionLabel = announcementSectionLabel(item);
                const lecturerName = announcementLecturerName(item);
                return (
                  <article key={item.id} className="rounded-lg border border-border/70 bg-background/70 p-5 shadow-sm">
                    <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
                      <div className="min-w-0 flex-1 space-y-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${tone}`}>{announcementPriorityLabel(item.priority, locale)}</span>
                          {item.archivedAt ? <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-muted-foreground">{copy.archived}</span> : null}
                          {updated ? <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-muted-foreground">{copy.updated}</span> : null}
                        </div>
                        <div>
                          <h3 className="text-lg font-semibold text-foreground">{item.title}</h3>
                          <p className="mt-2 whitespace-pre-line text-sm leading-7 text-muted-foreground">{item.content}</p>
                        </div>
                        <div className="flex flex-wrap gap-x-4 gap-y-2 text-sm text-muted-foreground">
                          <span>{audience}</span>
                          {semesterName ? <span>{copy.semester}: {semesterName}</span> : null}
                          {sectionLabel ? <span>{copy.section}: {sectionLabel}</span> : null}
                          {lecturerName ? <span>{copy.lecturer}: {lecturerName}</span> : null}
                          <span>{copy.publishAt}: {formatDateTime(item.publishAt || item.createdAt)}</span>
                        </div>
                        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
                          <span>{copy.originalAuthor}</span>
                          {item.version && item.version > 0 ? <span>{copy.revision} {item.version}</span> : null}
                          {item.archivedAt ? <span>{copy.statusArchived} · {formatDateTime(item.archivedAt)}</span> : null}
                        </div>
                      </div>
                      <AdminRowActions className="shrink-0 self-start">
                        <Button type="button" size="icon" variant="ghost" onClick={() => void openHistory(item)} aria-label={`${copy.history}: ${item.title}`} title={copy.history}>
                          <History className="h-4 w-4" aria-hidden="true" />
                        </Button>
                        {!item.archivedAt ? (
                          <>
                            <Button type="button" size="icon" variant="ghost" onClick={() => openEdit(item)} aria-label={`${copy.edit}: ${item.title}`} title={copy.edit} disabled={busy}>
                              <Pencil className="h-4 w-4" aria-hidden="true" />
                            </Button>
                            <Button type="button" size="icon" variant="ghost" onClick={() => openLifecycle(item, 'archive')} aria-label={`${copy.archive}: ${item.title}`} title={copy.archive} disabled={busy}>
                              <Archive className="h-4 w-4" aria-hidden="true" />
                            </Button>
                          </>
                        ) : (
                          <Button type="button" size="icon" variant="ghost" onClick={() => openLifecycle(item, 'restore')} aria-label={`${copy.restore}: ${item.title}`} title={copy.restore} disabled={busy}>
                            <RotateCcw className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        )}
                      </AdminRowActions>
                    </div>
                  </article>
                );
              })}
            </AdminTableCard>
          )}
        </div>
      </AdminFrame>

      {modal === 'editor' ? (
        <Modal isOpen onClose={closeModal} title={editorMode === 'edit' ? copy.edit : copy.create} closeLabel={copy.cancel} className="max-w-4xl">
          <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); void save(); }}>
            <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_20rem]">
              <div className="space-y-5">
                <AdminFormSection title={vi ? 'Nội dung chính' : 'Core content'} description={vi ? 'Giữ tiêu đề ngắn gọn và nội dung dễ đọc trên mọi vai trò.' : 'Keep the title concise and the message readable for every role.'}>
                  <AdminFormField label={`${vi ? 'Tiêu đề' : 'Title'} · ${copy.required}`}>
                    <Input autoFocus value={draft.title} maxLength={240} required onChange={(event) => setDraft((current) => ({ ...current, title: event.target.value }))} />
                  </AdminFormField>
                  <AdminFormField label={`${vi ? 'Nội dung' : 'Content'} · ${copy.required}`} description={vi ? 'Nội dung cập nhật ngay trên bảng tin; không tạo thêm thông báo đẩy.' : 'Changes appear in the feed immediately; no new push notification is created.'}>
                    <Textarea className="min-h-[150px]" value={draft.content} required onChange={(event) => setDraft((current) => ({ ...current, content: event.target.value }))} />
                  </AdminFormField>
                </AdminFormSection>

                <AdminFormSection title={copy.audience} description={vi ? 'Chọn Toàn trường hoặc nhóm cụ thể. Xem trước bên cạnh sẽ phản ánh lựa chọn này.' : 'Choose Campus-wide or specific groups. The preview reflects this choice.'}>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label htmlFor="announcement-editor-priority" className="mb-1 block text-sm font-medium text-foreground">{copy.priority}</label>
                      <Select id="announcement-editor-priority" aria-label={copy.priority} value={draft.priority} onChange={(event) => setDraft((current) => ({ ...current, priority: announcementPriorityValue(event.target.value) }))} options={ANNOUNCEMENT_PRIORITIES.map((priority) => ({ value: priority, label: announcementPriorityLabel(priority, locale) }))} />
                    </div>
                    <label className="inline-flex min-h-11 items-center gap-3 rounded-md border border-border/70 px-3 text-sm font-medium text-foreground">
                      <input type="checkbox" className="h-4 w-4 accent-primary" checked={draft.isGlobal} onChange={(event) => setDraft((current) => ({ ...current, isGlobal: event.target.checked }))} />
                      {copy.global}
                    </label>
                  </div>
                  {!draft.isGlobal ? (
                    <fieldset className="space-y-2">
                      <legend className="text-sm font-medium text-foreground">{copy.targeted}</legend>
                      <div className="grid gap-2 sm:grid-cols-2">
                        {ANNOUNCEMENT_ROLES.map((role) => (
                          <label key={role} className="inline-flex min-h-11 items-center gap-3 rounded-md border border-border/70 px-3 text-sm text-foreground hover:bg-secondary/50">
                            <input type="checkbox" className="h-4 w-4 accent-primary" checked={draft.targetRoles.includes(role)} onChange={(event) => setDraft((current) => ({ ...current, targetRoles: event.target.checked ? [...new Set([...current.targetRoles, role])] : current.targetRoles.filter((value) => value !== role) }))} />
                            {announcementRoleLabel(role, locale)}
                          </label>
                        ))}
                      </div>
                    </fieldset>
                  ) : null}
                  <AdminFormField label={copy.targetYears} description={copy.targetYearsHint}>
                    <Input inputMode="numeric" value={draft.targetYears} placeholder={vi ? 'Ví dụ: 1, 2, 3' : 'For example: 1, 2, 3'} onChange={(event) => setDraft((current) => ({ ...current, targetYears: event.target.value }))} />
                  </AdminFormField>
                </AdminFormSection>

                <AdminFormSection title={vi ? 'Phạm vi học vụ' : 'Academic scope'} description={vi ? 'Các lựa chọn này giúp thông báo đi đúng học kỳ, lớp và giảng viên.' : 'These choices keep the notice aligned with a semester, class, and lecturer.'}>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <div>
                      <label htmlFor="announcement-editor-semester" className="mb-1 block text-sm font-medium text-foreground">{copy.semester}</label>
                      <Select id="announcement-editor-semester" aria-label={copy.semester} value={draft.semesterId} onChange={(event) => setDraft((current) => ({ ...current, semesterId: event.target.value, sectionId: current.sectionId && sections.some((section) => section.id === current.sectionId && section.semesterId === event.target.value) ? current.sectionId : '' }))} options={editorSemesterOptions} />
                    </div>
                    <div>
                      <label htmlFor="announcement-editor-section" className="mb-1 block text-sm font-medium text-foreground">{copy.section}</label>
                      <Select id="announcement-editor-section" aria-label={copy.section} value={draft.sectionId} onChange={(event) => {
                        const section = sections.find((row) => row.id === event.target.value);
                        setDraft((current) => ({ ...current, sectionId: event.target.value, semesterId: current.semesterId || section?.semesterId || '', lecturerId: current.lecturerId || section?.lecturerId || '' }));
                      }} options={editorSectionOptions} />
                    </div>
                    <div className="sm:col-span-2">
                      <label htmlFor="announcement-editor-lecturer" className="mb-1 block text-sm font-medium text-foreground">{copy.lecturer}</label>
                      <Select id="announcement-editor-lecturer" aria-label={copy.lecturer} value={draft.lecturerId} onChange={(event) => setDraft((current) => ({ ...current, lecturerId: event.target.value }))} options={editorLecturerOptions} />
                    </div>
                  </div>
                  <p className="text-sm text-muted-foreground">{copy.sectionHint} {copy.lecturerHint}</p>
                </AdminFormSection>

                <AdminFormSection title={copy.schedule} description={vi ? 'Bỏ trống ngày kết thúc nếu thông báo không có hạn.' : 'Leave the end date empty when the notice has no expiry.'}>
                  <div className="grid gap-4 sm:grid-cols-2">
                    <AdminFormField label={copy.publishAt}>
                      <Input type="datetime-local" value={draft.publishAt} onChange={(event) => setDraft((current) => ({ ...current, publishAt: event.target.value }))} />
                    </AdminFormField>
                    <AdminFormField label={copy.expiresAt} description={!draft.expiresAt ? copy.noEnd : undefined}>
                      <Input type="datetime-local" value={draft.expiresAt} onChange={(event) => setDraft((current) => ({ ...current, expiresAt: event.target.value }))} />
                    </AdminFormField>
                  </div>
                </AdminFormSection>

                {editorMode === 'edit' ? (
                  <AdminFormSection title={copy.reason} description={copy.reasonHint}>
                    <AdminFormField label={`${copy.reason} · ${copy.required}`}>
                      <Textarea className="min-h-[110px]" value={draft.reason} maxLength={500} required aria-describedby="announcement-reason-count" onChange={(event) => setDraft((current) => ({ ...current, reason: event.target.value }))} />
                    </AdminFormField>
                    <p id="announcement-reason-count" className="text-right text-xs text-muted-foreground" aria-live="polite">{Array.from(draft.reason).length} / 500</p>
                  </AdminFormSection>
                ) : null}
              </div>

              <aside className="space-y-4 xl:sticky xl:top-2 xl:self-start">
                <Card variant="muted" className="overflow-hidden">
                  <CardHeader className="border-b border-border/70">
                    <div className="flex items-center gap-2">
                      <ShieldCheck className="h-4 w-4 text-primary" aria-hidden="true" />
                      <CardTitle>{copy.preview}</CardTitle>
                    </div>
                    <CardDescription>{vi ? 'Kiểm tra nhanh nội dung sẽ xuất hiện với từng vai trò.' : 'Check how the notice will appear for each role.'}</CardDescription>
                    <div className="mt-3 grid grid-cols-2 gap-2" role="tablist" aria-label={copy.preview}>
                      {(['STUDENT', 'LECTURER'] as const).map((role) => (
                        <button key={role} type="button" role="tab" aria-selected={previewRole === role} className={`min-h-11 rounded-md border px-3 text-sm font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${previewRole === role ? 'border-primary bg-primary text-primary-foreground' : 'border-input bg-background text-foreground hover:bg-secondary'}`} onClick={() => setPreviewRole(role)}>
                          {role === 'STUDENT' ? copy.previewStudent : copy.previewLecturer}
                        </button>
                      ))}
                    </div>
                    {previewRole === 'STUDENT' ? (
                      <AdminFormField label={copy.studentYear} className="mt-3">
                        <Input type="number" min={1} step={1} value={previewYear} onChange={(event) => setPreviewYear(event.target.value)} />
                      </AdminFormField>
                    ) : null}
                  </CardHeader>
                  <CardContent className="space-y-4 pt-5">
                    <div className={`rounded-md border p-4 ${previewVisible ? 'border-primary/30 bg-background' : 'border-border/70 bg-secondary/30'}`} aria-live="polite">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${statusToneClass(announcementPriorityTone(draft.priority))}`}>{announcementPriorityLabel(draft.priority, locale)}</span>
                        <span className="text-xs font-medium text-muted-foreground">{previewVisible ? copy.visible : copy.notVisible}</span>
                      </div>
                      <h3 className="mt-3 text-base font-semibold text-foreground">{draft.title || (vi ? 'Tiêu đề thông báo' : 'Announcement title')}</h3>
                      <p className="mt-2 whitespace-pre-line text-sm leading-6 text-muted-foreground">{draft.content || (vi ? 'Nội dung xem trước sẽ hiển thị tại đây.' : 'Preview content will appear here.')}</p>
                      {previewMeta.length > 0 ? <p className="mt-3 text-xs text-muted-foreground">{previewMeta.join(' · ')}</p> : null}
                    </div>
                    <div className="rounded-md border border-border/70 bg-background/70 p-4 text-sm">
                      <p className="font-semibold text-foreground">{announcementRoleLabel(previewRole, locale)}</p>
                      <p className="mt-1 text-muted-foreground">{announcementAudienceLabel({ isGlobal: draft.isGlobal, targetRoles: draft.targetRoles }, locale)}</p>
                    </div>
                  </CardContent>
                </Card>
              </aside>
            </div>
            <AdminDialogFooter className="border-t border-border/70 pt-4">
              <Button type="button" variant="outline" onClick={closeModal} disabled={busy}>{copy.cancel}</Button>
              <Button type="submit" disabled={busy}>{busy ? (vi ? 'Đang lưu…' : 'Saving…') : editorMode === 'edit' ? copy.save : copy.createSave}</Button>
            </AdminDialogFooter>
          </form>
        </Modal>
      ) : null}

      {modal === 'lifecycle' && lifecycle ? (
        <Modal isOpen onClose={closeModal} title={lifecycle.action === 'archive' ? copy.archiveTitle : copy.restoreTitle} closeLabel={copy.cancel}>
          <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); void submitLifecycle(); }}>
            <p className="text-sm leading-6 text-muted-foreground">{lifecycle.action === 'archive' ? copy.archiveMessage : copy.restoreMessage}</p>
            <div className="rounded-md border border-border/70 bg-secondary/25 p-4">
              <p className="font-semibold text-foreground">{lifecycle.item.title}</p>
              <p className="mt-1 text-sm text-muted-foreground">{announcementAudienceLabel(lifecycle.item, locale)}</p>
            </div>
            <AdminFormField label={`${copy.reason} · ${copy.required}`} description={copy.reasonHint}>
              <Textarea autoFocus className="min-h-[110px]" value={lifecycleReason} maxLength={500} required onChange={(event) => setLifecycleReason(event.target.value)} />
            </AdminFormField>
            <p className="text-right text-xs text-muted-foreground" aria-live="polite">{Array.from(lifecycleReason).length} / 500</p>
            <AdminDialogFooter>
              <Button type="button" variant="outline" onClick={closeModal} disabled={busy}>{copy.cancel}</Button>
              <Button type="submit" disabled={busy}>{busy ? (vi ? 'Đang xử lý…' : 'Working…') : lifecycle.action === 'archive' ? copy.confirmArchive : copy.confirmRestore}</Button>
            </AdminDialogFooter>
          </form>
        </Modal>
      ) : null}

      {modal === 'history' && selected ? (
        <Modal isOpen onClose={closeModal} title={copy.history} closeLabel={copy.cancel} className="max-w-3xl">
          <div className="space-y-4">
            <div className="rounded-md border border-border/70 bg-secondary/25 p-4">
              <h3 className="font-semibold text-foreground">{selected.title}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{announcementAudienceLabel(selected, locale)}</p>
            </div>
            {historyError ? <ErrorState title={copy.historyUnavailable} description={historyError} onRetry={() => void openHistory(selected)} /> : null}
            {historyLoading ? <LoadingState label={copy.history} className="min-h-[150px]" /> : history.length === 0 && !historyError ? <p className="rounded-md border border-dashed border-border p-6 text-center text-sm text-muted-foreground">{copy.historyEmpty}</p> : null}
            {!historyLoading ? (
              <ol className="space-y-4" aria-label={copy.history}>
                {history.map((entry) => {
                  const changes = historyChanges(entry);
                  return (
                    <li key={entry.id} className="rounded-lg border border-border/70 bg-background/70 p-4">
                      <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                        <div>
                          <p className="font-semibold text-foreground">{announcementHistoryActionLabel(entry.action, locale)}</p>
                          <p className="mt-1 text-xs text-muted-foreground">{copy.actor}: {entry.actorLabel?.trim() || actorDescription(locale)} · {copy.version} {entry.version} · {formatDateTime(entry.createdAt)}</p>
                        </div>
                        <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-muted-foreground">{announcementHistoryReason(entry.reason, entry.action, locale)}</span>
                      </div>
                      <div className="mt-4 space-y-2">
                        <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">{copy.changedFields}</p>
                        {changes.length === 0 ? <p className="text-sm text-muted-foreground">{copy.noFieldChange}</p> : (
                          <dl className="space-y-2">
                            {changes.map((change) => (
                              <div key={change.field} className="grid gap-1 rounded-md border border-border/60 p-3 sm:grid-cols-[8rem_minmax(0,1fr)]">
                                <dt className="text-sm font-medium text-foreground">{historyFieldLabel(change.field)}</dt>
                                <dd className="grid gap-2 text-sm sm:grid-cols-2">
                                  <span className="min-w-0 whitespace-pre-line break-words text-muted-foreground"><span className="font-medium text-foreground">{copy.before}: </span>{change.before}</span>
                                  <span className="min-w-0 whitespace-pre-line break-words text-foreground"><span className="font-medium">{copy.after}: </span>{change.after}</span>
                                </dd>
                              </div>
                            ))}
                          </dl>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ol>
            ) : null}
            <AdminDialogFooter>
              <Button type="button" variant="outline" onClick={closeModal}>{copy.close}</Button>
            </AdminDialogFooter>
          </div>
        </Modal>
      ) : null}
      {confirmationDialog}
    </>
  );
}
