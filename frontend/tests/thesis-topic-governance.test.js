const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

function executeDeclaration(file, name, dependencies) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8');
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let declaration;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === name) declaration = node;
    if (ts.isFunctionDeclaration(node) && node.name?.getText(tree) === name) declaration = node;
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(declaration, `${file} contains ${name}`);
  const text = ts.isVariableDeclaration(declaration)
    ? `const ${declaration.getText(tree)};`
    : declaration.getText(tree).replace(/^export\s+/, '');
  const output = ts.transpileModule(text, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
  }).outputText;
  return Function(...Object.keys(dependencies), 'useCallback', `${output}; return ${name};`)(
    ...Object.values(dependencies), fn => fn,
  );
}

function proposalFixture(roles, overrides = {}) {
  const calls = [];
  let success = '';
  const created = {
    id: 'topic', roundId: 'round', departmentId: 'department', title: 'Title',
    description: 'Description', maxGroups: 2, status: 'DRAFT', createdBy: 'owner',
  };
  const dependencies = {
    proposeRoundId: 'round', selectedRoundId: 'round', proposeTitle: 'Title',
    proposeDescription: 'Description', proposeDepartmentId: 'department', proposeMaxGroups: 2,
    proposePublishImmediately: true, proposeSupervisorId: 'lecturer', proposeSecondSupervisorId: '',
    isActionPending: false, canProposeTopics: true,
    isTopicApprover: roles.some(role => ['ADMIN', 'SUPER_ADMIN', 'TRUONG_KHOA', 'TRUONG_BO_MON'].includes(role)),
    messages: { thesis: {
      publishedSuccess: 'Approved', proposeSuccess: 'Waiting for department approval',
      supervisorAssignFailed: 'Supervisor assignment failed', supervisorInvalid: 'Invalid supervisor',
      actionFailed: 'Action failed', topicApprovalForbidden: 'Approval permission missing',
    } },
    departments: [{ id: 'department' }],
    thesisApi: {
      createTopic: async () => { calls.push('create'); return created; },
      setSupervisors: async () => { calls.push('supervisors'); },
      publishTopic: async () => { calls.push('publish'); return { ...created, status: 'PUBLISHED' }; },
    },
    refreshTopics: async () => { calls.push('refresh'); },
    setIsActionPending: () => {}, setProposeError: () => {},
    setActionSuccess: value => { success = value; },
    setSelectedRoundId: () => {}, setIsProposeModalOpen: () => {},
    setProposeTitle: () => {}, setProposeDescription: () => {}, setProposeDepartmentId: () => {},
    setProposeMaxGroups: () => {}, setProposePublishImmediately: () => {},
    setProposeSupervisorId: () => {}, setProposeSecondSupervisorId: () => {},
    setActionError: () => {}, topicActionErrorMessage: () => 'Approval failed',
    setError: () => {},
    toast: { error: () => {} }, getThesisErrorCode: error => error.response?.data?.code || '',
    ...overrides,
  };
  const submit = executeDeclaration('app/dashboard/thesis/page.tsx', 'handleProposeTopic', dependencies);
  return { submit: () => submit({ preventDefault: () => {} }), calls, success: () => success };
}

test('a plain lecturer creates a pending draft even if stale publish state is true', async () => {
  const fixture = proposalFixture(['LECTURER']);
  await fixture.submit();
  assert.deepEqual(fixture.calls, ['create', 'supervisors', 'refresh']);
  assert.equal(fixture.success(), 'Waiting for department approval');
});

test('governance publishes only after the selected supervisors have been assigned', async () => {
  const fixture = proposalFixture(['ADMIN']);
  await fixture.submit();
  assert.deepEqual(fixture.calls, ['create', 'supervisors', 'publish', 'refresh']);
  assert.equal(fixture.success(), 'Approved');
});

test('standalone department heads can enter the lecturer portal', () => {
  const matches = executeDeclaration('lib/login-portal.ts', 'portalMatchesUser', {
    userRoles: user => user.roles,
  });
  assert.equal(matches('lecturer', { roles: ['TRUONG_BO_MON'] }), true);
  assert.equal(matches('admin', { roles: ['TRUONG_BO_MON'] }), false);
  assert.equal(matches('student', { roles: ['TRUONG_BO_MON'] }), false);
});

