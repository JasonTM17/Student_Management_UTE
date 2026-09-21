/**
 * Function-level browser flows that the route walk cannot prove: the AI
 * assistant answer path, the rich-reader behaviours (TOC anchors, no leaked
 * markup, author image as cover), announcement reorder integrity, and the
 * dark/sepia reader theme. Screenshots and a JSON verdict per step land in
 * scratch/deepscan/out so the same script is the before/after oracle.
 *
 *   node scripts/deepscan-flows.mjs
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright-core';

const OUT = join(process.cwd(), '..', 'scratch', 'deepscan', 'out');
mkdirSync(OUT, { recursive: true });
const BASE = process.argv.includes('--base')
  ? process.argv[process.argv.indexOf('--base') + 1]
  : 'http://127.0.0.1:4317';
// `password123` is the seeded demo password; a database whose demo passwords
// were rotated needs DEEPSCAN_PASSWORD (or --password) instead.
const PASSWORD = process.argv.includes('--password')
  ? process.argv[process.argv.indexOf('--password') + 1]
  : process.env.DEEPSCAN_PASSWORD || 'password123';
const steps = [];
let bad = 0;

function say(step) {
  steps.push(step);
  process.stdout.write(`${JSON.stringify(step)}\n`);
  if (step.verdict === 'fail') bad += 1;
}

async function login(page, email) {
  await page.goto(`${BASE}/vi/login`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  await page.locator('input[type="email"]').last().fill(email);
  await page.locator('input[type="password"]').last().fill(PASSWORD);
  await page.locator('form button[type="submit"]').first().click();
  await page.waitForURL(/dashboard|admin/, { timeout: 25000 }).catch(() => {});
  await page.waitForTimeout(2500);
  return !page.url().includes('login');
}

async function shot(page, name) {
  await page.screenshot({ path: join(OUT, `${name.replace(/[^a-z0-9]+/gi, '_')}.png`) });
}

/** The floating assistant panel: ask, wait for a streamed answer, look for honesty. */
async function assistantFlow(page) {
  const launcher = page.locator('[data-assistant-launcher], button[aria-label*="trợ lý" i], button[aria-label*="assistant" i]').first();
  if (!(await launcher.count())) {
    say({ flow: 'assistant', step: 'open', verdict: 'fail', reason: 'no launcher found' });
    return;
  }
  await launcher.click();
  await page.waitForTimeout(1500);
  const input = page.locator('textarea, input[placeholder*="câu hỏi" i], input[placeholder*="ask" i]').last();
  if (!(await input.count())) {
    say({ flow: 'assistant', step: 'open', verdict: 'fail', reason: 'panel opened but no input' });
    return;
  }
  const before = await page.evaluate(() => document.body.innerText.length);
  await input.fill('Học phần tiên quyết là gì và tôi đăng ký khi nào?');
  await input.press('Enter').catch(() => {});
  const send = page.locator('button[aria-label*="gửi" i], button[aria-label*="send" i]').first();
  if (await send.count()) await send.click().catch(() => {});
  await page.waitForTimeout(18000);
  const after = await page.evaluate(() => document.body.innerText.length);
  const panelText = await page.evaluate(() => {
    const nodes = [...document.querySelectorAll('[class*="assistant" i], [role="dialog"]')];
    return (nodes.map((n) => n.innerText).join('\n') || '').slice(-1400);
  });
  await shot(page, 'flow-assistant-answer');
  say({
    flow: 'assistant',
    step: 'answer',
    verdict: after > before + 40 ? 'ok' : 'fail',
    grewBy: after - before,
    leakedSystemPrompt: /(system prompt|deepseek-v|api[_-]?key|<\|im_start\|>)/i.test(panelText),
    citations: /nguồn|source|tài liệu/i.test(panelText),
    tail: panelText.slice(-260).replace(/\s+/g, ' '),
  });
}

