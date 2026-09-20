'use client';

import React, { useEffect, useState } from 'react';
import {
  AlertCircle,
  Building2,
  Check,
  Eye,
  FileEdit,
  Globe,
  GraduationCap,
  ImageIcon,
  Loader2,
  Save,
  School,
  Sparkles,
  Wand2,
  X,
} from 'lucide-react';
import type { AnnouncementMutation, AnnouncementRecord } from '@/lib/api';
import {
  announcementLengthViolationMessage,
  findAnnouncementLengthViolation,
} from '@/lib/announcement-limits';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useI18n } from '@/i18n';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { TinyMceEditor } from '@/components/ui/tinymce-editor';
import { cn } from '@/lib/utils';

export const EDIT_BANNER_PRESETS = [
  {
    id: 'campus',
    url: '/images/banners/campus_academic_banner.jpg',
    titleVi: 'Khuôn viên học thuật HCMUTE',
    titleEn: 'HCMUTE Campus Academic',
    tagVi: 'Khuôn viên',
    tagEn: 'Campus',
  },
  {
    id: 'notice',
    url: '/images/banners/academic_notice_banner.jpg',
    titleVi: 'Bản tin thông báo học vụ',
    titleEn: 'Academic Notice Bulletin',
    tagVi: 'Học vụ',
    tagEn: 'Notice',
  },
  {
    id: 'thesis',
    url: '/images/banners/thesis_defense_hall.jpg',
    titleVi: 'Hội đồng bảo vệ khóa luận',
    titleEn: 'Thesis Defense Committee',
    tagVi: 'Khóa luận',
    tagEn: 'Thesis',
  },
  {
    id: 'council',
    url: '/images/banners/thesis_evaluation_council.jpg',
    titleVi: 'Hội đồng đánh giá đề tài',
    titleEn: 'Thesis Evaluation Council',
    tagVi: 'Hội đồng',
    tagEn: 'Council',
  },
  {
    id: 'faculty',
    url: '/images/banners/faculty_engineering_hall.jpg',
    titleVi: 'Tòa nhà Khoa Kỹ thuật HCMUTE',
    titleEn: 'Faculty of Engineering Hall',
    tagVi: 'Khoa Viện',
    tagEn: 'Faculty',
  },
  {
    id: 'library',
    url: '/images/banners/smart_campus_library.jpg',
    titleVi: 'Thư viện số & Không gian học tập',
    titleEn: 'Smart Digital Library & Commons',
    tagVi: 'Thư viện',
    tagEn: 'Library',
  },
  {
    id: 'convocation',
    url: '/images/banners/academic_convocation_ceremony.jpg',
    titleVi: 'Lễ tốt nghiệp & Vinh danh học thuật',
    titleEn: 'Grand Convocation Ceremony',
    tagVi: 'Tốt nghiệp',
    tagEn: 'Graduation',
  },
  {
    id: 'lab',
    url: '/images/banners/department_research_lab.jpg',
    titleVi: 'Phòng Lab nghiên cứu Khoa CNTT',
    titleEn: 'IT Smart Systems Lab',
    tagVi: 'Nghiên cứu',
    tagEn: 'Research',
  },
];

interface AnnouncementEditModalProps {
  announcement: AnnouncementRecord | null;
  isOpen: boolean;
  onClose: () => void;
  onSave: (id: string, payload: AnnouncementMutation) => Promise<void>;
  onOpenStudio?: (announcement: AnnouncementRecord) => void;
}

