'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ArrowUpDown,
  BookOpen,
  Check,
  ChevronDown,
  ChevronUp,
  Clock,
  Code2,
  Copy,
  Download,
  Eye,
  FileEdit,
  FileText,
  GripVertical,
  ImageIcon,
  Layers,
  ListOrdered,
  Megaphone,
  Palette,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Send,
  Sliders,
  Sparkles,
  Trash2,
  X,
} from 'lucide-react';
import { toast } from 'sonner';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { announcementsApi, type AnnouncementRecord } from '@/lib/api';
import { AnnouncementReaderModal } from '@/components/announcements/AnnouncementReaderModal';
import {
  AnnouncementEditModal,
  EDIT_BANNER_PRESETS,
} from '@/components/announcements/AnnouncementEditModal';
import {
  DEFAULT_SITE_APPEARANCE,
  SITE_APPEARANCE_ACCENTS,
  applyPageOrder,
  orderByIds,
  type SiteAppearance,
  type SiteAppearanceAccent,
} from '@/lib/site-appearance';
import {
  broadcastSiteAppearance,
  fetchSiteAppearance,
  saveSiteAppearance,
} from '@/lib/site-appearance-client';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState } from '@/components/ui/state-block';
import { RichTextEditor } from '@/components/ui/rich-text-editor';
import { TinyMceEditor, clearTinyMceAutosaveDrafts } from '@/components/ui/tinymce-editor';
import { SortableList, DragHandle } from '@/components/ui/sortable-list';
import {
  UnsavedChangesConfirmDialog,
} from '@/components/ui/unsaved-changes-confirm';
import {
  announcementLengthViolationMessage,
  findAnnouncementLengthViolation,
} from '@/lib/announcement-limits';
import { announcementPriorityLabel } from '@/lib/announcement-presentation';
import { useUnsavedChangesGuard } from '@/lib/use-unsaved-changes-guard';
import { replaceCoverBlock, removeCoverBlock } from '@/lib/cover-banner';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { cn } from '@/lib/utils';
import {
  buildDocumentFilename,
  parseStoredEditorDocument,
  shouldSeedDefaultEditorDocument,
  type StoredEditorDocument,
} from '@/lib/editor-document';
import {
  EDITOR_BLOCK_HTML,
  EDITOR_BLOCK_TYPES,
  EDITOR_SEED_HTML,
  EDITOR_SEED_MARKDOWN,
  EDITOR_TEMPLATE_HTML,
  EDITOR_TEMPLATE_IDS,
  type EditorBlockType,
  type EditorTemplateId,
} from '@/lib/editor-templates';
import { getMessages } from '@/i18n/messages';

export interface ContentBlock {
  id: string;
  type: 'header' | 'recipient' | 'summary' | 'table' | 'clauses' | 'notice' | 'signoff';
  title: string;
  description: string;
  enabled: boolean;
  htmlContent: string;
}

interface EditorBlockCopy {
  title: string;
  description: string;
}

/**
 * The block list is metadata (titles/descriptions) from the `editor.blocks`
 * messages plus the shared HTML bodies from lib/editor-templates.ts.
 */
function buildDefaultBlocks(copy: Record<EditorBlockType, EditorBlockCopy>): ContentBlock[] {
  return EDITOR_BLOCK_TYPES.map((type) => ({
    id: `block-${type}`,
    type,
    title: copy[type].title,
    description: copy[type].description,
    enabled: true,
    htmlContent: EDITOR_BLOCK_HTML[type],
  }));
}

const PRIORITY_VALUES = ['URGENT', 'HIGH', 'NORMAL', 'LOW'] as const;
const TARGET_ROLE_VALUES = ['ALL', 'BOTH', 'STUDENT', 'LECTURER'] as const;

type TargetRoleOption = (typeof TARGET_ROLE_VALUES)[number];

/**
 * Maps the composer's audience choice onto the stored announcement audience.
 * `BOTH` is a targeted STUDENT+LECTURER audience, distinct from `ALL`
 * (isGlobal campus-wide): collapsing the two made editing a role-scoped
 * notice silently turn it into a campus-wide one and add ADMIN to it.
 */
function audienceFor(role: TargetRoleOption) {
  if (role === 'ALL') return { isGlobal: true, targetRoles: ['STUDENT', 'LECTURER', 'ADMIN'] };
  if (role === 'BOTH') return { isGlobal: false, targetRoles: ['STUDENT', 'LECTURER'] };
  return { isGlobal: false, targetRoles: [role] };
}

const STORAGE_KEY = 'campuscore_editor_document';
const EDITOR_TYPE_KEY = 'campuscore_editor_engine';