const draft = {
  id: 'topic', roundId: 'round', departmentId: 'department', title: 'Draft topic',
  description: 'Description', maxGroups: 2, status: 'DRAFT', createdBy: 'other-owner',
};
const capabilities = executeDeclaration('lib/thesis-api.ts', 'getThesisTopicCapabilities', {});
function context(roles, overrides = {}) {
  return {
    roles, userId: 'owner', lecturerId: 'lecturer', roundStatus: 'PROPOSAL_OPEN',
    inDepartmentCatalog: true, hasHistory: false, ...overrides,
  };
}

test('only an owner lecturer can edit or archive, and ownership never grants publication', () => {
  const own = { ...draft, createdBy: 'owner' };
  assert.deepEqual(capabilities(own, context(['LECTURER'])), {
    canEdit: true, canPublish: false, canRequestArchive: true, canArchive: true,
  });
  assert.deepEqual(capabilities(draft, context(['LECTURER'])), {
    canEdit: false, canPublish: false, canRequestArchive: false, canArchive: false,
  });
  assert.equal(capabilities({ ...own, createdBy: 'lecturer' }, context(['LECTURER'])).canEdit, true);
});

test('department heads approve a server-scoped draft without editing someone else’s content', () => {
  const ownDepartment = capabilities(draft, context(['TRUONG_BO_MON']));
  assert.equal(ownDepartment.canPublish, true);
  assert.equal(ownDepartment.canEdit, false);
  assert.equal(ownDepartment.canArchive, true);
  const outside = capabilities(draft, context(['TRUONG_BO_MON'], { inDepartmentCatalog: false }));
  assert.equal(outside.canPublish, false);
  assert.equal(outside.canArchive, false);
  assert.equal(capabilities(draft, context(['LECTURER', 'TRUONG_BO_MON'], { inDepartmentCatalog: false })).canPublish, false);
});

test('admin, super-admin and faculty head approval roles preserve their global scope', () => {
  for (const role of ['ADMIN', 'SUPER_ADMIN', 'TRUONG_KHOA']) {
    assert.deepEqual(capabilities(draft, context([role], { inDepartmentCatalog: false })), {
      canEdit: true, canPublish: true, canRequestArchive: true, canArchive: true,
    });
  }
  assert.deepEqual(capabilities(draft, context(['STUDENT'])), {
    canEdit: false, canPublish: false, canRequestArchive: false, canArchive: false,
  });
});

test('approval closes with the catalog phase, while topic history blocks archival', () => {
  assert.equal(capabilities(draft, context(['TRUONG_BO_MON'], { roundStatus: 'PROPOSALS_PUBLISHED' })).canPublish, false);
  const inUse = capabilities({ ...draft, status: 'PUBLISHED' }, context(['ADMIN'], { hasHistory: true }));
  assert.equal(inUse.canRequestArchive, true);
  assert.equal(inUse.canArchive, false);
  assert.equal(inUse.canPublish, false);
  assert.deepEqual(capabilities({ ...draft, status: 'ARCHIVED' }, context(['ADMIN'])), {
    canEdit: false, canPublish: false, canRequestArchive: false, canArchive: false,
  });
});

test('a failed supervisor assignment leaves the saved draft pending and never publishes it', async () => {
  const calls = [];
  const fixture = proposalFixture(['ADMIN'], {
    thesisApi: {
      createTopic: async () => { calls.push('create'); return draft; },
      setSupervisors: async () => { calls.push('supervisors'); throw new Error('503'); },
      publishTopic: async () => { calls.push('publish'); },
    },
  });
  await fixture.submit();
  assert.deepEqual(calls, ['create', 'supervisors']);
  assert.equal(fixture.success(), 'Waiting for department approval');
  assert.deepEqual(fixture.calls, ['refresh']);
});

test('a rejected immediate approval preserves the new draft instead of inviting a second create', async () => {
  let closed = false;
  let approvalError = '';
  const fixture = proposalFixture(['ADMIN'], {
    thesisApi: {
      createTopic: async () => draft, setSupervisors: async () => {},
      publishTopic: async () => { throw { response: { status: 403 } }; },
    },
    setIsProposeModalOpen: value => { closed = !value; },
    setActionError: value => { approvalError = value; },
  });
  await fixture.submit();
  assert.equal(closed, true);
  assert.equal(approvalError, 'Approval failed');
  assert.equal(fixture.success(), 'Waiting for department approval');
  assert.deepEqual(fixture.calls, ['refresh']);
});

