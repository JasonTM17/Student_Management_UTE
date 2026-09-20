'use client';

import React from 'react';
import { AntdRegistry } from '@ant-design/nextjs-registry';
import { ConfigProvider, theme as antdTheme } from 'antd';
import { useTheme } from '@/components/ThemeProvider';

export function AntdProvider({ children }: { children: React.ReactNode }) {
  const { theme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <AntdRegistry>
      <ConfigProvider
        theme={{
          algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
          token: {
            colorPrimary: '#003f87',
            colorInfo: '#0056b3',
            colorSuccess: '#1b873d',
            colorWarning: '#d97706',
            colorError: '#ba1a1a',
            fontFamily: 'var(--font-sans), "Be Vietnam Pro", -apple-system, BlinkMacSystemFont, sans-serif',
            borderRadius: 8,
            wireframe: false,
          },
          components: {
            Button: {
              controlHeight: 40,
              borderRadius: 8,
              fontWeight: 600,
            },
            Card: {
              borderRadiusLG: 12,
            },
            Tag: {
              borderRadiusSM: 6,
            },
            Table: {
              borderRadiusLG: 10,
            },
          },
        }}
      >
        {children}
      </ConfigProvider>
    </AntdRegistry>
  );
}
