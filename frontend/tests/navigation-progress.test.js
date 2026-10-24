const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath, dependencies = {}) {
  const output = ts.transpileModule(
    fs.readFileSync(path.join(root, relativePath), 'utf8'),
    {
      compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
    },
  ).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(
    moduleRecord,
    moduleRecord.exports,
    (id) => dependencies[id] ?? {},
  );
  return moduleRecord.exports;
}

const {
  NavigationProgressController,
  shouldStartProgress,
} = load('src/lib/navigation-progress.ts');

// A deterministic scheduler: timers fire only when the test flushes them.
function fakeScheduler() {
  const timers = new Map();
  let seq = 0;
  return {
    pending: () => timers.size,
    setTimeout: (fn, ms) => {
      const id = ++seq;
      timers.set(id, { fn, ms });
      return id;
    },
    clearTimeout: (id) => {
      timers.delete(id);
    },
    flush: () => {
      for (const { fn } of [...timers.values()].sort((a, b) => a.ms - b.ms)) {
        fn();
      }
    },
  };
}

function makeController() {
  const scheduler = fakeScheduler();
  const log = [];
  const controller = new NavigationProgressController(
    (state) => log.push({ ...state }),
    scheduler.setTimeout,
    scheduler.clearTimeout,
  );
  return { controller, scheduler, log };
}

test('a click that never produces a route change cannot leave the bar stuck', () => {
  const { controller, scheduler, log } = makeController();
  controller.handleNavigationStart();
  // The click was swallowed/prevented: no commit ever arrives.
  scheduler.flush();
  const last = log.at(-1);
  assert.equal(last.active, false, 'the bar must return to idle instead of pinning at 70%');
  assert.equal(last.progress, 0);
});

test('a route commit inside the ramp window must not regress progress back to 70%', () => {
  const { controller, scheduler, log } = makeController();
  controller.handleNavigationStart();
  controller.handleRouteCommitted();
  // The queued ramp timer must have been cancelled by the commit; firing it
  // late would repaint the bar at 70% on the already-loaded page.
  scheduler.flush();
  const last = log.at(-1);
  assert.equal(last.active, false);
  assert.equal(last.progress, 0);
  assert.ok(
    !log.slice(log.findIndex((s) => s.progress === 100)).some((s) => s.progress === 70),
    'progress must never fall back to 70 after reaching 100',
  );
});

test('a route change with no pending navigation is a harmless no-op', () => {
  const { controller, scheduler, log } = makeController();
  controller.handleRouteCommitted();
  assert.equal(log.length, 0, 'mount or back/forward must not paint the bar');
  assert.equal(scheduler.pending(), 0);
});

test('dispose cancels every pending timer so nothing outlives the component', () => {
  const { controller, scheduler, log } = makeController();
  controller.handleNavigationStart();
  controller.dispose();
  assert.equal(scheduler.pending(), 0);
  scheduler.flush();
  assert.equal(log.at(-1).active, false);
});

test('a second navigation during the completion fade restarts cleanly', () => {
  const { controller, scheduler, log } = makeController();
  controller.handleNavigationStart();
  controller.handleRouteCommitted();
  controller.handleNavigationStart();
  scheduler.flush();
  const last = log.at(-1);
  assert.equal(last.active, false, 'the earlier finish timer must not strand the new run');
});

const location = new URL('https://campuscore.example/vi/dashboard/lecturer/grades/42?x=1');

function anchor(overrides = {}) {
  const attrs = { href: '/vi/dashboard', ...(overrides.attrs ?? {}) };
  return {
    href: overrides.href ?? 'https://campuscore.example/vi/dashboard',
    target: overrides.target ?? '',
    getAttribute: (name) => attrs[name] ?? null,
    hasAttribute: (name) => name in attrs,
  };
}

test('anchor predicate only starts progress for real same-origin route changes', () => {
  assert.equal(shouldStartProgress(anchor(), location), true);
  // Same page: identical pathname+search (hash-only hops included).
  assert.equal(
    shouldStartProgress(
      anchor({
        href: location.href + '#section',
        attrs: { href: location.pathname + location.search + '#section' },
      }),
      location,
    ),
    false,
  );
  assert.equal(shouldStartProgress(anchor({ href: 'https://other.example/x' }), location), false);
  assert.equal(shouldStartProgress(anchor({ target: '_blank' }), location), false);
  assert.equal(shouldStartProgress(anchor({ attrs: { href: '/x', download: '' } }), location), false);
  assert.equal(
    shouldStartProgress(anchor({ attrs: { href: 'mailto:a@b.c' }, href: 'mailto:a@b.c' }), location),
    false,
  );
  assert.equal(shouldStartProgress(anchor({ attrs: { href: null } }), location), false);
  // A named browsing context does not navigate this tab.
  assert.equal(shouldStartProgress(anchor({ target: 'panel' }), location), false);
});

test('the component wires the controller on the bubble phase so cancelled clicks are invisible to it', () => {
  const source = fs.readFileSync(
    path.join(root, 'src/components/NavigationProgressBar.tsx'),
    'utf8',
  );
  assert.ok(
    source.includes('NavigationProgressController'),
    'the component must delegate timer/state work to the shared controller',
  );
  assert.ok(
    !/addEventListener\('click'[^)]*capture:\s*true/.test(source),
    'a capture-phase listener starts progress even for clicks the unsaved-changes guard swallows',
  );
  assert.ok(
    source.includes('prefers-reduced-motion'),
    'the animated bar must respect reduced-motion preferences',
  );
});
