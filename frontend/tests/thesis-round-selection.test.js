const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

// Load the pure helper through the TypeScript compiler so the assertions run
// against the real module, not a copied reimplementation.
const source = fs.readFileSync(path.join(root, 'src/lib/thesis-round.ts'), 'utf8');
const js = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
  },
}).outputText;
const mod = { exports: {} };
new Function('module', 'exports', 'require', js)(mod, mod.exports, require);
const { pickPreferredThesisRound, sortThesisRoundsForOverview, mergeThesisRounds } = mod.exports;

test('round picker distinguishes live and cancelled rounds with localized status labels', () => {
  const labels = { REGISTRATION_OPEN: 'Đang mở đăng ký', CANCELLED: 'Đã hủy' };
  const rounds = [
    { id: 'cancelled', name: 'Demo KLTN', status: 'CANCELLED', registrationStart: '2026-10-25' },
    { id: 'open', name: 'Demo KLTN', status: 'REGISTRATION_OPEN', registrationStart: '2026-10-24' },
  ];
  const groups = mod.exports.groupThesisRoundsForPicker(rounds, labels);
  assert.deepEqual(groups.map(g => g.label), ['Đang mở đăng ký', 'Đã hủy']);
  assert.equal(groups[0].options[0].value, 'open');
  assert.equal(groups[0].options[0].label, 'Đang mở đăng ký · Demo KLTN');
  assert.equal(groups[1].options[0].label, 'Đã hủy · Demo KLTN');
  assert.equal(rounds[0].id, 'cancelled', 'sorting must not mutate the source');
  assert.equal(mod.exports.groupThesisRoundsForPicker(rounds, { CANCELLED: 'Cancelled', REGISTRATION_OPEN: 'Open' })[0].label, 'Open');
});

const round = (id, status = 'REGISTRATION_CLOSED', start = '2026-09-01', thesisType = 'KLTN') => ({
  id,
  status,
  registrationStart: start,
  thesisType,
});
const group = (roundId, status = 'SUBMITTED') => ({ roundId, status });

test('a round holding the student group beats the newest open round', () => {
  const rounds = [
    round('round-new-open', 'PROPOSAL_OPEN', '2027-01-10'),
    round('round-with-group', 'REGISTRATION_CLOSED', '2026-09-15'),
  ];
  // Newest-first group listing: the only membership lives in the closed round.
  const mine = [group('round-with-group')];
  assert.equal(pickPreferredThesisRound(rounds, mine), 'round-with-group');
});

test('no membership anywhere leaves the default selection untouched', () => {
  const rounds = [round('round-new-open', 'PROPOSAL_OPEN', '2027-01-10')];
  assert.equal(pickPreferredThesisRound(rounds, []), undefined);
  assert.equal(pickPreferredThesisRound(rounds, null), undefined);
});

test('a cancelled membership yields to an active one', () => {
  const rounds = [round('a'), round('b')];
  const mine = [group('b', 'CANCELLED'), group('a', 'APPROVED')];
  assert.equal(pickPreferredThesisRound(rounds, mine), 'a');
});

test('a membership in a cancelled round is never pinned', () => {
  // The seat is void: leaving the default selection alone lets the student
  // land on a round they can actually join instead of the dead round.
  const rounds = [
    round('round-open', 'REGISTRATION_OPEN', '2027-01-10'),
    round('round-cancelled', 'CANCELLED', '2026-09-15'),
  ];
  const mine = [group('round-cancelled', 'SUBMITTED')];
  assert.equal(pickPreferredThesisRound(rounds, mine), undefined);
});

test('a live-round membership still wins over one in a cancelled round', () => {
  const rounds = [
    round('round-cancelled', 'CANCELLED', '2026-09-15'),
    round('round-live', 'REGISTRATION_CLOSED', '2026-09-10'),
  ];
  const mine = [group('round-cancelled', 'SUBMITTED'), group('round-live', 'SUBMITTED')];
  assert.equal(pickPreferredThesisRound(rounds, mine), 'round-live');
});

test('a cancelled group inside a live round still anchors to that round', () => {
  // The round itself is alive — the student can form a new group there, so
  // the historical membership still points them at it.
  const rounds = [round('a', 'REGISTRATION_OPEN')];
  assert.equal(pickPreferredThesisRound(rounds, [group('a', 'CANCELLED')]), 'a');
});

test('groups in unknown rounds are ignored', () => {
  const rounds = [round('a')];
  assert.equal(pickPreferredThesisRound(rounds, [group('ghost')]), undefined);
});