export default function AcademicEditorPage() {
  const { user, hasAccess, isLoading: authLoading, isForbidden } = useRequireAuth([
    'ADMIN',
    'SUPER_ADMIN',
  ]);
  const { locale, formatDate, messages } = useI18n();
  const isVi = locale === 'vi';
  const editorCopy = messages.editor;

  // Navigation tab state: 'announcement' (Editor) | 'hero' (Site Controls) | 'templates' (Document Library)
  const [activeTab, setActiveTab] = useState<'announcement' | 'hero' | 'templates'>('announcement');

  // Editor states
  const [editorType, setEditorType] = useState<'tinymce' | 'markdown'>('tinymce');
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('notice');
  const [content, setContent] = useState('');
  const [priority, setPriority] = useState<'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'>('NORMAL');
  const [targetRole, setTargetRole] = useState<TargetRoleOption>('ALL');
  const [priorityKnown, setPriorityKnown] = useState(true);
  const [audienceKnown, setAudienceKnown] = useState(true);
  const [isPublishingNotice, setIsPublishingNotice] = useState(false);
  const [lastSaved, setLastSaved] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editingVersion, setEditingVersion] = useState<number>(0);
  const [publishedNotices, setPublishedNotices] = useState<AnnouncementRecord[]>([]);
  const [loadingNotices, setLoadingNotices] = useState(false);
  // An API outage must not read as an empty database: the honest error state
  // stops an author from re-publishing a "missing" notice as a duplicate.
  const [noticesLoadError, setNoticesLoadError] = useState(false);
  const [previewingNotice, setPreviewingNotice] = useState<AnnouncementRecord | null>(null);
  // A toast here expires and is routinely missed, so a deep link that failed to
  // resolve has to stay on the page until the author acknowledges it.
  const [deepLinkNotice, setDeepLinkNotice] = useState<{
    targetId: string;
    reason: 'not-found' | 'load-failed';
  } | null>(null);
  const [editingNoticeModal, setEditingNoticeModal] = useState<AnnouncementRecord | null>(null);
  const studioEditorRef = useRef<{ id: string } | null>(null);

  // RT-P3-3: unsaved-changes guard (beforeunload + in-app confirm) shared with
  // the other editors via lib/use-unsaved-changes-guard.ts. A document is
  // dirty once the author typed a title or moved the body off the template.
  const defaultBodyFor = useCallback(
    () =>
      editorType === 'tinymce'
        ? (isVi ? EDITOR_SEED_HTML.vi : EDITOR_SEED_HTML.en)
        : EDITOR_SEED_MARKDOWN,
    [editorType, isVi],
  );
  const unsaved = useUnsavedChangesGuard({
    isDirty: useCallback(
      () => title.trim() !== '' || content !== defaultBodyFor(),
      [title, content, defaultBodyFor],
    ),
    enabled: !isPublishingNotice,
  });

  // Sortable Content Blocks Builder State. Block titles/descriptions come from
  // the `editor.blocks` messages, HTML bodies from lib/editor-templates.ts.
  const [showBlockBuilder, setShowBlockBuilder] = useState(true);
  const defaultBlocks = useMemo(
    () => buildDefaultBlocks(messages.editor.blocks),
    [messages.editor.blocks],
  );
  const [blocks, setBlocks] = useState<ContentBlock[]>(defaultBlocks);
  const [hasUnsavedNoticeOrder, setHasUnsavedNoticeOrder] = useState(false);
  // F-2: the order save runs a site-appearance PUT plus one PUT per dragged
  // row; without an in-flight lock a double click re-runs the whole chain.
  const [isSavingNoticeOrder, setIsSavingNoticeOrder] = useState(false);
  // The pinned-notices list is a `tbody` SortableList, which cannot carry its own
  // live region, so the page keeps the sentence here and renders it below.
  const [noticeMove, setNoticeMove] = useState('');

  // Site Appearance (Hero / Banner control)
  const [siteAppearance, setSiteAppearance] = useState<SiteAppearance>(DEFAULT_SITE_APPEARANCE);
  const [heroEyebrow, setHeroEyebrow] = useState('ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH');
  const [heroTitle, setHeroTitle] = useState('Cổng Thông Tin Đào Tạo & Học Vụ Trực Tuyến');
  const [heroDescription, setHeroDescription] = useState('Hệ thống quản lý học vụ số tập trung dành cho Sinh viên, Giảng viên và Cán bộ Quản trị.');
  const [heroAccent, setHeroAccent] = useState<SiteAppearanceAccent>('ute-yellow');
  const [isSavingHero, setIsSavingHero] = useState(false);
  // The publish button must stay disabled until a real fetch hydrates the
  // fields — otherwise a failed load would push the built-in defaults over
  // the live site configuration.
  const [appearanceReady, setAppearanceReady] = useState(false);
  const [appearanceLoadFailed, setAppearanceLoadFailed] = useState(false);

  // Copy text definitions
  const copy = useMemo(
    () =>
      isVi
        ? {
            eyebrow: 'TRUNG TÂM SOẠN THẢO VĂN BẢN & QUẢN TRỊ NỘI DUNG',
            title: 'Trình Soạn Thảo & Quản Trị Nội Dung Học Vụ',
            description:
              'Bộ công cụ dành riêng cho Ban Quản trị: Soạn thảo văn bản học vụ điện tử, phát hành thông báo đào tạo và điều hành cổng thông tin trường.',
            tabs: {
              announcement: 'Soạn thảo & Phát hành Thông báo',
              hero: 'Điều khiển Banners & Trang chủ',
              templates: 'Thư viện Mẫu Văn bản Chuẩn',
            },
            docTitleLabel: 'Tiêu đề tài liệu / Thông báo',
            docTitlePlaceholder: 'Nhập tiêu đề thông báo học vụ...',
            categoryLabel: 'Thể loại',
            priorityLabel: 'Mức độ ưu tiên',
            targetRoleLabel: 'Đối tượng nhận tin',
            categories: {
              notice: 'Thông báo học vụ',
              syllabus: 'Đề cương môn học',
              thesis: 'Đề tài tốt nghiệp & Hội đồng',
              notes: 'Quyết định / Quy chế đào tạo',
            },
            editorEngine: 'Chế độ soạn thảo',
            tinymceMode: 'Soạn thảo trực quan',
            markdownMode: 'Soạn thảo ký hiệu / Markdown',
            newDoc: 'Tạo mới',
            saveDraft: 'Lưu nháp',
            copyContent: 'Sao chép nội dung',
            downloadDoc: 'Tải tệp về máy',
            publishAnnouncement: 'Đăng lên Bảng tin Học vụ',
            copiedToast: 'Đã sao chép nội dung vào bộ nhớ tạm',
            savedToast: 'Đã lưu bản nháp vào trình duyệt',
            draftSaveFailed: 'Không thể lưu bản nháp (bộ nhớ trình duyệt đầy). Hãy xuất file trước khi tiếp tục.',
            copyFailed: 'Không thể sao chép vào bộ nhớ tạm',
            newDocConfirm: 'Bạn có chắc chắn muốn làm mới toàn bộ nội dung tài liệu?',
            savedAt: 'Lưu gần nhất',
            statsTitle: 'Thống kê tài liệu',
            words: 'Từ',
            characters: 'Ký tự',
            readingTime: 'Thời gian đọc ước tính',
            minutes: 'phút',
            loading: 'Đang tải trung tâm điều khiển...',
          }
        : {
            eyebrow: 'ADMIN SITE CONTROL & ACADEMIC EDITOR',
            title: 'Academic Document Studio & Content Management',
            description:
              'Admin publishing workstation: Compose official academic documents, publish live announcements, and configure portal appearance.',
            tabs: {
              announcement: 'Compose & Publish Notices',
              hero: 'Hero Banner & Site Appearance',
              templates: 'Official Templates Library',
            },
            docTitleLabel: 'Document / Announcement Title',
            docTitlePlaceholder: 'Enter official academic announcement title...',
            categoryLabel: 'Category',
            priorityLabel: 'Priority',
            targetRoleLabel: 'Target Audience',
            categories: {
              notice: 'Academic Notice',
              syllabus: 'Course Syllabus',
              thesis: 'Thesis & Defense Council',
              notes: 'Regulations & Policies',
            },
            editorEngine: 'Editor Mode',
            tinymceMode: 'Rich Visual Editor',
            markdownMode: 'Markdown Editor',
            newDoc: 'New Document',
            saveDraft: 'Save Draft',
            copyContent: 'Copy Content',
            downloadDoc: 'Export File',
            publishAnnouncement: 'Publish to Campus Feed',
            copiedToast: 'Content copied to clipboard',
            savedToast: 'Draft saved to browser storage',
            draftSaveFailed: 'Could not save the draft (browser storage is full). Export the file before continuing.',
            copyFailed: 'Could not copy to clipboard',
            newDocConfirm: 'Reset and create a new document?',
            savedAt: 'Last saved',
            statsTitle: 'Document Statistics',
            words: 'Words',
            characters: 'Characters',
            readingTime: 'Estimated reading time',
            minutes: 'min',
            loading: 'Loading control studio...',
          },
    [isVi],
  );

  // Initialize from storage or default exactly once. The locale must never
  // reseed this editor afterwards: an in-progress edit (a loaded announcement)
  // or a hydrated draft would be wiped, and a subsequent save would overwrite
  // a live record with the blank template.
  const seedLocaleRef = useRef(isVi);
  const editingIdRef = useRef(editingId);
  useEffect(() => {
    editingIdRef.current = editingId;
  }, [editingId]);

  useEffect(() => {
    try {
      const savedEngine = localStorage.getItem(EDITOR_TYPE_KEY) as 'tinymce' | 'markdown' | null;
      if (savedEngine === 'tinymce' || savedEngine === 'markdown') {
        setEditorType(savedEngine);
      }

      const parsed = parseStoredEditorDocument(localStorage.getItem(STORAGE_KEY));
      if (parsed) {
        setTitle(parsed.title);
        setCategory(parsed.category);
        setContent(parsed.content);
        setLastSaved(parsed.updatedAt || null);
        if (parsed.editorType) {
          setEditorType(parsed.editorType);
        }
        // K1: a draft saved while a saved announcement was open has to restore
        // that edit context too. Without it a reload left editingId null and
        // "Publish" silently created a duplicate of the record being updated;
        // the amber editing banner keys off this state as well.
        if (parsed.editingId) {
          setEditingId(parsed.editingId);
          setEditingVersion(parsed.editingVersion ?? 0);
          // Legacy edit drafts did not store these fields. Omit unknown
          // metadata on PATCH instead of silently widening the live notice.
          setPriorityKnown(Boolean(parsed.priority));
          setAudienceKnown(Boolean(parsed.targetRole));
        }
        if (parsed.priority) setPriority(parsed.priority);
        if (parsed.targetRole) setTargetRole(parsed.targetRole);
        return;
      }
    } catch {
      // Storage can be unavailable (private mode); fall through to seeding.
    }

    if (!shouldSeedDefaultEditorDocument({ hasStoredDraft: false, editingId: editingIdRef.current })) {
      return;
    }
    // The seed is frozen at mount locale (seedLocaleRef) so a later locale
    // switch never reseeds over an in-progress edit or a hydrated draft.
    const seedMessages = getMessages(seedLocaleRef.current ? 'vi' : 'en');
    setTitle(
      seedLocaleRef.current ? seedMessages.editor.seedTitleVi : seedMessages.editor.seedTitleEn,
    );
    setContent(seedLocaleRef.current ? EDITOR_SEED_HTML.vi : EDITOR_SEED_HTML.en);
  }, []);

  // Load site appearance for Hero control tab
  useEffect(() => {
    let cancelled = false;
    setAppearanceReady(false);
    setAppearanceLoadFailed(false);
    fetchSiteAppearance()
      .then((data) => {
        if (cancelled) return;
        setSiteAppearance(data);
        const heroData = data.hero[locale] || data.hero.vi;
        if (heroData.eyebrow) setHeroEyebrow(heroData.eyebrow);
        if (heroData.title) setHeroTitle(heroData.title);
        if (heroData.description) setHeroDescription(heroData.description);
        if (data.accent) setHeroAccent(data.accent);
        setAppearanceReady(true);
      })
      .catch(() => {
        if (!cancelled) {
          setAppearanceReady(false);
          setAppearanceLoadFailed(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [locale]);

  const handleSwitchEditorType = (type: 'tinymce' | 'markdown') => {
    setEditorType(type);
    try {
      localStorage.setItem(EDITOR_TYPE_KEY, type);
    } catch {
      // ignore
    }
  };

  const saveDraft = useCallback(() => {
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    const payload: StoredEditorDocument = {
      title,
      category,
      content,
      editorType,
      updatedAt: timestamp,
      // K1: the draft carries the edit context, so hydrating it can resume the
      // update of a saved announcement instead of losing that association.
      editingId,
      editingVersion,
      priority: priorityKnown ? priority : undefined,
      targetRole: audienceKnown ? targetRole : undefined,
    };
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
      setLastSaved(timestamp);
      toast.success(copy.savedToast);
    } catch {
      // Quota errors (large inline images) used to fail with zero feedback,
      // so the author believed a draft existed that did not.
      toast.error(copy.draftSaveFailed);
    }
  }, [audienceKnown, category, content, copy.draftSaveFailed, copy.savedToast, editingId, editingVersion, editorType, priority, priorityKnown, targetRole, title]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(content);
      setCopied(true);
      toast.success(copy.copiedToast);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      toast.error(copy.copyFailed);
    }
  }, [content, copy.copiedToast, copy.copyFailed]);

  const handleDownload = useCallback(() => {
    const isHtml = editorType === 'tinymce';
    const ext = isHtml ? 'html' : 'md';
    const mime = isHtml ? 'text/html;charset=utf-8' : 'text/markdown;charset=utf-8';
    // K2: Unicode-aware slug shared with the TinyMCE shell export, so both
    // download buttons produce the same name for the same title.
    const filename = buildDocumentFilename(title, ext);

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
    // RT-P3-3: routed through the unsaved-changes guard so the confirm text
    // and the beforeunload protection come from one mechanism.
    unsaved.requestLeave(() => {
      // K1: "New Document" is the create-new flow, so it must also leave the
      // edit context — otherwise Publish would overwrite the record that was
      // open before the reset.
      setEditingId(null);
      setEditingVersion(0);
      setPriorityKnown(true);
      setAudienceKnown(true);
      setTitle('');
      setContent(defaultBodyFor());
      setLastSaved(null);
      try {
        localStorage.removeItem(STORAGE_KEY);
      } catch {
        // ignore
      }
      // K6: a discarded document must not be resurrected from TinyMCE's own
      // autosave draft.
      clearTinyMceAutosaveDrafts(studioEditorRef.current?.id);
    });
  }, [defaultBodyFor, unsaved]);

  // Reorder content blocks via SortableJS
  const handleReorderBlocks = (newBlocks: ContentBlock[]) => {
    setBlocks(newBlocks);
  };

  // Toggle single block inclusion
  const handleToggleBlock = (id: string) => {
    setBlocks((prev) =>
      prev.map((b) => (b.id === id ? { ...b, enabled: !b.enabled } : b))
    );
  };

  // Compile all enabled blocks into TinyMCE
  const handleCompileBlocksToTinyMce = () => {
    const active = blocks.filter((b) => b.enabled);
    if (active.length === 0) {
      toast.error(isVi ? 'Vui lòng chọn ít nhất một khối để biên dịch!' : 'Please enable at least one block!');
      return;
    }
    const compiled = `<div style="font-family: Arial, sans-serif; line-height: 1.6; color: #1e293b;">\n${active.map((b) => b.htmlContent).join('\n\n')}\n</div>`;
    // Compiling replaces the whole draft (title untouched but the body is
    // rewritten), so it goes through the same unsaved-changes guard as New
    // Document and Cancel — a one-click replace must not bypass the prompt.
    unsaved.requestLeave(() => {
      setContent(compiled);
      toast.success(
        isVi
          ? `Đã áp dụng ${active.length} khối cấu trúc vào văn bản đang soạn!`
          : `Applied ${active.length} structured blocks into document!`
      );
    });
  };

  // Insert a single block into TinyMCE
  const handleInsertSingleBlock = (block: ContentBlock) => {
    setContent((prev) => `${prev}\n\n${block.htmlContent}`);
    toast.success(
      isVi ? `Đã chèn khối "${block.title}" vào nội dung!` : `Inserted "${block.title}" into editor!`
    );
  };

  // Reset blocks to default order
  const handleResetBlocks = () => {
    setBlocks(defaultBlocks);
    toast.info(isVi ? 'Đã khôi phục các khối cấu trúc mẫu mặc định.' : 'Reset blocks to default.');
  };

  // Active cover image detection from content
  const activeCoverUrl =
    EDIT_BANNER_PRESETS.find((b) => content.includes(b.url))?.url ||
    content.match(/src=["'](\/images\/(?:banners|news)\/[^"']+)["']/i)?.[1] ||
    null;

  const handleApplyBanner = (bannerUrl: string, bannerTitle: string) => {
    const figureMarkup = `<figure class="my-3 text-center">\n  <img src="${bannerUrl}" alt="${bannerTitle}" class="w-full rounded-lg object-cover max-h-72 shadow-xs" />\n  <figcaption class="mt-1.5 text-xs text-muted-foreground italic">${bannerTitle}</figcaption>\n</figure>\n\n`;

    // Splice only the block that owns the active cover URL — a document may
    // hold ordinary figures (inline screenshots) before the banner.
    const replaced = activeCoverUrl
      ? replaceCoverBlock(content, activeCoverUrl, figureMarkup)
      : null;
    setContent(replaced ?? figureMarkup + content);
    toast.success(
      replaced !== null
        ? (isVi ? `Đã đổi ảnh bìa sang "${bannerTitle}"!` : `Changed cover banner to "${bannerTitle}"!`)
        : (isVi ? `Đã thêm ảnh bìa "${bannerTitle}"!` : `Added cover banner "${bannerTitle}"!`),
    );
  };

  const handleRemoveBanner = () => {
    if (!activeCoverUrl) return;
    const removed = removeCoverBlock(content, activeCoverUrl);
    if (removed !== null) {
      setContent(removed);
    }
    toast.info(isVi ? 'Đã gỡ ảnh bìa học thuật khỏi văn bản.' : 'Removed cover banner from document.');
  };

  // Reorder announcements via SortableJS
  const handleSortableNoticeReorder = (newNotices: AnnouncementRecord[]) => {
    setPublishedNotices(newNotices);
    setHasUnsavedNoticeOrder(true);
  };

  // Save announcements order to site appearance & Spring Boot backend
  const handleSaveNoticeOrder = async () => {
    // F-2: the header button is rendered with animate-pulse while the order is
    // unsaved, which invites the double click that used to re-run the whole
    // PUT chain. One run at a time, and both triggers reflect it.
    if (isSavingNoticeOrder) return;
    setIsSavingNoticeOrder(true);
    try {
      // Merge onto the freshest remote appearance so a stale mount-time copy
      // (or another admin's concurrent edit) is not clobbered by this PUT.
      const fresh = await fetchSiteAppearance();
      const newOrder = publishedNotices.map((n) => n.id);
      const updated: SiteAppearance = {
        ...fresh,
        // Only the loaded notices are reordered here; applying the order to that
        // subset keeps pins set for announcements outside this batch.
        postOrder: applyPageOrder(fresh.postOrder, newOrder),
      };
      const saved = await saveSiteAppearance(updated);
      broadcastSiteAppearance(updated);
      setSiteAppearance(saved);

      // This list is page 1 of fifteen published notices, so writing 0..14 would
      // re-seat every announcement outside the window as if it were the global
      // order. The dragged rows therefore swap the ranks they already occupy.
      // `displayOrder` is nullable by design (V70): a rank-less notice sorts
      // after the pinned ones, so it stays out of the swap instead of being
      // handed a number — and never an out-of-range one, since the column is a
      // 32-bit INTEGER.
      const ranked = publishedNotices.filter((n) => typeof n.displayOrder === 'number');
      const rankless = publishedNotices.length - ranked.length;
      const slots = ranked
        .map((n) => n.displayOrder as number)
        .sort((left, right) => left - right);
      const settled = await Promise.allSettled(
        ranked.map((n, index) => announcementsApi.updateDisplayOrder(n.id, slots[index])),
      );
      const failed = settled.filter((outcome) => outcome.status === 'rejected').length;
      if (failed > 0) {
        // The pins are saved but the server order is not, so reload from the
        // server rather than leave a success notice over a half-applied order.
        void fetchPublishedNotices();
        toast.error(
          isVi
            ? `Đã lưu danh sách ghim, nhưng ${failed} / ${ranked.length} bài không cập nhật được thứ tự trên máy chủ. Danh sách đã được tải lại.`
            : `Pins saved, but ${failed} of ${ranked.length} announcements could not be updated on the server. The list has been reloaded.`,
        );
        return;
      }

      setHasUnsavedNoticeOrder(false);
      if (ranked.length === 0) {
        // Nothing here carried a rank, so no server write could express the drag
        // at all; the pins are saved but the feed order is unchanged.
        toast.warning(
          isVi
            ? 'Đã lưu danh sách ghim, nhưng chưa bài nào có thứ tự trên máy chủ nên thứ tự hiển thị chưa đổi.'
            : 'Pins saved, but no notice carries a server rank yet, so the displayed order could not change.',
        );
      } else if (rankless > 0) {
        toast.info(
          isVi
            ? `Đã đổi thứ tự ${ranked.length} bài đã có thứ tự; ${rankless} bài chưa xếp hạng vẫn đứng sau nhóm ghim.`
            : `Reordered the ${ranked.length} ranked notices; ${rankless} unranked notice(s) stay after the pinned group.`,
        );
      }
      if (saved.persisted === false) {
        toast.warning(
          isVi
            ? 'Thứ tự đã lưu trên web server này và sẽ mất ở lần deploy kế tiếp; API trung tâm đang không kết nối được.'
            : 'Saved on this web server only; the central API is unreachable, so this order is lost on the next deploy.',
        );
      } else {
        toast.success(
          isVi
            ? 'Đã lưu và đồng bộ thứ tự ghim bài viết lên Bảng tin toàn trường!'
            : 'Saved and broadcast announcement feed order!'
        );
      }
    } catch {
      toast.error(
        isVi
          ? 'Không thể lưu thứ tự ghim bài viết.'
          : 'Could not save announcement order.'
      );
      void fetchPublishedNotices();
    } finally {
      setIsSavingNoticeOrder(false);
    }
  };

  // Fetch published announcements from campus database
  const fetchPublishedNotices = useCallback(async () => {
    setLoadingNotices(true);
    try {
      const res = await announcementsApi.getAll({ page: 1, limit: 15 });
      const raw = res.data || [];
      const ordered = siteAppearance.postOrder?.length > 0 ? orderByIds(raw, siteAppearance.postOrder) : raw;
      setPublishedNotices(ordered);
      setHasUnsavedNoticeOrder(false);
      setNoticesLoadError(false);
    } catch {
      setNoticesLoadError(true);
    } finally {
      setLoadingNotices(false);
    }
  }, [siteAppearance.postOrder]);

  useEffect(() => {
    if (hasAccess) {
      void fetchPublishedNotices();
    }
  }, [fetchPublishedNotices, hasAccess]);

  // Save modal edit changes directly
  const handleSaveModalEdit = async (id: string, payload: any) => {
    // The modal edits content through a plain textarea, making it a third authoring
    // surface. Gating only the publish and update buttons left this path able to
    // send an oversized body and receive the same opaque 400.
    const modalOverflow = findAnnouncementLengthViolation(String(payload?.content ?? ''));
    if (modalOverflow) {
      toast.error(announcementLengthViolationMessage(modalOverflow, locale));
      return;
    }
    const updated = await announcementsApi.update(id, payload);
    toast.success(isVi ? 'Đã lưu thay đổi thông báo thành công!' : 'Notice updated successfully!');
    setPublishedNotices((prev) =>
      prev.map((n) => (n.id === id ? { ...n, ...updated, ...payload } : n))
    );
  };

  // Load existing announcement into TinyMCE editor
  const handleLoadAnnouncement = useCallback((ann: AnnouncementRecord) => {
    setEditingId(ann.id);
    setEditingVersion(ann.version ?? 0);
    setPriorityKnown(true);
    setAudienceKnown(true);
    setTitle(ann.title);
    setContent(ann.content);
    if (ann.priority === 'URGENT' || ann.priority === 'HIGH' || ann.priority === 'NORMAL' || ann.priority === 'LOW') {
      setPriority(ann.priority as any);
    }
    if (ann.isGlobal || !ann.targetRoles || ann.targetRoles.length === 0) {
      setTargetRole('ALL');
    } else if (
      ann.targetRoles.includes('STUDENT') &&
      ann.targetRoles.includes('LECTURER')
    ) {
      // Targeted at both roles without being campus-wide: keep that state
      // distinct so saving the studio edit does not silently globalize it.
      setTargetRole('BOTH');
    } else if (ann.targetRoles.includes('STUDENT')) {
      setTargetRole('STUDENT');
    } else if (ann.targetRoles.includes('LECTURER')) {
      setTargetRole('LECTURER');
    } else {
      setTargetRole('ALL');
    }
    setEditorType('tinymce');
    setActiveTab('announcement');
    setTimeout(() => {
      const workspaceEl = document.getElementById('editor-workspace');
      if (workspaceEl) {
        workspaceEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 100);
    toast.success(
      isVi ? `Đang chỉnh sửa bài viết: "${ann.title.slice(0, 32)}..."` : `Editing notice: "${ann.title.slice(0, 32)}..."`
    );
  }, [isVi]);

  // Auto load announcement from query param ?editId=... or ?id=...
  // Runs once per mount: the effect keyed on isVi re-fired on every language
  // toggle and refetched the record over an in-progress edit, resetting the
  // draft to server values with no unsaved-changes prompt.
  // The guard arms only when a fetch RESOLVES: arming before the request
  // dead-locked the deep link under React StrictMode's mount→cleanup→remount
  // (run 1 armed and was cancelled, run 2 saw the armed ref and returned).
  const deepLinkLoadedRef = useRef(false);
  useEffect(() => {
    if (typeof window === 'undefined') return;
    if (deepLinkLoadedRef.current) return;
    const params = new URLSearchParams(window.location.search);
    const targetId = params.get('editId') || params.get('id');
    if (!targetId) return;

    let cancelled = false;
    const markLoaded = () => {
      deepLinkLoadedRef.current = true;
    };
    announcementsApi
      .getAll({ page: 1, limit: 50 })
      .then((res) => {
        if (cancelled) return;
        markLoaded();
        const found = (res.data || []).find((a) => a.id === targetId);
        if (found) {
          setDeepLinkNotice(null);
          handleLoadAnnouncement(found);
        } else {
          // The editor would otherwise show its blank seed document; the
          // author must know the requested draft was not loaded.
          setDeepLinkNotice({ targetId, reason: 'not-found' });
          toast.error(
            isVi
              ? `Không tìm thấy thông báo cần sửa (mã ${targetId}). Trình soạn thảo đang hiển thị bản nháp mới.`
              : `The notice to edit (id ${targetId}) was not found. The editor is showing a new blank draft.`,
          );
        }
      })
      .catch(() => {
        if (!cancelled) {
          markLoaded();
          setDeepLinkNotice({ targetId, reason: 'load-failed' });
          toast.error(
            isVi
              ? 'Không thể tải thông báo cần sửa. Trình soạn thảo đang hiển thị bản nháp mới.'
              : 'The notice to edit could not be loaded. The editor is showing a new blank draft.',
          );
        }
      });
    return () => {
      cancelled = true;
    };
  }, [handleLoadAnnouncement, isVi]);

  // Preview current draft in Official Administrative modal
  const handlePreviewCurrentDraft = () => {
    const authorName = user
      ? (`${user.lastName || ''} ${user.firstName || ''}`.trim() || `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.email)
      : 'Phòng Đào Tạo & Ban Giám Hiệu';
    setPreviewingNotice({
      id: editingId || 'draft-preview',
      title: title || (isVi ? 'THÔNG BÁO HỌC VỤ & HƯỚNG DẪN ĐÀO TẠO' : 'Official Academic Notice'),
      content: content,
      priority: priority,
      createdAt: new Date().toISOString(),
      publishAt: new Date().toISOString(),
      publishedBy: authorName,
      ...audienceFor(targetRole),
    });
  };

  // Cancel edit mode and start new
  const handleCancelEdit = () => {
    // RT-P3-3: the announcement being edited is lost on cancel, so the same
    // unsaved-changes guard protects it.
    unsaved.requestLeave(() => {
      setEditingId(null);
      setEditingVersion(0);
      setPriorityKnown(true);
      setAudienceKnown(true);
      setTitle('');
      setContent(defaultBodyFor());
      try {
        // K1: the stored draft holds the edit context; cancelling the edit has
        // to drop it as well, or the next reload restores the edit the author
        // just left. K6: also drop the TinyMCE autosave draft.
        localStorage.removeItem(STORAGE_KEY);
      } catch {
        // ignore
      }
      clearTinyMceAutosaveDrafts(studioEditorRef.current?.id);
      toast.info(isVi ? 'Đã hủy chế độ sửa, bắt đầu soạn thảo văn bản mới' : 'Cancelled edit mode');
    });
  };

  // Publish announcement to backend feed
  const handlePublishAnnouncement = async () => {
    // K1: when a saved announcement is open in the editor, "Publish" means
    // "save this document". It must update the live record instead of pushing a
    // duplicate of it; the create-new flow is reached by cancelling the edit or
    // starting a new document, both of which clear editingId.
    if (editingId) {
      await handleUpdateAnnouncement();
      return;
    }
    if (!title.trim()) {
      toast.error(isVi ? 'Vui lòng nhập tiêu đề thông báo!' : 'Please enter announcement title!');
      return;
    }
    // Gate before the request. Letting an oversized body reach the server returns an
    // opaque 400 and leaves the author with no idea what to change, which is the
    // dead-end the content limits exist to prevent.
    const publishOverflow = findAnnouncementLengthViolation(content);
    if (publishOverflow) {
      toast.error(announcementLengthViolationMessage(publishOverflow, locale));
      return;
    }

    setIsPublishingNotice(true);
    try {
      await announcementsApi.create({
        title,
        content,
        priority,
        targetYears: [],
        ...audienceFor(targetRole),
      });

      toast.success(
        isVi
          ? 'Đã phát hành thông báo thành công lên toàn trường!'
          : 'Announcement successfully published to campus feed!',
      );
      void fetchPublishedNotices();
    } catch (err) {
      toast.error(
        isVi
          ? 'Có lỗi khi phát hành thông báo. Vui lòng kiểm tra lại.'
          : 'Could not publish announcement.',
      );
    } finally {
      setIsPublishingNotice(false);
    }
  };

  // Update existing announcement in backend
  const handleUpdateAnnouncement = async () => {
    if (!editingId || !title.trim()) {
      toast.error(isVi ? 'Vui lòng nhập tiêu đề bài viết!' : 'Please enter title!');
      return;
    }
    // Same gate as publish: an update that exceeds the cap fails identically.
    const updateOverflow = findAnnouncementLengthViolation(content);
    if (updateOverflow) {
      toast.error(announcementLengthViolationMessage(updateOverflow, locale));
      return;
    }

    setIsPublishingNotice(true);
    try {
      await announcementsApi.update(editingId, {
        title,
        content,
        ...(priorityKnown ? { priority } : {}),
        ...(audienceKnown ? audienceFor(targetRole) : {}),
        reason: isVi ? 'Cập nhật nội dung văn bản học vụ' : 'Update academic notice content',
        expectedVersion: editingVersion,
      });

      setEditingVersion((prev) => prev + 1);
      toast.success(
        isVi ? 'Đã cập nhật bài viết thành công vào cơ sở dữ liệu!' : 'Announcement updated in database!'
      );
      void fetchPublishedNotices();
    } catch {
      toast.error(isVi ? 'Không thể cập nhật thông báo.' : 'Failed to update announcement.');
    } finally {
      setIsPublishingNotice(false);
    }
  };

  // Publish Hero banner changes to site appearance
  const handlePublishHero = async () => {
    setIsSavingHero(true);
    try {
      const updated: SiteAppearance = {
        ...siteAppearance,
        accent: heroAccent,
        hero: {
          ...siteAppearance.hero,
          [locale]: {
            eyebrow: heroEyebrow,
            title: heroTitle,
            description: heroDescription,
          },
        },
      };

      const saved = await saveSiteAppearance(updated);
      broadcastSiteAppearance(updated);
      setSiteAppearance(saved);
      if (saved.persisted === false) {
        // The hero block lives on the API row; this instance only is not
        // "published live", and saying so is the difference between an admin
        // leaving the page believing the banner changed everywhere and it
        // disappearing on the next deploy.
        toast.warning(
          isVi
            ? 'Hero đã lưu trên web server này; API trung tâm đang không kết nối được nên có thể mất ở lần deploy kế tiếp.'
            : 'Hero saved on this web server only; the central API is unreachable, so it may be lost on the next deploy.',
        );
      } else {
        toast.success(
          isVi
            ? 'Đã cập nhật và xuất bản trực tiếp lên Trang chủ!'
            : 'Homepage appearance updated and published live!',
        );
      }
    } catch {
      toast.error(
        isVi
          ? 'Không thể lưu cấu hình trang chủ.'
          : 'Failed to update homepage appearance.',
      );
    } finally {
      setIsSavingHero(false);
    }
  };

  // Load selected template into editor
  const applyTemplate = (templateContent: string, templateTitle: string) => {
    // Loading a template replaces the entire draft — behind the same
    // unsaved-changes guard as every other destructive action on this page,
    // so an author with a long in-progress document gets the confirm dialog.
    unsaved.requestLeave(() => {
      setContent(templateContent);
      setTitle(templateTitle);
      setActiveTab('announcement');
      toast.success(isVi ? 'Đã nạp mẫu văn bản vào trình soạn thảo!' : 'Template applied to editor!');
    });
  };

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

  // Strict Admin Gate: Only Admins can access!
  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const deepLinkCopy = deepLinkNotice
    ? deepLinkNotice.reason === 'not-found'
      ? {
          vi: `Không tìm thấy thông báo cần sửa (mã ${deepLinkNotice.targetId}). Trình soạn thảo đang hiển thị bản nháp mới.`,
          en: `The notice to edit (id ${deepLinkNotice.targetId}) was not found. The editor is showing a new blank draft.`,
        }
      : {
          vi: `Không thể tải thông báo cần sửa (mã ${deepLinkNotice.targetId}). Trình soạn thảo đang hiển thị bản nháp mới.`,
          en: `The notice to edit (id ${deepLinkNotice.targetId}) could not be loaded. The editor is showing a new blank draft.`,
        }
    : null;

  return (
    <div className="space-y-6 pb-12">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {deepLinkCopy ? (
        <div
          role="alert"
          aria-live="polite"
          className="flex items-start justify-between gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive"
        >
          <span>{isVi ? deepLinkCopy.vi : deepLinkCopy.en}</span>
          <button
            type="button"
            onClick={() => setDeepLinkNotice(null)}
            aria-label={isVi ? 'Đóng cảnh báo liên kết' : 'Dismiss deep-link warning'}
            className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-sm text-destructive transition-colors hover:bg-destructive/10"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
      ) : null}

      {/* Main Tab Controller */}
      <div className="flex flex-wrap items-center gap-2 border-b border-border/70 pb-3">
        <Button
          type="button"
          variant={activeTab === 'announcement' ? 'default' : 'outline'}
          onClick={() => setActiveTab('announcement')}
          className="gap-2 font-semibold"
        >
          <FileEdit className="h-4 w-4" />
          {copy.tabs.announcement}
        </Button>
        <Button
          type="button"
          variant={activeTab === 'hero' ? 'default' : 'outline'}
          onClick={() => setActiveTab('hero')}
          className="gap-2 font-semibold"
        >
          <Sliders className="h-4 w-4" />
          {copy.tabs.hero}
        </Button>
        <Button
          type="button"
          variant={activeTab === 'templates' ? 'default' : 'outline'}
          onClick={() => setActiveTab('templates')}
          className="gap-2 font-semibold"
        >
          <BookOpen className="h-4 w-4" />
          {copy.tabs.templates}
        </Button>
      </div>

      {/* TAB 1: HERO & SITE APPEARANCE CONTROL */}
      {activeTab === 'hero' && (
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base">
                <Sliders className="h-5 w-5 text-primary" />
                {editorCopy.heroCardTitle}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {editorCopy.heroEyebrowLabel}
                </label>
                <Input
                  value={heroEyebrow}
                  onChange={(e) => setHeroEyebrow(e.target.value)}
                  placeholder="ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH"
                  className="font-bold text-primary"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {editorCopy.heroTitleLabel}
                </label>
                <Input
                  value={heroTitle}
                  onChange={(e) => setHeroTitle(e.target.value)}
                  placeholder="Cổng Thông Tin Đào Tạo & Học Vụ Trực Tuyến"
                  className="font-semibold"
                />
              </div>

              <div>
                <label className="mb-1 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {editorCopy.heroDescriptionLabel}
                </label>
                <Textarea
                  value={heroDescription}
                  onChange={(e) => setHeroDescription(e.target.value)}
                  rows={3}
                  className="leading-relaxed"
                />
              </div>

              <div>
                <label className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  {editorCopy.heroAccentLabel}
                </label>
                <div className="flex flex-wrap gap-3">
                  {SITE_APPEARANCE_ACCENTS.map((accent) => (
                    <button
                      key={accent}
                      type="button"
                      onClick={() => setHeroAccent(accent)}
                      className={`flex items-center gap-2 rounded-lg border px-3 py-2 text-xs font-semibold transition-all ${
                        heroAccent === accent
                          ? 'border-primary bg-primary/10 text-primary ring-2 ring-primary/20'
                          : 'border-border bg-card text-muted-foreground hover:bg-muted/40'
                      }`}
                    >
                      <Palette className="h-3.5 w-3.5" />
                      <span>{accent}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Live Preview Box */}
              <div className="rounded-xl border border-border/80 bg-muted/30 p-5">
                <div className="text-xs font-bold uppercase tracking-wider text-muted-foreground mb-3">
                  {editorCopy.heroLivePreview}
                </div>
                <div className="border-l-4 border-[var(--portal-chrome-accent)] pl-5 py-2 space-y-2">
                  <SectionEyebrow>{heroEyebrow}</SectionEyebrow>
                  <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
                    {heroTitle}
                  </h1>
                  <p className="text-sm text-foreground/80 max-w-xl">
                    {heroDescription}
                  </p>
                </div>
              </div>

              <div className="flex items-center justify-end gap-3 pt-2">
                {!appearanceReady && (
                  <p className="text-xs text-muted-foreground">
                    {appearanceLoadFailed
                      ? isVi
                        ? 'Chưa tải được cấu hình hiện tại — nạp lại trang để xuất bản.'
                        : 'Current appearance could not be loaded — reload the page to publish.'
                      : isVi
                        ? 'Đang tải cấu hình hiện tại…'
                        : 'Loading current appearance…'}
                  </p>
                )}
                <Button
                  type="button"
                  onClick={handlePublishHero}
                  disabled={isSavingHero || !appearanceReady}
                  className="gap-2 bg-primary text-primary-foreground font-semibold px-6 shadow-sm"
                >
                  <Save className="h-4 w-4" />
                  {isSavingHero ? editorCopy.heroPublishing : editorCopy.heroPublish}
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* TAB 2: ANNOUNCEMENT & DOCUMENT WYSIWYG EDITOR */}
      {activeTab === 'announcement' && (
        <div id="editor-workspace" className="space-y-5">
          <Card>
            <CardContent className="p-4 sm:p-5">
              {editingId && (
                <div className="mb-4 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-2.5 text-xs text-amber-800 dark:text-amber-200">
                  <div className="flex items-center gap-2 font-medium">
                    <Sparkles className="h-4 w-4 text-amber-600 dark:text-amber-400 shrink-0" />
                    <span>
                      {editorCopy.editingBanner.replace('{title}', title || editingId)}
                    </span>
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={handleCancelEdit}
                    className="h-7 text-xs font-semibold text-amber-700 hover:bg-amber-500/20 dark:text-amber-300"
                  >
                    {editorCopy.cancelEdit}
                  </Button>
                </div>
              )}

              {/* Row 1: Document Metadata Configuration (Full-Width Responsive Grid) */}
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-12 items-end">
                <div className="sm:col-span-2 lg:col-span-6">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.docTitleLabel}
                  </label>
                  <Input
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder={copy.docTitlePlaceholder}
                    className="font-medium h-10 w-full"
                  />
                </div>
                <div className="sm:col-span-1 lg:col-span-3">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.priorityLabel}
                  </label>
                  <Select
                    aria-label={copy.priorityLabel}
                    value={editingId && !priorityKnown ? '' : priority}
                    onChange={(e) => {
                      setPriority(e.target.value as (typeof PRIORITY_VALUES)[number]);
                      setPriorityKnown(true);
                    }}
                    options={[...(editingId && !priorityKnown ? [{ value: '', label: isVi ? 'Giữ nguyên mức ưu tiên đã lưu' : 'Keep saved priority' }] : []), ...PRIORITY_VALUES.map((value) => ({
                      value,
                      label: editorCopy.priorityOptions[value],
                    }))]}
                    className="h-10"
                  />
                </div>
                <div className="sm:col-span-1 lg:col-span-3">
                  <label className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.12em] text-muted-foreground whitespace-nowrap">
                    {copy.targetRoleLabel}
                  </label>
                  <Select
                    aria-label={copy.targetRoleLabel}
                    value={editingId && !audienceKnown ? '' : targetRole}
                    onChange={(e) => {
                      setTargetRole(e.target.value as (typeof TARGET_ROLE_VALUES)[number]);
                      setAudienceKnown(true);
                    }}
                    options={[...(editingId && !audienceKnown ? [{ value: '', label: isVi ? 'Giữ nguyên đối tượng đã lưu' : 'Keep saved audience' }] : []), ...TARGET_ROLE_VALUES.map((value) => ({
                      value,
                      label: editorCopy.targetRoleOptions[value],
                    }))]}
                    className="h-10"
                  />
                </div>
              </div>

              {/* Row 2: Dedicated Action Toolbar */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/60 pt-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handlePreviewCurrentDraft}
                    className="gap-1.5 border-primary/40 text-primary hover:bg-primary/10"
                    title={editorCopy.previewActionTitle}
                  >
                    <Eye className="h-4 w-4" />
                    {editorCopy.previewAction}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={handleCopy}
                    className="gap-1.5"
                  >
                    {copied ? <Check className="h-4 w-4 text-status-success-foreground" /> : <Copy className="h-4 w-4" />}
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
                    {editorType === 'tinymce' ? editorCopy.exportHtml : editorCopy.exportMarkdown}
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
                </div>

                <div className="flex flex-wrap items-center gap-2">
                  {editingId ? (
                    <Button
                      type="button"
                      onClick={handleUpdateAnnouncement}
                      disabled={isPublishingNotice}
                      size="sm"
                      className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-xs"
                      title={editorCopy.saveChangesTitle}
                    >
                      <Save className="h-4 w-4" />
                      {isPublishingNotice ? editorCopy.saving : editorCopy.saveChanges}
                    </Button>
                  ) : null}
                  <Button
                    type="button"
                    onClick={handlePublishAnnouncement}
                    disabled={isPublishingNotice}
                    size="sm"
                    className="gap-1.5 bg-emerald-600 hover:bg-emerald-700 dark:bg-emerald-700 dark:hover:bg-emerald-800 text-white font-semibold shadow-xs"
                    title={editingId ? editorCopy.publishChangesTitle : undefined}
                  >
                    <Send className="h-4 w-4" />
                    {isPublishingNotice
                      ? editorCopy.publishing
                      : editingId
                      ? editorCopy.publishChanges
                      : copy.publishAnnouncement}
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

              {/* Engine Selector & Statistics Bar */}
              <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/70 pt-3 text-xs text-muted-foreground">
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

          {/* Sortable Content Blocks Builder Toggle & Workspace */}
          <Card className="border-border/80 shadow-xs">
            <CardHeader className="py-3 px-4 border-b border-border/60 bg-muted/20">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <Layers className="h-4 w-4" />
                  </div>
                  <div>
                    <CardTitle className="text-sm font-bold text-foreground flex items-center gap-2">
                      <span>{editorCopy.blockBuilderTitle}</span>
                      <span className="rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                        {editorCopy.blockBuilderBadge}
                      </span>
                    </CardTitle>
                    <p className="text-[11.5px] text-muted-foreground">
                      {editorCopy.blockBuilderHint}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant={showBlockBuilder ? 'default' : 'outline'}
                    size="sm"
                    onClick={() => setShowBlockBuilder(!showBlockBuilder)}
                    className="h-8 gap-1.5 text-xs font-semibold"
                  >
                    <ListOrdered className="h-3.5 w-3.5" />
                    <span>
                      {showBlockBuilder ? editorCopy.collapseBlocks : editorCopy.openBlocks}
                    </span>
                    {showBlockBuilder ? (
                      <ChevronUp className="h-3.5 w-3.5 ml-0.5" />
                    ) : (
                      <ChevronDown className="h-3.5 w-3.5 ml-0.5" />
                    )}
                  </Button>
                </div>
              </div>
            </CardHeader>
            {showBlockBuilder && (
              <CardContent className="p-4 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/60 pb-3">
                  <div className="text-xs text-muted-foreground flex items-center gap-1.5">
                    <Sparkles className="h-3.5 w-3.5 text-amber-500 shrink-0" />
                    <span>
                      {editorCopy.blockDragHint}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      onClick={handleResetBlocks}
                      className="h-7 text-xs text-muted-foreground hover:text-foreground"
                    >
                      <RotateCcw className="h-3.5 w-3.5 mr-1" />
                      {editorCopy.resetBlocks}
                    </Button>
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      onClick={handleCompileBlocksToTinyMce}
                      className="h-7 gap-1 bg-emerald-600 hover:bg-emerald-700 dark:bg-emerald-700 dark:hover:bg-emerald-800 text-white text-xs font-semibold shadow-xs"
                    >
                      <Sparkles className="h-3.5 w-3.5" />
                      {editorCopy.insertAllBlocks}
                    </Button>
                  </div>
                </div>

                <SortableList<ContentBlock>
                  tag="div"
                  itemTag="div"
                  items={blocks}
                  keyExtractor={(b) => b.id}
                  onOrderChange={handleReorderBlocks}
                  className="space-y-2.5"
                  renderItem={(block, index) => (
                    <div
                      className={cn(
                        'flex items-center justify-between gap-3 rounded-lg border p-3 transition-colors',
                        block.enabled
                          ? 'border-border bg-card shadow-xs'
                          : 'border-dashed border-border/60 bg-muted/30 opacity-60'
                      )}
                    >
                      <div className="flex items-center gap-3 min-w-0">
                        <DragHandle
                          className="cursor-grab hover:text-primary active:cursor-grabbing"
                          label={editorCopy.dragHandleLabel}
                          title={editorCopy.dragHandleLabel}
                        />
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-[11px] font-bold text-primary">
                          {String(index + 1).padStart(2, '0')}
                        </span>
                        <input
                          type="checkbox"
                          checked={block.enabled}
                          onChange={() => handleToggleBlock(block.id)}
                          className="h-4 w-4 rounded border-input text-primary focus:ring-primary cursor-pointer"
                          title={editorCopy.toggleBlockTitle}
                        />
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-foreground truncate">
                            {block.title}
                          </p>
                          <p className="text-[11px] text-muted-foreground truncate">
                            {block.description}
                          </p>
                        </div>
                      </div>

                      <div className="flex shrink-0 items-center gap-1.5">
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          onClick={() => handleInsertSingleBlock(block)}
                          className="h-6 px-2 text-[11px] font-medium"
                          title={editorCopy.appendBlockTitle}
                        >
                          <Plus className="h-3 w-3 mr-0.5" />
                          {editorCopy.appendBlock}
                        </Button>
                      </div>
                    </div>
                  )}
                />
              </CardContent>
            )}
          </Card>

          {/* Editorial Cover Banner Gallery (Stitch Screen a3c5abce08fb4069a3b2b1d7dd7aaea4) */}
          <Card className="border-border/80 shadow-xs">
            <CardHeader className="py-2.5 px-4 border-b border-border/60 bg-muted/20">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary/10 text-primary">
                    <ImageIcon className="h-4 w-4" />
                  </div>
                  <div>
                    <CardTitle className="text-xs font-bold text-foreground flex items-center gap-2">
                      <span>{editorCopy.coverGalleryTitle}</span>
                      <span className="rounded-md bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                        8K Nano Banana
                      </span>
                    </CardTitle>
                    <p className="text-[11px] text-muted-foreground">
                      {editorCopy.coverGalleryHint}
                    </p>
                  </div>
                </div>
                {activeCoverUrl ? (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    onClick={handleRemoveBanner}
                    className="h-7 text-xs font-medium text-destructive hover:bg-destructive/10"
                  >
                    <X className="h-3.5 w-3.5 mr-1" />
                    {editorCopy.removeBanner}
                  </Button>
                ) : null}
              </div>
            </CardHeader>
            <CardContent className="p-3">
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-8">
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
                      <div className="relative h-14 w-full overflow-hidden bg-muted">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={banner.url}
                          alt={bannerTitle}
                          className="h-full w-full object-cover object-center transition duration-300 group-hover:scale-105"
                        />
                        {isSelected ? (
                          <div className="absolute right-1 top-1 rounded-full bg-primary p-0.5 text-primary-foreground shadow-xs">
                            <Check className="h-3 w-3" />
                          </div>
                        ) : null}
                      </div>
                      <div className="p-1.5 bg-card">
                        <p className="text-[11px] font-semibold text-foreground truncate" title={bannerTitle}>
                          {bannerTitle}
                        </p>
                        <span className="text-[11px] text-muted-foreground">{bannerTag}</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          {/* Editor Workspace */}
          <div className="rounded-xl border border-border/80 bg-card p-1 shadow-sm sm:p-2">
            {editorType === 'tinymce' ? (
              <TinyMceEditor
                value={content}
                onChange={setContent}
                onInit={(_event, editor) => { studioEditorRef.current = editor; }}
                height={580}
                locale={isVi ? 'vi' : 'en'}
                showTemplates={true}
                documentTitle={title}
              />
            ) : (
              <RichTextEditor
                value={content}
                onChange={setContent}
                minHeight="540px"
                locale={isVi ? 'vi' : 'en'}
                showTemplates={true}
              />
            )}
          </div>

          {/* Section: Live Announcements Database & One-Click Load into TinyMCE */}
          <Card className="mt-6 border-border/80 shadow-xs">
            <CardHeader className="border-b border-border/60 pb-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <CardTitle className="flex items-center gap-2 text-base font-bold text-foreground">
                    <Megaphone className="h-4 w-4 text-primary" />
                    {isVi
                      ? 'Kho Bài Viết & Thông Báo Đang Lưu Trong Cơ Sở Dữ Liệu'
                      : 'Live Announcements in Campus Records'}
                    <span className="rounded-md bg-blue-500/10 px-2 py-0.5 text-[11px] font-semibold text-blue-600 dark:text-blue-300 border border-blue-500/20">
                      {isVi ? 'Kéo thả thứ tự' : 'Custom Order'}
                    </span>
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {isVi
                      ? 'Kéo thả các dòng bài viết ⠿ để thiết lập thứ tự ghim ưu tiên trên Bảng tin toàn trường. Bấm nút "Chỉnh sửa" để mở văn bản trong trình soạn thảo.'
                      : 'Drag and drop rows ⠿ to set priority feed display order. Click "Edit" to open in document editor.'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  {hasUnsavedNoticeOrder && (
                    <Button
                      type="button"
                      variant="default"
                      size="sm"
                      onClick={handleSaveNoticeOrder}
                      disabled={isSavingNoticeOrder}
                      className="h-8 gap-1.5 text-xs font-semibold bg-blue-600 hover:bg-blue-700 text-white shadow-xs animate-pulse disabled:animate-none"
                    >
                      {isSavingNoticeOrder ? (
                        <RefreshCw className="h-3.5 w-3.5 animate-spin" />
                      ) : (
                        <Save className="h-3.5 w-3.5" />
                      )}
                      <span>
                        {isSavingNoticeOrder
                          ? editorCopy.saving
                          : isVi
                          ? 'Lưu thứ tự ghim bài viết'
                          : 'Save Feed Order'}
                      </span>
                    </Button>
                  )}
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={fetchPublishedNotices}
                    disabled={loadingNotices}
                    className="h-8 gap-1.5 text-xs"
                  >
                    <RefreshCw className={cn('h-3.5 w-3.5', loadingNotices && 'animate-spin')} />
                    <span>{isVi ? 'Làm mới' : 'Refresh'}</span>
                  </Button>
                </div>
              </div>
            </CardHeader>

            {hasUnsavedNoticeOrder && (
              <div className="bg-blue-500/10 border-b border-blue-500/20 px-4 py-2.5 flex items-center justify-between gap-3 text-xs text-blue-700 dark:text-blue-300">
                <div className="flex items-center gap-2">
                  <ArrowUpDown className="h-4 w-4 shrink-0 text-status-info-foreground" />
                  <span>
                    {isVi
                      ? 'Bạn vừa kéo thả sắp xếp lại thứ tự bài viết. Bấm nút "Lưu thứ tự ghim bài viết" để đồng bộ ngay lập tức lên Bảng tin sinh viên & giảng viên.'
                      : 'You changed the order of announcements. Click "Save Feed Order" to broadcast.'}
                  </span>
                </div>
                <Button
                  type="button"
                  size="sm"
                  onClick={handleSaveNoticeOrder}
                  disabled={isSavingNoticeOrder}
                  className="h-7 px-3 text-xs bg-blue-600 hover:bg-blue-700 text-white font-semibold shrink-0 shadow-xs"
                >
                  <Save className="h-3.5 w-3.5 mr-1" />
                  {isSavingNoticeOrder ? editorCopy.saving : isVi ? 'Lưu ngay' : 'Save Now'}
                </Button>
              </div>
            )}

            <CardContent className="p-0">
              {loadingNotices ? (
                <div className="py-8 text-center text-xs text-muted-foreground">
                  <div className="flex items-center justify-center gap-2">
                    <RefreshCw className="h-4 w-4 animate-spin text-primary" />
                    <span>{isVi ? 'Đang tải danh sách bài viết từ CSDL...' : 'Loading announcements...'}</span>
                  </div>
                </div>
              ) : noticesLoadError ? (
                <div className="py-8 text-center text-xs text-destructive">
                  <p>{isVi ? 'Không tải được danh sách thông báo từ máy chủ.' : 'Could not load announcements from the server.'}</p>
                  <button
                    type="button"
                    className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-secondary"
                    onClick={() => void fetchPublishedNotices()}
                  >
                    {isVi ? 'Thử lại' : 'Retry'}
                  </button>
                </div>
              ) : publishedNotices.length === 0 ? (
                <div className="py-8 text-center text-xs text-muted-foreground">
                  {isVi ? 'Chưa có thông báo nào trong cơ sở dữ liệu.' : 'No announcements in database.'}
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <p aria-live="polite" role="status" className="sr-only">
                    {noticeMove}
                  </p>
                  <table className="w-full min-w-[920px] text-left text-xs border-collapse">
                    <thead className="border-b border-border/60 bg-muted/40 text-muted-foreground font-semibold">
                      <tr>
                        <th className="py-2.5 px-3 w-10 text-center">#</th>
                        <th className="py-2.5 px-3 w-14 text-center">{isVi ? 'Ghim' : 'Pin'}</th>
                        <th className="py-2.5 px-4 min-w-[200px]">{isVi ? 'Tiêu đề văn bản' : 'Title'}</th>
                        <th className="py-2.5 px-3 w-24 whitespace-nowrap">{isVi ? 'Ưu tiên' : 'Priority'}</th>
                        <th className="py-2.5 px-3 w-28 whitespace-nowrap">{isVi ? 'Đối tượng' : 'Audience'}</th>
                        <th className="py-2.5 px-3 max-w-[160px] truncate">{isVi ? 'Người đăng' : 'Publisher'}</th>
                        <th className="py-2.5 px-3 w-24 whitespace-nowrap">{isVi ? 'Ngày ban hành' : 'Date'}</th>
                        <th className="sticky right-0 z-20 bg-muted/95 backdrop-blur-xs py-2.5 px-4 min-w-[190px] text-right shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.08)]">{isVi ? 'Thao tác' : 'Actions'}</th>
                      </tr>
                    </thead>
                    <SortableList<AnnouncementRecord>
                      tag="tbody"
                      itemTag="tr"
                      items={publishedNotices}
                      keyExtractor={(ann) => ann.id}
                      onOrderChange={handleSortableNoticeReorder}
                      announceMove={({ item, from, to, total }) => {
                        const message = isVi
                          ? `Đã chuyển "${item.title}" từ vị trí ${from + 1} sang ${to + 1} trong ${total} bài.`
                          : `Moved "${item.title}" from position ${from + 1} to ${to + 1} of ${total}.`;
                        setNoticeMove(message);
                        return message;
                      }}
                      itemClassName="hover:bg-muted/30 transition-colors border-b border-border/50"
                      renderItem={(ann, index) => (
                        <>
                          <td className="py-3 px-3 text-center">
                            <DragHandle
                              className="mx-auto cursor-grab hover:text-primary active:cursor-grabbing"
                              label={isVi ? 'Kéo thả để sắp xếp thứ tự hiển thị' : 'Drag to reorder notice'}
                              title={isVi ? 'Kéo thả để sắp xếp thứ tự hiển thị' : 'Drag to reorder notice'}
                            />
                          </td>
                          <td className="py-3 px-3 text-center whitespace-nowrap">
                            <span
                              className={cn(
                                'inline-flex items-center justify-center rounded px-1.5 py-0.5 font-bold text-[11px]',
                                index === 0
                                  ? 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border border-amber-500/30'
                                  : 'bg-muted text-muted-foreground'
                              )}
                            >
                              {index === 0 ? (isVi ? 'Top 1' : 'Top 1') : `#${index + 1}`}
                            </span>
                          </td>
                          <td className="py-3 px-4 min-w-[200px] max-w-sm">
                            <div className="font-semibold text-foreground line-clamp-2">
                              {ann.title}
                            </div>
                            <div className="text-[11px] text-muted-foreground truncate mt-0.5">
                              {ann.isGlobal
                                ? (isVi ? 'Phạm vi: Toàn trường' : 'Scope: Campus-wide')
                                : (isVi ? 'Phạm vi: Phân quyền đối tượng' : 'Scope: Targeted audience')}
                            </div>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            <span
                              className={cn(
                                'inline-flex items-center rounded-md px-2 py-0.5 text-[11px] font-semibold',
                                ann.priority === 'URGENT'
                                  ? 'bg-red-500/10 text-red-600 dark:text-red-300 border border-red-500/30'
                                  : ann.priority === 'HIGH'
                                  ? 'bg-amber-500/10 text-amber-600 dark:text-amber-300 border border-amber-500/30'
                                  : 'bg-blue-500/10 text-blue-600 dark:text-blue-300 border border-blue-500/30'
                              )}
                            >
                              {announcementPriorityLabel(ann.priority, locale)}
                            </span>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap">
                            <span className="inline-flex items-center rounded bg-secondary/80 px-2 py-0.5 text-[11px] font-medium text-foreground">
                              {ann.isGlobal
                                ? (isVi ? 'Toàn trường' : 'All')
                                : ann.targetRoles?.includes('STUDENT')
                                ? (isVi ? 'Sinh viên' : 'Students')
                                : (isVi ? 'Giảng viên' : 'Lecturers')}
                            </span>
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-[11.5px] max-w-[160px] truncate" title={ann.publishedBy || 'Phòng Đào tạo'}>
                            {ann.publishedBy || 'Phòng Đào tạo'}
                          </td>
                          <td className="py-3 px-3 whitespace-nowrap text-muted-foreground text-[11.5px]">
                            {ann.createdAt ? formatDate(ann.createdAt) : '—'}
                          </td>
                          <td className="sticky right-0 z-10 bg-card/95 backdrop-blur-xs py-3 px-4 min-w-[190px] text-right whitespace-nowrap shadow-[-4px_0_6px_-2px_rgba(0,0,0,0.08)]">
                            <div className="flex items-center justify-end gap-2">
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => setPreviewingNotice(ann)}
                                className="h-7 px-2.5 text-[11.5px] gap-1 hover:bg-primary/10 hover:text-primary"
                                title={isVi ? 'Xem trước công văn chuẩn e-Office' : 'Preview document'}
                              >
                                <Eye className="h-3.5 w-3.5 text-primary" />
                                <span>{isVi ? 'Xem trước' : 'Preview'}</span>
                              </Button>
                              <Button
                                type="button"
                                variant="default"
                                size="sm"
                                onClick={() => setEditingNoticeModal(ann)}
                                className="h-7 px-2.5 text-[11.5px] gap-1 bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-xs"
                                title={isVi ? 'Chỉnh sửa nhanh bài viết này' : 'Edit announcement'}
                              >
                                <FileEdit className="h-3.5 w-3.5" />
                                <span>{isVi ? 'Chỉnh sửa' : 'Edit'}</span>
                              </Button>
                            </div>
                          </td>
                        </>
                      )}
                    />
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* TAB 3: TEMPLATES REPOSITORY. Names/descriptions from `editor.templates`
          messages; HTML bodies from lib/editor-templates.ts. */}
      {activeTab === 'templates' && (
        <div className="grid gap-5 md:grid-cols-2">
          {EDITOR_TEMPLATE_IDS.map((templateId) => {
            const templateCopy = editorCopy.templates[templateId];
            return (
              <Card key={templateId} className="flex flex-col justify-between hover:border-primary/50 transition-colors">
                <CardHeader>
                  <CardTitle className="text-base font-bold text-slate-800 dark:text-slate-100 flex items-center gap-2">
                    <BookOpen className="h-4 w-4 text-primary" />
                    {templateCopy.name}
                  </CardTitle>
                  <p className="text-xs text-muted-foreground mt-1">
                    {templateCopy.description}
                  </p>
                </CardHeader>
                <CardContent className="pt-2 flex justify-end gap-2 border-t border-border/50">
                  <Button
                    type="button"
                    variant="default"
                    size="sm"
                    onClick={() => applyTemplate(EDITOR_TEMPLATE_HTML[templateId], templateCopy.name)}
                    className="gap-1.5 text-xs font-semibold"
                  >
                    <Send className="h-3.5 w-3.5" />
                    {editorCopy.loadIntoEditor}
                  </Button>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}

      {/* Official Administrative Reader Modal */}
      {previewingNotice && (
        <AnnouncementReaderModal
          announcement={previewingNotice}
          isOpen={Boolean(previewingNotice)}
          onClose={() => setPreviewingNotice(null)}
          onEdit={(notice) => {
            setPreviewingNotice(null);
            setEditingNoticeModal(notice);
          }}
        />
      )}

      {/* In-Place Administrative Edit Modal */}
      {editingNoticeModal && (
        <AnnouncementEditModal
          announcement={editingNoticeModal}
          isOpen={Boolean(editingNoticeModal)}
          onClose={() => {
            setEditingNoticeModal(null);
          }}
          onSave={handleSaveModalEdit}
          onOpenStudio={(notice) => {
            setEditingNoticeModal(null);
            handleLoadAnnouncement(notice);
          }}
        />
      )}

      {/* RT-P3-3: unsaved-changes confirm for destructive in-page actions */}
      <UnsavedChangesConfirmDialog
        open={unsaved.confirmOpen}
        title={editorCopy.unsavedTitle}
        description={editorCopy.unsavedDescription}
        confirmLabel={editorCopy.unsavedConfirm}
        cancelLabel={editorCopy.unsavedKeep}
        onConfirm={unsaved.confirmLeave}
        onCancel={unsaved.cancelLeave}
      />
    </div>
  );
}
