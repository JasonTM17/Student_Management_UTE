const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

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

function pageFunction(file, name, dependencies) {
  const source = read('src/' + file);
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let declaration;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === name) declaration = node;
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(declaration, `page callback ${name} exists`);
  const output = ts.transpileModule(`const ${declaration.getText(tree)};`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
  }).outputText;
  return Function(...Object.keys(dependencies), 'useCallback', `${output}; return ${name};`)(
    ...Object.values(dependencies), fn => fn,
  );
}

// --- behavioral checks: real modules against stubbed boundaries ---

const apiCalls = [];
const fakeApi = {
  delete: async () => ({ data: null }),
  get: async (url, config) => {
    apiCalls.push({ url, config });
    return { data: [] };
  },
  post: async (url, body) => {
    apiCalls.push({ url, body });
    return { data: {} };
  },
};
const apiModule = loadModule('src/lib/thesis-api.ts', {
  '@/lib/api': {
    __esModule: true,
    default: fakeApi,
    API_BASE_URL: 'http://test.invalid',
    createRequestId: () => 'req-test',
    refreshSessionSingleFlight: async () => null,
  },
  '@/lib/assistant-stream': {
    createAssistantSseParser: () => ({}),
    parseAssistantStreamEvent: () => null,
    AssistantStreamOrder: {},
  },
  '@/lib/assistant-stream-helpers': { CANCEL_REQUEST_TIMEOUT_MS: 1000 },
});

test('closeRound POSTs the governance reason to the close endpoint', async () => {
  await apiModule.thesisApi.closeRound('round-7', 'academic year archived');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-7/close',
    body: { reason: 'academic year archived' },
  });
});

test('reopenRound POSTs the governance reason to the reopen endpoint', async () => {
  await apiModule.thesisApi.reopenRound('round-7', 'archive was premature');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-7/reopen',
    body: { reason: 'archive was premature' },
  });
});

test('listRounds passes the status filter only when one is given', async () => {
  await apiModule.thesisApi.listRounds();
  assert.deepEqual(apiCalls.at(-1), { url: '/thesis/rounds', config: { params: {} } });
  await apiModule.thesisApi.listRounds('CLOSED');
  assert.deepEqual(apiCalls.at(-1), { url: '/thesis/rounds', config: { params: { status: 'CLOSED' } } });
});

test('campusCodeMessage maps the new close/reopen codes in both locales', () => {
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const { en, vi } = loadMessages();
  const windowOpen = {
    response: {
      status: 409,
      data: {
        code: 'RETRACT_WINDOW_OPEN',
        details: { retractWindowEndsAt: '2026-11-08T01:00:00Z' },
      },
    },
  };
  const closeAdmin = { response: { status: 403, data: { code: 'CLOSE_ADMIN_REQUIRED' } } };
  const reopenAdmin = { response: { status: 403, data: { code: 'REOPEN_ADMIN_REQUIRED' } } };
  for (const dict of [en, vi]) {
    const copy = dict.common.campusErrors;
    for (const code of ['RETRACT_WINDOW_OPEN', 'CLOSE_ADMIN_REQUIRED', 'REOPEN_ADMIN_REQUIRED']) {
      assert.ok(copy.codes[code], `${code} copy missing`);
    }
    assert.equal(
      campusCodeMessage(windowOpen, copy),
      copy.codes.RETRACT_WINDOW_OPEN.replace(
        '{retractWindowEndsAt}',
        '2026-11-08T01:00:00Z',
      ),
    );
    assert.equal(campusCodeMessage(closeAdmin, copy), copy.codes.CLOSE_ADMIN_REQUIRED);
    assert.equal(campusCodeMessage(reopenAdmin, copy), copy.codes.REOPEN_ADMIN_REQUIRED);
  }
});

const lifecycleRules = loadModule('src/lib/thesis-round-lifecycle.ts');

test('registration opening clamps to now only after lecturer submissions end', () => {
  const now = Date.parse('2026-10-25T02:30:00.000Z');
  const allowed = lifecycleRules.registrationOpeningAvailability({
    lecturerSubmitEnd: '2026-10-25T02:00:00.000Z',
    registrationStart: '2026-10-25T04:00:00.000Z',
    registrationEnd: '2026-10-26T11:00:00.000Z',
  }, now);
  assert.equal(allowed.disabled, false);
  assert.equal(allowed.opensImmediately, true);
  assert.equal(allowed.effectiveStart, new Date(now).toISOString());
  assert.equal(allowed.effectiveEnd, '2026-10-26T11:00:00.000Z');

  const blocked = lifecycleRules.registrationOpeningAvailability({
    lecturerSubmitEnd: '2026-10-25T03:00:00.000Z',
    registrationStart: '2026-10-25T04:00:00.000Z',
    registrationEnd: '2026-10-26T11:00:00.000Z',
  }, now);
  assert.equal(blocked.disabled, true);
  assert.equal(blocked.reasonCode, 'REGISTRATION_EARLY_OPEN_BLOCKED');

  const ended = lifecycleRules.registrationOpeningAvailability({
    lecturerSubmitEnd: '2026-10-24T03:00:00.000Z',
    registrationStart: '2026-10-24T04:00:00.000Z',
    registrationEnd: '2026-10-25T02:00:00.000Z',
  }, now);
  assert.equal(ended.disabled, true);
  assert.equal(ended.reasonCode, 'REGISTRATION_WINDOW_CLOSED');
});

