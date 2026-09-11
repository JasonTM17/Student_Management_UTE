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
  Loader2,
  Save,
  School,
  Sparkles,
  Wand2,
} from 'lucide-react';
import type { AnnouncementMutation, AnnouncementRecord } from '@/lib/api';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useI18n } from '@/i18n';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { cn } from '@/lib/utils';

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
  const [isPreviewMode, setIsPreviewMode] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [validationError, setValidationError] = useState('');

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

    setIsPreviewMode(false);
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

        {/* Content Area with Preview Toggle */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
              {isVi ? 'Nội dung công văn / văn bản' : 'Document Content'} <span className="text-destructive">*</span>
            </label>
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setIsPreviewMode(false)}
                className={cn(
                  'rounded px-2 py-0.5 text-[11px] font-medium transition',
                  !isPreviewMode
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                {isVi ? 'Soạn thảo' : 'Editor'}
              </button>
              <button
                type="button"
                onClick={() => setIsPreviewMode(true)}
                className={cn(
                  'rounded px-2 py-0.5 text-[11px] font-medium transition',
                  isPreviewMode
                    ? 'bg-primary text-primary-foreground font-semibold shadow-xs'
                    : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <span className="inline-flex items-center gap-1">
                  <Eye className="h-3 w-3" />
                  {isVi ? 'Xem định dạng' : 'Preview'}
                </span>
              </button>
            </div>
          </div>

          {isPreviewMode ? (
            <div className="max-h-[360px] min-h-[220px] overflow-y-auto rounded-md border border-border bg-card p-4 text-xs">
              <RichContentRenderer content={content} />
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
