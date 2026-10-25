'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Same transpile harness as combobox.test.js: real modules, stubbed i18n.
function load(relativePath, aliases = {}) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
    fileName: relativePath,
  }).outputText;
  const record = { exports: {} };
  const resolve = (name) => {
    if (name === '@/lib/utils') return load('src/lib/utils.ts', aliases);
    if (name === '@/lib/date-time-24') return load('src/lib/date-time-24.ts', aliases);
    if (Object.prototype.hasOwnProperty.call(aliases, name)) return aliases[name];
    return require(name);
  };
  Function('module', 'exports', 'require', output)(record, record.exports, resolve);
  return record.exports;
}

const lib = load('src/lib/date-time-24.ts');
const { splitDateTime24, composeDateTime24, editDateTime24 } = lib;

// ---------- splitDateTime24: incoming values -> display parts ----------

test('splitDateTime24 reads empty, minute and sub-minute precision values', () => {
  assert.deepEqual(splitDateTime24(''), { date: '', hour: '', minute: '' });
  assert.deepEqual(splitDateTime24(null), { date: '', hour: '', minute: '' });
  assert.deepEqual(splitDateTime24(undefined), { date: '', hour: '', minute: '' });
  assert.deepEqual(splitDateTime24('2026-10-25T09:30'), {
    date: '2026-10-25', hour: '09', minute: '30',
  });
  // Stored rounds arrive with seconds/milliseconds; display keeps the minute.
  assert.deepEqual(splitDateTime24('2026-10-25T09:30:45'), {
    date: '2026-10-25', hour: '09', minute: '30',
  });
  assert.deepEqual(splitDateTime24('2026-10-25T09:30:45.140'), {
    date: '2026-10-25', hour: '09', minute: '30',
  });
});

test('splitDateTime24 rejects malformed and out-of-range parts', () => {
  for (const bad of ['garbage', '2026-10-25T', 'T09:30', '25/10/2026 09:30']) {
    assert.deepEqual(splitDateTime24(bad), { date: '', hour: '', minute: '' }, bad);
  }
  // A bare date is a valid partial read: the time fields stay unset.
  assert.deepEqual(splitDateTime24('2026-10-25'), {
    date: '2026-10-25', hour: '', minute: '',
  });
  // Out-of-range clock parts fall back to unset instead of pinning a wrong option.
  assert.deepEqual(splitDateTime24('2026-10-25T25:00'), {
    date: '2026-10-25', hour: '', minute: '00',
  });
  assert.deepEqual(splitDateTime24('2026-10-25T09:99'), {
    date: '2026-10-25', hour: '09', minute: '',
  });
});

// ---------- composeDateTime24: the emitted contract ----------

test('composeDateTime24 only emits a complete value when a date exists', () => {
  assert.equal(composeDateTime24('', '09', '30'), '');
  assert.equal(composeDateTime24('2026-10-25', '09', '30'), '2026-10-25T09:30');
  // Hour/minute default to 00 when unset.
  assert.equal(composeDateTime24('2026-10-25', '', ''), '2026-10-25T00:00');
  assert.equal(composeDateTime24('2026-10-25', '09'), '2026-10-25T09:00');
  // Inputs are normalized: single digits pad, out-of-range clamps to 00.
  assert.equal(composeDateTime24('2026-10-25', '9', '5'), '2026-10-25T09:05');
  assert.equal(composeDateTime24('2026-10-25', '24', '60'), '2026-10-25T00:00');
});

// ---------- editDateTime24: per-control edit semantics ----------

test('first date pick emits midnight unless a pending time was chosen', () => {
  assert.deepEqual(editDateTime24('', 'date', '2026-10-25'), {
    emit: '2026-10-25T00:00', pendingHour: '', pendingMinute: '',
  });
  const pendingPick = editDateTime24('', 'hour', '14');
  assert.equal(pendingPick.emit, null, 'an hour pick with no date does not emit');
  const withPending = editDateTime24('', 'date', '2026-10-25', {
    hour: pendingPick.pendingHour, minute: pendingPick.pendingMinute,
  });
  assert.equal(withPending.emit, '2026-10-25T14:00');
});

test('clearing the date emits the empty value', () => {
  assert.deepEqual(editDateTime24('2026-10-25T09:30', 'date', ''), {
    emit: '', pendingHour: '', pendingMinute: '',
  });
});

