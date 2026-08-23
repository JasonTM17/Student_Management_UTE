import { Platform } from 'react-native';

export const tokens = {
  colors: {
    background: '#F4F6F8',
    surfaceLow: '#EEF1F5',
    surface: '#E7ECF2',
    surfaceHigh: '#DDE4EC',
    card: '#FFFFFF',
    text: '#17243A',
    textMuted: '#526174',
    primary: '#0B4A86',
    primaryContainer: '#0D5AA2',
    primaryFixed: '#E7EFF8',
    secondary: '#4A5A6D',
    secondaryContainer: '#E8EDF3',
    outline: '#66758A',
    outlineVariant: '#CDD5DF',
    accent: '#E5A900',
    error: '#B42318',
    success: '#247A4A',
    warning: '#946200',
    onPrimary: '#FFFFFF',
  },
  spacing: {
    unit: 4,
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
  },
  radii: {
    control: 6,
    card: 6,
    pill: 999,
  },
  layout: {
    mobileGutter: 16,
    bottomNavigationHeight: 68,
    touchTarget: 48,
  },
  typography: {
    family: Platform.select({ ios: 'BeVietnamPro_400Regular', android: 'BeVietnamPro_400Regular', default: 'BeVietnamPro_400Regular' }),
    fallback: 'System',
    display: { fontSize: 34, lineHeight: 40, fontWeight: '700' as const },
    headlineLarge: { fontSize: 26, lineHeight: 32, fontWeight: '700' as const },
    headlineMedium: { fontSize: 22, lineHeight: 28, fontWeight: '700' as const },
    headlineSmall: { fontSize: 18, lineHeight: 24, fontWeight: '600' as const },
    bodyLarge: { fontSize: 17, lineHeight: 26, fontWeight: '400' as const },
    bodyMedium: { fontSize: 16, lineHeight: 24, fontWeight: '400' as const },
    bodySmall: { fontSize: 14, lineHeight: 20, fontWeight: '400' as const },
    label: { fontSize: 13, lineHeight: 18, fontWeight: '600' as const },
    meta: { fontSize: 12, lineHeight: 16, fontWeight: '600' as const },
  },
} as const;

export type DesignTokens = typeof tokens;
