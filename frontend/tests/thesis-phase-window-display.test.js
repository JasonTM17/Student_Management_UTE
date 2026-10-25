const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Same TypeScript transpile harness as thesis-round-schedule.test.js.
function loadModule(relative, aliases = {}) {
  const output = ts.transpileModule(read(relative), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
    fileName: relative,
  }).outputText;
  const moduleRecord = { exports: {} };
  const resolve = (name) => Object.prototype.hasOwnProperty.call(aliases, name)
    ? aliases[name]
    : require(name);
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, resolve);
  return moduleRecord.exports;
}

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': loadModule('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': loadModule('src/i18n/messages-dean-overview.ts'),
  });
}

const { formatThesisDateTime, thesisWindowRemaining } = loadModule('src/lib/thesis-datetime.ts');

// ---------- pinned display: dd/MM/yyyy HH:mm in Asia/Bangkok ----------

test('thesis phase windows render as dd/MM/yyyy HH:mm pinned to Asia/Bangkok', () => {
  // 17:00 UTC is midnight the next day in Bangkok (UTC+7).
  assert.equal(formatThesisDateTime('2026-10-25T17:00:00.000Z'), '26/10/2026 00:00');
  assert.equal(formatThesisDateTime('2026-10-25T08:05:00.000Z'), '25/10/2026 15:05');
  assert.equal(formatThesisDateTime(new Date('2027-01-15T00:00:00.000Z')), '15/01/2027 07:00');
});

test('the pinned formatter never emits a 12-hour clock or a month name', () => {
  const rendered = formatThesisDateTime('2026-11-30T16:59:00.000Z');
  assert.match(rendered, /^\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}$/);
  assert.equal(rendered, '30/11/2026 23:59');
});

test('the pinned formatter degrades to the em-dash on bad input', () => {
  assert.equal(formatThesisDateTime('not-a-date'), '—');
  assert.equal(formatThesisDateTime(null), '—');
  assert.equal(formatThesisDateTime(undefined), '—');
  assert.equal(formatThesisDateTime(''), '—');
});

// ---------- remaining time ----------

test('thesisWindowRemaining reports days, hours and minutes until a live deadline', () => {
  const end = '2026-11-10T17:00:00.000Z';
  const now = Date.parse('2026-11-07T11:30:00.000Z');
  assert.deepEqual(thesisWindowRemaining(end, now), { days: 3, hours: 5, minutes: 30 });
});

test('thesisWindowRemaining returns null once the window has passed or is unreadable', () => {
  const end = '2026-11-10T17:00:00.000Z';
  assert.equal(thesisWindowRemaining(end, Date.parse('2026-11-10T17:00:00.000Z')), null);
  assert.equal(thesisWindowRemaining(end, Date.parse('2026-11-10T17:00:01.000Z')), null);
  assert.equal(thesisWindowRemaining('not-a-date', Date.now()), null);
  assert.equal(thesisWindowRemaining(null, Date.now()), null);
});

// ---------- page wiring ----------

const page = read('src/app/dashboard/thesis/page.tsx');
const milestoneCard = read('src/components/thesis/RoundMilestoneCard.tsx');

test('the shared thesis workspace renders windows with the pinned Bangkok formatter', () => {
  assert.match(
    page,
    /import \{[^}]*formatThesisDateTime[^}]*\} from '@\/lib\/thesis-datetime'/,
    'the thesis page must import the pinned formatter',
  );
  // Every milestone/stepper/deadline surface on the page is fed one formatter;
  // it must be the pinned one rather than the locale-conditional i18n one.
  assert.match(page, /formatDateTime=\{formatThesisDateTime\}|formatDateTime = formatThesisDateTime/);
});

test('the milestone card shows remaining time for a live window', () => {
  assert.match(milestoneCard, /thesisWindowRemaining/, 'the card must compute remaining time');
  assert.match(
    milestoneCard,
    /\.remaining(DaysHours|HoursMinutes|Minutes)/,
    'the card must render the remaining copy',
  );
});

test('remaining-time copy exists in both locales with matching placeholders', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of ['remainingDaysHours', 'remainingHoursMinutes', 'remainingMinutes']) {
      assert.ok(dict.roundMilestone[key], `roundMilestone.${key} missing`);
    }
  }
  assert.match(en.roundMilestone.remainingDaysHours, /\{d\}.*\{h\}/);
  assert.match(en.roundMilestone.remainingHoursMinutes, /\{h\}.*\{m\}/);
  assert.match(en.roundMilestone.remainingMinutes, /\{m\}/);
  assert.match(vi.roundMilestone.remainingDaysHours, /\{d\}.*\{h\}/);
  assert.match(vi.roundMilestone.remainingHoursMinutes, /\{h\}.*\{m\}/);
  assert.match(vi.roundMilestone.remainingMinutes, /\{m\}/);
});