test('hour and minute edits recompose on the stored date', () => {
  assert.deepEqual(
    editDateTime24('2026-10-25T09:30', 'hour', '23'),
    { emit: '2026-10-25T23:30', pendingHour: '', pendingMinute: '' },
  );
  assert.deepEqual(
    editDateTime24('2026-10-25T09:30', 'minute', '59'),
    { emit: '2026-10-25T09:59', pendingHour: '', pendingMinute: '' },
  );
});

test('a stored seconds value round-trips untouched until the user edits', () => {
  const stored = '2026-10-25T09:00:00.140';
  // No edit -> the field emits nothing; the parent's stored value is unchanged.
  assert.equal(editDateTime24(stored, 'hour', '09').emit, '2026-10-25T09:00',
    'editing drops the seconds and emits canonical minute precision');
  assert.equal(editDateTime24(stored, 'date', '2026-11-01').emit, '2026-11-01T09:00',
    'editing the date keeps the wall-clock time and drops sub-minute precision');
  // The display split never leaks the seconds into the shown fields.
  assert.deepEqual(splitDateTime24(stored), { date: '2026-10-25', hour: '09', minute: '00' });
});

test('hour/minute picks before a date stay pending and never emit', () => {
  const hourPick = editDateTime24('', 'hour', '23');
  assert.equal(hourPick.emit, null);
  assert.equal(hourPick.pendingHour, '23');
  const minutePick = editDateTime24('', 'minute', '45', {
    hour: hourPick.pendingHour, minute: hourPick.pendingMinute,
  });
  assert.equal(minutePick.emit, null);
  assert.deepEqual(
    { hour: minutePick.pendingHour, minute: minutePick.pendingMinute },
    { hour: '23', minute: '45' },
  );
  const datePick = editDateTime24('', 'date', '2026-10-25', {
    hour: minutePick.pendingHour, minute: minutePick.pendingMinute,
  });
  assert.equal(datePick.emit, '2026-10-25T23:45');
  assert.equal(datePick.pendingHour, '', 'the pending time is consumed by the emission');
});

test('malformed edits never emit a broken value', () => {
  const ignored = editDateTime24('2026-10-25T09:30', 'date', 'not-a-date');
  assert.equal(ignored.emit, null, 'a malformed date event is ignored, not a wipe');
  assert.deepEqual(
    editDateTime24('2026-10-25T09:30', 'hour', '99'),
    { emit: '2026-10-25T00:30', pendingHour: '', pendingMinute: '' },
    'an out-of-range hour falls back to 00',
  );
});

// ---------- render contract ----------

const fieldFor = (messages) =>
  load('src/components/ui/date-time-24-field.tsx', { '@/i18n': { useI18n: () => ({ messages }) } });

const EN = { common: { dateTime24: { date: 'Date', hour: 'Hour', minute: 'Minute' } } };
const VI = { common: { dateTime24: { date: 'Ngày', hour: 'Giờ', minute: 'Phút' } } };

const render = (messages, props) =>
  renderToStaticMarkup(
    React.createElement(fieldFor(messages).DateTime24Field, {
      value: '',
      onChange: () => {},
      ...props,
    }),
  );

test('the group is named by the visible label and every control is labelled', () => {
  const html = render(EN, { label: 'Registration start' });
  const labelledby = html.match(/role="group"[^>]*aria-labelledby="([^"]+)"/);
  assert.ok(labelledby, 'the group references the visible label');
  const labelId = labelledby[1];
  assert.match(html, new RegExp(`id="${labelId}"[^>]*>Registration start`));
  assert.match(html, /<input[^>]*type="date"/);
  assert.match(html, /aria-label="Date"/);
  assert.match(html, /aria-label="Hour"/);
  assert.match(html, /aria-label="Minute"/);
});

test('a standalone field without a label falls back to the aria-label prop', () => {
  const html = render(EN, { 'aria-label': 'Publish at' });
  assert.match(html, /role="group"[^>]*aria-label="Publish at"/);
  assert.doesNotMatch(html, /aria-labelledby/);
});

test('the value is split into the three controls at minute precision', () => {
  const html = render(EN, { value: '2026-10-25T09:30:45.140' });
  assert.match(html, /<input[^>]*type="date"[^>]*value="2026-10-25"/);
  const selects = html.match(/<select[\s\S]*?<\/select>/g) ?? [];
  assert.equal(selects.length, 2, 'one select each for hour and minute');
  // React SSR marks the current value with selected="" on the option itself.
  assert.match(selects[0], /<option value="09" selected="">09<\/option>/, 'hour shows 09');
  assert.match(selects[1], /<option value="30" selected="">30<\/option>/, 'minute shows 30');
  // Sub-minute precision is shown to the minute — there is no 45s surface.
  assert.doesNotMatch(html, /selected=""[^>]*>45/);
});

