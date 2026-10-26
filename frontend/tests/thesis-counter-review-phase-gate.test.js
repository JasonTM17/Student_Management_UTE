const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const ADMIN_PAGE = 'src/app/admin/thesis/page.tsx';
const MESSAGES = 'src/i18n/messages.ts';
const CAMPUS_ERROR = 'src/lib/campus-error.ts';

async function loadCampusErrorModule() {
  const source = fs.readFileSync(path.join(root, CAMPUS_ERROR), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
    fileName: 'campus-error.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('rounds without a counter-review phase hide the reviewer picker', () => {
  const page = read(ADMIN_PAGE);
  // The combobox + assign button only render while the round actually
  // carries a counter-review deadline; otherwise one short line explains it.
  assert.match(page, /round\.gvpbDeadline \? \(\s*<div className="flex items-center gap-1\.5">\s*<Combobox/);
  assert.match(page, /noCounterReviewPhase/);
  assert.match(page, /\{messages\.thesis\.admin\.noCounterReviewPhase\}/);
});

test('the waiver button stays hidden when the topic has nothing to waive', () => {
  const page = read(ADMIN_PAGE);
  // A stored waiver must always be revocable, but granting a waiver needs a
  // real counter-review requirement: a round deadline or a reviewer row.
  assert.match(
    page,
    /top\.gvpbWaivedReason != null \|\|[\s\S]{0,120}round\.counterReviewWaiverAllowed &&[\s\S]{0,120}round\.gvpbDeadline != null \|\| reviewerId !== ''/,
  );
});

test('reviewer assignment errors resolve through the central code map', () => {
  const page = read(ADMIN_PAGE);
  const handler = page.match(/const handleAssignReviewer = async[\s\S]*?\n  \};/);
  assert.ok(handler, 'handleAssignReviewer exists');
  // No per-code ternary may shadow the central map: a known backend code
  // must surface its localized copy instead of generic validation text.
  assert.match(handler[0], /setError\(transitionErrorMessage\(err\)\)/);
  assert.doesNotMatch(handler[0], /SUPERVISOR_CANNOT_REVIEW\s*\?/);
});

test('both new refusal codes are localized in English and Vietnamese', () => {
  const messages = read(MESSAGES);
  assert.match(messages, /ROUND_HAS_NO_COUNTER_REVIEW:\s*'This round type has no counter-review phase\.'/);
  assert.match(messages, /TOPIC_HAS_NO_COUNTER_REVIEW:\s*'This topic has no counter-review requirement to waive\.'/);
  assert.match(messages, /ROUND_HAS_NO_COUNTER_REVIEW:\s*'Loại đợt này không có giai đoạn phản biện\.'/);
  assert.match(messages, /TOPIC_HAS_NO_COUNTER_REVIEW:\s*'Đề tài này không có yêu cầu phản biện để miễn\.'/);
  assert.match(messages, /noCounterReviewPhase: 'This round has no counter-review phase\.'/);
  assert.match(messages, /noCounterReviewPhase: 'Đợt này không có giai đoạn phản biện\.'/);
});

test('campusCodeMessage surfaces the mapped copy for the new codes', async () => {
  const { campusCodeMessage } = await loadCampusErrorModule();
  const copy = {
    validation: 'Check the fields.',
    conflict: 'State conflict.',
    unknown: 'Unknown.',
    codes: {
      ROUND_HAS_NO_COUNTER_REVIEW: 'This round type has no counter-review phase.',
      TOPIC_HAS_NO_COUNTER_REVIEW: 'This topic has no counter-review requirement to waive.',
    },
  };
  const conflict = (code) => ({ response: { status: 409, data: { code, message: 'backend text' } } });
  assert.equal(
    campusCodeMessage(conflict('ROUND_HAS_NO_COUNTER_REVIEW'), copy, 'fallback'),
    'This round type has no counter-review phase.',
  );
  assert.equal(
    campusCodeMessage(conflict('TOPIC_HAS_NO_COUNTER_REVIEW'), copy, 'fallback'),
    'This topic has no counter-review requirement to waive.',
  );
  // An unmapped code still falls back to the kind copy, never a crash.
  assert.equal(campusCodeMessage(conflict('UNMAPPED_CODE'), copy, 'fallback'), 'State conflict.');
});
