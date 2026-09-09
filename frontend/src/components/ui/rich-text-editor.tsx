'use client';

import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  AlertCircle,
  Bold,
  CheckSquare,
  Code,
  Columns,
  Edit3,
  Eye,
  FileCode,
  Heading1,
  Heading2,
  Heading3,
  HelpCircle,
  Image as ImageIcon,
  Italic,
  Link as LinkIcon,
  List,
  ListOrdered,
  Maximize2,
  Minimize2,
  Minus,
  Quote,
  Redo2,
  Sparkles,
  Strikethrough,
  Table,
  Undo2,
  X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { RichContentRenderer } from './rich-content-renderer';

export interface RichTextEditorProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  minHeight?: string;
  disabled?: boolean;
  readOnly?: boolean;
  locale?: 'vi' | 'en';
  hint?: string;
  showTemplates?: boolean;
  id?: string;
  'aria-label'?: string;
}

type EditorViewMode = 'write' | 'split' | 'preview';

interface AcademicTemplate {
  nameVi: string;
  nameEn: string;
  descriptionVi: string;
  descriptionEn: string;
  content: string;
}

const ACADEMIC_TEMPLATES: AcademicTemplate[] = [
  {
    nameVi: 'Thông báo khẩn / Thay đổi lịch',
    nameEn: 'Urgent Notice / Schedule Change',
    descriptionVi: 'Mẫu thông báo khẩn cấp có hộp cảnh báo và bảng thời gian',
    descriptionEn: 'Urgent notice template with alert box and timeline table',
    content: `# THÔNG BÁO KHẨN: THAY ĐỔI LỊCH HỌC VỤ

> [!WARNING]
> **Kính gửi toàn thể Giảng viên và Sinh viên:**
> Đề nghị kiểm tra kỹ thông tin điều chỉnh dưới đây để sắp xếp lịch trình phù hợp.

### 1. Nội dung điều chỉnh
Các lớp học phần và hoạt động thi kết thúc học phần được điều chỉnh theo thời gian biểu mới:

| Học phần | Giảng viên | Thời gian cũ | Thời gian mới | Phòng học |
| :--- | :--- | :--- | :--- | :--- |
| Lập trình Java nâng cao | TS. Nguyễn Văn A | Thứ 2, Tiết 1-3 | Thứ 4, Tiết 4-6 | A1-302 |
| Cơ sở dữ liệu phân tán | ThS. Trần Thị B | Thứ 3, Tiết 7-9 | Thứ 6, Tiết 7-9 | B2-405 |

### 2. Yêu cầu phối hợp
- [ ] Sinh viên chủ động cập nhật thời khóa biểu cá nhân trên cổng portal.
- [ ] Cán bộ quản lý lớp kiểm tra sĩ số và báo cáo nếu có xung đột lịch.

> [!NOTE]
> Mọi thắc mắc vui lòng liên hệ Văn phòng Đào tạo (Phòng A1-101) hoặc gửi hỗ trợ trực tuyến qua Trợ lý AI CampusCore.`,
  },
  {
    nameVi: 'Kế hoạch đăng ký tín chỉ học phần',
    nameEn: 'Course Registration Timeline',
    descriptionVi: 'Lộ trình mở cổng đăng ký môn học theo đợt và khóa học',
    descriptionEn: 'Structured registration timeline by batch and cohort',
    content: `# KẾ HOẠCH ĐĂNG KÝ HỌC PHẦN HỌC KỲ MỚI

> [!IMPORTANT]
> Cổng đăng ký học phần sẽ mở theo từng khung giờ phân luồng. Hệ thống áp dụng **cơ chế chống nghẽn 4 lớp**, sinh viên không cần gửi lặp lệnh.

### 1. Thời gian đăng ký chi tiết
| Đợt đăng ký | Đối tượng áp dụng | Thời gian bắt đầu | Thời gian kết thúc |
| :--- | :--- | :--- | :--- |
| **Đợt 1** | Sinh viên năm cuối & Khóa luận | 08:00 ngày 15/09/2026 | 23:59 ngày 16/09/2026 |
| **Đợt 2** | Sinh viên năm 3 & Năm 2 | 08:00 ngày 17/09/2026 | 23:59 ngày 18/09/2026 |
| **Đợt 3** | Sinh viên năm 1 & Đăng ký bù | 08:00 ngày 19/09/2026 | 17:00 ngày 20/09/2026 |

### 2. Quy định bắt buộc
1. Số tín chỉ đăng ký tối thiểu: **12 tín chỉ**, tối đa: **24 tín chỉ**.
2. Kiểm tra điều kiện học phần tiên quyết trước khi nhấn đăng ký.
3. Học phí cần hoàn tất theo quy định sau khi chốt danh sách chính thức.

> [!TIP]
> Sử dụng tính năng **Lọc theo khoa** trên giao diện Đăng ký học phần để tìm kiếm nhanh mã lớp mong muốn.`,
  },
  {
    nameVi: 'Hướng dẫn quy chế & biểu phí học tập',
    nameEn: 'Academic Policy & Tuition Guide',
    descriptionVi: 'Biên soạn tài liệu quy chế đào tạo và tri thức cho Trợ lý RAG',
    descriptionEn: 'Academic regulation documentation suited for Assistant RAG',
    content: `# HƯỚNG DẪN QUY CHẾ ĐÀO TẠO & HỌC VỤ

Tài liệu quy định chi tiết về đánh giá điểm học phần và điều kiện xét tốt nghiệp đại học hệ chính quy:

### 1. Thang điểm đánh giá
- **Điểm quá trình (40-50%)**: Bao gồm điểm chuyên cần, bài tập nhóm và kiểm tra giữa kỳ.
- **Điểm thi kết thúc (50-60%)**: Đánh giá toàn diện chuẩn đầu ra của học phần.
- **Điểm chữ quy đổi**:
  - \`A\` / \`A+\`: 8.5 - 10.0 (Tương đương 4.0 GPA)
  - \`B\` / \`B+\`: 7.0 - 8.4 (Tương đương 3.0 - 3.5 GPA)
  - \`C\` / \`C+\`: 5.5 - 6.9 (Tương đương 2.0 - 2.5 GPA)
  - \`D\` / \`D+\`: 4.0 - 5.4 (Tương đương 1.0 - 1.5 GPA)
  - \`F\`: Dưới 4.0 (Không đạt, phải đăng ký học lại)

### 2. Điều kiện nhận khóa luận tốt nghiệp
- Tích lũy tối thiểu **115 tín chỉ** thuộc khung chương trình đào tạo.
- Điểm trung bình tích lũy (CPA) đạt từ **2.50** trở lên.
- Không trong thời gian bị kỷ luật từ mức khiển trách trở lên.`,
  },
];

