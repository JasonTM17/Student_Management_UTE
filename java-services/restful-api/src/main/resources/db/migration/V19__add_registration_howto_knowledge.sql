INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-demo-registration-guide', 55, 'REGISTRATION'
FROM (VALUES
    ('registration-howto-vi', 'vi', 'Cách đăng ký học phần trên CampusCore', 'Mở Cổng sinh viên > Đăng ký học phần, chọn đúng học kỳ, tìm theo mã hoặc tên học phần, đọc số tín chỉ và lịch học, sau đó bấm Đăng ký ở lớp còn chỗ. Kiểm tra danh sách đã đăng ký và tổng tín chỉ sau khi xác nhận. Nếu hệ thống báo trùng lịch, đủ tín chỉ hoặc lớp đã đóng, hãy chọn lớp khác hoặc xem lại đợt đăng ký đang mở.'),
    ('registration-howto-en', 'en', 'How to register for a course in CampusCore', 'Open Student Portal > Course registration, choose the semester, search by course code or name, review credits and meeting times, then select Register for a section with available seats. Check your registered list and total credits after confirmation. If the system reports a schedule conflict, credit limit, or closed section, choose another section or review the active registration window.')
) AS seed(slug, locale, title, content)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-demo-registration-guide'
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_document_revision r WHERE r.document_id = d.id AND r.version = 1);

WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'THESIS') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
), summary AS (
    SELECT encode(digest(COALESCE(string_agg(concat_ws('|', source_id, domain, slug, locale, title, content, source, priority::text, version::text), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
           COUNT(*)::integer AS row_count,
           COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000019'::uuid, 'local-demo-v19', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v19', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000019'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000019'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000019'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000019'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