test('thesis page wires the membership-aware round selection', () => {
  const page = fs.readFileSync(
    path.join(root, 'src/app/dashboard/thesis/page.tsx'),
    'utf8',
  );
  assert.match(page, /pickPreferredThesisRound/);
  assert.match(page, /thesisApi\s*\.myGroups\(\)/);
  const api = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  assert.match(api, /\/thesis\/groups\/mine/);
});

test('sortThesisRoundsForOverview prioritizes active registration/execution KLTN cohorts ahead of future proposal-only rounds', () => {
  const rounds = [
    round('b3d980d4', 'PROPOSAL_OPEN', '2027-01-10', 'KLTN'),
    round('22d65ee6', 'REGISTRATION_CLOSED', '2026-09-10', 'TLCN'),
    round('c8f1a234', 'REGISTRATION_CLOSED', '2026-09-15', 'KLTN'),
    round('be1aa853', 'RESULTS_PUBLISHED', '2025-09-01', 'KLTN'),
  ];
  const sorted = sortThesisRoundsForOverview(rounds);
  assert.deepEqual(
    sorted.map((r) => r.id),
    ['c8f1a234', '22d65ee6', 'b3d980d4', 'be1aa853'],
  );
  const deanPage = fs.readFileSync(
    path.join(root, 'src/app/dashboard/dean/page.tsx'),
    'utf8',
  );
  assert.match(deanPage, /sortThesisRoundsForOverview/);
});

test('advisor directory and admin round list share the overview ordering', () => {
  const advisors = fs.readFileSync(
    path.join(root, 'src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx'),
    'utf8',
  );
  assert.match(advisors, /sortThesisRoundsForOverview\(roundsList\)/);
  assert.doesNotMatch(advisors, /roundsList\[0\]/);
  const admin = fs.readFileSync(path.join(root, 'src/app/admin/thesis/page.tsx'), 'utf8');
  assert.match(admin, /setRounds\(sortThesisRoundsForOverview\(mergeThesisRounds\(data, closed\)\)\)/);
});

test('live and archived lists merge to one entry per round id', () => {
  // The unfiltered listRounds() response can already include CLOSED rounds
  // (the e2e fixture returns the same payload for both calls), so a naive
  // concat renders the same round twice and React keys collide downstream.
  const live = [
    round('r-open', 'REGISTRATION_OPEN'),
    round('thesis-round-demo', 'REGISTRATION_CLOSED'),
  ];
  const archived = [
    round('thesis-round-demo', 'CLOSED'),
    round('r-archived', 'CLOSED'),
  ];
  assert.deepEqual(
    mergeThesisRounds(live, archived).map((r) => r.id),
    ['r-open', 'thesis-round-demo', 'r-archived'],
  );
});

test('the live copy wins when both lists describe the same round', () => {
  const liveCopy = round('r1', 'REGISTRATION_OPEN', '2026-09-01');
  const archivedEcho = round('r1', 'CLOSED', '2026-09-01');
  const merged = mergeThesisRounds([liveCopy], [archivedEcho]);
  assert.equal(merged.length, 1);
  assert.equal(merged[0].status, 'REGISTRATION_OPEN');
});

test('an empty archived list keeps the live order untouched', () => {
  const live = [round('a'), round('b')];
  assert.deepEqual(mergeThesisRounds(live, []), live);
});

test('every live+archived merge site dedupes instead of concatenating', () => {
  const sites = [
    'src/app/dashboard/thesis/page.tsx',
    'src/app/dashboard/thesis/[roundId]/page.tsx',
    'src/app/dashboard/dean/page.tsx',
    'src/app/admin/thesis/page.tsx',
    'src/components/dashboard/thesis/useThesisWorkspace.ts',
  ];
  for (const site of sites) {
    const source = fs.readFileSync(path.join(root, site), 'utf8');
    assert.doesNotMatch(
      source,
      /\[\.\.\.(liveRounds|roundsData|data|list), \.\.\.(closedRounds|closedData|closed)\]/,
      `${site} must merge round lists by id`,
    );
  }
});

test('advisor directory action buttons say they open the topic proposal form', () => {
  const advisors = fs.readFileSync(
    path.join(root, 'src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx'),
    'utf8',
  );
  const proposeLinks = advisors.match(/action=propose`\}[\s\S]{0,260}?<\/LinkButton>/g) || [];
  assert.equal(proposeLinks.length, 3);
  for (const link of proposeLinks) {
    assert.doesNotMatch(link, /Chọn GV|Chọn hướng dẫn|'Select'/);
  }
});