export function RichTextEditor({
  value,
  onChange,
  placeholder = 'Nhập nội dung tại đây... (Hỗ trợ Markdown, bảng biểu, danh sách, ghi chú)',
  className,
  minHeight = '200px',
  disabled = false,
  readOnly = false,
  locale = 'vi',
  hint,
  showTemplates = true,
  id,
  'aria-label': ariaLabel,
}: RichTextEditorProps) {
  const isVi = locale === 'vi';
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const editorContainerRef = useRef<HTMLDivElement>(null);

  // States
  const [viewMode, setViewMode] = useState<EditorViewMode>('write');
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [templatesOpen, setTemplatesOpen] = useState(false);
  const [linkDialogOpen, setLinkDialogOpen] = useState(false);
  const [linkText, setLinkText] = useState('');
  const [linkUrl, setLinkUrl] = useState('');
  const [helpDialogOpen, setHelpDialogOpen] = useState(false);

  // History stack for Undo/Redo
  const [history, setHistory] = useState<string[]>([value || '']);
  const [historyIndex, setHistoryIndex] = useState(0);
  const isUpdatingFromHistory = useRef(false);

  // Push new state to history if modified by user
  const pushHistory = useCallback((newText: string) => {
    if (isUpdatingFromHistory.current) return;
    setHistory((prev) => {
      const sliced = prev.slice(0, historyIndex + 1);
      if (sliced[sliced.length - 1] === newText) return prev;
      return [...sliced, newText].slice(-30); // max 30 states
    });
    setHistoryIndex((prev) => Math.min(prev + 1, 29));
  }, [historyIndex]);

  const handleTextChange = useCallback((newText: string) => {
    onChange(newText);
    pushHistory(newText);
  }, [onChange, pushHistory]);

  const handleUndo = useCallback(() => {
    if (historyIndex > 0) {
      isUpdatingFromHistory.current = true;
      const targetText = history[historyIndex - 1];
      setHistoryIndex(historyIndex - 1);
      onChange(targetText);
      setTimeout(() => {
        isUpdatingFromHistory.current = false;
      }, 50);
    }
  }, [history, historyIndex, onChange]);

  const handleRedo = useCallback(() => {
    if (historyIndex < history.length - 1) {
      isUpdatingFromHistory.current = true;
      const targetText = history[historyIndex + 1];
      setHistoryIndex(historyIndex + 1);
      onChange(targetText);
      setTimeout(() => {
        isUpdatingFromHistory.current = false;
      }, 50);
    }
  }, [history, historyIndex, onChange]);

  // Text insertion helper
  const insertText = useCallback((before: string, after: string = '', defaultSelection: string = '') => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const currentVal = textarea.value;

    const selected = currentVal.substring(start, end) || defaultSelection;
    const replacement = `${before}${selected}${after}`;

    const updated = currentVal.substring(0, start) + replacement + currentVal.substring(end);
    handleTextChange(updated);

    setTimeout(() => {
      textarea.focus();
      const newCursorStart = start + before.length;
      const newCursorEnd = newCursorStart + selected.length;
      textarea.setSelectionRange(newCursorStart, newCursorEnd);
    }, 10);
  }, [handleTextChange]);

  // Insert block at line start
  const insertLinePrefix = useCallback((prefix: string, defaultText: string = '') => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const currentVal = textarea.value;

    // Find start of current line
    const lastNewline = currentVal.lastIndexOf('\n', start - 1);
    const lineStart = lastNewline === -1 ? 0 : lastNewline + 1;

    const beforeLine = currentVal.substring(0, lineStart);
    const afterLine = currentVal.substring(lineStart);

    // If current line already has prefix, remove it; else add it
    let updated: string;
    let newCursor: number;

    if (afterLine.startsWith(prefix)) {
      updated = beforeLine + afterLine.substring(prefix.length);
      newCursor = Math.max(lineStart, start - prefix.length);
    } else {
      const addition = afterLine.trim() === '' && defaultText ? `${prefix}${defaultText}` : prefix;
      updated = beforeLine + addition + afterLine;
      newCursor = start + prefix.length;
    }

    handleTextChange(updated);
    setTimeout(() => {
      textarea.focus();
      textarea.setSelectionRange(newCursor, newCursor);
    }, 10);
  }, [handleTextChange]);

  // Insert block snippet
  const insertSnippet = useCallback((snippet: string) => {
    const textarea = textareaRef.current;
    if (!textarea) return;

    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const currentVal = textarea.value;

    const needsNewlineBefore = start > 0 && currentVal[start - 1] !== '\n';
    const needsNewlineAfter = end < currentVal.length && currentVal[end] !== '\n';

    const insertion = `${needsNewlineBefore ? '\n\n' : ''}${snippet}${needsNewlineAfter ? '\n\n' : ''}`;
    const updated = currentVal.substring(0, start) + insertion + currentVal.substring(end);

    handleTextChange(updated);
    setTimeout(() => {
      textarea.focus();
      const cursor = start + insertion.length;
      textarea.setSelectionRange(cursor, cursor);
    }, 10);
  }, [handleTextChange]);

  // Open Link Dialog
  const openLinkModal = () => {
    const textarea = textareaRef.current;
    const selected = textarea ? textarea.value.substring(textarea.selectionStart, textarea.selectionEnd) : '';
    setLinkText(selected || (isVi ? 'Liên kết' : 'Link text'));
    setLinkUrl('https://');
    setLinkDialogOpen(true);
  };

  const applyLink = () => {
    if (!linkUrl || linkUrl === 'https://') {
      setLinkDialogOpen(false);
      return;
    }
    insertText('[', `](${linkUrl.trim()})`, linkText || 'Link');
    setLinkDialogOpen(false);
  };

  // Keyboard shortcut listener
  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    const isMac = typeof window !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform);
    const modKey = isMac ? event.metaKey : event.ctrlKey;

    if (modKey && event.key.toLowerCase() === 'b') {
      event.preventDefault();
      insertText('**', '**', isVi ? 'văn bản in đậm' : 'bold text');
    } else if (modKey && event.key.toLowerCase() === 'i') {
      event.preventDefault();
      insertText('*', '*', isVi ? 'văn bản in nghiêng' : 'italic text');
    } else if (modKey && event.key.toLowerCase() === 'k') {
      event.preventDefault();
      openLinkModal();
    } else if (modKey && event.key.toLowerCase() === 'z') {
      event.preventDefault();
      if (event.shiftKey) {
        handleRedo();
      } else {
        handleUndo();
      }
    } else if (modKey && event.key.toLowerCase() === 'y') {
      event.preventDefault();
      handleRedo();
    } else if (event.key === 'Tab') {
      // Indent with 2 spaces
      event.preventDefault();
      insertText('  ', '');
    } else if (event.key === 'Escape' && isFullscreen) {
      event.preventDefault();
      setIsFullscreen(false);
    }
  };

  // Listen to Escape for fullscreen
  useEffect(() => {
    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && isFullscreen) {
        setIsFullscreen(false);
      }
    };
    window.addEventListener('keydown', handleGlobalKeyDown);
    return () => window.removeEventListener('keydown', handleGlobalKeyDown);
  }, [isFullscreen]);

  // Statistics
  const stats = useMemo(() => {
    const text = value || '';
    const chars = text.length;
    const words = text.trim() ? text.trim().split(/\s+/).length : 0;
    const minutes = Math.max(1, Math.ceil(words / 200));
    return { chars, words, minutes };
  }, [value]);

  return (
    <div
      ref={editorContainerRef}
      className={cn(
        'group flex flex-col rounded-lg border border-border/80 bg-background transition-all focus-within:border-primary/70 focus-within:ring-2 focus-within:ring-primary/20',
        isFullscreen && 'fixed inset-0 z-50 m-0 h-screen w-screen rounded-none border-none p-6 shadow-2xl overflow-y-auto',
        disabled && 'opacity-60 pointer-events-none',
        className
      )}
    >
      {/* Top Toolbar */}
      <div
        role="toolbar"
        aria-label={isVi ? 'Thanh công cụ soạn thảo' : 'Editor formatting toolbar'}
        className="flex flex-wrap items-center justify-between gap-1 border-b border-border/70 bg-secondary/40 px-3 py-2 text-foreground"
      >
        {/* Left Toolbar Actions */}
        <div className="flex flex-wrap items-center gap-0.5">
          {/* History */}
          <button
            type="button"
            onClick={handleUndo}
            disabled={historyIndex <= 0 || readOnly}
            title={isVi ? 'Hoàn tác (Ctrl+Z)' : 'Undo (Ctrl+Z)'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Undo2 className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={handleRedo}
            disabled={historyIndex >= history.length - 1 || readOnly}
            title={isVi ? 'Làm lại (Ctrl+Y)' : 'Redo (Ctrl+Y)'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Redo2 className="h-4 w-4" />
          </button>

          <span className="mx-1 h-4 w-px bg-border/80" aria-hidden="true" />

          {/* Text Formats */}
          <button
            type="button"
            onClick={() => insertText('**', '**', isVi ? 'in đậm' : 'bold')}
            disabled={readOnly}
            title={isVi ? 'In đậm (Ctrl+B)' : 'Bold (Ctrl+B)'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Bold className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertText('*', '*', isVi ? 'in nghiêng' : 'italic')}
            disabled={readOnly}
            title={isVi ? 'In nghiêng (Ctrl+I)' : 'Italic (Ctrl+I)'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Italic className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertText('~~', '~~', isVi ? 'gạch ngang' : 'strikethrough')}
            disabled={readOnly}
            title={isVi ? 'Gạch ngang' : 'Strikethrough'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Strikethrough className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertText('`', '`', 'code')}
            disabled={readOnly}
            title={isVi ? 'Mã nội dòng (Inline Code)' : 'Inline Code'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Code className="h-4 w-4" />
          </button>

          <span className="mx-1 h-4 w-px bg-border/80" aria-hidden="true" />

          {/* Headings */}
          <button
            type="button"
            onClick={() => insertLinePrefix('# ', isVi ? 'Tiêu đề lớn' : 'Heading 1')}
            disabled={readOnly}
            title={isVi ? 'Tiêu đề H1' : 'Heading 1'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Heading1 className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertLinePrefix('## ', isVi ? 'Tiêu đề phụ' : 'Heading 2')}
            disabled={readOnly}
            title={isVi ? 'Tiêu đề H2' : 'Heading 2'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Heading2 className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertLinePrefix('### ', isVi ? 'Tiêu đề cấp 3' : 'Heading 3')}
            disabled={readOnly}
            title={isVi ? 'Tiêu đề H3' : 'Heading 3'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Heading3 className="h-4 w-4" />
          </button>

          <span className="mx-1 h-4 w-px bg-border/80" aria-hidden="true" />

          {/* Lists and Blocks */}
          <button
            type="button"
            onClick={() => insertLinePrefix('- ', isVi ? 'Mục danh sách' : 'List item')}
            disabled={readOnly}
            title={isVi ? 'Danh sách dấu đầu dòng' : 'Bulleted List'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <List className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertLinePrefix('1. ', isVi ? 'Mục có thứ tự' : 'Ordered item')}
            disabled={readOnly}
            title={isVi ? 'Danh sách có thứ tự' : 'Numbered List'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <ListOrdered className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertLinePrefix('- [ ] ', isVi ? 'Công việc cần làm' : 'Task to complete')}
            disabled={readOnly}
            title={isVi ? 'Danh sách kiểm việc (Checklist)' : 'Task Checklist'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <CheckSquare className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertLinePrefix('> ', isVi ? 'Đoạn trích dẫn / Lưu ý' : 'Quote or note')}
            disabled={readOnly}
            title={isVi ? 'Khối trích dẫn' : 'Blockquote'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Quote className="h-4 w-4" />
          </button>

          <span className="mx-1 h-4 w-px bg-border/80" aria-hidden="true" />

          {/* Alert Callouts */}
          <button
            type="button"
            onClick={() => insertSnippet(`> [!NOTE]\n> ${isVi ? 'Ghi chú học vụ quan trọng cần lưu ý tại đây.' : 'Important academic note to record here.'}`)}
            disabled={readOnly}
            title={isVi ? 'Chèn hộp Lưu ý (Note)' : 'Insert Note Callout'}
            className="rounded p-1.5 text-blue-600 dark:text-blue-400 transition-colors hover:bg-blue-50 dark:hover:bg-blue-950/40 disabled:opacity-40"
          >
            <AlertCircle className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertSnippet(`> [!WARNING]\n> ${isVi ? 'Cảnh báo hạn chót hoặc nội dung bắt buộc.' : 'Warning about deadline or mandatory action.'}`)}
            disabled={readOnly}
            title={isVi ? 'Chèn hộp Cảnh báo (Warning)' : 'Insert Warning Callout'}
            className="rounded p-1.5 text-amber-600 dark:text-amber-400 transition-colors hover:bg-amber-50 dark:hover:bg-amber-950/40 disabled:opacity-40"
          >
            <AlertCircle className="h-4 w-4" />
          </button>

          <span className="mx-1 h-4 w-px bg-border/80" aria-hidden="true" />

          {/* Insertions */}
          <button
            type="button"
            onClick={openLinkModal}
            disabled={readOnly}
            title={isVi ? 'Chèn liên kết (Ctrl+K)' : 'Insert Link (Ctrl+K)'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <LinkIcon className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertText('![', '](https://example.com/image.png)', isVi ? 'Mô tả hình ảnh' : 'Image description')}
            disabled={readOnly}
            title={isVi ? 'Chèn hình ảnh' : 'Insert Image'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <ImageIcon className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() =>
              insertSnippet(
                `| ${isVi ? 'Tiêu chí' : 'Header 1'} | ${isVi ? 'Thông tin' : 'Header 2'} | ${isVi ? 'Ghi chú' : 'Notes'} |\n| :--- | :--- | :--- |\n| ${isVi ? 'Mục 1' : 'Row 1'} | ${isVi ? 'Giá trị A' : 'Value A'} | ${isVi ? 'Chi tiết' : 'Details'} |\n| ${isVi ? 'Mục 2' : 'Giá trị B'} | ${isVi ? 'Chi tiết' : 'Details'} |`
              )
            }
            disabled={readOnly}
            title={isVi ? 'Chèn bảng biểu 3 cột' : 'Insert 3-column Table'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Table className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertSnippet('```java\n// Mã nguồn minh họa\n```')}
            disabled={readOnly}
            title={isVi ? 'Khối mã nguồn (Code block)' : 'Code block'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <FileCode className="h-4 w-4" />
          </button>
          <button
            type="button"
            onClick={() => insertSnippet('---')}
            disabled={readOnly}
            title={isVi ? 'Đường kẻ phân cách' : 'Horizontal Rule'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground disabled:opacity-40"
          >
            <Minus className="h-4 w-4" />
          </button>

          {/* Academic Templates Menu */}
          {showTemplates ? (
            <div className="relative">
              <button
                type="button"
                onClick={() => setTemplatesOpen(!templatesOpen)}
                disabled={readOnly}
                className="inline-flex items-center gap-1 rounded bg-primary/10 px-2 py-1 text-xs font-semibold text-primary transition-colors hover:bg-primary/20"
                title={isVi ? 'Mẫu thông báo & quy chế soạn sẵn' : 'Pre-built academic templates'}
              >
                <Sparkles className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">{isVi ? 'Mẫu học vụ' : 'Templates'}</span>
              </button>

              {templatesOpen ? (
                <>
                  <div
                    className="fixed inset-0 z-10"
                    onClick={() => setTemplatesOpen(false)}
                    aria-hidden="true"
                  />
                  <div className="absolute left-0 top-full z-20 mt-1 w-72 rounded-lg border border-border/80 bg-popover p-1 shadow-lg">
                    <div className="px-2 py-1 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      {isVi ? 'Chọn mẫu văn bản nhanh' : 'Select quick template'}
                    </div>
                    {ACADEMIC_TEMPLATES.map((tmpl, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => {
                          handleTextChange(tmpl.content);
                          setTemplatesOpen(false);
                        }}
                        className="w-full text-left rounded-md px-2 py-1.5 text-xs hover:bg-secondary transition-colors"
                      >
                        <div className="font-semibold text-foreground">{isVi ? tmpl.nameVi : tmpl.nameEn}</div>
                        <div className="text-[11px] text-muted-foreground line-clamp-1">{isVi ? tmpl.descriptionVi : tmpl.descriptionEn}</div>
                      </button>
                    ))}
                  </div>
                </>
              ) : null}
            </div>
          ) : null}
        </div>

        {/* Right Toolbar Actions: View Modes & Fullscreen */}
        <div className="flex items-center gap-1">
          {/* Mode Switcher */}
          <div className="flex items-center rounded-md border border-border/70 bg-background/80 p-0.5" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={viewMode === 'write'}
              onClick={() => setViewMode('write')}
              title={isVi ? 'Chỉ soạn thảo' : 'Write only'}
              className={cn(
                'flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors',
                viewMode === 'write' ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <Edit3 className="h-3.5 w-3.5" />
              <span className="hidden md:inline">{isVi ? 'Soạn thảo' : 'Write'}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={viewMode === 'split'}
              onClick={() => setViewMode('split')}
              title={isVi ? 'Chia đôi trực tiếp (Split preview)' : 'Split live preview'}
              className={cn(
                'flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors',
                viewMode === 'split' ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <Columns className="h-3.5 w-3.5" />
              <span className="hidden md:inline">{isVi ? 'Chia đôi' : 'Split'}</span>
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={viewMode === 'preview'}
              onClick={() => setViewMode('preview')}
              title={isVi ? 'Chỉ xem trước' : 'Preview only'}
              className={cn(
                'flex items-center gap-1 rounded px-2 py-1 text-xs font-medium transition-colors',
                viewMode === 'preview' ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground'
              )}
            >
              <Eye className="h-3.5 w-3.5" />
              <span className="hidden md:inline">{isVi ? 'Xem trước' : 'Preview'}</span>
            </button>
          </div>

          {/* Help */}
          <button
            type="button"
            onClick={() => setHelpDialogOpen(true)}
            title={isVi ? 'Hướng dẫn phím tắt & cú pháp' : 'Formatting guide & shortcuts'}
            className="rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
          >
            <HelpCircle className="h-4 w-4" />
          </button>

          {/* Fullscreen Toggle */}
          <button
            type="button"
            onClick={() => setIsFullscreen(!isFullscreen)}
            title={isFullscreen ? (isVi ? 'Thu nhỏ (Esc)' : 'Exit Fullscreen (Esc)') : (isVi ? 'Toàn màn hình' : 'Fullscreen')}
            className={cn(
              'rounded p-1.5 text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground',
              isFullscreen && 'bg-primary/20 text-primary'
            )}
          >
            {isFullscreen ? <Minimize2 className="h-4 w-4" /> : <Maximize2 className="h-4 w-4" />}
          </button>
        </div>
      </div>

      {/* Editor Body */}
      <div className={cn('relative flex-1 min-h-0', isFullscreen && 'h-[calc(100vh-140px)]')}>
        {/* Write Only Mode */}
        {viewMode === 'write' ? (
          <textarea
            id={id}
            ref={textareaRef}
            value={value}
            onChange={(e) => handleTextChange(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            disabled={disabled}
            readOnly={readOnly}
            aria-label={ariaLabel || (isVi ? 'Vùng soạn thảo nội dung' : 'Content editor area')}
            style={{ minHeight: isFullscreen ? '100%' : minHeight }}
            className="w-full resize-y rounded-b-md bg-transparent p-4 font-mono text-sm leading-relaxed text-foreground placeholder:text-muted-foreground/60 focus:outline-none focus:ring-0 border-none"
          />
        ) : null}

        {/* Split View Mode */}
        {viewMode === 'split' ? (
          <div className="grid h-full grid-cols-1 divide-y border-b border-border/70 md:grid-cols-2 md:divide-x md:divide-y-0">
            <div className="flex flex-col">
              <div className="border-b border-border/60 bg-secondary/20 px-4 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Mã Markdown' : 'Markdown Code'}
              </div>
              <textarea
                id={id}
                ref={textareaRef}
                value={value}
                onChange={(e) => handleTextChange(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={placeholder}
                disabled={disabled}
                readOnly={readOnly}
                aria-label={ariaLabel || (isVi ? 'Vùng soạn thảo nội dung' : 'Content editor area')}
                style={{ minHeight: isFullscreen ? '100%' : minHeight }}
                className="w-full flex-1 resize-none bg-transparent p-4 font-mono text-sm leading-relaxed text-foreground placeholder:text-muted-foreground/60 focus:outline-none border-none"
              />
            </div>
            <div className="flex flex-col overflow-y-auto bg-card/40">
              <div className="border-b border-border/60 bg-secondary/20 px-4 py-1 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                {isVi ? 'Xem trước trực quan' : 'Live Rendered View'}
              </div>
              <div className="flex-1 p-4 overflow-y-auto" style={{ minHeight: isFullscreen ? '100%' : minHeight }}>
                <RichContentRenderer
                  content={value}
                  fallbackText={isVi ? '(Bản xem trước trực tiếp sẽ xuất hiện tại đây khi bạn nhập)' : '(Live preview will appear here as you type)'}
                />
              </div>
            </div>
          </div>
        ) : null}

        {/* Preview Only Mode */}
        {viewMode === 'preview' ? (
          <div className="p-5 overflow-y-auto bg-card/40" style={{ minHeight: isFullscreen ? '100%' : minHeight }}>
            <RichContentRenderer
              content={value}
              fallbackText={isVi ? '(Chưa có nội dung xem trước)' : '(No content to preview)'}
            />
          </div>
        ) : null}
      </div>

      {/* Bottom Status / Stats Bar */}
      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-border/70 bg-secondary/30 px-3 py-1.5 text-xs text-muted-foreground">
        <div className="flex items-center gap-3">
          <span>
            {stats.chars} {isVi ? 'ký tự' : 'characters'}
          </span>
          <span>·</span>
          <span>
            {stats.words} {isVi ? 'từ' : 'words'}
          </span>
          <span>·</span>
          <span>
            ~{stats.minutes} {isVi ? 'phút đọc' : 'min read'}
          </span>
          {hint ? (
            <>
              <span>·</span>
              <span className="text-muted-foreground/80">{hint}</span>
            </>
          ) : null}
        </div>
        <div className="hidden sm:flex items-center gap-2 text-[11px]">
          <span className="rounded bg-secondary/80 px-1.5 py-0.5 font-mono">Ctrl+B</span> {isVi ? 'In đậm' : 'Bold'}
          <span className="rounded bg-secondary/80 px-1.5 py-0.5 font-mono">Ctrl+I</span> {isVi ? 'In nghiêng' : 'Italic'}
          <span className="rounded bg-secondary/80 px-1.5 py-0.5 font-mono">Ctrl+K</span> {isVi ? 'Liên kết' : 'Link'}
        </div>
      </div>

      {/* Link Insertion Dialog */}
      {linkDialogOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4"
          onClick={(e) => {
            if (e.target === e.currentTarget) setLinkDialogOpen(false);
          }}
        >
          <div className="w-full max-w-md rounded-lg border border-border/80 bg-card p-5 shadow-xl">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <h3 className="font-semibold text-foreground">
                {isVi ? 'Chèn liên kết đường dẫn' : 'Insert Hyperlink'}
              </h3>
              <button
                type="button"
                onClick={() => setLinkDialogOpen(false)}
                className="rounded p-1 text-muted-foreground hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="mt-4 space-y-3 text-sm">
              <div>
                <label className="block font-medium text-foreground mb-1">
                  {isVi ? 'Văn bản hiển thị' : 'Link Text'}
                </label>
                <input
                  type="text"
                  value={linkText}
                  onChange={(e) => setLinkText(e.target.value)}
                  className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="CampusCore Portal"
                />
              </div>
              <div>
                <label className="block font-medium text-foreground mb-1">
                  {isVi ? 'Địa chỉ URL' : 'Link URL'}
                </label>
                <input
                  type="url"
                  value={linkUrl}
                  onChange={(e) => setLinkUrl(e.target.value)}
                  className="w-full rounded-md border border-input bg-background px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-primary"
                  placeholder="https://..."
                />
              </div>
            </div>
            <div className="mt-5 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setLinkDialogOpen(false)}
                className="rounded-md border border-border/80 px-3 py-1.5 text-sm font-medium hover:bg-secondary"
              >
                {isVi ? 'Hủy' : 'Cancel'}
              </button>
              <button
                type="button"
                onClick={applyLink}
                className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground hover:bg-primary/90"
              >
                {isVi ? 'Chèn liên kết' : 'Insert Link'}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {/* Help / Shortcuts Dialog */}
      {helpDialogOpen ? (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4"
          onClick={(e) => {
            if (e.target === e.currentTarget) setHelpDialogOpen(false);
          }}
        >
          <div className="w-full max-w-lg rounded-lg border border-border/80 bg-card p-6 shadow-xl">
            <div className="flex items-center justify-between border-b border-border/60 pb-3">
              <div className="flex items-center gap-2">
                <HelpCircle className="h-5 w-5 text-primary" />
                <h3 className="font-bold text-foreground">
                  {isVi ? 'Cẩm nang định dạng soạn thảo học vụ' : 'Editor Markdown & Formatting Guide'}
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setHelpDialogOpen(false)}
                className="rounded p-1 text-muted-foreground hover:text-foreground"
              >
                <X className="h-4 w-4" />
              </button>
            </div>
            <div className="mt-4 max-h-96 space-y-3 overflow-y-auto text-xs text-foreground/90 leading-relaxed pr-2">
              <div className="grid grid-cols-2 gap-2 border-b border-border/60 pb-2">
                <span className="font-semibold text-primary">{isVi ? 'Cú pháp' : 'Syntax'}</span>
                <span className="font-semibold text-primary">{isVi ? 'Kết quả hiển thị' : 'Result'}</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code># Tiêu đề 1</code>
                <span>Tiêu đề cấp lớn</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>## Tiêu đề 2</code>
                <span>Tiêu đề phụ</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>**In đậm**</code>
                <strong>In đậm văn bản</strong>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>*In nghiêng*</code>
                <em>In nghiêng văn bản</em>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>- [ ] Việc cần làm</code>
                <span>Hộp kiểm danh sách việc (Checklist)</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>&gt; [!NOTE]</code>
                <span>Hộp cảnh báo/ghi chú nổi bật màu xanh</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>&gt; [!WARNING]</code>
                <span>Hộp cảnh báo khẩn cấp màu vàng cam</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>| Cột 1 | Cột 2 |</code>
                <span>Bảng biểu dữ liệu có viền và tiêu đề</span>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <code>[Tên](https://...)</code>
                <span>Đường dẫn liên kết an toàn</span>
              </div>
            </div>
            <div className="mt-5 flex justify-end">
              <button
                type="button"
                onClick={() => setHelpDialogOpen(false)}
                className="rounded-md bg-primary px-4 py-1.5 text-xs font-semibold text-primary-foreground hover:bg-primary/90"
              >
                {isVi ? 'Đã hiểu' : 'Got it'}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