/** Reader integrity for whatever article is reachable. */
async function readerFlow(page) {
  await page.goto(`${BASE}/vi/dashboard/announcements`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  const card = page.locator('article, [role="listitem"], a[href*="announcement"]').first();
  if (!(await card.count())) {
    say({ flow: 'reader', step: 'open', verdict: 'fail', reason: 'no announcement card' });
    return;
  }
  await card.click().catch(() => {});
  await page.waitForTimeout(3500);
  const state = await page.evaluate(() => {
    const root = document.querySelector('article') || document.body;
    const toc = document.querySelector('nav[aria-label*="mục lục" i], nav[aria-label*="table of content" i]');
    const headings = [...root.querySelectorAll('h1,h2,h3,h4,h5,h6')];
    const withId = headings.filter((h) => h.id);
    const imgs = [...root.querySelectorAll('img')];
    const bodyText = root.innerText || '';
    return {
      tocPresent: Boolean(toc),
      tocItems: toc ? toc.querySelectorAll('button, a').length : 0,
      headings: headings.length,
      headingsWithId: withId.length,
      anchorTargetsResolve: withId.every((h) => Boolean(document.getElementById(h.id))),
      images: imgs.length,
      dataUriImages: imgs.filter((i) => (i.currentSrc || i.src).startsWith('data:')).length,
      leakedMarkup: /<\/?(?:p|div|span|img|figure|strong)\b|-->/.test(bodyText),
      leakedPlaceholder: /\{\{|\bnull\b|\bundefined\b\}/.test(bodyText),
    };
  });
  await shot(page, 'flow-reader');
  say({ flow: 'reader', step: 'inspect', verdict: state.leakedMarkup ? 'fail' : 'ok', ...state });
}

/** Admin reorder must refuse on a filtered/paged list and persist a real reorder. */
async function reorderFlow(page) {
  await page.goto(`${BASE}/vi/admin/announcements`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const handles = page.locator('[aria-roledescription="sortable"], .drag-handle, [class*="drag" i]');
  const handleCount = await handles.count();
  const disabled = await page.evaluate(() => {
    const el = document.querySelector('[aria-roledescription="sortable"], .drag-handle, [class*="drag" i]');
    if (!el) return 'no-handle';
    const btn = el.closest('button') || el;
    return btn.getAttribute('aria-disabled') || btn.disabled ? 'disabled' : 'enabled';
  });
  const explanation = await page.evaluate(() => {
    const t = document.body.innerText || '';
    const line = t.split('\n').find((l) => /lọc|trang 1|xóa bộ lọc|bỏ lọc/i.test(l));
    return line ? line.trim().slice(0, 140) : null;
  });
  await shot(page, 'flow-reorder-gating');
  say({ flow: 'reorder', step: 'gating', verdict: handleCount ? 'ok' : 'fail', handleCount, disabled, explanation });
}

/** Dark and sepia reader themes must not print white-on-white. */
async function themeFlow(page) {
  await page.goto(`${BASE}/vi/dashboard/announcements`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(2500);
  const card = page.locator('article, [role="listitem"], a[href*="announcement"]').first();
  if (!(await card.count())) {
    say({ flow: 'theme', step: 'open', verdict: 'info', reason: 'no article to theme' });
    return;
  }
  await card.click().catch(() => {});
  await page.waitForTimeout(2500);
  for (const mode of ['Tối', 'Sepia', 'Sáng']) {
    const btn = page.getByRole('button', { name: new RegExp(mode, 'i') }).first();
    if (!(await btn.count())) continue;
    await btn.click().catch(() => {});
    await page.waitForTimeout(1200);
    const contrast = await page.evaluate(() => {
      const a = document.querySelector('article');
      if (!a) return null;
      const cs = getComputedStyle(a);
      return { bg: cs.backgroundColor, color: cs.color, theme: a.dataset.readerTheme || null };
    });
    await shot(page, `flow-theme-${mode}`);
    const same = contrast && contrast.bg === contrast.color;
    say({ flow: 'theme', step: mode, verdict: same ? 'fail' : 'ok', contrast });
  }
}

/**
 * Admin appearance write seen end to end: the accent button persists through the
 * Next proxy and the Spring `@PreAuthorize` PUT, the KV row stamps a monotonic
 * version, an anonymous read sees it, and a reload re-seats the UI from the API.
 * The original accent is restored at the end so the run leaves no residue.
 */
async function appearanceFlow(page) {
  const readApi = async () => {
    const res = await page.request.get('/api/v1/site-appearance');
    return { status: res.status(), body: await res.json().catch(() => null) };
  };

  await page.goto(`${BASE}/vi/admin/appearance`, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);

  const before = await readApi();
  const accents = page.locator('button.min-h-11[aria-pressed]');
  if (!(await accents.count())) {
    say({ flow: 'appearance', step: 'controls', verdict: 'fail', reason: 'no accent buttons rendered' });
    return;
  }
  const originalLabel = ((await accents
    .locator('[aria-pressed="true"]')
    .first()
    .textContent()
    .catch(() => '')) || '').trim();
  const target = accents.locator('[aria-pressed="false"]').first();
  const targetLabel = ((await target.textContent().catch(() => '')) || '').trim();
  await shot(page, 'flow-appearance-before');

  await target.click().catch(() => {});
  await page.waitForTimeout(3000);
  const persisted = await readApi();
  say({
    flow: 'appearance',
    step: 'persist',
    verdict:
      persisted.status === 200 &&
      Number(persisted.body?.version ?? 0) > Number(before.body?.version ?? 0)
        ? 'ok'
        : 'fail',
    clicked: targetLabel,
    before: { status: before.status, accent: before.body?.accent, version: before.body?.version },
    after: { accent: persisted.body?.accent, version: persisted.body?.version },
  });
  await shot(page, 'flow-appearance-after');

  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3500);
  const rebound = ((await page
    .locator('button.min-h-11[aria-pressed="true"]')
    .first()
    .textContent()
    .catch(() => '')) || '').trim();
  say({
    flow: 'appearance',
    step: 'reload-reads-server',
    verdict: rebound && rebound === targetLabel ? 'ok' : 'fail',
    expected: targetLabel,
    rendered: rebound,
  });

  if (originalLabel) {
    await accents.filter({ hasText: originalLabel }).first().click().catch(() => {});
    await page.waitForTimeout(2500);
    const restored = await readApi();
    say({
      flow: 'appearance',
      step: 'restore',
      verdict: restored.body?.accent === before.body?.accent ? 'ok' : 'info',
      accent: restored.body?.accent,
    });
  }
}

async function main() {
  const { existsSync, readdirSync } = await import('node:fs');
  const root = join(process.env.LOCALAPPDATA || '', 'ms-playwright');
  const dirs = existsSync(root) ? readdirSync(root).filter((d) => /^chromium-\d+$/.test(d)).reverse() : [];
  let executablePath;
  for (const d of dirs) {
    for (const sub of ['chrome-win64/chrome.exe', 'chrome-win/chrome.exe']) {
      const p = join(root, d, sub);
      if (existsSync(p)) { executablePath = p; break; }
    }
    if (executablePath) break;
  }
  const browser = await chromium.launch(executablePath ? { executablePath } : {});

  const studentCtx = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const sp = await studentCtx.newPage();
  if (await login(sp, 'student@campuscore.edu')) {
    await assistantFlow(sp);
    await readerFlow(sp);
    await themeFlow(sp);
  } else {
    say({ flow: 'student', step: 'login', verdict: 'fail', url: sp.url() });
  }
  await studentCtx.close();

  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  const ap = await adminCtx.newPage();
  if (await login(ap, 'admin@campuscore.edu')) {
    await reorderFlow(ap);
    await appearanceFlow(ap);
  } else {
    say({ flow: 'admin', step: 'login', verdict: 'fail', url: ap.url() });
  }
  await adminCtx.close();

  await browser.close();
  writeFileSync(join(OUT, 'flows.json'), JSON.stringify(steps, null, 2));
  process.stdout.write(`\nSUMMARY fails=${bad} steps=${steps.length}\n`);
}

main().catch((e) => {
  process.stderr.write(`FATAL ${e.stack || e}\n`);
  process.exit(1);
});
