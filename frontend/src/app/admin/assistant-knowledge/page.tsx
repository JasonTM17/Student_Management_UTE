'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Archive,
  BookOpenCheck,
  Check,
  Edit3,
  FileText,
  Plus,
  Send,
  ShieldCheck,
  UploadCloud,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminFormField,
  AdminMetricCard,
  AdminTableCard,
  AdminTableScroll,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ConfirmModal, Modal } from '@/components/ui/modal';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { EmptyState, ForbiddenState, LoadingState } from '@/components/ui/state-block';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import { LinkButton } from '@/components/ui/link-button';
import {
  assistantKnowledgeApi,
  type AssistantCatalogCoverage,
  type AssistantKnowledgeDocument,
  type AssistantKnowledgeDomain,
  type AssistantKnowledgeRequest,
  type AssistantKnowledgeState,
} from '@/lib/thesis-api';

// Filter option lists carry both locales so one source feeds the filter
// selects, the table badges, and the form — no per-option ternary chains.
const states: Array<{ value: '' | AssistantKnowledgeState; en: string; vi: string }> = [
  { value: '', en: 'All states', vi: 'Tất cả trạng thái' },
  { value: 'DRAFT', en: 'Draft', vi: 'Bản nháp' },
  { value: 'PENDING_REVIEW', en: 'Pending review', vi: 'Chờ duyệt' },
  { value: 'PUBLISHED', en: 'Published', vi: 'Đã xuất bản' },
  { value: 'ARCHIVED', en: 'Archived', vi: 'Đã lưu trữ' },
];
const domains: Array<{ value: '' | AssistantKnowledgeDomain; en: string; vi: string }> = [
  { value: '', en: 'All domains', vi: 'Tất cả lĩnh vực' },
  { value: 'THESIS', en: 'Thesis', vi: 'Luận văn' },
  { value: 'REGISTRATION', en: 'Registration', vi: 'Đăng ký học phần' },
  { value: 'ACADEMIC_CATALOG', en: 'Academic catalog', vi: 'Danh mục học vụ' },
  { value: 'ANNOUNCEMENT', en: 'Announcements', vi: 'Thông báo' },
  { value: 'POLICY', en: 'Policy', vi: 'Chính sách' },
  { value: 'GENERAL_FAQ', en: 'Campus FAQ', vi: 'Câu hỏi thường gặp' },
  { value: 'SPECIALIZED', en: 'Specialized knowledge', vi: 'Kiến thức chuyên ngành' },
];
const coverageSections: Array<{ key: keyof AssistantCatalogCoverage; en: string; vi: string }> = [
  { key: 'departments', en: 'Departments', vi: 'Khoa' },
  { key: 'courses', en: 'Courses', vi: 'Môn học' },
  { key: 'curricula', en: 'Curricula', vi: 'Chương trình' },
  { key: 'semesters', en: 'Semesters', vi: 'Học kỳ' },
];

const blankForm: AssistantKnowledgeRequest = {
  slug: '',
  locale: 'both',
  title: '',
  content: '',
  source: 'CampusUTE academic policy',
  domain: 'GENERAL_FAQ',
  priority: 100,
};

function actionError(error: unknown, locale: 'en' | 'vi') {
  const response = (error as { response?: { status?: number; data?: { code?: string } } })?.response;
  if (response?.status === 409 || response?.data?.code === 'KNOWLEDGE_SECOND_REVIEW_REQUIRED') {
    return locale === 'vi'
      ? 'Bản nháp cần một quản trị viên khác duyệt xuất bản. Hãy tải lại để xem trạng thái mới.'
      : 'A different administrator must publish this revision. Reload to see the latest state.';
  }
  if (response?.status === 403) {
    return locale === 'vi' ? 'Bạn không có quyền thực hiện thao tác này.' : 'You are not allowed to perform this action.';
  }
  return locale === 'vi' ? 'Không thể hoàn thành thao tác. Vui lòng thử lại.' : 'The action could not be completed. Please try again.';
}

