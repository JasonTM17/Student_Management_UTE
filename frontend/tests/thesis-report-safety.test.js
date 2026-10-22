const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const api = read('src/lib/thesis-api.ts');
const page = read('src/app/dashboard/thesis/page.tsx');
const adminPage = read('src/app/admin/thesis/page.tsx');
const messages = read('src/i18n/messages.ts');

// Render the real component and copy; substitute only class-name formatting.
// This follows the Node/TypeScript SSR harness used by announcement tests.
function loadTimelineModule(relative, aliases = {}) {
  const ts = require('typescript');
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

for (const locale of ['en', 'vi']) {
  test(`report timeline never invents a revision from timestamps (${locale})`, () => {
    const React = require('react');
    const { renderToStaticMarkup } = require('react-dom/server');
    const helpers = loadTimelineModule('src/features/thesis/components/thesis-helpers.tsx');
    const copy = loadTimelineModule('src/i18n/messages-thesis-workflow.ts');
    const Timeline = loadTimelineModule('src/features/thesis/components/SubmissionStatusTimeline.tsx', {
      '@/lib/utils': { cn: (...values) => values.filter(Boolean).join(' ') },
      '@/features/thesis/components/thesis-helpers': helpers,
    }).default;
    const submittedAt = '2026-10-01T02:00:00Z';
    const updatedAt = '2026-10-10T06:00:00Z';
    const html = renderToStaticMarkup(React.createElement(Timeline, {
      report: { submittedAt, updatedAt, fileName: 'synthetic-report.pdf', fileSize: 2048 },
      formatDateTime: (value) => value,
      copy: (locale === 'vi' ? copy.thesisWorkflowVi : copy.thesisWorkflowEn).page.submissionStatus,
      locale,
      reportWindowState: 'open',
    }));
    assert.ok(html.includes(submittedAt), 'keep the actual first submission timestamp');
    assert.ok(html.includes(updatedAt), 'keep the actual latest update timestamp');
    assert.ok(html.includes('synthetic-report.pdf'), 'keep the submitted document identity');
    assert.equal(/\(v\d+\)/.test(html), false,
      'Report timestamps do not reveal an edit count; no numbered revision may be invented');
  });
}

function renderTimeline(locale, props) {
  const React = require('react');
  const { renderToStaticMarkup } = require('react-dom/server');
  const helpers = loadTimelineModule('src/features/thesis/components/thesis-helpers.tsx');
  const dictionaries = loadTimelineModule('src/i18n/messages-thesis-workflow.ts');
  const copy = (locale === 'vi' ? dictionaries.thesisWorkflowVi : dictionaries.thesisWorkflowEn).page.submissionStatus;
  const Timeline = loadTimelineModule('src/features/thesis/components/SubmissionStatusTimeline.tsx', {
    '@/lib/utils': { cn: (...values) => values.filter(Boolean).join(' ') },
    '@/features/thesis/components/thesis-helpers': helpers,
  }).default;
  return {
    copy,
    html: renderToStaticMarkup(React.createElement(Timeline, {
      report: null, formatDateTime: (value) => value, locale, copy, ...props,
    })),
  };
}

for (const locale of ['en', 'vi']) {
  test(`report timeline shows an open window without an invented deadline (${locale})`, () => {
    const { html, copy } = renderTimeline(locale, { reportWindowState: 'open', deadlineAt: null, daysRemaining: null });
    assert.ok(html.includes(locale === 'vi' ? 'Đang mở · Chưa đặt hạn nộp' : 'Open · No deadline set'));
    assert.equal(html.includes(copy.notOpenChip), false);
    assert.equal(html.includes(copy.overdueChip), false);
  });

  test(`report timeline preserves unopened lifecycle despite a future deadline (${locale})`, () => {
    const { html, copy } = renderTimeline(locale, {
      reportWindowState: 'notYetOpen', deadlineAt: '2026-11-01T00:00:00Z', daysRemaining: 3,
    });
    assert.ok(html.includes(copy.notOpenChip));
    assert.equal(html.includes(copy.deadlineChip.replace('{days}', '3')), false);
  });

  test(`report timeline labels exact and first-day overdue boundaries independently of rounded days (${locale})`, () => {
    const deadlineMs = Date.parse('2026-10-19T12:00:00Z');
    for (const elapsed of [0, 1, 3_600_000, 86_399_999]) {
      const daysRemaining = Math.ceil(-elapsed / 86_400_000);
      assert.equal(Math.abs(daysRemaining), 0, 'the rounded count cannot distinguish these boundaries');
      const { html, copy } = renderTimeline(locale, {
        reportWindowState: 'deadlinePassed', deadlineAt: new Date(deadlineMs).toISOString(), daysRemaining,
      });
      assert.ok(html.includes(copy.overdueChip), `overdue at +${elapsed}ms`);
      assert.ok(html.includes('bg-status-danger/15'), 'overdue chip uses danger tone');
      assert.equal(html.includes(copy.deadlineChip.replace('{days}', '0')), false);
    }
  });

  test(`report timeline keeps terminal rounds closed with future or absent deadlines (${locale})`, () => {
    for (const deadlineAt of ['2026-11-01T00:00:00Z', null]) {
      const { html, copy } = renderTimeline(locale, {
        reportWindowState: 'closedFinal', deadlineAt, daysRemaining: deadlineAt ? 3 : null,
      });
      assert.ok(html.includes(locale === 'vi' ? 'Đã đóng nộp báo cáo' : 'Submissions closed'));
      assert.equal(html.includes(copy.notOpenChip), false);
      assert.equal(html.includes(copy.deadlineChip.replace('{days}', '3')), false);
    }
  });

  test(`report timeline keeps the localized countdown and published deadline while open (${locale})`, () => {
    const deadlineAt = '2026-11-01T00:00:00Z';
    const { html, copy } = renderTimeline(locale, { reportWindowState: 'open', deadlineAt, daysRemaining: 3 });
    assert.ok(html.includes(copy.deadlineChip.replace('{days}', '3')));
    assert.ok(html.includes(deadlineAt));
    assert.ok(html.includes('bg-status-warning/15'));
  });
}

test('the report timeline receives the form lifecycle authority from its parent', () => {
  const ts = require('typescript');
  const ast = ts.createSourceFile('page.tsx', page, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let timeline;
  const visit = (node) => {
    if (ts.isJsxSelfClosingElement(node) && node.tagName.getText(ast) === 'SubmissionStatusTimeline') timeline = node;
    ts.forEachChild(node, visit);
  };
  visit(ast);
  const state = timeline.attributes.properties.find((attribute) => attribute.name?.getText(ast) === 'reportWindowState');
  assert.equal(state?.initializer?.expression?.getText(ast), 'reportWindowState');
});

test('supervised topic titles use only the already-authorized workload as a directory fallback', () => {
  const ts = require('typescript');
  const ast = ts.createSourceFile('page.tsx', page, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let resolver;
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'getTopicTitle') resolver = node.initializer;
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(resolver, 'execute the actual page resolver');
  const emitted = ts.transpileModule(`module.exports = (${resolver.getText(ast)});`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const load = (topics, lecturerWorkload) => {
    const record = { exports: {} };
    Function('topics', 'lecturerWorkload', 'module', emitted)(topics, lecturerWorkload, record);
    return record.exports;
  };
  const workload = { topics: [{ topicId: 'own-topic', title: 'Đề tài hướng dẫn đã được cấp quyền' }] };
  assert.equal(load([], workload)('own-topic'), workload.topics[0].title);
  assert.equal(load([{ id: 'own-topic', title: 'Directory title' }], workload)('own-topic'), 'Directory title');
  assert.equal(load([], workload)('unknown-topic'), 'unknown-topic');
  assert.equal(load([], null)('unknown-topic'), 'unknown-topic');
  assert.equal(load([], workload)(null), '—');
  assert.equal(load([], workload)(undefined), '—');
});

test('report editing uses the non-destructive PATCH and detach endpoints', () => {
  // PATCH metadata/link edit + DELETE file detach — the file bytes and the
  // submitted_* audit stamp survive both (pinned contract, F4/F7).
  assert.match(api, /updateReport/);
  assert.match(api, /api\.patch<ThesisGroupReport>/);
  assert.match(api, /deleteReportFile/);
  assert.match(api, /api\.delete<ThesisGroupReport>/);
  assert.match(api, /\/thesis\/groups\/' \+ groupId \+ '\/report\/file/);
  assert.match(page, /thesisApi\.updateReport\(currentGroup\.id/);
  assert.match(page, /thesisApi\.deleteReportFile\(currentGroup\.id\)/);
});

test('the metadata PATCH path is never wrapped in the destructive confirm', () => {
  // Only a fresh file replacing a stored document earns the destructive
  // dialog. Slice the existing-report no-file branch of handleSubmitReport
  // and prove it reaches updateReport without any confirm() inside.
  const submitStart = page.indexOf('const handleSubmitReport');
  assert.ok(submitStart > -1, 'handleSubmitReport must exist');
  const patchStart = page.indexOf('} else if (groupReport) {', submitStart);
  assert.ok(patchStart > -1, 'the existing-report PATCH branch must exist');
  const patchEnd = page.indexOf('} else {', patchStart);
  assert.ok(patchEnd > patchStart, 'the first-submission else branch must follow');
  const patchBranch = page.slice(patchStart, patchEnd);
  assert.match(patchBranch, /thesisApi\.updateReport/);
  assert.doesNotMatch(patchBranch, /confirm\(/);
});

test('a file-only report can be edited without providing a link', () => {
  // The old gate demanded a URL even when a stored document already satisfied
  // the artifact invariant — the impossible-edit dead-end (F7).
  assert.match(page, /!url && !groupReport\?\.url && !groupReport\?\.fileName/);
});

test('admin close-registration confirm is backed by the server preview', () => {
  assert.match(api, /closeRegistrationPreview/);
  assert.match(api, /\/thesis\/rounds\/' \+ roundId \+ '\/close-registration-preview/);
  // The preview must load before the confirm dialog opens, and the actual
  // close call comes after the confirm resolves.
  const previewAt = adminPage.indexOf('closeRegistrationPreview(roundId)');
  const confirmAt = adminPage.indexOf('const confirmed = await confirm({', previewAt);
  const closeAt = adminPage.indexOf('thesisApi.closeRegistration(roundId)');
  assert.ok(
    previewAt > -1 && confirmAt > previewAt && closeAt > confirmAt,
    'preview fetch must precede the confirm dialog and the close transition',
  );
  // A preview read failure still allows closing — never block on a read.
  assert.match(adminPage, /\.catch\(\(\) => null\)/);
  // A passed report deadline fills the dialog warning slot.
  assert.match(adminPage, /closePreviewDeadlinePassed/);
});

test('admin exposes the report-deadline escape hatch on closed rounds', () => {
  assert.match(api, /extendReportDeadline/);
  assert.match(api, /\/thesis\/rounds\/' \+ roundId \+ '\/report-deadline/);
  assert.match(adminPage, /thesisApi\.extendReportDeadline\(/);
  assert.match(adminPage, /reportDeadlineExtend/);
});

test('student page keeps a persistent membership banner pointing at the group round', () => {
  // F3: membership is resolved server-side (myGroups) and a banner always
  // offers the way back when the viewed round holds no group of the reader.
  assert.match(page, /thesisApi\s*\.myGroups\(\)/);
  assert.match(page, /pickPreferredThesisRound\(rounds, myGroups\)/);
  assert.match(page, /membershipRoundId !== selectedRoundId/);
  assert.match(page, /membershipRoundHint/);
  assert.match(page, /membershipRoundOpen/);
  assert.equal(
    (messages.match(/membershipRoundHint:/g) || []).length,
    2,
    'membershipRoundHint must be localized in en + vi',
  );
});

test('the report window renders four honest states', () => {
  // notYetOpen / open / deadlinePassed / closedFinal — a published round must
  // never again read "opens after registration closes" (F7).
  assert.match(page, /'notYetOpen' \| 'open' \| 'deadlinePassed' \| 'closedFinal'/);
  assert.match(page, /reportWindowState === 'closedFinal'/);
  assert.match(page, /closedFinalTitle/);
  assert.equal(
    (messages.match(/closedFinalTitle:/g) || []).length,
    2,
    'closedFinalTitle must be localized in en + vi',
  );
});

for (const locale of ['en', 'vi']) {
  test(`supervisor feedback note promises no deadline-driven review the system cannot deliver (${locale})`, () => {
    const { html, copy } = renderTimeline(locale, {
      report: { submittedAt: '2026-10-01T02:00:00Z', updatedAt: '2026-10-01T02:00:00Z', fileName: null },
      reportWindowState: 'open', deadlineAt: null, daysRemaining: null,
    });
    // The report payload carries no feedback field, and a round may have no
    // report deadline at all, so the note must not tie a review to one.
    assert.ok(html.includes(copy.feedbackPendingNote));
    assert.equal(/hạn nộp|deadline|submission window/i.test(copy.feedbackPendingNote), false);
  });
}

test('supervised report fetch requests each group once, even when it is unsubmitted', () => {
  const effect = page.match(/useEffect\(\(\) => \{\s*if \(!isSupervisorOrAdmin \|\| supervisedGroups\.length === 0\) return;[\s\S]*?\}, \[([^\]]*)\]\);/);
  assert.ok(effect, 'supervised report effect not found');
  // A 404 ("not submitted yet") stores no report, so keying the guard on the
  // loaded reports re-fetched every unsubmitted group whenever the effect re-ran.
  assert.match(effect[0], /requested\.has\(g\.id\)/);
  assert.match(effect[0], /requested\.add\(g\.id\)/);
  assert.doesNotMatch(effect[0], /!supervisedReports\[g\.id\]/);
  assert.doesNotMatch(effect[1], /supervisedReports/);
});
