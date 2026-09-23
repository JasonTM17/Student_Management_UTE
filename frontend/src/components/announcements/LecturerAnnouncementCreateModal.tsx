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
  ImageIcon,
  Loader2,
  Send,
  Sparkles,
  X,
} from 'lucide-react';
import { announcementsApi, type AnnouncementMutation } from '@/lib/api';
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
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { EDIT_BANNER_PRESETS } from '@/components/announcements/AnnouncementEditModal';
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
  icon: React.ElementType;
  defaultPriority: 'NORMAL' | 'HIGH' | 'URGENT';
  defaultContent: string;
  defaultContentVi: string;
  defaultContentEn: string;
}

const PRESETS: PresetItem[] = [
  {
    key: 'LEAVE_MAKEUP',
    icon: CalendarOff,
    defaultPriority: 'HIGH',
    defaultContent: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên phụ trách thông báo về việc hoãn buổi học và kế hoạch học bù như sau:</p>
<ul>
  <li><strong>Thời gian nghỉ:</strong> Buổi học ngày ... (Thứ ..., Tiết ...).</li>
  <li><strong>Lý do:</strong> Công tác chuyên môn đột xuất của Giảng viên.</li>
  <li><strong>Kế hoạch học bù:</strong> Buổi học bù dự kiến diễn ra vào Thứ ..., ngày ... (Tiết ...) tại phòng học ... / học trực tuyến qua MS Teams.</li>
</ul>
<p>Đề nghị ban cán sự lớp thông báo đến toàn thể các bạn sinh viên trong lớp để nắm thông tin và tham gia học bù đầy đủ.</p>
<p>Trân trọng cảm ơn các em./.</p>`,
    defaultContentVi: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên phụ trách thông báo về việc hoãn buổi học và kế hoạch học bù như sau:</p>
<ul>
  <li><strong>Thời gian nghỉ:</strong> Buổi học ngày ... (Thứ ..., Tiết ...).</li>
  <li><strong>Lý do:</strong> Công tác chuyên môn đột xuất của Giảng viên.</li>
  <li><strong>Kế hoạch học bù:</strong> Buổi học bù dự kiến diễn ra vào Thứ ..., ngày ... (Tiết ...) tại phòng học ... / học trực tuyến qua MS Teams.</li>
</ul>
<p>Đề nghị ban cán sự lớp thông báo đến toàn thể các bạn sinh viên trong lớp để nắm thông tin và tham gia học bù đầy đủ.</p>
<p>Trân trọng cảm ơn các em./.</p>`,
    defaultContentEn: `<p>Dear students of the course section,</p>
<p>The instructor announces a class postponement and the makeup session plan as follows:</p>
<ul>
  <li><strong>Postponed session:</strong> Class on ... (Day ..., Period ...).</li>
  <li><strong>Reason:</strong> Urgent academic or professional duty.</li>
  <li><strong>Makeup session plan:</strong> Scheduled for Day ..., date ... (Period ...) at room ... / online via MS Teams.</li>
</ul>
<p>Class representatives are requested to notify all students to ensure full attendance.</p>
<p>Best regards./.</p>`,
  },
  {
    key: 'ASSIGNMENT_DEADLINE',
    icon: FileCheck,
    defaultPriority: 'HIGH',
    defaultContent: `<p>Kính gửi sinh viên các nhóm lớp học phần,</p>
<p>Giảng viên xin nhắc nhở về thời hạn và quy cách nộp sản phẩm Bài tập lớn / Đồ án môn học như sau:</p>
<ul>
  <li><strong>Hạn chót nộp bài:</strong> 23h59 ngày ... (Hệ thống sẽ tự động khóa nộp bài sau thời gian này).</li>
  <li><strong>Hồ sơ nộp gồm:</strong> Báo cáo định dạng PDF, Slide thuyết trình và Mã nguồn đính kèm đặt tên theo cú pháp: <code>[Nhom_XX]_[TenMonHoc].zip</code>.</li>
  <li><strong>Lưu ý:</strong> Mọi trường hợp nộp muộn vì lý do cá nhân không chính đáng sẽ bị trừ điểm theo quy định. Các nhóm gặp sự cố kỹ thuật cần báo ngay cho giảng viên trước hạn chót.</li>
</ul>
<p>Chúc các bạn hoàn thành bài tập đúng tiến độ và đạt kết quả cao./.</p>`,
    defaultContentVi: `<p>Kính gửi sinh viên các nhóm lớp học phần,</p>
<p>Giảng viên xin nhắc nhở về thời hạn và quy cách nộp sản phẩm Bài tập lớn / Đồ án môn học như sau:</p>
<ul>
  <li><strong>Hạn chót nộp bài:</strong> 23h59 ngày ... (Hệ thống sẽ tự động khóa nộp bài sau thời gian này).</li>
  <li><strong>Hồ sơ nộp gồm:</strong> Báo cáo định dạng PDF, Slide thuyết trình và Mã nguồn đính kèm đặt tên theo cú pháp: <code>[Nhom_XX]_[TenMonHoc].zip</code>.</li>
  <li><strong>Lưu ý:</strong> Mọi trường hợp nộp muộn vì lý do cá nhân không chính đáng sẽ bị trừ điểm theo quy định. Các nhóm gặp sự cố kỹ thuật cần báo ngay cho giảng viên trước hạn chót.</li>
</ul>
<p>Chúc các bạn hoàn thành bài tập đúng tiến độ và đạt kết quả cao./.</p>`,
    defaultContentEn: `<p>Dear students and course project teams,</p>
<p>This is a reminder regarding the deadline and submission requirements for the major assignment / course project:</p>
<ul>
  <li><strong>Submission deadline:</strong> 23:59 on ... (The submission portal will close automatically after this deadline).</li>
  <li><strong>Submission deliverables:</strong> PDF report, presentation slides, and source code archive named: <code>[Group_XX]_[CourseCode].zip</code>.</li>
  <li><strong>Note:</strong> Late submissions without prior valid approval will be penalized per course regulations. Any technical issues must be reported to the instructor before the deadline.</li>
</ul>
<p>Wishing you productive completion and high marks on your submission./.</p>`,
  },
  {
    key: 'EXAM_SCHEDULE',
    icon: GraduationCap,
    defaultPriority: 'URGENT',
    defaultContent: `<p>Kính gửi các bạn sinh viên,</p>
<p>Kế hoạch bài kiểm tra giữa kỳ (chiếm 50% điểm quá trình học phần) được sắp xếp cụ thể như sau:</p>
<ul>
  <li><strong>Thời gian kiểm tra:</strong> Tiết ..., Thứ ..., ngày ... tại phòng học ...</li>
  <li><strong>Hình thức:</strong> Bài kiểm tra trắc nghiệm kết hợp tự luận (Thời gian làm bài: 60 phút).</li>
  <li><strong>Nội dung trọng tâm:</strong> Toàn bộ kiến thức từ Chương 1 đến Chương ...</li>
  <li><strong>Quy định:</strong> Sinh viên mang theo Thẻ sinh viên hoặc CCCD để đối chiếu; không sử dụng tài liệu trong phòng thi.</li>
</ul>
<p>Đề nghị các bạn sinh viên chuẩn bị chu đáo để làm bài đạt kết quả tốt nhất./.</p>`,
    defaultContentVi: `<p>Kính gửi các bạn sinh viên,</p>
<p>Kế hoạch bài kiểm tra giữa kỳ (chiếm 50% điểm quá trình học phần) được sắp xếp cụ thể như sau:</p>
<ul>
  <li><strong>Thời gian kiểm tra:</strong> Tiết ..., Thứ ..., ngày ... tại phòng học ...</li>
  <li><strong>Hình thức:</strong> Bài kiểm tra trắc nghiệm kết hợp tự luận (Thời gian làm bài: 60 phút).</li>
  <li><strong>Nội dung trọng tâm:</strong> Toàn bộ kiến thức từ Chương 1 đến Chương ...</li>
  <li><strong>Quy định:</strong> Sinh viên mang theo Thẻ sinh viên hoặc CCCD để đối chiếu; không sử dụng tài liệu trong phòng thi.</li>
</ul>
<p>Đề nghị các bạn sinh viên chuẩn bị chu đáo để làm bài đạt kết quả tốt nhất./.</p>`,
    defaultContentEn: `<p>Dear students,</p>
<p>The midterm exam schedule (accounting for 50% of the course continuous assessment) is arranged as follows:</p>
<ul>
  <li><strong>Exam schedule:</strong> Period ..., Day ..., date ... in room ...</li>
  <li><strong>Exam format:</strong> Multiple-choice combined with constructed response (Duration: 60 minutes).</li>
  <li><strong>Scope:</strong> Core topics from Chapter 1 through Chapter ...</li>
  <li><strong>Regulations:</strong> Students must present their Student ID or National ID card; no unauthorized reference materials allowed in the exam room.</li>
</ul>
<p>Please prepare thoroughly to achieve your best performance./.</p>`,
  },
  {
    key: 'COURSE_GENERAL',
    icon: BookOpen,
    defaultPriority: 'NORMAL',
    defaultContent: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên gửi đến các bạn một số thông tin và dặn dò quan trọng cho tuần học tiếp theo:</p>
<ul>
  <li>Đã tải lên Slide bài giảng và bài tập thực hành tuần này lên hệ thống.</li>
  <li>Các bạn sinh viên vui lòng đọc trước tài liệu trước khi đến lớp.</li>
  <li>Chuẩn bị sẵn môi trường cài đặt phần mềm trên laptop cá nhân.</li>
</ul>
<p>Nếu có thắc mắc trong quá trình làm bài, các bạn có thể trao đổi trong giờ học hoặc gửi email cho giảng viên.</p>
<p>Chúc các bạn có một tuần học tập hiệu quả./.</p>`,
    defaultContentVi: `<p>Kính gửi các bạn sinh viên lớp học phần,</p>
<p>Giảng viên gửi đến các bạn một số thông tin và dặn dò quan trọng cho tuần học tiếp theo:</p>
<ul>
  <li>Đã tải lên Slide bài giảng và bài tập thực hành tuần này lên hệ thống.</li>
  <li>Các bạn sinh viên vui lòng đọc trước tài liệu trước khi đến lớp.</li>
  <li>Chuẩn bị sẵn môi trường cài đặt phần mềm trên laptop cá nhân.</li>
</ul>
<p>Nếu có thắc mắc trong quá trình làm bài, các bạn có thể trao đổi trong giờ học hoặc gửi email cho giảng viên.</p>
<p>Chúc các bạn có một tuần học tập hiệu quả./.</p>`,
    defaultContentEn: `<p>Dear students of the course section,</p>
<p>Please note the following important updates and guidelines for the upcoming academic week:</p>
<ul>
  <li>Lecture slides and lab practice materials for this week have been uploaded to the portal.</li>
  <li>Please read the assigned materials prior to class.</li>
  <li>Ensure all required software and development tools are set up on your personal laptops.</li>
</ul>
<p>If you have questions, please ask during class or contact the instructor by email.</p>
<p>Wishing you an effective and rewarding study week./.</p>`,
  },
];

export function LecturerAnnouncementCreateModal({
  isOpen,
  onClose,
  onSuccess,
  lecturerName,
}: LecturerAnnouncementCreateModalProps) {
  const { locale, messages } = useI18n();
  const isVi = locale === 'vi';
  const templateCopy = messages.announcementTemplates;
  const templateMeta = (key: PresetKey) => templateCopy[key];
  const initialTemplate = templateMeta(PRESETS[0].key);
  const draftCopy = messages.lecturerAnnouncementDraft;
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // LEC-P3-B2: the host page keeps this modal mounted, so the values above
  // are the pristine baseline every reset returns to and every dirty check
  // compares against.
  const initialTitle = initialTemplate.defaultTitle;
  const initialContent = isVi ? PRESETS[0].defaultContentVi : PRESETS[0].defaultContentEn;

  const [title, setTitle] = useState(initialTemplate.defaultTitle);
  const [content, setContent] = useState(isVi ? PRESETS[0].defaultContentVi : PRESETS[0].defaultContentEn);
  const [priority, setPriority] = useState<'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'>('HIGH');
  const [selectedPreset, setSelectedPreset] = useState<PresetKey>('LEAVE_MAKEUP');
  const [editorMode, setEditorMode] = useState<'visual' | 'code' | 'preview'>('visual');
  const [isSubmitting, setIsSubmitting] = useState(false);
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

  // LEC-P3-B2: wipe every authored field back to the template defaults. The
  // TinyMCE surface is driven by the controlled `value` prop, so resetting
  // `content` here also clears the WYSIWYG document.
  const resetForm = () => {
    setSelectedPreset(PRESETS[0].key);
    setTitle(initialTemplate.defaultTitle);
    setContent(isVi ? PRESETS[0].defaultContentVi : PRESETS[0].defaultContentEn);
    setPriority('HIGH');
    setEditorMode('visual');
    setValidationError('');
  };

  const isDraftDirty = title !== initialTitle || content !== initialContent || priority !== 'HIGH';

  // LEC-P3-B2: closing mid-draft is destructive, so it goes through the same
  // inline confirm the admin announcement editor uses before discarding.
  const requestClose = async () => {
    if (isSubmitting) return;
    if (isDraftDirty) {
      const discard = await confirm({
        title: draftCopy.discardTitle,
        message: draftCopy.discardMessage,
        confirmText: draftCopy.discardConfirm,
        cancelText: draftCopy.discardCancel,
        variant: 'destructive',
      });
      if (!discard) return;
    }
    resetForm();
    onClose();
  };

  const handleApplyPreset = (preset: PresetItem) => {
    setSelectedPreset(preset.key);
    setTitle(templateMeta(preset.key).defaultTitle);
    setContent(isVi ? preset.defaultContentVi : preset.defaultContentEn);
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
    // A fourth authoring surface: this modal writes announcements directly, so it
    // must consult the same length contract as the editor page rather than letting
    // the server answer with an opaque 400.
    const overflow = findAnnouncementLengthViolation(content);
    if (overflow) {
      setValidationError(
        announcementLengthViolationMessage(overflow, isVi ? 'vi' : 'en'),
      );
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
      // LEC-P3-B2: clear the published draft BEFORE closing. The host page
      // keeps this modal mounted, so leftover state used to reopen showing
      // the just-published text, and a second Publish created a duplicate.
      resetForm();
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
    <>
    <Modal
      isOpen={isOpen}
      onClose={() => void requestClose()}
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
              const presetCopy = templateMeta(p.key);
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
                    <span>{presetCopy.label}</span>
                  </div>
                  <span className="text-[11px] text-muted-foreground line-clamp-1">
                    {presetCopy.badge}
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
              <span className="ml-auto rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
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

        {/* Editorial Cover Image Preset Tray */}
        <div className="space-y-2 rounded-lg border border-border/70 bg-secondary/15 p-3">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
              <ImageIcon className="h-3.5 w-3.5 text-primary" />
              {isVi ? 'Ảnh bìa học thuật (Bộ sưu tập HCMUTE):' : 'Editorial Cover Image (HCMUTE Collection):'}
            </span>
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

        {/* Mode Toggle Bar: Visual (TinyMCE) vs Code vs Preview */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <label className="text-xs font-semibold text-foreground">
              {isVi ? 'Nội dung thông báo * (Hỗ trợ TinyMCE trực quan & HTML)' : 'Notice Content * (TinyMCE WYSIWYG & HTML)'}
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
            <div className="min-h-[240px] max-h-[360px] overflow-y-auto rounded-md border border-border/80 bg-card p-4">
              <div className="border-b border-border/60 pb-2 mb-3">
                <h3 className="font-bold text-base text-foreground">{title || '—'}</h3>
              </div>
              <RichContentRenderer content={content} />
            </div>
          ) : editorMode === 'visual' ? (
            <div className="overflow-hidden rounded-md border border-border/80">
              <TinyMceEditor
                value={content}
                onChange={setContent}
                locale={locale}
                height={280}
                placeholder={
                  isVi
                    ? 'Soạn thảo nội dung thông báo chuẩn học thuật gửi đến sinh viên...'
                    : 'Compose academic notice for students...'
                }
              />
            </div>
          ) : (
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={9}
              placeholder={isVi ? 'Nhập nội dung thông báo gửi đến sinh viên (mã HTML hoặc văn bản)...' : 'Enter notice body (HTML or plain text)...'}
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
            onClick={() => void requestClose()}
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
    {/* LEC-P3-B2: inline discard confirm, rendered beside the composer the
        same way the admin announcements page renders it beside its editor. */}
    {confirmationDialog}
    </>
  );
}
