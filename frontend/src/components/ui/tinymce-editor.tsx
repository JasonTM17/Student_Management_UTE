'use client';

import React, { useEffect, useId, useMemo, useRef, useState } from 'react';
import dynamic from 'next/dynamic';
import {
  BookOpen,
  Check,
  Copy,
  Download,
  Eye,
  FileText,
  HelpCircle,
  Loader2,
  Maximize2,
  Minimize2,
  Sparkles,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

// Dynamically import TinyMCE Editor to guarantee zero SSR issues in Next.js App Router
const Editor = dynamic(
  () => import('@tinymce/tinymce-react').then((mod) => mod.Editor as unknown as React.ComponentType<any>),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-96 w-full items-center justify-center rounded-lg border border-border/70 bg-card/60">
        <div className="flex flex-col items-center gap-2 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin text-primary" />
          <span className="text-sm font-medium">Đang khởi tạo trình soạn thảo TinyMCE...</span>
        </div>
      </div>
    ),
  }
);

export interface TinyMceEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  height?: number | string;
  disabled?: boolean;
  readOnly?: boolean;
  locale?: 'vi' | 'en';
  id?: string;
  showTemplates?: boolean;
  showWordCount?: boolean;
  onInit?: (evt: any, editor: any) => void;
}

const TINYMCE_TEMPLATES = [
  {
    titleVi: 'Thông báo học vụ chính thức',
    titleEn: 'Official Academic Notice',
    descriptionVi: 'Mẫu thông báo ban hành từ phòng đào tạo có tiêu đề, bảng thời gian và lưu ý',
    descriptionEn: 'Official notice from academic affairs with timeline and notes',
    content: `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
        <div style="text-align: center; border-bottom: 2px solid #0284c7; padding-bottom: 12px; margin-bottom: 20px;">
          <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP.HCM</h4>
          <h2 style="margin: 8px 0 0 0; color: #0f172a; font-size: 20px; font-weight: 700;">THÔNG BÁO HỌC VỤ CHÍNH THỨC</h2>
          <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Số: 2026/TB-ĐHCNKT | Học kỳ I - Năm học 2026-2027</p>
        </div>

        <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
          <strong style="color: #1d4ed8; font-size: 14px;">Kính gửi:</strong> Toàn thể Giảng viên và Sinh viên các khóa hệ chính quy.
        </div>

        <h3 style="color: #0369a1; font-size: 16px; border-bottom: 1px solid #e2e8f0; padding-bottom: 6px;">1. Kế hoạch chi tiết</h3>
        <table style="width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 20px;">
          <thead>
            <tr style="background-color: #f1f5f9; text-align: left;">
              <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Hạng mục</th>
              <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian bắt đầu</th>
              <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Thời gian kết thúc</th>
              <th style="border: 1px solid #cbd5e1; padding: 10px; font-weight: 600;">Ghi chú</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">Đăng ký tín chỉ đợt 1</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 15/09/2026</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">23:59 - 18/09/2026</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên năm 3, 4</td>
            </tr>
            <tr>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">Đăng ký tín chỉ đợt 2</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">08:00 - 19/09/2026</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">17:00 - 22/09/2026</td>
              <td style="border: 1px solid #cbd5e1; padding: 10px;">Sinh viên năm 1, 2 và bổ sung</td>
            </tr>
          </tbody>
        </table>

        <div style="background-color: #fefce8; border-left: 4px solid #eab308; padding: 14px 18px; border-radius: 6px; margin-bottom: 20px;">
          <strong style="color: #a16207;">Lưu ý quan trọng:</strong> Sinh viên kiểm tra kỹ điều kiện học phần tiên quyết trước khi nộp đăng ký. Mọi thắc mắc liên hệ Văn phòng Đào tạo A1-101.
        </div>
      </div>
    `,
  },
  {
    titleVi: 'Đề cương đề tài khóa luận / NCKH',
    titleEn: 'Thesis / Research Proposal Outline',
    descriptionVi: 'Mẫu đề xuất đề tài tốt nghiệp có mục tiêu, phương pháp và danh mục công việc',
    descriptionEn: 'Academic proposal outline with objectives, methodology, and work plan',
    content: `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
        <h2 style="color: #0f172a; border-bottom: 2px solid #0284c7; padding-bottom: 8px;">ĐỀ CƯƠNG ĐỀ TÀI KHÓA LUẬN TỐT NGHIỆP</h2>

        <p><strong>1. Tên đề tài (Tiếng Việt):</strong> Hệ thống quản lý học tập và đăng ký tín chỉ thời gian thực CampusCore</p>
        <p><strong>2. Tên đề tài (Tiếng Anh):</strong> Real-time CampusCore Academic Management System</p>
        <p><strong>3. Giảng viên hướng dẫn:</strong> TS. Nguyễn Văn A</p>
        <p><strong>4. Nhóm sinh viên thực hiện:</strong></p>
        <ul>
          <li>Nguyễn Tiến Sơn (Trưởng nhóm - MSSV: 20110001)</li>
          <li>Thành viên phối hợp (MSSV: 20110002)</li>
        </ul>

        <h3 style="color: #0369a1; margin-top: 20px;">I. Tính cấp thiết của đề tài</h3>
        <p>Trình bày bối cảnh thực tiễn, những hạn chế của hệ thống hiện hữu và lý do lựa chọn nghiên cứu đề tài này.</p>

        <h3 style="color: #0369a1; margin-top: 20px;">II. Mục tiêu nghiên cứu</h3>
        <ol>
          <li>Xây dựng kiến trúc xử lý đăng ký tải cao chống nghẽn 4 lớp (Rate limit, Redis counter, Atomic slot lock, PostgreSQL optimistic lock).</li>
          <li>Tích hợp trợ lý học vụ AI hỗ trợ sinh viên tra cứu thời khóa biểu và quy chế đào tạo trực tiếp.</li>
          <li>Đảm bảo tuân thủ tiêu chuẩn Web Accessibility WCAG 2.1 AA và giao diện song ngữ mượt mà.</li>
        </ol>

        <h3 style="color: #0369a1; margin-top: 20px;">III. Kế hoạch triển khai (Gantt)</h3>
        <table style="width: 100%; border-collapse: collapse; margin-top: 10px;">
          <thead>
            <tr style="background-color: #f1f5f9;">
              <th style="border: 1px solid #cbd5e1; padding: 8px;">Giai đoạn</th>
              <th style="border: 1px solid #cbd5e1; padding: 8px;">Nội dung công việc</th>
              <th style="border: 1px solid #cbd5e1; padding: 8px;">Thời gian</th>
              <th style="border: 1px solid #cbd5e1; padding: 8px;">Kết quả dự kiến</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">1</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Khảo sát và thiết kế kiến trúc</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Tuần 1 - Tuần 3</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Báo cáo SRS & Architecture Spec</td>
            </tr>
            <tr>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">2</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Phát triển Core Engine & Web Portal</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Tuần 4 - Tuần 10</td>
              <td style="border: 1px solid #cbd5e1; padding: 8px;">Hệ thống chạy thử nghiệm Staging</td>
            </tr>
          </tbody>
        </table>
      </div>
    `,
  },
  {
    titleVi: 'Quyết định của Hiệu trưởng',
    titleEn: 'Official Rector Decision',
    descriptionVi: 'Mẫu quyết định công văn chuẩn hành chính đại học có quốc hiệu, căn cứ và nơi nhận',
    descriptionEn: 'Official administrative rector decision format with distribution and seal',
    content: `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
        <div style="text-align: center; border-bottom: 2px solid #0d509d; padding-bottom: 12px; margin-bottom: 20px;">
          <h4 style="margin: 0; text-transform: uppercase; color: #64748b; font-size: 13px; letter-spacing: 1px;">TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP.HCM</h4>
          <h2 style="margin: 8px 0 0 0; color: #0d509d; font-size: 20px; font-weight: 700;">QUYẾT ĐỊNH BAN HÀNH QUY CHẾ HỌC VỤ & ĐÀO TẠO</h2>
          <p style="margin: 4px 0 0 0; color: #64748b; font-size: 13px;">Căn cứ Luật Giáo dục Đại học và Đề án phát triển Trường ĐH Công nghệ Kỹ thuật TP.HCM</p>
        </div>

        <p><strong>HIỆU TRƯỞNG TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH QUYẾT ĐỊNH:</strong></p>

        <p><strong>Điều 1.</strong> Ban hành kèm theo Quyết định này Quy định tổ chức đào tạo theo hệ thống tín chỉ và quy chế đánh giá điểm rèn luyện sinh viên.</p>
        <p><strong>Điều 2.</strong> Quyết định này có hiệu lực kể từ ngày ký và áp dụng cho toàn thể các khóa sinh viên hệ chính quy.</p>
        <p><strong>Điều 3.</strong> Trưởng phòng Đào tạo, Trưởng khoa Công nghệ Thông tin và các đơn vị liên quan chịu trách nhiệm thi hành Quyết định này.</p>

        <div style="margin-top: 30px; display: flex; justify-content: space-between;">
          <div style="font-size: 12px; color: #64748b;">
            <strong>Nơi nhận:</strong><br/>
            - Như Điều 3;<br/>
            - Ban Giám hiệu (để báo cáo);<br/>
            - Lưu: VT, P.ĐT.
          </div>
          <div style="text-align: right;">
            <p style="margin: 0; font-weight: 700; text-transform: uppercase;">HIỆU TRƯỞNG</p>
            <div style="height: 50px;"></div>
            <p style="margin: 0; font-weight: 700; color: #0d509d;">PGS. TS. Lê Hiếu Giang</p>
          </div>
        </div>
      </div>
    `,
  },
  {
    titleVi: 'Thông báo quy chế thi & phúc khảo',
    titleEn: 'Exam Regulations & Appeal Notice',
    descriptionVi: 'Quy định phòng thi, phân ca thi và thủ tục nộp đơn phúc khảo trực tuyến',
    descriptionEn: 'Exam hall regulations, schedule sessions, and online score appeal procedure',
    content: `
      <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">
        <h2 style="color: #0d509d; border-bottom: 2px solid #0d509d; padding-bottom: 8px;">KẾ HOẠCH TỔ CHỨC THI KẾT THÚC HỌC PHẦN & PHÚC KHẢO ĐIỂM</h2>

        <div style="background-color: #eff6ff; border-left: 4px solid #3b82f6; padding: 12px 16px; border-radius: 6px; margin: 16px 0;">
          <strong style="color: #1d4ed8;">Phòng Khảo thí & Đảm bảo Chất lượng</strong> thông báo lịch thi và quy định dành cho sinh viên tham gia kỳ thi học kỳ.
        </div>

        <h3 style="color: #0369a1;">1. Quy định khi vào phòng thi</h3>
        <ul>
          <li>Sinh viên bắt buộc xuất trình Thẻ sinh viên có ảnh hoặc CCCD còn hạn sử dụng.</li>
          <li>Tuyệt đối không mang tài liệu, điện thoại di động và thiết bị thu phát sóng vào phòng thi.</li>
          <li>Có mặt tại phòng thi trước giờ bắt đầu tối thiểu 15 phút.</li>
        </ul>

        <h3 style="color: #0369a1;">2. Thủ tục nộp đơn phúc khảo điểm thi</h3>
        <p>Sinh viên có quyền gửi yêu cầu phúc khảo trực tuyến trên cổng học vụ trong vòng <strong>07 ngày làm việc</strong> kể từ ngày giảng viên công bố điểm chính thức.</p>
      </div>
    `,
  },
];

