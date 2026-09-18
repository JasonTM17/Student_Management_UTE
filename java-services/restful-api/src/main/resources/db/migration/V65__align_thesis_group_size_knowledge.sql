-- The approved execution contract is now 3-4 students per thesis group,
-- with exactly one leader. V38/V43/V57 are immutable history; this additive
-- migration updates the live corpus and publishes a new immutable snapshot.

UPDATE assistant.knowledge_document
SET content = REPLACE(
        REPLACE(
            REPLACE(content, 'tối đa 03 thành viên', 'từ 3 đến 4 thành viên'),
            'từ 1 đến tối đa 3 sinh viên', 'từ 3 đến 4 sinh viên'),
        'at most 03 members', '3 to 4 members')
WHERE slug IN (
          'thesis-group-members-rules-vi',
          'thesis-group-members-rules-en',
          'graduation-thesis-eligibility-defense-vi',
          'graduation-thesis-eligibility-defense-en'
      )
  AND (
      content LIKE '%tối đa 03 thành viên%'
      OR content LIKE '%từ 1 đến tối đa 3 sinh viên%'
      OR content LIKE '%at most 03 members%'
      OR content LIKE '%1 to 3 students%'
  );

UPDATE assistant.knowledge_document
SET content = REPLACE(content, '1 to 3 students', '3 to 4 students')
WHERE slug = 'graduation-thesis-eligibility-defense-en'
  AND content LIKE '%1 to 3 students%';

UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id
      FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'thesis-group-members-rules-vi',
          'thesis-group-members-rules-en',
          'graduation-thesis-eligibility-defense-vi',
          'graduation-thesis-eligibility-defense-en'
      )
  );

WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'thesis-group-members-rules-vi',
          'thesis-group-members-rules-en',
          'graduation-thesis-eligibility-defense-vi',
          'graduation-thesis-eligibility-defense-en'
      )
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content,
       t.source, t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id
      AND existing.state = 'PUBLISHED'
      AND existing.content = t.content
      AND existing.title = t.title
);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(
        concat_ws('|', source_id, domain, slug, locale, title, content, source,
                  priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object(
               'sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale
           ) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by,
     activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000065'::uuid,
       'thesis-group-size-v65', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'thesis-group-size-v65',
                          'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1 FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000065'::uuid
);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content,
     source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000065'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'THESIS'), d.slug,
       d.locale, d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000065'::uuid
        AND existing.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000065'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id,
    updated_at = CURRENT_TIMESTAMP;
