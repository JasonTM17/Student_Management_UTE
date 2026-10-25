const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Same TypeScript transpile harness as thesis-council-modal-errors.test.js:
// real modules against stubbed boundaries; page callbacks executed with their
// closure dependencies injected.
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

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': loadModule('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': loadModule('src/i18n/messages-dean-overview.ts'),
  });
}

const recorder = () => {
  const calls = [];
  const record = (name) => (value) => { calls.push([name, value]); };
  return { calls, record };
};

const {
  firstThesisRoundScheduleViolation,
  parseThesisLocalInput,
  thesisRoundScheduleErrorRule,
} = loadModule('src/lib/thesis-round-schedule.ts');
const { toThesisLocalInput } = loadModule('src/lib/thesis-datetime.ts');
const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
const en = loadMessages().en;

// The production incident: the admin entered 25/10-30/10 for both the lecturer
// window and the student registration window of a KLTN round.
const OVERLAPPING_FORM = {
  formName: 'KLTN 2026',
  formType: 'KLTN',
  formLecturerStart: '2026-10-25T08:00',
  formLecturerEnd: '2026-10-30T17:00',
  formStart: '2026-10-25T08:00',
  formEnd: '2026-10-30T17:00',
  formProposal: '',
  formGvpb: '2026-11-20T17:00',
  formReportDate: '2026-11-25T17:00',
  formDefenseDate: '2026-11-30T17:00',
};

const VALID_KLTN_FORM = {
  formName: 'KLTN 2026',
  formType: 'KLTN',
  formLecturerStart: '2026-10-25T08:00',
  formLecturerEnd: '2026-10-30T17:00',
  formStart: '2026-10-30T17:00',
  formEnd: '2026-11-10T17:00',
  formProposal: '',
  formGvpb: '2026-11-20T17:00',
  formReportDate: '2026-11-25T17:00',
  formDefenseDate: '2026-11-30T17:00',
};

function roundDeps(api, calls, form, extra = {}) {
  const { record } = calls;
  return {
    messages: en,
    campusCodeMessage,
    firstThesisRoundScheduleViolation,
    parseThesisLocalInput,
    thesisRoundScheduleErrorRule,
    roundMutationErrorMessage: (err) => {
      const rule = thesisRoundScheduleErrorRule(err);
      return (rule && en.thesis.admin.scheduleOrder[rule])
        || campusCodeMessage(err, en.common.campusErrors, en.thesis.actionFailed);
    },
    transitionErrorMessage: (err) =>
      campusCodeMessage(err, en.common.campusErrors, en.thesis.actionFailed),
    thesisApi: api,
    needsGvpb: true,
    needsDefense: true,
    editingRoundId: 'round-9',
    expandedRoundId: '',
    setIsSaving: record('setIsSaving'),
    setError: record('setError'),
    setSuccess: record('setSuccess'),
    setModalError: record('setModalError'),
    setShowCreateModal: record('setShowCreateModal'),
    setShowEditRoundModal: record('setShowEditRoundModal'),
    setEditingRoundId: record('setEditingRoundId'),
    setFormName: record('setFormName'),
    setFormType: record('setFormType'),
    setFormStart: record('setFormStart'),
    setFormEnd: record('setFormEnd'),
    setFormLecturerStart: record('setFormLecturerStart'),
    setFormLecturerEnd: record('setFormLecturerEnd'),
    setFormProposal: record('setFormProposal'),
    setFormGvpb: record('setFormGvpb'),
    setFormReportDate: record('setFormReportDate'),
    setFormDefenseDate: record('setFormDefenseDate'),
    loadData: async () => {},
    loadRoundDetail: async () => {},
    ...form,
    ...extra,
  };
}

function apiError(status, data) {
  return Object.assign(new Error('request failed'), { response: { status, data } });
}

// ---------- validator: mirrors ThesisMutationService.createRound/updateRound
// and the thesis_round_schedule_order_valid check constraint ----------