export function AnnouncementEditModal({
  announcement,
  isOpen,
  onClose,
  onSave,
  onOpenStudio,
}: AnnouncementEditModalProps) {
  const { locale } = useI18n();
  const isVi = locale === 'vi';

  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [priority, setPriority] = useState<'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'>('NORMAL');
  const [targetRole, setTargetRole] = useState<'ALL' | 'STUDENT' | 'LECTURER'>('ALL');
  const [publishedBy, setPublishedBy] = useState('');
  const [editorMode, setEditorMode] = useState<'visual' | 'code' | 'preview'>('visual');
  const [isSaving, setIsSaving] = useState(false);
  const [validationError, setValidationError] = useState('');

  // Active cover image detection from content
  const activeCoverUrl =
    EDIT_BANNER_PRESETS.find((b) => content.includes(b.url))?.url ||
    content.match(/src=["'](\/images\/(?:banners|news)\/[^"']+)["']/i)?.[1] ||
    null;

  const handleApplyBanner = (bannerUrl: string, bannerTitle: string) => {
    const figureMarkup = `<figure class="my-3 text-center">\n  <img src="${bannerUrl}" alt="${bannerTitle}" class="w-full rounded-lg object-cover max-h-72 shadow-xs" />\n  <figcaption class="mt-1.5 text-xs text-muted-foreground italic">${bannerTitle}</figcaption>\n</figure>\n\n`;

    if (activeCoverUrl) {
      const figureRegex = new RegExp(
        `<figure[^>]*>[\\s\\S]*?(?:${activeCoverUrl.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}|\\/images\\/(?:banners|news)\\/[^"']+)[\\s\\S]*?<\\/figure>\\s*`,
        'i'
      );
      if (figureRegex.test(content)) {
        setContent((prev) => prev.replace(figureRegex, figureMarkup));
        return;
      }
      const imgRegex = new RegExp(
        `<img[^>]+src=["']${activeCoverUrl.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}["'][^>]*>\\s*`,
        'i'
      );
      if (imgRegex.test(content)) {
        setContent((prev) => prev.replace(imgRegex, figureMarkup));
        return;
      }
    }
    setContent((prev) => figureMarkup + prev);
  };

  const handleRemoveBanner = () => {
    if (!activeCoverUrl) return;
    const figureRegex = new RegExp(
      `<figure[^>]*>[\\s\\S]*?${activeCoverUrl.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}[\\s\\S]*?<\\/figure>\\s*`,
      'gi'
    );
    if (figureRegex.test(content)) {
      setContent((prev) => prev.replace(figureRegex, '').trim());
    } else {
      const imgRegex = new RegExp(
        `<img[^>]+src=["']${activeCoverUrl.replace(/[.*+?^${}()|[\\]\\]/g, '\\$&')}["'][^>]*>\\s*`,
        'gi'
      );
      setContent((prev) => prev.replace(imgRegex, '').trim());
    }
  };

  useEffect(() => {
    if (!announcement) return;

    setTitle(announcement.title || '');
    setContent(announcement.content || '');
    setPriority((announcement.priority as any) || 'NORMAL');
    setPublishedBy(announcement.publishedBy || '');

    if (
      announcement.isGlobal ||
      !announcement.targetRoles ||
      announcement.targetRoles.length === 0 ||
      (announcement.targetRoles.includes('STUDENT') && announcement.targetRoles.includes('LECTURER'))
    ) {
      setTargetRole('ALL');
    } else if (announcement.targetRoles.includes('STUDENT')) {
      setTargetRole('STUDENT');
    } else if (announcement.targetRoles.includes('LECTURER')) {
      setTargetRole('LECTURER');
    }

    setEditorMode('visual');
    setValidationError('');
  }, [announcement]);

  if (!announcement) return null;

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setValidationError(isVi ? 'Vui lòng nhập tiêu đề thông báo / công văn' : 'Please enter a title');
      return;
    }
    if (!content.trim()) {
      setValidationError(isVi ? 'Vui lòng nhập nội dung văn bản' : 'Please enter document content');
      return;
    }

    const overflow = findAnnouncementLengthViolation(content);
    if (overflow) {
      setValidationError(
        announcementLengthViolationMessage(overflow, isVi ? 'vi' : 'en'),
      );
      return;
    }

    setValidationError('');
    setIsSaving(true);
    try {
      const isGlobal = targetRole === 'ALL';
      const targetRoles = isGlobal ? ['STUDENT', 'LECTURER'] : [targetRole];

      const payload: AnnouncementMutation = {
        title: title.trim(),
        content: content.trim(),
        priority,
        isGlobal,
        targetRoles,
        expectedVersion: announcement.version ?? 0,
        reason: 'Chỉnh sửa nhanh qua giao diện quản trị Bảng tin',
      };

      await onSave(announcement.id, payload);
      onClose();
    } catch (err: any) {
      setValidationError(
        err?.response?.data?.message ||
          (isVi ? 'Không thể lưu thay đổi. Vui lòng kiểm tra lại.' : 'Failed to save changes. Please retry.')
      );
    } finally {
      setIsSaving(false);
    }
  };

  const wordCount = content.trim() ? content.trim().split(/\s+/).length : 0;
  const charCount = content.length;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isVi ? 'Chỉnh sửa thông báo / công văn' : 'Edit Announcement'}
      className="max-w-3xl"
    >
      <form onSubmit={handleFormSubmit} className="space-y-4">
        {validationError ? (
          <div className="flex items-center gap-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-2.5 text-xs font-medium text-destructive">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{validationError}</span>
          </div>
        ) : null}

        {/* Header Notice Banner */}
        <div className="flex items-center justify-between rounded-lg border border-border/70 bg-muted/40 px-3.5 py-2 text-xs">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-foreground">
              {isVi ? 'Mã văn bản:' : 'Notice ID:'}
            </span>
            <code className="font-mono text-[11px] text-muted-foreground">
              {announcement.id.slice(0, 12)}...
            </code>
          </div>
          <div className="text-[11px] text-muted-foreground">
            {isVi ? 'Ban hành bởi:' : 'Published by:'}{' '}
            <span className="font-medium text-foreground">{publishedBy || 'Phòng Đào tạo'}</span>
          </div>
        </div>

        {/* Title Input */}
        <div>
          <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {isVi ? 'Tiêu đề thông báo / công văn' : 'Document Title'} <span className="text-destructive">*</span>
          </label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={isVi ? 'Nhập tiêu đề công văn chính thức...' : 'Enter official document title...'}
            className="font-medium h-9 text-sm"
            required
          />
        </div>

        {/* Metadata Controls: Priority & Target Role */}
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {isVi ? 'Mức độ ưu tiên' : 'Priority'}
            </label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as any)}
              className="h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="URGENT">{isVi ? '🚨 Khẩn cấp (Urgent)' : '🚨 Urgent'}</option>
              <option value="HIGH">{isVi ? '⚠️ Ưu tiên cao (High)' : '⚠️ High'}</option>
              <option value="NORMAL">{isVi ? 'ℹ️ Bình thường (Normal)' : 'ℹ️ Normal'}</option>
              <option value="LOW">{isVi ? '📝 Thông tin chung (Low)' : '📝 Low'}</option>
            </select>
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {isVi ? 'Đối tượng tiếp nhận' : 'Target Audience'}
            </label>
            <select
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value as any)}
              className="h-9 w-full rounded-md border border-input bg-background px-3 py-1.5 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="ALL">{isVi ? '🏛️ Toàn trường (Sinh viên & Giảng viên)' : '🏛️ All Campus'}</option>
              <option value="STUDENT">{isVi ? '🎓 Chỉ Sinh viên' : '🎓 Students only'}</option>
              <option value="LECTURER">{isVi ? '👨‍🏫 Chỉ Giảng viên' : '👨‍🏫 Lecturers only'}</option>
            </select>
          </div>
        </div>

        {/* Editorial Cover Image Preset Tray */}
        <div className="space-y-2 rounded-lg border border-border/70 bg-secondary/15 p-3">
          <div className="flex items-center justify-between">
            <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <ImageIcon className="h-3.5 w-3.5 text-primary" />
              {isVi ? 'Ảnh bìa học thuật (Bộ sưu tập HCMUTE):' : 'Editorial Cover Image (HCMUTE Collection):'}
            </label>
            {activeCoverUrl ? (
              <button
                type="button"
                onClick={handleRemoveBanner}
                className="text-[11px] font-medium text-destructive hover:underline flex items-center gap-1 cursor-pointer"
              >
                <X className="h-3 w-3" />
                {isVi ? 'Gỡ ảnh bìa' : 'Remove Cover'}
              </button>
            ) : null}
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {EDIT_BANNER_PRESETS.map((banner) => {
              const isSelected = activeCoverUrl === banner.url;
              const bannerTitle = isVi ? banner.titleVi : banner.titleEn;
              const bannerTag = isVi ? banner.tagVi : banner.tagEn;
              return (
                <button
                  key={banner.id}
                  type="button"
                  onClick={() => handleApplyBanner(banner.url, bannerTitle)}
                  className={cn(
                    'group relative flex flex-col overflow-hidden rounded-lg border text-left transition-all duration-150',
                    isSelected
                      ? 'border-primary ring-2 ring-primary/40 shadow-xs'
                      : 'border-border/70 bg-card hover:border-primary/50'
                  )}
                >
                  <div className="relative h-16 w-full overflow-hidden bg-muted">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      src={banner.url}
                      alt={bannerTitle}
                      className="h-full w-full object-cover object-center transition duration-300 group-hover:scale-105"
                    />
                    {isSelected ? (
                      <div className="absolute right-1.5 top-1.5 rounded-full bg-primary p-0.5 text-primary-foreground shadow-xs">
                        <Check className="h-3 w-3" />
                      </div>
                    ) : null}
                  </div>
                  <div className="p-1.5 bg-card">
                    <p className="text-[11px] font-semibold text-foreground truncate">{bannerTitle}</p>
                    <span className="text-[9px] text-muted-foreground">{bannerTag}</span>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* Content Area with 3-Mode Toggle: Visual (TinyMCE) vs Code vs Preview */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {isVi ? 'Nội dung công văn / văn bản' : 'Document Content'} <span className="text-destructive">*</span>
            </label>
            <div className="flex items-center gap-1 rounded-md border border-border/80 bg-background p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setEditorMode('visual')}
                className={cn(
                  'flex items-center gap-1 rounded px-2 py-1 font-medium transition',
                  editorMode === 'visual'
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <Sparkles className="h-3.5 w-3.5" />
                {isVi ? 'Trực quan (TinyMCE)' : 'Visual'}
              </button>
              <button
                type="button"
                onClick={() => setEditorMode('code')}
                className={cn(
                  'flex items-center gap-1 rounded px-2 py-1 font-medium transition',
                  editorMode === 'code'
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <FileEdit className="h-3.5 w-3.5" />
                {isVi ? 'Mã nguồn' : 'Code'}
              </button>
              <button
                type="button"
                onClick={() => setEditorMode('preview')}
                className={cn(
                  'flex items-center gap-1 rounded px-2 py-1 font-medium transition',
                  editorMode === 'preview'
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <Eye className="h-3.5 w-3.5" />
                {isVi ? 'Xem trước' : 'Preview'}
              </button>
            </div>
          </div>

          {editorMode === 'preview' ? (
            <div className="max-h-[360px] min-h-[220px] overflow-y-auto rounded-md border border-border bg-card p-4 text-xs">
              <RichContentRenderer content={content} />
            </div>
          ) : editorMode === 'visual' ? (
            <div className="overflow-hidden rounded-md border border-border">
              <TinyMceEditor
                value={content}
                onChange={setContent}
                locale={locale}
                height={280}
                placeholder={isVi ? 'Soạn thảo nội dung văn bản chuẩn học thuật...' : 'Compose document content...'}
              />
            </div>
          ) : (
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={10}
              placeholder={isVi ? 'Nhập nội dung văn bản...' : 'Enter document content...'}
              className="font-mono text-xs leading-relaxed"
              required
            />
          )}

          <div className="flex items-center justify-between text-[11px] text-muted-foreground">
            <span>
              {wordCount} {isVi ? 'từ' : 'words'} • {charCount} {isVi ? 'ký tự' : 'characters'}
            </span>
            <span className="italic">
              {isVi ? 'Hỗ trợ định dạng văn bản chuẩn e-Office' : 'Supports e-Office standard markup'}
            </span>
          </div>
        </div>

        {/* Modal Footer Actions */}
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/70 pt-3">
          {onOpenStudio ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => {
                onOpenStudio({
                  ...announcement,
                  title,
                  content,
                  priority,
                  targetRoles: targetRole === 'ALL' ? ['STUDENT', 'LECTURER'] : [targetRole],
                  isGlobal: targetRole === 'ALL',
                });
              }}
              className="gap-1.5 text-xs text-muted-foreground hover:text-foreground"
              title={isVi ? 'Chuyển sang Studio kéo thả khối đầy đủ' : 'Open in Advanced Studio'}
            >
              <Wand2 className="h-3.5 w-3.5 text-primary" />
              <span>{isVi ? 'Mở trong Studio Soạn thảo' : 'Open in Studio'}</span>
            </Button>
          ) : <div />}

          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={onClose}
              disabled={isSaving}
              className="text-xs"
            >
              {isVi ? 'Hủy' : 'Cancel'}
            </Button>
            <Button
              type="submit"
              size="sm"
              disabled={isSaving}
              className="gap-1.5 bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-semibold px-4 shadow-xs"
            >
              {isSaving ? (
                <>
                  <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  <span>{isVi ? 'Đang lưu...' : 'Saving...'}</span>
                </>
              ) : (
                <>
                  <Save className="h-3.5 w-3.5" />
                  <span>{isVi ? 'Lưu thay đổi' : 'Save Changes'}</span>
                </>
              )}
            </Button>
          </div>
        </div>
      </form>
    </Modal>
  );
}
