import "./globals.css";
import "@fontsource/be-vietnam-pro/400.css";
import "@fontsource/be-vietnam-pro/500.css";
import "@fontsource/be-vietnam-pro/600.css";
import "@fontsource/be-vietnam-pro/700.css";
import "@fontsource/noto-serif/400.css";
import "@fontsource/noto-serif/600.css";
import "@fontsource/noto-serif/700.css";
import type { Viewport } from "next";
import { AuthProvider } from "@/context/AuthContext";
import { ThemeProvider } from "@/components/ThemeProvider";
import { QueryProvider } from "@/components/providers/query-provider";
import { SiteAppearanceProvider } from "@/components/providers/SiteAppearanceProvider";
import { Toaster } from "sonner";
import { I18nProvider } from "@/i18n";
import { isLocale } from "@/i18n/config";
import {
  getHtmlLang,
  getLocalizedMetadata,
  getRequestLocale,
  isPrefixedRequest,
} from "@/i18n/server";

export const dynamic = 'force-dynamic';

export const viewport: Viewport = {
  themeColor: [
    { media: '(prefers-color-scheme: light)', color: '#F9F9FF' },
    { media: '(prefers-color-scheme: dark)', color: '#12161d' },
  ],
};

export async function generateMetadata() {
  return getLocalizedMetadata();
}

export default async function RootLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params?: Promise<{ locale?: string }>;
}) {
  const resolvedParams = params ? await params : {};
  const routeLocale = isLocale(resolvedParams.locale)
    ? resolvedParams.locale
    : null;
  const [requestLocale, requestHtmlLang, requestPrefixed] = await Promise.all([
    getRequestLocale(),
    getHtmlLang(),
    isPrefixedRequest(),
  ]);
  const locale = routeLocale ?? requestLocale;
  const htmlLang = routeLocale ?? requestHtmlLang;
  const prefixed = routeLocale ? true : requestPrefixed;

  return (
    <html lang={htmlLang} suppressHydrationWarning>
      <head>
        <script
          dangerouslySetInnerHTML={{
            __html:
              "(function(){try{var t=localStorage.getItem('theme');if(t==='dark'){document.documentElement.classList.add('dark');document.documentElement.dataset.theme='dark'}}catch(e){}})();",
          }}
        />
      </head>
      <body className="min-h-screen">
        <ThemeProvider>
          <I18nProvider locale={locale} isPrefixed={prefixed}>
            <AuthProvider>
              <QueryProvider>
                <SiteAppearanceProvider>
                  {children}
                  <Toaster position="top-right" />
                </SiteAppearanceProvider>
              </QueryProvider>
            </AuthProvider>
          </I18nProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
