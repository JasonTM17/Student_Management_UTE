export type ThemeName = 'light' | 'dark';

/** Theme to use when the visitor has never stored a choice (SSR-safe). */
export const DEFAULT_THEME: ThemeName = 'light';

/**
 * The visitor's OS-level preference. Safe to call during SSR/render: returns
 * the light default when `matchMedia` is unavailable.
 */
export function systemPreferredTheme(): ThemeName {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return DEFAULT_THEME;
  }
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
}

export function resolveStoredTheme(
  stored: string | null | undefined,
  fallback: ThemeName = DEFAULT_THEME,
): ThemeName {
  return stored === 'dark' || stored === 'light' ? stored : fallback;
}

export function nextTheme(current: ThemeName): ThemeName {
  return current === 'light' ? 'dark' : 'light';
}

/**
 * Colors the browser paints around the page (address bar, status bar).
 * Keep in sync with the `themeColor` viewport export and the pre-hydration
 * bootstrap script in `app/layout.tsx`.
 */
export const THEME_COLOR_BY_THEME: Record<ThemeName, string> = {
  light: '#F9F9FF',
  dark: '#12161d',
};

/**
 * The document element, structurally typed so the helper can be unit-tested
 * with a bare `{ classList, dataset }` stub that has no ownerDocument.
 */
type ThemeRoot = {
  classList: { toggle: (token: string, force?: boolean) => unknown };
  dataset: { theme?: string };
  ownerDocument?: Document | null;
};

/**
 * A theme flip that only moved the `dark` class used to leave
 * `<meta name="theme-color">` on the previous theme until the next reload, so
 * the browser chrome disagreed with the page. The bootstrap script already
 * fixes this on load; this keeps the same tags correct on the toggle itself.
 */
function syncThemeColorMeta(theme: ThemeName, root: ThemeRoot): void {
  const doc =
    root.ownerDocument ??
    (typeof document === 'undefined' ? null : document);
  if (!doc || typeof doc.getElementsByName !== 'function') {
    return;
  }
  const metas = doc.getElementsByName('theme-color');
  const color = THEME_COLOR_BY_THEME[theme];
  for (let index = 0; index < metas.length; index += 1) {
    metas[index].setAttribute('content', color);
  }
}

export function applyThemeClass(theme: ThemeName, root: ThemeRoot): void {
  root.classList.toggle('dark', theme === 'dark');
  root.dataset.theme = theme;
  syncThemeColorMeta(theme, root);
}
