'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import {
  applyThemeClass,
  nextTheme,
  resolveStoredTheme,
  type ThemeName,
} from '@/lib/apply-theme';

type Theme = ThemeName;

interface ThemeContextType {
  theme: Theme;
  toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [theme, setTheme] = useState<Theme>('light');
  const [mounted, setMounted] = useState(false);
  const userToggled = useRef(false);

  useEffect(() => {
    const initialTheme = resolveStoredTheme(localStorage.getItem('theme'));
    if (!userToggled.current) {
      setTheme(initialTheme);
      applyThemeClass(initialTheme, document.documentElement);
    }
    setMounted(true);
  }, []);

  useEffect(() => {
    if (!mounted) {
      return;
    }

    localStorage.setItem('theme', theme);
    applyThemeClass(theme, document.documentElement);
  }, [mounted, theme]);

  const toggleTheme = useCallback(() => {
    setTheme((currentTheme) => {
      const next = nextTheme(currentTheme);
      userToggled.current = true;
      applyThemeClass(next, document.documentElement);
      try {
        localStorage.setItem('theme', next);
      } catch {
        // Private mode can block storage; the class still flips this click.
      }
      return next;
    });
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
}
