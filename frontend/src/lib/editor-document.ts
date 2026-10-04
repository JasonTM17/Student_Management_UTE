/**
 * Control-studio editor seeding policy plus the draft/export helpers shared by
 * the studio page and the TinyMCE editor shell.
 *
 * The locale must never reseed an editor that already holds a real document:
 * a stored draft or a loaded announcement has to survive language switches so
 * that a subsequent "save" can never overwrite a live record with the blank
 * template. Seeding only happens on a pristine editor with no stored draft
 * and no announcement in edit mode.
 */
export interface EditorSeedInput {
  hasStoredDraft: boolean;
  editingId: string | null;
}

export function shouldSeedDefaultEditorDocument(input: EditorSeedInput): boolean {
  return !input.hasStoredDraft && input.editingId === null;
}

/**
 * A saved browser draft. `editingId`/`editingVersion` are part of the payload
 * because the page is stateless across reloads: without them a reloaded draft
 * lost the edit context and "Publish" silently created a duplicate of the
 * announcement the author meant to update (K1).
 */
export interface StoredEditorDocument {
  title: string;
  category: string;
  content: string;
  editorType?: 'tinymce' | 'markdown';
  updatedAt: string;
  editingId?: string | null;
  editingVersion?: number;
  priority?: 'URGENT' | 'HIGH' | 'NORMAL' | 'LOW';
  targetRole?: 'ALL' | 'BOTH' | 'STUDENT' | 'LECTURER';
}

/**
 * Hydrates a raw localStorage payload. Returns null when there is nothing to
 * restore (missing or malformed), so the caller falls back to seeding exactly
 * as it did before the helper existed. Legacy drafts written before K1 carry
 * no edit context and hydrate as new documents.
 */
export function parseStoredEditorDocument(raw: string | null): StoredEditorDocument | null {
  if (!raw) return null;
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;

  const record = parsed as Record<string, unknown>;
  const editorType =
    record.editorType === 'tinymce' || record.editorType === 'markdown'
      ? record.editorType
      : undefined;
  const editingId =
    typeof record.editingId === 'string' && record.editingId.trim() !== ''
      ? record.editingId
      : null;
  const editingVersion =
    typeof record.editingVersion === 'number' && Number.isFinite(record.editingVersion)
      ? record.editingVersion
      : 0;

  return {
    title: typeof record.title === 'string' ? record.title : '',
    category: typeof record.category === 'string' && record.category ? record.category : 'notice',
    content: typeof record.content === 'string' ? record.content : '',
    editorType,
    updatedAt: typeof record.updatedAt === 'string' ? record.updatedAt : '',
    editingId,
    editingVersion,
    priority: record.priority === 'URGENT' || record.priority === 'HIGH'
      || record.priority === 'NORMAL' || record.priority === 'LOW' ? record.priority : undefined,
    targetRole: record.targetRole === 'ALL' || record.targetRole === 'BOTH'
      || record.targetRole === 'STUDENT' || record.targetRole === 'LECTURER' ? record.targetRole : undefined,
  };
}

/**
 * Unicode-aware filename slug: NFD-decompose, drop combining marks (the
 * U+0300–U+036F block also carries the horn of ơ/ư), map đ/Đ explicitly (they
 * have no decomposition), then collapse every other separator run. The old
 * `[^a-z0-9-_]` filter deleted Vietnamese letters outright and produced names
 * like `-cng--ti-kha-lun-tt-nghip.html` (K2).
 */
export function slugifyDocumentTitle(title: string, fallback = 'academic-document'): string {
  const slug = (title || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[đĐ]/g, (char) => (char === 'đ' ? 'd' : 'D'))
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '');
  return slug || fallback;
}

/**
 * One naming rule for every export path in the editor: `<title-slug>.<ext>`,
 * with an explicit fallback when the title has no usable characters.
 */
export function buildDocumentFilename(
  title: string,
  extension: string,
  fallback = 'academic-document',
): string {
  return `${slugifyDocumentTitle(title, fallback)}.${extension}`;
}
