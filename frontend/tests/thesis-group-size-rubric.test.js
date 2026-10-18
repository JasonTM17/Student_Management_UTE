const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

// Execute the production TSX and UI primitives. Only routing, state storage,
// dialogs and the network boundary are supplied by the fixture.
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

function groupOf(count, approvalStatus = 'PENDING') {
  const members = Array.from({ length: count }, (_, index) => ({
    studentId: `student-${index}`, displayName: `Member ${index}`,
    identifier: `code-${index}`, isLeader: index === 0, isExternal: false,
  }));
  return { id: 'group', roundId: 'round', approvalStatus, leaderStudentId: 'student-0',
    memberStudentIds: members.map(member => member.studentId), members };
}

function studentFixture(count, locale, overrides = {}) {
  const messages = getMessages(locale);
  const calls = [];
  const group = groupOf(count);
  const { default: StudentGroupCard } = load('src/features/thesis/components/StudentGroupCard.tsx', {
    '@/i18n': { useI18n: () => ({ messages }) },
    '@/components/LocalizedLink': { LocalizedLink: props => React.createElement('a', props) },
  });
  const tree = StudentGroupCard({ messages, group, members: group.members,
    groupStatusLabel: 'Registered', roleLabel: 'Leader', topicTitle: 'Topic', supervisors: [],
    canCreateGroup: true, canManageMembers: true, isActionPending: false,
    onCreateGroup: () => calls.push('create'), onAddMember: () => calls.push('add'),
    onRemoveMember: id => calls.push(['remove', id]), ...overrides });
  return { tree, messages, calls, html: renderToStaticMarkup(tree) };
}

function lecturerFixture(count, locale, overrides = {}) {
  const messages = getMessages(locale);
  const group = groupOf(count, overrides.approvalStatus);
  const calls = [];
  const states = [];
  let cursor = 0;
  const { SupervisedGroupMembers } = load('src/components/dashboard/thesis/SupervisedGroupMembers.tsx', {
    react: { ...React, useState: initial => {
      const index = cursor++;
      if (!(index in states)) states[index] = initial;
      return [states[index], value => {
        states[index] = typeof value === 'function' ? value(states[index]) : value;
      }];
    } },
    '@/i18n': { useI18n: () => ({ messages, formatNumber: String }) },
    '@/components/ui/use-confirmation-dialog': { useConfirmationDialog: () => ({
      confirm: async options => { calls.push(['confirm', options]); return overrides.confirm !== false; },
      confirmationDialog: null,
    }) },
    '@/lib/thesis-api': { thesisApi: {
      addMember: async (id, member) => { calls.push(['add', id, member]); return group; },
      removeMember: async (id, member) => { calls.push(['remove', id, member]); return group; },
    } },
  });
  const render = () => {
    cursor = 0;
    return SupervisedGroupMembers({ group, roundOpen: overrides.roundOpen ?? true,
      onChanged: updated => calls.push(['changed', updated.id]) });
  };
  const collapsed = render();
  elements(collapsed).find(element => element.props['aria-expanded'] === false).props.onClick();
  return { render, messages, calls };
}