const validSchedule = (overrides = {}) => ({
  lecturerSubmitStart: new Date('2026-12-01T00:00:00Z'),
  lecturerSubmitEnd: new Date('2027-01-15T00:00:00Z'),
  registrationStart: new Date('2027-01-15T00:00:00Z'),
  registrationEnd: new Date('2027-02-15T00:00:00Z'),
  proposalPublishAt: new Date('2027-01-15T00:00:00Z'),
  gvpbDeadline: new Date('2027-03-01T00:00:00Z'),
  reportDate: new Date('2027-03-10T00:00:00Z'),
  defenseDate: new Date('2027-03-20T00:00:00Z'),
  ...overrides,
});

test('a fully ordered schedule passes, including boundary-equal timestamps', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule()), null);
  // The server allows equality across windows (Instant.isBefore rejects only
  // strictly earlier instants; the DB check uses >=).
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    registrationStart: new Date('2027-01-15T00:00:00Z'),
    proposalPublishAt: new Date('2027-01-15T00:00:00Z'),
    gvpbDeadline: new Date('2027-02-15T00:00:00Z'),
    reportDate: new Date('2027-03-01T00:00:00Z'),
    defenseDate: new Date('2027-03-10T00:00:00Z'),
  })), null);
  // Optional dates may be absent.
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    proposalPublishAt: null,
    gvpbDeadline: null,
    reportDate: null,
    defenseDate: null,
  })), null);
});

test('the production incident: registration overlapping the lecturer window is named', () => {
  const violated = firstThesisRoundScheduleViolation(validSchedule({
    lecturerSubmitStart: new Date('2026-10-25T08:00:00'),
    lecturerSubmitEnd: new Date('2026-10-30T17:00:00'),
    registrationStart: new Date('2026-10-25T08:00:00'),
    registrationEnd: new Date('2026-10-30T17:00:00'),
  }));
  assert.equal(violated, 'REGISTRATION_START_BEFORE_LECTURER_END');
});

test('a registration window must end strictly after it starts (requireDates)', () => {
  // requireDates fires for end <= start AND for missing bounds.
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    registrationEnd: new Date('2027-01-15T00:00:00Z'),
  })), 'REGISTRATION_WINDOW');
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    registrationStart: null,
  })), 'REGISTRATION_WINDOW');
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    registrationEnd: new Date('2027-02-14T00:00:00Z'),
    registrationStart: new Date('2027-02-15T00:00:00Z'),
  })), 'REGISTRATION_WINDOW');
});

test('a lecturer window must end strictly after it starts', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    lecturerSubmitEnd: new Date('2026-12-01T00:00:00Z'),
  })), 'LECTURER_WINDOW');
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    lecturerSubmitEnd: null,
  })), 'LECTURER_WINDOW');
});

test('the proposal publish date cannot precede the lecturer window end', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    proposalPublishAt: new Date('2027-01-14T23:59:59Z'),
  })), 'PROPOSAL_PUBLISH_BEFORE_LECTURER_END');
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    proposalPublishAt: null,
  })), null);
});

test('the GVPB deadline cannot precede the registration end', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    gvpbDeadline: new Date('2027-02-14T23:59:59Z'),
  })), 'GVPB_DEADLINE_BEFORE_REGISTRATION_END');
});

test('the report date cannot precede the GVPB deadline', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    reportDate: new Date('2027-02-28T00:00:00Z'),
  })), 'REPORT_DATE_BEFORE_GVPB_DEADLINE');
  // The rule only binds when both values exist, like the server.
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    gvpbDeadline: null,
    reportDate: new Date('2020-01-01T00:00:00Z'),
  })), null);
});

test('the defense date cannot precede the council report date', () => {
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    defenseDate: new Date('2027-03-09T00:00:00Z'),
  })), 'DEFENSE_DATE_BEFORE_REPORT_DATE');
});

test('the first violated rule in the server check order wins', () => {
  // requireDates(registration) is evaluated before requireDates(lecturer).
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    registrationStart: null,
    lecturerSubmitEnd: null,
  })), 'REGISTRATION_WINDOW');
  // The window checks precede the cross-window ordering checks.
  assert.equal(firstThesisRoundScheduleViolation(validSchedule({
    lecturerSubmitStart: new Date('2027-01-20T00:00:00Z'),
    lecturerSubmitEnd: new Date('2027-01-10T00:00:00Z'),
  })), 'LECTURER_WINDOW');
});