test('results readiness mirrors server blocker precedence and allows explicit all-excluded confirmation', () => {
  const preview = {
    approvedGroupCount: 2,
    includedGroupCount: 2,
    ungradedGroupCount: 1,
    pendingApprovalGroupCount: 0,
    openReviewTopics: [],
  };
  assert.equal(lifecycleRules.publishResultsBlockerCode(preview), 'SCORES_INCOMPLETE');
  assert.equal(lifecycleRules.publishResultsBlockerCode({ ...preview, openReviewTopics: [{}] }), 'REVIEW_WINDOW_OPEN');
  assert.equal(lifecycleRules.publishResultsBlockerCode({ ...preview, approvedGroupCount: 0 }), 'RESULTS_NOT_READY');
  assert.equal(lifecycleRules.publishResultsBlockerCode({
    ...preview,
    ungradedGroupCount: 0,
    pendingApprovalGroupCount: 1,
  }), 'GROUPS_PENDING_APPROVAL');
  assert.equal(lifecycleRules.publishResultsBlockerCode({
    ...preview,
    includedGroupCount: 0,
    ungradedGroupCount: 0,
  }), null, 'the page leaves all-excluded publication to its explicit confirmation');
});

test('registration availability follows the database clock despite browser clock skew', () => {
  const serverNow = '2026-10-25T02:30:00.000Z';
  const sample = lifecycleRules.databaseClockSample(serverNow, 1_000);
  const estimatedServerNow = lifecycleRules.estimatedServerNow(
    sample,
    31_000,
    Date.parse('2026-10-25T07:30:30.000Z'),
  );

  assert.equal(estimatedServerNow, Date.parse(serverNow) + 30_000);
  const availability = lifecycleRules.registrationOpeningAvailability({
    lecturerSubmitEnd: '2026-10-25T02:00:00.000Z',
    registrationStart: '2026-10-25T04:00:00.000Z',
    registrationEnd: '2026-10-26T11:00:00.000Z',
  }, estimatedServerNow);
  assert.equal(availability.disabled, false);
  assert.equal(availability.effectiveStart, new Date(estimatedServerNow).toISOString());
});

test('the GVPB extension blocker follows the report-date ceiling', () => {
  const now = Date.parse('2026-10-25T02:30:00.000Z');
  assert.equal(lifecycleRules.gvpbDeadlineExtensionBlocker('2026-10-25T02:00:00.000Z', now),
    '2026-10-25T02:00:00.000Z');
  assert.equal(lifecycleRules.gvpbDeadlineExtensionBlocker('2026-10-25T03:00:00.000Z', now), null);
  assert.equal(lifecycleRules.gvpbDeadlineExtensionBlocker(null, now), null);
});

test('late-topic availability accounts for the registration end and open status', () => {
  const now = Date.parse('2026-10-25T02:30:00.000Z');
  const registrationStart = '2026-10-25T04:00:00.000Z';
  const registrationEnd = '2026-10-26T11:00:00.000Z';

  assert.deepEqual(lifecycleRules.lateTopicAvailability(
    'PROPOSALS_PUBLISHED', registrationStart, registrationEnd, now,
  ), { disabled: false, reasonCode: null });
  assert.deepEqual(lifecycleRules.lateTopicAvailability(
    'REGISTRATION_OPEN', registrationStart, registrationEnd, now,
  ), { disabled: true, reasonCode: 'REGISTRATION_WINDOW_NOT_OPEN' });
  assert.deepEqual(lifecycleRules.lateTopicAvailability(
    'REGISTRATION_OPEN', registrationStart, registrationEnd, Date.parse(registrationEnd),
  ), { disabled: true, reasonCode: 'REGISTRATION_WINDOW_CLOSED' });
});

