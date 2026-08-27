export type ThemeName = 'light' | 'dark';

export function resolveStoredTheme(stored: string | null | undefined): ThemeName {
  return stored === 'dark' || stored === 'light' ? stored : 'light';
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
