#!/usr/bin/env node
// Regenerates the Flyway seed for the SPECIALIZED assistant-knowledge corpus
// from its single source of truth:
//
//   supabase/seed/assistant-specialized-knowledge.json
//
//   -> java-services/restful-api/src/main/resources/db/migration/
//      V71__specialized_domain_knowledge.sql
//
// V71 is applied migration history. After initial generation, corpus changes
// require a new additive Flyway migration and a governed Supabase release.
// This script refuses to rewrite the checked-in V71 file.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SEED_PATH = join(ROOT, 'supabase', 'seed', 'assistant-specialized-knowledge.json');
const MIGRATION_PATH = join(
  ROOT,
  'java-services',
  'restful-api',
  'src',
  'main',
  'resources',
  'db',
  'migration',
  'V71__specialized_domain_knowledge.sql',
);

const sqlQuote = (text) => String(text).replace(/'/g, "''");

function loadRows() {
  const rows = JSON.parse(readFileSync(SEED_PATH, 'utf8'));
  if (!Array.isArray(rows) || rows.length === 0) {
    throw new Error(`${SEED_PATH} must contain a non-empty JSON array`);
  }
  const seen = new Set();
  for (const row of rows) {
    for (const field of ['slug', 'locale', 'title', 'content', 'priority']) {
      if (row[field] === undefined || row[field] === null || row[field] === '') {
        throw new Error(`seed row ${row.slug || '?'} is missing ${field}`);
      }
    }
    if (seen.has(row.slug)) {
      throw new Error(`duplicate slug in corpus: ${row.slug}`);
    }
    seen.add(row.slug);
  }
  return rows;
}

function buildMigration(rows) {
  const values = rows
    .map(
      (row) =>
        `    ('${sqlQuote(row.slug)}', '${row.locale}',\n` +
        `     '${sqlQuote(row.title)}',\n` +
        `     '${sqlQuote(row.content)}',\n` +
        `     ${row.priority}, 'SPECIALIZED')`,
    )
    .join(',\n');
  const slugList = rows.map((row) => `'${row.slug}'`).join(',\n          ');
  return `-- Flyway Migration V71: SPECIALIZED assistant-knowledge domain (Trợ lý chuyên sâu)
-- Publishes the curated professional-domain corpus (software engineering expert Q&A)
-- with domain = 'SPECIALIZED', consumed by the specialized assistant scope
-- (ChatRequest.scope = 'specialized'). Mirrors
-- supabase/seed/assistant-specialized-knowledge.json, which is uploaded to the
-- Supabase authoring project via scripts/supabase/assistant-knowledge.mjs using
-- SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY from the process environment.
-- Regenerate with: node scripts/generate-specialized-knowledge.mjs
-- Replaces the active release with immutable snapshot
-- '00000000-0000-0000-0000-000000000071'.

SET search_path = thesis, assistant, public;

-- 0. Extend the governed domain whitelist (V16) with SPECIALIZED on all three
--    knowledge tables before any row is written.
ALTER TABLE assistant.knowledge_document
    DROP CONSTRAINT IF EXISTS assistant_knowledge_domain_valid;
ALTER TABLE assistant.knowledge_document
    ADD CONSTRAINT assistant_knowledge_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));
ALTER TABLE assistant.knowledge_document_revision
    DROP CONSTRAINT IF EXISTS assistant_revision_domain_valid;
ALTER TABLE assistant.knowledge_document_revision
    ADD CONSTRAINT assistant_revision_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));
ALTER TABLE assistant.knowledge_runtime_document
    DROP CONSTRAINT IF EXISTS assistant_runtime_domain_valid;
ALTER TABLE assistant.knowledge_runtime_document
    ADD CONSTRAINT assistant_runtime_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));

-- 1. Insert new documents into assistant.knowledge_document
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-specialized-corpus', seed.priority, seed.domain
FROM (VALUES
${values}
) AS seed(slug, locale, title, content, priority, domain)
ON CONFLICT (slug) DO UPDATE
SET title = EXCLUDED.title,
    content = EXCLUDED.content,
    priority = EXCLUDED.priority,
    domain = EXCLUDED.domain,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Archive superseded published revisions of these documents
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          ${slugList}));

-- 3. Publish new versioned revisions for the documents
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          ${slugList})
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content, t.source,
       t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id AND existing.version = t.next_version);

-- 4. Compute release summary and create release '00000000-0000-0000-0000-000000000071'
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id
     AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
),
summary AS (
    SELECT
        encode(
            thesis.digest(
                coalesce(
                    string_agg(
                        concat_ws('|', source_id, domain, slug, locale, title, content, source,
                                  priority::text, version::text),
                        E'\\n' ORDER BY source_id
                    ),
                    ''
                ),
                'sha256'
            ),
            'hex'
        ) AS corpus_hash,
        COUNT(*)::integer AS row_count,
        COALESCE(
            jsonb_agg(
                jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale)
                ORDER BY source_id
            ),
            '[]'::jsonb
        ) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000071'::uuid,
       'specialized-domain-corpus-v71', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'specialized-domain-corpus-v71',
           'rowCount', row_count,
           'sha256', corpus_hash,
           'documents', documents
       ),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id
        FROM assistant.knowledge_runtime_state
        WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000071'::uuid
);

-- 5. Project all published public documents into release '00000000-0000-0000-0000-000000000071'
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000071'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale,
       d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id
 AND r.state = 'PUBLISHED'
WHERE d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1
      FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000071'::uuid
        AND existing.source_id = d.id::text
  );

-- 6. Switch the runtime state to active release v71
UPDATE assistant.knowledge_runtime_state
SET active_release_id = '00000000-0000-0000-0000-000000000071'::uuid,
    updated_at = CURRENT_TIMESTAMP
WHERE singleton = TRUE;
`;
}

if (existsSync(MIGRATION_PATH)) {
  console.error('V71 is applied migration history; add a new Flyway migration instead of regenerating it.');
  process.exitCode = 1;
} else {
  const rows = loadRows();
  writeFileSync(MIGRATION_PATH, buildMigration(rows), 'utf8');
  console.log(`rows=${rows.length} seed=assistant-specialized-knowledge.json migration=V71__specialized_domain_knowledge.sql`);
}