test('a proposal refresh outage closes the saved form and preserves the pending-draft success', async () => {
  let closed = false;
  let loadFailed = false;
  const fixture = proposalFixture(['LECTURER'], {
    refreshTopics: async () => { throw new Error('503'); },
    setIsProposeModalOpen: value => { closed = !value; },
    setError: () => { loadFailed = true; },
  });
  await fixture.submit();
  assert.equal(closed, true);
  assert.equal(loadFailed, true);
  assert.equal(fixture.success(), 'Waiting for department approval');
});

const thesisCopy = {
  archiveTopicTitle: 'Archive topic?', archiveTopicConfirm: 'Archive {title}?',
  archiveTopic: 'Archive', archiveTopicSuccess: 'Archived', publishedSuccess: 'Approved',
  loadFailed: 'Catalog unavailable', topicInUse: 'History must be preserved',
  topicStateConflict: 'Topic state changed', roundNotAcceptingProposals: 'Catalog closed',
  lecturerWindowNotOpen: 'Proposal window not open', lecturerWindowClosed: 'Proposal window closed',
  topicActionForbidden: 'Permission changed', actionFailed: 'Action failed',
};

function mutationFixture(overrides = {}) {
  const calls = [];
  let topics = [draft];
  let viewingTopic = draft;
  let pending = false;
  let confirmationOpen = false;
  let topicError = '';
  let success = '';
  let loadError = '';
  const errorMessage = executeDeclaration('app/dashboard/thesis/page.tsx', 'topicActionErrorMessage', {
    messages: { thesis: thesisCopy }, getThesisErrorCode: error => error.response?.data?.code || '',
  });
  const dependencies = {
    topics, isActionPending: false,
    topicCapabilities: topic => capabilities(topic, context(['TRUONG_BO_MON'])),
    thesisApi: {
      archiveTopic: async id => { calls.push(`archive:${id}`); return { ...draft, status: 'ARCHIVED' }; },
      publishTopic: async id => { calls.push(`publish:${id}`); return { ...draft, status: 'PUBLISHED' }; },
    },
    confirm: async () => { calls.push('confirm'); return true; },
    refreshTopics: async () => { calls.push('refresh'); },
    messages: { thesis: thesisCopy, common: { actions: { cancel: 'Cancel' } } },
    fillCopy: (copy, values) => copy.replace('{title}', values.title),
    setIsActionPending: value => { pending = value; },
    setIsArchiveConfirmationOpen: value => { confirmationOpen = value; },
    setTopicActionError: value => { topicError = value; }, setActionError: () => {},
    setActionSuccess: value => { success = value; }, setError: value => { loadError = value; },
    setTopics: update => { topics = update(topics); },
    setViewingTopic: update => { viewingTopic = update(viewingTopic); },
    toast: { success: () => {}, error: () => {} }, topicActionErrorMessage: errorMessage,
    ...overrides,
  };
  return {
    archive: executeDeclaration('app/dashboard/thesis/page.tsx', 'handleArchiveTopic', dependencies),
    publish: executeDeclaration('app/dashboard/thesis/page.tsx', 'handlePublishTopic', dependencies),
    calls, state: () => ({ topics, viewingTopic, pending, confirmationOpen, topicError, success, loadError }),
  };
}

test('approval replaces the modal’s draft with the returned published topic', async () => {
  const fixture = mutationFixture();
  await fixture.publish(draft.id);
  assert.deepEqual(fixture.calls, ['publish:topic', 'refresh']);
  assert.equal(fixture.state().viewingTopic.status, 'PUBLISHED');
  assert.equal(fixture.state().topics[0].status, 'PUBLISHED');
  assert.equal(fixture.state().success, 'Approved');
  assert.equal(fixture.state().pending, false);
});

test('a forged lecturer publish action makes no API call', async () => {
  const fixture = mutationFixture({ topicCapabilities: () => ({ canPublish: false }) });
  await fixture.publish(draft.id);
  assert.deepEqual(fixture.calls, []);
  assert.equal(fixture.state().viewingTopic.status, 'DRAFT');
});

