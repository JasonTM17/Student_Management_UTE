const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

// Same fixture pattern as thesis-group-size-rubric.test.js: execute the
// production TSX with TypeScript transpile; only the i18n hook, the
// confirmation dialog and the API boundary are stubbed.
function load(relative, dependencies = {}, cache = new Map()) {
  if (cache.has(relative)) return cache.get(relative);
  const output = ts.transpileModule(fs.readFileSync(path.join(root, relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const record = { exports: {} };
  const resolve = name => {
    if (Object.hasOwn(dependencies, name)) return dependencies[name];
    if (name.startsWith('@/')) {
      const base = `src/${name.slice(2)}`;
      const target = ['.tsx', '.ts'].map(extension => base + extension)
        .find(candidate => fs.existsSync(path.join(root, candidate)));
      assert.ok(target, `local dependency ${name} exists`);
      return load(target, dependencies, cache);
    }
    return require(name);
  };
  Function('require', 'module', 'exports', output)(resolve, record, record.exports);
  cache.set(relative, record.exports);
  return record.exports;
}

const { getMessages } = load('src/i18n/messages.ts');

function elements(tree) {
  if (Array.isArray(tree)) return tree.flatMap(elements);
  if (!React.isValidElement(tree)) return [];
  return [tree, ...elements(tree.props.children), ...elements(tree.props.action)];
}

function textOf(tree) {
  if (Array.isArray(tree)) return tree.map(textOf).join('');
  if (React.isValidElement(tree)) return textOf(tree.props.children);
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}

function button(tree, label) {
  return elements(tree).find(element => typeof element.props.onClick === 'function'
    && textOf(element) === label);
}

function groupOf(count, approvalStatus = 'PENDING', extras = {}) {
  const memberIds = Array.from({ length: count }, (_, index) => `student-${index}`);
  return { id: 'group', roundId: 'round', approvalStatus, status: 'SUBMITTED',
    leaderStudentId: 'student-0', memberStudentIds: memberIds,
    members: memberIds.map(id => ({ studentId: id, displayName: id, isLeader: id === 'student-0' })),
    ...extras };
}

function listFixture(locale, groups, overrides = {}) {
  const messages = getMessages(locale);
  const calls = [];
  const { default: SupervisedGroupList } = load('src/features/thesis/components/SupervisedGroupList.tsx', {
    '@/i18n': { useI18n: () => ({ messages, formatNumber: String }) },
    '@/components/ui/use-confirmation-dialog': { useConfirmationDialog: () => ({
      confirm: async () => true, confirmationDialog: null }) },
    '@/lib/thesis-api': { thesisApi: { addMember: async () => groups[0], removeMember: async () => groups[0] } },
  });
  const tree = SupervisedGroupList({
    messages, title: 'Groups', description: 'desc', groups, reportsByGroup: {},
    isActionPending: false, getTopicTitle: () => 'Topic',
    isRoundOpen: () => true, onGroupUpdated: () => {},
    onApprove: id => calls.push(['approve', id]),
    onReject: id => calls.push(['reject', id]),
    onApproveWithException: overrides.onApproveWithException,
    onWithdrawApproval: id => calls.push(['withdraw', id]),
    onDownloadReport: () => {},
    minGroupSize: overrides.minGroupSize ?? 3,
    canSizeException: overrides.canSizeException ?? false,
    canReviewGroup: overrides.canReviewGroup,
  });
  return { tree, messages, calls, html: renderToStaticMarkup(tree) };
}

test('under-minimum group shows the missing-member state and disables approve for a supervisor', () => {
  const fixture = listFixture('en', [groupOf(2)]);
  assert.ok(fixture.html.includes('Needs 1 more to approve'));
  const approve = button(fixture.tree, 'Approve');
  assert.ok(approve, 'approve control still rendered');
  assert.equal(approve.props.disabled, true);
  assert.equal(Boolean(button(fixture.tree, 'Approve with exception')), false);
});

test('under-minimum group offers the exception action only to governance actors', () => {
  const calls = [];
  const fixture = listFixture('en', [groupOf(2)], {
    canSizeException: true,
    onApproveWithException: id => calls.push(id),
  });
  const action = button(fixture.tree, 'Approve with exception');
  assert.ok(action, 'size-exception action visible to governance');
  action.props.onClick();
  assert.deepEqual(calls, ['group']);
});

test('full roster keeps approve enabled and hides the missing-member hint', () => {
  const fixture = listFixture('en', [groupOf(3)]);
  assert.ok(!fixture.html.includes('more to approve'));
  assert.equal(button(fixture.tree, 'Approve').props.disabled, false);
});

test('withdraw approval appears only when the read model allows it', () => {
  const withdrawable = listFixture('en', [groupOf(3, 'APPROVED', {
    approvalWithdrawal: { withdrawable: true, reports: 0, scores: 0, councilTopics: 0, reviewers: 0, results: 0 },
  })]);
  const action = button(withdrawable.tree, 'Withdraw approval');
  assert.ok(action, 'withdraw action follows the read model flag');
  action.props.onClick();
  assert.deepEqual(withdrawable.calls, [['withdraw', 'group']]);

  const blocked = listFixture('en', [groupOf(3, 'APPROVED', {
    approvalWithdrawal: { withdrawable: false, reports: 1, scores: 0, councilTopics: 0, reviewers: 0, results: 0 },
  })]);
  assert.equal(Boolean(button(blocked.tree, 'Withdraw approval')), false);

  const legacy = listFixture('en', [groupOf(3, 'APPROVED')]);
  assert.equal(Boolean(button(legacy.tree, 'Withdraw approval')), false, 'absent field hides the action');
});

test('withdraw action is hidden when the reviewer cannot act on the group', () => {
  const fixture = listFixture('en', [groupOf(3, 'APPROVED', {
    approvalWithdrawal: { withdrawable: true, reports: 0, scores: 0, councilTopics: 0, reviewers: 0, results: 0 },
  })], { canReviewGroup: () => false });
  assert.equal(Boolean(button(fixture.tree, 'Withdraw approval')), false);
});

for (const locale of ['en', 'vi']) {
  test(`${locale}: new governance copy exists in both locales`, () => {
    const messages = getMessages(locale).thesis;
    for (const key of ['withdrawApproval', 'withdrawApprovalModalTitle',
      'withdrawApprovalModalDescription', 'withdrawApprovalReason',
      'withdrawApprovalReasonPlaceholder', 'withdrawApprovalSuccess',
      'membersNeededShort', 'approveOverrideModalTitle',
      'approveOverrideModalDescription']) {
      assert.equal(typeof messages[key], 'string', `${key} exists`);
      assert.ok(messages[key].trim().length > 0, `${key} is non-empty`);
    }
    const codes = getMessages(locale).common.campusErrors.codes;
    assert.equal(typeof codes.GROUP_WITHDRAWAL_BLOCKED, 'string');
    assert.equal(typeof codes.GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED, 'string');
  });

  test(`${locale}: under-minimum badge and withdraw button translate`, () => {
    const small = listFixture(locale, [groupOf(2)]);
    assert.ok(small.html.includes(small.messages.thesis.membersNeededShort.replace('{count}', '1')));
    const approved = listFixture(locale, [groupOf(3, 'APPROVED', {
      approvalWithdrawal: { withdrawable: true, reports: 0, scores: 0, councilTopics: 0, reviewers: 0, results: 0 },
    })]);
    assert.ok(button(approved.tree, approved.messages.thesis.withdrawApproval));
  });
}

test('page wires the governance modals and endpoint', () => {
  const page = fs.readFileSync(path.join(root, 'src/app/dashboard/thesis/page.tsx'), 'utf8');
  assert.match(page, /withdrawGroupApproval\(/);
  assert.match(page, /GROUP_APPROVAL_OVERRIDE_REASON_REQUIRED/);
  assert.match(page, /minGroupSize=\{selectedRound\?\.minGroupSize/);
  assert.match(page, /canSizeException=\{isAdmin\}/);
  assert.match(page, /onWithdrawApproval=\{openWithdrawModal\}/);
  const api = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  assert.match(api, /withdrawGroupApproval/);
  assert.match(api, /withdraw-approval/);
  assert.match(api, /approvalWithdrawal\?: ThesisGroupWithdrawalInfo/);
  assert.match(api, /minGroupSize\?: number/);
});