export function TinyMceEditor({
  value,
  onChange,
  placeholder,
  className,
  height = 500,
  disabled = false,
  readOnly = false,
  locale = 'vi',
  id,
  showTemplates = true,
  showWordCount = true,
  onInit,
}: TinyMceEditorProps) {
  const generatedId = useId();
  const editorId = id || `tinymce-${generatedId.replace(/:/g, '')}`;
  const editorRef = useRef<any>(null);
  const [isDark, setIsDark] = useState(false);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [copied, setCopied] = useState(false);

  // Detect dark mode in document element
  useEffect(() => {
    if (typeof window === 'undefined') return;
    const checkDark = () => {
      setIsDark(document.documentElement.classList.contains('dark'));
    };
    checkDark();

    const observer = new MutationObserver(checkDark);
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });

    return () => observer.disconnect();
  }, []);

  const isVi = locale === 'vi';

  // Apply academic template
  const handleInsertTemplate = (templateHtml: string) => {
    if (editorRef.current) {
      editorRef.current.setContent(templateHtml);
      onChange(templateHtml);
      setTemplatesOpen(false);
    }
  };

  // Copy HTML to clipboard
  const handleCopyHtml = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // fallback
    }
  };

  // Export HTML as file
  const handleExportHtml = () => {
    const blob = new Blob([value], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `document-${new Date().toISOString().slice(0, 10)}.html`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div
      className={cn(
        'relative flex flex-col rounded-lg border border-border/80 bg-card transition-all',
        isFullscreen && 'fixed inset-0 z-50 m-0 h-screen w-screen rounded-none border-none p-6 shadow-2xl overflow-hidden bg-background',
        disabled && 'opacity-60 pointer-events-none',
        className
      )}
    >
      {/* Upper Control Bar */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/70 bg-secondary/30 px-3 py-2 text-foreground">
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-1.5 font-semibold text-xs text-primary uppercase tracking-wider">
            <Sparkles className="h-4 w-4" />
            <span>{isVi ? 'Trình soạn thảo văn bản hành chính' : 'Official Document Editor'}</span>
          </div>
          <span className="rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
            {isVi ? 'Lưu trữ nội bộ an toàn' : 'Secure Internal Storage'}
          </span>
        </div>

        <div className="flex items-center gap-1.5">
          {showTemplates ? (
            <div className="relative">
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setTemplatesOpen(!templatesOpen)}
                className="h-8 gap-1.5 text-xs font-medium"
              >
                <BookOpen className="h-3.5 w-3.5 text-primary" />
                <span>{isVi ? 'Mẫu học vụ' : 'Templates'}</span>
              </Button>

              {templatesOpen ? (
                <>
                  <div
                    className="fixed inset-0 z-40"
                    onClick={() => setTemplatesOpen(false)}
                    aria-hidden="true"
                  />
                  <div className="absolute right-0 top-full z-50 mt-1 w-80 rounded-lg border border-border bg-popover p-2 shadow-xl animate-in fade-in zoom-in-95">
                  <div className="border-b border-border pb-2 px-2 text-xs font-semibold text-foreground">
                    {isVi ? 'Chọn mẫu văn bản học vụ sẵn có' : 'Select Academic Template'}
                  </div>
                  <div className="mt-1 space-y-1">
                    {TINYMCE_TEMPLATES.map((tmpl, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => handleInsertTemplate(tmpl.content)}
                        className="w-full text-left rounded p-2 text-xs transition-colors hover:bg-secondary focus:bg-secondary"
                      >
                        <div className="font-semibold text-foreground">
                          {isVi ? tmpl.titleVi : tmpl.titleEn}
                        </div>
                        <div className="mt-0.5 text-muted-foreground text-[11px] leading-tight line-clamp-2">
                          {isVi ? tmpl.descriptionVi : tmpl.descriptionEn}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              </>
            ) : null}
            </div>
          ) : null}

          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleCopyHtml}
            className="h-8 gap-1 text-xs"
            title={isVi ? 'Sao chép mã HTML' : 'Copy HTML code'}
          >
            {copied ? <Check className="h-3.5 w-3.5 text-emerald-500" /> : <Copy className="h-3.5 w-3.5" />}
            <span className="hidden sm:inline">{copied ? (isVi ? 'Đã sao chép' : 'Copied') : 'HTML'}</span>
          </Button>

          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleExportHtml}
            className="h-8 gap-1 text-xs"
            title={isVi ? 'Tải tệp HTML về máy' : 'Download HTML file'}
          >
            <Download className="h-3.5 w-3.5" />
            <span className="hidden sm:inline">{isVi ? 'Tải HTML' : 'Export'}</span>
          </Button>

          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => setIsFullscreen(!isFullscreen)}
            className="h-8 w-8 p-0"
            title={isFullscreen ? (isVi ? 'Thoát toàn màn hình' : 'Exit fullscreen') : (isVi ? 'Toàn màn hình' : 'Fullscreen')}
          >
            {isFullscreen ? <Minimize2 className="h-3.5 w-3.5" /> : <Maximize2 className="h-3.5 w-3.5" />}
          </Button>
        </div>
      </div>

      {/* TinyMCE Self-Hosted Container */}
      <div className="flex-1 overflow-hidden">
        <Editor
          id={editorId}
          tinymceScriptSrc="/tinymce/tinymce.min.js"
          value={value}
          disabled={disabled || readOnly}
          onEditorChange={(newContent: string) => {
            onChange(newContent);
          }}
          onInit={(evt: any, editor: any) => {
            editorRef.current = editor;
            if (onInit) {
              onInit(evt, editor);
            }
          }}
          init={{
            license_key: 'gpl',
            height: isFullscreen ? 'calc(100vh - 120px)' : height,
            menubar: 'file edit view insert format tools table help',
            menu: {
              file: { title: 'File', items: 'newdocument restoredraft | preview | print' },
              edit: { title: 'Edit', items: 'undo redo | cut copy paste pastetext | selectall | searchreplace' },
              view: { title: 'View', items: 'code | visualaid visualchars visualblocks | preview fullscreen' },
              insert: { title: 'Insert', items: 'image link media codesample inserttable | charmap emoticons hr | pagebreak nonbreaking anchor | insertdatetime' },
              format: { title: 'Format', items: 'bold italic underline strikethrough superscript subscript codeformat | styles blocks fontfamily fontsize align lineheight | forecolor backcolor | removeformat' },
              tools: { title: 'Tools', items: 'code wordcount' },
              table: { title: 'Table', items: 'inserttable | cell row column | tableprops deletetable' },
              help: { title: 'Help', items: 'help' },
            },
            plugins: [
              'accordion',
              'advlist',
              'anchor',
              'autolink',
              'autosave',
              'charmap',
              'code',
              'codesample',
              'directionality',
              'emoticons',
              'fullscreen',
              'help',
              'image',
              'insertdatetime',
              'link',
              'lists',
              'media',
              'nonbreaking',
              'pagebreak',
              'preview',
              'quickbars',
              'searchreplace',
              'table',
              'visualblocks',
              'visualchars',
              'wordcount',
            ],
            toolbar: [
              'undo redo | blocks fontfamily fontsize lineheight | bold italic underline strikethrough | forecolor backcolor | alignleft aligncenter alignright alignjustify | bullist numlist outdent indent',
              'table link image media codesample emoticons | accordion pagebreak charmap insertdatetime | visualblocks visualchars searchreplace wordcount | preview fullscreen | code help',
            ],
            toolbar_mode: 'sliding',
            font_family_formats:
              'Be Vietnam Pro=Be Vietnam Pro,sans-serif; Arial=arial,helvetica,sans-serif; Times New Roman=times new roman,times,serif; Calibri=calibri,sans-serif; Roboto=roboto,sans-serif; Segoe UI=Segoe UI,sans-serif; Courier New=courier new,courier,monospace; Tahoma=tahoma,arial,helvetica,sans-serif; Georgia=georgia,palatino;',
            font_size_formats: '10px 11px 12px 13px 14px 15px 16px 18px 20px 22px 24px 26px 28px 32px 36px 48px',
            line_height_formats: '1 1.15 1.3 1.5 1.65 1.8 2 2.5',
            style_formats: [
              {
                title: 'Tiêu đề (Headings)',
                items: [
                  { title: 'Tiêu đề chính 1 (H1)', format: 'h1' },
                  { title: 'Tiêu đề mục 2 (H2)', format: 'h2' },
                  { title: 'Tiêu đề nhỏ 3 (H3)', format: 'h3' },
                  { title: 'Tiêu đề chi tiết 4 (H4)', format: 'h4' },
                ],
              },
              {
                title: 'Khối hộp học vụ (Academic Callouts)',
                items: [
                  { title: 'Đoạn văn (Paragraph)', format: 'p' },
                  { title: 'Trích dẫn quy định (Blockquote)', format: 'blockquote' },
                  {
                    title: 'Hộp Lưu ý học vụ (Notice Callout)',
                    block: 'div',
                    classes: 'academic-callout academic-callout-info',
                    wrapper: true,
                  },
                  {
                    title: 'Hộp Cảnh báo quan trọng (Warning Callout)',
                    block: 'div',
                    classes: 'academic-callout academic-callout-warning',
                    wrapper: true,
                  },
                  {
                    title: 'Hộp Thành công / Đạt chuẩn (Success Callout)',
                    block: 'div',
                    classes: 'academic-callout academic-callout-success',
                    wrapper: true,
                  },
                ],
              },
              {
                title: 'Định dạng ký tự (Inline)',
                items: [
                  { title: 'Đậm (Bold)', format: 'bold' },
                  { title: 'Nghiêng (Italic)', format: 'italic' },
                  { title: 'Gạch chân (Underline)', format: 'underline' },
                  { title: 'Gạch ngang (Strikethrough)', format: 'strikethrough' },
                  { title: 'Chỉ số trên (Superscript)', format: 'superscript' },
                  { title: 'Chỉ số dưới (Subscript)', format: 'subscript' },
                  { title: 'Mã nguồn dòng (Inline Code)', format: 'code' },
                ],
              },
            ],
            image_advtab: true,
            image_caption: true,
            image_title: true,
            automatic_uploads: true,
            file_picker_types: 'image',
            images_upload_handler: (blobInfo: any) =>
              new Promise((resolve) => {
                resolve(`data:${blobInfo.blob().type};base64,${blobInfo.base64()}`);
              }),
            file_picker_callback: (callback: any, _value: any, meta: any) => {
              if (meta.filetype === 'image') {
                const input = document.createElement('input');
                input.setAttribute('type', 'file');
                input.setAttribute('accept', 'image/*');
                input.onchange = function () {
                  const file = (this as HTMLInputElement).files?.[0];
                  if (file) {
                    const reader = new FileReader();
                    reader.onload = function () {
                      callback(reader.result as string, { title: file.name, alt: file.name });
                    };
                    reader.readAsDataURL(file);
                  }
                };
                input.click();
              }
            },
            table_default_attributes: {
              border: '1',
            },
            table_default_styles: {
              'border-collapse': 'collapse',
              width: '100%',
            },
            table_responsive_width: true,
            table_advtab: true,
            table_cell_advtab: true,
            table_row_advtab: true,
            table_toolbar:
              'tableprops tabledelete | tableinsertrowbefore tableinsertrowafter tabledeleterow | tableinsertcolbefore tableinsertcolafter tabledeletecol',
            quickbars_selection_toolbar: 'bold italic underline | quicklink h2 h3 blockquote | forecolor backcolor',
            quickbars_insert_toolbar: 'quickimage quicktable | hr',
            autosave_interval: '30s',
            autosave_prefix: 'campuscore-tinymce-{path}{query}-{id}-',
            autosave_restore_when_empty: false,
            autosave_retention: '60m',
            skin: isDark ? 'oxide-dark' : 'oxide',
            content_css: isDark ? 'dark' : 'default',
            content_style: `
              body {
                font-family: 'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                font-size: 14.5px;
                line-height: 1.65;
                padding: 16px;
                color: ${isDark ? '#e2e8f0' : '#1e293b'};
                background-color: ${isDark ? '#0f172a' : '#ffffff'};
              }
              table { border-collapse: collapse; width: 100%; margin: 16px 0; }
              table td, table th { border: 1px solid ${isDark ? '#334155' : '#cbd5e1'}; padding: 9px 12px; }
              table th { background-color: ${isDark ? '#1e293b' : '#f8fafc'}; font-weight: 600; text-align: left; }
              img { max-width: 100%; height: auto; border-radius: 6px; margin: 8px 0; }
              blockquote { border-left: 4px solid #0284c7; padding-left: 14px; margin-left: 0; color: #64748b; font-style: italic; }
              .academic-callout { border-radius: 8px; padding: 14px 18px; margin: 16px 0; }
              .academic-callout-info { background-color: ${isDark ? 'rgba(59, 130, 246, 0.15)' : '#eff6ff'}; border-left: 4px solid #3b82f6; color: ${isDark ? '#93c5fd' : '#1d4ed8'}; }
              .academic-callout-warning { background-color: ${isDark ? 'rgba(234, 179, 8, 0.15)' : '#fefce8'}; border-left: 4px solid #eab308; color: ${isDark ? '#fde047' : '#a16207'}; }
              .academic-callout-success { background-color: ${isDark ? 'rgba(34, 197, 94, 0.15)' : '#f0fdf4'}; border-left: 4px solid #22c55e; color: ${isDark ? '#86efac' : '#15803d'}; }
              pre { background-color: ${isDark ? '#1e293b' : '#f1f5f9'}; border-radius: 6px; padding: 12px; overflow-x: auto; font-family: monospace; }
              code { background-color: ${isDark ? '#334155' : '#e2e8f0'}; padding: 2px 5px; border-radius: 4px; font-family: monospace; font-size: 0.9em; }
              details { border: 1px solid ${isDark ? '#334155' : '#cbd5e1'}; border-radius: 6px; padding: 10px 14px; margin: 12px 0; }
              summary { font-weight: 600; cursor: pointer; color: #0284c7; }
            `,
            branding: false,
            promotion: false,
            placeholder: placeholder || (isVi ? 'Bắt đầu soạn thảo nội dung với đầy đủ công cụ TinyMCE chuyên nghiệp...' : 'Start composing content with professional TinyMCE tools...'),
            language_url: undefined,
            contextmenu: 'link image table',
          }}
        />
      </div>
    </div>
  );
}
