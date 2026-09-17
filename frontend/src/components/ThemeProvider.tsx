'use client';

import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import {
  applyThemeClass,
  nextTheme,
  resolveStoredTheme,
  systemPreferredTheme,
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
    // Falls back to the OS preference when no stored choice exists so the
    // React state matches what the pre-hydration bootstrap script applied.
    const initialTheme = resolveStoredTheme(
      localStorage.getItem('theme'),
      systemPreferredTheme(),
    );
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

    applyThemeClass(theme, document.documentElement);
    // Persist only an explicit choice; visitors without one keep following
    // their OS preference on later visits.
    if (userToggled.current) {
      try {
        localStorage.setItem('theme', theme);
      } catch {
        // Private mode can block storage; the class still flips this click.
      }
    }
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