test('parseThesisLocalInput keeps seconds and milliseconds, nulls empty or bad input', () => {
  assert.equal(parseThesisLocalInput(''), null);
  assert.equal(parseThesisLocalInput(null), null);
  assert.equal(parseThesisLocalInput('not-a-date'), null);
  const parsed = parseThesisLocalInput('2026-10-25T07:53:12.345');
  assert.equal(parsed.getMilliseconds(), 345);
  assert.equal(parsed.getSeconds(), 12);
});

// ---------- server envelope: details.rule maps back to the same rule ----------

test('thesisRoundScheduleErrorRule reads the rule detail of the stable code', () => {
  const err = apiError(400, {
    code: 'THESIS_ROUND_SCHEDULE_ORDER',
    message: 'registrationStart must not be before lecturerSubmitEnd',
    details: { rule: 'REGISTRATION_START_BEFORE_LECTURER_END' },
  });
  assert.equal(thesisRoundScheduleErrorRule(err), 'REGISTRATION_START_BEFORE_LECTURER_END');
  assert.equal(thesisRoundScheduleErrorRule(apiError(400, { code: 'VALIDATION_ERROR' })), null);
  assert.equal(thesisRoundScheduleErrorRule(apiError(400, {
    code: 'THESIS_ROUND_SCHEDULE_ORDER',
    details: { rule: 'SOMETHING_ELSE' },
  })), null);
  assert.equal(thesisRoundScheduleErrorRule(apiError(409, { code: 'ROUND_STATE_CONFLICT' })), null);
  assert.equal(thesisRoundScheduleErrorRule(new Error('offline')), null);
});

// ---------- page behavior: the admin sees the specific rule, not actionFailed ----------

test('createRound blocks an overlapping schedule before any API call and names the rule', async () => {
  const calls = recorder();
  let apiTouched = false;
  const api = { createRound: async () => { apiTouched = true; return {}; } };
  const run = pageFunction('app/admin/thesis/page.tsx', 'createRound',
    roundDeps(api, calls, OVERLAPPING_FORM));
  await run();
  assert.equal(apiTouched, false, 'an invalid schedule must not reach the API');
  const writes = new Map(calls.calls);
  assert.equal(
    writes.get('setModalError'),
    en.thesis.admin.scheduleOrder.REGISTRATION_START_BEFORE_LECTURER_END,
    'the modal must name the violated schedule rule',
  );
  assert.notEqual(
    writes.get('setModalError'),
    en.thesis.actionFailed,
    'the generic actionFailed copy is not an acceptable answer for a schedule rule',
  );
});

test('createRound surfaces a server VALIDATION_ERROR instead of actionFailed', async () => {
  const calls = recorder();
  const api = {
    createRound: async () => { throw apiError(400, { code: 'VALIDATION_ERROR' }); },
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'createRound',
    roundDeps(api, calls, VALID_KLTN_FORM));
  await run();
  const writes = new Map(calls.calls);
  assert.equal(writes.get('setModalError'), en.common.campusErrors.codes.VALIDATION_ERROR);
  assert.notEqual(writes.get('setModalError'), en.thesis.actionFailed);
});

test('createRound maps the server schedule-order code through details.rule', async () => {
  const calls = recorder();
  const api = {
    createRound: async () => {
      throw apiError(400, {
        code: 'THESIS_ROUND_SCHEDULE_ORDER',
        details: { rule: 'GVPB_DEADLINE_BEFORE_REGISTRATION_END' },
      });
    },
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'createRound',
    roundDeps(api, calls, VALID_KLTN_FORM));
  await run();
  const writes = new Map(calls.calls);
  assert.equal(
    writes.get('setModalError'),
    en.thesis.admin.scheduleOrder.GVPB_DEADLINE_BEFORE_REGISTRATION_END,
  );
});

test('createRound sends the parsed schedule on a valid form', async () => {
  const calls = recorder();
  let payload;
  const api = { createRound: async (body) => { payload = body; return {}; } };
  const run = pageFunction('app/admin/thesis/page.tsx', 'createRound',
    roundDeps(api, calls, VALID_KLTN_FORM));
  await run();
  assert.ok(payload, 'a valid schedule reaches the API');
  assert.equal(payload.registrationStart, new Date(VALID_KLTN_FORM.formStart).toISOString());
  assert.equal(payload.lecturerSubmitEnd, new Date(VALID_KLTN_FORM.formLecturerEnd).toISOString());
  assert.equal(payload.gvpbDeadline, new Date(VALID_KLTN_FORM.formGvpb).toISOString());
});

