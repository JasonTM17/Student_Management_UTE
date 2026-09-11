'use client';

import React, { useState } from 'react';
import {
  AlertCircle,
  BookOpen,
  CalendarOff,
  Check,
  Eye,
  FileCheck,
  FileEdit,
  GraduationCap,
  Loader2,
  Send,
  Sparkles,
} from 'lucide-react';
import { announcementsApi, type AnnouncementMutation } from '@/lib/api';
import { Modal } from '@/components/ui/modal';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { useI18n } from '@/i18n';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';

interface LecturerAnnouncementCreateModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  lecturerName?: string;
}

type PresetKey = 'LEAVE_MAKEUP' | 'ASSIGNMENT_DEADLINE' | 'EXAM_SCHEDULE' | 'COURSE_GENERAL';

interface PresetItem {
  key: PresetKey;
  label: string;
  icon: React.ElementType;
  badge: string;
  defaultTitle: string;
  defaultPriority: 'NORMAL' | 'HIGH' | 'URGENT';
  defaultContent: string;
}

const PRESETS: PresetItem[] = [
  {
    key: 'LEAVE_MAKEUP',
    label: 'Nghỉ học & Học bù',
    icon: CalendarOff,
    badge: 'Nghỉ học / Học bù',
    defaultPriority: 'HIGH',
    defaultTitle: '[THÔNG BÁO] Nghỉ học và Kế hoạch học bù lớp học phần',
    defaultContent: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên phụ trách thông báo về việc hoãn buổi học và kế hoạch học bù như sau:</p>
<ul>
  <li><strong>Thời gian nghỉ:</strong> Buổi học ngày ... (Thứ ..., Tiết ...).</li>
  <li><strong>Lý do:</strong> Công tác chuyên môn đột xuất của Giảng viên.</li>
  <li><strong>Kế hoạch học bù:</strong> Buổi học bù dự kiến diễn ra vào Thứ ..., ngày ... (Tiết ...) tại phòng học ... / học trực tuyến qua MS Teams.</li>
</ul>
<p>Đề nghị ban cán sự lớp thông báo đến toàn thể các bạn sinh viên trong lớp để nắm thông tin và tham gia học bù đầy đủ.</p>
<p>Trân trọng cảm ơn các em./.</p>`,
  },
  {
    key: 'ASSIGNMENT_DEADLINE',
    label: 'Nhắc nhở nộp bài tập lớn/đồ án',
    icon: FileCheck,
    badge: 'Hạn nộp bài tập',
    defaultPriority: 'HIGH',
    defaultTitle: '[NHẮC NHỞ] Hạn chót nộp Bài tập lớn / Đồ án môn học',
    defaultContent: `<p>Kính gửi sinh viên các nhóm lớp học phần,</p>
<p>Giảng viên xin nhắc nhở về thời hạn và quy cách nộp sản phẩm Bài tập lớn / Đồ án môn học như sau:</p>
<ul>
  <li><strong>Hạn chót nộp bài:</strong> 23h59 ngày ... (Hệ thống sẽ tự động khóa nộp bài sau thời gian này).</li>
  <li><strong>Hồ sơ nộp gồm:</strong> Báo cáo định dạng PDF, Slide thuyết trình và Mã nguồn đính kèm đặt tên theo cú pháp: <code>[Nhom_XX]_[TenMonHoc].zip</code>.</li>
  <li><strong>Lưu ý:</strong> Mọi trường hợp nộp muộn vì lý do cá nhân không chính đáng sẽ bị trừ điểm theo quy định. Các nhóm gặp sự cố kỹ thuật cần báo ngay cho giảng viên trước hạn chót.</li>
</ul>
<p>Chúc các bạn hoàn thành bài tập đúng tiến độ và đạt kết quả cao./.</p>`,
  },
  {
    key: 'EXAM_SCHEDULE',
    label: 'Lịch thi & Kiểm tra',
    icon: GraduationCap,
    badge: 'Kiểm tra giữa kỳ',
    defaultPriority: 'URGENT',
    defaultTitle: '[THÔNG BÁO] Kế hoạch kiểm tra giữa kỳ & Nội dung ôn tập',
    defaultContent: `<p>Kính gửi các bạn sinh viên,</p>
<p>Kế hoạch bài kiểm tra giữa kỳ (chiếm 50% điểm quá trình học phần) được sắp xếp cụ thể như sau:</p>
<ul>
  <li><strong>Thời gian kiểm tra:</strong> Tiết ..., Thứ ..., ngày ... tại phòng học ...</li>
  <li><strong>Hình thức:</strong> Bài kiểm tra trắc nghiệm kết hợp tự luận (Thời gian làm bài: 60 phút).</li>
  <li><strong>Nội dung trọng tâm:</strong> Toàn bộ kiến thức từ Chương 1 đến Chương ...</li>
  <li><strong>Quy định:</strong> Sinh viên mang theo Thẻ sinh viên hoặc CCCD để đối chiếu; không sử dụng tài liệu trong phòng thi.</li>
</ul>
<p>Đề nghị các bạn sinh viên chuẩn bị chu đáo để làm bài đạt kết quả tốt nhất./.</p>`,
  },
  {
    key: 'COURSE_GENERAL',
    label: 'Thông báo lớp học phần',
    icon: BookOpen,
    badge: 'Tài liệu / Dặn dò',
    defaultPriority: 'NORMAL',
    defaultTitle: '[THÔNG BÁO] Cập nhật tài liệu học tập và Yêu cầu môn học',
    defaultContent: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên gửi đến các bạn một số thông tin và dặn dò quan trọng cho tuần học tiếp theo:</p>
<ul>
  <li>Đã tải lên Slide bài giảng và bài tập thực hành tuần này lên hệ thống.</li>
  <li>Các bạn sinh viên vui lòng đọc trước tài liệu trước khi đến lớp.</li>
  <li>Chuẩn bị sẵn môi trường cài đặt phần mềm trên laptop cá nhân.</li>
</ul>
<p>Nếu có thắc mắc trong quá trình làm bài, các bạn có thể trao đổi trong giờ học hoặc gửi email cho giảng viên.</p>
<p>Chúc các bạn có một tuần học tập hiệu quả./.</p>`,
  },
];

