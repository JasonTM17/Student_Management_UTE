INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-demo-curriculum-map', 50, 'ACADEMIC_CATALOG'
FROM (VALUES
    ('curriculum-buckets-vi', 'vi', 'Phân loại 100 học phần theo chương trình', 'Danh mục demo có đúng 100 học phần. Đúng kế hoạch là học phần thuộc chương trình SE2026, đúng năm 2 và học kỳ hiện hành. Ngoài kế hoạch là học phần vẫn thuộc SE2026 nhưng dành cho năm hoặc học kỳ khác. Ngoài chương trình là học phần mở để tham khảo hoặc tự chọn nhưng không có liên kết CurriculumCourse trong chương trình SE2026. Khi tư vấn, hãy kiểm tra mã học phần, năm, học kỳ, tín chỉ và điều kiện đăng ký trong dữ liệu hiện hành.'),
    ('curriculum-buckets-en', 'en', 'One hundred course curriculum buckets', 'The demo catalog contains exactly 100 courses. In plan means a course belongs to curriculum SE2026 and matches the student year 2 and the current semester. Out of plan means it belongs to SE2026 but is scheduled for another year or semester. Out of program means the course is offered for reference or electives without a CurriculumCourse link in SE2026. Advice should check the current course code, year, semester, credits, and registration requirements.')
) AS seed(slug, locale, title, content)
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_document existing WHERE existing.slug = seed.slug);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority, created_by, reviewed_by, published_at, domain)
SELECT md5(d.id::text || '-revision-1')::uuid, d.id, 1, 'PUBLISHED', d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-seed', 'system-seed', CURRENT_TIMESTAMP, d.domain
FROM assistant.knowledge_document d
WHERE d.source = 'campuscore-demo-curriculum-map'
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
SELECT '00000000-0000-0000-0000-000000000023'::uuid, 'local-demo-v23', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'local-demo-v23', 'rowCount', row_count, 'sha256', corpus_hash, 'documents', documents),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id FROM assistant.knowledge_runtime_state WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (SELECT 1 FROM assistant.knowledge_release WHERE id = '00000000-0000-0000-0000-000000000023'::uuid);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000023'::uuid, d.id::text, r.id, r.version,
       COALESCE(r.domain, d.domain, 'THESIS'), d.slug, d.locale, d.title, d.content, d.source, d.priority,
       TRUE, 'PUBLIC', COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r ON r.document_id = d.id AND r.state = 'PUBLISHED'
WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1 FROM assistant.knowledge_runtime_document p
      WHERE p.release_id = '00000000-0000-0000-0000-000000000023'::uuid AND p.source_id = d.id::text
  );

INSERT INTO assistant.knowledge_runtime_state (singleton, active_release_id)
VALUES (TRUE, '00000000-0000-0000-0000-000000000023'::uuid)
ON CONFLICT (singleton) DO UPDATE
SET active_release_id = EXCLUDED.active_release_id, updated_at = CURRENT_TIMESTAMP;
