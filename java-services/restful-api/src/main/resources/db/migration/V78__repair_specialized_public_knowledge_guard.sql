-- V71 is already applied and must keep its Flyway checksum. Its Vietnamese
-- RAG lesson quoted a blocked instruction, so the read-time public guard
-- discarded that otherwise public SPECIALIZED source. Correct the authoring
-- row, append a reviewed system revision, and activate a new immutable runtime
-- snapshot only while local SQL owns the active release.

CREATE TEMP TABLE v78_changed_document (id UUID PRIMARY KEY) ON COMMIT DROP;
WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = REPLACE(content, 'bỏ qua hướng dẫn', 'yêu cầu thay đổi chỉ dẫn'),
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-ai-rag-llm-foundations-vi'
       AND content LIKE '%bỏ qua hướng dẫn%'
     RETURNING id
)
INSERT INTO v78_changed_document SELECT id FROM corrected;

UPDATE assistant.knowledge_document_revision r
   SET state = 'ARCHIVED'
 WHERE r.state = 'PUBLISHED'
   AND r.document_id IN (SELECT id FROM v78_changed_document);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content,
     source, priority, created_by, reviewed_by, published_at)
SELECT md5(d.id::text || '-specialized-guard-v78')::uuid,
       d.id, COALESCE((SELECT MAX(version) FROM assistant.knowledge_document_revision r
                        WHERE r.document_id = d.id), 0) + 1,
       'PUBLISHED', d.domain, d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d
JOIN v78_changed_document changed ON changed.id = d.id;

-- Lock the same singleton used by runtime promotion. Under remote authority,
-- leave its active Supabase projection untouched; the new Supabase seed must
-- instead be published and reconciled through its own governance workflow.
SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton = TRUE FOR UPDATE;

CREATE TEMP TABLE v78_projected_runtime ON COMMIT DROP AS
SELECT p.source_id,
       COALESCE(r.id, p.revision_id) AS revision_id,
       COALESCE(r.version, p.version) AS version,
       COALESCE(r.domain, p.domain) AS domain,
       COALESCE(r.slug, p.slug) AS slug,
       COALESCE(r.locale, p.locale) AS locale,
       COALESCE(r.title, p.title) AS title,
       COALESCE(r.content, p.content) AS content,
       COALESCE(r.source, p.source) AS source,
       COALESCE(r.priority, p.priority) AS priority,
       p.active, p.visibility,
       COALESCE(r.published_at, p.published_at) AS published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release ON current_release.id = s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id = current_release.id
LEFT JOIN v78_changed_document changed ON changed.id::text = p.source_id
LEFT JOIN assistant.knowledge_document_revision r
  ON r.document_id = changed.id AND r.state = 'PUBLISHED'
WHERE s.singleton = TRUE
  AND current_release.status = 'PUBLISHED'
  AND current_release.source IN ('MANUAL', 'LEGACY')
  AND EXISTS (SELECT 1 FROM v78_changed_document);

CREATE TEMP TABLE v78_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(
           concat_ws('|', source_id, COALESCE(revision_id::text, ''), version::text,
                     domain, slug, locale, title, content, source, priority::text,
                     active::text, visibility), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
           'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
FROM v78_projected_runtime;

-- A matching hash is not enough to reuse an archived or differently sourced
-- release. Fail the Flyway transaction rather than commit repaired authoring
-- with a stale runtime pointer. An operator can inspect that exceptional
-- history before retrying the migration.
DO $$
DECLARE existing_release assistant.knowledge_release%ROWTYPE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM v78_projected_runtime) THEN
        RETURN;
    END IF;
    SELECT r.* INTO existing_release
      FROM assistant.knowledge_release r
      JOIN v78_summary s ON s.corpus_hash = r.corpus_hash
     LIMIT 1;
    IF existing_release.id IS NOT NULL THEN
        IF existing_release.status <> 'PUBLISHED'
           OR existing_release.source NOT IN ('MANUAL', 'LEGACY') THEN
            RAISE EXCEPTION 'V78 target corpus exists under an inactive or foreign release';
        END IF;
        IF EXISTS (
            (SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM v78_projected_runtime
             EXCEPT
             SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM assistant.knowledge_runtime_document
              WHERE release_id = existing_release.id)
            UNION ALL
            (SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM assistant.knowledge_runtime_document
              WHERE release_id = existing_release.id
             EXCEPT
             SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM v78_projected_runtime)
        ) THEN
            RAISE EXCEPTION 'V78 target corpus differs from matching release rows';
        END IF;
    END IF;
END;
$$;

INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest,
     created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000078'::uuid,
       'specialized-public-guard-v78', summary.corpus_hash, summary.row_count,
       'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'specialized-public-guard-v78',
                          'rowCount', summary.row_count, 'sha256', summary.corpus_hash,
                          'documents', summary.documents),
       'system-migration', CURRENT_TIMESTAMP, s.active_release_id
FROM v78_summary summary
CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton = TRUE
  AND EXISTS (SELECT 1 FROM v78_projected_runtime)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_release
                  WHERE corpus_hash = summary.corpus_hash);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title,
     content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000078'::uuid,
       source_id, revision_id, version, domain, slug, locale, title, content,
       source, priority, active, visibility, published_at
FROM v78_projected_runtime
WHERE EXISTS (SELECT 1 FROM assistant.knowledge_release
              WHERE id = '00000000-0000-0000-0000-000000000078'::uuid);

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = next_release.id, updated_at = CURRENT_TIMESTAMP
  FROM v78_summary summary
  JOIN assistant.knowledge_release next_release
    ON next_release.corpus_hash = summary.corpus_hash AND next_release.status = 'PUBLISHED'
 WHERE s.singleton = TRUE
   AND EXISTS (SELECT 1 FROM v78_projected_runtime);