test('cancelled archival sends no mutation and restores the topic dialog', async () => {
  const fixture = mutationFixture({ confirm: async () => false });
  await fixture.archive(draft);
  assert.deepEqual(fixture.calls, []);
  assert.equal(fixture.state().viewingTopic, draft);
  assert.equal(fixture.state().topics.length, 1);
  assert.equal(fixture.state().pending, false);
  assert.equal(fixture.state().confirmationOpen, false);
});

test('successful archival removes the topic and closes the detail modal', async () => {
  const fixture = mutationFixture();
  await fixture.archive(draft);
  assert.deepEqual(fixture.calls, ['confirm', 'archive:topic', 'refresh']);
  assert.deepEqual(fixture.state().topics, []);
  assert.equal(fixture.state().viewingTopic, null);
  assert.equal(fixture.state().success, 'Archived');
  assert.equal(fixture.state().pending, false);
});

test('an in-use archival conflict keeps the topic and shows localized history copy', async () => {
  const fixture = mutationFixture({
    thesisApi: { archiveTopic: async () => { throw { response: { status: 409, data: { code: 'TOPIC_IN_USE' } } }; } },
  });
  await fixture.archive(draft);
  assert.equal(fixture.state().topics.length, 1);
  assert.equal(fixture.state().viewingTopic, draft);
  assert.equal(fixture.state().topicError, 'History must be preserved');
  assert.equal(fixture.state().pending, false);
});

test('an approval 403 stays in the draft dialog and exposes no raw response text', async () => {
  const fixture = mutationFixture({
    thesisApi: { publishTopic: async () => { throw { response: { status: 403, data: { message: 'private provider details' } } }; } },
  });
  await fixture.publish(draft.id);
  assert.equal(fixture.state().viewingTopic.status, 'DRAFT');
  assert.equal(fixture.state().topicError, 'Permission changed');
  assert.equal(fixture.state().pending, false);
});

test('a refresh outage preserves a committed approval and reports only the catalog load failure', async () => {
  const fixture = mutationFixture({ refreshTopics: async () => { throw new Error('503'); } });
  await fixture.publish(draft.id);
  assert.equal(fixture.state().success, 'Approved');
  assert.equal(fixture.state().viewingTopic.status, 'PUBLISHED');
  assert.equal(fixture.state().topicError, '');
  assert.equal(fixture.state().loadError, 'Catalog unavailable');
});

test('pending actions and protected history never open another archive confirmation', async () => {
  for (const overrides of [
    { isActionPending: true },
    { topicCapabilities: () => ({ canArchive: false }) },
  ]) {
    const fixture = mutationFixture(overrides);
    await fixture.archive(draft);
    assert.deepEqual(fixture.calls, []);
  }
});

test('department-head-only workspace loading avoids group, council and repository endpoints', async () => {
  const calls = [];
  let groups;
  const load = executeDeclaration('app/dashboard/thesis/page.tsx', 'loadWorkspace', {
    selectedRoundId: 'round', cancelled: false, isStudent: false, canLoadStaffRelations: false,
    setError: () => {}, refreshTopics: async () => { calls.push('topics'); },
    thesisApi: {
      listGroups: async () => { calls.push('groups'); return []; },
      listCouncils: async () => { calls.push('councils'); return []; },
      listRoundRepository: async () => { calls.push('repository'); return []; },
    },
    setGroups: value => { groups = value; }, setIsRepositoryLoading: () => {},
    setCouncils: () => {}, loadCouncilDetails: () => {}, setRepositoryReports: () => {},
    setRepositoryError: () => {}, messages: { thesis: thesisCopy },
  });
  await load();
  assert.deepEqual(calls, ['topics']);
  assert.deepEqual(groups, []);
});

test('unused approved topics expose archive to governance and owners while history stays protected', () => {
  const approved = { ...draft, status: 'APPROVED', createdBy: 'owner' };
  for (const roles of [['ADMIN'], ['TRUONG_BO_MON'], ['LECTURER']]) {
    assert.equal(capabilities(approved, context(roles)).canArchive, true);
    assert.equal(capabilities(approved, context(roles, { hasHistory: true })).canArchive, false);
  }
  assert.equal(capabilities(approved, context(['STUDENT'])).canArchive, false);
});

test('approved topic archive callback confirms and removes the saved topic', async () => {
  const approved = { ...draft, status: 'APPROVED' };
  const fixture = mutationFixture();
  await fixture.archive(approved);
  assert.deepEqual(fixture.calls, ['confirm', 'archive:topic', 'refresh']);
  assert.equal(fixture.state().topics.length, 0);
  assert.equal(fixture.state().viewingTopic, null);
});