export function LecturerAnnouncementCreateModal({
  isOpen,
  onClose,
  onSuccess,
  lecturerName,
}: LecturerAnnouncementCreateModalProps) {
  const { locale } = useI18n();
  const isVi = locale === 'vi';

  const [title, setTitle] = useState(PRESETS[0].defaultTitle);
  const [content, setContent] = useState(PRESETS[0].defaultContent);
  const [priority, setPriority] = useState<'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'>('HIGH');
  const [selectedPreset, setSelectedPreset] = useState<PresetKey>('LEAVE_MAKEUP');
  const [isPreviewMode, setIsPreviewMode] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [validationError, setValidationError] = useState('');

  const handleApplyPreset = (preset: PresetItem) => {
    setSelectedPreset(preset.key);
    setTitle(preset.defaultTitle);
    setContent(preset.defaultContent);
    setPriority(preset.defaultPriority);
    setValidationError('');
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      setValidationError(isVi ? 'Vui lòng nhập tiêu đề thông báo' : 'Please enter a title');
      return;
    }
    if (!content.trim()) {
      setValidationError(isVi ? 'Vui lòng nhập nội dung thông báo' : 'Please enter notice content');
      return;
    }

    setValidationError('');
    setIsSubmitting(true);

    try {
      const payload: AnnouncementMutation = {
        title: title.trim(),
        content: content.trim(),
        priority,
        targetRoles: ['STUDENT'],
        isGlobal: false,
      };

      await announcementsApi.create(payload);
      toast.success(isVi ? 'Đã đăng thông báo cho sinh viên thành công!' : 'Notice published to students successfully!');
      onSuccess();
      onClose();
    } catch (err: any) {
      toast.error(
        err?.response?.data?.message ||
        (isVi ? 'Không thể đăng thông báo. Vui lòng thử lại.' : 'Failed to publish notice. Please try again.')
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isVi ? 'Đăng thông báo cho Sinh viên lớp học phần' : 'Post Announcement for Students'}
      className="max-w-3xl"
    >
      <form onSubmit={handleFormSubmit} className="space-y-5">
        {/* Quick presets bar */}
        <div className="space-y-2">
          <label className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
            <Sparkles className="h-3.5 w-3.5 text-primary" />
            {isVi ? 'Chọn mẫu học vụ soạn nhanh:' : 'Select quick academic template:'}
          </label>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
            {PRESETS.map((p) => {
              const Icon = p.icon;
              const isSelected = selectedPreset === p.key;
              return (
                <button
                  key={p.key}
                  type="button"
                  onClick={() => handleApplyPreset(p)}
                  className={cn(
                    'flex flex-col items-start gap-1 rounded-lg border p-2.5 text-left transition duration-150',
                    isSelected
                      ? 'border-primary bg-primary/10 text-primary shadow-xs'
                      : 'border-border/70 bg-card hover:border-primary/40 hover:bg-secondary/40 text-foreground'
                  )}
                >
                  <div className="flex items-center gap-1.5 font-semibold text-xs">
                    <Icon className="h-3.5 w-3.5 shrink-0" />
                    <span>{p.label}</span>
                  </div>
                  <span className="text-[11px] text-muted-foreground line-clamp-1">
                    {p.badge}
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Priority & Target Audience */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">
              {isVi ? 'Mức độ ưu tiên' : 'Priority'}
            </label>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as any)}
              className="w-full rounded-md border border-border/80 bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <option value="NORMAL">{isVi ? 'Thông thường (NORMAL)' : 'Normal'}</option>
              <option value="HIGH">{isVi ? 'Quan trọng (HIGH)' : 'High'}</option>
              <option value="URGENT">{isVi ? 'Khẩn cấp (URGENT)' : 'Urgent'}</option>
              <option value="LOW">{isVi ? 'Tham khảo (LOW)' : 'Low'}</option>
            </select>
          </div>

          <div className="space-y-1.5">
            <label className="text-xs font-semibold text-foreground">
              {isVi ? 'Đối tượng nhận thông báo' : 'Target Audience'}
            </label>
            <div className="flex items-center gap-2 rounded-md border border-border/80 bg-secondary/20 px-3 py-2 text-sm text-foreground">
              <GraduationCap className="h-4 w-4 text-primary" />
              <span className="font-medium">{isVi ? 'Sinh viên các lớp học phần' : 'Students'}</span>
              <span className="ml-auto rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                STUDENT
              </span>
            </div>
          </div>
        </div>

        {/* Title */}
        <div className="space-y-1.5">
          <label className="text-xs font-semibold text-foreground">
            {isVi ? 'Tiêu đề thông báo *' : 'Notice Title *'}
          </label>
          <Input
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder={isVi ? 'Nhập tiêu đề thông báo...' : 'Enter notice title...'}
            className="text-sm font-medium"
            required
          />
        </div>

        {/* Mode Toggle Bar: Edit vs Preview */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-foreground">
              {isVi ? 'Nội dung thông báo * (Hỗ trợ HTML / Văn bản)' : 'Notice Content * (HTML / Plain text)'}
            </label>
            <div className="flex items-center gap-1 rounded-md border border-border/80 bg-background p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setIsPreviewMode(false)}
                className={cn(
                  'flex items-center gap-1 rounded px-2 py-1 font-medium transition',
                  !isPreviewMode ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <FileEdit className="h-3.5 w-3.5" />
                {isVi ? 'Soạn thảo' : 'Edit'}
              </button>
              <button
                type="button"
                onClick={() => setIsPreviewMode(true)}
                className={cn(
                  'flex items-center gap-1 rounded px-2 py-1 font-medium transition',
                  isPreviewMode ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:text-foreground'
                )}
              >
                <Eye className="h-3.5 w-3.5" />
                {isVi ? 'Xem trước' : 'Preview'}
              </button>
            </div>
          </div>

          {isPreviewMode ? (
            <div className="min-h-[220px] max-h-[350px] overflow-y-auto rounded-md border border-border/80 bg-card p-4">
              <div className="border-b border-border/60 pb-2 mb-3">
                <h3 className="font-bold text-base text-foreground">{title || '—'}</h3>
              </div>
              <RichContentRenderer content={content} />
            </div>
          ) : (
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={8}
              placeholder={isVi ? 'Nhập nội dung thông báo gửi đến sinh viên...' : 'Enter notice body...'}
              className="font-mono text-xs leading-relaxed"
              required
            />
          )}
        </div>

        {validationError ? (
          <div className="flex items-center gap-2 rounded-md border border-red-500/30 bg-red-500/10 px-3 py-2 text-xs font-medium text-red-600 dark:text-red-400">
            <AlertCircle className="h-4 w-4 shrink-0" />
            <span>{validationError}</span>
          </div>
        ) : null}

        {/* Dialog Actions */}
        <div className="flex items-center justify-end gap-3 border-t border-border/70 pt-4">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isSubmitting}
          >
            {isVi ? 'Hủy bỏ' : 'Cancel'}
          </Button>
          <Button
            type="submit"
            disabled={isSubmitting}
            className="gap-2"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                <span>{isVi ? 'Đang đăng thông báo...' : 'Publishing...'}</span>
              </>
            ) : (
              <>
                <Send className="h-4 w-4" />
                <span>{isVi ? 'Đăng thông báo cho sinh viên' : 'Publish Notice'}</span>
              </>
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
