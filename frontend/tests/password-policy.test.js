const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');

async function profilePasswordHandler({ rejectChange = false, newPassword = 'SafeReplacement9', waitForRefresh = false } = {}) {
  const sourcePath = process.env.CAMPUSCORE_PROFILE_ROTATION_SOURCE
    || path.join(root, 'src/app/dashboard/profile/page.tsx');
  const source = fs.readFileSync(sourcePath, 'utf8');
  const parsed = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let handler;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(parsed) === 'handlePasswordSubmit') {
      handler = node.initializer;
    }
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  assert.ok(handler && ts.isArrowFunction(handler), 'Actual profile submit handler required');
  const events = [];
  let releaseRefresh;
  const { checkPasswordPolicy } = await loadPolicyModule();
  const values = { oldPassword: 'BeforeLocal9', newPassword, confirmPassword: newPassword };
  const form = { elements: { namedItem: name => ({ value: values[name] }) }, reset: () => events.push('reset') };
  const globals = {
    checkPasswordPolicy,
    user: { email: 'synthetic-profile@example.invalid' },
    setPasswordError: value => events.push(['error', value]),
    setIsUpdatingPassword: value => events.push(['saving', value]),
    setVisiblePasswordFields: () => events.push('hide-fields'),
    authApi: { changePassword: async () => { events.push('change'); if (rejectChange) throw new Error('Synthetic rejection'); } },
    messages: { profile: { passwordUpdated: 'Changed', passwordUpdateFailed: 'Failed' }, common: { campusErrors: { codes: { PASSWORD_TOO_SHORT: 'Too short' } } } },
    toast: { success: () => events.push('success') },
    campusCodeMessage: () => 'Translated rejection',
    // The server keeps the rotating device's session: the handler must
    // re-read the identity and stay in place — never sign out or navigate.
    refreshUser: () => {
      events.push('refreshUser');
      return waitForRefresh ? new Promise(resolve => { releaseRefresh = resolve; }) : Promise.resolve();
    },
    logout: () => {
      events.push('logout');
      return Promise.resolve();
    },
    router: { replace: value => events.push(['redirect', value]) },
  };
  const { outputText } = ts.transpileModule('(' + handler.getText(parsed) + ')', {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
  });
  const invoke = vm.runInNewContext(outputText, globals, { filename: 'profile-password-handler.js' });
  return { events, submit: () => invoke({ preventDefault() {}, currentTarget: form }), release: () => releaseRefresh() };
}

test('profile password success keeps the session and re-reads the identity', async () => {
  const scenario = await profilePasswordHandler({ waitForRefresh: true });
  const pending = scenario.submit();
  await new Promise(resolve => setImmediate(resolve));
  assert.ok(scenario.events.includes('refreshUser'), 'Successful credential replacement must re-read the identity');
  assert.equal(scenario.events.includes('logout'), false, 'rotation must not sign the user out');
  assert.equal(scenario.events.some(event => Array.isArray(event) && event[0] === 'redirect'), false);
  scenario.release();
  await pending;
  assert.ok(scenario.events.indexOf('change') < scenario.events.indexOf('refreshUser'));
  // The handler must not router.replace after the identity refresh resolves:
  // navigating to the login page would kick the still-signed-in user out.
  assert.equal(
    scenario.events.some(event => Array.isArray(event) && event[0] === 'redirect'),
    false,
    'a successful rotation keeps the user in the portal — no navigation at all',
  );
  assert.deepEqual(scenario.events.at(-1), ['saving', false]);
});

test('both rotation callers keep the session instead of signing out', () => {
  for (const rel of [
    'src/app/dashboard/profile/page.tsx',
    'src/components/auth/ForcedPasswordRotationGate.tsx',
  ]) {
    const source = fs.readFileSync(path.join(root, rel), 'utf8');
    assert.ok(
      source.includes('await refreshUser();'),
      `${rel} must re-read the identity after a successful rotation`,
    );
    assert.equal(
      source.includes('logout('),
      false,
      `${rel} must not sign out after a rotation`,
    );
    assert.equal(
      source.includes("'password-rotated'"),
      false,
      `${rel} must not redirect to the login page after rotation`,
    );
  }
});

test('profile password server rejection keeps the current session and inline failure', async () => {
  const scenario = await profilePasswordHandler({ rejectChange: true });
  await scenario.submit();
  assert.ok(scenario.events.includes('change'));
  assert.equal(scenario.events.includes('logout'), false);
  assert.equal(scenario.events.includes('refreshUser'), false);
  assert.equal(scenario.events.some(event => Array.isArray(event) && event[0] === 'redirect'), false);
  assert.ok(scenario.events.some(event => Array.isArray(event) && event[0] === 'error' && event[1] === 'Translated rejection'));
  assert.deepEqual(scenario.events.at(-1), ['saving', false]);
});

