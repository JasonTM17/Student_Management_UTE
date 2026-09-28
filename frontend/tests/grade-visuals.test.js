const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

const visuals = loadTs('src/lib/grade-visuals.ts');

test('score tiers follow the advisor bands: >=8 strong, 5-7.9 fair, <5 weak', () => {
  assert.equal(visuals.scoreTier(10), 'strong');
  assert.equal(visuals.scoreTier(8), 'strong');
  assert.equal(visuals.scoreTier(8.5), 'strong');
  assert.equal(visuals.scoreTier(7.9), 'fair');
  assert.equal(visuals.scoreTier(5), 'fair');
  assert.equal(visuals.scoreTier(6.4), 'fair');
  assert.equal(visuals.scoreTier(4.9), 'weak');
  assert.equal(visuals.scoreTier(0), 'weak');
});

test('missing scores have no tier', () => {
  assert.equal(visuals.scoreTier(null), null);
  assert.equal(visuals.scoreTier(undefined), null);
  assert.equal(visuals.scoreTier(Number.NaN), null);
  assert.equal(visuals.scoreTier(Number.POSITIVE_INFINITY), null);
  assert.equal(visuals.scoreTier('8'), null);
});

test('bar percent maps 0-10 onto 0-100 and clamps out-of-range values', () => {
  assert.equal(visuals.scoreBarPercent(0), 0);
  assert.equal(visuals.scoreBarPercent(5), 50);
  assert.equal(visuals.scoreBarPercent(8.5), 85);
  assert.equal(visuals.scoreBarPercent(10), 100);
  assert.equal(visuals.scoreBarPercent(12), 100);
  assert.equal(visuals.scoreBarPercent(-3), 0);
});

test('bar percent is zero for missing scores', () => {
  assert.equal(visuals.scoreBarPercent(null), 0);
  assert.equal(visuals.scoreBarPercent(undefined), 0);
  assert.equal(visuals.scoreBarPercent(Number.NaN), 0);
});

test('every tier maps to a non-empty Tailwind bar class', () => {
  assert.match(visuals.SCORE_TIER_BAR_CLASS.strong, /bg-status-success/);
  assert.match(visuals.SCORE_TIER_BAR_CLASS.fair, /bg-status-warning/);
  assert.match(visuals.SCORE_TIER_BAR_CLASS.weak, /bg-status-danger/);
});

test('semester progress cards mirror transcript order and keep names', () => {
  const cards = visuals.semesterProgressCards([
    {
      semesterId: 'hk241',
      semesterName: 'HK241',
      semesterNameEn: 'Fall 2024',
      semesterNameVi: 'Học kỳ 1 2024',
      records: [],
      gpa: 3.25,
      creditsEarned: 15,
      creditsAttempted: 15,
    },
    {
      semesterId: 'hk242',
      semesterName: 'HK242',
      records: [],
      gpa: 3.5,
      creditsEarned: 12,
      creditsAttempted: 14,
    },
  ]);

  assert.equal(cards.length, 2);
  assert.equal(cards[0].semesterId, 'hk241');
  assert.equal(cards[0].gpa, 3.25);
  assert.equal(cards[0].creditsEarned, 15);
  assert.equal(cards[0].semesterNameVi, 'Học kỳ 1 2024');
  assert.equal(cards[1].semesterNameEn, null);
});

test('a zero-GPA semester with no earned credits renders as "no result yet"', () => {
  const cards = visuals.semesterProgressCards([
    {
      semesterId: 'hk251',
      semesterName: 'HK251',
      records: [],
      gpa: 0,
      creditsEarned: 0,
      creditsAttempted: 0,
    },
  ]);

  assert.equal(cards[0].gpa, null);
  assert.equal(cards[0].creditsEarned, 0);
});

test('progress cards tolerate missing transcript data', () => {
  assert.deepEqual(visuals.semesterProgressCards(null), []);
  assert.deepEqual(visuals.semesterProgressCards(undefined), []);
  assert.deepEqual(visuals.semesterProgressCards([]), []);

  const cards = visuals.semesterProgressCards([
    {
      semesterId: 'hk252',
      semesterName: 'HK252',
      records: [],
      gpa: Number.NaN,
      creditsEarned: Number.NaN,
      creditsAttempted: 0,
    },
  ]);
  assert.equal(cards[0].gpa, null);
  assert.equal(cards[0].creditsEarned, 0);
});
