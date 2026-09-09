'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  BookOpen,
  Check,
  Clock,
  Code2,
  Copy,
  Download,
  FileEdit,
  FileText,
  RotateCcw,
  Sparkles,
} from 'lucide-react';
import { toast } from 'sonner';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState } from '@/components/ui/state-block';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { TinyMceEditor } from '@/components/ui/tinymce-editor';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';

const STORAGE_KEY = 'campuscore_editor_document';
const EDITOR_TYPE_KEY = 'campuscore_editor_engine';

interface StoredDocument {
  title: string;
  category: string;
  content: string;
  editorType?: 'tinymce' | 'markdown';
  updatedAt: string;
}

const DEFAULT_TINYMCE_VI = `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP.HCM</h4>
    <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 22px; font-weight: 700;">THÔNG BÁO HỌC VỤ & HƯỚNG DẪN ĐÀO TẠO</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Học kỳ I - Năm học 2026-2027 | Soạn thảo bằng TinyMCE</p>
  </div>

  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #1d4ed8; font-size: 14px;">Kính gửi:</strong> Toàn thể Giảng viên, Cán bộ học vụ và Sinh viên hệ đào tạo chính quy.
  </div>

  <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Kế hoạch đào tạo & Đăng ký tín chỉ</h3>
  <p>Nhà trường thông báo kế hoạch tổ chức học vụ và thời gian mở cổng đăng ký tín chỉ học phần đợt mới:</p>
  
  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f1f5f9; text-align: left;">
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đợt đăng ký</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian bắt đầu</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian kết thúc</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Đối tượng</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 1</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 15/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - 18/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên năm cuối & Làm đồ án</td>
      </tr>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Đợt 2</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 19/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - 22/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên các khóa còn lại</td>
      </tr>
    </tbody>
  </table>

  <div style="background-color: #fefce8; border-left: 4px solid #eab308; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #a16207;">Lưu ý quan trọng:</strong> Hệ thống áp dụng kiểm soát điều kiện tiên quyết và tải cao 4 lớp. Sinh viên kiểm tra lịch trước khi nhấn lưu.
  </div>
</div>
`;

const DEFAULT_TINYMCE_EN = `
<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
  <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
    <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">CAMPUSCORE UNIVERSITY OF TECHNOLOGY</h4>
    <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 22px; font-weight: 700;">ACADEMIC NOTICE & COURSE SYLLABUS</h2>
    <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Term 1 - Academic Year 2026-2027 | Drafted with TinyMCE</p>
  </div>

  <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
    <strong style="color: #1d4ed8; font-size: 14px;">Attention:</strong> All Lecturers, Faculty Staff, and Enrolled Students.
  </div>

  <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Course Registration Milestones</h3>
  <table style="width: 100%; border-collapse: collapse; margin: 16px 0;">
    <thead>
      <tr style="background-color: #f1f5f9; text-align: left;">
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Batch</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Start Time</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">End Time</th>
        <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Target Cohort</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Phase 1</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 15/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - 18/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Graduating & Thesis Students</td>
      </tr>
      <tr>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">Phase 2</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 19/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - 22/09/2026</td>
        <td style="border: 1px solid #cbd5e1; padding: 10px;">All remaining cohorts</td>
      </tr>
    </tbody>
  </table>
</div>
`;

const DEFAULT_MARKDOWN_VI = `# ĐỀ CƯƠNG HỌC PHẦN & TÀI LIỆU HƯỚNG DẪN

> [!NOTE]
> Tài liệu này được soạn thảo trực tiếp trên **Trình soạn thảo học vụ CampusCore**. Hỗ trợ bảng biểu, công thức, mã nguồn và hộp cảnh báo chuẩn institutional.

### 1. Mục tiêu và Chuẩn đầu ra (CLO)
- [ ] Nắm vững kiến trúc hệ thống và nguyên lý thiết kế cơ sở dữ liệu phân tán.
- [ ] Xây dựng giải pháp đảm bảo tính sẵn sàng cao (High Availability).
- [ ] Triển khai kiểm thử hồi quy và đo lường hiệu năng.

### 2. Kế hoạch học tập và phân bổ thời lượng
| Tuần | Chủ đề đào tạo | Hình thức | Chuẩn đầu ra |
| :--- | :--- | :--- | :--- |
| Tuần 1-3 | Tổng quan kiến trúc hướng dịch vụ | Lý thuyết & Demo | CLO-1 |
| Tuần 4-7 | Thiết kế CSDL & Tối ưu hóa truy vấn | Thực hành Lab | CLO-2 |
| Tuần 8-12 | Báo cáo tiến độ đồ án & Phản biện | Thuyết trình nhóm | CLO-3 |
`;