test('updateRound validates too and keeps sub-minute precision on untouched fields', async () => {
  const calls = recorder();
  let apiTouched = false;
  const api = { updateRound: async () => { apiTouched = true; return {}; } };
  const run = pageFunction('app/admin/thesis/page.tsx', 'updateRound',
    roundDeps(api, calls, OVERLAPPING_FORM));
  await run();
  assert.equal(apiTouched, false, 'edit must not call the API with an invalid schedule');
  const writes = new Map(calls.calls);
  assert.equal(
    writes.get('setModalError'),
    en.thesis.admin.scheduleOrder.REGISTRATION_START_BEFORE_LECTURER_END,
  );
});

test('updateRound round-trips seconds and milliseconds loaded from an existing round', async () => {
  const calls = recorder();
  let payload;
  const api = { updateRound: async (id, body) => { payload = body; return {}; } };
  // An existing round carrying seconds+millis is loaded through toLocalInput;
  // saving without touching the fields must not truncate the values.
  const millisForm = {
    formName: 'KLTN 2026',
    formType: 'KLTN',
    formLecturerStart: toThesisLocalInput('2026-10-25T08:00:11.111Z'),
    formLecturerEnd: toThesisLocalInput('2026-10-30T17:00:22.222Z'),
    formStart: toThesisLocalInput('2026-10-30T17:00:22.222Z'),
    formEnd: toThesisLocalInput('2026-11-10T17:00:33.333Z'),
    formProposal: '',
    formGvpb: toThesisLocalInput('2026-11-20T17:00:44.444Z'),
    formReportDate: toThesisLocalInput('2026-11-25T17:00:55.555Z'),
    formDefenseDate: toThesisLocalInput('2026-11-30T17:00:59.999Z'),
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'updateRound',
    roundDeps(api, calls, millisForm));
  await run();
  assert.ok(payload, 'the valid schedule reaches the API');
  for (const key of ['registrationStart', 'registrationEnd', 'lecturerSubmitStart',
    'lecturerSubmitEnd', 'gvpbDeadline', 'reportDate', 'defenseDate']) {
    const formKey = {
      registrationStart: 'formStart',
      registrationEnd: 'formEnd',
      lecturerSubmitStart: 'formLecturerStart',
      lecturerSubmitEnd: 'formLecturerEnd',
      gvpbDeadline: 'formGvpb',
      reportDate: 'formReportDate',
      defenseDate: 'formDefenseDate',
    }[key];
    assert.equal(
      payload[key],
      new Date(millisForm[formKey]).toISOString(),
      `${key} must round-trip with sub-minute precision`,
    );
    assert.match(payload[key], /\.\d{3}Z$/, `${key} keeps its milliseconds`);
  }
});

// ---------- i18n: both locales carry every rule and the server code ----------

test('schedule-order copy exists in both locales for every rule', () => {
  const { en: enDict, vi: viDict } = loadMessages();
  for (const dict of [enDict, viDict]) {
    for (const key of [
      'REGISTRATION_WINDOW',
      'LECTURER_WINDOW',
      'REGISTRATION_START_BEFORE_LECTURER_END',
      'PROPOSAL_PUBLISH_BEFORE_LECTURER_END',
      'GVPB_DEADLINE_BEFORE_REGISTRATION_END',
      'REPORT_DATE_BEFORE_GVPB_DEADLINE',
      'DEFENSE_DATE_BEFORE_REPORT_DATE',
    ]) {
      assert.ok(dict.thesis.admin.scheduleOrder[key], `thesis.admin.scheduleOrder.${key} missing`);
    }
    assert.ok(
      dict.common.campusErrors.codes.THESIS_ROUND_SCHEDULE_ORDER,
      'THESIS_ROUND_SCHEDULE_ORDER fallback copy missing',
    );
  }
});