for (const locale of ['en', 'vi']) {
  for (const count of [1, 2, 3, 4, 5, 6]) {
    test(`${locale}: student and lecturer controls use a five-member capacity at ${count} members`, () => {
      const student = studentFixture(count, locale);
      const add = button(student.tree, student.messages.thesis.addMember);
      assert.equal(Boolean(add), count < 5, 'student add control follows capacity');
      if (add) {
        assert.equal(add.props.disabled, false);
        add.props.onClick();
        assert.deepEqual(student.calls, ['add']);
      }
      assert.ok(student.html.includes(student.messages.thesis.memberCount.replace('{count}', String(count))));
      assert.equal(student.html.includes(student.messages.thesis.maxMembersReached), count >= 5);

      const lecturer = lecturerFixture(count, locale);
      const tree = lecturer.render();
      assert.equal(Boolean(button(tree, lecturer.messages.thesis.memberSearchAction)), count < 5,
        'lecturer search/add controls follow capacity');
      const html = renderToStaticMarkup(tree);
      assert.ok(html.includes(`(${count}/5)`));
      assert.equal(html.includes(lecturer.messages.thesis.maxMembersReached), count >= 5);
    });
  }

  test(`${locale}: pending drafts explain the missing members without an institutional policy claim`, () => {
    const messages = getMessages(locale).thesis;
    for (const count of [1, 2]) {
      const student = studentFixture(count, locale);
      assert.ok(student.html.includes(messages.membersNeededForApproval.replace('{count}', String(3 - count))));
    }
    assert.match(messages.groupTooSmall, locale === 'en' ? /3 to 5/ : /3 đến 5/);
    assert.match(messages.groupTooSmall, locale === 'en' ? /1 or 2/ : /1 hoặc 2/);
    assert.doesNotMatch(messages.groupTooSmall + messages.progressDataIntegrity,
      /HCM.?UTE|academic regulations|quy (?:chế|định).*đào tạo/i);
    assert.match(messages.progressDataIntegrity, locale === 'en' ? /3 to 5/ : /3 đến 5/);
    const complete = studentFixture(3, locale);
    assert.ok(!complete.html.includes(messages.membersNeededForApproval.replace('{count}', '0')));
  });
}

for (const count of [2, 3, 4, 5]) {
  test(`lecturer removal keeps an approved ${count}-member roster at or above three`, async () => {
    const lecturer = lecturerFixture(count, 'en', { approvalStatus: 'APPROVED' });
    const tree = lecturer.render();
    const remove = elements(tree).filter(element => element.props['aria-label']?.startsWith('Remove:'));
    assert.equal(remove.length, count - 1, 'leader remains protected');
    assert.ok(remove.every(element => element.props.disabled === (count <= 3)));
    remove[0].props.onClick();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(lecturer.calls.filter(call => call[0] === 'remove').length, count > 3 ? 1 : 0);
    assert.equal(lecturer.calls.filter(call => call[0] === 'confirm').length, count > 3 ? 1 : 0);
  });
}

test('pending drafts can remove nonleaders while closed rounds and pending actions remain blocked', async () => {
  for (const overrides of [{}, { roundOpen: false }]) {
    const lecturer = lecturerFixture(2, 'en', overrides);
    const tree = lecturer.render();
    const remove = elements(tree).find(element => element.props['aria-label'] === 'Remove: Member 1');
    assert.equal(remove.props.disabled, Boolean(overrides.roundOpen === false));
    remove.props.onClick();
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(lecturer.calls.filter(call => call[0] === 'remove').length, overrides.roundOpen === false ? 0 : 1);
    if (overrides.roundOpen === false) {
      assert.equal(Boolean(button(tree, lecturer.messages.thesis.memberSearchAction)), false);
    }
  }
  const student = studentFixture(4, 'en', { isActionPending: true });
  assert.equal(button(student.tree, student.messages.thesis.addMember).props.disabled, true);
  assert.ok(elements(student.tree).filter(element => element.props['aria-label'] === 'Remove')
    .every(element => element.props.disabled));
  assert.match(student.html, /<button[^>]*disabled=""/);
});

