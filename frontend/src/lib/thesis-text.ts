/**
 * Topic descriptions are authored in the rich-text editor (HTML). Compact
 * previews (catalog cards, council workspace, reviewer panel, round page)
 * need plain text — raw markup renders as literal `<p>`/`<strong>` noise
 * under a line-clamp.
 */
export function plainTextThesisDescription(raw: string | null | undefined): string {
  if (!raw) return '';
  return raw
    .replace(/<[^>]*>/g, ' ')
    .replace(/&nbsp;/gi, ' ')
    .replace(/&amp;/gi, '&')
    .replace(/&lt;/gi, '<')
    .replace(/&gt;/gi, '>')
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/\s+/g, ' ')
    .trim();
}