test('profile password client validation keeps the current session without a request', async () => {
  const scenario = await profilePasswordHandler({ newPassword: 'short' });
  await scenario.submit();
  assert.equal(scenario.events.includes('change'), false);
  assert.equal(scenario.events.includes('logout'), false);
  assert.equal(scenario.events.includes('refreshUser'), false);
  assert.ok(scenario.events.some(event => Array.isArray(event) && event[0] === 'error' && event[1] === 'Too short'));
  assert.deepEqual(scenario.events.at(-1), ['saving', false]);
});

async function loadPolicyModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/password-policy.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'password-policy.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('short candidates report PASSWORD_TOO_SHORT', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  assert.equal(checkPasswordPolicy(''), 'PASSWORD_TOO_SHORT');
  assert.equal(checkPasswordPolicy('ab1'), 'PASSWORD_TOO_SHORT');
  assert.equal(checkPasswordPolicy('abcdefg'), 'PASSWORD_TOO_SHORT');
  assert.equal(checkPasswordPolicy('a1b2c3d'), 'PASSWORD_TOO_SHORT');
});

test('over-length and over-byte candidates report PASSWORD_TOO_LONG', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  // 129 chars is over the character cap even with digits+letters.
  assert.equal(checkPasswordPolicy('a'.repeat(128) + '1'), 'PASSWORD_TOO_LONG');
  // Just under the byte cap with letter+digit passes all client rules.
  const seventyOneBytes = 'a'.repeat(68) + 'é' + '1'; // 68+2+1 = 71 bytes
  assert.equal(checkPasswordPolicy(seventyOneBytes), null);
});

test('72-byte boundary is measured in UTF-8 bytes, not characters', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  // Exactly 72 bytes passes the byte rule (then needs letter+digit):
  // 70 ASCII letters + 'é' (2 bytes) = 72 bytes, has letter, add '1' → 73.
  const withinBytes = 'a'.repeat(69) + 'é' + '1'; // 69+2+1 = 72 bytes, 71 chars
  assert.equal(checkPasswordPolicy(withinBytes), null);
  // One byte over → TOO_LONG even though only 72 characters long.
  const overBytes = 'a'.repeat(70) + 'é' + '1'; // 70+2+1 = 73 bytes, 72 chars
  assert.equal(checkPasswordPolicy(overBytes), 'PASSWORD_TOO_LONG');
  // Multi-byte heavy string: 40 '€' chars = 120 bytes, well under 128 chars.
  assert.equal(checkPasswordPolicy('€'.repeat(39) + 'a1'), 'PASSWORD_TOO_LONG');
});

test('unicode letters count for the weakness rule', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  assert.equal(checkPasswordPolicy('đặtlại123'), null); // Vietnamese letters + digits
  assert.equal(checkPasswordPolicy('密碼123456'), null); // CJK letters + digits
  assert.equal(checkPasswordPolicy('onlyletters'), 'PASSWORD_TOO_WEAK'); // no digit
  assert.equal(checkPasswordPolicy('12345678!'), 'PASSWORD_TOO_WEAK'); // no letter
  assert.equal(checkPasswordPolicy('        9'), 'PASSWORD_TOO_WEAK'); // spaces are not letters
});

test('identity rule mirrors the backend local-part semantics', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  const email = 'sinhvien@campuscore.edu';
  // equality with full email or local-part, case-insensitive
  assert.equal(checkPasswordPolicy('SinhVien@CampusCore.edu', { email }), 'PASSWORD_CONTAINS_IDENTITY');
  assert.equal(checkPasswordPolicy('SINHVIEN', { email }), 'PASSWORD_CONTAINS_IDENTITY');
  // substring containment applies when the local-part has >= 4 chars
  assert.equal(checkPasswordPolicy('sinhvien1234', { email }), 'PASSWORD_CONTAINS_IDENTITY');
  // short local-part (<4 chars): substring containment must not fire
  assert.equal(checkPasswordPolicy('abc999zz', { email: 'abc@x.edu' }), null);
  // unrelated password passes when email is known
  assert.equal(checkPasswordPolicy('khonglienquan1', { email }), null);
  // without an email the rule is skipped entirely
  assert.equal(checkPasswordPolicy('sinhvien1234'), null);
});

test('email without @ uses the whole trimmed value as the local part', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  assert.equal(checkPasswordPolicy('MyPass9x', { email: 'mypass' }), 'PASSWORD_CONTAINS_IDENTITY');
  assert.equal(checkPasswordPolicy('unrelated9x', { email: 'mypass' }), null);
});

