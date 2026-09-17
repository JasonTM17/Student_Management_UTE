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

export function applyThemeClass(
  theme: ThemeName,
  root: { classList: { toggle: (token: string, force?: boolean) => unknown }; dataset: { theme?: string } },
): void {
  root.classList.toggle('dark', theme === 'dark');
  root.dataset.theme = theme;
}
