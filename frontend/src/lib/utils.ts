import { type ClassValue, clsx } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

/**
 * Notifications carry authored HTML (the same bodies the reader renders).
 * Plain-text surfaces strip markup — figures included — collapse whitespace,
 * and clamp. Punctuation is kept: bodies carry dates ('20-12-2026') and term
 * codes ('2026-2027') that must not lose their hyphens.
 */
export function htmlToPlainText(content: string | undefined, maxLength = 160): string {
  if (!content) {
    return '';
  }
  const text = content
    .replace(/<figure[\s\S]*?<\/figure>/gi, ' ')
    .replace(/<[^>]*>/g, ' ')
    .replace(/<[a-zA-Z/!][^>]*$/g, ' ')
    .replace(/<\/[a-zA-Z]+(?![a-zA-Z>])/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (text.length <= maxLength) {
    return text;
  }
  return `${text.slice(0, maxLength).trim()}…`;
}

/**
 * Announcement fan-out tags titles with a transport prefix ('[Announcement] ')
 * that reads as an internal label. Plain-text surfaces drop it; the row's icon
 * and link already carry the category.
 */
export function stripNotificationTagPrefix(title: string | undefined, maxLength = 200): string {
  return htmlToPlainText(title, maxLength).replace(/^\[(announcement|thông báo)\]\s*/i, '');
}