test('student authority gates still depend on leader, round and approval status', () => {
  const source = fs.readFileSync(path.join(root, 'src/app/dashboard/thesis/page.tsx'), 'utf8');
  const ast = ts.createSourceFile('page.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let initializer;
  const visit = node => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'canManageMembers') initializer = node.initializer;
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(initializer, 'production member-authority rule exists');
  const rule = ts.transpileModule(initializer.getText(ast), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const evaluate = Function('currentGroup', 'user', 'isRegistrationWindowOpen', 'useMemo', `return ${rule}`);
  const openWindow = true;
  const closedWindow = false;
  for (const [studentId, windowOpen, approvalStatus, groupStatus, expected] of [
    ['student-0', openWindow, 'PENDING', 'SUBMITTED', true],
    ['student-1', openWindow, 'PENDING', 'SUBMITTED', false],
    ['student-0', closedWindow, 'PENDING', 'SUBMITTED', false],
    ['student-0', openWindow, 'APPROVED', 'SUBMITTED', false],
    ['student-0', openWindow, 'PENDING', 'CANCELLED', false],
  ]) {
    const canManageMembers = evaluate(
      { ...groupOf(4, approvalStatus), status: groupStatus },
      { studentId },
      windowOpen,
      fn => fn());
    assert.equal(canManageMembers, expected);
    const student = studentFixture(4, 'en', { canManageMembers });
    assert.equal(Boolean(button(student.tree, student.messages.thesis.addMember)), expected);
    assert.equal(elements(student.tree).filter(element => element.props['aria-label'] === 'Remove').length,
      expected ? 3 : 0);
  }
});

test('lecturer removal still requires explicit confirmation', async () => {
  const lecturer = lecturerFixture(4, 'en', { approvalStatus: 'APPROVED', confirm: false });
  elements(lecturer.render()).find(element => element.props['aria-label'] === 'Remove: Member 1').props.onClick();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(lecturer.calls.filter(call => call[0] === 'confirm').length, 1);
  assert.equal(lecturer.calls.filter(call => call[0] === 'remove').length, 0);
});

test('real group full modal uses the current five-member policy in both locales', () => {
  const source = fs.readFileSync(path.join(root, 'src/app/dashboard/thesis/page.tsx'), 'utf8');
  const ast = ts.createSourceFile('page.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let initializer;
  const visit = node => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'memberCopy') initializer = node.initializer;
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(initializer, 'the actual GROUP_FULL modal copy exists');
  const output = ts.transpileModule(`module.exports = (${initializer.getText(ast)});`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  for (const locale of ['vi', 'en']) {
    const record = { exports: {} };
    Function('locale', 'messages', 'module', output)(locale, getMessages(locale), record);
    assert.match(record.exports.groupFull, /\b5\b/, `${locale} real modal states maximum five`);
    assert.doesNotMatch(record.exports.groupFull, /\b3\b/, `${locale} no stale maximum three`);
  }
});

test('workflow guidance uses the approved three-to-five policy and allows incomplete drafts', () => {
  const strings = value => typeof value === 'string' ? [value]
    : value && typeof value === 'object' ? Object.values(value).flatMap(strings) : [];
  for (const locale of ['vi', 'en']) {
    const messages = getMessages(locale);
    const workflow = messages.thesisWorkflow;
    assert.doesNotMatch(strings(workflow).join('\n') + messages.roundMilestone.phase2Desc,
      /up to 3 students|at most 3 students|tối đa 3 (?:SV|sinh viên)/i,
      `${locale} workflow and handbook must not advertise a three-member capacity`);
    assert.match(messages.roundMilestone.phase2Desc, /3–5/);
    assert.match(workflow.stepper.stages[2].shortDesc, /3–5/);
    assert.match(workflow.stepper.stages[2].actions[0], /3–5/);
    assert.match(workflow.stepper.stages[2].actions[0], locale === 'en' ? /1 or 2/ : /1–2/);
    assert.match(workflow.stepper.authorityNote, locale === 'en' ? /project policy/ : /chính sách dự án/);
  }
});

test('supervised group roster controls stay closed on cancelled groups', () => {
  const source = fs.readFileSync(
    path.join(root, 'src/features/thesis/components/SupervisedGroupList.tsx'), 'utf8');
  assert.match(source, /roundOpen=\{isRoundOpen\(group\)\s*&&\s*group\.status\s*!==\s*'CANCELLED'\}/,
    'SupervisedGroupMembers must not offer add/remove controls on a cancelled group');
});
