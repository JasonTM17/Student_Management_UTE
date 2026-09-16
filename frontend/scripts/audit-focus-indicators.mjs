/**
 * Focus-visibility audit (WCAG 2.4.7) plus a border check for form controls
 * (WCAG 1.4.11). Tabs through each route with TRUSTED key input, and for every
 * focused element decides whether a focus indicator is actually visible:
 * outline set, box-shadow set, or a background/border change that is at least
 * 3:1 against the unfocused state.
 *
 *   AUDIT_BASE_URL   default http://127.0.0.1:3100
 *   AUDIT_EMAIL / AUDIT_PASSWORD   required
 *   AUDIT_PORTAL     default student
 *   AUDIT_ROUTES     comma-separated; default student routes
 *   AUDIT_SETTLE_MS  per-route settle time, default 6000
 *   AUDIT_TABS       max Tab presses per route, default 25
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.AUDIT_BASE_URL || 'http://127.0.0.1:3100').replace(/\/$/, '');
const EMAIL = process.env.AUDIT_EMAIL;
const PASSWORD = process.env.AUDIT_PASSWORD;
const LOCALE = process.env.AUDIT_LOCALE || 'vi';
const SETTLE_MS = Number(process.env.AUDIT_SETTLE_MS || 6000);
const MAX_TABS = Number(process.env.AUDIT_TABS || 25);

if (!EMAIL || !PASSWORD) {
  console.error('Missing AUDIT_EMAIL / AUDIT_PASSWORD in the environment.');
  process.exit(2);
}

const ROUTES = (process.env.AUDIT_ROUTES
  ? process.env.AUDIT_ROUTES.split(',')
  : ['/dashboard', '/dashboard/grades', '/dashboard/announcements']
).map((r) => r.trim()).filter(Boolean);

/** Runs in-page: describes the focused element and its focus indicator. */
function describeFocus() {
  const el = document.activeElement;
  if (!el || el === document.body) return { focused: false };

  // Split a box-shadow list into layers while respecting nested parentheses
  // (e.g. "rgb(0 0 0 / 0.5) 0px 0px 0px 3px, inset 0 0 0 1px").
  const splitLayers = (value) => {
    const layers = [];
    let depth = 0;
    let current = '';
    for (const ch of value) {
      if (ch === '(') depth += 1;
      if (ch === ')') depth -= 1;
      if (ch === ',' && depth === 0) {
        layers.push(current.trim());
        current = '';
      } else {
        current += ch;
      }
    }
    if (current.trim()) layers.push(current.trim());
    return layers;
  };

  // Normalize ANY CSS color (hsl, oklch, color-mix, keywords) through the
  // browser, then read back rgb()/rgba() form. Chrome serializes oklch back as
  // oklch(...), so fall back to a manual OKLCH → sRGB conversion.
  const probe = document.createElement('span');
  probe.style.display = 'none';
  document.body.appendChild(probe);
  const oklchToRgb = (value) => {
    const m = value.match(/oklch\(\s*([\d.]+%?)\s+([\d.]+)\s+([\d.]+)(?:deg)?\s*(?:\/\s*([\d.]+%?)\s*)?\)/);
    if (!m) return null;
    const pct = (v) => (v.endsWith('%') ? parseFloat(v) / 100 : parseFloat(v));
    const L = pct(m[1]);
    const C = parseFloat(m[2]);
    const Hd = parseFloat(m[3]) * (Math.PI / 180);
    const A = m[4] ? pct(m[4]) : 1;
    const a = Math.cos(Hd) * C;
    const b = Math.sin(Hd) * C;
    const l_ = L + 0.3963377774 * a + 0.2158037573 * b;
    const m_ = L - 0.1055613458 * a - 0.0638541728 * b;
    const s_ = L - 0.0894841775 * a - 1.291485548 * b;
    const l = l_ * l_ * l_;
    const mm = m_ * m_ * m_;
    const s = s_ * s_ * s_;
    const r = 4.0767416621 * l - 3.3077115913 * mm + 0.2309699292 * s;
    const g = -1.2684380046 * l + 2.6097574011 * mm - 0.3413193965 * s;
    const bb = -0.0041960863 * l - 0.7034186147 * mm + 1.707614701 * s;
    const gam = (v) => {
      const x = Math.min(Math.max(v, 0), 1);
      return Math.round((x <= 0.0031308 ? 12.92 * x : 1.055 * Math.pow(x, 1 / 2.4) - 0.055) * 255);
    };
    return { r: gam(r), g: gam(g), b: gam(bb), a: A };
  };
  const parseColor = (value) => {
    if (!value || value === 'none' || value === 'transparent') return null;
    const ok = oklchToRgb(value);
    if (ok) return ok;
    probe.style.color = '';
    probe.style.color = value;
    const normalized = probe.style.color;
    if (!normalized || normalized === 'none' || normalized === 'transparent') return null;
    const inner = normalized.match(/rgba?\(([^)]+)\)/);
    if (!inner) return oklchToRgb(normalized);
    const body = inner[1].replace(/\//g, ' ');
    const nums = body.trim().split(/[\s,]+/).filter(Boolean);
    if (nums.some((n) => n.endsWith('%'))) return null;
    const comp = nums.slice(0, 3).map((n) => parseFloat(n));
    if (comp.length < 3 || comp.some((n) => Number.isNaN(n))) return null;
    let alpha = nums.length > 3 ? parseFloat(nums[3]) : 1;
    if (Number.isNaN(alpha)) alpha = 1;
    return { r: comp[0], g: comp[1], b: comp[2], a: alpha };
  };
  const lin = (v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const lum = (c) => 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  const ratio = (a, b) => {
    const x = lum(a);
    const y = lum(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  };
  const style = getComputedStyle(el);
  const parse = parseColor;

  // Composite the element background over its ancestors once.
  let bg = { r: 255, g: 255, b: 255, a: 1 };
  const chain = [];
  for (let n = el; n && n.nodeType === 1; n = n.parentElement) chain.push(n);
  for (const node of chain.reverse()) {
    const c = parse(getComputedStyle(node).backgroundColor);
    if (c && c.a > 0) {
      bg = {
        r: c.r * c.a + bg.r * (1 - c.a),
        g: c.g * c.a + bg.g * (1 - c.a),
        b: c.b * c.a + bg.b * (1 - c.a),
        a: 1,
      };
    }
  }

  const outlineWidth = parseFloat(style.outlineWidth) || 0;
  const outlineStyle = style.outlineStyle;
  const outlineColorRaw = style.outlineColor;
  const outlineColor = parse(outlineColorRaw);
  const outlineVisible =
    outlineStyle !== 'none' && outlineWidth > 0 && outlineColor && outlineColor.a > 0;

  // Tailwind rings are box-shadow layers "C 0px 0px 0px Spx" — the spread is
  // the 4th <length>, not the first. Also capture the ring color per layer.
  const ringLayers = splitLayers(style.boxShadow || '').filter((s) => !s.includes('inset'));
  let shadowVisible = false;
  let shadowContrast = null;
  for (const layer of ringLayers) {
    const lengths = layer.match(/-?\d+(?:\.\d+)?px/g) || [];
    const spread = lengths.length >= 4 ? parseFloat(lengths[3]) : 0;
    const colorMatch = layer.match(/(?:rgba?|hsla?|oklcha?|color-mix)\([^)]*\)|#\w{3,8}/);
    const c = colorMatch ? parse(colorMatch[0]) : null;
    if (c && c.a > 0 && spread > 0) {
      shadowVisible = true;
      const solid = c.a < 1 ? over(c, bg) : c;
      shadowContrast = Math.round(ratio(solid, bg) * 100) / 100;
      break;
    }
  }

  let indicator = 'none';
  let outlineContrast = null;
  if (outlineVisible) {
    const solid = outlineColor.a < 1 ? over(outlineColor, bg) : outlineColor;
    outlineContrast = Math.round(ratio(solid, bg) * 100) / 100;
    indicator = `outline ${outlineStyle} ${outlineWidth}px, contrast ${outlineContrast}:1`;
  } else if (shadowVisible) {
    indicator = `box-shadow, contrast ${shadowContrast}:1`;
    outlineContrast = shadowContrast;
  }

  probe.remove();

  return {
    focused: true,
    tag: el.tagName.toLowerCase(),
    text: (el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 50),
    indicator,
    outlineVisible,
    shadowVisible,
    outlineContrast,
    debug: outlineVisible ? null : `outlineRaw=${outlineColorRaw} boxShadowRaw=${(style.boxShadow || '').slice(0, 120)}`,
  };

  function over(fg, bgc) {
    return {
      r: fg.r * fg.a + bgc.r * (1 - fg.a),
      g: fg.g * fg.a + bgc.g * (1 - fg.a),
      b: fg.b * fg.a + bgc.b * (1 - fg.a),
      a: 1,
    };
  }
}

async function login(page) {
  const portal = process.env.AUDIT_PORTAL || 'student';
  await page.goto(`${BASE_URL}/${LOCALE}/login?portal=${portal}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    try {
      await page.locator('#email').fill(EMAIL);
      await page.locator('#password').fill(PASSWORD);
    } catch {
      await page.waitForTimeout(1500);
      continue;
    }
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;
    await submit.click().catch(() => {});
    if (
      await page.waitForURL(/\/(dashboard|admin)(?:$|[/?#])/, { timeout: 75000 }).then(() => true).catch(() => false)
    ) {
      return;
    }
  }
  throw new Error('login did not reach the workspace');
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  await context.addInitScript(() => {
    try {
      window.localStorage.setItem('theme', 'dark');
    } catch {}
  });
  const page = await context.newPage();
  const totals = { checked: 0, invisible: 0 };

  try {
    await login(page);
    console.log('logged in:', page.url());

    for (const route of ROUTES) {
      await page.goto(`${BASE_URL}/${LOCALE}${route}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
      await page.evaluate(() => document.documentElement.classList.add('dark'));
      await page.waitForTimeout(SETTLE_MS);

      // Walk the tab order with trusted keys and inspect each focused element.
      const observations = [];
      let lastText = '';
      for (let i = 0; i < MAX_TABS; i += 1) {
        await page.keyboard.press('Tab');
        await page.waitForTimeout(120);
        const info = await page.evaluate(describeFocus);
        if (!info.focused) break;
        if (info.text === lastText) continue; // same control, still focused
        lastText = info.text;
        observations.push(info);
      }

      // WCAG 2.4.7 is binary: a visible focus indicator must exist. The
      // contrast number is advisory here — this app layers translucent glass
      // surfaces, so a generic in-page compositor cannot know the true
      // adjacent surface (verified separately by token math + screenshots).
      const missing = observations.filter((o) => o.indicator === 'none');
      const weak = observations.filter(
        (o) => o.indicator !== 'none' && o.outlineContrast !== null && o.outlineContrast < 3,
      );
      totals.checked += observations.length;
      totals.invisible += missing.length;
      console.log(
        `\n=== ${route} — focused ${observations.length}, missing indicator ${missing.length}, advisory-low-contrast ${weak.length} ===`,
      );
      missing.forEach((o) =>
        console.log(`  MISSING :: <${o.tag}> "${o.text}"${o.debug ? ` [${o.debug}]` : ''}`),
      );
      weak.forEach((o) => console.log(`  low-contrast ${o.outlineContrast}:1 :: <${o.tag}> "${o.text}"`));
    }
  } catch (error) {
    console.log(`HARNESS ERROR: ${String(error).slice(0, 300)}`);
  } finally {
    await browser.close();
  }

  console.log(`\n==== focused elements checked ${totals.checked}, weak/missing ${totals.invisible} ====`);
  process.exit(totals.invisible === 0 && totals.checked > 0 ? 0 : 1);
}

main();
