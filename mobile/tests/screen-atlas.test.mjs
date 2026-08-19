import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const routes = fs.readFileSync(path.join(mobileRoot, 'src/navigation/routes.ts'), 'utf8');
const tokens = fs.readFileSync(path.join(mobileRoot, 'src/design/tokens.ts'), 'utf8');
const readme = fs.readFileSync(path.join(mobileRoot, 'README.md'), 'utf8');

test('native registry keeps the Stitch mobile atlas above the 20-screen requirement', () => {
  const screenCount = (routes.match(/\{ name: '/g) ?? []).length;

  assert.equal(screenCount, 23);
  for (const requiredRoute of [
    'auth.signIn',
    'dashboard.student',
    'thesis.topics',
    'thesis.evaluation',
    'assistant.chat',
    'admin.dashboard',
    'lecturer.grading',
  ]) {
    assert.match(routes, new RegExp(`name: '${requiredRoute.replace('.', '\\.')}'`));
  }
  assert.match(readme, /23 navigable screens/);
});

test('native tokens preserve the Academic Continuity contract', () => {
  assert.match(tokens, /background: '#F9F9FF'/);
  assert.match(tokens, /primary: '#003F87'/);
  assert.match(tokens, /primaryContainer: '#0056B3'/);
  assert.match(tokens, /mobileGutter: 16/);
  assert.match(tokens, /touchTarget: 44/);
  assert.match(tokens, /family: 'Be Vietnam Pro'/);
});

test('native API seam fails closed until live mode is explicitly enabled', () => {
  const client = fs.readFileSync(path.join(mobileRoot, 'src/api/client.ts'), 'utf8');

  assert.match(client, /EXPO_PUBLIC_API_MODE === 'live'/);
  assert.match(client, /MOBILE_API_PREVIEW/);
  assert.match(client, /mode: ApiMode/);
});