// ---------- page wiring: form order, minute precision, alert semantics ----------

const page = read('src/app/admin/thesis/page.tsx');

const createModal = page.slice(
  page.indexOf('isOpen={showCreateModal}'),
  page.indexOf('{/* Create Council Modal */}'),
);
const editModal = page.slice(
  page.indexOf('isOpen={showEditRoundModal}'),
  page.indexOf('{/* Assign Topic Modal */}'),
);

test('the schedule fields are listed chronologically in both round modals', () => {
  for (const [name, modal] of [['create', createModal], ['edit', editModal]]) {
    const lecturer = modal.indexOf('messages.thesis.admin.lecturerWindow');
    const proposal = modal.indexOf('messages.thesis.admin.proposalPublishAt');
    const registration = modal.indexOf('messages.thesis.admin.registrationStart');
    const gvpb = modal.indexOf('messages.thesis.admin.gvpbDeadline');
    const report = modal.indexOf('messages.thesis.admin.reportDate}');
    const defense = modal.indexOf('messages.thesis.admin.defenseDate}');
    assert.ok(
      lecturer > -1 && lecturer < proposal && proposal < registration
        && registration < gvpb && gvpb < report && report < defense,
      `${name} modal must order: lecturer window -> proposal publish -> registration -> GVPB -> report -> defense`,
    );
  }
});

test('round form datetime inputs use minute precision', () => {
  for (const [name, modal] of [['create', createModal], ['edit', editModal]]) {
    const fields = modal.match(/<DateTime24Field\b/g) || [];
    assert.equal(fields.length, 8, `${name} modal still carries all eight datetime fields`);
    assert.doesNotMatch(modal, /type="datetime-local"/,
      `${name} modal must use the 24-hour date/time field, not the locale-dependent native control`);
  }
  // DateTime24Field only composes 'YYYY-MM-DDTHH:mm' — there is no seconds or
  // milliseconds surface left to misconfigure.
  assert.doesNotMatch(createModal, /step="0\.001"/);
  assert.doesNotMatch(editModal, /step="0\.001"/);
});

test('modal errors are announced to assistive technology', () => {
  for (const [name, modal] of [['create', createModal], ['edit', editModal]]) {
    const errorBlock = modal.slice(modal.indexOf('{modalError ?'), modal.indexOf('{modalError ?') + 400);
    assert.match(errorBlock, /role="alert"/, `${name} modal error banner needs role="alert"`);
  }
});

test('createRound no longer swallows the server error into actionFailed', () => {
  const createRoundBody = page.match(/const createRound = async \(\) => \{[\s\S]*?\n  \};/)[0];
  assert.doesNotMatch(createRoundBody, /\} catch \{\s*\}/, 'the bare catch is gone');
  assert.match(createRoundBody, /roundMutationErrorMessage\(err\)/);
});

test('updateRound runs the same schedule validation and error mapping', () => {
  const updateRoundBody = page.match(/const updateRound = async \(\) => \{[\s\S]*?\n  \};/)[0];
  assert.match(updateRoundBody, /firstThesisRoundScheduleViolation|scheduleViolation/);
  assert.match(updateRoundBody, /roundMutationErrorMessage\(err\)/);
});

test('hidden fields of a MON_HOC round cannot block submission with stale values', async () => {
  // Switching the thesis type hides the conditional date fields but keeps
  // their state; only the fields the server will receive may be validated.
  const calls = recorder();
  let payload;
  const api = { createRound: async (body) => { payload = body; return {}; } };
  const staleHiddenForm = {
    ...VALID_KLTN_FORM,
    formType: 'MON_HOC',
    formGvpb: '2020-01-01T00:00',       // would violate every order rule
    formReportDate: '2020-01-01T00:00', // if it were validated or sent
    formDefenseDate: '2020-01-01T00:00',
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'createRound',
    roundDeps(api, calls, staleHiddenForm, { needsGvpb: false, needsDefense: false }));
  await run();
  assert.ok(payload, 'hidden stale values must not block a MON_HOC create');
  assert.equal(payload.gvpbDeadline, undefined);
  assert.equal(payload.reportDate, undefined);
  assert.equal(payload.defenseDate, undefined);
});
