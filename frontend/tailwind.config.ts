/** @type {import('tailwindcss').Config} */

// Portal chrome tokens are authored as complete `oklch()` colours referenced
// through `var()`. A plain `var()` string cannot take a `/N` alpha modifier —
// Tailwind only emits the utility when the value carries an `<alpha-value>`
// placeholder (or the colour is channel-decomposed). Wrapping in color-mix
// keeps `bg-portal-*/15` working; without a modifier the placeholder becomes
// 1, i.e. the full colour.
const portalVar = (token: string) =>
  `color-mix(in oklab, var(${token}) calc(<alpha-value> * 100%), transparent)`;

module.exports = {
  darkMode: ["class"],
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
    // Feature views (src/features/**) render dashboard surfaces, so their
    // classes must be scanned; without this line Tailwind purges every utility
    // that is not also spelled out in src/app or src/components.
    "./src/features/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    container: {
      center: true,
      padding: "2rem",
      screens: {
        "2xl": "1400px",
      },
    },
    extend: {
      colors: {
        border: "hsl(var(--border))",
        input: "hsl(var(--input))",
        ring: "hsl(var(--ring))",
        background: "hsl(var(--background))",
        foreground: "hsl(var(--foreground))",
        primary: {
          DEFAULT: "hsl(var(--primary))",
          foreground: "hsl(var(--primary-foreground))",
        },
        secondary: {
          DEFAULT: "hsl(var(--secondary))",
          foreground: "hsl(var(--secondary-foreground))",
        },
        destructive: {
          DEFAULT: "hsl(var(--destructive))",
          foreground: "hsl(var(--destructive-foreground))",
        },
        muted: {
          DEFAULT: "hsl(var(--muted))",
          foreground: "hsl(var(--muted-foreground))",
        },
        accent: {
          DEFAULT: "hsl(var(--accent))",
          foreground: "hsl(var(--accent-foreground))",
        },
        popover: {
          DEFAULT: "hsl(var(--popover))",
          foreground: "hsl(var(--popover-foreground))",
        },
        card: {
          DEFAULT: "hsl(var(--card))",
          foreground: "hsl(var(--card-foreground))",
        },
        status: {
          success: {
            DEFAULT: "hsl(var(--status-success))",
            foreground: "hsl(var(--status-success-foreground))",
          },
          warning: {
            DEFAULT: "hsl(var(--status-warning))",
            foreground: "hsl(var(--status-warning-foreground))",
          },
          danger: {
            DEFAULT: "hsl(var(--status-danger))",
            foreground: "hsl(var(--status-danger-foreground))",
          },
          info: {
            DEFAULT: "hsl(var(--status-info))",
            foreground: "hsl(var(--status-info-foreground))",
          },
          neutral: {
            DEFAULT: "hsl(var(--status-neutral))",
            foreground: "hsl(var(--status-neutral-foreground))",
          },
        },
        role: {
          student: {
            DEFAULT: "hsl(var(--role-student))",
            solid: "hsl(var(--role-student-solid))",
          },
          lecturer: {
            DEFAULT: "hsl(var(--role-lecturer))",
            solid: "hsl(var(--role-lecturer-solid))",
          },
          admin: {
            DEFAULT: "hsl(var(--role-admin))",
            solid: "hsl(var(--role-admin-solid))",
          },
          "super-admin": {
            DEFAULT: "hsl(var(--role-super-admin))",
            solid: "hsl(var(--role-super-admin-solid))",
          },
        },
        // The portal chrome tokens are authored as complete `oklch()` colours,
        // not HSL triplets, so they are referenced through `portalVar`, which
        // preserves `/N` alpha modifiers via color-mix. Exposing them as
        // first-class utilities removes the need for `bg-[var(--portal-x)]`
        // arbitrary values, where a mistyped token name fails silently as a
        // transparent background instead of failing the build.
        portal: {
          sidebar: {
            DEFAULT: portalVar("--portal-sidebar"),
            strong: portalVar("--portal-sidebar-strong"),
            hover: portalVar("--portal-sidebar-hover"),
            text: portalVar("--portal-sidebar-text"),
            muted: portalVar("--portal-sidebar-muted"),
          },
          canvas: portalVar("--portal-canvas"),
          surface: portalVar("--portal-surface"),
          rule: portalVar("--portal-rule"),
          ribbon: portalVar("--portal-ribbon"),
          scrim: portalVar("--portal-scrim"),
          // Foreground/hairline accent drawn ON the navy chrome. Theme-stable,
          // unlike `portal.yellow` which is a fill that inverts between themes.
          "chrome-accent": portalVar("--portal-chrome-accent"),
          yellow: {
            DEFAULT: portalVar("--portal-yellow"),
            ink: portalVar("--portal-yellow-ink"),
          },
          "brand-gold": {
            DEFAULT: portalVar("--portal-brand-gold"),
            ink: portalVar("--portal-brand-gold-ink"),
          },
        },
      },
      borderRadius: {
        // Tailwind v4-name aliases used across the codebase: v3 has no xs step,
        // so `rounded-xs` silently resolved to nothing until added here.
        xs: "0.125rem",
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
        // The large end of the default scale is capped to an enterprise range:
        // 12/16/24px radii read as consumer chat bubbles on dense data surfaces.
        // Cards and modals land at 8-10px, accents at 12px maximum.
        xl: "0.5rem",
        "2xl": "0.625rem",
        "3xl": "0.75rem",
      },
      boxShadow: {
        // v4-name aliases (shadow-xs / shadow-2xs appear on 56 surfaces).
        "2xs": "0 1px rgb(0 0 0 / 0.05)",
        xs: "0 1px 2px 0 rgb(0 0 0 / 0.05)",
      },
      dropShadow: {
        xs: "0 1px 1px rgb(0 0 0 / 0.05)",
      },
      keyframes: {
        "accordion-down": {
          from: { height: "0" },
          to: { height: "var(--radix-accordion-content-height)" },
        },
        "accordion-up": {
          from: { height: "var(--radix-accordion-content-height)" },
          to: { height: "0" },
        },
      },
      animation: {
        "accordion-down": "accordion-down 0.2s ease-out",
        "accordion-up": "accordion-up 0.2s ease-out",
      },
    },
  },
  plugins: [require("tailwindcss-animate")],
}
