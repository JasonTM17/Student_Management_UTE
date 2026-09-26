const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

async function loadTwoFactorModule() {
  const source = read('src/lib/two-factor.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'two-factor.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('maskEmail mirrors the backend shape first-char + *** @ domain', async () => {
  const { maskEmail } = await loadTwoFactorModule();

  assert.equal(maskEmail('student@campuscore.edu'), 's***@campuscore.edu');
  assert.equal(maskEmail('nguyenvana@gmail.com'), 'n***@gmail.com');
  assert.equal(maskEmail('a@b.co'), 'a***@b.co');
});

test('maskEmail is safe on edge cases and never leaks the local part', async () => {
  const { maskEmail } = await loadTwoFactorModule();

  assert.equal(maskEmail(''), '');
  assert.equal(maskEmail(null), '');
  assert.equal(maskEmail(undefined), '');
  assert.equal(maskEmail('   '), '');
  // No "@" at all: mask everything after the first character.
  assert.equal(maskEmail('noatsign'), 'n***');
  assert.equal(maskEmail('@domainonly'), '@***');
  assert.equal(maskEmail(' padded@mail.com'), 'p***@mail.com');
});

test('normalizeOtpCode keeps digits only and caps at six', async () => {
  const { normalizeOtpCode, TWO_FACTOR_CODE_LENGTH } = await loadTwoFactorModule();

  assert.equal(TWO_FACTOR_CODE_LENGTH, 6);
  assert.equal(normalizeOtpCode('123456'), '123456');
  assert.equal(normalizeOtpCode('12 34 56'), '123456');
  assert.equal(normalizeOtpCode('1-2-3-4'), '1234');
  assert.equal(normalizeOtpCode('abc12x34'), '1234');
  assert.equal(normalizeOtpCode('1234567'), '123456');
  assert.equal(normalizeOtpCode(''), '');
  assert.equal(normalizeOtpCode(null), '');
  assert.equal(normalizeOtpCode(undefined), '');
});

test('isTwoFactorChallenge only accepts a real challenge payload', async () => {
  const { isTwoFactorChallenge } = await loadTwoFactorModule();

  assert.equal(
    isTwoFactorChallenge({
      twoFactorRequired: true,
      challengeId: '9f1c2b3e-0000-4000-8000-000000000001',
      email: 's***@gmail.com',
      user: null,
    }),
    true,
  );
  assert.equal(
    isTwoFactorChallenge({ user: { id: 'u1' }, accessToken: 'a', refreshToken: 'r' }),
    false,
  );
  // A challenge-shaped payload without an id is not a challenge.
  assert.equal(isTwoFactorChallenge({ twoFactorRequired: true }), false);
  assert.equal(isTwoFactorChallenge({ twoFactorRequired: true, challengeId: '' }), false);
  assert.equal(isTwoFactorChallenge({ twoFactorRequired: false, challengeId: 'x' }), false);
  assert.equal(isTwoFactorChallenge(null), false);
  assert.equal(isTwoFactorChallenge(undefined), false);
});

test('api auth namespace exposes the five two-factor endpoints', () => {
  const source = read('src/lib/api.ts');

  assert.match(source, /verifyTwoFactor[\s\S]*?post<LoginResponse>\(\s*'\/auth\/two-factor\/verify'/);
  assert.match(source, /getTwoFactorStatus[\s\S]*?get<\{ enabled: boolean \}>\('\/me\/two-factor'/);
  assert.match(source, /beginTwoFactorEnable[\s\S]*?post<\{ challengeId: string \}>\(\s*'\/me\/two-factor\/enable'/);
  assert.match(source, /confirmTwoFactorEnable[\s\S]*?post<\{ enabled: boolean \}>\(\s*'\/me\/two-factor\/confirm'/);
  assert.match(source, /disableTwoFactor[\s\S]*?post<\{ enabled: boolean \}>\(\s*'\/me\/two-factor\/disable'/);
});

test('both locales carry the two-factor copy for login and profile', () => {
  const source = read('src/i18n/messages.ts');

  // English login + profile blocks.
  assert.match(source, /sentNotice: 'We sent a 6-digit code to \{email\}\.'/);
  assert.match(source, /statusEnabled: 'Enabled'/);
  assert.match(source, /statusDisabled: 'Not enabled'/);
  // Vietnamese login + profile blocks.
  assert.match(source, /sentNotice: 'Mã xác thực 6 chữ số đã được gửi tới \{email\}\.'/);
  assert.match(source, /statusEnabled: 'Đang bật'/);
  assert.match(source, /statusDisabled: 'Chưa bật'/);
});

test('login step two and the profile card are wired to the new flows', () => {
  const login = read('src/app/login/page.tsx');
  const profile = read('src/app/dashboard/profile/page.tsx');

  assert.match(login, /isTwoFactorChallenge\(result\)/);
  assert.match(login, /verifyTwoFactor\(challenge\.challengeId, normalizedCode\)/);
  assert.match(login, /handleVerifySubmit/);
  assert.match(login, /aria-live="polite"/);
  assert.match(login, /autoComplete="one-time-code"/);
  assert.match(login, /normalizeOtpCode\(e\.target\.value\)/);
  for (const code of [
    'TWO_FACTOR_CODE_INVALID',
    'TWO_FACTOR_CODE_LOCKED',
    'TWO_FACTOR_CODE_EXPIRED',
  ]) {
    assert.match(login, new RegExp(`${code}: messages\\.`));
  }

  assert.match(profile, /getTwoFactorStatus/);
  assert.match(profile, /beginTwoFactorEnable/);
  assert.match(profile, /confirmTwoFactorEnable/);
  assert.match(profile, /disableTwoFactor/);
  assert.match(profile, /aria-live="polite"/);
  assert.match(profile, /normalizeOtpCode\(e\.target\.value\)/);
  for (const code of [
    'TWO_FACTOR_CODE_INVALID',
    'TWO_FACTOR_CODE_LOCKED',
    'TWO_FACTOR_CODE_EXPIRED',
    'TWO_FACTOR_ALREADY_ENABLED',
    'MAIL_DELIVERY_FAILED',
  ]) {
    assert.match(profile, new RegExp(`${code}: messages\\.`));
  }
});

test('the one-time code is never persisted or logged', () => {
  const sources = [
    read('src/lib/two-factor.ts'),
    read('src/app/login/page.tsx'),
    read('src/app/dashboard/profile/page.tsx'),
  ].join('\n');

  assert.doesNotMatch(sources, /localStorage|sessionStorage|document\.cookie/);
  assert.doesNotMatch(sources, /console\./);
});
