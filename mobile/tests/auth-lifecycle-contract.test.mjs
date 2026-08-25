import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const read = (relativePath) => fs.readFileSync(path.join(root, relativePath), 'utf8');

test('mobile auth client uses canonical Java lifecycle requests', () => {
  const client = read('src/api/client.ts');

  assert.match(client, /verifyEmail: '\/auth\/email-verifications\/confirm'/);
  assert.match(client, /resendVerification: '\/auth\/email-verifications\/resend'/);
  assert.match(client, /requestPasswordReset: '\/auth\/password-reset-requests'/);
  assert.match(client, /resetPassword: '\/auth\/password-reset\/confirm'/);
  assert.match(client, /apiClient\.post<AuthActionResponse>\(apiRoutes\.auth\.resetPassword, \{ token, newPassword: password \}\)/);
  assert.doesNotMatch(client, /apiRoutes\.auth\.resetPassword, \{ token, password \}/);
  assert.match(client, /!normalizedPath\.startsWith\('\/auth\/email-verifications'\)/);
  assert.match(client, /!normalizedPath\.startsWith\('\/auth\/password-reset'\)/);
});

test('mobile lifecycle supports deep links, manual fallback and stable errors', () => {
  const app = read('app.json');
  const navigator = read('src/navigation/MobileNavigator.tsx');
  const screens = read('src/screens/auth/AuthLifecycleScreens.tsx');

  assert.match(app, /"scheme": "campuscore"/);
  assert.match(navigator, /Linking\.getInitialURL\(\)/);
  assert.match(navigator, /Linking\.addEventListener\('url'/);
  assert.match(navigator, /rawFragment/);
  assert.match(navigator, /SMTP mail uses a fragment/);
  assert.match(navigator, /setAuthToken\(token\)/);
  assert.match(navigator, /setRoute\('auth\.resetPassword'\)/);
  assert.match(navigator, /setRoute\('auth\.verifyEmail'\)/);
  assert.match(screens, /placeholder="Paste token from email" secureTextEntry/);
  for (const code of [
    'EMAIL_ALREADY_EXISTS',
    'AUTH_CHALLENGE_EXPIRED',
    'AUTH_CHALLENGE_ATTEMPTS_EXCEEDED',
    'AUTH_RESEND_THROTTLED',
    'AUTH_RATE_LIMITED',
  ]) {
    assert.match(screens, new RegExp(code));
  }
});