test('restore target planning uses the sampled server clock instead of the browser clock', async () => {
  const calls = [];
  const serverNow = Date.parse('2026-10-25T02:30:00.000Z');
  const run = pageFunction('app/admin/thesis/page.tsx', 'restoreRound', {
    thesisApi: { listGroups: async () => [{ status: 'APPROVED' }] },
    currentAdminTime: () => serverNow,
    planThesisRoundRestore: (...args) => {
      calls.push(args);
      return { requiresChoice: true, explicitTargets: ['REGISTRATION_CLOSED'] };
    },
    setError: () => {},
    setModalError: () => {},
    setRestoreTargetRound: () => {},
    setRestoreTargets: () => {},
    setRestoreStatusDraft: () => {},
  });

  await run({ id: 'round-1', status: 'CANCELLED', cancelledFromStatus: 'REGISTRATION_OPEN' });

  assert.equal(calls[0][2], serverNow);
});

test('publish readiness remains associated with each round', () => {
  const blocked = {
    preview: { approvedGroupCount: 1, includedGroupCount: 1, ungradedGroupCount: 1,
      pendingApprovalGroupCount: 0, openReviewTopics: [], excludedTopics: [] },
    loading: false,
    error: '',
  };
  const ready = {
    preview: { approvedGroupCount: 1, includedGroupCount: 1, ungradedGroupCount: 0,
      pendingApprovalGroupCount: 0, openReviewTopics: [], excludedTopics: [] },
    loading: false,
    error: '',
  };
  const roundOne = lifecycleRules.mergePublishResultsReadiness({}, 'round-1', blocked);
  const bothRounds = lifecycleRules.mergePublishResultsReadiness(roundOne, 'round-2', ready);

  assert.equal(lifecycleRules.publishResultsBlockerCode(bothRounds['round-1'].preview), 'RESULTS_NOT_READY');
  assert.equal(lifecycleRules.publishResultsBlockerCode(bothRounds['round-2'].preview), null);
  assert.deepEqual(bothRounds['round-1'], blocked);
});

test('close/reopen UI copy exists in both locales', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of [
      'closeRound',
      'closeRoundTitle',
      'closeRoundHint',
      'closeRoundConfirmLabel',
      'closeBlockedRetractWindow',
      'closeStatusUnavailable',
      'refreshPublishReadiness',
      'publishPreviewUnavailable',
      'roundClosed',
      'reopenRound',
      'reopenRoundTitle',
      'roundReopened',
      'showClosedRounds',
    ]) {
      assert.ok(dict.thesis.admin[key], `thesis.admin.${key} missing`);
    }
  }
});

test('a failed close preview is translated and blocks both confirmation and transition', async () => {
  const calls = [];
  const run = pageFunction('app/admin/thesis/page.tsx', 'transitionRound', {
    messages: {
      common: { actions: { confirm: 'Confirm', cancel: 'Cancel' } },
      thesis: { admin: { transitionConfirmTitle: 'Confirm', transitionConfirm: { closeRegistration: 'Close' } } },
    },
    thesisApi: {
      closeRegistrationPreview: async () => { throw new Error('preview unavailable'); },
      closeRegistration: async () => { calls.push('close'); },
    },
    transitionConfirmCopy: () => 'Close',
    transitionErrorMessage: () => 'Round details could not be checked. Refresh and try again.',
    publishResultsBlockerCode: lifecycleRules.publishResultsBlockerCode,
    confirm: async () => { calls.push('confirm'); return true; },
    setIsSaving: () => {},
    setError: (value) => calls.push(['error', value]),
    setSuccess: () => {},
    loadData: async () => {},
  });

  await run('round-1', 'close');

  assert.equal(calls.includes('confirm'), false);
  assert.equal(calls.includes('close'), false);
  assert.ok(calls.some((call) => Array.isArray(call)
    && call[0] === 'error' && call[1] === 'Round details could not be checked. Refresh and try again.'));
});

test('a results preview blocker prevents the confirmation and publish request', async () => {
  const calls = [];
  const run = pageFunction('app/admin/thesis/page.tsx', 'transitionRound', {
    messages: {
      common: {
        actions: { confirm: 'Confirm', cancel: 'Cancel' },
        campusErrors: { codes: { SCORES_INCOMPLETE: 'Results are incomplete. Finalize every approved group score first.' } },
      },
      thesis: { admin: { transitionConfirmTitle: 'Confirm', transitionConfirm: { publishResults: 'Publish' } } },
    },
    thesisApi: {
      publishResultsPreview: async () => ({
        approvedGroupCount: 2,
        includedGroupCount: 2,
        pendingApprovalGroupCount: 0,
        ungradedGroupCount: 1,
        excludedTopics: [],
        openReviewTopics: [],
      }),
      publishResults: async () => { calls.push('publish'); },
    },
    transitionConfirmCopy: () => 'Publish',
    transitionErrorMessage: () => 'Results are incomplete. Finalize every approved group score first.',
    campusErrorForCode: () => 'Results are incomplete. Finalize every approved group score first.',
    publishBlockerMessage: () => 'Results are incomplete. Finalize every approved group score first.',
    publishResultsBlockerCode: lifecycleRules.publishResultsBlockerCode,
    confirm: async () => { calls.push('confirm'); return true; },
    setIsSaving: () => {},
    setError: (value) => calls.push(['error', value]),
    setSuccess: () => {},
    loadData: async () => {},
    updatePublishReadiness: () => {},
  });

  await run('round-1', 'publish-results');

  assert.equal(calls.includes('confirm'), false);
  assert.equal(calls.includes('publish'), false);
  assert.ok(calls.some((call) => Array.isArray(call)
    && call[0] === 'error' && call[1] === 'Results are incomplete. Finalize every approved group score first.'));
});

