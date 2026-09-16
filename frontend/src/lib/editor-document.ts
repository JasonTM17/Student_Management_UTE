/**
 * Control-studio editor seeding policy.
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
