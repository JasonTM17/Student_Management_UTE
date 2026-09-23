import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');
const script = path.join(root, 'scripts', 'supabase', 'assistant-knowledge.mjs');
const seed = path.join(root, 'supabase', 'seed', 'assistant-specialized-knowledge.json');

test('direct import cannot bypass governed assistant review', () => {
  const result = spawnSync(process.execPath, [script, 'import', '--file', seed], {
    cwd: root,
    encoding: 'utf8',
    env: {
      ...process.env,
      SUPABASE_URL: 'http://127.0.0.1:9',
      SUPABASE_SERVICE_ROLE_KEY: 'test-only-key',
      SUPABASE_TABLE: 'assistant.knowledge_document',
    },
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /direct table import is disabled/);
  assert.doesNotMatch(result.stderr, /fetch failed|ECONNREFUSED/);
});

test('offline validation of the specialized seed stays available', () => {
  const result = spawnSync(process.execPath,
    [script, 'validate', '--file', seed, '--expected-count', '24'],
    { cwd: root, encoding: 'utf8' });
  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /VALID: 24 document/);
});