function stateClass(state: string) {
  if (state === 'PUBLISHED') return statusToneClass('success');
  if (state === 'PENDING_REVIEW') return statusToneClass('warning');
  if (state === 'ARCHIVED') return statusToneClass('neutral');
  return statusToneClass('info');
}

function stateLabel(state: string, vi: boolean) {
  const option = states.find((item) => item.value === state);
  return option ? (vi ? option.vi : option.en) : (vi ? 'Chưa xác định' : 'Unknown');
}

function domainLabel(domain: string | undefined, vi: boolean) {
  const option = domains.find((item) => item.value === domain);
  return option ? (vi ? option.vi : option.en) : (vi ? 'Khác' : 'Other');
}

function localizedOptions(
  items: Array<{ value: '' | AssistantKnowledgeState | AssistantKnowledgeDomain; en: string; vi: string }>,
  vi: boolean,
) {
  return items.map((item) => ({ value: item.value, label: vi ? item.vi : item.en }));
}

interface CatalogCoverageCardProps {
  coverage: AssistantCatalogCoverage | null;
  coverageError: boolean;
  vi: boolean;
}

function CatalogCoverageCard({ coverage, coverageError, vi }: CatalogCoverageCardProps) {
  return (
    <Card variant="muted">
      <CardHeader className="pb-3">
        <CardTitle className="flex items-center gap-2">
          <BookOpenCheck className="h-5 w-5 text-primary" aria-hidden="true" />
          {vi ? 'Phạm vi nội dung CampusUTE công khai' : 'Public CampusUTE guidance'}
        </CardTitle>
        <CardDescription>
          {vi
            ? 'Chỉ hiển thị số lượng nội dung học thuật công khai. Không bao gồm hồ sơ, điểm, điểm danh, lịch cá nhân hoặc danh sách lớp.'
            : 'A quick count of public academic guidance. Personal records, grades, attendance, schedules, and class lists are not included.'}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {coverage ? (
          <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
            {coverageSections.map((section) => (
              <div key={section.key} className="border border-border/70 bg-background px-3 py-2">
                <div className="text-xs text-muted-foreground">{vi ? section.vi : section.en}</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-foreground">
                  {coverage[section.key]}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-sm text-muted-foreground">
            {coverageError
              ? vi
                ? 'Phạm vi thông tin hiện chưa khả dụng. Nội dung đã kiểm duyệt vẫn hoạt động.'
                : 'Public information counts are currently unavailable. Reviewed guidance remains available.'
              : vi
                ? 'Đang tải thông tin...'
                : 'Loading information...'}
          </p>
        )}
      </CardContent>
    </Card>
  );
}

interface GuidanceFiltersProps {
  domainFilter: '' | AssistantKnowledgeDomain;
  stateFilter: '' | AssistantKnowledgeState;
  onDomainChange: (value: '' | AssistantKnowledgeDomain) => void;
  onStateChange: (value: '' | AssistantKnowledgeState) => void;
  onReload: () => void;
  isLoading: boolean;
  vi: boolean;
}

function GuidanceFilters({
  domainFilter,
  stateFilter,
  onDomainChange,
  onStateChange,
  onReload,
  isLoading,
  vi,
}: GuidanceFiltersProps) {
  return (
    <div className="mb-4 flex flex-wrap items-end justify-between gap-3 border-b border-border/70 pb-4">
      <div className="flex flex-wrap items-end gap-3">
        <div className="w-48">
          <Select
            label={vi ? 'Lĩnh vực' : 'Domain'}
            value={domainFilter}
            onChange={(event) => onDomainChange(event.target.value as '' | AssistantKnowledgeDomain)}
            options={localizedOptions(domains, vi)}
            aria-label={vi ? 'Lọc lĩnh vực' : 'Filter domain'}
          />
        </div>
        <div className="w-48">
          <Select
            label={vi ? 'Trạng thái' : 'Status'}
            value={stateFilter}
            onChange={(event) => onStateChange(event.target.value as '' | AssistantKnowledgeState)}
            options={localizedOptions(states, vi)}
            aria-label={vi ? 'Lọc trạng thái' : 'Filter status'}
          />
        </div>
      </div>
      <Button type="button" variant="outline" onClick={onReload} disabled={isLoading}>
        {vi ? 'Tải lại' : 'Reload'}
      </Button>
    </div>
  );
}

interface GuidanceTableProps {
  documents: AssistantKnowledgeDocument[];
  isSaving: boolean;
  vi: boolean;
  onEdit: (document: AssistantKnowledgeDocument) => void;
  onTransition: (document: AssistantKnowledgeDocument, operation: 'submit' | 'publish') => void;
  onArchiveRequest: (document: AssistantKnowledgeDocument) => void;
}

function GuidanceTable({
  documents,
  isSaving,
  vi,
  onEdit,
  onTransition,
  onArchiveRequest,
}: GuidanceTableProps) {
  return (
    <AdminTableScroll>
      <table className="w-full min-w-[760px] text-left text-sm">
        <thead className="bg-secondary text-xs uppercase tracking-[0.08em] text-muted-foreground">
          <tr>
            <th className="px-3 py-3 font-semibold">{vi ? 'Nội dung' : 'Guidance'}</th>
            <th className="px-3 py-3 font-semibold">{vi ? 'Lĩnh vực' : 'Domain'}</th>
            <th className="px-3 py-3 font-semibold">{vi ? 'Trạng thái' : 'Status'}</th>
            <th className="px-3 py-3 font-semibold">{vi ? 'Lần cập nhật' : 'Revision'}</th>
            <th className="px-3 py-3 text-right font-semibold">{vi ? 'Thao tác' : 'Actions'}</th>
          </tr>
        </thead>
        <tbody>
          {documents.map((document) => (
            <tr key={document.documentId} className="border-b border-border/60 align-top last:border-0">
              <td className="max-w-[420px] px-3 py-4">
                <div className="font-semibold text-foreground">{document.title}</div>
                <div className="mt-1 truncate text-xs text-muted-foreground">{document.source}</div>
              </td>
              <td className="px-3 py-4">
                <span className="inline-flex rounded-md bg-secondary px-2.5 py-1 text-xs font-semibold text-secondary-foreground">
                  {domainLabel(document.domain, vi)}
                </span>
              </td>
              <td className="px-3 py-4">
                <span className={`inline-flex rounded-md px-2.5 py-1 text-xs font-semibold ${stateClass(document.state)}`}>
                  {stateLabel(document.state, vi)}
                </span>
              </td>
              <td className="px-3 py-4 tabular-nums text-muted-foreground">v{document.version}</td>
              <td className="px-3 py-4">
                <div className="flex flex-wrap justify-end gap-2">
                  <Button type="button" size="sm" variant="outline" onClick={() => onEdit(document)} disabled={isSaving}>
                    <Edit3 className="mr-1.5 h-4 w-4" aria-hidden="true" />
                    {vi ? 'Sửa' : 'Edit'}
                  </Button>
                  {document.state === 'DRAFT' ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      onClick={() => onTransition(document, 'submit')}
                      disabled={isSaving}
                    >
                      <Send className="mr-1.5 h-4 w-4" aria-hidden="true" />
                      {vi ? 'Gửi duyệt' : 'Send for review'}
                    </Button>
                  ) : null}
                  {document.state === 'PENDING_REVIEW' ? (
                    <Button
                      type="button"
                      size="sm"
                      onClick={() => onTransition(document, 'publish')}
                      disabled={isSaving}
                    >
                      <ShieldCheck className="mr-1.5 h-4 w-4" aria-hidden="true" />
                      {vi ? 'Xuất bản' : 'Release'}
                    </Button>
                  ) : null}
                  {document.state !== 'ARCHIVED' ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="ghost"
                      onClick={() => onArchiveRequest(document)}
                      disabled={isSaving}
                    >
                      <Archive className="mr-1.5 h-4 w-4" aria-hidden="true" />
                      {vi ? 'Lưu trữ' : 'Archive'}
                    </Button>
                  ) : null}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </AdminTableScroll>
  );
}

interface GuidanceFormModalProps {
  isOpen: boolean;
  isSaving: boolean;
  editing: boolean;
  form: AssistantKnowledgeRequest;
  onChange: (patch: Partial<AssistantKnowledgeRequest>) => void;
  onClose: () => void;
  onSave: () => void;
  vi: boolean;
}

function GuidanceFormModal({
  isOpen,
  isSaving,
  editing,
  form,
  onChange,
  onClose,
  onSave,
  vi,
}: GuidanceFormModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={
        editing
          ? vi
            ? 'Sửa nội dung đã kiểm duyệt'
            : 'Edit reviewed guidance'
          : vi
            ? 'Tạo nội dung mới'
            : 'Add guidance'
      }
      className="max-w-4xl"
    >
      <div className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <AdminFormField label={vi ? 'Mã nội dung' : 'Guidance code'}>
            <Input
              value={form.slug}
              onChange={(event) => onChange({ slug: event.target.value })}
              placeholder="registration-window"
            />
          </AdminFormField>
          <AdminFormField label={vi ? 'Lĩnh vực' : 'Domain'}>
            <Select
              value={form.domain ?? 'GENERAL_FAQ'}
              onChange={(event) => onChange({ domain: event.target.value as AssistantKnowledgeDomain })}
              options={localizedOptions(domains.filter((option) => option.value !== ''), vi)}
            />
          </AdminFormField>
          <AdminFormField label={vi ? 'Ngôn ngữ' : 'Language'}>
            <Select
              value={form.locale}
              onChange={(event) => onChange({ locale: event.target.value as AssistantKnowledgeRequest['locale'] })}
              options={[
                { value: 'both', label: vi ? 'Cả hai' : 'Both' },
                { value: 'vi', label: 'Tiếng Việt' },
                { value: 'en', label: 'English' },
              ]}
            />
          </AdminFormField>
        </div>

        <AdminFormField label={vi ? 'Tiêu đề' : 'Title'}>
          <Input value={form.title} onChange={(event) => onChange({ title: event.target.value })} />
        </AdminFormField>

        <AdminFormField label={vi ? 'Nguồn tham chiếu' : 'Reference'}>
          <Input value={form.source} onChange={(event) => onChange({ source: event.target.value })} />
        </AdminFormField>

        <div className="space-y-2 text-sm font-medium text-foreground">
          <span>{vi ? 'Nội dung quy chế / tri thức' : 'Guidance content'}</span>
          <RichTextEditor
            value={form.content}
            onChange={(newContent) => onChange({ content: newContent })}
            locale={vi ? 'vi' : 'en'}
            minHeight="240px"
            hint={
              vi
                ? 'Chỉ nhập thông tin học vụ công khai. Không thêm hồ sơ hoặc dữ liệu cá nhân.'
                : 'Only public campus information. Do not add profiles or personal data.'
            }
            placeholder={
              vi
                ? 'Biên soạn nội dung quy chế hoặc hướng dẫn học vụ... (Hỗ trợ tiêu đề, bảng biểu, danh sách, callout)'
                : 'Draft academic guidance content... (Supports headings, tables, lists, callouts)'
            }
          />
        </div>

        <div className="flex justify-end gap-2 border-t border-border/70 pt-4">
          <Button type="button" variant="outline" onClick={onClose} disabled={isSaving}>
            {vi ? 'Hủy' : 'Cancel'}
          </Button>
          <Button type="button" onClick={onSave} disabled={isSaving}>
            {isSaving ? (vi ? 'Đang lưu...' : 'Saving...') : (vi ? 'Lưu bản nháp' : 'Save draft')}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default function AdminAssistantKnowledgePage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { locale } = useI18n();
  const vi = locale === 'vi';
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const [documents, setDocuments] = useState<AssistantKnowledgeDocument[]>([]);
  const [coverage, setCoverage] = useState<AssistantCatalogCoverage | null>(null);
  const [coverageError, setCoverageError] = useState(false);
  const [stateFilter, setStateFilter] = useState<'' | AssistantKnowledgeState>('');
  const [domainFilter, setDomainFilter] = useState<'' | AssistantKnowledgeDomain>('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<AssistantKnowledgeDocument | null>(null);
  const [form, setForm] = useState<AssistantKnowledgeRequest>(blankForm);
  const [archiveTarget, setArchiveTarget] = useState<AssistantKnowledgeDocument | null>(null);

  const load = useCallback(async () => {
    setIsLoading(true);
    setError('');
    setCoverageError(false);
    try {
      const [rows, catalog] = await Promise.all([
        assistantKnowledgeApi.list({ ...(domainFilter ? { domain: domainFilter } : {}), ...(stateFilter ? { state: stateFilter } : {}) }),
        assistantKnowledgeApi.getCatalogCoverage(),
      ]);
      setDocuments(rows);
      setCoverage(catalog);
    } catch (loadError) {
      // Keep curated and catalog surfaces independently truthful when the optional coverage read is unavailable.
      try {
        const rows = await assistantKnowledgeApi.list({ ...(domainFilter ? { domain: domainFilter } : {}), ...(stateFilter ? { state: stateFilter } : {}) });
        setDocuments(rows);
        setCoverageError(true);
      } catch {
        setError(actionError(loadError, locale));
      }
    } finally {
      setIsLoading(false);
    }
  }, [domainFilter, locale, stateFilter]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const publishedCount = useMemo(() => documents.filter((row) => row.state === 'PUBLISHED').length, [documents]);
  const pendingCount = useMemo(() => documents.filter((row) => row.state === 'PENDING_REVIEW').length, [documents]);

  const updateForm = useCallback((patch: Partial<AssistantKnowledgeRequest>) => {
    setForm((current) => ({ ...current, ...patch }));
  }, []);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...blankForm });
    setNotice('');
    setError('');
    setFormOpen(true);
  };

  const openEdit = async (document: AssistantKnowledgeDocument) => {
    setEditing(document);
    setNotice('');
    setError('');
    try {
      const fullDoc = await assistantKnowledgeApi.get(document.documentId);
      setEditing(fullDoc);
      setForm({
        slug: fullDoc.slug,
        locale: fullDoc.locale === 'vi' || fullDoc.locale === 'en' ? fullDoc.locale : 'both',
        title: fullDoc.title,
        content: fullDoc.content,
        source: fullDoc.source,
        domain: (fullDoc.domain as AssistantKnowledgeDomain) || 'GENERAL_FAQ',
        priority: fullDoc.priority,
      });
    } catch {
      // The list row deliberately carries content '' — opening the editor on
      // it would show a blank document and a save would clobber the real one.
      setEditing(null);
      setFormOpen(false);
      setError(vi
        ? 'Không tải được nội dung đầy đủ của tài liệu. Vui lòng thử lại.'
        : 'Could not load the full document. Please retry.');
      return;
    }
    setFormOpen(true);
  };

  const save = async () => {
    if (!form.slug.trim() || !form.title.trim() || !form.content.trim() || !form.source.trim()) {
      setError(vi ? 'Vui lòng điền mã nội dung, tiêu đề, nội dung và nguồn tham chiếu.' : 'Guidance code, title, content, and reference are required.');
      return;
    }
    setIsSaving(true);
    setError('');
    try {
      if (editing) await assistantKnowledgeApi.update(editing.documentId, form);
      else await assistantKnowledgeApi.create(form);
      setFormOpen(false);
      setNotice(vi ? 'Đã lưu bản nháp.' : 'Draft saved.');
      await load();
    } catch (saveError) {
      setError(actionError(saveError, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const transition = async (document: AssistantKnowledgeDocument, operation: 'submit' | 'publish') => {
    setIsSaving(true);
    setError('');
    setNotice('');
    try {
      if (operation === 'submit') {
        await assistantKnowledgeApi.submit(document.documentId);
        setNotice(vi ? 'Đã gửi xét duyệt.' : 'Submitted for review.');
      } else {
        const result = await assistantKnowledgeApi.publish(document.documentId);
        const pendingProjection = result.sync && (result.sync.degraded || result.sync.status !== 'ACTIVATED');
        setNotice(pendingProjection
          ? (vi ? 'Đã xuất bản; trợ lý chưa cập nhật. Hãy kiểm tra trạng thái đồng bộ.' : 'Published; the assistant has not updated yet. Check sync status.')
          : (vi ? 'Đã xuất bản nội dung cho trợ lý.' : 'Guidance published for the assistant.'));
      }
      await load();
    } catch (transitionError) {
      setError(actionError(transitionError, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const archive = async () => {
    if (!archiveTarget) return;
    setIsSaving(true);
    setError('');
    try {
      const sync = await assistantKnowledgeApi.archive(archiveTarget.documentId);
      const pendingProjection = sync && (sync.degraded || (archiveTarget.state === 'PUBLISHED' && sync.status !== 'ACTIVATED'));
      setArchiveTarget(null);
      setNotice(pendingProjection
        ? (vi ? 'Đã lưu trữ trong kho quản trị; trợ lý chưa cập nhật. Hãy kiểm tra trạng thái đồng bộ.' : 'Archived in administration; the assistant has not updated yet. Check sync status.')
        : (vi ? 'Đã lưu trữ nội dung. Lịch sử thay đổi vẫn được giữ.' : 'Guidance archived. Its change history remains available.'));
      await load();
    } catch (archiveError) {
      setError(actionError(archiveError, locale));
    } finally {
      setIsSaving(false);
    }
  };

  const frameTitle = vi ? 'Kho kiến thức CampusUTE' : 'CampusUTE knowledge';
  const frameDescription = vi
    ? 'Quản trị nội dung học vụ công khai cho trợ lý.'
    : 'Manage public campus guidance for the assistant.';

  if (authLoading || isLoggingOut) {
    return (
      <AdminFrame title={frameTitle} description={frameDescription}>
        <LoadingState />
      </AdminFrame>
    );
  }

  if (!user || !canAccess) {
    return (
      <AdminFrame title={frameTitle} description={frameDescription}>
        <ForbiddenState
          title={vi ? 'Không có quyền truy cập' : 'Access restricted'}
          description={vi ? 'Chỉ quản trị viên có thể quản lý nội dung đã công bố.' : 'Only administrators can manage published guidance.'}
          action={
            <LinkButton href="/dashboard" variant="outline">
              {vi ? 'Về trang tổng quan' : 'Return to overview'}
            </LinkButton>
          }
        />
      </AdminFrame>
    );
  }

  return (
    <AdminFrame
      title={frameTitle}
      description={vi
        ? 'Quản trị nội dung học vụ công khai theo quy trình bản nháp, duyệt và xuất bản.'
        : 'Manage public campus guidance through draft, review, and release states.'}
      actions={
        <Button type="button" onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" aria-hidden="true" />
          {vi ? 'Tạo nội dung' : 'Add guidance'}
        </Button>
      }
    >
      {error ? (
        <div role="alert" className="mb-5 border border-destructive/35 bg-destructive/5 px-4 py-3 text-sm text-destructive">
          {error}{' '}
          <button
            type="button"
            className="ml-2 rounded font-semibold underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            onClick={() => void load()}
          >
            {vi ? 'Thử lại' : 'Retry'}
          </button>
        </div>
      ) : null}
      {notice ? (
        <div
          role="status"
          className="mb-5 flex items-center gap-2 border border-status-success/30 bg-status-success/15 px-4 py-3 text-sm text-status-success-foreground"
        >
          <Check className="h-4 w-4" aria-hidden="true" />
          {notice}
        </div>
      ) : null}

      <div className="space-y-6">
        <div className="grid gap-3 sm:grid-cols-3">
          <AdminMetricCard
            label={vi ? 'Tổng nội dung' : 'Total guidance'}
            value={documents.length}
            icon={<FileText className="h-5 w-5" />}
            toneClassName={metricToneClass('info')}
            compact
          />
          <AdminMetricCard
            label={vi ? 'Đã xuất bản' : 'Published'}
            value={publishedCount}
            icon={<ShieldCheck className="h-5 w-5" />}
            toneClassName={metricToneClass('success')}
            compact
          />
          <AdminMetricCard
            label={vi ? 'Chờ duyệt' : 'Pending review'}
            value={pendingCount}
            icon={<UploadCloud className="h-5 w-5" />}
            toneClassName={metricToneClass('warning')}
            compact
          />
        </div>

        <CatalogCoverageCard coverage={coverage} coverageError={coverageError} vi={vi} />

        <AdminTableCard
          title={vi ? 'Nội dung đã kiểm duyệt' : 'Reviewed guidance'}
          description={
            vi
              ? 'Nội dung được kiểm duyệt trước khi trợ lý sử dụng. Nội dung đã lưu trữ vẫn giữ lịch sử thay đổi.'
              : 'Guidance is reviewed before the assistant uses it. Archived items keep their change history.'
          }
        >
          <GuidanceFilters
            domainFilter={domainFilter}
            stateFilter={stateFilter}
            onDomainChange={setDomainFilter}
            onStateChange={setStateFilter}
            onReload={() => void load()}
            isLoading={isLoading}
            vi={vi}
          />
          {isLoading && documents.length === 0 ? (
            <LoadingState label={vi ? 'Đang tải nội dung...' : 'Loading guidance...'} />
          ) : documents.length === 0 ? (
            <EmptyState
              title={vi ? 'Chưa có nội dung phù hợp.' : 'No matching guidance.'}
              description={
                vi
                  ? 'Nội dung mới sẽ xuất hiện tại đây sau khi được tạo hoặc khi bộ lọc thay đổi.'
                  : 'New guidance will appear here once created or when the filters change.'
              }
            />
          ) : (
            <GuidanceTable
              documents={documents}
              isSaving={isSaving}
              vi={vi}
              onEdit={(document) => void openEdit(document)}
              onTransition={(document, operation) => void transition(document, operation)}
              onArchiveRequest={setArchiveTarget}
            />
          )}
        </AdminTableCard>
      </div>

      <GuidanceFormModal
        isOpen={formOpen}
        isSaving={isSaving}
        editing={Boolean(editing)}
        form={form}
        onChange={updateForm}
        onClose={() => setFormOpen(false)}
        onSave={() => void save()}
        vi={vi}
      />
      <ConfirmModal
        isOpen={Boolean(archiveTarget)}
        onClose={() => setArchiveTarget(null)}
        onConfirm={() => void archive()}
        title={vi ? 'Lưu trữ nội dung?' : 'Archive guidance?'}
        message={
          vi
            ? 'Nội dung sẽ ngừng được sử dụng nhưng lịch sử thay đổi vẫn được giữ. Bạn có chắc không?'
            : 'This guidance will stop being used while its change history remains available. Continue?'
        }
        confirmText={vi ? 'Lưu trữ' : 'Archive'}
        variant="destructive"
        isLoading={isSaving}
      />
    </AdminFrame>
  );
}