test('the admin can refresh publication readiness after resolving a blocker', async () => {
  const calls = [];
  const run = pageFunction('app/admin/thesis/page.tsx', 'refreshPublishResultsPreview', {
    detailRequestRef: { current: 4 },
    expandedRoundRef: { current: 'round-1' },
    thesisApi: {
      publishResultsPreview: async () => ({
        approvedGroupCount: 1,
        includedGroupCount: 1,
        ungradedGroupCount: 0,
        pendingApprovalGroupCount: 0,
        excludedTopics: [],
        openReviewTopics: [],
      }),
    },
    transitionErrorMessage: () => 'Could not refresh publication readiness.',
    updatePublishReadiness: (roundId, update) => calls.push(['readiness', roundId, update]),
  });

  await run('round-1');

  assert.deepEqual(calls[0], ['readiness', 'round-1', { loading: true, error: '' }]);
  const refreshed = calls.find((call) => call[0] === 'readiness' && call[2].preview)?.[2].preview;
  assert.equal(refreshed?.approvedGroupCount, 1);
  assert.deepEqual(calls.at(-1), ['readiness', 'round-1', { loading: false }]);
  assert.match(page, /disabled=\{isSaving \|\| publishReadiness\.loading \|\| Boolean\(publishResultsBlocker\)\}/);
  assert.match(page, /refreshPublishReadiness/);
});

test('a failed results preview is translated and blocks both confirmation and publication', async () => {
  const calls = [];
  const run = pageFunction('app/admin/thesis/page.tsx', 'transitionRound', {
    messages: {
      common: { actions: { confirm: 'Confirm', cancel: 'Cancel' } },
      thesis: { admin: { transitionConfirmTitle: 'Confirm', transitionConfirm: { publishResults: 'Publish' } } },
    },
    thesisApi: { publishResultsPreview: async () => { throw new Error('preview unavailable'); } },
    transitionConfirmCopy: () => 'Publish',
    transitionErrorMessage: () => 'Publication requirements could not be checked. Refresh the round and try again.',
    publishResultsBlockerCode: lifecycleRules.publishResultsBlockerCode,
    confirm: async () => { calls.push('confirm'); return true; },
    setIsSaving: () => {},
    setError: (value) => calls.push(['error', value]),
    setSuccess: () => {},
    loadData: async () => {},
    updatePublishReadiness: () => {},
  });

  await run('round-1', 'publish-results');

  assert.equal(calls.includes('confirm'), false);
  assert.equal(calls.includes('publish'), false);
  assert.ok(calls.some((call) => Array.isArray(call)
    && call[0] === 'error' && call[1] === 'Publication requirements could not be checked. Refresh the round and try again.'));
});

test('opening registration confirms the effective window and immediate start', async () => {
  let confirmation;
  const round = {
    id: 'round-1',
    registrationStart: '2026-10-25T04:00:00.000Z',
    registrationEnd: '2026-10-26T11:00:00.000Z',
    lecturerSubmitEnd: '2026-10-25T02:00:00.000Z',
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'transitionRound', {
    messages: {
      common: { actions: { confirm: 'Confirm', cancel: 'Cancel' } },
      thesis: {
        admin: {
          transitionConfirmTitle: 'Confirm',
          transitionConfirm: { openRegistration: 'Open registration?' },
          openRegistrationEffectiveWindow: 'Effective window: {window}.',
          openRegistrationEarlyHint: 'Registration starts {start}; opening now starts it immediately.',
          registrationOpened: 'Opened',
        },
      },
    },
    thesisApi: { openRegistration: async () => ({}) },
    transitionConfirmCopy: () => 'Open registration?',
    transitionErrorMessage: () => 'Could not open registration.',
    registrationOpeningAvailability: () => ({
      disabled: false,
      reasonCode: null,
      opensImmediately: true,
      effectiveStart: '2026-10-25T02:30:00.000Z',
      effectiveEnd: round.registrationEnd,
    }),
    currentAdminTime: () => Date.parse('2026-10-25T02:30:00.000Z'),
    formatRoundWindow: (_locale, start, end) => `${start} -> ${end}`,
    formatRoundInstant: (_locale, value) => value,
    locale: 'en',
    confirm: async (value) => { confirmation = value; return true; },
    setIsSaving: () => {},
    setError: () => {},
    setSuccess: () => {},
    loadData: async () => {},
  });

  await run('round-1', 'open', round);

  assert.match(confirmation.message, /Effective window: 2026-10-25T02:30:00\.000Z -> 2026-10-26T11:00:00\.000Z\./);
  assert.match(confirmation.message, /Registration starts 2026-10-25T04:00:00\.000Z; opening now starts it immediately\./);
});

