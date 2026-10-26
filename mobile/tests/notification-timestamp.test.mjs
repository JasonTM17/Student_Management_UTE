import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: path.basename(relativePath),
  }).outputText;
}

function loadFeedModule() {
  const module = { exports: {} };
  new Function(
    'exports',
    'require',
    'module',
    'process',
    transpile('src/screens/student/notification-feed.ts'),
  )(module.exports, () => ({}), module, { env: {} });
  return module.exports;
}

test('notification rows format the backend ISO instant instead of printing it raw', () => {
  const screens = read('src/screens/student/StudentScreens.tsx');
  const start = screens.indexOf('export function NotificationsScreen');
  const end = screens.indexOf('export function', start + 1);
  const screen = end === -1 ? screens.slice(start) : screens.slice(start, end);

  // The ListRow meta must run the wire value through the timestamp
  // formatter; feeding the raw Instant string would show "2026-10-26T…Z".
  assert.match(screen, /meta=\{formatNotificationTimestamp\(notification\.createdAt\)\}/);
  assert.doesNotMatch(screen, /meta=\{notification\.createdAt\}/);
});

test('formatNotificationTimestamp renders a readable local date-time', () => {
  const { formatNotificationTimestamp } = loadFeedModule();

  const rendered = formatNotificationTimestamp('2026-10-26T08:15:30.000Z');

  assert.notEqual(rendered, '2026-10-26T08:15:30.000Z');
  assert.doesNotMatch(rendered, /T\d{2}:\d{2}:\d{2}/);
  assert.match(rendered, /2026/);
  assert.match(rendered, /Oct|10/);
});

test('formatNotificationTimestamp passes non-date labels and blanks through safely', () => {
  const { formatNotificationTimestamp } = loadFeedModule();

  assert.equal(formatNotificationTimestamp('10m'), '10m');
  assert.equal(formatNotificationTimestamp('Just now'), 'Just now');
  assert.equal(formatNotificationTimestamp(''), '');
  assert.equal(formatNotificationTimestamp(undefined), '');
  assert.equal(formatNotificationTimestamp(null), '');
});