test('the hour select runs 00-23 and the minute select 00-59 with a placeholder while unset', () => {
  const html = render(EN, {});
  const selects = html.match(/<select[\s\S]*?<\/select>/g) ?? [];
  assert.equal(selects.length, 2);
  const hours = [...selects[0].matchAll(/<option value="(\d{2})">/g)].map((m) => m[1]);
  const minutes = [...selects[1].matchAll(/<option value="(\d{2})">/g)].map((m) => m[1]);
  assert.equal(hours.length, 24, 'hour options are exactly 00-23');
  assert.equal(hours[0], '00');
  assert.equal(hours[23], '23');
  assert.equal(minutes.length, 60, 'minute options are exactly 00-59');
  assert.equal(minutes[0], '00');
  assert.equal(minutes[59], '59');
  assert.match(html, /<option value="" selected="">--<\/option>/, 'unset field shows a placeholder');
  // Once a date is chosen the placeholder leaves the option list.
  const filled = render(EN, { value: '2026-10-25T09:30' });
  assert.doesNotMatch(filled, /<option value=""/, 'set field has no placeholder option');
});

test('required, disabled, min and max reach the date control', () => {
  const html = render(EN, { label: 'Due', required: true, disabled: true, min: '2026-01-01', max: '2026-12-31' });
  const input = html.match(/<input[^>]*type="date"[^>]*\/>/)[0];
  for (const attr of ['required=""', 'disabled=""', 'min="2026-01-01"', 'max="2026-12-31"']) {
    assert.ok(input.includes(attr), `date input carries ${attr}`);
  }
});

test('error and hint wiring matches the Input primitive', () => {
  const withError = render(EN, { error: 'Start is required' });
  assert.match(withError, /aria-invalid="true"/);
  const describedby = withError.match(/aria-describedby="([^"]+)"/)[1];
  assert.match(withError, new RegExp(`<p id="${describedby}"[^>]*>Start is required`));
  const withHint = render(EN, { hint: 'Leave empty for no end' });
  assert.match(withHint, /Leave empty for no end/);
});

test('the id prop lands on the date input for stable test hooks', () => {
  const html = render(EN, { id: 'extend-deadline', label: 'Report deadline' });
  assert.match(html, /<input[^>]*id="extend-deadline"[^>]*type="date"/);
});

// ---------- contract: no native datetime-local remains in src/app ----------

function walk(directory) {
  const files = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...walk(absolute));
    else if (/\.(tsx?|jsx?)$/.test(entry.name)) files.push(absolute);
  }
  return files;
}

test('no type="datetime-local" remains in src/app', () => {
  const offenders = [];
  for (const file of walk(path.join(root, 'src/app'))) {
    const source = fs.readFileSync(file, 'utf8');
    if (source.includes('datetime-local')) {
      offenders.push(path.relative(root, file));
    }
  }
  assert.deepEqual(offenders, [], `native datetime-local inputs left: ${offenders.join(', ')}`);
});

test('every date+time entry in admin pages goes through DateTime24Field', () => {
  for (const page of ['src/app/admin/thesis/page.tsx', 'src/app/admin/announcements/page.tsx']) {
    const source = read(page);
    assert.doesNotMatch(source, /type="time"/, `${page} uses no bare time inputs for date+time`);
    assert.ok(source.includes('DateTime24Field'), `${page} uses DateTime24Field`);
  }
});

// ---------- i18n parity ----------

function loadMessages() {
  return load('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': load('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': load('src/i18n/messages-dean-overview.ts'),
  });
}

test('en and vi both carry the dateTime24 control labels', () => {
  const { en, vi } = loadMessages();
  for (const key of ['date', 'hour', 'minute']) {
    assert.equal(typeof en.common.dateTime24[key], 'string', `en.common.dateTime24.${key}`);
    assert.equal(typeof vi.common.dateTime24[key], 'string', `vi.common.dateTime24.${key}`);
    assert.notEqual(en.common.dateTime24[key], vi.common.dateTime24[key],
      `${key} is actually translated, not copied`);
  }
});
