const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

const LAYOUT = read('src/app/dashboard/layout.tsx');
const CENTER = read('src/components/dashboard/NotificationsCenterPage.tsx');
const API = read('src/lib/api.ts');

/**
 * Round 8 fixed the notifications center's deep links but left the dashboard
 * bell guessing destinations from title/body keywords — 'thi' inside 'lịch
 * thi' opened the timetable and admin-authored broadcast links were discarded.
 * Both surfaces must now resolve from the authored link/type data, and the
 * wire type must actually carry those fields.
 */

test('the bell resolver is authored-data driven, not keyword driven', () => {
  const start = LAYOUT.indexOf('function resolveNotificationTarget');
  const end = LAYOUT.indexOf('export default function DashboardLayout');
  assert.ok(start >= 0 && end > start, 'resolver exists in the dashboard layout');
  const resolver = LAYOUT.slice(start, end);
  assert.match(resolver, /notification\.link/, 'authored link wins');
  assert.match(resolver, /NOTIFICATION_TYPE_TARGETS/, 'feature types map to a destination');
  assert.doesNotMatch(
    resolver,
    /text\.includes\(|title \|\|/,
    'no title/body keyword guessing may return to the resolver',
  );
});

test('both notification surfaces share the same type-to-target mapping', () => {
  const bell = LAYOUT.match(/const NOTIFICATION_TYPE_TARGETS[^;]+;/)?.[0] ?? '';
  const center = CENTER.match(/const NOTIFICATION_TYPE_TARGETS[^;]+;/)?.[0] ?? '';
  assert.ok(bell && center, 'both surfaces declare the mapping');
  const extract = (source) =>
    Array.from(source.matchAll(/([A-Z_]+):\s*'([^']+)'/g)).map(([, key, value]) => `${key}:${value}`);
  assert.deepEqual(extract(bell), extract(center), 'bell and center must not drift apart again');
});

test('the notification wire type carries link and type through the API layer', () => {
  const record = API.match(/type NotificationRecord = \{[\s\S]*?\};/)?.[0] ?? '';
  assert.match(record, /link\??:/, 'link must be declared on NotificationRecord');
  assert.match(record, /type\??:/, 'type must be declared on NotificationRecord');
});
