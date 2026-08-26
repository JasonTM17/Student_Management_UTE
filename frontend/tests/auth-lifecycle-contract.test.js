const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

test('web auth lifecycle exposes canonical and locale routes', () => {
  for (const route of ['register', 'verify-email', 'forgot-password', 'reset-password']) {
    assert.equal(fs.existsSync(path.join(root, 'src/app', route, 'page.tsx')), true, route);
    assert.equal(fs.existsSync(path.join(root, 'src/app/[locale]', route, 'page.tsx')), true, `[locale]/${route}`);
  }
});

test('web auth client matches the Java lifecycle DTO and canonical paths', () => {
  const api = read('src/lib/api.ts');
  const types = read('src/types/api.ts');

  assert.match(api, /'\/auth\/email-verifications\/confirm'/);
  assert.match(api, /'\/auth\/email-verifications\/resend'/);
  assert.match(api, /'\/auth\/password-reset-requests'/);
  assert.match(api, /'\/auth\/password-reset\/confirm'/);
  assert.match(api, /confirmPasswordReset:[\s\S]*?\{ token, newPassword \}/);
  assert.doesNotMatch(api, /password-reset\/confirm', \{ token, password \}/);
  assert.match(types, /expiresInSeconds: number/);
  assert.match(types, /resendAfterSeconds: number/);
  assert.match(types, /interface AuthActionResponse \{\s*message: string;/);
});

test('web token handoff scrubs URLs and disables referrer leakage', () => {
  const pages = read('src/components/auth/AuthLifecyclePages.tsx');
  const nextConfig = read('next.config.mjs');

  assert.match(pages, /window\.history\.replaceState/);
  assert.match(pages, /searchParams\.delete\('token'\)/);
  assert.match(pages, /type="password" value=\{token\}/);
  assert.match(nextConfig, /Referrer-Policy', value: 'no-referrer'/);
  assert.match(nextConfig, /\/:locale\(en\|vi\)\/verify-email/);
  assert.match(nextConfig, /\/:locale\(en\|vi\)\/reset-password/);
});

test('web lifecycle maps stable verification, reset and throttle codes', () => {
  const pages = read('src/components/auth/AuthLifecyclePages.tsx');
  const login = read('src/app/login/page.tsx');

  for (const code of [
    'EMAIL_ALREADY_EXISTS',
    'AUTH_CHALLENGE_EXPIRED',
    'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED',
    'AUTH_RESEND_THROTTLED',
    'AUTH_RATE_LIMITED',
  ]) {
    assert.match(pages, new RegExp(code));
  }
  assert.match(login, /EMAIL_VERIFICATION_REQUIRED/);
  assert.match(login, /messages\.login\.errors\.emailVerificationRequired/);
});

test('web lifecycle distinguishes invalid challenges from service outages and focuses errors', () => {
  const pages = read('src/components/auth/AuthLifecyclePages.tsx');

  assert.match(pages, /function challengeFailureState/);
  assert.match(pages, /AUTH_CHALLENGE_INVALID/);
  assert.match(pages, /return 'unavailable'/);
  assert.match(pages, /verificationUnavailable/);
  assert.match(pages, /unavailableTitle/);
  assert.match(pages, /function useAlertFocus/);
  assert.match(pages, /copy\.errors\.required/);
  assert.match(pages, /copy\.errors\.tooShort/);
});

test('verification outage copy does not claim whether a token was consumed', () => {
  const messages = read('src/i18n/messages.ts');
  assert.match(messages, /verificationUnavailable: 'Verification is temporarily unavailable\.[^']*if it already succeeded, sign in instead\.'/);
  assert.match(messages, /verificationUnavailable: 'Dịch vụ xác minh tạm thời chưa sẵn sàng\.[^']*nếu đã xác minh thành công, hãy đăng nhập\.'/);
  assert.doesNotMatch(messages, /has not been consumed|chưa bị sử dụng/);
});

test('explicit auth state transitions reject stale background refresh commits', () => {
  const { AuthStateFence } = require('../src/context/auth-state-fence.ts');
  const fence = new AuthStateFence();
  let user = null;

  const staleRefresh = fence.capture();
  fence.invalidate();
  user = { email: 'student@example.test' };
  assert.equal(staleRefresh.commit(() => {
    user = null;
  }), false);
  assert.deepEqual(user, { email: 'student@example.test' });
});