test('department-head sign-in routes to thesis and preserves lecturer/student/dean routes', async () => {
  for (const [portal, roles, expected] of [
    ['lecturer', ['TRUONG_BO_MON'], '/dashboard/thesis'],
    ['lecturer', ['LECTURER'], '/dashboard/lecturer'],
    ['lecturer', ['LECTURER', 'TRUONG_KHOA'], '/dashboard/lecturer'],
    ['student', ['STUDENT'], '/dashboard'],
    ['admin', ['ADMIN'], '/admin'],
    ['dean', ['TRUONG_KHOA'], '/dashboard/dean'],
    ['dean', ['LECTURER', 'TRUONG_KHOA'], '/dashboard/dean'],
  ]) {
    let destination;
    const route = executeDeclaration('app/login/page.tsx', 'finishLogin', {
      portal, portalMatchesUser: () => true, logout: async () => {}, setFormError: () => {},
      portalCopy: {}, router: { push: value => { destination = value; } }, href: value => value,
      postLoginRoute: value => ({ lecturer: '/dashboard/lecturer', student: '/dashboard', admin: '/admin', dean: '/dashboard/dean' })[value],
    });
    await route({ roles });
    assert.equal(destination, expected);
  }
});

test('a privileged account signed in from the wrong tab routes to its own portal', async () => {
  // An ADMIN/SUPER_ADMIN/TRUONG_KHOA account submitting a non-matching form is
  // never refused: the session is kept and routed to the account's own portal.
  for (const [portal, roles, belongsTo, expected] of [
    ['student', ['ADMIN'], 'admin', '/admin'],
    ['lecturer', ['SUPER_ADMIN'], 'admin', '/admin'],
    ['student', ['TRUONG_KHOA'], 'dean', '/dashboard/dean'],
    ['lecturer', ['TRUONG_KHOA'], 'dean', '/dashboard/dean'],
    ['admin', ['TRUONG_KHOA'], 'dean', '/admin/thesis'],
    ['dean', ['ADMIN'], 'admin', '/admin'],
  ]) {
    let destination;
    let loggedOut = false;
    const route = executeDeclaration('app/login/page.tsx', 'finishLogin', {
      portal,
      portalMatchesUser: () => false,
      portalFromUser: () => belongsTo,
      logout: async () => { loggedOut = true; },
      setFormError: () => {},
      setMismatchPortal: () => {},
      portalCopy: {},
      router: { push: value => { destination = value; } },
      href: value => value,
      postLoginRoute: value => ({ lecturer: '/dashboard/lecturer', student: '/dashboard', admin: '/admin', dean: '/dashboard/dean' })[value],
    });
    await route({ roles });
    assert.equal(destination, expected);
    assert.equal(loggedOut, false, 'the privileged session must be kept');
  }
});

test('a non-privileged account on the wrong tab is still refused', async () => {
  let destination;
  let loggedOut = false;
  let mismatchPortal;
  const route = executeDeclaration('app/login/page.tsx', 'finishLogin', {
    portal: 'lecturer',
    portalMatchesUser: () => false,
    portalFromUser: () => 'student',
    logout: async () => { loggedOut = true; },
    setFormError: () => {},
    setMismatchPortal: value => { mismatchPortal = value; },
    portalCopy: {},
    router: { push: value => { destination = value; } },
    href: value => value,
    postLoginRoute: value => ({ lecturer: '/dashboard/lecturer', student: '/dashboard', admin: '/admin' })[value],
  });
  await route({ roles: ['STUDENT'] });
  assert.equal(loggedOut, true, 'the mismatched session must be dropped');
  assert.equal(destination, undefined, 'no navigation may happen on refusal');
  assert.equal(mismatchPortal, 'student');
});

test('department heads retain the lecturer portal for session redirects', () => {
  const portal = executeDeclaration('lib/login-portal.ts', 'portalFromUser', { userRoles: user => user.roles });
  assert.equal(portal({ roles: ['TRUONG_BO_MON'] }), 'lecturer');
  assert.equal(portal({ roles: ['TRUONG_BO_MON', 'ADMIN'] }), 'admin');
  assert.equal(portal({ roles: ['STUDENT'] }), 'student');
});
