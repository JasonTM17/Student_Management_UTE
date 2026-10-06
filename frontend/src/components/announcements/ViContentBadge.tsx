import { cn } from '@/lib/utils';

/**
 * Honest-locale marker: announcements are authored in Vietnamese only, so on
 * the English surface each VI-authored card gets an explicit `VI` pill rather
 * than letting untranslated copy read as a rendering bug. `lang="vi"` keeps
 * the semantics correct for assistive tech and translation tools.
 */
export function ViContentBadge({
  show,
  className,
}: {
  show: boolean;
  className?: string;
}) {
  if (!show) return null;
  return (
    <span
      lang="vi"
      title="Bài viết bằng tiếng Việt / Article in Vietnamese"
      className={cn(
        'inline-flex shrink-0 items-center rounded border border-amber-500/40 bg-amber-500/10 px-1.5 py-px text-[11px] font-bold uppercase tracking-wide text-amber-700 dark:text-amber-400',
        className,
      )}
    >
      VI
    </span>
  );
}