export default function AcademicEditorPage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth([
    'STUDENT',
    'LECTURER',
    'ADMIN',
    'SUPER_ADMIN',
  ]);
  const { locale } = useI18n();
  const isVi = locale === 'vi';

  const [editorType, setEditorType] = useState<'tinymce' | 'markdown'>('tinymce');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('syllabus');
  const [content, setContent] = useState('');
  const [lastSaved, setLastSaved] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const copy = useMemo(
    () =>
      isVi
        ? {
            eyebrow: 'Công cụ học thuật',
            title: 'Trình soạn thảo văn bản học thuật (TinyMCE & Markdown)',
            description:
              'Môi trường soạn thảo thông báo, đề cương môn học, bài nghiên cứu và tài liệu học thuật với đầy đủ công cụ TinyMCE WYSIWYG và Markdown.',
            docTitleLabel: 'Tiêu đề tài liệu',
            docTitlePlaceholder: 'Nhập tiêu đề tài liệu...',
            categoryLabel: 'Thể loại tài liệu',
            categories: {
              syllabus: 'Đề cương môn học',
              notice: 'Thông báo học vụ',
              thesis: 'Đề tài tốt nghiệp',
              notes: 'Ghi chú học tập / Nghiên cứu',
            },
            editorEngine: 'Chế độ soạn thảo',
            tinymceMode: 'Trình soạn thảo TinyMCE WYSIWYG',
            markdownMode: 'Trình soạn thảo Markdown',
            newDoc: 'Tạo mới',
            saveDraft: 'Lưu nháp',
            copyContent: 'Sao chép nội dung',
            downloadDoc: 'Tải tệp về máy',
            copiedToast: 'Đã sao chép nội dung vào bộ nhớ tạm',
            savedToast: 'Đã lưu bản nháp vào trình duyệt',
            newDocConfirm: 'Bạn có chắc chắn muốn làm mới toàn bộ nội dung tài liệu?',
            savedAt: 'Lưu gần nhất',
            statsTitle: 'Thống kê tài liệu',
            words: 'Từ',
            characters: 'Ký tự',
            readingTime: 'Thời gian đọc ước tính',
            minutes: 'phút',
            loading: 'Đang tải trình soạn thảo...',
          }
        : {
            eyebrow: 'Academic Tools',
            title: 'Academic Document Editor (TinyMCE & Markdown)',
            description:
              'Compose announcements, syllabi, research notes, and academic documentation with full TinyMCE WYSIWYG and Markdown suites.',
            docTitleLabel: 'Document Title',
            docTitlePlaceholder: 'Enter document title...',
            categoryLabel: 'Document Category',
            categories: {
              syllabus: 'Course Syllabus',
              notice: 'Academic Notice',
              thesis: 'Thesis Proposal',
              notes: 'Study / Research Notes',
            },
            editorEngine: 'Editor Engine',
            tinymceMode: 'TinyMCE WYSIWYG',
            markdownMode: 'Markdown Editor',
            newDoc: 'New Document',
            saveDraft: 'Save Draft',
            copyContent: 'Copy Content',
            downloadDoc: 'Export File',
            copiedToast: 'Content copied to clipboard',
            savedToast: 'Draft saved to browser storage',
            newDocConfirm: 'Reset and create a new document?',
            savedAt: 'Last saved',
            statsTitle: 'Document Statistics',
            words: 'Words',
            characters: 'Characters',
            readingTime: 'Estimated reading time',
            minutes: 'min',
            loading: 'Loading editor...',
          },
    [isVi],
  );

  // Initialize from storage or default
  useEffect(() => {
    try {
      const savedEngine = localStorage.getItem(EDITOR_TYPE_KEY) as 'tinymce' | 'markdown' | null;
      if (savedEngine === 'tinymce' || savedEngine === 'markdown') {
        setEditorType(savedEngine);
      }

      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as StoredDocument;
        setTitle(parsed.title || '');
        setCategory(parsed.category || 'syllabus');
        setContent(parsed.content || '');
        setLastSaved(parsed.updatedAt || null);
        if (parsed.editorType) {
          setEditorType(parsed.editorType);
        }
        return;
      }
    } catch {
      // ignore parse errors
    }

    // Default template
    setTitle(isVi ? 'Đề cương học phần & Thông báo đào tạo' : 'Academic Notice & Course Outline');
    setContent(isVi ? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN);
  }, [isVi]);

  // Handle switching editor engines
  const handleSwitchEditorType = (type: 'tinymce' | 'markdown') => {
    setEditorType(type);
    try {
      localStorage.setItem(EDITOR_TYPE_KEY, type);
    } catch {
      // ignore
    }
  };

  // Autosave periodically to localStorage
  const saveDraft = useCallback(() => {
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const payload: StoredDocument = {
      title,
      category,
      content,
      editorType,
      updatedAt: timestamp,
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      setLastSaved(timestamp);
      toast.success(copy.savedToast);
    } catch {
      // storage quota or disabled
    }
  }, [category, content, copy.savedToast, editorType, title]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      toast.success(copy.copiedToast);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error('Could not copy to clipboard');
    }
  }, [content, copy.copiedToast]);

  const handleDownload = useCallback(() => {
    const isHtml = editorType === 'tinymce';
    const ext = isHtml ? 'html' : 'md';
    const mime = isHtml ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8';
    const filename = `${(title || 'academic-document').toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9-_]/g, '')}.${ext}`;
    
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [content, editorType, title]);

  const handleReset = useCallback(() => {
    if (window.confirm(copy.newDocConfirm)) {
      setTitle('');
      setContent(editorType === 'tinymce' ? (isVi ? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN) : DEFAULT_MARKDOWN_VI);
      setLastSaved(null);
      localStorage.removeItem(STORAGE_KEY);
    }
  }, [copy.newDocConfirm, editorType, isVi]);

  // Strip HTML tags for clean text stats in TinyMCE mode
  const stats = useMemo(() => {
    const cleanText = editorType === 'tinymce'
      ? content.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
      : content.trim();
    const chars = cleanText.length;
    const words = cleanText.length === 0 ? 0 : cleanText.split(/\s+/).filter(Boolean).length;
    const minutes = Math.max(1, Math.ceil(words / 200));
    return { chars, words, minutes };
  }, [content, editorType]);

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {/* Top Action Bar */}
      <Card>
        <CardContent className="p-4 sm:p-5">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div className="grid flex-1 gap-4 sm:grid-cols-3">
              <div className="sm:col-span-2">
                <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                  {copy.docTitleLabel}
                </label>
                <Input
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder={copy.docTitlePlaceholder}
                  className="font-medium"
                />
              </div>
              <div>
                <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground">
                  {copy.categoryLabel}
                </label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  className="h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="syllabus">{copy.categories.syllabus}</option>
                  <option value="notice">{copy.categories.notice}</option>
                  <option value="thesis">{copy.categories.thesis}</option>
                  <option value="notes">{copy.categories.notes}</option>
                </select>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleCopy}
                className="gap-1.5"
              >
                {copied ? <Check className="h-4 w-4 text-emerald-600" /> : <Copy className="h-4 w-4" />}
                {copy.copyContent}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleDownload}
                className="gap-1.5"
              >
                <Download className="h-4 w-4" />
                {editorType === 'tinymce' ? (isVi ? 'Tải HTML' : 'Export HTML') : (isVi ? 'Tải .md' : 'Export .md')}
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={saveDraft}
                className="gap-1.5"
              >
                <FileText className="h-4 w-4" />
                {copy.saveDraft}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={handleReset}
                className="gap-1.5 text-muted-foreground hover:text-destructive"
              >
                <RotateCcw className="h-4 w-4" />
                {copy.newDoc}
              </Button>
            </div>
          </div>

          {/* Engine Selector and Document Quick Stats Bar */}
          <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-3 text-xs text-muted-foreground">
            {/* Editor Switcher Tabs */}
            <div className="inline-flex rounded-lg border border-border bg-secondary/40 p-1">
              <button
                type="button"
                onClick={() => handleSwitchEditorType('tinymce')}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-all ${
                  editorType === 'tinymce'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span>{copy.tinymceMode}</span>
              </button>
              <button
                type="button"
                onClick={() => handleSwitchEditorType('markdown')}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-medium transition-all ${
                  editorType === 'markdown'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                }`}
              >
                <Code2 className="h-3.5 w-3.5" />
                <span>{copy.markdownMode}</span>
              </button>
            </div>

            <div className="flex flex-wrap items-center gap-4">
              <span className="flex items-center gap-1.5">
                <FileEdit className="h-3.5 w-3.5 text-primary" />
                <strong>{stats.words.toLocaleString()}</strong> {copy.words}
              </span>
              <span>•</span>
              <span className="flex items-center gap-1.5">
                <BookOpen className="h-3.5 w-3.5 text-primary" />
                <strong>{stats.chars.toLocaleString()}</strong> {copy.characters}
              </span>
              <span>•</span>
              <span className="flex items-center gap-1.5">
                <Clock className="h-3.5 w-3.5 text-primary" />
                ~<strong>{stats.minutes}</strong> {copy.minutes} {copy.readingTime}
              </span>
              {lastSaved && (
                <>
                  <span>•</span>
                  <span className="inline-flex items-center gap-1 text-muted-foreground">
                    <Sparkles className="h-3.5 w-3.5 text-amber-500" />
                    {copy.savedAt}: <strong>{lastSaved}</strong>
                  </span>
                </>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Main Rich Text Editor Workbench */}
      <div className="rounded-xl border border-border/80 bg-card p-1 shadow-sm sm:p-2">
        {editorType === 'tinymce' ? (
          <TinyMceEditor
            value={content}
            onChange={setContent}
            height={560}
            locale={isVi ? 'vi' : 'en'}
            showTemplates={true}
          />
        ) : (
          <RichTextEditor
            value={content}
            onChange={setContent}
            minHeight="520px"
            locale={isVi ? 'vi' : 'en'}
            showTemplates={true}
          />
        )}
      </div>
    </div>
  );
}