test('reused and confirmation checks run only when the option is supplied', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  assert.equal(
    checkPasswordPolicy('NewPass123', { currentPassword: 'NewPass123' }),
    'PASSWORD_REUSED',
  );
  assert.equal(
    checkPasswordPolicy('NewPass123', { currentPassword: 'OtherPass1' }),
    null,
  );
  assert.equal(
    checkPasswordPolicy('NewPass123', { confirmPassword: 'NewPass124' }),
    'PASSWORD_MISMATCH',
  );
  assert.equal(
    checkPasswordPolicy('NewPass123', { confirmPassword: 'NewPass123' }),
    null,
  );
  // no options → only the candidate rules apply
  assert.equal(checkPasswordPolicy('NewPass123'), null);
});

test('rule precedence matches the backend order', async () => {
  const { checkPasswordPolicy } = await loadPolicyModule();
  const email = 'student@campuscore.edu';
  // short wins over every other rule
  assert.equal(checkPasswordPolicy('stu', { email }), 'PASSWORD_TOO_SHORT');
  // identity reported before weakness (an identity-matching candidate with
  // no digit still reports identity, not weakness)
  assert.equal(
    checkPasswordPolicy('studentx', { email }),
    'PASSWORD_CONTAINS_IDENTITY',
  );
  // too-long wins over identity even when it contains the local part
  assert.equal(
    checkPasswordPolicy('student' + 'a'.repeat(200), { email }),
    'PASSWORD_TOO_LONG',
  );
});

async function loginNoticeMapper(reason) {
  const sourcePath = path.join(root, 'src/app/login/page.tsx');
  const source = fs.readFileSync(sourcePath, 'utf8');
  const parsed = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let memoBody;
  function visit(node) {
    if (
      ts.isCallExpression(node)
      && node.expression.getText(parsed) === 'useMemo'
      && node.getText(parsed).includes('session-expired')
    ) {
      memoBody = node.arguments[0];
    }
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  assert.ok(memoBody && ts.isArrowFunction(memoBody), 'Actual login notice memo required');
  const reasonMessages = {
    sessionExpired: 'SE', unauthorized: 'UA', signedOut: 'SO', passwordRotated: 'PR',
  };
  const { outputText } = ts.transpileModule('(' + memoBody.getText(parsed) + ')()', {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None },
  });
  return vm.runInNewContext(outputText, { reason, messages: { login: { reasonMessages } } }, { filename: 'login-notice.js' });
}

test('login notice maps password-rotated to the localized rotation message', async () => {
  assert.equal(await loginNoticeMapper('password-rotated'), 'PR');
  assert.equal(await loginNoticeMapper('session-expired'), 'SE');
  assert.equal(await loginNoticeMapper('unauthorized'), 'UA');
  assert.equal(await loginNoticeMapper('signed-out'), 'SO');
  assert.equal(await loginNoticeMapper('unknown-reason'), undefined);
  assert.equal(await loginNoticeMapper(''), undefined);
});

test('both locales define the passwordRotated notice title and body', () => {
  const sourcePath = path.join(root, 'src/i18n/messages.ts');
  const source = fs.readFileSync(sourcePath, 'utf8');
  const parsed = ts.createSourceFile(sourcePath, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  const blocks = [];
  function visit(node) {
    if (ts.isPropertyAssignment(node) && node.name.getText(parsed) === 'reasonMessages') {
      blocks.push(node.initializer.getText(parsed));
    }
    ts.forEachChild(node, visit);
  }
  visit(parsed);
  assert.equal(blocks.length, 2, 'exactly the en and vi reasonMessages blocks');
  for (const block of blocks) {
    assert.ok(block.includes('passwordRotated'), 'locale missing passwordRotated');
    const rotated = block.slice(block.indexOf('passwordRotated'));
    assert.ok(rotated.includes('title:'), 'passwordRotated needs a title');
    assert.ok(rotated.includes('body:'), 'passwordRotated needs a body');
  }
});

test('mapPasswordErrorCode only recognizes the known code namespace', async () => {
  const { mapPasswordErrorCode } = await loadPolicyModule();
  for (const code of [
    'PASSWORD_TOO_SHORT', 'PASSWORD_TOO_LONG', 'PASSWORD_CONTAINS_IDENTITY',
    'PASSWORD_TOO_WEAK', 'PASSWORD_TOO_COMMON', 'PASSWORD_REUSED',
    'PASSWORD_MISMATCH', 'RESET_TOKEN_INVALID', 'INVALID_OLD_PASSWORD',
    'RATE_LIMIT_EXCEEDED',
  ]) {
    assert.equal(mapPasswordErrorCode(code), code);
  }
  assert.equal(mapPasswordErrorCode('VALIDATION_ERROR'), null);
  assert.equal(mapPasswordErrorCode(''), null);
  assert.equal(mapPasswordErrorCode(undefined), null);
  assert.equal(mapPasswordErrorCode('password_too_short'), null); // case-sensitive
});
