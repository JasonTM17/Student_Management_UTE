-- V103: project the SPECIALIZED corpus into the active runtime release.
--
-- The specialized assistant ("Trợ lý AI chuyên sâu") retrieves exclusively
-- through knowledge_runtime_document rows tagged domain='SPECIALIZED'. On the
-- managed database every specialized document's authored content exists
-- (V93 markdown), but eighteen of twenty-four never received a PUBLISHED
-- revision — the per-document LIKE guards in V93 did not match their remote
-- pre-state, so no new revision was appended and the old ones stayed
-- ARCHIVED. Since every release projection copies the previous runtime rows
-- forward, zero SPECIALIZED documents ever reached the runtime corpus and
-- the specialized page declined every prompt with CHƯA CÓ HƯỚNG DẪN.
--
-- Repair: (1) append a reviewed PUBLISHED revision built from the document's
-- current authored content wherever no published revision exists, then
-- (2) rebuild the active release as the current documents UNION every
-- PUBLISHED specialized revision not already projected, publish and
-- activate it. Same projection mechanics as V78/V93/V96/V101: only runs
-- while local SQL owns the active release (source IN 'MANUAL','LEGACY'),
-- everything is idempotent, and no existing runtime row is mutated.

-- 1. Publish missing specialized revisions from the authored content.
CREATE TEMP TABLE v103_specialized_missing (id UUID PRIMARY KEY) ON COMMIT DROP;
INSERT INTO v103_specialized_missing
SELECT d.id
FROM assistant.knowledge_document d
WHERE d.domain = 'SPECIALIZED'
  AND d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND d.content IS NOT NULL
  AND btrim(d.content) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_document_revision r
      WHERE r.document_id = d.id AND r.state = 'PUBLISHED');

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content,
     source, priority, created_by, reviewed_by, published_at)
SELECT md5(d.id::text || '-v103-specialized-publish')::uuid, d.id,
       COALESCE((SELECT MAX(x.version) FROM assistant.knowledge_document_revision x
                  WHERE x.document_id = d.id), 0) + 1,
       'PUBLISHED', d.domain, d.locale, d.slug, d.title, d.content,
       d.source, d.priority,
       'system-migration', 'system-migration', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d
JOIN v103_specialized_missing m ON m.id = d.id;

-- 2. Rebuild the active release with the specialized corpus folded in.
SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton = TRUE FOR UPDATE;

CREATE TEMP TABLE v103_projected_runtime ON COMMIT DROP AS
SELECT p.source_id, p.revision_id, p.version, p.domain, p.slug, p.locale,
       p.title, p.content, p.source, p.priority, p.active, p.visibility,
       p.published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release
     ON current_release.id = s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id = current_release.id
WHERE s.singleton = TRUE
  AND current_release.status = 'PUBLISHED'
  AND current_release.source IN ('MANUAL', 'LEGACY')
UNION ALL
SELECT d.id::text, r.id, r.version, r.domain, r.slug, r.locale, r.title,
       r.content, r.source, r.priority, TRUE, 'PUBLIC', r.published_at
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
     ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.domain = 'SPECIALIZED'
  AND d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_state s
      JOIN assistant.knowledge_release cr ON cr.id = s.active_release_id
      WHERE s.singleton = TRUE AND cr.status = 'PUBLISHED'
        AND cr.source IN ('MANUAL', 'LEGACY'))
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      JOIN assistant.knowledge_runtime_state s
           ON s.singleton = TRUE AND p.release_id = s.active_release_id
      WHERE p.source_id = d.id::text);

CREATE TEMP TABLE v103_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(
           concat_ws('|', source_id, COALESCE(revision_id::text, ''), version::text,
                     domain, slug, locale, title, content, source, priority::text,
                     active::text, visibility), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
           'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
FROM v103_projected_runtime;

INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest,
     created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000103'::uuid, 'local-demo-v103',
       summary.corpus_hash, summary.row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v103',
           'rowCount', summary.row_count, 'sha256', summary.corpus_hash,
           'documents', summary.documents),
       'system-migration', CURRENT_TIMESTAMP, s.active_release_id
FROM v103_summary summary CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton = TRUE AND EXISTS (SELECT 1 FROM v103_projected_runtime)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_release e
                  WHERE e.id = '00000000-0000-0000-0000-000000000103'::uuid)
  -- When the current release already carries the full corpus (dev databases
  -- seeded it end-to-end), the projected hash collides with the existing
  -- release — that is the idempotent no-op path, not an error.
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_release dup
                  WHERE dup.corpus_hash = summary.corpus_hash);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title,
     content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000103'::uuid, source_id, revision_id, version,
       domain, slug, locale, title, content, source, priority, active, visibility,
       published_at
FROM v103_projected_runtime
WHERE EXISTS (SELECT 1 FROM assistant.knowledge_release e
              WHERE e.id = '00000000-0000-0000-0000-000000000103'::uuid)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_runtime_document x
                  WHERE x.release_id = '00000000-0000-0000-0000-000000000103'::uuid);

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = next_release.id, updated_at = CURRENT_TIMESTAMP
  FROM assistant.knowledge_release next_release
 WHERE s.singleton = TRUE
   AND next_release.id = '00000000-0000-0000-0000-000000000103'::uuid
   AND EXISTS (SELECT 1 FROM v103_projected_runtime);
