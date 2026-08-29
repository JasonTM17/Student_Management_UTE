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
  AdminMetricCard,
  AdminTableCard,
  AdminTableScroll,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ConfirmModal, Modal } from '@/components/ui/modal';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { ForbiddenState, LoadingState } from '@/components/ui/state-block';
import { metricToneClass, statusToneClass } from '@/components/ui/status';
import { LinkButton } from '@/components/ui/link-button';
import {
  assistantKnowledgeApi,
  type AssistantCatalogCoverage,
  type AssistantKnowledgeDocument,
  type AssistantKnowledgeRequest,
  type AssistantKnowledgeState,
} from '@/lib/thesis-api';

const states: Array<{ value: '' | AssistantKnowledgeState; label: string }> = [
  { value: '', label: 'All states' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PENDING_REVIEW', label: 'Pending review' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'ARCHIVED', label: 'Archived' },
];

const blankForm: AssistantKnowledgeRequest = {
  slug: '',
  locale: 'both',
  title: '',
  content: '',
  source: 'CampusCore academic policy',
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

export default function AdminAssistantKnowledgePage() {
  const { user, isAdmin, isSuperAdmin, isLoading: authLoading, isLoggingOut } = useAuth();
  const { locale } = useI18n();
  const vi = locale === 'vi';
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const [documents, setDocuments] = useState<AssistantKnowledgeDocument[]>([]);
  const [coverage, setCoverage] = useState<AssistantCatalogCoverage | null>(null);
  const [coverageError, setCoverageError] = useState(false);
  const [stateFilter, setStateFilter] = useState<'' | AssistantKnowledgeState>('');
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
        assistantKnowledgeApi.list({ domain: 'THESIS', ...(stateFilter ? { state: stateFilter } : {}) }),
        assistantKnowledgeApi.getCatalogCoverage(),
      ]);
      setDocuments(rows);
      setCoverage(catalog);
    } catch (loadError) {
      // Keep curated and catalog surfaces independently truthful when the optional coverage read is unavailable.
      try {
        const rows = await assistantKnowledgeApi.list({ domain: 'THESIS', ...(stateFilter ? { state: stateFilter } : {}) });
        setDocuments(rows);
        setCoverageError(true);
      } catch {
        setError(actionError(loadError, locale));
      }
    } finally {
      setIsLoading(false);
    }
  }, [locale, stateFilter]);

  useEffect(() => {
    if (canAccess) void load();
  }, [canAccess, load]);

  const publishedCount = useMemo(() => documents.filter((row) => row.state === 'PUBLISHED').length, [documents]);
  const pendingCount = useMemo(() => documents.filter((row) => row.state === 'PENDING_REVIEW').length, [documents]);

  const openCreate = () => {
    setEditing(null);
    setForm({ ...blankForm });
    setNotice('');
    setError('');
    setFormOpen(true);
  };

  const openEdit = (document: AssistantKnowledgeDocument) => {
    setEditing(document);
    setForm({
      slug: document.slug,
      locale: document.locale === 'vi' || document.locale === 'en' ? document.locale : 'both',
      title: document.title,
      content: document.content,
      source: document.source,
      priority: document.priority,
    });
    setNotice('');
    setError('');
    setFormOpen(true);
  };

  const save = async () => {
    if (!form.slug.trim() || !form.title.trim() || !form.content.trim() || !form.source.trim()) {
      setError(vi ? 'Vui lòng điền slug, tiêu đề, nội dung và nguồn.' : 'Slug, title, content, and source are required.');
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
      if (operation === 'submit') await assistantKnowledgeApi.submit(document.documentId);
      else await assistantKnowledgeApi.publish(document.documentId);
      setNotice(vi ? 'Đã cập nhật quy trình duyệt.' : 'Review workflow updated.');
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
      await assistantKnowledgeApi.archive(archiveTarget.documentId);
      setArchiveTarget(null);
      setNotice(vi ? 'Đã lưu trữ nguồn. Lịch sử phiên bản vẫn được giữ.' : 'Source archived. Revision history remains available.');
      await load();
    } catch (archiveError) {
      setError(actionError(archiveError, locale));
    } finally {
      setIsSaving(false);
    }
  };

  if (authLoading || isLoggingOut) {
    return <AdminFrame title={vi ? 'Kiến thức trợ lý AI' : 'AI assistant knowledge'} description={vi ? 'Quản trị nguồn học thuật công khai.' : 'Manage public academic sources for the assistant.'}><LoadingState /></AdminFrame>;
  }

  if (!user || !canAccess) {
    return <AdminFrame title={vi ? 'Kiến thức trợ lý AI' : 'AI assistant knowledge'} description={vi ? 'Quản trị nguồn học thuật công khai.' : 'Manage public academic sources for the assistant.'}><ForbiddenState title={vi ? 'Không có quyền truy cập' : 'Access restricted'} description={vi ? 'Chỉ quản trị viên có thể quản lý nguồn kiến thức.' : 'Only administrators can manage curated knowledge sources.'} action={<LinkButton href="/dashboard" variant="outline">{vi ? 'Về trang làm việc' : 'Return to workspace'}</LinkButton>} /></AdminFrame>;
  }

  return (
    <AdminFrame
      title={vi ? 'Kiến thức trợ lý AI' : 'AI assistant knowledge'}
      description={vi ? 'Quản trị nguồn học thuật công khai theo quy trình bản nháp, duyệt và xuất bản.' : 'Manage public academic sources through draft, review, and publish states.'}
      actions={<Button type="button" onClick={openCreate}><Plus className="mr-2 h-4 w-4" aria-hidden="true" />{vi ? 'Tạo nguồn' : 'Create source'}</Button>}
    >
      {error ? <div role="alert" className="mb-5 border border-destructive/35 bg-destructive/5 px-4 py-3 text-sm text-destructive">{error} <button type="button" className="ml-2 font-semibold underline" onClick={() => void load()}>{vi ? 'Thử lại' : 'Retry'}</button></div> : null}
      {notice ? <div role="status" className="mb-5 flex items-center gap-2 border border-status-success/30 bg-status-success/12 px-4 py-3 text-sm text-status-success-foreground"><Check className="h-4 w-4" aria-hidden="true" />{notice}</div> : null}

      <div className="space-y-6">
        <div className="grid gap-3 sm:grid-cols-3">
          <AdminMetricCard label={vi ? 'Tổng nguồn' : 'Total sources'} value={documents.length} icon={<FileText className="h-5 w-5" />} toneClassName={metricToneClass('info')} compact />
          <AdminMetricCard label={vi ? 'Đã xuất bản' : 'Published'} value={publishedCount} icon={<ShieldCheck className="h-5 w-5" />} toneClassName={metricToneClass('success')} compact />
          <AdminMetricCard label={vi ? 'Chờ duyệt' : 'Pending review'} value={pendingCount} icon={<UploadCloud className="h-5 w-5" />} toneClassName={metricToneClass('warning')} compact />
        </div>

        <Card variant="muted">
          <CardHeader className="pb-3"><CardTitle className="flex items-center gap-2"><BookOpenCheck className="h-5 w-5 text-primary" aria-hidden="true" />{vi ? 'Phạm vi catalog công khai' : 'Public catalog coverage'}</CardTitle><CardDescription>{vi ? 'Chỉ hiển thị số lượng projection công khai. Không bao gồm hồ sơ, điểm, điểm danh, lịch cá nhân hoặc danh sách lớp.' : 'Read-only public projections. No profiles, grades, attendance, personal schedules, or rosters are included.'}</CardDescription></CardHeader>
          <CardContent>
            {coverage ? <div className="grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">{([['departments', vi ? 'Khoa' : 'Departments'], ['courses', vi ? 'Môn học' : 'Courses'], ['curricula', vi ? 'Chương trình' : 'Curricula'], ['semesters', vi ? 'Học kỳ' : 'Semesters']] as const).map(([key, label]) => <div key={key} className="border border-border/70 bg-background px-3 py-2"><div className="text-xs text-muted-foreground">{label}</div><div className="mt-1 text-xl font-semibold tabular-nums text-foreground">{coverage[key]}</div></div>)}</div> : <p className="text-sm text-muted-foreground">{coverageError ? (vi ? 'Coverage hiện chưa khả dụng. Nguồn curated vẫn hoạt động.' : 'Coverage is currently unavailable. Curated sources remain available.') : (vi ? 'Đang tải coverage...' : 'Loading coverage...')}</p>}
          </CardContent>
        </Card>

        <AdminTableCard title={vi ? 'Nguồn curated' : 'Curated sources'} description={vi ? 'Nguồn được kiểm duyệt trước khi trợ lý sử dụng. Archive là soft archive để giữ audit và revision.' : 'Sources are reviewed before assistant retrieval. Archive is a soft archive that preserves audit and revisions.'}>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-border/70 pb-4"><label className="flex items-center gap-2 text-sm font-medium text-foreground">{vi ? 'Trạng thái' : 'State'}<select className="h-11 rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={stateFilter} onChange={(event) => setStateFilter(event.target.value as '' | AssistantKnowledgeState)} aria-label={vi ? 'Lọc trạng thái' : 'Filter state'}>{states.map((option) => <option key={option.value} value={option.value}>{vi && option.value === '' ? 'Tất cả trạng thái' : vi && option.value === 'DRAFT' ? 'Bản nháp' : vi && option.value === 'PENDING_REVIEW' ? 'Chờ duyệt' : vi && option.value === 'PUBLISHED' ? 'Đã xuất bản' : vi && option.value === 'ARCHIVED' ? 'Đã lưu trữ' : option.label}</option>)}</select></label><Button type="button" variant="outline" onClick={() => void load()} disabled={isLoading}>{vi ? 'Tải lại' : 'Reload'}</Button></div>
          {isLoading && documents.length === 0 ? <LoadingState label={vi ? 'Đang tải nguồn...' : 'Loading sources...'} /> : documents.length === 0 ? <p className="py-12 text-center text-sm text-muted-foreground">{vi ? 'Chưa có nguồn phù hợp.' : 'No matching sources.'}</p> : <AdminTableScroll><table className="w-full min-w-[760px] text-left text-sm"><thead className="bg-secondary text-xs uppercase tracking-[0.08em] text-muted-foreground"><tr><th className="px-3 py-3 font-semibold">{vi ? 'Nguồn' : 'Source'}</th><th className="px-3 py-3 font-semibold">{vi ? 'Trạng thái' : 'State'}</th><th className="px-3 py-3 font-semibold">{vi ? 'Phiên bản' : 'Version'}</th><th className="px-3 py-3 text-right font-semibold">{vi ? 'Thao tác' : 'Actions'}</th></tr></thead><tbody>{documents.map((document) => <tr key={document.documentId} className="border-b border-border/60 align-top last:border-0"><td className="max-w-[420px] px-3 py-4"><div className="font-semibold text-foreground">{document.title}</div><div className="mt-1 truncate text-xs text-muted-foreground">{document.slug} · {document.locale}</div><p className="mt-2 line-clamp-2 text-sm leading-5 text-muted-foreground">{document.content}</p></td><td className="px-3 py-4"><span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${stateClass(document.state)}`}>{document.state}</span></td><td className="px-3 py-4 tabular-nums text-muted-foreground">v{document.version}</td><td className="px-3 py-4"><div className="flex flex-wrap justify-end gap-2"><Button type="button" size="sm" variant="outline" onClick={() => openEdit(document)} disabled={isSaving}><Edit3 className="mr-1.5 h-4 w-4" aria-hidden="true" />{vi ? 'Sửa' : 'Edit'}</Button>{document.state === 'DRAFT' ? <Button type="button" size="sm" variant="outline" onClick={() => void transition(document, 'submit')} disabled={isSaving}><Send className="mr-1.5 h-4 w-4" aria-hidden="true" />{vi ? 'Gửi duyệt' : 'Submit'}</Button> : null}{document.state === 'PENDING_REVIEW' ? <Button type="button" size="sm" onClick={() => void transition(document, 'publish')} disabled={isSaving}><ShieldCheck className="mr-1.5 h-4 w-4" aria-hidden="true" />{vi ? 'Xuất bản' : 'Publish'}</Button> : null}{document.state !== 'ARCHIVED' ? <Button type="button" size="sm" variant="ghost" onClick={() => setArchiveTarget(document)} disabled={isSaving}><Archive className="mr-1.5 h-4 w-4" aria-hidden="true" />{vi ? 'Lưu trữ' : 'Archive'}</Button> : null}</div></td></tr>)}</tbody></table></AdminTableScroll>}
        </AdminTableCard>
      </div>

      <Modal isOpen={formOpen} onClose={() => setFormOpen(false)} title={editing ? (vi ? 'Sửa nguồn curated' : 'Edit curated source') : (vi ? 'Tạo nguồn curated' : 'Create curated source')} className="max-w-3xl">
        <div className="space-y-4"><div className="grid gap-4 sm:grid-cols-2"><label className="space-y-2 text-sm font-medium text-foreground"><span>Slug</span><Input value={form.slug} onChange={(event) => setForm((current) => ({ ...current, slug: event.target.value }))} placeholder="academic-thesis-policy" /></label><label className="space-y-2 text-sm font-medium text-foreground"><span>{vi ? 'Ngôn ngữ' : 'Locale'}</span><select className="h-11 w-full rounded-md border border-input bg-background px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring" value={form.locale} onChange={(event) => setForm((current) => ({ ...current, locale: event.target.value as AssistantKnowledgeRequest['locale'] }))}><option value="both">{vi ? 'Cả hai' : 'Both'}</option><option value="vi">Tiếng Việt</option><option value="en">English</option></select></label></div><label className="space-y-2 text-sm font-medium text-foreground"><span>{vi ? 'Tiêu đề' : 'Title'}</span><Input value={form.title} onChange={(event) => setForm((current) => ({ ...current, title: event.target.value }))} /></label><label className="space-y-2 text-sm font-medium text-foreground"><span>{vi ? 'Nguồn tham chiếu' : 'Source reference'}</span><Input value={form.source} onChange={(event) => setForm((current) => ({ ...current, source: event.target.value }))} /></label><label className="space-y-2 text-sm font-medium text-foreground"><span>{vi ? 'Nội dung' : 'Content'}</span><Textarea value={form.content} onChange={(event) => setForm((current) => ({ ...current, content: event.target.value }))} className="min-h-[220px]" hint={vi ? 'Chỉ nhập thông tin học thuật công khai. Không thêm hồ sơ hoặc dữ liệu cá nhân.' : 'Only public academic information. Do not add profiles or personal data.'} /></label><div className="flex justify-end gap-2 border-t border-border/70 pt-4"><Button type="button" variant="outline" onClick={() => setFormOpen(false)} disabled={isSaving}>{vi ? 'Hủy' : 'Cancel'}</Button><Button type="button" onClick={() => void save()} disabled={isSaving}>{isSaving ? (vi ? 'Đang lưu...' : 'Saving...') : (vi ? 'Lưu bản nháp' : 'Save draft')}</Button></div></div>
      </Modal>
      <ConfirmModal isOpen={Boolean(archiveTarget)} onClose={() => setArchiveTarget(null)} onConfirm={() => void archive()} title={vi ? 'Lưu trữ nguồn?' : 'Archive source?'} message={vi ? 'Nguồn sẽ ngừng được sử dụng nhưng revision và audit vẫn được giữ. Bạn có chắc không?' : 'The source will stop being retrieved while revisions and audit history remain. Continue?'} confirmText={vi ? 'Lưu trữ' : 'Archive'} variant="destructive" isLoading={isSaving} />
    </AdminFrame>
  );
}
