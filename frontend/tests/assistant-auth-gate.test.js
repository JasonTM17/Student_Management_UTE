const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

const composerSource = fs.readFileSync(
  path.join(root, 'src/components/assistant/AssistantComposer.tsx'), 'utf8');
const panelSource = fs.readFileSync(
  path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
const specializedSource = fs.readFileSync(
  path.join(root, 'src/app/dashboard/assistant-specialized/page.tsx'), 'utf8');
const messagesSource = fs.readFileSync(
  path.join(root, 'src/i18n/messages.ts'), 'utf8');

test('the composer disables its input when the session is unauthorized', () => {
  assert.match(composerSource, /authLocked = false/);
  assert.match(composerSource, /disabled=\{isSending \|\| authLocked\}/);
  // The submit button cannot bypass the lock with a leftover draft either.
  assert.match(composerSource, /disabled=\{!input\.trim\(\) \|\| authLocked\}/);
});

test('the locked composer explains the sign-in requirement via aria-live', () => {
  assert.match(composerSource, /authLocked\s*\?\s*messages\.assistant\.signInRequired/);
  // The hint span is the aria-live region announcing why input stopped working.
  assert.match(composerSource, /aria-live="polite"/);
});

test('the panel routes a 401 answer to sign-in instead of retry', () => {
  assert.match(panelSource, /const authRequired = state\.error === 'unauthorized';/);
  assert.match(panelSource, /authLocked=\{authRequired\}/);
  assert.match(panelSource, /router\.push\(`\$\{href\('\/login'\)\}\?reason=session-expired`\)/);
  // Retry must not be offered for an expired session: it can never succeed.
  assert.match(panelSource, /\{authRequired \? \(/);
  assert.match(panelSource, /messages\.assistant\.signInAction/);
});

test('the specialized page applies the same 401 sign-in gate', () => {
  assert.match(specializedSource, /const authRequired = state\.error === 'unauthorized';/);
  assert.match(specializedSource, /authLocked=\{authRequired\}/);
  assert.match(specializedSource, /router\.push\(`\$\{href\('\/login'\)\}\?reason=session-expired`\)/);
  assert.match(specializedSource, /messages\.assistant\.signInAction/);
});

test('sign-in gate copy exists in both locales', () => {
  assert.match(messagesSource, /signInRequired: 'Asking is locked until you sign in again\.'/);
  assert.match(messagesSource, /signInAction: 'Sign in again'/);
  assert.match(messagesSource, /signInRequired: 'Cần đăng nhập lại trước khi tiếp tục hỏi trợ lý\.'/);
  assert.match(messagesSource, /signInAction: 'Đăng nhập lại'/);
});