// --- page wiring: source assertions pin the close/reopen contract on the
// admin round card, matching the pattern the delete test uses. ---

const page = read('src/app/admin/thesis/page.tsx');

test('the GVPB deadline action explains the report-date limit accessibly', () => {
  assert.match(page, /disabled=\{isSaving \|\| Boolean\(gvpbExtensionUnavailableReason\)\}/);
  assert.match(page, /gvpbExtensionReportDatePassed/);
  assert.match(page, /aria-describedby=\{`gvpb-deadline-reason-\$\{round\.id\}`\}/);
  assert.match(page, /id=\{`gvpb-deadline-reason-\$\{round\.id\}`\} role="status"/);
});

test('the GVPB deadline action is hidden before a supported GVPB phase', () => {
  assert.match(page, /\{\(round\.status === 'PROPOSALS_PUBLISHED' \|\|[\s\S]{0,140}round\.status === 'REGISTRATION_CLOSED'\)\s*&&\s*round\.gvpbDeadline \?/);
});

test('late-topic availability is disabled with an accessible registration-window reason', () => {
  assert.match(page, /lateTopicAvailability\(\s*round\.status,\s*round\.registrationStart,\s*round\.registrationEnd,\s*lifecycleNow,\s*\)/);
  assert.match(page, /disabled=\{isSaving \|\| lateTopicAvailabilityState\.disabled\}/);
  assert.match(page, /aria-describedby=\{`late-topic-reason-\$\{round\.id\}`\}/);
  assert.match(page, /id=\{`late-topic-reason-\$\{round\.id\}`\} role="status"/);
});

test('deadline extension availability and input validation use the sampled database clock', () => {
  assert.match(page, /parsed\.getTime\(\) <= currentAdminTime\(\)/);
  assert.match(page, /const lifecycleNow = currentAdminTime\(\)/);
  assert.match(page, /registrationOpeningAvailability\(round, lifecycleNow\)/);
  assert.match(page, /gvpbDeadlineExtensionBlocker\(round\.reportDate, lifecycleNow\)/);
});

test('a published round keeps Close visible but disables it with an accessible reason when blocked', () => {
  assert.ok(/round\.status === 'RESULTS_PUBLISHED' \? \([\s\S]{0,650}disabled=\{isSaving \|\| !canManageGlobalLifecycle \|\| round\.closure\?\.closeAllowed === false\}/.test(page),
    'published rounds must keep the close button visible and disable it when the server blocks closure');
  assert.ok(/aria-describedby=\{`close-round-reason-\$\{round\.id\}`\}/.test(page),
    'the close button must reference its availability explanation');
  assert.match(page, /kind: 'closeRound', roundId: round\.id, roundName: round\.name/);
});

test('an open retract window explains when the disabled Close action becomes available', () => {
  assert.ok(/round\.closure\.closeBlocker === 'RETRACT_WINDOW_OPEN'/.test(page));
  assert.ok(/closeRoundUnavailableReason/.test(page), 'the button reason must include the grace-window explanation');
  assert.ok(/closeBlockedRetractWindow\.replace\([\s\S]{0,180}retractWindowEndsAt/.test(page));
});

test('the reopen action only renders on a closed round the server allows', () => {
  assert.match(page, /round\.status === 'CLOSED' && round\.closure\?\.reopenAllowed !== false/);
  assert.match(page, /kind: 'reopenRound', roundId: round\.id/);
});

test('closing requires the typed round name; reopening carries only the reason', () => {
  assert.match(page, /action\.kind === 'closeRound'[\s\S]{0,300}retractConfirmation\.trim\(\) !== action\.roundName/);
  assert.match(page, /thesisApi\.closeRound\(action\.roundId, reason\)/);
  assert.match(page, /action\.kind === 'reopenRound'[\s\S]{0,200}thesisApi\.reopenRound\(action\.roundId, reason\)/);
  assert.match(page, /messages\.thesis\.admin\.roundClosed/);
  assert.match(page, /messages\.thesis\.admin\.roundReopened/);
});

test('the archive view is opt-in via the show-closed toggle and the CLOSED filter', () => {
  assert.match(page, /showClosed \? thesisApi\.listRounds\('CLOSED'\)/);
  assert.match(page, /messages\.thesis\.admin\.showClosedRounds/);
});

test('registration dates are labeled and the opening confirm names the effective window', () => {
  assert.match(page, /registrationWindowLabel/);
  assert.match(page, /registrationOpenWindow/);
  assert.match(page, /openRegistrationEffectiveWindow/);
  assert.match(page, /openRegistrationEarlyHint/);
});

test('unavailable registration opening has a disabled reason connected to the button', () => {
  assert.match(page, /disabled=\{isSaving \|\| registrationOpenWindow\.disabled\}/);
  assert.match(page, /aria-describedby=\{`registration-open-reason-\$\{round\.id\}`\}/);
  assert.match(page, /id=\{`registration-open-reason-\$\{round\.id\}`\}/);
  assert.match(page, /setInterval\(\(\) => setAdminClockTick\(\(tick\) => tick \+ 1\), 30_000\)/);
  assert.match(page, /registrationOpeningAvailability\(round, lifecycleNow\)/);
  assert.match(page, /databaseClockSample\(sample, receivedAt\)/);
  assert.match(page, /registrationOpeningAvailability\(round, currentAdminTime\(\)\)/);
  assert.match(page, /publishResultsReadinessForRound\(publishReadinessByRound, round\.id\)/);
});

test('missing publication readiness does not deadlock the publish action after registration closes', () => {
  assert.doesNotMatch(page, /disabled=\{[^}]*!publishResultsPreview/);
  assert.match(page, /publishPreviewUnavailable/);
  assert.match(page, /publishResultsPreview\(roundId\)/);
});

test('lifecycle errors are announced and do not use the generic action-failed copy', () => {
  assert.match(page, /role="alert"[^>]*>[\s\S]{0,120}\{error\}/);
  const start = page.indexOf('const transitionErrorMessage');
  const end = page.indexOf('const roundMutationErrorMessage', start);
  assert.ok(start >= 0 && end > start);
  assert.doesNotMatch(page.slice(start, end), /messages\.thesis\.actionFailed/);
  assert.doesNotMatch(page, /messages\.thesis\.actionFailed/);
});

test('all lifecycle refusal codes have English and Vietnamese campus copy', () => {
  const { en, vi } = loadMessages();
  const codes = [
    'CLOSE_ADMIN_REQUIRED', 'FACULTY_PROFILE_REQUIRED', 'GOVERNANCE_ROLE_REQUIRED',
    'GROUPS_PENDING_APPROVAL', 'REGISTRATION_EARLY_OPEN_BLOCKED',
    'LEADER_CANNOT_BE_REMOVED', 'REGISTRATION_WINDOW_CLOSED', 'REGISTRATION_WINDOW_NOT_OPEN',
    'REOPEN_ADMIN_REQUIRED', 'REVIEW_SCORE_NOT_FOUND', 'ROLE_FORBIDDEN', 'ROUND_CLOSED',
    'RESULTS_ALL_EXCLUDED', 'RESULTS_NOT_READY', 'REVIEW_WINDOW_OPEN',
    'RETRACT_ADMIN_REQUIRED', 'RETRACT_WINDOW_CLOSED', 'RETRACT_WINDOW_OPEN',
    'ROUND_HAS_DATA', 'ROUND_NOT_DELETABLE', 'ROUND_NOT_FOUND', 'ROUND_RESTORE_FROZEN_HISTORY',
    'ROUND_RESTORE_MEMBER_CONFLICT', 'ROUND_RESTORE_TARGET_INVALID', 'ROUND_RESTORE_TARGET_REQUIRED',
    'ROUND_STATE_CONFLICT', 'SCORES_INCOMPLETE', 'THESIS_ROUND_SCHEDULE_ORDER', 'TOPIC_DEPARTMENT_FORBIDDEN',
    'TOPIC_SCOPE_UNRESOLVABLE', 'TOPIC_SUPERVISOR_REQUIRED', 'VALIDATION_ERROR',
  ];
  for (const dict of [en, vi]) {
    for (const code of codes) assert.ok(dict.common.campusErrors.codes[code], `${dict === vi ? 'vi' : 'en'} missing ${code}`);
  }
  assert.match(vi.common.campusErrors.codes.REGISTRATION_EARLY_OPEN_BLOCKED, /Hãy rút ngắn|Hãy chờ/);
  assert.match(vi.thesis.admin.registrationWindowLabel, /Đăng ký/);
});

test('all thesis API error codes have English and Vietnamese campus copy', () => {
  const { en, vi } = loadMessages();
  const sourceRoot = path.resolve(root, '..', 'java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis');
  const serviceRoot = path.join(sourceRoot, 'service');
  const controllerRoot = path.join(sourceRoot, 'controller');
  const sourceFiles = [
    ...fs.readdirSync(serviceRoot)
      .filter((name) => name.endsWith('.java') && !name.includes('Assistant'))
      .map((name) => path.join(serviceRoot, name)),
    ...fs.readdirSync(controllerRoot)
      .filter((name) => name.endsWith('.java'))
      .map((name) => path.join(controllerRoot, name)),
  ];
  const errorCodes = new Set();
  const errorCodePattern = /(?:conflict|notFound)\(\s*"([A-Z][A-Z0-9_]+)"|new DomainException\(\s*(?:[A-Za-z0-9_.]+\.)?HttpStatus\.[A-Z_]+\s*,\s*"([A-Z][A-Z0-9_]+)"/g;

  for (const file of sourceFiles) {
    const source = fs.readFileSync(file, 'utf8');
    for (const match of source.matchAll(errorCodePattern)) {
      errorCodes.add(match[1] ?? match[2]);
    }
  }

  assert.ok(errorCodes.size > 0, 'thesis API error codes were discovered');
  for (const [locale, dict] of [['en', en], ['vi', vi]]) {
    for (const code of errorCodes) {
      assert.ok(dict.common.campusErrors.codes[code], `${locale} missing ${code}`);
    }
  }
});

test('registration refusals are translated with the exact deadline and action for their context', () => {
  const { en, vi } = loadMessages();
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const run = pageFunction('app/admin/thesis/page.tsx', 'transitionErrorMessage', {
    messages: en,
    getThesisErrorCode: (error) => error?.response?.data?.code ?? '',
    getThesisErrorDetails: (error) => error?.response?.data?.details ?? {},
    formatAdminInstant: (value) => `localized:${value}`,
    campusCodeMessage,
  });

  assert.equal(run({
    response: {
      data: {
        code: 'REGISTRATION_EARLY_OPEN_BLOCKED',
        details: { lecturerSubmitEnd: '2026-10-25T02:00:00.000Z' },
      },
    },
  }), 'Lecturer submissions end localized:2026-10-25T02:00:00.000Z. Shorten that window or wait before opening registration.');
  assert.equal(run({
    response: {
      data: {
        code: 'REGISTRATION_WINDOW_CLOSED',
        details: { registrationEnd: '2026-10-26T11:00:00.000Z' },
      },
    },
  }, 'registration-open'), 'Registration ended at localized:2026-10-26T11:00:00.000Z. Edit schedule to set a later end, then open registration.');
  assert.equal(run({
    response: { data: { code: 'REGISTRATION_WINDOW_CLOSED' } },
  }, 'restore'), en.thesis.admin.restoreRegistrationWindowClosed);
  assert.equal(run({
    response: {
      data: {
        code: 'REGISTRATION_WINDOW_CLOSED',
        details: { registrationEnd: '2026-10-26T11:00:00.000Z' },
      },
    },
  }, 'late-topic'), 'Registration ended at localized:2026-10-26T11:00:00.000Z; students cannot join this topic. Keep it for the next round.');
  assert.equal(run({
    response: {
      data: {
        code: 'REGISTRATION_WINDOW_CLOSED',
        details: {
          registrationEnd: '2026-10-26T11:00:00.000Z',
          roundStatus: 'PROPOSALS_PUBLISHED',
        },
      },
    },
  }, 'late-topic'), en.thesis.admin.lateTopicRegistrationClosedBeforeOpen
    .replace('{end}', 'localized:2026-10-26T11:00:00.000Z'));
  assert.match(vi.thesis.admin.lateTopicRegistrationClosedBeforeOpen, /sửa lịch/);
  assert.equal(run({
    response: {
      data: {
        code: 'REGISTRATION_WINDOW_NOT_OPEN',
        details: { registrationStart: '2026-10-25T11:00:00.000Z' },
      },
    },
  }, 'late-topic'), 'Registration starts localized:2026-10-25T11:00:00.000Z; wait until then to add a late topic.');
  assert.equal(run({
    response: {
      data: {
        code: 'RETRACT_WINDOW_OPEN',
        details: { retractWindowEndsAt: '2026-10-30T04:00:00.000Z' },
      },
    },
  }), 'The retraction period remains open through localized:2026-10-30T04:00:00.000Z. Wait until then before closing this round.');
});

test('deadline extension refusals name the violated date rule in both locales', () => {
  const { en, vi } = loadMessages();
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const runWithCopy = (messages) => pageFunction('app/admin/thesis/page.tsx', 'transitionErrorMessage', {
    messages,
    getThesisErrorCode: (error) => error?.response?.data?.code ?? '',
    getThesisErrorDetails: (error) => error?.response?.data?.details ?? {},
    formatAdminInstant: (value) => `localized:${value}`,
    campusCodeMessage,
  });

  const ruleCases = [
    ['REPORT_DEADLINE_NOT_FUTURE', {}, /future/, /tương lai/],
    ['REPORT_DEADLINE_BEFORE_CURRENT', { currentDeadline: '2026-10-30T04:00:00.000Z' }, /on or after localized:/, /từ localized:.*trở đi/],
    ['GVPB_DEADLINE_NOT_FUTURE', {}, /future/, /tương lai/],
    ['GVPB_DEADLINE_BEFORE_CURRENT', { currentDeadline: '2026-10-30T04:00:00.000Z' }, /on or after localized:/, /từ localized:.*trở đi/],
    ['GVPB_DEADLINE_AFTER_REPORT_DATE', { reportDate: '2026-10-30T04:00:00.000Z' }, /on or before the report date \(localized:/, /trước hoặc bằng ngày báo cáo \(localized:/],
  ];
  for (const [rule, details, englishGuidance, vietnameseGuidance] of ruleCases) {
    const error = {
      response: { data: { code: 'VALIDATION_ERROR', details: { rule, ...details } } },
    };
    const english = runWithCopy(en)(error);
    const vietnamese = runWithCopy(vi)(error);
    assert.match(english, englishGuidance, `${rule} must have precise English guidance`);
    assert.match(vietnamese, vietnameseGuidance, `${rule} must have precise Vietnamese guidance`);
    assert.doesNotMatch(english, /cannot complete this action|transitionFailure/i);
    assert.doesNotMatch(vietnamese, /cannot complete this action|transitionFailure/i);
  }

  assert.match(vi.thesis.admin.deadlineExtensionRules.GVPB_DEADLINE_AFTER_REPORT_DATE,
    /phải trước hoặc bằng ngày báo cáo/);
  assert.match(vi.common.campusErrors.codes.ROUND_HAS_DATA, /giữ đợt ở trạng thái đã hủy/);
});

test('global-admin-only lifecycle actions explain the unavailable role', () => {
  assert.match(page, /const canManageGlobalLifecycle = Boolean\(isAdmin \|\| isSuperAdmin\)/);
  assert.match(page, /disabled=\{isSaving \|\| !canManageGlobalLifecycle\}/);
  assert.match(page, /messages\.thesis\.admin\.globalAdminRequired/);
});

// --- closed rounds stay resolvable for results/history while interactive
// pickers default to live rounds. ---

test('roundsForPicker hides archived rounds but keeps the selected one named', () => {
  const { roundsForPicker } = loadModule('src/lib/thesis-round.ts');
  const rounds = [
    { id: 'open-1', status: 'REGISTRATION_OPEN' },
    { id: 'pub-1', status: 'RESULTS_PUBLISHED' },
    { id: 'arch-1', status: 'CLOSED' },
    { id: 'arch-2', status: 'CLOSED' },
  ];
  assert.deepEqual(roundsForPicker(rounds).map((r) => r.id), ['open-1', 'pub-1']);
  assert.deepEqual(
    roundsForPicker(rounds, 'arch-1').map((r) => r.id),
    ['open-1', 'pub-1', 'arch-1'],
    'the viewed archived round must stay in the picker or the select goes blank',
  );
});

test('participant workspaces merge the CLOSED list so results still resolve', () => {
  for (const relative of [
    'src/app/dashboard/thesis/page.tsx',
    'src/app/dashboard/thesis/[roundId]/page.tsx',
    'src/app/dashboard/dean/page.tsx',
    'src/components/dashboard/thesis/useThesisWorkspace.ts',
  ]) {
    assert.match(
      read(relative),
      /listRounds\('CLOSED'\)/,
      `${relative} must merge the archived list or a closed round loses its view`,
    );
  }
});

test('interactive pickers route through roundsForPicker instead of raw rounds', () => {
  const workspacePage = read('src/app/dashboard/thesis/page.tsx');
  assert.match(workspacePage, /roundsForPicker\(rounds, selectedRoundId\)/);
  for (const relative of [
    'src/components/dashboard/thesis/ThesisProgressPage.tsx',
    'src/components/dashboard/thesis/ThesisTopicCatalogPage.tsx',
  ]) {
    assert.match(read(relative), /roundsForPicker\(workspace\.rounds/);
  }
});
