import { execFileSync } from 'node:child_process';
import { readFile, unlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import assert from 'node:assert/strict';
import { canonicalReleaseManifest, normalizeReleaseDocuments } from './assistant-release.mjs';

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

test('release manifest is deterministic and carries every campus domain', () => {
  const corpus = {
    documents: [
      { id: '22222222-2222-4222-8222-222222222222', slug: 'registration', title: 'Registration', content: 'Public registration guidance', locale: 'vi', source: 'registrar', domain: 'REGISTRATION', published_at: '2026-01-01T00:00:00Z' },
      { id: '11111111-1111-4111-8111-111111111111', slug: 'faq', title: 'FAQ', content: 'Public FAQ', locale: 'en', source: 'office', domain: 'GENERAL_FAQ', published_at: '2026-01-01T00:00:00Z' },
    ],
  };
  const first = canonicalReleaseManifest(corpus, 'campus-2026.01');
  const second = canonicalReleaseManifest({ documents: [...corpus.documents].reverse() }, 'campus-2026.01');
  assert.deepEqual(first, second);
  assert.equal(first.schemaVersion, 1);
  assert.equal(first.rowCount, 2);
  assert.match(first.sha256, /^[0-9a-f]{64}$/);
  assert.deepEqual(first.documents.map((document) => document.sourceId), [
    '11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222',
  ]);
});

test('release normalization rejects staff visibility and unsupported domain', () => {
  assert.throws(() => normalizeReleaseDocuments([{ id: '11111111-1111-4111-8111-111111111111', slug: 'staff', title: 'Staff', content: 'private', locale: 'en', source: 'office', visibility: 'STAFF', published_at: '2026-01-01T00:00:00Z' }]), /not public runtime knowledge/);
  assert.throws(() => normalizeReleaseDocuments([{ id: '11111111-1111-4111-8111-111111111111', slug: 'bad', title: 'Bad', content: 'content', locale: 'en', source: 'office', domain: 'STAFF', published_at: '2026-01-01T00:00:00Z' }]), /invalid domain/);
});
