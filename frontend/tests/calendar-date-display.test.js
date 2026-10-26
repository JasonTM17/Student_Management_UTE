const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Simulate a viewer west of UTC: date-only values ("YYYY-MM-DD") parse as
// UTC midnight under `new Date`, which lands on the previous civil day here.
process.env.TZ = 'America/New_York';

const root = path.resolve(__dirname, '..');

async function loadFormatDisplayModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/format-display.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'format-display.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('date-only API values render as their stored civil day in any browser zone', async () => {
  const precondition = new Date('2024-06-15');
  assert.equal(precondition.getUTCDate(), 15, 'spec parses date-only as UTC midnight');
  assert.equal(precondition.getDate(), 14, 'west of UTC the instant falls on the prior day');

  const { parseDisplayDate } = await loadFormatDisplayModule();
  const parsed = parseDisplayDate('2024-06-15');
  assert.equal(parsed.getFullYear(), 2024);
  assert.equal(parsed.getMonth(), 5);
  assert.equal(parsed.getDate(), 15, 'date-only must stay on its stored civil day');

  const withWhitespace = parseDisplayDate(' 2024-06-15 ');
  assert.equal(withWhitespace.getDate(), 15);

  const instant = parseDisplayDate('2024-06-15T10:30:00Z');
  assert.equal(instant.toISOString(), '2024-06-15T10:30:00.000Z', 'instants keep instant semantics');
});

test('i18n date formatters route through the civil-date parser', () => {
  const source = fs.readFileSync(path.join(root, 'src/i18n/index.tsx'), 'utf8');
  const formatDateBody = source.match(/formatDate: \(value, options\) => \{([\s\S]*?)\},\n\s*formatDateTime/);
  const formatDateTimeBody = source.match(/formatDateTime: \(value, options\) => \{([\s\S]*?)\},\n\s*formatNumber/);
  assert.ok(formatDateBody, 'formatDate body must be readable');
  assert.ok(formatDateTimeBody, 'formatDateTime body must be readable');
  assert.match(formatDateBody[1], /parseDisplayDate\(value\)/, 'formatDate must parse via parseDisplayDate');
  assert.match(formatDateTimeBody[1], /parseDisplayDate\(value\)/, 'formatDateTime must parse via parseDisplayDate');
});
