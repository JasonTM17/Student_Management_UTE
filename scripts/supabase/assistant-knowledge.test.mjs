import { execFileSync } from 'node:child_process';
import { readFile, unlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';

const root = path.resolve(import.meta.dirname, '../..');
const command = path.join(root, 'scripts/supabase/assistant-knowledge.mjs');
const seed = path.join(root, 'supabase/seed/assistant-knowledge.json');

function run(args) {
  return execFileSync(process.execPath, [command, ...args], {
    cwd: root,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}

test('curated seed validates with globally unique document slugs', () => {
  assert.match(
    run(['validate', '--file', seed, '--expected-count', '10']),
    /no duplicate slugs/,
  );
});

test('authoring validation rejects a bilingual duplicate slug', async () => {
  const documents = JSON.parse(await readFile(seed, 'utf8'));
  documents.push({
    ...documents[0],
    id: '22222222-2222-4222-8222-222222222222',
    locale: documents[0].locale === 'en' ? 'vi' : 'en',
  });
  const duplicateFile = path.join(os.tmpdir(), `assistant-knowledge-${process.pid}.json`);
  await writeFile(duplicateFile, JSON.stringify(documents), 'utf8');

  try {
    assert.throws(
      () => run(['validate', '--file', duplicateFile]),
      (error) => /Duplicate slug:/.test(`${error.stdout ?? ''}${error.stderr ?? ''}`),
    );
  } finally {
    await unlink(duplicateFile).catch(() => undefined);
  }
});
